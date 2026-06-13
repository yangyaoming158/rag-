# Phase 1 实现规划 — 知识库与文档管理

- 日期：2026-06-13
- 主力模型：Codex + Sonnet
- 对应规划：`RAG规划-03` Phase 1、`RAG规划-02` 第 3.2、3.3、4、5 节

## 1. 本阶段目标（一句话）
实现知识库 CRUD、文档上传落盘、文档列表/状态/删除，并让前端完成可演示的管理闭环；本阶段不解析、不切块、不向量化。

## 2. 验收 Gate（开工即明确，照抄不改）
- [x] 传 `.exe` 返回 422
- [x] 重复文件返回 409
- [x] 删 KB 后磁盘文件消失
- [x] 前端全交互可用 + 校验逻辑有单测

## 3. 任务卡拆分

### 任务 1：实现知识库 CRUD API
- **上下文**：规划 02 第 3.2 节：KB 的 CRUD；删除 KB 级联删除文档/块/向量/文件。API：`GET/POST /api/kbs`，`DELETE /api/kbs/{id}`。同 owner 同名拒绝 409；删除后向量计数为 0 且磁盘文件已清。
- **改动文件**：`backend/src/main/java/com/ragdocs/domain/*`、`backend/src/main/java/com/ragdocs/repository/*`、`backend/src/main/java/com/ragdocs/service/*`、`backend/src/main/java/com/ragdocs/web/*`、`backend/src/test/java/**`
- **契约**：
  - `POST /api/kbs`：`{name, description}` → `KbDto`
  - `GET /api/kbs`：`KbDto[]`，包含 `documentCount`
  - `DELETE /api/kbs/{id}`：owner 校验；删除数据库记录并清理磁盘文件
- **验收**：`mvn test`；curl 创建、列表、重复创建 409、删除不存在 404。
- **禁止**：不做 RBAC、多租户、分享知识库、文件夹层级。

### 任务 2：实现本地 StorageService 与文档上传 API
- **上下文**：规划 02 第 3.3 节：multipart 接收、类型/大小白名单校验、sha256 去重、落盘到 `./data/files/{kbId}/{docId}.{ext}`、创建 document 记录 + ingestion job。文件名注入路径 → 存储名一律用 `docId`，原始文件名只进数据库字段。
- **改动文件**：`backend/src/main/java/com/ragdocs/service/StorageService.java`、`backend/src/main/java/com/ragdocs/service/LocalStorageService.java`、`backend/src/main/java/com/ragdocs/repository/DocumentRepository.java`、`backend/src/main/java/com/ragdocs/repository/IngestionJobRepository.java`、`backend/src/main/java/com/ragdocs/web/DocumentController.java`、`backend/src/test/java/**`
- **契约**：
  - `POST /api/kbs/{kbId}/documents`：multipart `file` → `DocumentDto{id,status=UPLOADED,jobId}`
  - 白名单：`.md`、`.markdown`、`.txt`、`.pdf`
  - 大小：`> 0` 且 `<= 20MB`
  - 重复：同一 `kb_id + sha256` 返回 409，并带已存在 `docId` 信息
- **验收**：`.exe` 返回 422；重复文件返回 409；数据库存在 `UPLOADED` 文档与 `PARSE/PENDING` job；文件实际落盘。
- **禁止**：不做批量上传、断点续传、docx、解析、切块、embedding。

### 任务 3：实现文档列表、删除与 ingestion 查询 API
- **上下文**：规划 02 第 5 节 API 6–8：`GET /api/kbs/{kbId}/documents?status=&page=`、`DELETE /api/documents/{id}`、`GET /api/documents/{id}/ingestion`，均需 owner 校验。删除文档时数据库 chunks 由外键级联删除，citations 置 NULL 保快照，磁盘文件必须清理。
- **改动文件**：`backend/src/main/java/com/ragdocs/repository/*`、`backend/src/main/java/com/ragdocs/service/*`、`backend/src/main/java/com/ragdocs/web/*`、`backend/src/test/java/**`
- **契约**：
  - `GET /api/kbs/{kbId}/documents?status=&page=` → `Page<DocumentDto>`
  - `DELETE /api/documents/{id}` → 空响应
  - `GET /api/documents/{id}/ingestion` → `JobDto[]`
- **验收**：上传后列表可见；删除文档后磁盘文件消失；跨 owner 或不存在资源返回 404/403。
- **禁止**：不做重新解析按钮的后端行为，不改 Phase 2 ingestion 状态机。

### 任务 4：实现前端知识库列表与详情页
- **上下文**：规划 02 第 8 节：知识库列表为卡片栅格 + 新建弹窗 + 删除二次确认；知识库详情支持上传、文档表格、状态徽标、失败行展开 error。
- **改动文件**：`frontend/src/api/*`、`frontend/src/router/index.ts`、`frontend/src/views/*`、`frontend/src/styles.css`
- **契约**：前端调用 API 2–8；所有请求带 JWT；上传失败展示后端错误消息；删除操作必须二次确认。
- **验收**：登录后可新建 KB、进入详情、上传 `.md/.txt/.pdf`、看到文档状态、删除文档、删除 KB。
- **禁止**：不做暗色主题、移动端适配、聊天页、检索调试页、后台统计页。

## 4. 本阶段红线
- 禁止：文件夹/批量上传、断点续传、网页 URL 导入、知识库分享、RBAC、多租户。
- 暂缓（非本阶段做）：解析、切块、embedding、检索、问答、重新解析幂等重跑、docx。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| 文件名路径注入 | 原始文件名只入库；磁盘文件名用 `docId + ext` |
| 级联删除遗漏磁盘文件 | 删除 KB 前先查出 storage path，数据库删除后清理文件目录 |
| 重复文件写入半成品 | 先算 sha256 并预查重复；唯一约束作为并发兜底返回 409；成功拿到 docId 后再落盘并回写 storage_path |
| 校验逻辑散落 | 文件类型、大小、扩展名集中在 service 层，并用单测覆盖 |

## 6. 完成后动作
- [x] 开工前已从最新 `main` 切出 `phase-1` 分支（禁止在 main 上提交）
- [x] 跑全量测试 + `docker compose up -d` 冷启动验证
- [x] `docs/dev-log.md` 追加各任务「做了什么/没做什么/遗留」
- [x] Gate 全过 → 在 `PROGRESS.md` 勾掉本 Phase 并填日期；不过 → 记原因，不进下一阶段
- [x] 在 `phase-1` 分支做「阶段完成」提交：`git commit -m "phase-1: KB CRUD + 文档上传管理"`
- [ ] Gate 通过后合并：`git checkout main && git merge --no-ff phase-1`

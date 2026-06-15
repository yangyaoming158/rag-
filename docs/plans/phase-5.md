# Phase 5 实现规划 — 后台管理与可观测性

- 日期：2026-06-15
- 主力模型：Sonnet（后台页）+ Codex（查询接口与联调）
- 对应规划：`RAG规划-03` Phase 5、`RAG规划-02` 第 3.12、5、9 节

## 1. 本阶段目标（一句话）
补齐后台 ingestion 日志、模型调用日志、检索调试与统计卡片，让“上传坏文件 → 后台定位失败原因 → 重跑成功”可以一镜到底演示。

## 2. 验收 Gate（开工即明确，照抄不改）
- [x] 「上传坏文件 → 后台定位失败原因 → 重跑成功」一镜到底

## 3. 任务卡拆分

### 任务 1：补齐后台查询 API 与统计接口
- **上下文**：规划 02 第 5 节 API 14/15/17：`GET /api/admin/ingestion-jobs?status=&page=` 返回关联文档名的 `Page<JobDto>`；`GET /api/admin/model-calls?type=&status=&page=` 返回 `Page<ModelCallDto>`；`GET /api/admin/stats/overview` 返回 `{kbCount,docCount,chunkCount,tokenSum,avgLatency}`。
- **改动文件**：`backend/src/main/java/com/ragdocs/web/AdminController.java`、`backend/src/main/java/com/ragdocs/web/dto/*`、`backend/src/main/java/com/ragdocs/repository/*`
- **契约**：
  - `GET /api/admin/ingestion-jobs?status=&page=&size=`：按创建时间倒序，包含 `documentId`、`documentFilename`、`kbId`、`kbName`、`phase`、`status`、`attempt`、`errorMessage`、时间字段
  - `GET /api/admin/model-calls?type=&status=&page=&size=`：按创建时间倒序，包含 provider、model、token、latency、status、error
  - `GET /api/admin/stats/overview`：返回知识库数、文档数、chunk 数、token 总和、平均延迟
- **验收**：`mvn test` 通过；登录后调用三个接口均返回统一 `{code,message,data}`。
- **禁止**：不做 RBAC、不新增 audit 表、不引入 Prometheus/Grafana/链路追踪。

### 任务 2：实现后台前端页与导航入口
- **上下文**：规划 02 第 9 节：左侧固定导航含知识库/问答/历史/后台，后台子页用 tab；后台 ingestion 日志支持状态过滤与错误详情，模型调用日志展示成本/延迟与 token 汇总，检索调试展示 top-8 chunk 与分数条，统计卡片展示 4 个数字卡片。
- **改动文件**：`frontend/src/api/admin.ts`、`frontend/src/router/index.ts`、`frontend/src/views/AdminView.vue`、`frontend/src/App.vue`、必要时 `frontend/src/styles.css`
- **契约**：
  - `/admin` 页面包含概览、Ingestion 日志、模型调用日志、检索调试四个 tab
  - Ingestion 失败行可展开查看错误，并提供跳转文档与重跑入口
  - 检索调试复用 `POST /api/admin/retrieval-debug`
- **验收**：`npm run build` 通过；页面可完成过滤、展开、跳转、重跑和检索调试。
- **禁止**：不做暗色主题、i18n、移动端专项适配、复杂图表库。

### 任务 3：打通失败排查与重跑演示链路
- **上下文**：Phase 5 Gate：演示动线“上传坏文件 → 后台定位失败原因 → 重跑成功”一镜到底；Phase 2 已有文档失败状态与重新解析幂等能力。
- **改动文件**：`backend/src/main/java/com/ragdocs/web/DocumentController.java`（如需补重跑契约）、`frontend/src/views/KbDetailView.vue`、`frontend/src/views/AdminView.vue`、`docs/dev-log.md`
- **契约**：
  - 上传坏文件后，后台 Ingestion 日志能展示 `FAILED` job 与可读 `errorMessage`
  - 失败日志能定位到 KB/文档，并跳转文档详情页
  - 对同一文档执行“重新解析”后，后台日志能看到新任务，成功后文档状态为 `READY`
- **验收**：Docker 环境中手工跑通 Gate，并记录步骤与结果。
- **禁止**：不做临时绕过失败路径；不伪造成功状态；不新增非规划内后台功能。

### 任务 4：阶段收尾、文档与 Gate 验收
- **上下文**：CLAUDE.md 第 8 节：Gate 通过后更新 `PROGRESS.md`，每阶段结束跑全量测试与 Compose 冷启动验证。
- **改动文件**：`docs/dev-log.md`、`PROGRESS.md`、`README.md`（仅状态/能力小幅同步）、`docs/plans/phase-5.md`
- **契约**：
  - `docs/dev-log.md` 记录做了什么、没做什么、遗留
  - `PROGRESS.md` 只在 Gate 全过后勾选 Phase 5
  - 收尾提交信息：`phase-5: 后台管理与可观测性`
- **验收**：`mvn test`、`npm run build`、`git diff --check`、`docker compose up -d` 均通过。
- **禁止**：不进入 Phase 6；不提前做 Phase 7 演示包装。

## 4. 本阶段红线
- 禁止：Prometheus/Grafana、链路追踪、告警、RBAC、audit_logs、复杂 BI 图表、自动执行写操作。
- 暂缓（非本阶段做）：SSE、Agent、README 完整包装、录屏脚本、真实 token 价格计算。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| 后台接口变成越权入口 | 维持当前单 admin 用户模型，所有接口仍走 JWT，不新增 RBAC |
| 失败原因不可读 | 直接展示 `ingestion_jobs.error_message` 与文档 `error_message`，保留 500 字上限 |
| 统计卡片误导为生产级监控 | 仅展示库内聚合数字，文档说明为展示版统计，不引入监控系统 |
| 重跑后旧失败日志和新成功日志混淆 | 日志按创建时间倒序，并显示 documentId、phase、status、attempt、时间 |
| 前端后台过度设计 | 使用 Element Plus 表格、Tabs、统计卡片，不引入图表库 |

## 6. 完成后动作
- [x] 开工前已从最新 `main` 切出 `phase-5` 分支（禁止在 main 上提交）
- [x] 跑全量测试 + `docker compose up -d` 冷启动验证
- [x] `docs/dev-log.md` 追加各任务「做了什么/没做什么/遗留」
- [x] Gate 全过 → 在 `PROGRESS.md` 勾掉本 Phase 并填日期；不过 → 记原因，不进下一阶段
- [ ] 在 `phase-5` 分支做「阶段完成」提交：`git commit -m "phase-5: 后台管理与可观测性"`
- [ ] Gate 通过后合并：`git checkout main && git merge --no-ff phase-5`

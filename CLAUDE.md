# CLAUDE.md — DevDocs RAG 项目规则

> 本文件是所有 AI 模型（Codex / Opus / Sonnet / Fable 5 等）在本仓库工作时的**最高执行准则**。
> 它由四份规划书（`RAG规划-01~04.md`）提炼而成。规划书是「为什么」，本文件是「怎么做、不许做什么」。
> 冲突时以规划书原文为准；但任何对范围的扩张，都要先通过本文件第 9 节的「失控防线」。

---

## 0. 一句话定位

**DevDocs RAG**：一个 Spring Boot 单体 + Vue 3 的「项目文档智能问答系统」。上传工程文档（Markdown/PDF/TXT）→ 解析切块 → 向量化入 pgvector → 带**引用溯源**的检索问答。种子语料是 mini-mall 微服务项目的 30+ 份真实工程文档。

这是一个**简历/答辩项目**，不是商业产品。每一个功能都必须能回答：「它出现在 5 分钟演示的哪一步？」答不上来就不做。

---

## 1. 当前状态（执行前必读）

- 本仓库目前**只有四份规划书，无任何代码**。
- 前置依赖：本项目在 `mini-mall` 项目 Phase 3 收尾验收通过后才正式启动编码。
- 启动第一件事（Phase 0 之前）：验证外部依赖——`curl` 验证 DeepSeek chat key 与 embedding key（SiliconFlow `bge-m3` 或 DashScope `text-embedding-v4`）可用。这是唯一的外部依赖，先排雷。
- **无 key 也必须能跑全链路**：默认走 Mock Provider，CI/离线演示靠它兜底。

---

## 2. 技术栈（锁定，不得替换）

| 层 | 选型 | 红线 |
|---|---|---|
| 后端 | Java 17 + Spring Boot 3.x **单体单模块**（Maven） | 禁止 Spring Cloud / 多模块 / 微服务拆分 |
| AI 接入 | Spring AI，**只用 `ChatClient` 和 `EmbeddingModel`** | 禁止 LangChain4j；禁止用 Spring AI 的 `PgVectorStore` |
| 数据库 | PostgreSQL 16 + pgvector（关系表与向量**同库同表**） | 禁止 Milvus / ES / 独立向量库 |
| 迁移 | Flyway | — |
| 文档解析 | Apache Tika（PDF/TXT/DOCX）；Markdown 自己按行解析保留标题层级 | 禁止 OCR / 扫描件 / 图片提取 / 表格结构化 |
| 文件存储 | 本地磁盘卷，藏在 `StorageService` 接口后 | 禁止 MinIO（接口预留以便后换） |
| 前端 | Vue 3 + Vite + Pinia + Element Plus | 禁止暗色主题 / i18n / 移动端适配 |
| 流式 | SSE（仅展示版做）；MVP 先同步 JSON | — |
| 部署 | Docker Compose，**共 3 容器**（postgres + backend + frontend/Nginx） | 禁止 K8s |
| 可观测 | `model_call_logs` / `ingestion_jobs` 表 + 后台页面 | 禁止 Prometheus / Grafana / 链路追踪 |

**Provider 铁律**：所有出网调用都走 `ChatProvider` / `EmbeddingProvider` 接口，OpenAI 兼容协议，配置前缀 `rag.ai.*`，环境变量注入。每个接口都有真实实现 + `Mock*` 实现，Mock 为默认。Mock embedding 用内容哈希播种生成确定性伪向量（维度必须与真实一致，1024 维）。

---

## 3. 架构与包结构

单体分层，不分进程。四层包结构：

```
com.ragdocs.{web, service, repository, domain, provider, ingestion, retrieval, rag, config}
```

- `web` 控制器 → `service` 业务 → `repository` 持久化 → `provider` 出网。
- `ingestion` 异步入库（Spring `@Async` 线程池 + 数据库任务表，**不引入 MQ**）。
- `retrieval` 检索（自己写 SQL，收口在 `RetrievalPipeline` 接口后，便于将来加 rerank）。
- `rag` prompt 构造 → LLM 调用 → 引用解析。

数据流：`frontend → backend(web/service/repository/provider) → postgres(关系表+vector列同库)`；原始文件落 `./data/files/{kbId}/{docId}.{ext}`。

---

## 4. 编码约定（强制）

- **统一响应体**：`{code, message, data}`。
- **统一鉴权**：除 `POST /api/auth/login` 外全部要求 `Authorization: Bearer <JWT>`。JWT 自己写（复用 mini-mall 经验），**无注册 / 无刷新 token / 无找回密码**。
- **错误码族**（不得新增同义码）：
  - `40001` 参数错误 / `40101` 未认证 / `40301` 非本人资源 / `40401` 不存在 / `40901` 重复（同名 KB、重复文件）/ `42201` 文件类型不支持或超限 / `50001` 内部错误 / `50201` LLM 调用失败 / `50202` Embedding 调用失败。
- **属主隔离**：KB 带 `owner_id`；所有查询按 owner 过滤；检索 SQL 强制 `WHERE kb_id = ?`（`kb_id` 冗余进 chunk 表免 JOIN）。**不做 RBAC**——`owner_id` 就是权限模型。
- **失败显式化**：模型/解析失败**绝不静默吞掉**——置明确状态 + 错误码 + 写日志。
- **主键** `BIGSERIAL`，**时间** `timestamptz`，外键全部显式声明。
- **dev-log 习惯**：每个任务记录「做了什么 / 没做什么 / 遗留」。

---

## 5. 核心算法规格（不许自由发挥，照抄实现）

### 5.1 切块（chunking）— 检索质量第一决定因素
- **Markdown**：按标题层级建树 → 叶子节正文 <200 字向上合并到兄弟/父节 → >900 字按段落 +120 字重叠滑窗拆分 → `heading_path` 记录如 `架构设计 > 网关路由`。
- **PDF/TXT**：按空行分段 → 顺序累积到 600–900 字封块 → 相邻块重叠 120 字 → 记录起止页码。
- 输出 `List<ChunkDraft>{content, chunkIndex, headingPath, pageStart, pageEnd, charLen}`，每块长度落在 [200, 1000] 字。
- 此算法由 **Opus** 实现并写单测，不交给批量实现模型自由发挥。

### 5.2 检索 SQL（交付时直接给这条）
```sql
SELECT c.id, c.document_id, c.content, c.heading_path, c.page_start,
       1 - (c.embedding <=> :queryVec) AS similarity
FROM document_chunks c
JOIN documents d ON d.id = c.document_id AND d.status = 'READY'
WHERE c.kb_id = :kbId
ORDER BY c.embedding <=> :queryVec
LIMIT 8;
```
索引：`hnsw (embedding vector_cosine_ops)` + `btree(kb_id)` + `btree(document_id)`。

### 5.3 问答流程（含五层防幻觉）
1. 校验会话归属 + question 长度（≤2000 字）。
2. 会话绑定**单一 kb_id**（MVP 不做跨库联检）。
3. 问题 embedding（1 次调用，记日志）。
4. 向量检索 top-8（kb 过滤）。
5. **阈值短路**：top1 similarity < 0.4（初始值，Phase 3 用真实语料标定）→ 直接返回固定 NO_ANSWER 话术，**不调 LLM**（省成本 + 防幻觉第一道闸）。
6. 截断：取前 6 块，合计 ≤6000 字，超出按相似度从低到高丢弃。
7. 构造 prompt（模板进资源文件带版本号 + 最近 3 轮历史，每条截 500 字）。
8. `ChatProvider` 调用（temperature 0.2，60s 超时，重试 1 次）。
9. 解析答案中 `[n]` → 映射 chunk → 写 `citations`（含原文快照 ≤500 字）。引用了不存在的编号 → 丢弃 + `citationWarning`；无任何 `[n]` 且非拒答 → 标 `UNGROUNDED`。
10. 落 `messages`（status: `OK/NO_ANSWER/UNGROUNDED/ERROR`）+ `model_call_logs`。
11. 返回 `{answer, citations, status, latencyMs}`。

**五层防幻觉**（每层面试都能单独展开）：检索阈值短路 → 低 temperature → 模板三规则 → 引用编号校验 → 25 题评测集人工验收。

### 5.4 入库状态机（幂等可重跑）
`UPLOADED → PARSING → CHUNKING → EMBEDDING → READY / FAILED`
- 一个文档同一时刻只有一个 RUNNING job（`documents.status` 即互斥锁）。
- EMBED 分批（≤32），批级指数退避重试（attempt ≤3）；仍失败整文档 FAILED。
- 「重新解析」= 重置状态 + 清空该文档已有 chunk + 重建 job（保证幂等，不产生重复 chunk）。
- 解析后文本 <100 字或非法字符 >30% → 判 FAILED 给可读原因，**不写兜底魔法**。

---

## 6. 数据表（Flyway V1 一次建全）

`users` / `knowledge_bases` / `documents` / `document_chunks`（含 `embedding vector(1024)`）/ `conversations` / `messages` / `citations` / `ingestion_jobs` / `model_call_logs`。详细字段见 `RAG规划-02-架构设计.md` 第 4 节。

关键约束：
- `knowledge_bases`：`UNIQUE(owner_id, name)`。
- `documents`：`UNIQUE(kb_id, sha256)` 防重传。
- `document_chunks`：向量与 metadata **同行**（强类型列，不用 jsonb）；`ON DELETE CASCADE` 跟随 document。
- `citations.chunk_id`：**`ON DELETE SET NULL`** + 存原文快照——文档删除后历史问答的引用仍可读。
- 种子 admin 用户在 V1 写入。
- **不建表**：`audit_logs`、`permissions`（过度设计）。`tool_call_logs` 仅 Phase 6 建。

---

## 7. 开发阶段与验收 Gate（Gate 不过不进下一阶段）

| Phase | 内容 | 验收 Gate（摘要） | 主力模型 |
|---|---|---|---|
| 0 | 初始化：3 容器 Compose + Flyway V1 + 前后端骨架 | `docker compose up -d` health 通过；登录拿 token；`\d document_chunks` 见 vector 列与 hnsw 索引 | Codex |
| 1 | KB CRUD + 上传落盘 + 文档列表/状态/删除 | `.exe` 422 / 重复文件 409 / 删 KB 后磁盘文件消失 | Codex + Sonnet |
| 2 | 解析 + 切块入库（无向量），失败路径完整 | 三件套入库 chunk 长度全在 [200,1000]，heading_path 抽查 ≥9/10；坏 PDF FAILED 给原因；重跑不产生重复 chunk | **Opus**（切块）+ Codex |
| 3 | Embedding + 向量检索 + 检索调试页 + 标定阈值 | 10 个标准 query top1 命中 ≥8/10；Mock 全链路可跑；KB-A 检索不到 KB-B | Codex + Opus 审隔离 |
| 4 | RAG 问答闭环 + 引用 + 无答案 + 历史 + 调用日志 | 评测集库内题有据回答 ≥16/20，库外题 5/5 走 NO_ANSWER；拔 key 返回 50201 且日志可查 | **Opus**（prompt/引用）+ Codex + Sonnet |
| 5 | 后台三页补齐 + 统计卡片（展示版） | 「上传坏文件→后台定位失败原因→重跑成功」一镜到底 | Sonnet |
| 6 | Agent 增强（**默认跳过**） | 见第 10 节 | — |
| 7 | 演示/README/简历包装（**不可裁剪**） | 干净机器（或删 volume）按 README 三命令冷启动成功；录屏 ≤6 分钟 | Fable 5 审 + Sonnet |

每个 Phase 结束跑全量测试 + Compose 冷启动验证。

---

## 8. 阶段执行工作流（每个 Phase 都照此走）

规划书（`规划-01~04`）是**只读的事实依据**，任何模型**不得回写状态进规划书**。进度只在下面三个落点流动，各归各位：

| 时机 | 落点 | 动作 |
|---|---|---|
| **开工前** | `docs/plans/phase-N.md` | 按 `docs/plans/_template.md` 把本 Phase 拆成若干任务卡（任务卡六项见第 11 节）。这是动手前的强制步骤——不写规划不许写代码 |
| **实现中** | `docs/dev-log.md` | 每完成一个任务追加一条「做了什么 / 没做什么 / 遗留」（Phase 0 开工时创建此文件） |
| **验收后** | `PROGRESS.md` | 唯一的状态看板。Gate 全过才把该 Phase 勾掉并填日期；Gate 不过保持未勾并记原因 |

铁律：
1. **开工前先产出 `phase-N.md`**，经审阅（高错误成本阶段由 Fable 5 / Opus 审）再进入实现。
2. **状态只写 `PROGRESS.md` 一处**，不在规划书、不在 CLAUDE.md 里维护进度，避免多处同步。
3. **Gate 不过不进下一阶段**（第 7 节）——`PROGRESS.md` 上一个 Phase 未勾，下一个 Phase 的 `phase-N.md` 不许开。
4. 每个 Phase 结束跑全量测试 + `docker compose up -d` 冷启动验证后，才允许在 `PROGRESS.md` 打勾。

### Git 版本管理规范（强制）

- **`main` 是受保护分支，禁止直接在 `main` 上提交。** `main` 只接受「已通过 Gate 的阶段」合并进来，始终保持可冷启动的稳定状态。
- **每个 Phase 在自己的开发分支上工作**：开工时从最新 `main` 切出 `phase-N`（如 `phase-0`、`phase-1`），本阶段所有提交都落在该分支。
- **每完成一个阶段提交一次**：该 Phase 的 Gate 全过、测试 + Compose 冷启动验证通过、`PROGRESS.md` 打勾后，在 `phase-N` 分支做一次提交，提交信息概括本阶段成果。阶段内如有需要可多次提交，但**收尾必须有一次代表「阶段完成」的提交**。
- **合并回 main**：阶段完成提交后，将 `phase-N` 合并进 `main`（`git merge --no-ff phase-N`，保留阶段边界），`main` 上不再单独改动。
- 提交信息格式：`phase-N: <一句话成果>`（例：`phase-0: 三容器 Compose 冷启动 + Flyway V1 + 前后端骨架`）。
- 不提交：`.env`、`data/`、`target/`、`node_modules/`、`dist/`、日志（见 `.gitignore`）。密钥永远走环境变量，绝不入库。

标准节奏：`git checkout main && git pull` → `git checkout -b phase-N` → 实现 + 记 dev-log → 测试/冷启动 → 勾 `PROGRESS.md` → `git commit` →（Gate 通过）`git checkout main && git merge --no-ff phase-N`。

---

## 9. 失控防线 —— 坚决不做的黑名单

触发以下任何一项前，必须先回答「它出现在 5 分钟演示的哪一步」，答不上就**不做**：

> 多租户/组织、RBAC 权限矩阵、Kubernetes、微服务拆分、Redis、RabbitMQ、MinIO、Milvus/ES、OCR/扫描件、网页爬虫导入、rerank 自部署、模型微调/训练、多模态、自动执行写操作的 Agent、多 Agent 协作、工作流引擎、公网部署与「真实用户」叙事。

**MVP 暂缓（不是不做，但别在 MVP 碰）**：SSE 流式、docx、对话标题自动生成、hybrid 检索、rerank API、文档版本化、答案缓存。

**反套壳五件套——必须全部进 MVP**：引用溯源、无答案显式拒答、ingestion 可观测、检索调试页、评测集。少任何一件这个项目就是套壳 ChatBot。

---

## 10. Agent 能力边界（Phase 6 可选，MVP 不做）

仅当 Phase 0–5 + 7 全部完成且仍有 ≥1 周富余才做。做则铁律：
- 单一用例：「根据知识库文档生成下一步开发任务清单」。
- **3 个只读工具，白名单硬编码**：`search_knowledge_base` / `get_document_outline` / `read_chunk`。
- ≤5 步工具调用 + 30s 总超时，超限强制收束。
- **只生成计划文本，不执行任何写操作**（无写工具，故无需人工确认）。
- 每次调用写 `tool_call_logs`；独立页签 + 独立 API `/api/agent/plans`。

---

## 11. 模型分工与任务卡

| 模型 | 职责 |
|---|---|
| Fable 5 | Phase Gate 验收、契约变更裁决、范围防失控。任何「要不要加 X」先问它 |
| Opus | 跨边界与高错误成本代码：切块算法、prompt 构造、引用解析、Agent 循环、疑难 bug |
| Sonnet | 常规开发：前端页面、CRUD、联调、测试补全、文档初稿 |
| Codex | 批量模板化实现：Controller / Service / Repository / SQL |

**给执行模型的任务卡模板（必须包含五项，缺一不发）**：
```
## 任务: <一句话>
## 上下文: 规划 02 第 X 节（粘贴相关段落）
## 改动文件: <列表>
## 契约: <API/SQL/接口签名，直接粘贴>
## 验收: <可执行命令或可点击路径>
## 禁止: <本任务不许碰的范围>
```

---

## 12. 常用命令（占位，随骨架落地后更新）

```bash
# 一键起停（3 容器）
docker compose up -d
docker compose down

# 后端测试
cd backend && ./mvnw test

# 前端
cd frontend && npm run dev      # 开发
cd frontend && npm run build    # 构建

# 验证向量列与索引
psql ... -c '\d document_chunks'
```
> 这些命令在 Phase 0 骨架落地后核实并补全；当前仓库尚无代码。

---

## 13. 参考文档

- `RAG规划-01-决策结论与方向选择.md` — 为什么是这个项目、为什么是这套技术栈。
- `RAG规划-02-架构设计.md` — 模块/数据表/API/流程的**实现依据**（契约源）。
- `RAG规划-03-开发阶段与执行计划.md` — Phase 任务清单、验收 Gate、15 项避坑。
- `RAG规划-04-简历面试与演示.md` — README 结构、演示脚本、面试问答（影响功能取舍，做之前看一眼）。

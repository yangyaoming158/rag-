# Phase 3 实现规划 — Embedding 与向量检索

- 日期：2026-06-14
- 主力模型：Codex（Provider/SQL/状态机）+ Opus（检索隔离审查）+ Sonnet（调试页）
- 对应规划：`RAG规划-03` Phase 3、`RAG规划-02` 第 3.6、3.7、3.13、6、7 节

## 1. 本阶段目标（一句话）
跑通 `EMBEDDING → READY / FAILED`，把 chunk 向量写入 pgvector，并提供可验收的检索调试接口与页面。

## 2. 验收 Gate（开工即明确，照抄不改）
- [x] 10 个标准 query top1 命中预期文档 ≥8/10（记入 `docs/eval/retrieval.md`）
- [x] Mock 模式全链路可跑
- [x] KB 隔离集成测试通过（KB-A 检索不到 KB-B）

## 3. 任务卡拆分

### 任务 1：实现 EmbeddingProvider 与调用日志
- **上下文**：规划 02 第 3.6 节：`EmbeddingProvider { float[][] embed(List<String> texts); int dimensions(); String modelName(); }`；实现 OpenAI 兼容 provider 与 Mock provider；输入 ≤32 条文本/批，输出 1024 维；调用写入 `model_call_logs`。
- **改动文件**：`backend/pom.xml`、`backend/src/main/java/com/ragdocs/provider/*`、`backend/src/main/java/com/ragdocs/config/*`、`backend/src/main/java/com/ragdocs/repository/ModelCallLogRepository.java`
- **契约**：
  - 默认 `rag.ai.embedding.provider=mock`
  - Mock embedding 用内容哈希生成确定性 1024 维向量
  - 真实 provider 使用 OpenAI 兼容 `/embeddings` 响应格式
  - 维度不一致直接失败并写可读错误
- **验收**：Provider 单测覆盖确定性、维度校验和失败路径；Mock 模式不需要任何 key。
- **禁止**：不引入 LangChain4j；不使用 Spring AI 的 PgVectorStore；不在代码中写密钥。

### 任务 2：补 EMBED job 状态机与向量写库
- **上下文**：规划 02 第 6 节：CHUNK 后进入 EMBED；分批 ≤32 调 `EmbeddingProvider`；`UPDATE document_chunks.embedding`；全部成功后 `documents.status=READY`；批级指数退避，失败则整文档 FAILED；重跑前清空 chunks 保证幂等。
- **改动文件**：`backend/src/main/java/com/ragdocs/ingestion/*`、`backend/src/main/java/com/ragdocs/repository/*`、`backend/src/main/java/com/ragdocs/domain/*`
- **契约**：
  - `EMBED` job 写 `ingestion_jobs`
  - `document_chunks.embedding_model = provider.modelName()`
  - `documents.status = READY` only after all chunks have vectors
  - 任何 embedding 失败写 `documents.error_message` 与 `ingestion_jobs.error_message`
- **验收**：上传并解析后，Mock 模式自动从 `EMBEDDING` 变为 `READY`；数据库 chunks 均有 embedding 与 embedding_model。
- **禁止**：不做聊天问答；不做自动调参；不新增 MQ/Redis。

### 任务 3：实现检索 SQL、service 与调试 API
- **上下文**：规划 02 第 3.7 节核心 SQL：按 `kb_id` 过滤，JOIN `documents d ON d.id = c.document_id AND d.status = 'READY'`，按 `c.embedding <=> :queryVec` 排序，返回 top-8 与 similarity。权限过滤先校验 KB owner。
- **改动文件**：`backend/src/main/java/com/ragdocs/retrieval/*`、`backend/src/main/java/com/ragdocs/repository/*`、`backend/src/main/java/com/ragdocs/web/*`、`backend/src/main/java/com/ragdocs/web/dto/*`
- **契约**：
  - `POST /api/kbs/{kbId}/retrieval/debug`
  - 请求：`{ "query": "...", "topK": 8 }`
  - 响应：top chunks，含 filename、chunkIndex、headingPath、pageStart、charLen、similarity、contentPreview
  - 默认阈值配置 `rag.retrieval.min-similarity=0.4`
- **验收**：认证用户只能检索自己的 KB；topK 限制 1-20；空 query 返回 40001。
- **禁止**：不做 hybrid 检索、rerank、查询改写、跨库联检。

### 任务 4：补 KB 隔离集成测试与 Provider 单测
- **上下文**：规划 02 第 4 节：跨知识库污染由 `kb_id` 过滤天然杜绝，集成测试必须有一条“KB-A 的问题检索不出 KB-B 的 chunk”用例。
- **改动文件**：`backend/src/test/java/com/ragdocs/provider/*`、`backend/src/test/java/com/ragdocs/retrieval/*`
- **契约**：
  - 使用 repository/service 层测试验证 SQL 带 `kb_id`
  - Mock embedding 在测试中稳定可重复
- **验收**：`mvn test` 通过；隔离测试失败时能暴露缺失 `kb_id` 过滤。
- **禁止**：不依赖外部 embedding key；不连公网。

### 任务 5：实现前端检索调试页
- **上下文**：规划 02 第 3.13 节：检索调试页输入 query + kb，返回 top-8 chunk 与分数，不调 LLM，是反套壳核心证据。
- **改动文件**：`frontend/src/api/kbs.ts`、`frontend/src/views/KbDetailView.vue`、必要时 `frontend/src/router/index.ts`
- **契约**：
  - KB 详情页增加“检索调试”区域或抽屉
  - 显示 similarity、文档名、heading_path、页码、chunk 预览
  - 不展示聊天入口，不调用 LLM
- **验收**：`npm run build` 通过；浏览器可在 KB 详情页执行调试检索。
- **禁止**：不做聊天页、引用卡片、历史页、后台模型日志页。

### 任务 6：记录 10 条 query 标定结果
- **上下文**：规划 03 Phase 3：用 10 个真实 query 看分数分布，定初始阈值并写进配置；验收表存 `docs/eval/retrieval.md`。
- **改动文件**：`docs/eval/retrieval.md`、`README.md`、`PROGRESS.md`、`docs/dev-log.md`
- **契约**：
  - 记录 query、预期文档、top1 文档、similarity、是否命中
  - top1 命中 ≥8/10 才能勾 Gate
  - 若使用 Mock 语料验收，必须明确说明真实 provider 仍需复测
- **验收**：`docs/eval/retrieval.md` 有完整表格和阈值结论。
- **禁止**：不为了通过验收硬编码 query 或结果；不自动调参。

## 4. 本阶段红线
- 禁止：Milvus、Elasticsearch、独立向量库、Spring AI PgVectorStore、LangChain4j、hybrid 检索、rerank、自动调参、跨库联检、聊天问答。
- 暂缓（非本阶段做）：RAG prompt、引用解析、NO_ANSWER 返回、会话历史、模型调用日志后台页。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| embedding 维度与 `vector(1024)` 不一致 | 写入前校验 provider dimensions 与 chunk 向量长度；失败显式置 FAILED |
| 真实 provider 限流或无 key | 默认 Mock provider；真实 provider 批级指数退避，失败写日志 |
| 检索串库 | service 先校验 owner，SQL 强制 `WHERE c.kb_id = ?`，补隔离测试 |
| Mock embedding 语义质量不足 | Mock 只验全链路与隔离；10 query 标定需记录是否为 Mock，后续真实 key 复测 |
| 检索质量不稳 | 通过调试页记录 top-8 与分数分布，阈值先保守放在配置中 |

## 6. 完成后动作
- [x] 开工前已从最新 `main` 切出 `phase-3` 分支（禁止在 main 上提交）
- [x] 跑全量测试 + `docker compose up -d` 冷启动验证
- [x] `docs/dev-log.md` 追加各任务「做了什么/没做什么/遗留」
- [x] Gate 全过 → 在 `PROGRESS.md` 勾掉本 Phase 并填日期；不过 → 记原因，不进下一阶段
- [x] 在 `phase-3` 分支做「阶段完成」提交：`git commit -m "phase-3: Embedding 与向量检索"`
- [x] Gate 通过后合并：`git checkout main && git merge --no-ff phase-3`

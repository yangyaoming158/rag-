# agent.md — Codex 工作规范

> 本文件用于约束 Codex 在本仓库的日常执行方式。它不替代根目录
> `CLAUDE.md`，也不改写四份 `RAG规划-01~04`。若出现冲突：
> 项目事实与设计取舍以 `RAG规划-01~04` 原文为准，执行纪律以
> `CLAUDE.md` 为准，本文件只作为 Codex 的操作清单与自检规则。

## 1. 项目定位

DevDocs RAG 是一个用于简历、答辩和演示的项目文档智能问答系统：

- 后端：Java 17 + Spring Boot 3.x 单体单模块。
- 前端：Vue 3 + Vite + Pinia + Element Plus。
- 数据库：PostgreSQL 16 + pgvector，关系数据与向量同库同表。
- AI 接入：Spring AI 只使用 `ChatClient` 与 `EmbeddingModel`，所有出网调用必须经过 `ChatProvider` / `EmbeddingProvider`。
- 默认必须使用 Mock Provider，确保无 key、离线、CI 和演示兜底时仍能跑通全链路。

本项目不是商业化产品。任何新功能都必须能回答：

> 它出现在 5 分钟演示的哪一步？

答不上来就不做。

## 2. 每次开工前

Codex 每次开始工作前必须先确认上下文：

1. 读取或复核 `CLAUDE.md`、`PROGRESS.md`、相关 `RAG规划-0X-*.md`。
2. 若涉及某个 Phase，读取 `docs/plans/phase-N.md`；若不存在，则先按 `docs/plans/_template.md` 创建计划，未建计划不写代码。
3. 查看 `git status --short`，识别用户已有改动；不得回滚或覆盖非本次产生的修改。
4. 根据 `PROGRESS.md` 判断当前阶段。上一 Phase 未验收通过时，不得擅自进入下一 Phase。
5. 确认当前任务的验收 Gate、禁止事项和影响文件，再开始编辑。

当前仓库状态要点：

- 目前只有规划与规则文档，尚无业务代码。
- RAG 编码的前置条件是 mini-mall Phase 3 收尾验收通过。
- Phase 0 编码前必须先验证 DeepSeek chat key 与 embedding key；但系统默认仍走 Mock Provider。

## 3. 工作流

### Phase 工作流

每个 Phase 必须按下面顺序执行：

1. 从最新 `main` 切出 `phase-N` 分支，禁止在 `main` 上直接提交阶段开发改动。
2. 创建并填写 `docs/plans/phase-N.md`，任务卡必须包含：
   - 任务
   - 上下文
   - 改动文件
   - 契约
   - 验收
   - 禁止
3. 按任务卡实现，范围只覆盖当前任务。
4. 每完成一个任务，在 `docs/dev-log.md` 追加「做了什么 / 没做什么 / 遗留」。
5. Phase 结束后跑全量测试与 `docker compose up -d` 冷启动验证。
6. Gate 全部通过后，才更新 `PROGRESS.md` 的对应 Phase 状态与日期。
7. 阶段完成提交信息使用 `phase-N: <一句话成果>`。
8. Gate 通过后再 `git merge --no-ff phase-N` 合并回 `main`。

### 普通任务工作流

若用户请求不属于完整 Phase，例如补文档、修小问题或检查仓库：

1. 先判断是否会触碰阶段 Gate 或项目范围。
2. 优先小范围修改，不做顺手重构。
3. 修改前说明将编辑哪些文件。
4. 修改后运行与改动匹配的最小验证命令；若无法运行，明确说明原因。

## 4. 技术红线

以下选型已经锁定，Codex 不得自行替换：

- 禁止 Spring Cloud、多模块 Maven、微服务拆分。
- 禁止 LangChain4j。
- 禁止使用 Spring AI 的 `PgVectorStore`。
- 禁止 Milvus、Elasticsearch、独立向量库。
- 禁止 Redis、RabbitMQ、MinIO、Kubernetes。
- 禁止 OCR、扫描件识别、图片提取、表格结构化。
- 禁止暗色主题、i18n、移动端适配。
- 禁止 Prometheus、Grafana、链路追踪。
- 禁止自动执行写操作的 Agent、多 Agent 协作、工作流引擎。

MVP 暂缓项包括：

- SSE 流式输出
- docx
- 对话标题自动生成
- hybrid 检索
- rerank
- 文档版本化
- 答案缓存

这些不是没想到，而是当前阶段不做。

## 5. 后端实现约束

后端必须保持单体分层：

```text
com.ragdocs.{web, service, repository, domain, provider, ingestion, retrieval, rag, config}
```

强制规则：

- `web` 只做请求响应与参数校验，业务逻辑下沉到 `service`。
- 数据访问收口在 `repository`。
- 所有外部模型调用必须经过 `provider` 接口。
- ingestion 使用 Spring `@Async` + 数据库任务表，不引入 MQ。
- 检索 SQL 自己写，并收口在 `RetrievalPipeline` 之后。
- 文件落本地磁盘卷，并隐藏在 `StorageService` 接口之后。

统一契约：

- 响应体固定为 `{code, message, data}`。
- 除 `POST /api/auth/login` 外，全部接口要求 `Authorization: Bearer <JWT>`。
- JWT 自己实现；不做注册、刷新 token、找回密码。
- 只做 `owner_id` 属主隔离，不做 RBAC。

错误码不得随意新增同义码：

- `40001` 参数错误
- `40101` 未认证
- `40301` 非本人资源
- `40401` 不存在
- `40901` 重复
- `42201` 文件类型不支持或超限
- `50001` 内部错误
- `50201` LLM 调用失败
- `50202` Embedding 调用失败

## 6. 数据与迁移约束

Flyway V1 一次建全 MVP 表：

- `users`
- `knowledge_bases`
- `documents`
- `document_chunks`
- `conversations`
- `messages`
- `citations`
- `ingestion_jobs`
- `model_call_logs`

数据规则：

- 主键统一 `BIGSERIAL`。
- 时间统一 `timestamptz`。
- 外键全部显式声明。
- `document_chunks.embedding` 使用 `vector(1024)`。
- chunk metadata 使用强类型列，不用 jsonb 代替。
- `knowledge_bases` 必须有 `UNIQUE(owner_id, name)`。
- `documents` 必须有 `UNIQUE(kb_id, sha256)`。
- `document_chunks` 对 `documents` 使用 `ON DELETE CASCADE`。
- `citations.chunk_id` 使用 `ON DELETE SET NULL`，并保存原文快照。

不得新增 `audit_logs`、`permissions` 等过度设计表。`tool_call_logs` 只在 Phase 6 可选 Agent 实现时创建。

## 7. RAG 核心规格

### 切块

切块算法必须按规划实现，不得自由发挥：

- Markdown：按标题层级建树；叶子节正文少于 200 字向兄弟或父节合并；超过 900 字按段落加 120 字重叠滑窗拆分；保留 `heading_path`。
- PDF/TXT：按空行分段；顺序累积到 600-900 字封块；相邻块重叠 120 字；保留起止页码。
- 输出 `ChunkDraft{content, chunkIndex, headingPath, pageStart, pageEnd, charLen}`。
- 每块长度应落在 `[200, 1000]` 字。

切块属于高错误成本逻辑，必须有单测和人工抽查。

### 检索

检索必须强制按知识库隔离：

```sql
SELECT c.id, c.document_id, c.content, c.heading_path, c.page_start,
       1 - (c.embedding <=> :queryVec) AS similarity
FROM document_chunks c
JOIN documents d ON d.id = c.document_id AND d.status = 'READY'
WHERE c.kb_id = :kbId
ORDER BY c.embedding <=> :queryVec
LIMIT 8;
```

索引要求：

- `hnsw (embedding vector_cosine_ops)`
- `btree(kb_id)`
- `btree(document_id)`

### 问答

问答流程必须包含：

1. 校验会话归属与问题长度，问题不超过 2000 字。
2. 会话绑定单一 `kb_id`，MVP 不做跨库联检。
3. 问题 embedding 调用并写日志。
4. top-8 向量检索。
5. top1 similarity 低于阈值时直接 `NO_ANSWER`，不调 LLM。
6. 取前 6 块进入上下文，总计不超过 6000 字。
7. prompt 模板带版本号，并只带最近 3 轮历史，每条截 500 字。
8. Chat 调用 temperature 0.2，60s 超时，重试 1 次。
9. 解析 `[n]` 引用并映射 chunk；非法编号丢弃并返回 `citationWarning`。
10. 无引用且非拒答时标记 `UNGROUNDED`。
11. 写入 `messages`、`citations`、`model_call_logs`。

五层防幻觉必须保留：检索阈值短路、低 temperature、模板三规则、引用编号校验、25 题评测集。

## 8. 前端实现约束

前端必须使用 Vue 3 + Vite + Pinia + Element Plus。

页面应服务演示与验收，不做装饰性复杂设计：

- 登录页。
- KB 列表与详情页。
- 文档上传、状态、删除与重跑入口。
- 聊天页，必须展示引用卡片和回答状态。
- 历史会话页。
- ingestion 任务页。
- 模型调用日志页。
- 检索调试页。
- 展示版统计卡片。

UI 约束：

- 使用浅色主题。
- 不做移动端专项适配。
- 不做 i18n。
- 错误码通过 axios 拦截器统一 toast。
- 关键状态必须可见：`READY`、`FAILED`、`NO_ANSWER`、`UNGROUNDED`、`ERROR`。

## 9. 质量与验收

任何实现都必须能被命令或页面验证。

优先验证顺序：

1. 单元测试。
2. 集成测试。
3. 前后端构建。
4. Docker Compose 冷启动。
5. 手工验收 Gate。

每个 Phase 结束必须满足：

- 全量测试通过。
- `docker compose up -d` 冷启动通过。
- 对应 Gate 全部完成。
- `docs/dev-log.md` 已记录任务过程。
- `PROGRESS.md` 已在 Gate 通过后更新。

不能为了通过演示临时加功能或绕过失败路径。模型调用、解析、embedding 失败都必须显式返回状态、错误码和日志。

## 10. 文档写入规则

规划文档是只读事实依据：

- 不回写状态到 `RAG规划-01~04`。
- 不在 `CLAUDE.md` 里维护进度。
- 状态只写 `PROGRESS.md`。
- 阶段计划只写 `docs/plans/phase-N.md`。
- 过程记录只写 `docs/dev-log.md`。

README、演示脚本、评测集和架构图在 Phase 7 完成，不提前用临时材料替代 Gate。

## 11. Git 与文件编辑规则

- 开始前必须查看工作区状态。
- 不回滚用户已有改动。
- 不使用破坏性命令清理仓库。
- 手工编辑优先使用补丁方式，保持改动可审查。
- 不提交 `.env`、`data/`、`target/`、`node_modules/`、`dist/`、日志文件。
- 密钥永远通过环境变量注入，绝不写入仓库。

## 12. Codex 自检清单

提交最终答复前，Codex 必须自问：

- 是否读过本次任务相关的规划与规则？
- 是否确认当前 Phase 允许开工？
- 是否避免了黑名单和 MVP 暂缓项？
- 是否保持 Spring Boot 单体 + Vue 3 + PostgreSQL/pgvector 的锁定技术栈？
- 是否保留 Mock Provider 默认可跑？
- 是否没有修改无关文件？
- 是否没有覆盖用户已有改动？
- 是否运行了匹配的验证命令？
- 若无法验证，是否说明原因？
- 是否更新了应更新的 `docs/dev-log.md` 或 `PROGRESS.md`？

只有这些问题都有明确答案，才算完成一次合格工作。

# RAG 项目规划 02 — 架构设计

适用前提：已按规划 01 选定「独立项目文档 RAG 助手，Spring Boot 单体 + Spring AI + pgvector + Vue 3」。
本文档是交给 Codex / Opus / Sonnet 的实现依据；与规划 03 的 Phase 划分一一对应。

---

## 1. 系统架构说明

```
┌──────────────────────────── Docker Compose（3 容器） ───────────────────────────┐
│                                                                                  │
│  ┌─────────────┐      ┌──────────────────────────────────────┐   ┌────────────┐ │
│  │  frontend    │ HTTP │  backend (Spring Boot 3, 单体)        │   │ postgres16 │ │
│  │  Vue3+Vite   ├─────►│                                      │   │ + pgvector │ │
│  │  Nginx 静态  │      │  web 层: Auth/Kb/Doc/Chat/Admin API  │   │            │ │
│  └─────────────┘      │  service 层:                          ├──►│ 关系表 +    │ │
│                        │   ingestion: parse→clean→chunk→embed │   │ 向量列同库   │ │
│                        │   retrieval: 向量检索+过滤+阈值        │   └────────────┘ │
│                        │   rag: prompt 构造→LLM→引用解析       │                  │
│                        │  provider 层(出网):                   │   本地卷:         │
│                        │   ChatProvider / EmbeddingProvider   │   ./data/files   │
│                        │   (OpenAI 兼容 + Mock 兜底)           │   (原始文档)      │
│                        └──────────────┬───────────────────────┘                  │
└────────────────────────────────────────┼─────────────────────────────────────────┘
                                         ▼ HTTPS
                          DeepSeek API（chat） / SiliconFlow API（bge-m3 embedding）
```

要点：
- **单体分层，不分进程**。`web / service / repository / provider` 四层包结构，ingestion 用 Spring `@Async` 线程池 + 数据库任务表做异步，不引入 MQ。
- **向量与元数据同表同库**：`document_chunks.embedding vector(1024)`，检索就是一条带 `WHERE kb_id = ?` 的 SQL。
- **所有出网调用都走 Provider 接口**，默认 Mock，配 key 后切真实供应商。配置项前缀 `rag.ai.*`，环境变量注入（沿用 mini-mall 的 `AI_*` 模式）。

---

## 2. MVP 功能边界（回答 20 问）

| # | 问题 | MVP 决定 |
|---|---|---|
| 1 | 用户是谁 | 你自己 + 答辩演示者。单角色 ADMIN 种子账号，无注册 |
| 2 | 能上传什么 | 项目工程文档（README/设计文档/契约/PRD/讲义） |
| 3 | 文件类型 | `.md` `.txt` `.pdf`（≤20MB）；`.docx` 展示版顺手加（Tika 免费支持）；扫描件 PDF 明确拒绝（解析后文本 <100 字判失败） |
| 4 | 如何解析 | Apache Tika 抽纯文本；Markdown 不走 Tika，自己按行解析以保留标题层级 |
| 5 | 如何切块 | MD 按标题树切，小节合并至 ≥200 字、大节拆至 ≤900 字、重叠 120 字；PDF/TXT 按段落滑窗 600–900 字、重叠 120 字；保留 `heading_path` / `page_no` |
| 6 | embedding | `EmbeddingProvider` 接口，批量（每批 ≤32 chunk），bge-m3 1024 维 |
| 7 | 向量存哪 | pgvector，`document_chunks` 表内列，HNSW 索引 |
| 8 | 如何检索 | 余弦 top-8 → 阈值过滤 → 取前 6 进上下文；SQL 自己写 |
| 9 | prompt 构造 | 系统模板 + 编号上下文块 `[1]..[6]`（带来源标注）+ 最近 3 轮历史 + 用户问题；上下文预算 ≤6000 字 |
| 10 | 如何回答 | DeepSeek-chat，temperature 0.2，要求答案内标注 `[n]` 引用 |
| 11 | 引用展示 | 解析答案中 `[n]` → 关联 chunk → 前端可展开的引用卡片（文档名 + 标题路径 + 原文片段 + 相似度分数） |
| 12 | 无答案 | 双保险：top1 相似度 < 阈值（初始 0.4，Phase 3 用真实语料标定）直接返回固定话术不调模型；模型按模板规则也可拒答。两种都打 `NO_ANSWER` 状态 |
| 13 | 问答历史 | conversations + messages 表，列表 + 回看（含引用） |
| 14 | 用户系统 | 最小化：users 表 + 登录接口 + JWT（自己写，~半天，复用 mini-mall 经验），无注册/找回 |
| 15 | 管理员后台 | 要：ingestion 任务页 + 模型调用日志页 + 检索调试页（这是反套壳的核心证据） |
| 16 | 权限控制 | KB 带 `owner_id`，所有查询按 owner 过滤。不做 RBAC 矩阵 |
| 17 | Agent 工具调用 | MVP 不做，Phase 6 可选（见第 8 节） |
| 18 | 必做 | 登录、KB CRUD、上传、异步 ingestion 状态机、切块、embedding、向量检索、带引用问答、无答案、历史、调用日志、检索调试、评测集、Compose 一键起 |
| 19 | 暂缓 | SSE 流式（展示版强烈建议）、docx、对话标题自动生成、hybrid 关键词检索、rerank、文档版本化更新 |
| 20 | 坚决不做 | 多租户/组织、RBAC、OCR、爬虫、自动执行 Agent、微调训练、K8s、MQ、Redis、MinIO、多模态 |

分级汇总：
- **MVP 必做**：第 18 行全部。
- **展示版建议做**：SSE 流式输出、docx 支持、后台统计卡片（文档数/token 消耗/平均延迟）、admin-frontend 外链入口。
- **后续增强**：hybrid 检索（pg 全文 + 向量）、rerank（API 型 bge-reranker）、查询改写（多轮指代消解）、文档版本化、chunk 级缓存去重。
- **暂时不做**：网页 URL 导入、知识库分享。
- **不建议做**：第 20 行全部，理由统一是"演示收益为零、失控风险为正"。

---

## 3. 核心模块设计

包结构建议：`com.ragdocs.{web, service, repository, domain, provider, ingestion, retrieval, rag, config}`。

### 3.1 用户与权限模块（MVP ✅）
- 职责：登录、JWT 签发校验、当前用户上下文、KB owner 过滤。
- 输入：用户名/密码；输出：JWT、`CurrentUser(id, role)`。
- 数据结构：`users` 表；`CurrentUser` ThreadLocal/RequestAttribute。
- API：`POST /api/auth/login`。
- 风险：别把时间花在这——无注册、无刷新 token、无找回密码。
- 验收：错误密码 401；不带 token 访问业务接口 401；用户 A 的 token 查不到用户 B 的 KB（403/404）。

### 3.2 知识库管理模块（MVP ✅）
- 职责：KB 的 CRUD；删除 KB 级联删除文档/块/向量/文件。
- 数据结构：`knowledge_bases`。
- API：`GET/POST /api/kbs`，`DELETE /api/kbs/{id}`。
- 风险：级联删除遗漏文件 → 验收时检查磁盘。
- 验收：同名 KB（同 owner）拒绝 409；删除后向量计数为 0 且磁盘文件已清。

### 3.3 文档上传与存储模块（MVP ✅)
- 职责：multipart 接收、类型/大小白名单校验、sha256 去重、落盘（`./data/files/{kbId}/{docId}.{ext}`）、创建 document 记录 + ingestion job。
- 输入：file + kbId；输出：`DocumentDto{id, status=UPLOADED, jobId}`。
- 关键数据结构：`StorageService` 接口（`store/load/delete`），本地实现；`documents` 表。
- 风险：文件名注入路径 → 存储名一律用 `docId`，原始文件名只进数据库字段。
- 验收：传 `.exe` 拒绝 422；同一文件二传返回 409 + 已存在 docId；20MB+ 拒绝。

### 3.4 文档解析模块（MVP ✅）
- 职责：按类型抽取纯文本；MD 保留标题树；PDF 保留页码映射。
- 输入：storage_path + content_type；输出：`ParsedDocument{ blocks: [{text, headingPath?, pageNo?}] }`。
- 风险：PDF 乱序/乱码——抽取文本 <100 字或非法字符占比 >30% 即判 FAILED 并写明原因，不硬撑。
- 验收：3 份样例（mini-mall README.md、一份 PDF 讲义、一份 TXT）解析出的 block 数与人工抽查一致。

### 3.5 文档切块模块（MVP ✅）
- 职责：把 ParsedDocument 切成带元数据的 chunk 序列。
- 算法（必须按此实现，不要让 Codex 自由发挥）：
  - MD：按标题层级建树 → 叶子节正文 <200 字则向上合并到兄弟/父节 → >900 字按段落+120 字重叠滑窗拆分 → `heading_path` 记录如 `架构设计 > 网关路由`。
  - PDF/TXT：按空行分段 → 顺序累积到 600–900 字封块 → 相邻块重叠 120 字 → 记录起止页码。
- 输出：`List<ChunkDraft>{content, chunkIndex, headingPath, pageStart, pageEnd, charLen}`。
- 风险：chunk 粒度是检索质量的第一决定因素，Phase 3 验收必须用检索调试页人工核 10 个 query。
- 验收：对 mini-mall `architecture.md` 切块后，任意 chunk 长度在 [200, 1000] 字内，标题路径正确率人工抽查 ≥9/10。

### 3.6 Embedding 模块（MVP ✅）
- 职责：批量将 chunk 文本转向量；记录调用日志；失败重试。
- 接口：`EmbeddingProvider { float[][] embed(List<String> texts); int dimensions(); String modelName(); }`，实现：`OpenAiCompatibleEmbeddingProvider`（SiliconFlow bge-m3）、`MockEmbeddingProvider`（内容 hash 播种的确定性伪向量，维度一致）。
- 输入：≤32 条文本/批；输出：1024 维 float 数组。
- 风险：限流 429 → 指数退避重试 3 次，批间 sleep 可配。
- 验收：Mock 模式下全链路可跑；真实模式 100 chunk 入库后 `model_call_logs` 有 EMBEDDING 记录且 token 数 >0。

### 3.7 向量存储与检索模块（MVP ✅）
- 职责：向量写入 `document_chunks.embedding`；top-k 余弦检索 + kb/owner 过滤 + 阈值。
- 核心 SQL（交给 Codex 时直接给这条）：
  ```sql
  SELECT c.id, c.document_id, c.content, c.heading_path, c.page_start,
         1 - (c.embedding <=> :queryVec) AS similarity
  FROM document_chunks c
  JOIN documents d ON d.id = c.document_id AND d.status = 'READY'
  WHERE c.kb_id = :kbId
  ORDER BY c.embedding <=> :queryVec
  LIMIT 8;
  ```
- 索引：`CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);` + `btree(kb_id)`。
- 风险：HNSW + WHERE 过滤是后过滤，KB 很多时召回可能不足 k——本项目规模（<10 万 chunk）无碍；面试时主动讲这个权衡和 `ef_search` 调参即可。
- 验收：检索调试接口对 10 个标准 query 的 top1 命中预期文档 ≥8/10。

### 3.8 Rerank 模块（MVP ❌，不做，说明理由）
- 不做的理由：语料 <1 万 chunk 且为同领域工程文档，bge-m3 召回已够；rerank 增加一次外部依赖与延迟，MVP 收益无法验证。
- 替代：top-8 → 阈值 → 截前 6，按相似度排序即"简化排序"。
- 后续增强：接 SiliconFlow 的 `bge-reranker-v2-m3` API，对 top-20 重排取 top-5——只改 `retrieval` 层一个类，接口预留 `RetrievalPipeline` 即可。

### 3.9 Prompt 构造模块（MVP ✅）
- 职责：模板 + 上下文块 + 历史 + 问题 → messages 数组；控制预算。
- 模板（最小可用版，资源文件存放、带版本号，沿用 mini-mall 的版本化模板经验）：
  ```
  你是一个严谨的项目文档问答助手。仅依据下面【参考资料】回答问题。
  规则：
  1. 每个论断必须标注来源编号，如 [1][3]。
  2. 参考资料不足以回答时，回答恰好一句："根据当前知识库内容，无法回答这个问题。"，不要编造。
  3. 不要使用参考资料之外的知识回答事实性问题。

  【参考资料】
  [1] (来源: {文档名} > {标题路径}) {chunk 内容}
  [2] ...

  【历史对话】（最近 3 轮，每条截断 500 字）
  ...
  【问题】{question}
  ```
- 预算：上下文块合计 ≤6000 字，超出按相似度从低到高丢弃。
- 验收：单测断言——块编号连续、超长被截断、历史超 3 轮被丢弃。

### 3.10 LLM 回答模块（MVP ✅）
- 职责：`ChatProvider` 调用（Spring AI ChatClient 封装 DeepSeek / Mock）、超时（60s）、重试（1 次）、token 统计落 `model_call_logs`。
- 风险：供应商抖动 → 失败时 message 状态置 ERROR 并返回明确错误码，绝不静默吞掉。
- 验收：拔掉 key 演示——返回 50201 且日志可查（这是答辩上的可靠性证据）。

### 3.11 引用来源模块（MVP ✅）
- 职责：解析答案中的 `[n]` → 映射 chunk → 写 `citations` 表（含快照）→ 随响应返回。
- 边界规则：模型引用了不存在的编号 → 丢弃该编号并在响应里附 `citationWarning`；答案无任何 `[n]` 且非拒答 → 前端标注"该回答未提供依据"（黄色提示），状态记 `UNGROUNDED`。
- 验收：评测集 20 题中带引用回答 ≥16 题，引用点开能看到原文片段。

### 3.12 问答历史模块（MVP ✅）
- 职责：会话/消息持久化、列表、回看（含引用与状态）。
- 验收：刷新页面历史不丢；NO_ANSWER 与 ERROR 消息在历史中有区分样式。

### 3.13 知识库管理后台 + 可观测性模块（MVP ✅）
- 职责：ingestion 任务列表（按状态过滤、失败原因展开）、模型调用日志（类型/供应商/token/延迟/状态）、检索调试页（输入 query + kb → 返回 top-8 chunk 与分数，不调 LLM）。
- 验收：上传一个损坏 PDF → 任务页能看到 FAILED + 可读的失败原因；任一问答能在日志页找到对应 CHAT 调用记录。

### 3.14 Agent 工具调用模块（MVP ❌，Phase 6 可选）
见第 8 节。

---

## 4. 数据表设计（PostgreSQL 16 + pgvector，Flyway 管理）

> 主键统一 `BIGSERIAL`；时间统一 `timestamptz`；外键全部显式声明。

### users（MVP ✅）
| 字段 | 说明 |
|---|---|
| id, username(uniq), password_hash(BCrypt), role(`ADMIN`), created_at | 种子一条记录，Flyway V1 写入 |

### knowledge_bases（MVP ✅）
| 字段 | 说明 |
|---|---|
| id, owner_id→users, name, description, created_at, updated_at | `UNIQUE(owner_id, name)`；btree(owner_id) |

### documents（MVP ✅）
| 字段 | 说明 |
|---|---|
| id, kb_id→knowledge_bases, original_filename, content_type, file_size, storage_path, sha256, status, error_message, chunk_count, created_at, updated_at | status: `UPLOADED→PARSING→CHUNKING→EMBEDDING→READY / FAILED`；`UNIQUE(kb_id, sha256)` 防重；btree(kb_id, status) |

### document_chunks（MVP ✅，向量就在这张表）
| 字段 | 说明 |
|---|---|
| id, document_id→documents(ON DELETE CASCADE), kb_id(冗余,过滤用), chunk_index, content TEXT, heading_path, page_start, page_end, char_len, embedding vector(1024), embedding_model, created_at | 索引：hnsw(embedding vector_cosine_ops)、btree(kb_id)、btree(document_id)。**不单独建 embeddings 表**：chunk 与向量 1:1，分表只多一次 JOIN 和一处删除遗漏风险 |

回答 pgvector 四问：
- 向量字段放 `document_chunks.embedding`，与 metadata 同行。
- chunk metadata 用强类型列（heading_path/page_start...），不用 jsonb——citation 渲染需要确定结构。
- 按 `WHERE kb_id = :kbId` 过滤；kb_id 冗余进 chunk 表就是为了让这个过滤不需要 JOIN。
- 权限过滤在 service 层先校验 `kb.owner_id == currentUser.id` 再发检索 SQL；跨知识库污染由 kb_id 过滤天然杜绝，集成测试里必须有一条"KB-A 的问题检索不出 KB-B 的 chunk"用例。

### conversations（MVP ✅）
id, user_id, kb_id, title(首问前 30 字), created_at。btree(user_id, kb_id)。

### messages（MVP ✅）
id, conversation_id→conversations, role(`USER/ASSISTANT`), content, status(`OK/NO_ANSWER/UNGROUNDED/ERROR`), prompt_tokens, completion_tokens, latency_ms, created_at。btree(conversation_id)。

### citations（MVP ✅）
id, message_id→messages, chunk_id→document_chunks(**ON DELETE SET NULL**), rank, similarity, snippet(原文快照 ≤500 字), document_filename(快照)。
> 快照设计是为了：文档删除后历史问答的引用仍可读——面试可讲的小亮点。

### ingestion_jobs（MVP ✅）
id, document_id→documents, phase(`PARSE/CHUNK/EMBED`), status(`PENDING/RUNNING/SUCCEEDED/FAILED`), attempt, max_attempt(默认3), error_message, started_at, finished_at, created_at。btree(status), btree(document_id)。

### model_call_logs（MVP ✅）
id, call_type(`CHAT/EMBEDDING`), provider, model, message_id?, document_id?, prompt_tokens, completion_tokens, latency_ms, status(`OK/ERROR`), error_message, created_at。btree(call_type, created_at)。

### tool_call_logs（Phase 6 可选）
id, plan_id, tool_name, arguments jsonb, result_summary, latency_ms, status, created_at。

### 不建的表
- audit_logs：mini-mall 已展示过审计能力；本项目 model_call_logs + ingestion_jobs 已覆盖可观测诉求。后续可补。
- permissions：owner_id 就是权限模型。引入权限矩阵 = 过度设计。

---

## 5. API 设计

统一返回 `{code, message, data}`（沿用 mini-mall ApiResponse 习惯）；统一鉴权：除 login 外全部要求 `Authorization: Bearer`。
错误码族：40001 参数错误 / 40101 未认证 / 40301 非本人资源 / 40401 不存在 / 40901 重复(同名 KB、重复文件) / 42201 不支持的文件类型或超限 / 50001 内部错误 / 50201 LLM 调用失败 / 50202 Embedding 调用失败。

| # | Method & Path | 请求 | 响应 data | 关键校验 | MVP |
|---|---|---|---|---|---|
| 1 | POST `/api/auth/login` | {username,password} | {token, user} | BCrypt 校验 | ✅ |
| 2 | POST `/api/kbs` | {name, description} | KbDto | 同 owner 同名 409 | ✅ |
| 3 | GET `/api/kbs` | — | KbDto[]（含 documentCount） | 只返回本人 | ✅ |
| 4 | DELETE `/api/kbs/{id}` | — | — | owner 校验；级联删块/向量/文件 | ✅ |
| 5 | POST `/api/kbs/{kbId}/documents` | multipart file | DocumentDto{id,status,jobId} | 类型白名单/≤20MB/sha256 去重 | ✅ |
| 6 | GET `/api/kbs/{kbId}/documents?status=&page=` | — | Page\<DocumentDto\> | owner | ✅ |
| 7 | DELETE `/api/documents/{id}` | — | — | owner；级联 chunks（向量同行删除），citations 置 NULL 保快照 | ✅ |
| 8 | GET `/api/documents/{id}/ingestion` | — | JobDto[]{phase,status,attempt,error} | owner | ✅ |
| 9 | POST `/api/conversations` | {kbId} | ConversationDto | owner | ✅ |
| 10 | POST `/api/conversations/{id}/messages` | {question ≤2000 字} | {answer, status, citations[], latencyMs} | owner；question 非空；同会话串行（行锁/乐观锁防并发双发） | ✅ |
| 11 | POST `/api/conversations/{id}/messages/stream` | 同上 | SSE: token 流 + 末帧 citations | 同上 | 展示版 |
| 12 | GET `/api/conversations?kbId=` | — | ConversationDto[] | owner | ✅ |
| 13 | GET `/api/conversations/{id}` | — | 含 messages + citations | owner | ✅ |
| 14 | GET `/api/admin/ingestion-jobs?status=&page=` | — | Page\<JobDto\>（关联文档名） | ADMIN | ✅ |
| 15 | GET `/api/admin/model-calls?type=&status=&page=` | — | Page\<ModelCallDto\> | ADMIN | ✅ |
| 16 | POST `/api/admin/retrieval-debug` | {kbId, query, topK=8} | [{chunkId,similarity,headingPath,content}] | ADMIN；不调 LLM、不记会话 | ✅ |
| 17 | GET `/api/admin/stats/overview` | — | {kbCount,docCount,chunkCount,tokenSum,avgLatency} | ADMIN | 展示版 |
| 18 | POST `/api/agent/plans` | {kbId, goal} | PlanDto{steps[],toolCalls[]} | Phase 6 | 可选 |

---

## 6. RAG 入库流程（状态机）

```
上传(校验/去重/落盘, status=UPLOADED, 建 PARSE job)
  → @Async 执行 PARSE: Tika/MD 解析 → 清洗(去页眉页脚重复行/合并空白/去不可见字符)
      失败: job FAILED + document FAILED + error_message
  → CHUNK: 按 3.5 算法切块, 写 document_chunks(无向量), document.status=CHUNKING→EMBEDDING
  → EMBED: 分批(≤32)调 EmbeddingProvider → UPDATE chunks SET embedding → 全部成功后 document.status=READY, chunk_count 回填
      部分批失败: 重试(指数退避, attempt≤3) → 仍失败则整文档 FAILED(已写向量保留, 重跑前清空该文档 chunks 保证幂等)
失败重试: 手动为主(文档页"重新解析"按钮=重置状态+重建 job); 自动重试仅在 EMBED 阶段批级
错误日志: ingestion_jobs.error_message 存可读原因(前 500 字), 同时 logback 全栈
```

设计纪律：**一个文档同一时刻只有一个 RUNNING job**（documents.status 即互斥锁）；所有阶段幂等可重跑。

## 7. RAG 问答流程

```
1. 校验: 会话归属、question 长度
2. 范围: 会话绑定单一 kb_id(MVP 不做跨库联检)
3. 问题 embedding(1 次调用, 记日志)
4. 向量检索 top-8(kb_id 过滤, 见 3.7 SQL)
5. 阈值: top1 similarity < 0.4 → 短路返回 NO_ANSWER 固定话术, 不调 LLM(省成本+防幻觉第一道闸)
6. 截断: 取前 6 块, 合计 ≤6000 字, 超出丢低分块
7. 构造 prompt(3.9 模板 + 最近 3 轮历史)
8. ChatProvider 调用(60s 超时, 重试 1 次)
9. 解析引用 [n] → citations 落库(含快照)
10. 落 messages(status: OK/NO_ANSWER/UNGROUNDED/ERROR) + model_call_logs
11. 返回 {answer, citations, status}
```

专项回答：
- **防幻觉**：检索阈值短路（不给模型编造的机会）→ prompt 三规则 → 引用编号校验 → 无引用标 UNGROUNDED → 低 temperature(0.2) → 25 题评测集人工验收。五层，每层都能在面试里单独展开。
- **无答案**：第 5 步短路 + 模板第 2 条规则双保险；UI 用区别样式展示并建议"换个问法或上传相关文档"。
- **引用展示**：答案内联 `[n]` 角标，右侧/下方引用卡片（文档名、标题路径、片段、相似度），点击展开原文。
- **token 成本**：阈值短路省掉无效调用；上下文硬预算 6000 字；历史只带 3 轮且每条截 500 字；embedding 批量化；所有消耗进 model_call_logs 可统计——演示时能报出"本次答辩共消耗 X token、约 Y 元"。
- **避免上下文过长 / 全文塞模型**：永远只进 top-6 chunk；这正是 RAG 相对"长上下文塞全文"的卖点——成本 O(k) 不随库增长、来源可定位、文档更新即时生效。

---

## 8. Agent 能力边界（Phase 6 可选，MVP 不做）

**MVP 不加入 Agent 的理由**：问答闭环 + 可观测后台已足够支撑答辩与面试；Agent 在没有评测基础时上马，只会产出不可控的演示事故。

若 Phase 6 时间允许（mini-mall 与 RAG MVP 都已验收），按以下铁律实现：

| 项 | 决定 |
|---|---|
| 形态 | 单一用例：「根据该知识库的项目文档，生成下一步开发任务清单/学习计划」 |
| 工具列表（只读，白名单硬编码） | `search_knowledge_base(kbId, query, topK)`、`get_document_outline(documentId)`、`read_chunk(chunkId)` 三个，不可扩展配置 |
| 循环上限 | ≤5 次工具调用，超限强制收束输出 |
| 执行权限 | **只生成计划文本，不执行任何写操作**；无写工具，故无需人工确认环节 |
| 记录 | 每次工具调用写 tool_call_logs（参数/结果摘要/耗时）；计划页可展开"推理轨迹" |
| 防乱执行 | 只读工具 + 步数上限 + 30s 总超时 + 工具参数 Schema 校验 |
| 与普通问答区分 | 独立页签"任务规划"，独立 API `/api/agent/plans`，UI 明示"由 Agent 多步检索生成" |

---

## 9. 前端页面规划（Vue 3 + Element Plus）

| 页面 | 目标 | 核心组件/交互 | 调用 API | MVP | 简历价值 |
|---|---|---|---|---|---|
| 登录页 | 进系统 | 表单 + token 存 Pinia | 1 | ✅ | 低 |
| 知识库列表 | KB 总览/新建/删除 | 卡片栅格 + 新建弹窗 + 删除二次确认 | 2,3,4 | ✅ | 中 |
| 知识库详情(文档管理) | 上传/状态/删除 | 拖拽上传、文档表格（状态徽标 5s 轮询）、失败行展开 error、"重新解析"按钮 | 5,6,7,8 | ✅ | **高**（ingestion 可观测是反套壳证据） |
| 问答聊天页 | 核心演示页 | 消息流、引用角标 `[n]`、引用卡片抽屉（文档名/标题路径/原文/分数）、NO_ANSWER 与 UNGROUNDED 区分样式 | 9,10,(11) | ✅ | **最高** |
| 问答历史 | 回看 | 会话列表 + 详情（含引用） | 12,13 | ✅ | 中 |
| 管理后台-ingestion 日志 | 失败排查 | 状态过滤表格 + 错误详情 | 14 | ✅ | 高 |
| 管理后台-模型调用日志 | 成本/延迟 | 表格 + token 汇总行 | 15 | ✅ | 高 |
| 管理后台-检索调试 | 调 chunk 策略/答辩杀手锏 | query 输入 → top-8 chunk 列表带分数条 | 16 | ✅ | **高**（“给面试官看检索原始结果”极少有学生项目能做到） |
| 管理后台-统计卡片 | 概览 | 4 个数字卡片 | 17 | 展示版 | 中 |
| Agent 任务页 | Phase 6 | goal 输入 → 计划渲染 + 工具轨迹折叠面板 | 18 | 可选 | 高（若做） |

布局：左侧固定导航（知识库/问答/历史/后台），后台子页用 tab。**不做**暗色主题、国际化、移动端适配。

# Dev Log

## 2026-06-13 — 前置依赖预检

- 做了什么：
  - 读取 `PROGRESS.md` 与 `agent.md`，确认当前仍处于 RAG 编码前置阶段，Phase 0 尚不允许直接开工。
  - 检查当前仓库，确认尚无 `.env`、`docker-compose.yml`、后端或前端骨架。
  - 检查相邻 `mini-mall-order` 项目的 AI 配置命名，只输出变量名与非敏感状态，未输出密钥值。
  - 使用 `mini-mall-order/.env` 中已有 `AI_BASE_URL`、`AI_MODEL`、`AI_API_KEY` 做 DeepSeek `chat/completions` 最小调用预检；沙箱内因代理不可达失败，提升权限重试后通过，HTTP 200。
- 没做什么：
  - 未启动 Phase 0 编码。
  - 未创建 `docs/plans/phase-0.md`，因为 `PROGRESS.md` 中前置条件尚未验收通过。
  - 未更新 `PROGRESS.md`，因为本次不是 Phase Gate 通过。
  - 未验证 embedding provider。
- 遗留：
  - 需要提供 SiliconFlow `BAAI/bge-m3` 或 DashScope `text-embedding-v4` 的 embedding key 与 base URL 后，再执行 embedding 预检。
  - 需要确认 mini-mall Phase 3 收尾验收是否已完成；完成后才允许进入 RAG Phase 0 计划与编码。

## 2026-06-13 — mini-mall 前置验收核查与 Phase 0 计划

- 做了什么：
  - 按 mini-mall 本地工作流读取 `AGENTS.md`、`docs/dev-log.md`、`docs/phase3-acceptance.md` 与 TaskMaster 状态。
  - 确认 mini-mall `phase3-ai-inventory-assistant` tag 的 TaskMaster 进度为 14/14 done，`docs/phase3-acceptance.md` 明确 Phase 3 acceptance passed。
  - 更新 `PROGRESS.md`：前置阶段标记为 ✅，Phase 0 标记为 🔧 计划中。
  - 创建 `docs/plans/phase-0.md`，按模板拆分外部 Provider 预检、仓库骨架、Flyway V1、后端基础框架、Compose、前端登录页、README 初稿七张任务卡。
  - 由于 `.git` 在沙箱内只读，创建 `phase-0` 分支时请求提升权限；分支已创建。
- 没做什么：
  - 未写后端、前端、Compose 或 Flyway 业务代码。
  - 未把 Phase 0 Gate 标记为通过。
  - 未修改 mini-mall 项目文件；mini-mall 中已有的未提交改动保持原样。
- 遗留：
  - embedding provider 仍缺少 SiliconFlow/DashScope key 与 base URL，任务 0 的 embedding 预检未完成。
  - 拿到 embedding 配置前，不进入 Phase 0 业务代码实现。

## 2026-06-13 — embedding 预检失败：DeepSeek 不是可用 embedding provider

- 做了什么：
  - 使用用户提供的 embedding 配置做 OpenAI 兼容 `/embeddings` 最小请求，只输出状态码、响应大小与模型名，不输出 API key。
  - 测试 `https://api.deepseek.com/embeddings`，返回 HTTP 404。
  - 为排除 OpenAI v1 路径差异，测试 `https://api.deepseek.com/v1/embeddings`，返回 HTTP 404。
  - 更新 `PROGRESS.md` 的 Phase 0 备注，明确当前失败原因是 base URL / provider 不匹配。
- 没做什么：
  - 未把 API key 写入仓库或 `.env`。
  - 未启动 Phase 0 业务代码实现。
- 遗留：
  - 需要改用真正的 embedding provider：SiliconFlow `BAAI/bge-m3` 或 DashScope `text-embedding-v4` 的 OpenAI 兼容 base URL 与 key。
  - 本次对话中出现过明文 key，建议测试后在供应商控制台轮换。

## 2026-06-13 — Phase 0 骨架实现

- 做了什么：
  - 使用 `/tmp/devdocs-rag-embedding.env` 中的 SiliconFlow 配置完成 embedding 预检：`BAAI/bge-m3` 返回 HTTP 200，向量维度 1024。
  - 创建后端 Spring Boot 3 单模块骨架：`ApiResponse`、错误码、全局异常处理、自写 HMAC JWT、登录接口、受保护的 `/api/auth/me`、健康检查配置。
  - 创建 Flyway V1：`users`、`knowledge_bases`、`documents`、`document_chunks`、`conversations`、`messages`、`citations`、`ingestion_jobs`、`model_call_logs`，包含 `vector(1024)` 与 HNSW 索引，写入种子 admin 用户。
  - 创建前端 Vue 3 + Vite + Pinia + Element Plus 骨架：登录页、首页占位、axios token 拦截器、路由守卫。
  - 创建三容器目标文件：`docker-compose.yml`、后端 Dockerfile、前端 Dockerfile、Nginx 配置、`.env.example`、README 初稿。
- 没做什么：
  - 未实现 Phase 1 的 KB CRUD、文档上传、解析、向量化、检索或问答。
  - 未更新 Phase 0 Gate 为通过，因为当前 WSL 没有 `docker` 命令，无法执行 `docker compose up -d` 冷启动和 `\d document_chunks` 实机验收。
- 验证：
  - `backend`: `mvn test` 通过，1 个测试。
  - `backend`: `mvn package -DskipTests` 通过；第一次沙箱内因 `~/.m2` 只读失败，提升权限后通过。
  - `frontend`: `npm install` 通过；第一次沙箱内下载卡住，提升权限后通过。
  - `frontend`: `npm run build` 通过；启用 `skipLibCheck` 后避开 Element Plus / VueUse 依赖声明噪声；构建仅有大 chunk 警告。
  - `docker compose config` 未通过：当前 WSL 未安装 Docker / Docker Desktop WSL integration。
  - 密钥扫描：未发现 `sk-*` 或 API key 写入仓库文件。
- 遗留：
  - 在可用 Docker 环境补跑 `docker compose up -d`、登录拿 token、`psql -c '\d document_chunks'` 三项 Phase 0 Gate。
  - Gate 通过后再更新 `PROGRESS.md` 勾选 Phase 0 并提交阶段成果。

## 2026-06-13 — Phase 0 Docker Gate 验收

- 做了什么：
  - 打开 Docker Desktop WSL integration 后，重新执行 `docker compose config` 和 `docker compose up -d`。
  - 首次启动发现 frontend healthcheck 使用 `localhost` 在容器内连接被拒，但宿主访问 `http://localhost:3000` 为 HTTP 200；将 backend/frontend healthcheck 改为 `127.0.0.1` 后重建容器。
  - 补充 `backend/.dockerignore` 与 `frontend/.dockerignore`，避免后续 Docker build context 带入 `target/`、`node_modules/`、`dist/`。
  - 更新 `PROGRESS.md`：Phase 0 标记为 ✅，三项 Gate 全部勾选。
- 没做什么：
  - 未进入 Phase 1；KB CRUD、上传和文档管理仍未实现。
- 验证：
  - `docker compose ps`：postgres、backend、frontend 三容器均 healthy。
  - `curl http://localhost:8080/actuator/health` 返回 `UP`。
  - `POST /api/auth/login` 使用 admin/admin123 返回 `code=0` 且包含 JWT token。
  - `docker compose exec -T postgres psql -U rag_user -d devdocs_rag -c '\d document_chunks'` 显示 `embedding vector(1024)` 与 `idx_document_chunks_embedding_hnsw`。
- 遗留：
  - Phase 0 阶段完成提交尚未执行。
  - 下一步进入 Phase 1 前，需先创建 `docs/plans/phase-1.md`。

## 2026-06-13 — Phase 1 知识库与文档管理

- 做了什么：
  - 从 `main` 切出 `phase-1`，创建 `docs/plans/phase-1.md`，按 API 2–8 拆分 KB CRUD、上传落盘、文档列表/删除、前端管理页四张任务卡。
  - 后端新增 `KnowledgeBaseRepository`、`DocumentRepository`、`IngestionJobRepository`，实现 owner 过滤、分页文档列表、PARSE/PENDING job 查询。
  - 后端新增 `StorageService` / `LocalStorageService`，文件按 `{kbId}/{docId}.{ext}` 落到 `rag.storage.root`，删除文档或 KB 时清理本地文件。
  - 后端新增 `DocumentFileValidator`，集中处理空文件、20MB 上限、扩展名白名单与原始文件名清洗；单测覆盖 `.exe`、空文件、超限和允许的 Markdown。
  - 后端新增 `KbController` 与 `DocumentController`，开放 `POST/GET/DELETE /api/kbs`、`POST/GET /api/kbs/{kbId}/documents`、`DELETE /api/documents/{id}`、`GET /api/documents/{id}/ingestion`。
  - 前端新增 `frontend/src/api/kbs.ts`、知识库列表页和知识库详情页，支持新建、打开、上传、状态表格、任务抽屉、删除确认。
  - 更新 README 当前状态与 Phase 1 可用能力。
- 没做什么：
  - 未实现解析、切块、embedding、检索、问答、重新解析、docx、批量上传或断点续传。
  - 未引入 MinIO、Redis、MQ、独立向量库或额外服务容器。
- 验证：
  - `backend`: `mvn test` 通过，5 个测试。
  - `frontend`: `npm run build` 通过；仍有 Element Plus 大 chunk 警告。
  - `docker compose up -d --build` 通过，postgres、backend、frontend 三容器 healthy。
  - Gate 脚本：`.exe` 上传返回 HTTP 422 / code 42201；重复 `.md` 返回 HTTP 409 / code 40901；正常 `.md` 返回 `UPLOADED` 文档与 `PARSE/PENDING` job；删除 KB 后 `/app/data/files/{kbId}/{docId}.md` 不存在。
  - 文档删除脚本：`DELETE /api/documents/{id}` 返回 HTTP 200 / code 0；删除后 `/app/data/files/{kbId}/{docId}.txt` 不存在。
  - 前端容器：`http://localhost:3000/` 返回 200；`http://localhost:3000/api/auth/login` 反代返回 200。
- 遗留：
  - 前端依赖审计仍提示 3 个 high severity vulnerabilities，未在 Phase 1 范围内处理。
  - 下一步进入 Phase 2 前，需先创建 `docs/plans/phase-2.md`，并按规划由 Opus 负责切块算法实现与单测。

## 2026-06-14 — Phase 2 文档解析与切块入库

- 做了什么：
  - 从 `main` 切出 `phase-2`，创建 `docs/plans/phase-2.md`，明确解析、切块、异步 ingestion、重跑、前端状态展示任务卡。
  - 后端新增 Tika/PDFBox 依赖、`DocumentParser`、`DefaultDocumentParser`、`TextCleaner`，支持 Markdown/TXT/PDF 解析；PDF 逐页抽取并保留页码。
  - 后端新增 `DocumentChunker`，按规划参数实现 Markdown heading_path、短节合并、长段拆分、120 字重叠、TXT/PDF 段落累积。
  - 后端新增 `DocumentChunkRepository` 与 `IngestionWorker`，上传后异步执行 PARSE/CHUNK job，成功后写入 `document_chunks` 并把文档置为 `EMBEDDING`。
  - 后端新增 `POST /api/documents/{id}/reingest`，重跑前清空旧 chunks、重置状态并重新创建 PARSE job。
  - 前端详情页新增 FAILED 展开原因、任务抽屉继续展示 job 错误、重新解析按钮。
  - 针对 README 实测暴露的 chunk 尾块边界问题，补充短块重平衡、长段尾块回归单测，移除正则分段导致的栈溢出风险。
- 没做什么：
  - 未实现 embedding、向量写入、检索、问答、READY 状态、docx、OCR、表格结构化、图片提取。
  - 未引入 Redis、MQ、独立向量库或额外服务容器。
- 验证：
  - `backend`: `mvn test` 通过，14 个测试。
  - `frontend`: `npm run build` 通过；仍有 Element Plus 大 chunk 警告。
  - `docker compose build --pull=false backend && docker compose up -d backend` 通过；三容器继续运行。
  - Phase 2 Gate：mini-mall `README.md` 入库 19 chunks，长度 200-942；`docs/architecture.md` 入库 10 chunks，长度 265-900；临时 PDF 入库 10 chunks，长度 229-900。
  - `architecture.md` heading_path 抽查 10/10 可读并符合章节路径，如 `Architecture > Module Responsibilities`、`Architecture > Request Flow`。
  - 损坏 PDF 返回 `FAILED: PDF 解析失败: Error: End-of-File...`。
  - 对 README 触发 `POST /api/documents/{id}/reingest` 返回 code 0，重跑前后 chunk 数均为 19，未产生重复 chunk。
- 遗留：
  - 当前文档成功处理后停在 `EMBEDDING`，Phase 3 负责调用 embedding provider、写入 `document_chunks.embedding` 并把文档置为 `READY`。
  - 前端依赖审计与 Element Plus 大 chunk 警告仍按前述结论延后处理。

## 2026-06-14 — Phase 3 Embedding 与向量检索

- 做了什么：
  - 创建 `docs/plans/phase-3.md`，按 Provider、EMBED job、检索 SQL/API、隔离测试、前端调试页和 10 query 标定拆任务卡。
  - 新增 `EmbeddingProvider`、`MockEmbeddingProvider`、`OpenAiCompatibleEmbeddingProvider` 与 `AiProperties`；默认 Mock provider 无 key 可跑，真实 provider 走 OpenAI 兼容 `/embeddings`。
  - 新增 `ModelCallLogRepository`，embedding 批调用写入 `model_call_logs`，包含 provider、model、tokens、latency、status 和错误。
  - 扩展 `IngestionWorker`：`PARSE → CHUNK → EMBED` 串行执行，EMBED 分批最多 32 条，失败批级重试 3 次；全部 chunk 写入向量后文档置为 `READY`。
  - embedding 输入从纯正文调整为 `original_filename + heading_path + chunk content`，避免标题/文件名信号丢失。
  - 新增检索层：`RetrievalRepository` 使用 pgvector `<=>` SQL，强制 `kb_id` 过滤且只查 `READY` 文档；`RetrievalService` 先校验 owner，再调用 query embedding。
  - 新增 `POST /api/kbs/{kbId}/retrieval/debug`，返回 topK chunk、similarity、阈值命中、来源文件、heading、页码和 500 字预览。
  - 前端 KB 详情页新增“检索调试”区域，支持 query/topK 输入和 topK 结果表格展示；EMBEDDING 状态下禁用重跑。
  - 新增 provider 单测和检索隔离测试；为当前环境移除 Mockito inline 依赖，使用手写 fake repository，避免 JDK attach 限制导致测试不稳定。
  - 用 mini-mall 8 份真实 Markdown 文档跑 10 query 标定，Mock 模式 top1 命中 8/10，结果写入 `docs/eval/retrieval.md`；默认阈值标定为 `0.35`。
- 没做什么：
  - 未实现 RAG 问答、ChatProvider、prompt、引用解析、会话历史、NO_ANSWER 响应和模型调用日志后台页。
  - 未引入 Milvus/ES、Spring AI PgVectorStore、LangChain4j、hybrid 检索、rerank、Redis 或 MQ。
  - 未用真实 SiliconFlow/DashScope embedding key 复测分数分布。
- 验证：
  - `backend`: `mvn test` 通过，18 个测试。
  - `frontend`: `npm run build` 通过；仍有 Element Plus 大 chunk 警告。
  - `docker compose build --pull=false backend && docker compose up -d backend` 通过；三容器 healthy。
  - Mock 全链路：上传 mini-mall README 后状态从 `PARSING` 到 `READY`，5 个 chunk 均写入 `mock-bge-m3` embedding，检索调试接口返回 topK。
  - mini-mall eval KB：8 份文档全部 `READY`；10 query top1 命中 8/10。
- 遗留：
  - `docs/eval/retrieval.md` 当前是 Mock provider 基线；接入真实 embedding provider 后必须复测并重新标定阈值。
  - 前端依赖审计与 Element Plus 大 chunk 警告继续延后处理。

## 2026-06-15 — Phase 4 RAG 问答闭环

- 做了什么：
  - 创建 `docs/plans/phase-4.md`，按 ChatProvider、PromptBuilder、引用解析、会话/消息 API、前端聊天页和 25 题评测拆任务卡。
  - 新增 `ChatProvider`、`MockChatProvider`、`OpenAiCompatibleChatProvider`；默认 `mock-chat` 离线可跑，真实 provider 走 OpenAI 兼容 `/chat/completions`。
  - 新增版本化 prompt 模板 `prompts/rag-answer-v1.txt`，强制仅依据参考资料回答、每个论断标 `[n]`、资料不足固定拒答。
  - 新增 `PromptBuilder` 与 `CitationParser`，覆盖 top-6 context、6000 字预算、最近 3 轮历史、非法引用丢弃、无引用 `UNGROUNDED`。
  - 新增 `ConversationRepository`、`MessageRepository`、`CitationRepository`，实现会话、消息、引用快照持久化。
  - 新增 `/api/conversations` 系列 API：创建会话、列表、详情、同步提问；提问流程包含 embedding、检索阈值短路、Chat 调用、引用落库和历史回看。
  - 扩展 `model_call_logs` 写 CHAT 调用，成功/失败均记录 provider、model、token、latency 和 error。
  - 前端新增 `ChatView.vue` 与 `frontend/src/api/conversations.ts`，KB 详情页新增问答入口；聊天页展示会话列表、消息状态和引用卡片。
  - 建立 `docs/eval/questions.md`，用 mini-mall 8 份文档的 KB 跑 20 道库内题 + 5 道库外题。
- 没做什么：
  - 未实现 SSE 流式、Agent、后台模型日志页、统计卡片、rerank、hybrid 检索、跨库联检、缓存。
  - 未接真实 DeepSeek/SiliconFlow/DashScope key；当前评测是 Mock provider 离线基线。
- 验证：
  - `backend`: `mvn test` 通过，22 个测试。
  - `frontend`: `npm run build` 通过；仍有 Element Plus 大 chunk 警告。
  - `docker compose build --pull=false backend && docker compose up -d backend` 通过；仅 backend/postgres 运行，frontend 未启动。
  - API smoke：库内问题返回 `OK` 且 2 条引用；库外问题返回 `NO_ANSWER`；`GET /api/conversations/{id}` 历史回看引用完整。
  - 拔 key 演示：临时设置 `RAG_AI_CHAT_PROVIDER=openai` 且空 key，问答返回 `50201`，`model_call_logs` 记录 `CHAT|ERROR|openai|mock-chat|Chat api-key 未配置`。
  - 25 题评测：库内 19/20 有引用回答；库外 5/5 `NO_ANSWER`。
- 遗留：
  - `docs/eval/questions.md` 当前是 Mock provider 基线；真实 Chat/Embedding provider 接入后必须复测。
  - 前端依赖审计与 Element Plus 大 chunk 警告继续延后处理。

## 2026-06-15 — Phase 5 后台管理与可观测性

- 做了什么：
  - 创建 `docs/plans/phase-5.md`，按后台查询 API、后台页、失败排查/重跑链路和阶段收尾拆任务卡。
  - 新增 `AdminController` 与后台 DTO，补齐 `GET /api/admin/ingestion-jobs`、`GET /api/admin/model-calls`、`POST /api/admin/retrieval-debug`、`GET /api/admin/stats/overview`。
  - 扩展 `IngestionJobRepository` 和 `ModelCallLogRepository`，支持后台分页、状态过滤、类型过滤，并在 ingestion 日志中关联 KB 名称、文档名和文档状态。
  - 新增 `AdminStatsRepository`，从现有表聚合知识库数、文档数、chunk 数、token 总量和平均延迟；未新增监控或审计表。
  - 前端新增 `frontend/src/api/admin.ts` 与 `AdminView.vue`，`/admin` 页面包含概览、Ingestion 日志、模型调用日志、检索调试四个 tab。
  - 首页和 KB 详情页新增后台入口；后台失败日志可跳转到文档详情，详情页会按 `documentId` 定位并打开任务抽屉。
  - Ingestion 失败行支持展开错误原因，并可对非处理中状态触发重新解析。
- 没做什么：
  - 未引入 Prometheus/Grafana、链路追踪、告警、RBAC、audit_logs 或复杂图表库。
  - 未启动前端容器；本轮只重建并重启了 backend 容器用于接口验证。
  - 未进入 Phase 6，也未做 Phase 7 演示包装。
- 验证：
  - `backend`: `mvn test` 通过，22 个测试。
  - `frontend`: `npm run build` 通过；仍有 VueUse PURE 注释和 Element Plus 大 chunk 警告。
  - `git diff --check` 通过。
  - `docker compose build --pull=false backend && docker compose up -d backend` 通过；最终仅 backend/postgres 运行，frontend 未启动。
  - Admin API smoke：`/api/admin/stats/overview`、`/api/admin/ingestion-jobs`、`/api/admin/model-calls`、`/api/admin/retrieval-debug` 均返回 HTTP 200 / code 0。
  - Gate 验收：临时将 embedding provider 指向无效 endpoint，上传有效 Markdown 后文档在 `EMBED` 阶段 `FAILED`，后台日志可见错误；恢复默认 Mock 后对同一文档重跑，状态变为 `READY`，chunkCount=6，最新任务为 `EMBED/SUCCEEDED`。
- 遗留：
  - 真正损坏、不可解析或少于 100 字的文件重跑仍会失败，这是正确行为；Phase 5 Gate 使用的是“可恢复处理失败”场景，不应把不可恢复坏文件伪装成成功。
  - 展示版统计卡片只做库内聚合数字，不代表生产监控；真实 token 价格估算与图表化留到演示包装或后续扩展。
  - 前端依赖审计与大 chunk 警告继续延后处理。

## 2026-06-15 — Phase 7 交付材料初稿

- 做了什么：
  - 从 `main` 切出 `phase-7`，创建 `docs/plans/phase-7.md`，明确 README、演示脚本、语料、面试材料、架构图和验收 Gate。
  - 重写 `README.md` 为最终交付结构，包含核心特性、快速开始、架构、RAG 流程、页面、API 摘要、关键取舍、评测、演示材料和 Roadmap。
  - 新增 `docs/architecture.md`，使用 Mermaid 记录运行时架构、数据关系、ingestion 状态机、检索 SQL、问答时序和 Provider 配置。
  - 新增 `docs/demo-script.md`，整理 5 分钟演示时间线、推荐问题、失败演示方式、录屏检查表和备用方案。
  - 新增 `docs/demo-corpus.md`，定稿 mini-mall 8 份推荐演示语料和问题映射。
  - 新增 `docs/interview-qna.md`，整理简历描述、常见追问、不可夸大点和可翻代码位置。
  - 更新 `PROGRESS.md`，将 Phase 7 标为进行中，未误标 Gate 通过。
- 没做什么：
  - 未录制真实浏览器演示视频；当前 CLI 环境不能替代 GUI 录屏。
  - 未删除 Docker volume 做“干净机器”冷启动，因为这会清理本地测试数据库，需要用户明确同意。
  - 未启动后保留前端服务；完整三容器健康验证后已执行 `docker compose stop frontend`。
  - 未实现 Agent、SSE、hybrid 检索、rerank、docx 或任何演示临时功能。
- 验证：
  - `backend`: `mvn test` 通过，22 个测试。
  - `frontend`: `npm run build` 通过；仍有 VueUse PURE 注释和 Element Plus 大 chunk 警告。
  - `git diff --check` 通过。
  - `docker compose up -d` 启动三容器后，postgres、backend、frontend 均 healthy。
  - 按用户要求停止 frontend 后，最终仅 backend/postgres 运行且 healthy。
  - 提升权限下验证 `GET /actuator/health` 返回 `UP`，`POST /api/auth/login` 返回 code 0。
- 遗留：
  - Phase 7 Gate 1 仍需在干净机器或经用户同意删除 Docker volume 后执行 README 三命令冷启动。
  - Phase 7 Gate 2 仍需人工录制 ≤6 分钟浏览器演示视频。

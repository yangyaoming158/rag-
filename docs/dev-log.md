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

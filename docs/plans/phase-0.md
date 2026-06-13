# Phase 0 实现规划 — 项目初始化与基础设施

> 所有约束以根目录 `CLAUDE.md` 为准。本文件是 Phase 0 动手前的强制计划；未完成外部 embedding 预检前，不进入业务代码实现。

- 日期：2026-06-13
- 主力模型：Codex
- 对应规划：`RAG规划-03` Phase 0、`RAG规划-02` 第 1 / 4 / 5 节

## 1. 本阶段目标（一句话）

完成 DevDocs RAG 的三容器 Compose、Spring Boot 单体后端、Vue 3 前端骨架、Flyway V1 全量表结构与最小登录链路，让系统可以冷启动、登录并验证 `document_chunks.embedding vector(1024)` 与 HNSW 索引。

## 2. 验收 Gate（开工即明确，照抄不改）

- [ ] `docker compose up -d` 后 health 通过
- [ ] 登录拿到 token
- [ ] `\d document_chunks` 可见 vector 列与 hnsw 索引

## 3. 任务卡拆分

### 任务 0：外部 Provider 预检

- **上下文**：`CLAUDE.md` 第 1 节要求 Phase 0 前先用 `curl` 验证 DeepSeek chat key 与 embedding key 可用；同时系统默认必须走 Mock Provider，保证无 key 也能跑全链路。
- **改动文件**：`docs/dev-log.md`
- **契约**：真实 Provider 使用 OpenAI 兼容协议；后续配置前缀为 `rag.ai.*`，环境变量注入；Mock 为默认。
- **验收**：DeepSeek chat `chat/completions` 返回 HTTP 200；SiliconFlow `BAAI/bge-m3` 或 DashScope `text-embedding-v4` embedding 请求返回 1024 维向量。
- **禁止**：不得把任何 API key 写入仓库；不得因为缺 key 取消 Mock 兜底。

### 任务 1：仓库骨架与基础文件

- **上下文**：`RAG规划-03` Phase 0 任务 1：`backend/` Maven 单模块、`frontend/` Vite、`docker-compose.yml`、`.env.example`、`docs/`。
- **改动文件**：`backend/`、`frontend/`、`docker-compose.yml`、`.env.example`、`.gitignore`、`README.md`
- **契约**：后端单模块 Maven；前端 Vue 3 + Vite；Compose 目标容器固定为 postgres + backend + frontend/Nginx。
- **验收**：目录结构存在；`.env.example` 覆盖数据库、JWT、`rag.ai.*`、文件存储路径；`.gitignore` 排除 `.env`、`data/`、`target/`、`node_modules/`、`dist/`、日志。
- **禁止**：禁止多模块 Maven、Spring Cloud、Redis、MQ、MinIO、Kubernetes。

### 任务 2：Flyway V1 与 PostgreSQL/pgvector

- **上下文**：`RAG规划-02` 第 4 节要求 Flyway V1 一次建全 MVP 表：`users`、`knowledge_bases`、`documents`、`document_chunks`、`conversations`、`messages`、`citations`、`ingestion_jobs`、`model_call_logs`。
- **改动文件**：`backend/src/main/resources/db/migration/V1__init_schema.sql`、后端数据源配置
- **契约**：主键 `BIGSERIAL`；时间 `timestamptz`；`document_chunks.embedding vector(1024)`；`CREATE EXTENSION vector` 先于建表；HNSW 索引 `embedding vector_cosine_ops`；`citations.chunk_id ON DELETE SET NULL`；种子 admin 用户写入 V1。
- **验收**：`docker compose exec postgres psql ... -c '\\d document_chunks'` 能看到 `vector(1024)` 列与 HNSW 索引；Flyway 启动无报错。
- **禁止**：禁止单独 embeddings 表；禁止 jsonb 替代 chunk 强类型 metadata；禁止新增 `audit_logs`、`permissions`。

### 任务 3：后端基础框架、鉴权与健康检查

- **上下文**：`RAG规划-02` 第 3.1 / 5 节要求登录、JWT 签发校验、统一响应体、统一错误码、`/actuator/health`。
- **改动文件**：`backend/pom.xml`、`backend/src/main/java/com/ragdocs/**`、`backend/src/main/resources/application.yml`
- **契约**：包结构 `com.ragdocs.{web, service, repository, domain, provider, ingestion, retrieval, rag, config}`；`POST /api/auth/login` 返回 `{token,user}`；除 login 外全部要求 `Authorization: Bearer <JWT>`；响应体固定 `{code,message,data}`；错误密码返回 `40101`。
- **验收**：`curl /actuator/health` 返回 UP；使用种子 admin 登录返回 token；不带 token 访问业务占位接口返回 401。
- **禁止**：禁止注册、刷新 token、找回密码、RBAC。

### 任务 4：Compose 三容器冷启动

- **上下文**：`RAG规划-02` 第 1 节和 `RAG规划-03` Phase 0 要求 Docker Compose 共 3 容器：postgres、backend、frontend/Nginx。
- **改动文件**：`docker-compose.yml`、`frontend/nginx.conf`、后端 Dockerfile、前端 Dockerfile
- **契约**：postgres 使用 `pgvector/pgvector:pg16`；backend 依赖 postgres health；frontend 通过 Nginx 暴露静态页面并反代 `/api` 到 backend；本地文件卷预留 `./data/files`。
- **验收**：`docker compose up -d` 后三个容器健康；backend health 通过；浏览器可打开前端登录页。
- **禁止**：禁止添加第四个运行时容器；禁止引入公网部署叙事。

### 任务 5：前端骨架与登录页

- **上下文**：`RAG规划-03` Phase 0 任务 5：前端骨架、路由、Pinia、axios 拦截器、登录页。
- **改动文件**：`frontend/package.json`、`frontend/src/**`
- **契约**：Vue 3 + Vite + Pinia + Element Plus；axios 统一注入 JWT；错误码 toast；登录成功保存 token 并进入首页占位。
- **验收**：`npm run build` 通过；登录页可提交账号密码并拿 token；错误密码 toast。
- **禁止**：禁止暗色主题、i18n、移动端专项适配、营销式 landing page。

### 任务 6：README 初稿与 Phase 0 验收脚本

- **上下文**：`RAG规划-04` 第 4 节要求 README 包含定位、核心特性、架构、快速开始；Phase 0 先写最小可用启动说明。
- **改动文件**：`README.md`、`docs/dev-log.md`
- **契约**：快速开始必须包含 `.env.example` 复制、`docker compose up -d`、登录地址与 Mock 模式说明。
- **验收**：按 README 三步能完成 Phase 0 冷启动；dev-log 记录本阶段做了什么、没做什么、遗留。
- **禁止**：禁止提前声称 Phase 1+ 功能已完成；禁止虚构真实用户、商业部署、训练大模型。

## 4. 本阶段红线

- 禁止：Spring Cloud、多模块 Maven、微服务拆分、Redis、RabbitMQ、MinIO、Milvus/ES、Kubernetes。
- 禁止：使用 Spring AI `PgVectorStore`。
- 禁止：把密钥、真实 token、`.env` 写入仓库。
- 暂缓：SSE、docx、hybrid 检索、rerank、Agent、文档版本化。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| embedding key 未提供，外部依赖预检不完整 | 不写业务代码；保留 Mock 默认；拿到 SiliconFlow/DashScope 配置后先补 `curl` 预检 |
| pgvector 扩展与 Flyway 建表顺序错误 | V1 第一句创建扩展，再建 `vector(1024)` 列与 HNSW 索引 |
| Docker Compose health 顺序不稳定 | postgres 增加 healthcheck；backend 依赖 postgres healthy；frontend 依赖 backend health |
| JWT / 响应体与后续 API 契约漂移 | Phase 0 先固定 `ApiResponse`、错误码、认证过滤器和全局异常处理 |
| 前端过度设计拖慢骨架 | Phase 0 只做登录与首页占位，不做 KB/文档/聊天页面 |

## 6. 完成后动作

- [x] 开工前已从最新 `main` 切出 `phase-0` 分支（禁止在 main 上提交）
- [x] 跑全量测试 + `docker compose up -d` 冷启动验证
- [x] `docs/dev-log.md` 追加各任务「做了什么/没做什么/遗留」
- [x] Gate 全过 → 在 `PROGRESS.md` 勾掉本 Phase 并填日期；不过 → 记原因，不进下一阶段
- [ ] 在 `phase-0` 分支做「阶段完成」提交：`git commit -m "phase-0: <成果>"`
- [ ] Gate 通过后合并：`git checkout main && git merge --no-ff phase-0`

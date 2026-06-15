# DevDocs RAG — 项目文档智能问答系统

DevDocs RAG 是一个 Spring Boot 单体 + Vue 3 的项目文档智能问答系统。目标链路是上传工程文档，解析切块，向量化入 pgvector，再提供带引用溯源的检索问答。

当前状态：Phase 4 已完成。已包含三容器 Compose 目标、后端登录/健康检查、Flyway V1 表结构、知识库 CRUD、文档上传落盘、文档列表/状态/删除、异步解析、标题感知切块入库、Mock/OpenAI 兼容 embedding、pgvector 检索、检索调试页、RAG 问答、引用溯源、无答案短路、历史回看、失败原因展示、重新解析，以及前端知识库列表、详情页和聊天页。

## 快速开始

```bash
cp .env.example .env
docker compose up -d
```

打开：

```text
http://localhost:3000
```

默认账号：

```text
username: admin
password: admin123
```

Mock Provider 是默认配置，不配置任何模型 key 也必须能启动。真实模型后续通过 `.env` 中的 `RAG_AI_*` 变量启用，密钥不得入库。

## Phase 0 验收

```bash
curl http://localhost:8080/actuator/health

curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

docker compose exec postgres psql -U rag_user -d devdocs_rag -c '\d document_chunks'
```

`document_chunks` 必须包含 `embedding vector(1024)`，并且有 HNSW 索引。

## Phase 1 可用能力

- 登录后在首页创建、打开、删除知识库。
- 在知识库详情页上传 `.md`、`.markdown`、`.txt`、`.pdf` 文件。
- 上传成功后文档状态为 `UPLOADED`，并创建 `PARSE/PENDING` ingestion job。
- 同一知识库重复上传相同文件返回 409。
- 上传 `.exe` 或超限文件返回 422。
- 删除文档或知识库会清理本地磁盘文件。

## Phase 2 可用能力

- 上传后异步执行 `PARSE` 和 `CHUNK` ingestion job。
- Markdown/TXT/PDF 会解析为文本并写入 `document_chunks`。
- 解析切块失败会进入 `FAILED`，并在文档详情与任务抽屉展示可读原因。
- chunk 长度要求为 200-1000 字，Markdown 保留 `heading_path`，PDF 保留页码范围。
- 文档详情页可触发重新解析；重跑前会清空旧 chunks，避免重复入库。

## Phase 3 可用能力

- `EMBED` ingestion job 会批量生成 1024 维向量并写入 `document_chunks.embedding`。
- 默认 `mock-bge-m3` provider 无需 key；也预留 OpenAI 兼容 `/embeddings` provider。
- 所有 embedding 调用写入 `model_call_logs`，失败会置文档 `FAILED`。
- 文档全部向量化后状态变为 `READY`。
- `POST /api/kbs/{kbId}/retrieval/debug` 按 KB 隔离返回 topK chunk、分数、来源与预览。
- 知识库详情页提供“检索调试”区域；当前不调用 LLM。
- Mock 模式下 mini-mall 8 份文档 10 query top1 命中 8/10，结果见 `docs/eval/retrieval.md`。

## Phase 4 可用能力

- `POST /api/conversations` 创建会话，`POST /api/conversations/{id}/messages` 同步问答。
- RAG 流程包含问题 embedding、top-8 检索、阈值短路、top-6 prompt、ChatProvider、引用解析、citations 落库。
- top1 分数低于阈值时直接返回 `NO_ANSWER`，不调用 LLM。
- 默认 `mock-chat` 可离线回答并生成合法 `[n]` 引用；真实 ChatProvider 走 OpenAI 兼容 `/chat/completions`。
- Chat 调用写入 `model_call_logs`；缺 key 等失败返回 `50201` 并写 ERROR 日志。
- 历史回看保留引用快照；文档删除后引用仍可读。
- 前端新增知识库问答页，展示消息状态和引用卡片。
- Mock 模式 25 题评测：库内 19/20 有引用回答，库外 5/5 `NO_ANSWER`，结果见 `docs/eval/questions.md`。

## 架构

```text
frontend(Vue 3 + Nginx) -> backend(Spring Boot 3) -> postgres16 + pgvector
                                      |
                                      +-> local files ./data/files
```

运行时容器固定为 3 个：

- `postgres`: `pgvector/pgvector:pg16`
- `backend`: Java 17 + Spring Boot 3
- `frontend`: Nginx 静态站点 + `/api` 反代

## 开发命令

后端：

```bash
cd backend
mvn test
mvn package
```

前端：

```bash
cd frontend
npm install
npm run dev
npm run build
```

## 范围边界

本项目按 `CLAUDE.md` 与 `RAG规划-01~04` 执行。当前阶段禁止引入 Spring Cloud、多模块 Maven、Redis、MQ、MinIO、Milvus/ES、Kubernetes、Agent、SSE、rerank、hybrid 检索等非本阶段内容。

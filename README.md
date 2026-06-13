# DevDocs RAG — 项目文档智能问答系统

DevDocs RAG 是一个 Spring Boot 单体 + Vue 3 的项目文档智能问答系统。目标链路是上传工程文档，解析切块，向量化入 pgvector，再提供带引用溯源的检索问答。

当前状态：Phase 1 已完成。已包含三容器 Compose 目标、后端登录/健康检查、Flyway V1 表结构、知识库 CRUD、文档上传落盘、文档列表/状态/删除、前端知识库列表与详情页。解析、切块、embedding、检索和问答将在后续 Phase 实现。

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

# DevDocs RAG — 项目文档智能问答系统

DevDocs RAG 是一个 Spring Boot 单体 + Vue 3 的项目文档智能问答系统。目标链路是上传工程文档，解析切块，向量化入 pgvector，再提供带引用溯源的检索问答。

当前状态：Phase 0 骨架。已包含三容器 Compose 目标、后端登录/健康检查、Flyway V1 表结构、前端登录页。知识库、文档上传、切块、embedding、检索和问答将在后续 Phase 实现。

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

本项目按 `CLAUDE.md` 与 `RAG规划-01~04` 执行。Phase 0 禁止引入 Spring Cloud、多模块 Maven、Redis、MQ、MinIO、Milvus/ES、Kubernetes、Agent、SSE、rerank、hybrid 检索等非本阶段内容。

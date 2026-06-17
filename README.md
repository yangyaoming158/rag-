# DevDocs RAG — 项目文档智能问答系统

[![CI](https://github.com/yangyaoming158/rag-/actions/workflows/ci.yml/badge.svg)](https://github.com/yangyaoming158/rag-/actions/workflows/ci.yml)

DevDocs RAG 是一个 Spring Boot 单体 + Vue 3 的项目文档智能问答系统。它面向工程文档场景，支持上传 Markdown/TXT/PDF，异步解析切块，写入 PostgreSQL + pgvector，并提供带引用溯源的 RAG 问答。

当前状态：MVP 已完成 Phase 0-5 与 Phase 7 交付包装，Phase 6 Agent 默认跳过。默认使用 Mock Provider，不配置任何模型 key 也能跑通完整链路；正式录屏作为 Post-MVP 延后项。

## 项目截图

| 知识库首页 | READY 文档列表 |
|---|---|
| ![知识库首页](docs/images/01-home.png) | ![READY 文档列表](docs/images/02-documents-ready.png) |

| 引用问答 | 库外拒答 |
|---|---|
| ![带引用的问答结果](docs/images/03-chat-with-citations.png) | ![NO_ANSWER 拒答结果](docs/images/04-no-answer.png) |

| 模型调用日志 | Ingestion 日志 |
|---|---|
| ![模型调用日志](docs/images/05-model-call-logs.png) | ![Ingestion 日志](docs/images/06-ingestion-jobs.png) |

| 检索调试 |
|---|
| ![检索调试结果](docs/images/07-retrieval-debug.png) |

演示视频：暂缓录制，后续补充正式链接；当前展示以截图、真实 Provider 评测和本地可复现启动流程为准。

## 核心特性

- 文档入库：上传、落盘、解析、切块、embedding、状态机、失败原因、幂等重跑。
- 向量检索：pgvector HNSW 索引，检索 SQL 强制 `kb_id` 隔离，检索调试页展示 topK 原始 chunk。
- RAG 问答：同步问答、会话历史、引用编号、引用快照、无答案短路、`UNGROUNDED` 标记。
- 可观测：`ingestion_jobs`、`model_call_logs`、后台统计卡片、失败任务定位与重跑。
- 离线兜底：`mock-bge-m3` 与 `mock-chat` 默认启用，便于 CI、本地答辩和无网络演示。
- 一键部署：Docker Compose 固定 3 容器：postgres、backend、frontend。

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

三命令冷启动验收：

```bash
docker compose ps
curl http://localhost:8080/actuator/health
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

Mock Provider 是默认配置。真实模型可通过 `.env` 中的 `RAG_AI_CHAT_*` 与 `RAG_AI_EMBEDDING_*` 切换到 OpenAI 兼容接口，密钥不得入库。

## 架构

```mermaid
flowchart LR
    browser[Browser / Vue 3] --> nginx[frontend / Nginx]
    nginx --> backend[Spring Boot 3 单体]
    backend --> pg[(PostgreSQL 16 + pgvector)]
    backend --> files[(./data/files)]
    backend --> providers[Mock Provider 或 OpenAI 兼容模型 API]

    subgraph backend_modules[backend modules]
        web[web controllers]
        service[service]
        ingestion[async ingestion]
        retrieval[retrieval SQL]
        rag[RAG prompt + citations]
        repository[repository]
    end

    backend --> backend_modules
```

详细架构、状态机和问答时序见 [docs/architecture.md](docs/architecture.md)。

## RAG 流程

入库流程：

```text
UPLOAD -> PARSE -> CHUNK -> EMBED -> READY
                              |
                              +-> FAILED(error_message)
```

问答流程：

1. 校验会话归属与问题长度。
2. 对问题做 embedding，并写入 `model_call_logs`。
3. 用 pgvector 检索同一知识库 top-8 chunks。
4. top1 similarity 低于阈值时直接返回 `NO_ANSWER`，不调用 LLM。
5. top-6 chunks 进入 prompt，ChatProvider 生成答案。
6. 后端解析 `[n]` 引用，非法编号丢弃；无合法引用且非拒答时标记 `UNGROUNDED`。
7. 消息与 citations 落库，历史回看保留引用快照。

## 页面

- 登录页：JWT 登录，错误统一 toast。
- 知识库首页：创建、打开、删除知识库。
- 文档详情页：上传、状态表格、失败原因、任务抽屉、重新解析、检索调试入口。
- 问答页：会话列表、消息流、回答状态、引用卡片。
- 管理后台：统计概览、Ingestion 日志、模型调用日志、后台检索调试。

## API 摘要

| 能力 | API |
|---|---|
| 登录 | `POST /api/auth/login` |
| 知识库 | `GET/POST /api/kbs`、`DELETE /api/kbs/{id}` |
| 文档 | `POST /api/kbs/{kbId}/documents`、`GET /api/kbs/{kbId}/documents`、`DELETE /api/documents/{id}` |
| 入库任务 | `GET /api/documents/{id}/ingestion`、`POST /api/documents/{id}/reingest` |
| 检索调试 | `POST /api/kbs/{kbId}/retrieval/debug`、`POST /api/admin/retrieval-debug` |
| 问答 | `POST /api/conversations`、`GET /api/conversations`、`GET /api/conversations/{id}`、`POST /api/conversations/{id}/messages` |
| 后台 | `GET /api/admin/ingestion-jobs`、`GET /api/admin/model-calls`、`GET /api/admin/stats/overview` |

统一响应体：

```json
{"code":0,"message":"ok","data":{}}
```

错误码族：`40001` 参数错误、`40101` 未认证、`40301` 非本人资源、`40401` 不存在、`40901` 重复、`42201` 文件类型不支持或超限、`50001` 内部错误、`50201` LLM 调用失败、`50202` Embedding 调用失败。

## 关键设计取舍

| 取舍 | 结论 |
|---|---|
| pgvector vs Milvus | MVP 规模下向量和业务元数据同库，事务一致、部署简单；检索层已收口，后续可替换 |
| 单体 vs 微服务 | Spring Boot 单体足够表达 RAG 工程闭环，避免引入网关、注册中心、分布式事务 |
| Mock Provider | 默认 Mock 保证无 key、离线、CI 和演示兜底；真实 Provider 通过配置切换 |
| 删除+重传 | MVP 不做文档版本化；删除文档级联删除 chunks，citations 保留快照 |
| 不做 rerank/hybrid | 当前用评测集标定 topK 与阈值，先保证可解释与可观测 |

## 评测

- 检索评测见 [docs/eval/retrieval.md](docs/eval/retrieval.md)：Mock 模式 mini-mall 8 份文档，10 query top1 命中 8/10，默认阈值 `0.35`。
- 问答评测见 [docs/eval/questions.md](docs/eval/questions.md)：20 道库内题 19/20 有引用回答，5 道库外题 5/5 `NO_ANSWER`。
- 真实 Provider 评测见 [docs/eval/real-provider-baseline.md](docs/eval/real-provider-baseline.md)：DeepSeek `deepseek-v4-flash` + SiliconFlow `BAAI/bge-m3`，8 份文档 131 chunks，10 query top1 命中 9/10、top3 命中 10/10；20 道库内题 20/20 有引用回答，5 道库外题 5/5 `NO_ANSWER`。
- 以上 Mock Provider 离线基线只证明工程链路可跑，不能代表真实模型效果。

## 演示材料

- 快速体验：[docs/demo-quickstart.md](docs/demo-quickstart.md)
- 演示脚本：[docs/demo-script.md](docs/demo-script.md)
- 演示语料：[docs/demo-corpus.md](docs/demo-corpus.md)
- 面试问答：[docs/interview-qna.md](docs/interview-qna.md)
- 阶段记录：[docs/dev-log.md](docs/dev-log.md)

## 与 mini-mall 的关系

DevDocs RAG 与 mini-mall 是独立项目，零代码耦合。mini-mall 的工程文档作为种子语料，用来演示非结构化工程知识问答。两个项目覆盖不同 AI 落地形态：mini-mall 是结构化数据上的库存建议，DevDocs RAG 是非结构化文档检索问答。

## 开发命令

CI 使用 GitHub Actions 分别执行后端测试和前端构建，配置见 [.github/workflows/ci.yml](.github/workflows/ci.yml)。本地等价命令如下。

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

Compose：

```bash
docker compose up -d
docker compose ps
docker compose down
```

## Roadmap

- Phase 6 可选：受限只读 Agent 任务规划。
- 后续增强：真实 Provider 复测、hybrid 检索、rerank、文档版本化、SSE 流式、docx。
- 明确不在 MVP 内：RBAC、多租户、Kubernetes、Redis、MQ、MinIO、Milvus/ES、OCR、模型训练、多 Agent、自动执行写操作。

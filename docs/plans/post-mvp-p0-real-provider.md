# Post-MVP P0 实施计划 — 真实 Provider 评测

- 日期：2026-06-17
- 对应规划：`docs/plans/post-mvp-optimization.md` P0 / 1. 真实 Provider 评测

## 1. 目标

用真实 OpenAI-compatible Chat / Embedding Provider 跑通入库、检索、问答、拒答和调用日志，形成可放入 README 的真实模型基线，避免项目只停留在 Mock Provider 演示。

## 2. 当前状态

初始检查结果：

```text
RAG_AI_CHAT_PROVIDER=mock
RAG_AI_CHAT_BASE_URL 未配置
RAG_AI_CHAT_API_KEY 未配置
RAG_AI_CHAT_MODEL=mock-chat

RAG_AI_EMBEDDING_PROVIDER=mock
RAG_AI_EMBEDDING_BASE_URL 未配置
RAG_AI_EMBEDDING_API_KEY 未配置
RAG_AI_EMBEDDING_MODEL=mock-bge-m3
RAG_AI_EMBEDDING_DIMENSIONS=1024
```

初始结论：

- 当前无法执行真实 Provider 评测。
- 可以先完成评测计划、评测集、记录模板和 README 入口。
- 拿到真实 provider 配置后，再执行完整评测并更新 `docs/eval/real-provider-baseline.md`。

2026-06-17 更新：

- 用户已补齐真实 Provider 配置。
- Chat 使用 DeepSeek OpenAI-compatible API，model 为 `deepseek-v4-flash`。
- Embedding 使用 SiliconFlow OpenAI-compatible API，model 为 `BAAI/bge-m3`，维度 1024。
- 真实 Provider 基线评测已完成，结果见 `docs/eval/real-provider-baseline.md`。

## 3. 前置条件

真实 embedding provider 必须满足：

- 提供 OpenAI-compatible `/embeddings` 接口。
- 返回 1024 维向量，或先修改数据库 schema 与 `IngestionWorker` 的维度校验。
- 支持批量 input。

真实 chat provider 必须满足：

- 提供 OpenAI-compatible `/chat/completions` 接口。
- 返回 `choices[0].message.content`。
- 最好返回 usage；如果不返回，当前代码会使用字符数估算 token。

`.env` 配置示例：

```text
RAG_AI_CHAT_PROVIDER=openai
RAG_AI_CHAT_BASE_URL=https://example.com/v1
RAG_AI_CHAT_API_KEY=<never-commit-real-key>
RAG_AI_CHAT_MODEL=<chat-model>

RAG_AI_EMBEDDING_PROVIDER=openai
RAG_AI_EMBEDDING_BASE_URL=https://example.com/v1
RAG_AI_EMBEDDING_API_KEY=<never-commit-real-key>
RAG_AI_EMBEDDING_MODEL=<1024-dim-embedding-model>
RAG_AI_EMBEDDING_DIMENSIONS=1024
RAG_AI_EMBEDDING_MAX_BATCH_SIZE=32
```

## 4. 执行步骤

### 任务 1：真实配置预检

- **上下文**：当前代码已有 `OpenAiCompatibleEmbeddingProvider` 和 `OpenAiCompatibleChatProvider`，重点是配置和运行证据。
- **改动文件**：不提交 `.env`。
- **契约**：密钥只放本地 `.env` 或运行环境变量，不能写入仓库。
- **验收**：
  - `docker compose up -d --build`
  - `GET /actuator/health` 返回 `UP`
  - 错误配置时后台 `model_call_logs` 能记录 `ERROR`
- **禁止**：禁止把 API key、完整 Authorization header、供应商控制台截图提交到仓库。

### 任务 2：真实 embedding 入库

- **上下文**：用同一批 mini-mall 工程文档建立真实 provider 知识库。
- **改动文件**：`docs/eval/real-provider-baseline.md`
- **契约**：文档状态必须最终为 `READY`，`model_call_logs` 必须记录真实 provider 和 model。
- **验收**：
  - 至少 1 份文档真实 embedding 成功入库。
  - 建议 8 份评测语料全部 `READY`。
  - `document_chunks.embedding_model` 为真实 embedding model。
- **禁止**：禁止将 Mock 模式结果写成真实 Provider 结果。

### 任务 3：真实检索评测

- **上下文**：沿用 `docs/eval/retrieval.md` 的 10 条 query，重新记录真实 embedding 分数分布。
- **改动文件**：`docs/eval/real-provider-baseline.md`
- **契约**：记录 top1 文档、top1 分数、是否命中、threshold 建议。
- **验收**：
  - 10 条检索 query 全部记录。
  - 给出 top1 命中率和阈值调整建议。
- **禁止**：禁止沿用 Mock 阈值结论作为真实模型结论。

### 任务 4：真实问答评测

- **上下文**：沿用 `docs/eval/questions.md` 的 20 道库内题和 5 道库外题。
- **改动文件**：`docs/eval/real-provider-baseline.md`
- **契约**：每题记录状态、引用数、是否通过、失败原因。
- **验收**：
  - 20 道库内题全部记录。
  - 5 道库外题全部记录。
  - 至少 3 个失败或边界案例。
- **禁止**：禁止只记录成功样本。

### 任务 5：README 摘要

- **上下文**：README 当前只记录 Mock 基线。
- **改动文件**：`README.md`
- **契约**：真实 Provider 摘要必须明确日期、模型、语料、核心指标和边界。
- **验收**：
  - README 链接到 `docs/eval/real-provider-baseline.md`。
  - README 明确 Mock 指标不代表真实模型效果。
- **禁止**：真实评测未完成前，不写具体成功率。

## 5. 评测语料

沿用当前 Mock 基线使用的 8 份 mini-mall 工程文档：

```text
/home/oslab/projects/mini-mall-order/README.md
/home/oslab/projects/mini-mall-order/docs/architecture.md
/home/oslab/projects/mini-mall-order/docs/phase3-ai-inventory-contract.md
/home/oslab/projects/mini-mall-order/docs/phase3-acceptance.md
/home/oslab/projects/mini-mall-order/docs/phase2-admin-api-contract.md
/home/oslab/projects/mini-mall-order/docs/phase2-5-ai-inventory-contract.md
/home/oslab/projects/mini-mall-order/admin-frontend/README.md
/home/oslab/projects/mini-mall-order/frontend/README.md
```

## 6. 风险与预案

| 风险 | 预案 |
|---|---|
| embedding 模型不是 1024 维 | 优先换 1024 维模型；否则新建迁移调整 `vector(N)` 与校验逻辑 |
| provider 不完全 OpenAI-compatible | 先用 curl 预检响应结构；必要时扩展 provider 解析 |
| 真实模型延迟高或限流 | 降低批大小，记录平均延迟与失败原因 |
| 分数分布和 Mock 差异大 | 重新标定 `RAG_RETRIEVAL_MIN_SIMILARITY`，不要复用 Mock 阈值 |
| 模型回答无引用 | 记录为失败样本，分析 prompt 或引用格式问题 |

## 7. 完成标准

- [x] `docs/eval/real-provider-baseline.md` 不再是待执行模板，而是包含真实评测结果。
- [x] 至少 1 次真实 embedding 入库成功。
- [x] 至少 1 次真实 chat 问答成功。
- [x] 25 道真实问答评测完整记录。
- [x] README 有真实 Provider 评测摘要。
- [x] 至少 3 个失败或边界案例被记录。

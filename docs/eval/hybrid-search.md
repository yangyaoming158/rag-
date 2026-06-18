# Hybrid Search Evaluation

日期：2026-06-18

状态：已完成第一版

## 1. 配置

| 项 | 值 |
|---|---|
| 知识库 | `real-provider-baseline-20260617` |
| KB id | `4` |
| 文档数量 | 8 |
| chunk 数量 | 131 |
| Embedding model | `BAAI/bge-m3` |
| Chat model | `deepseek-v4-flash` |
| 检索模式 | vector topN + keyword topN + RRF |
| keyword MVP | PostgreSQL ILIKE |
| RRF K | 60 |
| topK | 5 |

说明：

- 本轮只评测检索接口，不重新跑 25 道 Chat 问答。
- 每条 query 会触发一次真实 embedding 调用。
- `similarity` 表示 vector score；`keywordScore` 表示关键词命中比例；`finalScore` 表示 RRF 融合分数。

## 2. 工程术语 Query 结果

| # | Query | 预期文档 | Top1 文档 | Vector | Keyword | Final | Top1 命中 | Top3 命中 |
|---:|---|---|---|---:|---:|---:|---|---|
| 1 | `/api/admin/inventories 路径是什么？` | phase2-admin-api-contract.md | phase2-admin-api-contract.md | 0.684 | 0.500 | 0.032 | Y | Y |
| 2 | `X-User-Role 如何处理？` | phase2-admin-api-contract.md | README.md | 0.524 | 0.500 | 0.032 | N | Y |
| 3 | `PENDING_REVIEW 状态是什么意思？` | phase3-ai-inventory-contract.md | phase3-acceptance.md | 0.575 | 0.500 | 0.032 | N | Y |
| 4 | `common-auth 模块负责什么？` | architecture.md | architecture.md | 0.643 | 0.500 | 0.033 | Y | Y |
| 5 | `AI_PROVIDER DEEPSEEK MINIMAX 如何配置？` | README.md | README.md | 0.644 | 0.750 | 0.033 | Y | Y |
| 6 | `/api/admin/ai-suggestions/{suggestionNo}/convert-inbound-draft 做什么？` | phase2-5-ai-inventory-contract.md | phase3-ai-inventory-contract.md | 0.709 | 0.750 | 0.033 | N | Y |
| 7 | `AiModelOutputValidator SQL internal provider secrets 会拒绝什么？` | phase3-ai-inventory-contract.md | README.md | 0.611 | 0.667 | 0.033 | N | Y |
| 8 | `Phase3AiLoopAcceptanceTest fullAiLoop 验收了什么？` | phase3-acceptance.md | phase3-acceptance.md | 0.683 | 0.667 | 0.033 | Y | Y |
| 9 | `pressure/mini-mall-gateway.js thresholds 是什么？` | architecture.md | architecture.md | 0.588 | 0.667 | 0.033 | Y | Y |
| 10 | `admin frontend port 5174 是哪个页面？` | README.md | README.md | 0.571 | 0.800 | 0.033 | Y | Y |

## 3. 汇总

| 指标 | 结果 |
|---|---:|
| Top1 命中率 | 6/10 |
| Top3 命中率 | 10/10 |
| 检索接口返回 vector score | 10/10 |
| 检索接口返回 keyword score | 10/10 |
| 检索接口返回 final score | 10/10 |

## 4. 观察

Hybrid Search 明显改善了工程术语的可解释性：

- `/api/admin/inventories` 能直接命中接口表格 chunk。
- `AI_PROVIDER DEEPSEEK MINIMAX` 能通过 keyword score 把配置表排在前面。
- `pressure/mini-mall-gateway.js` 能命中包含脚本名的 observability/pressure testing chunk。

Top1 仍不是所有 query 都命中人工指定文档，主要原因是多份文档都包含相同工程术语：

- `X-User-Role` 同时出现在 README、Phase 2、Phase 2.5 和 Phase 3 合同中。
- `PENDING_REVIEW` 同时出现在 contract 和 acceptance 证据中。
- `convert-inbound-draft` 同时出现在 Phase 2.5 和 Phase 3 AI contract 中。

因此当前 P1 更适合以 Top3 和可解释分数作为验收口径，而不是只追求 Top1。

## 5. 边界

当前 keyword search 是 ILIKE MVP，存在以下边界：

- 没有倒排索引，大规模数据下需要迁移到 PostgreSQL full-text search + GIN 或 Elasticsearch。
- keyword score 只按命中 token 比例计算，没有区分 filename、heading、content 的权重。
- query 中中文自然语言部分会被当成 token，但真实命中主要来自工程术语。
- RAG 拒答阈值仍基于 vector similarity，keyword-only 命中不会绕过 `NO_ANSWER` 短路。

## 6. 结论

P1 Hybrid Search MVP 已完成：

```text
vector search topN
+
keyword search topN
+
RRF fusion
```

检索调试页已能展示：

```text
vector score
keyword score
final score
```

下一步如果继续增强检索质量，应优先做：

```text
PostgreSQL full-text search + GIN index
heading/path 权重
vector rank / keyword rank 可视化
keyword-only grounded 阈值
```

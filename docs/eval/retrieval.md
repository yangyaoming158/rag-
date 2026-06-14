# Phase 3 Retrieval Evaluation

- 日期：2026-06-14
- 知识库：`phase3-eval-20260614-v2`（本地 KB id `12`）
- Provider：`mock-bge-m3`
- 向量输入：`original_filename + heading_path + chunk content`
- 检索参数：topK=5，SQL 按 `kb_id` 过滤，仅检索 `READY` 文档
- 标定结论：Mock 模式有效命中最低分约 `0.356`，本阶段默认阈值设为 `0.35`。接入真实 embedding provider 后必须复测并更新本文件。

## 语料

| 文件 |
|---|
| `/home/oslab/projects/mini-mall-order/README.md` |
| `/home/oslab/projects/mini-mall-order/docs/architecture.md` |
| `/home/oslab/projects/mini-mall-order/docs/phase3-ai-inventory-contract.md` |
| `/home/oslab/projects/mini-mall-order/docs/phase3-acceptance.md` |
| `/home/oslab/projects/mini-mall-order/docs/phase2-admin-api-contract.md` |
| `/home/oslab/projects/mini-mall-order/docs/phase2-5-ai-inventory-contract.md` |
| `/home/oslab/projects/mini-mall-order/admin-frontend/README.md` |
| `/home/oslab/projects/mini-mall-order/frontend/README.md` |

## 10 Query 结果

| # | Query | 预期文档 | Top1 文档 | Top1 分数 | 命中 |
|---:|---|---|---|---:|---|
| 1 | gateway JWT trusted headers CORS rate limiting | architecture.md | architecture.md | 0.377 | Y |
| 2 | RabbitMQ payment success event order notification queues | architecture.md | architecture.md | 0.695 | Y |
| 3 | admin console ai-inventory page offers Q&A low-stock daily operations report | README.md | README.md | 0.616 | Y |
| 4 | full flow analysis suggestion draft admin confirm screenshots evidence | phase3-acceptance.md | phase3-acceptance.md | 0.448 | Y |
| 5 | X-User-Role trusted headers admin route ownership | phase2-admin-api-contract.md | phase2-admin-api-contract.md | 0.356 | Y |
| 6 | Phase 2.5 AI operation suggestion persistence inbound draft | phase2-5-ai-inventory-contract.md | phase2-5-ai-inventory-contract.md | 0.635 | Y |
| 7 | new P0 endpoints api admin ai gateway-routed enforce ADMIN | phase3-ai-inventory-contract.md | phase3-ai-inventory-contract.md | 0.398 | Y |
| 8 | model provider configuration environment variables AI_PROVIDER DEEPSEEK MINIMAX | README.md | phase3-ai-inventory-contract.md | 0.749 | N |
| 9 | observability Prometheus Grafana pressure testing k6 thresholds | architecture.md | architecture.md | 0.598 | Y |
| 10 | Project Status all six delivery phases Modules common-core common-auth api-gateway | README.md | phase3-acceptance.md | 0.274 | N |

命中率：8/10。

## 说明

- 第 8 条的 top1 文档包含 provider 配置边界，语义上可解释，但与预期 README 不一致，按未命中记录。
- 第 10 条被 Phase 3 acceptance 的命令与模块证据干扰，按未命中记录。
- 当前结果只证明 Mock 模式全链路、隔离和调试能力可用；真实向量模型的分数分布需要重新标定。

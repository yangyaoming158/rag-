# Phase 4 RAG QA Evaluation

- 日期：2026-06-15
- 知识库：`phase3-eval-20260614-v2`（本地 KB id `12`）
- Provider：`mock-bge-m3` + `mock-chat`
- 检索阈值：`0.35`
- 验收结论：库内题 19/20 有引用回答；库外题 5/5 走 `NO_ANSWER`。
- 说明：这是 Mock provider 离线基线。接入真实 Chat/Embedding provider 后必须复测本文件。

## 结果表

| # | 类型 | Question | 预期 | 实际状态 | 引用数 | 通过 |
|---:|---|---|---|---|---:|---|
| 1 | IN | gateway JWT trusted headers CORS rate limiting 是怎么处理的？ | OK_WITH_CITATION | OK | 2 | Y |
| 2 | IN | RabbitMQ payment success event order notification queues 是什么流程？ | OK_WITH_CITATION | OK | 2 | Y |
| 3 | IN | api-gateway 作为唯一 browser-facing entrypoint 有哪些职责？ | OK_WITH_CITATION | OK | 2 | Y |
| 4 | IN | common-core 和 common-auth 模块分别负责什么？ | OK_WITH_CITATION | NO_ANSWER | 0 | N |
| 5 | IN | inventory-service 在 MiniMall 中负责哪些功能？ | OK_WITH_CITATION | OK | 2 | Y |
| 6 | IN | AI inventory assistant 的 admin console 页面提供哪些能力？ | OK_WITH_CITATION | OK | 2 | Y |
| 7 | IN | AI suggestion 的 PENDING_REVIEW 状态是什么意思？ | OK_WITH_CITATION | OK | 2 | Y |
| 8 | IN | convert inbound draft 到 admin confirm 的库存变更边界是什么？ | OK_WITH_CITATION | OK | 2 | Y |
| 9 | IN | Phase 3 AI contract 禁止 AI 做哪些事情？ | OK_WITH_CITATION | OK | 2 | Y |
| 10 | IN | X-User-Role trusted headers 在 admin API 中如何处理？ | OK_WITH_CITATION | OK | 2 | Y |
| 11 | IN | Phase 2 admin API 的 gateway admin route ownership 如何定义？ | OK_WITH_CITATION | OK | 2 | Y |
| 12 | IN | Phase 2.5 stock mutation foundation 包含哪些 inbound order 和 ai suggestion 能力？ | OK_WITH_CITATION | OK | 2 | Y |
| 13 | IN | Phase 3 acceptance 的 full flow 验收证据是什么？ | OK_WITH_CITATION | OK | 2 | Y |
| 14 | IN | Mock provider 在 AI inventory assistant 中有什么作用？ | OK_WITH_CITATION | OK | 2 | Y |
| 15 | IN | Prometheus Grafana observability 暴露了哪些 actuator endpoint？ | OK_WITH_CITATION | OK | 2 | Y |
| 16 | IN | pressure testing 使用哪个 k6 脚本和 gateway entrypoint？ | OK_WITH_CITATION | OK | 2 | Y |
| 17 | IN | Docker Compose 本地运行 MiniMall 需要哪些基础设施？ | OK_WITH_CITATION | OK | 2 | Y |
| 18 | IN | customer frontend 和 admin frontend 的 dev server 端口是什么？ | OK_WITH_CITATION | OK | 2 | Y |
| 19 | IN | model output validation 会拒绝哪些不安全输出？ | OK_WITH_CITATION | OK | 2 | Y |
| 20 | IN | canonical API prefixes 中 admin inventory and AI 的路径有哪些？ | OK_WITH_CITATION | OK | 2 | Y |
| 21 | OUT | 火星基地种植土豆需要哪些设备？ | NO_ANSWER | NO_ANSWER | 0 | Y |
| 22 | OUT | 清朝康熙皇帝的继位过程是什么？ | NO_ANSWER | NO_ANSWER | 0 | Y |
| 23 | OUT | 如何训练一只海豚完成杂技表演？ | NO_ANSWER | NO_ANSWER | 0 | Y |
| 24 | OUT | 量子色动力学中的渐近自由如何推导？ | NO_ANSWER | NO_ANSWER | 0 | Y |
| 25 | OUT | 法式可颂面团应该如何开酥？ | NO_ANSWER | NO_ANSWER | 0 | Y |

## Gate

- 库内题有依据回答：19/20，满足 ≥16/20。
- 库外题显式拒答：5/5。
- 历史回看引用：API smoke 中 `GET /api/conversations/{id}` 返回 assistant message citations。
- 拔 key 演示：临时设置 `RAG_AI_CHAT_PROVIDER=openai` 且空 `RAG_AI_CHAT_API_KEY`，问答返回 `50201`，`model_call_logs` 记录 `CHAT|ERROR|openai|mock-chat|Chat api-key 未配置`。

# Real Provider Baseline

状态：已完成

日期：2026-06-17

本文件记录 DevDocs RAG 在真实 OpenAI-compatible Chat / Embedding Provider 下的基线评测结果。

注意：本次评测使用新建知识库 `real-provider-baseline-20260617`，没有重跑或覆盖既有 Mock 知识库。Mock KB 与 Real KB 必须分开，因为 Mock embedding 和真实 embedding 不在同一个向量空间，混用会导致相似度失真。

## 1. 本次评测配置

| 项 | 值 |
|---|---|
| 评测日期 | 2026-06-17 |
| 知识库名称 | `real-provider-baseline-20260617` |
| KB id | `4` |
| 文档数量 | 8 |
| chunk 数量 | 131 |
| Chat provider | OpenAI-compatible / DeepSeek |
| Chat model | `deepseek-v4-flash` |
| Embedding provider | OpenAI-compatible / SiliconFlow |
| Embedding model | `BAAI/bge-m3` |
| Embedding dimensions | 1024 |
| Retrieval debug topK | 5 |
| RAG retrieval topK | 8 |
| Prompt context chunks | 6 |
| Similarity threshold | 0.35 |
| Temperature | 0.2 |

## 2. 语料

| # | 文件 | 状态 | chunk 数 |
|---:|---|---|---:|
| 1 | `/home/oslab/projects/mini-mall-order/README.md` | READY | 19 |
| 2 | `/home/oslab/projects/mini-mall-order/docs/architecture.md` | READY | 10 |
| 3 | `/home/oslab/projects/mini-mall-order/docs/phase3-ai-inventory-contract.md` | READY | 27 |
| 4 | `/home/oslab/projects/mini-mall-order/docs/phase3-acceptance.md` | READY | 10 |
| 5 | `/home/oslab/projects/mini-mall-order/docs/phase2-admin-api-contract.md` | READY | 28 |
| 6 | `/home/oslab/projects/mini-mall-order/docs/phase2-5-ai-inventory-contract.md` | READY | 25 |
| 7 | `/home/oslab/projects/mini-mall-order/admin-frontend/README.md` | READY | 3 |
| 8 | `/home/oslab/projects/mini-mall-order/frontend/README.md` | READY | 9 |

## 3. 真实 embedding 入库检查

| 检查项 | 结果 | 备注 |
|---|---|---|
| 至少 1 份文档 READY | 通过 | 8/8 READY |
| 全部评测文档 READY | 通过 | 总计 131 chunks |
| `model_call_logs` 有真实 EMBEDDING/OK | 通过 | provider=`openai`，model=`BAAI/bge-m3` |
| `document_chunks.embedding_model` 为真实模型 | 通过 | 131/131 chunks 为 `BAAI/bge-m3` |
| embedding 维度为 1024 | 通过 | 与 `vector(1024)` schema 匹配 |

## 4. 检索评测

10 条 query 沿用 `docs/eval/retrieval.md`。本次真实 provider 评测使用检索调试接口 `topK=5`。

| # | Query | 预期文档 | Top1 文档 | Top1 分数 | Top1 命中 | Top3 命中 |
|---:|---|---|---|---:|---|---|
| 1 | gateway JWT trusted headers CORS rate limiting | architecture.md | phase2-admin-api-contract.md | 0.584 | N | Y |
| 2 | RabbitMQ payment success event order notification queues | architecture.md | architecture.md | 0.732 | Y | Y |
| 3 | admin console ai-inventory page offers Q&A low-stock daily operations report | README.md | README.md | 0.755 | Y | Y |
| 4 | full flow analysis suggestion draft admin confirm screenshots evidence | phase3-acceptance.md | phase3-acceptance.md | 0.628 | Y | Y |
| 5 | X-User-Role trusted headers admin route ownership | phase2-admin-api-contract.md | phase2-admin-api-contract.md | 0.671 | Y | Y |
| 6 | Phase 2.5 AI operation suggestion persistence inbound draft | phase2-5-ai-inventory-contract.md | phase2-5-ai-inventory-contract.md | 0.700 | Y | Y |
| 7 | new P0 endpoints api admin ai gateway-routed enforce ADMIN | phase3-ai-inventory-contract.md | phase3-ai-inventory-contract.md | 0.664 | Y | Y |
| 8 | model provider configuration environment variables AI_PROVIDER DEEPSEEK MINIMAX | README.md | README.md | 0.688 | Y | Y |
| 9 | observability Prometheus Grafana pressure testing k6 thresholds | architecture.md | architecture.md | 0.534 | Y | Y |
| 10 | Project Status all six delivery phases Modules common-core common-auth api-gateway | README.md | README.md | 0.655 | Y | Y |

检索汇总：

| 指标 | 结果 |
|---|---:|
| Top1 命中率 | 9/10 |
| Top3 命中率 | 10/10 |
| Top1 命中最低分 | 0.534 |
| 库外问题最高误召回分 | 0.384 |
| 当前 threshold | 0.35 |
| 建议后续 threshold | 可评估上调到 0.45 |

说明：

- 第 1 条 top1 命中 `phase2-admin-api-contract.md`，但 `architecture.md` 位于 top2。该问题跨越 gateway、CORS、trusted headers、rate limiting，多份文档都覆盖相关内容，因此按 top1 未命中、top3 命中记录。
- 当前 0.35 threshold 能保证库内问题通过，但两个库外问题被检索误召回并进入 Chat，由 Chat 根据 prompt 完成拒答。后续可用 0.45 做二次标定，以减少无效 Chat 调用。

## 5. 问答评测

每题独立创建一个会话，避免历史上下文影响评测结果。

| # | 类型 | Question | 预期 | 实际状态 | 引用数 | 延迟 ms | 通过 |
|---:|---|---|---|---|---:|---:|---|
| 1 | IN | gateway JWT trusted headers CORS rate limiting 是怎么处理的？ | OK_WITH_CITATION | OK | 4 | 3804 | Y |
| 2 | IN | RabbitMQ payment success event order notification queues 是什么流程？ | OK_WITH_CITATION | OK | 1 | 3265 | Y |
| 3 | IN | api-gateway 作为唯一 browser-facing entrypoint 有哪些职责？ | OK_WITH_CITATION | OK | 4 | 7636 | Y |
| 4 | IN | common-core 和 common-auth 模块分别负责什么？ | OK_WITH_CITATION | OK | 2 | 2517 | Y |
| 5 | IN | inventory-service 在 MiniMall 中负责哪些功能？ | OK_WITH_CITATION | OK | 2 | 2160 | Y |
| 6 | IN | AI inventory assistant 的 admin console 页面提供哪些能力？ | OK_WITH_CITATION | OK | 1 | 1737 | Y |
| 7 | IN | AI suggestion 的 PENDING_REVIEW 状态是什么意思？ | OK_WITH_CITATION | OK | 1 | 1766 | Y |
| 8 | IN | convert inbound draft 到 admin confirm 的库存变更边界是什么？ | OK_WITH_CITATION | OK | 4 | 2626 | Y |
| 9 | IN | Phase 3 AI contract 禁止 AI 做哪些事情？ | OK_WITH_CITATION | OK | 2 | 5226 | Y |
| 10 | IN | X-User-Role trusted headers 在 admin API 中如何处理？ | OK_WITH_CITATION | OK | 4 | 2887 | Y |
| 11 | IN | Phase 2 admin API 的 gateway admin route ownership 如何定义？ | OK_WITH_CITATION | OK | 1 | 2287 | Y |
| 12 | IN | Phase 2.5 stock mutation foundation 包含哪些 inbound order 和 ai suggestion 能力？ | OK_WITH_CITATION | OK | 2 | 3810 | Y |
| 13 | IN | Phase 3 acceptance 的 full flow 验收证据是什么？ | OK_WITH_CITATION | OK | 1 | 2663 | Y |
| 14 | IN | Mock provider 在 AI inventory assistant 中有什么作用？ | OK_WITH_CITATION | OK | 5 | 4190 | Y |
| 15 | IN | Prometheus Grafana observability 暴露了哪些 actuator endpoint？ | OK_WITH_CITATION | OK | 1 | 1814 | Y |
| 16 | IN | pressure testing 使用哪个 k6 脚本和 gateway entrypoint？ | OK_WITH_CITATION | OK | 1 | 1544 | Y |
| 17 | IN | Docker Compose 本地运行 MiniMall 需要哪些基础设施？ | OK_WITH_CITATION | OK | 2 | 3042 | Y |
| 18 | IN | customer frontend 和 admin frontend 的 dev server 端口是什么？ | OK_WITH_CITATION | OK | 1 | 2095 | Y |
| 19 | IN | model output validation 会拒绝哪些不安全输出？ | OK_WITH_CITATION | OK | 3 | 2892 | Y |
| 20 | IN | canonical API prefixes 中 admin inventory and AI 的路径有哪些？ | OK_WITH_CITATION | OK | 1 | 1881 | Y |
| 21 | OUT | 火星基地种植土豆需要哪些设备？ | NO_ANSWER | NO_ANSWER | 0 | 2048 | Y |
| 22 | OUT | 清朝康熙皇帝的继位过程是什么？ | NO_ANSWER | NO_ANSWER | 0 | 222 | Y |
| 23 | OUT | 如何训练一只海豚完成杂技表演？ | NO_ANSWER | NO_ANSWER | 0 | 125 | Y |
| 24 | OUT | 量子色动力学中的渐近自由如何推导？ | NO_ANSWER | NO_ANSWER | 0 | 129 | Y |
| 25 | OUT | 法式可颂面团应该如何开酥？ | NO_ANSWER | NO_ANSWER | 0 | 1804 | Y |

问答汇总：

| 指标 | 结果 |
|---|---:|
| 库内回答率 | 20/20 |
| 库外拒答率 | 5/5 |
| 引用有效率 | 20/20 |
| `UNGROUNDED` 数量 | 0 |
| citation warning 数量 | 0 |
| 平均端到端响应延迟 | 2567 ms |
| Chat 调用次数 | 22 |
| Chat prompt tokens | 27708 |
| Chat completion tokens | 5220 |
| Chat 平均调用延迟 | 2755 ms |
| Embedding 调用次数 | 43 |
| Embedding prompt tokens | 22887 |
| Embedding 平均调用延迟 | 169 ms |

说明：

- 20 道库内题全部返回 `OK` 且有合法引用。
- 5 道库外题全部返回 `NO_ANSWER`。
- 其中 3 道库外题由 similarity threshold 短路拒答；2 道库外题因 top1 similarity 高于 0.35 进入 Chat，最终由 prompt 约束拒答。

## 6. 失败与边界案例

### 案例 1：Top1 命中相邻文档，但 Top3 有预期文档

```text
问题：
gateway JWT trusted headers CORS rate limiting

预期结果：
Top1 命中 architecture.md。

实际结果：
Top1 为 phase2-admin-api-contract.md，分数 0.584；architecture.md 位于 top2，分数 0.573。

失败类型：
embedding 召回边界 / 工程主题跨文档重叠。

原因分析：
该 query 同时包含 gateway、trusted headers、CORS、rate limiting。architecture.md 和 phase2-admin-api-contract.md 都覆盖相关内容，真实 embedding 更偏向具体 API contract。

修复方案：
后续引入 hybrid search 和 RRF，保留向量召回的同时用关键词命中接口契约、模块名和配置项。

复测结果：
当前 Top3 命中，问答仍能返回带引用答案。

剩余风险：
如果只看 Top1 命中率，会低估可用检索结果；如果 prompt 上下文预算过小，可能丢掉 top2 的关键文档。
```

### 案例 2：库外问题被 threshold 误召回，最终由 Chat 拒答

```text
问题：
火星基地种植土豆需要哪些设备？

预期结果：
NO_ANSWER。

实际结果：
检索 top1 为 README.md，分数 0.384，高于 0.35 threshold；进入 Chat 后返回 NO_ANSWER。

失败类型：
similarity threshold 问题 / 应该拒答但没有被检索短路。

原因分析：
问题中的“设备”与 README 中本地运行 prerequisites、Docker、Node.js 等词形成弱相关，导致相似度超过当前阈值。

修复方案：
真实 provider 下可评估将 threshold 从 0.35 上调到 0.45；也可以增加库外拒答评测集覆盖更多生活常识类问题。

复测结果：
最终回答正确拒答，但多消耗了一次 Chat 调用。

剩余风险：
如果模型未严格遵守 prompt，该类 false-positive retrieval 可能导致编造。
```

### 案例 3：库外问题被支付页面内容误召回，最终由 Chat 拒答

```text
问题：
法式可颂面团应该如何开酥？

预期结果：
NO_ANSWER。

实际结果：
检索 top1 为 README.md，分数 0.368，高于 0.35 threshold；进入 Chat 后返回 NO_ANSWER。

失败类型：
similarity threshold 问题 / 拒答边界。

原因分析：
问题中的流程性表达与文档中的支付页面、模拟支付说明产生弱语义相似，超过当前阈值。

修复方案：
提高 threshold 并扩大库外问题集；后续在检索 debug 页展示是否触发 Chat，有助于定位无效模型调用。

复测结果：
最终回答正确拒答，但多消耗了一次 Chat 调用。

剩余风险：
真实模型 provider 更强时不代表一定更保守，仍需保留 NO_ANSWER prompt 和后端 `UNGROUNDED` 标记。
```

## 7. 结论

真实 Provider 基线已经完成。相比 Mock 基线，本次真实模型在库内问答和库外拒答上表现更好：

- 检索 Top1：9/10。
- 检索 Top3：10/10。
- 库内问答：20/20 有引用回答。
- 库外问答：5/5 `NO_ANSWER`。

但也暴露出两个生产化边界：

- 当前 0.35 threshold 对真实 embedding 偏低，两个库外问题会进入 Chat 后再拒答。
- 工程文档中主题重叠明显，纯向量 Top1 不一定命中人工预期文档，后续仍需要 hybrid search 来增强接口路径、配置项和模块名召回。

# Failure Cases

状态：已完成第一版

日期：2026-06-18

本文档记录 DevDocs RAG 在 Mock 基线、真实 Provider 基线和本地验证过程中暴露出的失败或边界案例。目标不是只展示成功率，而是说明系统失败时如何定位、如何修复、修复后仍有什么风险。

参考材料：

- `docs/eval/retrieval.md`
- `docs/eval/questions.md`
- `docs/eval/real-provider-baseline.md`
- `model_call_logs`
- `citations`

## 案例 1：纯向量检索 Top1 命中相邻文档

问题：

```text
gateway JWT trusted headers CORS rate limiting
```

预期结果：

```text
Top1 命中 architecture.md。
```

实际结果：

```text
真实 Provider 检索中，Top1 为 phase2-admin-api-contract.md，分数 0.584；
architecture.md 位于 Top2，分数 0.573。
Top1 未命中，Top3 命中。
```

失败类型：

```text
embedding 召回问题
关键词召回问题
工程主题跨文档重叠
```

原因分析：

这个 query 同时包含 gateway、trusted headers、CORS、rate limiting。`architecture.md` 从系统架构角度描述这些能力，`phase2-admin-api-contract.md` 从接口契约角度描述这些能力。纯向量检索会把更具体的 API contract 排到第一，但人工预期是架构文档。

修复方案：

后续 P1-2 引入 Hybrid Search：

```text
vector search topK
+
keyword search topK
+
RRF 融合排序
```

检索调试页需要展示 vector score、keyword score 和 final score，便于判断命中是语义相似还是关键词命中。

复测结果：

当前真实 Provider 基线中 Top3 已命中，RAG 问答仍可返回带引用答案。

剩余风险：

如果 prompt context 预算减少，Top2 之后的关键文档可能被截掉。只看 Top1 命中率会低估检索链路的可用性，但只看 TopK 命中又可能掩盖排序质量问题。

## 案例 2：引用快照定位不够精确

问题：

```text
canonical API prefixes 中 admin inventory and AI 的路径有哪些？
```

预期结果：

```text
回答带引用，并且引用能明确定位到具体文档和章节。
```

实际结果：

```text
真实 Provider 问答返回 OK，引用有效。
但评测 KB 中存在 3 个同名 README.md：

- document id 8: README.md
- document id 14: README.md
- document id 15: README.md

citations 表只持久化 document_filename、snippet、similarity 和 chunk_id。
heading_path 没有写入 citation snapshot，而是历史查询时通过 chunk_id join document_chunks 得到。
如果后续文档删除导致 chunk_id 被置空，历史引用仍有 snippet，但会丢失 heading_path。
```

失败类型：

```text
引用不相关
引用定位不精确
引用快照元数据不足
```

原因分析：

当前引用不是“完全错误”，但定位能力不足。`document_filename=README.md` 在多文档语料里不唯一；`heading_path` 依赖 live chunk join，不是 citation 自身的不可变快照。MVP 能展示引用卡片，但生产化审计需要更稳定的 source identity。

修复方案：

后续可以扩展 citation snapshot：

```text
citations
- document_id 或 document_version_id
- document_filename
- document_original_path
- heading_path_snapshot
- page_start
- page_end
- chunk_index
- snippet
```

前端引用卡片优先展示：

```text
filename + heading_path_snapshot + chunk_index/page
```

复测结果：

当前尚未实现该修复。现阶段通过 `chunk_id` join 仍能在文档未删除时显示 heading_path。

剩余风险：

删除文档或未来引入文档版本化后，旧 citation 的定位信息可能降级。引用编号合法只能证明答案引用了某个检索上下文，不等于事实必然正确，也不等于引用定位足够精确。

## 案例 3：库外问题被 threshold 误召回

问题：

```text
火星基地种植土豆需要哪些设备？
```

预期结果：

```text
NO_ANSWER，并且最好由检索阈值直接短路，不调用 Chat 模型。
```

实际结果：

```text
真实 Provider 检索 top1 为 README.md，分数 0.384，高于当前 threshold 0.35。
系统进入 Chat 调用后，由 prompt 约束返回 NO_ANSWER。
```

失败类型：

```text
similarity threshold 问题
应该拒答但没有被检索短路
无效 Chat 调用
```

原因分析：

问题中的“设备”与 README 中运行环境、Docker、Node.js、依赖准备等内容产生弱语义相似，导致相似度超过 0.35。真实 embedding 的分数分布和 Mock 不同，Mock 阈值不能直接代表真实模型阈值。

修复方案：

短期方案：

```text
用真实 Provider 评测集重新标定 threshold，可评估从 0.35 上调到 0.45。
```

中期方案：

```text
扩大库外问题集，并在检索调试页展示是否触发 Chat。
```

复测结果：

最终回答正确拒答，库外拒答率仍为 5/5，但多消耗了一次 Chat 调用。

剩余风险：

如果 Chat 模型没有严格遵守 prompt，false-positive retrieval 可能导致编造答案。后续仍需保留后端 `NO_ANSWER`、`UNGROUNDED` 和引用合法性检查。

## 案例 4：真实 Chat Provider 模型名配置错误

问题：

```text
RAG_AI_CHAT_MODEL 初始填写为 deepseek v4。
```

预期结果：

```text
真实 Chat Provider 可正常完成问答，并在 model_call_logs 中记录 OK。
```

实际结果：

```text
OpenAI-compatible Chat 调用返回 HTTP 400。
model_call_logs 记录：

call_type=CHAT
provider=openai
model=deepseek v4
status=ERROR
error_message=Chat 调用失败: HTTP 400
```

失败类型：

```text
provider 配置失败
真实模型接入失败
模型调用错误可观测性
```

原因分析：

Provider 的 model id 必须使用供应商接口支持的精确字符串。自然语言式模型名 `deepseek v4` 不是可调用的 model id，因此 OpenAI-compatible API 返回 400。

修复方案：

将模型名调整为供应商支持的精确 id：

```text
RAG_AI_CHAT_MODEL=deepseek-v4-flash
```

并保留 README 和 `.env.example` 中“密钥不入库、model id 需以供应商文档为准”的说明。

复测结果：

修正后真实 Provider 基线完成：

```text
20/20 库内问题 OK 且有引用
5/5 库外问题 NO_ANSWER
CHAT OK 调用正常写入 model_call_logs
```

剩余风险：

不同 OpenAI-compatible Provider 对 base URL、model id、usage 字段和错误结构兼容程度不同。后续可增加 provider smoke test 文档或启动前配置校验，减少运行中才暴露配置错误。

## 汇总

| 案例 | 类型 | 当前状态 | 后续动作 |
|---|---|---|---|
| Top1 命中相邻文档 | 检索失败 | Top3 可用 | P1-2 Hybrid Search |
| 引用快照定位不够精确 | 引用问题 | 未修复 | 后续 citation snapshot 扩展 |
| 库外问题误召回 | 拒答边界 | 最终拒答正确 | 真实 Provider threshold 复标定 |
| Chat model id 配置错误 | Provider 接入 | 已修复 | 增加 smoke test 和配置说明 |

P1-1 结论：

```text
项目已经具备定位失败原因的基础观测能力：retrieval debug、model_call_logs、citations、ingestion_jobs。
下一步最值得做的是 Hybrid Search，因为它直接对应检索失败和工程术语召回不稳的问题。
```

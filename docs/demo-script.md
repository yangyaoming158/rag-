# DevDocs RAG Demo Script

目标：5 分钟内演示引用溯源、无答案处理、全链路可观测和检索调试。默认使用 Mock Provider，避免现场网络或供应商波动。

## 准备

```bash
cp .env.example .env
docker compose up -d
```

打开 `http://localhost:3000`，使用 `admin/admin123` 登录。

建议提前准备一个知识库：`mini-mall 项目文档`。语料见 [demo-corpus.md](demo-corpus.md)，8-10 份文档全部处理到 `READY`。

## 时间线

| 时间 | 画面 | 讲法 |
|---:|---|---|
| 0:00-0:20 | 登录后首页 | “这是给工程文档做的 RAG 问答系统，重点看引用溯源、拒答和可观测。” |
| 0:20-0:50 | 打开 mini-mall 知识库 | 展示文档列表，状态都是 `READY`，说明已完成解析、切块、向量化。 |
| 0:50-1:20 | 上传一份新 Markdown | 看状态从 `UPLOADED/PARSING/CHUNKING/EMBEDDING` 到 `READY`。 |
| 1:20-2:10 | 问答页提库内问题 | 问：“gateway JWT trusted headers CORS rate limiting 是怎么处理的？”展示带 `[1][2]` 引用的回答和引用卡片。 |
| 2:10-2:40 | 提库外问题 | 问：“学校食堂营业时间是什么？”展示 `NO_ANSWER`，强调低相似度短路不会调用 Chat。 |
| 2:40-3:20 | 后台模型调用日志 | 展示刚才的 embedding/chat 记录、token、延迟、状态。 |
| 3:20-4:00 | 后台 Ingestion 日志 | 展开失败任务错误，跳转文档详情，说明失败原因和重跑能力。 |
| 4:00-4:35 | 后台检索调试 | 输入同一个 query，看 topK chunk、相似度分数和来源。 |
| 4:35-5:00 | 架构/评测收尾 | 总结 pgvector、Mock/真实 Provider、25 题评测结果。 |

## 推荐问题

库内：

```text
gateway JWT trusted headers CORS rate limiting 是怎么处理的？
```

```text
AI inventory assistant 的 admin console 页面提供哪些能力？
```

```text
Phase 3 acceptance 的 full flow 验收证据是什么？
```

库外：

```text
学校食堂营业时间是什么？
```

```text
法式可颂面团应该如何开酥？
```

## 失败演示

真正损坏、不可解析或少于 100 字的文件，重跑仍会失败，这是正确行为。演示“重跑成功”时应使用可恢复失败场景：

1. 临时把 embedding provider 配到不可用 endpoint。
2. 上传一份有效 Markdown，让它在 `EMBED` 阶段失败。
3. 在后台 Ingestion 日志中查看 `FAILED` 和错误原因。
4. 恢复 Mock provider。
5. 在后台或文档详情页触发重新解析，同一文档最终到 `READY`。

这个场景已经在 Phase 5 Gate 验证过，记录见 [dev-log.md](dev-log.md)。

## 录屏检查表

- [ ] 分辨率和字体足够清晰。
- [ ] 全程 ≤6 分钟。
- [ ] 能看到引用卡片原文。
- [ ] 能看到 `NO_ANSWER`。
- [ ] 能看到后台 `model_call_logs` 和 `ingestion_jobs`。
- [ ] 能看到检索调试 topK chunk。
- [ ] 不展示任何真实 API key。

## 备用方案

- 如果真实 Provider 抖动，切回 Mock Provider。
- 如果现场不适合重新上传文件，使用预置 `READY` 文档和已有失败日志。
- 如果前端无法访问，使用 README 中的 curl 验证登录和后端健康，再展示代码与文档。

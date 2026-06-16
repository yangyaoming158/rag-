# Demo Quickstart

这份文档用于第一次直观体验 DevDocs RAG。目标不是看 Mock 模型多聪明，而是完整走一遍 RAG 工程链路：文档入库、检索、带引用问答、无答案拒答、后台日志和失败可观测。

## 0. 打开系统

地址：

```text
http://localhost:3000
```

账号：

```text
admin / admin123
```

如果页面打不开，先确认容器：

```bash
docker compose ps
```

需要看到 `frontend`、`backend`、`postgres` 都是 healthy。

## 1. 准备一个知识库

首页点击“新建”，建议名称：

```text
mini-mall 项目文档
```

描述可填：

```text
用于体验 RAG 上传、检索、问答和后台可观测
```

如果本机数据库里已经有 `phase3-eval-20260614-v2` 或类似评测知识库，也可以直接打开已有知识库继续测试。

## 2. 上传演示文档

进入知识库详情页，点击“上传”，优先上传这 3 份，体验已经足够直观：

```text
/home/oslab/projects/mini-mall-order/README.md
/home/oslab/projects/mini-mall-order/docs/architecture.md
/home/oslab/projects/mini-mall-order/docs/phase3-ai-inventory-contract.md
```

如果还想覆盖更多问题，再上传：

```text
/home/oslab/projects/mini-mall-order/docs/phase3-acceptance.md
/home/oslab/projects/mini-mall-order/docs/phase2-admin-api-contract.md
/home/oslab/projects/mini-mall-order/docs/phase2-5-ai-inventory-contract.md
/home/oslab/projects/mini-mall-order/admin-frontend/README.md
/home/oslab/projects/mini-mall-order/frontend/README.md
```

预期现象：

- 文档初始状态会从 `UPLOADED` 开始。
- 几秒内依次经过 `PARSING`、`CHUNKING`、`EMBEDDING`。
- 成功后变成 `READY`，`Chunks` 数大于 0。
- 点每行“任务”，能看到 ingestion job 时间线。

如果状态暂时没刷新，点击右上角“刷新”。

## 3. 体验检索调试

检索调试不在首页的知识库卡片列表页。先在某个知识库卡片底部点击“打开”，进入知识库详情页；在文档表格下方找到“检索调试”，输入：

```text
gateway JWT trusted headers CORS rate limiting
```

点击“检索”。

预期现象：

- 表格返回 topK chunk。
- 每条有 `Score`、来源文档、chunk 编号和内容预览。
- 这一步不会调用 Chat，只验证“问题能命中文档片段”。

这是理解 RAG 最关键的一屏：模型回答之前，系统先把这些 chunk 找出来。

## 4. 体验带引用问答

点击右上角“问答”，进入聊天页。

先问库内问题：

```text
gateway JWT trusted headers CORS rate limiting 是怎么处理的？
```

预期现象：

- 助手消息状态是 `OK`。
- 回答正文里有 `[1]`、`[2]` 这类引用编号。
- 回答下方有引用卡片，展示来源文档、相似度和原文片段。

再问一个和文档无关的问题：

```text
学校食堂几点开门？
```

预期现象：

- 助手消息状态是 `NO_ANSWER`。
- 回答是明确拒答，而不是编造。
- 这个结果说明检索阈值短路生效：知识库没有依据时，不把问题交给模型胡编。

可继续尝试：

```text
AI inventory assistant 的 admin console 页面提供哪些能力？
```

```text
Phase 3 acceptance 的 full flow 验收证据是什么？
```

## 5. 体验历史回看

刷新浏览器或返回后重新进入同一个知识库的“问答”页。

预期现象：

- 左侧能看到历史会话。
- 点开会话后，之前的用户问题、助手回答和引用卡片仍在。
- 引用内容来自数据库快照，不依赖重新检索。

## 6. 体验后台可观测

从首页或知识库详情页点击“后台”。

### 概览

看统计卡片：

- 知识库数
- 文档数
- Chunks
- Token / 平均延迟

### Ingestion 日志

切到“Ingestion 日志”。

预期现象：

- 能看到 `PARSE`、`CHUNK`、`EMBED` 任务。
- 可以按 `FAILED`、`SUCCEEDED` 等状态过滤。
- 展开行可以看 KB、文档、时间和错误原因。
- 点击“文档”会跳回文档详情并定位任务。

### 模型调用日志

切到“模型调用日志”。

预期现象：

- 能看到 `EMBEDDING` 和 `CHAT` 调用。
- 有 provider、model、token、延迟、状态。
- 问库外问题走阈值短路时，可能只有 embedding 调用，没有 chat 调用。

### 后台检索调试

切到“检索调试”，选择知识库，输入同一个 query：

```text
gateway JWT trusted headers CORS rate limiting
```

预期现象：

- 能看到 topK chunk 和分数条。
- 这和知识库详情页的检索调试是同一类能力，但集中在后台。

## 7. 体验失败路径

为了看失败可观测，可以上传一个内容少于 100 字的 `.txt` 文件，例如：

```text
too-short.txt
```

内容：

```text
too short
```

预期现象：

- 上传本身会成功，因为扩展名合法。
- ingestion 会失败，文档状态变成 `FAILED`。
- 文档展开行、任务抽屉、后台 Ingestion 日志都能看到类似“解析文本少于 100 字，无法入库”的错误。

注意：这种文件是真正不可恢复失败，直接点“重跑”仍会失败，这是正确行为。Phase 5 验证过的“重跑成功”场景是可恢复失败，例如临时模型调用失败后恢复 Mock Provider。

## 8. 你应该从这次体验里看到什么

完整测试后，应该能回答这几个问题：

- 文档如何从上传变成可检索的 chunks？
- 系统回答前检索到了哪些原文片段？
- 回答为什么能带引用？
- 知识库没有答案时为什么不会编？
- 失败在哪里能看到原因？
- 模型调用 token、延迟、状态在哪里看？

如果这些都能直观看到，这个项目的核心价值就已经体现出来了：它不是单纯套一个聊天框，而是一个可检索、可溯源、可拒答、可观测的 RAG 工程闭环。

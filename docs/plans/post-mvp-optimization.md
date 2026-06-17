# Post-MVP Optimization Plan

日期：2026-06-17

本文档用于承接 DevDocs RAG MVP 后续优化。排序依据是当前仓库真实状态，而不是泛化 RAG 项目模板。

当前项目已经完成 Spring Boot 3 后端、Vue 3 前端、PostgreSQL + pgvector、文档上传解析切块、Mock embedding/chat provider、向量检索、引用问答、`NO_ANSWER` 拒答、`UNGROUNDED` 标记、ingestion 日志、model call 日志、检索调试页、Docker Compose 和 Mock 评测基线。

当前最主要短板不是“能不能跑”，而是缺少真实 Provider 证据、GitHub 展示证据、失败样本复盘和工程术语检索增强。

## P0：可信度与展示证据

P0 优先处理能直接回答“这个项目是不是 mock 演示”的问题。

### 1. 真实 Provider 评测

新增：

```text
docs/eval/real-provider-baseline.md
```

工作内容：

- 使用真实 OpenAI-compatible embedding provider 完成至少一次文档入库。
- 使用真实 OpenAI-compatible chat provider 完成至少一次库内问答。
- 使用真实模型完成 20 道库内问答和 5 道库外问答。
- 记录 provider、model、token、latency、status、失败样本和阈值表现。
- README 增加真实 Provider 评测摘要，并明确 Mock 指标不能代表真实模型效果。

注意事项：

- 当前数据库 schema 和 `IngestionWorker` 固定使用 `vector(1024)`，真实 embedding 模型必须先选择 1024 维模型，或者先改 schema 和维度校验。
- 真实 Provider 接入不是从零写代码，当前已有 OpenAI-compatible provider 实现；重点是配置、运行、记录和复测。

验收标准：

- 至少完成一次真实 embedding 入库。
- 至少完成一次真实 chat 问答。
- 25 道真实 Provider 问答评测有结果。
- 至少记录 3 个失败或边界案例。

### 2. GitHub Actions CI

新增：

```text
.github/workflows/ci.yml
```

最低流程：

```text
backend-test:
- setup-java
- cd backend
- mvn test

frontend-build:
- setup-node
- cd frontend
- npm ci
- npm run build
```

验收标准：

- README 有 CI badge。
- push 后自动执行后端测试。
- push 后自动执行前端构建。
- CI 失败时能区分后端或前端问题。

### 3. README 截图与演示视频

新增：

```text
docs/images/
```

建议截图：

```text
01-home.png
02-documents-ready.png
03-chat-with-citations.png
04-no-answer.png
05-model-call-logs.png
06-ingestion-jobs.png
07-retrieval-debug.png
```

README 顶部补充：

- 项目截图。
- 一句话定位。
- 核心亮点。
- 技术栈。
- 一键启动命令。
- 演示账号。
- Mock / Real Provider 区别说明。
- 演示视频链接。

验收标准：

- README 能独立指导启动和演示。
- 视频不超过 6 分钟。
- 视频能看到引用卡片、拒答、模型调用日志、ingestion 日志和检索调试结果。
- 不暴露 API key。

## P1：质量分析与检索增强

P1 解决“系统失败时能不能解释”和“工程术语召回不稳”的问题。

### 4. 失败样本复盘

新增：

```text
docs/eval/failure-cases.md
```

每个案例按以下结构记录：

```text
问题：
预期结果：
实际结果：
失败类型：
原因分析：
修复方案：
复测结果：
剩余风险：
```

失败类型建议：

```text
解析失败
chunk 切分问题
embedding 召回问题
关键词召回问题
similarity threshold 问题
prompt 约束问题
引用不相关
模型生成错误
应该拒答但没有拒答
应该回答但错误拒答
```

验收标准：

- 至少记录 3 个真实失败案例。
- 至少一个来自检索失败。
- 至少一个来自引用问题。
- 至少一个来自拒答边界。

### 5. Hybrid Search

新增：

```text
docs/design/hybrid-search.md
docs/eval/hybrid-search.md
```

目标：

```text
vector search topK
+
keyword search topK
+
RRF 融合排序
```

第一版可以优先用 PostgreSQL full-text search；如果实现成本过高，可先做 ILIKE MVP，但需要在文档中明确边界。

重点 query：

```text
/api/admin/inventory 路径是什么？
X-User-Role 如何处理？
PENDING_REVIEW 状态是什么意思？
common-auth 模块负责什么？
RAG_AI_CHAT_PROVIDER 如何配置？
```

验收标准：

- 检索调试页展示 vector score、keyword score、final score。
- 至少构建 10 条工程术语 query。
- hybrid top3 命中率高于纯向量检索。
- README 增加 hybrid search 说明。

## P2：定位升级与面试材料

P2 把项目从“项目文档问答系统”进一步包装为“研发知识库与架构审查助手”，但不引入复杂 Agent。

### 6. 面试 Q&A 升级

更新：

```text
docs/interview-qna.md
docs/interview-script.md
```

重点新增高压追问：

- 你的项目和普通 PDF 问答有什么区别？
- 为什么不用 LangChain？
- 为什么用 Spring Boot？
- 为什么用 pgvector？
- 为什么不用 Milvus？
- 为什么不用 Elasticsearch？
- Mock Provider 是不是作弊？
- Mock 评测能不能证明真实效果？
- 真实 Provider 下结果如何？
- chunk 参数怎么来的？
- similarity threshold 怎么标定？
- 引用编号合法是否等于事实正确？
- 文档更新后旧向量怎么办？
- 如何定位 RAG 链路错误？
- 如果数据量扩大到百万 chunk 怎么办？
- 如果要生产化还缺什么？

每个回答建议包含：

```text
当前实现
当前边界
后续改进
```

### 7. 研发文档审查模板 MVP

新增功能入口：

```text
项目审查 / Review
```

第一批只做固定模板，不做 Agent：

1. PRD 与接口文档一致性检查。
2. 任务树遗漏风险检查。

输入：

```text
知识库 ID
审查类型
补充说明
```

输出：

```text
审查结论
风险等级
发现的问题
引用来源
建议修改项
```

验收标准：

- 至少实现 2 个审查模板。
- 审查结果必须带引用来源。
- 审查结果可历史查看。
- README 项目定位更新为“研发知识库与架构审查助手”。

## P3：质量治理与生产化设计

P3 属于进一步工程化能力，建议在 P0-P2 稳定后再做。

### 8. 用户反馈与低质量回答后台

新增表：

```text
qa_feedback
- id
- message_id
- user_id
- rating
- reason
- comment
- created_at
```

反馈类型：

```text
HELPFUL
WRONG
CITATION_IRRELEVANT
SHOULD_HAVE_ANSWERED
SHOULD_HAVE_REFUSED
TOO_LONG
TOO_SHORT
```

验收标准：

- 用户可以反馈回答质量。
- 后台可以查看低质量回答。
- 反馈能关联原问题、回答、引用、provider、model、latency。

### 9. 文档版本化设计

先新增设计文档，不急着实现：

```text
docs/design/document-versioning.md
```

推荐设计：

```text
documents
- id
- kb_id
- current_version_id
- original_filename

document_versions
- id
- document_id
- version_no
- sha256
- storage_path
- status
- created_at

document_chunks
- document_version_id
```

检索规则：

```text
默认只检索 latest READY version。
历史 citation 保留 snapshot。
旧版本可在详情页查看，但不参与默认问答。
```

验收标准：

- 能讲清楚为什么 MVP 先采用删除和重传。
- 能讲清楚生产化时如何做版本化。
- 能讲清楚历史引用为什么要保存快照。

## 暂不优先做

以下能力当前不建议优先投入：

- 多 Agent。
- MCP 工具调用。
- Kubernetes。
- 微服务拆分。
- Milvus 集群。
- Elasticsearch 集群。
- OCR。
- 企业级多租户。
- 自动代码修改。

这些能力会显著增加项目体积，但不能优先解决当前最核心的可信度和展示证据问题。

## 两周建议排期

```text
第 1-2 天：真实 Provider 评测
第 3 天：失败案例复盘
第 4 天：CI
第 5 天：截图与演示视频
第 6-8 天：Hybrid Search
第 9 天：面试 Q&A 升级
第 10-12 天：Review 模板 MVP
第 13-14 天：收尾、README、tag v0.2-showcase
```

## 最终定位

不建议继续写成：

```text
RAG 知识库问答系统
```

建议定位为：

```text
DevDocs RAG：面向软件项目文档的研发知识库与架构审查助手
```

推荐简历描述：

```text
设计并实现面向软件项目文档的 RAG 知识库与架构审查助手，支持 PRD、接口文档、数据库设计、任务树、验收报告等工程文档的上传、解析、切块、向量化检索和引用问答。系统基于 Spring Boot 3、PostgreSQL + pgvector 和 Vue 3 构建，提供文档 ingestion 状态机、失败重跑、检索调试、模型调用日志、引用快照、NO_ANSWER 拒答和 UNGROUNDED 标记等工程化能力。
```

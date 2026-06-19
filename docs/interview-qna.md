# Interview Q&A

## 简历描述

**研发知识库与架构审查助手（个人项目）** — Java 17 / Spring Boot 3 / PostgreSQL + pgvector / Vue 3 / Docker Compose

- 设计并实现完整 RAG 链路：文档上传、Tika/PDFBox 解析、标题感知切块、批量 embedding、pgvector HNSW 向量检索、Hybrid Search、带引用溯源问答。
- 实现基于数据库任务表的异步 ingestion 状态机，覆盖解析、切块、向量化三阶段，失败原因可视化，支持幂等重新解析。
- 通过检索阈值短路、低 temperature、强约束 Prompt、引用编号校验、失败样本复盘和评测集控制幻觉；知识库无答案时显式拒答。
- 向量检索层不依赖框架黑盒，自行设计 chunk 元数据表与检索 SQL，实现多知识库 `kb_id` 隔离、owner 权限过滤和 vector + keyword + RRF 融合排序。
- 建立全链路可观测后台：ingestion 任务日志、模型调用日志、检索调试工具、token/延迟统计卡片；真实 Provider 与 Mock Provider 均有可复现证据。
- Post-MVP 增加固定模板项目审查能力，支持 PRD 与接口一致性检查、任务树遗漏风险检查，审查结果持久化并保留引用来源。

## 一句话定位

这不是普通 PDF 问答，而是面向研发文档的知识库和审查工具：先把工程文档解析成可检索、可追踪的知识单元，再用 RAG 做带引用问答和固定模板审查。

## 高频追问

| 问题 | 回答骨架 |
|---|---|
| 你的项目和普通 PDF 问答有什么区别？ | 当前实现：不只是把 PDF 丢给模型，而是有上传、解析、标题感知切块、embedding、Hybrid Search、引用、拒答、日志和评测闭环；Post-MVP 还增加了固定模板项目审查。当前边界：MVP 只处理 Markdown/TXT/PDF，不做 OCR 和复杂表格结构化。后续改进：文档版本化、反馈闭环、生产级权限和增量索引。 |
| 为什么不用 LangChain？ | 当前实现：核心检索 SQL、Prompt、引用解析和 Provider 适配都自己收口，便于解释每一步。当前边界：这牺牲了一些现成组件能力，例如工具链编排和复杂 Agent。后续改进：如果后续需要多步工具调用，可以在保持现有 RAG contract 的基础上接入编排层。 |
| 为什么用 Spring Boot？ | 当前实现：项目主目标是展示后端工程能力，Spring Boot 单体能把权限、文件、任务、数据库、日志和 API 契约放在一个清晰边界内。当前边界：没有微服务弹性和分布式治理。后续改进：生产化可以先拆后台任务和模型调用队列，而不是一开始就拆微服务。 |
| 为什么用 pgvector？ | 当前实现：文档、chunk、向量、引用和业务元数据同库，删除、重跑和权限隔离更容易事务一致；部署只需要 PostgreSQL。当前边界：百万级 chunk、高并发检索、多路召回和复杂过滤时会遇到性能上限。后续改进：检索层已收口，后续可替换为专用向量库或搜索引擎。 |
| 为什么不用 Milvus？ | 当前实现：MVP 规模下 Milvus 会增加部署、数据一致性和权限过滤复杂度。当前边界：pgvector 不是为超大规模向量检索专门设计。后续改进：当 chunk 规模、召回 QPS 或分片需求明确后，再把 `RetrievalRepository` 替换成 Milvus 适配层。 |
| 为什么不用 Elasticsearch？ | 当前实现：第一版主线是语义检索，P1 用 PostgreSQL ILIKE 做 Hybrid Search MVP，避免新增 ES 运维面。当前边界：当前 keyword search 没有 BM25、分词词典和倒排索引能力。后续改进：如果工程术语关键词检索成为主需求，可以引入 PostgreSQL full-text 或 Elasticsearch。 |
| Mock Provider 是不是作弊？ | 当前实现：Mock 是离线兜底，保证 CI、答辩和无 key 环境可以跑完整链路；真实 Provider 已单独评测。当前边界：Mock 指标不能代表真实模型质量。后续改进：继续保留 Mock 做回归测试，把效果指标以真实 Provider 为准。 |
| Mock 评测能不能证明真实效果？ | 当前实现：不能。Mock 只证明工程链路可跑、引用和拒答逻辑存在。当前边界：真实 embedding 的相似度分布、真实 chat 的引用遵循度都不同。后续改进：每次替换模型后重跑真实 Provider 检索和问答评测。 |
| 真实 Provider 下结果如何？ | 当前实现：DeepSeek `deepseek-v4-flash` + SiliconFlow `BAAI/bge-m3`，8 份文档 131 chunks；检索 top1 9/10、top3 10/10；库内 20/20 有引用回答，库外 5/5 `NO_ANSWER`。当前边界：这是小规模工程文档基线，不代表任意语料。后续改进：扩大语料、分层统计失败类型，并单独标定阈值。 |
| chunk 参数怎么来的？ | 当前实现：Markdown 按标题层级；短节合并，长节按段落滑窗；TXT/PDF 累积到 600-900 字，120 字重叠，目标 chunk 长度 200-1000 字。当前边界：参数来自 MVP 语料和人工抽查，不是全局最优。后续改进：按文档类型记录召回失败，调参或引入 semantic chunking。 |
| similarity threshold 怎么标定？ | 当前实现：Mock 基线默认 `0.35`，真实 Provider 评测记录了库外误召回边界，RAG 仍要求至少一个 vector hit 过阈值。当前边界：Hybrid final score 不直接替代 vector threshold，避免 keyword-only 结果绕过拒答。后续改进：按真实 embedding 重新标定阈值，可能上调到 0.45 附近。 |
| 引用编号合法是否等于事实正确？ | 当前实现：不等于。合法引用只说明模型引用了检索上下文中的 chunk；事实正确还要看 chunk 是否相关、模型是否正确归纳。当前边界：系统能发现无引用和非法引用，但不能完全证明推理正确。后续改进：引入人工反馈、引用相关性评估和失败样本复盘。 |
| 文档更新后旧向量怎么办？ | 当前实现：MVP 采用删除+重传或重新解析；重跑会清空旧 chunks 后重建，避免重复向量。当前边界：没有版本化，历史引用只能依赖 citation 快照。后续改进：增加 `document_versions`，默认只检索 latest READY version，历史引用保留 snapshot。 |
| 如何定位 RAG 链路错误？ | 当前实现：看 ingestion 日志定位解析/切块/embedding，看检索调试页定位召回和排序，看 model_call_logs 定位 Provider、token、延迟和错误，看 citations 判断引用是否落到正确 chunk。当前边界：没有分布式 tracing 和在线质量看板。后续改进：增加反馈表和低质量回答后台。 |
| 如果数据量扩大到百万 chunk 怎么办？ | 当前实现：当前单库 pgvector + HNSW 适合演示和小规模团队文档。当前边界：百万 chunk 会遇到索引构建、更新、过滤和延迟压力。后续改进：按 KB/文档类型分区，异步 embedding 队列，召回层换专用向量库或搜索引擎，并增加 rerank。 |
| 如果要生产化还缺什么？ | 当前实现：有核心闭环、日志和评测，但不是生产系统。当前边界：缺少 RBAC、多租户、版本化、反馈治理、限流、审计、备份、灰度、监控告警和数据脱敏。后续改进：优先补文档版本化、反馈闭环、权限和任务队列，而不是先做多 Agent。 |
| 项目审查是不是 Agent？ | 当前实现：不是 Agent，只是固定模板 + RAG 检索 + ChatProvider + 引用持久化。当前边界：不会自动拆任务、不会调用外部工具、不会执行写操作。后续改进：如果要做 Agent，也应保持只读、可审计、人工确认。 |
| 和 mini-mall 的关系？ | 当前实现：两者独立部署、零代码耦合；mini-mall 文档作为种子语料，展示非结构化工程知识问答和审查。当前边界：DevDocs RAG 不直接读取 mini-mall 数据库或代码运行态。后续改进：可把更多项目文档纳入知识库，形成跨项目研发知识检索。 |

## 不能夸大的点

- 不说商业项目或真实用户量。
- 不说训练大模型。
- 不说生产级监控，当前是展示版统计与日志。
- 不说复杂 Agent 已完成，当前 Review 是固定模板审查。
- 不把 Mock 指标当真实效果。
- 不把真实 Provider 小样本评测泛化为生产效果。

## 可以现场翻代码的位置

| 主题 | 文件 |
|---|---|
| Hybrid 检索 SQL 与 RRF | `backend/src/main/java/com/ragdocs/repository/RetrievalRepository.java` |
| 切块算法 | `backend/src/main/java/com/ragdocs/ingestion/DocumentChunker.java` |
| Prompt 构造 | `backend/src/main/java/com/ragdocs/rag/PromptBuilder.java` |
| 引用解析 | `backend/src/main/java/com/ragdocs/rag/CitationParser.java` |
| 问答流程 | `backend/src/main/java/com/ragdocs/rag/RagService.java` |
| Review 模板审查 | `backend/src/main/java/com/ragdocs/rag/ReviewService.java` |
| Review 历史持久化 | `backend/src/main/java/com/ragdocs/repository/ReviewRepository.java` |
| 后台日志 API | `backend/src/main/java/com/ragdocs/web/AdminController.java` |
| 前端聊天页 | `frontend/src/views/ChatView.vue` |
| 前端审查页 | `frontend/src/views/ReviewView.vue` |
| 前端后台页 | `frontend/src/views/AdminView.vue` |

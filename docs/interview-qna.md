# Interview Q&A

## 简历描述

**项目文档智能问答系统（个人项目）** — Java 17 / Spring Boot 3 / PostgreSQL + pgvector / Vue 3 / Docker Compose

- 设计并实现完整 RAG 链路：文档上传、Tika/PDFBox 解析、标题感知切块、批量 embedding、pgvector HNSW 向量检索、带引用溯源问答。
- 实现基于数据库任务表的异步 ingestion 状态机，覆盖解析、切块、向量化三阶段，失败原因可视化，支持幂等重新解析。
- 通过检索阈值短路、低 temperature、强约束 Prompt、引用编号校验、25 题评测集控制幻觉；知识库无答案时显式拒答。
- 向量检索层不依赖框架黑盒，自行设计 chunk 元数据表与检索 SQL，实现多知识库 `kb_id` 隔离与 owner 权限过滤。
- 建立全链路可观测后台：ingestion 任务日志、模型调用日志、检索调试工具、token/延迟统计卡片；三容器 Docker Compose 一键运行。

## 高频追问

| 问题 | 回答骨架 |
|---|---|
| RAG 和普通 ChatBot 的区别？ | 普通 ChatBot 主要依赖参数记忆，无法保证知识及时性和可溯源。RAG 把知识外置到文档库，回答前先检索，只把 topK 上下文交给模型。本项目可演示：库内问题带引用回答；库外问题走 `NO_ANSWER`。 |
| chunk 策略怎么设计？ | Markdown 按标题层级解析，保留 `heading_path`；短节向兄弟/父节合并，长段按段落滑窗拆分。TXT/PDF 按空行段落累积到 600-900 字，120 字重叠。这样兼顾语义完整度、相似度质量和引用可读性。 |
| 为什么用 pgvector，不用 Milvus？ | MVP 规模小于专用向量库刚需。pgvector 让向量和业务元数据同库，删除文档时 chunks 和向量事务一致，部署也少两个服务。边界是百万级 chunk 或复杂 hybrid/rerank 场景，后续可替换检索层。 |
| 如何保证答案有依据？ | Prompt 要求每个论断标 `[n]`；后端解析引用编号，只接受检索上下文中存在的编号；citations 表保存 chunk 快照；没有合法引用且非拒答时标记 `UNGROUNDED`。 |
| 如何降低幻觉？ | 五层：检索阈值短路、低 temperature、模板三规则、引用编号校验、评测集验收。最关键是阈值短路发生在 Chat 调用前，资料不足就不给模型编的机会。 |
| 知识库没答案怎么办？ | top1 similarity 低于阈值直接返回固定拒答话术，不调用 Chat。若相似度过阈值但模型仍缺依据，模板要求拒答；若模型无引用乱答，后端标 `UNGROUNDED`。 |
| 权限隔离怎么做？ | Service 层先校验 KB owner；检索 SQL 强制 `WHERE c.kb_id = ?`；chunk 表冗余 `kb_id` 避免检索时依赖复杂 join。Phase 3 做过 KB 隔离测试。 |
| 文档更新后向量如何同步？ | MVP 不做版本化更新，采用删除+重传或重新解析。重新解析会清空旧 chunks 后重建，避免重复向量。citations 保存快照，历史问答不会因文档删除而不可读。 |
| 为什么不把全文塞给模型？ | 全文塞入成本随文档线性增长，且受上下文长度限制，来源也难以定位。RAG 每次只用 top-6 chunks，成本固定，答案可映射回文档片段。 |
| 模型调用失败怎么办？ | Embedding 失败会把文档置为 `FAILED`，错误写入 `ingestion_jobs` 和 `model_call_logs`；Chat 失败返回 `50201`，并写入 `CHAT/ERROR` 日志。后台可以定位失败原因。 |
| 真实 API 什么时候接入？ | MVP 默认 Mock 可跑。真实 Chat/Embedding 接入最好放在演示前复测阶段，接入后必须重跑检索和问答评测，并重新标定阈值。 |
| 为什么没做 Agent？ | Phase 6 默认跳过。问答闭环、引用、拒答、可观测和评测是 RAG 项目的核心证据；没有这些基础先做 Agent 会提高演示风险。 |
| 和 mini-mall 的关系？ | 两者独立部署、零代码耦合。mini-mall 文档作为种子语料，展示非结构化工程知识问答；mini-mall 自身覆盖结构化数据上的 AI 库存建议。 |
| 最大技术难点是什么？ | 切块和可观测。切块决定检索上限；可观测决定问题能不能定位。本项目用检索调试页、ingestion 日志、模型调用日志和评测集把质量问题显式化。 |

## 不能夸大的点

- 不说商业项目或真实用户量。
- 不说训练大模型。
- 不说生产级监控，当前是展示版统计与日志。
- 不说 Agent 已完成，Phase 6 默认跳过。
- 不说真实 Provider 指标，当前评测是 Mock Provider 基线。

## 可以现场翻代码的位置

| 主题 | 文件 |
|---|---|
| 检索 SQL | `backend/src/main/java/com/ragdocs/repository/RetrievalRepository.java` |
| 切块算法 | `backend/src/main/java/com/ragdocs/ingestion/DocumentChunker.java` |
| Prompt 构造 | `backend/src/main/java/com/ragdocs/rag/PromptBuilder.java` |
| 引用解析 | `backend/src/main/java/com/ragdocs/rag/CitationParser.java` |
| 问答流程 | `backend/src/main/java/com/ragdocs/rag/RagService.java` |
| 后台日志 API | `backend/src/main/java/com/ragdocs/web/AdminController.java` |
| 前端聊天页 | `frontend/src/views/ChatView.vue` |
| 前端后台页 | `frontend/src/views/AdminView.vue` |

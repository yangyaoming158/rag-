# Phase 4 实现规划 — RAG 问答闭环

- 日期：2026-06-15
- 主力模型：Opus（prompt/引用边界）+ Codex（CRUD/SQL/埋点）+ Sonnet（聊天页）
- 对应规划：`RAG规划-03` Phase 4、`RAG规划-02` 第 3.9、3.10、3.11、3.12、5、7 节

## 1. 本阶段目标（一句话）
完成带引用的同步 RAG 问答闭环：会话、检索、阈值拒答、ChatProvider、引用落库、历史回看和前端聊天页。

## 2. 验收 Gate（开工即明确，照抄不改）
- [x] 评测集库内题「有依据回答」≥16/20，库外题 5/5 走 NO_ANSWER
- [x] 拔 key 演示返回 50201 且日志可查
- [x] 历史回看引用完整
- [x] 评测结果记入 `docs/eval/questions.md`

## 3. 任务卡拆分

### 任务 1：实现 ChatProvider 与 CHAT 调用日志
- **上下文**：规划 02 第 3.10 节：`ChatProvider` 调用，temperature 0.2，60s 超时，重试 1 次，token 统计落 `model_call_logs`；失败时返回 50201 且日志可查。
- **改动文件**：`backend/src/main/java/com/ragdocs/provider/*`、`backend/src/main/java/com/ragdocs/config/*`、`backend/src/main/java/com/ragdocs/repository/ModelCallLogRepository.java`
- **契约**：
  - 默认 `rag.ai.chat.provider=mock`
  - 真实 provider 使用 OpenAI 兼容 `/chat/completions`
  - `ChatProvider.chat(List<ChatMessage> messages)` 返回 answer、promptTokens、completionTokens
  - provider 失败抛出明确异常，由业务层转换为 `50201`
- **验收**：Mock 模式不需要 key；强制真实 provider 且缺 key 时，问答返回 50201 并写 `model_call_logs` ERROR。
- **禁止**：不做 SSE；不引入 LangChain4j；不把密钥写入代码或文档。

### 任务 2：实现 PromptBuilder 与引用解析
- **上下文**：规划 02 第 3.9、3.11 节：Prompt 模板带版本号；上下文块编号连续 `[1]..[6]`；上下文合计 ≤6000 字；最近 3 轮历史每条截断 500 字；解析答案 `[n]` 映射 chunk，非法编号丢弃并返回 `citationWarning`；无引用且非拒答标 `UNGROUNDED`。
- **改动文件**：`backend/src/main/resources/prompts/rag-answer-v1.txt`、`backend/src/main/java/com/ragdocs/rag/*`、`backend/src/test/java/com/ragdocs/rag/*`
- **契约**：
  - `PromptBuilder.build(question, hits, history)` 输出 messages 与编号上下文
  - `CitationParser.parse(answer, contexts)` 输出合法 citations 与 warning
  - 固定拒答话术：`根据当前知识库内容，无法回答这个问题。`
- **验收**：单测覆盖编号连续、上下文截断、历史只保留最近 3 轮、非法引用丢弃、无引用 UNGROUNDED。
- **禁止**：不做“修复模型引用”的猜测性后处理；不把未检索内容塞进 prompt。

### 任务 3：实现 conversation/message/citation 持久化 API
- **上下文**：规划 02 第 5 节 API：`POST /api/conversations`、`POST /api/conversations/{id}/messages`、`GET /api/conversations?kbId=`、`GET /api/conversations/{id}`；表结构使用 `conversations/messages/citations`，历史回看引用完整。
- **改动文件**：`backend/src/main/java/com/ragdocs/domain/*`、`backend/src/main/java/com/ragdocs/repository/*`、`backend/src/main/java/com/ragdocs/rag/RagService.java`、`backend/src/main/java/com/ragdocs/web/*`、`backend/src/main/java/com/ragdocs/web/dto/*`
- **契约**：
  - 创建会话：`POST /api/conversations {kbId, title?}`
  - 提问：`POST /api/conversations/{id}/messages {question}`
  - 返回：`{answer,status,citations[],citationWarning,latencyMs}`
  - 历史：返回 conversation、messages、citations
- **验收**：owner 校验通过；无权访问返回 404/403；刷新历史仍能看到引用快照。
- **禁止**：不做跨库会话；不做 RBAC；不做流式。

### 任务 4：实现 RAG 问答流程和阈值短路
- **上下文**：规划 02 第 7 节：校验会话归属和 question 长度；问题 embedding 记日志；top-8 检索；top1 similarity 低于阈值直接 `NO_ANSWER`，不调 LLM；取前 6 块进 prompt；落 messages、citations、model_call_logs。
- **改动文件**：`backend/src/main/java/com/ragdocs/rag/*`、`backend/src/main/java/com/ragdocs/retrieval/*`、`backend/src/main/java/com/ragdocs/repository/*`
- **契约**：
  - question 非空且 ≤2000 字
  - top1 < `rag.retrieval.min-similarity` 返回 NO_ANSWER，不写 CHAT 调用日志
  - LLM 成功但无合法引用且非拒答 → `UNGROUNDED`
  - LLM 失败 → assistant message 状态 `ERROR`，接口返回 50201
- **验收**：单测覆盖 NO_ANSWER 短路、不调用 chat、引用落库和 ERROR 日志路径。
- **禁止**：不做 rerank、query rewrite、缓存、跨库联检。

### 任务 5：实现前端聊天页与历史回看
- **上下文**：规划 02 第 8 节：问答聊天页展示消息流、引用角标 `[n]`、引用卡片、NO_ANSWER 与 UNGROUNDED 区分样式；历史页能回看会话与引用。
- **改动文件**：`frontend/src/api/*`、`frontend/src/router/index.ts`、`frontend/src/views/*`、必要时 `frontend/src/App.vue`
- **契约**：
  - KB 详情页提供进入聊天入口
  - 聊天页可创建/选择会话、发送问题、展示状态和引用卡片
  - 历史回看使用后端 `GET /api/conversations/{id}`
- **验收**：`npm run build` 通过；浏览器能完成“库内问题 → 引用回答 → 刷新回看引用”。
- **禁止**：不做暗色主题、i18n、移动端适配、SSE。

### 任务 6：建立 25 题评测集并记录结果
- **上下文**：规划 03 Phase 4 Gate：20 库内题有据回答 ≥16/20，5 库外题 5/5 走 NO_ANSWER；结果记入 `docs/eval/questions.md`。
- **改动文件**：`docs/eval/questions.md`、`README.md`、`PROGRESS.md`、`docs/dev-log.md`
- **契约**：
  - 每题记录：question、类型、预期、状态、引用数、结果
  - Mock provider 下可作为离线基线；真实 provider 接入后需复测
- **验收**：Gate 全部通过后更新 `PROGRESS.md`。
- **禁止**：不硬编码评测问题答案；不绕过检索或引用校验。

## 4. 本阶段红线
- 禁止：SSE、Agent、多 Agent、自动执行写操作、rerank、hybrid 检索、跨库联检、RBAC、缓存、模型微调。
- 暂缓（非本阶段做）：后台模型日志页、后台统计卡片、Agent 任务规划、演示包装。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| 模型不按 `[n]` 引用 | Prompt 强约束；后端只接受合法编号；无合法引用标 `UNGROUNDED` |
| 库外问题被模型编造 | top1 阈值短路先于 LLM；评测集中保留 5 道库外题 |
| ChatProvider 失败静默 | 失败写 assistant ERROR、`model_call_logs` ERROR，并返回 50201 |
| 历史引用因文档删除丢失 | citations 存 snippet 和 document_filename 快照，chunk_id 可为空 |
| Mock 与真实模型表现不同 | Mock 作为离线基线；真实 key 接入后复跑 `docs/eval/questions.md` |

## 6. 完成后动作
- [x] 开工前已从最新 `main` 切出 `phase-4` 分支（禁止在 main 上提交）
- [x] 跑全量测试 + `docker compose up -d` 冷启动验证
- [x] `docs/dev-log.md` 追加各任务「做了什么/没做什么/遗留」
- [x] Gate 全过 → 在 `PROGRESS.md` 勾掉本 Phase 并填日期；不过 → 记原因，不进下一阶段
- [x] 在 `phase-4` 分支做「阶段完成」提交：`git commit -m "phase-4: RAG 问答闭环"`
- [x] Gate 通过后合并：`git checkout main && git merge --no-ff phase-4`

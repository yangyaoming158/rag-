# Phase 2 实现规划 — 文档解析与切块入库

- 日期：2026-06-14
- 主力模型：Opus（切块算法）+ Codex（状态机/API/SQL）+ Sonnet（前端状态展示）
- 对应规划：`RAG规划-03` Phase 2、`RAG规划-02` 第 3.4、3.5、5、6 节

## 1. 本阶段目标（一句话）
跑通 `UPLOADED → PARSING → CHUNKING → EMBEDDING / FAILED`，把解析后的文档切成 chunk 写入 `document_chunks`，但不生成 embedding 向量。

## 2. 验收 Gate（开工即明确，照抄不改）
- [x] 三件套（README.md / architecture.md / 一份 PDF）入库，chunk 长度全在 [200,1000] 字
- [x] heading_path 人工抽查 ≥9/10 正确
- [x] 损坏 PDF 显示 FAILED + 可读原因
- [x] 同文档重跑不产生重复 chunk

## 3. 任务卡拆分

### 任务 1：接入解析器与文本清洗
- **上下文**：规划 02 第 3.4 节：按类型抽取纯文本；MD 保留标题树；PDF 保留页码映射。解析后文本 <100 字或非法字符 >30% 即判 FAILED 并写明原因，不硬撑。
- **改动文件**：`backend/pom.xml`、`backend/src/main/java/com/ragdocs/ingestion/*`、`backend/src/test/java/com/ragdocs/ingestion/*`
- **契约**：
  - 输入：`storage_path + content_type`
  - 输出：`ParsedDocument{ blocks: [{text, headingPath?, pageNo?}] }`
  - 支持：Markdown、TXT、PDF
- **验收**：解析器单测覆盖 Markdown 标题、TXT 段落、短文本失败和非法字符失败。
- **禁止**：OCR、扫描件识别、图片提取、表格结构化、自动语言检测、docx。

### 任务 2：实现标题感知切块算法
- **上下文**：规划 02 第 3.5 节：MD 按标题层级建树，叶子节正文 <200 字向兄弟/父节合并，>900 字按段落+120 字重叠滑窗拆分，保留 heading_path；PDF/TXT 按空行分段，顺序累积到 600–900 字封块，相邻块重叠 120 字。输出 `ChunkDraft{content, chunkIndex, headingPath, pageStart, pageEnd, charLen}`，每块长度落在 [200,1000]。
- **改动文件**：`backend/src/main/java/com/ragdocs/ingestion/*Chunker*.java`、`backend/src/test/java/com/ragdocs/ingestion/*Chunker*Test.java`
- **契约**：
  - `DocumentChunker.chunk(ParsedDocument parsedDocument): List<ChunkDraft>`
  - `ChunkDraft{content, chunkIndex, headingPath, pageStart, pageEnd, charLen}`
- **验收**：单测覆盖 Markdown heading_path、短节合并、长段拆分、TXT/PDF 600–900 累积与 120 字重叠；测试断言 chunk 长度在 [200,1000]。
- **禁止**：自由改参数；不做 embedding、不做 rerank、不做检索。

### 任务 3：实现 ingestion 状态机与重跑接口
- **上下文**：规划 02 第 6 节：上传后 PARSE job；`@Async` 执行 PARSE → CHUNK；失败写 `ingestion_jobs.error_message` 与 `documents.error_message`。重跑 = 重置状态 + 清空该文档已有 chunk + 重建 job，保证幂等。
- **改动文件**：`backend/src/main/java/com/ragdocs/config/*`、`backend/src/main/java/com/ragdocs/repository/*`、`backend/src/main/java/com/ragdocs/service/*`、`backend/src/main/java/com/ragdocs/ingestion/*`、`backend/src/main/java/com/ragdocs/web/DocumentController.java`
- **契约**：
  - 上传成功后异步处理 PARSE/CHUNK
  - `POST /api/documents/{id}/reingest` → `DocumentDto{id,status,jobId}`
  - 成功结束时：`documents.status = EMBEDDING`，`chunk_count = chunks.size()`，chunks 的 `embedding` 为空
  - 失败结束时：`documents.status = FAILED`，`error_message` 可读
- **验收**：上传后轮询文档列表可见状态流转；重跑同文档 chunk 数不翻倍；坏 PDF/TXT 返回 FAILED 与原因。
- **禁止**：本阶段不调 embedding provider，不把文档置为 READY，不引入 MQ/Redis。

### 任务 4：补前端文档详情状态展示与重跑入口
- **上下文**：规划 02 第 8 节：知识库详情支持上传、文档表格、状态徽标、失败行展开 error、"重新解析"按钮；状态徽标 5s 轮询。
- **改动文件**：`frontend/src/api/kbs.ts`、`frontend/src/views/KbDetailView.vue`
- **契约**：
  - 展示 `UPLOADED/PARSING/CHUNKING/EMBEDDING/FAILED`
  - FAILED 行展示 `errorMessage`
  - 调用 `POST /api/documents/{id}/reingest`
- **验收**：上传后状态自动变化；FAILED 文档可看到原因；点击重跑后重新进入处理链路。
- **禁止**：不做聊天页、检索调试页、后台统计页。

## 4. 本阶段红线
- 禁止：OCR、扫描件识别、图片提取、表格结构化、自动语言检测、docx、embedding、检索、问答、MQ、Redis。
- 暂缓（非本阶段做）：向量化、召回评测、检索调试页、RAG prompt、引用解析。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| 切块算法质量影响后续检索 | 严格按规划参数实现；单测覆盖短节合并、长段拆分、heading_path、重叠；Gate 做人工抽查 |
| PDF 解析不稳定 | Tika 解析失败或文本质量不达标时直接 FAILED，并展示可读原因 |
| 异步状态不一致 | 所有状态更新集中在 ingestion service；每个 phase job 明确 RUNNING/SUCCEEDED/FAILED |
| 重跑产生重复 chunk | 重跑前 `DELETE FROM document_chunks WHERE document_id=?`，重新编号并回填 `chunk_count` |
| 当前环境没有独立 Opus 通道 | 实现保持小而可测，按规划原文参数落地；切块逻辑必须由单测和人工抽查共同兜底 |

## 6. 完成后动作
- [x] 开工前已从最新 `main` 切出 `phase-2` 分支（禁止在 main 上提交）
- [x] 跑全量测试 + `docker compose up -d` 冷启动验证
- [x] `docs/dev-log.md` 追加各任务「做了什么/没做什么/遗留」
- [x] Gate 全过 → 在 `PROGRESS.md` 勾掉本 Phase 并填日期；不过 → 记原因，不进下一阶段
- [x] 在 `phase-2` 分支做「阶段完成」提交：`git commit -m "phase-2: 文档解析与切块入库"`
- [x] Gate 通过后合并：`git checkout main && git merge --no-ff phase-2`

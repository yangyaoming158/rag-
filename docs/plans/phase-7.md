# Phase 7 实现规划 — 演示、README、简历包装

- 日期：2026-06-15
- 主力模型：Fable 5（叙事审阅目标）+ Sonnet（文档初稿）+ Codex（命令验证与整理）
- 对应规划：`RAG规划-03` Phase 7、`RAG规划-04` 第 1、2、3、4 节

## 1. 本阶段目标（一句话）
把已完成的 RAG MVP 整理成可冷启动、可演示、可写进简历、可回答面试追问的最终交付材料。

## 2. 验收 Gate（开工即明确，照抄不改）
- [ ] 干净机器（或删 volume）按 README 三命令冷启动成功
- [ ] 演示录屏 ≤6 分钟

## 3. 任务卡拆分

### 任务 1：补齐 README 最终结构
- **上下文**：规划 04 第 4 节：README 需要包含核心特性、架构图、快速开始、RAG 流程、关键设计取舍、评测、API 摘要、Roadmap、与 mini-mall 的关系。
- **改动文件**：`README.md`
- **契约**：
  - 快速开始必须是三命令：`cp .env.example .env`、`docker compose up -d`、打开 `http://localhost:3000`
  - 明确 Mock Provider 默认可跑，不配置 key 也能完成演示
  - 摘要只写已实现能力，不写虚构用户量或商业化叙事
- **验收**：README 从空环境读者视角能完成启动、登录、上传、问答、后台排查。
- **禁止**：不提前宣传 Agent、SSE、hybrid/rerank、生产监控等未完成能力。

### 任务 2：整理演示脚本、演示语料与面试材料
- **上下文**：规划 04 第 1、2、3 节：5 分钟演示脚本；简历描述必须能现场演示或翻代码佐证；面试追问围绕 RAG、chunk、pgvector、防幻觉、权限隔离、失败可观测。
- **改动文件**：`docs/demo-script.md`、`docs/demo-corpus.md`、`docs/interview-qna.md`
- **契约**：
  - 演示脚本按 5 分钟以内拆时间片
  - 演示语料定稿 8-10 份 mini-mall 文档，并说明可替换成本项目文档
  - 面试材料只覆盖已实现能力与明确暂缓项
- **验收**：按脚本能解释引用溯源、无答案拒答、后台可观测、检索调试、评测结果。
- **禁止**：不声称真实用户、学校系统、生产指标或训练大模型。

### 任务 3：补架构图与流程图
- **上下文**：规划 04 README 结构建议：一张架构图、入库状态机图、问答时序图。
- **改动文件**：`docs/architecture.md`、`README.md`
- **契约**：
  - 使用 Mermaid 文本图，便于 GitHub/Markdown 渲染
  - 架构图必须体现三容器、Spring Boot 单体、PostgreSQL+pgvector、本地文件、Mock/OpenAI 兼容 Provider
  - 流程图必须体现失败显式化、阈值短路、引用校验
- **验收**：README 可链接到详细架构文档；图中不出现未采用组件。
- **禁止**：不画 Kubernetes、Redis、MQ、MinIO、Milvus/ES。

### 任务 4：执行冷启动与收尾记录
- **上下文**：CLAUDE.md 第 8 节：Phase 结束跑全量测试 + Compose 冷启动验证；规划 04 第 3 节要求备份录屏。
- **改动文件**：`docs/dev-log.md`、`PROGRESS.md`、`docs/plans/phase-7.md`
- **契约**：
  - 执行 `mvn test`、`npm run build`、`git diff --check`
  - 执行 README 三命令冷启动验证；如果启动前端容器，验收后按用户要求关闭前端服务
  - 录屏 Gate 不能伪造；若当前环境无法录屏，保持 PROGRESS 未完成并写明待用户执行
- **验收**：命令输出通过；容器最终状态符合用户要求；dev-log 记录做了什么/没做什么/遗留。
- **禁止**：不为了 Gate 假造录屏或篡改进度。

## 4. 本阶段红线
- 禁止：为演示临时加功能、虚构商业用户、虚构性能指标、上线公网、生产监控、Agent 写操作、多 Agent 协作。
- 暂缓（非本阶段做）：Phase 6 Agent、SSE、hybrid 检索、rerank、docx、真实 token 价格精算。

## 5. 风险与预案

| 风险 | 预案 |
|---|---|
| README 写成宣传稿 | 只写可验证功能、命令和本地证据 |
| 录屏 Gate 在当前环境不可完成 | 产出精确脚本和检查表，PROGRESS 不误标完成 |
| 冷启动会启动前端容器 | 验收后执行 `docker compose stop frontend`，最终保持前端关闭 |
| 演示依赖真实 API 不稳定 | 默认 Mock Provider 演示；真实 API 作为可选复测 |
| 叙事范围膨胀 | Roadmap 明确标注未实现，不纳入 MVP 声称 |

## 6. 完成后动作
- [x] 开工前已从最新 `main` 切出 `phase-7` 分支（禁止在 main 上提交）
- [ ] 跑全量测试 + `docker compose up -d` 冷启动验证
- [ ] `docs/dev-log.md` 追加各任务「做了什么/没做什么/遗留」
- [ ] Gate 全过 → 在 `PROGRESS.md` 勾掉本 Phase 并填日期；不过 → 记原因，不进下一阶段
- [ ] 在 `phase-7` 分支做「阶段完成」提交：`git commit -m "phase-7: 演示与交付包装"`
- [ ] Gate 通过后合并：`git checkout main && git merge --no-ff phase-7`

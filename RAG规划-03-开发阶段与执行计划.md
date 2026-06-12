# RAG 项目规划 03 — 开发阶段与执行计划

前置：本计划在 mini-mall Phase 3 收尾验收通过后启动（预计 1–2 周，见规划 01 第 7 节）。
工期按"课余时间 + AI 辅助开发"估算；每个 Phase 末有验收 Gate，**Gate 不过不准进下一阶段**。

执行纪律（适用于所有 Phase）：
- 每个任务给 Codex 的指令必须包含：目标、涉及文件、接口契约（抄规划 02）、验收命令、禁止事项。
- 每个 Phase 结束跑一次全量测试 + Compose 冷启动验证。
- dev-log 习惯延续 mini-mall：每任务记录"做了什么/没做什么/遗留"。

---

## Phase 0：项目初始化与基础设施（2–3 天）

- **目标**：三容器 Compose 冷启动成功，前后端骨架互通，Flyway V1 落库。
- **任务**：
  1. 仓库 `devdocs-rag`：`backend/`（Maven 单模块）+ `frontend/`（Vite）+ `docker-compose.yml` + `.env.example` + `docs/`。
  2. Compose：`pgvector/pgvector:pg16` 镜像（自带扩展，避免手装）、backend、frontend(Nginx)；端口、卷、healthcheck。
  3. Flyway V1：全部 MVP 表（规划 02 第 4 节）+ pgvector 扩展 `CREATE EXTENSION vector` + HNSW 索引 + 种子 admin 用户。
  4. 后端骨架：分层包结构、ApiResponse、全局异常处理、JWT 过滤器、`/actuator/health`。
  5. 前端骨架：路由、Pinia、axios 拦截器（token + 错误码 toast）、登录页。
  6. README 初稿：一句话定位 + 启动三命令。
- **验收**：`docker compose up -d` 后 health 通过；登录拿到 token；`\d document_chunks` 可见 vector 列与 hnsw 索引。
- **模型分工**：Codex 全量实现；Sonnet 审 compose 与 Flyway 脚本。
- **风险**：pgvector 镜像与 Flyway 执行顺序（扩展必须在建表前）。
- **禁止**：引入 Redis/MQ/MinIO/多模块 Maven/Spring Cloud 任何组件。

## Phase 1：知识库与文档管理（3–4 天）

- **目标**：KB CRUD + 上传落盘 + 文档列表/状态/删除全链路（暂无解析）。
- **任务**：API 2–8（规划 02 第 5 节）；StorageService 本地实现；sha256 去重；owner 过滤切面；KB 列表页 + 详情页（上传、表格、状态徽标、删除确认）。
- **验收**：传 `.exe` 422 / 重复文件 409 / 删 KB 后磁盘文件消失；前端全交互可用；单测覆盖校验逻辑。
- **模型分工**：Codex 实现；Sonnet 写前端页面与联调；Opus 不需要。
- **风险**：multipart 大小限制要同时配 Spring 和 Nginx。
- **禁止**：做文件夹/批量上传、断点续传。

## Phase 2：文档解析与切块入库（4–5 天，全项目最难阶段之一）

- **目标**：UPLOADED→PARSING→CHUNKING 状态机跑通，chunk 落库（无向量），失败路径完整。
- **任务**：Tika 集成；MD 标题树解析器；清洗规则；切块算法（严格按规划 02 §3.5 的参数实现）；ingestion_jobs 状态机 + @Async 线程池；"重新解析"幂等重跑；文档详情页任务时间线。
- **验收**：mini-mall 的 README.md / architecture.md / 一份 PDF 三件套入库，chunk 长度全部在 [200,1000] 字，heading_path 抽查 ≥9/10 正确；损坏 PDF 显示 FAILED + 可读原因；同文档重跑不产生重复 chunk。
- **模型分工**：**Opus 负责切块算法实现与单测**（这是质量核心，别交给 Codex 自由发挥）；Codex 做状态机与任务表 CRUD；Sonnet 做前端状态展示。
- **风险**：PDF 千奇百怪——坚持"解析不出就 FAILED"，不写兜底魔法。
- **禁止**：OCR、表格结构化抽取、图片提取、自动语言检测。

## Phase 3：Embedding 与向量检索（3–4 天）

- **目标**：EMBED 阶段完成，检索调试接口/页面可用，检索质量初验。
- **任务**：EmbeddingProvider（OpenAI 兼容 + Mock）；批量调用与退避重试；向量 UPDATE 落库；检索 SQL + service（owner 校验→kb 过滤→top-8→阈值）；检索调试 API + 后台页；**标定阈值**：用 10 个真实 query 看分数分布，定初始阈值并写进配置。
- **验收**：mini-mall 文档库上 10 个标准 query top1 命中预期文档 ≥8/10（评测表存 `docs/eval/retrieval.md`）；Mock 模式全链路可跑；KB 隔离集成测试通过（KB-A 检索不到 KB-B）。
- **模型分工**：Codex 实现 Provider 与 SQL；Opus 审检索 service 与隔离测试；Sonnet 做调试页。
- **风险**：维度不一致（换 embedding 模型须重建全库向量——把 embedding_model 字段校验做进写入路径）。
- **禁止**：引入 Milvus/ES、hybrid 检索、rerank、自动调参。

## Phase 4：RAG 问答闭环（4–5 天，核心阶段）

- **目标**：带引用的问答全链路 + 无答案 + 历史 + 调用日志。
- **任务**：Prompt 构造器（模板进资源文件带版本号）+ 单测；ChatProvider（DeepSeek + Mock）；阈值短路；引用解析与落库（含快照、UNGROUNDED 规则）；conversations/messages API；聊天页 + 引用卡片 + 历史页；model_call_logs 全量埋点；**评测集**：`docs/eval/questions.md` 20 库内题 + 5 库外题，人工跑分记录。
- **验收**：评测集库内题"有依据回答"≥16/20，库外题 5/5 走 NO_ANSWER；拔 key 演示返回 50201 且日志可查；历史回看引用完整。
- **模型分工**：Opus 负责 prompt 构造器 + 引用解析（边界多）；Codex 做会话 CRUD 与埋点；Sonnet 做聊天页。
- **风险**：模型不守 `[n]` 格式——先把 system 规则写死、temperature 0.2，仍违规则走 UNGROUNDED 兜底，不要试图后处理修复。
- **禁止**：多轮查询改写、跨库联检、SSE（放展示版）、答案缓存。

## Phase 5：后台管理与可观测性（2–3 天）

- **目标**：后台三页（ingestion 日志/模型调用日志/检索调试已具备）补齐 + 统计卡片（展示版项）。
- **任务**：API 14/15/17；失败任务排查动线（列表→展开原因→跳转文档→重跑）；token 汇总。
- **验收**：演示动线"上传坏文件→后台定位失败原因→重跑成功"一镜到底。
- **模型分工**：Sonnet 全包；Codex 补查询接口。
- **禁止**：Prometheus/Grafana、链路追踪、告警。

## Phase 6：Agent 增强（可选，4–5 天，默认跳过）

- **进入条件**：Phase 0–5 + Phase 7 全部完成且 mini-mall 已收尾，仍有 ≥1 周富余。
- **任务与边界**：严格按规划 02 第 8 节（3 个只读工具、≤5 步、只生成不执行、tool_call_logs、独立页签）。
- **验收**：对"mini-mall 知识库"输入"为支付服务补充退款功能生成开发任务清单"，产出含文档引用的任务列表，工具轨迹可展开。
- **模型分工**：Opus 设计循环与工具协议；Codex 实现；Sonnet 页面。
- **禁止**：写操作工具、自动执行、多 Agent 协作、计划修改回写。

## Phase 7：演示、README、简历包装（2–3 天，不可裁剪）

- **目标**：5 分钟演示一镜到底 + README 完整 + 面试材料就绪。
- **任务**：演示语料定稿（mini-mall 文档精选 8–10 份）；演示脚本（见规划 04）排练 ≥2 遍并录屏备份；README（结构见规划 04）；架构图（按规划 02 §1 重绘）；面试 Q&A 文档化；`docs/eval/` 跑分结果入库。
- **验收**：换一台干净机器（或删 volume）按 README 三命令冷启动成功；录屏 ≤6 分钟。
- **模型分工**：Fable 5 审 README 与叙事；Sonnet 写初稿。
- **禁止**：为演示临时加功能。

### 总工期

| 阶段 | 工期 | 累计 |
|---|---|---|
| mini-mall Phase 3 收尾 | 1–2 周 | — |
| Phase 0–1 | ~1 周 | 1 周 |
| Phase 2–3 | ~1.5 周 | 2.5 周 |
| Phase 4–5 | ~1.5 周 | 4 周 |
| Phase 7 | 0.5 周 | **4.5 周（MVP 完）** |
| Phase 6（可选） | +1 周 | 5.5 周 |

---

## 模型分工总则（Codex / Opus / Sonnet / Fable 5）

| 模型 | 职责 | 典型任务 |
|---|---|---|
| Fable 5 | Phase Gate 验收、契约变更裁决、范围防失控 | 每 Phase 结束审一次；任何"要不要加 X"的问题先问它 |
| Opus | 跨边界与高错误成本代码 | 切块算法、prompt 构造、引用解析、Agent 循环、疑难 bug |
| Sonnet | 常规开发 | 前端页面、CRUD、联调、测试补全、文档初稿 |
| Codex | 批量实现 | 按任务卡写模板化代码（Controller/Service/Repository/SQL） |

任务卡模板（发给 Codex/Sonnet 时使用）：
```
## 任务: <一句话>
## 上下文: 规划 02 第 X 节(粘贴相关段落)
## 改动文件: <列表>
## 契约: <API/SQL/接口签名, 直接粘贴>
## 验收: <可执行命令或可点击路径>
## 禁止: <本任务不许碰的范围>
```

---

## 风险与避坑（15 项）

| # | 难点 | 风险 | MVP 处理 | 后续增强 |
|---|---|---|---|---|
| 1 | 文档解析质量 | PDF 乱码/乱序毁掉下游一切 | 演示语料 MD 为主；解析失败果断 FAILED 并展示原因 | 接专业解析 API |
| 2 | chunk 粒度 | 过碎丢上下文、过大稀释相似度 | 固定参数(600–900/120 重叠) + 检索调试页人工核 | 标题感知动态粒度 |
| 3 | embedding 成本 | 失控扣费 | bge-m3 免费档；批量化；sha256 文档去重；日志可统计 | chunk 内容 hash 缓存 |
| 4 | 检索不相关 | 答案张冠李戴 | 阈值短路 + 调试页可视化 + 10 query 标定 | hybrid + rerank |
| 5 | 没有引用 | 变套壳 | 引用是 P0 验收项, 无引用即 UNGROUNDED 标注 | 句级归因 |
| 6 | 幻觉 | 答辩现场翻车 | 五层防线(见规划 02 §7) + 评测集 | 自动化评测 |
| 7 | 多知识库隔离 | 检索串库 | kb_id 冗余列 + 强制 WHERE + 隔离集成测试 | 行级安全策略 |
| 8 | 权限过滤 | 越权访问 | owner 校验前置于一切检索 | RBAC(不建议) |
| 9 | 文档更新与向量删除 | 脏向量残留 | MVP 不做"更新", 只做删除+重传; chunk 外键 CASCADE | 文档版本化 |
| 10 | 模型调用失败 | 静默失败最致命 | 显式错误码 50201/50202 + 日志 + 拔 key 演示 | 多供应商 failover |
| 11 | 前后端联调 | 拖进度 | 契约先行(规划 02 §5 即契约), 后端先于页面交付 mock 数据 | — |
| 12 | Docker 环境 | "我机器上是好的" | pgvector 官方镜像; Phase 7 干净机冷启动验收 | — |
| 13 | 向量库迁移 | 换库重写 | 不迁移; 检索收口在 RetrievalPipeline 一个接口后面 | 真要换时只动一层 |
| 14 | Agent 过度设计 | 吞掉全部工期 | 默认跳过 Phase 6; 铁律见规划 02 §8 | — |
| 15 | 套壳化 | 简历减分 | 五件套全进 MVP: 引用/无答案/ingestion 可观测/检索调试/评测集 | — |

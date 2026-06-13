# PROGRESS — DevDocs RAG 进度看板

> 本文件是**唯一**的状态落点（CLAUDE.md 第 8 节）。规划书与 CLAUDE.md 不维护进度。
> 规则：一个 Phase 的全部 Gate 通过、且跑过全量测试 + Compose 冷启动后，才把状态改为 ✅ 并填完成日期。
> Gate 不过保持 ⬜ 并在「备注」记原因；上一个 Phase 未 ✅，下一个不许开工。

最近更新：2026-06-13（Phase 1 验收通过）

## 阶段总览

| Phase | 内容 | 状态 | 完成日期 | 规划文档 | 备注 |
|---|---|---|---|---|---|
| 前置 | mini-mall Phase 3 收尾验收 | ✅ | 2026-06-13 | — | 本地证据：mini-mall `phase3-ai-inventory-assistant` TaskMaster 14/14 done，`docs/phase3-acceptance.md` 记录验收通过 |
| 0 | 初始化：3 容器 Compose + Flyway V1 + 前后端骨架 | ✅ | 2026-06-13 | docs/plans/phase-0.md | 三容器 healthy；登录拿 token；`document_chunks.embedding vector(1024)` 与 HNSW 索引已验收 |
| 1 | KB CRUD + 上传落盘 + 文档列表/状态/删除 | ✅ | 2026-06-13 | docs/plans/phase-1.md | `.exe` 422；重复文件 409；删除 KB 后磁盘文件已清理；前端列表/详情/上传/删除可用 |
| 2 | 解析 + 切块入库（无向量），失败路径完整 | ⬜ | — | docs/plans/phase-2.md | 全项目最难阶段之一 |
| 3 | Embedding + 向量检索 + 检索调试页 + 标定阈值 | ⬜ | — | docs/plans/phase-3.md | |
| 4 | RAG 问答闭环 + 引用 + 无答案 + 历史 + 调用日志 | ⬜ | — | docs/plans/phase-4.md | 核心阶段 |
| 5 | 后台三页补齐 + 统计卡片（展示版） | ⬜ | — | docs/plans/phase-5.md | |
| 6 | Agent 增强 | ⏭️ 默认跳过 | — | — | 仅 0–5+7 完成且 ≥1 周富余才做 |
| 7 | 演示 / README / 简历包装 | ⬜ | — | docs/plans/phase-7.md | 不可裁剪 |

状态图例：⬜ 未开始 · 🔧 进行中 · ✅ 验收通过 · ⏭️ 跳过

## 各 Phase 验收 Gate 清单

> 勾选逐条 Gate；全勾才能把上表对应 Phase 改为 ✅。Gate 原文见 CLAUDE.md 第 7 节 / RAG规划-03。

### Phase 0
- [x] `docker compose up -d` 后 health 通过
- [x] 登录拿到 token
- [x] `\d document_chunks` 可见 vector 列与 hnsw 索引

### Phase 1
- [x] 传 `.exe` 返回 422
- [x] 重复文件返回 409
- [x] 删 KB 后磁盘文件消失
- [x] 前端全交互可用 + 校验逻辑有单测

### Phase 2
- [ ] 三件套（README.md / architecture.md / 一份 PDF）入库，chunk 长度全在 [200,1000] 字
- [ ] heading_path 人工抽查 ≥9/10 正确
- [ ] 损坏 PDF 显示 FAILED + 可读原因
- [ ] 同文档重跑不产生重复 chunk

### Phase 3
- [ ] 10 个标准 query top1 命中预期文档 ≥8/10（记入 `docs/eval/retrieval.md`）
- [ ] Mock 模式全链路可跑
- [ ] KB 隔离集成测试通过（KB-A 检索不到 KB-B）

### Phase 4
- [ ] 评测集库内题「有依据回答」≥16/20，库外题 5/5 走 NO_ANSWER
- [ ] 拔 key 演示返回 50201 且日志可查
- [ ] 历史回看引用完整
- [ ] 评测结果记入 `docs/eval/questions.md`

### Phase 5
- [ ] 「上传坏文件 → 后台定位失败原因 → 重跑成功」一镜到底

### Phase 7
- [ ] 干净机器（或删 volume）按 README 三命令冷启动成功
- [ ] 演示录屏 ≤6 分钟

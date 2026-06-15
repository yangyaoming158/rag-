# Demo Corpus

演示知识库建议命名为 `mini-mall 项目文档`。语料来自相邻 mini-mall 项目的真实工程文档，用来展示“给自己的微服务项目做文档问答”。

## 推荐语料 8 份

| # | 文件 | 演示价值 |
|---:|---|---|
| 1 | `/home/oslab/projects/mini-mall-order/README.md` | 项目总览、模块、运行方式、AI 助手入口 |
| 2 | `/home/oslab/projects/mini-mall-order/docs/architecture.md` | 网关、认证、消息、观测、压测等架构细节 |
| 3 | `/home/oslab/projects/mini-mall-order/docs/phase3-ai-inventory-contract.md` | Phase 3 AI 库存助手契约和禁止范围 |
| 4 | `/home/oslab/projects/mini-mall-order/docs/phase3-acceptance.md` | Phase 3 验收证据和 full flow 截图说明 |
| 5 | `/home/oslab/projects/mini-mall-order/docs/phase2-admin-api-contract.md` | Admin API、trusted headers、gateway ownership |
| 6 | `/home/oslab/projects/mini-mall-order/docs/phase2-5-ai-inventory-contract.md` | 入库草稿、AI suggestion、库存变更基础能力 |
| 7 | `/home/oslab/projects/mini-mall-order/admin-frontend/README.md` | Admin frontend 页面与 dev server 信息 |
| 8 | `/home/oslab/projects/mini-mall-order/frontend/README.md` | Customer frontend 页面与 dev server 信息 |

## 可选补充 2 份

| # | 文件 | 演示价值 |
|---:|---|---|
| 9 | `/home/oslab/projects/mini-mall-order/docs/dev-log.md` | 开发过程证据和取舍记录 |
| 10 | `/home/oslab/projects/mini-mall-order/docs/phase2-acceptance.md` | Phase 2 验收路径和 API 证据 |

如果相邻 mini-mall 项目不存在，可以改用本仓库的规划文档和 README 作为语料，但演示问题需要对应调整。

## 已使用评测语料

`docs/eval/retrieval.md` 和 `docs/eval/questions.md` 使用的是前 8 份语料。Mock Provider 基线：

- 检索 10 query top1 命中：8/10。
- 问答 20 道库内题：19/20 有引用回答。
- 问答 5 道库外题：5/5 `NO_ANSWER`。

## 上传顺序

1. 先上传 `README.md` 和 `docs/architecture.md`，确认基础链路可用。
2. 再上传 Phase 2/3 contract 与 acceptance 文档，增强问答覆盖面。
3. 最后上传 frontend/admin frontend README，覆盖端口、页面和运行说明类问题。

## 建议演示问题映射

| 问题 | 主要命中文档 |
|---|---|
| `gateway JWT trusted headers CORS rate limiting 是怎么处理的？` | `architecture.md` |
| `AI inventory assistant 的 admin console 页面提供哪些能力？` | `README.md`、`admin-frontend/README.md` |
| `X-User-Role trusted headers 在 admin API 中如何处理？` | `phase2-admin-api-contract.md` |
| `Phase 3 acceptance 的 full flow 验收证据是什么？` | `phase3-acceptance.md` |
| `customer frontend 和 admin frontend 的 dev server 端口是什么？` | `frontend/README.md`、`admin-frontend/README.md` |

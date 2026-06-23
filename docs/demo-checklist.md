# DevDocs RAG Demo Checklist

用途：发布前、录屏前或现场演示前逐项确认。默认以 Mock Provider 演示，避免网络和模型供应商波动。

## 1. 环境准备

- [ ] 已确认 `.env` 不会提交，真实 key 只留在本机。
- [ ] 如需完全按 GitHub 展示版验证，使用 `.env.example` 启动。
- [ ] Docker Desktop / Docker Engine 可用。
- [ ] 端口 `3000`、`8080`、`5433` 未被其他服务占用。
- [ ] 已执行：

```bash
cp .env.example .env
docker compose up -d --build
```

## 2. 启动验收

- [ ] `docker compose ps` 中 `postgres` 为 healthy。
- [ ] `docker compose ps` 中 `backend` 为 healthy。
- [ ] `docker compose ps` 中 `frontend` 为 healthy。
- [ ] 后端健康检查返回 `UP`：

```bash
curl http://localhost:8080/actuator/health
```

- [ ] 默认账号可登录：

```bash
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

- [ ] 浏览器可打开 `http://localhost:3000`。

## 3. 演示语料

- [ ] 知识库名称建议为 `mini-mall 项目文档`。
- [ ] 推荐 8 份语料与 [demo-corpus.md](demo-corpus.md) 一致。
- [ ] 所有演示文档状态均为 `READY`。
- [ ] 文档总 chunk 数大于 0。
- [ ] 至少能看到一条 ingestion job 时间线。

## 4. 问答路径

- [ ] 进入知识库问答页。
- [ ] 库内问题返回 `OK`，且回答正文包含 `[1]`、`[2]` 等引用编号。
- [ ] 引用卡片展示来源文档、相似度和原文片段。
- [ ] 推荐库内问题：

```text
gateway JWT trusted headers CORS rate limiting 是怎么处理的？
```

- [ ] 库外问题返回 `NO_ANSWER`。
- [ ] 推荐库外问题：

```text
学校食堂营业时间是什么？
```

## 5. 检索调试

- [ ] 知识库详情页的“检索调试”可返回 topK chunk。
- [ ] 管理后台的“检索调试”可选择知识库并返回结果。
- [ ] 结果中能看到来源文档、chunk 编号、分数和内容预览。
- [ ] Hybrid Search 展示字段与 [eval/hybrid-search.md](eval/hybrid-search.md) 口径一致：vector score、keyword score、final score。

## 6. 后台可观测

- [ ] 管理后台“概览”能看到知识库、文档、chunk、token 和平均延迟。
- [ ] “Ingestion 日志”能按状态过滤并展开失败原因。
- [ ] “模型调用日志”能看到 EMBEDDING / CHAT、provider、model、token、延迟和状态。
- [ ] “回答反馈”能回看低质量回答、原问题、引用和模型调用信息。

## 7. 失败路径

- [ ] 上传少于 100 字的 `.txt` 文件会进入 `FAILED`，错误原因可见。
- [ ] 说明这种失败不可通过重跑修复，这是正确行为。
- [ ] 如需演示“重跑成功”，使用可恢复失败：临时配置不可用 embedding endpoint，失败后恢复 Mock Provider，再触发重跑。

## 8. 截图和评测口径

- [ ] README 的 10 张截图均能对应到当前页面能力。
- [ ] `docs/demo-script.md` 的 5 分钟路径可顺畅完成。
- [ ] `docs/eval/retrieval.md` 的 Mock 检索指标仍用于离线链路基线。
- [ ] `docs/eval/questions.md` 的 Mock 问答指标仍用于离线问答基线。
- [ ] `docs/eval/real-provider-baseline.md` 只用于真实 Provider 效果说明，不与 Mock KB 混用。

## 9. 收尾

- [ ] 演示过程中没有展示真实 API key。
- [ ] 演示后如需释放容器，执行：

```bash
docker compose down
```

- [ ] 如需保留演示数据，不执行 `docker compose down -v`。

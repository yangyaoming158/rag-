# Document Versioning Design

日期：2026-06-19

状态：生产化设计，MVP 暂不实现

## 背景

当前 MVP 的文档更新策略是删除旧文档后重新上传。这个策略适合答辩演示和小规模知识库：

- 数据模型简单：`documents -> document_chunks` 级联删除。
- 检索规则简单：只查当前存在的 READY 文档。
- 失败恢复简单：同一文档重新解析会清理旧 chunks 后重建。

但生产环境会出现更复杂的问题：

- 同名文档需要保留历史版本，不能只靠删除覆盖。
- 旧问答的 citation 需要可回放，不能因为文档更新就丢失上下文。
- 新版本解析或 embedding 失败时，不能影响线上仍可用的旧版本。
- 审计场景需要知道某次回答基于哪个版本的内容。

## 目标

生产化版本需要满足：

- 一个逻辑文档可以有多个版本。
- 默认问答只检索 latest READY version。
- 新版本入库失败时，旧 READY version 继续可用。
- 历史 citation 保留回答当时的快照。
- 管理页面可以查看旧版本，但旧版本不参与默认检索。

## 推荐数据模型

```sql
documents
- id
- kb_id
- current_version_id
- original_filename
- created_at
- updated_at

document_versions
- id
- document_id
- version_no
- sha256
- content_type
- file_size
- storage_path
- status
- error_message
- chunk_count
- created_at
- updated_at

document_chunks
- id
- document_version_id
- kb_id
- chunk_index
- content
- heading_path
- page_start
- page_end
- char_len
- embedding
- embedding_model
- created_at
```

约束建议：

```sql
UNIQUE (document_id, version_no)
UNIQUE (document_id, sha256)
FOREIGN KEY (documents.current_version_id) REFERENCES document_versions(id)
FOREIGN KEY (document_chunks.document_version_id) REFERENCES document_versions(id)
```

`document_chunks` 保留 `kb_id` 是为了继续让检索 SQL 强制知识库隔离，避免每次检索都必须从 chunks join 到 versions 再 join documents 才能过滤 KB。

## 入库规则

上传同名文档时不直接覆盖旧数据，而是创建新版本：

```text
documents(id=10, original_filename=architecture.md)
  v1 READY
  v2 EMBEDDING
```

新版本流程：

```text
UPLOADED -> PARSE -> CHUNK -> EMBED -> READY
```

只有当 `document_versions.status = READY` 后，才把 `documents.current_version_id` 指向新版本。这样 v2 失败时，v1 仍然是默认检索版本。

## 检索规则

默认检索只查当前版本：

```sql
SELECT c.*
FROM document_chunks c
JOIN document_versions v ON v.id = c.document_version_id
JOIN documents d ON d.current_version_id = v.id
WHERE c.kb_id = ?
  AND v.status = 'READY'
```

历史版本查看、审计回放和对比页面可以显式传 `document_version_id`，但默认 RAG 问答不检索旧版本。

## Citation 快照

历史引用不能只保存 `chunk_id`。生产化 citation 应保存回答当时的快照：

```text
message_id
document_id
document_version_id
chunk_id
rank
similarity
document_filename_snapshot
heading_path_snapshot
snippet_snapshot
```

原因：

- 文档版本删除、归档或重嵌入后，历史回答仍能展示当时的依据。
- `heading_path` 变化不会污染旧回答。
- 审计时可以明确回答来自哪个版本。

当前 MVP 的 Review 已经把 `heading_path` 快照写入 `review_citations`；问答 `citations` 表仍有历史定位不够稳的问题，已在 `docs/eval/failure-cases.md` 记录。

## 为什么 MVP 先采用删除和重传

MVP 目标是验证 RAG 工程闭环：上传、解析、切块、embedding、检索、问答、引用、后台日志和失败重跑。版本化会引入更多状态：

- `documents.current_version_id` 与 version 状态切换。
- chunks 从 `document_id` 改为 `document_version_id`。
- 文档列表、详情页和后台日志都要区分逻辑文档与版本。
- 删除策略要区分删除逻辑文档、归档旧版本和清理物理文件。

这些复杂度对 MVP 演示价值不高，所以当前选择删除+重传。生产化时再引入版本化，能保持主链路清晰，也能把版本迁移作为独立设计点讲清楚。

## 迁移策略

从当前 schema 演进时，推荐分三步：

1. 新增 `document_versions`，为每条现有 `documents` 创建 v1。
2. 给 `document_chunks` 增加 `document_version_id` 并回填到 v1。
3. 代码改为读写版本表，稳定后再把旧的 `documents.storage_path/status/chunk_count` 等字段下沉或废弃。

迁移期间可以保留兼容字段，避免一次性重写 ingestion、retrieval 和前端详情页。

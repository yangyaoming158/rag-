# Hybrid Search Design

日期：2026-06-18

状态：MVP 已实现

## 背景

DevDocs RAG 的 MVP 只使用 pgvector 向量检索。真实 Provider 基线显示，纯向量检索能回答大多数问题，但在软件工程文档场景存在两个明显边界：

- 工程术语、接口路径、配置项和状态名需要精确召回，例如 `/api/admin/inventories`、`X-User-Role`、`PENDING_REVIEW`。
- 多份文档会描述同一个主题，纯向量 Top1 容易命中相邻文档，例如 gateway、trusted headers、CORS 和 rate limiting 同时出现在架构文档和接口契约文档中。

因此 P1 增加 Hybrid Search，不引入 Elasticsearch 或 Milvus 集群，先用 PostgreSQL 内完成 MVP。

## 目标

```text
vector search topN
+
keyword search topN
+
RRF fusion
```

检索调试页展示：

```text
vector score
keyword score
final score
```

RAG 问答复用 hybrid 排序，但仍使用 vector similarity 做当前拒答阈值，避免 keyword-only 命中导致无根据回答。

## 当前实现

核心文件：

```text
RetrievalRepository.java
RetrievalService.java
RetrievalHit.java
RetrievalHitDto.java
KbDetailView.vue
AdminView.vue
```

### Vector Search

向量检索继续使用 pgvector：

```sql
ORDER BY c.embedding <=> ?::vector
```

向量分数保持为：

```text
similarity = 1 - cosine_distance
```

该分数继续用于：

- `RAG_RETRIEVAL_MIN_SIMILARITY`
- `aboveThreshold`
- `NO_ANSWER` 短路判断：hybrid 返回候选中至少一个 vector similarity 过阈值，才允许进入 Chat

### Keyword Search

关键词检索使用 PostgreSQL `ILIKE` MVP，不新增 schema 和索引。

搜索文本：

```text
original_filename + heading_path + chunk content
```

query 先抽取最多 12 个 token，支持这些工程形式：

```text
/api/admin/inventories
X-User-Role
PENDING_REVIEW
common-auth
RAG_AI_CHAT_PROVIDER
```

keyword score：

```text
matched_terms / total_terms
```

### RRF Fusion

向量和关键词分别取候选 topN：

```text
candidateLimit = min(100, topK * 3)
```

融合公式：

```text
finalScore = 1 / (60 + vectorRank) + 1 / (60 + keywordRank)
```

只有向量命中时：

```text
finalScore = 1 / (60 + vectorRank)
```

只有关键词命中时：

```text
finalScore = 1 / (60 + keywordRank)
```

排序规则：

```text
finalScore desc
vectorScore desc
keywordScore desc
chunkId asc
```

## API 兼容

`RetrievalHitDto` 保留原有字段：

```text
similarity
aboveThreshold
```

新增字段：

```text
keywordScore
finalScore
```

其中：

- `similarity` 仍表示 vector similarity。
- `keywordScore` 表示关键词命中比例。
- `finalScore` 表示 RRF 融合分数。

## 前端展示

KB 详情页检索调试：

```text
F finalScore
V vectorScore / K keywordScore
```

管理后台检索调试：

```text
进度条按 RRF finalScore 归一化
F finalScore
V vectorScore / K keywordScore
```

## 边界

当前版本有意不做：

- Elasticsearch。
- PostgreSQL GIN full-text index。
- rerank model。
- query rewrite。
- synonym dictionary。

原因：

- 当前数据量小，ILIKE MVP 足够验证工程术语召回价值。
- P1 的重点是证明系统能解释并改善召回，不是引入重型搜索基础设施。
- 检索层已收口在 `RetrievalRepository`，后续可以把 keyword search 替换为 PostgreSQL full-text search 或 Elasticsearch。

## 后续改进

优先级从高到低：

1. 将 keyword search 从 ILIKE 替换为 PostgreSQL full-text search + GIN index。
2. keyword score 区分 exact phrase、token、filename、heading 和 content 权重。
3. 在调试页展示 vector rank 和 keyword rank。
4. 对 keyword-only 命中增加独立阈值，再决定是否允许进入 RAG prompt。
5. 增加 rerank，对 topN hybrid candidates 做最终排序。

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <el-button text :icon="ArrowLeft" @click="router.push('/')">返回</el-button>
        <h1>管理后台</h1>
        <p>Ingestion、模型调用、检索调试与展示版统计</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="refreshing" @click="refreshCurrent">刷新</el-button>
      </div>
    </header>

    <section class="page-content">
      <el-tabs v-model="activeTab" class="admin-tabs">
        <el-tab-pane label="概览" name="overview">
          <div v-loading="statsLoading" class="stats-grid">
            <div class="stat-card">
              <el-icon><Collection /></el-icon>
              <div>
                <span>知识库</span>
                <strong>{{ stats?.kbCount ?? 0 }}</strong>
              </div>
            </div>
            <div class="stat-card">
              <el-icon><Document /></el-icon>
              <div>
                <span>文档</span>
                <strong>{{ stats?.docCount ?? 0 }}</strong>
              </div>
            </div>
            <div class="stat-card">
              <el-icon><Tickets /></el-icon>
              <div>
                <span>Chunks</span>
                <strong>{{ stats?.chunkCount ?? 0 }}</strong>
              </div>
            </div>
            <div class="stat-card">
              <el-icon><Timer /></el-icon>
              <div>
                <span>Token / 平均延迟</span>
                <strong>{{ stats?.tokenSum ?? 0 }} / {{ stats?.avgLatencyMs ?? 0 }} ms</strong>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="Ingestion 日志" name="ingestion">
          <div class="table-tools">
            <el-select v-model="ingestionStatus" clearable placeholder="状态" @change="reloadIngestionJobs">
              <el-option label="PENDING" value="PENDING" />
              <el-option label="RUNNING" value="RUNNING" />
              <el-option label="SUCCEEDED" value="SUCCEEDED" />
              <el-option label="FAILED" value="FAILED" />
            </el-select>
            <el-button :icon="Refresh" :loading="ingestionLoading" @click="loadIngestionJobs">刷新</el-button>
          </div>

          <el-table
            :data="ingestionJobs"
            v-loading="ingestionLoading"
            class="data-table"
            row-key="id"
          >
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div class="expanded-row">
                  <el-alert
                    v-if="row.errorMessage"
                    :title="row.errorMessage"
                    type="error"
                    :closable="false"
                  />
                  <el-descriptions :column="3" border>
                    <el-descriptions-item label="KB">{{ row.kbName }} (#{{ row.kbId }})</el-descriptions-item>
                    <el-descriptions-item label="文档">{{ row.documentFilename }}</el-descriptions-item>
                    <el-descriptions-item label="文档状态">{{ row.documentStatus }}</el-descriptions-item>
                    <el-descriptions-item label="开始">{{ formatTime(row.startedAt) }}</el-descriptions-item>
                    <el-descriptions-item label="结束">{{ formatTime(row.finishedAt) }}</el-descriptions-item>
                    <el-descriptions-item label="创建">{{ formatTime(row.createdAt) }}</el-descriptions-item>
                  </el-descriptions>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="documentFilename" label="文档" min-width="220">
              <template #default="{ row }">
                <span class="filename">{{ row.documentFilename }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="kbName" label="知识库" min-width="180" />
            <el-table-column prop="phase" label="阶段" width="100" />
            <el-table-column prop="status" label="任务状态" width="130">
              <template #default="{ row }">
                <el-tag :type="jobStatusTag(row.status)" effect="plain">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="documentStatus" label="文档状态" width="130">
              <template #default="{ row }">
                <el-tag :type="documentStatusTag(row.documentStatus)" effect="plain">{{ row.documentStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Attempt" width="110">
              <template #default="{ row }">{{ row.attempt }} / {{ row.maxAttempt }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button text :icon="View" @click="openDocument(row)">文档</el-button>
                <el-button
                  text
                  :icon="RefreshRight"
                  :disabled="isProcessing(row.documentStatus)"
                  @click="confirmReingest(row)"
                >
                  重跑
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="ingestionPage.totalElements > ingestionPage.size"
            layout="prev, pager, next"
            :current-page="ingestionPage.page + 1"
            :page-size="ingestionPage.size"
            :total="ingestionPage.totalElements"
            @current-change="changeIngestionPage"
          />
        </el-tab-pane>

        <el-tab-pane label="模型调用日志" name="model-calls">
          <div class="table-tools">
            <el-select v-model="modelType" clearable placeholder="类型" @change="reloadModelCalls">
              <el-option label="CHAT" value="CHAT" />
              <el-option label="EMBEDDING" value="EMBEDDING" />
            </el-select>
            <el-select v-model="modelStatus" clearable placeholder="状态" @change="reloadModelCalls">
              <el-option label="OK" value="OK" />
              <el-option label="ERROR" value="ERROR" />
            </el-select>
            <el-button :icon="Refresh" :loading="modelLoading" @click="loadModelCalls">刷新</el-button>
          </div>

          <el-table :data="modelCalls" v-loading="modelLoading" class="data-table" row-key="id">
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div class="expanded-row">
                  <el-alert
                    v-if="row.errorMessage"
                    :title="row.errorMessage"
                    type="error"
                    :closable="false"
                  />
                  <el-descriptions :column="3" border>
                    <el-descriptions-item label="messageId">{{ row.messageId || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="documentId">{{ row.documentId || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="文档">{{ row.documentFilename || '-' }}</el-descriptions-item>
                  </el-descriptions>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="callType" label="类型" width="120" />
            <el-table-column prop="provider" label="Provider" width="130" />
            <el-table-column prop="model" label="Model" min-width="160" />
            <el-table-column label="Tokens" width="130">
              <template #default="{ row }">{{ tokenTotal(row) }}</template>
            </el-table-column>
            <el-table-column prop="latencyMs" label="延迟" width="110">
              <template #default="{ row }">{{ row.latencyMs ?? '-' }} ms</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'OK' ? 'success' : 'danger'" effect="plain">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="modelPage.totalElements > modelPage.size"
            layout="prev, pager, next"
            :current-page="modelPage.page + 1"
            :page-size="modelPage.size"
            :total="modelPage.totalElements"
            @current-change="changeModelPage"
          />
        </el-tab-pane>

        <el-tab-pane label="回答反馈" name="feedback">
          <div class="table-tools">
            <el-select v-model="feedbackRating" clearable placeholder="反馈类型" @change="reloadQaFeedback">
              <el-option
                v-for="option in feedbackOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-button :icon="Refresh" :loading="feedbackLoading" @click="loadQaFeedback">刷新</el-button>
          </div>

          <el-table :data="qaFeedback" v-loading="feedbackLoading" class="data-table" row-key="id">
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div class="expanded-row">
                  <el-alert
                    v-if="row.comment"
                    :title="row.comment"
                    type="warning"
                    :closable="false"
                  />
                  <el-descriptions :column="3" border>
                    <el-descriptions-item label="用户">{{ row.username }} (#{{ row.userId }})</el-descriptions-item>
                    <el-descriptions-item label="messageId">{{ row.messageId }}</el-descriptions-item>
                    <el-descriptions-item label="conversationId">{{ row.conversationId }}</el-descriptions-item>
                    <el-descriptions-item label="Provider">{{ row.provider || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="Model">{{ row.model || '-' }}</el-descriptions-item>
                    <el-descriptions-item label="Token">{{ feedbackTokenTotal(row) }}</el-descriptions-item>
                  </el-descriptions>
                  <div class="feedback-block">
                    <strong>原问题</strong>
                    <p>{{ row.question || '-' }}</p>
                  </div>
                  <div class="feedback-block">
                    <strong>回答</strong>
                    <p>{{ row.answer }}</p>
                  </div>
                  <div class="feedback-block">
                    <strong>引用</strong>
                    <el-empty v-if="row.citations.length === 0" description="暂无引用" />
                    <div v-else class="feedback-citations">
                      <div v-for="citation in row.citations" :key="`${row.id}-${citation.rank}`" class="feedback-citation">
                        <div class="citation-title">
                          <strong>[{{ citation.rank }}] {{ citation.documentFilename }}</strong>
                          <el-tag size="small" effect="plain">{{ citation.similarity.toFixed(3) }}</el-tag>
                        </div>
                        <span v-if="citation.headingPath">{{ citation.headingPath }}</span>
                        <p>{{ citation.snippet }}</p>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="rating" label="反馈" width="150">
              <template #default="{ row }">
                <el-tag :type="feedbackTag(row.rating)" effect="plain">{{ feedbackLabel(row.rating) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="kbName" label="知识库" min-width="180" />
            <el-table-column label="问题" min-width="260">
              <template #default="{ row }">
                <p class="text-preview">{{ row.question || '-' }}</p>
              </template>
            </el-table-column>
            <el-table-column label="回答状态" width="120">
              <template #default="{ row }">
                <el-tag :type="answerStatusTag(row.answerStatus)" effect="plain">{{ row.answerStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Provider / Model" min-width="210">
              <template #default="{ row }">
                <div class="provider-cell">
                  <strong>{{ row.provider || '-' }}</strong>
                  <span>{{ row.model || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="延迟" width="140">
              <template #default="{ row }">{{ feedbackLatency(row) }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="反馈时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button text :icon="ChatDotRound" @click="openFeedbackConversation(row)">会话</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="feedbackPage.totalElements > feedbackPage.size"
            layout="prev, pager, next"
            :current-page="feedbackPage.page + 1"
            :page-size="feedbackPage.size"
            :total="feedbackPage.totalElements"
            @current-change="changeFeedbackPage"
          />
        </el-tab-pane>

        <el-tab-pane label="检索调试" name="retrieval">
          <div class="retrieval-form">
            <div class="retrieval-controls">
              <el-select v-model="retrievalKbId" filterable placeholder="选择知识库">
                <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
              </el-select>
              <el-input-number v-model="retrievalTopK" :min="1" :max="20" controls-position="right" />
              <el-button type="primary" :icon="Search" :loading="retrievalLoading" @click="runRetrievalDebug">
                检索
              </el-button>
            </div>
            <el-input
              v-model="retrievalQuery"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
              placeholder="输入 query"
            />
          </div>

          <el-empty v-if="retrievalResult && retrievalResult.hits.length === 0" description="暂无命中" />
          <el-table
            v-else-if="retrievalResult"
            :data="retrievalResult.hits"
            class="data-table"
            row-key="chunkId"
          >
            <el-table-column prop="rank" label="#" width="64" />
            <el-table-column label="Score" width="190">
              <template #default="{ row }">
                <div class="score-cell">
                  <el-progress :percentage="scorePercent(row.finalScore)" :show-text="false" />
                  <div>
                    <strong>F {{ row.finalScore.toFixed(3) }}</strong>
                    <span>V {{ row.similarity.toFixed(3) }} / K {{ row.keywordScore.toFixed(3) }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="来源" min-width="220">
              <template #default="{ row }">
                <div class="hit-source">
                  <strong>{{ row.documentFilename }}</strong>
                  <span>chunk #{{ row.chunkIndex }}</span>
                  <span v-if="row.headingPath">{{ row.headingPath }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="预览" min-width="360">
              <template #default="{ row }">
                <p class="hit-preview">{{ row.contentPreview }}</p>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </main>
</template>

<script setup lang="ts">
import {
  ArrowLeft,
  ChatDotRound,
  Collection,
  Document,
  Refresh,
  RefreshRight,
  Search,
  Tickets,
  Timer,
  View
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as adminApi from '../api/admin'
import type { AdminIngestionJobDto, AdminQaFeedbackDto, ModelCallDto, StatsOverviewDto } from '../api/admin'
import * as kbApi from '../api/kbs'
import type { KbDto, RetrievalDebugResponse } from '../api/kbs'

const router = useRouter()
const activeTab = ref('overview')
const stats = ref<StatsOverviewDto | null>(null)
const kbs = ref<KbDto[]>([])
const ingestionJobs = ref<AdminIngestionJobDto[]>([])
const modelCalls = ref<ModelCallDto[]>([])
const qaFeedback = ref<AdminQaFeedbackDto[]>([])
const retrievalResult = ref<RetrievalDebugResponse | null>(null)
const statsLoading = ref(false)
const ingestionLoading = ref(false)
const modelLoading = ref(false)
const feedbackLoading = ref(false)
const retrievalLoading = ref(false)
const ingestionStatus = ref('')
const modelType = ref('')
const modelStatus = ref('')
const feedbackRating = ref('')
const retrievalKbId = ref<number | null>(null)
const retrievalQuery = ref('')
const retrievalTopK = ref(8)
const ingestionPage = ref({ page: 0, size: 20, totalElements: 0 })
const modelPage = ref({ page: 0, size: 20, totalElements: 0 })
const feedbackPage = ref({ page: 0, size: 20, totalElements: 0 })

const refreshing = computed(() =>
  statsLoading.value || ingestionLoading.value || modelLoading.value || feedbackLoading.value || retrievalLoading.value
)

const feedbackOptions = [
  { value: 'WRONG', label: '答案错误' },
  { value: 'CITATION_IRRELEVANT', label: '引用无关' },
  { value: 'SHOULD_HAVE_ANSWERED', label: '应回答' },
  { value: 'SHOULD_HAVE_REFUSED', label: '应拒答' },
  { value: 'TOO_LONG', label: '太长' },
  { value: 'TOO_SHORT', label: '太短' }
]

onMounted(async () => {
  await Promise.all([loadOverview(), loadKbs(), loadIngestionJobs(), loadModelCalls(), loadQaFeedback()])
  if (kbs.value.length > 0) {
    retrievalKbId.value = kbs.value[0].id
  }
})

async function loadOverview() {
  statsLoading.value = true
  try {
    stats.value = await adminApi.getStatsOverview()
  } finally {
    statsLoading.value = false
  }
}

async function loadKbs() {
  kbs.value = await kbApi.listKbs()
}

async function loadIngestionJobs() {
  ingestionLoading.value = true
  try {
    const result = await adminApi.listIngestionJobs({
      status: ingestionStatus.value || undefined,
      page: ingestionPage.value.page,
      size: ingestionPage.value.size
    })
    ingestionJobs.value = result.content
    ingestionPage.value = {
      page: result.page,
      size: result.size,
      totalElements: result.totalElements
    }
  } finally {
    ingestionLoading.value = false
  }
}

async function loadModelCalls() {
  modelLoading.value = true
  try {
    const result = await adminApi.listModelCalls({
      type: modelType.value || undefined,
      status: modelStatus.value || undefined,
      page: modelPage.value.page,
      size: modelPage.value.size
    })
    modelCalls.value = result.content
    modelPage.value = {
      page: result.page,
      size: result.size,
      totalElements: result.totalElements
    }
  } finally {
    modelLoading.value = false
  }
}

async function loadQaFeedback() {
  feedbackLoading.value = true
  try {
    const result = await adminApi.listQaFeedback({
      rating: feedbackRating.value || undefined,
      page: feedbackPage.value.page,
      size: feedbackPage.value.size
    })
    qaFeedback.value = result.content
    feedbackPage.value = {
      page: result.page,
      size: result.size,
      totalElements: result.totalElements
    }
  } finally {
    feedbackLoading.value = false
  }
}

async function refreshCurrent() {
  if (activeTab.value === 'overview') {
    await loadOverview()
  } else if (activeTab.value === 'ingestion') {
    await loadIngestionJobs()
  } else if (activeTab.value === 'model-calls') {
    await loadModelCalls()
  } else if (activeTab.value === 'feedback') {
    await loadQaFeedback()
  } else if (activeTab.value === 'retrieval') {
    await loadKbs()
  }
}

async function reloadIngestionJobs() {
  ingestionPage.value.page = 0
  await loadIngestionJobs()
}

async function reloadModelCalls() {
  modelPage.value.page = 0
  await loadModelCalls()
}

async function reloadQaFeedback() {
  feedbackPage.value.page = 0
  await loadQaFeedback()
}

async function changeIngestionPage(value: number) {
  ingestionPage.value.page = value - 1
  await loadIngestionJobs()
}

async function changeModelPage(value: number) {
  modelPage.value.page = value - 1
  await loadModelCalls()
}

async function changeFeedbackPage(value: number) {
  feedbackPage.value.page = value - 1
  await loadQaFeedback()
}

async function openDocument(row: AdminIngestionJobDto) {
  await router.push({
    name: 'kb-detail',
    params: { id: row.kbId },
    query: { documentId: String(row.documentId) }
  })
}

async function openFeedbackConversation(row: AdminQaFeedbackDto) {
  await router.push({
    name: 'kb-chat',
    params: { id: row.kbId },
    query: { conversationId: String(row.conversationId) }
  })
}

async function confirmReingest(row: AdminIngestionJobDto) {
  await ElMessageBox.confirm(`重新解析「${row.documentFilename}」？`, '重新解析', {
    type: 'warning',
    confirmButtonText: '重跑',
    cancelButtonText: '取消'
  })
  await kbApi.reingestDocument(row.documentId)
  ElMessage.success('已触发重新解析')
  await Promise.all([loadOverview(), loadIngestionJobs()])
}

async function runRetrievalDebug() {
  const query = retrievalQuery.value.trim()
  if (!retrievalKbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!query) {
    ElMessage.warning('请输入 query')
    return
  }
  retrievalLoading.value = true
  try {
    retrievalResult.value = await adminApi.debugRetrieval({
      kbId: retrievalKbId.value,
      query,
      topK: retrievalTopK.value
    })
  } finally {
    retrievalLoading.value = false
  }
}

function jobStatusTag(status: string) {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function documentStatusTag(status: string) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'UPLOADED') return 'info'
  if (status === 'EMBEDDING') return 'success'
  return 'warning'
}

function answerStatusTag(status: string) {
  if (status === 'OK') return 'success'
  if (status === 'NO_ANSWER') return 'info'
  if (status === 'UNGROUNDED') return 'warning'
  return 'danger'
}

function feedbackTag(rating: string) {
  if (rating === 'WRONG' || rating === 'CITATION_IRRELEVANT') return 'danger'
  if (rating === 'SHOULD_HAVE_ANSWERED' || rating === 'SHOULD_HAVE_REFUSED') return 'warning'
  return 'info'
}

function feedbackLabel(rating: string) {
  return feedbackOptions.find((option) => option.value === rating)?.label || rating
}

function isProcessing(status: string) {
  return status === 'PARSING' || status === 'CHUNKING' || status === 'EMBEDDING'
}

function tokenTotal(row: ModelCallDto) {
  return (row.promptTokens || 0) + (row.completionTokens || 0)
}

function feedbackTokenTotal(row: AdminQaFeedbackDto) {
  return (row.promptTokens || 0) + (row.completionTokens || 0)
}

function feedbackLatency(row: AdminQaFeedbackDto) {
  const answer = row.answerLatencyMs == null ? '-' : row.answerLatencyMs
  const model = row.modelLatencyMs == null ? '-' : row.modelLatencyMs
  return `${answer} / ${model} ms`
}

function scorePercent(value: number) {
  return Math.max(0, Math.min(100, Math.round((value / 0.033) * 100)))
}

function formatTime(value: string | null) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}
</script>

<style scoped>
.admin-tabs {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  padding: 18px;
}

.stats-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.stat-card {
  align-items: center;
  border: 1px solid #e5e7eb;
  display: flex;
  gap: 14px;
  min-height: 112px;
  padding: 16px;
}

.stat-card .el-icon {
  color: #2563eb;
  font-size: 28px;
}

.stat-card div {
  display: grid;
  gap: 6px;
}

.stat-card span {
  color: #6b7280;
  font-size: 13px;
}

.stat-card strong {
  color: #111827;
  font-size: 24px;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.table-tools,
.retrieval-controls {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.table-tools .el-select,
.retrieval-controls .el-select {
  width: 220px;
}

.data-table {
  width: 100%;
}

.expanded-row {
  display: grid;
  gap: 12px;
  padding: 12px 28px;
}

.filename {
  display: inline-block;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.retrieval-form {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.score-cell {
  align-items: center;
  display: grid;
  gap: 6px;
  grid-template-columns: minmax(80px, 1fr) 92px;
}

.score-cell div {
  display: grid;
  gap: 2px;
}

.score-cell strong {
  color: #111827;
  font-size: 13px;
}

.score-cell span {
  color: #4b5563;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.hit-source {
  display: grid;
  gap: 4px;
  line-height: 1.45;
}

.hit-source span {
  color: #6b7280;
  overflow-wrap: anywhere;
}

.hit-preview {
  line-height: 1.55;
  margin: 0;
  overflow-wrap: anywhere;
}

.text-preview {
  line-height: 1.5;
  margin: 0;
  max-height: 48px;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.provider-cell,
.feedback-block,
.feedback-citations,
.feedback-citation {
  display: grid;
  gap: 6px;
}

.provider-cell span,
.feedback-citation span {
  color: #6b7280;
  overflow-wrap: anywhere;
}

.feedback-block p,
.feedback-citation p {
  line-height: 1.6;
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.feedback-citation {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  padding: 12px;
}

.citation-title {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}
</style>

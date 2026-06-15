<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <el-button text :icon="ArrowLeft" @click="router.push('/')">返回</el-button>
        <h1>{{ currentKb?.name || `知识库 #${kbId}` }}</h1>
        <p>{{ currentKb?.description || '无描述' }}</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="loadDocuments">刷新</el-button>
        <el-button :icon="Search" @click="router.push({ name: 'kb-chat', params: { id: kbId } })">问答</el-button>
        <el-upload
          :http-request="uploadFile"
          :show-file-list="false"
          accept=".md,.markdown,.txt,.pdf"
          action="#"
        >
          <el-button type="primary" :icon="Upload" :loading="uploading">上传</el-button>
        </el-upload>
      </div>
    </header>

    <section class="page-content">
      <el-table :data="documents" v-loading="loading" class="data-table" row-key="id">
        <el-table-column type="expand" width="44">
          <template #default="{ row }">
            <div class="expanded-row">
              <el-alert
                v-if="row.errorMessage"
                :title="row.errorMessage"
                type="error"
                :closable="false"
              />
              <el-descriptions v-else :column="2" border>
                <el-descriptions-item label="Content-Type">{{ row.contentType }}</el-descriptions-item>
                <el-descriptions-item label="更新时间">{{ formatTime(row.updatedAt) }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="originalFilename" label="文件名" min-width="240">
          <template #default="{ row }">
            <span class="filename">{{ row.originalFilename }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="120">
          <template #default="{ row }">{{ formatBytes(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="Chunks" width="100" />
        <el-table-column prop="createdAt" label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="Tickets" @click="openJobs(row)">任务</el-button>
            <el-button text :icon="RefreshRight" :disabled="isProcessing(row.status)" @click="confirmReingest(row)">
              重跑
            </el-button>
            <el-button text type="danger" :icon="Delete" @click="confirmDeleteDocument(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="page.totalElements > page.size"
        layout="prev, pager, next"
        :current-page="page.page + 1"
        :page-size="page.size"
        :total="page.totalElements"
        @current-change="changePage"
      />

      <section class="retrieval-debug">
        <div class="section-title">
          <h2>检索调试</h2>
          <el-tag v-if="retrievalResult" effect="plain">阈值 {{ retrievalResult.minSimilarity.toFixed(2) }}</el-tag>
        </div>
        <div class="retrieval-form">
          <el-input
            v-model="retrievalQuery"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            placeholder="输入 query"
          />
          <div class="retrieval-actions">
            <el-input-number v-model="retrievalTopK" :min="1" :max="20" controls-position="right" />
            <el-button type="primary" :icon="Search" :loading="retrievalLoading" @click="runRetrievalDebug">
              检索
            </el-button>
          </div>
        </div>

        <el-empty v-if="retrievalResult && retrievalResult.hits.length === 0" description="暂无命中" />
        <el-table
          v-else-if="retrievalResult"
          :data="retrievalResult.hits"
          class="data-table"
          row-key="chunkId"
        >
          <el-table-column prop="rank" label="#" width="64" />
          <el-table-column label="Score" width="110">
            <template #default="{ row }">
              <el-tag :type="row.aboveThreshold ? 'success' : 'info'" effect="plain">
                {{ formatScore(row.similarity) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" min-width="220">
            <template #default="{ row }">
              <div class="hit-source">
                <strong>{{ row.documentFilename }}</strong>
                <span>chunk #{{ row.chunkIndex }}</span>
                <span v-if="row.headingPath">{{ row.headingPath }}</span>
                <span v-if="row.pageStart">p.{{ row.pageStart }}{{ row.pageEnd && row.pageEnd !== row.pageStart ? `-${row.pageEnd}` : '' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="预览" min-width="360">
            <template #default="{ row }">
              <p class="hit-preview">{{ row.contentPreview }}</p>
            </template>
          </el-table-column>
          <el-table-column prop="charLen" label="Chars" width="90" />
        </el-table>
      </section>
    </section>

    <el-drawer v-model="jobsVisible" title="处理任务" size="420px">
      <el-empty v-if="jobs.length === 0" description="暂无任务" />
      <el-timeline v-else>
        <el-timeline-item v-for="job in jobs" :key="job.id" :timestamp="formatTime(job.createdAt)">
          <div class="job-row">
            <strong>{{ job.phase }}</strong>
            <el-tag size="small" :type="job.status === 'FAILED' ? 'danger' : 'info'">{{ job.status }}</el-tag>
          </div>
          <p v-if="job.errorMessage" class="error-message">{{ job.errorMessage }}</p>
          <span>attempt {{ job.attempt }} / {{ job.maxAttempt }}</span>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { ArrowLeft, Delete, Refresh, RefreshRight, Search, Tickets, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as kbApi from '../api/kbs'
import type { DocumentDto, JobDto, KbDto, RetrievalDebugResponse } from '../api/kbs'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)
const loading = ref(false)
const uploading = ref(false)
const documents = ref<DocumentDto[]>([])
const kbs = ref<KbDto[]>([])
const jobs = ref<JobDto[]>([])
const jobsVisible = ref(false)
const retrievalQuery = ref('')
const retrievalTopK = ref(8)
const retrievalLoading = ref(false)
const retrievalResult = ref<RetrievalDebugResponse | null>(null)
const page = ref({ page: 0, size: 20, totalElements: 0 })
let timer: number | undefined

const currentKb = computed(() => kbs.value.find((kb) => kb.id === kbId))

onMounted(async () => {
  await Promise.all([loadKbs(), loadDocuments()])
  timer = window.setInterval(loadDocuments, 5000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})

async function loadKbs() {
  kbs.value = await kbApi.listKbs()
}

async function loadDocuments() {
  loading.value = true
  try {
    const result = await kbApi.listDocuments(kbId, { page: page.value.page, size: page.value.size })
    documents.value = result.content
    page.value = {
      page: result.page,
      size: result.size,
      totalElements: result.totalElements
    }
  } finally {
    loading.value = false
  }
}

async function uploadFile(options: UploadRequestOptions) {
  uploading.value = true
  try {
    await kbApi.uploadDocument(kbId, options.file)
    ElMessage.success('已上传')
    await loadDocuments()
    options.onSuccess?.({})
  } catch {
    // The global HTTP interceptor already reports the backend error message.
  } finally {
    uploading.value = false
  }
}

async function confirmDeleteDocument(document: DocumentDto) {
  await ElMessageBox.confirm(`删除「${document.originalFilename}」？`, '删除文档', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await kbApi.deleteDocument(document.id)
  ElMessage.success('已删除')
  await loadDocuments()
}

async function confirmReingest(document: DocumentDto) {
  await ElMessageBox.confirm(`重新解析「${document.originalFilename}」？`, '重新解析', {
    type: 'warning',
    confirmButtonText: '重跑',
    cancelButtonText: '取消'
  })
  await kbApi.reingestDocument(document.id)
  ElMessage.success('已触发重新解析')
  await loadDocuments()
}

async function openJobs(document: DocumentDto) {
  jobs.value = await kbApi.listIngestionJobs(document.id)
  jobsVisible.value = true
}

async function runRetrievalDebug() {
  const query = retrievalQuery.value.trim()
  if (!query) {
    ElMessage.warning('请输入 query')
    return
  }
  retrievalLoading.value = true
  try {
    retrievalResult.value = await kbApi.debugRetrieval(kbId, {
      query,
      topK: retrievalTopK.value
    })
  } finally {
    retrievalLoading.value = false
  }
}

async function changePage(value: number) {
  page.value.page = value - 1
  await loadDocuments()
}

function statusTag(status: string) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'UPLOADED') return 'info'
  if (status === 'EMBEDDING') return 'success'
  return 'warning'
}

function isProcessing(status: string) {
  return status === 'PARSING' || status === 'CHUNKING' || status === 'EMBEDDING'
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
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

function formatScore(value: number) {
  return value.toFixed(3)
}
</script>

<style scoped>
.data-table {
  width: 100%;
}

.filename {
  display: inline-block;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.expanded-row {
  padding: 12px 28px;
}

.job-row {
  align-items: center;
  display: flex;
  gap: 10px;
}

.error-message {
  color: #b42318;
  margin: 8px 0;
  overflow-wrap: anywhere;
}

.retrieval-debug {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  display: grid;
  gap: 14px;
  padding: 18px;
}

.section-title {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.section-title h2 {
  font-size: 18px;
  margin: 0;
}

.retrieval-form {
  display: grid;
  gap: 12px;
}

.retrieval-actions {
  align-items: center;
  display: flex;
  gap: 10px;
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
</style>

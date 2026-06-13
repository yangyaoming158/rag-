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
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="Tickets" @click="openJobs(row)">任务</el-button>
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
import { ArrowLeft, Delete, Refresh, Tickets, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as kbApi from '../api/kbs'
import type { DocumentDto, JobDto, KbDto } from '../api/kbs'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)
const loading = ref(false)
const uploading = ref(false)
const documents = ref<DocumentDto[]>([])
const kbs = ref<KbDto[]>([])
const jobs = ref<JobDto[]>([])
const jobsVisible = ref(false)
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

async function openJobs(document: DocumentDto) {
  jobs.value = await kbApi.listIngestionJobs(document.id)
  jobsVisible.value = true
}

async function changePage(value: number) {
  page.value.page = value - 1
  await loadDocuments()
}

function statusTag(status: string) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'UPLOADED') return 'info'
  return 'warning'
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
</style>

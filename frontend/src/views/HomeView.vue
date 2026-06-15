<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <h1>知识库</h1>
        <p>{{ auth.user?.username }} · {{ auth.user?.role }}</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="loadKbs">刷新</el-button>
        <el-button :icon="DataAnalysis" @click="router.push({ name: 'admin' })">后台</el-button>
        <el-button type="primary" :icon="Plus" @click="createDialogVisible = true">新建</el-button>
        <el-button @click="logout">退出</el-button>
      </div>
    </header>

    <section class="page-content">
      <el-empty v-if="!loading && kbs.length === 0" description="暂无知识库">
        <el-button type="primary" :icon="Plus" @click="createDialogVisible = true">新建知识库</el-button>
      </el-empty>

      <div v-else class="kb-grid">
        <el-card v-for="kb in kbs" :key="kb.id" class="kb-card" shadow="never">
          <div class="kb-card__body" @click="openKb(kb.id)">
            <div class="kb-card__title-row">
              <h2>{{ kb.name }}</h2>
              <el-tag size="small" type="info">{{ kb.documentCount }} 文档</el-tag>
            </div>
            <p>{{ kb.description || '无描述' }}</p>
            <span>{{ formatTime(kb.updatedAt) }}</span>
          </div>
          <template #footer>
            <div class="card-actions">
              <el-button text :icon="FolderOpened" @click="openKb(kb.id)">打开</el-button>
              <el-button text type="danger" :icon="Delete" @click="confirmDeleteKb(kb)">删除</el-button>
            </div>
          </template>
        </el-card>
      </div>
    </section>

    <el-dialog v-model="createDialogVisible" title="新建知识库" width="420px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submitCreate">
        <el-form-item label="名称" prop="name">
          <el-input v-model.trim="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model.trim="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { DataAnalysis, Delete, FolderOpened, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as kbApi from '../api/kbs'
import type { KbDto } from '../api/kbs'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const createDialogVisible = ref(false)
const formRef = ref<FormInstance>()
const kbs = ref<KbDto[]>([])

const form = reactive({
  name: '',
  description: ''
})

const rules: FormRules<typeof form> = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 128, message: '不能超过 128 个字符', trigger: 'blur' }
  ],
  description: [{ max: 1000, message: '不能超过 1000 个字符', trigger: 'blur' }]
}

onMounted(loadKbs)

async function loadKbs() {
  loading.value = true
  try {
    kbs.value = await kbApi.listKbs()
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const created = await kbApi.createKb({
      name: form.name,
      description: form.description || undefined
    })
    ElMessage.success('已创建')
    createDialogVisible.value = false
    form.name = ''
    form.description = ''
    await router.push({ name: 'kb-detail', params: { id: created.id } })
  } finally {
    submitting.value = false
  }
}

async function confirmDeleteKb(kb: KbDto) {
  await ElMessageBox.confirm(`删除「${kb.name}」会同时删除其中所有文档。`, '删除知识库', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await kbApi.deleteKb(kb.id)
  ElMessage.success('已删除')
  await loadKbs()
}

async function openKb(id: number) {
  await router.push({ name: 'kb-detail', params: { id } })
}

async function logout() {
  auth.logout()
  await router.push('/login')
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value))
}
</script>

<style scoped>
.kb-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

.kb-card {
  border-radius: 8px;
}

.kb-card__body {
  cursor: pointer;
  min-height: 130px;
}

.kb-card__title-row {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

h2 {
  font-size: 18px;
  line-height: 1.35;
  margin: 0;
  overflow-wrap: anywhere;
}

p {
  color: #5f6b7a;
  line-height: 1.6;
  margin: 12px 0 18px;
  min-height: 46px;
  overflow-wrap: anywhere;
}

span {
  color: #8a95a5;
  font-size: 13px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
}
</style>

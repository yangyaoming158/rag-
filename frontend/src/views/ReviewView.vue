<template>
  <main class="app-shell review-shell">
    <header class="topbar">
      <div>
        <el-button text :icon="ArrowLeft" @click="router.push('/')">返回</el-button>
        <h1>项目审查 / Review</h1>
        <p>固定模板审查，输出风险、问题、建议和引用来源</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="refreshing" @click="refreshAll">刷新</el-button>
      </div>
    </header>

    <section class="page-content">
      <section class="review-workbench">
        <el-form label-position="top" @submit.prevent="runReview">
          <div class="form-grid">
            <el-form-item label="知识库">
              <el-select v-model="form.kbId" filterable placeholder="选择知识库" @change="handleKbChange">
                <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="审查类型">
              <el-select v-model="form.reviewType" placeholder="选择审查类型">
                <el-option
                  v-for="type in reviewTypes"
                  :key="type.code"
                  :label="type.label"
                  :value="type.code"
                />
              </el-select>
            </el-form-item>
            <div class="run-cell">
              <el-button type="primary" :icon="DataAnalysis" :loading="creating" @click="runReview">
                开始审查
              </el-button>
            </div>
          </div>
          <el-form-item label="补充说明">
            <el-input
              v-model="form.supplement"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
              placeholder="可填写本次重点关注的路径、模块、阶段或疑点"
            />
          </el-form-item>
        </el-form>
      </section>

      <section class="review-layout">
        <aside class="review-history">
          <div class="panel-title">
            <h2>审查历史</h2>
            <el-tag effect="plain">{{ reviews.length }}</el-tag>
          </div>
          <el-empty v-if="!historyLoading && reviews.length === 0" description="暂无审查记录" />
          <button
            v-for="review in reviews"
            :key="review.id"
            class="history-item"
            :class="{ active: activeReview?.id === review.id }"
            @click="openReview(review.id)"
          >
            <strong>{{ review.reviewTypeLabel }}</strong>
            <span>{{ review.kbName }}</span>
            <div>
              <el-tag size="small" :type="riskTag(review.riskLevel)" effect="plain">{{ review.riskLevel }}</el-tag>
              <el-tag size="small" :type="statusTag(review.status)" effect="plain">{{ review.status }}</el-tag>
            </div>
            <time>{{ formatTime(review.createdAt) }}</time>
          </button>
        </aside>

        <section class="review-result">
          <el-empty v-if="!activeReview" description="选择历史记录或发起审查" />
          <div v-else class="result-stack">
            <div class="result-header">
              <div>
                <h2>{{ activeReview.reviewTypeLabel }}</h2>
                <span>{{ activeReview.kbName }} · {{ formatTime(activeReview.createdAt) }}</span>
              </div>
              <div class="result-tags">
                <el-tag :type="riskTag(activeReview.riskLevel)" effect="plain">{{ activeReview.riskLevel }}</el-tag>
                <el-tag :type="statusTag(activeReview.status)" effect="plain">{{ activeReview.status }}</el-tag>
                <el-tag v-if="activeReview.latencyMs !== null" effect="plain">{{ activeReview.latencyMs }} ms</el-tag>
              </div>
            </div>

            <el-alert
              v-if="activeReview.citationWarning"
              :title="activeReview.citationWarning"
              type="warning"
              :closable="false"
            />

            <section class="result-section">
              <h3>审查结论</h3>
              <p>{{ activeReview.conclusion }}</p>
            </section>

            <section class="result-section">
              <h3>发现的问题</h3>
              <p>{{ activeReview.issues }}</p>
            </section>

            <section class="result-section">
              <h3>建议修改项</h3>
              <p>{{ activeReview.suggestions }}</p>
            </section>

            <section class="result-section">
              <div class="panel-title">
                <h3>引用来源</h3>
                <el-tag effect="plain">{{ activeReview.citations.length }}</el-tag>
              </div>
              <el-empty v-if="activeReview.citations.length === 0" description="暂无引用" />
              <div v-else class="citation-grid">
                <div v-for="citation in activeReview.citations" :key="citation.rank" class="citation">
                  <div class="citation-title">
                    <strong>[{{ citation.rank }}] {{ citation.documentFilename }}</strong>
                    <el-tag size="small" effect="plain">{{ citation.similarity.toFixed(3) }}</el-tag>
                  </div>
                  <span v-if="citation.headingPath">{{ citation.headingPath }}</span>
                  <p>{{ citation.snippet }}</p>
                </div>
              </div>
            </section>
          </div>
        </section>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ArrowLeft, DataAnalysis, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as kbApi from '../api/kbs'
import type { KbDto } from '../api/kbs'
import * as reviewApi from '../api/reviews'
import type { ReviewDto, ReviewTypeDto } from '../api/reviews'

const route = useRoute()
const router = useRouter()
const kbs = ref<KbDto[]>([])
const reviewTypes = ref<ReviewTypeDto[]>([])
const reviews = ref<ReviewDto[]>([])
const activeReview = ref<ReviewDto | null>(null)
const kbsLoading = ref(false)
const typesLoading = ref(false)
const historyLoading = ref(false)
const creating = ref(false)

const form = reactive({
  kbId: null as number | null,
  reviewType: 'PRD_API_CONSISTENCY',
  supplement: ''
})

const refreshing = computed(() => kbsLoading.value || typesLoading.value || historyLoading.value || creating.value)

onMounted(async () => {
  await Promise.all([loadKbs(), loadReviewTypes()])
  const queryKbId = Number(route.query.kbId)
  if (queryKbId && kbs.value.some((kb) => kb.id === queryKbId)) {
    form.kbId = queryKbId
  } else if (kbs.value.length > 0) {
    form.kbId = kbs.value[0].id
  }
  await loadReviews()
  if (reviews.value.length > 0) {
    activeReview.value = reviews.value[0]
  }
})

async function loadKbs() {
  kbsLoading.value = true
  try {
    kbs.value = await kbApi.listKbs()
  } finally {
    kbsLoading.value = false
  }
}

async function loadReviewTypes() {
  typesLoading.value = true
  try {
    reviewTypes.value = await reviewApi.listReviewTypes()
    if (reviewTypes.value.length > 0 && !reviewTypes.value.some((type) => type.code === form.reviewType)) {
      form.reviewType = reviewTypes.value[0].code
    }
  } finally {
    typesLoading.value = false
  }
}

async function loadReviews() {
  historyLoading.value = true
  try {
    reviews.value = await reviewApi.listReviews({ kbId: form.kbId || undefined })
  } finally {
    historyLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadKbs(), loadReviewTypes(), loadReviews()])
}

async function handleKbChange() {
  activeReview.value = null
  await loadReviews()
  if (reviews.value.length > 0) {
    activeReview.value = reviews.value[0]
  }
}

async function runReview() {
  if (!form.kbId) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (!form.reviewType) {
    ElMessage.warning('请选择审查类型')
    return
  }
  creating.value = true
  try {
    const created = await reviewApi.createReview({
      kbId: form.kbId,
      reviewType: form.reviewType,
      supplement: form.supplement.trim() || undefined
    })
    activeReview.value = created
    await loadReviews()
    ElMessage.success('审查完成')
  } finally {
    creating.value = false
  }
}

async function openReview(id: number) {
  activeReview.value = await reviewApi.getReview(id)
}

function riskTag(risk: string) {
  if (risk === 'LOW') return 'success'
  if (risk === 'MEDIUM') return 'warning'
  if (risk === 'HIGH') return 'danger'
  return 'info'
}

function statusTag(status: string) {
  if (status === 'OK') return 'success'
  if (status === 'NO_ANSWER') return 'info'
  if (status === 'UNGROUNDED') return 'warning'
  return 'danger'
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
.review-shell {
  background: #f6f7fb;
}

.review-workbench,
.review-history,
.review-result {
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.review-workbench {
  padding: 18px;
}

.form-grid {
  align-items: end;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(220px, 1fr) minmax(260px, 1.2fr) auto;
}

.run-cell {
  padding-bottom: 18px;
}

.review-layout {
  display: grid;
  gap: 18px;
  grid-template-columns: 320px minmax(0, 1fr);
}

.review-history {
  align-content: start;
  display: grid;
  gap: 10px;
  min-height: 620px;
  padding: 14px;
}

.panel-title {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.panel-title h2,
.panel-title h3 {
  font-size: 18px;
  margin: 0;
}

.history-item {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  display: grid;
  gap: 8px;
  padding: 12px;
  text-align: left;
}

.history-item.active {
  border-color: #409eff;
}

.history-item strong {
  color: #111827;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.history-item span,
.history-item time,
.result-header span {
  color: #6b7280;
  font-size: 13px;
}

.history-item div,
.result-tags,
.citation-title {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.review-result {
  min-height: 620px;
  padding: 18px;
}

.result-stack {
  display: grid;
  gap: 14px;
}

.result-header {
  align-items: flex-start;
  display: flex;
  gap: 14px;
  justify-content: space-between;
}

.result-header h2 {
  font-size: 20px;
  line-height: 1.35;
  margin: 0 0 6px;
  overflow-wrap: anywhere;
}

.result-section {
  border-top: 1px solid #eef0f3;
  display: grid;
  gap: 10px;
  padding-top: 14px;
}

.result-section h3 {
  font-size: 16px;
  margin: 0;
}

.result-section p,
.citation p {
  line-height: 1.6;
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.citation-grid {
  display: grid;
  gap: 10px;
}

.citation {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  display: grid;
  gap: 8px;
  padding: 12px;
}

.citation span {
  color: #6b7280;
  overflow-wrap: anywhere;
}
</style>

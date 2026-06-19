<template>
  <main class="app-shell chat-shell">
    <header class="topbar">
      <div>
        <el-button text :icon="ArrowLeft" @click="router.push({ name: 'kb-detail', params: { id: kbId } })">返回</el-button>
        <h1>{{ currentKb?.name || `知识库 #${kbId}` }} · 问答</h1>
        <p>同步 RAG 问答，回答必须带引用或显式拒答</p>
      </div>
      <div class="toolbar">
        <el-button :icon="Refresh" :loading="loading" @click="loadConversations">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="startConversation">新会话</el-button>
      </div>
    </header>

    <section class="chat-layout">
      <aside class="conversation-list">
        <el-empty v-if="!loading && conversations.length === 0" description="暂无会话" />
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === activeConversationId }"
          @click="openConversation(conversation.id)"
        >
          <strong>{{ conversation.title }}</strong>
          <span>{{ formatTime(conversation.createdAt) }}</span>
        </button>
      </aside>

      <section class="chat-panel">
        <div class="messages">
          <el-empty v-if="messages.length === 0" description="选择会话或直接提问" />
          <article v-for="message in messages" :key="message.id" class="message" :class="message.role.toLowerCase()">
            <div class="message-meta">
              <strong>{{ message.role === 'USER' ? '你' : '助手' }}</strong>
              <el-tag v-if="message.role === 'ASSISTANT'" :type="statusTag(message.status)" effect="plain">
                {{ message.status }}
              </el-tag>
              <span>{{ formatTime(message.createdAt) }}</span>
            </div>
            <p>{{ message.content }}</p>
            <div v-if="message.citations.length" class="citations">
              <div v-for="citation in message.citations" :key="`${message.id}-${citation.rank}`" class="citation">
                <div class="citation-title">
                  <strong>[{{ citation.rank }}] {{ citation.documentFilename }}</strong>
                  <el-tag size="small" effect="plain">{{ citation.similarity.toFixed(3) }}</el-tag>
                </div>
                <span v-if="citation.headingPath">{{ citation.headingPath }}</span>
                <p>{{ citation.snippet }}</p>
              </div>
            </div>
            <div v-if="message.role === 'ASSISTANT'" class="feedback-actions">
              <el-button
                size="small"
                :icon="CircleCheck"
                :type="message.feedbackRating === 'HELPFUL' ? 'success' : 'default'"
                :loading="feedbackSubmittingId === message.id"
                @click="submitQuickFeedback(message, 'HELPFUL')"
              >
                有帮助
              </el-button>
              <el-dropdown
                trigger="click"
                :disabled="feedbackSubmittingId === message.id"
                @command="handleFeedbackCommand(message, $event)"
              >
                <el-button
                  size="small"
                  :icon="Warning"
                  :type="message.feedbackRating && message.feedbackRating !== 'HELPFUL' ? 'warning' : 'default'"
                >
                  问题反馈
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="option in negativeFeedbackOptions"
                      :key="option.rating"
                      :command="option.rating"
                      :icon="option.icon"
                    >
                      {{ option.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <span v-if="message.feedbackRating" class="feedback-note">
                已反馈：{{ feedbackLabel(message.feedbackRating) }}
              </span>
            </div>
          </article>
        </div>

        <div class="composer">
          <el-input
            v-model="question"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="输入问题"
            @keydown.ctrl.enter.prevent="send"
          />
          <div class="composer-actions">
            <span v-if="lastWarning" class="warning">{{ lastWarning }}</span>
            <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          </div>
        </div>
      </section>
    </section>
  </main>

  <el-dialog v-model="feedbackDialogVisible" title="回答反馈" width="520px">
    <el-form label-width="84px">
      <el-form-item label="类型">
        <el-tag effect="plain">{{ feedbackLabel(feedbackForm.rating) }}</el-tag>
      </el-form-item>
      <el-form-item label="原因">
        <el-input v-model="feedbackForm.reason" maxlength="120" show-word-limit />
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="feedbackForm.comment"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="feedbackDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="feedbackSubmittingId !== null" @click="submitFeedbackDialog">
        提交
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Bottom, ChatDotRound, CircleCheck, Link, Top, Warning } from '@element-plus/icons-vue'
import { ArrowLeft, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as conversationApi from '../api/conversations'
import * as kbApi from '../api/kbs'
import type { ConversationDto, MessageDto, QaFeedbackRating } from '../api/conversations'
import type { KbDto } from '../api/kbs'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)
const kbs = ref<KbDto[]>([])
const conversations = ref<ConversationDto[]>([])
const activeConversationId = ref<number | null>(null)
const messages = ref<MessageDto[]>([])
const question = ref('')
const loading = ref(false)
const sending = ref(false)
const lastWarning = ref<string | null>(null)
const feedbackDialogVisible = ref(false)
const feedbackTarget = ref<MessageDto | null>(null)
const feedbackSubmittingId = ref<number | null>(null)
const feedbackForm = ref<{ rating: QaFeedbackRating; reason: string; comment: string }>({
  rating: 'WRONG',
  reason: '',
  comment: ''
})

const feedbackLabels: Record<QaFeedbackRating, string> = {
  HELPFUL: '有帮助',
  WRONG: '答案错误',
  CITATION_IRRELEVANT: '引用无关',
  SHOULD_HAVE_ANSWERED: '应回答',
  SHOULD_HAVE_REFUSED: '应拒答',
  TOO_LONG: '太长',
  TOO_SHORT: '太短'
}

const negativeFeedbackOptions = [
  { rating: 'WRONG' as QaFeedbackRating, label: feedbackLabels.WRONG, icon: Warning },
  { rating: 'CITATION_IRRELEVANT' as QaFeedbackRating, label: feedbackLabels.CITATION_IRRELEVANT, icon: Link },
  { rating: 'SHOULD_HAVE_ANSWERED' as QaFeedbackRating, label: feedbackLabels.SHOULD_HAVE_ANSWERED, icon: ChatDotRound },
  { rating: 'SHOULD_HAVE_REFUSED' as QaFeedbackRating, label: feedbackLabels.SHOULD_HAVE_REFUSED, icon: Warning },
  { rating: 'TOO_LONG' as QaFeedbackRating, label: feedbackLabels.TOO_LONG, icon: Top },
  { rating: 'TOO_SHORT' as QaFeedbackRating, label: feedbackLabels.TOO_SHORT, icon: Bottom }
]

const currentKb = computed(() => kbs.value.find((kb) => kb.id === kbId))

onMounted(async () => {
  await Promise.all([loadKbs(), loadConversations()])
  const queryConversation = Number(route.query.conversationId)
  if (queryConversation) {
    await openConversation(queryConversation)
  } else if (conversations.value.length > 0) {
    await openConversation(conversations.value[0].id)
  }
})

async function loadKbs() {
  kbs.value = await kbApi.listKbs()
}

async function loadConversations() {
  loading.value = true
  try {
    conversations.value = await conversationApi.listConversations({ kbId })
  } finally {
    loading.value = false
  }
}

async function startConversation() {
  const created = await conversationApi.createConversation({ kbId })
  await loadConversations()
  await openConversation(created.id)
}

async function openConversation(id: number) {
  const detail = await conversationApi.getConversation(id)
  activeConversationId.value = detail.conversation.id
  messages.value = detail.messages
  lastWarning.value = null
}

async function send() {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('请输入问题')
    return
  }
  sending.value = true
  try {
    let conversationId = activeConversationId.value
    if (!conversationId) {
      const created = await conversationApi.createConversation({ kbId, title: text.slice(0, 30) })
      conversationId = created.id
      activeConversationId.value = conversationId
      await loadConversations()
    }
    const answer = await conversationApi.sendMessage(conversationId, text)
    question.value = ''
    lastWarning.value = answer.citationWarning
    await openConversation(conversationId)
  } finally {
    sending.value = false
  }
}

async function submitQuickFeedback(message: MessageDto, rating: QaFeedbackRating) {
  await submitFeedback(message, rating, feedbackLabels[rating], '')
}

function openFeedbackDialog(message: MessageDto, rating: QaFeedbackRating) {
  feedbackTarget.value = message
  feedbackForm.value = {
    rating,
    reason: feedbackLabels[rating],
    comment: ''
  }
  feedbackDialogVisible.value = true
}

function handleFeedbackCommand(message: MessageDto, rating: string | number | object) {
  openFeedbackDialog(message, String(rating) as QaFeedbackRating)
}

async function submitFeedbackDialog() {
  if (!feedbackTarget.value) return
  await submitFeedback(
    feedbackTarget.value,
    feedbackForm.value.rating,
    feedbackForm.value.reason,
    feedbackForm.value.comment
  )
}

async function submitFeedback(message: MessageDto, rating: QaFeedbackRating, reason: string, comment: string) {
  if (!activeConversationId.value) return
  feedbackSubmittingId.value = message.id
  try {
    const result = await conversationApi.submitFeedback(activeConversationId.value, message.id, {
      rating,
      reason: reason.trim() || undefined,
      comment: comment.trim() || undefined
    })
    message.feedbackRating = result.rating
    feedbackDialogVisible.value = false
    feedbackTarget.value = null
    ElMessage.success('反馈已记录')
  } finally {
    feedbackSubmittingId.value = null
  }
}

function statusTag(status: string) {
  if (status === 'OK') return 'success'
  if (status === 'NO_ANSWER') return 'info'
  if (status === 'UNGROUNDED') return 'warning'
  return 'danger'
}

function feedbackLabel(rating: QaFeedbackRating) {
  return feedbackLabels[rating]
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
.chat-shell {
  background: #f6f7fb;
}

.chat-layout {
  display: grid;
  gap: 18px;
  grid-template-columns: 280px minmax(0, 1fr);
  padding: 28px;
}

.conversation-list {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  display: grid;
  gap: 8px;
  min-height: 640px;
  padding: 12px;
}

.conversation-item {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  display: grid;
  gap: 6px;
  padding: 12px;
  text-align: left;
}

.conversation-item.active {
  border-color: #409eff;
}

.conversation-item strong {
  color: #1f2937;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.conversation-item span {
  color: #8a95a5;
  font-size: 13px;
}

.chat-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.messages {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  display: grid;
  gap: 14px;
  min-height: 500px;
  padding: 18px;
}

.message {
  border: 1px solid #e5e7eb;
  display: grid;
  gap: 10px;
  max-width: 900px;
  padding: 14px;
}

.message.user {
  justify-self: end;
}

.message.assistant {
  justify-self: start;
}

.message-meta,
.citation-title,
.composer-actions {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.message-meta span {
  color: #8a95a5;
  font-size: 13px;
}

.message p,
.citation p {
  line-height: 1.6;
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.citations {
  display: grid;
  gap: 10px;
}

.feedback-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feedback-note {
  color: #6b7280;
  font-size: 12px;
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

.composer {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.warning {
  color: #9a6700;
  overflow-wrap: anywhere;
}
</style>

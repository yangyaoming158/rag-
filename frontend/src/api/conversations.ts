import { http, type ApiResponse } from './http'

export interface ConversationDto {
  id: number
  kbId: number
  title: string
  createdAt: string
}

export interface CitationDto {
  rank: number
  chunkId: number | null
  documentFilename: string
  headingPath: string | null
  snippet: string
  similarity: number
}

export type QaFeedbackRating =
  | 'HELPFUL'
  | 'WRONG'
  | 'CITATION_IRRELEVANT'
  | 'SHOULD_HAVE_ANSWERED'
  | 'SHOULD_HAVE_REFUSED'
  | 'TOO_LONG'
  | 'TOO_SHORT'

export interface MessageDto {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  status: 'OK' | 'NO_ANSWER' | 'UNGROUNDED' | 'ERROR'
  promptTokens: number | null
  completionTokens: number | null
  latencyMs: number | null
  createdAt: string
  citations: CitationDto[]
  feedbackRating: QaFeedbackRating | null
}

export interface ConversationDetailDto {
  conversation: ConversationDto
  messages: MessageDto[]
}

export interface RagAnswerDto {
  userMessageId: number
  assistantMessageId: number
  answer: string
  status: string
  citations: CitationDto[]
  citationWarning: string | null
  latencyMs: number
}

export interface QaFeedbackDto {
  id: number
  messageId: number
  rating: QaFeedbackRating
  reason: string | null
  comment: string | null
  createdAt: string
}

export async function createConversation(payload: { kbId: number; title?: string }) {
  const response = await http.post<ApiResponse<ConversationDto>>('/api/conversations', payload)
  return response.data.data
}

export async function listConversations(params: { kbId?: number } = {}) {
  const response = await http.get<ApiResponse<ConversationDto[]>>('/api/conversations', { params })
  return response.data.data
}

export async function getConversation(id: number) {
  const response = await http.get<ApiResponse<ConversationDetailDto>>(`/api/conversations/${id}`)
  return response.data.data
}

export async function sendMessage(id: number, question: string) {
  const response = await http.post<ApiResponse<RagAnswerDto>>(
    `/api/conversations/${id}/messages`,
    { question },
    { timeout: 70000 }
  )
  return response.data.data
}

export async function submitFeedback(
  conversationId: number,
  messageId: number,
  payload: { rating: QaFeedbackRating; reason?: string; comment?: string }
) {
  const response = await http.post<ApiResponse<QaFeedbackDto>>(
    `/api/conversations/${conversationId}/messages/${messageId}/feedback`,
    payload
  )
  return response.data.data
}

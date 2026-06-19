import { http, type ApiResponse } from './http'
import type { CitationDto } from './conversations'

export interface ReviewTypeDto {
  code: string
  label: string
  description: string
}

export interface ReviewDto {
  id: number
  kbId: number
  kbName: string
  reviewType: string
  reviewTypeLabel: string
  supplement: string | null
  status: 'OK' | 'NO_ANSWER' | 'UNGROUNDED' | 'ERROR'
  conclusion: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN'
  issues: string
  suggestions: string
  citations: CitationDto[]
  citationWarning: string | null
  promptTokens: number | null
  completionTokens: number | null
  latencyMs: number | null
  createdAt: string
}

export async function listReviewTypes() {
  const response = await http.get<ApiResponse<ReviewTypeDto[]>>('/api/reviews/types')
  return response.data.data
}

export async function listReviews(params: { kbId?: number } = {}) {
  const response = await http.get<ApiResponse<ReviewDto[]>>('/api/reviews', { params })
  return response.data.data
}

export async function getReview(id: number) {
  const response = await http.get<ApiResponse<ReviewDto>>(`/api/reviews/${id}`)
  return response.data.data
}

export async function createReview(payload: { kbId: number; reviewType: string; supplement?: string }) {
  const response = await http.post<ApiResponse<ReviewDto>>('/api/reviews', payload, { timeout: 70000 })
  return response.data.data
}

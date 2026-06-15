import { http, type ApiResponse } from './http'
import type { PageResponse, RetrievalDebugResponse } from './kbs'

export interface AdminIngestionJobDto {
  id: number
  documentId: number
  documentFilename: string
  documentStatus: string
  kbId: number
  kbName: string
  phase: string
  status: string
  attempt: number
  maxAttempt: number
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface ModelCallDto {
  id: number
  callType: string
  provider: string
  model: string
  messageId: number | null
  documentId: number | null
  documentFilename: string | null
  promptTokens: number | null
  completionTokens: number | null
  latencyMs: number | null
  status: string
  errorMessage: string | null
  createdAt: string
}

export interface StatsOverviewDto {
  kbCount: number
  docCount: number
  chunkCount: number
  tokenSum: number
  avgLatencyMs: number
}

export async function getStatsOverview() {
  const response = await http.get<ApiResponse<StatsOverviewDto>>('/api/admin/stats/overview')
  return response.data.data
}

export async function listIngestionJobs(params: { status?: string; page?: number; size?: number } = {}) {
  const response = await http.get<ApiResponse<PageResponse<AdminIngestionJobDto>>>('/api/admin/ingestion-jobs', {
    params
  })
  return response.data.data
}

export async function listModelCalls(params: { type?: string; status?: string; page?: number; size?: number } = {}) {
  const response = await http.get<ApiResponse<PageResponse<ModelCallDto>>>('/api/admin/model-calls', { params })
  return response.data.data
}

export async function debugRetrieval(payload: { kbId: number; query: string; topK: number }) {
  const response = await http.post<ApiResponse<RetrievalDebugResponse>>('/api/admin/retrieval-debug', payload)
  return response.data.data
}

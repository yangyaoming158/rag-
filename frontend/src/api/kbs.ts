import { http, type ApiResponse } from './http'

export interface KbDto {
  id: number
  name: string
  description: string | null
  documentCount: number
  createdAt: string
  updatedAt: string
}

export interface DocumentDto {
  id: number
  kbId: number
  originalFilename: string
  contentType: string
  fileSize: number
  status: string
  errorMessage: string | null
  chunkCount: number
  jobId: number | null
  createdAt: string
  updatedAt: string
}

export interface JobDto {
  id: number
  documentId: number
  phase: string
  status: string
  attempt: number
  maxAttempt: number
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface RetrievalHitDto {
  rank: number
  chunkId: number
  documentId: number
  documentFilename: string
  chunkIndex: number
  headingPath: string | null
  pageStart: number | null
  pageEnd: number | null
  charLen: number
  similarity: number
  keywordScore: number
  finalScore: number
  aboveThreshold: boolean
  contentPreview: string
}

export interface RetrievalDebugResponse {
  query: string
  topK: number
  minSimilarity: number
  hits: RetrievalHitDto[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export async function listKbs() {
  const response = await http.get<ApiResponse<KbDto[]>>('/api/kbs')
  return response.data.data
}

export async function createKb(payload: { name: string; description?: string }) {
  const response = await http.post<ApiResponse<KbDto>>('/api/kbs', payload)
  return response.data.data
}

export async function deleteKb(id: number) {
  await http.delete<ApiResponse<null>>(`/api/kbs/${id}`)
}

export async function listDocuments(kbId: number, params: { status?: string; page?: number; size?: number } = {}) {
  const response = await http.get<ApiResponse<PageResponse<DocumentDto>>>(`/api/kbs/${kbId}/documents`, { params })
  return response.data.data
}

export async function uploadDocument(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiResponse<DocumentDto>>(`/api/kbs/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
  return response.data.data
}

export async function deleteDocument(id: number) {
  await http.delete<ApiResponse<null>>(`/api/documents/${id}`)
}

export async function reingestDocument(id: number) {
  const response = await http.post<ApiResponse<DocumentDto>>(`/api/documents/${id}/reingest`)
  return response.data.data
}

export async function listIngestionJobs(id: number) {
  const response = await http.get<ApiResponse<JobDto[]>>(`/api/documents/${id}/ingestion`)
  return response.data.data
}

export async function debugRetrieval(kbId: number, payload: { query: string; topK: number }) {
  const response = await http.post<ApiResponse<RetrievalDebugResponse>>(`/api/kbs/${kbId}/retrieval/debug`, payload)
  return response.data.data
}

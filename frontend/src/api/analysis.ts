import { request } from './http'

export type AnalysisType = 'SUMMARY' | 'OUTLINE' | 'QUESTIONS' | 'FIELDS'
export type AnalysisStatus = 'PENDING' | 'RUNNING' | 'READY' | 'FAILED'

export interface AnalysisTask {
  id: number
  documentId: number
  kbId: number
  type: AnalysisType
  status: AnalysisStatus
  content: string
  model: string
  promptTokens: number
  completionTokens: number
  latencyMs: number
  errorMsg: string
  createdAt: string
  updatedAt: string
}

/** 文档必须 READY；立即返回 PENDING，后台调用模型 */
export function createAnalysis(documentId: number, type: AnalysisType) {
  return request<AnalysisTask>(`/documents/${documentId}/analysis`, {
    method: 'POST',
    body: JSON.stringify({ type }),
  })
}

/** 某文档的分析记录，最新在前，最多 20 条 */
export function listAnalyses(documentId: number) {
  return request<AnalysisTask[]>(`/documents/${documentId}/analysis`)
}

export function getAnalysis(id: number) {
  return request<AnalysisTask>(`/analysis/${id}`)
}

export function deleteAnalysis(id: number) {
  return request<void>(`/analysis/${id}`, { method: 'DELETE' })
}

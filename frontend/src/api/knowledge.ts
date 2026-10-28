import { request } from './http'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  embeddingModel: string
  chunkSize: number
  chunkOverlap: number
  documentCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseInput {
  name: string
  description?: string
  chunkSize?: number
  chunkOverlap?: number
}

export type DocumentStatus = 'PENDING' | 'PARSING' | 'INDEXING' | 'READY' | 'FAILED'

export interface KnowledgeDocument {
  id: number
  kbId: number
  filename: string
  ext: string
  size: number
  status: DocumentStatus
  chunkCount: number
  errorMsg: string
  createdAt: string
  updatedAt: string
}

export interface SearchHit {
  chunkId: number
  documentId: number
  filename: string
  chunkIndex: number
  score: number
  content: string
}

/** 当前用户的知识库，按 id 倒序，最多 50 个 */
export function listKnowledgeBases() {
  return request<KnowledgeBase[]>('/knowledge-bases')
}

export function createKnowledgeBase(input: KnowledgeBaseInput) {
  return request<KnowledgeBase>('/knowledge-bases', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

/** 全量覆盖：未传的字段由后端回落默认值 */
export function updateKnowledgeBase(id: number, input: KnowledgeBaseInput) {
  return request<KnowledgeBase>(`/knowledge-bases/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteKnowledgeBase(id: number) {
  return request<void>(`/knowledge-bases/${id}`, { method: 'DELETE' })
}

/** 库下文档，按上传时间倒序 */
export function listDocuments(kbId: number) {
  return request<KnowledgeDocument[]>(`/knowledge-bases/${kbId}/documents`)
}

/** 上传即返回 PENDING，后台异步解析；前端轮询 detail 直到 READY / FAILED */
export function uploadDocument(kbId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<KnowledgeDocument>(`/knowledge-bases/${kbId}/documents`, {
    method: 'POST',
    body: form,
  })
}

export function getDocument(id: number) {
  return request<KnowledgeDocument>(`/documents/${id}`)
}

export function deleteDocument(id: number) {
  return request<void>(`/documents/${id}`, { method: 'DELETE' })
}

/** 清空旧切片后重新解析 */
export function reindexDocument(id: number) {
  return request<KnowledgeDocument>(`/documents/${id}/reindex`, { method: 'POST' })
}

export interface ChunkDetail {
  id: number
  documentId: number
  kbId: number
  filename: string
  chunkIndex: number
  tokenCount: number
  content: string
}

/** 引用溯源：按切片 ID 取完整原文，只能看自己的库 */
export function getChunk(id: number) {
  return request<ChunkDetail>(`/chunks/${id}`)
}

export function searchKnowledgeBase(kbId: number, query: string, topK?: number) {
  return request<SearchHit[]>(`/knowledge-bases/${kbId}/search`, {
    method: 'POST',
    body: JSON.stringify({ query, topK }),
  })
}

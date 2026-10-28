import { request } from './http'

/** 客服策略由后端下发，阈值调参不用改前端 */
export interface SupportPolicy {
  minScore: number
  topK: number
  handoffEnabled: boolean
}

export function getSupportPolicy() {
  return request<SupportPolicy>('/support/policy')
}

export type TicketStatus = 'OPEN' | 'CLOSED'

export interface SupportTicket {
  id: number
  question: string
  status: TicketStatus
  bestScore: number
  note: string
  conversationUid: string
  createdAt: string
  updatedAt: string
}

export interface SupportTicketInput {
  question: string
  conversationUid?: string
  kbId?: number
  bestScore?: number
}

/** 兜底时提交转人工工单 */
export function createSupportTicket(input: SupportTicketInput) {
  return request<SupportTicket>('/support/tickets', { method: 'POST', body: JSON.stringify(input) })
}

/** 我的工单，最新 20 条 */
export function listSupportTickets() {
  return request<SupportTicket[]>('/support/tickets')
}

/** 关闭工单（幂等） */
export function closeSupportTicket(id: number) {
  return request<SupportTicket>(`/support/tickets/${id}/close`, { method: 'POST' })
}

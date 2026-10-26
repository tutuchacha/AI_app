import { request } from './http'

export interface ConversationSummary {
  uid: string
  title: string
  module: string
  lastMessageAt: string
}

export interface ConversationMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  status: string
  tokensIn: number
  tokensOut: number
  latencyMs: number
  model: string
  createdAt: string
}

export interface ConversationDetail {
  uid: string
  title: string
  module: string
  messages: ConversationMessage[]
}

/** 左侧会话列表：按最后消息时间倒序 */
export function listConversations(module = 'chat') {
  return request<ConversationSummary[]>(`/conversations?module=${encodeURIComponent(module)}`)
}

/** 会话详情：用于刷新/切换会话时恢复历史消息 */
export function getConversation(uid: string) {
  return request<ConversationDetail>(`/conversations/${encodeURIComponent(uid)}`)
}

export type ChatMode = 'chat' | 'companion' | 'support'

export type MessageStatus = 'streaming' | 'done' | 'stopped' | 'failed'

export interface Citation {
  id: string
  title: string
  snippet: string
  score: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: Citation[]
  status: MessageStatus
  createdAt: number
}

export interface ChatRequest {
  conversationId?: string
  content: string
  mode: ChatMode
  personaId?: string
  model?: string
  /** 重试上一轮失败的回答：后端不再插入新的 user 消息 */
  retry?: boolean
}

export type FinishReason = 'stop' | 'length' | 'stopped'

export type ChatEvent =
  | { type: 'meta'; messageId: string; conversationId: string; model: string }
  | { type: 'delta'; content: string }
  | { type: 'citation'; citation: Citation }
  | { type: 'usage'; promptTokens: number; completionTokens: number }
  | { type: 'done'; finishReason: FinishReason; latencyMs: number }
  | { type: 'error'; code: string; message: string }

export interface ChatHandlers {
  onMeta?: (event: { messageId: string; conversationId: string; model: string }) => void
  onDelta: (content: string) => void
  onCitation?: (citation: Citation) => void
  onUsage?: (usage: { promptTokens: number; completionTokens: number }) => void
  onDone?: (event: { finishReason: FinishReason; latencyMs: number }) => void
  onError?: (event: { code: string; message: string }) => void
}

/** 传输层抽象：M1 换成 SSE / WS 时，聊天页面不需要改。 */
export interface ChatTransport {
  readonly name: string
  send(request: ChatRequest, handlers: ChatHandlers, signal: AbortSignal): Promise<void>
}

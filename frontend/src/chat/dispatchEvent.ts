import type { ChatEvent, ChatHandlers } from '@/types/chat'

/** SSE 与 WebSocket 共用：把一条 ChatEvent 分发给页面回调 */
export function dispatchEvent(event: ChatEvent, handlers: ChatHandlers): void {
  switch (event.type) {
    case 'meta':
      handlers.onMeta?.({
        messageId: event.messageId ?? '',
        conversationId: event.conversationId ?? '',
        model: event.model ?? '',
      })
      break
    case 'delta':
      if (event.content) handlers.onDelta(event.content)
      break
    case 'citation':
      if (event.citation) handlers.onCitation?.(event.citation)
      break
    case 'usage':
      handlers.onUsage?.({
        promptTokens: event.promptTokens ?? 0,
        completionTokens: event.completionTokens ?? 0,
      })
      break
    case 'done':
      handlers.onDone?.({
        finishReason:
          event.finishReason === 'length' || event.finishReason === 'stopped'
            ? event.finishReason
            : 'stop',
        latencyMs: event.latencyMs ?? 0,
      })
      break
    case 'error':
      handlers.onError?.({
        code: event.code ?? 'UNKNOWN',
        message: event.message ?? '模型调用失败，请稍后重试',
      })
      break
  }
}

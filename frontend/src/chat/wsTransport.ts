import { API_BASE, getToken } from '@/api/http'
import type { ChatEvent, ChatHandlers, ChatRequest, ChatTransport } from '@/types/chat'
import { dispatchEvent } from './dispatchEvent'

/**
 * M1-07 WebSocket 通道：一条连接跑一轮对话，帧就是 ChatEvent JSON。
 * 主动停止 = 关闭连接，后端取消上游并把消息标为 STOPPED，和 SSE 语义一致。
 */
export class WsChatTransport implements ChatTransport {
  readonly name = 'ws'

  send(request: ChatRequest, handlers: ChatHandlers, signal: AbortSignal): Promise<void> {
    if (signal.aborted) {
      return Promise.reject(new DOMException('Aborted', 'AbortError'))
    }

    const token = getToken()
    if (!token) {
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    const socket = new WebSocket(chatUrl(token))
    let settled = false
    let terminal = false

    return new Promise<void>((resolve, reject) => {
      const cleanup = () => {
        signal.removeEventListener('abort', onAbort)
        socket.onopen = null
        socket.onmessage = null
        socket.onerror = null
        socket.onclose = null
      }
      const finish = (action: () => void) => {
        if (settled) return
        settled = true
        cleanup()
        action()
      }
      const closeSocket = () => {
        try {
          socket.close()
        } catch {
          /* 已经关闭 */
        }
      }
      const onAbort = () => {
        closeSocket()
        finish(() => reject(new DOMException('Aborted', 'AbortError')))
      }

      signal.addEventListener('abort', onAbort)

      socket.onopen = () => {
        socket.send(
          JSON.stringify({
            content: request.content,
            conversationId: request.conversationId,
            module: request.mode,
            model: request.model,
            retry: request.retry,
          }),
        )
      }

      socket.onmessage = (message) => {
        let event: ChatEvent
        try {
          event = JSON.parse(String(message.data)) as ChatEvent
        } catch {
          return
        }
        dispatchEvent(event, handlers)
        if (event.type === 'done' || event.type === 'error') {
          terminal = true
          finish(() => resolve())
          closeSocket()
        }
      }

      socket.onerror = () => {
        if (!terminal) {
          finish(() => reject(new Error('WebSocket 连接失败，请确认后端 8080 已启动')))
        }
      }

      socket.onclose = () => {
        if (!terminal && !settled) {
          finish(() => reject(new Error('WebSocket 连接中断，请稍后重试')))
        }
      }
    })
  }
}

/** API_BASE 是 /api 时走同源（Vite 代理 /ws）；绝对地址时换成 ws(s) 协议 */
function chatUrl(token: string): string {
  const query = `?token=${encodeURIComponent(token)}`
  if (API_BASE.startsWith('http')) {
    const url = new URL(API_BASE)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    url.pathname = '/ws/chat'
    url.search = query
    return url.toString()
  }
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/chat${query}`
}

export const wsTransport = new WsChatTransport()

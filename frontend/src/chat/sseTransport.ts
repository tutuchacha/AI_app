import { API_BASE, getToken } from '@/api/http'
import type { ChatEvent, ChatHandlers, ChatRequest, ChatTransport } from '@/types/chat'
import { dispatchEvent } from './dispatchEvent'

/**
 * 真实 SSE 通道：POST /api/chat/stream，按空行切帧，解析 data: 里的 JSON 事件。
 * 页面只依赖 ChatTransport，不关心这里是 fetch + ReadableStream。
 */
export class SseChatTransport implements ChatTransport {
  readonly name = 'sse'

  async send(request: ChatRequest, handlers: ChatHandlers, signal: AbortSignal): Promise<void> {
    const token = getToken()
    let response: Response
    try {
      response = await fetch(`${API_BASE}/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          content: request.content,
          conversationId: request.conversationId,
          module: request.mode,
          model: request.model,
          retry: request.retry,
        }),
        signal,
      })
    } catch (cause) {
      if (isAbort(cause)) throw cause
      throw new Error('无法连接后端，请确认 8080 已启动')
    }

    if (!response.ok) {
      throw new Error(await readError(response))
    }
    if (!response.body) {
      throw new Error('当前浏览器不支持流式响应')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let match = buffer.match(/\r?\n\r?\n/)
        while (match && match.index !== undefined) {
          const frame = buffer.slice(0, match.index)
          buffer = buffer.slice(match.index + match[0].length)
          dispatchFrame(frame, handlers)
          match = buffer.match(/\r?\n\r?\n/)
        }
      }
      buffer += decoder.decode()
      if (buffer.trim()) dispatchFrame(buffer, handlers)
    } finally {
      reader.releaseLock()
    }
  }
}

function dispatchFrame(frame: string, handlers: ChatHandlers): void {
  const payload = frame
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')
  if (!payload) return

  let event: ChatEvent
  try {
    event = JSON.parse(payload) as ChatEvent
  } catch {
    return
  }
  dispatchEvent(event, handlers)
}

function isAbort(cause: unknown): boolean {
  return cause instanceof DOMException && cause.name === 'AbortError'
}

async function readError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string; traceId?: string }
    if (body.message) {
      return body.traceId ? `${body.message}（traceId: ${body.traceId}）` : body.message
    }
  } catch {
    /* 非 JSON 响应，走下面的兜底 */
  }
  if (response.status === 401) return '登录已过期，请重新登录'
  return `请求失败（HTTP ${response.status}）`
}

export const sseTransport = new SseChatTransport()

import { reactive } from 'vue'
import { API_BASE, getToken } from '@/api/http'
import type { ChatEvent, ChatHandlers, ChatRequest, ChatTransport } from '@/types/chat'
import { dispatchEvent } from './dispatchEvent'

interface Round {
  handlers: ChatHandlers
  resolve: () => void
  reject: (error: unknown) => void
  startedAt: number
  firstToken: boolean
  detach: () => void
}

function abortError() {
  return new DOMException('Aborted', 'AbortError')
}

/**
 * M1-09 WebSocket 持久连接：一次握手跑多轮。
 * 客户端帧：{ type: 'chat' | 'stop' | 'ping' }；服务端帧：ChatEvent 或 { type: 'pong' }。
 * 停止只发 stop 帧并取消当前轮，连接保留给下一轮复用。
 */
export class WsChatTransport implements ChatTransport {
  readonly name = 'ws'

  /** 页面页脚展示用：握手次数、本连接轮数、首字耗时 */
  readonly stats = reactive({
    connected: false,
    handshakes: 0,
    rounds: 0,
    firstTokenMs: 0,
  })

  private socket: WebSocket | null = null
  private opening: Promise<WebSocket> | null = null
  private current: Round | null = null
  private heartbeat: number | null = null
  private lastPong = 0

  async send(request: ChatRequest, handlers: ChatHandlers, signal: AbortSignal): Promise<void> {
    if (signal.aborted) throw abortError()

    await this.connect()
    if (signal.aborted) throw abortError()
    if (this.current) throw new Error('上一轮还在生成，请稍候')

    await new Promise<void>((resolve, reject) => {
      const round: Round = {
        handlers,
        resolve,
        reject,
        startedAt: performance.now(),
        firstToken: true,
        detach: () => undefined,
      }
      const onAbort = () => {
        this.sendFrame({ type: 'stop' })
        this.finish(abortError())
      }
      signal.addEventListener('abort', onAbort, { once: true })
      round.detach = () => signal.removeEventListener('abort', onAbort)

      this.current = round
      this.stats.rounds += 1
      this.stats.firstTokenMs = 0

      const sent = this.sendFrame({
        type: 'chat',
        requestId: `w_${Date.now().toString(36)}`,
        content: request.content,
        conversationId: request.conversationId,
        module: request.mode,
        model: request.model,
        retry: request.retry,
        kbId: request.kbId,
      })
      if (!sent) {
        this.finish(new Error('WebSocket 连接已断开，请重试'))
      }
    })
  }

  /** 切换回 SSE 时调用，避免空闲连接与心跳常驻 */
  close() {
    this.stopHeartbeat()
    this.finish(new Error('WebSocket 已关闭'))
    const socket = this.socket
    this.socket = null
    this.opening = null
    this.stats.connected = false
    this.stats.rounds = 0
    this.stats.firstTokenMs = 0
    if (socket) {
      socket.onclose = null
      socket.onerror = null
      socket.onmessage = null
      try {
        socket.close()
      } catch {
        /* 已经关闭 */
      }
    }
  }

  private connect(): Promise<WebSocket> {
    if (this.socket?.readyState === WebSocket.OPEN) {
      return Promise.resolve(this.socket)
    }
    if (this.opening) return this.opening

    const token = getToken()
    if (!token) return Promise.reject(new Error('登录已过期，请重新登录'))

    const socket = new WebSocket(chatUrl(token))
    this.socket = socket
    this.opening = new Promise<WebSocket>((resolve, reject) => {
      const timer = window.setTimeout(() => {
        cleanup()
        this.opening = null
        this.socket = null
        socket.close()
        reject(new Error('WebSocket 连接超时，请稍后重试'))
      }, 8000)

      const cleanup = () => {
        window.clearTimeout(timer)
        socket.onopen = null
        socket.onerror = null
        socket.onclose = null
      }

      socket.onopen = () => {
        cleanup()
        this.opening = null
        this.stats.connected = true
        this.stats.handshakes += 1
        this.stats.rounds = 0
        this.startHeartbeat()
        this.bindSocket(socket)
        resolve(socket)
      }

      socket.onerror = () => {
        cleanup()
        this.opening = null
        this.socket = null
        reject(new Error('WebSocket 连接失败，请确认后端 8080 已启动'))
      }

      socket.onclose = () => {
        cleanup()
        this.opening = null
        this.socket = null
        reject(new Error('WebSocket 连接中断，请稍后重试'))
      }
    })
    return this.opening
  }

  private bindSocket(socket: WebSocket) {
    socket.onmessage = (message) => this.handleMessage(message)
    socket.onerror = () => {
      // 具体原因由 onclose 统一处理
    }
    socket.onclose = () => {
      if (this.socket === socket) this.socket = null
      this.stopHeartbeat()
      this.stats.connected = false
      this.finish(new Error('WebSocket 连接中断，请稍后重试'))
    }
  }

  private handleMessage(message: MessageEvent) {
    let frame: ChatEvent | { type: 'pong' }
    try {
      frame = JSON.parse(String(message.data)) as ChatEvent | { type: 'pong' }
    } catch {
      return
    }
    if (frame.type === 'pong') {
      this.lastPong = Date.now()
      return
    }
    const event = frame as ChatEvent

    const round = this.current
    if (!round) return

    if (event.type === 'delta' && round.firstToken) {
      round.firstToken = false
      this.stats.firstTokenMs = Math.round(performance.now() - round.startedAt)
    }
    dispatchEvent(event, round.handlers)
    if (event.type === 'done' || event.type === 'error') {
      this.finish()
    }
  }

  private finish(error?: Error) {
    const round = this.current
    if (!round) return
    this.current = null
    round.detach()
    if (error) round.reject(error)
    else round.resolve()
  }

  private sendFrame(payload: unknown): boolean {
    const socket = this.socket
    if (!socket || socket.readyState !== WebSocket.OPEN) return false
    socket.send(JSON.stringify(payload))
    return true
  }

  private startHeartbeat() {
    this.stopHeartbeat()
    this.lastPong = Date.now()
    this.heartbeat = window.setInterval(() => {
      const socket = this.socket
      if (!socket || socket.readyState !== WebSocket.OPEN) return
      // 45s 没收到 pong 认为连接假死，关闭后下次发送自动重连
      if (Date.now() - this.lastPong > 45000) {
        socket.close()
        return
      }
      this.sendFrame({ type: 'ping' })
    }, 20000)
  }

  private stopHeartbeat() {
    if (this.heartbeat !== null) {
      window.clearInterval(this.heartbeat)
      this.heartbeat = null
    }
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

import { ref } from 'vue'
import type { ChatMessage, ChatMode, ChatTransport, Citation } from '@/types/chat'
import { mockTransport } from './mockTransport'

interface UseChatOptions {
  mode: ChatMode
  personaId?: string
  greeting?: string
}

function createId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}

function greetingMessage(text: string): ChatMessage {
  return {
    id: createId('greeting'),
    role: 'assistant',
    content: text,
    citations: [],
    status: 'done',
    createdAt: Date.now(),
  }
}

export function useChat(options: UseChatOptions) {
  const transport: ChatTransport = mockTransport
  const messages = ref<ChatMessage[]>(options.greeting ? [greetingMessage(options.greeting)] : [])
  const streaming = ref(false)
  const error = ref('')
  const lastLatency = ref<number | null>(null)

  let controller: AbortController | null = null
  let conversationId = createId('local')

  async function send(content: string) {
    const text = content.trim()
    if (!text || streaming.value) return

    error.value = ''
    lastLatency.value = null
    streaming.value = true

    messages.value.push({
      id: createId('user'),
      role: 'user',
      content: text,
      citations: [],
      status: 'done',
      createdAt: Date.now(),
    })

    const assistant: ChatMessage = {
      id: createId('assistant'),
      role: 'assistant',
      content: '',
      citations: [],
      status: 'streaming',
      createdAt: Date.now(),
    }
    messages.value.push(assistant)

    // 取回数组中的响应式代理：直接修改 push 前的原始对象不会触发视图更新
    const live = messages.value[messages.value.length - 1]

    controller = new AbortController()

    try {
      await transport.send(
        {
          conversationId,
          content: text,
          mode: options.mode,
          personaId: options.personaId,
        },
        {
          onDelta: (delta) => {
            live.content += delta
          },
          onCitation: (citation: Citation) => {
            live.citations.push(citation)
          },
          onDone: (event) => {
            live.status = 'done'
            lastLatency.value = event.latencyMs
          },
          onError: (event) => {
            live.status = 'failed'
            error.value = event.message
          },
        },
        controller.signal,
      )
      if (live.status === 'streaming') live.status = 'done'
    } catch (cause) {
      const aborted = cause instanceof DOMException && cause.name === 'AbortError'
      live.status = aborted ? 'stopped' : 'failed'
      if (!aborted) error.value = '连接中断，请稍后重试（M0 Mock 通道）'
    } finally {
      streaming.value = false
      controller = null
    }
  }

  function stop() {
    controller?.abort()
  }

  function reset() {
    stop()
    messages.value = options.greeting ? [greetingMessage(options.greeting)] : []
    error.value = ''
    lastLatency.value = null
    conversationId = createId('local')
  }

  return {
    transportName: transport.name,
    messages,
    streaming,
    error,
    lastLatency,
    send,
    stop,
    reset,
  }
}

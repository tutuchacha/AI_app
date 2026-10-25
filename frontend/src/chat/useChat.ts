import { ref } from 'vue'
import type { ChatMessage, ChatMode, ChatTransport, Citation } from '@/types/chat'
import { mockTransport } from './mockTransport'
import { sseTransport } from './sseTransport'

/** VITE_USE_MOCK=true 时走本地假模型，便于离线开发 */
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

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
  const transport: ChatTransport = USE_MOCK ? mockTransport : sseTransport
  const messages = ref<ChatMessage[]>(options.greeting ? [greetingMessage(options.greeting)] : [])
  const streaming = ref(false)
  const error = ref('')
  const lastLatency = ref<number | null>(null)
  const modelName = ref('qwen-plus')
  const lastUsage = ref<{ promptTokens: number; completionTokens: number } | null>(null)

  let controller: AbortController | null = null
  let conversationId = USE_MOCK ? createId('local') : ''

  async function send(content: string, model?: string) {
    const text = content.trim()
    if (!text || streaming.value) return

    error.value = ''
    lastLatency.value = null
    lastUsage.value = null
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

    // ȡ�������е���Ӧʽ������ֱ���޸� push ǰ��ԭʼ���󲻻ᴥ����ͼ����
    const live = messages.value[messages.value.length - 1]

    controller = new AbortController()

    try {
      await transport.send(
        {
          conversationId: conversationId || undefined,
          content: text,
          mode: options.mode,
          personaId: options.personaId,
          model: model || undefined,
        },
        {
          onMeta: (event) => {
            if (event.conversationId) conversationId = event.conversationId
            if (event.model) modelName.value = event.model
            if (event.messageId) live.id = event.messageId
          },
          onDelta: (delta) => {
            live.content += delta
          },
          onCitation: (citation: Citation) => {
            live.citations.push(citation)
          },
          onUsage: (usage) => {
            lastUsage.value = usage
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
      if (!aborted) {
        error.value = cause instanceof Error && cause.message
            ? cause.message
            : '连接中断，请稍后重试'
      }
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
    lastUsage.value = null
    conversationId = USE_MOCK ? createId('local') : ''
  }

  return {
    transportName: transport.name,
    messages,
    streaming,
    error,
    lastLatency,
    modelName,
    lastUsage,
    send,
    stop,
    reset,
  }
}

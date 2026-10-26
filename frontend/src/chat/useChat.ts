import { computed, ref } from 'vue'
import type { ChatMessage, ChatMode, ChatTransport, Citation, FinishReason } from '@/types/chat'
import { mockTransport } from './mockTransport'
import { sseTransport } from './sseTransport'
import { wsTransport } from './wsTransport'

/** VITE_USE_MOCK=true 时走本地假模型，便于离线开发 */
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'
const TRANSPORT_KEY = 'xy_transport'

export type TransportKind = 'sse' | 'ws'

function pickTransport(): ChatTransport {
  if (USE_MOCK) return mockTransport
  return localStorage.getItem(TRANSPORT_KEY) === 'ws' ? wsTransport : sseTransport
}

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
  const transport = ref<ChatTransport>(pickTransport())
  const transportName = computed(() => transport.value.name)
  const transportStats = computed(() =>
    transport.value.name === 'ws' ? wsTransport.stats : null,
  )
  const messages = ref<ChatMessage[]>(options.greeting ? [greetingMessage(options.greeting)] : [])
  const streaming = ref(false)
  const error = ref('')
  const lastLatency = ref<number | null>(null)
  const modelName = ref('qwen-plus')
  const lastUsage = ref<{ promptTokens: number; completionTokens: number } | null>(null)
  const lastFinishReason = ref<FinishReason | null>(null)

  let controller: AbortController | null = null
  const conversationId = ref(USE_MOCK ? createId('local') : '')

  async function send(content: string, model?: string) {
    const text = content.trim()
    if (!text || streaming.value) return

    messages.value.push({
      id: createId('user'),
      role: 'user',
      content: text,
      citations: [],
      status: 'done',
      createdAt: Date.now(),
    })

    await runStream(text, model, false)
  }

  /** 重试上一条失败/被停止的回答：保留提问，丢掉半截回答后重新生成 */
  async function retry(model?: string) {
    if (streaming.value) return
    let index = -1
    for (let i = messages.value.length - 1; i >= 0; i -= 1) {
      const item = messages.value[i]
      if (item.role === 'assistant' && (item.status === 'failed' || item.status === 'stopped')) {
        index = i
        break
      }
    }
    if (index <= 0) return
    const question = messages.value[index - 1]
    if (!question || question.role !== 'user') return

    messages.value.splice(index)
    await runStream(question.content, model, true)
  }

  async function runStream(text: string, model: string | undefined, isRetry: boolean) {
    error.value = ''
    lastLatency.value = null
    lastUsage.value = null
    lastFinishReason.value = null
    streaming.value = true

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
      await transport.value.send(
        {
          conversationId: conversationId.value || undefined,
          content: text,
          mode: options.mode,
          personaId: options.personaId,
          model: model || undefined,
          retry: isRetry || undefined,
        },
        {
          onMeta: (event) => {
            if (event.conversationId) conversationId.value = event.conversationId
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
            lastFinishReason.value = event.finishReason
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

  /** 切换传输通道：仅空闲时允许，选择记在 localStorage */
  function setTransport(kind: TransportKind) {
    if (USE_MOCK || streaming.value) return
    if (kind === 'sse') wsTransport.close()
    transport.value = kind === 'ws' ? wsTransport : sseTransport
    localStorage.setItem(TRANSPORT_KEY, kind)
  }

  function reset() {
    stop()
    messages.value = options.greeting ? [greetingMessage(options.greeting)] : []
    error.value = ''
    lastLatency.value = null
    lastUsage.value = null
    lastFinishReason.value = null
    conversationId.value = USE_MOCK ? createId('local') : ''
  }

  /** 恢复后端历史：切会话或刷新页面时用，替换当前消息并接上 conversationId */
  function hydrate(uid: string, rows: ChatMessage[]) {
    stop()
    messages.value = rows
    conversationId.value = uid
    error.value = ''
    lastLatency.value = null
    lastUsage.value = null
    lastFinishReason.value = null
    streaming.value = false
  }

  return {
    transportName,
    transportStats,
    messages,
    streaming,
    error,
    lastLatency,
    modelName,
    lastUsage,
    lastFinishReason,
    conversationId,
    send,
    retry,
    setTransport,
    stop,
    reset,
    hydrate,
  }
}

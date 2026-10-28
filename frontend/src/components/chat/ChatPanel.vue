<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import type { ChatMessage, ChatMode, Citation } from '@/types/chat'
import type { Persona } from '@/data/personas'
import { listKnowledgeBases } from '@/api/knowledge'
import type { ChunkDetail, KnowledgeBase } from '@/api/knowledge'
import { getChunk } from '@/api/knowledge'
import { useChat } from '@/chat/useChat'
import MessageItem from './MessageItem.vue'
import ChatComposer from './ChatComposer.vue'

const props = withDefaults(
  defineProps<{
    mode?: ChatMode
    persona?: Persona | null
    title?: string
    subtitle?: string
    greeting?: string
    suggestions?: string[]
    /** 是否显示选库器；默认只有通用对话显示，客服页显式打开 */
    kbPicker?: boolean | null
    /** 客服兜底阈值：最高引用分低于它就算「没查到」 */
    lowScore?: number
  }>(),
  {
    mode: 'chat',
    persona: null,
    title: '',
    subtitle: '',
    greeting: '',
    suggestions: () => [],
    kbPicker: null,
    lowScore: 0,
  },
)

const {
  transportName,
  transportStats,
  messages,
  streaming,
  error,
  lastLatency,
  modelName,
  lastUsage,
  lastFinishReason,
  send,
  retry,
  setTransport,
  stop,
  reset,
  hydrate,
  conversationId,
} = useChat({
  mode: props.mode,
  personaId: props.persona?.id,
  greeting: props.greeting,
})

const emit = defineEmits<{ changed: [uid: string]; handoff: [payload: { question: string; bestScore: number }] }>()

const model = ref('qwen-plus')
const listRef = ref<HTMLElement>()
const composerRef = ref<InstanceType<typeof ChatComposer>>()
const bases = ref<KnowledgeBase[]>([])
/** 客服页用 v-model:kb-id 控制选中的库；通用对话自己管理 */
const kbId = defineModel<number>('kbId', { default: 0 })
const kbPickerVisible = computed(() => props.kbPicker ?? props.mode === 'chat')
const activeKb = computed(() => bases.value.find((item) => item.id === kbId.value) ?? null)

const displayTitle = computed(() => props.title || props.persona?.name || '新对话')
const displaySubtitle = computed(() => {
  if (props.subtitle) return props.subtitle
  if (props.persona) return `${props.persona.title} · ${props.persona.tagline}`
  return transportName.value === 'sse'
      ? 'SSE 流式 · 真实模型，逐字返回'
      : transportName.value === 'ws'
          ? 'WebSocket 持久连接 · 一次握手多轮对话'
          : 'Mock 通道，离线验证界面与交互'
})
const modeLabel = computed(() => {
  if (props.mode === 'support') return '客服模式'
  if (props.mode === 'companion') return '陪伴模式'
  return '通用对话'
})
const hasUserMessage = computed(() => messages.value.some((item) => item.role === 'user'))

/** 右侧导航：每个用户提问一条，内容太长截断 */
const questions = computed(() =>
  messages.value
    .filter((item) => item.role === 'user')
    .map((item) => ({
      id: item.id,
      text: item.content.replace(/\s+/g, ' ').trim().slice(0, 26),
    })),
)
const outlineOpen = ref(false)
const activeIndex = ref(-1)
const stickToBottom = ref(true)

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
    scheduleActive()
  })
}

/** 流式输出时每个 token 都会滚一次，高亮刷新做 120ms 合并，避免频繁布局计算 */
let activeTimer: number | null = null
function scheduleActive() {
  if (activeTimer) return
  activeTimer = window.setTimeout(() => {
    activeTimer = null
    updateActive()
  }, 120)
}

/** 重新激活时列表高度可能还没稳定，隔几拍再贴底并刷新导航高亮 */
function pinToBottom() {
  scrollToBottom()
  updateActive()
  for (const delay of [60, 220, 500]) {
    window.setTimeout(() => {
      scrollToBottom()
      updateActive()
    }, delay)
  }
}

function onScroll() {
  const el = listRef.value
  if (!el) return
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  updateActive()
}

function updateActive() {
  const el = listRef.value
  if (!el) return
  const nodes = [...el.querySelectorAll<HTMLElement>('.message.user')]
  if (!nodes.length) {
    activeIndex.value = -1
    return
  }
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 8) {
    activeIndex.value = nodes.length - 1
    return
  }
  const line = el.getBoundingClientRect().top + 48
  let index = -1
  nodes.forEach((node, i) => {
    if (node.getBoundingClientRect().top <= line) index = i
  })
  activeIndex.value = index
}

function jumpTo(id: string) {
  const el = listRef.value
  if (!el) return
  const target = el.querySelector<HTMLElement>(`[data-mid="${id}"]`)
  if (!target) return
  const top = target.getBoundingClientRect().top - el.getBoundingClientRect().top + el.scrollTop - 12
  el.scrollTop = Math.max(0, top)
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  updateActive()
}

function pick(text: string) {
  if (streaming.value) return
  stickToBottom.value = true
  void send(text, model.value, kbId.value || undefined).then(() => emit('changed', conversationId.value))
}

function handleSend(text: string) {
  stickToBottom.value = true
  void send(text, model.value, kbId.value || undefined).then(() => emit('changed', conversationId.value))
}

/** 最后一条是失败/停止的回答时给出重试入口 */
const canRetry = computed(() => {
  const last = messages.value[messages.value.length - 1]
  return !!last && last.role === 'assistant' && (last.status === 'failed' || last.status === 'stopped')
})

function handleRetry() {
  if (streaming.value) return
  stickToBottom.value = true
  void retry(model.value, kbId.value || undefined).then(() => emit('changed', conversationId.value))
}

/** 客服兜底：答完发现最高引用分不够，就把「转人工」摆出来，而不是让用户继续猜 */
const lowConfidence = computed(() => {
  if (props.mode !== 'support' || props.lowScore <= 0) return false
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'assistant' || last.status !== 'done') return false
  return bestScore(last) < props.lowScore
})

const lastScore = computed(() => {
  const last = messages.value[messages.value.length - 1]
  return last ? bestScore(last) : 0
})

function bestScore(message: ChatMessage) {
  return message.citations.reduce((max, item) => Math.max(max, item.score ?? 0), 0)
}

/** 引用溯源弹窗：按切片 ID 拉原文，并把引用摘要那段高亮出来 */
const chunkVisible = ref(false)
const chunkLoading = ref(false)
const chunkError = ref('')
const chunk = ref<ChunkDetail | null>(null)
const chunkHit = ref('')

async function openCitation(citation: Citation) {
  chunkVisible.value = true
  chunkLoading.value = true
  chunkError.value = ''
  chunk.value = null
  chunkHit.value = citation.snippet.replace(/…$/, '').trim()
  try {
    chunk.value = await getChunk(Number(citation.id))
  } catch (err) {
    chunkError.value = err instanceof Error ? err.message : '原文加载失败'
  } finally {
    chunkLoading.value = false
  }
}

/**
 * 命中段切成「前 / 中 / 后」三段渲染。
 * 引用摘要里空白被压成单个空格，原文有换行，所以用正则把空白放宽再定位。
 */
const chunkParts = computed(() => {
  const content = chunk.value?.content ?? ''
  const probe = chunkHit.value.slice(0, 16)
  if (!probe) return { before: content, hit: '', after: '' }
  const pattern = probe
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    .replace(/\s+/g, '\\s+')
  const match = content.match(new RegExp(pattern))
  if (!match || match.index === undefined) return { before: content, hit: '', after: '' }
  return {
    before: content.slice(0, match.index),
    hit: match[0],
    after: content.slice(match.index + match[0].length),
  }
})

function requestHandoff() {
  const question = [...messages.value].reverse().find((item) => item.role === 'user')?.content ?? ''
  emit('handoff', { question, bestScore: lastScore.value })
}

/** 后端历史 → 组件消息；status 用大写存储，这里转成前端的小写 */
function toChatMessage(row: {
  id: number
  role: 'user' | 'assistant'
  content: string
  status: string
  citations?: ChatMessage['citations']
  createdAt: string
}) {
  return {
    id: String(row.id),
    role: row.role,
    content: row.content,
    citations: row.citations ?? [],
    status: row.status === 'STOPPED' ? ('stopped' as const) : ('done' as const),
    createdAt: Date.parse(row.createdAt) || Date.now(),
  }
}

/** 切会话/刷新后恢复历史；会话绑定的知识库一并回填到选择器 */
function loadConversation(detail: {
  uid: string
  kbId?: number | null
  messages: Parameters<typeof toChatMessage>[0][]
}) {
  kbId.value = detail.kbId ?? 0
  hydrate(detail.uid, detail.messages.map(toChatMessage))
  stickToBottom.value = true
  nextTick(() => pinToBottom())
}

function restart() {
  reset()
  composerRef.value?.fill('')
  stickToBottom.value = true
  scrollToBottom()
}

watch(
  messages,
  () => {
    if (stickToBottom.value) scrollToBottom()
  },
  { deep: true },
)
let contentObserver: MutationObserver | null = null

/** 知识库列表：只在通用聊天下拉；失败时静默降级为「不指定」 */
async function loadBases() {
  if (!kbPickerVisible.value) return
  try {
    bases.value = await listKnowledgeBases()
    if (kbId.value && !bases.value.some((item) => item.id === kbId.value)) kbId.value = 0
  } catch {
    bases.value = []
  }
}

onMounted(() => {
  scrollToBottom()
  void loadBases()
  const el = listRef.value
  if (!el) return
  // 内容异步撑高（图片/字体/重新挂载）时，若仍「贴底」就继续贴底
  contentObserver = new MutationObserver(() => {
    if (stickToBottom.value) {
      el.scrollTop = el.scrollHeight
      scheduleActive()
    }
  })
  contentObserver.observe(el, { childList: true, subtree: true, characterData: true })
})

onBeforeUnmount(() => contentObserver?.disconnect())

// KeepAlive 缓存后重新进入：固定回最新一条，并刷新导航高亮
onActivated(() => {
  stickToBottom.value = true
  pinToBottom()
  nextTick(updateActive)
  window.setTimeout(updateActive, 240)
  void loadBases()
})

defineExpose({ reset, restart, send, loadConversation })
</script>

<template>
  <section class="panel card">
    <header class="panel-head">
      <div class="who">
        <div class="ai-avatar" :style="persona ? { background: persona.accent } : {}">
          {{ persona?.avatar ?? '✦' }}
        </div>
        <div class="who-text">
          <div class="name-row">
            <h2>{{ displayTitle }}</h2>
            <span class="mode">{{ modeLabel }}</span>
          </div>
          <p>{{ displaySubtitle }}</p>
        </div>
      </div>

      <div class="tools">
        <span class="live" :class="{ on: streaming }">
          <i />
          {{ streaming ? '生成中' : transportName === 'mock' ? 'Mock 通道' : '已连接' }}
        </span>
        <div v-if="transportName !== 'mock'" class="transport-switch" title="切换传输通道">
          <button
            :class="{ on: transportName === 'sse' }"
            :disabled="streaming"
            @click="setTransport('sse')"
          >
            SSE
          </button>
          <button
            :class="{ on: transportName === 'ws' }"
            :disabled="streaming"
            @click="setTransport('ws')"
          >
            WS
          </button>
        </div>
        <el-select
          v-if="kbPickerVisible"
          v-model="kbId"
          size="small"
          class="kb-select"
          :disabled="streaming"
          placeholder="不指定知识库"
          title="选中后检索该库；不指定时沿用会话已绑定的库"
        >
          <el-option :value="0" label="不指定知识库" />
          <el-option v-for="item in bases" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="model" size="small" class="model-select" :disabled="streaming">
          <el-option label="qwen-plus" value="qwen-plus" />
          <el-option label="qwen-turbo" value="qwen-turbo" />
        </el-select>
        <el-button text size="small" title="重新开始" @click="restart">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
      </div>
    </header>

    <div class="messages-wrap">
      <div ref="listRef" class="messages" @scroll.passive="onScroll">
        <div v-if="!messages.length" class="empty">
          <div class="spark">✦</div>
          <h3>从一个具体的问题开始</h3>
          <p>回答由模型逐字流式返回；可随时停止，已生成内容会保留。</p>
          <div v-if="suggestions.length" class="chips">
            <button v-for="item in suggestions" :key="item" @click="pick(item)">{{ item }}</button>
          </div>
        </div>

        <template v-else>
          <MessageItem
            v-for="message in messages"
            :key="message.id"
            :message="message"
            :data-mid="message.id"
            @cite="openCitation"
          />

          <div v-if="suggestions.length && !hasUserMessage" class="chips inline">
            <button v-for="item in suggestions" :key="item" :disabled="streaming" @click="pick(item)">
              {{ item }}
            </button>
          </div>

          <div v-if="lowConfidence" class="handoff">
            <div class="handoff-copy">
              <strong>这条回答没有可靠依据</strong>
              <p>
                最高引用分 {{ Math.round(lastScore * 100) }}%，低于兜底线
                {{ Math.round(lowScore * 100) }}%。已按「不确定」处理，建议转人工确认。
              </p>
            </div>
            <el-button size="small" round type="primary" @click="requestHandoff">转人工</el-button>
          </div>
        </template>
      </div>

      <nav
        v-if="questions.length > 1"
        class="outline"
        :class="{ open: outlineOpen }"
        @mouseenter="outlineOpen = true"
        @mouseleave="outlineOpen = false"
      >
        <div class="outline-dots">
          <i
            v-for="(question, index) in questions"
            :key="question.id"
            :class="{ on: index === activeIndex }"
            :title="question.text"
            @click="jumpTo(question.id)"
          />
        </div>
        <div class="outline-list">
          <p class="outline-title">对话导航</p>
          <button
            v-for="(question, index) in questions"
            :key="question.id"
            :class="{ on: index === activeIndex }"
            @click="jumpTo(question.id)"
          >
            {{ question.text }}
          </button>
        </div>
      </nav>
    </div>

    <div v-if="error" class="error">
      <span>{{ error }}</span>
      <button v-if="canRetry" class="retry" @click="handleRetry">重试</button>
    </div>
    <div v-else-if="canRetry" class="error soft">
      <span>这一条没有生成完</span>
      <button class="retry" @click="handleRetry">重新生成</button>
    </div>

    <ChatComposer
      ref="composerRef"
      :streaming="streaming"
      @send="handleSend"
      @stop="stop"
    />

    <div class="panel-foot">
      <span>{{ transportName === 'ws' ? 'WS 持久连接' : transportName + ' transport' }}</span>
      <span v-if="activeKb">知识库 · {{ activeKb.name }}</span>
      <span v-if="transportStats && transportStats.handshakes">
        握手 {{ transportStats.handshakes }} 次 · 复用 {{ transportStats.rounds }} 轮
      </span>
      <span v-if="transportStats && transportStats.firstTokenMs">
        首字 {{ transportStats.firstTokenMs }}ms
      </span>
      <span v-if="lastLatency !== null">上一条耗时 {{ (lastLatency / 1000).toFixed(2) }}s</span>
      <span v-if="lastUsage">{{ lastUsage.promptTokens }} / {{ lastUsage.completionTokens }} tokens</span>
      <span v-if="lastFinishReason === 'length'" class="warn">已达长度上限，可发「继续」</span>
      <span>{{ transportName === 'mock' ? '离线 Mock' : modelName }}</span>
    </div>

    <el-dialog v-model="chunkVisible" title="引用原文" width="620px" append-to-body>
      <div v-if="chunkLoading" class="chunk-tip">正在读取切片…</div>
      <div v-else-if="chunkError" class="chunk-tip error">{{ chunkError }}</div>
      <template v-else-if="chunk">
        <div class="chunk-meta">
          <span class="doc">DOC</span>
          <strong>{{ chunk.filename }}</strong>
          <span>第 {{ chunk.chunkIndex + 1 }} 片</span>
          <span>{{ chunk.tokenCount }} tokens</span>
        </div>
        <div class="chunk-body">
          {{ chunkParts.before
          }}<mark v-if="chunkParts.hit">{{ chunkParts.hit }}</mark>{{ chunkParts.after }}
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 14px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--xy-line);
  background: #fff;
}

.who {
  display: flex;
  align-items: center;
  gap: 11px;
  flex: 1 1 200px;
  min-width: 0;
}

.ai-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ff8a5c, #ff5e8a 55%, #8b5cff);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.who-text {
  flex: 1 1 auto;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.name-row h2 {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mode {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
  font-size: 9.5px;
  font-weight: 700;
}

.who-text p {
  margin-top: 2px;
  overflow: hidden;
  color: var(--xy-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-left: auto;
  flex: 0 1 auto;
  max-width: 100%;
  min-width: 0;
}

.live {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--xy-muted);
  font-size: 10.5px;
  white-space: nowrap;
}

.live i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c7cad8;
}

.live.on i {
  background: var(--xy-mint);
  box-shadow: 0 0 0 3px rgba(20, 184, 138, 0.14);
  animation: pulse 1s infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.45;
  }
}

.model-select {
  width: 132px;
}

.kb-select {
  width: 150px;
}

.transport-switch {
  display: flex;
  padding: 2px;
  border-radius: 999px;
  background: #eef0f7;
}

.transport-switch button {
  padding: 2px 8px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--xy-muted);
  font-size: 10.5px;
  line-height: 16px;
  cursor: pointer;
}

.transport-switch button.on {
  background: #fff;
  color: var(--xy-ink);
  box-shadow: 0 1px 3px rgba(30, 40, 80, 0.12);
}

.transport-switch button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.messages-wrap {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
}

.messages {
  flex: 1;
  min-height: 0;
  padding: 20px 38px 10px 20px;
  overflow-y: auto;
  background:
    radial-gradient(60% 40% at 100% 0%, rgba(124, 92, 255, 0.04), transparent 70%),
    #fcfcfe;
}

.outline {
  position: absolute;
  top: 14px;
  right: 8px;
  bottom: 14px;
  z-index: 5;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.outline-dots {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 8px 5px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #eef0f6;
  box-shadow: 0 4px 14px rgba(20, 24, 48, 0.06);
  backdrop-filter: blur(6px);
}

.outline-dots i {
  width: 13px;
  height: 2px;
  border-radius: 2px;
  background: #dcdfe9;
  cursor: pointer;
  transition: background 0.15s ease, width 0.15s ease;
}

.outline-dots i.on {
  width: 17px;
  background: var(--xy-coral);
}

.outline-list {
  display: none;
  width: 196px;
  max-height: 100%;
  padding: 9px;
  overflow-y: auto;
  border-radius: 12px;
  border: 1px solid #eef0f6;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 10px 28px rgba(20, 24, 48, 0.12);
}

.outline.open .outline-list {
  display: block;
}

.outline-title {
  margin: 0 0 6px;
  padding: 0 6px;
  color: #9aa0b4;
  font-size: 10px;
  letter-spacing: 0.08em;
}

.outline-list button {
  display: block;
  width: 100%;
  padding: 5px 7px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #6b7086;
  font-size: 11px;
  line-height: 1.5;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.outline-list button:hover {
  background: #f4f5fa;
  color: #2f3448;
}

.outline-list button.on {
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.messages > :deep(.message + .message) {
  margin-top: 18px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 260px;
  padding: 30px 20px;
  text-align: center;
}

.spark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, #fff1ec, #f1edff);
  color: var(--xy-coral);
  font-size: 22px;
  box-shadow: 0 10px 24px rgba(124, 92, 255, 0.1);
}

.empty h3 {
  font-size: 16px;
  font-weight: 700;
}

.empty p {
  max-width: 420px;
  margin-top: 7px;
  color: var(--xy-muted);
  font-size: 12.5px;
  line-height: 1.7;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
}

.chips.inline {
  margin-top: 18px;
}

.chips button {
  padding: 7px 12px;
  border: 1px solid var(--xy-line);
  border-radius: 999px;
  background: #fff;
  color: #555a70;
  font-size: 11.5px;
  cursor: pointer;
  transition: 0.16s ease;
}

.chips button:hover {
  border-color: #ffc3bc;
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.chips button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 0 18px 8px;
  padding: 8px 11px;
  border-radius: 9px;
  background: var(--xy-coral-soft);
  color: #d95245;
  font-size: 11.5px;
}

.error.soft {
  background: #f4f5fa;
  color: #7b8199;
}

.retry {
  flex: 0 0 auto;
  padding: 3px 9px;
  border: 1px solid currentColor;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font-size: 11px;
  cursor: pointer;
}

.retry:hover {
  background: rgba(255, 255, 255, 0.7);
}

.panel-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 5px 18px 7px;
  border-top: 1px solid #f0f1f6;
  background: #fff;
  color: #b0b3c4;
  font-size: 9.5px;
}

.panel-foot .warn {
  color: #d9852f;
}

.panel-foot span:last-child {
  margin-left: auto;
}

@media (max-width: 820px) {
  .model-select,
  .kb-select,
  .live {
    display: none;
  }
  .panel-head {
    padding: 11px 13px;
  }
  .messages {
    padding: 15px 13px 8px;
  }
}

/* 客服兜底卡片：回答不可信时贴在最后一条下面 */
.handoff {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-left: 43px;
  padding: 11px 13px;
  border: 1px dashed #ffd3c8;
  border-radius: 12px;
  background: #fff8f6;
}

.handoff-copy strong {
  color: var(--xy-coral);
  font-size: 12px;
}

.handoff-copy p {
  margin-top: 3px;
  color: #8a8798;
  font-size: 10.5px;
  line-height: 1.6;
}

.chunk-tip {
  padding: 18px 0;
  color: var(--xy-muted);
  font-size: 12px;
  text-align: center;
}

.chunk-tip.error {
  color: var(--xy-coral);
}

.chunk-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--xy-muted);
  font-size: 11px;
}

.chunk-meta strong {
  color: #5f6377;
  font-size: 12px;
}

.chunk-meta .doc {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 9px;
  font-weight: 800;
}

.chunk-body {
  max-height: 52vh;
  padding: 12px 14px;
  overflow-y: auto;
  border: 1px solid var(--xy-line);
  border-radius: 10px;
  background: #fbfbfe;
  color: #4a4e63;
  font-size: 12px;
  line-height: 1.85;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-body mark {
  padding: 0 2px;
  border-radius: 3px;
  background: #fff0b8;
  color: inherit;
}
</style>

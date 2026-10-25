<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RefreshRight } from '@element-plus/icons-vue'
import type { ChatMode } from '@/types/chat'
import type { Persona } from '@/data/personas'
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
  }>(),
  {
    mode: 'chat',
    persona: null,
    title: '',
    subtitle: '',
    greeting: '',
    suggestions: () => [],
  },
)

const {
  transportName,
  messages,
  streaming,
  error,
  lastLatency,
  modelName,
  lastUsage,
  lastFinishReason,
  send,
  stop,
  reset,
} = useChat({
  mode: props.mode,
  personaId: props.persona?.id,
  greeting: props.greeting,
})

const model = ref('qwen-plus')
const listRef = ref<HTMLElement>()
const composerRef = ref<InstanceType<typeof ChatComposer>>()

const displayTitle = computed(() => props.title || props.persona?.name || '新对话')
const displaySubtitle = computed(() => {
  if (props.subtitle) return props.subtitle
  if (props.persona) return `${props.persona.title} · ${props.persona.tagline}`
  return transportName === 'sse'
      ? 'SSE 流式 · 真实模型，逐字返回'
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
  void send(text, model.value)
}

function handleSend(text: string) {
  stickToBottom.value = true
  void send(text, model.value)
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

onMounted(() => {
  scrollToBottom()
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
})

defineExpose({ reset, restart, send })
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
          {{ streaming ? '生成中' : transportName === 'sse' ? '已连接' : 'Mock 通道' }}
        </span>
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
          />

          <div v-if="suggestions.length && !hasUserMessage" class="chips inline">
            <button v-for="item in suggestions" :key="item" :disabled="streaming" @click="pick(item)">
              {{ item }}
            </button>
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

    <div v-if="error" class="error">{{ error }}</div>

    <ChatComposer
      ref="composerRef"
      :streaming="streaming"
      @send="handleSend"
      @stop="stop"
    />

    <div class="panel-foot">
      <span>{{ transportName }} transport</span>
      <span v-if="lastLatency !== null">上一条耗时 {{ (lastLatency / 1000).toFixed(2) }}s</span>
      <span v-if="lastUsage">{{ lastUsage.promptTokens }} / {{ lastUsage.completionTokens }} tokens</span>
      <span v-if="lastFinishReason === 'length'" class="warn">已达长度上限，可发「继续」</span>
      <span>{{ transportName === 'sse' ? modelName : '离线 Mock' }}</span>
    </div>
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
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--xy-line);
  background: #fff;
}

.who {
  display: flex;
  align-items: center;
  gap: 11px;
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
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name-row h2 {
  font-size: 14.5px;
  font-weight: 700;
}

.mode {
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
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
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
  margin: 0 18px 8px;
  padding: 8px 11px;
  border-radius: 9px;
  background: var(--xy-coral-soft);
  color: #d95245;
  font-size: 11.5px;
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
</style>

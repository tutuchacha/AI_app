<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function pick(text: string) {
  if (streaming.value) return
  void send(text, model.value)
}

function handleSend(text: string) {
  void send(text, model.value)
}

function restart() {
  reset()
  composerRef.value?.fill('')
  scrollToBottom()
}

watch(messages, scrollToBottom, { deep: true })
onMounted(scrollToBottom)

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

    <div ref="listRef" class="messages">
      <div v-if="!messages.length" class="empty">
        <div class="spark">✦</div>
        <h3>从一个具体的问题开始</h3>
        <p>回答由模型逐字流式返回；可随时停止，已生成内容会保留。</p>
        <div v-if="suggestions.length" class="chips">
          <button v-for="item in suggestions" :key="item" @click="pick(item)">{{ item }}</button>
        </div>
      </div>

      <template v-else>
        <MessageItem v-for="message in messages" :key="message.id" :message="message" />

        <div v-if="suggestions.length && !hasUserMessage" class="chips inline">
          <button v-for="item in suggestions" :key="item" :disabled="streaming" @click="pick(item)">
            {{ item }}
          </button>
        </div>
      </template>
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

.messages {
  flex: 1;
  min-height: 0;
  padding: 20px 20px 10px;
  overflow-y: auto;
  background:
    radial-gradient(60% 40% at 100% 0%, rgba(124, 92, 255, 0.04), transparent 70%),
    #fcfcfe;
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

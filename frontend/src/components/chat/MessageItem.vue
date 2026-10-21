<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '@/types/chat'

const props = defineProps<{ message: ChatMessage }>()

const isAssistant = computed(() => props.message.role === 'assistant')
const time = computed(() => {
  const date = new Date(props.message.createdAt)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
})
const isTyping = computed(
  () => props.message.status === 'streaming' && props.message.content.length === 0,
)
</script>

<template>
  <div class="message" :class="message.role">
    <div class="avatar" :class="{ ai: isAssistant }">
      <span v-if="isAssistant">✦</span>
      <span v-else>我</span>
    </div>

    <div class="body">
      <div class="meta">
        <strong>{{ isAssistant ? '星屿 AI' : '我' }}</strong>
        <span>{{ time }}</span>
        <span v-if="message.status === 'stopped'" class="status">已停止</span>
        <span v-else-if="message.status === 'failed'" class="status error">失败</span>
      </div>

      <div class="bubble" :class="{ user: !isAssistant }">
        <div v-if="isTyping" class="typing" aria-label="正在输入">
          <i /><i /><i />
        </div>
        <p v-else class="text">{{ message.content }}<span v-if="message.status === 'streaming'" class="caret" /></p>
      </div>

      <div v-if="message.citations.length" class="citations">
        <div class="cit-title">引用来源</div>
        <div v-for="citation in message.citations" :key="citation.id" class="citation">
          <div class="cit-head">
            <span class="doc">DOC</span>
            <strong>{{ citation.title }}</strong>
            <span class="score">{{ Math.round(citation.score * 100) }}%</span>
          </div>
          <p>{{ citation.snippet }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message {
  display: flex;
  gap: 11px;
  animation: fade-up 0.25s ease both;
}

.message.user {
  flex-direction: row-reverse;
}

.avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  margin-top: 2px;
  border-radius: 10px;
  background: #eceef6;
  color: #6d7186;
  font-size: 12px;
  font-weight: 700;
}

.avatar.ai {
  background: linear-gradient(135deg, #ff8a5c, #ff5e8a 55%, #8b5cff);
  color: #fff;
  font-size: 14px;
  box-shadow: 0 6px 14px rgba(255, 94, 138, 0.24);
}

.body {
  min-width: 0;
  max-width: min(760px, 82%);
}

.message.user .body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
  color: #a0a4b6;
  font-size: 10.5px;
}

.meta strong {
  color: #5f6377;
  font-size: 11.5px;
}

.status {
  padding: 1px 6px;
  border-radius: 99px;
  background: #f1f2f8;
  color: #8a8ea3;
}

.status.error {
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.bubble {
  padding: 11px 14px;
  border: 1px solid var(--xy-line);
  border-radius: 4px 14px 14px 14px;
  background: #fff;
  box-shadow: 0 2px 6px rgba(25, 28, 48, 0.03);
}

.bubble.user {
  border-color: transparent;
  border-radius: 14px 4px 14px 14px;
  background: linear-gradient(135deg, #ff7a68, #ff5f86);
  color: #fff;
  box-shadow: 0 8px 20px rgba(255, 107, 94, 0.2);
}

.text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--xy-coral);
  animation: blink 0.9s steps(2) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 18px;
}

.typing i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #b9bccd;
  animation: bounce 1s infinite ease-in-out;
}

.typing i:nth-child(2) {
  animation-delay: 0.12s;
}

.typing i:nth-child(3) {
  animation-delay: 0.24s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-4px);
  }
}

.citations {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 9px;
}

.cit-title {
  color: var(--xy-muted);
  font-size: 10.5px;
  font-weight: 600;
}

.citation {
  padding: 9px 11px;
  border: 1px solid #e6e2ff;
  border-radius: 10px;
  background: #faf9ff;
}

.cit-head {
  display: flex;
  align-items: center;
  gap: 7px;
}

.doc {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 9px;
  font-weight: 800;
}

.cit-head strong {
  font-size: 11.5px;
  font-weight: 650;
}

.score {
  margin-left: auto;
  color: var(--xy-mint);
  font-size: 10.5px;
  font-weight: 700;
}

.citation p {
  margin-top: 4px;
  color: #70748a;
  font-size: 11.5px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .body {
    max-width: 88%;
  }
}
</style>

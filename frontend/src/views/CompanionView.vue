<script setup lang="ts">
import { computed, ref } from 'vue'
import { Star } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import { personas } from '@/data/personas'

const selectedId = ref(personas[0].id)
const selected = computed(() => personas.find((item) => item.id === selectedId.value) ?? personas[0])
</script>

<template>
  <div class="companion-page">
    <LearningNote
      title="角色卡如何驱动一次对话"
      :points="['Persona 数据决定人设与开场白', '同一个 ChatPanel 复用三种模式', 'M4 把角色卡与长期记忆落库']"
      next="下一步：把 systemPrompt 从本地数据搬到 ai_model / persona 表"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">恋爱陪伴</h1>
        <p class="page-subtitle">先选一个角色，再开始对话。当前为 Mock 人设，M4 接入长期记忆。</p>
      </div>
      <span class="pill coral"><el-icon><Star /></el-icon> M4 规划</span>
    </div>

    <div class="companion-layout">
      <aside class="persona-col">
        <button
          v-for="persona in personas"
          :key="persona.id"
          class="persona-card card"
          :class="{ active: persona.id === selectedId }"
          @click="selectedId = persona.id"
        >
          <div class="avatar" :style="{ background: persona.accent }">{{ persona.avatar }}</div>
          <div class="info">
            <div class="name-row">
              <strong>{{ persona.name }}</strong>
              <span>{{ persona.title }}</span>
            </div>
            <p>{{ persona.tagline }}</p>
            <div class="traits">
              <span v-for="trait in persona.traits" :key="trait">{{ trait }}</span>
            </div>
          </div>
        </button>

        <div class="notice">
          <strong>设计要点</strong>
          角色卡不是一段写死的 Prompt，而是「人设 + 语气 + 边界 + 记忆策略」的数据组合。
        </div>
      </aside>

      <ChatPanel
        :key="selected.id"
        class="chat"
        mode="companion"
        :persona="selected"
        :greeting="selected.greeting"
        :suggestions="selected.suggestions"
      />
    </div>
  </div>
</template>

<style scoped>
.companion-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 60px - 54px);
  min-height: 560px;
}

.companion-layout {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.persona-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 300px;
  flex: 0 0 300px;
  overflow-y: auto;
}

.persona-card {
  display: flex;
  gap: 11px;
  padding: 13px;
  text-align: left;
  cursor: pointer;
  transition: 0.18s ease;
}

.persona-card:hover {
  border-color: #d9d2ff;
  transform: translateY(-1px);
}

.persona-card.active {
  border-color: #ffb3ac;
  box-shadow: 0 10px 24px rgba(255, 107, 94, 0.09);
}

.avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  border-radius: 13px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
}

.info {
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.name-row strong {
  font-size: 14px;
}

.name-row span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.info p {
  margin-top: 3px;
  color: #6f7387;
  font-size: 11.5px;
  line-height: 1.5;
}

.traits {
  display: flex;
  gap: 5px;
  margin-top: 7px;
}

.traits span {
  padding: 1px 6px;
  border-radius: 5px;
  background: #f4f5fa;
  color: #8a8ea3;
  font-size: 9.5px;
}

.notice {
  margin-top: 2px;
  padding: 12px;
  border: 1px dashed #e2def7;
  border-radius: 12px;
  background: #faf9ff;
  color: #73778c;
  font-size: 11px;
  line-height: 1.7;
}

.notice strong {
  display: block;
  margin-bottom: 2px;
  color: var(--xy-violet);
  font-size: 10px;
  letter-spacing: 0.04em;
}

.chat {
  flex: 1;
  min-width: 0;
}

@media (max-width: 980px) {
  .companion-page {
    height: auto;
  }
  .companion-layout {
    flex-direction: column;
  }
  .persona-col {
    width: 100%;
    flex-basis: auto;
    flex-direction: row;
    overflow-x: auto;
  }
  .persona-card {
    min-width: 250px;
  }
  .notice {
    display: none;
  }
  .chat {
    height: 620px;
  }
}
</style>

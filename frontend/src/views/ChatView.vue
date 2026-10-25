<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'

interface Conversation {
  id: string
  title: string
  time: string
}

const conversations = ref<Conversation[]>([
  { id: 'c1', title: '如何准备一次自然的表白', time: '10:24' },
  { id: 'c2', title: 'RAG 的切片大小怎么选', time: '09:41' },
  { id: 'c3', title: '帮我拆解一个客服流程', time: '昨天' },
  { id: 'c4', title: '周末约会计划', time: '昨天' },
  { id: 'c5', title: 'Spring AI 和 LangChain4j 对比', time: '周一' },
])

const keyword = ref('')
const activeId = ref<string | null>(null)
const panelRef = ref<InstanceType<typeof ChatPanel>>()

const filtered = computed(() => {
  const key = keyword.value.trim()
  if (!key) return conversations.value
  return conversations.value.filter((item) => item.title.includes(key))
})

const activeTitle = computed(() => {
  const item = conversations.value.find((row) => row.id === activeId.value)
  return item?.title ?? '新对话'
})

const suggestions = [
  '用一句话解释 RAG 是什么',
  '帮我设计一个客服知识库的结构',
  '恋爱陪伴和普通聊天有什么区别？',
]

function newChat() {
  const item: Conversation = {
    id: `c-${Date.now()}`,
    title: '新的对话',
    time: '刚刚',
  }
  conversations.value.unshift(item)
  activeId.value = item.id
  panelRef.value?.restart()
}

function openConversation(item: Conversation) {
  activeId.value = item.id
  panelRef.value?.restart()
  ElMessage.info('M1 接入会话持久化后，这里会恢复历史消息')
}
</script>

<template>
  <div class="chat-page">
    <LearningNote
      title="Transport 抽象 + 流式 UI"
      :points="['useChat 只管理消息状态', 'ChatTransport 负责发送与中断', 'Mock 与 SSE 可无缝替换']"
      next="M1-04 已完成：页面默认走 SSE，设置 VITE_USE_MOCK=true 可离线"
    />

    <div class="chat-layout">
      <aside class="conversations card">
        <div class="conv-head">
          <el-button type="primary" size="small" round class="new-btn" @click="newChat">
            <el-icon><Plus /></el-icon>
            新对话
          </el-button>
        </div>

        <div class="search">
          <el-input v-model="keyword" size="small" placeholder="搜索会话">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>

        <div class="conv-list">
          <button
            v-for="item in filtered"
            :key="item.id"
            class="conv-item"
            :class="{ active: item.id === activeId }"
            @click="openConversation(item)"
          >
            <span class="conv-title">{{ item.title }}</span>
            <span class="conv-time">{{ item.time }}</span>
          </button>
          <p v-if="!filtered.length" class="empty-tip">没有匹配的会话</p>
        </div>

        <div class="conv-foot">
          <span class="dot" />
          历史消息将在 M1-05 落库后恢复
        </div>
      </aside>

      <ChatPanel
        ref="panelRef"
        :title="activeTitle"
        :suggestions="suggestions"
        class="chat-panel"
      />
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: calc(100vh - 60px - 54px);
  min-height: 520px;
}

.chat-layout {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.conversations {
  display: flex;
  flex-direction: column;
  width: 252px;
  flex: 0 0 252px;
  overflow: hidden;
}

.conv-head {
  padding: 12px 12px 8px;
}

.new-btn {
  width: 100%;
}

.search {
  padding: 0 12px 8px;
}

.conv-list {
  flex: 1;
  padding: 2px 8px 8px;
  overflow-y: auto;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 9px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #4d5166;
  text-align: left;
  cursor: pointer;
}

.conv-item:hover {
  background: #f6f6fb;
}

.conv-item.active {
  background: var(--xy-coral-soft);
  color: #d95245;
}

.conv-title {
  flex: 1;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  color: #aeb1c2;
  font-size: 9.5px;
}

.empty-tip {
  padding: 18px 8px;
  color: #aeb1c2;
  font-size: 11px;
  text-align: center;
}

.conv-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-top: 1px solid #f1f2f7;
  color: #aeb1c2;
  font-size: 10px;
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #d5d7e4;
}

.chat-panel {
  flex: 1;
  min-width: 0;
}

@media (max-width: 900px) {
  .conversations {
    display: none;
  }
  .chat-page {
    height: calc(100vh - 60px - 32px);
  }
}
</style>

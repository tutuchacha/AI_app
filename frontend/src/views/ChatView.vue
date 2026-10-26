<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import { getConversation, listConversations } from '@/api/conversations'
import type { ConversationDetail, ConversationSummary } from '@/api/conversations'

const conversations = ref<ConversationSummary[]>([])
const keyword = ref('')
const activeId = ref<string | null>(null)
const loading = ref(false)
const panelRef = ref<InstanceType<typeof ChatPanel>>()

const filtered = computed(() => {
  const key = keyword.value.trim()
  if (!key) return conversations.value
  return conversations.value.filter((item) => item.title.includes(key))
})

const activeTitle = computed(() => {
  const item = conversations.value.find((row) => row.uid === activeId.value)
  return item?.title ?? '新对话'
})

const suggestions = [
  '用一句话解释 RAG 是什么',
  '帮我设计一个客服知识库的结构',
  '恋爱陪伴和普通聊天有什么区别？',
]

/** 今天显示时分，昨天显示「昨天」，更早显示月-日 */
function formatTime(iso: string) {
  const time = new Date(iso)
  if (Number.isNaN(time.getTime())) return ''
  const now = new Date()
  const sameDay = time.toDateString() === now.toDateString()
  if (sameDay) {
    return `${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`
  }
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (time.toDateString() === yesterday.toDateString()) return '昨天'
  return `${time.getMonth() + 1}-${time.getDate()}`
}

async function refresh() {
  loading.value = true
  try {
    conversations.value = await listConversations('chat')
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '会话列表加载失败')
  } finally {
    loading.value = false
  }
}

async function openConversation(item: ConversationSummary) {
  if (item.uid === activeId.value) return
  try {
    const detail: ConversationDetail = await getConversation(item.uid)
    activeId.value = detail.uid
    panelRef.value?.loadConversation(detail)
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : '会话加载失败')
  }
}

/** 一轮问答结束：刷新列表（新会话出现、排序与时间更新），并同步选中项 */
function onChanged(uid: string) {
  activeId.value = uid || null
  void refresh()
}

function newChat() {
  activeId.value = null
  panelRef.value?.restart()
}

onMounted(async () => {
  await refresh()
  const latest = conversations.value[0]
  if (latest) await openConversation(latest)
})
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
            :key="item.uid"
            class="conv-item"
            :class="{ active: item.uid === activeId }"
            @click="openConversation(item)"
          >
            <span class="conv-title">{{ item.title }}</span>
            <span class="conv-time">{{ formatTime(item.lastMessageAt) }}</span>
          </button>
          <p v-if="!filtered.length" class="empty-tip">
            {{ loading ? '加载中…' : keyword ? '没有匹配的会话' : '还没有会话，先问一句试试' }}
          </p>
        </div>

        <div class="conv-foot">
          <span class="dot" />
          会话已落库，刷新后可从左侧恢复
        </div>
      </aside>

      <ChatPanel
        ref="panelRef"
        :title="activeTitle"
        :suggestions="suggestions"
        class="chat-panel"
        @changed="onChanged"
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

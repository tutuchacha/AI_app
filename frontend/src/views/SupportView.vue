<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Collection, Service } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import LearningNote from '@/components/LearningNote.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import { listDocuments, listKnowledgeBases } from '@/api/knowledge'
import type { KnowledgeBase, KnowledgeDocument } from '@/api/knowledge'
import { getSupportPolicy } from '@/api/support'

const panelRef = ref<InstanceType<typeof ChatPanel>>()

const bases = ref<KnowledgeBase[]>([])
const documents = ref<KnowledgeDocument[]>([])
const kbId = ref(0)
const loading = ref(false)
const loadError = ref('')
/** 后端下发，别在前端写死阈值 */
const minScore = ref(0.45)

const activeKb = computed(() => bases.value.find((item) => item.id === kbId.value) ?? null)
const readyCount = computed(() => documents.value.filter((item) => item.status === 'READY').length)
const chunkCount = computed(() =>
  documents.value.reduce((sum, item) => sum + (item.chunkCount || 0), 0),
)

const faqs = [
  '退款一般多久到账？',
  '订单已发货还能退吗？',
  '超过 7 天怎么处理？',
]

const suggestions = [
  '退款一般多久到账？',
  '订单已发货，我不想要了怎么办？',
  '怎么修改收货地址？',
]

const subtitle = computed(() => {
  if (!activeKb.value) return '未绑定知识库 · 先到「知识库」创建并上传售后文档'
  return `${activeKb.value.name} · ${readyCount.value}/${documents.value.length} 个文档就绪 · 回答附引用`
})

function ask(text: string) {
  if (!kbId.value) {
    ElMessage.warning('先在右侧选择要检索的知识库')
    return
  }
  panelRef.value?.send(text)
}

async function loadDocuments(id: number) {
  if (!id) {
    documents.value = []
    return
  }
  try {
    documents.value = await listDocuments(id)
  } catch {
    documents.value = []
  }
}

async function loadBases() {
  loading.value = true
  loadError.value = ''
  try {
    const [list, policy] = await Promise.all([listKnowledgeBases(), getSupportPolicy()])
    bases.value = list
    minScore.value = policy.minScore
    if (!kbId.value || !list.some((item) => item.id === kbId.value)) {
      kbId.value = list[0]?.id ?? 0
    }
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : '知识库加载失败'
  } finally {
    loading.value = false
  }
}

/** 兜底卡片的「转人工」：M4-04 才建工单，这里先把问题带到控制台便于联调 */
function onHandoff(payload: { question: string; bestScore: number }) {
  console.info('[support] handoff', payload)
  ElMessage.info(`已准备转人工：最高引用分 ${Math.round(payload.bestScore * 100)}%，工单入口在 M4-04`)
}

watch(kbId, (id) => void loadDocuments(id))
onMounted(async () => {
  await loadBases()
  await loadDocuments(kbId.value)
})
</script>

<template>
  <div class="support-page">
    <LearningNote
      title="客服 = RAG + 引用 + 兜底"
      :points="['先检索绑定的知识库，再生成回答', '引用可点开看原文', '低于置信阈值就提示转人工']"
      next="M4-04：转人工工单（表 + 提交 + 处理状态）"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">智能客服</h1>
        <p class="page-subtitle">{{ subtitle }}</p>
      </div>
      <span class="pill mint"><el-icon><Service /></el-icon> 真实知识库</span>
    </div>

    <div v-if="loadError" class="error-bar card">
      <span>{{ loadError }}</span>
      <el-button size="small" round :loading="loading" @click="loadBases">重试</el-button>
    </div>

    <div class="support-layout">
      <aside class="side">
        <div class="kb card">
          <div class="kb-top">
            <div class="kb-icon"><el-icon :size="18"><Collection /></el-icon></div>
            <div>
              <strong>{{ activeKb?.name ?? '未绑定知识库' }}</strong>
              <span v-if="activeKb">
                {{ readyCount }}/{{ documents.length }} 文档就绪 · {{ chunkCount }} 切片
              </span>
              <span v-else>先创建知识库并上传文档</span>
            </div>
          </div>
          <div class="kb-stats">
            <div><b>{{ documents.length }}</b><span>文档</span></div>
            <div><b>{{ chunkCount }}</b><span>切片</span></div>
            <div><b>{{ Math.round(minScore * 100) }}%</b><span>兜底线</span></div>
          </div>
          <el-button
            v-if="bases.length > 1"
            text
            size="small"
            class="kb-switch"
            @click="kbId = 0"
          >
            清空选择（在右侧重新选库）
          </el-button>
          <router-link v-else-if="!bases.length" class="kb-link" to="/knowledge">
            去创建知识库 →
          </router-link>
        </div>

        <div class="faq card">
          <h3>常见问题</h3>
          <button v-for="item in faqs" :key="item" @click="ask(item)">
            {{ item }}
            <span>→</span>
          </button>
        </div>

        <div class="flow card">
          <h3>回答流程</h3>
          <ol>
            <li><i>1</i> 检索绑定库 TopK</li>
            <li><i>2</i> 组装资料与约束</li>
            <li><i>3</i> 流式生成 + 引用</li>
            <li><i>4</i> 低置信提示转人工</li>
          </ol>
        </div>
      </aside>

      <ChatPanel
        ref="panelRef"
        v-model:kb-id="kbId"
        class="chat"
        mode="support"
        kb-picker
        :low-score="minScore"
        title="星屿客服"
        :subtitle="subtitle"
        greeting="你好，我是星屿客服。可以问我退款、物流、售后相关的问题，我会先查知识库再回答。"
        :suggestions="suggestions"
        @handoff="onHandoff"
      />
    </div>
  </div>
</template>

<style scoped>
.support-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 60px - 54px);
  min-height: 560px;
}

.support-layout {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 290px;
  flex: 0 0 290px;
  overflow-y: auto;
}

.kb {
  padding: 14px;
}

.kb-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kb-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 11px;
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
}

.kb-top strong {
  display: block;
  font-size: 13px;
}

.kb-top span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.kb-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--xy-line);
}

.kb-stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kb-stats b {
  font-size: 13px;
}

.kb-stats span {
  color: var(--xy-muted);
  font-size: 9.5px;
}

.kb-switch {
  margin-top: 6px;
  padding: 0;
}

.kb-link {
  display: inline-block;
  margin-top: 8px;
  color: var(--xy-violet);
  font-size: 11px;
  text-decoration: none;
}

.faq,
.flow {
  padding: 13px 14px;
}

.faq h3,
.flow h3 {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
}

.faq button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 7px 0;
  border: 0;
  border-bottom: 1px dashed #f0f1f6;
  background: transparent;
  color: #63677d;
  font-size: 11.5px;
  text-align: left;
  cursor: pointer;
}

.faq button:last-child {
  border-bottom: 0;
}

.faq button:hover {
  color: var(--xy-coral);
}

.faq button span {
  color: #c3c6d4;
}

.flow ol {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: #6f7387;
  font-size: 11px;
}

.flow li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow i {
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
}

.chat {
  flex: 1;
  min-width: 0;
}

.error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-color: #ffe0dc;
  background: #fff8f7;
  color: var(--xy-coral);
  font-size: 11.5px;
}

@media (max-width: 980px) {
  .support-page {
    height: auto;
  }
  .support-layout {
    flex-direction: column;
  }
  .side {
    width: 100%;
    flex-basis: auto;
    flex-direction: row;
    overflow-x: auto;
  }
  .side > * {
    min-width: 240px;
  }
  .kb {
    min-width: 260px;
  }
  .chat {
    height: 620px;
  }
}
</style>

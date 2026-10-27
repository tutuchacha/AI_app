<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  CircleClose,
  Clock,
  Coin,
  Cpu,
  Delete,
  Document,
  Download,
  List,
  MagicStick,
  Refresh,
  Tickets,
} from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import {
  createAnalysis,
  deleteAnalysis,
  getAnalysis,
  listAnalyses,
  type AnalysisStatus,
  type AnalysisTask,
  type AnalysisType,
} from '@/api/analysis'
import {
  listDocuments,
  listKnowledgeBases,
  type DocumentStatus,
  type KnowledgeBase,
  type KnowledgeDocument,
} from '@/api/knowledge'

const STATUS_LABELS: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '生成中',
  READY: '已完成',
  FAILED: '失败',
  PARSING: '解析中',
  INDEXING: '索引中',
}

const TYPE_LABELS: Record<AnalysisType, string> = {
  SUMMARY: '摘要',
  OUTLINE: '结构',
  QUESTIONS: '追问',
  FIELDS: '要素',
}

const typeCards = [
  { type: 'SUMMARY' as const, label: '摘要', desc: '3~6 句话概括文档目的与结论', icon: Document },
  { type: 'OUTLINE' as const, label: '结构', desc: '按章节抽取层级与关键要点', icon: List },
  { type: 'QUESTIONS' as const, label: '追问', desc: '生成评审与深挖问题清单', icon: ChatDotRound },
  { type: 'FIELDS' as const, label: '要素', desc: '抽取主体、金额、期限等字段', icon: Tickets },
]

const knowledgeBases = ref<KnowledgeBase[]>([])
const documents = ref<KnowledgeDocument[]>([])
const analyses = ref<AnalysisTask[]>([])
const kbId = ref<number | null>(null)
const documentId = ref<number | null>(null)
const type = ref<AnalysisType>('SUMMARY')
const activeTab = ref<AnalysisType>('SUMMARY')
const viewing = ref<AnalysisTask | null>(null)
const loadingBases = ref(false)
const loadingDocs = ref(false)
const loadingAnalyses = ref(false)
const starting = ref(false)
const loaded = ref(false)

const pollTimers = new Set<number>()

const currentDocument = computed(() =>
  documents.value.find((doc) => doc.id === documentId.value) ?? null,
)

/** 后端按 id 倒序返回，取每种类型的第一条即最新结果 */
const latestByType = computed<Record<AnalysisType, AnalysisTask | undefined>>(() => {
  const map: Record<AnalysisType, AnalysisTask | undefined> = {
    SUMMARY: undefined,
    OUTLINE: undefined,
    QUESTIONS: undefined,
    FIELDS: undefined,
  }
  for (const task of analyses.value) {
    if (!map[task.type]) map[task.type] = task
  }
  return map
})

const tabs = computed(() =>
  (['SUMMARY', 'OUTLINE', 'QUESTIONS', 'FIELDS'] as const).map((item) => ({
    type: item,
    label: TYPE_LABELS[item],
    task: latestByType.value[item],
  })),
)

const canStart = computed(
  () => currentDocument.value?.status === 'READY' && !starting.value,
)

const renderedContent = computed(() => renderMarkdown(viewing.value?.content ?? ''))

onMounted(loadKnowledgeBases)
onBeforeUnmount(clearPolling)

async function loadKnowledgeBases() {
  loadingBases.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
    if (knowledgeBases.value.length) {
      kbId.value = knowledgeBases.value[0].id
      await loadDocuments()
    }
  } catch (cause) {
    ElMessage.error(errorText(cause, '知识库加载失败'))
  } finally {
    loaded.value = true
    loadingBases.value = false
  }
}

async function onKbChange() {
  documentId.value = null
  documents.value = []
  analyses.value = []
  viewing.value = null
  await loadDocuments()
}

async function loadDocuments() {
  clearPolling()
  if (!kbId.value) {
    documents.value = []
    documentId.value = null
    analyses.value = []
    viewing.value = null
    return
  }
  loadingDocs.value = true
  try {
    documents.value = await listDocuments(kbId.value)
    const preferred =
      documents.value.find((doc) => doc.status === 'READY') ?? documents.value[0]
    documentId.value = preferred?.id ?? null
    analyses.value = []
    viewing.value = null
    if (documentId.value) await loadAnalyses()
  } catch (cause) {
    documents.value = []
    documentId.value = null
    ElMessage.error(errorText(cause, '文档列表加载失败'))
  } finally {
    loadingDocs.value = false
  }
}

async function onDocumentChange() {
  clearPolling()
  analyses.value = []
  viewing.value = null
  if (documentId.value) await loadAnalyses()
}

async function loadAnalyses() {
  if (!documentId.value) return
  loadingAnalyses.value = true
  try {
    const list = await listAnalyses(documentId.value)
    analyses.value = list
    const keep = viewing.value
      ? list.find((task) => task.id === viewing.value?.id)
      : undefined
    viewing.value = keep ?? list[0] ?? null
    if (viewing.value) activeTab.value = viewing.value.type
  } catch (cause) {
    ElMessage.error(errorText(cause, '分析记录加载失败'))
  } finally {
    loadingAnalyses.value = false
  }
}

function pickType(next: AnalysisType) {
  type.value = next
  switchTab(next)
}

function switchTab(next: AnalysisType) {
  activeTab.value = next
  const latest = latestByType.value[next]
  if (latest) {
    viewing.value = latest
  } else if (viewing.value?.type !== next) {
    viewing.value = null
  }
}

function viewTask(task: AnalysisTask) {
  type.value = task.type
  activeTab.value = task.type
  viewing.value = task
}

async function start() {
  if (!documentId.value || !currentDocument.value) {
    ElMessage.warning('请先选择知识库和文档')
    return
  }
  if (currentDocument.value.status !== 'READY') {
    ElMessage.warning('文档还没解析完成，READY 后才能分析')
    return
  }
  starting.value = true
  try {
    const task = await createAnalysis(documentId.value, type.value)
    upsertTask(task)
    activeTab.value = task.type
    viewing.value = task
    if (task.status === 'PENDING' || task.status === 'RUNNING') {
      poll(task.id, documentId.value)
    } else if (task.status === 'READY') {
      ElMessage.success(`${TYPE_LABELS[task.type]}已生成`)
    } else {
      ElMessage.error(task.errorMsg || '分析失败')
    }
  } catch (cause) {
    ElMessage.error(errorText(cause, '创建分析任务失败'))
  } finally {
    starting.value = false
  }
}

/** 1.2s 轮询，约 2 分钟仍未完成则提示稍后刷新 */
function poll(id: number, forDocument: number, attempt = 0) {
  const timer = window.setTimeout(async () => {
    pollTimers.delete(timer)
    if (documentId.value !== forDocument) return
    try {
      const task = await getAnalysis(id)
      if (documentId.value !== forDocument) return
      upsertTask(task)
      if (task.status === 'PENDING' || task.status === 'RUNNING') {
        if (attempt >= 100) {
          ElMessage.warning('分析耗时较长，可稍后刷新查看')
          return
        }
        poll(id, forDocument, attempt + 1)
        return
      }
      await loadAnalyses()
      if (task.status === 'READY') {
        ElMessage.success(`${TYPE_LABELS[task.type]}已生成`)
      } else {
        ElMessage.error(task.errorMsg || '分析失败')
      }
    } catch (cause) {
      ElMessage.error(errorText(cause, '查询分析进度失败'))
    }
  }, 1200)
  pollTimers.add(timer)
}

function upsertTask(task: AnalysisTask) {
  const index = analyses.value.findIndex((item) => item.id === task.id)
  if (index >= 0) {
    analyses.value.splice(index, 1, task)
  } else {
    analyses.value.unshift(task)
  }
  analyses.value.sort((a, b) => b.id - a.id)
  if (viewing.value?.id === task.id) viewing.value = task
}

async function removeTask(task: AnalysisTask) {
  try {
    await ElMessageBox.confirm(
      `删除这条「${TYPE_LABELS[task.type]}」记录？删除后需要重新分析。`,
      '删除分析记录',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteAnalysis(task.id)
    analyses.value = analyses.value.filter((item) => item.id !== task.id)
    if (viewing.value?.id === task.id) {
      viewing.value = latestByType.value[activeTab.value] ?? null
    }
    ElMessage.success('分析记录已删除')
  } catch (cause) {
    ElMessage.error(errorText(cause, '删除失败'))
  }
}

function retry() {
  if (!viewing.value) return
  if (!documentId.value || viewing.value.documentId !== documentId.value) {
    ElMessage.warning('请先在左侧选中这条记录对应的文档')
    return
  }
  type.value = viewing.value.type
  void start()
}

function exportMarkdown() {
  const task = viewing.value
  if (!task?.content) return
  const filename = currentDocument.value?.filename ?? '文档'
  const header = [
    `# ${filename} · ${TYPE_LABELS[task.type]}`,
    '',
    `> 模型：${task.model || '默认'} ｜ tokens：${task.promptTokens} + ${task.completionTokens} ｜ 耗时：${formatLatency(task.latencyMs)}`,
    '',
  ].join('\n')
  const blob = new Blob([header + task.content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${filename.replace(/\.[^.]+$/, '')}-${TYPE_LABELS[task.type]}.md`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出 Markdown')
}

function clearPolling() {
  for (const timer of pollTimers) window.clearTimeout(timer)
  pollTimers.clear()
}

function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status
}

function taskTagType(status: AnalysisStatus) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function docTagType(status: DocumentStatus) {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

function formatLatency(ms: number) {
  if (!ms || ms <= 0) return '—'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

function errorText(cause: unknown, fallback: string) {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

/**
 * 极简 Markdown：标题 / 无序 / 有序 / 引用 / 表格 / 粗体 / 行内代码。
 * 先转义再拼标签，结果通过 v-html 渲染，避免模型输出带入 HTML。
 */
function renderMarkdown(markdown: string) {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n')
  const html: string[] = []
  let list: 'ul' | 'ol' | null = null
  let table: string[][] | null = null

  const closeList = () => {
    if (!list) return
    html.push(`</${list}>`)
    list = null
  }

  const closeTable = () => {
    if (!table) return
    const [head, ...body] = table
    table = null
    html.push(
      '<table><thead><tr>' +
        head.map((cell) => `<th>${inline(cell)}</th>`).join('') +
        '</tr></thead><tbody>' +
        body
          .map((row) => '<tr>' + row.map((cell) => `<td>${inline(cell)}</td>`).join('') + '</tr>')
          .join('') +
        '</tbody></table>',
    )
  }

  const closeBlocks = () => {
    closeList()
    closeTable()
  }

  for (const raw of lines) {
    const line = raw.trim()
    if (!line) {
      closeBlocks()
      continue
    }
    if (line.startsWith('|')) {
      closeList()
      const cells = line
        .replace(/^\|/, '')
        .replace(/\|$/, '')
        .split('|')
        .map((cell) => cell.trim())
      // 跳过 |---|:---| 这类分隔行
      if (cells.every((cell) => /^:?-{3,}:?$/.test(cell))) continue
      table ??= []
      table.push(cells)
      continue
    }
    closeTable()
    let match = /^(#{1,4})\s+(.*)$/.exec(line)
    if (match) {
      closeList()
      const level = Math.min(match[1].length + 1, 4)
      html.push(`<h${level}>${inline(match[2])}</h${level}>`)
      continue
    }
    match = /^[-*]\s+(.*)$/.exec(line)
    if (match) {
      if (list !== 'ul') {
        closeList()
        html.push('<ul>')
        list = 'ul'
      }
      html.push(`<li>${inline(match[1])}</li>`)
      continue
    }
    match = /^\d+[.、)]\s*(.*)$/.exec(line)
    if (match) {
      if (list !== 'ol') {
        closeList()
        html.push('<ol>')
        list = 'ol'
      }
      html.push(`<li>${inline(match[1])}</li>`)
      continue
    }
    match = /^>\s?(.*)$/.exec(line)
    if (match) {
      closeList()
      html.push(`<blockquote>${inline(match[1])}</blockquote>`)
      continue
    }
    match = /^考察点[：:]\s*(.*)$/.exec(line)
    if (match) {
      closeList()
      html.push(`<p class="exam">考察点：${inline(match[1])}</p>`)
      continue
    }
    closeList()
    html.push(`<p>${inline(line)}</p>`)
  }
  closeBlocks()
  return html.join('')
}

function inline(text: string) {
  return escapeHtml(text)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

function escapeHtml(text: string) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<template>
  <div class="page">
    <LearningNote
      title="文档分析的四类结果"
      :points="[
        '摘要：快速判断文档讲了什么',
        '结构：还原章节层级与关键要点',
        '追问：生成评审问题与考察点',
        '要素：抽取主体、金额、期限等字段',
      ]"
      next="M3：四类结果都支持导出 Markdown"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">文档分析</h1>
        <p class="page-subtitle">
          选一份知识库里的 READY 文档，生成摘要、结构、追问或要素；任务后台执行，可随时回来查看。
        </p>
      </div>
      <span class="pill coral">
        <el-icon><MagicStick /></el-icon>
        M3 · 真实模型
      </span>
    </div>

    <div class="analysis-grid">
      <div class="left">
        <section class="card panel">
          <header class="card-head">
            <div>
              <strong>01 · 选择文档</strong>
              <span>只分析状态为 READY 的文档</span>
            </div>
            <el-button v-if="kbId" text size="small" :loading="loadingDocs" @click="loadDocuments">
              刷新
            </el-button>
          </header>

          <el-select
            v-model="kbId"
            class="full"
            placeholder="选择知识库"
            :loading="loadingBases"
            @change="onKbChange"
          >
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id">
              <div class="option-row">
                <span class="option-name">{{ kb.name }}</span>
                <span class="option-meta">{{ kb.documentCount }} 篇</span>
              </div>
            </el-option>
          </el-select>

          <el-select
            v-model="documentId"
            class="full"
            placeholder="选择文档"
            :disabled="!kbId || loadingDocs"
            @change="onDocumentChange"
          >
            <el-option
              v-for="doc in documents"
              :key="doc.id"
              :label="doc.filename"
              :value="doc.id"
            >
              <div class="option-row">
                <span class="option-name">{{ doc.filename }}</span>
                <span class="option-status" :class="doc.status.toLowerCase()">
                  {{ statusLabel(doc.status) }}
                </span>
              </div>
            </el-option>
          </el-select>

          <div v-if="loaded && !knowledgeBases.length" class="inline-empty">
            还没有知识库，先去
            <RouterLink to="/knowledge">知识库</RouterLink>
            建一个并上传文档。
          </div>
          <div v-else-if="kbId && !documents.length && !loadingDocs" class="inline-empty">
            这个知识库还没有文档，先去
            <RouterLink to="/knowledge">上传</RouterLink>
            一份。
          </div>

          <div v-if="currentDocument" class="doc-meta">
            <span>
              {{ currentDocument.ext.toUpperCase() }} · {{ formatSize(currentDocument.size) }} ·
              {{ currentDocument.chunkCount }} 个切片
            </span>
            <el-tag size="small" :type="docTagType(currentDocument.status)" effect="light">
              {{ statusLabel(currentDocument.status) }}
            </el-tag>
          </div>
          <p v-if="currentDocument && currentDocument.status !== 'READY'" class="hint warn">
            {{
              currentDocument.status === 'FAILED'
                ? currentDocument.errorMsg || '解析失败，请回知识库重建索引'
                : '文档还在解析，READY 后才能分析'
            }}
          </p>
        </section>

        <section class="card panel">
          <header class="card-head">
            <div>
              <strong>02 · 选择类型</strong>
              <span>点击卡片切换</span>
            </div>
          </header>

          <div class="type-grid">
            <button
              v-for="item in typeCards"
              :key="item.type"
              type="button"
              class="type-card"
              :class="{ active: type === item.type }"
              @click="pickType(item.type)"
            >
              <el-icon :size="18"><component :is="item.icon" /></el-icon>
              <strong>{{ item.label }}</strong>
              <span>{{ item.desc }}</span>
            </button>
          </div>

          <div class="action-row">
            <div class="action-text">
              <strong>{{ TYPE_LABELS[type] }} · {{ currentDocument?.filename || '未选择文档' }}</strong>
              <span>{{ canStart ? '预计 5~20 秒，后台执行，可继续浏览' : '选择 READY 文档后开始' }}</span>
            </div>
            <el-button type="primary" round :loading="starting" :disabled="!canStart" @click="start">
              开始分析
            </el-button>
          </div>
        </section>

        <section class="card panel">
          <header class="card-head">
            <div>
              <strong>最近任务</strong>
              <span>最多 20 条，点击查看</span>
            </div>
            <el-button
              v-if="documentId"
              text
              size="small"
              :loading="loadingAnalyses"
              @click="loadAnalyses"
            >
              刷新
            </el-button>
          </header>

          <div v-if="!analyses.length" class="inline-empty">还没有分析记录</div>
          <button
            v-for="task in analyses"
            :key="task.id"
            type="button"
            class="history-row"
            :class="{ active: viewing?.id === task.id }"
            @click="viewTask(task)"
          >
            <span class="history-type">{{ TYPE_LABELS[task.type] }}</span>
            <span class="history-time">{{ formatTime(task.updatedAt || task.createdAt) }}</span>
            <el-tag size="small" :type="taskTagType(task.status)" effect="plain">
              {{ statusLabel(task.status) }}
            </el-tag>
            <el-icon class="history-del" title="删除记录" @click.stop="removeTask(task)">
              <Delete />
            </el-icon>
          </button>
        </section>
      </div>

      <div class="right">
        <div class="card tabs">
          <button
            v-for="tab in tabs"
            :key="tab.type"
            type="button"
            class="tab"
            :class="{ active: activeTab === tab.type }"
            @click="switchTab(tab.type)"
          >
            <span>{{ tab.label }}</span>
            <i :class="tab.task ? tab.task.status.toLowerCase() : 'idle'" />
          </button>
        </div>

        <div v-if="!viewing" class="result-empty card">
          <div class="empty-icon"><el-icon :size="26"><Document /></el-icon></div>
          <h3>{{ currentDocument ? '还没有这类结果' : '先选一份文档' }}</h3>
          <p v-if="!currentDocument">从左侧选择知识库和文档，再挑一个分析类型。</p>
          <p v-else-if="currentDocument.status !== 'READY'">
            文档解析完成后才能分析，可以先去知识库页查看状态。
          </p>
          <p v-else>在左侧点「开始分析」，结果会显示在这里，并支持导出 Markdown。</p>
          <ul>
            <li>摘要：快速了解文档讲了什么</li>
            <li>结构：还原章节层级与要点</li>
            <li>追问：生成评审问题与考察点</li>
            <li>要素：抽取主体、金额与期限</li>
          </ul>
        </div>

        <div v-else class="result card">
          <div class="result-head">
            <div class="result-title">
              <div class="result-tags">
                <span class="pill mint">{{ TYPE_LABELS[viewing.type] }}</span>
                <el-tag size="small" :type="taskTagType(viewing.status)" effect="light">
                  {{ statusLabel(viewing.status) }}
                </el-tag>
              </div>
              <h3>{{ currentDocument?.filename || '分析结果' }}</h3>
            </div>
            <div class="result-actions">
              <el-button
                size="small"
                round
                :disabled="viewing.status !== 'READY' || !viewing.content"
                @click="exportMarkdown"
              >
                <el-icon><Download /></el-icon>
                导出
              </el-button>
              <el-button v-if="viewing.status === 'FAILED'" size="small" round @click="retry">
                <el-icon><Refresh /></el-icon>
                重试
              </el-button>
            </div>
          </div>

          <div v-if="viewing.status === 'PENDING' || viewing.status === 'RUNNING'" class="running">
            <div class="running-bar"><i /></div>
            <p>
              {{
                viewing.status === 'PENDING'
                  ? '任务已排队，准备读取文档…'
                  : '正在读取切片并调用模型…'
              }}
            </p>
          </div>

          <div v-else-if="viewing.status === 'FAILED'" class="failed">
            <el-icon :size="20"><CircleClose /></el-icon>
            <div>
              <strong>这次分析没有完成</strong>
              <p>{{ viewing.errorMsg || '模型调用失败，请稍后重试' }}</p>
            </div>
          </div>

          <template v-else>
            <div class="meta">
              <span><el-icon><Cpu /></el-icon>{{ viewing.model || '默认模型' }}</span>
              <span>
                <el-icon><Coin /></el-icon>
                {{ viewing.promptTokens }} + {{ viewing.completionTokens }} tokens
              </span>
              <span><el-icon><Clock /></el-icon>{{ formatLatency(viewing.latencyMs) }}</span>
              <span class="meta-time">{{ formatTime(viewing.createdAt) }}</span>
            </div>
            <div class="markdown" v-html="renderedContent" />
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.92fr) minmax(0, 1.08fr);
  gap: 16px;
  align-items: start;
}

.left {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 11px;
  padding: 15px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.card-head > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-head strong {
  font-size: 12.5px;
}

.card-head span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.full {
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.option-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-meta {
  color: var(--xy-muted);
  font-size: 11px;
}

.option-status {
  font-size: 10.5px;
  color: var(--xy-muted);
}

.option-status.ready {
  color: var(--xy-mint);
}

.option-status.failed {
  color: var(--xy-coral);
}

.inline-empty {
  color: var(--xy-muted);
  font-size: 11px;
  line-height: 1.7;
}

.inline-empty a {
  color: var(--xy-violet);
}

.doc-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 11px;
  border-radius: 10px;
  background: #fafbfe;
  color: var(--xy-muted);
  font-size: 11px;
}

.hint {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.6;
}

.hint.warn {
  color: #d97706;
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(116px, 1fr));
  gap: 8px;
}

.type-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-height: 96px;
  padding: 10px;
  border: 1px solid var(--xy-line);
  border-radius: 12px;
  background: #fff;
  color: var(--xy-muted);
  text-align: left;
  cursor: pointer;
  transition: 0.16s ease;
}

.type-card .el-icon {
  color: var(--xy-coral);
}

.type-card strong {
  color: var(--xy-ink);
  font-size: 12px;
}

.type-card span {
  font-size: 10px;
  line-height: 1.5;
}

.type-card:hover {
  border-color: #ffc9c2;
  background: #fffbfa;
}

.type-card.active {
  border-color: #ffb3ac;
  background: var(--xy-coral-soft);
  box-shadow: 0 4px 14px rgba(255, 107, 94, 0.12);
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #f2f3f8;
}

.action-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.action-text strong {
  overflow: hidden;
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-text span {
  color: var(--xy-muted);
  font-size: 10px;
}

.history-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 5px;
  border: 0;
  border-top: 1px solid #f2f3f8;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.history-row:first-of-type {
  border-top: 0;
}

.history-row:hover {
  background: #fcfcfe;
}

.history-row.active {
  background: #fff7f5;
}

.history-type {
  width: 38px;
  flex: 0 0 38px;
  font-size: 11.5px;
  font-weight: 650;
}

.history-time {
  flex: 1;
  color: var(--xy-muted);
  font-size: 10px;
}

.history-del {
  color: #c3c6d4;
  transition: color 0.15s ease;
}

.history-del:hover {
  color: var(--xy-coral);
}

.tabs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  padding: 5px;
}

.tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--xy-muted);
  font-size: 12px;
  cursor: pointer;
  transition: 0.16s ease;
}

.tab:hover {
  background: #fafbfe;
}

.tab.active {
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-weight: 650;
}

.tab i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d6d9e5;
}

.tab i.ready {
  background: var(--xy-mint);
}

.tab i.running,
.tab i.pending {
  background: var(--xy-coral);
  animation: pulse 1.1s ease-in-out infinite;
}

.tab i.failed {
  background: #ef4444;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 430px;
  padding: 30px;
  text-align: center;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin-bottom: 14px;
  border-radius: 16px;
  background: #f4f5fa;
  color: #9da1b5;
}

.result-empty h3 {
  font-size: 15px;
}

.result-empty p {
  max-width: 380px;
  margin-top: 7px;
  color: var(--xy-muted);
  font-size: 11.5px;
  line-height: 1.7;
}

.result-empty ul {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
  color: #8a8ea3;
  font-size: 11px;
  line-height: 2;
}

.result {
  min-height: 430px;
  padding: 18px;
}

.result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 13px;
  border-bottom: 1px solid #f0f1f6;
}

.result-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-title h3 {
  margin-top: 7px;
  font-size: 16px;
  word-break: break-all;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.running {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 80px 12px;
}

.running-bar {
  width: min(320px, 70%);
  height: 6px;
  border-radius: 99px;
  background: #f1f2f8;
  overflow: hidden;
}

.running-bar i {
  display: block;
  width: 38%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ff8a5c, #ff5e8a);
  animation: slide 1.15s ease-in-out infinite;
}

.running p {
  color: var(--xy-muted);
  font-size: 11.5px;
}

.failed {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 18px;
  padding: 14px;
  border: 1px solid #ffe0dc;
  border-radius: 12px;
  background: #fff8f7;
  color: var(--xy-coral);
}

.failed strong {
  color: #b64135;
  font-size: 12.5px;
}

.failed p {
  margin-top: 4px;
  color: #8a6a66;
  font-size: 11px;
  line-height: 1.7;
  word-break: break-word;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 10px 0 2px;
  border-bottom: 1px dashed #f0f1f6;
  color: var(--xy-muted);
  font-size: 10.5px;
}

.meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.meta-time {
  margin-left: auto;
}

.markdown {
  margin-top: 14px;
  color: #4f536a;
  font-size: 12.5px;
  line-height: 1.85;
  word-break: break-word;
}

.markdown :deep(h2) {
  margin: 18px 0 8px;
  color: var(--xy-ink);
  font-size: 16px;
}

.markdown :deep(h3) {
  margin: 16px 0 6px;
  padding-left: 8px;
  border-left: 3px solid var(--xy-coral);
  color: var(--xy-ink);
  font-size: 13.5px;
}

.markdown :deep(h4) {
  margin: 14px 0 5px;
  color: var(--xy-violet);
  font-size: 12.5px;
}

.markdown :deep(p) {
  margin: 6px 0;
}

.markdown :deep(ul),
.markdown :deep(ol) {
  margin: 6px 0;
  padding-left: 18px;
}

.markdown :deep(li) {
  margin: 3px 0;
}

.markdown :deep(li::marker) {
  color: var(--xy-coral);
}

.markdown :deep(strong) {
  color: var(--xy-ink);
}

.markdown :deep(code) {
  padding: 1px 4px;
  border-radius: 4px;
  background: #f4f5fa;
  font-size: 11px;
}

.markdown :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 10px;
  border-left: 2px solid #e4e6f0;
  border-radius: 0 8px 8px 0;
  background: #fafbfe;
  color: var(--xy-muted);
}

.markdown :deep(p.exam) {
  margin: 2px 0 10px;
  color: var(--xy-violet);
  font-size: 11.5px;
}

.markdown :deep(table) {
  width: 100%;
  margin: 10px 0;
  border-collapse: collapse;
  font-size: 11.5px;
}

.markdown :deep(th),
.markdown :deep(td) {
  padding: 6px 8px;
  border: 1px solid #eceef5;
  text-align: left;
  vertical-align: top;
  line-height: 1.6;
}

.markdown :deep(th) {
  background: #fafbfe;
  color: var(--xy-ink);
  font-weight: 650;
  white-space: nowrap;
}

.markdown :deep(td:last-child) {
  color: var(--xy-muted);
}

@keyframes slide {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(300%);
  }
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.35;
  }
}

@media (max-width: 980px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .type-grid {
    grid-template-columns: 1fr;
  }

  .type-card {
    min-height: 0;
  }
}
</style>

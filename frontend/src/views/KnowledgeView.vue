<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  Delete,
  Document,
  EditPen,
  Plus,
  Refresh,
  Search,
  UploadFilled,
} from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import {
  createKnowledgeBase,
  deleteDocument,
  deleteKnowledgeBase,
  getDocument,
  listDocuments,
  listKnowledgeBases,
  reindexDocument,
  searchKnowledgeBase,
  updateKnowledgeBase,
  uploadDocument,
} from '@/api/knowledge'
import type {
  DocumentStatus,
  KnowledgeBase,
  KnowledgeDocument,
  SearchHit,
} from '@/api/knowledge'

const bases = ref<KnowledgeBase[]>([])
const documents = ref<KnowledgeDocument[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const docLoading = ref(false)
const uploading = ref(false)
const searching = ref(false)
const hits = ref<SearchHit[]>([])
const query = ref('')

const selected = computed(() => bases.value.find((item) => item.id === selectedId.value) ?? null)
const docTotal = computed(() => bases.value.reduce((sum, item) => sum + item.documentCount, 0))
const chunkTotal = computed(() => documents.value.reduce((sum, item) => sum + item.chunkCount, 0))
const busyCount = computed(
  () => documents.value.filter((item) => !['READY', 'FAILED'].includes(item.status)).length,
)

const statusText: Record<DocumentStatus, string> = {
  PENDING: '排队中',
  PARSING: '解析中',
  INDEXING: '向量化',
  READY: '已就绪',
  FAILED: '失败',
}

const form = reactive({
  visible: false,
  id: null as number | null,
  name: '',
  description: '',
  chunkSize: 800,
  chunkOverlap: 120,
  saving: false,
})

const fileInput = ref<HTMLInputElement>()
let disposed = false
let pollTimer: number | null = null

function formatTime(iso: string) {
  const time = new Date(iso)
  if (Number.isNaN(time.getTime())) return '—'
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  if (time.toDateString() === now.toDateString()) {
    return `${pad(time.getHours())}:${pad(time.getMinutes())}`
  }
  return `${time.getMonth() + 1}-${time.getDate()} ${pad(time.getHours())}:${pad(time.getMinutes())}`
}

function formatSize(bytes: number) {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function errorText(cause: unknown, fallback: string) {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

async function refreshBases() {
  loading.value = true
  try {
    bases.value = await listKnowledgeBases()
    if (!bases.value.length) {
      selectedId.value = null
      documents.value = []
      return
    }
    const exists = bases.value.some((item) => item.id === selectedId.value)
    if (!exists) selectedId.value = bases.value[0].id
    await loadDocuments(selectedId.value)
  } catch (cause) {
    ElMessage.error(errorText(cause, '知识库加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadDocuments(kbId: number | null) {
  if (!kbId) {
    documents.value = []
    return
  }
  docLoading.value = true
  try {
    documents.value = await listDocuments(kbId)
  } catch (cause) {
    documents.value = []
    ElMessage.error(errorText(cause, '文档列表加载失败'))
  } finally {
    docLoading.value = false
  }
}

async function selectBase(id: number) {
  if (selectedId.value === id) return
  selectedId.value = id
  hits.value = []
  query.value = ''
  await loadDocuments(id)
}

function openCreate() {
  form.id = null
  form.name = ''
  form.description = ''
  form.chunkSize = 800
  form.chunkOverlap = 120
  form.visible = true
}

function openEdit(item: KnowledgeBase) {
  form.id = item.id
  form.name = item.name
  form.description = item.description
  form.chunkSize = item.chunkSize
  form.chunkOverlap = item.chunkOverlap
  form.visible = true
}

async function saveBase() {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请填写知识库名称')
    return
  }
  if (form.chunkOverlap >= form.chunkSize) {
    ElMessage.warning('重叠字数需要小于切片长度')
    return
  }
  form.saving = true
  try {
    const payload = {
      name,
      description: form.description.trim(),
      chunkSize: form.chunkSize,
      chunkOverlap: form.chunkOverlap,
    }
    const saved = form.id
      ? await updateKnowledgeBase(form.id, payload)
      : await createKnowledgeBase(payload)
    form.visible = false
    selectedId.value = saved.id
    hits.value = []
    await refreshBases()
    ElMessage.success(form.id ? '知识库已更新' : '知识库已创建')
  } catch (cause) {
    ElMessage.error(errorText(cause, '保存失败'))
  } finally {
    form.saving = false
  }
}

async function removeBase(item: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(
      `删除「${item.name}」会同时删除 ${item.documentCount} 个文档与全部切片，确定继续？`,
      '删除知识库',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteKnowledgeBase(item.id)
    if (selectedId.value === item.id) {
      selectedId.value = null
      hits.value = []
    }
    await refreshBases()
    ElMessage.success('知识库已删除')
  } catch (cause) {
    ElMessage.error(errorText(cause, '删除失败'))
  }
}

function pickFile() {
  if (!selected.value) {
    ElMessage.warning('请先选择或新建一个知识库')
    return
  }
  fileInput.value?.click()
}

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const kb = selected.value
  input.value = ''
  if (!file || !kb) return

  uploading.value = true
  try {
    const created = await uploadDocument(kb.id, file)
    if (selectedId.value === kb.id) {
      documents.value = [created, ...documents.value]
    }
    ElMessage.success(`${file.name} 已上传，正在后台解析`)
    await refreshBases()
    startPolling(created.id)
  } catch (cause) {
    ElMessage.error(errorText(cause, '上传失败'))
  } finally {
    uploading.value = false
  }
}

/** 上传后轮询详情：PENDING → PARSING → INDEXING → READY / FAILED */
function startPolling(id: number) {
  if (pollTimer) window.clearInterval(pollTimer)
  let attempts = 0
  pollTimer = window.setInterval(async () => {
    attempts += 1
    if (disposed || attempts > 80) {
      stopPolling()
      return
    }
    try {
      const doc = await getDocument(id)
      const index = documents.value.findIndex((item) => item.id === id)
      if (index >= 0 && doc.kbId === selectedId.value) documents.value[index] = doc
      if (doc.status === 'READY' || doc.status === 'FAILED') {
        stopPolling()
        if (doc.status === 'FAILED') {
          ElMessage.error(`${doc.filename} 解析失败：${doc.errorMsg || '未知原因'}`)
        } else {
          ElMessage.success(`${doc.filename} 已就绪，共 ${doc.chunkCount} 个切片`)
        }
        await refreshBases()
      }
    } catch {
      stopPolling()
    }
  }, 1500)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function reindex(item: KnowledgeDocument) {
  try {
    const updated = await reindexDocument(item.id)
    const index = documents.value.findIndex((row) => row.id === item.id)
    if (index >= 0) documents.value[index] = updated
    ElMessage.info(`${item.filename} 已重新排队解析`)
    startPolling(item.id)
  } catch (cause) {
    ElMessage.error(errorText(cause, '重建索引失败'))
  }
}

async function removeDocument(item: KnowledgeDocument) {
  try {
    await ElMessageBox.confirm(`删除「${item.filename}」及其 ${item.chunkCount} 个切片？`, '删除文档', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteDocument(item.id)
    documents.value = documents.value.filter((row) => row.id !== item.id)
    await refreshBases()
    ElMessage.success('文档已删除')
  } catch (cause) {
    ElMessage.error(errorText(cause, '删除失败'))
  }
}

async function runSearch() {
  const kb = selected.value
  const text = query.value.trim()
  if (!kb) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!text) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  try {
    hits.value = await searchKnowledgeBase(kb.id, text, 5)
    if (!hits.value.length) ElMessage.info('没有命中片段，可上传文档或换个问法')
  } catch (cause) {
    hits.value = []
    ElMessage.error(errorText(cause, '检索失败'))
  } finally {
    searching.value = false
  }
}

onMounted(refreshBases)

onBeforeUnmount(() => {
  disposed = true
  stopPolling()
})
</script>

<template>
  <div class="page">
    <LearningNote
      title="RAG 入库链路"
      :points="['上传 → Tika 解析 → 清洗切分', 'Embedding → 写入向量列', '状态可追踪、可重建、可检索']"
      next="M2 已全链路打通：建库、传文档、看切片、检索测试；聊天页选库后回答带引用"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">知识库</h1>
        <p class="page-subtitle">建库、上传文档、跟踪解析状态，并用真实问句验证检索效果。</p>
      </div>
      <div class="head-actions">
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.docx,.doc,.md,.txt"
          hidden
          @change="onFile"
        />
        <el-button round :disabled="!selected || uploading" :loading="uploading" @click="pickFile">
          <el-icon><UploadFilled /></el-icon>
          上传文档
        </el-button>
        <el-button type="primary" round @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建知识库
        </el-button>
      </div>
    </div>

    <div class="grid-3">
      <div class="stat card">
        <div class="stat-icon violet"><el-icon :size="18"><Collection /></el-icon></div>
        <div><b>{{ bases.length }}</b><span>知识库</span></div>
      </div>
      <div class="stat card">
        <div class="stat-icon coral"><el-icon :size="18"><Document /></el-icon></div>
        <div><b>{{ docTotal }}</b><span>文档</span></div>
      </div>
      <div class="stat card">
        <div class="stat-icon mint"><el-icon :size="18"><Search /></el-icon></div>
        <div>
          <b>{{ chunkTotal }}</b>
          <span>切片{{ selected ? ` · ${selected.name}` : '' }}</span>
        </div>
      </div>
    </div>

    <div v-if="loading && !bases.length" class="hint card">知识库加载中…</div>

    <div v-else-if="!bases.length" class="hint card">
      <p>还没有知识库。新建一个，再上传 PDF / Word / Markdown / TXT。</p>
      <el-button type="primary" round @click="openCreate">新建知识库</el-button>
    </div>

    <div v-else class="kb-grid">
      <button
        v-for="item in bases"
        :key="item.id"
        class="kb-card card"
        :class="{ active: item.id === selectedId }"
        @click="selectBase(item.id)"
      >
        <div class="kb-head">
          <div class="kb-mark" :class="item.id === selectedId ? 'violet' : 'plain'">
            {{ item.name.slice(0, 1) }}
          </div>
          <div class="kb-name">
            <strong>{{ item.name }}</strong>
            <span>{{ item.documentCount }} 个文档 · {{ item.embeddingModel }}</span>
          </div>
          <span v-if="item.id === selectedId" class="pill mint">使用中</span>
        </div>
        <p class="desc">{{ item.description || '暂无描述' }}</p>
        <div class="meta">
          <span>chunk {{ item.chunkSize }} / overlap {{ item.chunkOverlap }}</span>
          <span>更新 {{ formatTime(item.updatedAt) }}</span>
        </div>
        <div class="kb-ops" @click.stop>
          <button title="编辑" @click="openEdit(item)"><el-icon><EditPen /></el-icon>编辑</button>
          <button class="danger" title="删除" @click="removeBase(item)">
            <el-icon><Delete /></el-icon>删除
          </button>
        </div>
      </button>
    </div>

    <div class="table-card card">
      <div class="table-head">
        <div>
          <h3>{{ selected ? `${selected.name} · 文档` : '文档' }}</h3>
          <span class="muted">
            PENDING → PARSING → INDEXING → READY / FAILED
            <template v-if="busyCount"> · {{ busyCount }} 个处理中</template>
          </span>
        </div>
        <el-button text size="small" :disabled="!selected" @click="loadDocuments(selectedId)">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <div v-if="docLoading" class="empty-row">加载文档中…</div>
      <div v-else-if="!documents.length" class="empty-row">
        还没有文档，点右上角「上传文档」拉一份进来。
      </div>
      <table v-else>
        <thead>
          <tr>
            <th>文件名</th>
            <th>大小</th>
            <th>切片</th>
            <th>状态</th>
            <th>更新时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="doc in documents" :key="doc.id">
            <td class="name" :title="doc.filename">{{ doc.filename }}</td>
            <td class="muted">{{ formatSize(doc.size) }}</td>
            <td>{{ doc.chunkCount }}</td>
            <td>
              <span class="status" :class="doc.status.toLowerCase()">
                {{ statusText[doc.status] }}
              </span>
              <span v-if="doc.status === 'FAILED' && doc.errorMsg" class="err" :title="doc.errorMsg">
                {{ doc.errorMsg }}
              </span>
            </td>
            <td class="muted">{{ formatTime(doc.updatedAt) }}</td>
            <td class="ops">
              <button @click="reindex(doc)">重建</button>
              <button class="danger" @click="removeDocument(doc)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="table-card card">
      <div class="table-head">
        <div>
          <h3>检索测试</h3>
          <span class="muted">走 `/search`，Java 侧余弦相似度；分数低于阈值的片段会被过滤</span>
        </div>
      </div>
      <div class="search-bar">
        <el-input
          v-model="query"
          :disabled="!selected || searching"
          placeholder="例如：年假有几天？"
          @keyup.enter="runSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" :loading="searching" :disabled="!selected" @click="runSearch">
          检索
        </el-button>
      </div>
      <div v-if="hits.length" class="hits">
        <div v-for="hit in hits" :key="hit.chunkId" class="hit">
          <div class="hit-head">
            <strong>{{ hit.filename }}</strong>
            <span class="muted">#{{ hit.chunkIndex }}</span>
            <span class="score">{{ Math.round(hit.score * 100) }}%</span>
          </div>
          <p>{{ hit.content }}</p>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="form.visible"
      :title="form.id ? '编辑知识库' : '新建知识库'"
      width="420px"
    >
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="例如：员工手册" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            placeholder="这个库放什么资料、给哪个场景用"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="切片长度">
            <el-input-number v-model="form.chunkSize" :min="100" :max="2000" :step="100" />
          </el-form-item>
          <el-form-item label="重叠字数">
            <el-input-number v-model="form.chunkOverlap" :min="0" :max="500" :step="20" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="form.visible = false">取消</el-button>
        <el-button type="primary" :loading="form.saving" @click="saveBase">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 16px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
}

.stat-icon.violet {
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
}

.stat-icon.coral {
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.stat-icon.mint {
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
}

.stat b {
  display: block;
  font-size: 21px;
  letter-spacing: -0.02em;
}

.stat span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 34px 16px;
  color: var(--xy-muted);
  font-size: 12px;
  text-align: center;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.kb-card {
  display: block;
  padding: 15px 16px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.kb-card:hover {
  border-color: #d9d4ff;
}

.kb-card.active {
  border-color: #cfc7ff;
  box-shadow: 0 8px 22px rgba(124, 92, 255, 0.1);
}

.kb-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kb-name {
  flex: 1;
  min-width: 0;
}

.kb-name strong {
  display: block;
  overflow: hidden;
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-name span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.kb-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 11px;
  font-size: 15px;
  font-weight: 700;
}

.kb-mark.violet {
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
}

.kb-mark.plain {
  background: #f2f3f9;
  color: #8d92a8;
}

.desc {
  display: -webkit-box;
  margin-top: 10px;
  overflow: hidden;
  color: #6f7387;
  font-size: 11.5px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px dashed #f0f1f6;
  color: #a0a4b6;
  font-size: 10px;
}

.kb-ops {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 8px;
}

.kb-ops button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #9a9eb2;
  font-size: 10.5px;
  cursor: pointer;
}

.kb-ops button:hover {
  background: #f4f5fa;
  color: #5f6377;
}

.kb-ops button.danger:hover {
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.table-card {
  overflow: hidden;
}

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px;
  border-bottom: 1px solid #f0f1f6;
}

.table-head h3 {
  font-size: 13px;
  font-weight: 700;
}

.table-head span {
  font-size: 10px;
}

.empty-row {
  padding: 26px 16px;
  color: #aeb1c2;
  font-size: 11.5px;
  text-align: center;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 10px 16px;
  border-bottom: 1px solid #f5f5fa;
  font-size: 11.5px;
  text-align: left;
}

th {
  background: #fbfbfe;
  color: #9094a8;
  font-size: 10px;
  font-weight: 600;
}

tbody tr:hover {
  background: #fcfcff;
}

td.name {
  max-width: 280px;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 9.5px;
  font-weight: 700;
}

.status.ready {
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
}

.status.pending,
.status.parsing,
.status.indexing {
  background: #fff7e6;
  color: #c98412;
}

.status.failed {
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.err {
  display: inline-block;
  max-width: 190px;
  margin-left: 6px;
  overflow: hidden;
  color: var(--xy-coral);
  font-size: 10px;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.ops {
  text-align: right;
  white-space: nowrap;
}

.ops button {
  padding: 2px 6px;
  border: 0;
  background: transparent;
  color: #a0a4b6;
  font-size: 10.5px;
  cursor: pointer;
}

.ops button:hover {
  color: var(--xy-violet);
}

.ops button.danger:hover {
  color: var(--xy-coral);
}

.search-bar {
  display: flex;
  gap: 8px;
  padding: 13px 16px;
}

.hits {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 16px 15px;
}

.hit {
  padding: 10px 12px;
  border: 1px solid #e6e2ff;
  border-radius: 10px;
  background: #faf9ff;
}

.hit-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hit-head strong {
  font-size: 11.5px;
  font-weight: 650;
}

.hit-head .muted {
  font-size: 10px;
}

.hit-head .score {
  margin-left: auto;
  color: var(--xy-mint);
  font-size: 10.5px;
  font-weight: 700;
}

.hit p {
  display: -webkit-box;
  margin-top: 5px;
  overflow: hidden;
  color: #70748a;
  font-size: 11.5px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.form-row {
  display: flex;
  gap: 14px;
}

.form-row :deep(.el-form-item) {
  flex: 1;
}

@media (max-width: 900px) {
  .kb-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .table-card {
    overflow-x: auto;
  }
  table {
    min-width: 640px;
  }
  .search-bar {
    flex-direction: column;
  }
}
</style>

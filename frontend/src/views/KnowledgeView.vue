<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Collection, Document, UploadFilled } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'

interface DocRow {
  name: string
  kb: string
  chunks: number
  status: 'READY' | 'INDEXING' | 'FAILED'
  updated: string
}

const docs = ref<DocRow[]>([
  { name: '售后手册 v1.pdf', kb: '售后手册 v1', chunks: 36, status: 'READY', updated: '10-21 10:12' },
  { name: '退款政策说明.md', kb: '售后手册 v1', chunks: 18, status: 'READY', updated: '10-21 09:40' },
  { name: '物流常见问题.docx', kb: '售后手册 v1', chunks: 24, status: 'INDEXING', updated: '10-21 09:32' },
  { name: '亲密关系笔记.md', kb: '恋爱心理学', chunks: 52, status: 'READY', updated: '10-20 21:18' },
  { name: '沟通案例集.pdf', kb: '恋爱心理学', chunks: 0, status: 'FAILED', updated: '10-20 20:55' },
])

const fileInput = ref<HTMLInputElement>()

const statusText = {
  READY: '已就绪',
  INDEXING: '解析中',
  FAILED: '失败',
}

function pickFile() {
  fileInput.value?.click()
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  ElMessage.success(`已选择 ${file.name}。M2 接入后会上传、解析并建立索引`)
  input.value = ''
}
</script>

<template>
  <div class="page">
    <LearningNote
      title="RAG 入库链路"
      :points="['上传 → 解析 → 清洗 → 切分', 'Embedding → 写入向量库', '文档状态可追踪、可重建']"
      next="M2：先做本地文件入库，再做异步解析与进度"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">知识库</h1>
        <p class="page-subtitle">文档入库、切片预览与检索测试。当前展示 Mock 数据。</p>
      </div>
      <div class="head-actions">
        <input ref="fileInput" type="file" accept=".pdf,.docx,.md,.txt" hidden @change="onFile" />
        <el-button type="primary" round @click="pickFile">
          <el-icon><UploadFilled /></el-icon>
          上传文档
        </el-button>
      </div>
    </div>

    <div class="grid-3">
      <div class="stat card">
        <div class="stat-icon violet"><el-icon :size="18"><Collection /></el-icon></div>
        <div><b>2</b><span>知识库</span></div>
      </div>
      <div class="stat card">
        <div class="stat-icon coral"><el-icon :size="18"><Document /></el-icon></div>
        <div><b>5</b><span>文档</span></div>
      </div>
      <div class="stat card">
        <div class="stat-icon mint"><el-icon :size="18"><UploadFilled /></el-icon></div>
        <div><b>130</b><span>切片</span></div>
      </div>
    </div>

    <div class="grid-2">
      <div class="kb-card card">
        <div class="kb-head">
          <div class="kb-mark violet">售</div>
          <div>
            <strong>售后手册 v1</strong>
            <span>客服场景 · 3 个文档</span>
          </div>
          <span class="pill mint">已绑定</span>
        </div>
        <p class="desc">退款、物流、换货等售后政策，用于智能客服问答。</p>
        <div class="meta">
          <span>Embedding · bge-m3</span>
          <span>chunk 800 / overlap 120</span>
        </div>
      </div>

      <div class="kb-card card">
        <div class="kb-head">
          <div class="kb-mark coral">恋</div>
          <div>
            <strong>恋爱心理学</strong>
            <span>陪伴场景 · 2 个文档</span>
          </div>
          <span class="pill">未绑定</span>
        </div>
        <p class="desc">沟通模式、依恋类型与冲突修复，用于恋爱陪伴角色的知识补充。</p>
        <div class="meta">
          <span>Embedding · bge-m3</span>
          <span>chunk 600 / overlap 100</span>
        </div>
      </div>
    </div>

    <div class="table-card card">
      <div class="table-head">
        <h3>文档列表</h3>
        <span class="muted">状态机：PENDING → PARSING → INDEXING → READY / FAILED</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>文件名</th>
            <th>知识库</th>
            <th>切片</th>
            <th>状态</th>
            <th>更新时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="doc in docs" :key="doc.name">
            <td class="name">{{ doc.name }}</td>
            <td class="muted">{{ doc.kb }}</td>
            <td>{{ doc.chunks }}</td>
            <td>
              <span class="status" :class="doc.status.toLowerCase()">{{ statusText[doc.status] }}</span>
            </td>
            <td class="muted">{{ doc.updated }}</td>
            <td class="ops">
              <button title="M2 开放" @click="ElMessage.info('M2 接入后开放重建索引')">重建</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
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

.kb-card {
  padding: 15px 16px;
}

.kb-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kb-head > div:nth-child(2) {
  flex: 1;
  min-width: 0;
}

.kb-head strong {
  display: block;
  font-size: 13.5px;
}

.kb-head span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.kb-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  font-size: 15px;
  font-weight: 700;
}

.kb-mark.violet {
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
}

.kb-mark.coral {
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.desc {
  margin-top: 10px;
  color: #6f7387;
  font-size: 11.5px;
  line-height: 1.65;
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
  font-weight: 600;
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

.status.indexing {
  background: #fff7e6;
  color: #c98412;
}

.status.failed {
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.ops {
  text-align: right;
}

.ops button {
  border: 0;
  background: transparent;
  color: #a0a4b6;
  font-size: 10.5px;
  cursor: pointer;
}

.ops button:hover {
  color: var(--xy-coral);
}

@media (max-width: 700px) {
  .table-card {
    overflow-x: auto;
  }
  table {
    min-width: 660px;
  }
}
</style>

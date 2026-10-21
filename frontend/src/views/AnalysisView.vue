<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, MagicStick, UploadFilled } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'

const fileInput = ref<HTMLInputElement>()
const selected = ref<string>('')
const dragging = ref(false)
const analyzing = ref(false)
const progress = ref(0)
const showResult = ref(false)

let timer: number | undefined

const fields = [
  { label: '文档类型', value: '服务合同' },
  { label: '甲方', value: '星屿科技有限公司' },
  { label: '合同金额', value: '¥ 120,000.00' },
  { label: '有效期', value: '2026-11-01 至 2027-10-31' },
  { label: '付款方式', value: '分三期，验收后 15 日内支付' },
]

const risks = ['自动续约条款未加粗提示', '违约金比例高于行业常见区间', '交付验收标准缺少量化指标']

function openPicker() {
  fileInput.value?.click()
}

function setFile(file: File) {
  selected.value = file.name
  showResult.value = false
  progress.value = 0
  ElMessage.success(`已选择 ${file.name}`)
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) setFile(file)
  input.value = ''
}

function onDrop(event: DragEvent) {
  dragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) setFile(file)
}

function analyze() {
  if (!selected.value) {
    ElMessage.warning('请先选择一个文档')
    return
  }
  analyzing.value = true
  progress.value = 0
  showResult.value = false
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    progress.value = Math.min(100, progress.value + 8)
    if (progress.value >= 100) {
      window.clearInterval(timer)
      analyzing.value = false
      showResult.value = true
      ElMessage.success('示例分析完成（M0 Mock，不解析真实文件）')
    }
  }, 110)
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="page">
    <LearningNote
      title="文档分析的三层能力"
      :points="['解析：PDF / Word / Markdown 统一转文本', '抽取：按 JSON Schema 拿结构化字段', '追问：基于文档的 RAG 问答']"
      next="M3：先做摘要与追问，再做字段抽取和导出"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">文档分析</h1>
        <p class="page-subtitle">上传一份文档，得到摘要、关键字段与风险提示。当前为示例结果。</p>
      </div>
      <span class="pill coral"><el-icon><MagicStick /></el-icon> M3 规划</span>
    </div>

    <div class="analysis-grid">
      <div class="left">
        <div
          class="dropzone card"
          :class="{ dragging }"
          @click="openPicker"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="onDrop"
        >
          <input ref="fileInput" type="file" accept=".pdf,.docx,.md,.txt" hidden @change="onFile" />
          <div class="drop-icon"><el-icon :size="22"><UploadFilled /></el-icon></div>
          <strong>{{ selected || '把文档拖到这里' }}</strong>
          <p>支持 PDF / DOCX / Markdown / TXT，单文件不超过 20MB</p>
          <span class="pick">选择文件</span>
        </div>

        <div class="action card">
          <div class="action-text">
            <strong>分析任务</strong>
            <span>摘要 + 关键字段 + 风险提示</span>
          </div>
          <el-button type="primary" round :loading="analyzing" @click="analyze">
            开始分析
          </el-button>
        </div>

        <div v-if="analyzing" class="progress card">
          <div class="progress-head">
            <span>正在解析与抽取…</span>
            <b>{{ progress }}%</b>
          </div>
          <div class="bar"><i :style="{ width: `${progress}%` }" /></div>
          <p>M0 为模拟进度；M3 会改为后端异步任务 + 轮询/SSE。</p>
        </div>
      </div>

      <div class="right">
        <div v-if="!showResult" class="result-empty card">
          <div class="doc-icon"><el-icon :size="26"><Document /></el-icon></div>
          <h3>分析结果会显示在这里</h3>
          <p>上传文档并点击“开始分析”，查看摘要、字段和风险点的展示方式。</p>
          <ul>
            <li>摘要：3–5 句话说明文档目的</li>
            <li>字段：金额、期限、双方等</li>
            <li>风险：需要人工复核的条款</li>
          </ul>
        </div>

        <div v-else class="result card fade-up">
          <div class="result-head">
            <div>
              <span class="pill mint">示例结果</span>
              <h3>{{ selected }}</h3>
            </div>
            <el-button size="small" round @click="ElMessage.info('M3 接入后支持导出 Markdown / JSON')">
              导出
            </el-button>
          </div>

          <section>
            <h4>摘要</h4>
            <p>
              这是一份为期一年的技术服务合同，甲方委托乙方提供 AI 应用开发与运维支持。合同总额 12
              万元，分三期支付，验收后 15 日内结算。需要重点关注自动续约与验收标准条款。
            </p>
          </section>

          <section>
            <h4>关键字段</h4>
            <div class="fields">
              <div v-for="field in fields" :key="field.label" class="field">
                <span>{{ field.label }}</span>
                <strong>{{ field.value }}</strong>
              </div>
            </div>
          </section>

          <section>
            <h4>风险提示</h4>
            <ul class="risks">
              <li v-for="risk in risks" :key="risk">{{ risk }}</li>
            </ul>
          </section>

          <p class="disclaimer">以上为 Mock 示例数据，不对应真实文件，仅用于确认界面结构。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
  align-items: start;
}

.left {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 34px 20px;
  border-style: dashed;
  border-width: 1.5px;
  text-align: center;
  cursor: pointer;
  transition: 0.18s ease;
}

.dropzone:hover,
.dropzone.dragging {
  border-color: #ffb3ac;
  background: #fffbfa;
}

.drop-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 12px;
  border-radius: 14px;
  background: var(--xy-coral-soft);
  color: var(--xy-coral);
}

.dropzone strong {
  max-width: 100%;
  overflow: hidden;
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropzone p {
  margin-top: 6px;
  color: var(--xy-muted);
  font-size: 11px;
}

.pick {
  margin-top: 12px;
  padding: 5px 12px;
  border-radius: 999px;
  background: #f4f5fa;
  color: #63677d;
  font-size: 10.5px;
}

.action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 15px;
}

.action-text strong {
  display: block;
  font-size: 12.5px;
}

.action-text span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.progress {
  padding: 13px 15px;
}

.progress-head {
  display: flex;
  justify-content: space-between;
  font-size: 11.5px;
}

.progress-head b {
  color: var(--xy-coral);
}

.bar {
  height: 5px;
  margin-top: 9px;
  border-radius: 99px;
  background: #f1f2f8;
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ff8a5c, #ff5e8a);
  transition: width 0.12s linear;
}

.progress p {
  margin-top: 7px;
  color: #b0b3c4;
  font-size: 9.5px;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 30px;
  text-align: center;
}

.doc-icon {
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
  max-width: 360px;
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

.result-head h3 {
  margin-top: 6px;
  font-size: 15px;
}

.result section {
  margin-top: 16px;
}

.result h4 {
  margin-bottom: 7px;
  color: var(--xy-violet);
  font-size: 10.5px;
  letter-spacing: 0.04em;
}

.result section > p {
  color: #5f6377;
  font-size: 12px;
  line-height: 1.8;
}

.fields {
  display: flex;
  flex-direction: column;
  gap: 1px;
  border: 1px solid #f0f1f6;
  border-radius: 10px;
  overflow: hidden;
}

.field {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 11px;
  background: #fcfcfe;
  font-size: 11.5px;
}

.field span {
  color: var(--xy-muted);
}

.field strong {
  text-align: right;
}

.risks {
  margin: 0;
  padding-left: 16px;
  color: #5f6377;
  font-size: 11.5px;
  line-height: 1.9;
}

.risks li::marker {
  color: var(--xy-coral);
}

.disclaimer {
  margin-top: 16px;
  padding-top: 10px;
  border-top: 1px dashed #f0f1f6;
  color: #b0b3c4;
  font-size: 9.5px;
}

@media (max-width: 900px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
}
</style>

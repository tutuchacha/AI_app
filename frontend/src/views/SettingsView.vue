<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const form = reactive({
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode',
  chatModel: 'qwen-plus',
  embeddingModel: 'text-embedding-v3',
  apiKey: '',
  temperature: 0.7,
})

const checkpoints = [
  { label: 'JDK 21', done: true, note: 'D:\\JDK21' },
  { label: 'MySQL 8', done: true, note: 'ai_app / Flyway V2' },
  { label: '后端骨架', done: true, note: 'Spring Boot 3.5 + JWT' },
  { label: '模型服务', done: true, note: '百炼 qwen-plus（SSE 已通）' },
]

function save() {
  localStorage.setItem('xy_model_config', JSON.stringify(form))
  ElMessage.success('已保存到浏览器本地（暂不提交后端）')
}
</script>

<template>
  <div class="page settings">
    <LearningNote
      title="配置为什么要放数据库"
      :points="['api_key 加密存储，前端不接触明文', '模型切换不改代码', '参数变更可审计']"
      next="M1：ai_model 表 + 管理接口，前端只拿模型列表"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">设置</h1>
        <p class="page-subtitle">模型、Prompt 与偏好。当前配置只保存在浏览器 localStorage。</p>
      </div>
      <span class="pill"><el-icon><Setting /></el-icon> 本地配置</span>
    </div>

    <div class="settings-grid">
      <div class="form-card card">
        <div class="card-head">
          <div>
            <h3>模型服务</h3>
            <p>OpenAI 兼容协议，可切换 Ollama / DeepSeek / 通义</p>
          </div>
        </div>

        <div class="form">
          <label>
            <span>Base URL</span>
            <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
          </label>

          <div class="row">
            <label>
              <span>对话模型</span>
              <el-input v-model="form.chatModel" placeholder="deepseek-chat" />
            </label>
            <label>
              <span>Embedding 模型</span>
              <el-input v-model="form.embeddingModel" placeholder="bge-m3" />
            </label>
          </div>

          <label>
            <span>API Key</span>
            <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-...（不会上传）" />
            <small>正式接入后只存后端密文，前端永远不保存 Key。</small>
          </label>

          <label>
            <span>Temperature · {{ form.temperature.toFixed(1) }}</span>
            <el-slider v-model="form.temperature" :min="0" :max="1" :step="0.1" />
          </label>

          <div class="form-actions">
            <el-button type="primary" round @click="save">保存本地配置</el-button>
            <el-button round @click="ElMessage.info('M1 接入后支持测试连接')">测试连接</el-button>
          </div>
        </div>
      </div>

      <div class="side">
        <div class="switch-card card">
          <div class="switch-row">
            <div>
              <strong>学习提示</strong>
              <span>在每个页面显示实现思路</span>
            </div>
            <el-switch v-model="ui.learningMode" />
          </div>
          <div class="switch-row">
            <div>
              <strong>Mock 模式</strong>
              <span>使用本地模拟流式响应</span>
            </div>
            <el-switch :model-value="true" disabled />
          </div>
        </div>

        <div class="checklist card">
          <h3>环境清单</h3>
          <div v-for="item in checkpoints" :key="item.label" class="check">
            <span class="tick" :class="{ done: item.done }">{{ item.done ? '✓' : '' }}</span>
            <div>
              <strong>{{ item.label }}</strong>
              <span>{{ item.note }}</span>
            </div>
          </div>
        </div>

        <div class="keys card">
          <h3>密钥原则</h3>
          <p>
            前端不落 Key；后端用环境变量或加密表存储；日志与错误信息脱敏；提交前用
            <code>.gitignore</code> 拦一遍。
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings {
  max-width: 1080px;
}

.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(300px, 0.8fr);
  gap: 16px;
  align-items: start;
}

.form-card {
  padding: 18px;
}

.card-head h3 {
  font-size: 14px;
  font-weight: 700;
}

.card-head p {
  margin-top: 3px;
  color: var(--xy-muted);
  font-size: 10.5px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 18px;
}

.form label > span {
  display: block;
  margin-bottom: 6px;
  color: #5f6377;
  font-size: 11.5px;
  font-weight: 600;
}

.form small {
  display: block;
  margin-top: 5px;
  color: #b0b3c4;
  font-size: 10px;
}

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-actions {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.switch-card {
  padding: 6px 15px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f1f6;
}

.switch-row:last-child {
  border-bottom: 0;
}

.switch-row strong {
  display: block;
  font-size: 12px;
}

.switch-row span {
  color: var(--xy-muted);
  font-size: 10px;
}

.checklist,
.keys {
  padding: 15px;
}

.checklist h3,
.keys h3 {
  margin-bottom: 10px;
  font-size: 12.5px;
}

.check {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 6px 0;
}

.tick {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  margin-top: 1px;
  border: 1px solid #d5d7e4;
  border-radius: 5px;
  color: #fff;
  font-size: 9px;
}

.tick.done {
  border-color: var(--xy-mint);
  background: var(--xy-mint);
}

.check strong {
  display: block;
  font-size: 11.5px;
}

.check span {
  color: var(--xy-muted);
  font-size: 10px;
}

.keys p {
  color: #6f7387;
  font-size: 11px;
  line-height: 1.8;
}

.keys code {
  padding: 1px 4px;
  border-radius: 4px;
  background: #f4f5fa;
  font-size: 10px;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
  .row {
    grid-template-columns: 1fr;
  }
}
</style>

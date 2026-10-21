<script setup lang="ts">
import { computed } from 'vue'
import { Coin, DataLine, Timer, Warning } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'

const stats = [
  { label: '今日调用', value: '128', unit: '次', delta: '+18%', icon: DataLine, tone: 'violet' },
  { label: 'Token 消耗', value: '86.4', unit: 'k', delta: '+12%', icon: Coin, tone: 'coral' },
  { label: 'P95 延迟', value: '1.8', unit: 's', delta: '-6%', icon: Timer, tone: 'mint' },
  { label: '错误率', value: '0.7', unit: '%', delta: '正常', icon: Warning, tone: 'amber' },
]

const days = [
  { day: '周一', value: 42 },
  { day: '周二', value: 68 },
  { day: '周三', value: 51 },
  { day: '周四', value: 90 },
  { day: '周五', value: 76 },
  { day: '周六', value: 112 },
  { day: '周日', value: 128 },
]

const max = computed(() => Math.max(...days.map((item) => item.value)))

const modules = [
  { name: '通用对话', value: 54, color: '#7c5cff' },
  { name: '智能客服', value: 28, color: '#14b88a' },
  { name: '恋爱陪伴', value: 12, color: '#ff6b5e' },
  { name: '文档分析', value: 6, color: '#f0a020' },
]
</script>

<template>
  <div class="page">
    <LearningNote
      title="可观测性从第一天就埋点"
      :points="['Micrometer 统一采集', 'Actuator 暴露 /prometheus', 'Grafana 做展示与告警']"
      next="M5：自定义 ai_chat_latency / ai_tokens_total 指标"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">用量看板</h1>
        <p class="page-subtitle">调用量、Token、延迟与错误率。当前为示例数据，M5 接入 Prometheus。</p>
      </div>
      <span class="pill">示例数据</span>
    </div>

    <div class="grid-4">
      <div v-for="stat in stats" :key="stat.label" class="stat card">
        <div class="stat-top">
          <span>{{ stat.label }}</span>
          <div class="stat-icon" :class="stat.tone">
            <el-icon :size="16"><component :is="stat.icon" /></el-icon>
          </div>
        </div>
        <div class="stat-value">
          <b>{{ stat.value }}</b>
          <span>{{ stat.unit }}</span>
          <em>{{ stat.delta }}</em>
        </div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="chart-card card">
        <div class="card-head">
          <div>
            <h3>近 7 天调用量</h3>
            <p>单位：次 / 天</p>
          </div>
          <span class="pill mint">+18%</span>
        </div>

        <div class="chart">
          <div v-for="item in days" :key="item.day" class="bar-col">
            <span class="bar-value">{{ item.value }}</span>
            <div class="bar-track">
              <i :style="{ height: `${(item.value / max) * 100}%` }" />
            </div>
            <span class="bar-day">{{ item.day }}</span>
          </div>
        </div>
      </div>

      <div class="modules card">
        <div class="card-head">
          <div>
            <h3>模块分布</h3>
            <p>按调用次数占比</p>
          </div>
        </div>
        <div class="module-list">
          <div v-for="item in modules" :key="item.name" class="module">
            <div class="module-row">
              <span>{{ item.name }}</span>
              <b>{{ item.value }}%</b>
            </div>
            <div class="module-track">
              <i :style="{ width: `${item.value}%`, background: item.color }" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="obs card">
      <div class="obs-copy">
        <span class="pill">M5 接入</span>
        <h3>Actuator → Prometheus → Grafana</h3>
        <p>
          后端暴露 <code>/actuator/prometheus</code>，Prometheus 每 10s 抓取，Grafana
          展示 QPS、P95、Token、RAG 命中率。这里的卡片只是前端占位。
        </p>
      </div>
      <div class="obs-flow">
        <span>Actuator</span>
        <i>→</i>
        <span>Prometheus</span>
        <i>→</i>
        <span>Grafana</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat {
  padding: 14px 15px;
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--xy-muted);
  font-size: 11px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
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

.stat-icon.amber {
  background: #fff7e6;
  color: #c98412;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 8px;
}

.stat-value b {
  font-size: 25px;
  letter-spacing: -0.03em;
}

.stat-value span {
  color: var(--xy-muted);
  font-size: 11px;
}

.stat-value em {
  margin-left: auto;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
  font-size: 9.5px;
  font-style: normal;
  font-weight: 700;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
}

.chart-card,
.modules {
  padding: 16px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card-head h3 {
  font-size: 13px;
  font-weight: 700;
}

.card-head p {
  margin-top: 3px;
  color: var(--xy-muted);
  font-size: 10px;
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 210px;
  margin-top: 20px;
  padding: 0 4px;
}

.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  height: 100%;
  gap: 6px;
}

.bar-value {
  color: #a0a4b6;
  font-size: 9.5px;
}

.bar-track {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  flex: 1;
  border-radius: 8px;
  background: linear-gradient(180deg, transparent, #f8f8fc);
}

.bar-track i {
  display: block;
  width: 55%;
  border-radius: 7px 7px 3px 3px;
  background: linear-gradient(180deg, #ff8a5c, #ff5e8a);
  transition: height 0.3s ease;
}

.bar-col:hover .bar-track i {
  background: linear-gradient(180deg, #8b5cff, #6d8cff);
}

.bar-day {
  color: var(--xy-muted);
  font-size: 9.5px;
}

.module-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-top: 22px;
}

.module-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #63677d;
  font-size: 11.5px;
}

.module-row b {
  font-size: 11px;
}

.module-track {
  height: 6px;
  margin-top: 6px;
  border-radius: 99px;
  background: #f4f5fa;
  overflow: hidden;
}

.module-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.obs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 18px;
  background:
    radial-gradient(60% 100% at 100% 0%, rgba(124, 92, 255, 0.06), transparent 70%),
    #fff;
}

.obs-copy {
  max-width: 620px;
}

.obs-copy h3 {
  margin-top: 8px;
  font-size: 15px;
}

.obs-copy p {
  margin-top: 6px;
  color: var(--xy-muted);
  font-size: 11.5px;
  line-height: 1.75;
}

.obs-copy code {
  padding: 1px 5px;
  border-radius: 5px;
  background: #f4f5fa;
  color: var(--xy-violet);
  font-size: 10.5px;
}

.obs-flow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.obs-flow span {
  padding: 7px 11px;
  border: 1px solid var(--xy-line);
  border-radius: 9px;
  background: #fbfbfe;
  color: #63677d;
  font-size: 10.5px;
  font-weight: 600;
}

.obs-flow i {
  color: #c3c6d4;
  font-style: normal;
}

@media (max-width: 1000px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  .obs {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

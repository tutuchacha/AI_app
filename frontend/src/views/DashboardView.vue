<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Coin, DataLine, Refresh, Timer, Warning } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import { getUsage, type UsageView } from '@/api/metrics'

const GRAFANA_URL = 'http://192.168.101.137:3000'

const usage = ref<UsageView | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    usage.value = await getUsage(7)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '用量数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function formatTokens(value: number): string {
  if (value >= 10000) return `${(value / 1000).toFixed(1)}k`
  return String(value)
}

const stats = computed(() => {
  const today = usage.value?.today
  const mine = usage.value?.mine
  return [
    {
      label: '今日调用',
      value: String(today?.calls ?? 0),
      unit: '次',
      note: `我的 ${mine?.calls ?? 0} 次`,
      icon: DataLine,
      tone: 'violet',
    },
    {
      label: 'Token 消耗',
      value: formatTokens((today?.tokensIn ?? 0) + (today?.tokensOut ?? 0)),
      unit: 'tok',
      note: `入 ${formatTokens(today?.tokensIn ?? 0)} / 出 ${formatTokens(today?.tokensOut ?? 0)}`,
      icon: Coin,
      tone: 'coral',
    },
    {
      label: 'P95 延迟',
      value: ((today?.p95Ms ?? 0) / 1000).toFixed(1),
      unit: 's',
      note: '今日样本',
      icon: Timer,
      tone: 'mint',
    },
    {
      label: '错误率',
      value: (today?.errorRate ?? 0).toFixed(1),
      unit: '%',
      note: (today?.errorRate ?? 0) > 2 ? '偏高' : '正常',
      icon: Warning,
      tone: 'amber',
    },
  ]
})

const chartDays = computed(() => usage.value?.daily ?? [])

const max = computed(() => Math.max(1, ...chartDays.value.map((item) => item.calls)))

const palette = ['#7c5cff', '#14b88a', '#ff6b5e', '#f0a020', '#6d8cff']

const modules = computed(() =>
  (usage.value?.modules ?? []).map((item, index) => ({
    name: item.label,
    value: item.share,
    calls: item.calls,
    color: palette[index % palette.length],
  })),
)

const sourceText = computed(() => {
  if (!usage.value) return '等待数据'
  return usage.value.prometheusUp ? '库 + Prometheus' : '数据库口径'
})
</script>

<template>
  <div class="page">
    <LearningNote
      title="指标从埋点到看板"
      :points="['Micrometer 埋点，Actuator 暴露', '看板经后端取数，不外露内网地址', 'Prometheus 挂了自动退回数据库口径']"
      next="M4：告警阈值与更多模块维度"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">用量看板</h1>
        <p class="page-subtitle">
          调用量、Token、P95 延迟与错误率。数据来自本地库与 Prometheus，30 秒缓存。
        </p>
      </div>
      <span class="pill" :class="{ mint: usage?.prometheusUp }">{{ sourceText }}</span>
    </div>

    <div v-if="error" class="error-bar card">
      <span>{{ error }}</span>
      <el-button size="small" round :loading="loading" @click="load">重试</el-button>
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
          <b>{{ loading && !usage ? '—' : stat.value }}</b>
          <span>{{ stat.unit }}</span>
          <em>{{ stat.note }}</em>
        </div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="chart-card card">
        <div class="card-head">
          <div>
            <h3>近 7 天调用量</h3>
            <p>单位：次 / 天，只统计已完成的模型回复</p>
          </div>
          <el-button text size="small" :loading="loading" @click="load">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>

        <div class="chart">
          <div v-for="item in chartDays" :key="item.date" class="bar-col">
            <span class="bar-value">{{ item.calls }}</span>
            <div class="bar-track">
              <i :style="{ height: `${(item.calls / max) * 100}%` }" />
            </div>
            <span class="bar-day">{{ item.label }}</span>
          </div>
          <div v-if="!chartDays.length" class="chart-empty">暂无调用记录</div>
        </div>
      </div>

      <div class="modules card">
        <div class="card-head">
          <div>
            <h3>模块分布</h3>
            <p>按近 7 天调用次数占比</p>
          </div>
        </div>
        <div class="module-list">
          <div v-for="item in modules" :key="item.name" class="module">
            <div class="module-row">
              <span>{{ item.name }}</span>
              <b>{{ item.value }}% · {{ item.calls }} 次</b>
            </div>
            <div class="module-track">
              <i :style="{ width: `${item.value}%`, background: item.color }" />
            </div>
          </div>
          <div v-if="!modules.length" class="inline-empty">还没有调用记录</div>
        </div>
      </div>
    </div>

    <div class="obs card">
      <div class="obs-copy">
        <span class="pill" :class="{ mint: usage?.prometheusUp }">
          {{ usage?.prometheusUp ? 'Prometheus 已连接' : 'Prometheus 未连接' }}
        </span>
        <h3>Actuator → Prometheus → Grafana</h3>
        <p>
          后端埋点 <code>ai_chat_requests</code> / <code>ai_chat_tokens</code> /
          <code>ai_chat_latency</code>，Prometheus 每 15s 抓取。当前活跃流
          <b>{{ usage?.activeStreams ?? 0 }}</b> 条；Prometheus 不可用时本页仍按数据库统计。
        </p>
      </div>
      <div class="obs-flow">
        <span>Actuator</span>
        <i>→</i>
        <span>Prometheus</span>
        <i>→</i>
        <span>Grafana</span>
        <el-button size="small" round tag="a" :href="GRAFANA_URL" target="_blank">打开 Grafana</el-button>
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
  background: #f4f5fa;
  color: #7d8196;
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
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 210px;
  margin-top: 20px;
  padding: 0 4px;
}

.chart-empty,
.inline-empty {
  display: grid;
  place-items: center;
  flex: 1;
  color: var(--xy-muted);
  font-size: 11.5px;
}

.inline-empty {
  min-height: 80px;
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
  min-height: 2px;
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

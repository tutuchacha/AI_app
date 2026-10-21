<script setup lang="ts">
import { ArrowRight, Reading } from '@element-plus/icons-vue'
import { useUiStore } from '@/stores/ui'

defineProps<{
  title: string
  points: string[]
  next?: string
}>()

const ui = useUiStore()
</script>

<template>
  <aside v-if="ui.learningMode" class="learning-note fade-up">
    <div class="icon">
      <el-icon :size="16"><Reading /></el-icon>
    </div>
    <div class="body">
      <div class="eyebrow">本页学习点 · M0</div>
      <h3>{{ title }}</h3>
      <ul>
        <li v-for="point in points" :key="point">{{ point }}</li>
      </ul>
      <p v-if="next" class="next">
        <el-icon :size="13"><ArrowRight /></el-icon>
        {{ next }}
      </p>
    </div>
  </aside>
</template>

<style scoped>
.learning-note {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border: 1px dashed #d9d2ff;
  border-radius: 14px;
  background: linear-gradient(120deg, #faf8ff, #fff8f5);
}

.icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 9px;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
}

.eyebrow {
  color: var(--xy-violet);
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.body h3 {
  margin-top: 3px;
  font-size: 13.5px;
  font-weight: 700;
}

.body ul {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  margin: 7px 0 0;
  padding: 0;
  list-style: none;
  color: #5c6075;
  font-size: 12px;
}

.body li::before {
  content: '·';
  margin-right: 5px;
  color: var(--xy-coral);
  font-weight: 800;
}

.next {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 7px;
  color: var(--xy-muted);
  font-size: 11.5px;
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { LocationInformation } from '@element-plus/icons-vue'
import { CITY_OPTIONS } from '@/constants/cities'
import { locateCity } from '@/api/date'

/**
 * 城市选择：常用城市下拉（可搜索、可直接输入）+「用当前位置」。
 * 定位走浏览器 geolocation，再由后端调高德逆地理，key 不暴露给前端。
 */
const props = withDefaults(defineProps<{ modelValue?: string; placeholder?: string }>(), {
  modelValue: '',
  placeholder: '选择城市，或直接输入',
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const city = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value ?? ''),
})

const locating = ref(false)
const hint = ref('')

const GEO_ERRORS: Record<number, string> = {
  1: '定位权限被拒绝，请在浏览器地址栏允许后重试',
  2: '无法获取位置，请检查系统定位或网络',
  3: '定位超时，请重试或手动选择',
}

function useLocation() {
  if (!('geolocation' in navigator)) {
    hint.value = '当前浏览器不支持定位，请手动选择城市'
    return
  }
  locating.value = true
  hint.value = ''
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      try {
        const geo = await locateCity(position.coords.longitude, position.coords.latitude)
        if (!geo.city) {
          hint.value = '已拿到坐标，但没解析出城市，请手动选一个'
          return
        }
        city.value = geo.city
        hint.value = geo.address
          ? `定位到 ${geo.city}${geo.district || ''} · ${geo.address}`
          : `已定位到 ${geo.city}，可再手动微调`
        ElMessage.success(`已定位到 ${geo.city}`)
      } catch (err) {
        hint.value = err instanceof Error ? err.message : '定位失败，请手动选择'
      } finally {
        locating.value = false
      }
    },
    (err) => {
      locating.value = false
      hint.value = GEO_ERRORS[err.code] || '定位失败，请手动选择'
    },
    { enableHighAccuracy: false, timeout: 8000, maximumAge: 300000 },
  )
}
</script>

<template>
  <div class="city-picker">
    <div class="city-row">
      <el-select
        v-model="city"
        class="city-select"
        filterable
        allow-create
        default-first-option
        clearable
        :placeholder="placeholder"
      >
        <el-option v-for="item in CITY_OPTIONS" :key="item" :label="item" :value="item" />
      </el-select>
      <el-button :loading="locating" @click="useLocation">
        <el-icon><LocationInformation /></el-icon>
        用当前位置
      </el-button>
    </div>
    <p v-if="hint" class="city-hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.city-row {
  display: flex;
  gap: 6px;
  width: 100%;
}

.city-select {
  min-width: 0;
  flex: 1;
}

.city-hint {
  margin-top: 4px;
  color: var(--xy-muted);
  font-size: 10.5px;
  line-height: 1.5;
}
</style>

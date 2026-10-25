<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { Promotion, VideoPause } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    streaming?: boolean
    placeholder?: string
  }>(),
  {
    streaming: false,
    placeholder: '说点什么，Enter 发送，Shift + Enter 换行…',
  },
)

const emit = defineEmits<{
  send: [text: string]
  stop: []
}>()

const text = ref('')
const inputRef = ref<HTMLTextAreaElement>()

function resize() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 148)}px`
}

function submit() {
  const value = text.value.trim()
  if (!value || props.streaming) return
  emit('send', value)
  text.value = ''
  nextTick(resize)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}

function fill(value: string) {
  text.value = value
  nextTick(() => {
    resize()
    inputRef.value?.focus()
  })
}

defineExpose({ fill })
</script>

<template>
  <div class="composer">
    <div class="box">
      <textarea
        ref="inputRef"
        v-model="text"
        :placeholder="placeholder"
        rows="1"
        :disabled="streaming"
        @input="resize"
        @keydown="onKeydown"
      />
      <div class="actions">
        <span class="hint">Enter 发送 · Shift + Enter 换行</span>
        <el-button v-if="streaming" round size="small" @click="emit('stop')">
          <el-icon><VideoPause /></el-icon>
          停止
        </el-button>
        <el-button v-else type="primary" round size="small" :disabled="!text.trim()" @click="submit">
          <el-icon><Promotion /></el-icon>
          发送
        </el-button>
      </div>
    </div>
    <p class="foot">回答由服务端流式返回，可随时停止；设置 VITE_USE_MOCK=true 可离线</p>
  </div>
</template>

<style scoped>
.composer {
  padding: 10px 18px 14px;
  background: #fff;
  border-top: 1px solid var(--xy-line);
}

.box {
  padding: 10px 12px 8px;
  border: 1px solid var(--xy-line);
  border-radius: 14px;
  background: #fbfbfe;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.box:focus-within {
  border-color: #ffb3ac;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(255, 107, 94, 0.08);
}

textarea {
  display: block;
  width: 100%;
  min-height: 24px;
  max-height: 148px;
  padding: 0;
  border: 0;
  outline: 0;
  resize: none;
  background: transparent;
  color: var(--xy-ink);
  font-size: 13.5px;
  line-height: 1.7;
}

textarea::placeholder {
  color: #a8acbd;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.hint {
  margin-right: auto;
  color: #b0b3c4;
  font-size: 10.5px;
}

.foot {
  margin-top: 6px;
  text-align: center;
  color: #b0b3c4;
  font-size: 10.5px;
}

@media (max-width: 640px) {
  .hint {
    display: none;
  }
}
</style>

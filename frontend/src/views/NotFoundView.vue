<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="notfound">
    <span class="code">404</span>
    <h1>这座小岛还没有建好</h1>
    <p>页面不存在，或者对应的功能还在路上。</p>
    <el-button type="primary" round @click="router.push('/chat')">回到对话</el-button>
  </div>
</template>

<style scoped>
.notfound {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 100vh;
  background:
    radial-gradient(50% 40% at 50% 0%, rgba(124, 92, 255, 0.08), transparent 70%),
    var(--xy-bg);
  text-align: center;
}

.code {
  background: linear-gradient(135deg, #ff8a5c, #ff5e8a 55%, #8b5cff);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-size: 80px;
  font-weight: 800;
  letter-spacing: -0.05em;
}

h1 {
  margin-top: 6px;
  font-size: 20px;
}

p {
  margin: 8px 0 20px;
  color: var(--xy-muted);
  font-size: 12px;
}
</style>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, ChatDotRound, Document, Lock, Star, User } from '@element-plus/icons-vue'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('demo')
const password = ref('demo1234')
const loading = ref(false)

const features = [
  { icon: ChatDotRound, title: '对话', text: 'SSE / WebSocket 双通道' },
  { icon: Star, title: '陪伴', text: '角色卡与长期记忆' },
  { icon: Document, title: '知识', text: 'RAG 与文档分析' },
]

function redirectTarget(): string {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
  return redirect.startsWith('/') ? redirect : '/chat'
}

async function submit() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    ElMessage.success(auth.isMock ? '已进入离线 Mock 预览' : '登录成功')
    await router.push(redirectTarget())
  } catch (error) {
    const message = error instanceof ApiError ? error.message : '登录失败，请稍后重试'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="visual">
      <div class="brand">
        <div class="mark">星</div>
        <div>
          <strong>星屿 AI</strong>
          <span>学习型 AI 工作台</span>
        </div>
      </div>

      <div class="pitch">
        <span class="eyebrow">M1 · SSE ONLINE</span>
        <h1>把 AI 能力，<br />一步步做成产品。</h1>
        <p>
          聊天、恋爱陪伴、智能客服、文档分析。先看见完整的样子，再一层层补上后端。
        </p>

        <div class="features">
          <div v-for="item in features" :key="item.title" class="feature">
            <el-icon :size="16"><component :is="item.icon" /></el-icon>
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.text }}</span>
            </div>
          </div>
        </div>
      </div>

      <p class="foot">Spring AI · Vue 3 · MySQL · Prometheus / Grafana</p>
    </section>

    <section class="form-side">
      <div class="form-card fade-up">
        <span class="pill">M1 阶段</span>
        <h2>欢迎回来</h2>
        <p class="sub">真实账号登录，token 由后端签发；开发时可开 Mock 离线预览。</p>

        <form @submit.prevent="submit">
          <label>
            <span>账号</span>
            <el-input v-model="username" size="large" placeholder="输入用户名">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </label>

          <label>
            <span>密码</span>
            <el-input v-model="password" type="password" size="large" show-password placeholder="输入密码">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </label>

          <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">
            进入工作台
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </el-button>
        </form>

        <div class="note">
          <strong>登录方式</strong>
          默认请求 `/api/auth/login`，JWT 有效期 2 小时；设置 `VITE_USE_MOCK=true` 可离线跳过后端。
        </div>

        <p class="demo">演示账号：demo / demo1234（本地库）</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  min-height: 100%;
  background: #fff;
}

.visual {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 38px 48px 30px;
  overflow: hidden;
  background:
    radial-gradient(60% 50% at 85% 10%, rgba(124, 92, 255, 0.3), transparent 60%),
    radial-gradient(50% 40% at 10% 90%, rgba(255, 107, 94, 0.22), transparent 60%),
    #141726;
  color: #fff;
}

.visual::after {
  position: absolute;
  top: 12%;
  right: -12%;
  width: 380px;
  height: 380px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  content: '';
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: linear-gradient(135deg, #ff8a5c, #ff5e8a 55%, #8b5cff);
  font-size: 19px;
  font-weight: 800;
  box-shadow: 0 10px 24px rgba(255, 94, 138, 0.3);
}

.brand strong {
  display: block;
  font-size: 16px;
}

.brand span {
  color: #9aa0bb;
  font-size: 11px;
}

.pitch {
  position: relative;
  z-index: 1;
  max-width: 520px;
  margin: auto 0;
  padding: 40px 0;
}

.eyebrow {
  color: #ffb4a8;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.pitch h1 {
  margin-top: 16px;
  font-size: clamp(32px, 4vw, 46px);
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.03em;
}

.pitch > p {
  max-width: 430px;
  margin-top: 16px;
  color: #a9aec6;
  font-size: 13.5px;
  line-height: 1.9;
}

.features {
  display: flex;
  gap: 12px;
  margin-top: 34px;
}

.feature {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.045);
  color: #ffb4a8;
}

.feature strong {
  display: block;
  color: #fff;
  font-size: 12px;
}

.feature span {
  color: #8f95b0;
  font-size: 10.5px;
}

.foot {
  position: relative;
  z-index: 1;
  color: #6f7590;
  font-size: 10.5px;
}

.form-side {
  display: grid;
  place-items: center;
  padding: 40px;
  background:
    radial-gradient(50% 35% at 100% 0%, rgba(255, 107, 94, 0.06), transparent 70%),
    #fff;
}

.form-card {
  width: min(390px, 100%);
}

.form-card h2 {
  margin-top: 14px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.sub {
  margin-top: 8px;
  color: var(--xy-muted);
  font-size: 12.5px;
  line-height: 1.7;
}

form {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-top: 28px;
}

form label > span {
  display: block;
  margin-bottom: 6px;
  color: #5f6377;
  font-size: 12px;
  font-weight: 600;
}

.submit {
  width: 100%;
  margin-top: 6px;
}

.arrow {
  margin-left: 4px;
}

.note {
  margin-top: 22px;
  padding: 12px 14px;
  border: 1px dashed #e2def7;
  border-radius: 12px;
  background: #faf9ff;
  color: #73778c;
  font-size: 11.5px;
  line-height: 1.7;
}

.note strong {
  display: block;
  margin-bottom: 2px;
  color: var(--xy-violet);
  font-size: 10.5px;
  letter-spacing: 0.05em;
}

.demo {
  margin-top: 14px;
  color: #b0b3c4;
  font-size: 10.5px;
  text-align: center;
}

@media (max-width: 900px) {
  .login {
    grid-template-columns: 1fr;
  }
  .visual {
    display: none;
  }
  .form-side {
    min-height: 100%;
    padding: 24px;
  }
}
</style>

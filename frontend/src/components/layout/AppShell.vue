<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  ChatDotRound,
  Collection,
  DataLine,
  Document,
  Reading,
  Service,
  Setting,
  Star,
  SwitchButton,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const { learningMode } = storeToRefs(ui)

const navItems = [
  { path: '/chat', label: '对话', icon: ChatDotRound },
  { path: '/companion', label: '恋爱陪伴', icon: Star },
  { path: '/support', label: '智能客服', icon: Service },
  { path: '/knowledge', label: '知识库', icon: Collection },
  { path: '/analysis', label: '文档分析', icon: Document },
  { path: '/dashboard', label: '用量看板', icon: DataLine },
]

const initial = computed(() => (auth.name || '访客').slice(0, 1).toUpperCase())

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">星</div>
        <div class="brand-text">
          <strong>星屿 AI</strong>
          <span>学习型 AI 工作台</span>
        </div>
      </div>

      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="side-bottom">
        <RouterLink to="/settings" class="nav-item" active-class="active">
          <el-icon :size="18"><Setting /></el-icon>
          <span>设置</span>
        </RouterLink>

        <div class="stage-card">
          <div class="stage-head">
            <span>当前阶段</span>
            <b>M1</b>
          </div>
          <p>真实流式聊天</p>
          <div class="bar"><i /></div>
          <small>下一步：会话落库</small>
        </div>

        <div class="user">
          <div class="avatar">{{ initial }}</div>
          <div class="user-meta">
            <strong>{{ auth.name || '访客' }}</strong>
            <span>Mock 登录</span>
          </div>
          <button class="logout" title="退出登录" @click="logout">
            <el-icon :size="16"><SwitchButton /></el-icon>
          </button>
        </div>
      </div>
    </aside>

    <section class="main">
      <header class="topbar">
        <div class="crumb">
          <span>星屿工作台</span>
          <i>/</i>
          <strong>{{ route.meta.title }}</strong>
        </div>
        <div class="top-actions">
          <span class="stage-badge">M1 · SSE Online</span>
          <label class="learn-switch">
            <el-icon :size="15"><Reading /></el-icon>
            <span>学习提示</span>
            <el-switch v-model="learningMode" size="small" />
          </label>
        </div>
      </header>

      <main class="content">
        <RouterView v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </main>
    </section>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: 236px;
  flex: 0 0 236px;
  padding: 22px 14px 16px;
  background:
    radial-gradient(120% 60% at 0% 0%, rgba(124, 92, 255, 0.22), transparent 55%),
    radial-gradient(100% 50% at 100% 100%, rgba(255, 107, 94, 0.14), transparent 60%),
    var(--xy-navy);
  color: #eef0f8;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 10px 22px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ff8a5c, #ff5e8a 55%, #8b5cff);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(255, 94, 138, 0.28);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand-text strong {
  font-size: 16px;
  letter-spacing: 0.02em;
}

.brand-text span {
  color: #9aa0bb;
  font-size: 11px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  height: 42px;
  padding: 0 12px;
  border-radius: 11px;
  color: #a9aec6;
  font-size: 13.5px;
  font-weight: 550;
  transition: 0.18s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  box-shadow: inset 3px 0 0 var(--xy-coral);
}

.side-bottom {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: auto;
}

.stage-card {
  margin: 0 4px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.055);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #9aa0bb;
  font-size: 11px;
}

.stage-head b {
  color: #ffb4a8;
  font-size: 12px;
}

.stage-card p {
  margin: 4px 0 9px;
  font-size: 13px;
  font-weight: 600;
}

.bar {
  height: 5px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.bar i {
  display: block;
  width: 18%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #ff8a5c, #ff5e8a);
}

.stage-card small {
  display: block;
  margin-top: 8px;
  color: #8f95b0;
  font-size: 10.5px;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px 2px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 13px;
  font-weight: 700;
}

.user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.user-meta strong {
  font-size: 12.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta span {
  color: #8f95b0;
  font-size: 10.5px;
}

.logout {
  margin-left: auto;
  padding: 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #9aa0bb;
  cursor: pointer;
}

.logout:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 60px;
  flex: 0 0 60px;
  padding: 0 26px;
  background: rgba(255, 255, 255, 0.75);
  border-bottom: 1px solid var(--xy-line);
  backdrop-filter: blur(10px);
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--xy-muted);
}

.crumb i {
  font-style: normal;
  color: #c3c6d4;
}

.crumb strong {
  color: var(--xy-ink);
  font-weight: 650;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stage-badge {
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 11px;
  font-weight: 650;
}

.learn-switch {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--xy-muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 24px 26px 30px;
}

.page-enter-active,
.page-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-leave-to {
  opacity: 0;
}

@media (max-width: 1080px) {
  .sidebar {
    width: 76px;
    flex-basis: 76px;
    padding-left: 10px;
    padding-right: 10px;
  }
  .brand {
    justify-content: center;
    padding-left: 0;
    padding-right: 0;
  }
  .brand-text,
  .nav-item span,
  .stage-card,
  .user-meta {
    display: none;
  }
  .nav-item {
    justify-content: center;
    padding: 0;
  }
  .user {
    justify-content: center;
  }
  .logout {
    display: none;
  }
}

@media (max-width: 720px) {
  .topbar {
    padding: 0 16px;
  }
  .content {
    padding: 16px;
  }
  .stage-badge,
  .learn-switch span:not(.el-switch) {
    display: none;
  }
}
</style>

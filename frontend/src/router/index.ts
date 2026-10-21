import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppShell from '@/components/layout/AppShell.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: AppShell,
      redirect: '/chat',
      children: [
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/ChatView.vue'),
          meta: { title: '对话', subtitle: '多轮会话、流式输出与传输层抽象' },
        },
        {
          path: 'companion',
          name: 'companion',
          component: () => import('@/views/CompanionView.vue'),
          meta: { title: '恋爱陪伴', subtitle: '角色卡、人设与长期记忆（规划中）' },
        },
        {
          path: 'support',
          name: 'support',
          component: () => import('@/views/SupportView.vue'),
          meta: { title: '智能客服', subtitle: '知识库问答、引用来源与转人工（规划中）' },
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/KnowledgeView.vue'),
          meta: { title: '知识库', subtitle: '文档入库、切片与检索测试（M2）' },
        },
        {
          path: 'analysis',
          name: 'analysis',
          component: () => import('@/views/AnalysisView.vue'),
          meta: { title: '文档分析', subtitle: '摘要、追问与结构化抽取（M3）' },
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '用量看板', subtitle: '调用量、Token 与延迟（M5 接 Grafana）' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/SettingsView.vue'),
          meta: { title: '设置', subtitle: '模型、Prompt 与偏好' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { public: true, title: '页面不存在' },
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (to.name === 'login' && auth.isLoggedIn) return { path: '/chat' }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router

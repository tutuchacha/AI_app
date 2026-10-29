import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { loginApi, registerApi } from '@/api/auth'
import { clearSession, getNickname, getToken, getUsername, saveSession } from '@/api/http'

/** VITE_USE_MOCK=true 时走本地假登录，便于离线开发 */
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const name = ref(getUsername())
  const nickname = ref(getNickname())
  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(username: string, password: string) {
    if (USE_MOCK) {
      const mockToken = `mock-${Date.now()}`
      token.value = mockToken
      name.value = username
      nickname.value = username
      saveSession(mockToken, username, username)
      return
    }

    const result = await loginApi(username, password)
    token.value = result.token
    name.value = result.username
    nickname.value = result.nickname
    saveSession(result.token, result.username, result.nickname)
  }

  /** 注册成功后直接登录，少一次手动输入 */
  async function register(username: string, password: string, nickname?: string) {
    await registerApi(username, password, nickname)
    await login(username, password)
  }

  function logout() {
    token.value = ''
    name.value = ''
    nickname.value = ''
    clearSession()
  }

  return { token, name, nickname, isLoggedIn, isMock: USE_MOCK, login, register, logout }
})

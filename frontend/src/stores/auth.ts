import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const TOKEN_KEY = 'xy_token'
const NAME_KEY = 'xy_name'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) ?? '')
  const name = ref(localStorage.getItem(NAME_KEY) ?? '')
  const isLoggedIn = computed(() => Boolean(token.value))

  function login(username: string) {
    token.value = `mock-${Date.now()}`
    name.value = username
    localStorage.setItem(TOKEN_KEY, token.value)
    localStorage.setItem(NAME_KEY, username)
  }

  function logout() {
    token.value = ''
    name.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(NAME_KEY)
  }

  return { token, name, isLoggedIn, login, logout }
})

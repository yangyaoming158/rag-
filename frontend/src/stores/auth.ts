import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '../api/auth'
import type { UserDto } from '../api/auth'

const TOKEN_KEY = 'devdocs-rag-token'
const USER_KEY = 'devdocs-rag-user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserDto | null>(readUser())
  const loggedIn = computed(() => Boolean(token.value))

  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    token.value = result.token
    user.value = result.user
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(USER_KEY, JSON.stringify(result.user))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, loggedIn, login, logout }
})

function readUser(): UserDto | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserDto
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

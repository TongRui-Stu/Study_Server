import { defineStore } from 'pinia'
import { getCurrentUser, loginUser, registerUser } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/authStorage'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: null
  }),
  actions: {
    async login(credentials) {
      const response = await loginUser(credentials)
      this.token = response.data.token
      this.user = response.data.user
      setToken(this.token)
      return response.data
    },
    async register(details) {
      const response = await registerUser(details)
      return response.data
    },
    async fetchCurrentUser() {
      if (!this.token) return null
      try {
        const response = await getCurrentUser()
        this.user = response.data
        return this.user
      } catch (error) {
        this.logout()
        throw error
      }
    },
    logout() {
      this.token = ''
      this.user = null
      clearToken()
    }
  }
})

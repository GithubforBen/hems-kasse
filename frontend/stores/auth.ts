import { defineStore } from 'pinia'
import type { AuthUser } from '~/types/api'

interface LoginResponse {
  token: string
  user: AuthUser
}

const TOKEN_COOKIE = 'kasse-token'
const USER_COOKIE = 'kasse-user'

/** Secure so the token never travels over plain HTTP once deployed behind TLS (localhost is exempt in browsers). */
const cookieOpts = (maxAge: number) => ({
  sameSite: 'lax' as const,
  secure: import.meta.client && location.protocol === 'https:',
  maxAge,
})

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null as string | null,
    user: null as AuthUser | null,
  }),

  actions: {
    restoreFromCookie() {
      const t = useCookie<string | null>(TOKEN_COOKIE, { sameSite: 'lax' })
      const u = useCookie<AuthUser | null>(USER_COOKIE, { sameSite: 'lax' })
      this.token = t.value ?? null
      this.user = u.value ?? null
    },

    persistCookie() {
      const t = useCookie<string | null>(TOKEN_COOKIE, cookieOpts(60 * 60 * 24))
      const u = useCookie<AuthUser | null>(USER_COOKIE, cookieOpts(60 * 60 * 24))
      t.value = this.token
      u.value = this.user
    },

    async login(body: { role: 'VERKAUF' | 'ADMIN'; name: string; gruppe?: string; password: string }) {
      const api = useApi()
      const res = await api<LoginResponse>('/api/auth/login', { method: 'POST', body })
      this.token = res.token
      this.user = res.user
      this.persistCookie()
    },

    clear() {
      this.token = null
      this.user = null
      this.persistCookie()
    },

    async logout() {
      this.clear()
      await navigateTo('/login')
    },
  },
})

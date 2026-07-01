import { defineStore } from 'pinia'
import type { RegisterDto } from '~/types/api'

const REGISTER_COOKIE = 'kasse-register'

export const useRegisterStore = defineStore('register', {
  state: () => ({
    all: [] as RegisterDto[],
    selectedId: null as string | null,
  }),

  getters: {
    selected: (state) => state.all.find(r => r.id === state.selectedId) ?? null,
    active: (state) => state.all.filter(r => r.active),
  },

  actions: {
    restoreFromCookie() {
      const c = useCookie<string | null>(REGISTER_COOKIE, { sameSite: 'lax' })
      this.selectedId = c.value ?? null
    },

    select(id: string | null) {
      this.selectedId = id
      const c = useCookie<string | null>(REGISTER_COOKIE, {
        sameSite: 'lax',
        secure: import.meta.client && location.protocol === 'https:',
        maxAge: 60 * 60 * 24 * 30,
      })
      c.value = id
    },

    clear() {
      this.select(null)
    },

    async fetch() {
      const api = useApi()
      this.all = await api<RegisterDto[]>('/api/registers')
      return this.all
    },

    async create(name: string) {
      const api = useApi()
      const created = await api<RegisterDto>('/api/registers', { method: 'POST', body: { name } })
      this.all.push(created)
      this.all.sort((a, b) => a.sortOrder - b.sortOrder)
      return created
    },

    async patch(id: string, body: Partial<{ name: string; sortOrder: number; active: boolean }>) {
      const api = useApi()
      const updated = await api<RegisterDto>(`/api/registers/${id}`, { method: 'PATCH', body })
      const i = this.all.findIndex(r => r.id === id)
      if (i >= 0) this.all[i] = updated
      return updated
    },

    async remove(id: string) {
      const api = useApi()
      await api(`/api/registers/${id}`, { method: 'DELETE' })
      const i = this.all.findIndex(r => r.id === id)
      if (i >= 0) this.all[i] = { ...this.all[i]!, active: false }
    },
  },
})

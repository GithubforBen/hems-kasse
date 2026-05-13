import { defineStore } from 'pinia'

export const usePrefStore = defineStore('pref', {
  state: () => ({
    theme: 'default' as 'default' | 'farm',
  }),

  actions: {
    async fetch() {
      const api = useApi()
      const res = await api<{ theme: string }>('/api/me/pref')
      this.theme = (res.theme === 'farm' ? 'farm' : 'default')
    },

    async setTheme(theme: 'default' | 'farm') {
      this.theme = theme
      const api = useApi()
      await api('/api/me/pref', { method: 'PUT', body: { theme } })
    },

    toggle() {
      return this.setTheme(this.theme === 'farm' ? 'default' : 'farm')
    },
  },
})

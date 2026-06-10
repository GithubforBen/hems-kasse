import { defineStore } from 'pinia'
import type { StatsDto } from '~/types/api'

export const useStatsStore = defineStore('stats', {
  state: () => ({
    stats: null as StatsDto | null,
  }),

  actions: {
    async fetch(from?: string, to?: string) {
      const api = useApi()
      const query: Record<string, string> = {}
      if (from) query.from = from
      if (to) query.to = to
      this.stats = await api<StatsDto>('/api/stats', { query })
      return this.stats
    },
  },
})

import { defineStore } from 'pinia'
import type { ShiftDto, ShiftDetailDto } from '~/types/api'

export const useShiftStore = defineStore('shift', {
  state: () => ({
    current: null as ShiftDto | null,
    mine: [] as ShiftDto[],
    all: [] as ShiftDto[],
  }),

  actions: {
    async fetchCurrent() {
      const api = useApi()
      this.current = await api<ShiftDto>('/api/shifts/current')
      return this.current
    },

    async patchCurrent(body: Partial<{ openingCashCents: number; notes: string }>) {
      const api = useApi()
      this.current = await api<ShiftDto>('/api/shifts/current', { method: 'PATCH', body })
      return this.current
    },

    async close(countedCashCents: number, notes?: string) {
      const api = useApi()
      const closed = await api<ShiftDto>('/api/shifts/current/close', {
        method: 'POST',
        body: { countedCashCents, notes: notes ?? '' },
      })
      this.current = null
      // Newly closed shift gets prepended to "mine" if it's loaded.
      this.mine = [closed, ...this.mine.filter(s => s.id !== closed.id)]
      return closed
    },

    async fetchMine() {
      const api = useApi()
      this.mine = await api<ShiftDto[]>('/api/shifts/mine')
      return this.mine
    },

    async fetchAll(filters: { from?: string; to?: string; klasse?: string; registerId?: string; q?: string } = {}) {
      const api = useApi()
      this.all = await api<ShiftDto[]>('/api/shifts', { query: filters as any })
      return this.all
    },

    async fetchById(id: string) {
      const api = useApi()
      return api<ShiftDetailDto>(`/api/shifts/${id}`)
    },
  },
})

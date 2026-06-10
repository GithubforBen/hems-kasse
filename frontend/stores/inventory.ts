import { defineStore } from 'pinia'
import type { InventoryCountDto, StockIntakeDto, ExpectedStockDto } from '~/types/api'

export const useInventoryStore = defineStore('inventory', {
  state: () => ({
    counts: [] as InventoryCountDto[],
    intakes: [] as StockIntakeDto[],
    expected: [] as ExpectedStockDto[],
  }),

  actions: {
    async fetchCounts() {
      const api = useApi()
      this.counts = await api<InventoryCountDto[]>('/api/inventory/counts')
      return this.counts
    },

    async fetchCountById(id: string) {
      const api = useApi()
      return api<InventoryCountDto>(`/api/inventory/counts/${id}`)
    },

    async recordCount(lines: Array<{ productId: string; countedQty: number }>, notes?: string) {
      const api = useApi()
      const created = await api<InventoryCountDto>('/api/inventory/counts', {
        method: 'POST',
        body: { lines, notes: notes ?? '' },
      })
      this.counts = [created, ...this.counts]
      return created
    },

    async fetchIntakes() {
      const api = useApi()
      this.intakes = await api<StockIntakeDto[]>('/api/inventory/intakes')
      return this.intakes
    },

    async fetchIntakeById(id: string) {
      const api = useApi()
      return api<StockIntakeDto>(`/api/inventory/intakes/${id}`)
    },

    async recordIntake(lines: Array<{ productId: string; qty: number }>, notes?: string) {
      const api = useApi()
      const created = await api<StockIntakeDto>('/api/inventory/intakes', {
        method: 'POST',
        body: { lines, notes: notes ?? '' },
      })
      this.intakes = [created, ...this.intakes]
      return created
    },

    async fetchExpected() {
      const api = useApi()
      this.expected = await api<ExpectedStockDto[]>('/api/inventory/expected')
      return this.expected
    },
  },
})

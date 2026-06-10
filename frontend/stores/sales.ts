import { defineStore } from 'pinia'
import type { SaleDto, CartItem } from '~/types/api'

export const useSalesStore = defineStore('sales', {
  state: () => ({
    sales: [] as SaleDto[],
  }),

  getters: {
    cashCents: (s) => s.sales.filter(x => x.method === 'BAR').reduce((t, x) => t + x.totalCents, 0),
    cardCents: (s) => s.sales.filter(x => x.method === 'KARTE').reduce((t, x) => t + x.totalCents, 0),
    paypalCents: (s) => s.sales.filter(x => x.method === 'PAYPAL').reduce((t, x) => t + x.totalCents, 0),
    totalCents(): number { return this.cashCents + this.cardCents + this.paypalCents },
    itemsSold: (s) => s.sales.reduce((t, x) => t + x.items.reduce((q, i) => q + i.qty, 0), 0),
  },

  actions: {
    async fetch() {
      const api = useApi()
      this.sales = await api<SaleDto[]>('/api/sales')
    },

    async record(body: { method: 'BAR' | 'KARTE' | 'PAYPAL'; givenCents: number; items: CartItem[]; transactionRef?: string }) {
      const api = useApi()
      const sale = await api<SaleDto>('/api/sales', {
        method: 'POST',
        body: {
          method: body.method,
          givenCents: body.givenCents,
          items: body.items.map(it => ({
            productId: it.productId,
            qty: it.qty,
            ...(it.variable ? { priceCentsOverride: it.priceCents } : {}),
          })),
          transactionRef: body.transactionRef,
        },
      })
      this.sales.unshift(sale)
      return sale
    },

    clear() { this.sales = [] },
  },
})

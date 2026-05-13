import { defineStore } from 'pinia'
import type { CartItem, ProductDto } from '~/types/api'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [] as CartItem[],
  }),

  getters: {
    totalCents: (s) => s.items.reduce((t, x) => t + x.priceCents * x.qty, 0),
    totalQty: (s) => s.items.reduce((t, x) => t + x.qty, 0),
    qtyByProduct(): Record<string, number> {
      const m: Record<string, number> = {}
      for (const it of this.items) m[it.productId] = it.qty
      return m
    },
  },

  actions: {
    add(p: ProductDto) {
      const i = this.items.findIndex(x => x.productId === p.id)
      if (i >= 0) {
        this.items[i]!.qty += 1
      } else {
        this.items.push({ productId: p.id, name: p.name, priceCents: p.priceCents, color: p.color, qty: 1 })
      }
    },
    inc(id: string) {
      const it = this.items.find(x => x.productId === id)
      if (it) it.qty += 1
    },
    dec(id: string) {
      const i = this.items.findIndex(x => x.productId === id)
      if (i < 0) return
      const it = this.items[i]!
      if (it.qty > 1) it.qty -= 1
      else this.items.splice(i, 1)
    },
    remove(id: string) {
      this.items = this.items.filter(x => x.productId !== id)
    },
    clear() {
      this.items = []
    },
  },
})

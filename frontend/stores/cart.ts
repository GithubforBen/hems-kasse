import { defineStore } from 'pinia'
import type { CartItem, ProductDto } from '~/types/api'
import { applyDiscount, lineId } from '~/utils/discount'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [] as CartItem[],
  }),

  getters: {
    totalCents: (s) => s.items.reduce((t, x) => t + x.priceCents * x.qty, 0),
    totalQty: (s) => s.items.reduce((t, x) => t + x.qty, 0),
    /** Total saved by discounts across the whole cart (positive number). */
    savedCents: (s) => s.items.reduce((t, x) => t + (x.listPriceCents - x.priceCents) * x.qty, 0),
    qtyByProduct(): Record<string, number> {
      const m: Record<string, number> = {}
      for (const it of this.items) m[it.productId] = (m[it.productId] ?? 0) + it.qty
      return m
    },
  },

  actions: {
    add(p: ProductDto, priceCentsOverride?: number) {
      const base = (p.variable && priceCentsOverride !== undefined) ? priceCentsOverride : p.priceCents
      // Merge only undiscounted, non-variable lines — discounted/variable lines stay separate.
      if (!p.variable) {
        const i = this.items.findIndex(x => x.productId === p.id && x.discountPercent === 0)
        if (i >= 0) {
          this.items[i]!.qty += 1
          return
        }
      }
      this.items.push({
        lineId: lineId(),
        productId: p.id,
        name: p.name,
        priceCents: base,
        listPriceCents: base,
        color: p.color,
        qty: 1,
        variable: p.variable,
        discountable: p.discountable,
        minPriceCents: p.minPriceCents,
        discountPercent: 0,
      })
    },
    inc(lineId: string) {
      const it = this.items.find(x => x.lineId === lineId)
      if (it) it.qty += 1
    },
    dec(lineId: string) {
      const i = this.items.findIndex(x => x.lineId === lineId)
      if (i < 0) return
      const it = this.items[i]!
      if (it.qty > 1) it.qty -= 1
      else this.items.splice(i, 1)
    },
    remove(lineId: string) {
      this.items = this.items.filter(x => x.lineId !== lineId)
    },
    /** Apply (or clear, with percent 0) a discount to one cart line. Returns whether the floor capped it. */
    setDiscount(lineId: string, percent: number): boolean {
      const it = this.items.find(x => x.lineId === lineId)
      if (!it || !it.discountable) return false
      const r = applyDiscount(it.listPriceCents, percent, it.minPriceCents, it.discountable)
      it.discountPercent = percent
      it.priceCents = r.priceCents
      return r.capped
    },
    clear() {
      this.items = []
    },
  },
})

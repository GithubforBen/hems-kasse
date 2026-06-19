import { defineStore } from 'pinia'
import type { CategoryDto, ProductDto } from '~/types/api'

export const useCatalogStore = defineStore('catalog', {
  state: () => ({
    categories: [] as CategoryDto[],
    loaded: false,
  }),

  actions: {
    async fetch(force = false) {
      if (this.loaded && !force) return
      const api = useApi()
      this.categories = await api<CategoryDto[]>('/api/categories')
      this.loaded = true
    },

    async createCategory(body: { name: string; color: string }) {
      const api = useApi()
      const created = await api<CategoryDto>('/api/categories', { method: 'POST', body })
      this.categories.push(created)
      this.categories.sort((a, b) => a.sortOrder - b.sortOrder)
      return created
    },

    async patchCategory(id: string, body: Partial<{ name: string; color: string; sortOrder: number }>) {
      const api = useApi()
      const updated = await api<CategoryDto>(`/api/categories/${id}`, { method: 'PATCH', body })
      const i = this.categories.findIndex(c => c.id === id)
      if (i >= 0) this.categories[i] = updated
      this.categories.sort((a, b) => a.sortOrder - b.sortOrder)
      return updated
    },

    async deleteCategory(id: string) {
      const api = useApi()
      await api(`/api/categories/${id}`, { method: 'DELETE' })
      this.categories = this.categories.filter(c => c.id !== id)
    },

    async addProduct(catId: string, body: { name: string; priceCents: number; color: string; variable?: boolean; discountable?: boolean; minPriceCents?: number | null; plu?: string | null }) {
      const api = useApi()
      const created = await api<ProductDto>(`/api/categories/${catId}/products`, { method: 'POST', body })
      const cat = this.categories.find(c => c.id === catId)
      if (cat) cat.products.push(created)
      return created
    },

    async patchProduct(id: string, body: Partial<{ name: string; priceCents: number; color: string; sortOrder: number; categoryId: string; variable: boolean; discountable: boolean; minPriceCents: number | null; plu: string | null }>) {
      const api = useApi()
      const updated = await api<ProductDto>(`/api/products/${id}`, { method: 'PATCH', body })
      for (const c of this.categories) {
        const i = c.products.findIndex(p => p.id === id)
        if (i >= 0) {
          if (body.categoryId && body.categoryId !== c.id) {
            c.products.splice(i, 1)
            const target = this.categories.find(x => x.id === body.categoryId)
            if (target) {
              target.products.push(updated)
              target.products.sort((a, b) => a.sortOrder - b.sortOrder)
            }
          } else {
            c.products[i] = updated
            c.products.sort((a, b) => a.sortOrder - b.sortOrder)
          }
        }
      }
      return updated
    },

    async deleteProduct(id: string) {
      const api = useApi()
      await api(`/api/products/${id}`, { method: 'DELETE' })
      for (const c of this.categories) {
        c.products = c.products.filter(p => p.id !== id)
      }
    },

    async setComponents(productId: string, components: Array<{ componentProductId: string; qty: number }>) {
      const api = useApi()
      const updated = await api<ProductDto>(`/api/products/${productId}/components`, { method: 'PUT', body: { components } })
      for (const c of this.categories) {
        const i = c.products.findIndex(p => p.id === productId)
        if (i >= 0) c.products[i] = updated
      }
      return updated
    },
  },
})

import { defineStore } from 'pinia'
import type { AccountDto, Role, SlipDto } from '~/types/api'

/**
 * Gruppen and admin logins. Passwords are deliberately not kept in this store: they arrive
 * only from the endpoints that create or reset one, and from the slip endpoint, and are handed
 * straight to the component that prints them.
 */
export const useAccountsStore = defineStore('accounts', {
  state: () => ({
    all: [] as AccountDto[],
    loading: false,
  }),

  getters: {
    gruppen: (state) => state.all.filter(a => a.role === 'VERKAUF'),
    admins: (state) => state.all.filter(a => a.role === 'ADMIN'),
  },

  actions: {
    async fetch() {
      const api = useApi()
      this.loading = true
      try {
        this.all = await api<AccountDto[]>('/api/accounts')
      } finally {
        this.loading = false
      }
      return this.all
    },

    /** Creates an account; an empty password makes the server generate one. */
    async create(role: Role, name: string, password?: string): Promise<SlipDto> {
      const api = useApi()
      const slip = await api<SlipDto>('/api/accounts', {
        method: 'POST',
        body: { role, name, password: password || undefined },
      })
      await this.fetch()
      return slip
    },

    async patch(id: string, body: Partial<{ name: string; active: boolean }>) {
      const api = useApi()
      const updated = await api<AccountDto>(`/api/accounts/${id}`, { method: 'PATCH', body })
      const i = this.all.findIndex(a => a.id === id)
      if (i >= 0) this.all[i] = updated
      return updated
    },

    /** Sets a password, or generates one when none is given. Returns the new plaintext. */
    async setPassword(id: string, password?: string): Promise<SlipDto> {
      const api = useApi()
      const slip = await api<SlipDto>(`/api/accounts/${id}/password`, {
        method: 'POST',
        body: { password: password || undefined },
      })
      await this.fetch()
      return slip
    },

    async remove(id: string) {
      const api = useApi()
      await api(`/api/accounts/${id}`, { method: 'DELETE' })
      this.all = this.all.filter(a => a.id !== id)
    },

    /** Slip data for printing. Without ids, every account is returned. */
    async slips(ids?: string[]): Promise<SlipDto[]> {
      const api = useApi()
      return api<SlipDto[]>('/api/accounts/slips', {
        query: ids && ids.length ? { ids: ids.join(',') } : undefined,
      })
    },
  },
})

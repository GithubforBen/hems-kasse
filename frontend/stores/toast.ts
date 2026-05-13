import { defineStore } from 'pinia'

export const useToastStore = defineStore('toast', {
  state: () => ({
    message: null as string | null,
  }),
  actions: {
    show(msg: string, ms = 2200) {
      this.message = msg
      setTimeout(() => {
        if (this.message === msg) this.message = null
      }, ms)
    },
  },
})

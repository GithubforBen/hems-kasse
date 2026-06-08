import type { FetchOptions } from 'ofetch'

/**
 * Centralised $fetch wrapper.
 *
 * - Injects `Authorization: Bearer <token>` from the auth store on every request.
 * - On 401, clears auth and redirects to /login.
 * - Everything else surfaces as a normal thrown FetchError.
 */
export const useApi = () => {
  const { public: { apiBase } } = useRuntimeConfig()
  const auth = useAuthStore()
  const register = useRegisterStore()

  const api = $fetch.create({
    baseURL: apiBase,
    onRequest({ options }) {
      const token = auth.token
      const registerId = register.selectedId
      if (token || registerId) {
        const headers = new Headers(options.headers)
        if (token) headers.set('Authorization', `Bearer ${token}`)
        if (registerId) headers.set('X-Kasse-Register-Id', registerId)
        options.headers = headers
      }
    },
    async onResponseError({ response }) {
      if (response?.status === 401) {
        auth.clear()
        if (import.meta.client && !location.pathname.startsWith('/login')) {
          await navigateTo('/login')
        }
      }
    },
  })

  return <T = unknown>(url: string, opts: FetchOptions = {}) => api<T>(url, opts as any)
}

import type { FetchOptions } from 'ofetch'

/**
 * Centralised $fetch wrapper.
 *
 * - Injects `Authorization: Bearer <token>` from the auth store on every request.
 * - Reports network reachability to `useOnline()` so the offline banner reflects the
 *   API, not just `navigator.onLine`.
 * - On 401, clears auth and redirects to /login.
 * - On 409 from the current-shift endpoints, ends the session the same way: the token names an
 *   Abrechnung that can no longer be booked into (it was closed, or the envelope is running at
 *   another Kassette). This is what a tab reloaded after the Abrechnung was closed hits, so it
 *   has to land on the login form instead of a half-loaded till.
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
    onRequestError() {
      // The request never reached the server (no network, DNS, CORS preflight …).
      markServerUnreachable()
    },
    onResponse() {
      // A response came back — the server is alive, 4xx/5xx included.
      markServerReachable()
    },
    async onResponseError({ request, response }) {
      if (response?.status === 401) {
        auth.clear()
        if (import.meta.client && !location.pathname.startsWith('/login')) {
          await navigateTo('/login')
        }
        return
      }
      if (response?.status === 409 && isCurrentShiftRequest(request)) {
        auth.clear()
        if (import.meta.client && !location.pathname.startsWith('/login')) {
          await navigateTo({ path: '/login', query: { reason: 'abrechnung' } })
        }
      }
    },
  })

  return <T = unknown>(url: string, opts: FetchOptions = {}) => api<T>(url, opts as any)
}

/** True for /api/shifts/current and its sub-routes — the endpoints tied to the open Abrechnung. */
function isCurrentShiftRequest(request: unknown): boolean {
  const url = typeof request === 'string' ? request : (request as Request | undefined)?.url ?? ''
  return url.includes('/api/shifts/current')
}

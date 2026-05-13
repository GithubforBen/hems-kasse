/**
 * Global auth gate. Anything that isn't /login requires a token.
 * Admin-only routes additionally check the role.
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuthStore()

  // Restore token from cookie on first navigation (SSR is off, but cookie may exist from a prior session).
  if (!auth.token && import.meta.client) {
    auth.restoreFromCookie()
  }

  if (to.path.startsWith('/login')) {
    return
  }

  if (!auth.token || !auth.user) {
    return navigateTo({ path: '/login', query: { next: to.fullPath } })
  }

  if (to.path.startsWith('/admin') && auth.user.role !== 'ADMIN') {
    return navigateTo('/')
  }
})

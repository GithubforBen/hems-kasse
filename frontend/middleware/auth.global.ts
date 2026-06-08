/**
 * Global auth gate. Anything that isn't /login requires a token.
 * Admin-only routes additionally check the role.
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuthStore()
  const register = useRegisterStore()

  // Restore token/register from cookie on first navigation (SSR is off, but cookies may exist from a prior session).
  if (!auth.token && import.meta.client) {
    auth.restoreFromCookie()
  }
  if (register.selectedId === null && import.meta.client) {
    register.restoreFromCookie()
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

  // VERKAUF devices must pick a Kassette (own independent shift) before using the till.
  if (auth.user.role === 'VERKAUF' && !to.path.startsWith('/kassette') && !register.selectedId) {
    return navigateTo({ path: '/kassette', query: { next: to.fullPath } })
  }
})

/**
 * One-time PWA wiring on startup: service worker registration plus install and
 * update detection. The site itself keeps working unchanged if any of it fails.
 */
export default defineNuxtPlugin(() => {
  setupPwa()
})

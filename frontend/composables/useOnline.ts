/**
 * Connectivity state of the till.
 *
 * Two signals, because they describe different failures:
 *
 * - `networkOnline` — what the browser reports (`navigator.onLine`). Only says whether
 *   a network interface is up. A tablet on school wifi that has no uplink still
 *   reports "online" here.
 * - `serverReachable` — whether the last API call actually reached the backend.
 *   Maintained by `useApi()` via `markServerReachable`/`markServerUnreachable`.
 *
 * For display, `offline` is what matters — it folds both together.
 */
const networkOnline = ref(true)
const serverReachable = ref(true)
let listening = false

function startListening() {
  if (listening || !import.meta.client) return
  listening = true
  networkOnline.value = navigator.onLine
  window.addEventListener('online', () => {
    networkOnline.value = true
    // Give the server a fresh chance — the next call decides.
    serverReachable.value = true
  })
  window.addEventListener('offline', () => { networkOnline.value = false })
}

/** Called by `useApi()` once a response arrives (a 4xx/5xx counts — the server is alive). */
export function markServerReachable() {
  serverReachable.value = true
}

/** Called by `useApi()` when the request never made it onto the wire. */
export function markServerUnreachable() {
  serverReachable.value = false
}

export const useOnline = () => {
  startListening()
  return {
    networkOnline: readonly(networkOnline),
    serverReachable: readonly(serverReachable),
    /** True as soon as either signal drops. */
    offline: computed(() => !networkOnline.value || !serverReachable.value),
  }
}

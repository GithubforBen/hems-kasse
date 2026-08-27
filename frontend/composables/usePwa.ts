/**
 * PWA state: installation and updates.
 *
 * State deliberately lives in module scope — the app runs with `ssr: false`, so there
 * is exactly one client per module instance. TopBar, login page and the banners all
 * share the same values.
 *
 * Everything is wired up once in `plugins/pwa.client.ts`.
 */

/** Chromium-only event that lets us trigger the install dialog ourselves. */
interface BeforeInstallPromptEvent extends Event {
  readonly platforms: string[]
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>
}

const DISMISS_KEY = 'kasse.pwa.install-dismissed'
/** How long a "später" holds before we ask again. */
const DISMISS_DAYS = 14

const installEvent = ref<BeforeInstallPromptEvent | null>(null)
const installed = ref(false)
const updateReady = ref(false)
const registration = ref<ServiceWorkerRegistration | null>(null)
const dismissed = ref(false)
const standalone = ref(false)

let reloading = false
let setupDone = false

function isStandaloneNow(): boolean {
  if (!import.meta.client) return false
  return window.matchMedia('(display-mode: standalone)').matches
    || window.matchMedia('(display-mode: window-controls-overlay)').matches
    || window.matchMedia('(display-mode: minimal-ui)').matches
    // iOS doesn't report display-mode reliably but does set navigator.standalone.
    || (navigator as any).standalone === true
}

function readDismissed(): boolean {
  try {
    const raw = localStorage.getItem(DISMISS_KEY)
    if (!raw) return false
    const until = Number(raw)
    return Number.isFinite(until) && Date.now() < until
  } catch {
    return false
  }
}

/** One-time setup — registers the service worker and attaches the listeners. */
export function setupPwa() {
  if (setupDone || !import.meta.client) return
  setupDone = true

  standalone.value = isStandaloneNow()
  dismissed.value = readDismissed()

  window.matchMedia('(display-mode: standalone)')
    .addEventListener?.('change', () => { standalone.value = isStandaloneNow() })

  window.addEventListener('beforeinstallprompt', (e) => {
    // Suppress the browser's own mini-infobar; we ask in our own banner.
    e.preventDefault()
    installEvent.value = e as BeforeInstallPromptEvent
  })

  window.addEventListener('appinstalled', () => {
    installed.value = true
    installEvent.value = null
  })

  if (!('serviceWorker' in navigator)) return

  if (import.meta.dev) {
    // On the dev server a worker left over from a production build would serve stale
    // assets and defeat HMR. Unregister instead of registering.
    navigator.serviceWorker.getRegistrations()
      .then((regs) => regs.forEach((r) => r.unregister()))
      .catch(() => {})
    return
  }

  // Register once the page is idle — but plugins can run *after* `load` has already
  // fired, in which case the listener would never see it. Check the state first.
  if (document.readyState === 'complete') {
    void registerWorker()
  } else {
    window.addEventListener('load', () => { void registerWorker() }, { once: true })
  }
}

async function registerWorker() {
  // Captured before registering: on a first-ever visit there is no controller yet, and
  // the worker's `clients.claim()` will fire `controllerchange` for this very page.
  // That is an install, not an update — reloading there would throw away whatever the
  // cashier has already typed.
  const hadController = !!navigator.serviceWorker.controller
  try {
    const reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' })
    registration.value = reg

    // A new version is already waiting (e.g. the tab sat open for hours).
    if (reg.waiting && navigator.serviceWorker.controller) updateReady.value = true

    reg.addEventListener('updatefound', () => {
      const sw = reg.installing
      if (!sw) return
      sw.addEventListener('statechange', () => {
        // `controller` separates a first install (no update notice needed) from a
        // genuine update on top of an already running version.
        if (sw.state === 'installed' && navigator.serviceWorker.controller) {
          updateReady.value = true
        }
      })
    })

    navigator.serviceWorker.addEventListener('controllerchange', () => {
      // Only an update swap reloads — see `hadController` above.
      if (!hadController || reloading) return
      reloading = true
      window.location.reload()
    })

    // Check for updates when the tab comes back — a till runs for hours without
    // anyone reloading it.
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') void reg.update().catch(() => {})
    })
    setInterval(() => { void reg.update().catch(() => {}) }, 30 * 60 * 1000)
  } catch {
    // No worker (insecure context, for instance) — the site works regardless.
  }
}

export const usePwa = () => {
  const isIos = computed(() =>
    import.meta.client && /iphone|ipad|ipod/i.test(navigator.userAgent),
  )

  /** iOS has no `beforeinstallprompt` — there only the instructions help. */
  const canPromptInstall = computed(() => installEvent.value !== null)
  const showInstallHint = computed(() =>
    !standalone.value && !installed.value && !dismissed.value
    && (canPromptInstall.value || isIos.value),
  )

  /** Opens the install dialog. Resolves to whether the app was installed. */
  async function promptInstall(): Promise<boolean> {
    const e = installEvent.value
    if (!e) return false
    await e.prompt()
    const { outcome } = await e.userChoice
    // The event is spent after `prompt()`; the browser sends a new one if needed.
    installEvent.value = null
    if (outcome === 'accepted') installed.value = true
    return outcome === 'accepted'
  }

  /** "Später" — stay quiet for DISMISS_DAYS days. */
  function dismissInstall() {
    dismissed.value = true
    try {
      localStorage.setItem(DISMISS_KEY, String(Date.now() + DISMISS_DAYS * 864e5))
    } catch {}
  }

  /**
   * Activate the waiting version. The reload happens via `controllerchange` once the
   * new worker takes over.
   */
  function applyUpdate() {
    const waiting = registration.value?.waiting
    if (!waiting) {
      window.location.reload()
      return
    }
    updateReady.value = false
    waiting.postMessage({ type: 'SKIP_WAITING' })
  }

  return {
    standalone: readonly(standalone),
    installed: readonly(installed),
    updateReady: readonly(updateReady),
    isIos,
    canPromptInstall,
    showInstallHint,
    promptInstall,
    dismissInstall,
    applyUpdate,
  }
}

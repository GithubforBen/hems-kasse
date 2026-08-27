/* ============================================================
   Service Worker — Schulkasse
   ============================================================

   Keeps the till usable as an installed app even when the wifi in the hall drops
   for a moment. The worker caches the *interface* only (app shell, build assets,
   fonts). Till data deliberately never goes through the cache — a sale must reach
   the server or fail visibly, never be "confirmed" out of a stale cache.

   Strategies:
     navigation (HTML)   network-first → cached app shell → /offline.html
     /_nuxt/* (hashed)   cache-first   (the filename carries the hash)
     icons / manifest    stale-while-revalidate
     Google Fonts        stale-while-revalidate
     everything else     passed through (no respondWith)

   Bump VERSION on every deploy: `activate` then drops the old caches and clients
   are offered the new version through the update banner.
   ============================================================ */

const VERSION = 'v1'
const SHELL_CACHE = `kasse-shell-${VERSION}`
const ASSET_CACHE = `kasse-assets-${VERSION}`
const FONT_CACHE = `kasse-fonts-${VERSION}`
const CURRENT_CACHES = [SHELL_CACHE, ASSET_CACHE, FONT_CACHE]

/** SPA entry point — this HTML boots every route client-side. */
const APP_SHELL = '/'
const OFFLINE_PAGE = '/offline.html'

/** Precached on install: everything that must be there instantly without a network. */
const PRECACHE = [
  OFFLINE_PAGE,
  '/manifest.webmanifest',
  '/icon.svg',
  '/icon-192.png',
  '/icon-512.png',
  '/apple-touch-icon.png',
]

const FONT_HOSTS = ['fonts.googleapis.com', 'fonts.gstatic.com']

// ------------------------------------------------------------------
// Lifecycle
// ------------------------------------------------------------------

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL_CACHE)
    // One by one rather than addAll: a single missing file must not fail the install.
    await Promise.all(PRECACHE.map((url) => cache.add(url).catch(() => {})))
    await cache.add(APP_SHELL).catch(() => {})
    await precacheEntryAssets(cache)
    // No skipWaiting() — the new version waits until the user accepts it in the
    // update banner. Nothing should reload in the middle of a sale.
  })())
})

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    if (self.registration.navigationPreload) {
      await self.registration.navigationPreload.enable().catch(() => {})
    }
    const names = await caches.keys()
    await Promise.all(
      names
        .filter((n) => n.startsWith('kasse-') && !CURRENT_CACHES.includes(n))
        .map((n) => caches.delete(n)),
    )
    await self.clients.claim()
  })())
})

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting()
})

/**
 * Pull the entry bundle out of the cached shell HTML and precache it.
 *
 * Without this the very first visit has nothing in ASSET_CACHE: the browser loads
 * /_nuxt/* before the worker claims the page, so those requests never pass through
 * here. The app would then boot offline only as long as the HTTP cache still holds
 * them. The shell references exactly the entry CSS and entry JS; lazy route chunks
 * are cached later, as they are requested.
 */
async function precacheEntryAssets(shellCache) {
  try {
    const res = await shellCache.match(APP_SHELL)
    if (!res) return
    const html = await res.text()
    const urls = [...new Set(html.match(/\/_nuxt\/[A-Za-z0-9._-]+\.(?:js|mjs|css)/g) || [])]
    if (!urls.length) return
    const assets = await caches.open(ASSET_CACHE)
    await Promise.all(urls.map((u) =>
      assets.add(new Request(u, { credentials: 'same-origin' })).catch(() => {}),
    ))
  } catch {
    // Best effort — a failed precache must never block the install.
  }
}

// ------------------------------------------------------------------
// Fetch
// ------------------------------------------------------------------

self.addEventListener('fetch', (event) => {
  const req = event.request
  const url = new URL(req.url)

  // GET only. POST/PUT/DELETE (sales, login, shift close) always go straight to the network.
  if (req.method !== 'GET') return
  // Leave range requests (media) alone — partial responses do not belong in a cache.
  if (req.headers.has('range')) return

  if (FONT_HOSTS.includes(url.hostname)) {
    event.respondWith(staleWhileRevalidate(req, FONT_CACHE))
    return
  }

  // Pass through foreign origins — above all the REST API at NUXT_PUBLIC_API_BASE.
  if (url.origin !== self.location.origin) return
  // In case the API is ever mounted on the same origin: never cache it either.
  if (url.pathname.startsWith('/api/')) return

  if (req.mode === 'navigate') {
    event.respondWith(handleNavigation(event))
    return
  }

  // Hashed build assets are immutable → cache-first.
  if (url.pathname.startsWith('/_nuxt/')) {
    event.respondWith(cacheFirst(req, ASSET_CACHE))
    return
  }

  if (isStaticAsset(url.pathname)) {
    event.respondWith(staleWhileRevalidate(req, SHELL_CACHE))
  }
})

// ------------------------------------------------------------------
// Strategies
// ------------------------------------------------------------------

/**
 * Navigation: network first, so a deploy takes effect immediately; otherwise the
 * cached app shell. The SPA then routes client-side, so a cached "/" also serves
 * /abschluss or /lager.
 */
async function handleNavigation(event) {
  const cache = await caches.open(SHELL_CACHE)
  try {
    const preloaded = await event.preloadResponse
    const res = preloaded || await fetch(event.request)
    if (res && res.ok) cache.put(APP_SHELL, res.clone())
    return res
  } catch {
    return (await cache.match(APP_SHELL))
      || (await cache.match(OFFLINE_PAGE))
      || Response.error()
  }
}

async function cacheFirst(req, cacheName) {
  const cache = await caches.open(cacheName)
  const hit = await cache.match(req)
  if (hit) return hit
  const res = await fetch(req)
  if (res && res.ok) cache.put(req, res.clone())
  return res
}

async function staleWhileRevalidate(req, cacheName) {
  const cache = await caches.open(cacheName)
  const hit = await cache.match(req)
  const network = fetch(req)
    .then((res) => {
      // `opaque` (no-cors, e.g. font files) has status 0 and is still usable.
      if (res && (res.ok || res.type === 'opaque')) cache.put(req, res.clone())
      return res
    })
    .catch(() => undefined)
  return hit || (await network) || Response.error()
}

function isStaticAsset(pathname) {
  return /\.(?:css|js|mjs|png|jpe?g|svg|gif|webp|avif|ico|woff2?|ttf|otf|webmanifest)$/i.test(pathname)
}

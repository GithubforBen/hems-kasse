// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  devtools: { enabled: false },
  modules: ['@pinia/nuxt'],
  ssr: false, // single-page app — auth state lives in the browser

  app: {
    head: {
      title: 'Schulkasse · Kuchenverkauf',
      htmlAttrs: { lang: 'de' },
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover' },
        { name: 'mobile-web-app-capable', content: 'yes' },
        { name: 'apple-mobile-web-app-capable', content: 'yes' },
        { name: 'apple-mobile-web-app-status-bar-style', content: 'default' },
        { name: 'apple-mobile-web-app-title', content: 'Schulkasse' },
        { name: 'theme-color', content: '#f3efe7' },
        { name: 'application-name', content: 'Schulkasse' },
        { name: 'description', content: 'Kassensystem für den Kuchenverkauf' },
      ],
      link: [
        { rel: 'manifest', href: '/manifest.webmanifest' },
        // Safari ignores SVG here, so the home-screen icon has to be a PNG.
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },
        { rel: 'icon', type: 'image/svg+xml', href: '/icon.svg' },
        { rel: 'icon', type: 'image/png', sizes: '32x32', href: '/favicon-32.png' },
      ],
    },
  },

  css: [
    '~/assets/css/base.css',
    '~/assets/css/shell.css',
    '~/assets/css/pos.css',
    '~/assets/css/cart.css',
    '~/assets/css/pay.css',
    '~/assets/css/report.css',
    '~/assets/css/admin.css',
    '~/assets/css/mobile.css',
    '~/assets/css/pwa.css',
  ],

  // The backend already sets these on API responses; mirror them on the app shell
  // so the till UI can't be framed (clickjacking) or MIME-sniffed.
  routeRules: {
    '/**': {
      headers: {
        'X-Frame-Options': 'DENY',
        'X-Content-Type-Options': 'nosniff',
        'Referrer-Policy': 'no-referrer',
      },
    },
    // The worker must never be served from cache, otherwise a deploy can't reach
    // devices that already have the old one. Scope header lets it control '/'.
    '/sw.js': {
      headers: {
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'Service-Worker-Allowed': '/',
      },
    },
    '/manifest.webmanifest': {
      headers: { 'Cache-Control': 'no-cache' },
    },
  },

  runtimeConfig: {
    public: {
      // Override at runtime via NUXT_PUBLIC_API_BASE
      apiBase: 'http://localhost:8080',
    },
  },

  typescript: {
    strict: true,
    shim: false,
  },
})

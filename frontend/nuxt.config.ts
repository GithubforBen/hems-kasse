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
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
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
  ],

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

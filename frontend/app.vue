<script setup lang="ts">
const auth = useAuthStore()
const pref = usePrefStore()

// Sync body[data-theme] with the persisted theme.
watch(() => pref.theme, (t) => {
  if (!import.meta.client) return
  if (t === 'farm') document.body.setAttribute('data-theme', 'farm')
  else document.body.removeAttribute('data-theme')
}, { immediate: true })

// On boot: pull token from cookie; if logged in, fetch the user's persisted theme.
if (import.meta.client) {
  auth.restoreFromCookie()
  if (auth.token) {
    pref.fetch().catch(() => {})
  }
}
</script>

<template>
  <NuxtPage />
  <Toast />
</template>

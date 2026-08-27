<script setup lang="ts">
const auth = useAuthStore()
const register = useRegisterStore()

if (import.meta.client) {
  auth.restoreFromCookie()
  register.restoreFromCookie()
}
</script>

<template>
  <NuxtPage />
  <Toast />

  <!-- PWA notices: one stack for all three so they never overlap. -->
  <div class="pwa-stack">
    <PwaOfflineBanner />
    <PwaUpdateBanner />
    <PwaInstallBanner />
  </div>
</template>

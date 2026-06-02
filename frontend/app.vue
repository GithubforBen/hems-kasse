<script setup lang="ts">
const auth = useAuthStore()

if (import.meta.client) {
  auth.restoreFromCookie()
}
</script>

<template>
  <NuxtPage />
  <Toast />
</template>

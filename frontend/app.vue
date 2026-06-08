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
</template>

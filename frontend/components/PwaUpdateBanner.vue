<script setup lang="ts">
/**
 * Offered when the service worker has a newer version waiting. Never reloads on its
 * own — an unsent cart would be lost, so the decision stays with the user.
 */
const { updateReady, applyUpdate } = usePwa()
const snoozed = ref(false)

// A later update makes the banner relevant again.
watch(updateReady, (ready) => { if (ready) snoozed.value = false })
</script>

<template>
  <div v-if="updateReady && !snoozed" class="pwa-card" role="status">
    <div class="pwa-head">
      <span class="pwa-ico" aria-hidden="true">↻</span>
      <strong>Neue Version verfügbar</strong>
    </div>
    <p class="pwa-text">
      Neu laden übernimmt sie. Ein offener Warenkorb geht dabei verloren.
    </p>
    <div class="pwa-actions">
      <button class="btn pwa-btn" @click="applyUpdate">Neu laden</button>
      <button class="btn ghost pwa-btn" @click="snoozed = true">Später</button>
    </div>
  </div>
</template>

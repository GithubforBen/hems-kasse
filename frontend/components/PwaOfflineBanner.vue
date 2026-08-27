<script setup lang="ts">
/**
 * Connection warning. Sits above the modals on purpose: losing the network during a
 * payment is exactly when this has to stay visible.
 */
const { offline, networkOnline } = useOnline()
</script>

<template>
  <div v-if="offline" class="pwa-card bad" role="status" aria-live="polite">
    <div class="pwa-head">
      <span class="pwa-ico" aria-hidden="true">⚠</span>
      <strong>{{ networkOnline ? 'Server nicht erreichbar' : 'Keine Verbindung' }}</strong>
    </div>
    <p class="pwa-text">
      {{ networkOnline
        ? 'Die Kasse erreicht den Server gerade nicht. Buchungen kommen erst wieder an, sobald er antwortet.'
        : 'Das Gerät ist offline. Verkäufe lassen sich erst buchen, wenn die Verbindung zurück ist.' }}
    </p>
  </div>
</template>

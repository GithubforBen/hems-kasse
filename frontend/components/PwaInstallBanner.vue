<script setup lang="ts">
/**
 * Invitation to install. On Chromium/Android this opens the real install dialog; on
 * iOS, where no such API exists, it shows the "Zum Home-Bildschirm" steps instead.
 */
const { showInstallHint, canPromptInstall, isIos, promptInstall, dismissInstall } = usePwa()
const toast = useToastStore()

async function install() {
  const accepted = await promptInstall()
  if (accepted) toast.show('Kasse wurde installiert')
}
</script>

<template>
  <div v-if="showInstallHint" class="pwa-card">
    <div class="pwa-head">
      <span class="pwa-ico" aria-hidden="true">📲</span>
      <strong>Kasse installieren</strong>
    </div>

    <template v-if="canPromptInstall">
      <p class="pwa-text">
        Als App auf dem Startbildschirm — im Vollbild, ohne Adressleiste, mit eigenem Symbol.
      </p>
      <div class="pwa-actions">
        <button class="btn pwa-btn" @click="install">Installieren</button>
        <button class="btn ghost pwa-btn" @click="dismissInstall">Später</button>
      </div>
    </template>

    <template v-else-if="isIos">
      <p class="pwa-text">
        In Safari auf <strong>Teilen</strong> tippen und
        <strong>„Zum Home-Bildschirm“</strong> wählen.
      </p>
      <div class="pwa-actions">
        <button class="btn pwa-btn" @click="dismissInstall">Verstanden</button>
      </div>
    </template>
  </div>
</template>

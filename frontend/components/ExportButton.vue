<script setup lang="ts">
/**
 * Single CSV export button for domains that don't fit ExportButtons' shift-shaped
 * report types (Kassetten, Lager, Katalog) — same run/busy/toast pattern and styling.
 */
const props = withDefaults(defineProps<{
  /** Full endpoint path, e.g. /api/registers/export.csv */
  path: string
  /** Optional query params (e.g. { type: 'counts' }) forwarded to the backend. */
  query?: Record<string, any>
  label: string
  icon?: string
  hint?: string
}>(), {
  query: () => ({}),
  icon: '⬇️',
})

const toast = useToastStore()
const download = useDownload()
const busy = ref(false)

async function run() {
  if (busy.value) return
  busy.value = true
  try {
    await download(props.path, props.query)
  } catch (e: any) {
    toast.show(`Export fehlgeschlagen: ${e?.statusMessage ?? e?.message ?? 'Unbekannter Fehler'}`)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <button class="btn ghost export-btn" :disabled="busy" :title="hint" @click="run">
    <span class="ic" aria-hidden="true">{{ icon }}</span>
    <span>{{ label }}</span>
    <span v-if="busy" class="dots">…</span>
  </button>
</template>

<style scoped>
.export-btn {
  padding: 6px 10px;
  font-size: 12.5px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.export-btn .ic { font-size: 13px; }
.export-btn .dots { color: var(--ink-3); }
</style>

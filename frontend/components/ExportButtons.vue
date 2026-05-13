<script setup lang="ts">
/**
 * A row of "CSV exportieren" buttons.
 *
 * Each button kicks off a download of the matching report type from the given path
 * (which must be the *base* path — `type=…` is appended). Filters are passed through.
 *
 * Pass exactly the report types that make sense for the scope:
 *   single shift  →  ['items', 'sales', 'products']         (shifts.csv would be a 1-row file)
 *   mine / all    →  ['shifts', 'items', 'sales', 'products']
 */
type ReportType = 'shifts' | 'sales' | 'items' | 'products'

const props = withDefaults(defineProps<{
  /** Endpoint base path, e.g. /api/shifts/{id}/export.csv or /api/shifts/mine/export.csv */
  path: string
  /** Which report types to offer. */
  types?: ReportType[]
  /** Optional query params (date range, klasse, q) forwarded to the backend. */
  filters?: Record<string, any>
  /** Optional smaller layout for inline placement. */
  compact?: boolean
}>(), {
  types: () => ['shifts', 'items', 'products', 'sales'] as ReportType[],
  filters: () => ({}),
  compact: false,
})

const labels: Record<ReportType, { icon: string; text: string; hint: string }> = {
  shifts:   { icon: '📋', text: 'Schichten',   hint: 'Eine Zeile pro Schicht (Umsatz, Soll/Ist, Diff)' },
  sales:    { icon: '🧾', text: 'Bons',        hint: 'Eine Zeile pro Verkauf (Summe, Zahlart, Artikel)' },
  items:    { icon: '🔢', text: 'Artikel',     hint: 'Eine Zeile pro Kassenposition (am detailliertesten)' },
  products: { icon: '🥇', text: 'Produktstats',hint: 'Aggregat: Menge, Umsatz, Anteil je Produkt' },
}

const toast = useToastStore()
const download = useDownload()
const busy = ref<ReportType | null>(null)

async function run(t: ReportType) {
  if (busy.value) return
  busy.value = t
  try {
    await download(props.path, { ...props.filters, type: t })
  } catch (e: any) {
    toast.show(`Export fehlgeschlagen: ${e?.statusMessage ?? e?.message ?? 'Unbekannter Fehler'}`)
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div class="export-row" :class="{ compact }">
    <span class="export-label">CSV exportieren:</span>
    <button
      v-for="t in types"
      :key="t"
      class="btn ghost export-btn"
      :disabled="busy !== null"
      :title="labels[t].hint"
      @click="run(t)">
      <span class="ic" aria-hidden="true">{{ labels[t].icon }}</span>
      <span>{{ labels[t].text }}</span>
      <span v-if="busy === t" class="dots">…</span>
    </button>
  </div>
</template>

<style scoped>
.export-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 6px 0 0;
}
.export-row.compact { margin: 0; }
.export-label {
  font-size: 11.5px;
  text-transform: uppercase;
  letter-spacing: .04em;
  color: var(--ink-3);
  margin-right: 4px;
  font-weight: 600;
}
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

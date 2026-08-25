<script setup lang="ts">
import { formatEUR } from '~/utils/format'
import type { ShiftDto } from '~/types/api'

const props = defineProps<{
  shifts: ShiftDto[]
  showOperator?: boolean // include the Name + Gruppe columns (Admin "all" view)
  emptyText?: string
}>()

function fmtDate(iso: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

function diffClass(d: number | null | undefined): string {
  if (d == null || d === 0) return 'zero'
  return d > 0 ? 'ok' : 'bad'
}
</script>

<template>
  <div>
    <div v-if="shifts.length === 0" class="report-empty">
      {{ emptyText ?? 'Noch keine archivierten Schichten.' }}
    </div>
    <div v-else class="table-scroll">
    <table class="history-table">
      <thead>
        <tr>
          <th>Abschluss</th>
          <th v-if="showOperator">Wer</th>
          <th>Abrechnung</th>
          <th class="num">Umsatz</th>
          <th class="num">Bar</th>
          <th class="num">Karte</th>
          <th class="num">Diff</th>
          <th class="num">Bons</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in shifts" :key="s.id">
          <td>{{ fmtDate(s.closedAt) }}</td>
          <td v-if="showOperator">
            <span style="font-weight:550">{{ s.userName }}</span>
            <span v-if="s.gruppe" style="color:var(--ink-3);font-size:12px;margin-left:6px">{{ s.gruppe }}</span>
            <span v-if="s.registerName" style="color:var(--ink-3);font-size:12px;margin-left:6px">· {{ s.registerName }}</span>
          </td>
          <td>
            <span v-if="s.abrechnungNr != null" class="abrechnung-badge">#{{ s.abrechnungNr }}</span>
            <span v-else style="color:var(--ink-3)">–</span>
          </td>
          <td class="num">{{ formatEUR(s.totalSalesCents ?? 0) }}</td>
          <td class="num">{{ formatEUR(s.cashSalesCents ?? 0) }}</td>
          <td class="num">{{ formatEUR(s.cardSalesCents ?? 0) }}</td>
          <td class="num diff" :class="diffClass(s.diffCents)">
            {{ (s.diffCents ?? 0) > 0 ? '+' : '' }}{{ formatEUR(s.diffCents ?? 0) }}
          </td>
          <td class="num">{{ s.salesCount ?? 0 }}</td>
          <td>
            <NuxtLink :to="`/schicht/${s.id}`" class="btn ghost" style="padding:4px 10px;font-size:12.5px">
              Details →
            </NuxtLink>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
  </div>
</template>

<style scoped>
.history-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.history-table th,
.history-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--line); }
.history-table th { font-size: 11.5px; color: var(--ink-3); font-weight: 600; letter-spacing: .04em; text-transform: uppercase; }
.history-table td.num,
.history-table th.num { text-align: right; font-variant-numeric: tabular-nums; font-family: var(--font-num); }
.history-table td.diff.ok { color: var(--ok); font-weight: 600; }
.history-table td.diff.bad { color: var(--bad); font-weight: 600; }
.history-table td.diff.zero { color: var(--ink-2); }
.history-table tbody tr:hover { background: var(--paper-2, var(--paper)); }
</style>

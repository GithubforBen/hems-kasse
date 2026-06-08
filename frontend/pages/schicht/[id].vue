<script setup lang="ts">
import { formatEUR, centsToEuroString } from '~/utils/format'
import type { ShiftDetailDto } from '~/types/api'

const route = useRoute()
const shift = useShiftStore()

const data = ref<ShiftDetailDto | null>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    data.value = await shift.fetchById(String(route.params.id))
  } catch (e: any) {
    error.value = e?.response?.status === 403
      ? 'Diese Schicht gehört einer anderen Person.'
      : 'Schicht nicht gefunden.'
  }
})

function fmt(iso: string | null) {
  return iso ? new Date(iso).toLocaleString('de-DE') : '–'
}

/** Aggregate per-product: same data the products.csv export contains, for an at-a-glance card. */
const productStats = computed(() => {
  if (!data.value) return { rows: [], totalQty: 0, totalRev: 0 }
  const m: Record<string, { qty: number; rev: number; color: string }> = {}
  let totalQty = 0
  let totalRev = 0
  for (const s of data.value.sales) {
    for (const it of s.items) {
      const e = m[it.name] ??= { qty: 0, rev: 0, color: it.color }
      e.qty += it.qty
      e.rev += it.qty * it.priceCents
      totalQty += it.qty
      totalRev += it.qty * it.priceCents
    }
  }
  const rows = Object.entries(m)
    .map(([name, v]) => ({ name, ...v }))
    .sort((a, b) => b.rev - a.rev || b.qty - a.qty || a.name.localeCompare(b.name))
  return { rows, totalQty, totalRev }
})
const productMaxQty = computed(() => productStats.value.rows[0]?.qty ?? 1)

const exportPath = computed(() => `/api/shifts/${route.params.id}/export.csv`)
</script>

<template>
  <div class="app">
    <TopBar />

    <div class="scroll-y" style="flex:1">
      <div class="report" v-if="data">
        <div style="display:flex;justify-content:flex-end;margin:-4px 0 10px">
          <ExportButtons :path="exportPath" :types="['items', 'products', 'sales']" />
        </div>
        <div class="stat-grid">
          <div class="stat">
            <div class="l">Umsatz</div>
            <div class="v">{{ formatEUR(data.shift.totalSalesCents ?? 0) }}</div>
            <div class="s">{{ data.shift.salesCount ?? 0 }} Bons · {{ data.shift.itemsSold ?? 0 }} Artikel</div>
          </div>
          <div class="stat">
            <div class="l">Bar</div>
            <div class="v">{{ formatEUR(data.shift.cashSalesCents ?? 0) }}</div>
            <div class="s">Anfangsbestand {{ formatEUR(data.shift.openingCashCents) }}</div>
          </div>
          <div class="stat">
            <div class="l">Karte</div>
            <div class="v">{{ formatEUR(data.shift.cardSalesCents ?? 0) }}</div>
            <div class="s">SEPA via QR</div>
          </div>
          <div class="stat">
            <div class="l">PayPal</div>
            <div class="v">{{ formatEUR(data.shift.paypalSalesCents ?? 0) }}</div>
            <div class="s">PayPal.me</div>
          </div>
          <div class="stat">
            <div class="l">Differenz</div>
            <div class="v" :class="{ ok: (data.shift.diffCents ?? 0) > 0, bad: (data.shift.diffCents ?? 0) < 0 }">
              {{ (data.shift.diffCents ?? 0) > 0 ? '+' : '' }}{{ formatEUR(data.shift.diffCents ?? 0) }}
            </div>
            <div class="s">Soll {{ formatEUR(data.shift.expectedCashCents ?? 0) }} · Ist {{ formatEUR(data.shift.countedCashCents ?? 0) }}</div>
          </div>
        </div>

        <div class="two-col">
          <div class="card-box">
            <h3>
              <span>Schicht</span>
              <span class="meta">{{ data.shift.userName }}<span v-if="data.shift.klasse"> · {{ data.shift.klasse }}</span><span v-if="data.shift.registerName"> · {{ data.shift.registerName }}</span></span>
            </h3>
            <div class="summary-list">
              <div class="l"><span>Gestartet</span><span class="v">{{ fmt(data.shift.startedAt) }}</span></div>
              <div class="l"><span>Abgeschlossen</span><span class="v">{{ fmt(data.shift.closedAt) }}</span></div>
              <div class="l"><span>Anfangsbestand</span><span class="v">{{ formatEUR(data.shift.openingCashCents) }}</span></div>
              <div class="l"><span>+ Barumsatz</span><span class="v">{{ formatEUR(data.shift.cashSalesCents ?? 0) }}</span></div>
              <div class="l tot"><span>Soll-Bestand</span><span class="v">{{ formatEUR(data.shift.expectedCashCents ?? 0) }}</span></div>
              <div class="l"><span>Ist-Bestand</span><span class="v">{{ formatEUR(data.shift.countedCashCents ?? 0) }}</span></div>
            </div>
            <div class="diff" :class="(data.shift.diffCents ?? 0) === 0 ? 'zero' : (data.shift.diffCents ?? 0) > 0 ? 'ok' : 'bad'">
              <span class="l">
                {{ (data.shift.diffCents ?? 0) === 0 ? '✓ Kasse stimmte' : (data.shift.diffCents ?? 0) > 0 ? '↑ Überschuss' : '↓ Fehlbetrag' }}
              </span>
              <span class="v">
                {{ (data.shift.diffCents ?? 0) > 0 ? '+' : '' }}{{ formatEUR(data.shift.diffCents ?? 0) }}
              </span>
            </div>
            <template v-if="data.shift.notes">
              <label class="label" style="margin-top:14px">Anmerkungen</label>
              <div style="white-space:pre-wrap;color:var(--ink-2);font-size:13.5px">{{ data.shift.notes }}</div>
            </template>
          </div>

          <div class="card-box">
            <h3>
              <span>Bons</span>
              <span class="meta">{{ data.sales.length }} Buchungen</span>
            </h3>
            <div v-if="data.sales.length === 0" class="report-empty">Keine Verkäufe.</div>
            <div v-else class="top-list">
              <div v-for="s in data.sales" :key="s.id" class="t-row">
                <span class="rk">{{ s.method === 'BAR' ? '€' : s.method === 'PAYPAL' ? 'PP' : '⌐' }}</span>
                <div>
                  <div style="font-weight:550">
                    {{ formatEUR(s.totalCents) }} · {{ s.method === 'BAR' ? 'Bar' : s.method === 'PAYPAL' ? 'PayPal' : 'Karte' }}
                    <span v-if="s.method !== 'BAR'" style="font-size:11px;font-weight:400;opacity:.65;margin-left:4px">#{{ s.transactionRef }}</span>
                  </div>
                  <div style="color:var(--ink-3);font-size:12px">
                    {{ new Date(s.ts).toLocaleString('de-DE') }} · {{ s.items.map(i => `${i.qty}× ${i.name}`).join(', ') }}
                  </div>
                </div>
                <span class="qy">{{ s.items.reduce((q, i) => q + i.qty, 0) }} Art.</span>
                <span class="sm">{{ s.byName }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card-box" style="margin-top:14px" v-if="productStats.rows.length > 0">
          <h3>
            <span>Was wurde verkauft?</span>
            <span class="meta">{{ productStats.totalQty }} Artikel · {{ formatEUR(productStats.totalRev) }}</span>
          </h3>
          <div class="top-list">
            <div v-for="(p, i) in productStats.rows" :key="p.name" class="t-row">
              <span class="rk">{{ i + 1 }}</span>
              <div>
                <div style="font-weight:550">{{ p.name }}</div>
                <div class="bar-bg">
                  <div class="bar" :style="{ width: ((p.qty / productMaxQty) * 100) + '%' }"></div>
                </div>
                <div style="color:var(--ink-3);font-size:11.5px;margin-top:2px">
                  {{ productStats.totalQty === 0 ? '0,0' : ((p.qty / productStats.totalQty) * 100).toFixed(1).replace('.', ',') }}% der Menge
                  · {{ productStats.totalRev === 0 ? '0,0' : ((p.rev / productStats.totalRev) * 100).toFixed(1).replace('.', ',') }}% des Umsatzes
                </div>
              </div>
              <span class="qy">×{{ p.qty }}</span>
              <span class="sm">{{ formatEUR(p.rev) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="error" class="report-empty" style="margin:40px auto;text-align:center">
        {{ error }} <NuxtLink to="/abschluss">↩ Zurück</NuxtLink>
      </div>
    </div>
  </div>
</template>

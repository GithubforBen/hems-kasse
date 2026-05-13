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
</script>

<template>
  <div class="app">
    <TopBar />

    <div class="scroll-y" style="flex:1">
      <div class="report" v-if="data">
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
              <span class="meta">{{ data.shift.userName }}<span v-if="data.shift.klasse"> · {{ data.shift.klasse }}</span></span>
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
                <span class="rk">{{ s.method === 'BAR' ? '€' : '⌐' }}</span>
                <div>
                  <div style="font-weight:550">{{ formatEUR(s.totalCents) }} · {{ s.method === 'BAR' ? 'Bar' : 'Karte' }}</div>
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
      </div>

      <div v-else-if="error" class="report-empty" style="margin:40px auto;text-align:center">
        {{ error }} <NuxtLink to="/abschluss">↩ Zurück</NuxtLink>
      </div>
    </div>
  </div>
</template>

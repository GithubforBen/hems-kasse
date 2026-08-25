<script setup lang="ts">
import { formatEUR, centsToEuroString } from '~/utils/format'
import { DENOMS, NOTES, COINS } from '~/utils/denoms'

const shift = useShiftStore()
const sales = useSalesStore()

const counts = reactive<Record<number, number>>({})
for (const d of DENOMS) counts[d.cents] = 0

const notes = ref('')
const countOpen = ref(false)

onMounted(async () => {
  // A 409 here means this Abrechnung is no longer bookable; useApi already routes back to the
  // login form, so swallow the rejection rather than letting it surface as a page error.
  try {
    if (!shift.current) await shift.fetchCurrent()
    if (sales.sales.length === 0) await sales.fetch()
  } catch {}
})

const countedCents = computed(() =>
  DENOMS.reduce((t, d) => t + d.cents * (counts[d.cents] || 0), 0)
)

const expectedCents = computed(() =>
  (shift.current?.openingCashCents ?? 0) + sales.cashCents
)

const diffCents = computed(() => countedCents.value - expectedCents.value)

const avgCents = computed(() =>
  sales.sales.length ? Math.round(sales.totalCents / sales.sales.length) : 0
)

const startedAt = computed(() => {
  const s = shift.current?.startedAt
  if (!s) return '–'
  return new Date(s).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
})

const top = computed(() => {
  const m: Record<string, { qty: number; sum: number }> = {}
  for (const s of sales.sales) {
    for (const i of s.items) {
      const e = m[i.name] ??= { qty: 0, sum: 0 }
      e.qty += i.qty
      e.sum += i.qty * i.priceCents
    }
  }
  return Object.entries(m).sort((a, b) => b[1].qty - a[1].qty).slice(0, 8)
})
const topMaxQty = computed(() => top.value[0]?.[1].qty || 1)

// Per-cashier breakdown — several people of the same class share one shift, so the
// closeout shows who booked how many Bons / how much revenue (data from each sale's byName).
const byCashier = computed(() => {
  const m: Record<string, { bons: number; sum: number; items: number }> = {}
  for (const s of sales.sales) {
    const who = s.byName?.trim() || 'Unbekannt'
    const e = m[who] ??= { bons: 0, sum: 0, items: 0 }
    e.bons += 1
    e.sum += s.totalCents
    e.items += s.items.reduce((q, i) => q + i.qty, 0)
  }
  return Object.entries(m)
    .map(([name, v]) => ({ name, ...v }))
    .sort((a, b) => b.sum - a.sum || b.bons - a.bons || a.name.localeCompare(b.name))
})

const toast = useToastStore()
const auth = useAuthStore()
const closing = ref(false)

async function closeShift() {
  if (closing.value) return
  const totalSales = sales.totalCents
  const nr = auth.user?.abrechnungNr
  const ok = confirm(
    `Abrechnung${nr != null ? ` #${nr}` : ''} abschließen?\n\n` +
    `Umsatz: ${formatEUR(totalSales)}\n` +
    `Differenz: ${diffCents.value >= 0 ? '+' : ''}${formatEUR(diffCents.value)}\n\n` +
    `Die Verkaufsdaten werden archiviert${nr != null ? ` und auf Umschlag #${nr} abgerechnet` : ''}. ` +
    `Für die nächste Schicht wird die Nummer des nächsten Umschlags gebraucht.`
  )
  if (!ok) return
  closing.value = true
  try {
    await shift.close(countedCents.value, notes.value)
  } catch (e) {
    closing.value = false
    toast.show('Abschluss fehlgeschlagen – bitte erneut versuchen')
    throw e
  }
  sales.clear()
  // The envelope is sealed: this session can no longer book anything, so end it here rather
  // than reopening a shift on a spent number. The next shift logs in with the next envelope.
  auth.clear()
  await navigateTo({ path: '/login', query: { reason: 'abgeschlossen' } })
}

// reverse-order list, large denoms on top
const denomRows = computed(() => [...DENOMS].reverse())

function printPage() {
  if (import.meta.client) window.print()
}
</script>

<template>
  <div class="report">
      <!-- KPI -->
      <div class="stat-grid">
        <div class="stat">
          <div class="l">Umsatz gesamt</div>
          <div class="v">{{ formatEUR(sales.totalCents) }}</div>
          <div class="s">{{ sales.sales.length }} Bons · {{ sales.itemsSold }} Artikel</div>
        </div>
        <div class="stat">
          <div class="l">Bar</div>
          <div class="v">{{ formatEUR(sales.cashCents) }}</div>
          <div class="s">{{ sales.sales.filter(s => s.method === 'BAR').length }} Zahlungen</div>
        </div>
        <div class="stat">
          <div class="l">Karte</div>
          <div class="v">{{ formatEUR(sales.cardCents) }}</div>
          <div class="s">{{ sales.sales.filter(s => s.method === 'KARTE').length }} Zahlungen</div>
        </div>
        <div class="stat">
          <div class="l">PayPal</div>
          <div class="v">{{ formatEUR(sales.paypalCents) }}</div>
          <div class="s">{{ sales.sales.filter(s => s.method === 'PAYPAL').length }} Zahlungen</div>
        </div>
        <div class="stat">
          <div class="l">Ø Bon</div>
          <div class="v">{{ formatEUR(avgCents) }}</div>
          <div class="s">seit {{ startedAt }} Uhr</div>
        </div>
      </div>

      <div class="two-col">
        <!-- Kassenzählung -->
        <div class="card-box">
          <h3>
            <span>Kassenzählung</span>
            <span class="meta">{{ centsToEuroString(countedCents) }} € gezählt</span>
          </h3>

          <div class="opening-row">
            <div class="opening-info">
              <span class="label">Anfangsbestand</span>
              <span class="opening-val">{{ formatEUR(shift.current?.openingCashCents ?? 0) }}</span>
            </div>
            <button class="btn secondary" style="flex-shrink:0" @click="countOpen = true">
              {{ (shift.current?.openingCashCents ?? 0) === 0 ? 'Einzählen' : 'Ändern' }}
            </button>
          </div>

          <div class="money-section">
            <div class="money-label">Scheine</div>
            <div class="money-grid notes-grid">
              <button
                v-for="n in NOTES"
                :key="n.cents"
                :class="['note', `note-${n.cents / 100}`]"
                @click="counts[n.cents]++">
                <span class="v">{{ n.label }}</span>
              </button>
            </div>
            <div class="money-label">Münzen</div>
            <div class="money-grid coins-grid">
              <button
                v-for="c in COINS"
                :key="c.cents"
                :class="['coin', c.cents >= 100 ? `coin-eu${c.cents / 100}` : (c.cents === 50 ? 'coin-au' : 'coin-cu')]"
                @click="counts[c.cents]++">
                <span class="v">{{ c.label }}</span>
              </button>
            </div>
          </div>

          <div v-for="d in denomRows" :key="d.cents" class="denom-row">
            <div class="d">{{ d.label }}</div>
            <input
              type="number"
              min="0"
              :value="counts[d.cents] || ''"
              placeholder="0"
              @input="(e) => { counts[d.cents] = parseInt((e.target as HTMLInputElement).value.replace(/[^\d]/g, '') || '0', 10) }" />
            <div class="sub">{{ formatEUR(d.cents * (counts[d.cents] || 0)) }}</div>
          </div>
        </div>

        <!-- Auswertung -->
        <div>
          <div class="card-box" style="margin-bottom:14px">
            <h3>Tagesabschluss</h3>
            <div class="summary-list">
              <div class="l">
                <span>Anfangsbestand</span>
                <span class="v">{{ formatEUR(shift.current?.openingCashCents ?? 0) }}</span>
              </div>
              <div class="l">
                <span>+ Barumsatz</span>
                <span class="v">{{ formatEUR(sales.cashCents) }}</span>
              </div>
              <div class="l tot">
                <span>Soll-Bestand Bar</span>
                <span class="v">{{ formatEUR(expectedCents) }}</span>
              </div>
              <div class="l">
                <span>Ist-Bestand (gezählt)</span>
                <span class="v">{{ formatEUR(countedCents) }}</span>
              </div>
            </div>
            <div class="diff" :class="diffCents === 0 ? 'zero' : (diffCents > 0 ? 'ok' : 'bad')">
              <span class="l">
                {{ diffCents === 0 ? '✓ Kasse stimmt' : diffCents > 0 ? '↑ Überschuss' : '↓ Fehlbetrag' }}
              </span>
              <span class="v">{{ diffCents > 0 ? '+' : '' }}{{ formatEUR(diffCents) }}</span>
            </div>

            <label class="label" style="margin-top:14px">Anmerkungen (optional)</label>
            <textarea
              class="input"
              rows="2"
              v-model="notes"
              placeholder="z. B. Spende, beschädigtes Geld, Übergabe an…" />

            <div class="report-actions">
              <button class="btn secondary" @click="printPage">Drucken</button>
              <button class="btn danger" @click="closeShift">Schicht abschließen</button>
            </div>
          </div>

          <div class="card-box" style="margin-bottom:14px" v-if="byCashier.length > 0">
            <h3>
              <span>Pro Verkäufer:in</span>
              <span class="meta">gestartet von {{ shift.current?.userName ?? '–' }} · {{ startedAt }} Uhr</span>
            </h3>
            <div class="top-list">
              <div v-for="(c, i) in byCashier" :key="c.name" class="t-row">
                <span class="rk">{{ i + 1 }}</span>
                <div>
                  <div style="font-weight:550">{{ c.name }}</div>
                  <div style="color:var(--ink-3);font-size:11.5px;margin-top:2px">
                    {{ c.bons }} Bons · {{ c.items }} Artikel
                  </div>
                </div>
                <span class="qy">×{{ c.bons }}</span>
                <span class="sm">{{ formatEUR(c.sum) }}</span>
              </div>
            </div>
          </div>

          <div class="card-box">
            <h3>
              <span>Top-Verkäufer</span>
              <span class="meta">{{ sales.itemsSold }} Artikel insg.</span>
            </h3>
            <div v-if="top.length === 0" class="report-empty">
              Noch keine Verkäufe in dieser Schicht.
            </div>
            <div v-else class="top-list">
              <div v-for="(entry, i) in top" :key="entry[0]" class="t-row">
                <span class="rk">{{ i + 1 }}</span>
                <div>
                  <div style="font-weight:550">{{ entry[0] }}</div>
                  <div class="bar-bg">
                    <div class="bar" :style="{ width: ((entry[1].qty / topMaxQty) * 100) + '%' }"></div>
                  </div>
                </div>
                <span class="qy">×{{ entry[1].qty }}</span>
                <span class="sm">{{ formatEUR(entry[1].sum) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
  </div>

  <CashCountModal :open="countOpen" @close="countOpen = false" />
</template>

<style scoped>
.opening-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.opening-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.opening-val {
  font-size: 20px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  font-family: var(--font-num);
  letter-spacing: -.01em;
}
</style>

<script setup lang="ts">
import { formatEUR } from '~/utils/format'

const stats = useStatsStore()

onMounted(async () => {
  if (!stats.stats) await stats.fetch()
})

const WEEKDAYS = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']

function pct(v: number, max: number) {
  return max <= 0 ? 0 : Math.round((v / max) * 100)
}

const topByQty = computed(() => stats.stats?.topByQty ?? [])
const topByQtyMax = computed(() => topByQty.value[0]?.qty || 1)

const topByRevenue = computed(() => stats.stats?.topByRevenue ?? [])
const topByRevenueMax = computed(() => topByRevenue.value[0]?.revenueCents || 1)

const shortages = computed(() => stats.stats?.shortages ?? [])
const shortagesMax = computed(() => shortages.value[0]?.totalShortage || 1)

const byHour = computed(() => stats.stats?.byHour ?? [])
const byHourMax = computed(() => Math.max(1, ...byHour.value.map(b => b.qty)))

const byWeekday = computed(() => stats.stats?.byWeekday ?? [])
const byWeekdayMax = computed(() => Math.max(1, ...byWeekday.value.map(b => b.qty)))

const daily = computed(() => stats.stats?.daily ?? [])
const dailyMax = computed(() => Math.max(1, ...daily.value.map(d => d.revenueCents)))

function fmtDay(iso: string) {
  const [, m, d] = iso.split('-')
  return `${d}.${m}.`
}

const productNames = computed(() => {
  const set = new Set((stats.stats?.productHours ?? []).map(p => p.product))
  return [...set].sort((a, b) => a.localeCompare(b, 'de'))
})
const selectedProduct = ref<string | null>(null)
watch(productNames, names => {
  if (!selectedProduct.value && names.length) selectedProduct.value = names[0]!
}, { immediate: true })

const productHourData = computed(() => {
  const arr = Array.from({ length: 24 }, (_, h) => ({ hour: h, qty: 0 }))
  if (selectedProduct.value) {
    for (const p of stats.stats?.productHours ?? []) {
      if (p.product === selectedProduct.value) arr[p.hour]!.qty = p.qty
    }
  }
  return arr
})
const productHourMax = computed(() => Math.max(1, ...productHourData.value.map(d => d.qty)))
const productTotalQty = computed(() => productHourData.value.reduce((t, d) => t + d.qty, 0))
</script>

<template>
  <div class="scroll-y" style="padding:18px 22px 28px;flex:1">
    <div v-if="!stats.stats" class="report-empty">Lade Statistiken…</div>

    <template v-else>
      <!-- KPI -->
      <div class="stat-grid">
        <div class="stat">
          <div class="l">Umsatz gesamt</div>
          <div class="v">{{ formatEUR(stats.stats.totalRevenueCents) }}</div>
          <div class="s">{{ stats.stats.totalSales }} Bons</div>
        </div>
        <div class="stat">
          <div class="l">Artikel verkauft</div>
          <div class="v">{{ stats.stats.totalQty }}</div>
          <div class="s">über alle Schichten</div>
        </div>
        <div class="stat">
          <div class="l">Ø Bon</div>
          <div class="v">{{ formatEUR(stats.stats.totalSales ? Math.round(stats.stats.totalRevenueCents / stats.stats.totalSales) : 0) }}</div>
          <div class="s">{{ stats.stats.daily.length }} Verkaufstage</div>
        </div>
      </div>

      <div class="two-col">
        <!-- Top-Verkäufer nach Menge -->
        <div class="card-box">
          <h3>
            <span>Meistverkauft</span>
            <span class="meta">nach Menge</span>
          </h3>
          <div v-if="topByQty.length === 0" class="report-empty">Noch keine Verkäufe.</div>
          <div v-else class="top-list">
            <div v-for="(p, i) in topByQty" :key="p.name" class="t-row">
              <span class="rk">{{ i + 1 }}</span>
              <div>
                <div style="font-weight:550">{{ p.name }}</div>
                <div class="bar-bg"><div class="bar" :style="{ width: pct(p.qty, topByQtyMax) + '%' }"></div></div>
              </div>
              <span class="qy">×{{ p.qty }}</span>
              <span class="sm">{{ formatEUR(p.revenueCents) }}</span>
            </div>
          </div>
        </div>

        <!-- Top-Verkäufer nach Umsatz -->
        <div class="card-box">
          <h3>
            <span>Umsatzstärkste</span>
            <span class="meta">nach Umsatz</span>
          </h3>
          <div v-if="topByRevenue.length === 0" class="report-empty">Noch keine Verkäufe.</div>
          <div v-else class="top-list">
            <div v-for="(p, i) in topByRevenue" :key="p.name" class="t-row">
              <span class="rk">{{ i + 1 }}</span>
              <div>
                <div style="font-weight:550">{{ p.name }}</div>
                <div class="bar-bg"><div class="bar" :style="{ width: pct(p.revenueCents, topByRevenueMax) + '%' }"></div></div>
              </div>
              <span class="qy">×{{ p.qty }}</span>
              <span class="sm">{{ formatEUR(p.revenueCents) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="two-col" style="margin-top:18px">
        <!-- Verkäufe nach Tageszeit -->
        <div class="card-box">
          <h3>
            <span>Verkäufe nach Uhrzeit</span>
            <span class="meta">Menge je Stunde</span>
          </h3>
          <div v-if="byHour.every(b => b.qty === 0)" class="report-empty">Noch keine Verkäufe.</div>
          <div v-else class="vbar-chart">
            <div v-for="b in byHour" :key="b.bucket" class="vbar-col" :title="`${b.bucket}–${(b.bucket + 1) % 24} Uhr · ${b.qty} Artikel · ${formatEUR(b.revenueCents)}`">
              <div class="vbar-bg"><div class="vbar" :style="{ height: pct(b.qty, byHourMax) + '%' }"></div></div>
              <span class="vbar-lbl">{{ b.bucket }}</span>
            </div>
          </div>
        </div>

        <!-- Verkäufe nach Wochentag -->
        <div class="card-box">
          <h3>
            <span>Verkäufe nach Wochentag</span>
            <span class="meta">Menge je Tag</span>
          </h3>
          <div v-if="byWeekday.every(b => b.qty === 0)" class="report-empty">Noch keine Verkäufe.</div>
          <div v-else class="vbar-chart">
            <div v-for="b in byWeekday" :key="b.bucket" class="vbar-col" :title="`${WEEKDAYS[b.bucket]} · ${b.qty} Artikel · ${formatEUR(b.revenueCents)}`">
              <div class="vbar-bg"><div class="vbar" :style="{ height: pct(b.qty, byWeekdayMax) + '%' }"></div></div>
              <span class="vbar-lbl">{{ WEEKDAYS[b.bucket] }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="card-box" style="margin-top:18px">
        <h3>
          <span>Umsatz pro Tag</span>
          <span class="meta">{{ daily.length }} Tage mit Verkäufen</span>
        </h3>
        <div v-if="daily.length === 0" class="report-empty">Noch keine Verkäufe.</div>
        <div v-else class="vbar-chart daily-chart">
          <div v-for="d in daily" :key="d.date" class="vbar-col" :title="`${d.date} · ${formatEUR(d.revenueCents)} · ${d.qty} Artikel · ${d.sales} Bons`">
            <div class="vbar-bg"><div class="vbar" :style="{ height: pct(d.revenueCents, dailyMax) + '%' }"></div></div>
            <span class="vbar-lbl">{{ fmtDay(d.date) }}</span>
          </div>
        </div>
      </div>

      <div class="two-col" style="margin-top:18px">
        <!-- Fehlbestand-Häufigkeit -->
        <div class="card-box">
          <h3>
            <span>Häufig fehlend</span>
            <span class="meta">aus Inventuren</span>
          </h3>
          <p style="font-size:12.5px;color:var(--ink-3);margin:-4px 0 12px">
            Produkte, bei denen Inventuren wiederholt einen Fehlbestand (gezählt &lt; erwartet) ergeben haben.
          </p>
          <div v-if="shortages.length === 0" class="report-empty">Bisher kein Fehlbestand festgestellt.</div>
          <div v-else class="top-list">
            <div v-for="(s, i) in shortages" :key="s.name" class="t-row">
              <span class="rk">{{ i + 1 }}</span>
              <div>
                <div style="font-weight:550">{{ s.name }}</div>
                <div class="bar-bg"><div class="bar bad" :style="{ width: pct(s.totalShortage, shortagesMax) + '%' }"></div></div>
              </div>
              <span class="qy">{{ s.countsWithShortage }}× erfasst</span>
              <span class="sm" style="color:var(--bad)">−{{ s.totalShortage }} (max −{{ s.worstShortage }})</span>
            </div>
          </div>
        </div>

        <!-- Verkaufszeiten pro Produkt -->
        <div class="card-box">
          <h3>
            <span>Verkaufszeiten je Produkt</span>
            <span class="meta">Menge je Stunde</span>
          </h3>
          <div v-if="productNames.length === 0" class="report-empty">Noch keine Verkäufe.</div>
          <template v-else>
            <select class="input" v-model="selectedProduct" style="margin-bottom:14px">
              <option v-for="n in productNames" :key="n" :value="n">{{ n }}</option>
            </select>
            <p style="font-size:12px;color:var(--ink-3);margin:-8px 0 10px">
              {{ productTotalQty }} Stück insgesamt verkauft, aufgeschlüsselt nach Tageszeit.
            </p>
            <div class="vbar-chart">
              <div v-for="d in productHourData" :key="d.hour" class="vbar-col" :title="`${d.hour}–${(d.hour + 1) % 24} Uhr · ${d.qty}×`">
                <div class="vbar-bg"><div class="vbar" :style="{ height: pct(d.qty, productHourMax) + '%' }"></div></div>
                <span class="vbar-lbl">{{ d.hour }}</span>
              </div>
            </div>
          </template>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.vbar-chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 150px;
  padding-top: 8px;
}
.daily-chart { gap: 3px; }
.vbar-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  height: 100%;
}
.vbar-bg {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  background: var(--paper-3);
  border-radius: 4px;
  overflow: hidden;
}
.vbar {
  width: 100%;
  background: var(--ink);
  border-radius: 3px 3px 0 0;
  min-height: 2px;
  transition: height .15s ease;
}
.vbar-lbl {
  font-size: 10px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
  font-family: var(--font-num);
  white-space: nowrap;
}
.bar.bad { background: var(--bad); }
</style>

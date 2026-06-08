<script setup lang="ts">
const auth = useAuthStore()
const inventory = useInventoryStore()

onMounted(async () => {
  await Promise.all([inventory.fetchExpected(), inventory.fetchCounts()])
})

function fmtDate(iso: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

const latestCount = computed(() => inventory.counts[0] ?? null)

function diffClass(d: number): string {
  if (d === 0) return 'zero'
  return d > 0 ? 'ok' : 'bad'
}
</script>

<template>
  <div class="app">
    <TopBar />

    <div class="scroll-y" style="padding:18px 22px 28px">
      <div class="card-box">
        <h3>
          <span>Lagerbestand</span>
          <span class="meta">aktuell erwartet</span>
        </h3>
        <p style="font-size:13px;color:var(--ink-3);margin:-4px 0 14px;max-width:720px">
          Berechnet aus der letzten Inventur, seitdem erfassten Wareneingängen und den
          tatsächlich verkauften Mengen (auch über Verkaufstasten-Kompositionen aufgelöst).
        </p>

        <div v-if="inventory.expected.length === 0" class="report-empty">
          Noch keine zählbaren Produkte oder noch keine Inventur durchgeführt.
        </div>
        <table v-else class="history-table">
          <thead>
            <tr>
              <th>Produkt</th>
              <th class="num">Erwartet</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in inventory.expected" :key="e.productId">
              <td>{{ e.name }}</td>
              <td class="num">{{ e.expectedQty }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="inventory.expected.length" style="font-size:12px;color:var(--ink-3);margin-top:10px">
          Basis: {{ latestCount ? `Inventur vom ${fmtDate(latestCount.ts)}` : 'noch keine Inventur — Zählung seit Beginn' }}
        </p>
      </div>

      <div class="card-box" style="margin-top:18px">
        <h3>
          <span>Letzte Inventur</span>
          <span v-if="latestCount" class="meta">{{ fmtDate(latestCount.ts) }} · {{ latestCount.byName }}</span>
        </h3>
        <div v-if="!latestCount" class="report-empty">Noch keine Inventur durchgeführt.</div>
        <table v-else class="history-table">
          <thead>
            <tr>
              <th>Produkt</th>
              <th class="num">Erwartet</th>
              <th class="num">Gezählt</th>
              <th class="num">Fehlbestand</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in latestCount.lines" :key="l.productId ?? l.productName">
              <td>{{ l.productName }}</td>
              <td class="num">{{ l.expectedQty }}</td>
              <td class="num">{{ l.countedQty }}</td>
              <td class="num diff" :class="diffClass(l.diffQty)">{{ l.diffQty > 0 ? '+' : '' }}{{ l.diffQty }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="auth.user?.role === 'ADMIN'" style="margin-top:14px;font-size:13px;color:var(--ink-3)">
        Inventuren und Wareneingänge erfassen: <NuxtLink to="/admin" style="color:var(--accent)">Admin → Lager</NuxtLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.history-table td.diff.ok { color: var(--ok); font-weight: 600; }
.history-table td.diff.bad { color: var(--bad); font-weight: 600; }
.history-table td.diff.zero { color: var(--ink-2); }
</style>

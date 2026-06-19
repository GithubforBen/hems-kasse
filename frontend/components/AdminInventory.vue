<script setup lang="ts">
import { formatEUR } from '~/utils/format'
import type { ProductDto } from '~/types/api'

const catalog = useCatalogStore()
const inventory = useInventoryStore()
const toast = useToastStore()

const mode = ref<'count' | 'intake' | 'history'>('count')

onMounted(async () => {
  await Promise.all([
    catalog.fetch(),
    inventory.fetchExpected(),
    inventory.fetchCounts(),
    inventory.fetchIntakes(),
  ])
})

function fmtDate(iso: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

/** Trackable stock products: simple, fixed-price, non-Verkaufstaste. */
const trackable = computed<ProductDto[]>(() =>
  catalog.categories.flatMap(c => c.products).filter(p => !p.variable && !p.composed)
)

const expectedById = computed(() => {
  const m = new Map<string, number>()
  for (const e of inventory.expected) m.set(e.productId, e.expectedQty)
  return m
})

// ---------- Inventur durchführen ----------
const counted = reactive<Record<string, string>>({})
const countNotes = ref('')
const countSaving = ref(false)

async function submitCount() {
  const lines = trackable.value
    .filter(p => counted[p.id] !== undefined && counted[p.id] !== '')
    .map(p => ({ productId: p.id, countedQty: parseInt(counted[p.id]!, 10) || 0 }))
  if (lines.length === 0) {
    toast.show('Bitte mindestens ein Produkt zählen')
    return
  }
  countSaving.value = true
  try {
    await inventory.recordCount(lines, countNotes.value || undefined)
    toast.show(`Inventur gespeichert · ${lines.length} Produkt(e) gezählt`)
    for (const k of Object.keys(counted)) delete counted[k]
    countNotes.value = ''
    await Promise.all([inventory.fetchExpected(), inventory.fetchCounts()])
  } finally {
    countSaving.value = false
  }
}

// ---------- Wareneingang erfassen ----------
interface IntakeLine { productId: string; name: string; qty: number }
const intakeLines = ref<IntakeLine[]>([])
const intakePickerId = ref('')
const intakeNotes = ref('')
const intakeSaving = ref(false)

const intakePickable = computed(() =>
  trackable.value
    .filter(p => !intakeLines.value.some(l => l.productId === p.id))
    .sort((a, b) => a.name.localeCompare(b.name))
)

function addIntakeLine() {
  const p = intakePickable.value.find(x => x.id === intakePickerId.value)
  if (!p) return
  intakeLines.value.push({ productId: p.id, name: p.name, qty: 1 })
  intakePickerId.value = ''
}
function removeIntakeLine(id: string) {
  intakeLines.value = intakeLines.value.filter(l => l.productId !== id)
}
function incIntake(id: string, delta: number) {
  const l = intakeLines.value.find(x => x.productId === id)
  if (l) l.qty = Math.max(1, l.qty + delta)
}

async function submitIntake() {
  if (intakeLines.value.length === 0) return
  intakeSaving.value = true
  try {
    await inventory.recordIntake(
      intakeLines.value.map(l => ({ productId: l.productId, qty: l.qty })),
      intakeNotes.value || undefined,
    )
    toast.show(`Wareneingang gespeichert · ${intakeLines.value.length} Produkt(e)`)
    intakeLines.value = []
    intakeNotes.value = ''
    await Promise.all([inventory.fetchExpected(), inventory.fetchIntakes()])
  } finally {
    intakeSaving.value = false
  }
}

// ---------- Verlauf ----------
const historyOpenCount = ref<string | null>(null)
const historyOpenIntake = ref<string | null>(null)

function diffClass(d: number): string {
  if (d === 0) return 'zero'
  return d > 0 ? 'ok' : 'bad'
}
</script>

<template>
  <div class="scroll-y" style="padding:18px 22px 28px;flex:1">
    <div class="lager-modes" style="display:flex;gap:8px;margin-bottom:16px">
      <button class="btn" :class="mode === 'count' ? '' : 'ghost'" style="padding:7px 14px;font-size:13px" @click="mode = 'count'">Inventur durchführen</button>
      <button class="btn" :class="mode === 'intake' ? '' : 'ghost'" style="padding:7px 14px;font-size:13px" @click="mode = 'intake'">Wareneingang erfassen</button>
      <button class="btn" :class="mode === 'history' ? '' : 'ghost'" style="padding:7px 14px;font-size:13px" @click="mode = 'history'">Verlauf</button>
    </div>

    <!-- Inventur durchführen -->
    <div v-if="mode === 'count'" class="card-box">
      <h3>
        <span>Inventur durchführen</span>
        <span class="meta">{{ trackable.length }} zählbare Produkte</span>
      </h3>
      <p class="lager-hint">
        Gezählten Bestand eintragen. Nicht gezählte Produkte bleiben offen (Teilzählung möglich).
        „Erwartet“ zeigt den live berechneten Sollbestand seit der letzten Inventur zum Vergleich.
      </p>

      <div v-if="trackable.length === 0" class="report-empty">
        Keine zählbaren Produkte. Diverse und Verkaufstasten haben keinen Lagerbestand.
      </div>
      <table v-else class="history-table lager-table">
        <thead>
          <tr>
            <th>Produkt</th>
            <th class="num">Erwartet</th>
            <th class="num">Gezählt</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in trackable" :key="p.id">
            <td>
              <span class="swatch" :class="`col-${p.color}`"></span>
              {{ p.name }}
              <span v-if="p.plu" class="lager-plu">#{{ p.plu }}</span>
            </td>
            <td class="num lager-expected">{{ expectedById.get(p.id) ?? '—' }}</td>
            <td class="num">
              <input
                class="input lager-count-i"
                type="number"
                min="0"
                v-model="counted[p.id]"
                placeholder="—" />
            </td>
          </tr>
        </tbody>
      </table>

      <div class="lager-form-row">
        <input class="input" style="flex:1" v-model="countNotes" placeholder="Notiz (optional)" />
        <button class="btn ok" :disabled="countSaving" @click="submitCount">
          {{ countSaving ? 'Speichern…' : 'Inventur speichern' }}
        </button>
      </div>
    </div>

    <!-- Wareneingang erfassen -->
    <div v-else-if="mode === 'intake'" class="card-box">
      <h3><span>Wareneingang erfassen</span></h3>
      <p class="lager-hint">Gelieferte Mengen erfassen — sie fließen in die Sollbestand-Berechnung ein.</p>

      <div v-if="intakeLines.length" class="pcm-lines">
        <div v-for="l in intakeLines" :key="l.productId" class="pcm-line">
          <span class="pcm-name">{{ l.name }}</span>
          <div class="pcm-qty">
            <button class="btn ghost" @click="incIntake(l.productId, -1)">−</button>
            <span class="pcm-qty-v">{{ l.qty }}×</span>
            <button class="btn ghost" @click="incIntake(l.productId, 1)">+</button>
          </div>
          <button class="btn ghost pcm-rm" @click="removeIntakeLine(l.productId)" title="Entfernen">✕</button>
        </div>
      </div>

      <div class="lager-form-row">
        <select v-model="intakePickerId" class="input" style="flex:1">
          <option value="" disabled>Produkt hinzufügen…</option>
          <option v-for="p in intakePickable" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
        <button class="btn secondary" :disabled="!intakePickerId" @click="addIntakeLine">+ hinzufügen</button>
      </div>

      <div class="lager-form-row">
        <input class="input" style="flex:1" v-model="intakeNotes" placeholder="Notiz (optional, z. B. Lieferant)" />
        <button class="btn ok" :disabled="intakeSaving || intakeLines.length === 0" @click="submitIntake">
          {{ intakeSaving ? 'Speichern…' : 'Wareneingang speichern' }}
        </button>
      </div>
    </div>

    <!-- Verlauf -->
    <div v-else class="card-box">
      <h3><span>Inventuren</span><span class="meta">{{ inventory.counts.length }}</span></h3>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px">
        <ExportButton path="/api/inventory/export.csv" :query="{ type: 'counts' }" label="Inventuren" icon="📋" hint="Alle Inventuren als CSV" />
        <ExportButton path="/api/inventory/export.csv" :query="{ type: 'intakes' }" label="Wareneingänge" icon="📦" hint="Alle Wareneingänge als CSV" />
        <ExportButton path="/api/inventory/export.csv" :query="{ type: 'expected' }" label="Lagerbestand" icon="📊" hint="Aktuell erwarteter Lagerbestand als CSV" />
      </div>
      <div v-if="inventory.counts.length === 0" class="report-empty">Noch keine Inventur durchgeführt.</div>
      <div v-else class="lager-history">
        <div v-for="c in inventory.counts" :key="c.id" class="lager-entry">
          <div class="lager-entry-h" @click="historyOpenCount = historyOpenCount === c.id ? null : c.id">
            <span class="lager-entry-date">{{ fmtDate(c.ts) }}</span>
            <span class="lager-entry-by">{{ c.byName }}</span>
            <span class="meta">{{ c.lines.length }} Produkt(e)</span>
            <span class="lager-entry-toggle">{{ historyOpenCount === c.id ? '▲' : '▼' }}</span>
          </div>
          <table v-if="historyOpenCount === c.id" class="history-table lager-table">
            <thead>
              <tr>
                <th>Produkt</th>
                <th class="num">Erwartet</th>
                <th class="num">Gezählt</th>
                <th class="num">Fehlbestand</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="l in c.lines" :key="l.productId ?? l.productName">
                <td>{{ l.productName }}</td>
                <td class="num">{{ l.expectedQty }}</td>
                <td class="num">{{ l.countedQty }}</td>
                <td class="num diff" :class="diffClass(l.diffQty)">{{ l.diffQty > 0 ? '+' : '' }}{{ l.diffQty }}</td>
              </tr>
            </tbody>
          </table>
          <p v-if="historyOpenCount === c.id && c.notes" class="lager-notes">„{{ c.notes }}“</p>
        </div>
      </div>

      <h3 style="margin-top:24px"><span>Wareneingänge</span><span class="meta">{{ inventory.intakes.length }}</span></h3>
      <div v-if="inventory.intakes.length === 0" class="report-empty">Noch kein Wareneingang erfasst.</div>
      <div v-else class="lager-history">
        <div v-for="i in inventory.intakes" :key="i.id" class="lager-entry">
          <div class="lager-entry-h" @click="historyOpenIntake = historyOpenIntake === i.id ? null : i.id">
            <span class="lager-entry-date">{{ fmtDate(i.ts) }}</span>
            <span class="lager-entry-by">{{ i.byName }}</span>
            <span class="meta">{{ i.lines.length }} Produkt(e)</span>
            <span class="lager-entry-toggle">{{ historyOpenIntake === i.id ? '▲' : '▼' }}</span>
          </div>
          <table v-if="historyOpenIntake === i.id" class="history-table lager-table">
            <thead>
              <tr><th>Produkt</th><th class="num">Menge</th></tr>
            </thead>
            <tbody>
              <tr v-for="l in i.lines" :key="l.productId ?? l.productName">
                <td>{{ l.productName }}</td>
                <td class="num">+{{ l.qty }}</td>
              </tr>
            </tbody>
          </table>
          <p v-if="historyOpenIntake === i.id && i.notes" class="lager-notes">„{{ i.notes }}“</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lager-hint {
  font-size: 13px;
  color: var(--ink-3);
  margin: -4px 0 14px;
  max-width: 720px;
}
.lager-table .swatch {
  display: inline-block;
  width: 10px; height: 10px;
  border-radius: 3px;
  margin-right: 6px;
  vertical-align: middle;
}
.lager-plu {
  margin-left: 8px;
  font-size: 11px;
  color: var(--ink-3);
  font-family: var(--font-num);
}
.lager-expected {
  color: var(--ink-3);
}
.lager-count-i {
  width: 80px;
  text-align: right;
}
.lager-form-row {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  align-items: center;
}
.pcm-lines {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 4px;
}
.pcm-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: var(--r-xs);
  background: var(--paper-2);
  border: 1px solid var(--line-2);
}
.pcm-name { flex: 1; font-weight: 600; }
.pcm-qty { display: flex; align-items: center; gap: 6px; }
.pcm-qty-v { min-width: 32px; text-align: center; font-variant-numeric: tabular-nums; }
.pcm-rm { padding: 2px 8px; }
.lager-history {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.lager-entry {
  border: 1px solid var(--line-2);
  border-radius: var(--r-sm);
  overflow: hidden;
}
.lager-entry-h {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
}
.lager-entry-h:hover { background: var(--paper-2); }
.lager-entry-date { font-weight: 600; }
.lager-entry-by { color: var(--ink-3); font-size: 13px; }
.lager-entry-toggle { margin-left: auto; color: var(--ink-3); font-size: 11px; }
.lager-notes { padding: 0 14px 12px; color: var(--ink-3); font-size: 13px; font-style: italic; }
.history-table.lager-table td.diff.ok { color: var(--ok); font-weight: 600; }
.history-table.lager-table td.diff.bad { color: var(--bad); font-weight: 600; }
.history-table.lager-table td.diff.zero { color: var(--ink-2); }
</style>

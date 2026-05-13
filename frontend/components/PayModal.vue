<script setup lang="ts">
import { formatEUR } from '~/utils/format'
import { NOTES, COINS } from '~/utils/denoms'
import type { CartItem, SaleDto } from '~/types/api'

const props = defineProps<{
  open: boolean
  items: CartItem[]
}>()

const emit = defineEmits<{
  close: []
  paid: [sale: SaleDto]
}>()

type Stage = 'choose' | 'cash' | 'card' | 'done'
const stage = ref<Stage>('choose')
const givenCents = ref(0)
const finished = ref<SaleDto | null>(null)
const cardError = ref<string | null>(null)
const cardWaiting = ref(false)

const totalCents = computed(() => props.items.reduce((t, x) => t + x.priceCents * x.qty, 0))
const changeCents = computed(() => givenCents.value - totalCents.value)
const qty = computed(() => props.items.reduce((t, x) => t + x.qty, 0))

// Reset on open
watch(() => props.open, (o) => {
  if (o) {
    stage.value = 'choose'
    givenCents.value = 0
    finished.value = null
    cardError.value = null
    cardWaiting.value = false
  }
})

function setStage(s: Stage) { stage.value = s }

function addDenom(c: number) {
  givenCents.value = Math.min(givenCents.value + c, 99_999_99)
}
function clearGiven() { givenCents.value = 0 }
function backspace() {
  // Treat as "drop one digit" off the decimal representation.
  givenCents.value = Math.floor(givenCents.value / 10)
}
function pressDigit(d: number) {
  const next = givenCents.value * 10 + d
  if (next > 99_999_99) return
  givenCents.value = next
}

const sales = useSalesStore()

async function finishCash() {
  if (givenCents.value < totalCents.value) return
  cardError.value = null
  try {
    const sale = await sales.record({
      method: 'BAR',
      givenCents: givenCents.value,
      items: props.items,
    })
    finished.value = sale
    stage.value = 'done'
    emit('paid', sale)
  } catch (e: any) {
    cardError.value = e?.data?.message ?? 'Buchung fehlgeschlagen.'
  }
}

async function finishCard() {
  cardWaiting.value = true
  cardError.value = null
  try {
    const sale = await sales.record({
      method: 'KARTE',
      givenCents: totalCents.value,
      items: props.items,
    })
    finished.value = sale
    stage.value = 'done'
    emit('paid', sale)
  } catch (e: any) {
    cardError.value = e?.data?.message ?? 'Buchung fehlgeschlagen.'
  } finally {
    cardWaiting.value = false
  }
}

const { public: { apiBase } } = useRuntimeConfig()
const auth = useAuthStore()
// img src is loaded by the browser, which can't send Authorization headers — fall back to including the token as a URL hint?
// The QR endpoint requires auth. To avoid leaking the JWT in URLs, we fetch the PNG via $fetch and turn it into a blob URL.
const qrUrl = ref<string | null>(null)
let qrAbort: AbortController | null = null

async function loadQr(amount: number) {
  qrAbort?.abort()
  qrAbort = new AbortController()
  qrUrl.value = null
  try {
    const blob = await $fetch<Blob>(`${apiBase}/api/payments/epc-qr.png`, {
      query: { amountCents: amount, ref: new Date().toISOString().slice(0, 16) },
      responseType: 'blob',
      headers: auth.token ? { Authorization: `Bearer ${auth.token}` } : {},
      signal: qrAbort.signal,
    } as any)
    if (qrUrl.value) URL.revokeObjectURL(qrUrl.value)
    qrUrl.value = URL.createObjectURL(blob)
  } catch (e: any) {
    if (e?.name !== 'AbortError') cardError.value = 'QR-Code konnte nicht geladen werden.'
  }
}

watch([() => stage.value, totalCents], ([s, t]) => {
  if (s === 'card' && t > 0) loadQr(t)
}, { immediate: false })

onBeforeUnmount(() => {
  if (qrUrl.value) URL.revokeObjectURL(qrUrl.value)
  qrAbort?.abort()
})

function onBackdropClick(e: MouseEvent) {
  if (e.target === e.currentTarget && stage.value !== 'card') emit('close')
}

const givenDisplay = computed(() => (givenCents.value / 100).toFixed(2).replace('.', ','))
</script>

<template>
  <div v-if="open" class="modal-bg" @mousedown="onBackdropClick">
    <div class="modal" :class="{ wide: stage === 'cash' }">

      <!-- choose stage -->
      <template v-if="stage === 'choose'">
        <div class="modal-h">
          <h3>Bezahlen</h3>
          <p>{{ qty }} Artikel · Bitte Zahlungsmethode wählen</p>
        </div>
        <div class="modal-b">
          <div class="pay-summary">
            <span class="l">Zu zahlen</span>
            <span class="v">{{ formatEUR(totalCents) }}</span>
          </div>
          <div class="pay-row">
            <button class="pay-btn pay-cash" @click="setStage('cash')">
              <span class="ico">💶</span>
              <span>Bar</span>
              <span class="lbl">Bargeld zählen</span>
            </button>
            <button class="pay-btn pay-card" @click="setStage('card')">
              <span class="ico">💳</span>
              <span>Karte</span>
              <span class="lbl">Überweisung per QR</span>
            </button>
          </div>
        </div>
        <div class="modal-f">
          <button class="btn ghost" @click="emit('close')">Abbrechen</button>
        </div>
      </template>

      <!-- cash stage -->
      <template v-else-if="stage === 'cash'">
        <div class="modal-h">
          <h3>Bar bezahlen</h3>
          <p>Auf Geldsymbole tippen oder Betrag eingeben.</p>
        </div>
        <div class="modal-b">
          <div class="pay-summary">
            <span class="l">Zu zahlen</span>
            <span class="v">{{ formatEUR(totalCents) }}</span>
          </div>

          <div class="given-row">
            <div class="given-field">
              <span class="given-l">Gegeben</span>
              <input
                class="given-input"
                inputmode="decimal"
                placeholder="0,00"
                :value="givenDisplay"
                @input="e => {
                  const raw = (e.target as HTMLInputElement).value.replace(/[^\d]/g, '')
                  givenCents = raw === '' ? 0 : Math.min(parseInt(raw, 10), 99_999_99)
                }" />
            </div>
            <button class="btn-clear" @click="clearGiven" title="Zurücksetzen">↻</button>
          </div>

          <div class="money-section">
            <div class="money-label">Scheine</div>
            <div class="money-grid notes-grid">
              <button
                v-for="n in NOTES"
                :key="n.cents"
                :class="['note', `note-${n.cents / 100}`]"
                @click="addDenom(n.cents)">
                <span class="v">{{ n.label }}</span>
              </button>
            </div>
            <div class="money-label">Münzen</div>
            <div class="money-grid coins-grid">
              <button
                v-for="c in COINS"
                :key="c.cents"
                :class="['coin', c.cents >= 100 ? `coin-eu${c.cents / 100}` : (c.cents === 50 ? 'coin-au' : 'coin-cu')]"
                @click="addDenom(c.cents)">
                <span class="v">{{ c.label }}</span>
              </button>
            </div>
          </div>

          <div v-if="givenCents > 0" class="change-line" :class="{ bad: changeCents < 0 }">
            <span class="l">{{ changeCents < 0 ? 'Es fehlen' : 'Rückgeld' }}</span>
            <span class="v">{{ formatEUR(Math.abs(changeCents)) }}</span>
          </div>

          <div v-if="cardError" style="margin-top:10px;color:var(--bad);font-size:13px">{{ cardError }}</div>
        </div>
        <div class="modal-f">
          <button class="btn ghost" @click="setStage('choose')">← Zurück</button>
          <button class="btn ok" :disabled="givenCents < totalCents" @click="finishCash">
            Bezahlung bestätigen
          </button>
        </div>
      </template>

      <!-- card stage -->
      <template v-else-if="stage === 'card'">
        <div class="modal-h">
          <h3>Kartenzahlung · Überweisung per QR</h3>
          <p>Kundinnen-Bankapp öffnen und QR-Code scannen.</p>
        </div>
        <div class="modal-b">
          <div class="card-anim">
            <div class="lab">Betrag</div>
            <div class="v">{{ formatEUR(totalCents) }}</div>
            <div style="margin-top:10px;display:flex;justify-content:center">
              <div style="background:#fff;padding:8px;border-radius:8px;min-width:240px;min-height:240px;display:flex;align-items:center;justify-content:center">
                <img v-if="qrUrl" :src="qrUrl" alt="EPC-QR" width="224" height="224" />
                <span v-else class="status"><span class="pulse"></span>QR wird erzeugt…</span>
              </div>
            </div>
            <div class="status" style="margin-top:10px">
              Mit der Banking-App scannen, um die SEPA-Überweisung anzustoßen.
            </div>
          </div>

          <div v-if="cardError" style="margin-top:10px;color:var(--bad);font-size:13px">{{ cardError }}</div>
        </div>
        <div class="modal-f">
          <button class="btn ghost" @click="setStage('choose')" :disabled="cardWaiting">Abbrechen</button>
          <button class="btn ok" @click="finishCard" :disabled="cardWaiting">
            {{ cardWaiting ? 'Buchen…' : 'Zahlung erhalten' }}
          </button>
        </div>
      </template>

      <!-- done stage -->
      <template v-else-if="stage === 'done' && finished">
        <div class="modal-h">
          <div class="success-ic">✓</div>
          <h3 style="text-align:center">Bezahlt</h3>
          <p style="text-align:center">
            {{ finished.method === 'BAR' ? 'Bar' : 'Karte' }} · {{ formatEUR(finished.totalCents) }} · Vielen Dank!
          </p>
        </div>
        <div class="modal-b">
          <div class="receipt">
            <div class="h">
              <div class="t1">SCHULKASSE · KUCHENVERKAUF</div>
              <div class="t2">{{ new Date(finished.ts).toLocaleString('de-DE') }}</div>
            </div>
            <div v-for="it in finished.items" :key="it.name" class="r">
              <span>{{ it.qty }}× {{ it.name }}</span>
              <span>{{ formatEUR(it.priceCents * it.qty) }}</span>
            </div>
            <div class="sep"></div>
            <div class="r grand"><span>GESAMT</span><span>{{ formatEUR(finished.totalCents) }}</span></div>
            <div class="r"><span>Zahlung</span><span>{{ finished.method === 'BAR' ? 'Bar' : 'Karte' }}</span></div>
            <template v-if="finished.method === 'BAR' && finished.givenCents > 0">
              <div class="r"><span>Gegeben</span><span>{{ formatEUR(finished.givenCents) }}</span></div>
              <div class="r"><span>Rückgeld</span><span>{{ formatEUR(finished.changeCents) }}</span></div>
            </template>
          </div>
        </div>
        <div class="modal-f">
          <button class="btn" @click="emit('close')">Weiter verkaufen →</button>
        </div>
      </template>

    </div>
  </div>
</template>

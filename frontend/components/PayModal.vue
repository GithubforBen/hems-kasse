<script setup lang="ts">
import { formatEUR } from '~/utils/format'
import { NOTES, COINS } from '~/utils/denoms'
import type { CartItem, SaleDto } from '~/types/api'
import QRCode from 'qrcode'

const props = defineProps<{
  open: boolean
  items: CartItem[]
}>()

const emit = defineEmits<{
  close: []
  paid: [sale: SaleDto]
}>()

type Stage = 'choose' | 'cash' | 'card' | 'paypal' | 'done'
const stage = ref<Stage>('choose')
const givenCents = ref(0)
const finished = ref<SaleDto | null>(null)
const cardError = ref<string | null>(null)
const cardWaiting = ref(false)
const txRef = ref<string>('')
const paypalQrDataUrl = ref<string | null>(null)

function generateTxRef(): string {
  // 8 hex chars. crypto.randomUUID() only exists in secure contexts (HTTPS /
  // localhost); on a phone reaching the till over a plain-HTTP LAN address it is
  // undefined and would crash checkout. getRandomValues works in any context,
  // and Math.random is a last-resort fallback (this ref is not security-critical).
  const c = typeof crypto !== 'undefined' ? crypto : undefined
  if (c?.randomUUID) {
    return c.randomUUID().replace(/-/g, '').substring(0, 8).toUpperCase()
  }
  if (c?.getRandomValues) {
    const buf = new Uint8Array(4)
    c.getRandomValues(buf)
    return Array.from(buf, b => b.toString(16).padStart(2, '0')).join('').toUpperCase()
  }
  return Math.random().toString(16).slice(2, 10).padStart(8, '0').toUpperCase()
}

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
    txRef.value = ''
    paypalQrDataUrl.value = null
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
      transactionRef: txRef.value || undefined,
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

async function finishPaypal() {
  cardWaiting.value = true
  cardError.value = null
  try {
    const sale = await sales.record({
      method: 'PAYPAL',
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

const paypalUrl = computed(() => {
  const euros = (totalCents.value / 100).toFixed(2)
  return `https://paypal.me/hems2027/${euros}`
})

async function openPaypal() {
  paypalQrDataUrl.value = null
  stage.value = 'paypal'
  try {
    paypalQrDataUrl.value = await QRCode.toDataURL(paypalUrl.value, {
      width: 320,
      margin: 2,
      errorCorrectionLevel: 'M',
      color: { dark: '#000000', light: '#ffffff' },
    })
  } catch {
    cardError.value = 'PayPal-QR konnte nicht erzeugt werden.'
  }
}

// QR endpoint requires auth — fetch as blob to avoid leaking JWT in the img src URL.
const api = useApi()
const qrUrl = ref<string | null>(null)
let qrAbort: AbortController | null = null

async function loadQr(amount: number) {
  qrAbort?.abort()
  const abort = new AbortController()
  qrAbort = abort
  const prevUrl = qrUrl.value
  qrUrl.value = null
  if (prevUrl) URL.revokeObjectURL(prevUrl)
  if (!txRef.value) txRef.value = generateTxRef()
  try {
    const blob = await api<Blob>('/api/payments/epc-qr.png', {
      query: { amountCents: amount, ref: txRef.value },
      responseType: 'blob',
      signal: abort.signal,
    })
    qrUrl.value = URL.createObjectURL(blob as Blob)
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
  if (e.target === e.currentTarget && stage.value !== 'card' && stage.value !== 'paypal') emit('close')
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
            <div v-if="txRef" class="tx-ref">
              Transaktions-ID: <strong>#{{ txRef }}</strong>
            </div>
          </div>

          <button class="paypal-open-btn" @click="openPaypal">
            <span class="paypal-pp">PP</span>
            mit PayPal zahlen
          </button>

          <div v-if="cardError" style="margin-top:10px;color:var(--bad);font-size:13px">{{ cardError }}</div>
        </div>
        <div class="modal-f">
          <button class="btn ghost" @click="setStage('choose')" :disabled="cardWaiting">Abbrechen</button>
          <button class="btn ok" @click="finishCard" :disabled="cardWaiting">
            {{ cardWaiting ? 'Buchen…' : 'Zahlung erhalten' }}
          </button>
        </div>
      </template>

      <!-- paypal stage -->
      <template v-else-if="stage === 'paypal'">
        <div class="modal-h">
          <h3>
            <span class="paypal-logo-text"><span class="pp-dark">Pay</span><span class="pp-light">Pal</span></span>
          </h3>
          <p>QR-Code mit der PayPal-App scannen.</p>
        </div>
        <div class="modal-b">
          <div class="paypal-card">
            <div class="lab">Zu zahlen</div>
            <div class="v">{{ formatEUR(totalCents) }}</div>
            <div style="margin-top:18px;display:flex;justify-content:center">
              <div class="paypal-qr-wrap">
                <img v-if="paypalQrDataUrl" :src="paypalQrDataUrl" alt="PayPal QR" width="280" height="280" />
                <span v-else class="status"><span class="pulse"></span>QR wird erzeugt…</span>
              </div>
            </div>
            <div class="paypal-link">{{ paypalUrl }}</div>
            <div class="status" style="margin-top:10px;font-size:12px;opacity:.75">
              Kein Unternehmenskonto — privat via PayPal.me
            </div>
          </div>
          <div v-if="cardError" style="margin-top:10px;color:var(--bad);font-size:13px">{{ cardError }}</div>
        </div>
        <div class="modal-f">
          <button class="btn ghost" @click="setStage('card')" :disabled="cardWaiting">← Zurück</button>
          <button class="btn ok" @click="finishPaypal" :disabled="cardWaiting">
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
            {{ finished.method === 'BAR' ? 'Bar' : finished.method === 'PAYPAL' ? 'PayPal' : 'Karte' }} · {{ formatEUR(finished.totalCents) }} · Vielen Dank!
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
            <div class="r"><span>Zahlung</span><span>{{ finished.method === 'BAR' ? 'Bar' : finished.method === 'PAYPAL' ? 'PayPal' : 'Karte' }}</span></div>
            <div class="r tx"><span>Transaktions-ID</span><span>#{{ finished.transactionRef }}</span></div>
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

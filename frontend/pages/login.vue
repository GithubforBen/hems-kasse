<script setup lang="ts">
import { readLoginPrefill } from '~/utils/loginLink'

definePageMeta({ layout: false })

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const role = ref<'VERKAUF' | 'ADMIN'>('VERKAUF')
const name = ref('')
const gruppe = ref('')
/** Kept as a string so a half-typed or invalid entry never becomes a silent 0/NaN. */
const abrechnungNr = ref('')
const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)

const MAX_ABRECHNUNG_NR = 999999

/** Whole positive number only — "12a", "1.5", "-3" and "" all come back null. */
const parsedAbrechnungNr = computed(() => {
  const raw = abrechnungNr.value.trim()
  if (!/^\d{1,6}$/.test(raw)) return null
  const n = Number(raw)
  return n >= 1 && n <= MAX_ABRECHNUNG_NR ? n : null
})

/**
 * Why a session was sent back here. Fixed codes rather than a free-text message, so a
 * crafted link cannot put arbitrary wording in front of a cashier.
 */
const REASONS: Record<string, string> = {
  abrechnung: 'Diese Abrechnung ist abgeschlossen oder wird an einer anderen Kassette geführt. '
    + 'Bitte mit der Nummer des nächsten Umschlags neu anmelden.',
  abgeschlossen: 'Abrechnung abgeschlossen. Für die nächste Schicht bitte mit der Nummer des '
    + 'nächsten Umschlags anmelden.',
}

/** Scanned from a Passwort-Zettel: fills in role, Gruppe and password. */
const fromSlip = ref(false)

onMounted(() => {
  const reason = route.query.reason
  if (typeof reason === 'string' && REASONS[reason]) error.value = REASONS[reason]!
  applyPrefill()
})

function applyPrefill() {
  const prefill = readLoginPrefill(window.location.hash)
  if (!prefill) return
  role.value = prefill.r
  password.value = prefill.p
  if (prefill.r === 'VERKAUF') gruppe.value = prefill.n
  else name.value = prefill.n
  fromSlip.value = true
  // Keep the password out of the address bar, the tab title and any link the user might copy.
  history.replaceState(null, '', window.location.pathname + window.location.search)
  // Everything except who is standing at the till is filled in now.
  nextTick(() => document.getElementById('login-name')?.focus())
}

async function submit() {
  if (busy.value) return
  if (!name.value.trim()) return
  if (!password.value) {
    error.value = 'Bitte Passwort eingeben.'
    return
  }
  if (role.value === 'VERKAUF' && !gruppe.value.trim()) {
    error.value = 'Bitte Gruppe eingeben.'
    return
  }
  if (role.value === 'VERKAUF' && parsedAbrechnungNr.value === null) {
    error.value = `Bitte die Abrechnungs-Nr. vom Umschlag eingeben (ganze Zahl zwischen 1 und ${MAX_ABRECHNUNG_NR}).`
    return
  }

  busy.value = true
  error.value = null
  try {
    await auth.login({
      role: role.value,
      name: name.value.trim(),
      gruppe: role.value === 'VERKAUF' ? gruppe.value.trim() : undefined,
      abrechnungNr: role.value === 'VERKAUF' ? parsedAbrechnungNr.value! : undefined,
      password: password.value,
    })
    const next = typeof route.query.next === 'string' ? route.query.next : null
    await router.replace(next && next.startsWith('/') ? next : (auth.user?.role === 'ADMIN' ? '/admin' : '/'))
  } catch (e: any) {
    if (e?.response?.status === 401) error.value = 'Anmeldung fehlgeschlagen. Falsche Zugangsdaten.'
    else if (e?.response?.status === 409) error.value = e?.data?.message ?? 'Diese Abrechnungs-Nr. ist nicht mehr frei.'
    else if (e?.response?.status === 400) error.value = e?.data?.message ?? 'Ungültige Eingabe.'
    else if (e?.response?.status === 429) error.value = e?.data?.message ?? 'Zu viele Fehlversuche. Bitte kurz warten.'
    else error.value = 'Server nicht erreichbar.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand">
        <div class="brand-mark">K</div>
        <div class="brand-text">
          <div class="t1">Schulkasse</div>
          <div class="t2">Kuchenverkauf · Anmeldung</div>
        </div>
      </div>

      <div v-if="fromSlip" class="slip-hint">
        📄 Vom Passwort-Zettel übernommen. Bitte nur noch Namen und Abrechnungs-Nr. eintragen.
      </div>

      <div style="margin-bottom:12px">
        <label class="label">Name</label>
        <input
          id="login-name"
          class="input"
          autofocus
          autocomplete="name"
          v-model="name"
          @keydown.enter="submit"
          placeholder="z. B. Lena Müller" />
      </div>

      <div v-if="role === 'VERKAUF'" style="margin-bottom:12px">
        <label class="label">Gruppe</label>
        <input
          class="input"
          v-model="gruppe"
          @keydown.enter="submit"
          placeholder="z. B. 1" />
      </div>

      <div v-if="role === 'VERKAUF'" style="margin-bottom:12px">
        <label class="label">Abrechnungs-Nr.</label>
        <input
          class="input"
          v-model="abrechnungNr"
          type="text"
          inputmode="numeric"
          autocomplete="off"
          maxlength="6"
          @keydown.enter="submit"
          placeholder="Nummer auf dem Umschlag, z. B. 7" />
        <div style="font-size:12px;color:var(--ink-3);margin-top:4px">
          Alle Verkäufe dieser Schicht werden auf diesen Umschlag abgerechnet.
        </div>
      </div>

      <div style="margin-bottom:14px">
        <label class="label">Passwort</label>
        <input
          class="input"
          type="password"
          autocomplete="current-password"
          v-model="password"
          @keydown.enter="submit"
          :placeholder="role === 'VERKAUF' ? 'Gruppenpasswort' : 'Admin-Passwort'" />
      </div>

      <div>
        <label class="label">Anmelden als</label>
        <div class="role-pick">
          <label :class="role === 'VERKAUF' ? 'sel' : ''">
            <input type="radio" name="role" value="VERKAUF" v-model="role" />
            <span>👋 Verkäufer:in</span>
          </label>
          <label :class="role === 'ADMIN' ? 'sel' : ''">
            <input type="radio" name="role" value="ADMIN" v-model="role" />
            <span>🔑 Admin</span>
          </label>
        </div>
      </div>

      <button class="btn big" style="margin-top:18px" @click="submit" :disabled="busy">
        {{ busy ? 'Anmelden…' : 'Schicht beginnen →' }}
      </button>

      <div v-if="error" style="margin-top:14px;color:var(--bad);font-size:13px">{{ error }}</div>
    </div>
  </div>
</template>

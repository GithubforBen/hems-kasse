<script setup lang="ts">
import QRCode from 'qrcode'
import type { SlipDto } from '~/types/api'
import { buildLoginUrl } from '~/utils/loginLink'

const props = defineProps<{ slips: SlipDto[] }>()
const emit = defineEmits<{ close: [] }>()

/** Slip id → QR data URL. Rendered client-side so the URL matches the host the admin is on. */
const qrCodes = ref<Record<string, string>>({})
const qrFailed = ref(false)

const origin = computed(() => (import.meta.client ? window.location.origin : ''))

function loginUrl(s: SlipDto) {
  return buildLoginUrl(origin.value, { r: s.role, n: s.name, p: s.password })
}

async function renderCodes() {
  const out: Record<string, string> = {}
  try {
    for (const s of props.slips) {
      out[s.id] = await QRCode.toDataURL(loginUrl(s), {
        width: 320,
        margin: 1,
        errorCorrectionLevel: 'M',
        color: { dark: '#000000', light: '#ffffff' },
      })
    }
    qrCodes.value = out
    qrFailed.value = false
  } catch {
    // The password is printed in text as well, so a missing QR is inconvenient, not fatal.
    qrFailed.value = true
  }
}

onMounted(renderCodes)
watch(() => props.slips, renderCodes)

function printSheet() {
  if (import.meta.client) window.print()
}

const today = new Intl.DateTimeFormat('de-DE', { dateStyle: 'long' }).format(new Date())
const roleLabel = (r: string) => (r === 'ADMIN' ? 'Admin' : 'Gruppe')
</script>

<template>
  <div class="slips-overlay" @click.self="emit('close')">
    <div class="slips-modal">
      <div class="slips-bar">
        <div>
          <strong>Passwort-Zettel</strong>
          <span style="color:var(--ink-3);font-size:12.5px;margin-left:8px">
            {{ props.slips.length }} {{ props.slips.length === 1 ? 'Zettel' : 'Zettel' }}
          </span>
        </div>
        <div style="display:flex;gap:8px">
          <button class="btn" @click="printSheet">🖨️ Drucken</button>
          <button class="btn ghost" @click="emit('close')">Schließen</button>
        </div>
      </div>

      <p class="slips-note">
        Der QR-Code öffnet die Kasse mit vorausgefüllter Gruppe und Passwort — an der Kasse müssen
        nur noch Name und Abrechnungs-Nr. eingetragen werden. Zettel bitte nicht offen liegen lassen.
      </p>
      <p v-if="qrFailed" class="slips-note" style="color:var(--bad)">
        Die QR-Codes konnten nicht erzeugt werden. Das Passwort steht unten trotzdem lesbar auf jedem Zettel.
      </p>

      <div class="slips-sheet">
        <div v-for="s in props.slips" :key="s.id" class="slip">
          <div class="slip-head">
            <!-- The role lives in the kicker, so a Gruppe literally called "Gruppe Süd"
                 does not end up titled "Gruppe Gruppe Süd". -->
            <div class="slip-brand">Schulkasse · {{ roleLabel(s.role) }}</div>
            <div class="slip-title">{{ s.name }}</div>
          </div>

          <div class="slip-body">
            <img v-if="qrCodes[s.id]" class="slip-qr" :src="qrCodes[s.id]"
                 :alt="`Login-QR für ${s.name}`" />
            <div v-else class="slip-qr slip-qr-empty">kein QR</div>

            <div class="slip-creds">
              <div class="slip-field">
                <span class="slip-label">{{ s.role === 'ADMIN' ? 'Benutzer' : 'Gruppe' }}</span>
                <span class="slip-value">{{ s.name }}</span>
              </div>
              <div v-if="!s.active" class="slip-inactive">Konto ist deaktiviert</div>
            </div>
          </div>

          <!-- Full width and on its own line: a password broken across two lines gets mistyped. -->
          <div class="slip-field slip-pw-block">
            <span class="slip-label">Passwort</span>
            <span class="slip-pw">{{ s.password }}</span>
          </div>

          <div class="slip-foot">
            <span>{{ origin.replace(/^https?:\/\//, '') }}</span>
            <span>{{ today }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

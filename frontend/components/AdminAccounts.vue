<script setup lang="ts">
import type { AccountDto, Role, SlipDto } from '~/types/api'

const accounts = useAccountsStore()
const toast = useToastStore()

const newGruppe = ref('')
const newAdmin = ref('')
const newAdminPassword = ref('')
const busy = ref(false)
const error = ref<string | null>(null)

/** Slips handed to the print sheet. Empty means the sheet is closed. */
const slips = ref<SlipDto[]>([])

onMounted(() => { accounts.fetch().catch(() => {}) })

function fail(e: any, fallback: string) {
  error.value = e?.data?.message ?? fallback
  toast.show(error.value!)
}

async function guard(fn: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = null
  try { await fn() } finally { busy.value = false }
}

async function addGruppe() {
  const name = newGruppe.value.trim()
  if (!name) return
  await guard(async () => {
    try {
      const slip = await accounts.create('VERKAUF', name)
      newGruppe.value = ''
      slips.value = [slip]
      toast.show(`Gruppe „${slip.name}" angelegt`)
    } catch (e: any) { fail(e, 'Gruppe konnte nicht angelegt werden.') }
  })
}

async function addAdmin() {
  const name = newAdmin.value.trim()
  if (!name) return
  await guard(async () => {
    try {
      const slip = await accounts.create('ADMIN', name, newAdminPassword.value.trim() || undefined)
      newAdmin.value = ''
      newAdminPassword.value = ''
      slips.value = [slip]
      toast.show(`Admin „${slip.name}" angelegt`)
    } catch (e: any) { fail(e, 'Admin konnte nicht angelegt werden.') }
  })
}

async function rename(a: AccountDto, name: string) {
  const trimmed = name.trim()
  if (!trimmed || trimmed === a.name) return
  await guard(async () => {
    try { await accounts.patch(a.id, { name: trimmed }) }
    catch (e: any) { fail(e, 'Name konnte nicht geändert werden.'); await accounts.fetch() }
  })
}

async function toggleActive(a: AccountDto, active: boolean) {
  await guard(async () => {
    try { await accounts.patch(a.id, { active }) }
    catch (e: any) { fail(e, 'Konto konnte nicht geändert werden.'); await accounts.fetch() }
  })
}

async function newPassword(a: AccountDto) {
  const label = a.role === 'VERKAUF' ? 'Gruppe' : 'Admin'
  if (!confirm(`Neues Passwort für ${label} „${a.name}" erzeugen?\n\n`
    + `Das bisherige Passwort gilt sofort nicht mehr. Bereits ausgeteilte Zettel werden ungültig.`)) return
  await guard(async () => {
    try {
      const slip = await accounts.setPassword(a.id)
      slips.value = [slip]
      toast.show(`Neues Passwort für „${slip.name}"`)
    } catch (e: any) { fail(e, 'Passwort konnte nicht erzeugt werden.') }
  })
}

async function setOwnPassword(a: AccountDto) {
  const entered = prompt(`Passwort für „${a.name}" festlegen (mindestens 4 Zeichen):`)
  if (entered === null) return
  await guard(async () => {
    try {
      const slip = await accounts.setPassword(a.id, entered)
      slips.value = [slip]
      toast.show(`Passwort für „${slip.name}" gesetzt`)
    } catch (e: any) { fail(e, 'Passwort konnte nicht gesetzt werden.') }
  })
}

async function remove(a: AccountDto) {
  const label = a.role === 'VERKAUF' ? 'Gruppe' : 'Admin'
  if (!confirm(`${label} „${a.name}" wirklich löschen?\n\n`
    + `Bereits abgeschlossene Schichten bleiben erhalten — sie speichern den Namen als Text.`)) return
  await guard(async () => {
    try { await accounts.remove(a.id); toast.show(`„${a.name}" gelöscht`) }
    catch (e: any) { fail(e, 'Konto konnte nicht gelöscht werden.') }
  })
}

async function printAll(role?: Role) {
  await guard(async () => {
    try {
      const all = await accounts.slips()
      const wanted = role ? all.filter(s => s.role === role) : all
      if (!wanted.length) { toast.show('Keine Konten zum Drucken.'); return }
      slips.value = wanted
    } catch (e: any) { fail(e, 'Zettel konnten nicht geladen werden.') }
  })
}

async function printOne(a: AccountDto) {
  await guard(async () => {
    try {
      const found = (await accounts.slips([a.id]))[0]
      if (found) slips.value = [found]
    } catch (e: any) { fail(e, 'Zettel konnte nicht geladen werden.') }
  })
}
</script>

<template>
  <div class="admin admin-accounts">
    <div class="side" style="grid-column:1 / -1">
      <h4>Gruppen</h4>
      <p style="font-size:12.5px;color:var(--ink-3);margin:0 8px 8px">
        Jede Gruppe ist ein Login für den Verkauf. Beim Anlegen wird automatisch ein Passwort
        erzeugt und der Passwort-Zettel geöffnet.
      </p>

      <div v-for="a in accounts.gruppen" :key="a.id" class="cat-item">
        <span class="swatch" :class="a.active ? 'sw-mint' : ''"
              :style="a.active ? '' : 'background:var(--ink-3);opacity:.35'"></span>
        <input class="nm" :value="a.name"
               @change="(e) => rename(a, (e.target as HTMLInputElement).value)" />
        <button class="btn ghost acct-btn" :disabled="busy" @click="printOne(a)" title="Passwort-Zettel drucken">📄 Zettel</button>
        <button class="btn ghost acct-btn" :disabled="busy" @click="newPassword(a)" title="Neues Passwort erzeugen">🔑 Neu</button>
        <label class="acct-active">
          <input type="checkbox" :checked="a.active" :disabled="busy"
                 @change="(e) => toggleActive(a, (e.target as HTMLInputElement).checked)" />
          aktiv
        </label>
        <button class="del-x" :disabled="busy" @click.stop="remove(a)" title="Gruppe löschen">✕</button>
      </div>

      <div class="add-cat">
        <input v-model="newGruppe" @keydown.enter="addGruppe" placeholder='Neue Gruppe, z. B. „3"…' />
        <button class="btn" :disabled="busy" @click="addGruppe">+</button>
      </div>

      <div class="acct-actions">
        <button class="btn ghost" :disabled="busy" @click="printAll('VERKAUF')">📄 Zettel für alle Gruppen</button>
      </div>

      <h4 style="margin-top:22px">Admins</h4>
      <p style="font-size:12.5px;color:var(--ink-3);margin:0 8px 8px">
        Persönliche Logins für den Admin-Bereich. Das letzte aktive Admin-Konto lässt sich weder
        löschen noch deaktivieren.
      </p>

      <div v-for="a in accounts.admins" :key="a.id" class="cat-item">
        <span class="swatch" :class="a.active ? 'sw-lavender' : ''"
              :style="a.active ? '' : 'background:var(--ink-3);opacity:.35'"></span>
        <input class="nm" :value="a.name"
               @change="(e) => rename(a, (e.target as HTMLInputElement).value)" />
        <button class="btn ghost acct-btn" :disabled="busy" @click="printOne(a)" title="Passwort-Zettel drucken">📄 Zettel</button>
        <button class="btn ghost acct-btn" :disabled="busy" @click="setOwnPassword(a)" title="Passwort festlegen">✏️ Setzen</button>
        <button class="btn ghost acct-btn" :disabled="busy" @click="newPassword(a)" title="Neues Passwort erzeugen">🔑 Neu</button>
        <label class="acct-active">
          <input type="checkbox" :checked="a.active" :disabled="busy"
                 @change="(e) => toggleActive(a, (e.target as HTMLInputElement).checked)" />
          aktiv
        </label>
        <button class="del-x" :disabled="busy" @click.stop="remove(a)" title="Admin löschen">✕</button>
      </div>

      <div class="add-cat">
        <input v-model="newAdmin" @keydown.enter="addAdmin" placeholder="Neuer Admin, z. B. „frau-mueller“…" />
        <input v-model="newAdminPassword" @keydown.enter="addAdmin"
               placeholder="Passwort (leer = erzeugen)" style="max-width:210px" />
        <button class="btn" :disabled="busy" @click="addAdmin">+</button>
      </div>

      <div class="acct-actions">
        <button class="btn ghost" :disabled="busy" @click="printAll()">📄 Zettel für alle Konten</button>
      </div>

      <div v-if="error" style="margin:12px 8px 0;color:var(--bad);font-size:13px">{{ error }}</div>
    </div>

    <PasswordSlips v-if="slips.length" :slips="slips" @close="slips = []" />
  </div>
</template>

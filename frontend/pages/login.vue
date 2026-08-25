<script setup lang="ts">
definePageMeta({ layout: false })

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const role = ref<'VERKAUF' | 'ADMIN'>('VERKAUF')
const name = ref('')
const gruppe = ref('')
const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)

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

  busy.value = true
  error.value = null
  try {
    await auth.login({
      role: role.value,
      name: name.value.trim(),
      gruppe: role.value === 'VERKAUF' ? gruppe.value.trim() : undefined,
      password: password.value,
    })
    const next = typeof route.query.next === 'string' ? route.query.next : null
    await router.replace(next && next.startsWith('/') ? next : (auth.user?.role === 'ADMIN' ? '/admin' : '/'))
  } catch (e: any) {
    if (e?.response?.status === 401) error.value = 'Anmeldung fehlgeschlagen. Falsche Zugangsdaten.'
    else if (e?.response?.status === 400) error.value = e?.data?.message ?? 'Ungültige Eingabe.'
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

      <div style="margin-bottom:12px">
        <label class="label">Name</label>
        <input
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

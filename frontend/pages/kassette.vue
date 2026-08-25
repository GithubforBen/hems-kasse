<script setup lang="ts">
definePageMeta({ layout: false })

const auth = useAuthStore()
const register = useRegisterStore()
const router = useRouter()
const route = useRoute()

const busy = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  try { await register.fetch() } catch {}
})

async function pick(id: string) {
  if (busy.value) return
  busy.value = true
  error.value = null
  try {
    register.select(id)
    const next = typeof route.query.next === 'string' ? route.query.next : '/'
    await router.replace(next.startsWith('/') ? next : '/')
  } catch {
    error.value = 'Kassette konnte nicht ausgewählt werden.'
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
          <div class="t1">Kassette wählen</div>
          <div class="t2">{{ auth.user?.name }} · {{ auth.user?.gruppe }}</div>
        </div>
      </div>

      <p style="font-size:13px;color:var(--ink-3);margin:0 0 14px">
        Jede Kassette führt ihre eigene Schicht mit eigener Kasseneinzählung.
      </p>

      <div v-if="register.active.length === 0" style="font-size:13px;color:var(--ink-3)">
        Noch keine Kassetten eingerichtet. Bitte ein Admin-Konto im Admin-Menü unter „Kassetten" anlegen.
      </div>

      <div class="role-pick" style="grid-template-columns:1fr">
        <label
          v-for="r in register.active"
          :key="r.id"
          @click="pick(r.id)">
          <span style="font-size:18px">🗄️</span>
          <span style="font-weight:600">{{ r.name }}</span>
        </label>
      </div>

      <div v-if="error" style="margin-top:14px;color:var(--bad);font-size:13px">{{ error }}</div>
    </div>
  </div>
</template>

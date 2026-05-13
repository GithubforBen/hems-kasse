<script setup lang="ts">
const auth = useAuthStore()
const pref = usePrefStore()

const route = useRoute()
const clock = ref(new Date())
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => { clock.value = new Date() }, 15_000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })

const time = computed(() =>
  clock.value.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
)

const isAdmin = computed(() => auth.user?.role === 'ADMIN')

function activeIf(prefix: string) {
  return route.path === prefix || (prefix !== '/' && route.path.startsWith(prefix))
}

async function logout() {
  await auth.logout()
}
</script>

<template>
  <div class="topbar">
    <div class="brand">
      <div class="brand-mark">K</div>
      <div class="brand-text">
        <div class="t1">Schulkasse</div>
        <div class="t2">Kuchenverkauf</div>
      </div>
    </div>

    <div class="tabs">
      <NuxtLink to="/" class="tab" :class="{ active: activeIf('/') && route.path === '/' }">
        {{ pref.theme === 'farm' ? '› POS' : 'Kasse' }}
      </NuxtLink>
      <NuxtLink to="/abschluss" class="tab" :class="{ active: activeIf('/abschluss') }">
        {{ pref.theme === 'farm' ? '› SHIFT' : 'Abschluss' }}
      </NuxtLink>
      <NuxtLink v-if="isAdmin" to="/admin" class="tab" :class="{ active: activeIf('/admin') }">
        {{ pref.theme === 'farm' ? '› ADMIN' : 'Admin' }}
      </NuxtLink>
    </div>

    <div class="right">
      <button
        class="theme-toggle"
        @click="pref.toggle()"
        :title="pref.theme === 'farm' ? 'Standard-Ansicht' : 'Farm-Modus (Terminal)'">
        <span class="dot"></span>
        {{ pref.theme === 'farm' ? 'FARM' : 'Farm-Modus' }}
      </button>
      <span class="clock">{{ time }}</span>
      <div class="user-pill" v-if="auth.user">
        <div class="avatar">{{ (auth.user.name || '?')[0]!.toUpperCase() }}</div>
        <span style="font-size:13px;font-weight:550;color:var(--ink)">{{ auth.user.name }}</span>
        <span v-if="auth.user.klasse" style="color:var(--ink-3);font-size:12px">{{ auth.user.klasse }}</span>
      </div>
      <button class="btn ghost" @click="logout" title="Abmelden" style="padding:6px 10px">Abmelden</button>
    </div>
  </div>
</template>

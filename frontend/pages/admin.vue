<script setup lang="ts">
const shift = useShiftStore()
const filters = reactive({ q: '', klasse: '' })

async function refreshHistory() {
  await shift.fetchAll({
    q: filters.q || undefined,
    klasse: filters.klasse || undefined,
  })
}

onMounted(refreshHistory)

const tab = ref<'catalog' | 'shifts'>('catalog')
</script>

<template>
  <div class="app">
    <TopBar />

    <div style="display:flex;gap:8px;padding:14px 22px 0">
      <button
        class="btn"
        :class="tab === 'catalog' ? '' : 'ghost'"
        @click="tab = 'catalog'">Kategorien & Produkte</button>
      <button
        class="btn"
        :class="tab === 'shifts' ? '' : 'ghost'"
        @click="tab = 'shifts'">Schichten · Alle</button>
    </div>

    <AdminCatalog v-if="tab === 'catalog'" />

    <div v-else class="scroll-y" style="padding:18px 22px 28px">
      <div class="card-box">
        <h3>
          <span>Schichten · Alle</span>
          <span class="meta">{{ shift.all.length }} archiviert</span>
        </h3>

        <div style="display:flex;gap:10px;margin-bottom:14px;flex-wrap:wrap">
          <input
            class="input"
            style="max-width:220px"
            v-model="filters.q"
            placeholder="Nach Name suchen…"
            @keydown.enter="refreshHistory" />
          <input
            class="input"
            style="max-width:160px"
            v-model="filters.klasse"
            placeholder="Klasse, z. B. BG12e"
            @keydown.enter="refreshHistory" />
          <button class="btn" @click="refreshHistory">Filtern</button>
        </div>

        <ShiftHistoryList
          :shifts="shift.all"
          :show-operator="true"
          empty-text="Es wurden noch keine Schichten abgeschlossen." />
      </div>
    </div>
  </div>
</template>

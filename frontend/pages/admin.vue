<script setup lang="ts">
const shift = useShiftStore()
const register = useRegisterStore()
const filters = reactive({ q: '', klasse: '', registerId: '' })

async function refreshHistory() {
  await shift.fetchAll({
    q: filters.q || undefined,
    klasse: filters.klasse || undefined,
    registerId: filters.registerId || undefined,
  })
}

onMounted(() => {
  refreshHistory()
  register.fetch().catch(() => {})
})

const tab = ref<'catalog' | 'registers' | 'shifts'>('catalog')
</script>

<template>
  <div class="app">
    <TopBar />

    <div style="display:flex;flex-direction:column;min-height:0;overflow:hidden">
      <div style="display:flex;gap:8px;padding:14px 22px 0;flex-shrink:0">
        <button
          class="btn"
          :class="tab === 'catalog' ? '' : 'ghost'"
          style="padding:7px 14px;font-size:13px;white-space:nowrap;flex-shrink:0"
          @click="tab = 'catalog'">Kategorien & Produkte</button>
        <button
          class="btn"
          :class="tab === 'registers' ? '' : 'ghost'"
          style="padding:7px 14px;font-size:13px;white-space:nowrap;flex-shrink:0"
          @click="tab = 'registers'">Kassetten</button>
        <button
          class="btn"
          :class="tab === 'shifts' ? '' : 'ghost'"
          style="padding:7px 14px;font-size:13px;white-space:nowrap;flex-shrink:0"
          @click="tab = 'shifts'">Schichten · Alle</button>
      </div>

      <div style="flex:1;min-height:0;overflow:hidden;display:flex;flex-direction:column">
        <AdminCatalog v-if="tab === 'catalog'" />
        <AdminRegisters v-else-if="tab === 'registers'" />

        <div v-else class="scroll-y" style="padding:18px 22px 28px;flex:1">
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
              <select class="input" style="max-width:170px" v-model="filters.registerId" @change="refreshHistory">
                <option value="">Alle Kassetten</option>
                <option v-for="r in register.all" :key="r.id" :value="r.id">{{ r.name }}</option>
              </select>
              <button class="btn" @click="refreshHistory">Filtern</button>
            </div>

            <div v-if="shift.all.length > 0" style="margin-bottom:12px">
              <ExportButtons
                path="/api/shifts/export.csv"
                :types="['shifts', 'items', 'products', 'sales']"
                :filters="{
                  klasse: filters.klasse || undefined,
                  registerId: filters.registerId || undefined,
                  q: filters.q || undefined,
                }" />
            </div>

            <ShiftHistoryList
              :shifts="shift.all"
              :show-operator="true"
              empty-text="Es wurden noch keine Schichten abgeschlossen." />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

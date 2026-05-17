<script setup lang="ts">
const shift = useShiftStore()

onMounted(async () => {
  await shift.fetchMine()
})
</script>

<template>
  <div class="app">
    <TopBar />

    <div class="scroll-y">
      <Report />

      <div style="padding:0 22px 28px">
        <div class="card-box">
          <h3>
            <span>Meine Schichten</span>
            <span class="meta">{{ shift.mine.length }} archiviert</span>
          </h3>
          <div v-if="shift.mine.length > 0" style="margin-bottom:12px">
            <ExportButtons
              path="/api/shifts/mine/export.csv"
              :types="['shifts', 'items', 'products', 'sales']" />
          </div>
          <ShiftHistoryList
            :shifts="shift.mine"
            empty-text="Noch keine archivierten Schichten. Schließe diese Schicht ab, um sie hier zu sehen." />
        </div>
      </div>
    </div>
  </div>
</template>

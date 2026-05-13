<script setup lang="ts">
const shift = useShiftStore()

onMounted(async () => {
  await shift.fetchMine()
})
</script>

<template>
  <div class="app">
    <TopBar />

    <Report />

    <div class="scroll-y" style="padding:0 22px 28px">
      <div class="card-box">
        <h3>
          <span>Meine Schichten</span>
          <span class="meta">{{ shift.mine.length }} archiviert</span>
        </h3>
        <ShiftHistoryList
          :shifts="shift.mine"
          empty-text="Noch keine archivierten Schichten. Schließe diese Schicht ab, um sie hier zu sehen." />
      </div>
    </div>
  </div>
</template>

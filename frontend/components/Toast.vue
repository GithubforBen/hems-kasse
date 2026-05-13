<script setup lang="ts">
const t = useToastStore()
</script>

<template>
  <div v-if="t.message" class="toast">{{ t.message }}</div>
</template>

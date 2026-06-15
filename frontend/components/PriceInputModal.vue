<script setup lang="ts">
import type { ProductDto } from '~/types/api'

const props = defineProps<{ product: ProductDto | null }>()
const emit = defineEmits<{
  confirm: [priceCents: number]
  cancel: []
}>()

const rawEur = ref('')

watch(() => props.product, (p) => {
  if (p) rawEur.value = ''
})

const priceCents = computed(() => {
  const n = parseFloat(rawEur.value.replace(',', '.'))
  return isNaN(n) ? 0 : Math.round(n * 100)
})

function confirm() {
  if (priceCents.value <= 0) return
  emit('confirm', priceCents.value)
  rawEur.value = ''
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter') confirm()
  if (e.key === 'Escape') emit('cancel')
}
</script>

<template>
  <div v-if="product" class="modal-bg" @mousedown.self="emit('cancel')">
    <div class="modal price-modal">
      <div class="modal-h">
        <h3>{{ product.name }}</h3>
        <p>Preis eingeben</p>
      </div>
      <div class="modal-b">
        <div class="price-field">
          <span class="price-unit">€</span>
          <input
            class="price-input"
            type="text"
            inputmode="decimal"
            placeholder="0,00"
            v-model="rawEur"
            @keydown="onKey"
            autofocus />
        </div>
      </div>
      <div class="modal-f">
        <button class="btn ghost" @click="emit('cancel')">Abbrechen</button>
        <button class="btn ok" :disabled="priceCents <= 0" @click="confirm">Hinzufügen</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.price-modal { width: min(340px, 100%) }
.price-field {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--paper-2);
  border: 1px solid var(--line-2);
  border-radius: var(--r-lg);
  padding: 10px 16px;
}
.price-field:focus-within {
  border-color: var(--accent);
  background: var(--paper);
}
.price-unit {
  font-size: 22px;
  font-weight: 700;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}
.price-input {
  flex: 1;
  background: none;
  border: 0;
  outline: none;
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  width: 100%;
  font-family: var(--font-num);
}
</style>

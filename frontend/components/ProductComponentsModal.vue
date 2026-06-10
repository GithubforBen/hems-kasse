<script setup lang="ts">
import type { ProductDto } from '~/types/api'

const props = defineProps<{
  product: ProductDto
  allProducts: ProductDto[]
}>()

const emit = defineEmits<{
  close: []
}>()

const catalog = useCatalogStore()
const toast = useToastStore()
const saving = ref(false)

interface Line { componentProductId: string; name: string; qty: number }

const lines = ref<Line[]>(
  props.product.components
    .filter(c => c.productId)
    .map(c => ({ componentProductId: c.productId as string, name: c.name, qty: c.qty }))
)

const pickerId = ref('')

const pickable = computed(() =>
  props.allProducts
    .filter(p => p.id !== props.product.id)
    .filter(p => !p.variable)
    .filter(p => !lines.value.some(l => l.componentProductId === p.id))
    .sort((a, b) => a.name.localeCompare(b.name))
)

function addLine() {
  const p = pickable.value.find(x => x.id === pickerId.value)
  if (!p) return
  lines.value.push({ componentProductId: p.id, name: p.name, qty: 1 })
  pickerId.value = ''
}

function removeLine(id: string) {
  lines.value = lines.value.filter(l => l.componentProductId !== id)
}

function inc(id: string, delta: number) {
  const l = lines.value.find(x => x.componentProductId === id)
  if (!l) return
  l.qty = Math.max(1, l.qty + delta)
}

const preview = computed(() =>
  lines.value.length === 0
    ? 'Einfaches Produkt – kein Bestandteil ausgewählt'
    : lines.value.map(l => `${l.qty}× ${l.name}`).join(' + ')
)

async function save() {
  saving.value = true
  try {
    await catalog.setComponents(props.product.id, lines.value.map(l => ({ componentProductId: l.componentProductId, qty: l.qty })))
    toast.show(lines.value.length ? 'Verkaufstaste gespeichert' : 'Wieder einfaches Produkt')
    emit('close')
  } catch (e: any) {
    toast.show(`Fehler: ${e?.statusMessage ?? e?.message ?? 'Unbekannt'}`)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-bg" @mousedown.self="emit('close')">
    <div class="modal wide">
      <div class="modal-h">
        <h3>Verkaufstaste: {{ product.name }}</h3>
        <p>Diese Taste kann beim Verkauf mehrere Produkte gleichzeitig abbuchen, z.B. „2-für-1 Red Bull“.</p>
      </div>
      <div class="modal-b">
        <div class="pcm-preview">{{ preview }}</div>

        <div v-if="lines.length" class="pcm-lines">
          <div v-for="l in lines" :key="l.componentProductId" class="pcm-line">
            <span class="pcm-name">{{ l.name }}</span>
            <div class="pcm-qty">
              <button class="btn ghost" @click="inc(l.componentProductId, -1)">−</button>
              <span class="pcm-qty-v">{{ l.qty }}×</span>
              <button class="btn ghost" @click="inc(l.componentProductId, 1)">+</button>
            </div>
            <button class="btn ghost pcm-rm" @click="removeLine(l.componentProductId)" title="Entfernen">✕</button>
          </div>
        </div>

        <div class="pcm-add">
          <select v-model="pickerId" class="pcm-sel">
            <option value="" disabled>Produkt hinzufügen…</option>
            <option v-for="p in pickable" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
          <button class="btn secondary" :disabled="!pickerId" @click="addLine">+ hinzufügen</button>
        </div>
      </div>
      <div class="modal-f">
        <button class="btn ghost" @click="emit('close')">Abbrechen</button>
        <button class="btn ok" :disabled="saving" @click="save">
          {{ saving ? 'Speichern…' : 'Speichern' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pcm-preview {
  padding: 10px 14px;
  border-radius: var(--r-sm);
  background: var(--paper-2);
  border: 1px solid var(--line-2);
  font-size: 14px;
  color: var(--ink-2);
  margin-bottom: 14px;
}
.pcm-lines {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}
.pcm-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: var(--r-xs);
  background: var(--paper-2);
  border: 1px solid var(--line-2);
}
.pcm-name { flex: 1; font-weight: 600; }
.pcm-qty {
  display: flex;
  align-items: center;
  gap: 6px;
}
.pcm-qty-v { min-width: 32px; text-align: center; font-variant-numeric: tabular-nums; }
.pcm-rm { padding: 2px 8px; }
.pcm-add {
  display: flex;
  gap: 8px;
}
.pcm-sel {
  flex: 1;
  padding: 8px 10px;
  border-radius: var(--r-xs);
  border: 1px solid var(--line-2);
  background: var(--paper-2);
  color: var(--ink-1);
}
</style>

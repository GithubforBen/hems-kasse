<script setup lang="ts">
import { formatEUR, centsToEuroString } from '~/utils/format'
import { DENOMS, NOTES, COINS } from '~/utils/denoms'

const props = defineProps<{ open: boolean }>()

const emit = defineEmits<{
  close: []
  saved: [cents: number]
}>()

const shift = useShiftStore()
const toast = useToastStore()

const counts = reactive<Record<number, number>>({})
for (const d of DENOMS) counts[d.cents] = 0

watch(() => props.open, (o) => {
  if (o) for (const d of DENOMS) counts[d.cents] = 0
})

const totalCents = computed(() =>
  DENOMS.reduce((t, d) => t + d.cents * (counts[d.cents] || 0), 0)
)

const denomRows = computed(() => [...DENOMS].reverse())

const saving = ref(false)

async function save() {
  saving.value = true
  try {
    await shift.patchCurrent({ openingCashCents: totalCents.value })
    toast.show(`Einzählung gespeichert · ${formatEUR(totalCents.value)}`)
    emit('saved', totalCents.value)
    emit('close')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="open" class="modal-bg" @mousedown.self="emit('close')">
    <div class="modal wide cc-modal">
      <div class="modal-h">
        <h3>Kasseneinzählung</h3>
        <p>Geld zu Beginn der Schicht zählen und speichern.</p>
      </div>
      <div class="modal-b cc-body">
        <div class="pay-summary">
          <span class="l">Gezählt</span>
          <span class="v">{{ centsToEuroString(totalCents) }} €</span>
        </div>

        <div class="money-section">
          <div class="money-label">Scheine</div>
          <div class="money-grid notes-grid">
            <button
              v-for="n in NOTES"
              :key="n.cents"
              :class="['note', `note-${n.cents / 100}`]"
              @click="counts[n.cents]++">
              <span class="v">{{ n.label }}</span>
            </button>
          </div>
          <div class="money-label">Münzen</div>
          <div class="money-grid coins-grid">
            <button
              v-for="c in COINS"
              :key="c.cents"
              :class="['coin', c.cents >= 100 ? `coin-eu${c.cents / 100}` : (c.cents === 50 ? 'coin-au' : 'coin-cu')]"
              @click="counts[c.cents]++">
              <span class="v">{{ c.label }}</span>
            </button>
          </div>
        </div>

        <div v-for="d in denomRows" :key="d.cents" class="denom-row">
          <div class="d">{{ d.label }}</div>
          <input
            type="number"
            min="0"
            :value="counts[d.cents] || ''"
            placeholder="0"
            @input="(e) => { counts[d.cents] = parseInt((e.target as HTMLInputElement).value.replace(/[^\d]/g, '') || '0', 10) }" />
          <div class="sub">{{ formatEUR(d.cents * (counts[d.cents] || 0)) }}</div>
        </div>
      </div>
      <div class="modal-f">
        <button class="btn ghost" @click="emit('close')">Überspringen</button>
        <button class="btn ok" :disabled="saving" @click="save">
          {{ saving ? 'Speichern…' : 'Einzählung speichern' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cc-modal { max-height: 92dvh; display: flex; flex-direction: column; }
.cc-body  { overflow-y: auto; flex: 1; }
</style>

<script setup lang="ts">
import { formatEUR } from '~/utils/format'
import { DISCOUNT_STEPS, applyDiscount } from '~/utils/discount'
import type { CartItem } from '~/types/api'

const props = defineProps<{ line: CartItem | null }>()
const emit = defineEmits<{
  apply: [percent: number]
  close: []
}>()

// Live preview for each preset, plus a "no discount" option.
const previews = computed(() => {
  const line = props.line
  if (!line) return []
  return DISCOUNT_STEPS.map((percent) => {
    const r = applyDiscount(line.listPriceCents, percent, line.minPriceCents, line.discountable)
    return { percent, priceCents: r.priceCents, capped: r.capped }
  })
})

const minPriceLabel = computed(() =>
  props.line?.minPriceCents != null ? formatEUR(props.line.minPriceCents) : null,
)

function choose(percent: number) {
  emit('apply', percent)
}
</script>

<template>
  <div v-if="line" class="modal-bg" @mousedown.self="emit('close')">
    <div class="modal disc-modal">
      <div class="modal-h">
        <h3>Rabatt · {{ line.name }}</h3>
        <p>
          Normalpreis {{ formatEUR(line.listPriceCents) }}
          <template v-if="minPriceLabel"> · Mindestpreis {{ minPriceLabel }}</template>
        </p>
      </div>

      <div class="modal-b">
        <!-- Non-discountable: clear feedback on desktop and mobile -->
        <div v-if="!line.discountable" class="disc-note bad">
          🔒 „{{ line.name }}" ist nicht rabattierbar.
        </div>

        <template v-else>
          <div class="disc-grid">
            <button
              class="disc-btn"
              :class="{ sel: line.discountPercent === 0 }"
              @click="choose(0)">
              <span class="pct">Kein</span>
              <span class="res">{{ formatEUR(line.listPriceCents) }}</span>
            </button>
            <button
              v-for="p in previews"
              :key="p.percent"
              class="disc-btn"
              :class="{ sel: line.discountPercent === p.percent, capped: p.capped }"
              @click="choose(p.percent)">
              <span class="pct">−{{ p.percent }}%</span>
              <span class="res">{{ formatEUR(p.priceCents) }}</span>
              <span v-if="p.capped" class="cap-tag">Min</span>
            </button>
          </div>

          <div v-if="minPriceLabel" class="disc-note warn">
            ⚠ Mindestpreis {{ minPriceLabel }}: Bei „Min" markierten Stufen greift der Mindestpreis –
            der Preis sinkt nicht weiter, auch nicht bei 100 %.
          </div>
        </template>
      </div>

      <div class="modal-f">
        <button class="btn ghost" @click="emit('close')">Schließen</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.disc-modal { width: min(420px, 100%); }
.disc-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.disc-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 12px 6px;
  border: 1px solid var(--line-2);
  border-radius: var(--r-sm);
  background: var(--paper-2);
  color: var(--ink);
  cursor: pointer;
  position: relative;
  min-height: 58px;
  transition: .12s;
}
.disc-btn:hover { border-color: var(--accent); background: var(--paper); }
.disc-btn.sel { border-color: var(--accent); background: var(--ok-soft); }
.disc-btn .pct { font-weight: 700; font-size: 14px; }
.disc-btn .res {
  font-size: 12px;
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
  font-family: var(--font-num);
}
.disc-btn.capped .res { color: var(--bad); }
.cap-tag {
  position: absolute;
  top: 3px; right: 3px;
  font-size: 8.5px; font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .03em;
  color: #fff; background: var(--bad);
  border-radius: 999px;
  padding: 1px 5px;
}
.disc-note {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  font-size: 13px;
  line-height: 1.4;
}
.disc-note.bad { background: var(--bad-soft); color: var(--bad); }
.disc-note.warn { background: var(--paper-2); color: var(--ink-2); border: 1px solid var(--line-2); }
@media (max-width: 480px) {
  .disc-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>

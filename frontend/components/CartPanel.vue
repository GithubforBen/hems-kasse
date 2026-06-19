<script setup lang="ts">
import { formatEUR } from '~/utils/format'
import type { CartItem } from '~/types/api'

const cart = useCartStore()
const toast = useToastStore()

defineEmits<{ checkout: [] }>()

const discountLine = ref<CartItem | null>(null)

function openDiscount(line: CartItem) {
  discountLine.value = line
}

function applyDiscount(percent: number) {
  const line = discountLine.value
  if (!line) return
  if (!line.discountable) {
    toast.show(`„${line.name}" ist nicht rabattierbar`)
    discountLine.value = null
    return
  }
  const capped = cart.setDiscount(line.lineId, percent)
  if (percent === 0) {
    toast.show(`Rabatt entfernt · ${line.name}`)
  } else if (capped && line.minPriceCents != null) {
    // Floor hit — surface the popup the user asked for.
    toast.show(`Mindestpreis ${formatEUR(line.minPriceCents)} angewendet · Rabatt begrenzt`)
  } else {
    toast.show(`−${percent}% auf ${line.name}`)
  }
  discountLine.value = null
}
</script>

<template>
  <div class="panel cart">
    <div class="panel-h">
      <h2>Warenkorb</h2>
      <span class="sub">{{ cart.totalQty }} Artikel</span>
      <button
        v-if="cart.items.length > 0"
        class="btn ghost"
        style="margin-left:auto;padding:4px 8px;font-size:12.5px"
        @click="cart.clear()">
        Leeren
      </button>
    </div>

    <div class="cart-list">
      <div v-if="cart.items.length === 0" class="cart-empty">
        <div class="ico">🧁</div>
        Tippe auf ein Produkt,<br />
        um es zum Warenkorb hinzuzufügen.
      </div>

      <div v-else v-for="c in cart.items" :key="c.lineId" class="cart-row">
        <div class="cart-info">
          <div class="nm">
            {{ c.name }}
            <span v-if="c.discountPercent > 0" class="disc-badge">−{{ c.discountPercent }}%</span>
          </div>
          <div class="pr">
            <span v-if="c.discountPercent > 0" class="old">{{ formatEUR(c.listPriceCents) }}</span>
            {{ formatEUR(c.priceCents) }} / Stück
          </div>
          <button
            class="disc-link"
            :class="{ off: !c.discountable }"
            @click="openDiscount(c)">
            <template v-if="!c.discountable">🔒 kein Rabatt</template>
            <template v-else-if="c.discountPercent > 0">% Rabatt ändern</template>
            <template v-else>% Rabatt</template>
          </button>
        </div>
        <div class="qty">
          <button @click="cart.dec(c.lineId)" aria-label="weniger">−</button>
          <span class="n">{{ c.qty }}</span>
          <button @click="cart.inc(c.lineId)" aria-label="mehr">+</button>
        </div>
        <div class="line-total">{{ formatEUR(c.priceCents * c.qty) }}</div>
        <button class="x" @click="cart.remove(c.lineId)" title="Entfernen">✕</button>
      </div>
    </div>

    <div class="cart-foot">
      <div class="tot-row">
        <span>Zwischensumme</span><span class="v">{{ formatEUR(cart.totalCents) }}</span>
      </div>
      <div v-if="cart.savedCents > 0" class="tot-row saved">
        <span>Rabatt gespart</span><span class="v">−{{ formatEUR(cart.savedCents) }}</span>
      </div>
      <div class="tot-row">
        <span>Artikel</span><span class="v">{{ cart.totalQty }}</span>
      </div>
      <div class="tot-row grand">
        <span>Summe</span><span class="v">{{ formatEUR(cart.totalCents) }}</span>
      </div>

      <button
        class="sum-btn"
        :disabled="cart.items.length === 0"
        @click="$emit('checkout')">
        <span>Summe & Bezahlen</span>
        <span class="arrow">→</span>
      </button>
    </div>
  </div>

  <DiscountModal
    :line="discountLine"
    @apply="applyDiscount"
    @close="discountLine = null" />
</template>

<style scoped>
.cart-info { min-width: 0; }
.disc-badge {
  display: inline-block;
  margin-left: 4px;
  font-size: 10.5px;
  font-weight: 700;
  color: var(--ok);
  background: var(--ok-soft);
  border-radius: 999px;
  padding: 1px 6px;
  vertical-align: middle;
}
.pr .old {
  text-decoration: line-through;
  color: var(--ink-3);
  margin-right: 5px;
}
.disc-link {
  margin-top: 3px;
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--accent);
}
.disc-link.off { color: var(--ink-3); cursor: default; }
.tot-row.saved .v { color: var(--ok); }
</style>

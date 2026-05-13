<script setup lang="ts">
import { formatEUR } from '~/utils/format'

const cart = useCartStore()

defineEmits<{ checkout: [] }>()
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
        Tippe rechts auf ein Produkt,<br />
        um es zum Warenkorb hinzuzufügen.
      </div>

      <div v-else v-for="c in cart.items" :key="c.productId" class="cart-row">
        <div>
          <div class="nm">{{ c.name }}</div>
          <div class="pr">{{ formatEUR(c.priceCents) }} / Stück</div>
        </div>
        <div class="qty">
          <button @click="cart.dec(c.productId)" aria-label="weniger">−</button>
          <span class="n">{{ c.qty }}</span>
          <button @click="cart.inc(c.productId)" aria-label="mehr">+</button>
        </div>
        <div class="line-total">{{ formatEUR(c.priceCents * c.qty) }}</div>
        <button class="x" @click="cart.remove(c.productId)" title="Entfernen">✕</button>
      </div>
    </div>

    <div class="cart-foot">
      <div class="tot-row">
        <span>Zwischensumme</span><span class="v">{{ formatEUR(cart.totalCents) }}</span>
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
</template>

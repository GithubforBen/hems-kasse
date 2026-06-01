<script setup lang="ts">
const auth = useAuthStore()
const cart = useCartStore()
const shift = useShiftStore()
const sales = useSalesStore()
const toast = useToastStore()
const { formatEUR } = await import('~/utils/format')

const payOpen = ref(false)
const cashCountOpen = ref(false)
const mobileTab = ref<'grid' | 'cart'>('grid')

onMounted(async () => {
  if (auth.token) {
    try {
      await shift.fetchCurrent()
      await sales.fetch()
      if ((shift.current?.openingCashCents ?? 0) === 0) {
        cashCountOpen.value = true
      }
    } catch {}
  }
})

function checkout() {
  if (cart.items.length === 0) return
  payOpen.value = true
}

function onPaid(sale: { totalCents: number; method: 'BAR' | 'KARTE' }) {
  cart.clear()
  mobileTab.value = 'grid'
  toast.show(`Verkauf gebucht · ${formatEUR(sale.totalCents)} ${sale.method === 'BAR' ? 'Bar' : 'Karte'}`)
}
</script>

<template>
  <div class="app">
    <TopBar />

    <div class="pos-wrap">
      <!-- opening cash banner -->
      <div v-if="shift.current && shift.current.openingCashCents === 0" class="opening-banner" @click="cashCountOpen = true">
        <span class="opening-banner-ico">⚠</span>
        <span>Kasseneinzählung fehlt –&nbsp;</span>
        <span class="opening-banner-cta">Jetzt einzählen</span>
      </div>

      <div class="pos" :class="{ 'mob-cart': mobileTab === 'cart' }">
        <CartPanel @checkout="checkout" />
        <POSGrid />
      </div>
    </div>

    <!-- visible only on mobile via CSS -->
    <div class="mob-tabs">
      <button
        class="mob-tab"
        :class="{ active: mobileTab === 'grid' }"
        @click="mobileTab = 'grid'">
        <span class="mob-tab-ico">🧁</span>
        <span class="mob-tab-lbl">Produkte</span>
      </button>
      <button
        class="mob-tab"
        :class="{ active: mobileTab === 'cart' }"
        @click="mobileTab = 'cart'">
        <span class="mob-tab-ico">🛒</span>
        <span class="mob-tab-lbl">Warenkorb</span>
        <span v-if="cart.totalQty > 0" class="mob-badge">{{ cart.totalQty }}</span>
      </button>
    </div>

    <PayModal :open="payOpen" :items="cart.items" @close="payOpen = false" @paid="onPaid" />
    <CashCountModal :open="cashCountOpen" @close="cashCountOpen = false" />
  </div>
</template>

<style scoped>
.pos-wrap {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  flex: 1;
}
/* pos fills remaining height inside the wrapper */
.pos-wrap :deep(.pos) {
  flex: 1;
}
.opening-banner {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 16px;
  background: var(--bad-soft);
  border-bottom: 1px solid #efc4bf;
  font-size: 12px;
  cursor: pointer;
  transition: background .12s;
}
.opening-banner:hover { background: #f9ddd9; }
body[data-theme="farm"] .opening-banner {
  background: transparent;
  border-bottom-color: var(--bad);
  color: var(--bad);
  font-family: var(--font-mono);
}
.opening-banner-ico { color: var(--bad); font-style: normal; }
.opening-banner-cta { font-weight: 650; color: var(--bad); text-decoration: underline; }
</style>

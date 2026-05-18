<script setup lang="ts">
const auth = useAuthStore()
const cart = useCartStore()
const shift = useShiftStore()
const sales = useSalesStore()
const toast = useToastStore()
const { formatEUR } = await import('~/utils/format')

const payOpen = ref(false)
const mobileTab = ref<'grid' | 'cart'>('grid')

onMounted(async () => {
  // Ensure a shift exists, and load its sales so Abschluss has data.
  if (auth.token) {
    try {
      await shift.fetchCurrent()
      await sales.fetch()
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

    <div class="pos" :class="{ 'mob-cart': mobileTab === 'cart' }">
      <CartPanel @checkout="checkout" />
      <POSGrid />
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
  </div>
</template>

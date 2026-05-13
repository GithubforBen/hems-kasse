<script setup lang="ts">
const auth = useAuthStore()
const cart = useCartStore()
const shift = useShiftStore()
const sales = useSalesStore()
const toast = useToastStore()
const { formatEUR } = await import('~/utils/format')

const payOpen = ref(false)

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
  toast.show(`Verkauf gebucht · ${formatEUR(sale.totalCents)} ${sale.method === 'BAR' ? 'Bar' : 'Karte'}`)
}
</script>

<template>
  <div class="app">
    <TopBar />

    <div class="pos">
      <CartPanel @checkout="checkout" />
      <POSGrid />
    </div>

    <PayModal :open="payOpen" :items="cart.items" @close="payOpen = false" @paid="onPaid" />
  </div>
</template>

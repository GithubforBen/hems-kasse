<script setup lang="ts">
import { formatEUR } from '~/utils/format'
import { colorCls, swatchCls } from '~/utils/colors'
import type { ProductDto } from '~/types/api'

const catalog = useCatalogStore()
const cart = useCartStore()

const activeCatId = ref<string | null>(null)
const priceInputProduct = ref<ProductDto | null>(null)

onMounted(async () => {
  await catalog.fetch()
  if (!activeCatId.value) activeCatId.value = catalog.categories[0]?.id ?? null
})

const activeCat = computed(() =>
  catalog.categories.find(c => c.id === activeCatId.value) ?? catalog.categories[0] ?? null
)

function onProductClick(p: ProductDto) {
  if (p.variable) {
    priceInputProduct.value = p
  } else {
    cart.add(p)
  }
}

function onPriceConfirm(priceCents: number) {
  if (priceInputProduct.value) {
    cart.add(priceInputProduct.value, priceCents)
    priceInputProduct.value = null
  }
}
</script>

<template>
  <div class="panel">
    <div v-if="activeCat && activeCat.products.length > 0" class="grid">
      <button
        v-for="p in activeCat.products"
        :key="p.id"
        class="product"
        :class="[colorCls(p.color), { 'product-variable': p.variable }]"
        @click="onProductClick(p)">
        <span v-if="!p.variable && cart.qtyByProduct[p.id]" class="badge">×{{ cart.qtyByProduct[p.id] }}</span>
        <div class="name">{{ p.name }}</div>
        <div class="price">{{ p.variable ? 'Freier Preis' : formatEUR(p.priceCents) }}</div>
      </button>
    </div>
    <div v-else class="empty-state">
      Keine Produkte in dieser Kategorie.<br />
      <span style="font-size:12px;opacity:.7">Im Admin-Panel hinzufügen.</span>
    </div>

    <div class="cat-bar cat-bar-bottom">
      <button
        v-for="c in catalog.categories"
        :key="c.id"
        class="cat-pill"
        :class="{ active: activeCatId === c.id }"
        @click="activeCatId = c.id">
        <span class="dot" :class="swatchCls(c.color)"></span>
        <span>{{ c.name }}</span>
        <span class="ct">{{ c.products.length }}</span>
      </button>
    </div>
  </div>

  <PriceInputModal
    :product="priceInputProduct"
    @confirm="onPriceConfirm"
    @cancel="priceInputProduct = null" />
</template>

<style scoped>
.product-variable .price {
  font-style: italic;
  opacity: 0.75;
}
</style>

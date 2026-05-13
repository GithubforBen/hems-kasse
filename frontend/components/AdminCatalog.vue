<script setup lang="ts">
import { COLORS, swatchCls } from '~/utils/colors'

const catalog = useCatalogStore()
const activeId = ref<string | null>(null)
const newCatName = ref('')

onMounted(async () => {
  await catalog.fetch()
  if (!activeId.value) activeId.value = catalog.categories[0]?.id ?? null
})

const cat = computed(() =>
  catalog.categories.find(c => c.id === activeId.value) ?? catalog.categories[0] ?? null
)

async function renameCat(id: string, name: string) {
  await catalog.patchCategory(id, { name })
}
async function recolorCat(id: string, color: string) {
  await catalog.patchCategory(id, { color })
}

async function addCat() {
  const name = newCatName.value.trim()
  if (!name) return
  const used = new Set(catalog.categories.map(c => c.color))
  const color = (COLORS.find(c => !used.has(c.id)) ?? COLORS[0]!).id
  const created = await catalog.createCategory({ name, color })
  newCatName.value = ''
  activeId.value = created.id
}

async function delCat(id: string) {
  if (!confirm('Kategorie wirklich löschen? Alle enthaltenen Produkte gehen verloren.')) return
  await catalog.deleteCategory(id)
  if (activeId.value === id) activeId.value = catalog.categories[0]?.id ?? null
}

async function addProd(catId: string) {
  const c = catalog.categories.find(x => x.id === catId)
  await catalog.addProduct(catId, { name: 'Neues Produkt', priceCents: 100, color: c?.color ?? 'peach' })
}

async function patchProd(id: string, body: Partial<{ name: string; priceCents: number; color: string }>) {
  await catalog.patchProduct(id, body)
}

async function delProd(id: string) {
  await catalog.deleteProduct(id)
}
</script>

<template>
  <div class="admin">
    <div class="side">
      <h4>Kategorien</h4>
      <div
        v-for="c in catalog.categories"
        :key="c.id"
        class="cat-item"
        :class="{ active: activeId === c.id }"
        @click="activeId = c.id">
        <span class="swatch" :class="swatchCls(c.color)"></span>
        <input
          class="nm"
          :value="c.name"
          @click.stop
          @change="(e) => renameCat(c.id, (e.target as HTMLInputElement).value)" />
        <span class="ct">{{ c.products.length }}</span>
        <button class="del-x" @click.stop="delCat(c.id)" title="Kategorie löschen">✕</button>
      </div>

      <div class="add-cat">
        <input
          v-model="newCatName"
          @keydown.enter="addCat"
          placeholder="Neue Kategorie…" />
        <button class="btn" @click="addCat">+</button>
      </div>
    </div>

    <div class="main">
      <div v-if="!cat" class="empty-state">Keine Kategorie ausgewählt.</div>

      <template v-else>
        <div class="main-h">
          <h3>{{ cat.name }}</h3>
          <span class="meta">{{ cat.products.length }} Produkte</span>
          <div class="swatch-pick" title="Kategorie-Farbe">
            <button
              v-for="c in COLORS"
              :key="c.id"
              :class="['s', c.sw, { sel: cat.color === c.id }]"
              @click="recolorCat(cat.id, c.id)"
              :title="c.label"></button>
          </div>
        </div>

        <div class="prod-list">
          <div
            v-if="cat.products.length === 0"
            class="empty-state"
            style="padding:40px 20px">
            Noch keine Produkte. Mit „+ Produkt“ hinzufügen.
          </div>

          <div v-else v-for="p in cat.products" :key="p.id" class="prod-row">
            <span class="drag" title="Ziehen zum Umsortieren">⋮⋮</span>
            <input
              class="nm-i"
              :value="p.name"
              @change="(e) => patchProd(p.id, { name: (e.target as HTMLInputElement).value })" />
            <div class="swatch-pick">
              <button
                v-for="c in COLORS"
                :key="c.id"
                :class="['s', c.sw, { sel: p.color === c.id }]"
                @click="patchProd(p.id, { color: c.id })"
                :title="c.label"></button>
            </div>
            <input
              class="pr-i"
              type="number"
              step="0.10"
              min="0"
              :value="(p.priceCents / 100).toFixed(2)"
              @change="(e) => {
                const eur = Number((e.target as HTMLInputElement).value.replace(',', '.')) || 0
                patchProd(p.id, { priceCents: Math.round(eur * 100) })
              }" />
            <button class="del" @click="delProd(p.id)">✕</button>
          </div>
        </div>

        <div class="add-prod">
          <button class="btn secondary" @click="addProd(cat.id)">+ Produkt hinzufügen</button>
        </div>
      </template>
    </div>
  </div>
</template>

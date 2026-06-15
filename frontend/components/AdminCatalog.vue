<script setup lang="ts">
import { COLORS, swatchCls } from '~/utils/colors'

const catalog = useCatalogStore()
const toast = useToastStore()
const activeId = ref<string | null>(null)
const newCatName = ref('')
const componentsForId = ref<string | null>(null)

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

async function addProd(catId: string, variable = false) {
  const c = catalog.categories.find(x => x.id === catId)
  await catalog.addProduct(catId, { name: variable ? 'Divers' : 'Neues Produkt', priceCents: 0, color: c?.color ?? 'peach', variable })
}

async function patchProd(id: string, body: Partial<{ name: string; priceCents: number; color: string; variable: boolean }>) {
  await catalog.patchProduct(id, body)
}

async function setPlu(id: string, plu: string) {
  try {
    await catalog.patchProduct(id, { plu: plu.trim() || null })
  } catch (e: any) {
    if (e?.response?.status === 409) toast.show('PLU bereits vergeben')
    else throw e
  }
}

const componentsProduct = computed(() => {
  if (!componentsForId.value) return null
  for (const c of catalog.categories) {
    const p = c.products.find(x => x.id === componentsForId.value)
    if (p) return p
  }
  return null
})

async function delProd(id: string) {
  await catalog.deleteProduct(id)
}

async function moveProduct(prodId: string, direction: 'up' | 'down') {
  if (!cat.value) return
  const products = cat.value.products
  const idx = products.findIndex(p => p.id === prodId)
  const swapIdx = direction === 'up' ? idx - 1 : idx + 1
  if (swapIdx < 0 || swapIdx >= products.length) return

  const a = products[idx]!
  const b = products[swapIdx]!
  const aOrder = a.sortOrder
  const bOrder = b.sortOrder

  // Swap sortOrders; use distinct values if they happen to be equal
  const newA = bOrder !== aOrder ? bOrder : direction === 'up' ? aOrder - 1 : aOrder + 1
  const newB = bOrder !== aOrder ? aOrder : direction === 'up' ? bOrder + 1 : bOrder - 1

  await Promise.all([
    catalog.patchProduct(a.id, { sortOrder: newA }),
    catalog.patchProduct(b.id, { sortOrder: newB }),
  ])
}

async function moveToCat(prodId: string, newCatId: string) {
  if (!newCatId) return
  await catalog.patchProduct(prodId, { categoryId: newCatId })
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

      <ExportButton
        path="/api/products/export.csv"
        label="Katalog"
        icon="🗂️"
        hint="Kategorien, Produkte, PLU & Kompositionen als CSV"
        style="margin-top:14px" />
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
            Noch keine Produkte. Mit „+ Produkt" hinzufügen.
          </div>

          <div v-else v-for="(p, idx) in cat.products" :key="p.id" class="prod-row">
            <div class="sort-btns">
              <button
                class="sort-btn"
                :disabled="idx === 0"
                @click="moveProduct(p.id, 'up')"
                title="Nach oben">▲</button>
              <button
                class="sort-btn"
                :disabled="idx === cat.products.length - 1"
                @click="moveProduct(p.id, 'down')"
                title="Nach unten">▼</button>
            </div>
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
              :disabled="p.variable"
              :title="p.variable ? 'Preis wird an der Kasse eingegeben' : ''"
              :value="p.variable ? '' : (p.priceCents / 100).toFixed(2)"
              :placeholder="p.variable ? 'Freier Preis' : '0.00'"
              @change="(e) => {
                if (p.variable) return
                const eur = Number((e.target as HTMLInputElement).value.replace(',', '.')) || 0
                patchProd(p.id, { priceCents: Math.round(eur * 100) })
              }" />
            <label class="variable-toggle" :title="p.variable ? 'Freier Preis – klicken zum Deaktivieren' : 'Festen Preis – klicken für freien Preis'">
              <input
                type="checkbox"
                :checked="p.variable"
                @change="patchProd(p.id, { variable: !p.variable })" />
              <span>~</span>
            </label>
            <input
              class="plu-i"
              :value="p.plu ?? ''"
              placeholder="PLU"
              title="PLU-Code"
              @change="(e) => setPlu(p.id, (e.target as HTMLInputElement).value)" />
            <button
              v-if="!p.variable"
              class="btn secondary compose-btn"
              :class="{ active: p.composed }"
              @click="componentsForId = p.id"
              :title="p.composed ? 'Verkaufstaste: ' + p.components.map(c => `${c.qty}× ${c.name}`).join(' + ') : 'Als Verkaufstaste mehrere Produkte zusammenfassen'">
              {{ p.composed ? '⚙ Verkaufstaste' : 'Komposition…' }}
            </button>
            <span v-else class="compose-placeholder"></span>
            <select
              class="cat-sel"
              :value="cat.id"
              @change="(e) => {
                const val = (e.target as HTMLSelectElement).value
                if (val !== cat!.id) moveToCat(p.id, val)
                else (e.target as HTMLSelectElement).value = cat!.id
              }"
              title="In andere Kategorie verschieben">
              <option
                v-for="c in catalog.categories"
                :key="c.id"
                :value="c.id">{{ c.name }}</option>
            </select>
            <button class="del" @click="delProd(p.id)">✕</button>
          </div>
        </div>

        <div class="add-prod">
          <button class="btn secondary" @click="addProd(cat.id)">+ Produkt</button>
          <button class="btn secondary" @click="addProd(cat.id, true)" title="Produkt mit frei eingebbarem Preis">+ Divers</button>
        </div>
      </template>
    </div>

    <ProductComponentsModal
      v-if="componentsProduct"
      :product="componentsProduct"
      :all-products="catalog.categories.flatMap(c => c.products)"
      @close="componentsForId = null" />
  </div>
</template>

<style scoped>
.variable-toggle {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
}
.variable-toggle input { display: none }
.variable-toggle span {
  width: 22px; height: 22px;
  display: grid; place-items: center;
  border-radius: var(--r-xs);
  border: 1px solid var(--line-2);
  font-size: 14px; font-weight: 700;
  color: var(--ink-3);
  background: var(--paper-2);
  transition: .12s;
}
.variable-toggle input:checked + span {
  background: var(--ok-soft);
  border-color: var(--ok);
  color: var(--ok);
}
.sort-btns {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.sort-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 10px;
  line-height: 1;
  padding: 1px 3px;
  color: var(--c-muted, #888);
  border-radius: 3px;
}
.sort-btn:hover:not(:disabled) {
  background: var(--c-hover, #eee);
  color: var(--c-text, #333);
}
.sort-btn:disabled {
  opacity: 0.2;
  cursor: default;
}
.cat-sel {
  font-size: 12px;
  padding: 2px 4px;
  border: 1px solid var(--c-border, #ddd);
  border-radius: 4px;
  background: var(--c-bg, #fff);
  color: var(--c-text, #333);
  max-width: 110px;
}
.plu-i {
  width: 64px;
  font-size: 12px;
  padding: 4px 6px;
  border: 1px solid var(--line-2);
  border-radius: var(--r-xs);
  background: var(--paper-2);
  color: var(--ink-1);
}
.compose-btn {
  font-size: 12px;
  padding: 4px 10px;
  white-space: nowrap;
}
.compose-btn.active {
  background: var(--ok-soft);
  border-color: var(--ok);
  color: var(--ok);
}
</style>

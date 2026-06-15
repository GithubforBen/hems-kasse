<script setup lang="ts">
const register = useRegisterStore()
const newName = ref('')

onMounted(async () => {
  await register.fetch()
})

async function rename(id: string, name: string) {
  const trimmed = name.trim()
  if (!trimmed) return
  await register.patch(id, { name: trimmed })
}

async function add() {
  const name = newName.value.trim()
  if (!name) return
  await register.create(name)
  newName.value = ''
}

async function toggleActive(id: string, active: boolean) {
  await register.patch(id, { active })
}

async function remove(id: string) {
  if (!confirm('Kassette wirklich löschen? Sie wird nur deaktiviert, falls sie schon Schichten hatte.')) return
  try {
    await register.remove(id)
  } catch (e: any) {
    if (e?.response?.status === 409) alert('Diese Kassette hat noch eine offene Schicht und kann nicht gelöscht werden.')
    else throw e
  }
}
</script>

<template>
  <div class="admin">
    <div class="side" style="grid-column:1 / -1">
      <h4>Kassetten</h4>
      <p style="font-size:12.5px;color:var(--ink-3);margin:0 8px 8px">
        Jede Kassette führt eine eigene, unabhängige Schicht (eigene Kasseneinzählung). Verkäufer:innen wählen
        nach der Anmeldung ihre Kassette aus.
      </p>

      <div
        v-for="r in register.all"
        :key="r.id"
        class="cat-item"
        :class="{ active: false }">
        <span class="swatch" :class="r.active ? 'sw-mint' : ''" :style="r.active ? '' : 'background:var(--ink-3);opacity:.35'"></span>
        <input
          class="nm"
          :value="r.name"
          @change="(e) => rename(r.id, (e.target as HTMLInputElement).value)" />
        <label style="display:flex;align-items:center;gap:6px;font-size:12px;color:var(--ink-3);cursor:pointer">
          <input type="checkbox" :checked="r.active" @change="(e) => toggleActive(r.id, (e.target as HTMLInputElement).checked)" />
          aktiv
        </label>
        <button class="del-x" @click.stop="remove(r.id)" title="Kassette löschen">✕</button>
      </div>

      <div class="add-cat">
        <input
          v-model="newName"
          @keydown.enter="add"
          placeholder='Neue Kassette, z. B. „Kassette 2"…' />
        <button class="btn" @click="add">+</button>
      </div>

      <ExportButton
        path="/api/registers/export.csv"
        label="Kassetten"
        icon="🗄️"
        hint="Liste aller Kassetten als CSV"
        style="margin-top:14px" />
    </div>
  </div>
</template>

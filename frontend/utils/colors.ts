/** Ported verbatim from design/project/js/data.js. */
export interface ColorDef {
  id: string
  cls: string
  sw: string
  label: string
}

export const COLORS: ColorDef[] = [
  { id: 'yellow',   cls: 'col-yellow',   sw: 'sw-yellow',   label: 'Sonne'     },
  { id: 'peach',    cls: 'col-peach',    sw: 'sw-peach',    label: 'Pfirsich'  },
  { id: 'pink',     cls: 'col-pink',     sw: 'sw-pink',     label: 'Rosa'      },
  { id: 'mint',     cls: 'col-mint',     sw: 'sw-mint',     label: 'Mint'      },
  { id: 'lavender', cls: 'col-lavender', sw: 'sw-lavender', label: 'Lavendel'  },
  { id: 'blue',     cls: 'col-blue',     sw: 'sw-blue',     label: 'Himmel'    },
]

export const colorCls = (id?: string) => (COLORS.find(c => c.id === id) ?? COLORS[0]!).cls
export const swatchCls = (id?: string) => (COLORS.find(c => c.id === id) ?? COLORS[0]!).sw

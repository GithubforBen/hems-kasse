/** Coin/note denominations in cents. Mirrors design/project/js/data.js. */
export interface Denom {
  cents: number
  label: string
}

export const DENOMS: Denom[] = [
  { cents: 1,    label: '1 ct'   },
  { cents: 2,    label: '2 ct'   },
  { cents: 5,    label: '5 ct'   },
  { cents: 10,   label: '10 ct'  },
  { cents: 20,   label: '20 ct'  },
  { cents: 50,   label: '50 ct'  },
  { cents: 100,  label: '1 €'    },
  { cents: 200,  label: '2 €'    },
  { cents: 500,  label: '5 €'    },
  { cents: 1000, label: '10 €'   },
  { cents: 2000, label: '20 €'   },
  { cents: 5000, label: '50 €'   },
  { cents: 10000,label: '100 €'  },
]

export const NOTES = DENOMS.filter(d => d.cents >= 500)
export const COINS = DENOMS.filter(d => d.cents < 500 && d.cents >= 5)

/**
 * Money formatting helpers. The backend speaks integer cents; the UI shows euros
 * in German locale (1.234,56 €).
 */
const formatter = new Intl.NumberFormat('de-DE', {
  style: 'currency',
  currency: 'EUR',
})

export function formatEUR(cents: number | null | undefined): string {
  const n = (cents ?? 0) / 100
  return formatter.format(n)
}

/** Parse a German-formatted decimal string ("1.234,56" or "5,00") into cents. Returns 0 on garbage. */
export function parseEuroToCents(raw: string | null | undefined): number {
  if (!raw) return 0
  const cleaned = String(raw).replace(/\s|€|\.|·/g, '').replace(',', '.')
  const n = Number(cleaned)
  if (!Number.isFinite(n) || n < 0) return 0
  return Math.round(n * 100)
}

export function centsToEuroString(cents: number): string {
  return (cents / 100).toFixed(2).replace('.', ',')
}

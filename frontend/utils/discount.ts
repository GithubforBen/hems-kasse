/**
 * Discount helpers shared by the cart and the discount modal.
 *
 * The backend re-computes the effective price authoritatively (see SaleController.applyDiscount);
 * this client-side version exists only for live preview and must stay in sync with it.
 */

/** Preset discount percentages offered in the UI. */
export const DISCOUNT_STEPS = [5, 10, 15, 20, 25, 50, 100] as const

export interface DiscountResult {
  /** Effective unit price in cents after the discount and the price floor. */
  priceCents: number
  /** True when the price floor (minPriceCents) capped the discount. */
  capped: boolean
}

export function applyDiscount(
  listPriceCents: number,
  percent: number,
  minPriceCents: number | null,
  discountable: boolean,
): DiscountResult {
  if (!discountable || percent <= 0) {
    return { priceCents: listPriceCents, capped: false }
  }
  const discounted = Math.round((listPriceCents * (100 - percent)) / 100)
  if (minPriceCents != null && discounted < minPriceCents) {
    return { priceCents: Math.min(listPriceCents, minPriceCents), capped: true }
  }
  return { priceCents: Math.max(0, discounted), capped: false }
}

/** Short, collision-resistant id for transient cart lines (no secure context needed). */
export function lineId(): string {
  return Math.random().toString(36).slice(2, 10) + Date.now().toString(36)
}

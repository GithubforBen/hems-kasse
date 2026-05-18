export type Role = 'VERKAUF' | 'ADMIN'

export interface AuthUser {
  name: string
  klasse: string | null
  role: Role
}

export interface ProductDto {
  id: string
  name: string
  priceCents: number
  color: string
  sortOrder: number
}

export interface CategoryDto {
  id: string
  name: string
  color: string
  sortOrder: number
  products: ProductDto[]
}

export interface SaleItemDto {
  productId: string | null
  name: string
  priceCents: number
  qty: number
  color: string
}

export interface SaleDto {
  id: string
  ts: string
  method: 'BAR' | 'KARTE'
  totalCents: number
  givenCents: number
  changeCents: number
  byName: string
  items: SaleItemDto[]
  transactionRef: string
}

export interface ShiftDto {
  id: string
  userName: string
  klasse: string | null
  role: Role
  startedAt: string
  closedAt: string | null
  openingCashCents: number
  countedCashCents: number | null
  expectedCashCents: number | null
  diffCents: number | null
  cashSalesCents: number | null
  cardSalesCents: number | null
  totalSalesCents: number | null
  salesCount: number | null
  itemsSold: number | null
  notes: string | null
}

export interface ShiftDetailDto {
  shift: ShiftDto
  sales: Array<{
    id: string
    ts: string
    method: 'BAR' | 'KARTE'
    totalCents: number
    givenCents: number
    changeCents: number
    byName: string
    items: Array<{ name: string; priceCents: number; qty: number; color: string }>
  }>
}

export interface CartItem {
  productId: string
  name: string
  priceCents: number
  color: string
  qty: number
}

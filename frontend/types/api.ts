export type Role = 'VERKAUF' | 'ADMIN'

export interface AuthUser {
  name: string
  klasse: string | null
  role: Role
}

export interface ComponentDto {
  productId: string | null
  name: string
  qty: number
}

export interface ProductDto {
  id: string
  name: string
  priceCents: number
  color: string
  sortOrder: number
  variable: boolean
  plu: string | null
  composed: boolean
  components: ComponentDto[]
}

export interface RegisterDto {
  id: string
  name: string
  sortOrder: number
  active: boolean
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
  components: ComponentDto[]
}

export interface SaleDto {
  id: string
  ts: string
  method: 'BAR' | 'KARTE' | 'PAYPAL'
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
  registerId: string | null
  registerName: string | null
  startedAt: string
  closedAt: string | null
  openingCashCents: number
  countedCashCents: number | null
  expectedCashCents: number | null
  diffCents: number | null
  cashSalesCents: number | null
  cardSalesCents: number | null
  paypalSalesCents: number | null
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
    method: 'BAR' | 'KARTE' | 'PAYPAL'
    totalCents: number
    givenCents: number
    changeCents: number
    byName: string
    transactionRef: string
    items: Array<{ name: string; priceCents: number; qty: number; color: string }>
  }>
}

export interface InventoryCountLineDto {
  productId: string | null
  productName: string
  countedQty: number
  expectedQty: number
  diffQty: number
}

export interface InventoryCountDto {
  id: string
  ts: string
  byName: string
  notes: string | null
  lines: InventoryCountLineDto[]
}

export interface StockIntakeLineDto {
  productId: string | null
  productName: string
  qty: number
}

export interface StockIntakeDto {
  id: string
  ts: string
  byName: string
  notes: string | null
  lines: StockIntakeLineDto[]
}

export interface ExpectedStockDto {
  productId: string
  name: string
  expectedQty: number
  baselineTs: string | null
}

export interface CartItem {
  productId: string
  name: string
  priceCents: number
  color: string
  qty: number
  variable?: boolean
}

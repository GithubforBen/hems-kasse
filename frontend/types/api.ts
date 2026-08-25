export type Role = 'VERKAUF' | 'ADMIN'

export interface AuthUser {
  name: string
  gruppe: string | null
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
  discountable: boolean
  /** Price floor in cents; a discount can never go below it. null = no floor. */
  minPriceCents: number | null
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
  listPriceCents: number
  discountPercent: number
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
  gruppe: string | null
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

export interface ProductStatDto {
  name: string
  qty: number
  revenueCents: number
}

export interface TimeBucketDto {
  bucket: number
  qty: number
  revenueCents: number
}

export interface DailyPointDto {
  date: string
  qty: number
  revenueCents: number
  sales: number
}

export interface ProductHourPointDto {
  product: string
  hour: number
  qty: number
}

export interface ShortageStatDto {
  name: string
  countsWithShortage: number
  totalShortage: number
  worstShortage: number
}

export interface StatsDto {
  from: string
  to: string
  totalRevenueCents: number
  totalQty: number
  totalSales: number
  topByQty: ProductStatDto[]
  topByRevenue: ProductStatDto[]
  byHour: TimeBucketDto[]
  byWeekday: TimeBucketDto[]
  daily: DailyPointDto[]
  productHours: ProductHourPointDto[]
  shortages: ShortageStatDto[]
}

export interface CartItem {
  /** Unique per cart line — a product may appear multiple times (e.g. different discounts). */
  lineId: string
  productId: string
  name: string
  /** Effective unit price after discount. */
  priceCents: number
  /** Base unit price before discount. */
  listPriceCents: number
  color: string
  qty: number
  variable?: boolean
  discountable: boolean
  minPriceCents: number | null
  /** Applied discount in percent (0 = none). */
  discountPercent: number
}

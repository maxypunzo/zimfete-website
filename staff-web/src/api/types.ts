// Mirrors the JSON returned by the asset-financing service.

export type Role = 'OFFICER' | 'MANAGER' | 'ADMIN'

export type Me = {
  username: string
  officeId: number
  staffId: number | null
  roles: Role[]
  allOffices: boolean
  demo: boolean
}

export type Office = { id: number; name: string }

export type Member = {
  id: number
  displayName: string
  accountNo: string
  officeId: number
  officeName: string
  mobileNo: string | null
  active: boolean
}

export type AssetCategory = 'BOREHOLE' | 'TRACTOR' | 'IRRIGATION' | 'SOLAR' | 'VEHICLE' | 'IMPLEMENT' | 'OTHER'

export type CatalogueItem = {
  id: number
  code: string
  name: string
  category: AssetCategory
  description: string | null
  standardCost: number
  currency: string
  active: boolean
}

export type Supplier = {
  id: number
  name: string
  phone: string | null
  email: string | null
  address: string | null
  active: boolean
}

export type Quote = {
  id: number
  catalogueItemId: number
  supplierId: number
  supplierName: string
  price: number
  currency: string
  quoteDate: string
  validUntil: string
  reference: string | null
}

export type ApplicationStatus = 'SAVING' | 'QUALIFIED' | 'PROCUREMENT' | 'DELIVERED' | 'REPAYING' | 'PAID_OFF' | 'CANCELLED'

export type ConversionStep =
  | 'NOT_STARTED'
  | 'APPLYING_DEPOSIT'
  | 'DEPOSIT_APPLIED'
  | 'LOAN_CREATED'
  | 'LOAN_APPROVED'
  | 'LOAN_DISBURSED'

export type Application = {
  id: number
  reference: string
  clientId: number
  memberName: string
  officeId: number
  officerStaffId: number | null
  catalogueItemId: number
  assetName: string
  quoteId: number | null
  assetCost: number
  currency: string
  depositPercent: number
  depositTarget: number
  depositedAmount: number
  percentComplete: number
  avgMonthlyDeposit: number
  estimatedTargetDate: string | null
  balanceSyncedAt: string | null
  status: ApplicationStatus
  openedOn: string
  qualifiedAt: string | null
  savingsAccountId: number
  conversionStep: ConversionStep
  depositApplied: number | null
  financedAmount: number | null
  loanId: number | null
  cancelReason: string | null
  createdBy: string
}

export type Progress = {
  target: number
  balance: number
  remaining: number
  percentComplete: number
  averageMonthlyDeposit: number
  estimatedTargetDate: string | null
}

export type QueueEntry = {
  position: number
  applicationId: number
  reference: string
  clientId: number
  memberName: string
  officeId: number
  assetName: string
  assetCost: number
  currency: string
  depositedAmount: number
  openedOn: string
  qualifiedOn: string | null
  daysWaiting: number
  cumulativeCashNeeded: number
}

export type OfficeForecast = {
  officeId: number
  inQueue: number
  queueCash: number
  expectedToQualify: number
  expectedCash: number
}

export type CurrencyForecast = {
  inQueue: number
  cashNeededNow: number
  expectedToQualify: number
  expectedCash: number
  totalCashNeeded: number
  byOffice: OfficeForecast[]
}

export type Forecast = {
  asOf: string
  horizon: string
  byCurrency: Record<string, CurrencyForecast>
}

export type PurchaseOrderStatus = 'DRAFT' | 'APPROVED' | 'COMPLETED' | 'CANCELLED'

export type PurchaseOrder = {
  id: number
  applicationId: number
  applicationReference: string
  memberName: string
  officeId: number
  supplierId: number
  supplierName: string
  quoteId: number | null
  amount: number
  currency: string
  status: PurchaseOrderStatus
  queueOverrideReason: string | null
  notes: string | null
  createdBy: string
  createdAt: string
  approvedBy: string | null
  approvedAt: string | null
  cancelledBy: string | null
  cancelReason: string | null
  completedAt: string | null
}

export type Ownership = 'SACCO_OWNED' | 'TRANSFERRED_TO_MEMBER' | 'REPOSSESSED'

export type FinancedAsset = {
  id: number
  applicationId: number
  applicationReference: string
  memberName: string
  clientId: number
  officeId: number
  catalogueItemId: number
  assetName: string
  serialNumber: string | null
  latitude: number
  longitude: number
  deliveredOn: string
  deliveredBy: string
  notes: string | null
  ownership: Ownership
  ownershipChangedAt: string
  ownershipNote: string | null
}

export type AssetCondition = 'GOOD' | 'NEEDS_REPAIR' | 'DAMAGED' | 'NOT_FOUND'

export type Inspection = {
  id: number
  inspectedOn: string
  condition: AssetCondition
  latitude: number | null
  longitude: number | null
  notes: string | null
  inspectedBy: string
}

export type Photo = {
  id: number
  kind: 'DELIVERY' | 'INSPECTION'
  originalName: string | null
  contentType: string
  sizeBytes: number
  uploadedBy: string
  uploadedAt: string
}

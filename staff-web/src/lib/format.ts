import type { ApplicationStatus, AssetCondition, ConversionStep, Ownership, PurchaseOrderStatus } from '../api/types'

const moneyFormats = new Map<string, Intl.NumberFormat>()

/** "US$4,000.00" style amounts. */
export function money(amount: number | null | undefined, currency = 'USD'): string {
  if (amount === null || amount === undefined) return '—'
  let format = moneyFormats.get(currency)
  if (!format) {
    format = new Intl.NumberFormat('en-ZW', { style: 'currency', currency, minimumFractionDigits: 2 })
    moneyFormats.set(currency, format)
  }
  return format.format(amount)
}

/** "1 Oct 2026". Accepts ISO dates or timestamps. */
export function date(value: string | null | undefined): string {
  if (!value) return '—'
  const d = value.length === 10 ? new Date(value + 'T00:00:00') : new Date(value)
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Today's date as yyyy-mm-dd in the browser's time zone (for date inputs). */
export function today(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Plain-language months until a date, e.g. "in about 4 months". */
export function fromNow(value: string | null | undefined, now = new Date()): string {
  if (!value) return ''
  const target = new Date(value + 'T00:00:00')
  const days = Math.round((target.getTime() - now.getTime()) / 86_400_000)
  if (days <= 0) return 'now'
  if (days < 14) return `in ${days} day${days === 1 ? '' : 's'}`
  if (days < 60) return `in about ${Math.round(days / 7)} weeks`
  return `in about ${Math.round(days / 30.44)} months`
}

type Tone = 'neutral' | 'info' | 'progress' | 'success' | 'warning' | 'danger'

export const applicationStatus: Record<ApplicationStatus, { label: string; tone: Tone; help: string }> = {
  SAVING: { label: 'Saving', tone: 'neutral', help: 'Depositing toward the 50% target' },
  QUALIFIED: { label: 'In queue', tone: 'info', help: 'Target reached; waiting to be served' },
  PROCUREMENT: { label: 'Being procured', tone: 'progress', help: 'Purchase order approved' },
  DELIVERED: { label: 'Delivered', tone: 'warning', help: 'Waiting for conversion into a loan' },
  REPAYING: { label: 'Repaying', tone: 'progress', help: 'Loan disbursed; member is repaying' },
  PAID_OFF: { label: 'Paid off', tone: 'success', help: 'Asset belongs to the member' },
  CANCELLED: { label: 'Cancelled', tone: 'danger', help: 'Application cancelled' },
}

export const orderStatus: Record<PurchaseOrderStatus, { label: string; tone: Tone }> = {
  DRAFT: { label: 'Awaiting approval', tone: 'warning' },
  APPROVED: { label: 'Approved', tone: 'info' },
  COMPLETED: { label: 'Completed', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
}

export const ownershipLabel: Record<Ownership, { label: string; tone: Tone }> = {
  SACCO_OWNED: { label: 'ZimFete owned', tone: 'info' },
  TRANSFERRED_TO_MEMBER: { label: 'Member owned', tone: 'success' },
  REPOSSESSED: { label: 'Repossessed', tone: 'danger' },
}

export const conditionLabel: Record<AssetCondition, { label: string; tone: Tone }> = {
  GOOD: { label: 'Good', tone: 'success' },
  NEEDS_REPAIR: { label: 'Needs repair', tone: 'warning' },
  DAMAGED: { label: 'Damaged', tone: 'danger' },
  NOT_FOUND: { label: 'Not found', tone: 'danger' },
}

export const conversionStepLabel: Record<ConversionStep, string> = {
  NOT_STARTED: 'Not started',
  APPLYING_DEPOSIT: 'Applying the deposit',
  DEPOSIT_APPLIED: 'Deposit applied; creating the loan',
  LOAN_CREATED: 'Loan created; approving',
  LOAN_APPROVED: 'Loan approved; disbursing to supplier',
  LOAN_DISBURSED: 'Completed',
}

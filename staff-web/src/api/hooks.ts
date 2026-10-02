import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { api } from './client'
import type {
  Application,
  ApplicationStatus,
  CatalogueItem,
  FinancedAsset,
  Forecast,
  Inspection,
  Member,
  Office,
  Ownership,
  Photo,
  Progress,
  PurchaseOrder,
  PurchaseOrderStatus,
  QueueEntry,
  Quote,
  Supplier,
} from './types'

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}

// ---- Reference data ----

export function useOffices() {
  return useQuery({
    queryKey: ['offices'],
    queryFn: () => api<Office[]>('/api/offices'),
    staleTime: 10 * 60_000,
  })
}

export function useOfficeName() {
  const { data } = useOffices()
  return (id: number) => data?.find((o) => o.id === id)?.name ?? `Office ${id}`
}

export function useMemberSearch(text: string) {
  const q = text.trim()
  return useQuery({
    queryKey: ['members', q],
    queryFn: () => api<Member[]>(`/api/members${query({ query: q })}`),
    enabled: q.length >= 2,
    staleTime: 60_000,
  })
}

// ---- Catalogue ----

export function useCatalogue(includeInactive = false) {
  return useQuery({
    queryKey: ['catalogue', includeInactive],
    queryFn: () => api<CatalogueItem[]>(`/api/catalogue/items${query({ includeInactive })}`),
  })
}

export function useSuppliers() {
  return useQuery({ queryKey: ['suppliers'], queryFn: () => api<Supplier[]>('/api/suppliers') })
}

export function useQuotes(itemId: number | undefined) {
  return useQuery({
    queryKey: ['quotes', itemId],
    queryFn: () => api<Quote[]>(`/api/catalogue/items/${itemId}/quotes`),
    enabled: itemId !== undefined,
  })
}

// ---- Applications ----

export function useApplications(filter: { status?: ApplicationStatus; officeId?: number; clientId?: number } = {}) {
  return useQuery({
    queryKey: ['applications', filter],
    queryFn: () => api<Application[]>(`/api/applications${query(filter)}`),
  })
}

export function useApplication(id: number) {
  return useQuery({ queryKey: ['application', id], queryFn: () => api<Application>(`/api/applications/${id}`) })
}

export function useApplicationOrders(applicationId: number) {
  return useQuery({
    queryKey: ['purchase-orders', { applicationId }],
    queryFn: () => api<PurchaseOrder[]>(`/api/purchase-orders${query({ applicationId })}`),
  })
}

export function useApplicationAsset(applicationId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['application-asset', applicationId],
    queryFn: () => api<FinancedAsset>(`/api/applications/${applicationId}/asset`),
    enabled,
  })
}

// ---- Queue ----

export function useQueue(officeId?: number) {
  return useQuery({ queryKey: ['queue', officeId], queryFn: () => api<QueueEntry[]>(`/api/queue${query({ officeId })}`) })
}

export function useForecast(days: number, officeId?: number) {
  return useQuery({
    queryKey: ['forecast', days, officeId],
    queryFn: () => api<Forecast>(`/api/queue/forecast${query({ days, officeId })}`),
  })
}

// ---- Purchase orders ----

export function usePurchaseOrders(filter: { status?: PurchaseOrderStatus; officeId?: number } = {}) {
  return useQuery({
    queryKey: ['purchase-orders', filter],
    queryFn: () => api<PurchaseOrder[]>(`/api/purchase-orders${query(filter)}`),
  })
}

// ---- Asset register ----

export function useAssets(filter: { ownership?: Ownership; officeId?: number } = {}) {
  return useQuery({
    queryKey: ['assets', filter],
    queryFn: () => api<FinancedAsset[]>(`/api/assets${query(filter)}`),
  })
}

export function useAsset(id: number) {
  return useQuery({ queryKey: ['asset', id], queryFn: () => api<FinancedAsset>(`/api/assets/${id}`) })
}

export function useInspections(assetId: number) {
  return useQuery({
    queryKey: ['inspections', assetId],
    queryFn: () => api<Inspection[]>(`/api/assets/${assetId}/inspections`),
  })
}

export function usePhotos(assetId: number) {
  return useQuery({ queryKey: ['photos', assetId], queryFn: () => api<Photo[]>(`/api/assets/${assetId}/photos`) })
}

// ---- Writes ----

/**
 * A write that refreshes everything afterwards. Statuses ripple across screens (queue, orders,
 * register), so invalidating all cached lists is simpler and safer than tracking each one.
 */
export function useAction<TInput, TResult = unknown>(fn: (input: TInput) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => client.invalidateQueries(),
  })
}

export const actions = {
  openApplication: (b: { clientId: number; catalogueItemId: number; quoteId?: number }) =>
    api<Application>('/api/applications', { method: 'POST', body: b }),
  refresh: (id: number) =>
    api<{ application: Application; progress: Progress | null }>(`/api/applications/${id}/refresh`, { method: 'POST' }),
  reprice: (id: number, b: { quoteId?: number; assetCost?: number }) =>
    api<Application>(`/api/applications/${id}/reprice`, { method: 'POST', body: b }),
  cancelApplication: (id: number, reason: string) =>
    api<Application>(`/api/applications/${id}/cancel`, { method: 'POST', body: { reason } }),
  raiseOrder: (id: number, b: { supplierId: number; quoteId?: number; queueOverrideReason?: string; notes?: string }) =>
    api<PurchaseOrder>(`/api/applications/${id}/purchase-orders`, { method: 'POST', body: b }),
  approveOrder: (id: number) => api<PurchaseOrder>(`/api/purchase-orders/${id}/approve`, { method: 'POST' }),
  cancelOrder: (id: number, reason: string) =>
    api<PurchaseOrder>(`/api/purchase-orders/${id}/cancel`, { method: 'POST', body: { reason } }),
  recordDelivery: (
    id: number,
    b: {
      serialNumber?: string
      latitude: number
      longitude: number
      deliveredOn: string
      memberAcknowledged: boolean
      notes?: string
    },
  ) => api<FinancedAsset>(`/api/applications/${id}/delivery`, { method: 'POST', body: b }),
  convert: (id: number) => api<Application>(`/api/applications/${id}/convert`, { method: 'POST' }),
  uploadPhoto: (assetId: number, file: File, kind: Photo['kind']) => {
    const form = new FormData()
    form.append('file', file)
    return api<Photo>(`/api/assets/${assetId}/photos?kind=${kind}`, { method: 'POST', form })
  },
  inspect: (
    assetId: number,
    b: { inspectedOn: string; condition: string; latitude?: number; longitude?: number; notes?: string },
  ) => api<Inspection>(`/api/assets/${assetId}/inspections`, { method: 'POST', body: b }),
  repossess: (assetId: number, reason: string) =>
    api<FinancedAsset>(`/api/assets/${assetId}/repossess`, { method: 'POST', body: { reason } }),
  saveItem: (id: number | undefined, b: Omit<CatalogueItem, 'id'>) =>
    id
      ? api<CatalogueItem>(`/api/catalogue/items/${id}`, { method: 'PUT', body: b })
      : api<CatalogueItem>('/api/catalogue/items', { method: 'POST', body: b }),
  saveSupplier: (id: number | undefined, b: Omit<Supplier, 'id'>) =>
    id
      ? api<Supplier>(`/api/suppliers/${id}`, { method: 'PUT', body: b })
      : api<Supplier>('/api/suppliers', { method: 'POST', body: b }),
  addQuote: (
    itemId: number,
    b: { supplierId: number; price: number; quoteDate: string; validUntil: string; reference?: string },
  ) => api<Quote>(`/api/catalogue/items/${itemId}/quotes`, { method: 'POST', body: b }),
  demoDeposit: (savingsId: number, amount: number) =>
    api<void>(`/api/demo/savings/${savingsId}/deposit`, { method: 'POST', body: { amount } }),
  demoRepay: (loanId: number) => api<void>(`/api/demo/loans/${loanId}/repay`, { method: 'POST' }),
}

import { api } from './client'
import type { Paged } from './types'

/** The procurement mode of PPMP, APP and PR lines bought from the Procurement Service of the DBM. */
export const PS_DBM = 'Procurement from PS-DBM'

export interface PsCatalogueItem {
  id: number
  code: string
  description: string
  unit: string
  unit_price: number
  item_id: number | null
  item_name: string | null
  is_active: boolean
  price_as_of: string | null
}

export interface PsCatalogueInput {
  code: string
  description: string
  unit: string
  unit_price: number
  item_id?: number | null
  is_active?: boolean
  price_as_of?: string | null
}

export type PsOrderStatus = 'draft' | 'submitted' | 'paid' | 'delivered' | 'cancelled'
export type PsPaymentMode = 'cash' | 'lddap_ada' | 'check' | 'unutilized_funds'

export interface PsOrderLine {
  id: number
  purchase_request_item_id: number
  ps_catalogue_item_id: number | null
  catalogue_code: string | null
  description: string
  unit: string | null
  quantity: number
  unit_price: number
  amount: number
  availability: 'available' | 'not_available'
  cnas_number: string | null
  cnas_date: string | null
  cnas_valid_until: string | null
}

export interface PsOrder {
  id: number
  apr_number: string
  status: PsOrderStatus
  purchase_request: { id: number; name: string }
  department: string | null
  vs_reference: string | null
  submitted_date: string | null
  payment_mode: PsPaymentMode | null
  payment_reference: string | null
  payment_date: string | null
  amount_paid: number | null
  dr_number: string | null
  dr_date: string | null
  delivery_mode: 'pickup' | 'delivery' | null
  purchase_order: { id: number; name: string } | null
  purchase_order_status: string | null
  total_amount: number
  remarks: string | null
  prepared_by: { id: number; name: string } | null
  items: PsOrderLine[] | null
  can_edit: boolean
  can_submit: boolean
  can_pay: boolean
  can_deliver: boolean
  can_cancel: boolean
  can_mark_unavailable: boolean
}

export interface PsPrOption {
  id: number
  pr_number: string
  purpose: string | null
  department: string | null
  lines: { id: number; general_description: string; specifications: string | null; unit: string | null; quantity: number; remaining: number; estimated_unit_cost: number | null }[]
}

export interface PsOrderInput {
  purchase_request_id: number
  remarks?: string | null
  items: { purchase_request_item_id: number; ps_catalogue_item_id?: number | null; quantity: number; unit_price?: number | null }[]
}

export const psDbmApi = {
  catalogue: (search?: string, activeOnly?: boolean) =>
    api<{ data: PsCatalogueItem[] }>('GET', '/ps-dbm/catalogue', undefined, { params: { search: search || undefined, active_only: activeOnly ? 'true' : undefined } }),
  addCatalogue: (input: PsCatalogueInput) => api<PsCatalogueItem>('POST', '/ps-dbm/catalogue', input),
  updateCatalogue: (id: number, input: PsCatalogueInput) => api<PsCatalogueItem>('PUT', `/ps-dbm/catalogue/${id}`, input),
  removeCatalogue: (id: number) => api<{ message: string }>('DELETE', `/ps-dbm/catalogue/${id}`),
  list: (f: { page: number; status?: string; search?: string }) => api<Paged<PsOrder>>('GET', '/ps-dbm/orders', undefined, { params: f }),
  get: (id: number) => api<PsOrder>('GET', `/ps-dbm/orders/${id}`),
  prOptions: () => api<{ data: PsPrOption[] }>('GET', '/ps-dbm/purchase-requests'),
  create: (input: PsOrderInput) => api<PsOrder>('POST', '/ps-dbm/orders', input),
  update: (id: number, input: PsOrderInput) => api<PsOrder>('PUT', `/ps-dbm/orders/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/ps-dbm/orders/${id}`),
  submit: (id: number, input: { vs_reference: string; submitted_date?: string }) => api<PsOrder>('POST', `/ps-dbm/orders/${id}/submit`, input),
  notAvailable: (id: number, lineId: number, input: { cnas_number: string; cnas_date?: string }) =>
    api<PsOrder>('POST', `/ps-dbm/orders/${id}/items/${lineId}/not-available`, input),
  pay: (id: number, input: { payment_mode: PsPaymentMode; payment_reference: string; payment_date?: string; amount_paid: number }) =>
    api<PsOrder>('POST', `/ps-dbm/orders/${id}/pay`, input),
  deliver: (id: number, input: { dr_number: string; dr_date?: string; delivery_mode: 'pickup' | 'delivery'; delivery_address?: string }) =>
    api<PsOrder>('POST', `/ps-dbm/orders/${id}/deliver`, input),
  cancel: (id: number, reason: string) => api<PsOrder>('POST', `/ps-dbm/orders/${id}/cancel`, { reason }),
}

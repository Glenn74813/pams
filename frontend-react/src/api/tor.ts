import { api, ApiError, tokenStore } from './client'
import type { Paged } from './types'

// ───────────────────────── locations (TOR 1.2.3, 1.4.7) ─────────────────────────

export interface AssetLocation {
  id: number
  name: string
  building: string | null
  floor: string | null
  room: string | null
  description: string | null
  is_active: boolean
  assets: number
}

export interface LocationInput {
  name: string
  building?: string | null
  floor?: string | null
  room?: string | null
  description?: string | null
  is_active?: boolean
}

export interface LocationMove {
  id: number
  from: string | null
  to: string | null
  moved_by: string | null
  remarks: string | null
  moved_at: string | null
}

export const locationsApi = {
  list: (activeOnly = false) => api<AssetLocation[]>('GET', '/locations', undefined, { params: { active_only: activeOnly || undefined } }),
  create: (input: LocationInput) => api<AssetLocation>('POST', '/locations', input),
  update: (id: number, input: LocationInput) => api<AssetLocation>('PUT', `/locations/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/locations/${id}`),
  move: (assetId: number, input: { location_id: number | null; remarks?: string | null }) => api<{ message: string }>('POST', `/properties/${assetId}/location`, input),
  history: (assetId: number) => api<LocationMove[]>('GET', `/properties/${assetId}/locations`),
}

// ───────────────────────── photos (TOR 1.4.4, 1.5.4) ─────────────────────────

/** Fetches an authenticated image as an object URL (the caller revokes it); null when there is no photo. */
export async function photoUrl(path: string): Promise<string | null> {
  const token = tokenStore.get()
  const res = await fetch('/api' + path, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (res.status === 404) return null
  if (!res.ok) throw new ApiError(res.status, {})
  return URL.createObjectURL(await res.blob())
}

const upload = (path: string, file: File) => {
  const form = new FormData()
  form.append('photo', file)
  return api<{ message: string; has_photo: boolean }>('POST', path, form)
}

export const photosApi = {
  asset: (id: number) => `/properties/${id}/photo`,
  item: (id: number) => `/items/${id}/photo`,
  uploadAsset: (id: number, file: File) => upload(`/properties/${id}/photo`, file),
  removeAsset: (id: number) => api<{ message: string }>('DELETE', `/properties/${id}/photo`),
  uploadItem: (id: number, file: File) => upload(`/items/${id}/photo`, file),
  removeItem: (id: number) => api<{ message: string }>('DELETE', `/items/${id}/photo`),
}

// ───────────────────────── maintenance (TOR 1.4.5) ─────────────────────────

export type MaintenanceType = 'preventive' | 'corrective' | 'inspection' | 'calibration'

export interface Maintenance {
  id: number
  property_id: number
  tag_number: string
  item_name: string
  holder: string | null
  location: string | null
  maintenance_type: MaintenanceType
  description: string
  scheduled_date: string
  interval_months: number | null
  status: 'scheduled' | 'done' | 'cancelled'
  overdue: boolean
  completed_date: string | null
  performed_by: string | null
  cost: number | null
  remarks: string | null
  created_by: string | null
  completed_by: string | null
}

export interface MaintenanceSummary {
  overdue: number
  upcoming: number
  done_this_year: number
  cost_this_year: number
  upcoming_days: number
}

export interface ScheduleInput {
  property_id: number
  maintenance_type: MaintenanceType
  description: string
  scheduled_date: string
  interval_months?: number | null
  remarks?: string | null
}

export const maintenanceApi = {
  list: (params: { page?: number; status?: string; due?: string; property_id?: number; search?: string }) =>
    api<Paged<Maintenance>>('GET', '/maintenance', undefined, { params }),
  summary: () => api<MaintenanceSummary>('GET', '/maintenance/summary'),
  schedule: (input: ScheduleInput) => api<Maintenance>('POST', '/maintenance', input),
  update: (id: number, input: ScheduleInput) => api<Maintenance>('PUT', `/maintenance/${id}`, input),
  complete: (id: number, input: { completed_date: string; performed_by?: string | null; cost?: number | null; remarks?: string | null }) =>
    api<{ message: string; maintenance: Maintenance; next: Maintenance | null }>('POST', `/maintenance/${id}/complete`, input),
  cancel: (id: number) => api<Maintenance>('POST', `/maintenance/${id}/cancel`),
}

// ───────────────────────── clearance (TOR 1.4.12, 1.5.5) ─────────────────────────

export type ClearancePurpose = 'resignation' | 'retirement' | 'transfer' | 'end_of_contract' | 'leave' | 'other'

export interface ClearanceAsset {
  id: number
  tag_number: string
  item_name: string
  status: string
  acquisition_cost: number | null
  accountability_number: string | null
}

export interface Clearance {
  id: number
  certificate_number: string | null
  user_id: number
  employee: string
  department: string | null
  purpose: ClearancePurpose
  purpose_details: string | null
  effective_date: string | null
  status: 'pending' | 'issued' | 'cancelled'
  outstanding: number
  assets_turned_over: number
  requested_by: string | null
  issued_by: string | null
  issued_at: string | null
  remarks: string | null
  created_at: string | null
  outstanding_assets: ClearanceAsset[] | null
}

export const clearanceApi = {
  list: (params: { page?: number; status?: string; search?: string }) => api<Paged<Clearance>>('GET', '/clearances', undefined, { params }),
  show: (id: number) => api<Clearance>('GET', `/clearances/${id}`),
  check: (userId: number) => api<{ user_id: number; name: string; outstanding: number; assets: ClearanceAsset[] }>('GET', `/clearances/check/${userId}`),
  open: (input: { user_id: number; purpose: ClearancePurpose; purpose_details?: string | null; effective_date?: string | null; remarks?: string | null }) =>
    api<Clearance>('POST', '/clearances', input),
  issue: (id: number) => api<Clearance>('POST', `/clearances/${id}/issue`),
  cancel: (id: number) => api<Clearance>('POST', `/clearances/${id}/cancel`),
}

// ───────────────────────── digital signing (TOR 1.4.11) ─────────────────────────

export interface DocumentSignature {
  id: number
  signer: string
  algorithm: string
  certificate_subject: string
  certificate_issuer: string
  certificate_serial: string
  certificate_not_after: string | null
  trusted_ca: string | null
  source: 'in_app' | 'platform'
  signed_at: string
}

export interface SigningRequest {
  id: number
  request_number: string
  title: string
  file_name: string
  file_size: number
  file_sha256: string
  requested_by_id: number
  requested_by: string
  signer_id: number
  signer: string
  status: 'pending' | 'signed' | 'declined' | 'cancelled'
  platform_reference: string | null
  remarks: string | null
  signed_at: string | null
  created_at: string | null
  can_sign: boolean
  can_cancel: boolean
  signatures: DocumentSignature[]
}

export interface TrustedCa {
  id: number
  name: string
  subject: string
  fingerprint: string
  not_after: string | null
  created_at: string | null
}

export const ALGORITHMS = ['RSA-SHA256', 'RSA-SHA384', 'RSA-SHA512', 'ECDSA-SHA256', 'ECDSA-SHA384', 'ECDSA-SHA512'] as const

export const signingApi = {
  list: (params: { box: 'inbox' | 'outbox' | 'all'; status?: string; page?: number }) => api<Paged<SigningRequest>>('GET', '/signing/requests', undefined, { params }),
  show: (id: number) => api<SigningRequest>('GET', `/signing/requests/${id}`),
  create: (input: { file: File; title: string; signer_id: number; remarks?: string }) => {
    const form = new FormData()
    form.append('file', input.file)
    form.append('title', input.title)
    form.append('signer_id', String(input.signer_id))
    if (input.remarks) form.append('remarks', input.remarks)
    return api<SigningRequest>('POST', '/signing/requests', form)
  },
  sign: (id: number, input: { certificate_pem: string; signature: string; algorithm: string }) => api<SigningRequest>('POST', `/signing/requests/${id}/sign`, input),
  decline: (id: number, remarks: string) => api<SigningRequest>('POST', `/signing/requests/${id}/decline`, { remarks }),
  cancel: (id: number) => api<SigningRequest>('POST', `/signing/requests/${id}/cancel`),
  verify: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api<{ sha256: string; signed: boolean; message: string; requests: SigningRequest[] }>('POST', '/signing/verify', form)
  },
  trusted: () => api<TrustedCa[]>('GET', '/signing/trusted-cas'),
  addTrusted: (input: { name: string; pem: string }) => api<TrustedCa>('POST', '/signing/trusted-cas', input),
  removeTrusted: (id: number) => api<{ message: string }>('DELETE', `/signing/trusted-cas/${id}`),
}

// ───────────────────────── single sign-on (TOR 1.3.1) ─────────────────────────

export const ssoApi = {
  config: () => api<{ enabled: boolean; label: string }>('GET', '/auth/sso/config', undefined, { skipAuthRedirect: true }),
  /** Full-page navigation: the provider's sign-in page cannot be fetched. */
  startUrl: '/api/auth/sso/start',
}

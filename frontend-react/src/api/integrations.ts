import { api } from './client'

export type AuthType = 'none' | 'bearer' | 'header' | 'basic' | 'oauth'
export type ApiContext = 'none' | 'payable'

export interface ApiColumn {
  label: string
  field: string
}

/** Another system's API that PAMS reads. Setup fields are null for people who only view payments. */
export interface ExternalApi {
  id: number
  name: string
  description: string | null
  category: string
  base_url: string | null
  path: string | null
  auth_type: AuthType | null
  auth_header: string | null
  token_url: string | null
  client_id: string | null
  has_secret: boolean
  data_path: string | null
  columns: ApiColumn[]
  context: ApiContext
  timeout_seconds: number
  is_active: boolean
  last_status: string | null
  last_called_at: string | null
}

export interface ExternalApiInput {
  name: string
  description?: string | null
  base_url: string
  path?: string
  auth_type: AuthType
  auth_header?: string | null
  token_url?: string | null
  client_id?: string | null
  secret?: string
  clear_secret?: boolean
  data_path?: string | null
  columns?: ApiColumn[]
  context: ApiContext
  timeout_seconds?: number
  is_active?: boolean
}

export interface ApiData {
  api: ExternalApi
  result: { columns: string[]; rows: (string | number | boolean | null)[][]; truncated: boolean; status: number } | null
  error: string | null
  fetched_at: string
}

const I = '/integrations'

export const integrationsApi = {
  list: () => api<{ data: ExternalApi[]; can_manage: boolean }>('GET', I),
  create: (input: ExternalApiInput) => api<ExternalApi>('POST', I, input),
  update: (id: number, input: ExternalApiInput) => api<ExternalApi>('PUT', `${I}/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `${I}/${id}`),
  test: (id: number, values: Record<string, string> = {}) => api<ApiData>('POST', `${I}/${id}/test`, { values }),
  data: (id: number) => api<ApiData>('GET', `${I}/${id}/data`),
  payable: (type: 'purchase_order' | 'contract', id: number) =>
    api<{ data: ApiData[]; reference: string }>('GET', `${I}/payable`, undefined, { params: { type, id } }),
}

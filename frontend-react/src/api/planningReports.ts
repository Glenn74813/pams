import { api, download } from './client'

/** GPPB/COA planning and reporting forms: JSON previews and Excel downloads under /reports/planning. */

type Params = Record<string, string | number | undefined>
type Cell = string | number | boolean | null

export type PlanningOptions = {
  departments: { id: number; name: string; code: string }[]
  ppmps: { id: number; ppmp_number: string; title: string; status: string; fiscal_year: number; department_id: number; department: string | null }[]
  document_types: string[]
}

export type PpmpGppbRow = {
  ppmp_number: string
  code: string | null
  general_description: string
  specifications: string | null
  quantity: number
  unit: string | null
  quantity_size: string
  estimated_unit_cost: number
  estimated_budget: number
  procurement_mode: string
  procurement_category: string
  schedule: string | null
  jan: string | null
  feb: string | null
  mar: string | null
  apr: string | null
  may: string | null
  jun: string | null
  jul: string | null
  aug: string | null
  sep: string | null
  oct: string | null
  nov: string | null
  dec: string | null
  source_of_funds: string | null
  remarks: string | null
} & Record<string, Cell>

export type PpmpGppb = {
  title: string
  fiscal_year: number
  department_id: number
  department: string
  ppmps: { id: number; ppmp_number: string; title: string; status: string }[]
  total_estimated_budget: number
  prepared_by: string | null
  approved_by: string | null
  approved_by_designation: string | null
  rows: PpmpGppbRow[]
}

export type AppGppbRow = {
  app_item_id: number
  code: string | null
  procurement_project: string
  specifications: string | null
  quantity: number
  unit: string | null
  pmo_end_user: string
  procurement_mode: string
  procurement_category: string
  schedule_quarter: string | null
  advertisement_posting: string | null
  submission_opening: string | null
  notice_of_award: string | null
  contract_signing: string | null
  schedule_basis: 'actual' | 'partly actual' | 'planned' | 'unscheduled'
  procurement_reference: string | null
  source_of_funds: string | null
  estimated_budget_total: number
  estimated_budget_mooe: number
  estimated_budget_co: number
  remarks: string | null
  status: string
} & Record<string, Cell>

export type AppGppb = {
  title: string
  fiscal_year: number
  app: { id: number; app_number: string; title: string; status: string; approved_at: string | null } | null
  capitalization_threshold: number
  total_budget: number
  total_mooe: number
  total_co: number
  prepared_by: string | null
  recommended_by: string | null
  recommended_by_designation: string | null
  approved_by: string | null
  approved_by_designation: string | null
  rows: AppGppbRow[]
}

export type AppCseRow = {
  no: number
  code: string | null
  item: string
  unit: string | null
  q1: number
  q2: number
  q3: number
  q4: number
  unscheduled: number
  total_quantity: number
  price_catalogue: number
  total_amount: number
  end_users: string
  remarks: string | null
} & Record<string, Cell>

export type AppCse = {
  title: string
  fiscal_year: number
  source: 'app' | 'ppmp'
  app_number: string | null
  department_id: number | null
  department: string | null
  category: string
  mapping: string
  total_amount: number
  prepared_by: string | null
  certified_by: string | null
  approved_by: string | null
  rows: AppCseRow[]
}

export type AprRow = {
  section: 'competitive' | 'alternative' | 'subtotal' | 'total'
  mode: string
  planned_projects: number
  planned_budget: number
  projects: number
  awarded: number
  unsuccessful: number
  ongoing: number
  total_abc: number
  abc_of_awarded: number
  contract_amount: number
  savings: number
  share_of_contract_amount?: number
} & Record<string, Cell>

export type Apr = {
  title: string
  fiscal_year: number
  competitive: AprRow
  alternative: AprRow
  total: AprRow
  prepared_by: string | null
  approved_by: string | null
  rows: AprRow[]
}

export type CoaTransmittalRow = {
  no: number
  document_kind: 'purchase_order' | 'contract'
  document_type: string
  id: number
  document_number: string
  document_date: string
  supplier: string
  particulars: string | null
  reference: string | null
  amount: number
  status: string
} & Record<string, Cell>

export type CoaTransmittal = {
  title: string
  date_from: string
  date_to: string
  document_type: 'all' | 'purchase_order' | 'contract'
  transmittal_number: string | null
  transmittal_date: string
  auditor: string | null
  purchase_orders: number
  contracts: number
  total_amount: number
  transmitted_by: string | null
  transmitted_by_designation: string | null
  noted_by: string | null
  noted_by_designation: string | null
  rows: CoaTransmittalRow[]
}

export type PpmpFilter = { ppmp_id?: number; department_id?: number; fiscal_year?: number; status?: string }
export type AppCseFilter = { fiscal_year: number; source?: 'app' | 'ppmp'; department_id?: number }
export type TransmittalFilter = { date_from: string; date_to: string; document_type?: string; transmittal_number?: string; auditor?: string }

const get = <T,>(path: string, params: Params) => api<T>('GET', `/reports/planning/${path}`, undefined, { params })

export const planningReportsApi = {
  options: (fiscalYear?: number) => get<PlanningOptions>('options', { fiscal_year: fiscalYear }),
  ppmp: (f: PpmpFilter) => get<PpmpGppb>('ppmp', f),
  app: (fiscalYear: number) => get<AppGppb>('app', { fiscal_year: fiscalYear }),
  appCse: (f: AppCseFilter) => get<AppCse>('app-cse', f),
  apr: (fiscalYear: number) => get<Apr>('apr', { fiscal_year: fiscalYear }),
  coaTransmittal: (f: TransmittalFilter) => get<CoaTransmittal>('coa-transmittal', f),
  /** Path of a form's Excel download, for ExportButton (e.g. exportPath('apr') → /reports/planning/apr/export). */
  exportPath: (form: 'ppmp' | 'app' | 'app-cse' | 'apr' | 'coa-transmittal') => `/reports/planning/${form}/export`,
  export: (form: 'ppmp' | 'app' | 'app-cse' | 'apr' | 'coa-transmittal', params: Params, name: string) =>
    download(`/reports/planning/${form}/export`, params, name),
}

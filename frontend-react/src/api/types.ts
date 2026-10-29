// Shapes returned by the Spring Boot API (snake_case, as the Laravel API used).

export interface Department {
  id: number
  name: string
  code: string
}

export interface AuthUser {
  id: number
  name: string
  email: string
  role_id: number
  department_id: number
  role: { id: number; name: string; description: string | null }
  department: Department
  permissions: string[]
  accessible_modules: string[]
  is_super_admin: boolean
  two_factor_enabled: boolean
  has_signature: boolean
  signature_image?: string
}

export interface LoginResponse {
  access_token?: string
  token_type?: string
  expires_in?: number
  user?: AuthUser
  two_factor_required?: boolean
  message?: string
}

export interface User {
  id: number
  name: string
  email: string
  role_id: number
  department_id: number
  role: { id: number; name: string; is_super_admin: boolean } | null
  department: Department | null
  two_factor_enabled: boolean
  has_signature: boolean
  email_verified_at: string | null
  password_changed_at: string | null
  created_at: string | null
  updated_at: string | null
}

export interface Pagination {
  total: number
  per_page: number
  current_page: number
  last_page: number
}

export interface Paged<T> {
  data: T[]
  pagination: Pagination
}

export interface RoleOption {
  id: number
  name: string
  description: string
}

export interface Role {
  id: number
  name: string
  description: string | null
  permissions: string[]
  is_system: boolean
  is_super_admin: boolean
  users_count?: number
  created_at: string | null
  updated_at: string | null
}

/** Permission group label -> { permission key: label } */
export type PermissionCatalogue = Record<string, Record<string, string>>

// ── Inventory ──

export type Classification = 'consumable' | 'semi_expendable' | 'ppe'
export type StockLevel = 'out' | 'low' | 'ok' | 'over'

export interface Category {
  id: number
  name: string
  parent_id: number | null
  is_leaf: boolean
  items_count: number
}

export interface ItemRef {
  id: number
  code: string
  name: string
  unit_of_measure: string
}

export interface StockLot {
  id: number
  item_id: number
  item?: ItemRef
  brand_name: string | null
  specifications: string | null
  warranty: string | null
  warranty_months: number | null
  warranty_expiry: string | null
  warranty_status: 'none' | 'active' | 'expiring' | 'expired'
  quantity: number
  unit_cost: number
  total_value: number
  lot_number: string | null
  expiration_date: string | null
  days_until_expiration: number | null
  received_date: string
  supplier_id: number | null
  supplier: { id: number; name: string } | null
  location: string | null
}

export interface Item {
  id: number
  code: string
  name: string
  description: string | null
  category_id: number
  category: { id: number; name: string } | null
  unit_of_measure: string
  weighted_average_cost: number
  current_stock: number
  min_stock: number
  max_stock: number | null
  classification: Classification
  classification_label: string
  is_assembly: boolean
  is_available: boolean
  stock_status: StockLevel
  total_value: number
  stocks?: StockLot[]
  created_at: string | null
  updated_at: string | null
}

export interface StockTransfer {
  id: number
  transfer_number: string
  item: ItemRef | null
  from_location: string | null
  to_location: string
  quantity: number
  transfer_date: string
  remarks: string | null
  transferred_by: string | null
}

export interface ExpiringStock {
  warning_days: number
  expired: StockLot[]
  expiring_soon: StockLot[]
  summary: {
    expired_lots: number
    expired_qty: number
    expired_value: number
    expiring_lots: number
    expiring_qty: number
    expiring_value: number
  }
}

export interface StockCard {
  item: { code: string; name: string; description: string | null; unit_of_measure: string; reorder_point: number; current_stock: number }
  rows: { date: string | null; reference: string; receipt: number | null; issue: number | null; office: string | null; balance: number }[]
}

export interface InventoryConfig {
  capitalization_threshold: number
  fefo_expiry_warning_days: number
  agency_name: string | null
  fund_cluster: string | null
}

// ── Issuance, adjustments, semi-expendable ──

export type RequestStatus = 'pending' | 'approved' | 'rejected' | 'partially_fulfilled' | 'fulfilled'
export type LineStatus = 'pending' | 'approved' | 'rejected' | 'fulfilled'

export interface RequestLine {
  id: number
  item_id: number
  code: string | null
  name: string
  unit_of_measure: string | null
  classification: Classification | null
  current_stock: number
  unit_cost: number | null
  quantity: number
  approved_quantity: number
  status: LineStatus
}

export interface SupplyRequest {
  id: number
  request_number: string
  status: RequestStatus
  priority: 'low' | 'medium' | 'high'
  purpose: string | null
  request_date: string | null
  department: { id: number; name: string } | null
  requester: { id: number; name: string } | null
  custodian_name: string | null
  ris_number: string | null
  ris_date: string | null
  ics_number: string | null
  ics_date: string | null
  par_number: string | null
  par_date: string | null
  remarks: string | null
  items: RequestLine[]
  total_requested: number
  total_issued: number
  can_edit: boolean
  can_delete: boolean
  can_process: boolean
  created_at: string | null
}

export interface StockAdjustment {
  id: number
  adjustment_number: string
  item: { id: number; code: string; name: string; unit_of_measure: string; current_stock: number } | null
  adjustment_type: 'increase' | 'decrease'
  quantity: number
  unit_cost: number | null
  reason: string
  remarks: string | null
  status: 'pending' | 'approved' | 'rejected'
  previous_quantity: number | null
  new_quantity: number | null
  requested_by: string | null
  requested_by_id: number | null
  approved_by: string | null
  approved_at: string | null
  created_at: string | null
  can_approve: boolean
  can_delete: boolean
}

export interface SemiExpendableIssue {
  id: number
  item: { id: number; code: string; name: string; unit_of_measure: string } | null
  request_id: number | null
  holder: { id: number; name: string } | null
  department: { id: number; name: string } | null
  custodian_name: string | null
  ics_number: string | null
  quantity: number
  quantity_returned: number
  outstanding: number
  issued_date: string
  status: 'issued' | 'partially_returned' | 'returned'
  remarks: string | null
}

// ── Assets, disposal, gate pass ──

export type AssetStatus = 'assigned' | 'available' | 'under_repair' | 'retired' | 'disposed'

export interface Asset {
  id: number
  tag_number: string
  serial_number: string | null
  item: { id: number; code: string; name: string; unit_of_measure: string; classification: Classification } | null
  brand: string | null
  specifications: string | null
  warranty: string | null
  warranty_months: number | null
  warranty_expiry: string | null
  holder: { id: number; name: string } | null
  holder_office: string | null
  assigned_date: string | null
  accountability_number: string | null
  status: AssetStatus
  condition: 'excellent' | 'good' | 'fair' | 'poor'
  acquisition_cost: number | null
  acquisition_date: string | null
  useful_life_years: number
  salvage_value: number | null
  is_depreciable: boolean
  accumulated_depreciation: number
  book_value: number
  monthly_depreciation: number
  can_edit: boolean
  can_transfer: boolean
  can_delete: boolean
}

export interface AssetMove {
  id: number
  transfer_type: 'transfer' | 'return' | 'reissue'
  reference_number: string | null
  transfer_date: string
  from: { id: number; name: string } | null
  to: { id: number; name: string } | null
  reason: string
  recorded_by: string | null
  property_id: number
  tag_number: string | null
  item_name: string | null
}

export interface Depreciation {
  is_depreciable: boolean
  acquisition_cost: number | null
  residual_value: number
  useful_life_years: number
  monthly_depreciation: number
  starts: string
  accumulated_depreciation: number
  book_value: number
  schedule: { year: number; depreciation: number; accumulated: number; book_value: number }[]
  postings: { period: string; amount: number; accumulated: number; book_value: number }[]
}

export interface Disposal {
  id: number
  disposal_number: string
  category: 'property' | 'inventory'
  disposal_type: string
  status: 'draft' | 'submitted' | 'approved' | 'rejected' | 'completed'
  reason: string
  remarks: string | null
  request_date: string
  requester: { id: number; name: string }
  approver: { id: number; name: string } | null
  approval_date: string | null
  disposal_date: string | null
  property_id: number | null
  tag_number: string | null
  serial_number: string | null
  item_id: number | null
  item_code: string | null
  item_name: string | null
  unit_of_measure: string | null
  quantity: number | null
  unit_cost: number | null
  value: number | null
  book_value: number | null
  proceeds: number | null
  can_edit: boolean
  can_submit: boolean
  can_decide: boolean
  can_complete: boolean
}

export interface GatePass {
  id: number
  gate_pass_number: string
  property_id: number
  tag_number: string | null
  serial_number: string | null
  item_name: string | null
  holder: string | null
  received_by: string
  purpose: string
  date_released: string
  expected_return_date: string | null
  actual_return_date: string | null
  condition_on_release: string
  condition_on_return: string | null
  status: 'released' | 'overdue' | 'returned'
  overdue: boolean
  remarks: string | null
  released_by: string | null
  returned_by: string | null
  received_back_by: string | null
  can_edit: boolean
  can_return: boolean
}

// ── Procurement planning & purchase requests ──

export interface NamedRef {
  id: number
  name: string | null
}

export interface ProcurementOptions {
  modes: string[]
  categories: string[]
  fund_sources: string[]
  quarters: string[]
  ceilings: Record<string, number>
}

export type PpmpStatus = 'draft' | 'submitted' | 'under_review' | 'approved' | 'returned'

export interface PpmpLine {
  id: number
  general_description: string
  specifications: string | null
  code: string | null
  unit: string | null
  quantity: number
  estimated_unit_cost: number
  estimated_total: number
  procurement_mode: string
  procurement_category: string
  is_sustainable: boolean
  schedule: string | null
  fund_source: string | null
  remarks: string | null
}

export interface Ppmp {
  id: number
  ppmp_number: string
  department: NamedRef
  fiscal_year: number
  title: string
  status: PpmpStatus
  total_estimated_budget: number
  revision: number
  preparer: NamedRef | null
  reviewer: NamedRef | null
  approver: NamedRef | null
  submitted_at: string | null
  approved_at: string | null
  return_reason: string | null
  remarks: string | null
  items_count: number
  items: PpmpLine[] | null
  app_number: string | null
  can_edit: boolean
  can_submit: boolean
  can_review: boolean
  can_decide: boolean
  can_delete: boolean
  created_at: string
}

export interface AppLine {
  id: number
  ppmp_id: number | null
  ppmp_number: string | null
  department: NamedRef | null
  general_description: string
  specifications: string | null
  unit: string | null
  procurement_mode: string
  procurement_category: string
  quantity: number
  estimated_budget: number
  unit_cost: number
  schedule: string | null
  fund_source: string | null
  requested_quantity: number
  remaining_quantity: number
  requested_amount: number
  utilized_amount: number
  remaining_budget: number
  utilization_rate: number
  status: 'planned' | 'ongoing' | 'completed'
}

export interface AppPlan {
  id: number
  app_number: string
  fiscal_year: number
  title: string
  status: 'consolidated' | 'approved'
  total_budget: number
  preparer: NamedRef | null
  approver: NamedRef | null
  approved_at: string | null
  remarks: string | null
  items_count: number
  ppmp_count: number
  requested_amount: number
  utilized_amount: number
  has_pr: boolean
  is_fully_requested: boolean
  pending_ppmps: number
  items: AppLine[] | null
  can_edit: boolean
  can_approve: boolean
  can_delete: boolean
  created_at: string
}

export interface BudgetRow {
  key: string
  label: string
  count: number
  estimated_budget: number
  requested_amount: number
  utilized_amount: number
  remaining: number
  utilization_rate: number
}

export interface BudgetSummary {
  fiscal_year: number
  app_number: string | null
  app_status: string | null
  total_estimated: number
  total_requested: number
  total_utilized: number
  total_remaining: number
  utilization_rate: number
  ppmp_count: number
  ppmp_approved_count: number
  by_department: BudgetRow[]
  by_mode: BudgetRow[]
  by_quarter: BudgetRow[]
}

export type PrStatus = 'draft' | 'submitted' | 'under_review' | 'approved' | 'returned' | 'cancelled'

export interface PrLine {
  id: number
  app_item_id: number | null
  general_description: string
  unit: string | null
  quantity: number
  estimated_unit_cost: number
  estimated_total: number
  specifications: string | null
  procurement_mode: string | null
  procurement_category: string
  fund_source: string | null
  is_sustainable: boolean
  remarks: string | null
}

export interface PrStep {
  step: number
  label: string
  required_permission: string
  status: 'pending' | 'approved' | 'returned'
  actor: NamedRef | null
  acted_at: string | null
  remarks: string | null
}

export interface PurchaseRequest {
  id: number
  pr_number: string
  department: NamedRef
  app: { id: number; app_number: string; fiscal_year: number } | null
  fiscal_year: number | null
  requester: NamedRef
  request_date: string
  purpose: string | null
  status: PrStatus
  stage_label: string
  current_step: number | null
  priority: 'low' | 'medium' | 'high'
  total_amount: number
  caf_number: string | null
  caf_certifier: NamedRef | null
  caf_certified_at: string | null
  caf_amount: number | null
  caf_remarks: string | null
  remarks: string | null
  return_reason: string | null
  cancelled_at: string | null
  items_count: number
  items: PrLine[] | null
  approvals: PrStep[] | null
  procurement_modes: string[]
  procurement_categories: string[]
  fund_sources: string[]
  funds_covered: boolean
  can_edit: boolean
  can_submit: boolean
  can_cancel: boolean
  can_delete: boolean
  can_act: boolean
  can_certify: boolean
  created_at: string
}

// ── RFQ & BAC ──

export interface RfqLine {
  id: number
  purchase_request_item_id: number | null
  general_description: string
  specifications: string | null
  unit: string | null
  quantity: number
  abc_unit_cost: number
  abc_total: number
  delivery_lead_time: string | null
}

export interface RfqQuotation {
  id: number
  supplier: NamedRef
  quotation_number: string | null
  submitted_at: string | null
  total_amount: number
  is_responsive: boolean
  is_winner: boolean
  over_abc: boolean
  supplier_eligible: boolean
  remarks: string | null
  lines: { rfq_item_id: number; unit_price: number; total_price: number; over_abc: boolean }[]
}

export type RfqStatus = 'draft' | 'issued' | 'closed' | 'awarded' | 'cancelled'

export interface Rfq {
  id: number
  rfq_number: string
  title: string
  procurement_method: string
  single_source: boolean
  status: RfqStatus
  abc: number
  deadline: string | null
  deadline_passed: boolean
  delivery_term: string | null
  delivery_lead_days: number | null
  description: string | null
  justification: string | null
  purchase_request: NamedRef | null
  department: NamedRef | null
  preparer: NamedRef | null
  awarded_supplier: NamedRef | null
  awarded_at: string | null
  bac_resolution: NamedRef | null
  remarks: string | null
  noa_number: string | null
  noa_date: string | null
  ntp_number: string | null
  ntp_date: string | null
  items_count: number
  quotations_count: number
  items: RfqLine[] | null
  quotations: RfqQuotation[] | null
  can_edit: boolean
  can_issue: boolean
  can_close: boolean
  can_cancel: boolean
  can_delete: boolean
  can_quote: boolean
  can_award: boolean
  can_noa: boolean
  can_ntp: boolean
  created_at: string
}

export interface RfqPrOption {
  id: number
  pr_number: string
  purpose: string | null
  department: string
  total_amount: number
  modes: string[]
  lines: {
    id: number
    general_description: string
    specifications: string | null
    unit: string | null
    quantity: number
    remaining_quantity: number
    estimated_unit_cost: number
    procurement_mode: string | null
  }[]
}

export interface RfqAbstract {
  rfq: Rfq
  suppliers: RfqQuotation[]
  rows: { rfq_item_id: number; general_description: string; unit: string | null; quantity: number; abc_unit_cost: number; prices: Record<string, number | null>; lowest_unit_price: number | null }[]
  recommended: RfqQuotation | null
  quotation_count: number
  single_source: boolean
  insufficient_quotations: boolean
}

export interface BacMember {
  id: number
  user: NamedRef | null
  full_name: string
  position: string | null
  office: string | null
  designation: string
  appointment_order_no: string | null
  appointment_date: string | null
  term_start: string | null
  term_end: string | null
  is_active: boolean
  term_expired: boolean
  sequence: number
  remarks: string | null
}

export interface BacCompliance {
  has_chairperson: boolean
  has_vice_chairperson: boolean
  voting_members: number
  has_secretariat: boolean
  has_twg: boolean
  is_properly_constituted: boolean
  issues: string[]
}

export interface BacQuorum {
  voting_seated: number
  voting_present: number
  needed: number
  met: boolean
}

export interface BacMeeting {
  id: number
  meeting_number: string
  title: string
  meeting_date: string
  venue: string | null
  agenda: string | null
  status: 'scheduled' | 'ongoing' | 'completed' | 'cancelled'
  minutes: string | null
  caller: NamedRef | null
  remarks: string | null
  attendees_count: number
  quorum: BacQuorum
  attendees: { id: number; user_id: number | null; name: string; committee_role: string | null; present: boolean; remarks: string | null }[] | null
  resolutions: { id: number; resolution_number: string; title: string; status: string }[] | null
  can_edit: boolean
  created_at: string
}

export interface BacResolution {
  id: number
  resolution_number: string
  title: string
  resolution_type: string
  content: string | null
  status: 'draft' | 'for_approval' | 'approved' | 'archived'
  meeting: NamedRef | null
  purchase_request: NamedRef | null
  preparer: NamedRef | null
  approver: NamedRef | null
  approved_at: string | null
  remarks: string | null
  used_by: string[]
  can_edit: boolean
  can_submit: boolean
  can_approve: boolean
  can_archive: boolean
  can_delete: boolean
  created_at: string
}

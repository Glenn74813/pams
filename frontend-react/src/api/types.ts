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

// ── Competitive bidding ──

export type BiddingStatus = 'draft' | 'pre_procurement' | 'invitation' | 'bid_opening' | 'evaluation' | 'awarded' | 'failed' | 'cancelled'
export type StageResult = 'pending' | 'passed' | 'failed'

export interface Bidder {
  id: number
  supplier: NamedRef
  supplier_eligible: boolean
  bid_amount: number | null
  over_abc: boolean
  bid_security_form: string | null
  bid_security_amount: number | null
  bid_security_posted: boolean
  bid_security_required: number | null
  bid_security_ok: boolean
  results: Record<'eligibility' | 'technical' | 'financial' | 'post_qualification', StageResult>
  scores: Record<string, number>
  rated_score: number | null
  mearb_score: number | null
  computed_rank: number | null
  post_qual_turn: boolean
  is_winner: boolean
  remarks: string | null
}

export interface BidProtest {
  id: number
  protest_type: string
  supplier: NamedRef | null
  filed_by: string | null
  grounds: string
  filed_date: string
  protest_fee: number | null
  status: string
  resolution: string | null
  resolved_date: string | null
}

export interface BidObserver {
  id: number
  name: string
  organization: string | null
  observer_type: string
  invited: boolean
  invited_date: string | null
  attended: boolean
  remarks: string | null
}

export interface Bidding {
  id: number
  bidding_number: string
  title: string
  status: BiddingStatus
  abc: number
  purchase_request: NamedRef | null
  department: NamedRef | null
  preparer: NamedRef | null
  delivery_term: string | null
  delivery_lead_days: number | null
  evaluation_method: 'price' | 'mearb'
  price_weight: number
  technical_weight: number
  pre_proc_date: string | null
  pre_proc_notes: string | null
  pre_proc_required: boolean
  itb_reference: string | null
  itb_posted_at: string | null
  itb_publication: string | null
  submission_deadline: string | null
  deadline_passed: boolean
  bid_opening_date: string | null
  awarded_supplier: NamedRef | null
  awarded_amount: number | null
  awarded_at: string | null
  bac_resolution: NamedRef | null
  noa_number: string | null
  noa_date: string | null
  ntp_number: string | null
  ntp_date: string | null
  contract_recommendation: string | null
  failure_reason: string | null
  failed_count: number
  remarks: string | null
  bidders_count: number
  items: RfqLine[] | null
  criteria: { id: number; name: string; weight: number; is_environmental: boolean }[] | null
  bidders: Bidder[] | null
  protests: BidProtest[] | null
  observers: BidObserver[] | null
  recommended_bidder_id: number | null
  next_post_qual_bidder_id: number | null
  open_protests: number
  observers_invited: number
  timeline: { key: string; label: string; from: string | null; to: string | null; max_days: number; elapsed_days: number | null; breached: boolean; pending: boolean }[] | null
  can_edit: boolean
  can_schedule: boolean
  can_open: boolean
  can_record_bids: boolean
  can_evaluate: boolean
  can_award: boolean
  can_noa: boolean
  can_ntp: boolean
  can_fail: boolean
  can_cancel: boolean
  can_delete: boolean
  created_at: string
}

export interface BiddingPrOption {
  id: number
  pr_number: string
  purpose: string | null
  department: string
  failed_biddings: number
  lines: RfqPrOption['lines']
}

// ── Purchase orders & receiving ──

export type PoStatus = 'draft' | 'sent' | 'confirmed' | 'partially_received' | 'received' | 'cancelled'

export interface PoLine {
  id: number
  description: string
  specifications: string | null
  unit: string | null
  quantity: number
  unit_cost: number
  total_cost: number
  delivered: number
  accepted: number
  outstanding: number
  rfq_item_id: number | null
  bidding_item_id: number | null
}

export interface PurchaseOrder {
  id: number
  po_number: string
  status: PoStatus
  supplier: NamedRef
  purchase_request: NamedRef | null
  department: NamedRef | null
  source: { type: 'rfq' | 'bidding'; id: number; number: string; noa_number: string | null } | null
  order_date: string
  delivery_date: string | null
  late: boolean
  delivery_address: string | null
  total_amount: number
  remarks: string | null
  preparer: NamedRef | null
  approver: NamedRef | null
  fund_source: string | null
  caf_number: string | null
  items_count: number
  items: PoLine[] | null
  inspections: { id: number; iar_number: string; status: string; acceptance_status: string; delivery_date: string }[] | null
  can_edit: boolean
  can_send: boolean
  can_confirm: boolean
  can_cancel: boolean
  can_delete: boolean
  can_receive: boolean
  created_at: string
}

export interface AwardSource {
  type: 'rfq' | 'bidding'
  id: number
  number: string
  title: string
  supplier: NamedRef
  purchase_request: NamedRef | null
  noa_number: string
  amount: number
  delivery_lead_days: number | null
  delivery_term: string | null
  prices_fixed: boolean
  lines: { source_item_id: number; description: string; specifications: string | null; unit: string | null; quantity: number; unit_cost: number }[]
}

export interface IarLine {
  id: number
  purchase_order_item_id: number | null
  description: string
  specifications: string | null
  unit: string | null
  quantity_ordered: number
  quantity_received: number
  quantity_accepted: number
  quantity_rejected: number
  status: string
  remarks: string | null
  unit_cost: number | null
  item: { id: number; code: string; name: string; unit_of_measure: string } | null
  stock_id: number | null
}

export interface InspectionReport {
  id: number
  iar_number: string
  purchase_order: NamedRef | null
  supplier: NamedRef | null
  purchase_request: NamedRef | null
  department: NamedRef | null
  delivery_date: string
  inspection_date: string
  inspector: NamedRef
  status: 'passed' | 'partial' | 'failed'
  total_items_ordered: number
  total_items_received: number
  total_items_accepted: number
  total_items_rejected: number
  observations: string | null
  remarks: string | null
  acceptance_status: 'pending' | 'accepted' | 'partially_accepted' | 'rejected'
  accepter: NamedRef | null
  accepted_at: string | null
  items: IarLine[] | null
  can_edit: boolean
  can_accept: boolean
  can_reject: boolean
  can_delete: boolean
  created_at: string
}

// ── Contracts ──

export interface Contract {
  id: number
  contract_number: string
  title: string
  contract_type: string
  status: 'draft' | 'active' | 'completed' | 'terminated'
  supplier: NamedRef
  purchase_request: NamedRef | null
  source: NamedRef | null
  source_type: 'rfq' | 'bidding' | null
  purchase_order: NamedRef | null
  amount: number
  original_amount: number
  variation_percent: number
  start_date: string | null
  end_date: string | null
  signed_date: string | null
  delivery_lead_days: number | null
  days_to_expiry: number | null
  expiring: boolean
  expired: boolean
  description: string | null
  terms: string | null
  remarks: string | null
  preparer: NamedRef | null
  delivery_deadline: string | null
  approved_extension_days: number
  delivery: { order_value: number; delivered_value: number; undelivered_value: number; complete: boolean; last_delivery: string | null } | null
  ld_estimate: { deadline: string; as_of: string; days_delayed: number; basis: number; rate: number; amount: number; capped: boolean; may_terminate: boolean } | null
  ld_amount: number
  ld_days_delayed: number | null
  ld_as_of_date: string | null
  ld_remarks: string | null
  fund_source: string | null
  caf_number: string | null
  calloffs_total: number
  amendments: { id: number; amendment_number: number; title: string; description: string | null; amount_change: number; new_end_date: string | null; status: string; preparer: NamedRef | null; approver: NamedRef | null; approved_at: string | null; remarks: string | null; can_approve: boolean }[] | null
  securities: { id: number; security_type: string; form: string | null; reference_number: string | null; amount: number; percentage: number | null; posted_date: string | null; validity_date: string | null; status: string; remarks: string | null; required: number | null; sufficient: boolean }[] | null
  extensions: { id: number; requested_days: number; approved_days: number | null; reason: string; status: string; requested_date: string; approver: NamedRef | null; approved_at: string | null; remarks: string | null }[] | null
  calloffs: { id: number; calloff_number: string; title: string; supplier: NamedRef; amount: number; calloff_date: string | null; status: string; remarks: string | null }[] | null
  performance_secured: boolean
  can_edit: boolean
  can_activate: boolean
  can_complete: boolean
  can_terminate: boolean
  can_delete: boolean
  created_at: string
}

export interface ContractSource {
  type: 'rfq' | 'bidding'
  id: number
  number: string
  title: string
  supplier: NamedRef
  purchase_request: NamedRef | null
  amount: number
  noa_number: string
  ntp_date: string | null
  delivery_lead_days: number | null
  delivery_term: string | null
  purchase_order: NamedRef | null
}

// ── Suppliers ──

export interface Supplier {
  id: number
  vendor_code: string | null
  name: string
  tin: string | null
  philgeps_reg_number: string | null
  contact_person: string | null
  email: string | null
  phone: string | null
  mobile_number: string | null
  address: string | null
  website: string | null
  credit_terms: string | null
  notes: string | null
  is_active: boolean
  is_eligible: boolean
  is_msme: boolean
  is_sanctioned: boolean
  sanction: string | null
  awardable: boolean
  eligibility: { required: { document_type: string; present: boolean; valid: boolean; expiry_date: string | null }[]; missing: string[]; expired: string[]; ready: boolean }
  expiring_documents: number
  documents: { id: number; document_type: string; reference_number: string | null; issued_date: string | null; expiry_date: string | null; status: 'valid' | 'expiring' | 'expired'; days_to_expiry: number | null; remarks: string | null }[] | null
  sanctions: { id: number; sanction_type: string; grounds: string; reference_number: string | null; start_date: string; end_date: string | null; status: string; in_force: boolean; lifted_at: string | null; lifted_reason: string | null; recorded_by: NamedRef | null }[] | null
  evaluations: { id: number; evaluation_date: string; delivery_rating: number; quality_rating: number; timeliness_rating: number | null; compliance_rating: number | null; after_sales_rating: number | null; overall_rating: number; reference: string | null; remarks: string | null; evaluator: NamedRef | null; can_delete: boolean }[] | null
  performance: {
    ratings: number
    avg_overall: number | null
    avg_delivery: number | null
    avg_quality: number | null
    satisfactory: boolean | null
    purchase_orders: number
    purchase_orders_value: number
    contracts: number
    contracts_value: number
    rfqs_won: number
    biddings_won: number
    deliveries: number
    on_time_deliveries: number
    on_time_rate: number | null
    units_received: number
    units_rejected: number
    rejection_rate: number | null
    rejected_deliveries: number
    liquidated_damages: number
  } | null
  can_edit: boolean
  can_delete: boolean
  created_at: string
}

// ── Physical count ──

export type CountType = 'supplies' | 'ppe' | 'semi_expendable'
export type CountStatus = 'draft' | 'in_progress' | 'completed' | 'verified'
export type TagFinding = 'pending' | 'found' | 'condition_changed' | 'missing' | 'not_in_masterlist' | 'out_of_scope'

export interface PhysicalCount {
  id: number
  count_number: string
  count_type: CountType
  count_date: string
  status: CountStatus
  purpose: string | null
  remarks: string | null
  department: NamedRef | null
  category: NamedRef | null
  initiated_by: NamedRef
  started_at: string | null
  completed_by: NamedRef | null
  completed_at: string | null
  verified_by: NamedRef | null
  verified_at: string | null
  total_items_counted: number
  total_items_found: number
  total_discrepancies: number
  created_at: string
  can_edit: boolean
  can_start: boolean
  can_count: boolean
  can_complete: boolean
  can_return: boolean
  can_verify: boolean
  can_delete: boolean
  verify_blocked: string | null
}

export interface CountLine {
  id: number
  item: { id: number; code: string; name: string; unit_of_measure: string; category: string | null; on_hand: number }
  system_quantity: number
  counted_quantity: number | null
  variance: number | null
  unit_cost: number
  variance_value: number | null
  location: string | null
  remarks: string | null
  counted_by: NamedRef | null
  counted_at: string | null
  moved_since_cutoff: number
  adjustment_number: string | null
}

export interface CountTag {
  id: number
  property_id: number | null
  tag_number: string
  description: string | null
  holder: NamedRef | null
  department: NamedRef | null
  book_value: number | null
  condition_on_record: string | null
  in_masterlist: boolean
  in_scope: boolean
  result: 'pending' | 'found' | 'missing'
  finding: TagFinding
  observed_condition: string | null
  location: string | null
  remarks: string | null
  checked_by: NamedRef | null
  checked_at: string | null
  removable: boolean
}

export interface CountSummary {
  lines: number
  counted: number
  matched: number
  short_count: number
  short_value: number
  over_count: number
  over_value: number
  expected: number
  found: number
  missing: number
  missing_value: number
  not_in_masterlist: number
  out_of_scope: number
  condition_changed: number
  unserviceable: number
}

export interface CountDetail {
  count: PhysicalCount
  summary: CountSummary
  lines: CountLine[]
  properties: CountTag[]
}

// ── Settings, audit, log trails ──

export interface SystemSettings {
  capitalization_threshold: number
  idle_timeout_minutes: number
  password_min_length: number
}

export interface MisSettings {
  agency_name: string
  agency_head_name: string
  agency_head_title: string
  agency_address: string
  fund_cluster: string
  fefo_expiry_warning_days: number
}

export interface SignatoryAssignment {
  role_key: string
  label: string
  user_id: number | null
  user_name: string | null
  has_signature: boolean
}

export interface DocumentLogo {
  id: number
  position: string
  sequence: number
  label: string | null
  data_uri: string | null
}

export interface AuditEntry {
  id: number
  user: NamedRef | null
  user_name: string | null
  user_role: string | null
  action: string
  auditable_type: string | null
  auditable_id: number | null
  description: string | null
  ip_address: string | null
  created_at: string
  old_values: Record<string, unknown> | null
  new_values: Record<string, unknown> | null
  user_agent: string | null
  url: string | null
  method: string | null
}

export interface InventoryLogEntry {
  id: number
  item: { id: number; code: string; name: string } | null
  user: NamedRef | null
  action: string
  quantity: number | null
  previous_quantity: number | null
  new_quantity: number | null
  reference_type: string | null
  reference_id: number | null
  notes: string | null
  created_at: string
}

export interface LoginAttemptEntry {
  id: number
  email: string
  ip_address: string
  user_agent: string | null
  successful: boolean
  failure_reason: string | null
  created_at: string
}

export interface ActiveUser {
  id: number
  name: string
  email: string
  role: string
  department: string | null
  last_seen_at: string
  signed_in_at: string | null
  ip_address: string | null
}

export interface AppNotification {
  id: number
  type: string
  title: string
  message: string
  related_type: string | null
  related_id: number | null
  is_read: boolean
  created_at: string | null
}

export interface Ranked {
  name: string
  code: string | null
  count: number
  amount: number
}

export interface WatchEntry {
  kind: string
  severity: 'overdue' | 'due_soon'
  reference: string
  title: string | null
  detail: string
  date: string | null
  days: number | null
  related_type: string | null
  related_id: number | null
}

export interface DashboardData {
  fiscal_year: number
  me: { open_requests: number; open_purchase_requests: number; assets: number; assets_value: number }
  todo: { key: string; label: string; count: number; path: string }[]
  inventory?: {
    total_items: number
    low_stock: number
    out_of_stock: number
    total_value: number
    pending_requests: number
    requests_today: number
    expiring_lots: number
    expired_lots: number
    top_items: Ranked[]
    by_department: Ranked[]
  }
  assets?: { total: number; assigned: number; available: number; under_repair: number; acquisition_cost: number; book_value: number; disposal_pending: number }
  procurement?: {
    active: Record<string, number>
    app_utilization: { estimated: number; utilized: number; remaining: number; rate: number }
    pr_by_status: Record<string, number>
    awards: { biddings: number; rfqs: number; po_value: number }
    watch: { overdue: number; due_soon: number; top: WatchEntry[] }
  }
  suppliers?: { active: number; total: number; expiring_compliance: number; top_awarded: Ranked[]; top_rated: Ranked[] }
}

export type MonitorLevel = 'out' | 'low' | 'normal' | 'over' | 'untracked'
export type Movement = 'fast' | 'slow' | 'non_moving' | 'idle'

export interface StockRow {
  id: number
  code: string
  name: string
  category: { id: number; name: string } | null
  unit_of_measure: string
  current_stock: number
  min_stock: number
  max_stock: number
  level: MonitorLevel
  deficit: number
  monthly_usage: number
  months_of_cover: number | null
  suggested_order: number
  unit_cost: number
  suggested_value: number
  issued_quantity: number
  movement: Movement
}

export interface StockMonitor {
  usage_days: number
  summary: {
    total_items: number
    out_of_stock: number
    low_stock: number
    pending_requests: number
    inventory_value: number
    reorder_value: number
    expired_lots: number
    expiring_lots: number
  }
  levels: Record<MonitorLevel, number>
  movement: Record<Movement, number>
  items: StockRow[]
  reorder: StockRow[]
  categories: { id: number; name: string }[]
}

export interface RequestTrend {
  year: number
  month: number
  label: string
  total_requests: number
  fulfilled: number
  rejected: number
  open: number
  units_issued: number
}

export interface DocumentVersion {
  id: number
  version_number: number
  original_filename: string
  mime_type: string | null
  file_size: number
  file_size_human: string
  checksum: string | null
  remarks: string | null
  uploader: { id: number; name: string } | null
  created_at: string | null
}

export interface RepoDocument {
  id: number
  title: string
  document_number: string | null
  type: string
  category: string | null
  description: string | null
  documentable_type: string | null
  documentable_id: number | null
  status: 'active' | 'archived'
  current_version: number
  uploader: { id: number; name: string } | null
  latest_version: DocumentVersion | null
  versions: DocumentVersion[] | null
  created_at: string | null
  updated_at: string | null
}

export interface InventorySummary {
  total_items: number
  total_value: number
  low_stock_items: number
  out_of_stock_items: number
  by_category: Record<string, { count: number; value: number }>
}

export interface RequestLogEntry {
  id: number
  quantity: number
  requested_at: string | null
  department: { id: number; name: string } | null
  item: { id: number; code: string; name: string } | null
  requester: { id: number; name: string } | null
}

export interface RequestLogSummary {
  total_requests: number
  total_quantity: number
  by_department: Record<string, { count: number; quantity: number }>
  by_item: Record<string, { count: number; quantity: number }>
  time_period: { start: string | null; end: string | null }
}

export interface RsmiSummary {
  id: number
  report_number: string
  year: number
  month: number
  generated_at: string
  total_requests: number
  fulfilled_requests: number
  pending_requests: number
  rejected_requests: number
  requests_by_department: Record<string, { total: number; fulfilled: number; pending: number }>
  items_requested: Record<string, { total_quantity: number; total_approved: number; request_count: number }>
  monthly_trend: Record<string, { month: number; total: number; fulfilled: number }>
}

export interface ApcpiPillar {
  key: string
  title: string
  indicators: [string, string][]
}

export interface ApcpiScore {
  score: number
  remarks: string | null
  means_of_verification: string | null
}

export interface ApcpiSummary {
  pillars: Record<string, { title: string; average: number; maturity: string }>
  overall: number
  maturity: string
}

export interface ApcpiAssessment {
  id: number
  fiscal_year: number
  status: 'draft' | 'finalized'
  remarks: string | null
  conducted_by: string | null
  finalized_at: string | null
  catalogue: ApcpiPillar[]
  scores: Record<string, ApcpiScore>
  summary: ApcpiSummary
  evidence: Record<string, { suggested: number | null; basis: string }>
}

export interface ApcpiListRow {
  id: number
  fiscal_year: number
  status: 'draft' | 'finalized'
  conducted_by: string | null
  overall: number
  maturity: string
  finalized_at: string | null
}

export interface QrLabel {
  type: 'item' | 'property' | 'gate-pass'
  id: number
  code: string
  title: string
  subtitle: string | null
  accountability: string | null
  url: string
  data_uri: string
}

export type ScanResult = Record<string, string | number | null> & { type: 'item' | 'property' | 'gate-pass'; id: number; code: string; title: string; path: string }

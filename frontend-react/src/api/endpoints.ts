import { api } from './client'
import type {
  AppPlan,
  AwardSource,
  BacCompliance,
  BacMeeting,
  BacMember,
  BacResolution,
  Bidding,
  BiddingPrOption,
  Asset,
  AssetMove,
  BudgetSummary,
  Depreciation,
  Disposal,
  GatePass,
  AuthUser,
  Category,
  Contract,
  ContractSource,
  Classification,
  Department,
  ExpiringStock,
  InventoryConfig,
  InspectionReport,
  Item,
  SemiExpendableIssue,
  StockAdjustment,
  StockCard,
  StockLot,
  SupplyRequest,
  StockTransfer,
  Supplier,
  CountDetail,
  PhysicalCount,
  CountType,
  LoginResponse,
  Paged,
  PermissionCatalogue,
  Ppmp,
  PurchaseOrder,
  ProcurementOptions,
  PurchaseRequest,
  Rfq,
  RfqAbstract,
  RfqPrOption,
  Role,
  RoleOption,
  User,
} from './types'

export const authApi = {
  login: (email: string, password: string, otp?: string) =>
    api<LoginResponse>('POST', '/auth/login', { email, password, otp: otp || undefined }, { skipAuthRedirect: true }),
  me: () => api<AuthUser>('GET', '/auth/me'),
  logout: () => api<{ message: string }>('POST', '/auth/logout', undefined, { skipAuthRedirect: true }),
}

export interface UserInput {
  name: string
  email: string
  password?: string
  password_confirmation?: string
  role_id: number
  department_id: number
}

export const usersApi = {
  list: (page: number, search: string, roleId?: number) =>
    api<Paged<User>>('GET', '/users', undefined, { params: { page, search, role_id: roleId } }),
  create: (input: UserInput) => api<{ message: string; user: User }>('POST', '/users', input),
  update: (id: number, input: Partial<UserInput>) => api<{ message: string; user: User }>('PUT', `/users/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/users/${id}`),
  roleOptions: () => api<{ data: RoleOption[] }>('GET', '/users/roles'),
  departments: () => api<{ data: Department[] }>('GET', '/users/departments'),
}

export interface RoleInput {
  name: string
  description: string
  permissions: string[]
}

export const rolesApi = {
  list: () => api<{ data: Role[] }>('GET', '/roles'),
  catalogue: () => api<{ data: PermissionCatalogue }>('GET', '/roles/permissions-list'),
  create: (input: RoleInput) => api<{ message: string; data: Role }>('POST', '/roles', input),
  update: (id: number, input: RoleInput) => api<{ message: string; data: Role }>('PUT', `/roles/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/roles/${id}`),
}

// ── Inventory ──

export interface LotInput {
  id?: number
  brand_name?: string
  specifications?: string
  warranty?: string
  warranty_months?: number | null
  quantity: number
  unit_cost: number
  supplier_id?: number | null
  location?: string
  lot_number?: string
  expiration_date?: string | null
  received_date?: string | null
}

export interface ItemInput {
  code?: string
  name: string
  description?: string
  category_id: number
  unit_of_measure: string
  min_stock: number
  max_stock: number
  classification: Classification
  is_assembly: boolean
  stock_entries?: LotInput[]
}

export interface ItemFilters {
  page: number
  search?: string
  category_id?: number
  classification?: Classification
  stock_level?: 'out' | 'low' | 'attention'
}

export const inventoryApi = {
  config: () => api<InventoryConfig>('GET', '/inventory/config'),
  categories: () => api<Category[]>('GET', '/inventory/categories'),
  createCategory: (name: string, parent_id: number | null) =>
    api<Category>('POST', '/inventory/categories', { name, parent_id }),
  updateCategory: (id: number, name: string, parent_id: number | null) =>
    api<Category>('PUT', `/inventory/categories/${id}`, { name, parent_id }),
  deleteCategory: (id: number) => api<{ message: string }>('DELETE', `/inventory/categories/${id}`),

  items: (f: ItemFilters) => api<Paged<Item>>('GET', '/inventory/items', undefined, { params: { ...f } }),
  itemOptions: (search?: string) => api<Item[]>('GET', '/inventory/items/options', undefined, { params: { search } }),
  item: (id: number) => api<Item>('GET', `/inventory/items/${id}`),
  createItem: (input: ItemInput) => api<Item>('POST', '/inventory/items', input),
  updateItem: (id: number, input: Partial<ItemInput>) => api<Item>('PUT', `/inventory/items/${id}`, input),
  deleteItem: (id: number) => api<{ message: string }>('DELETE', `/inventory/items/${id}`),

  addStock: (itemId: number, lot: LotInput) =>
    api<{ message: string; stock: StockLot }>('POST', `/inventory/items/${itemId}/stock`, lot),
  lots: (itemId: number) => api<StockLot[]>('GET', `/inventory/items/${itemId}/lots`),
  locations: () => api<string[]>('GET', '/inventory/locations'),
  stockEntries: (page: number, itemId?: number) =>
    api<Paged<StockLot>>('GET', '/inventory/stock-entries', undefined, { params: { page, item_id: itemId } }),
  transfer: (input: { stock_id: number; to_location: string; quantity: number; transfer_date?: string; remarks?: string }) =>
    api<{ message: string; transfer: StockTransfer }>('POST', '/inventory/transfer-stock', input),
  transfers: (page: number, location?: string) =>
    api<Paged<StockTransfer>>('GET', '/inventory/stock-transfers', undefined, { params: { page, location } }),
  expiring: (days?: number) => api<ExpiringStock>('GET', '/inventory/expiring', undefined, { params: { days } }),
  stockCard: (itemId: number) => api<StockCard>('GET', `/reports/stock-card/${itemId}/preview`),
}

export interface SupplierInput {
  vendor_code?: string | null
  name?: string
  tin?: string | null
  philgeps_reg_number?: string | null
  contact_person?: string | null
  email?: string | null
  phone?: string | null
  mobile_number?: string | null
  address?: string | null
  website?: string | null
  credit_terms?: string | null
  notes?: string | null
  is_active?: boolean
  is_msme?: boolean
}

export const supplierApi = {
  options: () => api<{ data: { id: number; name: string }[] }>('GET', '/suppliers/options'),
  list: (f: { page: number; active?: boolean; eligible?: boolean; search?: string }) => api<Paged<Supplier>>('GET', '/suppliers', undefined, { params: f }),
  get: (id: number) => api<Supplier>('GET', `/suppliers/${id}`),
  create: (input: SupplierInput) => api<Supplier>('POST', '/suppliers', input),
  update: (id: number, input: SupplierInput) => api<Supplier>('PUT', `/suppliers/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/suppliers/${id}`),
  setEligibility: (id: number, eligible: boolean, reason?: string) => api<Supplier>('POST', `/suppliers/${id}/eligibility`, { eligible, reason }),
  addDocument: (id: number, input: { document_type: string; reference_number?: string | null; issued_date?: string | null; expiry_date?: string | null; remarks?: string | null }) =>
    api<Supplier>('POST', `/suppliers/${id}/compliance`, input),
  removeDocument: (documentId: number) => api<Supplier>('DELETE', `/suppliers/compliance/${documentId}`),
  sanction: (id: number, input: { sanction_type: string; grounds: string; reference_number?: string | null; start_date?: string; end_date?: string | null }) =>
    api<Supplier>('POST', `/suppliers/${id}/blacklist`, input),
  lift: (sanctionId: number, reason: string) => api<Supplier>('PUT', `/suppliers/blacklist/${sanctionId}/lift`, { lifted_reason: reason }),
  evaluate: (id: number, input: { evaluation_date?: string; delivery_rating: number; quality_rating: number; timeliness_rating?: number | null; compliance_rating?: number | null; after_sales_rating?: number | null; reference?: string | null; remarks?: string | null }) =>
    api<Supplier>('POST', `/suppliers/${id}/evaluations`, input),
  removeEvaluation: (evaluationId: number) => api<Supplier>('DELETE', `/suppliers/evaluations/${evaluationId}`),
}

// ── Issuance, adjustments, semi-expendable ──

export interface RequestInput {
  department_id?: number
  purpose: string
  priority: 'low' | 'medium' | 'high'
  custodian_name?: string
  items: { item_id: number; quantity: number }[]
}

export interface Decision {
  id: number
  status: 'approved' | 'rejected'
  approved_quantity?: number
}

type Processed = { message: string; data: SupplyRequest; warnings: string[] }

export const requestsApi = {
  list: (f: { page: number; status?: string; search?: string; mine?: boolean }) =>
    api<Paged<SupplyRequest>>('GET', '/requests', undefined, { params: { ...f, mine: f.mine ? 'true' : undefined } }),
  get: (id: number) => api<SupplyRequest>('GET', `/requests/${id}`),
  create: (input: RequestInput) => api<SupplyRequest>('POST', '/requests', input),
  update: (id: number, input: RequestInput) => api<SupplyRequest>('PUT', `/requests/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/requests/${id}`),
  process: (id: number, items: Decision[]) => api<Processed>('PUT', `/requests/${id}/approve-items`, { items }),
  approveAll: (id: number) => api<Processed>('PUT', `/requests/${id}/approve`),
  reject: (id: number, reason: string) => api<{ message: string; data: SupplyRequest }>('PUT', `/requests/${id}/reject`, { reason }),
}

export const adjustmentsApi = {
  list: (f: { page: number; status?: string; adjustment_type?: string }) =>
    api<Paged<StockAdjustment>>('GET', '/stock-adjustments', undefined, { params: f }),
  create: (input: { item_id: number; adjustment_type: 'increase' | 'decrease'; quantity: number; unit_cost?: number | null; reason: string; remarks?: string }) =>
    api<StockAdjustment>('POST', '/stock-adjustments', input),
  approve: (id: number) => api<StockAdjustment>('POST', `/stock-adjustments/${id}/approve`),
  reject: (id: number, remarks?: string) => api<StockAdjustment>('POST', `/stock-adjustments/${id}/reject`, { remarks }),
  remove: (id: number) => api<{ message: string }>('DELETE', `/stock-adjustments/${id}`),
}

export const semiExpendableApi = {
  list: (f: { page: number; outstanding?: boolean; search?: string; mine?: boolean }) =>
    api<Paged<SemiExpendableIssue>>('GET', '/semi-expendable', undefined, {
      params: { page: f.page, search: f.search, outstanding: f.outstanding ? 'true' : undefined, mine: f.mine ? 'true' : undefined },
    }),
  recordReturn: (id: number, input: { quantity: number; restock: boolean; remarks?: string }) =>
    api<SemiExpendableIssue>('POST', `/semi-expendable/${id}/return`, input),
}

// ── Assets, disposal, gate pass ──

export interface RegisterInput {
  item_id: number
  stock_id: number
  assigned_to: number
  assigned_date?: string
  condition: string
  useful_life_years?: number
  salvage_value?: number | null
  units: { serial_number?: string; tag_number?: string }[]
}

export interface MoveInput {
  to_user?: number
  reason: string
  date?: string
  condition?: string
}

export const assetsApi = {
  list: (f: { page: number; search?: string; status?: string; mine?: boolean }) =>
    api<Paged<Asset>>('GET', '/properties', undefined, { params: { page: f.page, search: f.search, status: f.status, mine: f.mine ? 'true' : undefined } }),
  mine: () => api<{ data: Asset[]; summary: { total: number; total_value: number } }>('GET', '/properties/my-assets'),
  get: (id: number) => api<Asset>('GET', `/properties/${id}`),
  register: (input: RegisterInput) => api<{ message: string; accountability_number: string; properties: Asset[] }>('POST', '/properties', input),
  update: (id: number, input: Record<string, unknown>) => api<Asset>('PUT', `/properties/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/properties/${id}`),
  transfer: (id: number, input: MoveInput) => api<{ message: string; transfer: AssetMove; property: Asset }>('POST', `/properties/${id}/transfer`, input),
  turnIn: (id: number, input: MoveInput) => api<{ message: string; transfer: AssetMove; property: Asset }>('POST', `/properties/${id}/return`, input),
  issue: (id: number, input: MoveInput) => api<{ message: string; transfer: AssetMove; property: Asset }>('POST', `/properties/${id}/issue`, input),
  status: (id: number, action: string, remarks?: string) => api<Asset>('POST', `/properties/${id}/status`, { action, remarks }),
  history: (id: number) => api<AssetMove[]>('GET', `/properties/${id}/transfers`),
  moves: (page: number, type?: string, search?: string) =>
    api<Paged<AssetMove>>('GET', '/properties/transfers/all', undefined, { params: { page, type, search } }),
  depreciation: (id: number) => api<Depreciation>('GET', `/properties/${id}/depreciation`),
  runDepreciation: (period?: string) =>
    api<{ message: string; months_posted: number; assets: number; amount_posted: number }>('POST', '/depreciation/run', { period }),
  gatePasses: (id: number) => api<GatePass[]>('GET', `/gate-pass/property/${id}`),
}

export const peopleApi = {
  all: () => api<{ data: { id: number; name: string; email: string }[] }>('GET', '/users/all'),
}

export interface DisposalInput {
  disposal_category: 'property' | 'inventory'
  property_id?: number
  item_id?: number
  quantity?: number
  disposal_type: string
  reason: string
  remarks?: string
}

export const disposalApi = {
  list: (f: { page: number; status?: string; category?: string; search?: string }) => api<Paged<Disposal>>('GET', '/disposal/requests', undefined, { params: f }),
  create: (input: DisposalInput) => api<Disposal>('POST', '/disposal/requests', input),
  update: (id: number, input: DisposalInput) => api<Disposal>('PUT', `/disposal/requests/${id}`, input),
  withdraw: (id: number) => api<{ message: string }>('DELETE', `/disposal/requests/${id}`),
  submit: (id: number) => api<Disposal>('POST', `/disposal/requests/${id}/submit`),
  approve: (id: number, remarks?: string) => api<Disposal>('POST', `/disposal/requests/${id}/approve`, { remarks }),
  reject: (id: number, remarks?: string) => api<Disposal>('POST', `/disposal/requests/${id}/reject`, { remarks }),
  complete: (id: number, input: { proceeds?: number | null; disposal_date?: string; remarks?: string }) =>
    api<Disposal>('POST', `/disposal/requests/${id}/complete`, input),
}

export interface GatePassInput {
  property_id: number
  received_by: string
  purpose: string
  date_released?: string
  expected_return_date?: string | null
  condition_on_release: string
  remarks?: string
}

export const gatePassApi = {
  list: (f: { page: number; status?: string; search?: string }) => api<Paged<GatePass>>('GET', '/gate-pass', undefined, { params: f }),
  create: (input: GatePassInput) => api<GatePass>('POST', '/gate-pass', input),
  update: (id: number, input: GatePassInput) => api<GatePass>('PUT', `/gate-pass/${id}`, input),
  markReturned: (id: number, input: { actual_return_date?: string; condition_on_return: string; returned_by?: string; remarks?: string }) =>
    api<GatePass>('POST', `/gate-pass/${id}/return`, input),
}

export interface PlanLineInput {
  general_description: string
  specifications?: string | null
  code?: string | null
  unit?: string | null
  quantity: number
  estimated_unit_cost: number
  procurement_mode?: string | null
  procurement_category?: string | null
  is_sustainable?: boolean
  schedule?: string | null
  fund_source?: string | null
  remarks?: string | null
  app_item_id?: number | null
}

export interface PpmpInput {
  department_id?: number
  fiscal_year: number
  title: string
  remarks?: string | null
  items: PlanLineInput[]
}

export interface AppLineInput {
  id: number
  general_description: string
  specifications: string | null
  unit: string | null
  procurement_mode: string
  procurement_category: string
  quantity: number
  estimated_budget: number
  schedule: string | null
  fund_source: string | null
}

const PP = '/procurement-planning'

export const planningApi = {
  options: () => api<ProcurementOptions>('GET', `${PP}/options`),
  ppmps: (f: { page: number; fiscal_year?: number; status?: string; search?: string }) => api<Paged<Ppmp>>('GET', `${PP}/ppmps`, undefined, { params: f }),
  ppmp: (id: number) => api<Ppmp>('GET', `${PP}/ppmps/${id}`),
  createPpmp: (input: PpmpInput) => api<Ppmp>('POST', `${PP}/ppmps`, input),
  updatePpmp: (id: number, input: PpmpInput) => api<Ppmp>('PUT', `${PP}/ppmps/${id}`, input),
  deletePpmp: (id: number) => api<{ message: string }>('DELETE', `${PP}/ppmps/${id}`),
  ppmpAction: (id: number, action: 'submit' | 'review' | 'approve') => api<Ppmp>('POST', `${PP}/ppmps/${id}/${action}`),
  returnPpmp: (id: number, reason: string) => api<Ppmp>('POST', `${PP}/ppmps/${id}/return`, { return_reason: reason }),
  apps: (f: { page: number; fiscal_year?: number; status?: string }) => api<Paged<AppPlan>>('GET', `${PP}/apps`, undefined, { params: f }),
  app: (id: number, excludePr?: number) => api<AppPlan>('GET', `${PP}/apps/${id}`, undefined, { params: { exclude_pr_id: excludePr } }),
  generateApp: (fiscalYear: number, title?: string) => api<AppPlan>('POST', `${PP}/apps/generate`, { fiscal_year: fiscalYear, title: title || undefined }),
  updateApp: (id: number, input: { title?: string; remarks?: string | null; items?: AppLineInput[] }) => api<AppPlan>('PUT', `${PP}/apps/${id}`, input),
  approveApp: (id: number) => api<AppPlan>('POST', `${PP}/apps/${id}/approve`),
  deleteApp: (id: number) => api<{ message: string }>('DELETE', `${PP}/apps/${id}`),
  budget: (fiscalYear: number) => api<BudgetSummary>('GET', `${PP}/budget-summary`, undefined, { params: { fiscal_year: fiscalYear } }),
}

export interface PrInput {
  department_id?: number
  app_id?: number | null
  fiscal_year?: number
  purpose: string
  priority: 'low' | 'medium' | 'high'
  remarks?: string | null
  items: PlanLineInput[]
}

export const prApi = {
  list: (f: { page: number; status?: string; priority?: string; fiscal_year?: number; search?: string; awaiting_me?: boolean; mine?: boolean }) =>
    api<Paged<PurchaseRequest>>('GET', '/purchase-requests', undefined, { params: f }),
  get: (id: number) => api<PurchaseRequest>('GET', `/purchase-requests/${id}`),
  create: (input: PrInput) => api<PurchaseRequest>('POST', '/purchase-requests', input),
  update: (id: number, input: PrInput) => api<PurchaseRequest>('PUT', `/purchase-requests/${id}`, input),
  submit: (id: number) => api<PurchaseRequest>('POST', `/purchase-requests/${id}/submit`),
  cancel: (id: number) => api<PurchaseRequest>('POST', `/purchase-requests/${id}/cancel`),
  remove: (id: number) => api<{ message: string }>('DELETE', `/purchase-requests/${id}`),
  approveStep: (id: number, remarks?: string) => api<PurchaseRequest>('POST', `/purchase-requests/${id}/approve-step`, { remarks }),
  returnStep: (id: number, reason: string) => api<PurchaseRequest>('POST', `/purchase-requests/${id}/return`, { return_reason: reason }),
  certifyFunds: (id: number, input: { caf_amount?: number; caf_remarks?: string }) => api<PurchaseRequest>('POST', `/purchase-requests/${id}/certify-funds`, input),
}

export interface RfqInput {
  title: string
  procurement_method: string
  purchase_request_id: number
  deadline: string | null
  delivery_term?: string | null
  delivery_lead_days?: number | null
  description?: string | null
  justification?: string | null
  items: { purchase_request_item_id: number; quantity: number; abc_unit_cost?: number; specifications?: string | null; delivery_lead_time?: string | null }[]
}

export interface QuotationInput {
  supplier_id?: number | null
  supplier_name?: string
  quotation_number?: string | null
  submitted_at?: string
  remarks?: string | null
  items: { rfq_item_id: number; unit_price: number }[]
}

export const rfqApi = {
  list: (f: { page: number; status?: string; procurement_method?: string; search?: string }) => api<Paged<Rfq>>('GET', '/rfq', undefined, { params: f }),
  get: (id: number) => api<Rfq>('GET', `/rfq/${id}`),
  abstract: (id: number) => api<RfqAbstract>('GET', `/rfq/${id}/abstract`),
  prOptions: () => api<{ data: RfqPrOption[]; methods: string[]; single_source: string[] }>('GET', '/rfq/purchase-requests'),
  create: (input: RfqInput) => api<Rfq>('POST', '/rfq', input),
  update: (id: number, input: RfqInput) => api<Rfq>('PUT', `/rfq/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/rfq/${id}`),
  issue: (id: number) => api<Rfq>('POST', `/rfq/${id}/issue`),
  close: (id: number) => api<Rfq>('POST', `/rfq/${id}/close`),
  cancel: (id: number, remarks?: string) => api<Rfq>('POST', `/rfq/${id}/cancel`, { remarks }),
  addQuotation: (id: number, input: QuotationInput) => api<Rfq>('POST', `/rfq/${id}/quotations`, input),
  removeQuotation: (quotationId: number) => api<Rfq>('DELETE', `/rfq/quotations/${quotationId}`),
  setResponsive: (quotationId: number, isResponsive: boolean, remarks?: string) =>
    api<Rfq>('PUT', `/rfq/quotations/${quotationId}/responsive`, { is_responsive: isResponsive, remarks }),
  award: (id: number, input: { quotation_id: number; bac_resolution_id: number; remarks?: string }) => api<Rfq>('POST', `/rfq/${id}/award`, input),
  noa: (id: number, date?: string) => api<Rfq>('POST', `/rfq/${id}/noa`, { date: date || undefined }),
  ntp: (id: number, date?: string) => api<Rfq>('POST', `/rfq/${id}/ntp`, { date: date || undefined }),
}

export interface BacMemberInput {
  user_id?: number | null
  full_name: string
  position?: string | null
  office?: string | null
  designation: string
  appointment_order_no?: string | null
  appointment_date?: string | null
  term_start?: string | null
  term_end?: string | null
  is_active?: boolean
  sequence?: number
  remarks?: string | null
}

export interface BacMeetingInput {
  title?: string
  meeting_date?: string
  venue?: string | null
  agenda?: string | null
  minutes?: string | null
  status?: string
  remarks?: string | null
}

export interface BacResolutionInput {
  title: string
  resolution_type: string
  bac_meeting_id?: number | null
  purchase_request_id?: number | null
  content?: string | null
  remarks?: string | null
}

export const bacApi = {
  members: () => api<{ data: BacMember[]; compliance: BacCompliance }>('GET', '/bac/members'),
  addMember: (input: BacMemberInput) => api<BacMember>('POST', '/bac/members', input),
  updateMember: (id: number, input: BacMemberInput) => api<BacMember>('PUT', `/bac/members/${id}`, input),
  removeMember: (id: number) => api<{ message: string }>('DELETE', `/bac/members/${id}`),
  meetings: (f: { page: number; status?: string; search?: string }) => api<Paged<BacMeeting>>('GET', '/bac/meetings', undefined, { params: f }),
  meeting: (id: number) => api<BacMeeting>('GET', `/bac/meetings/${id}`),
  createMeeting: (input: BacMeetingInput) => api<BacMeeting>('POST', '/bac/meetings', input),
  updateMeeting: (id: number, input: BacMeetingInput) => api<BacMeeting>('PUT', `/bac/meetings/${id}`, input),
  attendance: (id: number, attendees: { user_id?: number | null; name: string; committee_role: string | null; present: boolean }[]) =>
    api<BacMeeting>('POST', `/bac/meetings/${id}/attendance`, { attendees }),
  removeMeeting: (id: number) => api<{ message: string }>('DELETE', `/bac/meetings/${id}`),
  resolutions: (f: { page: number; status?: string; resolution_type?: string; purchase_request_id?: number; search?: string }) =>
    api<Paged<BacResolution>>('GET', '/bac/resolutions', undefined, { params: f }),
  resolution: (id: number) => api<BacResolution>('GET', `/bac/resolutions/${id}`),
  createResolution: (input: BacResolutionInput) => api<BacResolution>('POST', '/bac/resolutions', input),
  updateResolution: (id: number, input: BacResolutionInput) => api<BacResolution>('PUT', `/bac/resolutions/${id}`, input),
  resolutionAction: (id: number, action: 'submit' | 'approve' | 'archive') => api<BacResolution>('POST', `/bac/resolutions/${id}/${action}`),
  returnResolution: (id: number, remarks: string) => api<BacResolution>('POST', `/bac/resolutions/${id}/return`, { remarks }),
  removeResolution: (id: number) => api<{ message: string }>('DELETE', `/bac/resolutions/${id}`),
}

export interface BiddingInput {
  title: string
  purchase_request_id: number
  delivery_term?: string | null
  delivery_lead_days?: number | null
  evaluation_method: 'price' | 'mearb'
  price_weight?: number
  technical_weight?: number
  remarks?: string | null
  items: { purchase_request_item_id: number; quantity: number; abc_unit_cost?: number }[]
}

export interface BidderInput {
  supplier_id?: number | null
  supplier_name?: string
  bid_amount?: number | null
  bid_security_form?: string | null
  bid_security_amount?: number | null
  bid_security_posted?: boolean
}

const BD = '/bidding'

export const biddingApi = {
  list: (f: { page: number; status?: string; search?: string }) => api<Paged<Bidding>>('GET', BD, undefined, { params: f }),
  get: (id: number) => api<Bidding>('GET', `${BD}/${id}`),
  prOptions: () =>
    api<{ data: BiddingPrOption[]; security_forms: string[]; protest_types: string[]; protest_statuses: string[]; observer_types: string[] }>('GET', `${BD}/purchase-requests`),
  create: (input: BiddingInput) => api<Bidding>('POST', BD, input),
  update: (id: number, input: BiddingInput) => api<Bidding>('PUT', `${BD}/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `${BD}/${id}`),
  criteria: (id: number, criteria: { name: string; weight: number; is_environmental?: boolean }[]) => api<Bidding>('PUT', `${BD}/${id}/criteria`, { criteria }),
  preProc: (id: number, input: { pre_proc_date: string; pre_proc_notes?: string | null }) => api<Bidding>('POST', `${BD}/${id}/pre-proc`, input),
  invitation: (id: number, input: { itb_reference?: string | null; itb_posted_at?: string; itb_publication?: string | null; submission_deadline: string; bid_opening_date?: string | null }) =>
    api<Bidding>('POST', `${BD}/${id}/invitation`, input),
  openBids: (id: number) => api<Bidding>('POST', `${BD}/${id}/open-bids`),
  addBidder: (id: number, input: BidderInput) => api<Bidding>('POST', `${BD}/${id}/bidders`, input),
  evaluate: (bidderId: number, input: { stage: string; result: string; remarks?: string }) => api<Bidding>('PUT', `${BD}/bidders/${bidderId}`, input),
  removeBidder: (bidderId: number) => api<Bidding>('DELETE', `${BD}/bidders/${bidderId}`),
  scores: (bidderId: number, scores: { bidding_criterion_id: number; score: number }[]) => api<Bidding>('PUT', `${BD}/bidders/${bidderId}/scores`, { scores }),
  declareFailed: (id: number, reason: string) => api<{ bidding: Bidding; message: string; may_negotiate: boolean }>('POST', `${BD}/${id}/declare-failed`, { reason }),
  cancel: (id: number, reason?: string) => api<Bidding>('POST', `${BD}/${id}/cancel`, { reason }),
  award: (id: number, input: { bidder_id: number; bac_resolution_id: number; contract_recommendation?: string }) => api<Bidding>('POST', `${BD}/${id}/award`, input),
  noa: (id: number) => api<Bidding>('POST', `${BD}/${id}/noa`, {}),
  ntp: (id: number) => api<Bidding>('POST', `${BD}/${id}/ntp`, {}),
  addProtest: (id: number, input: { protest_type: string; supplier_id?: number | null; filed_by?: string | null; grounds: string; filed_date?: string }) =>
    api<Bidding>('POST', `${BD}/${id}/protests`, input),
  updateProtest: (protestId: number, input: { status: string; resolution?: string; resolved_date?: string }) => api<Bidding>('PUT', `${BD}/protests/${protestId}`, input),
  addObserver: (id: number, input: { name: string; organization?: string | null; observer_type: string; invited_date?: string | null }) =>
    api<Bidding>('POST', `${BD}/${id}/observers`, input),
  updateObserver: (observerId: number, input: { name: string; organization?: string | null; observer_type: string; invited?: boolean; invited_date?: string | null; attended?: boolean }) =>
    api<Bidding>('PUT', `${BD}/observers/${observerId}`, input),
  removeObserver: (observerId: number) => api<Bidding>('DELETE', `${BD}/observers/${observerId}`),
}

export interface PoInput {
  source_type?: 'rfq' | 'bidding'
  source_id?: number
  delivery_date?: string | null
  delivery_address?: string | null
  remarks?: string | null
  prices?: { source_item_id: number; unit_cost: number }[]
}

export const poApi = {
  sources: () => api<{ data: AwardSource[] }>('GET', '/procurement/award-sources'),
  list: (f: { page: number; status?: string; search?: string }) => api<Paged<PurchaseOrder>>('GET', '/procurement/purchase-orders', undefined, { params: f }),
  get: (id: number) => api<PurchaseOrder>('GET', `/procurement/purchase-orders/${id}`),
  create: (input: PoInput) => api<PurchaseOrder>('POST', '/procurement/purchase-orders', input),
  update: (id: number, input: PoInput) => api<PurchaseOrder>('PUT', `/procurement/purchase-orders/${id}`, input),
  send: (id: number) => api<PurchaseOrder>('POST', `/procurement/purchase-orders/${id}/send`),
  confirm: (id: number) => api<PurchaseOrder>('POST', `/procurement/purchase-orders/${id}/confirm`),
  cancel: (id: number, reason?: string) => api<PurchaseOrder>('POST', `/procurement/purchase-orders/${id}/cancel`, { reason }),
  remove: (id: number) => api<{ message: string }>('DELETE', `/procurement/purchase-orders/${id}`),
}

export interface IarInput {
  purchase_order_id: number
  delivery_date: string
  inspection_date?: string
  observations?: string | null
  remarks?: string | null
  items: { purchase_order_item_id: number; quantity_received: number; quantity_accepted: number; quantity_rejected: number; remarks?: string | null }[]
}

export interface AcceptLineInput {
  iar_item_id: number
  item_id: number
  lot_number?: string | null
  expiration_date?: string | null
  brand_name?: string | null
  location?: string | null
}

export const receivingApi = {
  list: (f: { page: number; acceptance_status?: string; purchase_order_id?: number; search?: string }) =>
    api<Paged<InspectionReport>>('GET', '/receiving/inspection-reports', undefined, { params: f }),
  get: (id: number) => api<InspectionReport>('GET', `/receiving/inspection-reports/${id}`),
  create: (input: IarInput) => api<InspectionReport>('POST', '/receiving/inspection-reports', input),
  update: (id: number, input: IarInput) => api<InspectionReport>('PUT', `/receiving/inspection-reports/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `/receiving/inspection-reports/${id}`),
  accept: (id: number, lines: AcceptLineInput[]) => api<InspectionReport>('POST', `/receiving/inspection-reports/${id}/accept`, { lines }),
  reject: (id: number, remarks: string) => api<InspectionReport>('POST', `/receiving/inspection-reports/${id}/reject`, { remarks }),
}

export interface ContractInput {
  source_type?: 'rfq' | 'bidding'
  source_id?: number
  title?: string
  contract_type?: string
  start_date?: string | null
  end_date?: string | null
  signed_date?: string | null
  delivery_lead_days?: number | null
  description?: string | null
  terms?: string | null
  remarks?: string | null
}

const CT = '/contracts'

export const contractApi = {
  list: (f: { page: number; status?: string; contract_type?: string; expiring_days?: number; search?: string }) => api<Paged<Contract>>('GET', CT, undefined, { params: f }),
  get: (id: number) => api<Contract>('GET', `${CT}/${id}`),
  sources: () => api<{ data: ContractSource[] }>('GET', `${CT}/awarded-sources`),
  create: (input: ContractInput) => api<Contract>('POST', CT, input),
  update: (id: number, input: ContractInput) => api<Contract>('PUT', `${CT}/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `${CT}/${id}`),
  activate: (id: number) => api<Contract>('POST', `${CT}/${id}/activate`),
  complete: (id: number) => api<Contract>('POST', `${CT}/${id}/complete`),
  terminate: (id: number, remarks: string) => api<Contract>('POST', `${CT}/${id}/terminate`, { remarks }),
  addSecurity: (id: number, input: { security_type: string; form?: string | null; reference_number?: string | null; amount: number; posted_date?: string; validity_date?: string | null; remarks?: string | null }) =>
    api<Contract>('POST', `${CT}/${id}/securities`, input),
  setSecurity: (securityId: number, status: 'returned' | 'forfeited', remarks?: string) => api<Contract>('PUT', `${CT}/securities/${securityId}`, { status, remarks }),
  removeSecurity: (securityId: number) => api<Contract>('DELETE', `${CT}/securities/${securityId}`),
  addAmendment: (id: number, input: { title: string; description?: string | null; amount_change?: number; new_end_date?: string | null }) => api<Contract>('POST', `${CT}/${id}/amendments`, input),
  approveAmendment: (amendmentId: number) => api<Contract>('POST', `${CT}/amendments/${amendmentId}/approve`),
  removeAmendment: (amendmentId: number) => api<Contract>('DELETE', `${CT}/amendments/${amendmentId}`),
  addExtension: (id: number, input: { requested_days: number; reason: string; requested_date?: string }) => api<Contract>('POST', `${CT}/${id}/extensions`, input),
  approveExtension: (extensionId: number, approvedDays: number, remarks?: string) => api<Contract>('PUT', `${CT}/extensions/${extensionId}/approve`, { approved_days: approvedDays, remarks }),
  denyExtension: (extensionId: number, remarks: string) => api<Contract>('PUT', `${CT}/extensions/${extensionId}/deny`, { remarks }),
  assessLd: (id: number, input: { as_of_date?: string | null; remarks?: string | null }) => api<Contract>('PUT', `${CT}/${id}/liquidated-damages`, input),
  addCalloff: (id: number, input: { title: string; amount: number; calloff_date?: string }) => api<Contract>('POST', `${CT}/${id}/calloffs`, input),
  setCalloff: (calloffId: number, status: string) => api<Contract>('PUT', `${CT}/calloffs/${calloffId}`, { status }),
}

export interface CountInput {
  count_type: CountType
  count_date: string
  department_id?: number | null
  category_id?: number | null
  purpose?: string
  remarks?: string
}

export interface CountLineInput {
  id: number
  counted_quantity: number | null
  unit_cost?: number | null
  location?: string
  remarks?: string
}

export interface ScanInput {
  tag_number: string
  observed_condition?: string
  location?: string
  remarks?: string
  description?: string
}

const PC = '/physical-inventory/counts'
export const physicalCountApi = {
  list: (f: { page: number; status?: string; count_type?: string; search?: string }) => api<Paged<PhysicalCount>>('GET', PC, undefined, { params: f }),
  options: () => api<{ departments: { id: number; name: string }[]; categories: { id: number; name: string }[] }>('GET', `${PC}/options`),
  get: (id: number) => api<CountDetail>('GET', `${PC}/${id}`),
  create: (input: CountInput) => api<CountDetail>('POST', PC, input),
  update: (id: number, input: CountInput) => api<CountDetail>('PUT', `${PC}/${id}`, input),
  remove: (id: number) => api<{ message: string }>('DELETE', `${PC}/${id}`),
  start: (id: number) => api<CountDetail>('POST', `${PC}/${id}/start`),
  record: (id: number, lines: CountLineInput[]) => api<CountDetail>('POST', `${PC}/${id}/record`, { lines }),
  scan: (id: number, input: ScanInput) => api<CountDetail>('POST', `${PC}/${id}/scan`, input),
  mark: (id: number, lineId: number, input: { result: string; observed_condition?: string | null; location?: string | null; remarks?: string | null }) =>
    api<CountDetail>('POST', `${PC}/${id}/properties/${lineId}`, input),
  unscan: (id: number, lineId: number) => api<CountDetail>('DELETE', `${PC}/${id}/properties/${lineId}`),
  complete: (id: number) => api<CountDetail>('POST', `${PC}/${id}/complete`),
  sendBack: (id: number, remarks: string) => api<CountDetail>('POST', `${PC}/${id}/return`, { remarks }),
  verify: (id: number) => api<CountDetail>('POST', `${PC}/${id}/verify`),
}

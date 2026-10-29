import { api, download } from './client'

export interface PayRef {
  id: number
  name: string | null
}

export type PayableType = 'contract' | 'purchase_order'
export type TrancheStatus = 'pending' | 'for_payment' | 'paid'
export type PaymentMethod = 'check' | 'ada' | 'lddap_ada'

export interface Tranche {
  id: number
  payable_type: PayableType
  payable_id: number
  payable_reference: string
  supplier: string | null
  sequence_no: number
  description: string
  percentage: number | null
  amount: number
  due_date: string | null
  deliverable: string | null
  responsibility_center: string | null
  mfo_pap: string | null
  uacs_code: string | null
  status: TrancheStatus
  overdue: boolean
  days_overdue: number | null
  ors_number: string | null
  dv_number: string | null
  paid_date: string | null
  payment_method: PaymentMethod | null
  payment_reference: string | null
  amount_paid: number | null
  retention_amount: number
  ld_deducted: number
  withholding_tax: number
  remarks: string | null
  created_by: PayRef | null
  approved_by: PayRef | null
  approved_at: string | null
  paid_by: PayRef | null
  can_edit: boolean
  can_approve: boolean
  can_pay: boolean
}

export interface PaymentSchedule {
  type: PayableType
  id: number
  reference: string
  title: string | null
  status: string
  supplier: PayRef
  base_amount: number
  scheduled_total: number
  unscheduled: number
  paid_total: number
  released_total: number
  outstanding: number
  overdue_count: number
  ld_assessed: number
  ld_deducted: number
  ld_available: number
  /** Set when the linked contract/PO already carries the schedule, so this one cannot. */
  linked_notice: string | null
  can_manage: boolean
  can_schedule: boolean
  tranches: Tranche[]
}

export interface TrancheInput {
  type?: PayableType
  id?: number
  description?: string
  percentage?: number | null
  amount?: number | null
  due_date?: string | null
  deliverable?: string | null
  responsibility_center?: string | null
  mfo_pap?: string | null
  uacs_code?: string | null
  remarks?: string | null
}

export interface ForPaymentInput {
  ors_number?: string | null
  dv_number?: string | null
  responsibility_center?: string | null
  mfo_pap?: string | null
  uacs_code?: string | null
  remarks?: string | null
}

export interface PayInput {
  paid_date?: string
  payment_method: PaymentMethod
  payment_reference: string
  amount_paid?: number
  retention_amount?: number
  ld_deducted?: number
  withholding_tax?: number
  remarks?: string | null
}

/** Same shape as the Monitoring page's entries (kind "payment_due"). */
export interface PaymentDue {
  kind: 'payment_due'
  severity: 'overdue' | 'due_soon'
  reference: string
  title: string | null
  detail: string
  date: string
  days: number
  related_type: string
  related_id: number
}

export interface PettyCashVoucher {
  id: number
  pcv_number: string
  voucher_date: string
  payee: string
  particulars: string
  amount: number
  responsibility_center: string | null
  status: 'pending' | 'approved' | 'cancelled'
  recorded_by: PayRef | null
  approved_by: PayRef | null
  approved_at: string | null
  replenishment: PayRef | null
  replenished: boolean
  remarks: string | null
  can_approve: boolean
  can_cancel: boolean
}

export interface PettyCashReplenishment {
  id: number
  report_number: string
  report_date: string
  total: number
  voucher_count: number
  status: 'requested' | 'replenished'
  requested_by: PayRef | null
  replenished_by: PayRef | null
  replenished_date: string | null
  reference: string | null
  remarks: string | null
  can_replenish: boolean
}

export interface PettyCashFund {
  id: number
  fund_name: string
  custodian: PayRef | null
  amount: number
  fund_cluster: string | null
  status: 'active' | 'closed'
  remarks: string | null
  /** Approved vouchers not yet replenished. */
  disbursed: number
  pending: number
  in_replenishment: number
  /** Cash on hand: the fund less approved vouchers not yet replenished. */
  balance: number
  /** Cash on hand less vouchers awaiting approval. */
  available: number
  /** Approved vouchers not yet put on a replenishment report. */
  unrequested: number
  voucher_count: number
  max_per_voucher: number
  can_manage: boolean
  can_record: boolean
  can_request_replenishment: boolean
  created_at: string | null
  /** Present on the single-fund read. */
  vouchers: PettyCashVoucher[] | null
  replenishments: PettyCashReplenishment[] | null
}

export interface FundInput {
  fund_name?: string
  custodian_id?: number
  amount?: number
  fund_cluster?: string | null
  status?: 'active' | 'closed'
  remarks?: string | null
}

export interface VoucherInput {
  voucher_date?: string
  payee: string
  particulars: string
  amount: number
  responsibility_center?: string | null
  remarks?: string | null
}

const P = '/payments'
const PC = '/petty-cash'

export const paymentsApi = {
  schedule: (type: PayableType, id: number) => api<PaymentSchedule>('GET', `${P}/schedule`, undefined, { params: { type, id } }),
  tranches: (f: { status?: TrancheStatus; overdue?: boolean } = {}) => api<{ data: Tranche[] }>('GET', `${P}/tranches`, undefined, { params: f }),
  due: (days?: number) => api<{ data: PaymentDue[] }>('GET', `${P}/due`, undefined, { params: { days } }),
  addTranche: (input: TrancheInput & { type: PayableType; id: number }) => api<PaymentSchedule>('POST', `${P}/tranches`, input),
  updateTranche: (id: number, input: TrancheInput) => api<PaymentSchedule>('PUT', `${P}/tranches/${id}`, input),
  removeTranche: (id: number) => api<PaymentSchedule>('DELETE', `${P}/tranches/${id}`),
  forPayment: (id: number, input: ForPaymentInput = {}) => api<PaymentSchedule>('POST', `${P}/tranches/${id}/for-payment`, input),
  pay: (id: number, input: PayInput) => api<PaymentSchedule>('POST', `${P}/tranches/${id}/pay`, input),
  orsPath: (id: number) => `${P}/tranches/${id}/ors`,
  dvPath: (id: number) => `${P}/tranches/${id}/dv`,
  downloadOrs: (id: number) => download(`${P}/tranches/${id}/ors`, {}, 'ORS.xlsx'),
  downloadDv: (id: number) => download(`${P}/tranches/${id}/dv`, {}, 'DV.xlsx'),
}

export const pettyCashApi = {
  funds: () => api<{ data: PettyCashFund[] }>('GET', `${PC}/funds`),
  fund: (id: number) => api<PettyCashFund>('GET', `${PC}/funds/${id}`),
  createFund: (input: FundInput) => api<PettyCashFund>('POST', `${PC}/funds`, input),
  updateFund: (id: number, input: FundInput) => api<PettyCashFund>('PUT', `${PC}/funds/${id}`, input),
  addVoucher: (fundId: number, input: VoucherInput) => api<PettyCashFund>('POST', `${PC}/funds/${fundId}/vouchers`, input),
  approveVoucher: (id: number) => api<PettyCashFund>('POST', `${PC}/vouchers/${id}/approve`),
  cancelVoucher: (id: number, remarks: string) => api<PettyCashFund>('POST', `${PC}/vouchers/${id}/cancel`, { remarks }),
  requestReplenishment: (fundId: number, input: { report_date?: string; remarks?: string | null } = {}) =>
    api<PettyCashFund>('POST', `${PC}/funds/${fundId}/replenishments`, input),
  replenish: (id: number, input: { reference: string; replenished_date?: string; remarks?: string | null }) =>
    api<PettyCashFund>('POST', `${PC}/replenishments/${id}/replenish`, input),
  pcvPath: (id: number) => `${PC}/vouchers/${id}/pcv`,
  reportPath: (id: number) => `${PC}/replenishments/${id}/report`,
}

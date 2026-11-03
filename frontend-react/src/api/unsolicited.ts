import { api } from './client'
import type { NamedRef, Paged } from './types'

export interface UnsolicitedOffer {
  id: number
  offer_number: string
  supplier: NamedRef
  title: string
  concept: string
  category: string
  proposed_amount: number
  received_date: string
  status: string
  notice_date: string | null
  documents_date: string | null
  new_concept_basis: string | null
  evaluation_extension_days: number | null
  evaluation_extension_reason: string | null
  evaluation_date: string | null
  negotiation_start: string | null
  negotiation_end: string | null
  negotiated_amount: number | null
  negotiated_terms: string | null
  certification_number: string | null
  certification_date: string | null
  bid_security_form: string | null
  bid_security_amount: number | null
  bid_security_date: string | null
  bid_security_required: number | null
  bidding: NamedRef | null
  bidding_status: string | null
  lccrb_date: string | null
  lccrb_supplier: NamedRef | null
  lccrb_amount: number | null
  match_result: string | null
  match_amount: number | null
  match_date: string | null
  closing_reason: string | null
  closed_date: string | null
  late_withdrawal: boolean
  due_date: string | null
  due_for: string | null
  overdue: boolean
  can_manage: boolean
}

const U = '/unsolicited-offers'
const step = (id: number, name: string, input: unknown = {}) => api<UnsolicitedOffer>('POST', `${U}/${id}/${name}`, input)

export const unsolicitedApi = {
  options: () => api<{ bases: Record<string, string>; security_forms: string[]; categories: string[] }>('GET', `${U}/options`),
  list: (params: { page?: number; status?: string; search?: string }) => api<Paged<UnsolicitedOffer>>('GET', U, undefined, { params }),
  show: (id: number) => api<UnsolicitedOffer>('GET', `${U}/${id}`),
  create: (input: { supplier_id: number; title: string; concept: string; category: string; proposed_amount: number; received_date?: string }) => api<UnsolicitedOffer>('POST', U, input),
  assess: (id: number, input: { needed: boolean; notice_date?: string; reason?: string }) => step(id, 'assess', input),
  documents: (id: number, input: { concept_details: boolean; proof_of_concept: boolean; eligibility_documents: boolean; terms_and_consent: boolean; date?: string }) => step(id, 'documents', input),
  evaluate: (
    id: number,
    input: { favorable: boolean; new_concept_basis?: string; funds_available?: boolean; eligibility_verified?: boolean; date?: string; negotiation_start?: string; extension_days?: number; extension_reason?: string; reason?: string },
  ) => step(id, 'evaluate', input),
  negotiation: (id: number, input: { successful: boolean; date?: string; negotiated_amount?: number; terms?: string; certification_number?: string; reason?: string }) => step(id, 'negotiation', input),
  bidSecurity: (id: number, input: { form: string; amount?: number; date?: string }) => step(id, 'bid-security', input),
  linkBidding: (id: number, biddingId: number) => step(id, 'link-bidding', { bidding_id: biddingId }),
  startMatching: (id: number) => step(id, 'start-matching'),
  match: (id: number, input: { matched: boolean; amount?: number; date?: string }) => step(id, 'match', input),
  reject: (id: number, input: { reason: string; date?: string }) => step(id, 'reject', input),
  withdraw: (id: number, input: { reason?: string; date?: string }) => step(id, 'withdraw', input),
}

export const BIDDING_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  pre_procurement: ['Pre-procurement', 'st-pending'],
  invitation: ['Invitation to Bid', 'st-pending'],
  bid_opening: ['Bid opening', 'st-partial'],
  evaluation: ['Evaluation', 'st-partial'],
  awarded: ['Awarded', 'st-ok'],
  failed: ['Failed', 'st-bad'],
  cancelled: ['Cancelled', 'badge-muted'],
}

export const STAGES: ['eligibility' | 'technical' | 'financial' | 'post_qualification', string][] = [
  ['eligibility', 'Eligibility'],
  ['technical', 'Technical'],
  ['financial', 'Financial'],
  ['post_qualification', 'Post-qual.'],
]

export const SECURITY_FORMS: Record<string, string> = {
  cash: 'Cash (2%)',
  cashiers_check: "Cashier's check (2%)",
  managers_check: "Manager's check (2%)",
  bank_draft: 'Bank draft or guarantee (5%)',
  irrevocable_letter_of_credit: 'Irrevocable LC (5%)',
  surety_bond: 'Surety bond (5%)',
  bid_securing_declaration: 'Bid Securing Declaration',
}

export const PROTEST_STATUS: Record<string, [string, string]> = {
  filed: ['Filed', 'st-pending'],
  under_review: ['Under review', 'st-pending'],
  upheld: ['Upheld', 'st-ok'],
  denied: ['Denied', 'st-bad'],
  withdrawn: ['Withdrawn', 'badge-muted'],
}

/** The milestones in order, for the progress strip. */
export const FLOW = ['draft', 'pre_procurement', 'invitation', 'bid_opening', 'evaluation', 'awarded'] as const

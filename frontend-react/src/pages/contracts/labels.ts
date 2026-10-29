export const CONTRACT_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  active: ['In force', 'st-ok'],
  completed: ['Completed', 'st-partial'],
  terminated: ['Terminated', 'st-bad'],
}

export const CONTRACT_TYPES = ['Goods', 'Services', 'Infrastructure', 'Consulting', 'Framework Agreement']

export const SECURITY_TYPES = ['Performance Security', 'Warranty Security', 'Retention Money', 'Bid Security']

export const PERFORMANCE_FORMS: [string, number, number][] = [
  ["Cash / Cashier's or Manager's Check", 0.05, 0.1],
  ['Bank Draft / Guarantee', 0.05, 0.1],
  ['Irrevocable Letter of Credit', 0.05, 0.1],
  ['Surety Bond', 0.3, 0.3],
]

export const ITEM_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  pending: ['Pending', 'st-pending'],
  approved: ['Approved', 'st-ok'],
  denied: ['Denied', 'st-bad'],
  posted: ['Posted', 'st-ok'],
  returned: ['Returned', 'badge-muted'],
  forfeited: ['Forfeited', 'st-bad'],
  issued: ['Issued', 'st-partial'],
  completed: ['Completed', 'st-ok'],
  cancelled: ['Cancelled', 'badge-muted'],
}

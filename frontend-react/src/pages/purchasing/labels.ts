export const PO_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  sent: ['Sent to supplier', 'st-pending'],
  confirmed: ['Confirmed', 'st-partial'],
  partially_received: ['Partly delivered', 'st-partial'],
  received: ['Delivered', 'st-ok'],
  cancelled: ['Cancelled', 'badge-muted'],
}

export const IAR_STATUS: Record<string, [string, string]> = {
  passed: ['Complete', 'st-ok'],
  partial: ['Partial', 'st-partial'],
  failed: ['Failed', 'st-bad'],
}

export const ACCEPTANCE: Record<string, [string, string]> = {
  pending: ['For acceptance', 'st-pending'],
  accepted: ['In stock', 'st-ok'],
  partially_accepted: ['In stock (partial)', 'st-ok'],
  rejected: ['Rejected', 'st-bad'],
}

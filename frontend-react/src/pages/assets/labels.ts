export const STATUS_LABEL: Record<string, [string, string]> = {
  assigned: ['In use', 'st-ok'],
  available: ['Available', 'st-partial'],
  under_repair: ['Under repair', 'st-pending'],
  retired: ['Retired', 'badge-muted'],
  disposed: ['Disposed', 'st-bad'],
  lost: ['Lost', 'st-bad'],
}

export const CONDITIONS = ['excellent', 'good', 'fair', 'poor'] as const

export const MOVE_LABEL: Record<string, string> = { transfer: 'Transfer (PTR)', return: 'Return', reissue: 'Issue (PAR / ICS)' }

export const DISPOSAL_MODES: [string, string][] = [
  ['condemnation', 'Condemnation'],
  ['destruction', 'Destruction'],
  ['public_auction', 'Public auction'],
  ['negotiated_sale', 'Sale through negotiation'],
  ['sale', 'Sale'],
  ['barter', 'Barter'],
  ['donation', 'Donation'],
  ['transfer', 'Transfer to another agency'],
]
export const SALE_MODES = ['sale', 'public_auction', 'negotiated_sale']

export const DISPOSAL_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  submitted: ['Awaiting approval', 'st-pending'],
  approved: ['Approved', 'st-partial'],
  rejected: ['Rejected', 'st-bad'],
  completed: ['Completed', 'st-ok'],
}

export const GATE_STATUS: Record<string, [string, string]> = {
  released: ['Out', 'st-partial'],
  overdue: ['Overdue', 'st-bad'],
  returned: ['Returned', 'st-ok'],
}

export function cap(s: string | null | undefined) {
  return s ? s.charAt(0).toUpperCase() + s.slice(1).replace(/_/g, ' ') : ''
}

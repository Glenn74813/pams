const REQUEST: Record<string, [string, string]> = {
  pending: ['Pending', 'st-pending'],
  approved: ['Approved', 'st-ok'],
  partially_fulfilled: ['Partly issued', 'st-partial'],
  fulfilled: ['Issued', 'st-ok'],
  rejected: ['Rejected', 'st-bad'],
}

export function RequestStatusBadge({ status }: { status: string }) {
  const [label, cls] = REQUEST[status] ?? [status, '']
  return <span className={`badge ${cls}`}>{label}</span>
}

const ADJ: Record<string, [string, string]> = {
  pending: ['Pending approval', 'st-pending'],
  approved: ['Approved', 'st-ok'],
  rejected: ['Rejected', 'st-bad'],
}

export function AdjustmentStatusBadge({ status }: { status: string }) {
  const [label, cls] = ADJ[status] ?? [status, '']
  return <span className={`badge ${cls}`}>{label}</span>
}

export const COUNT_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  in_progress: ['Counting', 'st-pending'],
  completed: ['For verification', 'st-partial'],
  verified: ['Verified', 'st-ok'],
}

export const COUNT_TYPE: Record<string, string> = {
  supplies: 'Supplies (RPCI)',
  ppe: 'PPE (RPCPPE)',
  semi_expendable: 'Semi-expendable (RPCSP)',
}

export const FINDING: Record<string, [string, string]> = {
  pending: ['Not yet checked', 'badge-muted'],
  found: ['Found', 'st-ok'],
  condition_changed: ['Found, condition changed', 'st-partial'],
  missing: ['Missing', 'st-bad'],
  not_in_masterlist: ['Not in masterlist', 'st-pending'],
  out_of_scope: ['On record elsewhere', 'st-pending'],
}

export const CONDITIONS = ['excellent', 'good', 'fair', 'poor', 'unserviceable']

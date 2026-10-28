export const PPMP_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  submitted: ['Submitted', 'st-pending'],
  under_review: ['Under review', 'st-pending'],
  approved: ['Approved', 'st-ok'],
  returned: ['Returned', 'st-bad'],
}

export const APP_STATUS: Record<string, [string, string]> = {
  consolidated: ['Consolidated', 'st-pending'],
  approved: ['Approved', 'st-ok'],
}

export const LINE_STATUS: Record<string, [string, string]> = {
  planned: ['Planned', 'badge-muted'],
  ongoing: ['In progress', 'st-partial'],
  completed: ['Completed', 'st-ok'],
}

export const PR_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  submitted: ['In routing', 'st-pending'],
  under_review: ['In routing', 'st-pending'],
  approved: ['Approved', 'st-ok'],
  returned: ['Returned', 'st-bad'],
  cancelled: ['Cancelled', 'badge-muted'],
}

export const PRIORITY: Record<string, [string, string]> = {
  low: ['Low', 'badge-muted'],
  medium: ['Medium', 'st-partial'],
  high: ['High', 'st-bad'],
}

export const QUARTER_LABEL: Record<string, string> = {
  Q1: 'Q1 (Jan–Mar)',
  Q2: 'Q2 (Apr–Jun)',
  Q3: 'Q3 (Jul–Sep)',
  Q4: 'Q4 (Oct–Dec)',
}

export const thisYear = () => new Date().getFullYear()

/** Fiscal years to offer: last year through two years ahead. */
export function fiscalYears() {
  const y = thisYear()
  return [y + 2, y + 1, y, y - 1]
}

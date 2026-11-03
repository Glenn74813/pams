export const RFQ_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  issued: ['Open for quotations', 'st-pending'],
  closed: ['Closed — for award', 'st-partial'],
  awarded: ['Awarded', 'st-ok'],
  cancelled: ['Cancelled', 'badge-muted'],
  failed: ['Failed', 'st-bad'],
}

export const RESOLUTION_STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  for_approval: ['For approval', 'st-pending'],
  approved: ['Approved', 'st-ok'],
  archived: ['Archived', 'badge-muted'],
}

export const RESOLUTION_TYPES: [string, string][] = [
  ['resolution_to_award', 'Resolution to award'],
  ['recommendation', 'Recommendation'],
  ['award', 'Award'],
  ['mode_of_procurement', 'Mode of procurement'],
  ['declaration_of_failure', 'Declaration of failure'],
  ['other', 'Other'],
]

export const MEETING_STATUS: Record<string, [string, string]> = {
  scheduled: ['Scheduled', 'st-pending'],
  ongoing: ['In session', 'st-partial'],
  completed: ['Held', 'st-ok'],
  cancelled: ['Cancelled', 'badge-muted'],
}

export const DESIGNATIONS: [string, string][] = [
  ['chairperson', 'Chairperson'],
  ['vice_chairperson', 'Vice-Chairperson'],
  ['member', 'Member'],
  ['provisional_member', 'Provisional member'],
  ['secretariat', 'Secretariat'],
  ['twg', 'Technical Working Group'],
]

export const VOTING = ['chairperson', 'vice_chairperson', 'member', 'provisional_member']

export const designationLabel = (d: string | null) => DESIGNATIONS.find(([k]) => k === d)?.[1] ?? d ?? '—'

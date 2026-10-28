/** A line being edited. Numbers are kept as typed; the form converts them on save. */
export interface EditLine {
  key: string
  app_item_id?: number | null
  /** Shown for a line drawn on an APP line: how much is left there. */
  app_hint?: string
  general_description: string
  specifications: string
  code: string
  unit: string
  quantity: string
  estimated_unit_cost: string
  procurement_mode: string
  procurement_category: string
  schedule: string
  fund_source: string
  is_sustainable: boolean
}

let seq = 0
export const newKey = () => `l${++seq}`

export function blankLine(over: Partial<EditLine> = {}): EditLine {
  return {
    key: newKey(),
    general_description: '',
    specifications: '',
    code: '',
    unit: '',
    quantity: '1',
    estimated_unit_cost: '',
    procurement_mode: '',
    procurement_category: 'Goods',
    schedule: '',
    fund_source: '',
    is_sustainable: false,
    ...over,
  }
}

export const lineTotal = (l: EditLine) => (Number(l.quantity) || 0) * (Number(l.estimated_unit_cost) || 0)

export function toInput(l: EditLine) {
  const t = (s: string) => (s.trim() === '' ? null : s.trim())
  return {
    general_description: l.general_description.trim(),
    specifications: t(l.specifications),
    code: t(l.code),
    unit: t(l.unit),
    quantity: Number(l.quantity) || 0,
    estimated_unit_cost: Number(l.estimated_unit_cost) || 0,
    procurement_mode: t(l.procurement_mode),
    procurement_category: t(l.procurement_category),
    schedule: t(l.schedule),
    fund_source: t(l.fund_source),
    is_sustainable: l.is_sustainable,
    app_item_id: l.app_item_id ?? null,
  }
}

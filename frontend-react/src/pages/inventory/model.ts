import type { Classification, StockLot } from '../../api/types'
import type { LotInput } from '../../api/endpoints'

export const CLASSIFICATIONS: { value: Classification; label: string; hint: string }[] = [
  { value: 'consumable', label: 'Consumable', hint: 'Supplies used up in operations (no property record).' },
  { value: 'semi_expendable', label: 'Semi-Expendable', hint: 'Lasts over a year, unit cost below the capitalization threshold (ICS).' },
  { value: 'ppe', label: 'PPE', hint: 'Lasts over a year, unit cost at or above the threshold (PAR).' },
]

/** A stock lot as edited in a form (numbers kept as strings until submit). */
export interface LotDraft {
  key: string
  id?: number
  brand_name: string
  quantity: string
  unit_cost: string
  supplier_id: string
  location: string
  lot_number: string
  expiration_date: string
  received_date: string
  warranty: string
  warranty_months: string
  specifications: string
}

let seq = 0
export function emptyLot(receivedDate = ''): LotDraft {
  return {
    key: `new-${++seq}`,
    brand_name: '',
    quantity: '',
    unit_cost: '',
    supplier_id: '',
    location: '',
    lot_number: '',
    expiration_date: '',
    received_date: receivedDate,
    warranty: '',
    warranty_months: '',
    specifications: '',
  }
}

export function lotFromStock(s: StockLot): LotDraft {
  return {
    key: `lot-${s.id}`,
    id: s.id,
    brand_name: s.brand_name ?? '',
    quantity: String(s.quantity),
    unit_cost: String(s.unit_cost),
    supplier_id: s.supplier_id ? String(s.supplier_id) : '',
    location: s.location ?? '',
    lot_number: s.lot_number ?? '',
    expiration_date: s.expiration_date ?? '',
    received_date: s.received_date ?? '',
    warranty: s.warranty ?? '',
    warranty_months: s.warranty_months != null ? String(s.warranty_months) : '',
    specifications: s.specifications ?? '',
  }
}

export function lotToInput(l: LotDraft): LotInput {
  return {
    id: l.id,
    brand_name: l.brand_name,
    quantity: Number(l.quantity) || 0,
    unit_cost: Number(l.unit_cost) || 0,
    supplier_id: l.supplier_id ? Number(l.supplier_id) : null,
    location: l.location,
    lot_number: l.lot_number,
    expiration_date: l.expiration_date || null,
    received_date: l.received_date || null,
    warranty: l.warranty,
    warranty_months: l.warranty_months ? Number(l.warranty_months) : null,
    specifications: l.specifications,
  }
}

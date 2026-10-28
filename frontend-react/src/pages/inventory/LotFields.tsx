import { formatPeso } from '../../lib/format'
import type { LotDraft } from './model'

interface Props {
  lot: LotDraft
  onChange: (lot: LotDraft) => void
  suppliers: { id: number; name: string }[]
  locations: string[]
  showReceivedDate?: boolean
  idPrefix: string
}

export function LotFields({ lot, onChange, suppliers, locations, showReceivedDate, idPrefix }: Props) {
  const set = (key: keyof LotDraft) => (e: { target: { value: string } }) => onChange({ ...lot, [key]: e.target.value })
  const total = (Number(lot.quantity) || 0) * (Number(lot.unit_cost) || 0)
  const id = (f: string) => `${idPrefix}-${f}`
  return (
    <div className="lot-grid">
      <div className="field">
        <label htmlFor={id('qty')}>Quantity *</label>
        <input id={id('qty')} type="number" min={0} step={1} value={lot.quantity} onChange={set('quantity')} required />
      </div>
      <div className="field">
        <label htmlFor={id('cost')}>Unit cost (₱) *</label>
        <input id={id('cost')} type="number" min={0} step="0.01" value={lot.unit_cost} onChange={set('unit_cost')} required />
      </div>
      <div className="field">
        <label>Total</label>
        <div className="readonly">{formatPeso(total)}</div>
      </div>
      <div className="field">
        <label htmlFor={id('brand')}>Brand</label>
        <input id={id('brand')} value={lot.brand_name} onChange={set('brand_name')} maxLength={255} />
      </div>
      <div className="field">
        <label htmlFor={id('supplier')}>Supplier</label>
        <select id={id('supplier')} value={lot.supplier_id} onChange={set('supplier_id')}>
          <option value="">—</option>
          {suppliers.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label htmlFor={id('loc')}>Location</label>
        <input id={id('loc')} value={lot.location} onChange={set('location')} list={id('locs')} maxLength={255} placeholder="e.g. Main Warehouse" />
        <datalist id={id('locs')}>
          {locations.map((l) => (
            <option key={l} value={l} />
          ))}
        </datalist>
      </div>
      <div className="field">
        <label htmlFor={id('lotno')}>Lot / batch no.</label>
        <input id={id('lotno')} value={lot.lot_number} onChange={set('lot_number')} />
      </div>
      <div className="field">
        <label htmlFor={id('exp')}>Expiration date</label>
        <input id={id('exp')} type="date" value={lot.expiration_date} onChange={set('expiration_date')} />
      </div>
      {showReceivedDate && (
        <div className="field">
          <label htmlFor={id('recv')}>Received date</label>
          <input id={id('recv')} type="date" value={lot.received_date} onChange={set('received_date')} />
        </div>
      )}
      <div className="field">
        <label htmlFor={id('war')}>Warranty</label>
        <input id={id('war')} value={lot.warranty} onChange={set('warranty')} placeholder="e.g. 1 year parts" />
      </div>
      <div className="field">
        <label htmlFor={id('warm')}>Warranty (months)</label>
        <input id={id('warm')} type="number" min={0} value={lot.warranty_months} onChange={set('warranty_months')} />
      </div>
      <div className="field lot-wide">
        <label htmlFor={id('spec')}>Specifications</label>
        <input id={id('spec')} value={lot.specifications} onChange={set('specifications')} />
      </div>
    </div>
  )
}

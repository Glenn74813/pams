import type { ProcurementOptions } from '../../api/types'
import { formatPeso } from '../../lib/format'
import { QUARTER_LABEL } from './labels'
import { lineTotal, type EditLine } from './lines'

interface Props {
  lines: EditLine[]
  onChange: (lines: EditLine[]) => void
  options?: ProcurementOptions
  /** PPMP lines carry an accounting code and a schedule, and must name their mode. */
  planning?: boolean
}

/** One card per line, so the form stays usable on a phone. */
export function LineEditor({ lines, onChange, options, planning }: Props) {
  const set = (key: string, patch: Partial<EditLine>) => onChange(lines.map((l) => (l.key === key ? { ...l, ...patch } : l)))
  return (
    <div className="line-cards">
      {lines.map((l, i) => {
        const id = (f: string) => `ln-${l.key}-${f}`
        const ceiling = l.procurement_mode ? options?.ceilings[l.procurement_mode] : undefined
        const over = ceiling !== undefined && lineTotal(l) > ceiling
        return (
          <div key={l.key} className="line-card">
            <div className="line-card-head">
              <strong>Line {i + 1}</strong>
              {l.app_hint && <span className="muted small">{l.app_hint}</span>}
              <span className="line-total">{formatPeso(lineTotal(l))}</span>
              <button type="button" className="icon-btn delete" title={`Remove line ${i + 1}`} onClick={() => onChange(lines.filter((x) => x.key !== l.key))}>
                ×
              </button>
            </div>
            <div className="form-grid four">
              <div className="field span-4">
                <label htmlFor={id('desc')}>Description *</label>
                <input id={id('desc')} value={l.general_description} maxLength={255} required onChange={(e) => set(l.key, { general_description: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor={id('qty')}>Quantity *</label>
                <input id={id('qty')} type="number" min={1} value={l.quantity} required onChange={(e) => set(l.key, { quantity: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor={id('unit')}>Unit</label>
                <input id={id('unit')} value={l.unit} maxLength={50} placeholder="pc, box, lot" onChange={(e) => set(l.key, { unit: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor={id('cost')}>Est. unit cost (₱) *</label>
                <input id={id('cost')} type="number" min={0} step="0.01" value={l.estimated_unit_cost} required onChange={(e) => set(l.key, { estimated_unit_cost: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor={id('mode')}>Mode of procurement{planning ? ' *' : ''}</label>
                <select id={id('mode')} value={l.procurement_mode} required={planning} onChange={(e) => set(l.key, { procurement_mode: e.target.value })}>
                  <option value="">{planning ? 'Choose' : l.app_item_id ? 'As planned in the APP' : 'Not set'}</option>
                  {options?.modes.map((m) => (
                    <option key={m}>{m}</option>
                  ))}
                </select>
                {over && <span className="hint warn">Over the ₱{ceiling!.toLocaleString()} ceiling for this mode.</span>}
              </div>
              <div className="field">
                <label htmlFor={id('cat')}>Category</label>
                <select id={id('cat')} value={l.procurement_category} onChange={(e) => set(l.key, { procurement_category: e.target.value })}>
                  {options?.categories.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </div>
              <div className="field span-2">
                <label htmlFor={id('fund')}>Source of funds</label>
                <select id={id('fund')} value={l.fund_source} onChange={(e) => set(l.key, { fund_source: e.target.value })}>
                  <option value="">{l.app_item_id ? 'As planned in the APP' : 'Not set'}</option>
                  {options?.fund_sources.map((f) => (
                    <option key={f}>{f}</option>
                  ))}
                </select>
              </div>
              {planning && (
                <>
                  <div className="field">
                    <label htmlFor={id('sched')}>Schedule</label>
                    <select id={id('sched')} value={l.schedule} onChange={(e) => set(l.key, { schedule: e.target.value })}>
                      <option value="">Not scheduled</option>
                      {options?.quarters.map((q) => (
                        <option key={q} value={q}>
                          {QUARTER_LABEL[q]}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="field">
                    <label htmlFor={id('code')}>Accounting code</label>
                    <input id={id('code')} value={l.code} maxLength={100} placeholder="UACS object code" onChange={(e) => set(l.key, { code: e.target.value })} />
                  </div>
                </>
              )}
              <div className={'field ' + (planning ? 'span-2' : 'span-4')}>
                <label htmlFor={id('spec')}>Technical specifications</label>
                <input id={id('spec')} value={l.specifications} onChange={(e) => set(l.key, { specifications: e.target.value })} />
              </div>
              <label className="checkbox-line span-4">
                <input type="checkbox" checked={l.is_sustainable} onChange={(e) => set(l.key, { is_sustainable: e.target.checked })} /> Green / sustainable procurement
              </label>
            </div>
          </div>
        )
      })}
    </div>
  )
}

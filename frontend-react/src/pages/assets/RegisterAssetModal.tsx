import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { assetsApi, inventoryApi } from '../../api/endpoints'
import type { Item } from '../../api/types'
import { ItemPicker } from '../../components/ItemPicker'
import { Modal } from '../../components/Modal'
import { formatDate, formatPeso, today } from '../../lib/format'
import { CONDITIONS, cap } from './labels'
import { PersonSelect } from './pickers'

interface Props {
  onClose: () => void
  onSaved: (message: string) => void
}

/** Takes serialised units out of a stock lot and puts them under someone's PAR / ICS. */
export function RegisterAssetModal({ onClose, onSaved }: Props) {
  const [item, setItem] = useState<Item | null>(null)
  const [lotId, setLotId] = useState('')
  const [holder, setHolder] = useState('')
  const [date, setDate] = useState(today())
  const [condition, setCondition] = useState('good')
  const [life, setLife] = useState('5')
  const [salvage, setSalvage] = useState('')
  const [units, setUnits] = useState([{ key: 1, serial: '', tag: '' }])
  const [error, setError] = useState<string | null>(null)
  const lots = useQuery({ queryKey: ['lots', item?.id], queryFn: () => inventoryApi.lots(item!.id), enabled: !!item })
  const lot = lots.data?.find((l) => String(l.id) === lotId)

  const save = useMutation({
    mutationFn: () =>
      assetsApi.register({
        item_id: item!.id,
        stock_id: Number(lotId),
        assigned_to: Number(holder),
        assigned_date: date,
        condition,
        useful_life_years: Number(life) || 5,
        salvage_value: salvage ? Number(salvage) : null,
        units: units.map((u) => ({ serial_number: u.serial || undefined, tag_number: u.tag || undefined })),
      }),
    onSuccess: (r) => onSaved(r.message),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not register the assets.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!item) return setError('Choose the item.')
    save.mutate()
  }

  const kind = item?.classification === 'ppe' ? 'PAR' : 'ICS'

  return (
    <Modal title="Register Assets" onClose={onClose} width={820}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field" style={{ marginBottom: '1rem' }}>
          <label htmlFor="ra-item">Item (PPE or semi-expendable) *</label>
          {item ? (
            <div className="chosen">
              <span>
                <span className="mono">{item.code}</span> {item.name} · {item.classification_label}
              </span>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => { setItem(null); setLotId('') }}>
                Change
              </button>
            </div>
          ) : (
            <ItemPicker id="ra-item" requireStock onPick={(i) => (i.classification === 'consumable' ? setError('Consumables are issued through requests, not registered as property.') : (setError(null), setItem(i)))} />
          )}
        </div>
        <div className="form-grid three">
          <div className="field span-2">
            <label htmlFor="ra-lot">From stock lot *</label>
            <select id="ra-lot" value={lotId} onChange={(e) => setLotId(e.target.value)} required disabled={!item}>
              <option value="">{item ? (lots.data?.length ? 'Choose a lot' : 'No stock on hand') : 'Choose an item first'}</option>
              {lots.data?.map((l) => (
                <option key={l.id} value={l.id}>
                  {(l.brand_name ?? 'No brand') + ` — ${l.quantity} left @ ${formatPeso(l.unit_cost)} — received ${formatDate(l.received_date)}`}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="ra-date">Date issued</label>
            <input id="ra-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="field span-2">
            <label htmlFor="ra-holder">Accountable person *</label>
            <PersonSelect id="ra-holder" value={holder} onChange={setHolder} required />
          </div>
          <div className="field">
            <label htmlFor="ra-cond">Condition</label>
            <select id="ra-cond" value={condition} onChange={(e) => setCondition(e.target.value)}>
              {CONDITIONS.map((c) => (
                <option key={c} value={c}>
                  {cap(c)}
                </option>
              ))}
            </select>
          </div>
          {item?.classification === 'ppe' && (
            <>
              <div className="field">
                <label htmlFor="ra-life">Useful life (years)</label>
                <input id="ra-life" type="number" min={1} max={50} value={life} onChange={(e) => setLife(e.target.value)} />
              </div>
              <div className="field">
                <label htmlFor="ra-salv">Residual value (₱)</label>
                <input id="ra-salv" type="number" min={0} step="0.01" value={salvage} onChange={(e) => setSalvage(e.target.value)} placeholder="5% of cost" />
              </div>
            </>
          )}
        </div>

        <div className="lots-head" style={{ margin: '1.25rem 0 0.5rem' }}>
          <h4>Units ({units.length})</h4>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            disabled={!!lot && units.length >= lot.quantity}
            onClick={() => setUnits((u) => [...u, { key: Date.now(), serial: '', tag: '' }])}
          >
            + Add unit
          </button>
        </div>
        {units.map((u, i) => (
          <div key={u.key} className="unit-row">
            <span className="muted">#{i + 1}</span>
            <input aria-label={`Serial number of unit ${i + 1}`} placeholder="Serial no." value={u.serial} onChange={(e) => setUnits((us) => us.map((x) => (x.key === u.key ? { ...x, serial: e.target.value } : x)))} />
            <input aria-label={`Tag number of unit ${i + 1}`} placeholder="Tag no. (blank = automatic)" value={u.tag} onChange={(e) => setUnits((us) => us.map((x) => (x.key === u.key ? { ...x, tag: e.target.value } : x)))} />
            {units.length > 1 && (
              <button type="button" className="icon-btn delete" title="Remove unit" onClick={() => setUnits((us) => us.filter((x) => x.key !== u.key))}>
                ×
              </button>
            )}
          </div>
        ))}
        {item && <p className="hint" style={{ marginTop: '0.5rem' }}>One {kind} number will cover all units in this registration.</p>}
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Registering…' : `Register ${units.length} unit${units.length === 1 ? '' : 's'}`}
          </button>
        </div>
      </form>
    </Modal>
  )
}

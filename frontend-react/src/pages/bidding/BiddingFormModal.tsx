import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { biddingApi, type BiddingInput } from '../../api/endpoints'
import type { Bidding } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatPeso, formatQty } from '../../lib/format'

interface Props {
  bidding: Bidding | null
  onClose: () => void
  onSaved: (b: Bidding, message: string) => void
}

interface Pick {
  on: boolean
  quantity: string
  abc: string
}

export function BiddingFormModal({ bidding, onClose, onSaved }: Props) {
  const editing = bidding !== null
  const opts = useQuery({ queryKey: ['bidding-pr-options'], queryFn: biddingApi.prOptions })
  const [prId, setPrId] = useState(bidding?.purchase_request ? String(bidding.purchase_request.id) : '')
  const [title, setTitle] = useState(bidding?.title ?? '')
  const [method, setMethod] = useState<'price' | 'mearb'>(bidding?.evaluation_method ?? 'price')
  const [priceWeight, setPriceWeight] = useState(String(bidding?.evaluation_method === 'mearb' ? bidding.price_weight : 70))
  const [term, setTerm] = useState(bidding?.delivery_term ?? '')
  const [days, setDays] = useState(bidding?.delivery_lead_days != null ? String(bidding.delivery_lead_days) : '')
  const [picks, setPicks] = useState<Record<number, Pick>>(() =>
    Object.fromEntries((bidding?.items ?? []).map((l) => [l.purchase_request_item_id, { on: true, quantity: String(l.quantity), abc: String(l.abc_unit_cost) }])),
  )
  const [error, setError] = useState<string | null>(null)
  const pr = opts.data?.data.find((p) => String(p.id) === prId)
  const lines = [
    ...(bidding?.items ?? []).filter((l) => !pr?.lines.some((x) => x.id === l.purchase_request_item_id)).map((l) => ({
      id: l.purchase_request_item_id!,
      general_description: l.general_description,
      unit: l.unit,
      remaining_quantity: l.quantity,
      estimated_unit_cost: l.abc_unit_cost,
    })),
    ...(pr?.lines ?? []).map((l) => ({ ...l, remaining_quantity: l.remaining_quantity + (bidding?.items?.find((x) => x.purchase_request_item_id === l.id)?.quantity ?? 0) })),
  ]
  const choosePr = (v: string) => {
    setPrId(v)
    const p = opts.data?.data.find((x) => String(x.id) === v)
    setPicks(Object.fromEntries((p?.lines ?? []).map((l) => [l.id, { on: true, quantity: String(l.remaining_quantity), abc: String(l.estimated_unit_cost) }])))
    if (!title && p) setTitle(p.purpose ?? '')
  }
  const set = (id: number, patch: Partial<Pick>) => setPicks((ps) => ({ ...ps, [id]: { ...ps[id], ...patch } }))
  const chosen = lines.filter((l) => picks[l.id]?.on)
  const abc = chosen.reduce((s, l) => s + (Number(picks[l.id].quantity) || 0) * (Number(picks[l.id].abc) || 0), 0)
  const save = useMutation({
    mutationFn: () => {
      const input: BiddingInput = {
        title,
        purchase_request_id: Number(prId),
        evaluation_method: method,
        price_weight: method === 'mearb' ? Number(priceWeight) : undefined,
        technical_weight: method === 'mearb' ? 100 - Number(priceWeight) : undefined,
        delivery_term: term || null,
        delivery_lead_days: days === '' ? null : Number(days),
        items: chosen.map((l) => ({ purchase_request_item_id: l.id, quantity: Number(picks[l.id].quantity) || 0, abc_unit_cost: Number(picks[l.id].abc) || 0 })),
      }
      return editing ? biddingApi.update(bidding.id, input) : biddingApi.create(input)
    },
    onSuccess: (b) => onSaved(b, editing ? `${b.bidding_number} saved.` : `${b.bidding_number} drafted.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the bidding.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (chosen.length === 0) return setError('Pick at least one purchase request line.')
    save.mutate()
  }
  return (
    <Modal title={editing ? `Edit ${bidding.bidding_number}` : 'New public bidding'} onClose={onClose} width={900}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        {pr && pr.failed_biddings > 0 && <div className="alert alert-warning">This purchase request has {pr.failed_biddings} failed bidding(s) already.</div>}
        <div className="form-grid three">
          <div className="field span-2">
            <label htmlFor="bd-pr">Approved purchase request *</label>
            {editing ? (
              <div className="readonly">{bidding.purchase_request?.name}</div>
            ) : (
              <select id="bd-pr" value={prId} onChange={(e) => choosePr(e.target.value)} required>
                <option value="">{opts.isPending ? 'Loading…' : opts.data?.data.length ? 'Choose' : 'No approved PRs with bidding lines left'}</option>
                {opts.data?.data.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.pr_number} — {p.department} — {p.purpose}
                  </option>
                ))}
              </select>
            )}
          </div>
          <div className="field">
            <label htmlFor="bd-method">Evaluation *</label>
            <select id="bd-method" value={method} onChange={(e) => setMethod(e.target.value as 'price' | 'mearb')}>
              <option value="price">Lowest calculated responsive bid</option>
              <option value="mearb">MEARB (price + rated criteria)</option>
            </select>
          </div>
          <div className="field span-2">
            <label htmlFor="bd-title">Title *</label>
            <input id="bd-title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} />
          </div>
          {method === 'mearb' ? (
            <div className="field">
              <label htmlFor="bd-pw">Price weight (%)</label>
              <input id="bd-pw" type="number" min={0} max={100} value={priceWeight} onChange={(e) => setPriceWeight(e.target.value)} />
              <span className="hint">Rated criteria: {100 - (Number(priceWeight) || 0)}%</span>
            </div>
          ) : (
            <div />
          )}
          <div className="field span-2">
            <label htmlFor="bd-term">Delivery term</label>
            <input id="bd-term" value={term} onChange={(e) => setTerm(e.target.value)} maxLength={255} />
          </div>
          <div className="field">
            <label htmlFor="bd-days">Delivery within (days)</label>
            <input id="bd-days" type="number" min={0} value={days} onChange={(e) => setDays(e.target.value)} />
          </div>
        </div>
        {lines.length > 0 && (
          <div className="table-wrap" style={{ marginTop: '1rem' }}>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th></th>
                  <th>PR line</th>
                  <th className="num">Available</th>
                  <th className="num">Quantity</th>
                  <th className="num">ABC / unit</th>
                </tr>
              </thead>
              <tbody>
                {lines.map((l) => {
                  const p = picks[l.id] ?? { on: false, quantity: String(l.remaining_quantity), abc: String(l.estimated_unit_cost) }
                  return (
                    <tr key={l.id} className={p.on ? '' : 'row-muted'}>
                      <td>
                        <input type="checkbox" aria-label={`Include ${l.general_description}`} checked={p.on} onChange={(e) => set(l.id, { ...p, on: e.target.checked })} />
                      </td>
                      <td>{l.general_description}</td>
                      <td className="num">
                        {formatQty(l.remaining_quantity)} <span className="muted">{l.unit}</span>
                      </td>
                      <td className="num">
                        <input className="qty-input" type="number" min={1} max={l.remaining_quantity} disabled={!p.on} aria-label={`Quantity of ${l.general_description}`} value={p.quantity} onChange={(e) => set(l.id, { ...p, quantity: e.target.value })} />
                      </td>
                      <td className="num">
                        <input className="qty-input" style={{ width: '7.5rem' }} type="number" min={0} max={l.estimated_unit_cost} step="0.01" disabled={!p.on} aria-label={`ABC per unit of ${l.general_description}`} value={p.abc} onChange={(e) => set(l.id, { ...p, abc: e.target.value })} />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="form-total">
          ABC <strong>{formatPeso(abc)}</strong> {abc > 2_000_000 && <span className="muted small">— needs a pre-procurement conference</span>}
        </p>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Save draft'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

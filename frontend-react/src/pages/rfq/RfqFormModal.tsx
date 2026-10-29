import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { rfqApi, type RfqInput } from '../../api/endpoints'
import type { Rfq } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatPeso, formatQty, toLocalInput } from '../../lib/format'

interface Props {
  rfq: Rfq | null
  onClose: () => void
  onSaved: (r: Rfq, message: string) => void
}

interface Pick {
  on: boolean
  quantity: string
  abc: string
}

export function RfqFormModal({ rfq, onClose, onSaved }: Props) {
  const editing = rfq !== null
  const opts = useQuery({ queryKey: ['rfq-pr-options'], queryFn: rfqApi.prOptions })
  const [prId, setPrId] = useState(rfq?.purchase_request ? String(rfq.purchase_request.id) : '')
  const [title, setTitle] = useState(rfq?.title ?? '')
  const [method, setMethod] = useState(rfq?.procurement_method ?? '')
  const [deadline, setDeadline] = useState(toLocalInput(rfq?.deadline))
  const [term, setTerm] = useState(rfq?.delivery_term ?? '')
  const [days, setDays] = useState(rfq?.delivery_lead_days != null ? String(rfq.delivery_lead_days) : '')
  const [description, setDescription] = useState(rfq?.description ?? '')
  const [justification, setJustification] = useState(rfq?.justification ?? '')
  const [picks, setPicks] = useState<Record<number, Pick>>(() =>
    Object.fromEntries((rfq?.items ?? []).map((l) => [l.purchase_request_item_id, { on: true, quantity: String(l.quantity), abc: String(l.abc_unit_cost) }])),
  )
  const [error, setError] = useState<string | null>(null)

  // An RFQ being edited has its own lines; offer them back alongside what is still free on the PR.
  const pr = opts.data?.data.find((p) => String(p.id) === prId)
  const lines = [
    ...(rfq?.items ?? []).filter((l) => !pr?.lines.some((x) => x.id === l.purchase_request_item_id)).map((l) => ({
      id: l.purchase_request_item_id!,
      general_description: l.general_description,
      unit: l.unit,
      remaining_quantity: l.quantity,
      estimated_unit_cost: l.abc_unit_cost,
      procurement_mode: rfq!.procurement_method,
    })),
    ...(pr?.lines ?? []).map((l) => ({ ...l, remaining_quantity: l.remaining_quantity + (rfq?.items?.find((x) => x.purchase_request_item_id === l.id)?.quantity ?? 0) })),
  ]
  const single = method !== '' && (opts.data?.single_source ?? []).includes(method)
  const choosePr = (v: string) => {
    setPrId(v)
    const p = opts.data?.data.find((x) => String(x.id) === v)
    setPicks(Object.fromEntries((p?.lines ?? []).map((l) => [l.id, { on: true, quantity: String(l.remaining_quantity), abc: String(l.estimated_unit_cost) }])))
    if (p?.modes.length === 1) setMethod(p.modes[0])
    if (!title && p) setTitle(p.purpose ?? '')
  }
  const set = (id: number, patch: Partial<Pick>) => setPicks((ps) => ({ ...ps, [id]: { ...ps[id], ...patch } }))
  const chosen = lines.filter((l) => picks[l.id]?.on)
  const abc = chosen.reduce((s, l) => s + (Number(picks[l.id].quantity) || 0) * (Number(picks[l.id].abc) || 0), 0)

  const save = useMutation({
    mutationFn: () => {
      const input: RfqInput = {
        title,
        procurement_method: method,
        purchase_request_id: Number(prId),
        deadline: deadline || null,
        delivery_term: term || null,
        delivery_lead_days: days === '' ? null : Number(days),
        description: description || null,
        justification: justification || null,
        items: chosen.map((l) => ({ purchase_request_item_id: l.id, quantity: Number(picks[l.id].quantity) || 0, abc_unit_cost: Number(picks[l.id].abc) || 0 })),
      }
      return editing ? rfqApi.update(rfq.id, input) : rfqApi.create(input)
    },
    onSuccess: (r) => onSaved(r, editing ? `${r.rfq_number} saved.` : `${r.rfq_number} drafted.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the RFQ.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (chosen.length === 0) return setError('Pick at least one purchase request line.')
    save.mutate()
  }

  return (
    <Modal title={editing ? `Edit ${rfq.rfq_number}` : 'New Request for Quotation'} onClose={onClose} width={900}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid three">
          <div className="field span-2">
            <label htmlFor="rq-pr">Approved purchase request *</label>
            {editing ? (
              <div className="readonly">{rfq.purchase_request?.name}</div>
            ) : (
              <select id="rq-pr" value={prId} onChange={(e) => choosePr(e.target.value)} required>
                <option value="">{opts.isPending ? 'Loading…' : opts.data?.data.length ? 'Choose' : 'No approved PRs with lines left'}</option>
                {opts.data?.data.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.pr_number} — {p.department} — {p.purpose}
                  </option>
                ))}
              </select>
            )}
          </div>
          <div className="field">
            <label htmlFor="rq-method">Mode *</label>
            <select id="rq-method" value={method} onChange={(e) => setMethod(e.target.value)} required>
              <option value="">Choose</option>
              {opts.data?.methods.map((m) => (
                <option key={m}>{m}</option>
              ))}
            </select>
            {pr && pr.modes.length > 0 && <span className="hint">Planned: {pr.modes.join(', ')}</span>}
          </div>
          <div className="field span-2">
            <label htmlFor="rq-title">Title *</label>
            <input id="rq-title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} />
          </div>
          <div className="field">
            <label htmlFor="rq-dl">Deadline for quotations</label>
            <input id="rq-dl" type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
            <span className="hint">Needed before issuing.</span>
          </div>
          <div className="field span-2">
            <label htmlFor="rq-term">Delivery term</label>
            <input id="rq-term" value={term} onChange={(e) => setTerm(e.target.value)} maxLength={255} placeholder="e.g. Delivered to the supply office" />
          </div>
          <div className="field">
            <label htmlFor="rq-days">Delivery within (days)</label>
            <input id="rq-days" type="number" min={0} value={days} onChange={(e) => setDays(e.target.value)} />
          </div>
          {single && (
            <div className="field span-3">
              <label htmlFor="rq-just">Justification for {method} *</label>
              <textarea id="rq-just" rows={2} value={justification} onChange={(e) => setJustification(e.target.value)} required placeholder="Exclusive distributorship, proprietary nature, legal ground…" />
            </div>
          )}
          <div className="field span-3">
            <label htmlFor="rq-desc">Instructions to suppliers</label>
            <textarea id="rq-desc" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
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
                      <td>
                        {l.general_description} <span className="muted small">{l.procurement_mode}</span>
                      </td>
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
          Approved budget for the contract (ABC) <strong>{formatPeso(abc)}</strong>
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

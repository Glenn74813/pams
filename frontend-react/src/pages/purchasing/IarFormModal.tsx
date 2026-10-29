import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { poApi, receivingApi } from '../../api/endpoints'
import type { InspectionReport } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatQty, today } from '../../lib/format'

interface Props {
  poId: number
  iar: InspectionReport | null
  onClose: () => void
  onSaved: (r: InspectionReport) => void
}

interface Row {
  received: string
  accepted: string
  rejected: string
  remarks: string
}

/** The inspection committee's record of one delivery against a PO. */
export function IarFormModal({ poId, iar, onClose, onSaved }: Props) {
  const po = useQuery({ queryKey: ['po', poId], queryFn: () => poApi.get(poId) })
  const [delivered, setDelivered] = useState(iar?.delivery_date ?? today())
  const [inspected, setInspected] = useState(iar?.inspection_date ?? today())
  const [observations, setObservations] = useState(iar?.observations ?? '')
  const [rows, setRows] = useState<Record<number, Row>>(() =>
    Object.fromEntries(
      (iar?.items ?? []).map((l) => [
        l.purchase_order_item_id,
        { received: String(l.quantity_received), accepted: String(l.quantity_accepted), rejected: String(l.quantity_rejected), remarks: l.remarks ?? '' },
      ]),
    ),
  )
  const [error, setError] = useState<string | null>(null)
  const set = (id: number, patch: Partial<Row>) =>
    setRows((rs) => {
      const cur = rs[id] ?? { received: '', accepted: '', rejected: '0', remarks: '' }
      const next = { ...cur, ...patch }
      // Received follows accepted + rejected unless typed directly.
      if (('accepted' in patch || 'rejected' in patch) && !('received' in patch)) next.received = String((Number(next.accepted) || 0) + (Number(next.rejected) || 0))
      return { ...rs, [id]: next }
    })
  const save = useMutation({
    mutationFn: () => {
      const items = Object.entries(rows)
        .filter(([, r]) => Number(r.received) > 0)
        .map(([id, r]) => ({
          purchase_order_item_id: Number(id),
          quantity_received: Number(r.received) || 0,
          quantity_accepted: Number(r.accepted) || 0,
          quantity_rejected: Number(r.rejected) || 0,
          remarks: r.remarks || null,
        }))
      const input = { purchase_order_id: poId, delivery_date: delivered, inspection_date: inspected, observations: observations || null, items }
      return iar ? receivingApi.update(iar.id, input) : receivingApi.create(input)
    },
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the inspection.'),
  })
  const p = po.data
  return (
    <Modal title={iar ? `Edit ${iar.iar_number}` : `Record delivery — ${p?.po_number ?? ''}`} onClose={onClose} width={900}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          setError(null)
          save.mutate()
        }}
      >
        {error && <div className="alert alert-error">{error}</div>}
        {p && (
          <p className="muted">
            {p.supplier.name} · due {p.delivery_date} {p.late && <span className="badge st-bad">overdue</span>}
          </p>
        )}
        <div className="form-grid three">
          <div className="field">
            <label htmlFor="iar-del">Delivered on *</label>
            <input id="iar-del" type="date" value={delivered} max={today()} onChange={(e) => setDelivered(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="iar-ins">Inspected on *</label>
            <input id="iar-ins" type="date" value={inspected} min={delivered} max={today()} onChange={(e) => setInspected(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="iar-obs">Observations</label>
            <input id="iar-obs" value={observations} onChange={(e) => setObservations(e.target.value)} />
          </div>
        </div>
        <div className="table-wrap" style={{ marginTop: '1rem' }}>
          <table className="table table-compact">
            <thead>
              <tr>
                <th>Item</th>
                <th className="num">Outstanding</th>
                <th className="num">Accepted</th>
                <th className="num">Rejected</th>
                <th>Reason / remarks</th>
              </tr>
            </thead>
            <tbody>
              {p?.items?.map((l) => {
                const r = rows[l.id] ?? { received: '', accepted: '', rejected: '0', remarks: '' }
                const mine = iar?.items?.find((x) => x.purchase_order_item_id === l.id)?.quantity_accepted ?? 0
                const left = l.outstanding + mine
                return (
                  <tr key={l.id} className={left === 0 ? 'row-muted' : ''}>
                    <td>{l.description}</td>
                    <td className="num">
                      {formatQty(left)} <span className="muted">{l.unit}</span>
                    </td>
                    <td className="num">
                      <input className="qty-input" type="number" min={0} max={left} disabled={left === 0} aria-label={`Accepted ${l.description}`} value={r.accepted} onChange={(e) => set(l.id, { accepted: e.target.value })} />
                    </td>
                    <td className="num">
                      <input className="qty-input" type="number" min={0} disabled={left === 0} aria-label={`Rejected ${l.description}`} value={r.rejected} onChange={(e) => set(l.id, { rejected: e.target.value })} />
                    </td>
                    <td>
                      <input aria-label={`Remarks for ${l.description}`} value={r.remarks} disabled={left === 0} placeholder={Number(r.rejected) > 0 ? 'Why rejected? *' : ''} onChange={(e) => set(l.id, { remarks: e.target.value })} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <p className="hint">Only lines with something delivered are recorded. The delivery goes to the supply officer for acceptance into stock.</p>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending || !p}>
            {save.isPending ? 'Saving…' : 'Save inspection'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

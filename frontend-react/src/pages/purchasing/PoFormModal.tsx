import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { poApi, type PoInput } from '../../api/endpoints'
import type { PurchaseOrder } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatPeso, formatQty } from '../../lib/format'

interface Props {
  po: PurchaseOrder | null
  onClose: () => void
  onSaved: (po: PurchaseOrder, message: string) => void
}

/** New PO from an award with its NOA, or edit of a draft (delivery terms; bidding line prices). */
export function PoFormModal({ po, onClose, onSaved }: Props) {
  const editing = po !== null
  const sources = useQuery({ queryKey: ['po-sources'], queryFn: poApi.sources, enabled: !editing })
  const [key, setKey] = useState('')
  const [delivery, setDelivery] = useState(po?.delivery_date ?? '')
  const [address, setAddress] = useState(po?.delivery_address ?? '')
  const [remarks, setRemarks] = useState(po?.remarks ?? '')
  const [prices, setPrices] = useState<Record<number, string>>(() =>
    Object.fromEntries((po?.items ?? []).filter((l) => l.bidding_item_id).map((l) => [l.bidding_item_id!, String(l.unit_cost)])),
  )
  const [error, setError] = useState<string | null>(null)
  const src = sources.data?.data.find((s) => `${s.type}:${s.id}` === key)
  const choose = (k: string) => {
    setKey(k)
    const s = sources.data?.data.find((x) => `${x.type}:${x.id}` === k)
    setPrices(Object.fromEntries((s?.lines ?? []).map((l) => [l.source_item_id, String(l.unit_cost)])))
    if (s?.delivery_term && !address) setAddress(s.delivery_term)
  }
  const lines = editing
    ? (po.items ?? []).map((l) => ({ id: l.bidding_item_id ?? l.id, description: l.description, unit: l.unit, quantity: l.quantity, unit_cost: l.unit_cost, editable: l.bidding_item_id != null }))
    : (src?.lines ?? []).map((l) => ({ id: l.source_item_id, description: l.description, unit: l.unit, quantity: l.quantity, unit_cost: l.unit_cost, editable: !src!.prices_fixed }))
  const total = lines.reduce((s, l) => s + l.quantity * (l.editable ? Number(prices[l.id]) || 0 : l.unit_cost), 0)
  const awarded = editing ? null : src?.amount
  const save = useMutation({
    mutationFn: () => {
      const input: PoInput = {
        delivery_date: delivery || null,
        delivery_address: address || null,
        remarks: remarks || null,
        prices: lines.some((l) => l.editable) ? lines.filter((l) => l.editable).map((l) => ({ source_item_id: l.id, unit_cost: Number(prices[l.id]) || 0 })) : undefined,
      }
      return editing ? poApi.update(po.id, input) : poApi.create({ ...input, source_type: src!.type, source_id: src!.id })
    },
    onSuccess: (p) => onSaved(p, editing ? `${p.po_number} saved.` : `${p.po_number} drafted.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the purchase order.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!editing && !src) return setError('Choose the award.')
    save.mutate()
  }
  return (
    <Modal title={editing ? `Edit ${po.po_number}` : 'New purchase order'} onClose={onClose} width={860}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid three">
          <div className="field span-3">
            <label htmlFor="po-src">Award *</label>
            {editing ? (
              <div className="readonly">
                {po.source?.number} · {po.supplier.name}
              </div>
            ) : (
              <select id="po-src" value={key} onChange={(e) => choose(e.target.value)} required>
                <option value="">{sources.isPending ? 'Loading…' : sources.data?.data.length ? 'Choose' : 'No awards with a Notice of Award waiting for a PO'}</option>
                {sources.data?.data.map((s) => (
                  <option key={`${s.type}:${s.id}`} value={`${s.type}:${s.id}`}>
                    {s.number} — {s.supplier.name} — {formatPeso(s.amount)} ({s.noa_number})
                  </option>
                ))}
              </select>
            )}
          </div>
          <div className="field">
            <label htmlFor="po-del">Delivery date</label>
            <input id="po-del" type="date" value={delivery} onChange={(e) => setDelivery(e.target.value)} />
            {!editing && src?.delivery_lead_days != null && <span className="hint">Defaults to {src.delivery_lead_days} days from today.</span>}
          </div>
          <div className="field span-2">
            <label htmlFor="po-addr">Place of delivery</label>
            <input id="po-addr" value={address} onChange={(e) => setAddress(e.target.value)} />
          </div>
          <div className="field span-3">
            <label htmlFor="po-rem">Remarks</label>
            <input id="po-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        {lines.length > 0 && (
          <div className="table-wrap" style={{ marginTop: '1rem' }}>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="num">Qty</th>
                  <th className="num">Unit cost</th>
                  <th className="num">Amount</th>
                </tr>
              </thead>
              <tbody>
                {lines.map((l) => {
                  const unit = l.editable ? Number(prices[l.id]) || 0 : l.unit_cost
                  return (
                    <tr key={l.id}>
                      <td>{l.description}</td>
                      <td className="num">
                        {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
                      </td>
                      <td className="num">
                        {l.editable ? (
                          <input className="qty-input" style={{ width: '8rem' }} type="number" min={0} step="0.01" aria-label={`Unit cost of ${l.description}`} value={prices[l.id] ?? ''} onChange={(e) => setPrices((p) => ({ ...p, [l.id]: e.target.value }))} />
                        ) : (
                          formatPeso(unit)
                        )}
                      </td>
                      <td className="num">{formatPeso(unit * l.quantity)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="form-total">
          Total <strong>{formatPeso(total)}</strong>
          {awarded != null && <span className="muted"> · awarded {formatPeso(awarded)}</span>}
        </p>
        {lines.some((l) => l.editable) && <p className="hint">Enter the unit prices from the winning bid form; they must add up to the awarded bid.</p>}
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

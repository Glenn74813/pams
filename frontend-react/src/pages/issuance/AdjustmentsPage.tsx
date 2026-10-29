import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { adjustmentsApi } from '../../api/endpoints'
import type { Item, StockAdjustment } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ItemPicker } from '../../components/ItemPicker'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso, formatQty } from '../../lib/format'
import { AdjustmentStatusBadge } from './badges'
import '../inventory/inventory.css'
import './issuance.css'

export function AdjustmentsPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('pending')
  const [creating, setCreating] = useState(false)
  const [confirm, setConfirm] = useState<{ a: StockAdjustment; action: 'approve' | 'reject' | 'withdraw' } | null>(null)

  const list = useQuery({
    queryKey: ['adjustments', page, status],
    queryFn: () => adjustmentsApi.list({ page, status: status || undefined }),
    placeholderData: keepPreviousData,
  })
  const refresh = () => {
    for (const k of ['adjustments', 'items', 'item', 'stock-card']) void queryClient.invalidateQueries({ queryKey: [k] })
  }

  const act = useMutation({
    mutationFn: async ({ a, action }: { a: StockAdjustment; action: 'approve' | 'reject' | 'withdraw' }): Promise<void> => {
      if (action === 'approve') await adjustmentsApi.approve(a.id)
      else if (action === 'reject') await adjustmentsApi.reject(a.id)
      else await adjustmentsApi.remove(a.id)
    },
    onSuccess: (_, { a, action }) => {
      toast(`${a.adjustment_number} ${action === 'withdraw' ? 'withdrawn' : action === 'reject' ? 'rejected' : action + 'd'}.`, 'success')
      setConfirm(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Action failed.', 'error')
      setConfirm(null)
    },
  })

  return (
    <div className="page page-wide">
      <h1 className="page-title">Stock Adjustments</h1>
      <p className="muted" style={{ marginBottom: '1rem' }}>
        Corrections to on-hand stock (count differences, damage, items found). Each adjustment needs approval from someone other than
        the person who filed it.
      </p>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="pending">Pending approval</option>
            <option value="approved">Approved</option>
            <option value="rejected">Rejected</option>
            <option value="">All</option>
          </select>
        </div>
        {can('create_adjustments') && (
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            + New Adjustment
          </button>
        )}
      </div>

      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>No.</th>
              <th>Filed</th>
              <th>Item</th>
              <th>Change</th>
              <th>Reason</th>
              <th>Filed by</th>
              <th className="num">Before → after</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((a) => (
              <tr key={a.id}>
                <td className="mono">{a.adjustment_number}</td>
                <td>{formatDate(a.created_at)}</td>
                <td>
                  <span className="mono">{a.item?.code}</span> {a.item?.name}
                </td>
                <td className={a.adjustment_type === 'increase' ? 'chg-up' : 'chg-down'}>
                  {a.adjustment_type === 'increase' ? '+' : '−'}
                  {formatQty(a.quantity)} {a.item?.unit_of_measure}
                  {a.unit_cost != null && <span className="muted"> @ {formatPeso(a.unit_cost)}</span>}
                </td>
                <td className="clip">{a.reason}</td>
                <td>{a.requested_by}</td>
                <td className="num">
                  {a.status === 'approved' ? `${formatQty(a.previous_quantity)} → ${formatQty(a.new_quantity)}` : `now ${formatQty(a.item?.current_stock)}`}
                </td>
                <td>
                  <AdjustmentStatusBadge status={a.status} />
                  {a.approved_by && <div className="muted small">by {a.approved_by}</div>}
                </td>
                <td>
                  <div className="actions">
                    {a.can_approve && (
                      <>
                        <button className="btn btn-primary btn-sm" onClick={() => setConfirm({ a, action: 'approve' })}>
                          Approve
                        </button>
                        <button className="btn btn-danger btn-sm" onClick={() => setConfirm({ a, action: 'reject' })}>
                          Reject
                        </button>
                      </>
                    )}
                    {a.can_delete && (
                      <button className="btn btn-outline btn-sm" onClick={() => setConfirm({ a, action: 'withdraw' })}>
                        Withdraw
                      </button>
                    )}
                    {a.status === 'pending' && !a.can_approve && !a.can_delete && <span className="muted small">Awaiting approver</span>}
                  </div>
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={9} className="empty">
                  No adjustments here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {creating && (
        <AdjustmentForm
          onClose={() => setCreating(false)}
          onSaved={(a) => {
            toast(`${a.adjustment_number} filed; it now needs approval.`, 'success')
            setCreating(false)
            refresh()
          }}
        />
      )}
      {confirm && (
        <ConfirmDialog
          title={`${confirm.action[0].toUpperCase() + confirm.action.slice(1)} ${confirm.a.adjustment_number}`}
          message={
            confirm.action === 'approve'
              ? `This ${confirm.a.adjustment_type === 'increase' ? 'adds' : 'removes'} ${confirm.a.quantity} ${confirm.a.item?.unit_of_measure} ${confirm.a.adjustment_type === 'increase' ? 'to' : 'from'} ${confirm.a.item?.name} and records it on the stock card.`
              : confirm.action === 'reject'
                ? 'Reject this adjustment? Stock will not change.'
                : 'Withdraw this adjustment request?'
          }
          confirmLabel={confirm.action[0].toUpperCase() + confirm.action.slice(1)}
          busy={act.isPending}
          onConfirm={() => act.mutate(confirm)}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  )
}

function AdjustmentForm({ onClose, onSaved }: { onClose: () => void; onSaved: (a: StockAdjustment) => void }) {
  const [item, setItem] = useState<Item | null>(null)
  const [type, setType] = useState<'increase' | 'decrease'>('decrease')
  const [qty, setQty] = useState('')
  const [cost, setCost] = useState('')
  const [reason, setReason] = useState('')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      adjustmentsApi.create({
        item_id: item!.id,
        adjustment_type: type,
        quantity: Number(qty),
        unit_cost: type === 'increase' && cost ? Number(cost) : null,
        reason,
        remarks,
      }),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not file the adjustment.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!item) {
      setError('Choose the item to adjust.')
      return
    }
    save.mutate()
  }

  return (
    <Modal title="New Stock Adjustment" onClose={onClose} width={620}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field" style={{ marginBottom: '1rem' }}>
          <label htmlFor="adj-item">Item *</label>
          {item ? (
            <div className="chosen">
              <span>
                <span className="mono">{item.code}</span> {item.name} · on hand {formatQty(item.current_stock)} {item.unit_of_measure}
              </span>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setItem(null)}>
                Change
              </button>
            </div>
          ) : (
            <ItemPicker id="adj-item" onPick={setItem} />
          )}
        </div>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="adj-type">Change *</label>
            <select id="adj-type" value={type} onChange={(e) => setType(e.target.value as 'increase' | 'decrease')}>
              <option value="decrease">Decrease (shortage, damage, loss)</option>
              <option value="increase">Increase (overage, found)</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="adj-qty">Quantity *</label>
            <input id="adj-qty" type="number" min={1} max={type === 'decrease' ? item?.current_stock : undefined} value={qty} onChange={(e) => setQty(e.target.value)} required />
          </div>
          {type === 'increase' && (
            <div className="field">
              <label htmlFor="adj-cost">Unit cost (₱)</label>
              <input id="adj-cost" type="number" min={0} step="0.01" value={cost} onChange={(e) => setCost(e.target.value)} placeholder={item ? `Avg ${formatPeso(item.weighted_average_cost)}` : ''} />
              <span className="hint">Leave blank to use the item's average cost.</span>
            </div>
          )}
          <div className="field span-2">
            <label htmlFor="adj-reason">Reason *</label>
            <input id="adj-reason" value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={255} placeholder="e.g. Physical count shortage, water damage" />
          </div>
          <div className="field span-2">
            <label htmlFor="adj-rem">Remarks</label>
            <textarea id="adj-rem" rows={2} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <p className="hint" style={{ marginTop: '0.75rem' }}>
          Decreases remove expired lots first. Nothing changes until another approver approves it.
        </p>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Filing…' : 'File for approval'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

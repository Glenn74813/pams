import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, poApi } from '../../api/endpoints'
import type { PurchaseOrder } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatPeso, formatQty } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { ACCEPTANCE, PO_STATUS } from './labels'
import { PoSlip } from './PoSlip'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'
import { PaymentsPanel } from '../payments/PaymentsPanel'
import { ExternalPaymentsSection } from '../integrations/ExternalPaymentsPage'

interface Props {
  poId: number
  onClose: () => void
  onEdit: (po: PurchaseOrder) => void
  onReceive?: (po: PurchaseOrder) => void
  onChanged: (message: string, closed?: boolean) => void
}

export function PoDetailModal({ poId, onClose, onEdit, onReceive, onChanged }: Props) {
  const { can } = useAuth()
  const q = useQuery({ queryKey: ['po', poId], queryFn: () => poApi.get(poId) })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const [printing, setPrinting] = useState(false)
  const [mode, setMode] = useState<null | 'cancel' | 'delete'>(null)
  const [why, setWhy] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const done = (m: string, closed?: boolean) => {
    setMode(null)
    setError(null)
    onChanged(m, closed)
  }
  const send = useMutation({ mutationFn: () => poApi.send(poId), onSuccess: (p) => done(`${p.po_number} approved and sent to ${p.supplier.name}.`), onError: fail })
  const confirm = useMutation({ mutationFn: () => poApi.confirm(poId), onSuccess: (p) => done(`${p.po_number} confirmed; deliveries can now be received.`), onError: fail })
  const cancel = useMutation({ mutationFn: () => poApi.cancel(poId, why || undefined), onSuccess: (p) => done(`${p.po_number} cancelled.`), onError: fail })
  const remove = useMutation({ mutationFn: () => poApi.remove(poId), onSuccess: (r) => done(r.message, true), onError: fail })
  const po = q.data
  const busy = send.isPending || confirm.isPending || cancel.isPending

  return (
    <Modal title={po ? `${po.po_number} — ${po.supplier.name}` : 'Purchase order'} onClose={onClose} width={960}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {po && printing && (
        <>
          <div className="slip-toolbar no-print">
            <button className="btn btn-secondary btn-sm" onClick={() => setPrinting(false)}>
              ‹ Back
            </button>
            <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
              🖨 Print
            </button>
          </div>
          <PoSlip po={po} config={config.data} />
        </>
      )}
      {po && !printing && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={PO_STATUS} value={po.status} /> {po.late && <span className="badge st-bad">Delivery overdue</span>}
            </dd>
            <dt>Award</dt>
            <dd>
              {po.source?.number} · {po.source?.noa_number}
            </dd>
            <dt>Purchase request</dt>
            <dd>
              {po.purchase_request?.name} · {po.department?.name}
            </dd>
            <dt>Delivery</dt>
            <dd>
              {formatDate(po.delivery_date)} · {po.delivery_address ?? '—'}
            </dd>
            <dt>Prepared / approved</dt>
            <dd>
              {po.preparer?.name} · {po.approver?.name ?? <span className="muted">not yet approved</span>}
            </dd>
            {po.fund_source && (
              <>
                <dt>Fund source</dt>
                <dd>
                  {po.fund_source} {po.caf_number && `· ${po.caf_number}`}
                </dd>
              </>
            )}
            {po.remarks && (
              <>
                <dt>Remarks</dt>
                <dd>{po.remarks}</dd>
              </>
            )}
          </dl>
          <div className="table-wrap" style={{ marginTop: '1rem' }}>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="num">Ordered</th>
                  <th className="num">Unit cost</th>
                  <th className="num">Amount</th>
                  <th className="num">Delivered</th>
                  <th className="num">In stock</th>
                  <th className="num">Outstanding</th>
                </tr>
              </thead>
              <tbody>
                {po.items?.map((l) => (
                  <tr key={l.id}>
                    <td>{l.description}</td>
                    <td className="num">
                      {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
                    </td>
                    <td className="num">{formatPeso(l.unit_cost)}</td>
                    <td className="num">{formatPeso(l.total_cost)}</td>
                    <td className="num">{formatQty(l.delivered)}</td>
                    <td className="num">{formatQty(l.accepted)}</td>
                    <td className={'num' + (l.outstanding > 0 && po.late ? ' short' : '')}>{formatQty(l.outstanding)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan={3}>Total</th>
                  <th className="num">{formatPeso(po.total_amount)}</th>
                  <th colSpan={3}></th>
                </tr>
              </tfoot>
            </table>
          </div>
          {po.inspections && po.inspections.length > 0 && (
            <>
              <h4>Deliveries</h4>
              <ul>
                {po.inspections.map((r) => (
                  <li key={r.id}>
                    <span className="mono">{r.iar_number}</span> · {formatDate(r.delivery_date)} <Badge map={ACCEPTANCE} value={r.acceptance_status} />
                  </li>
                ))}
              </ul>
            </>
          )}
          {mode === 'cancel' && (
            <div className="reject-box">
              <div className="field">
                <label htmlFor="po-why">Reason for cancelling{po.status === 'draft' ? '' : ' *'}</label>
                <input id="po-why" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus />
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setMode(null)}>
                  Back
                </button>
                <button className="btn btn-danger" disabled={busy || (po.status !== 'draft' && !why.trim())} onClick={() => cancel.mutate()}>
                  Cancel PO
                </button>
              </div>
            </div>
          )}
          {mode === 'delete' && (
            <ConfirmDialog title="Delete purchase order" message={`Delete the draft ${po.po_number}? The award can then be ordered again.`} confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} onCancel={() => setMode(null)} />
          )}
          {mode === null && (
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setPrinting(true)}>
                🖨 PO form
              </button>
              {po.can_delete && (
                <button className="btn btn-danger" onClick={() => setMode('delete')}>
                  Delete
                </button>
              )}
              {po.can_cancel && po.status !== 'draft' && (
                <button className="btn btn-secondary" onClick={() => setMode('cancel')}>
                  Cancel PO
                </button>
              )}
              {po.can_edit && (
                <button className="btn btn-secondary" onClick={() => onEdit(po)}>
                  Edit
                </button>
              )}
              {po.status === 'draft' && !po.can_send && <span className="muted footer-note">Someone other than the preparer approves and sends it.</span>}
              {po.can_send && (
                <button className="btn btn-primary" onClick={() => send.mutate()} disabled={busy}>
                  Approve &amp; send
                </button>
              )}
              {po.can_confirm && (
                <button className="btn btn-primary" onClick={() => confirm.mutate()} disabled={busy}>
                  Supplier confirmed
                </button>
              )}
              {po.can_receive && onReceive && (
                <button className="btn btn-primary" onClick={() => onReceive(po)}>
                  Record delivery
                </button>
              )}
            </div>
          )}
        </>
      )}
      {po && !printing && can('view_payments', 'manage_payments', 'approve_payments') && <PaymentsPanel type="purchase_order" id={po.id} amount={po.total_amount} />}
      {po && !printing && can('view_payments', 'manage_payments', 'approve_payments') && <ExternalPaymentsSection type="purchase_order" id={po.id} />}
      {po && !printing && <AttachmentsPanel type="PurchaseOrder" id={po.id} label={po.po_number} docType="PO" />}
    </Modal>
  )
}

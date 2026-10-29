import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, prApi } from '../../api/endpoints'
import type { PurchaseRequest } from '../../api/types'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatPeso, formatQty } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { PR_STATUS, PRIORITY } from './labels'
import { PrSlip } from './PrSlip'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'

interface Props {
  prId: number
  onClose: () => void
  onEdit: (p: PurchaseRequest) => void
  onChanged: (message: string, closed?: boolean) => void
}

export function PrDetailModal({ prId, onClose, onEdit, onChanged }: Props) {
  const q = useQuery({ queryKey: ['pr', prId], queryFn: () => prApi.get(prId) })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const [printing, setPrinting] = useState(false)
  const p = q.data
  return (
    <Modal title={p ? `Purchase Request ${p.pr_number}` : 'Purchase Request'} onClose={onClose} width={960}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {p && printing && (
        <>
          <div className="slip-toolbar no-print">
            <button className="btn btn-secondary btn-sm" onClick={() => setPrinting(false)}>
              ‹ Back
            </button>
            <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
              🖨 Print
            </button>
          </div>
          <PrSlip pr={p} config={config.data} />
        </>
      )}
      {p && !printing && <Details key={p.status + p.current_step + p.caf_number} p={p} onPrint={() => setPrinting(true)} onEdit={onEdit} onChanged={onChanged} />}
      {p && !printing && <AttachmentsPanel type="PurchaseRequest" id={p.id} label={p.pr_number} docType="PR" />}
    </Modal>
  )
}

type Mode = null | 'return' | 'caf' | 'cancel' | 'delete'

function Details({ p, onPrint, onEdit, onChanged }: { p: PurchaseRequest; onPrint: () => void; onEdit: Props['onEdit']; onChanged: Props['onChanged'] }) {
  const [mode, setMode] = useState<Mode>(null)
  const [text, setText] = useState('')
  const [amount, setAmount] = useState(String(p.total_amount))
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => {
    setMode(null)
    setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  }
  const done = (m: string, closed?: boolean) => () => onChanged(m, closed)
  const submit = useMutation({ mutationFn: () => prApi.submit(p.id), onSuccess: done(`${p.pr_number} submitted for approval.`), onError: fail })
  const approve = useMutation({ mutationFn: () => prApi.approveStep(p.id), onSuccess: (r) => onChanged(r.status === 'approved' ? `${p.pr_number} is fully approved.` : `Signed; ${p.pr_number} is now with ${r.stage_label}.`), onError: fail })
  const ret = useMutation({ mutationFn: () => prApi.returnStep(p.id, text), onSuccess: done(`${p.pr_number} returned to the requester.`), onError: fail })
  const caf = useMutation({ mutationFn: () => prApi.certifyFunds(p.id, { caf_amount: Number(amount), caf_remarks: text || undefined }), onSuccess: (r) => onChanged(`${r.caf_number} issued for ${p.pr_number}.`), onError: fail })
  const cancel = useMutation({ mutationFn: () => prApi.cancel(p.id), onSuccess: done(`${p.pr_number} cancelled.`), onError: fail })
  const remove = useMutation({ mutationFn: () => prApi.remove(p.id), onSuccess: (r) => onChanged(r.message, true), onError: fail })
  const busy = [submit, approve, ret, caf, cancel, remove].some((m) => m.isPending)
  const step = p.approvals?.find((a) => a.step === p.current_step)
  const budgetStep = step?.required_permission === 'pr_approve_budget'

  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <dl className="detail-grid">
        <dt>Status</dt>
        <dd>
          <Badge map={PR_STATUS} value={p.status} /> {p.status !== 'draft' && <span className="muted">{p.stage_label}</span>} <Badge map={PRIORITY} value={p.priority} />
        </dd>
        <dt>Requested by</dt>
        <dd>
          {p.requester.name} · {p.department.name} · {formatDate(p.request_date)}
        </dd>
        <dt>APP</dt>
        <dd>{p.app ? `${p.app.app_number} (FY ${p.app.fiscal_year})` : <span className="muted">Not in the APP</span>}</dd>
        <dt>Purpose</dt>
        <dd>{p.purpose}</dd>
        {p.procurement_modes.length > 0 && (
          <>
            <dt>Mode / funds</dt>
            <dd>
              {p.procurement_modes.join(', ')} · {p.fund_sources.join('; ') || 'no fund source'}
            </dd>
          </>
        )}
        {p.return_reason && (
          <>
            <dt>Returned because</dt>
            <dd>{p.return_reason}</dd>
          </>
        )}
        {p.remarks && (
          <>
            <dt>Remarks</dt>
            <dd>{p.remarks}</dd>
          </>
        )}
      </dl>

      <div className="table-wrap" style={{ marginTop: '1rem' }}>
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Description</th>
              <th className="num">Qty</th>
              <th className="num">Unit cost</th>
              <th className="num">Total</th>
              <th>Mode</th>
            </tr>
          </thead>
          <tbody>
            {p.items?.map((l) => (
              <tr key={l.id}>
                <td>
                  {l.general_description} {l.app_item_id && <span className="badge badge-muted">APP</span>}
                  {l.specifications && <div className="muted small">{l.specifications}</div>}
                </td>
                <td className="num">
                  {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
                </td>
                <td className="num">{formatPeso(l.estimated_unit_cost)}</td>
                <td className="num">{formatPeso(l.estimated_total)}</td>
                <td>{l.procurement_mode ?? '—'}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th colSpan={3}>Total</th>
              <th className="num">{formatPeso(p.total_amount)}</th>
              <th></th>
            </tr>
          </tfoot>
        </table>
      </div>

      {p.approvals && p.approvals.length > 0 && (
        <>
          <h4 style={{ margin: '1rem 0 0' }}>Approval routing</h4>
          <ol className="chain">
            {p.approvals.map((a) => (
              <li key={a.step} className={`step-${a.status}${a.step === p.current_step ? ' step-current' : ''}`}>
                <span className="step-dot">{a.status === 'approved' ? '✓' : a.status === 'returned' ? '↩' : a.step}</span>
                <span>
                  <strong>{a.label}</strong>{' '}
                  {a.actor ? (
                    <span className="muted">
                      {a.status === 'returned' ? 'returned by' : 'signed by'} {a.actor.name} · {formatDate(a.acted_at)}
                    </span>
                  ) : (
                    <span className="muted">{a.step === p.current_step ? 'waiting' : 'pending'}</span>
                  )}
                  {a.remarks && <div className="small">{a.remarks}</div>}
                </span>
              </li>
            ))}
          </ol>
        </>
      )}

      {p.status !== 'draft' && p.status !== 'cancelled' && (
        <div className="caf-box">
          {p.caf_number ? (
            <>
              <span className={p.funds_covered ? 'badge st-ok' : 'badge st-bad'}>{p.funds_covered ? 'Funds certified' : 'CAF below the PR total'}</span>
              <strong>{p.caf_number}</strong>
              <span>{formatPeso(p.caf_amount)}</span>
              <span className="muted">
                {p.caf_certifier?.name} · {formatDate(p.caf_certified_at)}
              </span>
              {p.caf_remarks && <span className="muted">{p.caf_remarks}</span>}
            </>
          ) : (
            <span className="badge st-pending">Funds not yet certified (CAF)</span>
          )}
          {p.can_certify && mode === null && (
            <button className="btn btn-outline btn-sm" onClick={() => setMode('caf')}>
              Certify funds
            </button>
          )}
        </div>
      )}

      {mode === 'caf' && (
        <div className="reject-box">
          <div className="form-grid">
            <div className="field">
              <label htmlFor="caf-amt">Amount certified (₱) *</label>
              <input id="caf-amt" type="number" step="0.01" min={p.total_amount} value={amount} onChange={(e) => setAmount(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="caf-rem">Remarks</label>
              <input id="caf-rem" value={text} onChange={(e) => setText(e.target.value)} placeholder="Fund, allotment class, object code" />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setMode(null)}>
              Cancel
            </button>
            <button className="btn btn-primary" onClick={() => caf.mutate()} disabled={busy}>
              {caf.isPending ? 'Certifying…' : 'Issue CAF'}
            </button>
          </div>
        </div>
      )}
      {mode === 'return' && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="pr-ret">What needs revising? *</label>
            <textarea id="pr-ret" rows={2} value={text} onChange={(e) => setText(e.target.value)} autoFocus maxLength={1000} />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setMode(null)}>
              Cancel
            </button>
            <button className="btn btn-danger" onClick={() => ret.mutate()} disabled={busy || !text.trim()}>
              {ret.isPending ? 'Returning…' : 'Return to requester'}
            </button>
          </div>
        </div>
      )}
      {mode === null && (
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onPrint}>
            🖨 PR form
          </button>
          {p.can_delete && (
            <button className="btn btn-danger" onClick={() => setMode('delete')}>
              Delete
            </button>
          )}
          {p.can_cancel && (
            <button className="btn btn-secondary" onClick={() => setMode('cancel')}>
              Cancel PR
            </button>
          )}
          {p.can_edit && (
            <button className="btn btn-secondary" onClick={() => onEdit(p)}>
              Edit
            </button>
          )}
          {p.can_submit && (
            <button className="btn btn-primary" onClick={() => submit.mutate()} disabled={busy}>
              {p.status === 'returned' ? 'Resubmit' : 'Submit for approval'}
            </button>
          )}
          {p.can_act && (
            <>
              <button className="btn btn-danger" onClick={() => { setText(''); setMode('return') }} disabled={busy}>
                Return
              </button>
              <button className="btn btn-primary" onClick={() => approve.mutate()} disabled={busy || (budgetStep && !p.funds_covered)} title={budgetStep && !p.funds_covered ? 'Certify funds first' : undefined}>
                {approve.isPending ? 'Signing…' : `Sign as ${step?.label}`}
              </button>
            </>
          )}
        </div>
      )}
      {mode === 'cancel' && (
        <ConfirmDialog title="Cancel purchase request" message={`Cancel ${p.pr_number}? Its APP balance is released.`} confirmLabel="Cancel PR" busy={busy} onConfirm={() => cancel.mutate()} onCancel={() => setMode(null)} />
      )}
      {mode === 'delete' && (
        <ConfirmDialog title="Delete purchase request" message={`Delete ${p.pr_number}? The number is not reused.`} confirmLabel="Delete PR" busy={busy} onConfirm={() => remove.mutate()} onCancel={() => setMode(null)} />
      )}
    </>
  )
}

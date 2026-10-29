import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { planningApi } from '../../api/endpoints'
import type { Ppmp } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatDate, formatPeso, formatQty } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { PPMP_STATUS, QUARTER_LABEL } from './labels'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'

interface Props {
  ppmpId: number
  onClose: () => void
  onEdit: (p: Ppmp) => void
  onChanged: (message: string) => void
}

export function PpmpDetailModal({ ppmpId, onClose, onEdit, onChanged }: Props) {
  const q = useQuery({ queryKey: ['ppmp', ppmpId], queryFn: () => planningApi.ppmp(ppmpId) })
  const [returning, setReturning] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const act = useMutation({
    mutationFn: (a: 'submit' | 'review' | 'approve') => planningApi.ppmpAction(ppmpId, a),
    onSuccess: (p, a) => onChanged(`${p.ppmp_number} ${a === 'submit' ? 'submitted for review' : a === 'review' ? 'taken up for review' : 'approved'}.`),
    onError: fail,
  })
  const ret = useMutation({
    mutationFn: () => planningApi.returnPpmp(ppmpId, reason),
    onSuccess: (p) => onChanged(`${p.ppmp_number} returned for revision.`),
    onError: fail,
  })
  const p = q.data
  const busy = act.isPending || ret.isPending

  return (
    <Modal title={p ? p.ppmp_number : 'PPMP'} onClose={onClose} width={960}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {p && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={PPMP_STATUS} value={p.status} /> {p.revision > 0 && <span className="muted">Revision {p.revision}</span>}
              {p.app_number && <span className="muted"> · in {p.app_number}</span>}
            </dd>
            <dt>Title</dt>
            <dd>{p.title}</dd>
            <dt>Department / FY</dt>
            <dd>
              {p.department.name} · FY {p.fiscal_year}
            </dd>
            <dt>Prepared by</dt>
            <dd>
              {p.preparer?.name} · {formatDate(p.created_at)}
            </dd>
            {p.reviewer && (
              <>
                <dt>Reviewed by</dt>
                <dd>{p.reviewer.name}</dd>
              </>
            )}
            {p.approver && (
              <>
                <dt>Approved by</dt>
                <dd>
                  {p.approver.name} · {formatDate(p.approved_at)}
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
                  <th>Code</th>
                  <th>Description</th>
                  <th className="num">Qty</th>
                  <th className="num">Unit cost</th>
                  <th className="num">Total</th>
                  <th>Mode</th>
                  <th>Schedule</th>
                  <th>Source of funds</th>
                </tr>
              </thead>
              <tbody>
                {p.items?.map((l) => (
                  <tr key={l.id}>
                    <td className="mono">{l.code ?? '—'}</td>
                    <td>
                      {l.general_description}
                      {l.specifications && <div className="muted small">{l.specifications}</div>}
                      {l.is_sustainable && <span className="badge st-ok">Green</span>}
                    </td>
                    <td className="num">
                      {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
                    </td>
                    <td className="num">{formatPeso(l.estimated_unit_cost)}</td>
                    <td className="num">{formatPeso(l.estimated_total)}</td>
                    <td>
                      {l.procurement_mode}
                      <div className="muted small">{l.procurement_category}</div>
                    </td>
                    <td>{l.schedule ? QUARTER_LABEL[l.schedule] : '—'}</td>
                    <td className="clip">{l.fund_source ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan={4}>Total estimated budget</th>
                  <th className="num">{formatPeso(p.total_estimated_budget)}</th>
                  <th colSpan={3}></th>
                </tr>
              </tfoot>
            </table>
          </div>

          {returning ? (
            <div className="reject-box">
              <div className="field">
                <label htmlFor="pp-ret">What needs revising? *</label>
                <textarea id="pp-ret" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} autoFocus maxLength={1000} />
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setReturning(false)}>
                  Cancel
                </button>
                <button className="btn btn-danger" onClick={() => ret.mutate()} disabled={busy || !reason.trim()}>
                  {ret.isPending ? 'Returning…' : 'Return for revision'}
                </button>
              </div>
            </div>
          ) : (
            (p.can_edit || p.can_submit || p.can_review || p.can_decide) && (
              <div className="modal-footer">
                {p.can_edit && (
                  <button className="btn btn-secondary" onClick={() => onEdit(p)}>
                    Edit
                  </button>
                )}
                {p.can_submit && (
                  <button className="btn btn-primary" onClick={() => act.mutate('submit')} disabled={busy}>
                    Submit for review
                  </button>
                )}
                {p.can_decide && (
                  <button className="btn btn-danger" onClick={() => setReturning(true)} disabled={busy}>
                    Return
                  </button>
                )}
                {p.can_review && (
                  <button className="btn btn-outline" onClick={() => act.mutate('review')} disabled={busy}>
                    Take up for review
                  </button>
                )}
                {p.can_decide && (
                  <button className="btn btn-primary" onClick={() => act.mutate('approve')} disabled={busy}>
                    Approve
                  </button>
                )}
              </div>
            )
          )}
        </>
      )}
      {p && <AttachmentsPanel type="Ppmp" id={p.id} label={p.ppmp_number} docType="PPMP" />}
    </Modal>
  )
}

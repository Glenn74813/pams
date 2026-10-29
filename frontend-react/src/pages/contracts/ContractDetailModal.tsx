import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { contractApi } from '../../api/endpoints'
import type { Contract } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatPeso } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { AmendmentsPanel, CalloffsPanel, DeliveryPanel, SecuritiesPanel } from './ContractPanels'
import { CONTRACT_STATUS } from './labels'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'
import { PaymentsPanel } from '../payments/PaymentsPanel'

interface Props {
  contractId: number
  onClose: () => void
  onEdit: (c: Contract) => void
  onChanged: (message: string, closed?: boolean) => void
}

export function ContractDetailModal({ contractId, onClose, onEdit, onChanged }: Props) {
  const { can } = useAuth()
  const q = useQuery({ queryKey: ['contract', contractId], queryFn: () => contractApi.get(contractId) })
  const [mode, setMode] = useState<null | 'terminate' | 'delete'>(null)
  const [why, setWhy] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const done = (m: string, closed?: boolean) => {
    setMode(null)
    setError(null)
    onChanged(m, closed)
  }
  const activate = useMutation({ mutationFn: () => contractApi.activate(contractId), onSuccess: (c) => done(`${c.contract_number} is in force.`), onError: fail })
  const complete = useMutation({ mutationFn: () => contractApi.complete(contractId), onSuccess: (c) => done(`${c.contract_number} completed.`), onError: fail })
  const terminate = useMutation({ mutationFn: () => contractApi.terminate(contractId, why), onSuccess: (c) => done(`${c.contract_number} terminated.`), onError: fail })
  const remove = useMutation({ mutationFn: () => contractApi.remove(contractId), onSuccess: (r) => done(r.message, true), onError: fail })
  const c = q.data
  const manage = can('create_contracts', 'edit_contracts')
  const edit = can('edit_contracts')
  const panel = { manage, edit, onChanged: (m: string) => done(m), onError: setError }
  return (
    <Modal title={c ? `${c.contract_number} — ${c.supplier.name}` : 'Contract'} onClose={onClose} width={1000}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {c && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={CONTRACT_STATUS} value={c.status} /> {c.expiring && <span className="badge st-pending">ends in {c.days_to_expiry} day(s)</span>}
              {c.expired && <span className="badge st-bad">past its end date</span>}
            </dd>
            <dt>Title / type</dt>
            <dd>
              {c.title} · {c.contract_type}
            </dd>
            <dt>Award</dt>
            <dd>
              {c.source?.name} {c.purchase_order && `· ${c.purchase_order.name}`} {c.purchase_request && `· ${c.purchase_request.name}`}
            </dd>
            <dt>Price</dt>
            <dd>
              <strong>{formatPeso(c.amount)}</strong> {c.amount !== c.original_amount && <span className="muted">(original {formatPeso(c.original_amount)})</span>}
            </dd>
            <dt>Term</dt>
            <dd>
              {formatDate(c.start_date)} – {formatDate(c.end_date)} · signed {formatDate(c.signed_date)}
            </dd>
            {c.fund_source && (
              <>
                <dt>Fund source</dt>
                <dd>
                  {c.fund_source} {c.caf_number && `· ${c.caf_number}`}
                </dd>
              </>
            )}
            {c.description && (
              <>
                <dt>Scope</dt>
                <dd>{c.description}</dd>
              </>
            )}
            {c.terms && (
              <>
                <dt>Special conditions</dt>
                <dd style={{ whiteSpace: 'pre-wrap' }}>{c.terms}</dd>
              </>
            )}
            {c.remarks && (
              <>
                <dt>Remarks</dt>
                <dd>{c.remarks}</dd>
              </>
            )}
          </dl>
          <SecuritiesPanel c={c} {...panel} />
          {c.status !== 'draft' && c.contract_type !== 'Framework Agreement' && <DeliveryPanel c={c} {...panel} />}
          {c.status !== 'draft' && <AmendmentsPanel c={c} {...panel} />}
          {c.contract_type === 'Framework Agreement' && c.status !== 'draft' && <CalloffsPanel c={c} {...panel} />}
          {mode === 'terminate' && (
            <div className="reject-box">
              <div className="field">
                <label htmlFor="ct-why">Ground for termination *</label>
                <input id="ct-why" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus />
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setMode(null)}>
                  Back
                </button>
                <button className="btn btn-danger" disabled={!why.trim() || terminate.isPending} onClick={() => terminate.mutate()}>
                  Terminate contract
                </button>
              </div>
            </div>
          )}
          {mode === 'delete' && (
            <ConfirmDialog title="Delete contract" message={`Delete the draft ${c.contract_number}?`} confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} onCancel={() => setMode(null)} />
          )}
          {mode === null && (
            <div className="modal-footer">
              {c.can_delete && (
                <button className="btn btn-danger" onClick={() => setMode('delete')}>
                  Delete
                </button>
              )}
              {c.can_terminate && (
                <button className="btn btn-secondary" onClick={() => setMode('terminate')}>
                  Terminate
                </button>
              )}
              {c.can_edit && c.status === 'draft' && (
                <button className="btn btn-secondary" onClick={() => onEdit(c)}>
                  Edit
                </button>
              )}
              {c.status === 'draft' && !c.can_activate && <span className="muted footer-note">Someone other than the preparer puts it in force.</span>}
              {c.can_activate && (
                <button className="btn btn-primary" onClick={() => activate.mutate()} disabled={activate.isPending}>
                  Put in force
                </button>
              )}
              {c.can_complete && (
                <button className="btn btn-primary" onClick={() => complete.mutate()} disabled={complete.isPending}>
                  Complete
                </button>
              )}
            </div>
          )}
        </>
      )}
      {c && can('view_payments', 'manage_payments', 'approve_payments') && <PaymentsPanel type="contract" id={c.id} amount={c.amount} />}
      {c && <AttachmentsPanel type="Contract" id={c.id} label={c.contract_number} docType="CONTRACT" />}
    </Modal>
  )
}

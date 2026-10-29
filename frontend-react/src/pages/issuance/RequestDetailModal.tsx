import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, requestsApi, type Decision } from '../../api/endpoints'
import type { SupplyRequest } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatDate, formatQty } from '../../lib/format'
import { RequestStatusBadge } from './badges'
import { PrintSlip, type SlipKind } from './PrintSlip'
import { ExcelButton } from '../../components/ExcelButton'

interface Props {
  requestId: number
  onClose: () => void
  onChanged: (message: string, warnings?: string[]) => void
}

export function RequestDetailModal({ requestId, onClose, onChanged }: Props) {
  const req = useQuery({ queryKey: ['request', requestId], queryFn: () => requestsApi.get(requestId) })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const [slip, setSlip] = useState<SlipKind | null>(null)
  const r = req.data

  return (
    <Modal title={r ? `Request ${r.request_number}` : 'Request'} onClose={onClose} width={960}>
      {req.isPending && <div className="page-loading">Loading…</div>}
      {req.isError && <div className="alert alert-error">{(req.error as Error).message}</div>}
      {r && slip && (
        <>
          <div className="slip-toolbar no-print">
            <button className="btn btn-secondary btn-sm" onClick={() => setSlip(null)}>
              ‹ Back
            </button>
            <span>
              <ExcelButton path={`/requests/${r.id}/export-${slip}`} name={`${slip.toUpperCase()}_${r.request_number}.xlsx`} label={`${slip.toUpperCase()} (Excel)`} />{' '}
              <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
                🖨 Print
              </button>
            </span>
          </div>
          <PrintSlip request={r} kind={slip} config={config.data} />
        </>
      )}
      {r && !slip && <Details r={r} onSlip={setSlip} onChanged={onChanged} />}
    </Modal>
  )
}

function Details({ r, onSlip, onChanged }: { r: SupplyRequest; onSlip: (k: SlipKind) => void; onChanged: Props['onChanged'] }) {
  const [qty, setQty] = useState<Record<number, string>>(() =>
    Object.fromEntries(r.items.map((l) => [l.id, String(Math.min(l.quantity, Math.max(0, l.current_stock)))])),
  )
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const process = useMutation({
    mutationFn: () => {
      const decisions: Decision[] = r.items.map((l) => {
        const n = Number(qty[l.id]) || 0
        return n > 0 ? { id: l.id, status: 'approved', approved_quantity: n } : { id: l.id, status: 'rejected' }
      })
      return requestsApi.process(r.id, decisions)
    },
    onSuccess: (res) => onChanged(`${res.data.request_number} ${res.data.status.replace('_', ' ')} — ${res.data.ris_number ?? ''}`, res.warnings),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not process the request.'),
  })
  const reject = useMutation({
    mutationFn: () => requestsApi.reject(r.id, reason),
    onSuccess: () => onChanged(`${r.request_number} rejected.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not reject the request.'),
  })

  const issued = r.status === 'fulfilled' || r.status === 'partially_fulfilled'

  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <dl className="detail-grid">
        <dt>Status</dt>
        <dd>
          <RequestStatusBadge status={r.status} /> <span className="muted">Priority: {r.priority}</span>
        </dd>
        <dt>Requested by</dt>
        <dd>
          {r.requester?.name} · {r.department?.name} · {formatDate(r.request_date)}
        </dd>
        <dt>Purpose</dt>
        <dd>{r.purpose}</dd>
        {r.custodian_name && (
          <>
            <dt>Accountable person</dt>
            <dd>{r.custodian_name}</dd>
          </>
        )}
        {issued && (
          <>
            <dt>Documents</dt>
            <dd className="doc-links">
              {r.ris_number && (
                <button className="btn btn-outline btn-sm" onClick={() => onSlip('ris')}>
                  RIS {r.ris_number}
                </button>
              )}
              {r.ics_number && (
                <button className="btn btn-outline btn-sm" onClick={() => onSlip('ics')}>
                  ICS {r.ics_number}
                </button>
              )}
              {r.par_number && (
                <button className="btn btn-outline btn-sm" onClick={() => onSlip('par')}>
                  PAR {r.par_number}
                </button>
              )}
            </dd>
          </>
        )}
        {r.remarks && (
          <>
            <dt>Remarks</dt>
            <dd>{r.remarks}</dd>
          </>
        )}
      </dl>

      <div className="table-wrap" style={{ marginTop: '1rem' }}>
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Item</th>
              <th>Class</th>
              <th className="num">Requested</th>
              <th className="num">In stock</th>
              <th className="num">{r.can_process ? 'Issue' : 'Issued'}</th>
              <th>Line status</th>
            </tr>
          </thead>
          <tbody>
            {r.items.map((l) => (
              <tr key={l.id}>
                <td>
                  <span className="mono">{l.code}</span> {l.name}
                </td>
                <td>{l.classification === 'ppe' ? 'PPE' : l.classification === 'semi_expendable' ? 'Semi-exp.' : 'Consumable'}</td>
                <td className="num">
                  {formatQty(l.quantity)} <span className="muted">{l.unit_of_measure}</span>
                </td>
                <td className={'num' + (l.current_stock < l.quantity ? ' short' : '')}>{formatQty(l.current_stock)}</td>
                <td className="num">
                  {r.can_process ? (
                    <input
                      className="qty-input"
                      type="number"
                      min={0}
                      max={Math.min(l.quantity, l.current_stock)}
                      value={qty[l.id]}
                      aria-label={`Quantity of ${l.name} to issue`}
                      onChange={(e) => setQty((q) => ({ ...q, [l.id]: e.target.value }))}
                    />
                  ) : (
                    formatQty(l.approved_quantity)
                  )}
                </td>
                <td>{l.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {r.can_process && !rejecting && (
        <div className="modal-footer">
          <span className="muted footer-note">Lines set to 0 are not issued. Stock is taken first-expired-first-out.</span>
          <button className="btn btn-danger" onClick={() => setRejecting(true)}>
            Reject request
          </button>
          <button className="btn btn-primary" onClick={() => process.mutate()} disabled={process.isPending}>
            {process.isPending ? 'Issuing…' : 'Approve & issue'}
          </button>
        </div>
      )}
      {r.can_process && rejecting && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="rj-reason">Reason for rejecting *</label>
            <textarea id="rj-reason" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setRejecting(false)}>
              Cancel
            </button>
            <button className="btn btn-danger" onClick={() => reject.mutate()} disabled={reject.isPending || !reason.trim()}>
              {reject.isPending ? 'Rejecting…' : 'Reject request'}
            </button>
          </div>
        </div>
      )}
    </>
  )
}

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, physicalCountApi } from '../../api/endpoints'
import type { CountDetail, PhysicalCount } from '../../api/types'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatDateTime } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { CountReport, type GroupBy } from './CountReport'
import { SuppliesSheet } from './SuppliesSheet'
import { TagsSheet } from './TagsSheet'
import { COUNT_STATUS, COUNT_TYPE } from './labels'

interface Props {
  countId: number
  onClose: () => void
  onEdit: (c: PhysicalCount) => void
  onChanged: (message: string, closed?: boolean) => void
}

type Mode = null | 'start' | 'complete' | 'return' | 'verify' | 'delete'

export function CountDetailModal({ countId, onClose, onEdit, onChanged }: Props) {
  const queryClient = useQueryClient()
  const q = useQuery({ queryKey: ['physical-count', countId], queryFn: () => physicalCountApi.get(countId) })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const [printing, setPrinting] = useState(false)
  const [groupBy, setGroupBy] = useState<GroupBy>('none')
  const [discrepanciesOnly, setDiscrepanciesOnly] = useState(false)
  const [mode, setMode] = useState<Mode>(null)
  const [why, setWhy] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => {
    setMode(null)
    setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  }
  const updated = (d: CountDetail, message: string) => {
    queryClient.setQueryData(['physical-count', countId], d)
    setMode(null)
    setError(null)
    onChanged(message)
  }
  const act = useMutation({
    mutationFn: (m: Exclude<Mode, null | 'delete'>) =>
      m === 'start' ? physicalCountApi.start(countId) : m === 'complete' ? physicalCountApi.complete(countId) : m === 'return' ? physicalCountApi.sendBack(countId, why) : physicalCountApi.verify(countId),
    onSuccess: (d, m) =>
      updated(
        d,
        m === 'start'
          ? `${d.count.count_number} started: book balances frozen.`
          : m === 'complete'
            ? `${d.count.count_number} completed and sent for verification.`
            : m === 'return'
              ? `${d.count.count_number} sent back for recount.`
              : `${d.count.count_number} verified and posted.`,
      ),
    onError: fail,
  })
  const remove = useMutation({ mutationFn: () => physicalCountApi.remove(countId), onSuccess: (r) => onChanged(r.message, true), onError: fail })
  const d = q.data
  const c = d?.count
  const supplies = c?.count_type === 'supplies'
  const confirm: Record<string, [string, string, string]> = {
    start: ['Start counting?', supplies ? 'The book balance of every item in scope is frozen now. Receipts and issues can continue; only the difference against this cut-off is posted.' : 'The asset masterlist for this scope is frozen now.', 'Start'],
    complete: ['Close counting?', supplies ? 'Every line must have a counted quantity.' : 'Tags not yet checked will be reported missing.', 'Complete'],
    verify: ['Verify and post?', supplies ? `Each variance becomes an approved stock adjustment (${d?.summary.short_count ?? 0} short, ${d?.summary.over_count ?? 0} over).` : 'Conditions seen on the floor become the conditions on record. Missing assets stay on record for follow-up.', 'Verify'],
    delete: ['Delete this count?', 'Nothing has been posted from it yet.', 'Delete'],
  }
  return (
    <Modal title={c ? `${c.count_number} — ${COUNT_TYPE[c.count_type]}` : 'Physical count'} onClose={onClose} width={1150}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {d && c && printing && (
        <>
          <div className="slip-toolbar no-print">
            <button className="btn btn-secondary btn-sm" onClick={() => setPrinting(false)}>
              ‹ Back
            </button>
            <div className="toolbar" style={{ margin: 0 }}>
              {!supplies && (
                <select value={groupBy} onChange={(e) => setGroupBy(e.target.value as GroupBy)} aria-label="Group by">
                  <option value="none">One list</option>
                  <option value="holder">By accountable person</option>
                  <option value="department">By department</option>
                </select>
              )}
              <label className="checkbox-line">
                <input type="checkbox" checked={discrepanciesOnly} onChange={(e) => setDiscrepanciesOnly(e.target.checked)} /> Discrepancies only
              </label>
              <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
                🖨 Print
              </button>
            </div>
          </div>
          <CountReport d={d} config={config.data} groupBy={groupBy} discrepanciesOnly={discrepanciesOnly} />
        </>
      )}
      {d && c && !printing && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={COUNT_STATUS} value={c.status} />
            </dd>
            <dt>Scope</dt>
            <dd>{c.department?.name ?? c.category?.name ?? (supplies ? 'All items' : 'All departments')}</dd>
            <dt>Count date</dt>
            <dd>
              {formatDate(c.count_date)}
              {c.started_at && <span className="muted"> · cut-off {formatDateTime(c.started_at)}</span>}
            </dd>
            <dt>Set up by</dt>
            <dd>{c.initiated_by.name}</dd>
            {c.completed_by && (
              <>
                <dt>Closed by</dt>
                <dd>
                  {c.completed_by.name} · {formatDateTime(c.completed_at)}
                </dd>
              </>
            )}
            {c.verified_by && (
              <>
                <dt>Verified by</dt>
                <dd>
                  {c.verified_by.name} · {formatDateTime(c.verified_at)}
                </dd>
              </>
            )}
            {c.purpose && (
              <>
                <dt>Purpose</dt>
                <dd>{c.purpose}</dd>
              </>
            )}
            {c.remarks && (
              <>
                <dt>Remarks</dt>
                <dd className="pre-line">{c.remarks}</dd>
              </>
            )}
          </dl>
          {c.status === 'completed' && c.verify_blocked && <div className="alert alert-info">{c.verify_blocked}</div>}
          <div className="workflow-actions count-actions">
            {c.can_edit && (
              <button className="btn btn-secondary" onClick={() => onEdit(c)}>
                Edit
              </button>
            )}
            {c.can_start && (
              <button className="btn btn-primary" onClick={() => setMode('start')}>
                Start counting
              </button>
            )}
            {c.can_complete && (
              <button className="btn btn-primary" onClick={() => setMode('complete')}>
                Complete count
              </button>
            )}
            {c.can_verify && (
              <button className="btn btn-primary" onClick={() => setMode('verify')}>
                Verify &amp; post
              </button>
            )}
            {c.can_return && (
              <button className="btn btn-secondary" onClick={() => setMode('return')}>
                Send back for recount
              </button>
            )}
            {c.status !== 'draft' && (
              <button className="btn btn-secondary" onClick={() => setPrinting(true)}>
                🖨 Report
              </button>
            )}
            {c.can_delete && (
              <button className="btn btn-danger" onClick={() => setMode('delete')}>
                Delete
              </button>
            )}
          </div>
          {mode === 'return' && (
            <form className="inline-form" onSubmit={(e) => { e.preventDefault(); act.mutate('return') }}>
              <input value={why} onChange={(e) => setWhy(e.target.value)} placeholder="What needs to be recounted?" aria-label="Reason" required autoFocus />
              <button className="btn btn-primary" type="submit" disabled={act.isPending}>
                Send back
              </button>
              <button className="btn btn-secondary" type="button" onClick={() => setMode(null)}>
                Cancel
              </button>
            </form>
          )}
          {c.status === 'draft' ? (
            <p className="empty">Lines appear once counting starts.</p>
          ) : supplies ? (
            <SuppliesSheet d={d} onSaved={updated} />
          ) : (
            <TagsSheet d={d} onSaved={updated} />
          )}
        </>
      )}
      {mode && mode !== 'return' && (
        <ConfirmDialog
          title={confirm[mode][0]}
          message={confirm[mode][1]}
          confirmLabel={confirm[mode][2]}
          busy={act.isPending || remove.isPending}
          onConfirm={() => (mode === 'delete' ? remove.mutate() : act.mutate(mode))}
          onCancel={() => setMode(null)}
        />
      )}
    </Modal>
  )
}

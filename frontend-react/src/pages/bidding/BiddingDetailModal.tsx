import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { bacApi, biddingApi, inventoryApi } from '../../api/endpoints'
import type { Bidding } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatDateTime, formatPeso, formatQty, today } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { AbstractOfBids } from './AbstractOfBids'
import { BiddersPanel } from './BiddersPanel'
import { BIDDING_STATUS, FLOW } from './labels'
import { ObserversPanel, ProtestsPanel, TimelinePanel } from './RecordsPanels'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'
import { ExcelButton } from '../../components/ExcelButton'

interface Props {
  biddingId: number
  onClose: () => void
  onEdit: (b: Bidding) => void
  onChanged: (message: string, closed?: boolean) => void
}

export function BiddingDetailModal({ biddingId, onClose, onEdit, onChanged }: Props) {
  const q = useQuery({ queryKey: ['bidding', biddingId], queryFn: () => biddingApi.get(biddingId) })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const [printing, setPrinting] = useState(false)
  const b = q.data
  return (
    <Modal title={b ? `${b.bidding_number} — ${b.title}` : 'Public bidding'} onClose={onClose} width={1100}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {b && printing && (
        <>
          <div className="slip-toolbar no-print">
            <button className="btn btn-secondary btn-sm" onClick={() => setPrinting(false)}>
              ‹ Back
            </button>
            <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
              🖨 Print
            </button>
          </div>
          <AbstractOfBids b={b} config={config.data} />
        </>
      )}
      {b && !printing && <Details b={b} onPrint={() => setPrinting(true)} onEdit={onEdit} onChanged={onChanged} />}
      {b && !printing && <AttachmentsPanel type="Bidding" id={b.id} label={b.bidding_number} docType="OTHER" />}
    </Modal>
  )
}

type Mode = null | 'preproc' | 'itb' | 'criteria' | 'award' | 'fail' | 'cancel' | 'delete'

function Details({ b, onPrint, onEdit, onChanged }: { b: Bidding; onPrint: () => void; onEdit: Props['onEdit']; onChanged: Props['onChanged'] }) {
  const { can } = useAuth()
  const [mode, setMode] = useState<Mode>(null)
  const [error, setError] = useState<string | null>(null)
  const done = (m: string, closed?: boolean) => {
    setMode(null)
    setError(null)
    onChanged(m, closed)
  }
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const open = useMutation({ mutationFn: () => biddingApi.openBids(b.id), onSuccess: () => done('Bids opened; record them as read.'), onError: fail })
  const noa = useMutation({ mutationFn: () => biddingApi.noa(b.id), onSuccess: (r) => done(`Notice of Award ${r.noa_number} issued.`), onError: fail })
  const ntp = useMutation({ mutationFn: () => biddingApi.ntp(b.id), onSuccess: (r) => done(`Notice to Proceed ${r.ntp_number} issued.`), onError: fail })
  const remove = useMutation({ mutationFn: () => biddingApi.remove(b.id), onSuccess: (r) => done(r.message, true), onError: fail })
  const stepAt = FLOW.indexOf(b.status as (typeof FLOW)[number])

  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <ol className="chain" style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
        {FLOW.map((s, i) => {
          const passed = stepAt > i || b.status === 'awarded'
          // The pre-procurement conference is optional at or below its threshold; show it as skipped, not done.
          const skipped = s === 'pre_procurement' && passed && !b.pre_proc_date
          return (
            <li key={s} className={skipped ? '' : passed ? 'step-approved' : stepAt === i ? 'step-current' : ''} style={{ display: 'flex', gap: '0.35rem', alignItems: 'center' }}>
              <span className="step-dot">{skipped ? '–' : passed ? '✓' : i + 1}</span>
              <span className={skipped ? 'small muted' : 'small'}>
                {BIDDING_STATUS[s][0]}
                {skipped && ' (skipped)'}
              </span>
            </li>
          )
        })}
        {(b.status === 'failed' || b.status === 'cancelled') && <Badge map={BIDDING_STATUS} value={b.status} />}
      </ol>
      <dl className="detail-grid">
        <dt>Purchase request</dt>
        <dd>
          {b.purchase_request?.name} · {b.department?.name}
        </dd>
        <dt>ABC</dt>
        <dd>{formatPeso(b.abc)}</dd>
        <dt>Evaluation</dt>
        <dd>{b.evaluation_method === 'mearb' ? `MEARB — price ${b.price_weight}%, rated ${b.technical_weight}%${b.min_quality_score != null ? `, minimum quality ${b.min_quality_score}` : ''}` : b.evaluation_method === 'marb' ? 'MARB — highest rated, price within the ABC' : 'Lowest calculated responsive bid'}</dd>
        <dt>Pre-procurement</dt>
        <dd>
          {b.pre_proc_date ? formatDateTime(b.pre_proc_date) : b.pre_proc_required ? <span className="badge st-pending">required for this ABC</span> : 'Not held'}
          {b.pre_proc_notes && <div className="small">{b.pre_proc_notes}</div>}
        </dd>
        {b.itb_posted_at && (
          <>
            <dt>Invitation to Bid</dt>
            <dd>
              {b.itb_reference && `${b.itb_reference} · `}posted {formatDate(b.itb_posted_at)} · bids due {formatDateTime(b.submission_deadline)} · opening {formatDateTime(b.bid_opening_date)}
              {b.itb_publication && <div className="small">{b.itb_publication}</div>}
            </dd>
          </>
        )}
        {b.awarded_supplier && (
          <>
            <dt>Award</dt>
            <dd>
              <strong>{b.awarded_supplier.name}</strong> · {formatPeso(b.awarded_amount)} · {formatDate(b.awarded_at)} · {b.bac_resolution?.name}
            </dd>
            <dt>Notices</dt>
            <dd>
              {b.noa_number ? `${b.noa_number} (${formatDate(b.noa_date)})` : 'Notice of Award not issued'} ·{' '}
              {b.ntp_number ? `${b.ntp_number} (${formatDate(b.ntp_date)})` : 'Notice to Proceed not issued'}
            </dd>
          </>
        )}
        {b.failure_reason && (
          <>
            <dt>Failure</dt>
            <dd>
              Failed bidding #{b.failed_count}: {b.failure_reason}
              {b.failed_count >= 2 && <div className="small">After two failed biddings, Negotiated Procurement may be used (RFQ / alternative modes).</div>}
            </dd>
          </>
        )}
        {b.remarks && (
          <>
            <dt>Remarks</dt>
            <dd>{b.remarks}</dd>
          </>
        )}
      </dl>

      <div className="table-wrap" style={{ marginTop: '1rem' }}>
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Item</th>
              <th className="num">Qty</th>
              <th className="num">ABC / unit</th>
              <th className="num">ABC</th>
            </tr>
          </thead>
          <tbody>
            {b.items?.map((l) => (
              <tr key={l.id}>
                <td>
                  {l.general_description}
                  {l.specifications && <div className="muted small">{l.specifications}</div>}
                </td>
                <td className="num">
                  {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
                </td>
                <td className="num">{formatPeso(l.abc_unit_cost)}</td>
                <td className="num">{formatPeso(l.abc_total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {b.evaluation_method !== 'price' && (
        <>
          <div className="section-header" style={{ marginTop: '1rem' }}>
            <h4>Rated criteria</h4>
            {b.can_edit && mode === null && (
              <button className="btn btn-outline btn-sm" onClick={() => setMode('criteria')}>
                Set criteria
              </button>
            )}
          </div>
          {mode === 'criteria' ? (
            <CriteriaForm b={b} onCancel={() => setMode(null)} onSaved={() => done('Criteria saved.')} onError={setError} />
          ) : (
            <p>{b.criteria?.length ? b.criteria.map((c) => `${c.name} ${c.weight}%`).join(' · ') : <span className="muted">None set — needed before the Invitation to Bid.</span>}</p>
          )}
        </>
      )}

      {!['draft', 'pre_procurement', 'invitation'].includes(b.status) && (
        <BiddersPanel b={b} onChanged={(m) => done(m)} onError={setError} />
      )}
      {!['draft', 'pre_procurement'].includes(b.status) && <ProtestsPanel b={b} manage={can('manage_bidding')} onChanged={(m) => done(m)} onError={setError} />}
      <ObserversPanel b={b} manage={can('manage_bidding')} onChanged={(m) => done(m)} onError={setError} />
      {b.itb_posted_at && <TimelinePanel b={b} />}

      {mode === 'preproc' && <PreProcForm b={b} onCancel={() => setMode(null)} onSaved={() => done('Pre-procurement conference recorded.')} onError={setError} />}
      {mode === 'itb' && <ItbForm b={b} onCancel={() => setMode(null)} onSaved={() => done('Invitation to Bid posted.')} onError={setError} />}
      {mode === 'award' && <AwardForm b={b} onCancel={() => setMode(null)} onSaved={(m) => done(m)} onError={setError} />}
      {(mode === 'fail' || mode === 'cancel') && <ReasonForm b={b} kind={mode} onCancel={() => setMode(null)} onSaved={(m) => done(m)} onError={setError} />}
      {mode === 'delete' && (
        <ConfirmDialog title="Delete bidding" message={`Delete the draft ${b.bidding_number}?`} confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} onCancel={() => setMode(null)} />
      )}

      {mode === null && (
        <div className="modal-footer">
          {!['draft', 'pre_procurement', 'invitation'].includes(b.status) && (
            <button className="btn btn-outline" onClick={onPrint}>
              🖨 Abstract of Bids
            </button>
          )}
          {!['draft', 'pre_procurement', 'invitation'].includes(b.status) && (
            <ExcelButton path={`/bidding/${b.id}/abstract/export`} name={`AbstractOfBids_${b.bidding_number}.xlsx`} label="Abstract (Excel)" />
          )}
          {b.can_delete && (
            <button className="btn btn-danger" onClick={() => setMode('delete')}>
              Delete
            </button>
          )}
          {b.can_cancel && b.status !== 'draft' && (
            <button className="btn btn-secondary" onClick={() => setMode('cancel')}>
              Cancel bidding
            </button>
          )}
          {b.can_fail && (
            <button className="btn btn-secondary" onClick={() => setMode('fail')}>
              Declare failure
            </button>
          )}
          {b.can_edit && (
            <button className="btn btn-secondary" onClick={() => onEdit(b)}>
              Edit
            </button>
          )}
          {b.can_schedule && (
            <>
              <button className="btn btn-outline" onClick={() => setMode('preproc')}>
                Pre-procurement conference
              </button>
              <button className="btn btn-primary" onClick={() => setMode('itb')}>
                Post Invitation to Bid
              </button>
            </>
          )}
          {b.status === 'invitation' && !b.deadline_passed && <span className="muted footer-note">Bids are opened after {formatDateTime(b.submission_deadline)}.</span>}
          {b.can_open && (
            <button className="btn btn-primary" onClick={() => open.mutate()} disabled={open.isPending}>
              Open bids
            </button>
          )}
          {b.status === 'evaluation' && b.open_protests > 0 && <span className="muted footer-note">Award waits for the pending protest.</span>}
          {b.can_award && (
            <button className="btn btn-primary" onClick={() => setMode('award')}>
              Award…
            </button>
          )}
          {b.can_noa && (
            <button className="btn btn-primary" onClick={() => noa.mutate()} disabled={noa.isPending}>
              Issue Notice of Award
            </button>
          )}
          {b.can_ntp && (
            <button className="btn btn-primary" onClick={() => ntp.mutate()} disabled={ntp.isPending}>
              Issue Notice to Proceed
            </button>
          )}
        </div>
      )}
    </>
  )
}

interface SubProps {
  b: Bidding
  onCancel: () => void
  onError: (m: string) => void
}

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

function PreProcForm({ b, onCancel, onSaved, onError }: SubProps & { onSaved: () => void }) {
  const [when, setWhen] = useState('')
  const [notes, setNotes] = useState(b.pre_proc_notes ?? '')
  const save = useMutation({ mutationFn: () => biddingApi.preProc(b.id, { pre_proc_date: when, pre_proc_notes: notes || null }), onSuccess: onSaved, onError: (e) => onError(msg(e, 'Could not save.')) })
  return (
    <div className="reject-box">
      <div className="form-grid">
        <div className="field">
          <label htmlFor="pp-when">Date and time *</label>
          <input id="pp-when" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pp-notes">Notes</label>
          <input id="pp-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={!when || save.isPending} onClick={() => save.mutate()}>
          Save
        </button>
      </div>
    </div>
  )
}

function ItbForm({ b, onCancel, onSaved, onError }: SubProps & { onSaved: () => void }) {
  const [ref, setRef] = useState(b.itb_reference ?? '')
  const [posted, setPosted] = useState(today())
  const [where, setWhere] = useState('')
  const [deadline, setDeadline] = useState('')
  const [opening, setOpening] = useState('')
  const save = useMutation({
    mutationFn: () => biddingApi.invitation(b.id, { itb_reference: ref || null, itb_posted_at: posted, itb_publication: where || null, submission_deadline: deadline, bid_opening_date: opening || null }),
    onSuccess: onSaved,
    onError: (e) => onError(msg(e, 'Could not post the invitation.')),
  })
  return (
    <div className="reject-box">
      <div className="form-grid three">
        <div className="field">
          <label htmlFor="itb-ref">ITB reference</label>
          <input id="itb-ref" value={ref} onChange={(e) => setRef(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="itb-posted">Posted on</label>
          <input id="itb-posted" type="date" value={posted} max={today()} onChange={(e) => setPosted(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="itb-where">Posted at</label>
          <input id="itb-where" value={where} onChange={(e) => setWhere(e.target.value)} placeholder="PhilGEPS, website, bulletin board" />
        </div>
        <div className="field">
          <label htmlFor="itb-dl">Deadline for bids *</label>
          <input id="itb-dl" type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="itb-open">Bid opening</label>
          <input id="itb-open" type="datetime-local" value={opening} min={deadline} onChange={(e) => setOpening(e.target.value)} />
          <span className="hint">Defaults to the deadline.</span>
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={!deadline || save.isPending} onClick={() => save.mutate()}>
          Post invitation
        </button>
      </div>
    </div>
  )
}

function CriteriaForm({ b, onCancel, onSaved, onError }: SubProps & { onSaved: () => void }) {
  const [rows, setRows] = useState(() => (b.criteria?.length ? b.criteria.map((c) => ({ name: c.name, weight: String(c.weight) })) : [{ name: '', weight: '' }]))
  const total = rows.reduce((s, r) => s + (Number(r.weight) || 0), 0)
  const save = useMutation({
    mutationFn: () => biddingApi.criteria(b.id, rows.filter((r) => r.name.trim()).map((r) => ({ name: r.name, weight: Number(r.weight) }))),
    onSuccess: onSaved,
    onError: (e) => onError(msg(e, 'Could not save the criteria.')),
  })
  return (
    <div className="reject-box">
      {rows.map((r, i) => (
        <div key={i} className="toolbar" style={{ marginBottom: '0.35rem' }}>
          <input aria-label={`Criterion ${i + 1}`} placeholder="Criterion" value={r.name} onChange={(e) => setRows((rs) => rs.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
          <input aria-label={`Weight of criterion ${i + 1}`} className="qty-input" type="number" min={0} max={100} placeholder="%" value={r.weight} onChange={(e) => setRows((rs) => rs.map((x, j) => (j === i ? { ...x, weight: e.target.value } : x)))} />
          <button className="icon-btn delete" title="Remove criterion" onClick={() => setRows((rs) => rs.filter((_, j) => j !== i))}>
            ×
          </button>
        </div>
      ))}
      <button className="btn btn-outline btn-sm" onClick={() => setRows((rs) => [...rs, { name: '', weight: '' }])}>
        + Criterion
      </button>
      <div className="modal-footer">
        <span className={total === 100 ? 'muted footer-note' : 'footer-note'}>Total {total}% (must be 100)</span>
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={total !== 100 || save.isPending} onClick={() => save.mutate()}>
          Save criteria
        </button>
      </div>
    </div>
  )
}

function AwardForm({ b, onCancel, onSaved, onError }: SubProps & { onSaved: (m: string) => void }) {
  const res = useQuery({
    queryKey: ['bac-resolutions', 'award', b.purchase_request?.id],
    queryFn: () => bacApi.resolutions({ page: 1, status: 'approved', purchase_request_id: b.purchase_request?.id }),
  })
  const [resolution, setResolution] = useState('')
  const [rec, setRec] = useState(b.contract_recommendation ?? '')
  const winner = b.bidders?.find((x) => x.id === b.recommended_bidder_id)
  const usable = res.data?.data.filter((x) => ['resolution_to_award', 'award', 'recommendation'].includes(x.resolution_type) && x.used_by.length === 0) ?? []
  const save = useMutation({
    mutationFn: () => biddingApi.award(b.id, { bidder_id: b.recommended_bidder_id!, bac_resolution_id: Number(resolution), contract_recommendation: rec || undefined }),
    onSuccess: (r) => onSaved(`${r.bidding_number} awarded to ${r.awarded_supplier?.name}.`),
    onError: (e) => onError(msg(e, 'Could not award.')),
  })
  return (
    <div className="reject-box">
      <p>
        Award to <strong>{winner?.supplier.name}</strong> at {formatPeso(winner?.bid_amount)} — passed post-qualification as the{' '}
        {b.evaluation_method !== 'price' ? 'highest-rated' : 'lowest calculated'} responsive bid.
      </p>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="bw-res">BAC resolution *</label>
          <select id="bw-res" value={resolution} onChange={(e) => setResolution(e.target.value)}>
            <option value="">{usable.length ? 'Choose' : 'No approved award resolution for this PR'}</option>
            {usable.map((x) => (
              <option key={x.id} value={x.id}>
                {x.resolution_number} — {x.title}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="bw-rec">Contract recommendation</label>
          <input id="bw-rec" value={rec} onChange={(e) => setRec(e.target.value)} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={!resolution || save.isPending} onClick={() => save.mutate()}>
          Award
        </button>
      </div>
    </div>
  )
}

function ReasonForm({ b, kind, onCancel, onSaved, onError }: SubProps & { kind: 'fail' | 'cancel'; onSaved: (m: string) => void }) {
  const [why, setWhy] = useState('')
  const save = useMutation({
    mutationFn: async () => (kind === 'fail' ? (await biddingApi.declareFailed(b.id, why)).message : (await biddingApi.cancel(b.id, why || undefined), `${b.bidding_number} cancelled.`)),
    onSuccess: onSaved,
    onError: (e) => onError(msg(e, 'Could not save.')),
  })
  return (
    <div className="reject-box">
      <div className="field">
        <label htmlFor="bd-reason">{kind === 'fail' ? 'Ground for declaring a failure of bidding *' : 'Reason for cancelling'}</label>
        <input id="bd-reason" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus placeholder={kind === 'fail' ? 'e.g. No bids received; all bids failed post-qualification' : ''} />
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Back
        </button>
        <button className="btn btn-danger" disabled={(kind === 'fail' || b.status !== 'draft') && !why.trim()} onClick={() => save.mutate()}>
          {kind === 'fail' ? 'Declare failure' : 'Cancel bidding'}
        </button>
      </div>
    </div>
  )
}

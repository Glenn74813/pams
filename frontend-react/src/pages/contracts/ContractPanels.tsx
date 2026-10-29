import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { contractApi } from '../../api/endpoints'
import type { Contract } from '../../api/types'
import { formatDate, formatPeso, today } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { ITEM_STATUS, PERFORMANCE_FORMS, SECURITY_TYPES } from './labels'

interface Props {
  c: Contract
  manage: boolean
  edit: boolean
  onChanged: (message: string) => void
  onError: (message: string) => void
}

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

export function SecuritiesPanel({ c, manage, edit, onChanged, onError }: Props) {
  const [adding, setAdding] = useState(false)
  const [type, setType] = useState('Performance Security')
  const [form, setForm] = useState(PERFORMANCE_FORMS[1][0])
  const [amount, setAmount] = useState('')
  const [ref, setRef] = useState('')
  const [validity, setValidity] = useState(c.end_date ?? '')
  const [forfeiting, setForfeiting] = useState<number | null>(null)
  const [why, setWhy] = useState('')
  const rate = PERFORMANCE_FORMS.find(([f]) => f === form)
  const need = rate ? c.original_amount * (c.contract_type === 'Infrastructure' ? rate[2] : rate[1]) : 0
  const add = useMutation({
    mutationFn: () => contractApi.addSecurity(c.id, { security_type: type, form: type === 'Performance Security' ? form : form || null, amount: Number(amount), reference_number: ref || null, validity_date: validity || null, posted_date: today() }),
    onSuccess: () => {
      setAdding(false)
      setAmount('')
      onChanged('Security recorded.')
    },
    onError: (e) => onError(msg(e, 'Could not record the security.')),
  })
  const set = useMutation({
    mutationFn: (v: { id: number; status: 'returned' | 'forfeited'; remarks?: string }) => contractApi.setSecurity(v.id, v.status, v.remarks),
    onSuccess: (_, v) => {
      setForfeiting(null)
      onChanged(`Security ${v.status}.`)
    },
    onError: (e) => onError(msg(e, 'Could not update the security.')),
  })
  return (
    <>
      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>
          Securities{' '}
          {c.contract_type !== 'Framework Agreement' && (c.status === 'draft' || c.status === 'active') && (c.performance_secured ? <span className="badge st-ok">performance secured</span> : <span className="badge st-pending">no sufficient performance security</span>)}
        </h4>
        {manage && (c.status === 'draft' || c.status === 'active') && !adding && (
          <button className="btn btn-outline btn-sm" onClick={() => setAdding(true)}>
            + Record security
          </button>
        )}
      </div>
      {adding && (
        <div className="reject-box">
          <div className="form-grid three">
            <div className="field">
              <label htmlFor="sc-type">Kind</label>
              <select id="sc-type" value={type} onChange={(e) => setType(e.target.value)}>
                {SECURITY_TYPES.map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="sc-form">Form</label>
              <select id="sc-form" value={form} onChange={(e) => setForm(e.target.value)}>
                {PERFORMANCE_FORMS.map(([f, g, i]) => (
                  <option key={f} value={f}>
                    {f} ({Math.round((c.contract_type === 'Infrastructure' ? i : g) * 100)}%)
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="sc-amt">Amount (₱) *</label>
              <input id="sc-amt" type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
              {type === 'Performance Security' && <span className="hint">At least {formatPeso(need)}</span>}
            </div>
            <div className="field">
              <label htmlFor="sc-ref">Reference no.</label>
              <input id="sc-ref" value={ref} onChange={(e) => setRef(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="sc-val">Valid until</label>
              <input id="sc-val" type="date" value={validity} onChange={(e) => setValidity(e.target.value)} />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setAdding(false)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={!amount || add.isPending} onClick={() => add.mutate()}>
              Record
            </button>
          </div>
        </div>
      )}
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Kind</th>
              <th>Form / ref.</th>
              <th className="num">Amount</th>
              <th>Valid until</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {c.securities?.map((s) => (
              <tr key={s.id}>
                <td>{s.security_type}</td>
                <td>
                  {s.form ?? '—'} {s.reference_number && <span className="muted small">· {s.reference_number}</span>}
                </td>
                <td className="num">
                  {formatPeso(s.amount)}
                  {s.required != null && !s.sufficient && <div className="small short">needs {formatPeso(s.required)}</div>}
                </td>
                <td>{formatDate(s.validity_date)}</td>
                <td>
                  <Badge map={ITEM_STATUS} value={s.status} />
                  {s.remarks && <div className="muted small">{s.remarks}</div>}
                </td>
                <td>
                  {edit && s.status === 'posted' && (
                    <div className="workflow-actions">
                      <button className="btn btn-outline btn-sm" onClick={() => set.mutate({ id: s.id, status: 'returned' })}>
                        Return
                      </button>
                      <button className="btn btn-outline btn-sm" onClick={() => { setWhy(''); setForfeiting(s.id) }}>
                        Forfeit
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {c.securities?.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  None posted.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {forfeiting && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="sc-why">Ground for forfeiture *</label>
            <input id="sc-why" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setForfeiting(null)}>
              Cancel
            </button>
            <button className="btn btn-danger" disabled={!why.trim()} onClick={() => set.mutate({ id: forfeiting, status: 'forfeited', remarks: why })}>
              Forfeit
            </button>
          </div>
        </div>
      )}
    </>
  )
}

export function AmendmentsPanel({ c, manage, onChanged, onError }: Props) {
  const [adding, setAdding] = useState(false)
  const [title, setTitle] = useState('')
  const [change, setChange] = useState('')
  const [end, setEnd] = useState('')
  const add = useMutation({
    mutationFn: () => contractApi.addAmendment(c.id, { title, amount_change: change === '' ? 0 : Number(change), new_end_date: end || null }),
    onSuccess: () => {
      setAdding(false)
      setTitle('')
      setChange('')
      setEnd('')
      onChanged('Amendment drafted.')
    },
    onError: (e) => onError(msg(e, 'Could not draft the amendment.')),
  })
  const approve = useMutation({ mutationFn: (id: number) => contractApi.approveAmendment(id), onSuccess: () => onChanged('Amendment approved.'), onError: (e) => onError(msg(e, 'Could not approve.')) })
  const remove = useMutation({ mutationFn: (id: number) => contractApi.removeAmendment(id), onSuccess: () => onChanged('Amendment deleted.'), onError: (e) => onError(msg(e, 'Could not delete.')) })
  const room = c.original_amount * 0.1 - Math.abs(c.amount - c.original_amount)
  return (
    <>
      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>
          Amendments <span className="muted small">variation {c.variation_percent}% of the original {formatPeso(c.original_amount)} (limit 10%)</span>
        </h4>
        {manage && c.status === 'active' && !adding && (
          <button className="btn btn-outline btn-sm" onClick={() => setAdding(true)}>
            + Amendment
          </button>
        )}
      </div>
      {adding && (
        <div className="reject-box">
          <div className="form-grid three">
            <div className="field span-3">
              <label htmlFor="am-title">What changes *</label>
              <input id="am-title" value={title} onChange={(e) => setTitle(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="am-chg">Price change (₱)</label>
              <input id="am-chg" type="number" step="0.01" value={change} onChange={(e) => setChange(e.target.value)} />
              <span className="hint">Up to ±{formatPeso(Math.max(0, room))} left</span>
            </div>
            <div className="field">
              <label htmlFor="am-end">New end date</label>
              <input id="am-end" type="date" value={end} onChange={(e) => setEnd(e.target.value)} />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setAdding(false)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={!title.trim() || add.isPending} onClick={() => add.mutate()}>
              Draft amendment
            </button>
          </div>
        </div>
      )}
      <ul className="chain">
        {c.amendments?.map((a) => (
          <li key={a.id} className={a.status === 'approved' ? 'step-approved' : ''}>
            <span className="step-dot">{a.amendment_number}</span>
            <span>
              <strong>{a.title}</strong> {a.amount_change !== 0 && <span>{a.amount_change > 0 ? '+' : ''}{formatPeso(a.amount_change)}</span>}{' '}
              {a.new_end_date && <span className="muted">· ends {formatDate(a.new_end_date)}</span>} <Badge map={ITEM_STATUS} value={a.status} />
              <div className="muted small">
                by {a.preparer?.name}
                {a.approver && ` · approved by ${a.approver.name} ${formatDate(a.approved_at)}`}
              </div>
              {a.status === 'draft' && (
                <div className="workflow-actions" style={{ marginTop: '0.35rem' }}>
                  {a.can_approve && (
                    <button className="btn btn-primary btn-sm" onClick={() => approve.mutate(a.id)}>
                      Approve
                    </button>
                  )}
                  {manage && (
                    <button className="btn btn-outline btn-sm" onClick={() => remove.mutate(a.id)}>
                      Delete
                    </button>
                  )}
                </div>
              )}
            </span>
          </li>
        ))}
        {c.amendments?.length === 0 && <li className="muted">None.</li>}
      </ul>
    </>
  )
}

export function DeliveryPanel({ c, manage, edit, onChanged, onError }: Props) {
  const [asking, setAsking] = useState(false)
  const [days, setDays] = useState('')
  const [reason, setReason] = useState('')
  const [asked, setAsked] = useState(today())
  const [deciding, setDeciding] = useState<{ id: number; days: string; remarks: string } | null>(null)
  const add = useMutation({
    mutationFn: () => contractApi.addExtension(c.id, { requested_days: Number(days), reason, requested_date: asked }),
    onSuccess: () => {
      setAsking(false)
      setDays('')
      setReason('')
      onChanged('Extension request recorded.')
    },
    onError: (e) => onError(msg(e, 'Could not record the request.')),
  })
  const approve = useMutation({ mutationFn: () => contractApi.approveExtension(deciding!.id, Number(deciding!.days), deciding!.remarks || undefined), onSuccess: () => { setDeciding(null); onChanged('Extension granted; the deadline moved.') }, onError: (e) => onError(msg(e, 'Could not grant.')) })
  const deny = useMutation({ mutationFn: () => contractApi.denyExtension(deciding!.id, deciding!.remarks), onSuccess: () => { setDeciding(null); onChanged('Extension denied.') }, onError: (e) => onError(msg(e, 'Could not deny.')) })
  const assess = useMutation({ mutationFn: () => contractApi.assessLd(c.id, {}), onSuccess: (r) => onChanged(`Liquidated damages recorded: ${formatPeso(r.ld_amount)}.`), onError: (e) => onError(msg(e, 'Could not assess.')) })
  const d = c.delivery
  const ld = c.ld_estimate
  return (
    <>
      <h4 style={{ margin: '1rem 0 0.5rem' }}>Delivery &amp; liquidated damages</h4>
      <dl className="detail-grid">
        <dt>Deadline</dt>
        <dd>
          {formatDate(c.delivery_deadline)} {c.approved_extension_days > 0 && <span className="muted">(incl. {c.approved_extension_days} day(s) extension)</span>}
        </dd>
        {d && (
          <>
            <dt>Delivered</dt>
            <dd>
              {formatPeso(d.delivered_value)} of {formatPeso(d.order_value)} {d.complete ? <span className="badge st-ok">complete</span> : <span className="badge st-pending">{formatPeso(d.undelivered_value)} to go</span>}
            </dd>
          </>
        )}
        {ld && (
          <>
            <dt>Liquidated damages</dt>
            <dd>
              {ld.days_delayed === 0 ? (
                <span className="muted">On time as of {formatDate(ld.as_of)}</span>
              ) : (
                <>
                  {ld.days_delayed} day(s) late × 0.1% × {formatPeso(ld.basis)} = <strong>{formatPeso(ld.amount)}</strong>
                  {ld.capped && <span className="badge st-bad">10% cap reached — may terminate</span>}
                </>
              )}
              {c.ld_as_of_date && <div className="muted small">Recorded: {formatPeso(c.ld_amount)} as of {formatDate(c.ld_as_of_date)}</div>}
              {edit && c.status === 'active' && ld.days_delayed > 0 && (
                <div>
                  <button className="btn btn-outline btn-sm" style={{ marginTop: '0.35rem' }} onClick={() => assess.mutate()} disabled={assess.isPending}>
                    Record assessment
                  </button>
                </div>
              )}
            </dd>
          </>
        )}
      </dl>
      <div className="section-header">
        <h4>Extension requests</h4>
        {manage && c.status === 'active' && !asking && (
          <button className="btn btn-outline btn-sm" onClick={() => setAsking(true)}>
            + Record request
          </button>
        )}
      </div>
      {asking && (
        <div className="reject-box">
          <div className="form-grid three">
            <div className="field">
              <label htmlFor="ex-days">Days asked *</label>
              <input id="ex-days" type="number" min={1} value={days} onChange={(e) => setDays(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="ex-date">Requested on</label>
              <input id="ex-date" type="date" value={asked} max={today()} onChange={(e) => setAsked(e.target.value)} />
            </div>
            <div className="field span-3">
              <label htmlFor="ex-why">Ground *</label>
              <input id="ex-why" value={reason} onChange={(e) => setReason(e.target.value)} />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setAsking(false)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={!days || !reason.trim() || add.isPending} onClick={() => add.mutate()}>
              Record
            </button>
          </div>
        </div>
      )}
      <ul className="chain">
        {c.extensions?.map((e) => (
          <li key={e.id} className={e.status === 'approved' ? 'step-approved' : ''}>
            <span className="step-dot">{e.status === 'approved' ? '✓' : e.status === 'denied' ? '✕' : '…'}</span>
            <span>
              <strong>{e.requested_days} day(s)</strong> asked {formatDate(e.requested_date)} — {e.reason} <Badge map={ITEM_STATUS} value={e.status} />
              {e.approved_days != null && <span> {e.approved_days} granted</span>}
              {e.remarks && <div className="muted small">{e.remarks}</div>}
              {edit && e.status === 'pending' && (
                <div className="workflow-actions" style={{ marginTop: '0.35rem' }}>
                  <button className="btn btn-outline btn-sm" onClick={() => setDeciding({ id: e.id, days: String(e.requested_days), remarks: '' })}>
                    Decide…
                  </button>
                </div>
              )}
            </span>
          </li>
        ))}
        {c.extensions?.length === 0 && <li className="muted">None.</li>}
      </ul>
      {deciding && (
        <div className="reject-box">
          <div className="form-grid three">
            <div className="field">
              <label htmlFor="dx-days">Days to grant</label>
              <input id="dx-days" type="number" min={1} value={deciding.days} onChange={(e) => setDeciding({ ...deciding, days: e.target.value })} />
            </div>
            <div className="field span-2">
              <label htmlFor="dx-rem">Remarks (required to deny)</label>
              <input id="dx-rem" value={deciding.remarks} onChange={(e) => setDeciding({ ...deciding, remarks: e.target.value })} />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setDeciding(null)}>
              Cancel
            </button>
            <button className="btn btn-danger" disabled={!deciding.remarks.trim() || deny.isPending} onClick={() => deny.mutate()}>
              Deny
            </button>
            <button className="btn btn-primary" disabled={!deciding.days || approve.isPending} onClick={() => approve.mutate()}>
              Grant
            </button>
          </div>
        </div>
      )}
    </>
  )
}

export function CalloffsPanel({ c, manage, onChanged, onError }: Props) {
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState('')
  const add = useMutation({
    mutationFn: () => contractApi.addCalloff(c.id, { title, amount: Number(amount) }),
    onSuccess: () => {
      setTitle('')
      setAmount('')
      onChanged('Call-off recorded.')
    },
    onError: (e) => onError(msg(e, 'Could not record the call-off.')),
  })
  const set = useMutation({ mutationFn: (v: { id: number; status: string }) => contractApi.setCalloff(v.id, v.status), onSuccess: () => onChanged('Call-off updated.'), onError: (e) => onError(msg(e, 'Could not update.')) })
  const next: Record<string, [string, string][]> = { draft: [['issued', 'Issue'], ['cancelled', 'Cancel']], issued: [['completed', 'Complete'], ['cancelled', 'Cancel']] }
  return (
    <>
      <h4 style={{ margin: '1rem 0 0.5rem' }}>
        Call-offs <span className="muted small">{formatPeso(c.calloffs_total)} of {formatPeso(c.amount)} used</span>
      </h4>
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>No.</th>
              <th>Title</th>
              <th>Date</th>
              <th className="num">Amount</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {c.calloffs?.map((f) => (
              <tr key={f.id}>
                <td className="mono">{f.calloff_number}</td>
                <td>{f.title}</td>
                <td>{formatDate(f.calloff_date)}</td>
                <td className="num">{formatPeso(f.amount)}</td>
                <td>
                  <Badge map={ITEM_STATUS} value={f.status} />
                </td>
                <td>
                  {manage && (
                    <div className="workflow-actions">
                      {(next[f.status] ?? []).map(([s, l]) => (
                        <button key={s} className="btn btn-outline btn-sm" onClick={() => set.mutate({ id: f.id, status: s })}>
                          {l}
                        </button>
                      ))}
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {c.calloffs?.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  None yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {manage && c.status === 'active' && (
        <div className="toolbar">
          <input aria-label="Call-off title" placeholder="What is called off" value={title} onChange={(e) => setTitle(e.target.value)} />
          <input aria-label="Call-off amount" className="qty-input" style={{ width: '9rem' }} type="number" min={0} step="0.01" placeholder="₱" value={amount} onChange={(e) => setAmount(e.target.value)} />
          <button className="btn btn-outline btn-sm" disabled={!title.trim() || !amount || add.isPending} onClick={() => add.mutate()}>
            + Call-off
          </button>
        </div>
      )}
    </>
  )
}

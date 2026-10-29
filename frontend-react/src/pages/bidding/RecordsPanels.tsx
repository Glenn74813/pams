import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { biddingApi } from '../../api/endpoints'
import type { BidProtest, Bidding } from '../../api/types'
import { formatDate, today } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { PROTEST_STATUS } from './labels'

interface Props {
  b: Bidding
  manage: boolean
  onChanged: (message: string) => void
  onError: (message: string) => void
}

export function ProtestsPanel({ b, manage, onChanged, onError }: Props) {
  const [adding, setAdding] = useState(false)
  const [type, setType] = useState('Request for Reconsideration')
  const [by, setBy] = useState('')
  const [grounds, setGrounds] = useState('')
  const [resolving, setResolving] = useState<{ p: BidProtest; status: string } | null>(null)
  const [resolution, setResolution] = useState('')
  const fail = (err: unknown) => onError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const add = useMutation({
    mutationFn: () => biddingApi.addProtest(b.id, { protest_type: type, supplier_id: by ? Number(by) : null, grounds, filed_date: today() }),
    onSuccess: () => {
      setAdding(false)
      setGrounds('')
      onChanged(`${type} recorded; the award waits until it is resolved.`)
    },
    onError: fail,
  })
  const move = useMutation({
    mutationFn: (v: { p: BidProtest; status: string; resolution?: string }) => biddingApi.updateProtest(v.p.id, { status: v.status, resolution: v.resolution }),
    onSuccess: (_, v) => {
      setResolving(null)
      setResolution('')
      onChanged(`${v.p.protest_type}: ${PROTEST_STATUS[v.status][0].toLowerCase()}.`)
    },
    onError: fail,
  })
  const canFile = manage && !['draft', 'pre_procurement', 'cancelled'].includes(b.status)
  return (
    <>
      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>Reconsideration &amp; protests {b.open_protests > 0 && <span className="badge st-pending">{b.open_protests} pending</span>}</h4>
        {canFile && !adding && (
          <button className="btn btn-outline btn-sm" onClick={() => setAdding(true)}>
            + Record filing
          </button>
        )}
      </div>
      {adding && (
        <div className="reject-box">
          <div className="form-grid">
            <div className="field">
              <label htmlFor="pt-type">Type</label>
              <select id="pt-type" value={type} onChange={(e) => setType(e.target.value)}>
                <option>Request for Reconsideration</option>
                <option>Protest</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="pt-by">Filed by</label>
              <select id="pt-by" value={by} onChange={(e) => setBy(e.target.value)}>
                <option value="">Choose the bidder</option>
                {b.bidders?.map((x) => (
                  <option key={x.supplier.id} value={x.supplier.id}>
                    {x.supplier.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field span-2">
              <label htmlFor="pt-grounds">Grounds *</label>
              <textarea id="pt-grounds" rows={2} value={grounds} onChange={(e) => setGrounds(e.target.value)} />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setAdding(false)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={!grounds.trim() || add.isPending} onClick={() => add.mutate()}>
              Record
            </button>
          </div>
        </div>
      )}
      {(b.protests ?? []).length === 0 ? (
        <p className="muted">None filed.</p>
      ) : (
        <ul className="chain">
          {b.protests?.map((p) => (
            <li key={p.id}>
              <span className="step-dot">{p.status === 'upheld' || p.status === 'denied' ? '✓' : '!'}</span>
              <span>
                <strong>{p.protest_type}</strong> {p.supplier?.name && `by ${p.supplier.name}`} · {formatDate(p.filed_date)} <Badge map={PROTEST_STATUS} value={p.status} />
                <div className="small">{p.grounds}</div>
                {p.resolution && (
                  <div className="small muted">
                    Resolved {formatDate(p.resolved_date)}: {p.resolution}
                  </div>
                )}
                {manage && (p.status === 'filed' || p.status === 'under_review') && (
                  <div className="workflow-actions" style={{ marginTop: '0.35rem' }}>
                    {p.status === 'filed' && (
                      <button className="btn btn-outline btn-sm" onClick={() => move.mutate({ p, status: 'under_review' })}>
                        Take up
                      </button>
                    )}
                    <button className="btn btn-outline btn-sm" onClick={() => setResolving({ p, status: 'denied' })}>
                      Deny
                    </button>
                    <button className="btn btn-outline btn-sm" onClick={() => setResolving({ p, status: 'upheld' })}>
                      Uphold
                    </button>
                    <button className="btn btn-outline btn-sm" onClick={() => move.mutate({ p, status: 'withdrawn' })}>
                      Withdrawn
                    </button>
                  </div>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
      {resolving && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="pt-res">BAC resolution of the {resolving.p.protest_type.toLowerCase()} ({resolving.status}) *</label>
            <textarea id="pt-res" rows={2} value={resolution} onChange={(e) => setResolution(e.target.value)} autoFocus />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setResolving(null)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={!resolution.trim() || move.isPending} onClick={() => move.mutate({ ...resolving, resolution })}>
              Save
            </button>
          </div>
        </div>
      )}
    </>
  )
}

export function ObserversPanel({ b, manage, onChanged, onError }: Props) {
  const [name, setName] = useState('')
  const [org, setOrg] = useState('')
  const [type, setType] = useState('COA')
  const fail = (err: unknown) => onError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const add = useMutation({
    mutationFn: () => biddingApi.addObserver(b.id, { name, organization: org || null, observer_type: type, invited_date: today() }),
    onSuccess: () => {
      setName('')
      setOrg('')
      onChanged('Observer invited.')
    },
    onError: fail,
  })
  const attend = useMutation({
    mutationFn: (o: NonNullable<Bidding['observers']>[number]) => biddingApi.updateObserver(o.id, { name: o.name, organization: o.organization, observer_type: o.observer_type, invited: o.invited, invited_date: o.invited_date, attended: !o.attended }),
    onSuccess: () => onChanged('Attendance updated.'),
    onError: fail,
  })
  const open = !['awarded', 'failed', 'cancelled'].includes(b.status)
  return (
    <>
      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>Observers</h4>
        {b.observers_invited < 2 && open && <span className="badge st-pending">invite at least 2</span>}
      </div>
      <ul style={{ paddingLeft: b.observers?.length ? undefined : 0, listStyle: b.observers?.length ? undefined : 'none' }}>
        {b.observers?.map((o) => (
          <li key={o.id}>
            {o.name} <span className="muted">· {o.observer_type}{o.organization ? `, ${o.organization}` : ''}</span>{' '}
            {manage && open ? (
              <label className="checkbox-line">
                <input type="checkbox" checked={o.attended} onChange={() => attend.mutate(o)} /> attended
              </label>
            ) : (
              o.attended && <span className="badge st-ok">attended</span>
            )}
          </li>
        ))}
        {b.observers?.length === 0 && <li className="muted">None invited.</li>}
      </ul>
      {manage && open && (
        <div className="toolbar">
          <input aria-label="Observer name" placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} />
          <input aria-label="Organization" placeholder="Organization" value={org} onChange={(e) => setOrg(e.target.value)} />
          <select aria-label="Observer type" value={type} onChange={(e) => setType(e.target.value)}>
            {['COA', 'CSO', 'Professional Organization', 'Other'].map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
          <button className="btn btn-outline btn-sm" disabled={!name.trim() || add.isPending} onClick={() => add.mutate()}>
            Invite
          </button>
        </div>
      )}
    </>
  )
}

export function TimelinePanel({ b }: { b: Bidding }) {
  return (
    <>
      <h4 style={{ margin: '1rem 0 0.5rem' }}>Timeline (maximum period per stage)</h4>
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Stage</th>
              <th>From</th>
              <th>To</th>
              <th className="num">Days</th>
              <th className="num">Max</th>
            </tr>
          </thead>
          <tbody>
            {b.timeline?.map((t) => (
              <tr key={t.key}>
                <td>{t.label}</td>
                <td>{formatDate(t.from)}</td>
                <td>{t.to ? formatDate(t.to) : t.pending ? <span className="muted">running</span> : '—'}</td>
                <td className="num">
                  {t.elapsed_days ?? '—'} {t.breached && <span className="badge st-bad">over</span>}
                </td>
                <td className="num">{t.max_days}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

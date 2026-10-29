import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { apcpiApi } from '../../api/endpoints'
import type { ApcpiAssessment, ApcpiScore } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { formatDateTime } from '../../lib/format'
import { thisYear } from '../procurement/labels'
import '../inventory/inventory.css'
import '../issuance/issuance.css'

const MATURITY: Record<string, string> = { Advanced: 'st-ok', Maturing: 'st-partial', Beginning: 'st-pending', Negligible: 'st-bad' }

export function ApcpiPage() {
  const { can } = useAuth()
  const toast = useToast()
  const qc = useQueryClient()
  const manage = can('manage_bac_meetings', 'approve_procurement')
  const [openId, setOpenId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [fy, setFy] = useState(thisYear())
  const list = useQuery({ queryKey: ['apcpi'], queryFn: apcpiApi.list })
  const fail = (e: unknown) => toast(e instanceof ApiError ? e.message : 'Something went wrong.', 'error')
  const create = useMutation({
    mutationFn: () => apcpiApi.create(fy),
    onSuccess: (a) => {
      setCreating(false)
      void qc.invalidateQueries({ queryKey: ['apcpi'] })
      setOpenId(a.id)
    },
    onError: fail,
  })
  const remove = useMutation({
    mutationFn: (id: number) => apcpiApi.remove(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['apcpi'] })
      toast('Assessment deleted.')
    },
    onError: fail,
  })

  if (openId !== null) return <Sheet id={openId} manage={manage} onBack={() => (setOpenId(null), void qc.invalidateQueries({ queryKey: ['apcpi'] }))} />

  return (
    <div className="page">
      <div className="section-header">
        <h1 className="page-title">APCPI Self-Assessment</h1>
        {manage && (
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            + New Assessment
          </button>
        )}
      </div>
      <p className="muted small">
        Agency Procurement Compliance and Performance Indicators (GPPB) — yearly self-assessment scored 0–3 per indicator across four pillars.
      </p>
      {list.isLoading && <div className="page-loading">Loading…</div>}
      {list.data && list.data.data.length === 0 && <p className="muted">No APCPI assessments yet.</p>}
      {list.data && list.data.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Fiscal Year</th>
                <th>Conducted by</th>
                <th className="num">Overall</th>
                <th>Maturity</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {list.data.data.map((a) => (
                <tr key={a.id}>
                  <td>FY {a.fiscal_year}</td>
                  <td>{a.conducted_by ?? '—'}</td>
                  <td className="num">{Number(a.overall).toFixed(2)}</td>
                  <td>
                    <span className={'badge ' + (MATURITY[a.maturity] ?? 'badge-muted')}>{a.maturity}</span>
                  </td>
                  <td>
                    <span className={'badge ' + (a.status === 'finalized' ? 'st-ok' : 'badge-muted')}>{a.status === 'finalized' ? 'Finalized' : 'Draft'}</span>
                  </td>
                  <td className="nowrap">
                    <button className="btn btn-outline btn-sm" onClick={() => setOpenId(a.id)}>
                      Open
                    </button>{' '}
                    {manage && a.status !== 'finalized' && (
                      <button className="btn btn-outline btn-sm" onClick={() => window.confirm(`Delete the FY ${a.fiscal_year} assessment?`) && remove.mutate(a.id)}>
                        Delete
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {creating && (
        <Modal title="New APCPI assessment" onClose={() => setCreating(false)} width={420}>
          <div className="field">
            <label htmlFor="apcpi-fy">Fiscal year</label>
            <input id="apcpi-fy" type="number" value={fy} onChange={(e) => setFy(Number(e.target.value))} />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setCreating(false)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={create.isPending} onClick={() => create.mutate()}>
              Create
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}

function Sheet({ id, manage, onBack }: { id: number; manage: boolean; onBack: () => void }) {
  const q = useQuery({ queryKey: ['apcpi', id], queryFn: () => apcpiApi.get(id) })
  return q.data ? <SheetBody key={q.data.status + q.dataUpdatedAt} a={q.data} manage={manage} onBack={onBack} /> : <div className="page-loading">Loading…</div>
}

function SheetBody({ a, manage, onBack }: { a: ApcpiAssessment; manage: boolean; onBack: () => void }) {
  const toast = useToast()
  const qc = useQueryClient()
  const [scores, setScores] = useState<Record<string, ApcpiScore>>(a.scores)
  const [remarks, setRemarks] = useState(a.remarks ?? '')
  const editable = manage && a.status !== 'finalized'
  const done = (msg: string) => (r: ApcpiAssessment) => {
    qc.setQueryData(['apcpi', a.id], r)
    toast(msg)
  }
  const fail = (e: unknown) => toast(e instanceof ApiError ? e.message : 'Something went wrong.', 'error')
  const payload = () => Object.entries(scores).map(([k, s]) => ({ indicator_key: k, score: s.score, remarks: s.remarks, means_of_verification: s.means_of_verification }))
  const save = useMutation({ mutationFn: () => apcpiApi.saveScores(a.id, payload(), remarks), onSuccess: done('Scores saved.'), onError: fail })
  const finalize = useMutation({
    mutationFn: async () => {
      await apcpiApi.saveScores(a.id, payload(), remarks)
      return apcpiApi.finalize(a.id)
    },
    onSuccess: done(`FY ${a.fiscal_year} finalized.`),
    onError: fail,
  })
  const reopen = useMutation({ mutationFn: () => apcpiApi.reopen(a.id), onSuccess: done('Reopened for editing.'), onError: fail })
  const set = (k: string, patch: Partial<ApcpiScore>) => setScores((s) => ({ ...s, [k]: { ...(s[k] ?? { score: 0, remarks: null, means_of_verification: null }), ...patch } }))
  const useSuggested = () => {
    Object.entries(a.evidence).forEach(([k, e]) => {
      if (e.suggested != null) set(k, { score: e.suggested, means_of_verification: scores[k]?.means_of_verification || `System records: ${e.basis}` })
    })
  }
  return (
    <div className="page page-wide">
      <div className="section-header">
        <button className="btn btn-secondary btn-sm" onClick={onBack}>
          ← Back
        </button>
        <h1 className="page-title">
          APCPI FY {a.fiscal_year}{' '}
          <span className={'badge ' + (a.status === 'finalized' ? 'st-ok' : 'badge-muted')}>{a.status === 'finalized' ? 'Finalized' : 'Draft'}</span>
        </h1>
        <div className="toolbar" style={{ margin: 0 }}>
          {editable && (
            <>
              <button className="btn btn-outline" onClick={useSuggested}>
                Use system suggestions
              </button>
              <button className="btn btn-primary" disabled={save.isPending} onClick={() => save.mutate()}>
                💾 Save Scores
              </button>
              <button className="btn btn-secondary" disabled={finalize.isPending} onClick={() => finalize.mutate()}>
                ✔ Finalize
              </button>
            </>
          )}
          {manage && a.status === 'finalized' && (
            <button className="btn btn-secondary" disabled={reopen.isPending} onClick={() => reopen.mutate()}>
              ↩ Reopen
            </button>
          )}
          <button className="btn btn-secondary" onClick={() => window.print()}>
            🖨 Print
          </button>
        </div>
      </div>
      <p className="muted small">
        Conducted by {a.conducted_by ?? '—'}
        {a.finalized_at && ` · finalized ${formatDateTime(a.finalized_at)}`}. Ratings: 0 none, 1 beginning, 2 maturing, 3 advanced. Saved scores update the
        averages below.
      </p>
      <div className="kpi-row">
        <div className="kpi">
          <span className="kpi-value">
            {Number(a.summary.overall).toFixed(2)} <small>/ 3.00</small>
          </span>
          <span className="kpi-label">
            Overall · <span className={'badge ' + MATURITY[a.summary.maturity]}>{a.summary.maturity}</span>
          </span>
        </div>
        {Object.entries(a.summary.pillars).map(([k, p]) => (
          <div key={k} className="kpi">
            <span className="kpi-value">{Number(p.average).toFixed(2)}</span>
            <span className="kpi-label">
              {p.title.split(' — ')[0]} · <span className={'badge ' + MATURITY[p.maturity]}>{p.maturity}</span>
            </span>
          </div>
        ))}
      </div>
      <div className="print-area">
        {a.catalogue.map((p) => (
          <section key={p.key} style={{ marginBottom: '1.25rem' }}>
            <h3>
              {p.title} <span className="muted small">avg {Number(a.summary.pillars[p.key]?.average ?? 0).toFixed(2)}</span>
            </h3>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Indicator</th>
                    <th>Score (0–3)</th>
                    <th>From the system</th>
                    <th>Means of Verification</th>
                    <th>Remarks</th>
                  </tr>
                </thead>
                <tbody>
                  {p.indicators.map(([k, label]) => {
                    const s = scores[k] ?? { score: 0, remarks: null, means_of_verification: null }
                    const e = a.evidence[k]
                    return (
                      <tr key={k}>
                        <td>{label}</td>
                        <td>
                          <select value={s.score} disabled={!editable} onChange={(ev) => set(k, { score: Number(ev.target.value) })} aria-label={`Score for ${label}`}>
                            {[0, 1, 2, 3].map((n) => (
                              <option key={n}>{n}</option>
                            ))}
                          </select>
                        </td>
                        <td className="small">
                          {e ? (
                            <>
                              {e.suggested != null && <span className="badge st-partial">suggests {e.suggested}</span>} {e.basis}
                            </>
                          ) : (
                            <span className="muted">—</span>
                          )}
                        </td>
                        <td>
                          <input value={s.means_of_verification ?? ''} disabled={!editable} onChange={(ev) => set(k, { means_of_verification: ev.target.value || null })}
                            placeholder="e.g. posted IBs, training records" aria-label={`Means of verification for ${label}`} />
                        </td>
                        <td>
                          <input value={s.remarks ?? ''} disabled={!editable} onChange={(ev) => set(k, { remarks: ev.target.value || null })} aria-label={`Remarks for ${label}`} />
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </section>
        ))}
        <div className="field">
          <label htmlFor="apcpi-remarks">Overall remarks</label>
          <textarea id="apcpi-remarks" rows={3} value={remarks} disabled={!editable} onChange={(e) => setRemarks(e.target.value)} />
        </div>
      </div>
    </div>
  )
}

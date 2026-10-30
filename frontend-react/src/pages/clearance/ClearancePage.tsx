import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { clearanceApi, type Clearance, type ClearancePurpose } from '../../api/tor'
import { useAuth } from '../../auth/AuthContext'
import { ExcelButton } from '../../components/ExcelButton'
import { Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatDateTime, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge, PersonSelect } from '../assets/pickers'
import { STATUS_LABEL } from '../assets/labels'
import '../inventory/inventory.css'
import '../assets/assets.css'

export const PURPOSES: Record<ClearancePurpose, string> = {
  resignation: 'Resignation',
  retirement: 'Retirement',
  transfer: 'Transfer to another office or agency',
  end_of_contract: 'End of contract',
  leave: 'Extended leave',
  other: 'Other',
}

const CLEARANCE_STATUS: Record<string, [string, string]> = {
  pending: ['Turnover in progress', 'st-pending'],
  issued: ['Cleared', 'st-ok'],
  cancelled: ['Cancelled', 'badge-muted'],
}

/**
 * Property turnover and clearance (TOR 1.4.12, 1.5.5): who is turning over property before leaving,
 * what they still hold, and the certificate once everything is back.
 */
export function ClearancePage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('pending')
  const [search, setSearch] = useState('')
  const [opening, setOpening] = useState(false)
  const [viewing, setViewing] = useState<number | null>(null)
  const q = useDebounced(search.trim())
  const list = useQuery({ queryKey: ['clearances', page, status, q], queryFn: () => clearanceApi.list({ page, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const manage = can('assign_properties', 'transfer_properties')
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['clearances'] })

  return (
    <div className="page page-wide">
      <h1 className="page-title">Property Turnover &amp; Clearance</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search name or certificate no." value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} aria-label="Search clearances" />
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="pending">In progress</option>
            <option value="issued">Cleared</option>
            <option value="cancelled">Cancelled</option>
            <option value="">All</option>
          </select>
        </div>
        {manage && (
          <button className="btn btn-primary" onClick={() => setOpening(true)}>
            + Start clearance
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Employee</th>
              <th>Office</th>
              <th>Reason</th>
              <th>Effective</th>
              <th>Still holds</th>
              <th>Status</th>
              <th>Certificate</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((c) => (
              <tr key={c.id}>
                <td>{c.employee}</td>
                <td>{c.department ?? '—'}</td>
                <td>{PURPOSES[c.purpose]}</td>
                <td>{formatDate(c.effective_date)}</td>
                <td>{c.status === 'pending' ? <strong className={c.outstanding > 0 ? 'text-danger' : ''}>{c.outstanding} asset(s)</strong> : '—'}</td>
                <td>
                  <Badge map={CLEARANCE_STATUS} value={c.status} />
                </td>
                <td className="mono">{c.certificate_number ?? '—'}</td>
                <td>
                  <button className="btn btn-outline btn-sm" onClick={() => setViewing(c.id)}>
                    Open
                  </button>
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No clearances here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {opening && <OpenForm onClose={() => setOpening(false)} onSaved={(c) => { setOpening(false); toast(`Clearance started for ${c.employee}.`, 'success'); refresh(); setViewing(c.id) }} />}
      {viewing && <ClearanceDetail id={viewing} manage={manage} onClose={() => setViewing(null)} onChanged={(m) => { toast(m, 'success'); refresh() }} />}
    </div>
  )
}

function OpenForm({ onClose, onSaved }: { onClose: () => void; onSaved: (c: Clearance) => void }) {
  const [userId, setUserId] = useState('')
  const [purpose, setPurpose] = useState<ClearancePurpose>('resignation')
  const [details, setDetails] = useState('')
  const [effective, setEffective] = useState('')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const check = useQuery({ queryKey: ['clearance-check', userId], queryFn: () => clearanceApi.check(Number(userId)), enabled: !!userId })
  const save = useMutation({
    mutationFn: () => clearanceApi.open({ user_id: Number(userId), purpose, purpose_details: details || null, effective_date: effective || null, remarks: remarks || null }),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not start the clearance.'),
  })
  return (
    <Modal title="Start property clearance" onClose={onClose} width={640}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field span-2">
            <label htmlFor="cl-user">Employee *</label>
            <PersonSelect id="cl-user" value={userId} onChange={setUserId} required />
            {check.data && (
              <span className="hint">
                {check.data.outstanding === 0 ? 'Holds no property; the certificate can be issued right away.' : `Still accountable for ${check.data.outstanding} asset(s).`}
              </span>
            )}
          </div>
          <div className="field">
            <label htmlFor="cl-purpose">Reason *</label>
            <select id="cl-purpose" value={purpose} onChange={(e) => setPurpose(e.target.value as ClearancePurpose)}>
              {Object.entries(PURPOSES).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="cl-eff">Effective date</label>
            <input id="cl-eff" type="date" value={effective} onChange={(e) => setEffective(e.target.value)} />
          </div>
          <div className="field span-2">
            <label htmlFor="cl-det">Details{purpose === 'other' ? ' *' : ''}</label>
            <input id="cl-det" value={details} maxLength={255} onChange={(e) => setDetails(e.target.value)} required={purpose === 'other'} placeholder="e.g. Transfer to the regional office" />
          </div>
          <div className="field span-2">
            <label htmlFor="cl-rem">Remarks</label>
            <input id="cl-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending || !userId}>
            {save.isPending ? 'Saving…' : 'Start clearance'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ClearanceDetail({ id, manage, onClose, onChanged }: { id: number; manage: boolean; onClose: () => void; onChanged: (m: string) => void }) {
  const c = useQuery({ queryKey: ['clearances', 'one', id], queryFn: () => clearanceApi.show(id) })
  const [error, setError] = useState<string | null>(null)
  const act = useMutation({
    mutationFn: (what: 'issue' | 'cancel') => (what === 'issue' ? clearanceApi.issue(id) : clearanceApi.cancel(id)),
    onSuccess: (r) => {
      void c.refetch()
      onChanged(r.status === 'issued' ? `Certificate ${r.certificate_number} issued.` : 'Clearance cancelled.')
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not update the clearance.'),
  })
  const d = c.data
  return (
    <Modal title={d ? `Clearance — ${d.employee}` : 'Clearance'} onClose={onClose} width={820}>
      {c.isPending && <div className="page-loading">Loading…</div>}
      {d && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={CLEARANCE_STATUS} value={d.status} /> {d.certificate_number && <span className="mono">{d.certificate_number}</span>}
            </dd>
            <dt>Reason</dt>
            <dd>
              {PURPOSES[d.purpose]}
              {d.purpose_details ? ` — ${d.purpose_details}` : ''}
              {d.effective_date ? ` · effective ${formatDate(d.effective_date)}` : ''}
            </dd>
            <dt>Office</dt>
            <dd>{d.department ?? '—'}</dd>
            <dt>Started</dt>
            <dd>
              {formatDateTime(d.created_at)} by {d.requested_by ?? '—'}
            </dd>
            {d.status === 'issued' && (
              <>
                <dt>Cleared</dt>
                <dd>
                  {formatDateTime(d.issued_at)} by {d.issued_by} · {d.assets_turned_over} asset(s) turned over during the clearance
                </dd>
              </>
            )}
          </dl>

          {d.status === 'pending' && (
            <>
              <h4 style={{ margin: '1rem 0 0.5rem' }}>Still accountable for</h4>
              {d.outstanding_assets && d.outstanding_assets.length > 0 ? (
                <div className="table-wrap">
                  <table className="table table-compact">
                    <thead>
                      <tr>
                        <th>Property no.</th>
                        <th>Item</th>
                        <th>PAR / ICS</th>
                        <th>Status</th>
                        <th>Cost</th>
                      </tr>
                    </thead>
                    <tbody>
                      {d.outstanding_assets.map((a) => (
                        <tr key={a.id}>
                          <td className="mono">{a.tag_number}</td>
                          <td>{a.item_name}</td>
                          <td className="mono">{a.accountability_number ?? '—'}</td>
                          <td>
                            <Badge map={STATUS_LABEL} value={a.status} />
                          </td>
                          <td>{formatPeso(a.acquisition_cost)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="alert alert-success">Everything has been turned over. The certificate can be issued.</div>
              )}
              <p className="small muted">
                Record each return or transfer on the asset (Assets → open the asset → Record return / Transfer). A lost asset stays on the employee until relieved through
                the RLSDDP and disposal.
              </p>
            </>
          )}

          <div className="modal-footer">
            {d.status === 'issued' && <ExcelButton path={`/clearances/${d.id}/export`} name={`Clearance_${d.certificate_number}.xlsx`} label="Certificate" />}
            {manage && d.status === 'pending' && (
              <>
                <button className="btn btn-secondary" disabled={act.isPending} onClick={() => act.mutate('cancel')}>
                  Cancel clearance
                </button>
                <button className="btn btn-primary" disabled={act.isPending || d.outstanding > 0} onClick={() => act.mutate('issue')}>
                  Issue certificate
                </button>
              </>
            )}
          </div>
        </>
      )}
    </Modal>
  )
}

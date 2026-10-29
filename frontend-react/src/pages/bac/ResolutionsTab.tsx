import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { bacApi, prApi } from '../../api/endpoints'
import type { BacResolution } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { RESOLUTION_STATUS, RESOLUTION_TYPES } from '../rfq/labels'

const typeLabel = (t: string) => RESOLUTION_TYPES.find(([k]) => k === t)?.[1] ?? t

export function ResolutionsTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<BacResolution | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const key = `${status}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const list = useQuery({ queryKey: ['bac-resolutions', page, status, q], queryFn: () => bacApi.resolutions({ page, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['bac-resolutions', 'bac-resolution', 'bac-meeting', 'bac-meetings']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search resolution no. or title" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search resolutions" />
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            {Object.entries(RESOLUTION_STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        {can('manage_bac_resolutions') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + Draft resolution
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>No.</th>
              <th>Title</th>
              <th>Type</th>
              <th>PR</th>
              <th>Meeting</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((r) => (
              <tr key={r.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(r.id)}>
                    {r.resolution_number}
                  </button>
                </td>
                <td className="clip">{r.title}</td>
                <td>{typeLabel(r.resolution_type)}</td>
                <td className="mono">{r.purchase_request?.name ?? '—'}</td>
                <td className="clip">{r.meeting?.name ?? '—'}</td>
                <td>
                  <Badge map={RESOLUTION_STATUS} value={r.status} /> {r.used_by.length > 0 && <span className="muted small">→ {r.used_by.join(', ')}</span>}
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No resolutions.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {form && (
        <ResolutionForm
          resolution={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(r, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(r.id)
          }}
        />
      )}
      {viewing !== null && (
        <ResolutionDetail
          id={viewing}
          onClose={() => setViewing(null)}
          onEdit={(r) => {
            setViewing(null)
            setForm(r)
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
    </>
  )
}

function ResolutionForm({ resolution, onClose, onSaved }: { resolution: BacResolution | null; onClose: () => void; onSaved: (r: BacResolution, m: string) => void }) {
  const meetings = useQuery({ queryKey: ['bac-meetings', 'pick'], queryFn: () => bacApi.meetings({ page: 1 }) })
  const prs = useQuery({ queryKey: ['prs', 'approved-pick'], queryFn: () => prApi.list({ page: 1, status: 'approved' }) })
  const [title, setTitle] = useState(resolution?.title ?? '')
  const [type, setType] = useState(resolution?.resolution_type ?? 'resolution_to_award')
  const [meeting, setMeeting] = useState(resolution?.meeting ? String(resolution.meeting.id) : '')
  const [pr, setPr] = useState(resolution?.purchase_request ? String(resolution.purchase_request.id) : '')
  const [content, setContent] = useState(resolution?.content ?? '')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      const input = { title, resolution_type: type, bac_meeting_id: meeting ? Number(meeting) : null, purchase_request_id: pr ? Number(pr) : null, content: content || null }
      return resolution ? bacApi.updateResolution(resolution.id, input) : bacApi.createResolution(input)
    },
    onSuccess: (r) => onSaved(r, resolution ? `${r.resolution_number} saved.` : `${r.resolution_number} drafted.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  return (
    <Modal title={resolution ? `Edit ${resolution.resolution_number}` : 'Draft BAC resolution'} onClose={onClose} width={720}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        {resolution?.remarks && <div className="alert alert-warning">Returned: {resolution.remarks}</div>}
        <div className="form-grid">
          <div className="field span-2">
            <label htmlFor="rs-title">Title *</label>
            <input id="rs-title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} />
          </div>
          <div className="field">
            <label htmlFor="rs-type">Type *</label>
            <select id="rs-type" value={type} onChange={(e) => setType(e.target.value)}>
              {RESOLUTION_TYPES.map(([k, l]) => (
                <option key={k} value={k}>
                  {l}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="rs-pr">Purchase request{type === 'resolution_to_award' || type === 'award' ? ' *' : ''}</label>
            <select id="rs-pr" value={pr} onChange={(e) => setPr(e.target.value)}>
              <option value="">None</option>
              {prs.data?.data.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.pr_number} — {p.purpose}
                </option>
              ))}
            </select>
          </div>
          <div className="field span-2">
            <label htmlFor="rs-mt">Adopted at meeting</label>
            <select id="rs-mt" value={meeting} onChange={(e) => setMeeting(e.target.value)}>
              <option value="">None</option>
              {meetings.data?.data.filter((m) => m.status !== 'cancelled').map((m) => (
                <option key={m.id} value={m.id}>
                  {m.meeting_number} — {m.title} ({formatDate(m.meeting_date)})
                </option>
              ))}
            </select>
            <span className="hint">Approval needs this meeting held with a quorum.</span>
          </div>
          <div className="field span-2">
            <label htmlFor="rs-body">Resolution *</label>
            <textarea id="rs-body" rows={8} value={content} onChange={(e) => setContent(e.target.value)} placeholder="WHEREAS … NOW, THEREFORE, the BAC RESOLVES …" />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save draft'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ResolutionDetail({ id, onClose, onEdit, onChanged }: { id: number; onClose: () => void; onEdit: (r: BacResolution) => void; onChanged: (m: string, closed?: boolean) => void }) {
  const q = useQuery({ queryKey: ['bac-resolution', id], queryFn: () => bacApi.resolution(id) })
  const [returning, setReturning] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [why, setWhy] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const act = useMutation({
    mutationFn: (a: 'submit' | 'approve' | 'archive') => bacApi.resolutionAction(id, a),
    onSuccess: (r) => onChanged(`${r.resolution_number}: ${RESOLUTION_STATUS[r.status][0]}.`),
    onError: fail,
  })
  const ret = useMutation({ mutationFn: () => bacApi.returnResolution(id, why), onSuccess: (r) => { setReturning(false); onChanged(`${r.resolution_number} returned to draft.`) }, onError: fail })
  const remove = useMutation({ mutationFn: () => bacApi.removeResolution(id), onSuccess: (r) => onChanged(r.message, true), onError: (e) => { setDeleting(false); fail(e) } })
  const r = q.data
  return (
    <Modal title={r ? r.resolution_number : 'Resolution'} onClose={onClose} width={760}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {r && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={RESOLUTION_STATUS} value={r.status} /> <span className="muted">{typeLabel(r.resolution_type)}</span>
            </dd>
            <dt>Title</dt>
            <dd>{r.title}</dd>
            {r.purchase_request && (
              <>
                <dt>Purchase request</dt>
                <dd className="mono">{r.purchase_request.name}</dd>
              </>
            )}
            {r.meeting && (
              <>
                <dt>Meeting</dt>
                <dd>{r.meeting.name}</dd>
              </>
            )}
            <dt>Drafted by</dt>
            <dd>
              {r.preparer?.name} · {formatDate(r.created_at)}
            </dd>
            {r.approver && (
              <>
                <dt>Approved by</dt>
                <dd>
                  {r.approver.name} · {formatDate(r.approved_at)}
                </dd>
              </>
            )}
            {r.used_by.length > 0 && (
              <>
                <dt>Backs</dt>
                <dd>{r.used_by.join(', ')}</dd>
              </>
            )}
            {r.remarks && (
              <>
                <dt>Remarks</dt>
                <dd>{r.remarks}</dd>
              </>
            )}
          </dl>
          <div className="dash-card" style={{ marginTop: '1rem', whiteSpace: 'pre-wrap' }}>
            {r.content ?? <span className="muted">No text yet.</span>}
          </div>
          {returning ? (
            <div className="reject-box">
              <div className="field">
                <label htmlFor="rs-why">What needs changing? *</label>
                <input id="rs-why" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus />
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setReturning(false)}>
                  Cancel
                </button>
                <button className="btn btn-danger" onClick={() => ret.mutate()} disabled={ret.isPending || !why.trim()}>
                  Return to draft
                </button>
              </div>
            </div>
          ) : (
            (r.can_edit || r.can_approve || r.can_archive || r.can_delete) && (
              <div className="modal-footer">
                {r.can_delete && (
                  <button className="btn btn-danger" onClick={() => setDeleting(true)}>
                    Delete
                  </button>
                )}
                {r.can_edit && (
                  <button className="btn btn-secondary" onClick={() => onEdit(r)}>
                    Edit
                  </button>
                )}
                {r.can_submit && (
                  <button className="btn btn-primary" onClick={() => act.mutate('submit')} disabled={act.isPending}>
                    Submit for approval
                  </button>
                )}
                {r.can_approve && (
                  <>
                    <button className="btn btn-danger" onClick={() => setReturning(true)}>
                      Return
                    </button>
                    <button className="btn btn-primary" onClick={() => act.mutate('approve')} disabled={act.isPending}>
                      Approve
                    </button>
                  </>
                )}
                {r.can_archive && (
                  <button className="btn btn-outline" onClick={() => act.mutate('archive')} disabled={act.isPending}>
                    Archive
                  </button>
                )}
              </div>
            )
          )}
          {deleting && (
            <ConfirmDialog title="Delete resolution" message={`Delete the draft ${r.resolution_number}?`} confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} onCancel={() => setDeleting(false)} />
          )}
        </>
      )}
    </Modal>
  )
}

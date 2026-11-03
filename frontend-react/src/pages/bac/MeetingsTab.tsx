import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { bacApi } from '../../api/endpoints'
import type { BacMeeting } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDateTime } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { designationLabel, MEETING_STATUS, RESOLUTION_STATUS, VOTING } from '../rfq/labels'

export function MeetingsTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const list = useQuery({ queryKey: ['bac-meetings', page, status], queryFn: () => bacApi.meetings({ page, status: status || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['bac-meetings', 'bac-meeting', 'bac-resolutions']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="">All meetings</option>
            {Object.entries(MEETING_STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        {can('manage_bac_meetings') && (
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            + Schedule meeting
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
              <th>When</th>
              <th>Venue</th>
              <th>Quorum</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((m) => (
              <tr key={m.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(m.id)}>
                    {m.meeting_number}
                  </button>
                </td>
                <td className="clip">{m.title}</td>
                <td>{formatDateTime(m.meeting_date)}</td>
                <td>{m.venue ?? '—'}</td>
                <td>{m.attendees_count === 0 ? <span className="muted">no attendance</span> : m.quorum.met ? <span className="badge st-ok">met</span> : <span className="badge st-bad">none</span>}</td>
                <td>
                  <Badge map={MEETING_STATUS} value={m.status} />
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No meetings.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {creating && (
        <NewMeeting
          onClose={() => setCreating(false)}
          onSaved={(m) => {
            toast(`${m.meeting_number} scheduled.`, 'success')
            setCreating(false)
            refresh()
            setViewing(m.id)
          }}
        />
      )}
      {viewing !== null && (
        <MeetingDetail
          id={viewing}
          onClose={() => setViewing(null)}
          onChanged={(msg, closed) => {
            toast(msg, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
    </>
  )
}

function NewMeeting({ onClose, onSaved }: { onClose: () => void; onSaved: (m: BacMeeting) => void }) {
  const [title, setTitle] = useState('')
  const [when, setWhen] = useState('')
  const [venue, setVenue] = useState('')
  const [agenda, setAgenda] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => bacApi.createMeeting({ title, meeting_date: when, venue: venue || null, agenda: agenda || null }),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not schedule the meeting.'),
  })
  return (
    <Modal title="Schedule BAC meeting" onClose={onClose} width={600}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field span-2">
            <label htmlFor="mt-title">Title *</label>
            <input id="mt-title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} placeholder="e.g. Opening of quotations, RFQ-2026-0001" />
          </div>
          <div className="field">
            <label htmlFor="mt-when">Date and time *</label>
            <input id="mt-when" type="datetime-local" value={when} onChange={(e) => setWhen(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="mt-venue">Venue</label>
            <input id="mt-venue" value={venue} onChange={(e) => setVenue(e.target.value)} />
          </div>
          <div className="field span-2">
            <label htmlFor="mt-agenda">Agenda</label>
            <textarea id="mt-agenda" rows={3} value={agenda} onChange={(e) => setAgenda(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Schedule'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function MeetingDetail({ id, onClose, onChanged }: { id: number; onClose: () => void; onChanged: (m: string, closed?: boolean) => void }) {
  const q = useQuery({ queryKey: ['bac-meeting', id], queryFn: () => bacApi.meeting(id) })
  const m = q.data
  return (
    <Modal title={m ? `${m.meeting_number} — ${m.title}` : 'BAC meeting'} onClose={onClose} width={860}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {m && <MeetingBody key={m.status + m.attendees_count} m={m} onChanged={onChanged} />}
    </Modal>
  )
}

interface Row {
  key: string
  user_id: number | null
  name: string
  committee_role: string | null
  present: boolean
}

function MeetingBody({ m, onChanged }: { m: BacMeeting; onChanged: (msg: string, closed?: boolean) => void }) {
  const roster = useQuery({ queryKey: ['bac-members'], queryFn: bacApi.members, enabled: m.can_edit })
  const [rows, setRows] = useState<Row[] | null>(null)
  const [minutes, setMinutes] = useState(m.minutes ?? '')
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const status = useMutation({ mutationFn: (s: string) => bacApi.updateMeeting(m.id, { status: s }), onSuccess: (r) => onChanged(`${r.meeting_number}: ${MEETING_STATUS[r.status][0]}.`), onError: fail })
  const saveMinutes = useMutation({ mutationFn: () => bacApi.updateMeeting(m.id, { minutes }), onSuccess: () => onChanged('Minutes saved.'), onError: fail })
  const saveAttendance = useMutation({
    mutationFn: () => bacApi.attendance(m.id, rows!.filter((r) => r.name.trim()).map(({ user_id, name, committee_role, present }) => ({ user_id, name, committee_role, present }))),
    onSuccess: (r) => {
      setRows(null)
      onChanged(`Attendance saved: ${r.quorum.voting_present} of ${r.quorum.voting_seated} voting members present (${r.quorum.met ? 'quorum' : 'no quorum'}).`)
    },
    onError: fail,
  })
  const remove = useMutation({ mutationFn: () => bacApi.removeMeeting(m.id), onSuccess: (r) => onChanged(r.message, true), onError: (e) => { setDeleting(false); fail(e) } })
  const busy = status.isPending || saveMinutes.isPending || saveAttendance.isPending
  // Once a resolution from the meeting is approved, its attendance is final.
  const attendanceFinal = (m.resolutions ?? []).some((r) => r.status === 'approved' || r.status === 'archived')

  const startAttendance = () => {
    const recorded = m.attendees ?? []
    const seated = (roster.data?.data ?? []).filter((x) => x.is_active && !x.term_expired)
    const fromRoster: Row[] = seated.map((x) => {
      const prev = recorded.find((a) => a.name === x.full_name)
      return { key: `m${x.id}`, user_id: x.user?.id ?? null, name: x.full_name, committee_role: x.designation, present: prev ? prev.present : true }
    })
    const others: Row[] = recorded.filter((a) => !seated.some((x) => x.full_name === a.name)).map((a) => ({ key: `a${a.id}`, user_id: a.user_id, name: a.name, committee_role: a.committee_role, present: a.present }))
    setRows([...fromRoster, ...others])
  }

  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <dl className="detail-grid">
        <dt>Status</dt>
        <dd>
          <Badge map={MEETING_STATUS} value={m.status} />
        </dd>
        <dt>When / where</dt>
        <dd>
          {formatDateTime(m.meeting_date)} {m.venue && `· ${m.venue}`}
        </dd>
        <dt>Called by</dt>
        <dd>{m.caller?.name}</dd>
        {m.agenda && (
          <>
            <dt>Agenda</dt>
            <dd style={{ whiteSpace: 'pre-wrap' }}>{m.agenda}</dd>
          </>
        )}
        <dt>Quorum</dt>
        <dd>
          {m.quorum.voting_present} of {m.quorum.voting_seated} voting members present; {m.quorum.needed} needed —{' '}
          {m.quorum.met ? <span className="badge st-ok">quorum</span> : <span className="badge st-bad">no quorum</span>}
        </dd>
      </dl>

      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>Attendance</h4>
        {m.can_edit && !attendanceFinal && rows === null && (
          <button className="btn btn-outline btn-sm" onClick={startAttendance} disabled={roster.isPending}>
            {m.attendees_count ? 'Edit attendance' : 'Take attendance'}
          </button>
        )}
      </div>
      {rows === null ? (
        <ul className="chain">
          {m.attendees?.map((a) => (
            <li key={a.id} className={a.present ? 'step-approved' : ''}>
              <span className="step-dot">{a.present ? '✓' : '–'}</span>
              <span>
                <strong>{a.name}</strong> <span className="muted">{designationLabel(a.committee_role)}</span>
                {!a.present && <span className="muted"> · absent</span>}
              </span>
            </li>
          ))}
          {m.attendees?.length === 0 && <li className="muted">Not taken yet.</li>}
        </ul>
      ) : (
        <div className="reject-box">
          {rows.map((r) => (
            <label key={r.key} className="checkbox-line" style={{ display: 'flex', marginBottom: '0.35rem' }}>
              <input type="checkbox" checked={r.present} onChange={(e) => setRows((rs) => rs!.map((x) => (x.key === r.key ? { ...x, present: e.target.checked } : x)))} />
              {r.name} <span className="muted">{designationLabel(r.committee_role)}{r.committee_role && VOTING.includes(r.committee_role) ? '' : ' (non-voting)'}</span>
            </label>
          ))}
          {rows.length === 0 && <p className="muted">No seated members; add them under Composition.</p>}
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setRows((rs) => [...rs!, { key: `n${rs!.length}`, user_id: null, name: '', committee_role: 'observer', present: true }])}>
            + Observer / guest
          </button>
          {rows.filter((r) => r.key.startsWith('n')).map((r) => (
            <input
              key={r.key}
              aria-label="Guest name"
              placeholder="Name of observer or resource person"
              value={r.name}
              style={{ display: 'block', marginTop: '0.5rem' }}
              onChange={(e) => setRows((rs) => rs!.map((x) => (x.key === r.key ? { ...x, name: e.target.value } : x)))}
            />
          ))}
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setRows(null)}>
              Cancel
            </button>
            <button className="btn btn-primary" onClick={() => saveAttendance.mutate()} disabled={busy}>
              Save attendance
            </button>
          </div>
        </div>
      )}

      {m.status !== 'scheduled' && m.status !== 'cancelled' && (
        <div className="field" style={{ marginTop: '1rem' }}>
          <label htmlFor="mt-min">Minutes</label>
          <textarea id="mt-min" rows={4} value={minutes} onChange={(e) => setMinutes(e.target.value)} readOnly={!m.can_edit} />
          {m.can_edit && minutes !== (m.minutes ?? '') && (
            <button className="btn btn-outline btn-sm" style={{ marginTop: '0.5rem', alignSelf: 'flex-start' }} onClick={() => saveMinutes.mutate()} disabled={busy}>
              Save minutes
            </button>
          )}
        </div>
      )}

      {m.resolutions && m.resolutions.length > 0 && (
        <>
          <h4>Resolutions adopted</h4>
          <ul>
            {m.resolutions.map((r) => (
              <li key={r.id}>
                <span className="mono">{r.resolution_number}</span> {r.title} <Badge map={RESOLUTION_STATUS} value={r.status} />
              </li>
            ))}
          </ul>
        </>
      )}

      {m.can_edit && m.status !== 'completed' && rows === null && (
        <div className="modal-footer">
          <button className="btn btn-danger" onClick={() => setDeleting(true)}>
            Delete
          </button>
          {(m.status === 'scheduled' || m.status === 'ongoing') && (
            <button className="btn btn-secondary" onClick={() => status.mutate('cancelled')} disabled={busy}>
              Cancel meeting
            </button>
          )}
          {m.status === 'scheduled' && (
            <button className="btn btn-outline" onClick={() => status.mutate('ongoing')} disabled={busy}>
              Start
            </button>
          )}
          {(m.status === 'scheduled' || m.status === 'ongoing') && (
            <button className="btn btn-primary" onClick={() => status.mutate('completed')} disabled={busy || m.attendees_count === 0} title={m.attendees_count === 0 ? 'Take attendance first' : undefined}>
              Mark held
            </button>
          )}
        </div>
      )}
      {deleting && (
        <ConfirmDialog title="Delete meeting" message={`Delete ${m.meeting_number}?`} confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} onCancel={() => setDeleting(false)} />
      )}
    </>
  )
}

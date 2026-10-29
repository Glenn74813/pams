import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { bacApi } from '../../api/endpoints'
import type { BacMember } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { formatDate } from '../../lib/format'
import { PersonSelect } from '../assets/pickers'
import { designationLabel, DESIGNATIONS } from '../rfq/labels'

export function CompositionTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const q = useQuery({ queryKey: ['bac-members'], queryFn: bacApi.members })
  const [form, setForm] = useState<BacMember | 'new' | null>(null)
  const [removing, setRemoving] = useState<BacMember | null>(null)
  const manage = can('manage_bac_meetings')
  const remove = useMutation({
    mutationFn: (m: BacMember) => bacApi.removeMember(m.id),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setRemoving(null)
      void queryClient.invalidateQueries({ queryKey: ['bac-members'] })
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not remove.', 'error')
      setRemoving(null)
    },
  })
  const c = q.data?.compliance
  return (
    <>
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {c && (
        <div className={c.is_properly_constituted ? 'alert alert-success' : 'alert alert-warning'}>
          {c.is_properly_constituted
            ? `Properly constituted: chairperson, vice-chairperson, ${c.voting_members} voting members and a secretariat.`
            : `Not properly constituted (RA 12009): ${c.issues.join(' ')}`}
        </div>
      )}
      <div className="section-header">
        <h2>Members</h2>
        {manage && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + Add member
          </button>
        )}
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Designation</th>
              <th>Position / office</th>
              <th>Appointment</th>
              <th>Term</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {q.data?.data.map((m) => (
              <tr key={m.id} className={m.is_active && !m.term_expired ? '' : 'row-muted'}>
                <td>
                  {m.full_name}
                  {m.user && <div className="muted small">user: {m.user.name}</div>}
                </td>
                <td>{designationLabel(m.designation)}</td>
                <td>{[m.position, m.office].filter(Boolean).join(' · ') || '—'}</td>
                <td>{m.appointment_order_no ? `${m.appointment_order_no} (${formatDate(m.appointment_date)})` : '—'}</td>
                <td>{m.term_start || m.term_end ? `${formatDate(m.term_start)} – ${formatDate(m.term_end)}` : '—'}</td>
                <td>{!m.is_active ? <span className="badge badge-muted">Inactive</span> : m.term_expired ? <span className="badge st-bad">Term ended</span> : <span className="badge st-ok">Seated</span>}</td>
                <td>
                  {manage && (
                    <div className="workflow-actions">
                      <button className="btn btn-outline btn-sm" onClick={() => setForm(m)}>
                        Edit
                      </button>
                      <button className="icon-btn delete" title={`Remove ${m.full_name}`} onClick={() => setRemoving(m)}>
                        🗑
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {q.data && q.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No members recorded.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {form && (
        <MemberForm
          member={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(m) => {
            toast(m, 'success')
            setForm(null)
            void queryClient.invalidateQueries({ queryKey: ['bac-members'] })
          }}
        />
      )}
      {removing && (
        <ConfirmDialog
          title="Remove member"
          message={`Remove ${removing.full_name}? Someone who has attended meetings is marked inactive instead.`}
          confirmLabel="Remove"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(removing)}
          onCancel={() => setRemoving(null)}
        />
      )}
    </>
  )
}

function MemberForm({ member, onClose, onSaved }: { member: BacMember | null; onClose: () => void; onSaved: (m: string) => void }) {
  const [userId, setUserId] = useState(member?.user ? String(member.user.id) : '')
  const [name, setName] = useState(member?.full_name ?? '')
  const [designation, setDesignation] = useState(member?.designation ?? 'member')
  const [position, setPosition] = useState(member?.position ?? '')
  const [office, setOffice] = useState(member?.office ?? '')
  const [order, setOrder] = useState(member?.appointment_order_no ?? '')
  const [orderDate, setOrderDate] = useState(member?.appointment_date ?? '')
  const [start, setStart] = useState(member?.term_start ?? '')
  const [end, setEnd] = useState(member?.term_end ?? '')
  const [active, setActive] = useState(member?.is_active ?? true)
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      const input = {
        user_id: userId ? Number(userId) : null,
        full_name: name,
        designation,
        position: position || null,
        office: office || null,
        appointment_order_no: order || null,
        appointment_date: orderDate || null,
        term_start: start || null,
        term_end: end || null,
        is_active: active,
      }
      return member ? bacApi.updateMember(member.id, input) : bacApi.addMember(input)
    },
    onSuccess: (m) => onSaved(member ? `${m.full_name} updated.` : `${m.full_name} added to the BAC.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  return (
    <Modal title={member ? `Edit ${member.full_name}` : 'Add BAC member'} onClose={onClose} width={640}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field">
            <label htmlFor="bm-user">System user</label>
            <PersonSelect id="bm-user" value={userId} onChange={setUserId} />
            <span className="hint">Optional; links attendance and signatures.</span>
          </div>
          <div className="field">
            <label htmlFor="bm-name">Full name *</label>
            <input id="bm-name" value={name} onChange={(e) => setName(e.target.value)} required={!userId} maxLength={255} />
          </div>
          <div className="field">
            <label htmlFor="bm-des">Designation *</label>
            <select id="bm-des" value={designation} onChange={(e) => setDesignation(e.target.value)}>
              {DESIGNATIONS.map(([k, l]) => (
                <option key={k} value={k}>
                  {l}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="bm-pos">Position</label>
            <input id="bm-pos" value={position} onChange={(e) => setPosition(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="bm-off">Office</label>
            <input id="bm-off" value={office} onChange={(e) => setOffice(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="bm-order">Office order no.</label>
            <input id="bm-order" value={order} onChange={(e) => setOrder(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="bm-od">Order date</label>
            <input id="bm-od" type="date" value={orderDate} onChange={(e) => setOrderDate(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="bm-ts">Term from</label>
            <input id="bm-ts" type="date" value={start} onChange={(e) => setStart(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="bm-te">Term to</label>
            <input id="bm-te" type="date" value={end} min={start} onChange={(e) => setEnd(e.target.value)} />
          </div>
          <label className="checkbox-line span-2">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> Active
          </label>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

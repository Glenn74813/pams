import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { departmentsApi, type DepartmentRow } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'

/** Offices users belong to; they also own requests, PRs, PPMPs and physical counts. */
export function DepartmentsTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<DepartmentRow | 'new' | null>(null)
  const [deleting, setDeleting] = useState<DepartmentRow | null>(null)
  const list = useQuery({ queryKey: ['department-rows'], queryFn: departmentsApi.list })
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['department-rows'] })
    void queryClient.invalidateQueries({ queryKey: ['departments'] })
  }
  const remove = useMutation({
    mutationFn: (d: DepartmentRow) => departmentsApi.remove(d.id),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setDeleting(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete the office.', 'error')
      setDeleting(null)
    },
  })
  const canEdit = can('create_users') || can('edit_users')

  return (
    <>
      <div className="section-header">
        <h2>Offices {list.data ? `(${list.data.data.length})` : ''}</h2>
        {canEdit && (
          <button className="btn btn-primary" onClick={() => setEditing('new')}>
            + Add office
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Office</th>
              <th>Users</th>
              {canEdit && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((d) => (
              <tr key={d.id}>
                <td className="mono">{d.code}</td>
                <td>{d.name}</td>
                <td>{d.users}</td>
                {canEdit && (
                  <td>
                    <div className="actions">
                      <button className="icon-btn edit" title={`Edit ${d.name}`} onClick={() => setEditing(d)}>
                        ✎
                      </button>
                      {can('delete_users') && (
                        <button className="icon-btn delete" title={`Delete ${d.name}`} onClick={() => setDeleting(d)}>
                          🗑
                        </button>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing && (
        <DepartmentForm
          department={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(m) => {
            toast(m, 'success')
            setEditing(null)
            refresh()
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete office"
          message={`Delete ${deleting.name}? Offices still used by users or records cannot be deleted; rename them instead.`}
          confirmLabel="Delete office"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

function DepartmentForm({ department, onClose, onSaved }: { department: DepartmentRow | null; onClose: () => void; onSaved: (m: string) => void }) {
  const [name, setName] = useState(department?.name ?? '')
  const [code, setCode] = useState(department?.code ?? '')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => (department ? departmentsApi.update(department.id, { name, code }) : departmentsApi.create({ name, code })),
    onSuccess: (d) => onSaved(department ? `${d.name} updated.` : `${d.name} added.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the office.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    save.mutate()
  }
  return (
    <Modal title={department ? 'Edit office' : 'Add office'} onClose={onClose} width={480}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field">
          <label htmlFor="dept-name">Office name *</label>
          <input id="dept-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={255} required autoFocus />
        </div>
        <div className="field">
          <label htmlFor="dept-code">Code *</label>
          <input id="dept-code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} maxLength={20} required placeholder="e.g. ICT" />
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

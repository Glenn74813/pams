import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { locationsApi, type AssetLocation } from '../../api/tor'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'

/** Buildings, floors and rooms where assets are kept (TOR 1.2.3). */
export function LocationsTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<AssetLocation | 'new' | null>(null)
  const [deleting, setDeleting] = useState<AssetLocation | null>(null)
  const list = useQuery({ queryKey: ['locations', 'all'], queryFn: () => locationsApi.list() })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['locations'] })
  const remove = useMutation({
    mutationFn: (l: AssetLocation) => locationsApi.remove(l.id),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setDeleting(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete the location.', 'error')
      setDeleting(null)
    },
  })
  const canEdit = can('edit_settings')

  return (
    <>
      <div className="section-header">
        <h2>Asset locations {list.data ? `(${list.data.length})` : ''}</h2>
        {canEdit && (
          <button className="btn btn-primary" onClick={() => setEditing('new')}>
            + Add location
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Location</th>
              <th>Building</th>
              <th>Floor</th>
              <th>Room</th>
              <th>Assets kept here</th>
              <th>Status</th>
              {canEdit && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {list.data?.map((l) => (
              <tr key={l.id}>
                <td>
                  {l.name}
                  {l.description && <div className="muted small">{l.description}</div>}
                </td>
                <td>{l.building ?? '—'}</td>
                <td>{l.floor ?? '—'}</td>
                <td>{l.room ?? '—'}</td>
                <td>{l.assets}</td>
                <td>{l.is_active ? <span className="badge st-ok">In use</span> : <span className="badge badge-muted">Inactive</span>}</td>
                {canEdit && (
                  <td>
                    <div className="workflow-actions">
                      <button className="icon-btn edit" title={`Edit ${l.name}`} onClick={() => setEditing(l)}>
                        ✎
                      </button>
                      <button className="icon-btn delete" title={`Delete ${l.name}`} onClick={() => setDeleting(l)}>
                        🗑
                      </button>
                    </div>
                  </td>
                )}
              </tr>
            ))}
            {list.data?.length === 0 && (
              <tr>
                <td colSpan={canEdit ? 7 : 6} className="empty">
                  No locations yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {editing && (
        <LocationForm
          location={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(l) => {
            toast(`${l.name} saved.`, 'success')
            setEditing(null)
            refresh()
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete location"
          message={`Delete ${deleting.name}? A location that has held assets cannot be deleted; deactivate it instead.`}
          confirmLabel="Delete"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

function LocationForm({ location, onClose, onSaved }: { location: AssetLocation | null; onClose: () => void; onSaved: (l: AssetLocation) => void }) {
  const [f, setF] = useState({
    name: location?.name ?? '',
    building: location?.building ?? '',
    floor: location?.floor ?? '',
    room: location?.room ?? '',
    description: location?.description ?? '',
    is_active: location?.is_active ?? true,
  })
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => (location ? locationsApi.update(location.id, f) : locationsApi.create(f)),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the location.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    save.mutate()
  }
  const text = (k: 'name' | 'building' | 'floor' | 'room' | 'description') => ({ value: f[k], onChange: (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value }) })
  return (
    <Modal title={location ? `Edit ${location.name}` : 'Add location'} onClose={onClose} width={560}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field span-2">
            <label htmlFor="lc-name">Name *</label>
            <input id="lc-name" maxLength={150} required {...text('name')} placeholder="e.g. Supply Office stockroom" />
          </div>
          <div className="field">
            <label htmlFor="lc-bldg">Building</label>
            <input id="lc-bldg" maxLength={150} {...text('building')} />
          </div>
          <div className="field">
            <label htmlFor="lc-floor">Floor</label>
            <input id="lc-floor" maxLength={50} {...text('floor')} />
          </div>
          <div className="field">
            <label htmlFor="lc-room">Room</label>
            <input id="lc-room" maxLength={100} {...text('room')} />
          </div>
          <div className="field">
            <label htmlFor="lc-active">Status</label>
            <select id="lc-active" value={f.is_active ? '1' : '0'} onChange={(e) => setF({ ...f, is_active: e.target.value === '1' })}>
              <option value="1">In use</option>
              <option value="0">Inactive</option>
            </select>
          </div>
          <div className="field span-2">
            <label htmlFor="lc-desc">Description</label>
            <input id="lc-desc" maxLength={500} {...text('description')} />
          </div>
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

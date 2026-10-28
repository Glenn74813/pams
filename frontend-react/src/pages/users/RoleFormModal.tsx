import { useMemo, useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { rolesApi } from '../../api/endpoints'
import type { Role } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'

interface Props {
  role: Role | null
  onClose: () => void
  onSaved: (message: string) => void
}

export function RoleFormModal({ role, onClose, onSaved }: Props) {
  const { user: me } = useAuth()
  const editing = role !== null
  const isSuperAdminRole = role?.name === 'Super Admin'
  const catalogue = useQuery({ queryKey: ['permission-catalogue'], queryFn: rolesApi.catalogue })

  const [name, setName] = useState(role?.name ?? '')
  const [description, setDescription] = useState(role?.description ?? '')
  const [fullAccess, setFullAccess] = useState(role?.permissions.includes('*') ?? false)
  const [selected, setSelected] = useState<Set<string>>(
    () => new Set((role?.permissions ?? []).filter((p) => p !== '*')),
  )
  const [error, setError] = useState<string | null>(null)

  const groups = useMemo(() => Object.entries(catalogue.data?.data ?? {}), [catalogue.data])

  const toggle = (key: string) =>
    setSelected((s) => {
      const next = new Set(s)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })

  const toggleGroup = (keys: string[], on: boolean) =>
    setSelected((s) => {
      const next = new Set(s)
      keys.forEach((k) => (on ? next.add(k) : next.delete(k)))
      return next
    })

  const save = useMutation({
    mutationFn: () => {
      const input = { name, description, permissions: fullAccess ? ['*'] : [...selected] }
      return editing ? rolesApi.update(role.id, input) : rolesApi.create(input)
    },
    onSuccess: (res) => onSaved(res.message),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the role.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!fullAccess && selected.size === 0) {
      setError('Choose at least one permission.')
      return
    }
    save.mutate()
  }

  return (
    <Modal title={editing ? `Edit role: ${role.name}` : 'Create New Role'} onClose={onClose} width={860}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field">
            <label htmlFor="r-name">Role name *</label>
            <input id="r-name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} disabled={isSuperAdminRole} />
          </div>
          <div className="field">
            <label htmlFor="r-desc">Description</label>
            <input id="r-desc" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} />
          </div>
        </div>

        {me?.is_super_admin && (
          <label className="full-access">
            <input type="checkbox" checked={fullAccess} onChange={(e) => setFullAccess(e.target.checked)} disabled={isSuperAdminRole} />
            <span>
              <strong>Full access</strong> — every permission, including ones added in future
            </span>
          </label>
        )}

        {!fullAccess && (
          <>
            <div className="perm-toolbar">
              <h4>Permissions ({selected.size})</h4>
              <div>
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setSelected(new Set(groups.flatMap(([, p]) => Object.keys(p))))}>
                  Select all
                </button>{' '}
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setSelected(new Set())}>
                  Clear all
                </button>
              </div>
            </div>
            {catalogue.isPending && <div className="page-loading">Loading permissions…</div>}
            <div className="perm-grid">
              {groups.map(([group, perms]) => {
                const keys = Object.keys(perms)
                const all = keys.every((k) => selected.has(k))
                return (
                  <fieldset key={group} className="perm-group">
                    <legend>
                      <label>
                        <input type="checkbox" checked={all} onChange={(e) => toggleGroup(keys, e.target.checked)} /> {group}
                      </label>
                    </legend>
                    {Object.entries(perms).map(([key, label]) => (
                      <label key={key} className="perm-item">
                        <input type="checkbox" checked={selected.has(key)} onChange={() => toggle(key)} />
                        <span>{label}</span>
                      </label>
                    ))}
                  </fieldset>
                )
              })}
            </div>
          </>
        )}

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save role' : 'Create role'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

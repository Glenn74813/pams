import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { usersApi, type UserInput } from '../../api/endpoints'
import type { User } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { PasswordInput } from '../../components/PasswordInput'

interface Props {
  user: User | null
  onClose: () => void
  onSaved: (message: string) => void
}

export function UserFormModal({ user, onClose, onSaved }: Props) {
  const { user: me } = useAuth()
  const editing = user !== null
  const editingSelf = editing && user.id === me?.id
  const [form, setForm] = useState({
    name: user?.name ?? '',
    email: user?.email ?? '',
    password: '',
    password_confirmation: '',
    role_id: user?.role_id ? String(user.role_id) : '',
    department_id: user?.department_id ? String(user.department_id) : '',
  })
  const [errors, setErrors] = useState<Record<string, string[]>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const roles = useQuery({ queryKey: ['user-role-options'], queryFn: usersApi.roleOptions })
  const departments = useQuery({ queryKey: ['departments'], queryFn: usersApi.departments })

  const save = useMutation({
    mutationFn: () => {
      const payload: UserInput = {
        name: form.name,
        email: form.email,
        role_id: Number(form.role_id),
        department_id: Number(form.department_id),
      }
      if (form.password) {
        payload.password = form.password
        payload.password_confirmation = form.password_confirmation
      }
      return editing ? usersApi.update(user.id, payload) : usersApi.create(payload)
    },
    onSuccess: (res) => onSaved(res.message),
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setErrors(err.fieldErrors)
        setFormError(null)
      } else {
        setErrors({})
        setFormError(err instanceof Error ? err.message : 'Could not save the user.')
      }
    },
  })

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setErrors({})
    setFormError(null)
    save.mutate()
  }

  const fieldError = (key: string) => errors[key]?.[0]
  const roleDescription = roles.data?.data.find((r) => String(r.id) === form.role_id)?.description
  // A role the current user can't assign (e.g. Super Admin) isn't in the list; keep it shown when editing.
  const roleMissing = editing && form.role_id && roles.data && !roles.data.data.some((r) => String(r.id) === form.role_id)

  return (
    <Modal title={editing ? `Edit ${user.name}` : 'Add New User'} onClose={onClose}>
      <form onSubmit={submit}>
        {formError && <div className="alert alert-error">{formError}</div>}
        <div className="form-grid">
          <div className={'field' + (fieldError('name') ? ' invalid' : '')}>
            <label htmlFor="u-name">Full name *</label>
            <input id="u-name" value={form.name} onChange={set('name')} required minLength={2} maxLength={100} />
            {fieldError('name') && <span className="field-error">{fieldError('name')}</span>}
          </div>
          <div className={'field' + (fieldError('email') ? ' invalid' : '')}>
            <label htmlFor="u-email">Email *</label>
            <input id="u-email" type="email" value={form.email} onChange={set('email')} required maxLength={100} />
            {fieldError('email') && <span className="field-error">{fieldError('email')}</span>}
          </div>
          <div className={'field' + (fieldError('password') ? ' invalid' : '')}>
            <label htmlFor="u-password">{editing ? 'New password' : 'Password *'}</label>
            <PasswordInput
              id="u-password"
              value={form.password}
              onChange={set('password')}
              required={!editing}
              autoComplete="new-password"
              placeholder={editing ? 'Leave blank to keep' : ''}
            />
            {fieldError('password') ? (
              <span className="field-error">{fieldError('password')}</span>
            ) : (
              <span className="hint">8+ characters with upper and lower case, a number and a symbol.</span>
            )}
          </div>
          <div className="field">
            <label htmlFor="u-password2">{editing ? 'Confirm new password' : 'Confirm password *'}</label>
            <PasswordInput
              id="u-password2"
              value={form.password_confirmation}
              onChange={set('password_confirmation')}
              required={!editing || form.password !== ''}
              autoComplete="new-password"
            />
          </div>
          <div className={'field' + (fieldError('role_id') ? ' invalid' : '')}>
            <label htmlFor="u-role">Role *</label>
            <select id="u-role" value={form.role_id} onChange={set('role_id')} required disabled={editingSelf}>
              <option value="">Select role</option>
              {roleMissing && <option value={form.role_id}>{user.role?.name}</option>}
              {roles.data?.data.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
            {editingSelf && <span className="hint">You cannot change your own role.</span>}
            {fieldError('role_id') && <span className="field-error">{fieldError('role_id')}</span>}
          </div>
          <div className={'field' + (fieldError('department_id') ? ' invalid' : '')}>
            <label htmlFor="u-dept">Department *</label>
            <select id="u-dept" value={form.department_id} onChange={set('department_id')} required>
              <option value="">Select department</option>
              {departments.data?.data.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
            {fieldError('department_id') && <span className="field-error">{fieldError('department_id')}</span>}
          </div>
        </div>
        {roleDescription && (
          <div className="alert alert-info" style={{ marginTop: '1rem', marginBottom: 0 }}>
            <strong>{roles.data?.data.find((r) => String(r.id) === form.role_id)?.name}:</strong> {roleDescription}
          </div>
        )}
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create user'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { PasswordInput } from '../components/PasswordInput'
import { useAuth } from './AuthContext'

/** Shown instead of the app while the password is past the expiry set in Settings → System. */
export function ExpiredPassword() {
  const { user, refresh, logout } = useAuth()
  const [pw, setPw] = useState({ current_password: '', password: '', password_confirmation: '' })
  const [error, setError] = useState<string | null>(null)
  const change = useMutation({
    mutationFn: () => authApi.changePassword(pw),
    onSuccess: () => void refresh(),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not change the password.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    change.mutate()
  }
  return (
    <div className="page" style={{ maxWidth: 480, margin: '3rem auto' }}>
      <h1 className="page-title">Your password has expired</h1>
      <p>
        {user?.name}, your agency requires passwords to be changed regularly. Choose a new password to continue. It must be different from the
        current one, have upper and lower case, a number and a symbol.
      </p>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field">
          <label htmlFor="xp-current">Current password</label>
          <PasswordInput id="xp-current" value={pw.current_password} onChange={(e) => setPw({ ...pw, current_password: e.target.value })} autoComplete="current-password" required />
        </div>
        <div className="field">
          <label htmlFor="xp-new">New password</label>
          <PasswordInput id="xp-new" value={pw.password} onChange={(e) => setPw({ ...pw, password: e.target.value })} autoComplete="new-password" required />
        </div>
        <div className="field">
          <label htmlFor="xp-confirm">Confirm new password</label>
          <PasswordInput id="xp-confirm" value={pw.password_confirmation} onChange={(e) => setPw({ ...pw, password_confirmation: e.target.value })} autoComplete="new-password" required />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={() => void logout()}>
            Sign out
          </button>
          <button type="submit" className="btn btn-primary" disabled={change.isPending}>
            {change.isPending ? 'Saving…' : 'Change password'}
          </button>
        </div>
      </form>
    </div>
  )
}

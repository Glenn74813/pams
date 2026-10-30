import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './AuthContext'
import { ExpiredPassword } from './ExpiredPassword'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="page-loading">Loading…</div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.password_expired) return <ExpiredPassword />
  return children
}

/** Blocks pages the user's role cannot open, even when the URL is typed in directly. */
export function RequireModule({ module, children }: { module: string; children: ReactNode }) {
  const { canOpen } = useAuth()
  if (!canOpen(module)) {
    return (
      <div className="page">
        <div className="alert alert-error">You do not have access to this page.</div>
      </div>
    )
  }
  return children
}

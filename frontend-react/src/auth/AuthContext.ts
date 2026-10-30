import { createContext, useContext } from 'react'
import type { AuthUser } from '../api/types'

/** Set when the app signs someone out for inactivity, so the login page can say why. */
export const IDLE_FLAG = 'pams.signedOutIdle'

export type LoginOutcome = { ok: true } | { ok: false; twoFactorRequired: true }

export interface AuthState {
  user: AuthUser | null
  /** True until the stored token has been checked against /auth/me. */
  loading: boolean
  login: (email: string, password: string, otp?: string) => Promise<LoginOutcome>
  logout: (reason?: 'idle') => Promise<void>
  /** Reloads the signed-in user, e.g. after a forced password change. */
  refresh: () => Promise<void>
  /** Super Admin passes every check. */
  can: (...permissions: string[]) => boolean
  canOpen: (module: string) => boolean
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}

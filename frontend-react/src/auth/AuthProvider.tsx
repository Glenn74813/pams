import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { setUnauthorizedHandler, tokenStore } from '../api/client'
import { authApi } from '../api/endpoints'
import type { AuthUser } from '../api/types'
import { AuthContext, type AuthState, type LoginOutcome } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)

  const clearSession = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  // Any 401 (expired, revoked, user deleted) ends the session.
  useEffect(() => {
    setUnauthorizedHandler(clearSession)
  }, [clearSession])

  // Restore the session from a stored token.
  useEffect(() => {
    if (!tokenStore.get()) return
    let cancelled = false
    authApi
      .me()
      .then((me) => !cancelled && setUser(me))
      .catch(() => !cancelled && clearSession())
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [clearSession])

  const login = useCallback(async (email: string, password: string, otp?: string): Promise<LoginOutcome> => {
    const res = await authApi.login(email, password, otp)
    if (res.two_factor_required && !res.access_token) {
      return { ok: false, twoFactorRequired: true }
    }
    tokenStore.set(res.access_token!)
    setUser(await authApi.me())
    return { ok: true }
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      /* the token is discarded locally either way */
    }
    clearSession()
  }, [clearSession])

  const value = useMemo<AuthState>(() => {
    const can = (...permissions: string[]) =>
      !!user && (user.is_super_admin || permissions.some((p) => user.permissions.includes(p)))
    const canOpen = (module: string) => !!user && user.accessible_modules.includes(module)
    return { user, loading, login, logout, can, canOpen }
  }, [user, loading, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

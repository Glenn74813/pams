import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { setUnauthorizedHandler, tokenStore } from '../api/client'
import { authApi, settingsApi } from '../api/endpoints'
import type { AuthUser } from '../api/types'
import { AuthContext, IDLE_FLAG, type AuthState, type LoginOutcome } from './AuthContext'

const ACTIVITY_KEY = 'pams.lastActivity'
// When the current token was issued, and when a tab last started renewing it (so only one tab does).
const ISSUED_KEY = 'pams.tokenIssued'
const RENEW_KEY = 'pams.tokenRenewing'
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'wheel'] as const

function store(kind: 'local' | 'session', key: string, value?: string): string | null {
  try {
    const s = kind === 'local' ? localStorage : sessionStorage
    if (value !== undefined) s.setItem(key, value)
    return s.getItem(key)
  } catch {
    return null
  }
}

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
    store('local', ISSUED_KEY, String(Date.now()))
    setUser(await authApi.me())
    return { ok: true }
  }, [])

  const logout = useCallback(async (reason?: 'idle') => {
    try {
      await authApi.logout(reason)
    } catch {
      /* the token is discarded locally either way */
    }
    clearSession()
  }, [clearSession])

  // Idle sign-out (TOR 7.2): after the minutes set in Settings with no activity in any tab.
  useEffect(() => {
    if (!user) return
    let minutes = 0
    let done = false
    store('local', ACTIVITY_KEY, String(Date.now()))
    settingsApi
      .system()
      .then((s) => (minutes = s.idle_timeout_minutes))
      .catch(() => undefined)
    let lastWrite = Date.now()
    const touch = () => {
      const now = Date.now()
      if (now - lastWrite > 10_000) {
        lastWrite = now
        store('local', ACTIVITY_KEY, String(now))
      }
    }
    for (const e of ACTIVITY_EVENTS) window.addEventListener(e, touch, { passive: true })
    const timer = window.setInterval(() => {
      if (done) return
      const last = Number(store('local', ACTIVITY_KEY) ?? Date.now())
      // Tokens last 60 minutes: renew one in use (activity in the last 10 minutes) once it is 40 minutes old.
      // One tab renews; the others see the new token in shared storage.
      const issued = Number(store('local', ISSUED_KEY) ?? 0)
      const claim = Number(store('local', RENEW_KEY) ?? 0)
      if (Date.now() - last < 10 * 60_000 && Date.now() - issued > 40 * 60_000 && Date.now() - claim > 60_000) {
        store('local', RENEW_KEY, String(Date.now()))
        authApi
          .refresh()
          .then((r) => {
            tokenStore.set(r.access_token)
            store('local', ISSUED_KEY, String(Date.now()))
          })
          .catch(() => undefined)
      }
      if (!minutes) return
      if (Date.now() - last >= minutes * 60_000) {
        done = true
        store('session', IDLE_FLAG, String(minutes))
        void logout('idle')
      }
    }, 15_000)
    return () => {
      for (const e of ACTIVITY_EVENTS) window.removeEventListener(e, touch)
      window.clearInterval(timer)
    }
  }, [user, logout])

  const refresh = useCallback(async () => {
    setUser(await authApi.me())
  }, [])

  const value = useMemo<AuthState>(() => {
    const can = (...permissions: string[]) =>
      !!user && (user.is_super_admin || permissions.some((p) => user.permissions.includes(p)))
    const canOpen = (module: string) => !!user && user.accessible_modules.includes(module)
    return { user, loading, login, logout, refresh, can, canOpen }
  }, [user, loading, login, logout, refresh])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

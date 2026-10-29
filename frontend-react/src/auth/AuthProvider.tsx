import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { setUnauthorizedHandler, tokenStore } from '../api/client'
import { authApi, settingsApi } from '../api/endpoints'
import type { AuthUser } from '../api/types'
import { AuthContext, IDLE_FLAG, type AuthState, type LoginOutcome } from './AuthContext'

const ACTIVITY_KEY = 'pams.lastActivity'
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
      if (!minutes || done) return
      const last = Number(store('local', ACTIVITY_KEY) ?? Date.now())
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

  const value = useMemo<AuthState>(() => {
    const can = (...permissions: string[]) =>
      !!user && (user.is_super_admin || permissions.some((p) => user.permissions.includes(p)))
    const canOpen = (module: string) => !!user && user.accessible_modules.includes(module)
    return { user, loading, login, logout, can, canOpen }
  }, [user, loading, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { IDLE_FLAG, useAuth } from '../auth/AuthContext'
import { PasswordInput } from '../components/PasswordInput'
import './LoginPage.css'

function greeting() {
  const h = new Date().getHours()
  if (h < 12) return 'Good Morning'
  if (h < 18) return 'Good Afternoon'
  return 'Good Evening'
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [otp, setOtp] = useState('')
  const [twoFactor, setTwoFactor] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [showForgot, setShowForgot] = useState(false)
  const [idleMinutes] = useState(() => {
    try {
      const v = sessionStorage.getItem(IDLE_FLAG)
      sessionStorage.removeItem(IDLE_FLAG)
      return v
    } catch {
      return null
    }
  })

  const from = (location.state as { from?: string } | null)?.from ?? '/dashboard'
  if (user) return <Navigate to={from} replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const result = await login(email, password, twoFactor ? otp : undefined)
      if (!result.ok) {
        setTwoFactor(true)
        return
      }
      navigate(from, { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.body.two_factor_required) setTwoFactor(true)
        const remaining = err.body.remaining_attempts
        setError(
          typeof remaining === 'number' && remaining > 0
            ? `${err.message}. ${remaining} attempt${remaining === 1 ? '' : 's'} left before the account is locked.`
            : err.message,
        )
      } else {
        setError('Login failed. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-shell">
        <div className="brand-panel">
          <div className="brand-top">
            <div className="logo-circle">
              <img src="/avtech-logo.png" alt="AVTECH Solutions" />
            </div>
          </div>
          <div className="brand-text">
            <p className="brand-tag">Procurement &amp; Inventory Management System</p>
            <p className="brand-sub">Sign in to access your dashboard, requests, and reports.</p>
          </div>
        </div>

        <div className="form-panel">
          <div className="greeting">
            <h2>Hello!</h2>
            <h3>{greeting()}</h3>
          </div>

          <div className="form-card">
            <h4 className="form-title">Login your account</h4>
            <form onSubmit={submit} noValidate={false}>
              <div className="login-field">
                <label htmlFor="email">Email Address</label>
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@agency.gov.ph"
                  autoComplete="username"
                  required
                  autoFocus
                  disabled={twoFactor}
                />
              </div>

              <div className="login-field">
                <label htmlFor="password">Password</label>
                <PasswordInput
                  id="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  required
                  disabled={twoFactor}
                />
              </div>

              {twoFactor && (
                <div className="login-field">
                  <label htmlFor="otp">Authenticator Code</label>
                  <input
                    id="otp"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value)}
                    inputMode="numeric"
                    placeholder="6-digit code"
                    autoComplete="one-time-code"
                    autoFocus
                    required
                  />
                  <small className="hint">
                    Enter the 6-digit code from your authenticator app.{' '}
                    <button type="button" className="link-btn" onClick={() => { setTwoFactor(false); setOtp('') }}>
                      Use a different account
                    </button>
                  </small>
                </div>
              )}

              <div className="forgot">
                <button type="button" onClick={() => setShowForgot((s) => !s)}>
                  forgot password?
                </button>
              </div>
              {showForgot && (
                <div className="alert alert-info">Please contact your system administrator to reset your password.</div>
              )}

              {idleMinutes && !error && <div className="alert alert-info">You were signed out after {idleMinutes} minutes without activity.</div>}
              {error && <div className="alert alert-error">{error}</div>}

              <button type="submit" className="btn-login" disabled={busy}>
                {busy ? 'Verifying…' : twoFactor ? 'Verify & Login' : 'Login'}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}

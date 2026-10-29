import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { authApi, settingsApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { PasswordInput } from '../../components/PasswordInput'
import { useToast } from '../../components/toast-context'

const message = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

/** The signed-in person's own password, two-factor sign-in and signature image. */
export function AccountTab() {
  const { user } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const system = useQuery({ queryKey: ['settings-system'], queryFn: settingsApi.system })
  const me = useQuery({ queryKey: ['me'], queryFn: authApi.me })
  const refreshMe = () => void queryClient.invalidateQueries({ queryKey: ['me'] })

  const [pw, setPw] = useState({ current_password: '', password: '', password_confirmation: '' })
  const [pwError, setPwError] = useState<string | null>(null)
  const changePw = useMutation({
    mutationFn: () => authApi.changePassword(pw),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setPw({ current_password: '', password: '', password_confirmation: '' })
      setPwError(null)
    },
    onError: (err) => setPwError(message(err, 'Could not change the password.')),
  })

  const [setup, setSetup] = useState<{ secret: string; otpauth_uri: string } | null>(null)
  const [otp, setOtp] = useState('')
  const [disablePw, setDisablePw] = useState('')
  const [tfError, setTfError] = useState<string | null>(null)
  const twoFactorOn = me.data?.two_factor_enabled ?? user?.two_factor_enabled ?? false
  const start2fa = useMutation({ mutationFn: authApi.setupTwoFactor, onSuccess: (r) => { setSetup(r); setTfError(null) }, onError: (e) => setTfError(message(e, 'Could not start setup.')) })
  const enable2fa = useMutation({
    mutationFn: () => authApi.enableTwoFactor(otp),
    onSuccess: (r) => { toast(r.message, 'success'); setSetup(null); setOtp(''); setTfError(null); refreshMe() },
    onError: (e) => setTfError(message(e, 'Could not enable two-factor sign-in.')),
  })
  const disable2fa = useMutation({
    mutationFn: () => authApi.disableTwoFactor(disablePw),
    onSuccess: (r) => { toast(r.message, 'success'); setDisablePw(''); setTfError(null); refreshMe() },
    onError: (e) => setTfError(message(e, 'Could not disable two-factor sign-in.')),
  })

  const [sigError, setSigError] = useState<string | null>(null)
  const upload = useMutation({
    mutationFn: (f: File) => authApi.uploadSignature(f),
    onSuccess: (r) => { toast(r.message, 'success'); setSigError(null); refreshMe() },
    onError: (e) => setSigError(message(e, 'Could not upload the signature.')),
  })
  const removeSig = useMutation({ mutationFn: authApi.removeSignature, onSuccess: (r) => { toast(r.message, 'success'); refreshMe() }, onError: (e) => setSigError(message(e, 'Could not remove it.')) })
  const signature = me.data?.signature_image

  return (
    <div className="settings-grid">
      <section className="settings-card">
        <h2>Change password</h2>
        <form onSubmit={(e: FormEvent) => { e.preventDefault(); changePw.mutate() }}>
          {pwError && <div className="alert alert-error">{pwError}</div>}
          <div className="field">
            <label htmlFor="pw-current">Current password</label>
            <PasswordInput id="pw-current" value={pw.current_password} onChange={(e) => setPw({ ...pw, current_password: e.target.value })} autoComplete="current-password" required />
          </div>
          <div className="field">
            <label htmlFor="pw-new">New password</label>
            <PasswordInput id="pw-new" value={pw.password} onChange={(e) => setPw({ ...pw, password: e.target.value })} autoComplete="new-password" required />
            <span className="hint">
              At least {system.data?.password_min_length ?? 8} characters with upper and lower case, a number and a symbol. Common passwords and your email name are not allowed.
            </span>
          </div>
          <div className="field">
            <label htmlFor="pw-confirm">Confirm new password</label>
            <PasswordInput id="pw-confirm" value={pw.password_confirmation} onChange={(e) => setPw({ ...pw, password_confirmation: e.target.value })} autoComplete="new-password" required />
          </div>
          <button className="btn btn-primary" type="submit" disabled={changePw.isPending}>
            {changePw.isPending ? 'Saving…' : 'Change password'}
          </button>
        </form>
      </section>

      <section className="settings-card">
        <h2>Two-factor sign-in</h2>
        {tfError && <div className="alert alert-error">{tfError}</div>}
        {twoFactorOn ? (
          <form onSubmit={(e: FormEvent) => { e.preventDefault(); disable2fa.mutate() }}>
            <p>
              <span className="badge st-ok">On</span> Sign-in asks for a 6-digit code from your authenticator app.
            </p>
            <div className="field">
              <label htmlFor="tf-pw">Password, to turn it off</label>
              <PasswordInput id="tf-pw" value={disablePw} onChange={(e) => setDisablePw(e.target.value)} autoComplete="current-password" required />
            </div>
            <button className="btn btn-danger" type="submit" disabled={disable2fa.isPending}>
              Turn off
            </button>
          </form>
        ) : setup ? (
          <form onSubmit={(e: FormEvent) => { e.preventDefault(); enable2fa.mutate() }}>
            <p>Add an account in your authenticator app (Google Authenticator, Microsoft Authenticator, Authy) using this setup key, then enter the code it shows.</p>
            <div className="setup-key mono">{setup.secret.replace(/(.{4})/g, '$1 ').trim()}</div>
            <p className="small muted">
              On a phone, <a href={setup.otpauth_uri}>open this link</a> to add it directly.
            </p>
            <div className="field">
              <label htmlFor="tf-otp">6-digit code</label>
              <input id="tf-otp" value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))} inputMode="numeric" autoComplete="one-time-code" required />
            </div>
            <div className="workflow-actions">
              <button className="btn btn-primary" type="submit" disabled={enable2fa.isPending || otp.length !== 6}>
                Turn on
              </button>
              <button className="btn btn-secondary" type="button" onClick={() => setSetup(null)}>
                Cancel
              </button>
            </div>
          </form>
        ) : (
          <>
            <p>
              <span className="badge badge-muted">Off</span> Add a code from your phone to every sign-in.
            </p>
            <button className="btn btn-primary" onClick={() => start2fa.mutate()} disabled={start2fa.isPending}>
              Set up
            </button>
          </>
        )}
      </section>

      <section className="settings-card">
        <h2>My signature</h2>
        <p className="small muted">Printed on forms where you are a signatory. PNG or JPG on a white or transparent background.</p>
        {sigError && <div className="alert alert-error">{sigError}</div>}
        {signature ? <img className="signature-preview" src={signature} alt="Your signature" /> : <p className="muted">No signature uploaded.</p>}
        <div className="workflow-actions">
          <label className="btn btn-secondary">
            {signature ? 'Replace' : 'Upload'}
            <input
              type="file"
              accept="image/png,image/jpeg"
              hidden
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) upload.mutate(f)
                e.target.value = ''
              }}
            />
          </label>
          {signature && (
            <button className="btn btn-danger" onClick={() => removeSig.mutate()} disabled={removeSig.isPending}>
              Remove
            </button>
          )}
        </div>
      </section>
    </div>
  )
}

import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { settingsApi } from '../../api/endpoints'
import type { SystemSettings } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/toast-context'

export function SystemTab() {
  const { can } = useAuth()
  const q = useQuery({ queryKey: ['settings-system'], queryFn: settingsApi.system })
  return q.data ? <SystemForm key={JSON.stringify(q.data)} initial={q.data} editable={can('edit_settings', 'manage_system_settings')} /> : null
}

function SystemForm({ initial, editable }: { initial: SystemSettings; editable: boolean }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [f, setF] = useState(initial)
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => settingsApi.updateSystem(f),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['settings-system'] })
      void queryClient.invalidateQueries({ queryKey: ['inventory-config'] })
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  const num = (key: keyof SystemSettings) => (e: ChangeEvent<HTMLInputElement>) => setF({ ...f, [key]: Number(e.target.value) })
  return (
    <section className="settings-card">
      <h2>System</h2>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field">
          <label htmlFor="sys-cap">COA capitalization threshold (₱)</label>
          <input id="sys-cap" type="number" min={1} step="0.01" value={f.capitalization_threshold} onChange={num('capitalization_threshold')} disabled={!editable} required />
          <span className="hint">Unit cost at or above this is PPE; below it, semi-expendable property.</span>
        </div>
        <div className="field">
          <label htmlFor="sys-idle">Sign out after inactivity (minutes)</label>
          <input id="sys-idle" type="number" min={0} max={480} value={f.idle_timeout_minutes} onChange={num('idle_timeout_minutes')} disabled={!editable} />
          <span className="hint">5 to 480 minutes; 0 turns it off. Applies to everyone at their next page load.</span>
        </div>
        <div className="field">
          <label htmlFor="sys-pw">Minimum password length</label>
          <input id="sys-pw" type="number" min={8} max={64} value={f.password_min_length} onChange={num('password_min_length')} disabled={!editable} />
          <span className="hint">8 to 64. Applies to new and changed passwords.</span>
        </div>
        <div className="field">
          <label htmlFor="sys-url">App address</label>
          <input id="sys-url" type="url" placeholder={window.location.origin} value={f.app_url} onChange={(e) => setF({ ...f, app_url: e.target.value })} disabled={!editable} />
          <span className="hint">
            The address people open the system at, e.g. https://pams.agency.gov.ph. QR labels link here, so set it before printing stickers; labels
            printed earlier keep the old address.{' '}
            {editable && !f.app_url && (
              <button type="button" className="link-btn" onClick={() => setF({ ...f, app_url: window.location.origin })}>
                Use {window.location.origin}
              </button>
            )}
          </span>
        </div>
        {editable && (
          <button className="btn btn-primary" type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
        )}
      </form>
    </section>
  )
}

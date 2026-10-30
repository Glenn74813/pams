import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { settingsApi } from '../../api/endpoints'
import type { MisSettings } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/toast-context'

const FIELDS: [keyof MisSettings, string, string][] = [
  ['agency_name', 'Agency name', 'Printed as "Entity Name" on COA forms'],
  ['fund_cluster', 'Fund cluster', 'e.g. 01 — Regular Agency Fund'],
  ['agency_head_name', 'Head of agency', ''],
  ['agency_head_title', 'Head of agency title', 'e.g. Regional Director'],
  ['agency_address', 'Address', ''],
]

/** Agency profile printed on COA forms, the expiry warning window, and logos for generated documents. */
export function AgencyTab() {
  const { can } = useAuth()
  const editable = can('edit_settings', 'manage_agency_profile')
  const mis = useQuery({ queryKey: ['settings-mis'], queryFn: settingsApi.mis })
  return (
    <div className="settings-grid">
      {mis.data && <ProfileForm key={JSON.stringify(mis.data)} initial={mis.data} editable={editable} />}
      <Logos editable={editable} />
    </div>
  )
}

function ProfileForm({ initial, editable }: { initial: MisSettings; editable: boolean }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [f, setF] = useState<MisSettings>(initial)
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => settingsApi.updateMis(f),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['settings-mis'] })
      void queryClient.invalidateQueries({ queryKey: ['inventory-config'] })
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  return (
    <section className="settings-card">
      <h2>Agency profile</h2>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        {FIELDS.map(([key, label, hint]) => (
          <div className="field" key={key}>
            <label htmlFor={`mis-${key}`}>{label}</label>
            <input id={`mis-${key}`} value={String(f[key] ?? '')} onChange={(e) => setF({ ...f, [key]: e.target.value })} disabled={!editable} maxLength={key === 'agency_address' ? 500 : 255} />
            {hint && <span className="hint">{hint}</span>}
          </div>
        ))}
        <div className="field">
          <label htmlFor="mis-fefo">Expiry warning (days)</label>
          <input id="mis-fefo" type="number" min={0} max={3650} value={f.fefo_expiry_warning_days} onChange={(e) => setF({ ...f, fefo_expiry_warning_days: Number(e.target.value) })} disabled={!editable} />
          <span className="hint">Lots expiring within this many days are flagged on the expiring-stock list and when issuing.</span>
        </div>
        {editable && (
          <button className="btn btn-primary" type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save profile'}
          </button>
        )}
      </form>
    </section>
  )
}

const POSITION_LABEL: Record<string, string> = {
  'top-left': 'Top left',
  'top-center': 'Top centre',
  'top-right': 'Top right',
  'bottom-left': 'Bottom left',
  'bottom-center': 'Bottom centre',
  'bottom-right': 'Bottom right',
}

function Logos({ editable }: { editable: boolean }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const q = useQuery({ queryKey: ['settings-logos'], queryFn: settingsApi.logos })
  const [file, setFile] = useState<File | null>(null)
  const [position, setPosition] = useState('top-left')
  const [label, setLabel] = useState('')
  const [error, setError] = useState<string | null>(null)
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['settings-logos'] })
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const add = useMutation({
    mutationFn: () => settingsApi.addLogo(file!, position, label),
    onSuccess: () => { toast('Logo added.', 'success'); setFile(null); setLabel(''); setError(null); refresh() },
    onError: fail,
  })
  const move = useMutation({ mutationFn: ({ id, pos }: { id: number; pos: string }) => settingsApi.updateLogo(id, { position: pos }), onSuccess: refresh, onError: fail })
  const remove = useMutation({ mutationFn: (id: number) => settingsApi.removeLogo(id), onSuccess: (r) => { toast(r.message, 'success'); refresh() }, onError: fail })
  return (
    <section className="settings-card">
      <h2>Document logos</h2>
      <p className="small muted">Printed along the top or bottom of the PR, PO, IAR, RIS / ICS / PAR, gate pass and property slips. The COA Excel forms keep their prescribed layout without logos.</p>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="logo-list">
        {q.data?.logos.map((l) => (
          <div className="logo-row" key={l.id}>
            {l.data_uri ? <img src={l.data_uri} alt={l.label ?? 'Logo'} /> : <span className="muted small">file missing</span>}
            <div className="logo-meta">
              <strong>{l.label ?? 'Logo'}</strong>
              {editable ? (
                <select value={l.position} onChange={(e) => move.mutate({ id: l.id, pos: e.target.value })} aria-label={`Position of ${l.label ?? 'logo'}`}>
                  {q.data.positions.map((p) => (
                    <option key={p} value={p}>
                      {POSITION_LABEL[p] ?? p}
                    </option>
                  ))}
                </select>
              ) : (
                <span className="small">{POSITION_LABEL[l.position] ?? l.position}</span>
              )}
            </div>
            {editable && (
              <button className="btn btn-danger btn-sm" onClick={() => remove.mutate(l.id)}>
                Remove
              </button>
            )}
          </div>
        ))}
        {q.data && q.data.logos.length === 0 && <p className="muted">No logos yet.</p>}
      </div>
      {editable && (
        <form className="logo-add" onSubmit={(e: FormEvent) => { e.preventDefault(); if (file) add.mutate() }}>
          <input type="file" accept="image/png,image/jpeg,image/svg+xml" onChange={(e) => setFile(e.target.files?.[0] ?? null)} aria-label="Logo file" />
          <select value={position} onChange={(e) => setPosition(e.target.value)} aria-label="Position">
            {Object.entries(POSITION_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
          <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Label (e.g. Agency seal)" maxLength={100} aria-label="Label" />
          <button className="btn btn-primary" type="submit" disabled={!file || add.isPending}>
            Add logo
          </button>
        </form>
      )}
    </section>
  )
}

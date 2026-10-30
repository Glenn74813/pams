import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { locationsApi, maintenanceApi, photoUrl } from '../../api/tor'
import type { Asset } from '../../api/types'
import { useToast } from '../../components/toast-context'
import { formatDate, formatDateTime, formatPeso } from '../../lib/format'
import { MaintenanceForm, MAINTENANCE_STATUS, MAINTENANCE_TYPE } from '../maintenance/MaintenanceForms'
import { Badge } from './pickers'

/**
 * The picture of an asset or item (TOR 1.4.4, 1.5.4). Images are fetched with the session token,
 * so they are shown from object URLs rather than plain <img src> links.
 */
export function PhotoPanel({ path, hasPhoto, canEdit, onUpload, onRemove, label }: {
  path: string
  hasPhoto: boolean
  canEdit: boolean
  onUpload: (file: File) => Promise<unknown>
  onRemove: () => Promise<unknown>
  label: string
}) {
  const toast = useToast()
  const input = useRef<HTMLInputElement>(null)
  const [version, setVersion] = useState(0)
  const [busy, setBusy] = useState(false)
  const photo = useQuery({ queryKey: ['photo', path, version], queryFn: () => photoUrl(path), enabled: hasPhoto, staleTime: Infinity })
  useEffect(() => {
    const url = photo.data
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [photo.data])

  const run = async (work: () => Promise<unknown>, done: string) => {
    setBusy(true)
    try {
      await work()
      setVersion((v) => v + 1)
      toast(done, 'success')
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not save the photo.', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <figure className="photo-panel">
      {hasPhoto && photo.data ? (
        <a href={photo.data} target="_blank" rel="noreferrer" title="Open full size">
          <img src={photo.data} alt={label} />
        </a>
      ) : (
        <div className="photo-empty">{hasPhoto && photo.isPending ? 'Loading…' : 'No photo yet'}</div>
      )}
      {canEdit && (
        <figcaption className="no-print">
          <input
            ref={input}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            capture="environment"
            hidden
            onChange={(e) => {
              const f = e.target.files?.[0]
              e.target.value = ''
              if (f) void run(() => onUpload(f), 'Photo saved.')
            }}
          />
          <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={() => input.current?.click()}>
            {hasPhoto ? 'Replace photo' : 'Add photo'}
          </button>
          {hasPhoto && (
            <button type="button" className="btn btn-outline btn-sm" disabled={busy} onClick={() => void run(onRemove, 'Photo removed.')}>
              Remove
            </button>
          )}
        </figcaption>
      )}
    </figure>
  )
}

/** Moves the asset to another location; the accountable person does not change. */
export function MoveLocationForm({ asset, onCancel, onDone }: { asset: Asset; onCancel: () => void; onDone: (message: string) => void }) {
  const locations = useQuery({ queryKey: ['locations', 'active'], queryFn: () => locationsApi.list(true) })
  const [to, setTo] = useState(asset.location?.id ? String(asset.location.id) : '')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => locationsApi.move(asset.id, { location_id: to ? Number(to) : null, remarks: remarks || null }),
    onSuccess: (r) => onDone(r.message),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not move the asset.'),
  })
  return (
    <form className="inline-panel" onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}>
      <h4>Move {asset.tag_number}</h4>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="loc-to">New location</label>
          <select id="loc-to" value={to} onChange={(e) => setTo(e.target.value)}>
            <option value="">No location</option>
            {locations.data?.map((l) => (
              <option key={l.id} value={l.id}>
                {l.name}
                {l.building ? ` · ${l.building}` : ''}
              </option>
            ))}
          </select>
          {locations.data?.length === 0 && <span className="hint">No locations yet; add them in Settings → Locations.</span>}
        </div>
        <div className="field">
          <label htmlFor="loc-rem">Remarks</label>
          <input id="loc-rem" value={remarks} maxLength={500} onChange={(e) => setRemarks(e.target.value)} placeholder="e.g. Moved for the office renovation" />
        </div>
      </div>
      <div className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Moving…' : 'Move'}
        </button>
      </div>
    </form>
  )
}

export function LocationHistory({ assetId }: { assetId: number }) {
  const moves = useQuery({ queryKey: ['asset-locations', assetId], queryFn: () => locationsApi.history(assetId) })
  return (
    <div className="table-wrap">
      <table className="table table-compact">
        <thead>
          <tr>
            <th>When</th>
            <th>From</th>
            <th>To</th>
            <th>By</th>
            <th>Remarks</th>
          </tr>
        </thead>
        <tbody>
          {moves.data?.map((m) => (
            <tr key={m.id}>
              <td>{formatDateTime(m.moved_at)}</td>
              <td>{m.from ?? '—'}</td>
              <td>{m.to ?? '—'}</td>
              <td>{m.moved_by ?? '—'}</td>
              <td className="clip">{m.remarks ?? ''}</td>
            </tr>
          ))}
          {moves.data?.length === 0 && (
            <tr>
              <td colSpan={5} className="empty">
                Not moved yet.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

/** Maintenance of one asset, with a button to schedule more. */
export function AssetMaintenance({ asset, canManage }: { asset: Asset; canManage: boolean }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [adding, setAdding] = useState(false)
  const list = useQuery({ queryKey: ['maintenance', 'asset', asset.id], queryFn: () => maintenanceApi.list({ property_id: asset.id }) })
  return (
    <>
      {canManage && asset.status !== 'disposed' && !adding && (
        <div className="section-header" style={{ marginBottom: '0.5rem' }}>
          <span />
          <button className="btn btn-outline btn-sm" onClick={() => setAdding(true)}>
            + Schedule maintenance
          </button>
        </div>
      )}
      {adding && (
        <MaintenanceForm
          asset={asset}
          onClose={() => setAdding(false)}
          onSaved={() => {
            setAdding(false)
            toast('Maintenance scheduled.', 'success')
            void queryClient.invalidateQueries({ queryKey: ['maintenance'] })
          }}
        />
      )}
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Due</th>
              <th>Type</th>
              <th>Work</th>
              <th>Status</th>
              <th>Done</th>
              <th>Cost</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((m) => (
              <tr key={m.id}>
                <td>{formatDate(m.scheduled_date)}</td>
                <td>{MAINTENANCE_TYPE[m.maintenance_type]}</td>
                <td className="clip">
                  {m.description}
                  {m.interval_months ? <span className="muted"> · every {m.interval_months} mo.</span> : null}
                </td>
                <td>
                  <Badge map={MAINTENANCE_STATUS} value={m.overdue ? 'overdue' : m.status} />
                </td>
                <td>{m.completed_date ? `${formatDate(m.completed_date)}${m.performed_by ? ' · ' + m.performed_by : ''}` : '—'}</td>
                <td>{m.cost != null ? formatPeso(m.cost) : '—'}</td>
              </tr>
            ))}
            {list.data?.data.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No maintenance recorded.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}

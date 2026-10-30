import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { maintenanceApi, type Maintenance, type MaintenanceType } from '../../api/tor'
import type { Asset } from '../../api/types'
import { Modal } from '../../components/Modal'
import { today } from '../../lib/format'
import { AssetPicker } from '../assets/pickers'

export const MAINTENANCE_TYPE: Record<MaintenanceType, string> = {
  preventive: 'Preventive',
  corrective: 'Corrective / repair',
  inspection: 'Inspection',
  calibration: 'Calibration',
}

export const MAINTENANCE_STATUS: Record<string, [string, string]> = {
  scheduled: ['Scheduled', 'st-partial'],
  overdue: ['Overdue', 'st-bad'],
  done: ['Done', 'st-ok'],
  cancelled: ['Cancelled', 'badge-muted'],
}

/** Schedules maintenance (or reschedules one that is still open); a repeat interval re-schedules it each time it is done. */
export function MaintenanceForm({ asset, entry, onClose, onSaved }: { asset?: Asset; entry?: Maintenance; onClose: () => void; onSaved: () => void }) {
  const [picked, setPicked] = useState<Asset | null>(asset ?? null)
  const [type, setType] = useState<MaintenanceType>(entry?.maintenance_type ?? 'preventive')
  const [description, setDescription] = useState(entry?.description ?? '')
  const [date, setDate] = useState(entry?.scheduled_date ?? today())
  const [interval, setInterval] = useState(entry?.interval_months ? String(entry.interval_months) : '')
  const [remarks, setRemarks] = useState(entry?.remarks ?? '')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      const input = {
        property_id: entry ? entry.property_id : picked!.id,
        maintenance_type: type,
        description,
        scheduled_date: date,
        interval_months: interval ? Number(interval) : null,
        remarks: remarks || null,
      }
      return entry ? maintenanceApi.update(entry.id, input) : maintenanceApi.schedule(input)
    },
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the schedule.'),
  })
  return (
    <Modal title={entry ? `Reschedule — ${entry.tag_number}` : 'Schedule maintenance'} onClose={onClose} width={620}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); if (!entry && !picked) return setError('Choose the asset.'); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field" style={{ marginBottom: '1rem' }}>
          <label htmlFor="mt-asset">Asset *</label>
          {entry ? (
            <div className="chosen">
              <span>
                <span className="mono">{entry.tag_number}</span> {entry.item_name}
              </span>
            </div>
          ) : picked ? (
            <div className="chosen">
              <span>
                <span className="mono">{picked.tag_number}</span> {picked.item?.name}
              </span>
              {!asset && (
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setPicked(null)}>
                  Change
                </button>
              )}
            </div>
          ) : (
            <AssetPicker id="mt-asset" filter={(a) => (a.status === 'disposed' ? 'disposed' : null)} onPick={setPicked} />
          )}
        </div>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="mt-type">Type *</label>
            <select id="mt-type" value={type} onChange={(e) => setType(e.target.value as MaintenanceType)}>
              {Object.entries(MAINTENANCE_TYPE).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="mt-date">Due on *</label>
            <input id="mt-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
          </div>
          <div className="field span-2">
            <label htmlFor="mt-desc">Work to do *</label>
            <input id="mt-desc" value={description} maxLength={500} onChange={(e) => setDescription(e.target.value)} required placeholder="e.g. Aircon cleaning and freon check" />
          </div>
          <div className="field">
            <label htmlFor="mt-int">Repeat every (months)</label>
            <input id="mt-int" type="number" min={1} max={120} value={interval} onChange={(e) => setInterval(e.target.value)} placeholder="Once only" />
            <span className="hint">When done, the next one is scheduled this many months later.</span>
          </div>
          <div className="field">
            <label htmlFor="mt-rem">Remarks</label>
            <input id="mt-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : entry ? 'Save changes' : 'Schedule'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export function CompleteForm({ entry, onClose, onSaved }: { entry: Maintenance; onClose: () => void; onSaved: (message: string) => void }) {
  const [date, setDate] = useState(today())
  const [by, setBy] = useState('')
  const [cost, setCost] = useState('')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => maintenanceApi.complete(entry.id, { completed_date: date, performed_by: by || null, cost: cost ? Number(cost) : null, remarks: remarks || null }),
    onSuccess: (r) => onSaved(r.message),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not record the work.'),
  })
  return (
    <Modal title={`Done — ${entry.tag_number}`} onClose={onClose} width={560}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <p className="muted">{entry.description}</p>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="mc-date">Date done</label>
            <input id="mc-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="mc-cost">Cost (₱)</label>
            <input id="mc-cost" type="number" min={0} step="0.01" value={cost} onChange={(e) => setCost(e.target.value)} />
          </div>
          <div className="field span-2">
            <label htmlFor="mc-by">Done by</label>
            <input id="mc-by" value={by} maxLength={255} onChange={(e) => setBy(e.target.value)} placeholder="Technician or service provider" />
          </div>
          <div className="field span-2">
            <label htmlFor="mc-rem">Findings / remarks</label>
            <input id="mc-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        {entry.interval_months && <div className="alert alert-info">The next one will be scheduled {entry.interval_months} month(s) after the date done.</div>}
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Record as done'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

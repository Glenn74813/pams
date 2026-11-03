import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { assetsApi, inventoryApi } from '../../api/endpoints'
import type { Asset, AssetMove } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatDate, formatPeso, today } from '../../lib/format'
import { AssetSlip, type AssetSlipKind } from './AssetSlip'
import { CONDITIONS, GATE_STATUS, MOVE_LABEL, STATUS_LABEL, cap } from './labels'
import { Badge, PersonSelect } from './pickers'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'
import { ExcelButton } from '../../components/ExcelButton'
import { QrLabelSheet } from '../../components/QrLabelSheet'
import { qrApi } from '../../api/endpoints'
import { AssemblyPanel } from './AssemblyPanel'
import { useAuth } from '../../auth/AuthContext'
import { photosApi } from '../../api/tor'
import { AssetMaintenance, LocationHistory, MoveLocationForm, PhotoPanel } from './AssetExtras'

type Tab = 'details' | 'history' | 'location' | 'maintenance' | 'depreciation' | 'gate'
type Action = 'transfer' | 'return' | 'issue' | 'edit' | 'move' | null

interface Props {
  assetId: number
  onClose: () => void
  onChanged: (message: string) => void
}

export function AssetDetailModal({ assetId, onClose, onChanged }: Props) {
  const [tab, setTab] = useState<Tab>('details')
  const [qrOpen, setQrOpen] = useState(false)
  const { can } = useAuth()
  const [action, setAction] = useState<Action>(null)
  const [slip, setSlip] = useState<{ kind: AssetSlipKind; move?: AssetMove } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const asset = useQuery({ queryKey: ['asset', assetId], queryFn: () => assetsApi.get(assetId) })
  const history = useQuery({ queryKey: ['asset-history', assetId], queryFn: () => assetsApi.history(assetId), enabled: tab === 'history' })
  const dep = useQuery({ queryKey: ['asset-dep', assetId], queryFn: () => assetsApi.depreciation(assetId), enabled: tab === 'depreciation' })
  const gates = useQuery({ queryKey: ['asset-gates', assetId], queryFn: () => assetsApi.gatePasses(assetId), enabled: tab === 'gate', retry: false })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })

  const status = useMutation({
    mutationFn: (act: string) => assetsApi.status(assetId, act),
    onSuccess: (a) => {
      asset.refetch()
      onChanged(`${a.tag_number} is now ${STATUS_LABEL[a.status][0].toLowerCase()}.`)
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not change the status.'),
  })

  const a = asset.data
  if (a && slip) {
    return (
      <Modal title={`${a.tag_number} — print`} onClose={onClose} width={900}>
        <div className="slip-toolbar no-print">
          <button className="btn btn-secondary btn-sm" onClick={() => setSlip(null)}>
            ‹ Back
          </button>
          <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
            🖨 Print
          </button>
        </div>
        <AssetSlip kind={slip.kind} asset={a} move={slip.move} config={config.data} />
      </Modal>
    )
  }

  return (
    <Modal title={a ? `${a.tag_number} — ${a.item?.name ?? ''}` : 'Asset'} onClose={onClose} width={960}>
      {asset.isPending && <div className="page-loading">Loading…</div>}
      {asset.isError && <div className="alert alert-error">{(asset.error as Error).message}</div>}
      {a && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <div className="asset-actions no-print">
            {a.can_transfer && a.holder && a.status === 'assigned' && (
              <button className="btn btn-outline btn-sm" onClick={() => setAction('transfer')}>
                ➜ Transfer
              </button>
            )}
            {a.can_transfer && a.holder && (
              <button className="btn btn-outline btn-sm" onClick={() => setAction('return')}>
                ↩ Record return
              </button>
            )}
            {a.can_transfer && !a.holder && a.status === 'available' && (
              <button className="btn btn-outline btn-sm" onClick={() => setAction('issue')}>
                Issue to someone
              </button>
            )}
            {a.can_edit && (a.status === 'assigned' || a.status === 'available') && (
              <button className="btn btn-outline btn-sm" onClick={() => status.mutate('repair')}>
                🔧 Send for repair
              </button>
            )}
            {a.can_edit && a.status === 'under_repair' && (
              <button className="btn btn-outline btn-sm" onClick={() => status.mutate('in_service')}>
                Back in service
              </button>
            )}
            {a.can_edit && !a.holder && (a.status === 'available' || a.status === 'under_repair') && (
              <button className="btn btn-outline btn-sm" onClick={() => status.mutate('retire')}>
                Retire
              </button>
            )}
            <button className="btn btn-outline btn-sm" onClick={() => setQrOpen(true)}>
              ▦ QR sticker
            </button>
            {a.can_edit && a.status === 'lost' && (
              <button className="btn btn-outline btn-sm" onClick={() => status.mutate('found')}>
                Mark found
              </button>
            )}
            {a.can_edit && a.status === 'retired' && (
              <button className="btn btn-outline btn-sm" onClick={() => status.mutate('reinstate')}>
                Reinstate
              </button>
            )}
            {(a.can_edit || a.can_transfer) && (
              <button className="btn btn-outline btn-sm" onClick={() => setAction('move')}>
                ⌖ Move location
              </button>
            )}
            {a.can_edit && (
              <button className="btn btn-outline btn-sm" onClick={() => setAction('edit')}>
                ✎ Edit details
              </button>
            )}
            {a.accountability_number && a.holder && (
              <button className="btn btn-outline btn-sm" onClick={() => setSlip({ kind: 'accountability' })}>
                🖨 {a.item?.classification === 'ppe' ? 'PAR' : 'ICS'}
              </button>
            )}
            {can('view_properties') && <ExcelButton path={`/reports/property-card/${a.id}/export`} name={`PropertyCard_${a.tag_number}.xlsx`} label="Property Card" />}
          </div>

          {action === 'move' && (
            <MoveLocationForm
              asset={a}
              onCancel={() => setAction(null)}
              onDone={(msg) => {
                setAction(null)
                asset.refetch()
                onChanged(msg)
              }}
            />
          )}
          {action && action !== 'edit' && action !== 'move' && (
            <MoveForm
              asset={a}
              kind={action}
              onCancel={() => setAction(null)}
              onDone={(msg, move) => {
                setAction(null)
                asset.refetch()
                history.refetch()
                onChanged(msg)
                setSlip({ kind: 'move', move })
              }}
            />
          )}
          {action === 'edit' && (
            <EditForm
              asset={a}
              onCancel={() => setAction(null)}
              onDone={(msg) => {
                setAction(null)
                asset.refetch()
                onChanged(msg)
              }}
            />
          )}

          <div className="tabs">
            {(['details', 'history', 'location', 'maintenance', 'depreciation', 'gate'] as Tab[]).map((t) => (
              <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>
                {t === 'gate' ? 'Gate passes' : cap(t)}
              </button>
            ))}
          </div>

          {tab === 'details' && (
            <div className="asset-details">
            <PhotoPanel
              path={photosApi.asset(a.id)}
              hasPhoto={a.has_photo}
              canEdit={a.can_edit}
              label={`${a.tag_number} ${a.item?.name ?? ''}`}
              onUpload={async (f) => { await photosApi.uploadAsset(a.id, f); await asset.refetch() }}
              onRemove={async () => { await photosApi.removeAsset(a.id); await asset.refetch() }}
            />
            <dl className="detail-grid">
              <dt>Status</dt>
              <dd>
                <Badge map={STATUS_LABEL} value={a.status} /> <span className="muted">condition {a.condition}</span>
              </dd>
              <dt>Accountable person</dt>
              <dd>{a.holder ? `${a.holder.name}${a.holder_office ? ' · ' + a.holder_office : ''} since ${formatDate(a.assigned_date)}` : 'Returned to the property office'}</dd>
              <dt>Location</dt>
              <dd>{a.location?.name ?? '—'}</dd>
              <dt>Document</dt>
              <dd className="mono">{a.accountability_number ?? '—'}</dd>
              <dt>Item</dt>
              <dd>
                {a.item?.code} · {a.item?.name} {a.brand ? `· ${a.brand}` : ''}
              </dd>
              <dt>Serial no.</dt>
              <dd>{a.serial_number ?? '—'}</dd>
              {a.specifications && (
                <>
                  <dt>Specifications</dt>
                  <dd>{a.specifications}</dd>
                </>
              )}
              <dt>Acquired</dt>
              <dd>
                {formatDate(a.acquisition_date)} for {formatPeso(a.acquisition_cost)}
              </dd>
              {a.is_depreciable && (
                <>
                  <dt>Book value</dt>
                  <dd>
                    {formatPeso(a.book_value)} <span className="muted">(accumulated depreciation {formatPeso(a.accumulated_depreciation)})</span>
                  </dd>
                </>
              )}
              <dt>Warranty</dt>
              <dd>{a.warranty_expiry ? `until ${formatDate(a.warranty_expiry)}` : a.warranty ?? '—'}</dd>
            </dl>
            </div>
          )}

          {tab === 'location' && <LocationHistory assetId={a.id} />}
          {tab === 'maintenance' && <AssetMaintenance asset={a} canManage={can('edit_properties')} />}

          {tab === 'history' && (
            <div className="table-wrap">
              <table className="table table-compact">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>What</th>
                    <th>Document</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Reason</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {history.data?.map((m) => (
                    <tr key={m.id}>
                      <td>{formatDate(m.transfer_date)}</td>
                      <td>{MOVE_LABEL[m.transfer_type]}</td>
                      <td className="mono">{m.reference_number ?? '—'}</td>
                      <td>{m.from?.name ?? 'Property office'}</td>
                      <td>{m.to?.name ?? 'Property office'}</td>
                      <td className="clip">{m.reason}</td>
                      <td>
                        <button className="btn btn-outline btn-sm" onClick={() => setSlip({ kind: 'move', move: m })}>
                          🖨
                        </button>{' '}
                        {m.transfer_type === 'transfer' && can('view_properties') && (
                          <ExcelButton path={`/properties/transfers/${m.id}/export`} name={`PTR_${m.reference_number ?? m.id}.xlsx`} label="PTR" />
                        )}
                      </td>
                    </tr>
                  ))}
                  {history.data?.length === 0 && (
                    <tr>
                      <td colSpan={7} className="empty">
                        No transfers or returns yet. Registered under {a.accountability_number ?? '—'}.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}

          {tab === 'depreciation' && dep.data && (
            dep.data.is_depreciable ? (
              <>
                <dl className="detail-grid">
                  <dt>Cost / residual</dt>
                  <dd>
                    {formatPeso(dep.data.acquisition_cost)} / {formatPeso(dep.data.residual_value)}
                  </dd>
                  <dt>Useful life</dt>
                  <dd>
                    {dep.data.useful_life_years} years, {formatPeso(dep.data.monthly_depreciation)} a month from {dep.data.starts}
                  </dd>
                  <dt>Posted so far</dt>
                  <dd>
                    {formatPeso(dep.data.accumulated_depreciation)} ({dep.data.postings.length} months) — book value {formatPeso(dep.data.book_value)}
                  </dd>
                </dl>
                <h4>Schedule</h4>
                <div className="table-wrap">
                  <table className="table table-compact">
                    <thead>
                      <tr>
                        <th>Year</th>
                        <th className="num">Depreciation</th>
                        <th className="num">Accumulated</th>
                        <th className="num">Book value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dep.data.schedule.map((r) => (
                        <tr key={r.year}>
                          <td>{r.year}</td>
                          <td className="num">{formatPeso(r.depreciation)}</td>
                          <td className="num">{formatPeso(r.accumulated)}</td>
                          <td className="num">{formatPeso(r.book_value)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            ) : (
              <p className="muted">Semi-expendable property is expensed when issued and does not depreciate.</p>
            )
          )}

          {tab === 'gate' && (
            gates.isError ? (
              <p className="muted">You don't have access to gate passes.</p>
            ) : (
              <div className="table-wrap">
                <table className="table table-compact">
                  <thead>
                    <tr>
                      <th>No.</th>
                      <th>Released</th>
                      <th>Carried by</th>
                      <th>Purpose</th>
                      <th>Back</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {gates.data?.map((g) => (
                      <tr key={g.id}>
                        <td className="mono">{g.gate_pass_number}</td>
                        <td>{formatDate(g.date_released)}</td>
                        <td>{g.received_by}</td>
                        <td className="clip">{g.purpose}</td>
                        <td>{g.actual_return_date ? formatDate(g.actual_return_date) : g.expected_return_date ? `due ${formatDate(g.expected_return_date)}` : '—'}</td>
                        <td>
                          <Badge map={GATE_STATUS} value={g.status} />
                        </td>
                      </tr>
                    ))}
                    {gates.data?.length === 0 && (
                      <tr>
                        <td colSpan={6} className="empty">
                          Never left on a gate pass.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )
          )}
        </>
      )}
      {a && <AttachmentsPanel type="Property" id={a.id} label={a.tag_number} docType="OTHER" />}
      {a && <AssemblyPanel asset={a} />}
      {qrOpen && a && <QrLabelSheet title={`QR sticker — ${a.tag_number}`} load={() => qrApi.properties([a.id])} onClose={() => setQrOpen(false)} />}
    </Modal>
  )
}

function MoveForm({ asset, kind, onCancel, onDone }: { asset: Asset; kind: 'transfer' | 'return' | 'issue'; onCancel: () => void; onDone: (m: string, move: AssetMove) => void }) {
  const [to, setTo] = useState('')
  const [reason, setReason] = useState('')
  const [date, setDate] = useState(today())
  const [condition, setCondition] = useState(asset.condition)
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      const input = { to_user: to ? Number(to) : undefined, reason, date, condition }
      return kind === 'transfer' ? assetsApi.transfer(asset.id, input) : kind === 'return' ? assetsApi.turnIn(asset.id, input) : assetsApi.issue(asset.id, input)
    },
    onSuccess: (r) => onDone(r.message, r.transfer),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not record it.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    save.mutate()
  }
  const title = kind === 'transfer' ? `Transfer from ${asset.holder?.name}` : kind === 'return' ? `Return from ${asset.holder?.name} to the property office` : 'Issue to someone'
  return (
    <form className="inline-panel" onSubmit={submit}>
      <h4>{title}</h4>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-grid three">
        {kind !== 'return' && (
          <div className="field span-2">
            <label htmlFor="mv-to">To *</label>
            <PersonSelect id="mv-to" value={to} onChange={setTo} exclude={asset.holder?.id} required />
          </div>
        )}
        <div className="field">
          <label htmlFor="mv-date">Date</label>
          <input id="mv-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
        </div>
        {kind === 'return' && (
          <div className="field">
            <label htmlFor="mv-cond">Condition</label>
            <select id="mv-cond" value={condition} onChange={(e) => setCondition(e.target.value as Asset['condition'])}>
              {CONDITIONS.map((c) => (
                <option key={c} value={c}>
                  {cap(c)}
                </option>
              ))}
            </select>
          </div>
        )}
        <div className="field span-3">
          <label htmlFor="mv-reason">Reason {kind !== 'issue' && '*'}</label>
          <input id="mv-reason" value={reason} onChange={(e) => setReason(e.target.value)} required={kind !== 'issue'} />
        </div>
      </div>
      <div className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : kind === 'transfer' ? 'Transfer (PTR)' : kind === 'return' ? 'Record return' : 'Issue'}
        </button>
      </div>
    </form>
  )
}

function EditForm({ asset, onCancel, onDone }: { asset: Asset; onCancel: () => void; onDone: (m: string) => void }) {
  const [f, setF] = useState({
    tag_number: asset.tag_number,
    serial_number: asset.serial_number ?? '',
    brand: asset.brand ?? '',
    specifications: asset.specifications ?? '',
    warranty_months: asset.warranty_months != null ? String(asset.warranty_months) : '',
    condition: asset.condition as string,
    useful_life_years: String(asset.useful_life_years),
    salvage_value: asset.salvage_value != null ? String(asset.salvage_value) : '',
  })
  const [error, setError] = useState<string | null>(null)
  const locked = asset.accumulated_depreciation > 0
  const save = useMutation({
    mutationFn: () =>
      assetsApi.update(asset.id, {
        ...f,
        warranty_months: f.warranty_months ? Number(f.warranty_months) : null,
        useful_life_years: locked ? undefined : Number(f.useful_life_years),
        salvage_value: locked || !f.salvage_value ? undefined : Number(f.salvage_value),
      }),
    onSuccess: () => onDone('Asset details saved.'),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }))
  return (
    <form className="inline-panel" onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}>
      <h4>Edit details</h4>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-grid three">
        <div className="field">
          <label htmlFor="ed-tag">Tag no.</label>
          <input id="ed-tag" value={f.tag_number} onChange={set('tag_number')} required />
        </div>
        <div className="field">
          <label htmlFor="ed-sn">Serial no.</label>
          <input id="ed-sn" value={f.serial_number} onChange={set('serial_number')} />
        </div>
        <div className="field">
          <label htmlFor="ed-cond">Condition</label>
          <select id="ed-cond" value={f.condition} onChange={set('condition')}>
            {CONDITIONS.map((c) => (
              <option key={c} value={c}>
                {cap(c)}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="ed-brand">Brand / model</label>
          <input id="ed-brand" value={f.brand} onChange={set('brand')} />
        </div>
        <div className="field">
          <label htmlFor="ed-wm">Warranty (months)</label>
          <input id="ed-wm" type="number" min={0} value={f.warranty_months} onChange={set('warranty_months')} />
        </div>
        {asset.is_depreciable && (
          <div className="field">
            <label htmlFor="ed-life">Useful life (years)</label>
            <input id="ed-life" type="number" min={1} max={50} value={f.useful_life_years} onChange={set('useful_life_years')} disabled={locked} />
            {locked && <span className="hint">Fixed once depreciation is posted.</span>}
          </div>
        )}
        <div className="field span-3">
          <label htmlFor="ed-spec">Specifications</label>
          <input id="ed-spec" value={f.specifications} onChange={set('specifications')} />
        </div>
      </div>
      <div className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : 'Save'}
        </button>
      </div>
    </form>
  )
}

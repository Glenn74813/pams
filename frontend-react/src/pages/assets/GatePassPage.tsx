import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { gatePassApi, inventoryApi } from '../../api/endpoints'
import type { Asset, GatePass } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, today } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { AssetSlip } from './AssetSlip'
import { GATE_STATUS, cap } from './labels'
import { AssetPicker, Badge } from './pickers'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import './assets.css'

export function GatePassPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('released')
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const [returning, setReturning] = useState<GatePass | null>(null)
  const [printing, setPrinting] = useState<GatePass | null>(null)
  const q = useDebounced(search.trim())
  const list = useQuery({ queryKey: ['gate-passes', page, status, q], queryFn: () => gatePassApi.list({ page, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const refresh = () => {
    for (const k of ['gate-passes', 'asset', 'asset-gates', 'assets']) void queryClient.invalidateQueries({ queryKey: [k] })
  }

  return (
    <div className="page page-wide">
      <h1 className="page-title">Gate Pass</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search pass no., tag no., person or purpose" value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} aria-label="Search gate passes" />
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="released">Out</option>
            <option value="overdue">Overdue</option>
            <option value="returned">Returned</option>
            <option value="">All</option>
          </select>
        </div>
        {can('create_gate_pass') && (
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            + New Gate Pass
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>No.</th>
              <th>Released</th>
              <th>Asset</th>
              <th>Carried out by</th>
              <th>Purpose</th>
              <th>Back</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((g) => (
              <tr key={g.id}>
                <td className="mono">{g.gate_pass_number}</td>
                <td>{formatDate(g.date_released)}</td>
                <td>
                  <span className="mono">{g.tag_number}</span> {g.item_name}
                </td>
                <td>{g.received_by}</td>
                <td className="clip">{g.purpose}</td>
                <td>{g.actual_return_date ? formatDate(g.actual_return_date) : g.expected_return_date ? `due ${formatDate(g.expected_return_date)}` : 'Not returnable'}</td>
                <td>
                  <Badge map={GATE_STATUS} value={g.status} />
                </td>
                <td>
                  <div className="workflow-actions">
                    <button className="btn btn-outline btn-sm" onClick={() => setPrinting(g)}>
                      🖨
                    </button>
                    {g.can_return && (
                      <button className="btn btn-primary btn-sm" onClick={() => setReturning(g)}>
                        Mark returned
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No gate passes here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {creating && <CreateForm onClose={() => setCreating(false)} onSaved={(g) => { toast(`${g.gate_pass_number} issued.`, 'success'); setCreating(false); refresh(); setPrinting(g) }} />}
      {returning && <ReturnForm pass={returning} onClose={() => setReturning(null)} onSaved={(m) => { toast(m, 'success'); setReturning(null); refresh() }} />}
      {printing && (
        <Modal title={`Gate pass ${printing.gate_pass_number}`} onClose={() => setPrinting(null)} width={820}>
          <div className="slip-toolbar no-print">
            <span />
            <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
              🖨 Print
            </button>
          </div>
          <AssetSlip kind="gate" gate={printing} config={config.data} />
        </Modal>
      )}
    </div>
  )
}

function CreateForm({ onClose, onSaved }: { onClose: () => void; onSaved: (g: GatePass) => void }) {
  const [asset, setAsset] = useState<Asset | null>(null)
  const [by, setBy] = useState('')
  const [purpose, setPurpose] = useState('')
  const [released, setReleased] = useState(today())
  const [due, setDue] = useState('')
  const [cond, setCond] = useState('good')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => gatePassApi.create({ property_id: asset!.id, received_by: by, purpose, date_released: released, expected_return_date: due || null, condition_on_release: cond }),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not issue the gate pass.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!asset) return setError('Choose the asset.')
    save.mutate()
  }
  return (
    <Modal title="New Gate Pass" onClose={onClose} width={620}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field" style={{ marginBottom: '1rem' }}>
          <label htmlFor="gp-asset">Asset *</label>
          {asset ? (
            <div className="chosen">
              <span>
                <span className="mono">{asset.tag_number}</span> {asset.item?.name} · {asset.holder?.name ?? 'property office'}
              </span>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setAsset(null)}>
                Change
              </button>
            </div>
          ) : (
            <AssetPicker
              id="gp-asset"
              filter={(a) => (a.status === 'disposed' || a.status === 'retired' ? a.status : null)}
              onPick={(a) => {
                setAsset(a)
                if (!by && a.holder) setBy(a.holder.name)
              }}
            />
          )}
        </div>
        <div className="form-grid">
          <div className="field span-2">
            <label htmlFor="gp-by">Carried out by *</label>
            <input id="gp-by" value={by} onChange={(e) => setBy(e.target.value)} required />
          </div>
          <div className="field span-2">
            <label htmlFor="gp-purpose">Purpose *</label>
            <input id="gp-purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)} required maxLength={500} placeholder="e.g. Repair at service centre" />
          </div>
          <div className="field">
            <label htmlFor="gp-rel">Date released</label>
            <input id="gp-rel" type="date" value={released} onChange={(e) => setReleased(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="gp-due">Expected back</label>
            <input id="gp-due" type="date" value={due} min={released} onChange={(e) => setDue(e.target.value)} />
            <span className="hint">Leave blank if it is not coming back.</span>
          </div>
          <div className="field">
            <label htmlFor="gp-cond">Condition on release</label>
            <select id="gp-cond" value={cond} onChange={(e) => setCond(e.target.value)}>
              {['new', 'excellent', 'good', 'fair', 'poor'].map((c) => (
                <option key={c} value={c}>
                  {cap(c)}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Issuing…' : 'Issue gate pass'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ReturnForm({ pass, onClose, onSaved }: { pass: GatePass; onClose: () => void; onSaved: (m: string) => void }) {
  const [date, setDate] = useState(today())
  const [cond, setCond] = useState('good')
  const [by, setBy] = useState(pass.received_by)
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => gatePassApi.markReturned(pass.id, { actual_return_date: date, condition_on_return: cond, returned_by: by, remarks }),
    onSuccess: () => onSaved(`${pass.tag_number} is back (${pass.gate_pass_number}).`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not record the return.'),
  })
  return (
    <Modal title={`Return — ${pass.gate_pass_number}`} onClose={onClose} width={520}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field">
            <label htmlFor="gr-date">Date back</label>
            <input id="gr-date" type="date" value={date} min={pass.date_released} max={today()} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="gr-cond">Condition *</label>
            <select id="gr-cond" value={cond} onChange={(e) => setCond(e.target.value)}>
              {['good', 'fair', 'poor', 'damaged', 'lost'].map((c) => (
                <option key={c} value={c}>
                  {cap(c)}
                </option>
              ))}
            </select>
          </div>
          <div className="field span-2">
            <label htmlFor="gr-by">Returned by</label>
            <input id="gr-by" value={by} onChange={(e) => setBy(e.target.value)} />
          </div>
          <div className="field span-2">
            <label htmlFor="gr-rem">Remarks</label>
            <input id="gr-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Mark returned'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

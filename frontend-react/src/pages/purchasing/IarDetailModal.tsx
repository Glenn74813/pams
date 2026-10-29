import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, receivingApi, type AcceptLineInput } from '../../api/endpoints'
import type { Classification, InspectionReport, IarLine } from '../../api/types'
import { ItemPicker } from '../../components/ItemPicker'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatDateTime, formatPeso, formatQty } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { IarSlip } from './IarSlip'
import { ACCEPTANCE, IAR_STATUS } from './labels'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'
import { ExcelButton } from '../../components/ExcelButton'

interface Props {
  iarId: number
  onClose: () => void
  onEdit: (r: InspectionReport) => void
  onChanged: (message: string, closed?: boolean) => void
}

export function IarDetailModal({ iarId, onClose, onEdit, onChanged }: Props) {
  const q = useQuery({ queryKey: ['iar', iarId], queryFn: () => receivingApi.get(iarId) })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const [printing, setPrinting] = useState(false)
  const [mode, setMode] = useState<null | 'accept' | 'reject' | 'delete'>(null)
  const [why, setWhy] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const reject = useMutation({ mutationFn: () => receivingApi.reject(iarId, why), onSuccess: (r) => { setMode(null); onChanged(`${r.iar_number} rejected.`) }, onError: fail })
  const remove = useMutation({ mutationFn: () => receivingApi.remove(iarId), onSuccess: (r) => onChanged(r.message, true), onError: fail })
  const r = q.data
  return (
    <Modal title={r ? `${r.iar_number} — ${r.purchase_order?.name}` : 'Inspection report'} onClose={onClose} width={1000}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {r && printing && (
        <>
          <div className="slip-toolbar no-print">
            <button className="btn btn-secondary btn-sm" onClick={() => setPrinting(false)}>
              ‹ Back
            </button>
            <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
              🖨 Print
            </button>
          </div>
          <IarSlip r={r} config={config.data} />
        </>
      )}
      {r && !printing && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={IAR_STATUS} value={r.status} /> <Badge map={ACCEPTANCE} value={r.acceptance_status} />
            </dd>
            <dt>Supplier</dt>
            <dd>
              {r.supplier?.name} · {r.purchase_request?.name} ({r.department?.name})
            </dd>
            <dt>Delivered / inspected</dt>
            <dd>
              {formatDate(r.delivery_date)} · {formatDate(r.inspection_date)} by {r.inspector.name}
            </dd>
            {r.accepter && (
              <>
                <dt>{r.acceptance_status === 'rejected' ? 'Rejected by' : 'Accepted by'}</dt>
                <dd>
                  {r.accepter.name} · {formatDateTime(r.accepted_at)}
                </dd>
              </>
            )}
            {r.observations && (
              <>
                <dt>Observations</dt>
                <dd>{r.observations}</dd>
              </>
            )}
            {r.remarks && (
              <>
                <dt>Remarks</dt>
                <dd>{r.remarks}</dd>
              </>
            )}
          </dl>
          {mode === 'accept' ? (
            <AcceptForm r={r} onCancel={() => setMode(null)} onDone={(m) => { setMode(null); setError(null); onChanged(m) }} onError={(m) => setError(m)} />
          ) : (
            <div className="table-wrap" style={{ marginTop: '1rem' }}>
              <table className="table table-compact">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th className="num">Received</th>
                    <th className="num">Accepted</th>
                    <th className="num">Rejected</th>
                    <th className="num">Unit cost</th>
                    <th>Into stock as</th>
                  </tr>
                </thead>
                <tbody>
                  {r.items?.map((l) => (
                    <tr key={l.id}>
                      <td>
                        {l.description}
                        {l.remarks && <div className="muted small">{l.remarks}</div>}
                      </td>
                      <td className="num">{formatQty(l.quantity_received)}</td>
                      <td className="num">{formatQty(l.quantity_accepted)}</td>
                      <td className={'num' + (l.quantity_rejected > 0 ? ' short' : '')}>{formatQty(l.quantity_rejected)}</td>
                      <td className="num">{formatPeso(l.unit_cost)}</td>
                      <td>{l.item ? `${l.item.code} ${l.item.name}` : <span className="muted">—</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {mode === 'reject' && (
            <div className="reject-box">
              <div className="field">
                <label htmlFor="iar-why">Why is the delivery rejected? *</label>
                <input id="iar-why" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus />
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setMode(null)}>
                  Back
                </button>
                <button className="btn btn-danger" disabled={!why.trim() || reject.isPending} onClick={() => reject.mutate()}>
                  Reject delivery
                </button>
              </div>
            </div>
          )}
          {mode === 'delete' && (
            <ConfirmDialog title="Delete inspection report" message={`Delete ${r.iar_number}? The quantities go back to outstanding.`} confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} onCancel={() => setMode(null)} />
          )}
          {mode === null && (
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setPrinting(true)}>
                🖨 IAR
              </button>
              <ExcelButton path={`/receiving/inspection-reports/${r.id}/export`} name={`IAR_${r.iar_number}.xlsx`} label="IAR (Excel)" />
              {r.can_delete && (
                <button className="btn btn-danger" onClick={() => setMode('delete')}>
                  Delete
                </button>
              )}
              {r.can_edit && (
                <button className="btn btn-secondary" onClick={() => onEdit(r)}>
                  Edit
                </button>
              )}
              {r.acceptance_status === 'pending' && !r.can_accept && !r.can_reject && <span className="muted footer-note">Acceptance is signed by someone other than the inspector.</span>}
              {r.can_reject && (
                <button className="btn btn-danger" onClick={() => setMode('reject')}>
                  Reject
                </button>
              )}
              {r.can_accept && (
                <button className="btn btn-primary" onClick={() => setMode('accept')}>
                  Accept into stock…
                </button>
              )}
            </div>
          )}
        </>
      )}
      {r && !printing && <AttachmentsPanel type="InspectionAcceptanceReport" id={r.id} label={r.iar_number} docType="IAR" />}
    </Modal>
  )
}

interface Pick {
  item: { id: number; code: string; name: string } | null
  lot_number: string
  expiration_date: string
  location: string
}

function AcceptForm({ r, onCancel, onDone, onError }: { r: InspectionReport; onCancel: () => void; onDone: (m: string) => void; onError: (m: string) => void }) {
  const lines = (r.items ?? []).filter((l) => l.quantity_accepted > 0)
  const [picks, setPicks] = useState<Record<number, Pick>>(() => Object.fromEntries(lines.map((l) => [l.id, { item: null, lot_number: '', expiration_date: '', location: '' }])))
  const [creating, setCreating] = useState<IarLine | null>(null)
  const set = (id: number, patch: Partial<Pick>) => setPicks((p) => ({ ...p, [id]: { ...p[id], ...patch } }))
  const accept = useMutation({
    mutationFn: () =>
      receivingApi.accept(
        r.id,
        lines.map(
          (l): AcceptLineInput => ({
            iar_item_id: l.id,
            item_id: picks[l.id].item!.id,
            lot_number: picks[l.id].lot_number || null,
            expiration_date: picks[l.id].expiration_date || null,
            location: picks[l.id].location || null,
          }),
        ),
      ),
    onSuccess: (x) => onDone(`${x.iar_number} accepted; stock updated.`),
    onError: (err) => onError(err instanceof ApiError ? err.message : 'Could not accept the delivery.'),
  })
  const ready = lines.every((l) => picks[l.id].item)
  return (
    <div className="reject-box">
      <p className="muted">Each accepted line becomes a new stock lot at the PO unit price, received on {formatDate(r.delivery_date)}.</p>
      {lines.map((l) => {
        const p = picks[l.id]
        return (
          <div key={l.id} className="line-card" style={{ marginBottom: '0.5rem' }}>
            <div className="line-card-head">
              <strong>{l.description}</strong>
              <span className="muted">
                {formatQty(l.quantity_accepted)} {l.unit} at {formatPeso(l.unit_cost)}
              </span>
            </div>
            <div className="form-grid four">
              <div className="field span-2">
                <label htmlFor={`ac-item-${l.id}`}>Inventory item *</label>
                {p.item ? (
                  <div className="chosen">
                    <span>
                      <span className="mono">{p.item.code}</span> {p.item.name}
                    </span>
                    <button type="button" className="btn btn-outline btn-sm" onClick={() => set(l.id, { item: null })}>
                      Change
                    </button>
                  </div>
                ) : (
                  <>
                    <ItemPicker id={`ac-item-${l.id}`} onPick={(i) => set(l.id, { item: { id: i.id, code: i.code, name: i.name } })} />
                    <button type="button" className="link-btn small" onClick={() => setCreating(l)}>
                      + New catalogue item
                    </button>
                  </>
                )}
              </div>
              <div className="field">
                <label htmlFor={`ac-lot-${l.id}`}>Lot no.</label>
                <input id={`ac-lot-${l.id}`} value={p.lot_number} onChange={(e) => set(l.id, { lot_number: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor={`ac-exp-${l.id}`}>Expires</label>
                <input id={`ac-exp-${l.id}`} type="date" min={r.delivery_date} value={p.expiration_date} onChange={(e) => set(l.id, { expiration_date: e.target.value })} />
              </div>
              <div className="field span-2">
                <label htmlFor={`ac-loc-${l.id}`}>Location</label>
                <input id={`ac-loc-${l.id}`} value={p.location} onChange={(e) => set(l.id, { location: e.target.value })} placeholder="Warehouse / shelf" />
              </div>
            </div>
          </div>
        )
      })}
      {creating && (
        <NewItemForm
          line={creating}
          onCancel={() => setCreating(null)}
          onCreated={(i) => {
            set(creating.id, { item: i })
            setCreating(null)
          }}
          onError={onError}
        />
      )}
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Back
        </button>
        <button className="btn btn-primary" disabled={!ready || accept.isPending} onClick={() => accept.mutate()}>
          {accept.isPending ? 'Receiving…' : 'Accept into stock'}
        </button>
      </div>
    </div>
  )
}

function NewItemForm({ line, onCancel, onCreated, onError }: { line: IarLine; onCancel: () => void; onCreated: (i: { id: number; code: string; name: string }) => void; onError: (m: string) => void }) {
  const cats = useQuery({ queryKey: ['categories'], queryFn: inventoryApi.categories })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const threshold = config.data?.capitalization_threshold ?? 50000
  const [name, setName] = useState(line.description)
  const [unit, setUnit] = useState(line.unit ?? 'unit')
  const [category, setCategory] = useState('')
  const [cls, setCls] = useState<Classification>((line.unit_cost ?? 0) >= threshold ? 'ppe' : 'consumable')
  const save = useMutation({
    mutationFn: () => inventoryApi.createItem({ name, unit_of_measure: unit, category_id: Number(category), classification: cls, min_stock: 0, max_stock: 0, is_assembly: false }),
    onSuccess: (i) => onCreated({ id: i.id, code: i.code, name: i.name }),
    onError: (err) => onError(err instanceof ApiError ? err.message : 'Could not create the item.'),
  })
  return (
    <div className="line-card">
      <h4 style={{ margin: '0 0 0.5rem' }}>New catalogue item for “{line.description}”</h4>
      <div className="form-grid four">
        <div className="field span-2">
          <label htmlFor="ni-name">Name *</label>
          <input id="ni-name" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="ni-unit">Unit *</label>
          <input id="ni-unit" value={unit} onChange={(e) => setUnit(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="ni-cls">Class *</label>
          <select id="ni-cls" value={cls} onChange={(e) => setCls(e.target.value as Classification)}>
            <option value="consumable">Consumable</option>
            <option value="semi_expendable">Semi-expendable</option>
            <option value="ppe">PPE</option>
          </select>
        </div>
        <div className="field span-2">
          <label htmlFor="ni-cat">Category *</label>
          <select id="ni-cat" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">Choose</option>
            {cats.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary btn-sm" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary btn-sm" disabled={!name.trim() || !unit.trim() || !category || save.isPending} onClick={() => save.mutate()}>
          Create item
        </button>
      </div>
    </div>
  )
}

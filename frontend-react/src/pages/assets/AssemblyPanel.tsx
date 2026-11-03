import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { assemblyApi } from '../../api/endpoints'
import type { Asset } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/toast-context'
import { formatPeso } from '../../lib/format'
import { AssetPicker } from './pickers'

/**
 * Parts of a locally assembled unit (e.g. a Clone PC): each part comes out of a lot of the assembly
 * item and goes back when removed; the unit is recorded at the total of its parts.
 */
export function AssemblyPanel({ asset }: { asset: Asset }) {
  const { can } = useAuth()
  const toast = useToast()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['assembly', asset.id], queryFn: () => assemblyApi.get(asset.id) })
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState({ stock_id: '', description: '', specifications: '', serial_number: '', quantity: '1', remarks: '' })
  const [error, setError] = useState<string | null>(null)
  const manage = can('edit_properties', 'create_properties')
  const done = (msg: string) => {
    void qc.invalidateQueries({ queryKey: ['assembly', asset.id] })
    void qc.invalidateQueries({ queryKey: ['asset', asset.id] })
    void qc.invalidateQueries({ queryKey: ['assets'] })
    setError(null)
    toast(msg)
  }
  const fail = (e: unknown) => setError(e instanceof ApiError ? e.message : 'Something went wrong.')
  const add = useMutation({
    mutationFn: () =>
      assemblyApi.addPart(asset.id, {
        description: f.description,
        specifications: f.specifications || undefined,
        serial_number: f.serial_number || undefined,
        quantity: Number(f.quantity) || 1,
        remarks: f.remarks || undefined,
        stock_id: f.stock_id ? Number(f.stock_id) : undefined,
      }),
    onSuccess: () => {
      setAdding(false)
      setF({ stock_id: '', description: '', specifications: '', serial_number: '', quantity: '1', remarks: '' })
      done(`${f.description} added to ${asset.tag_number}.`)
    },
    onError: fail,
  })
  const remove = useMutation({ mutationFn: (id: number) => assemblyApi.removePart(id), onSuccess: () => done('Part removed and returned to stock.'), onError: fail })
  const link = useMutation({ mutationFn: (childId: number) => assemblyApi.link(asset.id, childId), onSuccess: () => done('Asset attached.'), onError: fail })
  const unlink = useMutation({ mutationFn: (childId: number) => assemblyApi.unlink(childId), onSuccess: () => done('Asset detached.'), onError: fail })

  const a = q.data
  if (!a || (!a.is_assembly && a.linked.length === 0 && !a.parent)) return null
  const pickLot = (id: string) => {
    const lot = a.lots.find((l) => String(l.id) === id)
    setF((x) => ({ ...x, stock_id: id, description: lot ? lot.label : x.description, specifications: lot?.brand ?? x.specifications }))
  }
  return (
    <>
      {a.parent && (
        <p className="small">
          Part of <strong>{a.parent.tag_number}</strong> ({a.parent.item})
        </p>
      )}
      {a.is_assembly && (
        <>
          <div className="section-header" style={{ marginTop: '1rem' }}>
            <h4>
              Assembled parts · {formatPeso(a.total)}{' '}
              {a.classification && (
                <span className={'badge ' + (a.classification === 'ppe' ? 'st-partial' : 'badge-muted')}>
                  {a.classification === 'ppe' ? 'PPE' : 'Semi-expendable'} (threshold {formatPeso(a.threshold)})
                </span>
              )}
            </h4>
            {manage && !adding && (
              <button className="btn btn-outline btn-sm" onClick={() => setAdding(true)}>
                + Add part
              </button>
            )}
          </div>
          {error && <div className="alert alert-error">{error}</div>}
          {adding && (
            <div className="reject-box">
              <div className="form-grid">
                <div className="field">
                  <label htmlFor="ap-lot">From stock lot</label>
                  <select id="ap-lot" value={f.stock_id} onChange={(e) => pickLot(e.target.value)}>
                    <option value="">— not from stock —</option>
                    {a.lots.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.label} · {l.quantity} left @ {formatPeso(l.unit_cost)}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="ap-desc">Part *</label>
                  <input id="ap-desc" value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} placeholder="e.g. RAM 16 GB DDR4" />
                </div>
                <div className="field">
                  <label htmlFor="ap-spec">Brand / model</label>
                  <input id="ap-spec" value={f.specifications} onChange={(e) => setF({ ...f, specifications: e.target.value })} />
                </div>
                <div className="field">
                  <label htmlFor="ap-sn">Serial no.</label>
                  <input id="ap-sn" value={f.serial_number} onChange={(e) => setF({ ...f, serial_number: e.target.value })} />
                </div>
                <div className="field">
                  <label htmlFor="ap-qty">Quantity</label>
                  <input id="ap-qty" type="number" min={1} value={f.quantity} onChange={(e) => setF({ ...f, quantity: e.target.value })} />
                </div>
                <div className="field">
                  <label htmlFor="ap-rem">Remarks</label>
                  <input id="ap-rem" value={f.remarks} onChange={(e) => setF({ ...f, remarks: e.target.value })} />
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary btn-sm" onClick={() => setAdding(false)}>
                  Cancel
                </button>
                <button className="btn btn-primary btn-sm" disabled={!f.description.trim() || add.isPending} onClick={() => add.mutate()}>
                  Add part
                </button>
              </div>
            </div>
          )}
          {a.parts.length === 0 ? (
            <p className="muted small">No parts listed yet.</p>
          ) : (
            <div className="table-wrap">
              <table className="table table-compact">
                <thead>
                  <tr>
                    <th>Part</th>
                    <th>Brand / model</th>
                    <th>Serial no.</th>
                    <th className="num">Qty</th>
                    <th className="num">Unit cost</th>
                    <th className="num">Amount</th>
                    <th>Warranty</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {a.parts.map((p) => (
                    <tr key={p.id}>
                      <td>{p.description}</td>
                      <td>{p.specifications ?? '—'}</td>
                      <td>{p.serial_number ?? '—'}</td>
                      <td className="num">{p.quantity}</td>
                      <td className="num">{formatPeso(p.unit_cost)}</td>
                      <td className="num">{formatPeso(p.amount)}</td>
                      <td>{p.warranty ?? '—'}</td>
                      <td>
                        {manage && (
                          <button className="btn btn-outline btn-sm" disabled={remove.isPending}
                            onClick={() => window.confirm(`Remove ${p.description}? It goes back to stock.`) && remove.mutate(p.id)}>
                            Remove
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
      {(a.is_assembly || a.linked.length > 0) && (
        <>
          <h4>Attached assets</h4>
          {a.linked.length === 0 && <p className="muted small">None.</p>}
          <ul className="stat-list" style={{ listStyle: 'none' }}>
            {a.linked.map((l) => (
              <li key={l.id} style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem' }}>
                <span>
                  <strong>{l.tag_number}</strong> {l.item} {l.serial_number && <span className="muted small">S/N {l.serial_number}</span>}
                </span>
                {manage && (
                  <button className="btn btn-outline btn-sm" onClick={() => unlink.mutate(l.id)}>
                    Detach
                  </button>
                )}
              </li>
            ))}
          </ul>
          {manage && a.is_assembly && (
            <div className="field" style={{ maxWidth: 420 }}>
              <label htmlFor="ap-link">Attach a whole asset (e.g. the monitor of this PC)</label>
              <AssetPicker id="ap-link" onPick={(x: Asset) => link.mutate(x.id)} filter={(x) => (x.id === asset.id ? 'This asset' : null)} />
            </div>
          )}
        </>
      )}
    </>
  )
}

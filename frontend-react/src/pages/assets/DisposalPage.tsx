import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { disposalApi, type DisposalInput } from '../../api/endpoints'
import type { Asset, Disposal, Item } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ItemPicker } from '../../components/ItemPicker'
import { Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso, formatQty, today } from '../../lib/format'
import { DISPOSAL_MODES, DISPOSAL_STATUS, SALE_MODES } from './labels'
import { AssetPicker, Badge } from './pickers'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import './assets.css'

const modeLabel = (m: string) => DISPOSAL_MODES.find(([k]) => k === m)?.[1] ?? m

export function DisposalPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState(can('approve_disposal') ? 'submitted' : '')
  const [editing, setEditing] = useState<Disposal | 'new' | null>(null)
  const [completing, setCompleting] = useState<Disposal | null>(null)
  const [viewing, setViewing] = useState<Disposal | null>(null)
  const list = useQuery({ queryKey: ['disposals', page, status], queryFn: () => disposalApi.list({ page, status: status || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['disposals', 'assets', 'asset', 'items', 'item', 'stock-card']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  const act = useMutation({
    mutationFn: async ({ d, action }: { d: Disposal; action: 'submit' | 'withdraw' }) => {
      if (action === 'submit') await disposalApi.submit(d.id)
      else await disposalApi.withdraw(d.id)
    },
    onSuccess: (_, { d, action }) => {
      toast(`${d.disposal_number}: ${action === 'withdraw' ? 'withdrawn' : action === 'submit' ? 'submitted' : action + 'd'}.`, 'success')
      refresh()
    },
    onError: (err) => toast(err instanceof ApiError ? err.message : 'Action failed.', 'error'),
  })

  return (
    <div className="page page-wide">
      <h1 className="page-title">Disposal &amp; Write-Off</h1>
      <p className="muted" style={{ marginBottom: '1rem' }}>
        Unserviceable assets must be returned to the property office before they can be proposed for disposal. Someone other than the
        requester approves; completing the disposal records the asset's book value and any proceeds.
      </p>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="">All</option>
            {Object.entries(DISPOSAL_STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        {can('create_disposal') && (
          <button className="btn btn-primary" onClick={() => setEditing('new')}>
            + New Disposal Request
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>No.</th>
              <th>Date</th>
              <th>What</th>
              <th>Mode</th>
              <th className="num">Value</th>
              <th>Requested by</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((d) => (
              <tr key={d.id}>
                <td className="mono">{d.disposal_number}</td>
                <td>{formatDate(d.request_date)}</td>
                <td>
                  {d.category === 'property' ? (
                    <>
                      <span className="mono">{d.tag_number}</span> {d.item_name}
                    </>
                  ) : (
                    <>
                      {formatQty(d.quantity)} {d.unit_of_measure} {d.item_name}
                    </>
                  )}
                  <div className="muted small clip">{d.reason}</div>
                </td>
                <td>{modeLabel(d.disposal_type)}</td>
                <td className="num">
                  {formatPeso(d.status === 'completed' ? d.book_value : d.value)}
                  {d.proceeds != null && <div className="muted small">sold for {formatPeso(d.proceeds)}</div>}
                </td>
                <td>{d.requester.name}</td>
                <td>
                  <Badge map={DISPOSAL_STATUS} value={d.status} />
                  {d.approver && <div className="muted small">by {d.approver.name}</div>}
                </td>
                <td>
                  <div className="workflow-actions">
                    {d.can_edit && (
                      <button className="btn btn-outline btn-sm" onClick={() => setEditing(d)}>
                        Edit
                      </button>
                    )}
                    {d.can_submit && (
                      <button className="btn btn-primary btn-sm" onClick={() => act.mutate({ d, action: 'submit' })}>
                        Submit
                      </button>
                    )}
                    {d.can_edit && (
                      <button className="btn btn-outline btn-sm" onClick={() => act.mutate({ d, action: 'withdraw' })}>
                        Withdraw
                      </button>
                    )}
                    <button className={`btn btn-sm ${d.can_decide ? 'btn-primary' : 'btn-outline'}`} onClick={() => setViewing(d)}>
                      {d.can_decide ? 'Review…' : 'Details'}
                    </button>
                    {d.can_complete && (
                      <button className="btn btn-primary btn-sm" onClick={() => setCompleting(d)}>
                        Complete…
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No disposal requests here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {editing && (
        <DisposalForm
          disposal={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(m) => {
            toast(m, 'success')
            setEditing(null)
            refresh()
          }}
        />
      )}
      {viewing && (
        <DisposalDetail
          disposal={viewing}
          onClose={() => setViewing(null)}
          onDecided={(m) => {
            toast(m, 'success')
            setViewing(null)
            refresh()
          }}
        />
      )}
      {completing && (
        <CompleteForm
          disposal={completing}
          onClose={() => setCompleting(null)}
          onSaved={(m) => {
            toast(m, 'success')
            setCompleting(null)
            refresh()
          }}
        />
      )}
    </div>
  )
}

function DisposalForm({ disposal, onClose, onSaved }: { disposal: Disposal | null; onClose: () => void; onSaved: (m: string) => void }) {
  const [category, setCategory] = useState<'property' | 'inventory'>(disposal?.category ?? 'property')
  const [asset, setAsset] = useState<{ id: number; label: string } | null>(disposal?.property_id ? { id: disposal.property_id, label: `${disposal.tag_number} ${disposal.item_name}` } : null)
  const [item, setItem] = useState<{ id: number; label: string; max: number } | null>(
    disposal?.category === 'inventory' && disposal.item_id ? { id: disposal.item_id, label: `${disposal.item_code} ${disposal.item_name}`, max: Infinity } : null,
  )
  const [qty, setQty] = useState(String(disposal?.quantity ?? ''))
  const [mode, setMode] = useState(disposal?.disposal_type ?? 'condemnation')
  const [reason, setReason] = useState(disposal?.reason ?? '')
  const [remarks, setRemarks] = useState(disposal?.remarks ?? '')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const input: DisposalInput = {
        disposal_category: category,
        property_id: category === 'property' ? asset?.id : undefined,
        item_id: category === 'inventory' ? item?.id : undefined,
        quantity: category === 'inventory' ? Number(qty) : undefined,
        disposal_type: mode,
        reason,
        remarks,
      }
      return disposal ? disposalApi.update(disposal.id, input) : disposalApi.create(input)
    },
    onSuccess: (d) => onSaved(disposal ? 'Draft saved.' : `${d.disposal_number} drafted; submit it when ready.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (category === 'property' && !asset) return setError('Choose the asset.')
    if (category === 'inventory' && !item) return setError('Choose the item.')
    save.mutate()
  }
  const assetBlock = (a: Asset) =>
    a.status === 'disposed' ? 'already disposed' : a.holder ? 'still issued — record its return first' : null

  return (
    <Modal title={disposal ? `Edit ${disposal.disposal_number}` : 'New Disposal Request'} onClose={onClose} width={640}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        {!disposal && (
          <div className="tabs">
            <button type="button" className={category === 'property' ? 'active' : ''} onClick={() => setCategory('property')}>
              Asset (PPE / semi-expendable)
            </button>
            <button type="button" className={category === 'inventory' ? 'active' : ''} onClick={() => setCategory('inventory')}>
              Stock write-off
            </button>
          </div>
        )}
        <div className="field" style={{ marginBottom: '1rem' }}>
          <label htmlFor="dp-what">{category === 'property' ? 'Asset *' : 'Item *'}</label>
          {category === 'property' ? (
            asset ? (
              <div className="chosen">
                <span>{asset.label}</span>
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setAsset(null)}>
                  Change
                </button>
              </div>
            ) : (
              <AssetPicker id="dp-what" filter={assetBlock} onPick={(a) => setAsset({ id: a.id, label: `${a.tag_number} ${a.item?.name}` })} />
            )
          ) : item ? (
            <div className="chosen">
              <span>{item.label}</span>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setItem(null)}>
                Change
              </button>
            </div>
          ) : (
            <ItemPicker id="dp-what" requireStock onPick={(i: Item) => setItem({ id: i.id, label: `${i.code} ${i.name} (${i.current_stock} ${i.unit_of_measure})`, max: i.current_stock })} />
          )}
        </div>
        <div className="form-grid">
          {category === 'inventory' && (
            <div className="field">
              <label htmlFor="dp-qty">Quantity *</label>
              <input id="dp-qty" type="number" min={1} max={Number.isFinite(item?.max) ? item?.max : undefined} value={qty} onChange={(e) => setQty(e.target.value)} required />
            </div>
          )}
          <div className="field">
            <label htmlFor="dp-mode">Mode of disposal *</label>
            <select id="dp-mode" value={mode} onChange={(e) => setMode(e.target.value)}>
              {DISPOSAL_MODES.map(([k, l]) => (
                <option key={k} value={k}>
                  {l}
                </option>
              ))}
            </select>
          </div>
          <div className="field span-2">
            <label htmlFor="dp-reason">Reason *</label>
            <input id="dp-reason" value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={500} placeholder="e.g. Beyond economical repair" />
          </div>
          <div className="field span-2">
            <label htmlFor="dp-rem">Remarks</label>
            <textarea id="dp-rem" rows={2} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save draft'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/** The request with its supporting documents (inspection report, photos, appraisal), and the approver's decision. */
function DisposalDetail({ disposal: d, onClose, onDecided }: { disposal: Disposal; onClose: () => void; onDecided: (m: string) => void }) {
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const decide = useMutation({
    mutationFn: (approve: boolean) => (approve ? disposalApi.approve(d.id, remarks || undefined) : disposalApi.reject(d.id, remarks || undefined)),
    onSuccess: (r) => onDecided(`${r.disposal_number}: ${r.status}.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not record the decision.'),
  })
  const reject = () => {
    setError(null)
    if (!remarks.trim()) return setError('Give the reason for rejecting the disposal.')
    decide.mutate(false)
  }
  return (
    <Modal title={`Disposal ${d.disposal_number}`} onClose={onClose} width={760}>
      <dl className="detail-grid">
        <dt>Status</dt>
        <dd>
          <Badge map={DISPOSAL_STATUS} value={d.status} />
        </dd>
        <dt>What</dt>
        <dd>
          {d.category === 'property' ? (
            <>
              <span className="mono">{d.tag_number}</span> {d.item_name}
            </>
          ) : (
            <>
              {formatQty(d.quantity)} {d.unit_of_measure} {d.item_name}
            </>
          )}
        </dd>
        <dt>Mode</dt>
        <dd>{modeLabel(d.disposal_type)}</dd>
        <dt>Value</dt>
        <dd>{formatPeso(d.status === 'completed' ? d.book_value : d.value)}</dd>
        <dt>Requested</dt>
        <dd>
          {d.requester.name}, {formatDate(d.request_date)}
        </dd>
        <dt>Reason</dt>
        <dd>{d.reason}</dd>
        {d.remarks && (
          <>
            <dt>Requester's remarks</dt>
            <dd>{d.remarks}</dd>
          </>
        )}
        {d.approver && (
          <>
            <dt>{d.status === 'rejected' ? 'Rejected by' : 'Approved by'}</dt>
            <dd>
              {d.approver.name}
              {d.approval_date ? `, ${formatDate(d.approval_date)}` : ''}
              {d.approval_remarks && <div className="muted">“{d.approval_remarks}”</div>}
            </dd>
          </>
        )}
      </dl>
      <AttachmentsPanel type="DisposalRequest" id={d.id} label={d.disposal_number} docType="OTHER" />
      {d.can_decide && (
        <div style={{ marginTop: '1rem' }}>
          {error && <div className="alert alert-error">{error}</div>}
          <div className="field">
            <label htmlFor="dd-rem">Remarks</label>
            <textarea id="dd-rem" rows={2} maxLength={1000} value={remarks} onChange={(e) => setRemarks(e.target.value)} placeholder="Required when rejecting" />
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-danger" disabled={decide.isPending} onClick={reject}>
              Reject
            </button>
            <button type="button" className="btn btn-primary" disabled={decide.isPending} onClick={() => { setError(null); decide.mutate(true) }}>
              Approve
            </button>
          </div>
        </div>
      )}
    </Modal>
  )
}

function CompleteForm({ disposal, onClose, onSaved }: { disposal: Disposal; onClose: () => void; onSaved: (m: string) => void }) {
  const [date, setDate] = useState(today())
  const [proceeds, setProceeds] = useState('')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const sale = SALE_MODES.includes(disposal.disposal_type)
  const save = useMutation({
    mutationFn: () => disposalApi.complete(disposal.id, { disposal_date: date, proceeds: sale && proceeds ? Number(proceeds) : null, remarks }),
    onSuccess: (d) => onSaved(`${d.disposal_number} completed; book value ${formatPeso(d.book_value)} written off.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not complete.'),
  })
  return (
    <Modal title={`Complete ${disposal.disposal_number}`} onClose={onClose} width={520}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <p className="muted" style={{ marginBottom: '1rem' }}>
          {disposal.category === 'property' ? `Asset ${disposal.tag_number} will be marked disposed.` : `${disposal.quantity} ${disposal.unit_of_measure} of ${disposal.item_name} will be written off (expired lots first).`}
        </p>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="cp-date">Date disposed</label>
            <input id="cp-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
          </div>
          {sale && (
            <div className="field">
              <label htmlFor="cp-proc">Proceeds (₱)</label>
              <input id="cp-proc" type="number" min={0} step="0.01" value={proceeds} onChange={(e) => setProceeds(e.target.value)} />
            </div>
          )}
          <div className="field span-2">
            <label htmlFor="cp-rem">Remarks</label>
            <input id="cp-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Completing…' : 'Complete disposal'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

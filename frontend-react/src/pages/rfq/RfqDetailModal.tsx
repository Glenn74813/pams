import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { bacApi, inventoryApi, rfqApi, supplierApi } from '../../api/endpoints'
import type { Rfq } from '../../api/types'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatDateTime, formatPeso, formatQty, today } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { AbstractSlip } from './AbstractSlip'
import { RFQ_STATUS } from './labels'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'

interface Props {
  rfqId: number
  onClose: () => void
  onEdit: (r: Rfq) => void
  onChanged: (message: string, closed?: boolean) => void
}

export function RfqDetailModal({ rfqId, onClose, onEdit, onChanged }: Props) {
  const q = useQuery({ queryKey: ['rfq', rfqId], queryFn: () => rfqApi.get(rfqId) })
  const [printing, setPrinting] = useState(false)
  const r = q.data
  return (
    <Modal title={r ? `${r.rfq_number} — ${r.procurement_method}` : 'Request for Quotation'} onClose={onClose} width={1000}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {r && printing && <Abstract rfqId={r.id} onBack={() => setPrinting(false)} />}
      {r && !printing && <Details key={r.status + r.quotations_count} r={r} onPrint={() => setPrinting(true)} onEdit={onEdit} onChanged={onChanged} />}
      {r && !printing && <AttachmentsPanel type="Rfq" id={r.id} label={r.rfq_number} docType="RFQ" />}
    </Modal>
  )
}

function Abstract({ rfqId, onBack }: { rfqId: number; onBack: () => void }) {
  const a = useQuery({ queryKey: ['rfq-abstract', rfqId], queryFn: () => rfqApi.abstract(rfqId) })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  return (
    <>
      <div className="slip-toolbar no-print">
        <button className="btn btn-secondary btn-sm" onClick={onBack}>
          ‹ Back
        </button>
        <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
          🖨 Print
        </button>
      </div>
      {a.data ? <AbstractSlip a={a.data} config={config.data} /> : <div className="page-loading">Loading…</div>}
    </>
  )
}

type Mode = null | 'quote' | 'award' | 'cancel' | 'delete' | { reject: number }

function Details({ r, onPrint, onEdit, onChanged }: { r: Rfq; onPrint: () => void; onEdit: Props['onEdit']; onChanged: Props['onChanged'] }) {
  const [mode, setMode] = useState<Mode>(null)
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const ok = (m: string, closed?: boolean) => () => {
    setMode(null)
    setText('')
    onChanged(m, closed)
  }
  const issue = useMutation({ mutationFn: () => rfqApi.issue(r.id), onSuccess: ok(`${r.rfq_number} issued to suppliers.`), onError: fail })
  const close = useMutation({ mutationFn: () => rfqApi.close(r.id), onSuccess: ok(`${r.rfq_number} closed; ready for award.`), onError: fail })
  const cancel = useMutation({ mutationFn: () => rfqApi.cancel(r.id, text || undefined), onSuccess: ok(`${r.rfq_number} cancelled.`), onError: fail })
  const remove = useMutation({ mutationFn: () => rfqApi.remove(r.id), onSuccess: (x) => onChanged(x.message, true), onError: fail })
  const responsive = useMutation({
    mutationFn: (v: { id: number; on: boolean; why?: string }) => rfqApi.setResponsive(v.id, v.on, v.why),
    onSuccess: ok('Quotation updated.'),
    onError: fail,
  })
  const dropQuote = useMutation({ mutationFn: (id: number) => rfqApi.removeQuotation(id), onSuccess: ok('Quotation removed.'), onError: fail })
  const noa = useMutation({ mutationFn: () => rfqApi.noa(r.id), onSuccess: (x) => onChanged(`Notice of Award ${x.noa_number} issued.`), onError: fail })
  const ntp = useMutation({ mutationFn: () => rfqApi.ntp(r.id), onSuccess: (x) => onChanged(`Notice to Proceed ${x.ntp_number} issued.`), onError: fail })
  const busy = [issue, close, cancel, remove, responsive, dropQuote, noa, ntp].some((m) => m.isPending)
  // Negotiated Procurement proceeds even with one response (IRR 35.1.1).
  const enough = r.single_source || r.procurement_method === 'Negotiated Procurement' || r.quotations_count >= 3

  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <dl className="detail-grid">
        <dt>Status</dt>
        <dd>
          <Badge map={RFQ_STATUS} value={r.status} />
        </dd>
        <dt>Title</dt>
        <dd>{r.title}</dd>
        <dt>Purchase request</dt>
        <dd>
          {r.purchase_request?.name} · {r.department?.name}
        </dd>
        <dt>ABC</dt>
        <dd>{formatPeso(r.abc)}</dd>
        <dt>Deadline</dt>
        <dd>
          {formatDateTime(r.deadline)} {r.status === 'issued' && (r.deadline_passed ? <span className="badge st-partial">passed</span> : <span className="muted">open</span>)}
        </dd>
        {(r.delivery_term || r.delivery_lead_days != null) && (
          <>
            <dt>Delivery</dt>
            <dd>
              {r.delivery_term}
              {r.delivery_lead_days != null && ` · within ${r.delivery_lead_days} days`}
            </dd>
          </>
        )}
        {r.negotiated_ground && (
          <>
            <dt>Ground</dt>
            <dd>{r.negotiated_ground.replaceAll('_', ' ')}</dd>
          </>
        )}
        {r.invited_suppliers.length > 0 && (
          <>
            <dt>Invited</dt>
            <dd>{r.invited_suppliers.join(', ')}</dd>
          </>
        )}
        {r.justification && (
          <>
            <dt>Justification</dt>
            <dd>{r.justification}</dd>
          </>
        )}
        {r.awarded_supplier && (
          <>
            <dt>Award</dt>
            <dd>
              <strong>{r.awarded_supplier.name}</strong> · {formatDate(r.awarded_at)} · {r.bac_resolution?.name}
            </dd>
            <dt>Notices</dt>
            <dd>
              {r.noa_number ? `${r.noa_number} (${formatDate(r.noa_date)})` : 'Notice of Award not issued'} · {r.ntp_number ? `${r.ntp_number} (${formatDate(r.ntp_date)})` : 'Notice to Proceed not issued'}
            </dd>
          </>
        )}
        {r.remarks && (
          <>
            <dt>Remarks</dt>
            <dd>{r.remarks}</dd>
          </>
        )}
      </dl>

      <div className="table-wrap" style={{ marginTop: '1rem' }}>
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Item</th>
              <th className="num">Qty</th>
              <th className="num">ABC / unit</th>
              <th className="num">ABC</th>
            </tr>
          </thead>
          <tbody>
            {r.items?.map((l) => (
              <tr key={l.id}>
                <td>
                  {l.general_description}
                  {l.specifications && <div className="muted small">{l.specifications}</div>}
                </td>
                <td className="num">
                  {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
                </td>
                <td className="num">{formatPeso(l.abc_unit_cost)}</td>
                <td className="num">{formatPeso(l.abc_total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {r.status !== 'draft' && (
        <>
          <div className="section-header" style={{ marginTop: '1rem' }}>
            <h4>
              Quotations ({r.quotations_count}){' '}
              {!enough && r.status !== 'cancelled' && <span className="badge st-pending">fewer than 3</span>}
            </h4>
            {r.can_quote && mode === null && (
              <button className="btn btn-outline btn-sm" onClick={() => setMode('quote')}>
                + Record quotation
              </button>
            )}
          </div>
          {mode === 'quote' && <QuoteForm r={r} onCancel={() => setMode(null)} onSaved={ok('Quotation recorded.')} />}
          <div className="table-wrap">
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Supplier</th>
                  <th>Submitted</th>
                  <th className="num">Total</th>
                  <th>Evaluation</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {r.quotations?.map((x) => (
                  <tr key={x.id} className={x.is_responsive ? '' : 'row-muted'}>
                    <td>
                      {x.is_winner && '🏆 '}
                      {x.supplier.name}
                      {x.quotation_number && <span className="muted small"> · {x.quotation_number}</span>}
                    </td>
                    <td>{formatDate(x.submitted_at)}</td>
                    <td className="num">{formatPeso(x.total_amount)}</td>
                    <td>
                      {!x.is_responsive && <span className="badge st-bad">Non-responsive</span>} {x.over_abc && <span className="badge st-bad">Over ABC</span>}{' '}
                      {!x.supplier_eligible && <span className="badge st-bad">Not eligible</span>}
                      {x.remarks && <div className="small">{x.remarks}</div>}
                    </td>
                    <td>
                      {(r.status === 'issued' || r.status === 'closed') && r.can_cancel && (
                        <div className="workflow-actions">
                          {x.is_responsive ? (
                            <button className="btn btn-outline btn-sm" onClick={() => { setText(''); setMode({ reject: x.id }) }} disabled={busy}>
                              Non-responsive
                            </button>
                          ) : (
                            <button className="btn btn-outline btn-sm" onClick={() => responsive.mutate({ id: x.id, on: true })} disabled={busy}>
                              Restore
                            </button>
                          )}
                          <button className="icon-btn delete" title={`Remove ${x.supplier.name}'s quotation`} onClick={() => dropQuote.mutate(x.id)} disabled={busy}>
                            🗑
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
                {r.quotations?.length === 0 && (
                  <tr>
                    <td colSpan={5} className="empty">
                      No quotations recorded.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}

      {typeof mode === 'object' && mode !== null && 'reject' in mode && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="rq-nr">Why is it not responsive? *</label>
            <input id="rq-nr" value={text} onChange={(e) => setText(e.target.value)} autoFocus placeholder="e.g. Specifications not met" />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setMode(null)}>
              Cancel
            </button>
            <button className="btn btn-danger" onClick={() => responsive.mutate({ id: mode.reject, on: false, why: text })} disabled={busy || !text.trim()}>
              Mark non-responsive
            </button>
          </div>
        </div>
      )}
      {mode === 'award' && <AwardForm r={r} onCancel={() => setMode(null)} onSaved={ok(`${r.rfq_number} awarded.`)} />}
      {mode === 'cancel' && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="rq-cx">Reason for cancelling{r.status === 'draft' ? '' : ' *'}</label>
            <input id="rq-cx" value={text} onChange={(e) => setText(e.target.value)} autoFocus placeholder="e.g. Failure of quotations" />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setMode(null)}>
              Back
            </button>
            <button className="btn btn-danger" onClick={() => cancel.mutate()} disabled={busy || (r.status !== 'draft' && !text.trim())}>
              Cancel RFQ
            </button>
          </div>
        </div>
      )}
      {mode === 'delete' && (
        <ConfirmDialog title="Delete RFQ" message={`Delete the draft ${r.rfq_number}?`} confirmLabel="Delete" busy={busy} onConfirm={() => remove.mutate()} onCancel={() => setMode(null)} />
      )}

      {mode === null && (
        <div className="modal-footer">
          {r.status !== 'draft' && (
            <button className="btn btn-outline" onClick={onPrint}>
              🖨 Abstract
            </button>
          )}
          {r.can_delete && (
            <button className="btn btn-danger" onClick={() => setMode('delete')}>
              Delete
            </button>
          )}
          {r.can_cancel && r.status !== 'draft' && (
            <button className="btn btn-secondary" onClick={() => setMode('cancel')}>
              Cancel RFQ
            </button>
          )}
          {r.can_edit && (
            <button className="btn btn-secondary" onClick={() => onEdit(r)}>
              Edit
            </button>
          )}
          {r.can_issue && (
            <button className="btn btn-primary" onClick={() => issue.mutate()} disabled={busy}>
              Issue to suppliers
            </button>
          )}
          {r.status === 'issued' && !r.deadline_passed && <span className="muted footer-note">Closes after the deadline.</span>}
          {r.can_close && (
            <button className="btn btn-primary" onClick={() => close.mutate()} disabled={busy}>
              Close &amp; open quotations
            </button>
          )}
          {r.can_award && (
            <button className="btn btn-primary" onClick={() => setMode('award')}>
              Award…
            </button>
          )}
          {r.can_noa && (
            <button className="btn btn-primary" onClick={() => noa.mutate()} disabled={busy}>
              Issue Notice of Award
            </button>
          )}
          {r.can_ntp && (
            <button className="btn btn-primary" onClick={() => ntp.mutate()} disabled={busy}>
              Issue Notice to Proceed
            </button>
          )}
        </div>
      )}
    </>
  )
}

function QuoteForm({ r, onCancel, onSaved }: { r: Rfq; onCancel: () => void; onSaved: () => void }) {
  const suppliers = useQuery({ queryKey: ['supplier-options'], queryFn: supplierApi.options })
  const [supplierId, setSupplierId] = useState('')
  const [name, setName] = useState('')
  const [number, setNumber] = useState('')
  const [date, setDate] = useState(today())
  const [prices, setPrices] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () =>
      rfqApi.addQuotation(r.id, {
        supplier_id: supplierId && supplierId !== 'new' ? Number(supplierId) : null,
        supplier_name: supplierId === 'new' ? name : undefined,
        quotation_number: number || null,
        submitted_at: date,
        items: (r.items ?? []).map((l) => ({ rfq_item_id: l.id, unit_price: Number(prices[l.id]) })),
      }),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not record the quotation.'),
  })
  const total = (r.items ?? []).reduce((s, l) => s + l.quantity * (Number(prices[l.id]) || 0), 0)
  const quoted = new Set(r.quotations?.map((x) => x.supplier.id))
  return (
    <div className="reject-box">
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-grid three">
        <div className="field">
          <label htmlFor="qt-sup">Supplier *</label>
          <select id="qt-sup" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
            <option value="">Choose</option>
            {suppliers.data?.data.filter((s) => !quoted.has(s.id)).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
            <option value="new">+ New supplier…</option>
          </select>
        </div>
        {supplierId === 'new' && (
          <div className="field">
            <label htmlFor="qt-name">Supplier name *</label>
            <input id="qt-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={255} />
          </div>
        )}
        <div className="field">
          <label htmlFor="qt-no">Quotation no.</label>
          <input id="qt-no" value={number} onChange={(e) => setNumber(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="qt-date">Received</label>
          <input id="qt-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
        </div>
      </div>
      <div className="table-wrap" style={{ marginTop: '0.75rem' }}>
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Item</th>
              <th className="num">Qty</th>
              <th className="num">ABC / unit</th>
              <th className="num">Quoted / unit *</th>
            </tr>
          </thead>
          <tbody>
            {r.items?.map((l) => (
              <tr key={l.id}>
                <td>{l.general_description}</td>
                <td className="num">{formatQty(l.quantity)}</td>
                <td className="num">{formatPeso(l.abc_unit_cost)}</td>
                <td className="num">
                  <input className="qty-input" style={{ width: '7.5rem' }} type="number" min={0} step="0.01" aria-label={`Unit price for ${l.general_description}`} value={prices[l.id] ?? ''} onChange={(e) => setPrices((p) => ({ ...p, [l.id]: e.target.value }))} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="form-total">
        Total <strong>{formatPeso(total)}</strong> {total > r.abc && <span className="badge st-bad">over the ABC</span>}
      </p>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button
          className="btn btn-primary"
          onClick={() => {
            setError(null)
            if (!supplierId || (supplierId === 'new' && !name.trim())) return setError('Choose or name the supplier.')
            if ((r.items ?? []).some((l) => prices[l.id] === undefined || prices[l.id] === '')) return setError('Price every line.')
            save.mutate()
          }}
          disabled={save.isPending}
        >
          {save.isPending ? 'Saving…' : 'Record quotation'}
        </button>
      </div>
    </div>
  )
}

function AwardForm({ r, onCancel, onSaved }: { r: Rfq; onCancel: () => void; onSaved: () => void }) {
  const abs = useQuery({ queryKey: ['rfq-abstract', r.id], queryFn: () => rfqApi.abstract(r.id) })
  const res = useQuery({
    queryKey: ['bac-resolutions', 'award', r.purchase_request?.id],
    queryFn: () => bacApi.resolutions({ page: 1, status: 'approved', purchase_request_id: r.purchase_request?.id }),
  })
  const [pick, setPick] = useState<string | null>(null)
  const [resolution, setResolution] = useState('')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const recommended = abs.data?.recommended
  const chosen = pick ?? (recommended ? String(recommended.id) : '')
  const usable = res.data?.data.filter((x) => ['resolution_to_award', 'award', 'recommendation'].includes(x.resolution_type) && x.used_by.length === 0) ?? []
  const save = useMutation({
    mutationFn: () => rfqApi.award(r.id, { quotation_id: Number(chosen), bac_resolution_id: Number(resolution), remarks: remarks || undefined }),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not award.'),
  })
  return (
    <div className="reject-box">
      {error && <div className="alert alert-error">{error}</div>}
      {abs.data && !recommended && <div className="alert alert-warning">No responsive quotation is within the ABC. Cancel the RFQ for failure of quotations.</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="aw-q">Award to *</label>
          <select id="aw-q" value={chosen} onChange={(e) => setPick(e.target.value)}>
            <option value="">Choose</option>
            {r.quotations?.filter((x) => x.is_responsive && !x.over_abc).map((x) => (
              <option key={x.id} value={x.id} disabled={!x.supplier_eligible}>
                {x.supplier.name} — {formatPeso(x.total_amount)}
                {recommended?.id === x.id ? ' (lowest)' : ''}
                {x.supplier_eligible ? '' : ' — not eligible (documents or sanctions)'}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="aw-r">BAC resolution *</label>
          <select id="aw-r" value={resolution} onChange={(e) => setResolution(e.target.value)}>
            <option value="">{usable.length ? 'Choose' : 'No approved award resolution for this PR'}</option>
            {usable.map((x) => (
              <option key={x.id} value={x.id}>
                {x.resolution_number} — {x.title}
              </option>
            ))}
          </select>
        </div>
        <div className="field span-2">
          <label htmlFor="aw-rem">Remarks{abs.data?.insufficient_quotations ? ' * (fewer than 3 quotations — justify the award)' : ''}</label>
          <input id="aw-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" onClick={() => save.mutate()} disabled={save.isPending || !chosen || !resolution}>
          {save.isPending ? 'Awarding…' : 'Award'}
        </button>
      </div>
    </div>
  )
}

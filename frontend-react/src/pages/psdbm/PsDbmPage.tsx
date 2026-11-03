import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { psDbmApi, type PsCatalogueItem, type PsOrder, type PsOrderLine, type PsPaymentMode, PS_RELEASE_GROUNDS, type PsReleaseGround } from '../../api/psdbm'
import { useAuth } from '../../auth/AuthContext'
import { HelpLink } from '../../components/HelpLink'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso, formatQty, today } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

const STATUS: Record<string, [string, string]> = {
  draft: ['Draft', 'badge-muted'],
  submitted: ['In Virtual Store', 'st-pending'],
  paid: ['Paid', 'st-partial'],
  delivered: ['Delivered (DR)', 'st-ok'],
  cancelled: ['Cancelled', 'st-bad'],
}
const PAYMENT: Record<PsPaymentMode, string> = { cash: 'Cash', lddap_ada: 'LDDAP-ADA', check: 'Check', unutilized_funds: 'Unutilized funds with PS' }
const message = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

/** Common-use supplies and equipment bought from the Procurement Service of the DBM. */
export function PsDbmPage() {
  const [tab, setTab] = useState<'requests' | 'catalogue'>('requests')
  return (
    <div className="page page-wide">
      <h1 className="page-title">
        PS-DBM (Common-Use Supplies) <HelpLink slug="ps-dbm" />
      </h1>
      <p className="muted small" style={{ marginTop: '-0.5rem' }}>
        Common-use supplies and equipment are bought from the Procurement Service of the DBM without bidding: an Agency Procurement Request (APR) placed in the
        PhilGEPS Virtual Store, paid ahead, then picked up or delivered against PS's Delivery Receipt and inspected in Receiving.
      </p>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'requests'} className={tab === 'requests' ? 'active' : ''} onClick={() => setTab('requests')}>
          Agency Procurement Requests
        </button>
        <button role="tab" aria-selected={tab === 'catalogue'} className={tab === 'catalogue' ? 'active' : ''} onClick={() => setTab('catalogue')}>
          PS price catalogue
        </button>
      </div>
      {tab === 'requests' ? <RequestsTab /> : <CatalogueTab />}
    </div>
  )
}

function RequestsTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const toast = useToast()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<PsOrder | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const list = useQuery({ queryKey: ['ps-orders', page, status, q], queryFn: () => psDbmApi.list({ page, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['ps-orders', 'ps-order', 'ps-pr-options', 'purchase-orders']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search APR, PR, Virtual Store or DR no." value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} aria-label="Search requests" />
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="">All statuses</option>
            {Object.entries(STATUS).map(([k, [label]]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </select>
        </div>
        {can('edit_procurement') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New request
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>APR no.</th>
              <th>Purchase request</th>
              <th>Office</th>
              <th>Virtual Store</th>
              <th className="num">Amount</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((o) => (
              <tr key={o.id}>
                <td className="mono">{o.apr_number}</td>
                <td className="mono">{o.purchase_request.name}</td>
                <td>{o.department ?? '—'}</td>
                <td>{o.vs_reference ?? '—'}</td>
                <td className="num">{formatPeso(o.total_amount)}</td>
                <td>
                  <Badge map={STATUS} value={o.status} />
                  {o.purchase_order_status && <div className="muted small">receiving: {o.purchase_order_status.replace('_', ' ')}</div>}
                </td>
                <td>
                  <button className="btn btn-outline btn-sm" onClick={() => setViewing(o.id)}>
                    Open
                  </button>
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No Agency Procurement Requests yet. Plan common-use lines as “Procurement from PS-DBM” on the PPMP and purchase request.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {form && (
        <OrderForm
          order={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(o) => {
            toast(`${o.apr_number} saved.`, 'success')
            setForm(null)
            refresh()
            setViewing(o.id)
          }}
        />
      )}
      {viewing && (
        <OrderDetail
          id={viewing}
          onClose={() => setViewing(null)}
          onEdit={(o) => {
            setViewing(null)
            setForm(o)
          }}
          onChanged={(m) => {
            toast(m, 'success')
            refresh()
          }}
        />
      )}
    </>
  )
}

function OrderForm({ order, onClose, onSaved }: { order: PsOrder | null; onClose: () => void; onSaved: (o: PsOrder) => void }) {
  const options = useQuery({ queryKey: ['ps-pr-options'], queryFn: psDbmApi.prOptions })
  const catalogue = useQuery({ queryKey: ['ps-catalogue', 'active'], queryFn: () => psDbmApi.catalogue(undefined, true) })
  const [prId, setPrId] = useState<number | null>(order?.purchase_request.id ?? null)
  const [remarks, setRemarks] = useState(order?.remarks ?? '')
  const [lines, setLines] = useState<Record<number, { on: boolean; qty: string; cat: string; price: string }>>(() =>
    Object.fromEntries((order?.items ?? []).map((l) => [l.purchase_request_item_id, { on: true, qty: String(l.quantity), cat: l.ps_catalogue_item_id ? String(l.ps_catalogue_item_id) : '', price: String(l.unit_price) }])),
  )
  const [error, setError] = useState<string | null>(null)
  const pr = options.data?.data.find((p) => p.id === prId)
  // An edited request's own lines are not in the options (they are taken by it), so show them from the request.
  const prLines = pr?.lines ?? (order?.items ?? []).map((l) => ({ id: l.purchase_request_item_id, general_description: l.description, specifications: null, unit: l.unit, quantity: l.quantity, remaining: l.quantity, estimated_unit_cost: null }))
  const set = (id: number, patch: Partial<{ on: boolean; qty: string; cat: string; price: string }>) =>
    setLines((ls) => ({ ...ls, [id]: { ...(ls[id] ?? { on: false, qty: '', cat: '', price: '' }), ...patch } }))
  const save = useMutation({
    mutationFn: () => {
      const items = prLines.filter((l) => lines[l.id]?.on).map((l) => {
        const v = lines[l.id]
        return { purchase_request_item_id: l.id, quantity: Number(v.qty || l.remaining), ps_catalogue_item_id: v.cat ? Number(v.cat) : null, unit_price: v.cat ? null : v.price ? Number(v.price) : null }
      })
      const input = { purchase_request_id: prId!, remarks: remarks || null, items }
      return order ? psDbmApi.update(order.id, input) : psDbmApi.create(input)
    },
    onSuccess: onSaved,
    onError: (err) => setError(message(err, 'Could not save the request.')),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!prId) return setError('Choose the approved purchase request.')
    if (!prLines.some((l) => lines[l.id]?.on)) return setError('Tick at least one line.')
    save.mutate()
  }
  return (
    <Modal title={order ? `Edit ${order.apr_number}` : 'New Agency Procurement Request'} onClose={onClose} width={900}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field">
          <label htmlFor="ps-pr">Approved purchase request *</label>
          {order ? (
            <div className="chosen">
              <span className="mono">{order.purchase_request.name}</span>
            </div>
          ) : (
            <select id="ps-pr" value={prId ?? ''} onChange={(e) => { setPrId(e.target.value ? Number(e.target.value) : null); setLines({}) }} required>
              <option value="">{options.data?.data.length ? 'Choose a PR with PS-DBM lines' : 'No approved PR has PS-DBM lines left'}</option>
              {options.data?.data.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.pr_number} — {p.department ?? ''} — {p.purpose ?? ''}
                </option>
              ))}
            </select>
          )}
        </div>
        {prLines.length > 0 && (
          <div className="table-wrap" style={{ marginTop: '0.75rem' }}>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th />
                  <th>PR line</th>
                  <th className="num">Left</th>
                  <th>PS catalogue item</th>
                  <th className="num">PS price (₱)</th>
                  <th className="num">Qty</th>
                </tr>
              </thead>
              <tbody>
                {prLines.map((l) => {
                  const v = lines[l.id] ?? { on: false, qty: '', cat: '', price: '' }
                  const cat = catalogue.data?.data.find((c) => String(c.id) === v.cat)
                  return (
                    <tr key={l.id}>
                      <td>
                        <input type="checkbox" checked={v.on} onChange={(e) => set(l.id, { on: e.target.checked })} aria-label={`Include ${l.general_description}`} />
                      </td>
                      <td>
                        {l.general_description} <span className="muted small">{l.unit}</span>
                        {l.estimated_unit_cost != null && <div className="muted small">PR estimate {formatPeso(l.estimated_unit_cost)}</div>}
                      </td>
                      <td className="num">{formatQty(l.remaining)}</td>
                      <td>
                        <select value={v.cat} onChange={(e) => set(l.id, { cat: e.target.value, on: true })} aria-label={`Catalogue item for ${l.general_description}`}>
                          <option value="">— enter the PS price —</option>
                          {catalogue.data?.data.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.code} · {c.description} ({c.unit}) {formatPeso(c.unit_price)}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="num">
                        {cat ? (
                          formatPeso(cat.unit_price)
                        ) : (
                          <input className="qty-input" type="number" min={0} step="0.01" value={v.price} onChange={(e) => set(l.id, { price: e.target.value, on: true })} aria-label={`PS price of ${l.general_description}`} />
                        )}
                      </td>
                      <td className="num">
                        <input className="qty-input" type="number" min={1} max={l.remaining} placeholder={String(l.remaining)} value={v.qty} onChange={(e) => set(l.id, { qty: e.target.value, on: true })} aria-label={`Quantity of ${l.general_description}`} />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        <div className="field" style={{ marginTop: '0.75rem' }}>
          <label htmlFor="ps-rem">Remarks</label>
          <input id="ps-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        </div>
        <p className="hint">The PS price may not exceed the PR estimate. Prices come from the PS catalogue tab; update it from the Virtual Store price list.</p>
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

type Step = 'submit' | 'pay' | 'deliver' | 'cancel' | 'delete' | { cnas: PsOrderLine } | null

function OrderDetail({ id, onClose, onEdit, onChanged }: { id: number; onClose: () => void; onEdit: (o: PsOrder) => void; onChanged: (m: string) => void }) {
  const queryClient = useQueryClient()
  const q = useQuery({ queryKey: ['ps-order', id], queryFn: () => psDbmApi.get(id) })
  const [step, setStep] = useState<Step>(null)
  const [error, setError] = useState<string | null>(null)
  const [f, setF] = useState<Record<string, string>>({})
  const o = q.data
  const done = (m: string) => {
    setStep(null)
    setF({})
    setError(null)
    void queryClient.invalidateQueries({ queryKey: ['ps-order', id] })
    onChanged(m)
  }
  const act = useMutation({
    mutationFn: async (): Promise<string> => {
      if (!o) return ''
      if (step === 'submit') return (await psDbmApi.submit(o.id, { vs_reference: f.ref ?? '', submitted_date: f.date || undefined })).apr_number + ' placed in the Virtual Store.'
      if (step === 'pay') return (await psDbmApi.pay(o.id, { payment_mode: (f.mode || 'lddap_ada') as PsPaymentMode, payment_reference: f.ref ?? '', payment_date: f.date || undefined, amount_paid: Number(f.amount ?? o.total_amount) })).apr_number + ' paid.'
      if (step === 'deliver') return (await psDbmApi.deliver(o.id, { dr_number: f.ref ?? '', dr_date: f.date || undefined, delivery_mode: (f.mode || 'pickup') as 'pickup' | 'delivery', delivery_address: f.address || undefined })).apr_number + ' delivered; inspect it in Receiving.'
      if (step === 'cancel') return (await psDbmApi.cancel(o.id, f.reason ?? '')).apr_number + ' cancelled.'
      if (step === 'delete') return (await psDbmApi.remove(o.id)).message
      if (step && typeof step === 'object') return (await psDbmApi.notAvailable(o.id, step.cnas.id, { release_ground: (f.ground || 'zero_inventory') as PsReleaseGround, available_quantity: f.ground === 'insufficient_stock' && f.available ? Number(f.available) : undefined, cnas_number: f.ref ?? '', cnas_date: f.date || undefined })).apr_number + ': line marked not available.'
      return ''
    },
    onSuccess: (m) => {
      if (step === 'delete') {
        onChanged(m)
        onClose()
      } else done(m)
    },
    onError: (err) => setError(message(err, 'The action failed.')),
  })
  const field = (key: string, label: string, type = 'text') => (
    <div className="field">
      <label htmlFor={`psd-${key}`}>{label}</label>
      <input id={`psd-${key}`} type={type} value={f[key] ?? ''} max={type === 'date' ? today() : undefined} onChange={(e) => setF({ ...f, [key]: e.target.value })} />
    </div>
  )
  return (
    <Modal title={o ? `Agency Procurement Request ${o.apr_number}` : 'Agency Procurement Request'} onClose={onClose} width={900}>
      {!o ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={STATUS} value={o.status} />
            </dd>
            <dt>Purchase request</dt>
            <dd className="mono">{o.purchase_request.name}</dd>
            <dt>Office</dt>
            <dd>{o.department ?? '—'}</dd>
            <dt>Virtual Store order</dt>
            <dd>{o.vs_reference ? `${o.vs_reference}, ${formatDate(o.submitted_date)}` : '—'}</dd>
            <dt>Payment</dt>
            <dd>{o.payment_mode ? `${PAYMENT[o.payment_mode]} ${o.payment_reference}, ${formatDate(o.payment_date)} — ${formatPeso(o.amount_paid)}` : '—'}</dd>
            <dt>PS Delivery Receipt</dt>
            <dd>{o.dr_number ? `${o.dr_number}, ${formatDate(o.dr_date)} (${o.delivery_mode === 'delivery' ? 'delivered' : 'picked up'})` : '—'}</dd>
            {o.purchase_order && (
              <>
                <dt>Receiving</dt>
                <dd>
                  Order <span className="mono">{o.purchase_order.name}</span> to PS-DBM — {o.purchase_order_status?.replace('_', ' ')}.{' '}
                  <Link to="/receiving">Inspect and accept in Receiving</Link>
                </dd>
              </>
            )}
            {o.remarks && (
              <>
                <dt>Remarks</dt>
                <dd style={{ whiteSpace: 'pre-line' }}>{o.remarks}</dd>
              </>
            )}
          </dl>
          <div className="table-wrap" style={{ marginTop: '1rem' }}>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="num">Qty</th>
                  <th className="num">PS price</th>
                  <th className="num">Amount</th>
                  <th>Stock at PS</th>
                </tr>
              </thead>
              <tbody>
                {o.items?.map((l) => (
                  <tr key={l.id} className={l.availability === 'not_available' ? 'muted' : undefined}>
                    <td>
                      {l.catalogue_code && <span className="mono">{l.catalogue_code} </span>}
                      {l.description} <span className="muted small">{l.unit}</span>
                    </td>
                    <td className="num">{formatQty(l.quantity)}</td>
                    <td className="num">{formatPeso(l.unit_price)}</td>
                    <td className="num">{l.availability === 'available' ? formatPeso(l.amount) : '—'}</td>
                    <td>
                      {l.availability === 'available' ? (
                        o.can_mark_unavailable ? (
                          <button className="btn btn-outline btn-sm" onClick={() => { setF({}); setStep({ cnas: l }) }}>
                            Cannot buy from PS…
                          </button>
                        ) : (
                          'Available'
                        )
                      ) : (
                        <span className="small">
                          {PS_RELEASE_GROUNDS[l.release_ground ?? 'zero_inventory'].label}: {l.cnas_number}, {formatDate(l.cnas_date)} — may be bought elsewhere
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
                <tr>
                  <td colSpan={3} className="num">
                    <strong>Total</strong>
                  </td>
                  <td className="num">
                    <strong>{formatPeso(o.total_amount)}</strong>
                  </td>
                  <td />
                </tr>
              </tbody>
            </table>
          </div>

          {error && <div className="alert alert-error" style={{ marginTop: '0.75rem' }}>{error}</div>}
          {step === 'submit' && (
            <div className="reject-box">
              <div className="form-grid">
                {field('ref', 'Virtual Store order reference *')}
                {field('date', 'Order date', 'date')}
              </div>
              <StepButtons label="Record order" busy={act.isPending} onCancel={() => setStep(null)} onGo={() => act.mutate()} />
            </div>
          )}
          {step === 'pay' && (
            <div className="reject-box">
              <div className="form-grid">
                <div className="field">
                  <label htmlFor="psd-mode">Paid by</label>
                  <select id="psd-mode" value={f.mode ?? 'lddap_ada'} onChange={(e) => setF({ ...f, mode: e.target.value })}>
                    {Object.entries(PAYMENT).map(([k, l]) => (
                      <option key={k} value={k}>
                        {l}
                      </option>
                    ))}
                  </select>
                </div>
                {field('ref', 'OR / LDDAP-ADA / check no. *')}
                {field('date', 'Payment date', 'date')}
                <div className="field">
                  <label htmlFor="psd-amount">Amount (₱)</label>
                  <input id="psd-amount" type="number" step="0.01" value={f.amount ?? String(o.total_amount)} onChange={(e) => setF({ ...f, amount: e.target.value })} />
                  <span className="hint">The whole request, {formatPeso(o.total_amount)}.</span>
                </div>
              </div>
              <StepButtons label="Record payment" busy={act.isPending} onCancel={() => setStep(null)} onGo={() => act.mutate()} />
            </div>
          )}
          {step === 'deliver' && (
            <div className="reject-box">
              <div className="form-grid">
                {field('ref', 'PS Delivery Receipt no. *')}
                {field('date', 'DR date', 'date')}
                <div className="field">
                  <label htmlFor="psd-dmode">Received by</label>
                  <select id="psd-dmode" value={f.mode ?? 'pickup'} onChange={(e) => setF({ ...f, mode: e.target.value })}>
                    <option value="pickup">Pick-up at the PS depot</option>
                    <option value="delivery">Delivery by PS</option>
                  </select>
                </div>
                {field('address', 'Delivered to')}
              </div>
              <p className="hint">PS releases a request in one lot. An order to PS-DBM opens for inspection and acceptance in Receiving.</p>
              <StepButtons label="Record delivery" busy={act.isPending} onCancel={() => setStep(null)} onGo={() => act.mutate()} />
            </div>
          )}
          {step && typeof step === 'object' && (
            <div className="reject-box">
              <p>
                <strong>{step.cnas.description}</strong>: record why this line cannot come from PS-DBM. It can then go to Small Value Procurement or Direct
                Acquisition, or to Competitive Bidding if above their ceilings.
              </p>
              <div className="form-grid">
                <div className="field span-2">
                  <label htmlFor="psd-ground">Ground *</label>
                  <select id="psd-ground" value={f.ground ?? 'zero_inventory'} onChange={(e) => setF({ ...f, ground: e.target.value })}>
                    {Object.entries(PS_RELEASE_GROUNDS).map(([k, g]) => (
                      <option key={k} value={k}>
                        {g.label}
                      </option>
                    ))}
                  </select>
                </div>
                {field('ref', `${PS_RELEASE_GROUNDS[(f.ground || 'zero_inventory') as PsReleaseGround].reference}: reference *`)}
                {field('date', 'Document date', 'date')}
                {f.ground === 'insufficient_stock' && field('available', `Quantity PS can still supply (of ${step.cnas.quantity}); it stays on this request`)}
              </div>
              <StepButtons label="Mark not available" busy={act.isPending} onCancel={() => setStep(null)} onGo={() => act.mutate()} />
            </div>
          )}
          {step === 'cancel' && (
            <div className="reject-box">
              {field('reason', 'Reason for cancelling *')}
              <StepButtons label="Cancel request" danger busy={act.isPending} onCancel={() => setStep(null)} onGo={() => act.mutate()} />
            </div>
          )}
          {step === 'delete' && (
            <ConfirmDialog title="Delete request" message={`Delete the draft ${o.apr_number}?`} confirmLabel="Delete" busy={act.isPending} onConfirm={() => act.mutate()} onCancel={() => setStep(null)} />
          )}
          {step === null && (
            <div className="modal-footer">
              {o.can_edit && (
                <>
                  <button className="btn btn-outline" onClick={() => setStep('delete')}>
                    Delete
                  </button>
                  <button className="btn btn-outline" onClick={() => onEdit(o)}>
                    Edit
                  </button>
                </>
              )}
              {o.can_cancel && !o.can_edit && (
                <button className="btn btn-outline" onClick={() => setStep('cancel')}>
                  Cancel request
                </button>
              )}
              {o.can_submit && (
                <button className="btn btn-primary" onClick={() => setStep('submit')}>
                  Placed in Virtual Store…
                </button>
              )}
              {o.can_pay && (
                <button className="btn btn-primary" onClick={() => setStep('pay')}>
                  Record payment…
                </button>
              )}
              {o.can_deliver && (
                <button className="btn btn-primary" onClick={() => setStep('deliver')}>
                  Record Delivery Receipt…
                </button>
              )}
            </div>
          )}
        </>
      )}
    </Modal>
  )
}

function StepButtons({ label, busy, danger, onCancel, onGo }: { label: string; busy: boolean; danger?: boolean; onCancel: () => void; onGo: () => void }) {
  return (
    <div className="modal-footer">
      <button type="button" className="btn btn-secondary" onClick={onCancel}>
        Back
      </button>
      <button type="button" className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} disabled={busy} onClick={onGo}>
        {busy ? 'Saving…' : label}
      </button>
    </div>
  )
}

function CatalogueTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const q = useDebounced(search.trim())
  const list = useQuery({ queryKey: ['ps-catalogue', q], queryFn: () => psDbmApi.catalogue(q || undefined) })
  const [editing, setEditing] = useState<PsCatalogueItem | 'new' | null>(null)
  const [deleting, setDeleting] = useState<PsCatalogueItem | null>(null)
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['ps-catalogue'] })
  const remove = useMutation({
    mutationFn: (c: PsCatalogueItem) => psDbmApi.removeCatalogue(c.id),
    onSuccess: (r) => { toast(r.message, 'success'); setDeleting(null); refresh() },
    onError: (err) => { toast(message(err, 'Could not delete it.'), 'error'); setDeleting(null) },
  })
  const edit = can('edit_procurement')
  return (
    <>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search code or description" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search catalogue" />
        </div>
        {edit && (
          <button className="btn btn-primary" onClick={() => setEditing('new')}>
            + Add item
          </button>
        )}
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>PS code</th>
              <th>Description</th>
              <th>Unit</th>
              <th className="num">PS price</th>
              <th>Price as of</th>
              <th>Inventory item</th>
              <th>Status</th>
              {edit && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((c) => (
              <tr key={c.id} className={c.is_active ? undefined : 'muted'}>
                <td className="mono">{c.code}</td>
                <td>{c.description}</td>
                <td>{c.unit}</td>
                <td className="num">{formatPeso(c.unit_price)}</td>
                <td>{formatDate(c.price_as_of)}</td>
                <td>{c.item_name ?? '—'}</td>
                <td>{c.is_active ? <span className="badge st-ok">In catalogue</span> : <span className="badge badge-muted">Withdrawn</span>}</td>
                {edit && (
                  <td>
                    <div className="actions">
                      <button className="icon-btn edit" title={`Edit ${c.code}`} onClick={() => setEditing(c)}>
                        ✎
                      </button>
                      <button className="icon-btn delete" title={`Delete ${c.code}`} onClick={() => setDeleting(c)}>
                        🗑
                      </button>
                    </div>
                  </td>
                )}
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No catalogue items. Add the common-use items your office buys, with their prices from the PS Virtual Store.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {editing && <CatalogueForm item={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={(m) => { toast(m, 'success'); setEditing(null); refresh() }} />}
      {deleting && (
        <ConfirmDialog title="Delete catalogue item" message={`Delete ${deleting.code}? Items already on a request are kept; withdraw them instead.`} confirmLabel="Delete"
          busy={remove.isPending} onConfirm={() => remove.mutate(deleting)} onCancel={() => setDeleting(null)} />
      )}
    </>
  )
}

function CatalogueForm({ item, onClose, onSaved }: { item: PsCatalogueItem | null; onClose: () => void; onSaved: (m: string) => void }) {
  const [f, setF] = useState({
    code: item?.code ?? '',
    description: item?.description ?? '',
    unit: item?.unit ?? '',
    unit_price: item ? String(item.unit_price) : '',
    price_as_of: item?.price_as_of ?? today(),
    is_active: item?.is_active ?? true,
  })
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      const input = { code: f.code, description: f.description, unit: f.unit, unit_price: Number(f.unit_price), price_as_of: f.price_as_of || null, is_active: f.is_active, item_id: item?.item_id ?? null }
      return item ? psDbmApi.updateCatalogue(item.id, input) : psDbmApi.addCatalogue(input)
    },
    onSuccess: (c) => onSaved(`${c.code} saved.`),
    onError: (err) => setError(message(err, 'Could not save the item.')),
  })
  return (
    <Modal title={item ? `Edit ${item.code}` : 'Add PS catalogue item'} onClose={onClose} width={620}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field">
            <label htmlFor="pc-code">PS item code *</label>
            <input id="pc-code" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} required maxLength={50} />
          </div>
          <div className="field">
            <label htmlFor="pc-unit">Unit *</label>
            <input id="pc-unit" value={f.unit} onChange={(e) => setF({ ...f, unit: e.target.value })} required maxLength={50} />
          </div>
          <div className="field span-2">
            <label htmlFor="pc-desc">Description *</label>
            <input id="pc-desc" value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} required maxLength={500} />
          </div>
          <div className="field">
            <label htmlFor="pc-price">PS price (₱) *</label>
            <input id="pc-price" type="number" min={0} step="0.01" value={f.unit_price} onChange={(e) => setF({ ...f, unit_price: e.target.value })} required />
          </div>
          <div className="field">
            <label htmlFor="pc-asof">Price as of</label>
            <input id="pc-asof" type="date" value={f.price_as_of} max={today()} onChange={(e) => setF({ ...f, price_as_of: e.target.value })} />
          </div>
          <div className="field span-2">
            <label className="checkbox-line">
              <input type="checkbox" checked={f.is_active} onChange={(e) => setF({ ...f, is_active: e.target.checked })} /> In the current PS catalogue
            </label>
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

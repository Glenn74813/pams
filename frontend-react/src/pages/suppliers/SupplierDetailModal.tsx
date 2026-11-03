import { useState, type ReactNode } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { supplierApi } from '../../api/endpoints'
import type { Supplier } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { formatDate, formatPeso, today } from '../../lib/format'
import { AttachmentsPanel } from '../documents/AttachmentsPanel'
import './suppliers.css'

const DOC_TYPES = [
  'PhilGEPS Certificate of Registration (Platinum)',
  'PhilGEPS Organization ID',
  'PhilGEPS Registration',
  'DTI Registration',
  'SEC Registration',
  "Mayor's Permit",
  'Business Registration Permit',
  "Mayor's Permit renewal receipt",
  'BIR Registration',
  'Professional License',
  'Curriculum Vitae',
  'NFCC Computation',
  'Committed Line of Credit',
  'PCAB License',
  'Income or Business Tax Return',
  'Omnibus Sworn Statement',
  'Tax Clearance',
  'Other',
]
const DOC_CLASS: Record<string, string> = { valid: 'st-ok', expiring: 'st-pending', expired: 'st-bad' }

/** A section heading with an optional note and action, styled like the mode table above it. */
function Section({ title, note, action, children }: { title: string; note?: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="sd-section">
      <div className="sd-head">
        <div>
          <h4 className="sd-title">{title}</h4>
          {note && <p className="sd-note">{note}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  )
}

interface Props {
  supplierId: number
  onClose: () => void
  onEdit: (s: Supplier) => void
  onChanged: (message: string, closed?: boolean) => void
}

export function SupplierDetailModal({ supplierId, onClose, onEdit, onChanged }: Props) {
  const { can } = useAuth()
  const q = useQuery({ queryKey: ['supplier', supplierId], queryFn: () => supplierApi.get(supplierId) })
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const ok = (m: string, closed?: boolean) => {
    setError(null)
    onChanged(m, closed)
  }
  const remove = useMutation({ mutationFn: () => supplierApi.remove(supplierId), onSuccess: (r) => ok(r.message, true), onError: (e) => { setDeleting(false); fail(e) } })
  const s = q.data
  const edit = can('edit_suppliers')
  const rate = can('edit_suppliers', 'accept_delivery', 'edit_procurement')
  return (
    <Modal title={s ? `${s.name} (${s.vendor_code ?? '—'})` : 'Supplier'} onClose={onClose} width={1000}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {s && (
        <div className="supplier-detail">
          {error && <div className="alert alert-error">{error}</div>}
          <div className={s.awardable ? 'alert alert-success' : 'alert alert-warning'}>
            {s.awardable
              ? 'Verified for competitive selection.'
              : s.is_sanctioned
                ? `Under ${s.sanction}; not eligible for award.`
                : !s.is_active
                  ? 'Inactive.'
                  : s.eligibility.ready
                    ? 'PhilGEPS Platinum on file — not yet marked eligible for competitive selection. Other modes go by the table under Eligibility requirements.'
                    : `Not eligible: ${[s.eligibility.missing.length ? 'missing ' + s.eligibility.missing.join(', ') : '', s.eligibility.expired.length ? 'expired ' + s.eligibility.expired.join(', ') : ''].filter(Boolean).join('; ')}.`}
          </div>
          <Section title="Supplier details">
            <div className="table-wrap">
              <table className="table table-compact sd-facts">
                <tbody>
                  <tr>
                    <th scope="row">TIN</th>
                    <td>{s.tin ?? '—'}</td>
                  </tr>
                  <tr>
                    <th scope="row">PhilGEPS reg. no.</th>
                    <td>
                      {s.philgeps_reg_number ?? '—'} {s.is_msme && <span className="badge st-partial">MSME</span>}
                    </td>
                  </tr>
                  <tr>
                    <th scope="row">Contact</th>
                    <td>{[s.contact_person, s.email, s.phone, s.mobile_number].filter(Boolean).join(' · ') || '—'}</td>
                  </tr>
                  <tr>
                    <th scope="row">Address</th>
                    <td>{s.address ?? '—'}</td>
                  </tr>
                  {s.credit_terms && (
                    <tr>
                      <th scope="row">Credit terms</th>
                      <td>{s.credit_terms}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </Section>
          <EligibilityPanel s={s} edit={edit} onOk={ok} onError={fail} />
          <DocumentsPanel s={s} edit={can('edit_suppliers', 'create_suppliers')} remove={edit} onOk={ok} onError={fail} />
          <PerformancePanel s={s} />
          <RatingsPanel s={s} rate={rate} onOk={ok} onError={fail} />
          <SanctionsPanel s={s} edit={edit} onOk={ok} onError={fail} />
          <div className="modal-footer">
            {s.can_delete && (
              <button className="btn btn-danger" onClick={() => setDeleting(true)}>
                Delete
              </button>
            )}
            {s.can_edit && (
              <button className="btn btn-secondary" onClick={() => onEdit(s)}>
                Edit details
              </button>
            )}
          </div>
          {deleting && (
            <ConfirmDialog title="Delete supplier" message={`Delete ${s.name}? Suppliers with any history are kept; deactivate them instead.`} confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} onCancel={() => setDeleting(false)} />
          )}
        </div>
      )}
      {s && <AttachmentsPanel type="Supplier" id={s.id} label={s.name} docType="OTHER" />}
    </Modal>
  )
}

interface PanelProps {
  s: Supplier
  onOk: (m: string) => void
  onError: (e: unknown) => void
}

function EligibilityPanel({ s, edit, onOk, onError }: PanelProps & { edit: boolean }) {
  const [why, setWhy] = useState('')
  const [revoking, setRevoking] = useState(false)
  const set = useMutation({
    mutationFn: (to: boolean) => supplierApi.setEligibility(s.id, to, to ? undefined : why),
    onSuccess: (r) => {
      setRevoking(false)
      onOk(r.is_eligible ? `${r.name} marked eligible.` : `${r.name} marked ineligible.`)
    },
    onError,
  })
  return (
    <>
      <Section title="Eligibility requirements" note="What competitive selection needs on file before the supplier can be marked eligible.">
        <div className="table-wrap">
          <table className="table table-compact">
            <thead>
              <tr>
                <th>Document</th>
                <th>Valid until</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {s.eligibility.required.map((r) => (
                <tr key={r.document_type}>
                  <td>{r.document_type}</td>
                  <td className="sd-muted">{r.present ? (r.expiry_date ? formatDate(r.expiry_date) : 'No expiry') : '—'}</td>
                  <td>{r.valid ? <span className="badge st-ok">Ready</span> : r.present ? <span className="badge st-bad">Expired</span> : <span className="badge st-pending">Not on file</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>
      {s.by_mode && (
        <Section title="Documents by mode (GPPB Circular 06-2026)" note="For goods above ₱500,000. Infrastructure and consulting add their licenses, and lower amounts need less. Other modes ask for no documents here.">
          <div className="table-wrap">
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Mode</th>
                  <th>Needs</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {s.by_mode.map((m) => (
                  <tr key={m.mode}>
                    <td>{m.mode}</td>
                    <td>{m.required.join(', ') || 'None'}</td>
                    <td className="sd-missing">{m.missing.length === 0 ? <span className="badge st-ok">Ready</span> : m.missing.join(', ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Section>
      )}
      {edit && (
        <div className="workflow-actions" style={{ marginTop: '0.75rem' }}>
          {!s.is_eligible && (
            <button className="btn btn-primary btn-sm" disabled={set.isPending || !s.eligibility.ready || s.is_sanctioned || !s.is_active} onClick={() => set.mutate(true)}>
              Mark eligible
            </button>
          )}
          {s.is_eligible && !revoking && (
            <button className="btn btn-outline btn-sm" onClick={() => setRevoking(true)}>
              Mark ineligible…
            </button>
          )}
          {revoking && (
            <>
              <input aria-label="Reason" placeholder="Reason" value={why} onChange={(e) => setWhy(e.target.value)} />
              <button className="btn btn-danger btn-sm" disabled={!why.trim()} onClick={() => set.mutate(false)}>
                Confirm
              </button>
            </>
          )}
        </div>
      )}
    </>
  )
}

function DocumentsPanel({ s, edit, remove, onOk, onError }: PanelProps & { edit: boolean; remove: boolean }) {
  const [type, setType] = useState(DOC_TYPES[0])
  const [ref, setRef] = useState('')
  const [issued, setIssued] = useState('')
  const [expiry, setExpiry] = useState('')
  const add = useMutation({
    mutationFn: () => supplierApi.addDocument(s.id, { document_type: type, reference_number: ref || null, issued_date: issued || null, expiry_date: expiry || null }),
    onSuccess: () => {
      setRef('')
      setIssued('')
      setExpiry('')
      onOk(`${type} recorded.`)
    },
    onError,
  })
  const drop = useMutation({ mutationFn: (id: number) => supplierApi.removeDocument(id), onSuccess: () => onOk('Document removed.'), onError })
  return (
    <Section title="Documents on file">
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Document</th>
              <th>Reference</th>
              <th>Issued</th>
              <th>Expires</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {s.documents?.map((d) => (
              <tr key={d.id}>
                <td>{d.document_type}</td>
                <td className="mono sd-muted">{d.reference_number ?? '—'}</td>
                <td>{formatDate(d.issued_date)}</td>
                <td>
                  {d.expiry_date ? formatDate(d.expiry_date) : 'no expiry'} <span className={`badge ${DOC_CLASS[d.status]}`}>{d.status}</span>
                </td>
                <td className="sd-actions">
                  {remove && (
                    <button className="icon-btn delete" title={`Remove ${d.document_type}`} onClick={() => drop.mutate(d.id)}>
                      🗑
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {s.documents?.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  Nothing on file.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {edit && (
        <div className="toolbar sd-form">
          <select aria-label="Document type" value={type} onChange={(e) => setType(e.target.value)}>
            {DOC_TYPES.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
          <input aria-label="Reference number" placeholder="Reference no." value={ref} onChange={(e) => setRef(e.target.value)} />
          <input aria-label="Issued on" type="date" max={today()} value={issued} onChange={(e) => setIssued(e.target.value)} />
          <input aria-label="Expires on" type="date" min={issued} value={expiry} onChange={(e) => setExpiry(e.target.value)} />
          <button className="btn btn-outline btn-sm" disabled={add.isPending} onClick={() => add.mutate()}>
            + Record
          </button>
        </div>
      )}
    </Section>
  )
}

function PerformancePanel({ s }: { s: Supplier }) {
  const p = s.performance
  if (!p) return null
  const kpi = (label: string, value: string, warn?: boolean) => (
    <div className={'kpi' + (warn ? ' kpi-warn' : '')}>
      <span className="kpi-label">{label}</span>
      <span className="kpi-value">{value}</span>
    </div>
  )
  return (
    <Section title="Performance" note="Worked out from the supplier's awards, deliveries and ratings.">
      <div className="kpi-row">
        {kpi('Awards', `${p.rfqs_won + p.biddings_won}`)}
        {kpi('Purchase orders', `${p.purchase_orders} · ${formatPeso(p.purchase_orders_value)}`)}
        {kpi('On-time deliveries', p.on_time_rate == null ? '—' : `${p.on_time_rate}% of ${p.deliveries}`, p.on_time_rate != null && p.on_time_rate < 80)}
        {kpi('Units rejected', p.rejection_rate == null ? '—' : `${p.rejection_rate}%`, (p.rejection_rate ?? 0) > 5)}
        {kpi('Rating', p.avg_overall == null ? '—' : `${p.avg_overall} / 5`, p.satisfactory === false)}
        {p.liquidated_damages > 0 && kpi('Liquidated damages', formatPeso(p.liquidated_damages), true)}
      </div>
    </Section>
  )
}

function RatingsPanel({ s, rate, onOk, onError }: PanelProps & { rate: boolean }) {
  const [r, setR] = useState({ delivery: '4', quality: '4', timeliness: '', compliance: '', after_sales: '', reference: '', remarks: '' })
  const add = useMutation({
    mutationFn: () =>
      supplierApi.evaluate(s.id, {
        delivery_rating: Number(r.delivery),
        quality_rating: Number(r.quality),
        timeliness_rating: r.timeliness ? Number(r.timeliness) : null,
        compliance_rating: r.compliance ? Number(r.compliance) : null,
        after_sales_rating: r.after_sales ? Number(r.after_sales) : null,
        reference: r.reference || null,
        remarks: r.remarks || null,
      }),
    onSuccess: () => onOk('Rating recorded.'),
    onError,
  })
  const drop = useMutation({ mutationFn: (id: number) => supplierApi.removeEvaluation(id), onSuccess: () => onOk('Rating withdrawn.'), onError })
  const sel = (key: 'delivery' | 'quality' | 'timeliness' | 'compliance' | 'after_sales', label: string, optional?: boolean) => (
    <div className="field" key={key}>
      <label htmlFor={`rt-${key}`}>{label}</label>
      <select id={`rt-${key}`} value={r[key]} onChange={(e) => setR({ ...r, [key]: e.target.value })}>
        {optional && <option value="">—</option>}
        {[5, 4, 3, 2, 1].map((n) => (
          <option key={n} value={n}>
            {n}
          </option>
        ))}
      </select>
    </div>
  )
  return (
    <Section title="Ratings">
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Rating</th>
              <th>Date</th>
              <th>PO / contract</th>
              <th>Rated by</th>
              <th>Remarks</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {s.evaluations?.map((e) => (
              <tr key={e.id}>
                <td>
                  <strong>{e.overall_rating}</strong> / 5
                </td>
                <td>{formatDate(e.evaluation_date)}</td>
                <td className="mono sd-muted">{e.reference ?? '—'}</td>
                <td>{e.evaluator?.name ?? '—'}</td>
                <td className="sd-muted">{e.remarks ?? '—'}</td>
                <td className="sd-actions">
                  {e.can_delete && (
                    <button className="link-btn small" onClick={() => drop.mutate(e.id)}>
                      Withdraw
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {s.evaluations?.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  Not rated yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {rate && (
        <div className="sd-panel sd-form">
          <div className="form-grid four">
            {sel('delivery', 'Delivery *')}
            {sel('quality', 'Quality *')}
            {sel('timeliness', 'Timeliness', true)}
            {sel('compliance', 'Compliance', true)}
            {sel('after_sales', 'After-sales', true)}
            <div className="field">
              <label htmlFor="rt-ref">PO / contract</label>
              <input id="rt-ref" value={r.reference} onChange={(e) => setR({ ...r, reference: e.target.value })} />
            </div>
            <div className="field span-2">
              <label htmlFor="rt-rem">Remarks</label>
              <input id="rt-rem" value={r.remarks} onChange={(e) => setR({ ...r, remarks: e.target.value })} />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-primary btn-sm" disabled={add.isPending} onClick={() => add.mutate()}>
              Record rating
            </button>
          </div>
        </div>
      )}
    </Section>
  )
}

function SanctionsPanel({ s, edit, onOk, onError }: PanelProps & { edit: boolean }) {
  const [adding, setAdding] = useState(false)
  const [type, setType] = useState('Suspension')
  const [grounds, setGrounds] = useState('')
  const [ref, setRef] = useState('')
  const [end, setEnd] = useState('')
  const [lifting, setLifting] = useState<number | null>(null)
  const [why, setWhy] = useState('')
  const add = useMutation({
    mutationFn: () => supplierApi.sanction(s.id, { sanction_type: type, grounds, reference_number: ref || null, start_date: today(), end_date: end || null }),
    onSuccess: () => {
      setAdding(false)
      setGrounds('')
      onOk(`${type} recorded; ${s.name} is no longer eligible.`)
    },
    onError,
  })
  const lift = useMutation({ mutationFn: () => supplierApi.lift(lifting!, why), onSuccess: () => { setLifting(null); onOk('Sanction lifted; verify eligibility again.') }, onError })
  return (
    <Section
      title="Blacklisting & suspension"
      action={
        edit &&
        !adding && (
          <button className="btn btn-outline btn-sm" onClick={() => setAdding(true)}>
            + Record sanction
          </button>
        )
      }
    >
      {adding && (
        <div className="reject-box sd-form" style={{ marginBottom: '0.75rem' }}>
          <div className="form-grid three">
            <div className="field">
              <label htmlFor="sn-type">Sanction</label>
              <select id="sn-type" value={type} onChange={(e) => setType(e.target.value)}>
                <option>Suspension</option>
                <option>Blacklist</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="sn-ref">Order / reference</label>
              <input id="sn-ref" value={ref} onChange={(e) => setRef(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="sn-end">Until{type === 'Suspension' ? ' *' : ''}</label>
              <input id="sn-end" type="date" min={today()} value={end} onChange={(e) => setEnd(e.target.value)} />
            </div>
            <div className="field span-3">
              <label htmlFor="sn-grounds">Grounds *</label>
              <input id="sn-grounds" value={grounds} onChange={(e) => setGrounds(e.target.value)} />
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setAdding(false)}>
              Cancel
            </button>
            <button className="btn btn-danger" disabled={!grounds.trim() || add.isPending} onClick={() => add.mutate()}>
              Record {type.toLowerCase()}
            </button>
          </div>
        </div>
      )}
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Sanction</th>
              <th>Period</th>
              <th>Reference</th>
              <th>Grounds</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {s.sanctions?.map((x) => (
              <tr key={x.id}>
                <td>{x.sanction_type}</td>
                <td>
                  {formatDate(x.start_date)} – {x.end_date ? formatDate(x.end_date) : 'until lifted'}
                </td>
                <td className="mono sd-muted">{x.reference_number ?? '—'}</td>
                <td>
                  {x.grounds}
                  {x.status === 'lifted' && (
                    <div className="sd-note">
                      Lifted {formatDate(x.lifted_at)}: {x.lifted_reason}
                    </div>
                  )}
                </td>
                <td>{x.in_force ? <span className="badge st-bad">In force</span> : x.status === 'lifted' ? <span className="badge badge-muted">Lifted</span> : <span className="badge badge-muted">Ended</span>}</td>
                <td className="sd-actions">
                  {edit && x.status === 'active' && (
                    <button className="link-btn small" onClick={() => { setWhy(''); setLifting(x.id) }}>
                      Lift…
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {s.sanctions?.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  None.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {lifting && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="sn-why">Why is it lifted? *</label>
            <input id="sn-why" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setLifting(null)}>
              Cancel
            </button>
            <button className="btn btn-primary" disabled={!why.trim() || lift.isPending} onClick={() => lift.mutate()}>
              Lift
            </button>
          </div>
        </div>
      )}
    </Section>
  )
}

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { paymentsApi, type PayableType, type PaymentMethod, type PaymentSchedule, type Tranche } from '../../api/payments'
import { ExcelButton } from '../../components/ExcelButton'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso, today } from '../../lib/format'

interface Props {
  type: PayableType
  id: number
  /** The contract price or PO total, shown until the schedule loads. */
  amount: number
}

const TRANCHE_STATUS: Record<string, [string, string]> = {
  pending: ['Pending', 'st-pending'],
  for_payment: ['For payment', 'st-partial'],
  paid: ['Paid', 'st-ok'],
}

const METHOD_LABEL: Record<PaymentMethod, string> = { check: 'Check', ada: 'ADA', lddap_ada: 'LDDAP-ADA' }

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)
const num = (v: string) => (v.trim() === '' ? 0 : Number(v))
const round2 = (v: number) => Math.round(v * 100) / 100

type Mode = { kind: 'add' } | { kind: 'edit'; t: Tranche } | { kind: 'approve'; t: Tranche } | { kind: 'pay'; t: Tranche } | null

/** Payment schedule of a contract or purchase order (TOR B 7.4), for its detail window. */
export function PaymentsPanel({ type, id, amount }: Props) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const key = ['payment-schedule', type, id]
  const q = useQuery({ queryKey: key, queryFn: () => paymentsApi.schedule(type, id) })
  const [mode, setMode] = useState<Mode>(null)
  const done = (s: PaymentSchedule, message: string) => {
    queryClient.setQueryData(key, s)
    void queryClient.invalidateQueries({ queryKey: ['payments'] })
    setMode(null)
    toast(message, 'success')
  }
  const fail = (e: unknown, fallback: string) => toast(msg(e, fallback), 'error')
  const remove = useMutation({
    mutationFn: (t: Tranche) => paymentsApi.removeTranche(t.id),
    onSuccess: (s) => done(s, 'Tranche removed.'),
    onError: (e) => fail(e, 'Could not remove the tranche.'),
  })
  const s = q.data
  return (
    <>
      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>
          Payment schedule {s && s.overdue_count > 0 && <span className="badge st-bad">{s.overdue_count} overdue</span>}
        </h4>
        {s?.can_schedule && mode === null && s.unscheduled > 0 && (
          <button className="btn btn-outline btn-sm" onClick={() => setMode({ kind: 'add' })}>
            + Add tranche
          </button>
        )}
      </div>
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {s && (
        <div className="kpi-row">
          <Kpi label={type === 'contract' ? 'Contract price' : 'PO amount'} value={s.base_amount ?? amount} />
          <Kpi label="Scheduled" value={s.scheduled_total} />
          <Kpi label="Paid (gross)" value={s.paid_total} />
          <Kpi label="Outstanding" value={s.outstanding} warn={s.overdue_count > 0} />
          {s.ld_assessed > 0 && <Kpi label="LD to deduct" value={s.ld_available} warn={s.ld_available > 0} />}
        </div>
      )}
      {s?.linked_notice && <div className="alert alert-info">{s.linked_notice}</div>}
      {s && s.ld_available > 0 && (
        <p className="muted small">
          Liquidated damages of {formatPeso(s.ld_assessed)} are assessed; {formatPeso(s.ld_available)} has not been deducted from a payment yet.
        </p>
      )}
      {(mode?.kind === 'add' || mode?.kind === 'edit') && s && (
        <TrancheForm s={s} t={mode.kind === 'edit' ? mode.t : null} onCancel={() => setMode(null)} onSaved={done} onError={fail} />
      )}
      {mode?.kind === 'approve' && <ApproveForm t={mode.t} onCancel={() => setMode(null)} onSaved={done} onError={fail} />}
      {mode?.kind === 'pay' && s && <PayForm s={s} t={mode.t} onCancel={() => setMode(null)} onSaved={done} onError={fail} />}
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>#</th>
              <th>Tranche / deliverable</th>
              <th className="num">Amount</th>
              <th>Due</th>
              <th>Status</th>
              <th>Payment</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {s?.tranches.map((t) => (
              <tr key={t.id}>
                <td>{t.sequence_no}</td>
                <td>
                  {t.description}
                  {t.percentage != null && <span className="muted small"> · {t.percentage}%</span>}
                  {t.deliverable && <div className="muted small">{t.deliverable}</div>}
                </td>
                <td className="num">{formatPeso(t.amount)}</td>
                <td>
                  {formatDate(t.due_date)} {t.overdue && <span className="badge st-bad">{t.days_overdue}d overdue</span>}
                </td>
                <td>
                  <span className={`badge ${TRANCHE_STATUS[t.status]?.[1] ?? ''}`}>{TRANCHE_STATUS[t.status]?.[0] ?? t.status}</span>
                  {(t.ors_number || t.dv_number) && (
                    <div className="muted small mono">
                      {t.ors_number} {t.dv_number && `· ${t.dv_number}`}
                    </div>
                  )}
                </td>
                <td className="small">
                  {t.status === 'paid' ? (
                    <>
                      {formatPeso(t.amount_paid)} net · {formatDate(t.paid_date)}
                      <div className="muted">
                        {t.payment_method && METHOD_LABEL[t.payment_method]} {t.payment_reference}
                        {t.retention_amount > 0 && ` · retention ${formatPeso(t.retention_amount)}`}
                        {t.ld_deducted > 0 && ` · LD ${formatPeso(t.ld_deducted)}`}
                        {t.withholding_tax > 0 && ` · tax ${formatPeso(t.withholding_tax)}`}
                      </div>
                    </>
                  ) : t.approved_by ? (
                    <span className="muted">approved by {t.approved_by.name}</span>
                  ) : (
                    '—'
                  )}
                </td>
                <td>
                  {mode === null && (
                    <div className="workflow-actions">
                      {t.can_approve && (
                        <button className="btn btn-outline btn-sm" onClick={() => setMode({ kind: 'approve', t })}>
                          Approve
                        </button>
                      )}
                      {t.can_pay && (
                        <button className="btn btn-primary btn-sm" onClick={() => setMode({ kind: 'pay', t })}>
                          Pay
                        </button>
                      )}
                      {t.can_edit && (
                        <>
                          <button className="btn btn-outline btn-sm" onClick={() => setMode({ kind: 'edit', t })}>
                            Edit
                          </button>
                          <button className="btn btn-outline btn-sm" disabled={remove.isPending} onClick={() => remove.mutate(t)}>
                            Remove
                          </button>
                        </>
                      )}
                      <ExcelButton path={paymentsApi.orsPath(t.id)} name={`ORS_${t.ors_number ?? t.payable_reference + '-T' + t.sequence_no}.xlsx`} label="ORS" />
                      <ExcelButton path={paymentsApi.dvPath(t.id)} name={`DV_${t.dv_number ?? t.payable_reference + '-T' + t.sequence_no}.xlsx`} label="DV" />
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {s && s.tranches.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No payment schedule yet.
                </td>
              </tr>
            )}
            {q.isPending && (
              <tr>
                <td colSpan={7} className="empty">
                  Loading…
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {s && s.tranches.some((t) => t.status !== 'paid') && (
        <p className="muted small">A tranche is approved for payment by someone other than whoever scheduled it, and released by someone other than its approver.</p>
      )}
    </>
  )
}

function Kpi({ label, value, warn }: { label: string; value: number; warn?: boolean }) {
  return (
    <div className={`kpi${warn ? ' kpi-warn' : ''}`}>
      <span className="kpi-value" style={{ fontSize: '1.15rem' }}>
        {formatPeso(value)}
      </span>
      <span className="kpi-label">{label}</span>
    </div>
  )
}

interface FormProps {
  onCancel: () => void
  onSaved: (s: PaymentSchedule, message: string) => void
  onError: (e: unknown, fallback: string) => void
}

function TrancheForm({ s, t, onCancel, onSaved, onError }: FormProps & { s: PaymentSchedule; t: Tranche | null }) {
  const [description, setDescription] = useState(t?.description ?? '')
  const [byPercent, setByPercent] = useState(t ? t.percentage != null : true)
  const [value, setValue] = useState(t ? String(t.percentage ?? t.amount) : '')
  const [due, setDue] = useState(t?.due_date ?? '')
  const [deliverable, setDeliverable] = useState(t?.deliverable ?? '')
  const [rc, setRc] = useState(t?.responsibility_center ?? '')
  const [mfo, setMfo] = useState(t?.mfo_pap ?? '')
  const [uacs, setUacs] = useState(t?.uacs_code ?? '')
  const room = s.unscheduled + (t?.amount ?? 0)
  const preview = byPercent ? round2((s.base_amount * num(value)) / 100) : num(value)
  const save = useMutation({
    mutationFn: () => {
      const input = {
        description,
        percentage: byPercent ? num(value) : null,
        amount: byPercent ? null : num(value),
        due_date: due || null,
        deliverable: deliverable || null,
        responsibility_center: rc || null,
        mfo_pap: mfo || null,
        uacs_code: uacs || null,
      }
      return t ? paymentsApi.updateTranche(t.id, input) : paymentsApi.addTranche({ ...input, type: s.type, id: s.id })
    },
    onSuccess: (r) => onSaved(r, t ? 'Tranche updated.' : 'Tranche scheduled.'),
    onError: (e) => onError(e, 'Could not save the tranche.'),
  })
  return (
    <div className="reject-box">
      <div className="form-grid three">
        <div className="field full">
          <label htmlFor="pt-desc">Tranche *</label>
          <input id="pt-desc" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. 30% upon delivery of the first lot" autoFocus />
        </div>
        <div className="field">
          <label htmlFor="pt-by">Expressed as</label>
          <select id="pt-by" value={byPercent ? 'p' : 'a'} onChange={(e) => setByPercent(e.target.value === 'p')}>
            <option value="p">Percentage of {formatPeso(s.base_amount)}</option>
            <option value="a">Amount (₱)</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="pt-val">{byPercent ? 'Percentage *' : 'Amount (₱) *'}</label>
          <input id="pt-val" type="number" min={0} step={byPercent ? '0.0001' : '0.01'} value={value} onChange={(e) => setValue(e.target.value)} />
          <span className={`hint${preview > room ? ' short' : ''}`}>
            {formatPeso(preview)} · {formatPeso(room)} left to schedule
          </span>
        </div>
        <div className="field">
          <label htmlFor="pt-due">Due date</label>
          <input id="pt-due" type="date" value={due} onChange={(e) => setDue(e.target.value)} />
        </div>
        <div className="field full">
          <label htmlFor="pt-del">Deliverable / condition</label>
          <input id="pt-del" value={deliverable} onChange={(e) => setDeliverable(e.target.value)} placeholder="e.g. Upon acceptance (IAR) of all items" />
        </div>
        <div className="field">
          <label htmlFor="pt-rc">Responsibility center</label>
          <input id="pt-rc" value={rc} onChange={(e) => setRc(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pt-mfo">MFO/PAP</label>
          <input id="pt-mfo" value={mfo} onChange={(e) => setMfo(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pt-uacs">UACS object code</label>
          <input id="pt-uacs" value={uacs} onChange={(e) => setUacs(e.target.value)} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={!description.trim() || num(value) <= 0 || preview > room || save.isPending} onClick={() => save.mutate()}>
          {t ? 'Save' : 'Schedule'}
        </button>
      </div>
    </div>
  )
}

function ApproveForm({ t, onCancel, onSaved, onError }: FormProps & { t: Tranche }) {
  const [ors, setOrs] = useState('')
  const [dv, setDv] = useState('')
  const [rc, setRc] = useState(t.responsibility_center ?? '')
  const [mfo, setMfo] = useState(t.mfo_pap ?? '')
  const approve = useMutation({
    mutationFn: () => paymentsApi.forPayment(t.id, { ors_number: ors || null, dv_number: dv || null, responsibility_center: rc || null, mfo_pap: mfo || null }),
    onSuccess: (r) => onSaved(r, `Tranche ${t.sequence_no} approved for payment.`),
    onError: (e) => onError(e, 'Could not approve the tranche.'),
  })
  return (
    <div className="reject-box">
      <p>
        Approve tranche {t.sequence_no} ({t.description}, {formatPeso(t.amount)}) for payment. Leave the numbers blank to assign the next ORS and DV numbers.
      </p>
      <div className="form-grid three">
        <div className="field">
          <label htmlFor="pa-ors">ORS/BURS no.</label>
          <input id="pa-ors" value={ors} onChange={(e) => setOrs(e.target.value)} placeholder="auto" />
        </div>
        <div className="field">
          <label htmlFor="pa-dv">DV no.</label>
          <input id="pa-dv" value={dv} onChange={(e) => setDv(e.target.value)} placeholder="auto" />
        </div>
        <div className="field">
          <label htmlFor="pa-rc">Responsibility center</label>
          <input id="pa-rc" value={rc} onChange={(e) => setRc(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pa-mfo">MFO/PAP</label>
          <input id="pa-mfo" value={mfo} onChange={(e) => setMfo(e.target.value)} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={approve.isPending} onClick={() => approve.mutate()}>
          Approve for payment
        </button>
      </div>
    </div>
  )
}

function PayForm({ s, t, onCancel, onSaved, onError }: FormProps & { s: PaymentSchedule; t: Tranche }) {
  const [date, setDate] = useState(today())
  const [method, setMethod] = useState<PaymentMethod>('lddap_ada')
  const [ref, setRef] = useState('')
  const [retention, setRetention] = useState('')
  const [ld, setLd] = useState(s.ld_available > 0 ? String(Math.min(s.ld_available, t.amount)) : '')
  const [wtax, setWtax] = useState('')
  const net = round2(t.amount - num(retention) - num(ld) - num(wtax))
  const pay = useMutation({
    mutationFn: () =>
      paymentsApi.pay(t.id, {
        paid_date: date,
        payment_method: method,
        payment_reference: ref,
        retention_amount: num(retention),
        ld_deducted: num(ld),
        withholding_tax: num(wtax),
        amount_paid: net,
      }),
    onSuccess: (r) => onSaved(r, `Tranche ${t.sequence_no} paid: ${formatPeso(net)}.`),
    onError: (e) => onError(e, 'Could not record the payment.'),
  })
  return (
    <div className="reject-box">
      <p>
        Release tranche {t.sequence_no} ({t.description}): gross {formatPeso(t.amount)}.
      </p>
      <div className="form-grid three">
        <div className="field">
          <label htmlFor="pp-date">Date paid</label>
          <input id="pp-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pp-method">Paid by</label>
          <select id="pp-method" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            {Object.entries(METHOD_LABEL).map(([k, l]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="pp-ref">{METHOD_LABEL[method]} no. *</label>
          <input id="pp-ref" value={ref} onChange={(e) => setRef(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pp-ret">Retention (₱)</label>
          <input id="pp-ret" type="number" min={0} step="0.01" value={retention} onChange={(e) => setRetention(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pp-ld">Liquidated damages (₱)</label>
          <input id="pp-ld" type="number" min={0} step="0.01" value={ld} onChange={(e) => setLd(e.target.value)} />
          <span className="hint">{s.ld_available > 0 ? `${formatPeso(s.ld_available)} assessed and not yet deducted` : 'None assessed'}</span>
        </div>
        <div className="field">
          <label htmlFor="pp-tax">Withholding tax (₱)</label>
          <input id="pp-tax" type="number" min={0} step="0.01" value={wtax} onChange={(e) => setWtax(e.target.value)} />
        </div>
      </div>
      <p>
        Net paid: <strong className={net < 0 ? 'short' : ''}>{formatPeso(net)}</strong>
      </p>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={!ref.trim() || net < 0 || num(ld) > s.ld_available || pay.isPending} onClick={() => pay.mutate()}>
          Record payment
        </button>
      </div>
    </div>
  )
}

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { pettyCashApi, type PettyCashFund, type PettyCashReplenishment, type PettyCashVoucher } from '../../api/payments'
import { useAuth } from '../../auth/AuthContext'
import { ExcelButton } from '../../components/ExcelButton'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso, today } from '../../lib/format'
import { PersonSelect } from '../assets/pickers'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

const VOUCHER_STATUS: Record<string, [string, string]> = {
  pending: ['Awaiting approval', 'st-pending'],
  approved: ['Approved', 'st-ok'],
  cancelled: ['Cancelled', 'st-bad'],
}

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

/** Petty cash funds, their vouchers (COA Appendix 50) and replenishments. */
export function PettyCashPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const funds = useQuery({ queryKey: ['petty-cash-funds'], queryFn: pettyCashApi.funds })
  const [picked, setPicked] = useState<number | null>(null)
  const [fundForm, setFundForm] = useState<PettyCashFund | 'new' | null>(null)
  const fundId = picked ?? funds.data?.data[0]?.id ?? null
  const fund = useQuery({ queryKey: ['petty-cash-fund', fundId], queryFn: () => pettyCashApi.fund(fundId as number), enabled: fundId !== null })
  const changed = (f: PettyCashFund, message: string) => {
    queryClient.setQueryData(['petty-cash-fund', f.id], f)
    void queryClient.invalidateQueries({ queryKey: ['petty-cash-funds'] })
    toast(message, 'success')
  }
  const f = fund.data
  return (
    <div className="page page-wide">
      <h1 className="page-title">Petty Cash</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <select value={fundId ?? ''} onChange={(e) => setPicked(Number(e.target.value))} aria-label="Fund" disabled={!funds.data?.data.length}>
            {funds.data?.data.length === 0 && <option value="">No funds yet</option>}
            {funds.data?.data.map((x) => (
              <option key={x.id} value={x.id}>
                {x.fund_name} — {x.custodian?.name ?? 'no custodian'} {x.status === 'closed' ? '(closed)' : ''}
              </option>
            ))}
          </select>
        </div>
        <div className="workflow-actions">
          {f?.can_manage && (
            <button className="btn btn-secondary" onClick={() => setFundForm(f)}>
              Edit fund
            </button>
          )}
          {can('manage_payments') && (
            <button className="btn btn-primary" onClick={() => setFundForm('new')}>
              + New fund
            </button>
          )}
        </div>
      </div>
      {funds.isError && <div className="alert alert-error">{(funds.error as Error).message}</div>}
      {fund.isError && <div className="alert alert-error">{(fund.error as Error).message}</div>}
      {funds.data?.data.length === 0 && <p className="muted">No petty cash fund has been established.</p>}
      {f && <FundDetail f={f} onChanged={changed} onError={(e, m) => toast(msg(e, m), 'error')} />}
      {fundForm && (
        <FundFormModal
          fund={fundForm === 'new' ? null : fundForm}
          onClose={() => setFundForm(null)}
          onSaved={(x, m) => {
            setFundForm(null)
            setPicked(x.id)
            changed(x, m)
          }}
        />
      )}
    </div>
  )
}

interface DetailProps {
  f: PettyCashFund
  onChanged: (f: PettyCashFund, message: string) => void
  onError: (e: unknown, fallback: string) => void
}

function FundDetail({ f, onChanged, onError }: DetailProps) {
  const [recording, setRecording] = useState(false)
  const [cancelling, setCancelling] = useState<PettyCashVoucher | null>(null)
  const [replenishing, setReplenishing] = useState<PettyCashReplenishment | null>(null)
  const approve = useMutation({
    mutationFn: (v: PettyCashVoucher) => pettyCashApi.approveVoucher(v.id),
    onSuccess: (x, v) => onChanged(x, `${v.pcv_number} approved.`),
    onError: (e) => onError(e, 'Could not approve the voucher.'),
  })
  const request = useMutation({
    mutationFn: () => pettyCashApi.requestReplenishment(f.id, { report_date: today() }),
    onSuccess: (x) => onChanged(x, 'Replenishment requested.'),
    onError: (e) => onError(e, 'Could not request the replenishment.'),
  })
  return (
    <>
      <div className="kpi-row">
        <div className="kpi">
          <span className="kpi-value">{formatPeso(f.amount)}</span>
          <span className="kpi-label">Fund</span>
        </div>
        <div className={`kpi${f.balance < f.amount * 0.25 ? ' kpi-warn' : ''}`}>
          <span className="kpi-value">{formatPeso(f.balance)}</span>
          <span className="kpi-label">Cash on hand</span>
        </div>
        <div className="kpi">
          <span className="kpi-value">{formatPeso(f.pending)}</span>
          <span className="kpi-label">Awaiting approval</span>
        </div>
        <div className="kpi">
          <span className="kpi-value">{formatPeso(f.available)}</span>
          <span className="kpi-label">Available for new vouchers</span>
        </div>
        <div className="kpi">
          <span className="kpi-value">{formatPeso(f.disbursed)}</span>
          <span className="kpi-label">To be replenished ({formatPeso(f.in_replenishment)} requested)</span>
        </div>
      </div>
      <p className="muted small">
        Custodian: {f.custodian?.name ?? '—'} {f.fund_cluster && `· Fund cluster ${f.fund_cluster}`} · up to {formatPeso(f.max_per_voucher)} per voucher
        {f.status === 'closed' && <span className="badge st-bad">closed</span>}
      </p>

      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>Petty cash vouchers</h4>
        <div className="workflow-actions">
          {f.can_request_replenishment && (
            <button className="btn btn-outline btn-sm" disabled={request.isPending} onClick={() => request.mutate()}>
              Request replenishment ({formatPeso(f.unrequested)})
            </button>
          )}
          {f.can_record && !recording && (
            <button className="btn btn-primary btn-sm" onClick={() => setRecording(true)}>
              + Record voucher
            </button>
          )}
        </div>
      </div>
      {recording && (
        <VoucherForm
          f={f}
          onCancel={() => setRecording(false)}
          onSaved={(x) => {
            setRecording(false)
            onChanged(x, 'Voucher recorded; it needs someone else’s approval.')
          }}
          onError={onError}
        />
      )}
      {cancelling && (
        <ReasonBox
          label={`Why is ${cancelling.pcv_number} cancelled? *`}
          action="Cancel voucher"
          onBack={() => setCancelling(null)}
          run={(why) => pettyCashApi.cancelVoucher(cancelling.id, why)}
          onDone={(x) => {
            onChanged(x, `${cancelling.pcv_number} cancelled.`)
            setCancelling(null)
          }}
          onError={onError}
        />
      )}
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>PCV no.</th>
              <th>Date</th>
              <th>Payee</th>
              <th>Particulars</th>
              <th className="num">Amount</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {f.vouchers?.map((v) => (
              <tr key={v.id}>
                <td className="mono">{v.pcv_number}</td>
                <td>{formatDate(v.voucher_date)}</td>
                <td>{v.payee}</td>
                <td className="clip">
                  {v.particulars}
                  {v.responsibility_center && <div className="muted small">{v.responsibility_center}</div>}
                </td>
                <td className="num">{formatPeso(v.amount)}</td>
                <td>
                  <span className={`badge ${VOUCHER_STATUS[v.status]?.[1] ?? ''}`}>{VOUCHER_STATUS[v.status]?.[0] ?? v.status}</span>
                  <div className="muted small">
                    by {v.recorded_by?.name ?? '—'}
                    {v.approved_by && ` · approved by ${v.approved_by.name}`}
                    {v.replenishment && ` · ${v.replenishment.name}${v.replenished ? ' (replenished)' : ''}`}
                  </div>
                  {v.status === 'cancelled' && v.remarks && <div className="muted small">{v.remarks}</div>}
                </td>
                <td>
                  <div className="workflow-actions">
                    {v.can_approve && (
                      <button className="btn btn-outline btn-sm" disabled={approve.isPending} onClick={() => approve.mutate(v)}>
                        Approve
                      </button>
                    )}
                    {v.can_cancel && (
                      <button className="btn btn-outline btn-sm" onClick={() => setCancelling(v)}>
                        Cancel
                      </button>
                    )}
                    <ExcelButton path={pettyCashApi.pcvPath(v.id)} name={`PCV_${v.pcv_number}.xlsx`} label="PCV" />
                  </div>
                </td>
              </tr>
            ))}
            {f.vouchers?.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No vouchers yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="section-header" style={{ marginTop: '1.5rem' }}>
        <h4>Replenishments</h4>
      </div>
      {replenishing && (
        <ReasonBox
          label={`Check/ADA no. replenishing ${replenishing.report_number} (${formatPeso(replenishing.total)}) *`}
          action="Record replenishment"
          primary
          onBack={() => setReplenishing(null)}
          run={(ref) => pettyCashApi.replenish(replenishing.id, { reference: ref, replenished_date: today() })}
          onDone={(x) => {
            onChanged(x, `${replenishing.report_number} replenished.`)
            setReplenishing(null)
          }}
          onError={onError}
        />
      )}
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>Report no.</th>
              <th>Date</th>
              <th className="num">Vouchers</th>
              <th className="num">Amount</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {f.replenishments?.map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.report_number}</td>
                <td>{formatDate(r.report_date)}</td>
                <td className="num">{r.voucher_count}</td>
                <td className="num">{formatPeso(r.total)}</td>
                <td>
                  <span className={`badge ${r.status === 'replenished' ? 'st-ok' : 'st-pending'}`}>{r.status === 'replenished' ? 'Replenished' : 'Requested'}</span>
                  <div className="muted small">
                    by {r.requested_by?.name ?? '—'}
                    {r.status === 'replenished' && ` · ${r.reference} on ${formatDate(r.replenished_date)} by ${r.replenished_by?.name ?? '—'}`}
                  </div>
                </td>
                <td>
                  <div className="workflow-actions">
                    {r.can_replenish && (
                      <button className="btn btn-primary btn-sm" onClick={() => setReplenishing(r)}>
                        Replenish
                      </button>
                    )}
                    <ExcelButton path={pettyCashApi.reportPath(r.id)} name={`PettyCashReplenishment_${r.report_number}.xlsx`} label="Report" />
                  </div>
                </td>
              </tr>
            ))}
            {f.replenishments?.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No replenishments yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}

function VoucherForm({ f, onCancel, onSaved, onError }: { f: PettyCashFund; onCancel: () => void; onSaved: (f: PettyCashFund) => void; onError: DetailProps['onError'] }) {
  const [date, setDate] = useState(today())
  const [payee, setPayee] = useState('')
  const [particulars, setParticulars] = useState('')
  const [amount, setAmount] = useState('')
  const [rc, setRc] = useState('')
  const cap = Math.min(f.available, f.max_per_voucher)
  const save = useMutation({
    mutationFn: () => pettyCashApi.addVoucher(f.id, { voucher_date: date, payee, particulars, amount: Number(amount), responsibility_center: rc || null }),
    onSuccess: onSaved,
    onError: (e) => onError(e, 'Could not record the voucher.'),
  })
  return (
    <div className="reject-box">
      <div className="form-grid three">
        <div className="field">
          <label htmlFor="pv-date">Date</label>
          <input id="pv-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pv-payee">Payee *</label>
          <input id="pv-payee" value={payee} onChange={(e) => setPayee(e.target.value)} autoFocus />
        </div>
        <div className="field">
          <label htmlFor="pv-amt">Amount (₱) *</label>
          <input id="pv-amt" type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
          <span className={`hint${Number(amount) > cap ? ' short' : ''}`}>Up to {formatPeso(cap)}</span>
        </div>
        <div className="field full">
          <label htmlFor="pv-part">Particulars *</label>
          <input id="pv-part" value={particulars} onChange={(e) => setParticulars(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="pv-rc">Responsibility center</label>
          <input id="pv-rc" value={rc} onChange={(e) => setRc(e.target.value)} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button
          className="btn btn-primary"
          disabled={!payee.trim() || !particulars.trim() || !(Number(amount) > 0) || Number(amount) > cap || save.isPending}
          onClick={() => save.mutate()}
        >
          Record
        </button>
      </div>
    </div>
  )
}

interface ReasonProps {
  label: string
  action: string
  primary?: boolean
  onBack: () => void
  run: (text: string) => Promise<PettyCashFund>
  onDone: (f: PettyCashFund) => void
  onError: DetailProps['onError']
}

function ReasonBox({ label, action, primary, onBack, run, onDone, onError }: ReasonProps) {
  const [text, setText] = useState('')
  const go = useMutation({ mutationFn: () => run(text), onSuccess: onDone, onError: (e) => onError(e, 'Something went wrong.') })
  return (
    <div className="reject-box">
      <div className="field">
        <label htmlFor="pc-reason">{label}</label>
        <input id="pc-reason" value={text} onChange={(e) => setText(e.target.value)} autoFocus />
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onBack}>
          Back
        </button>
        <button className={`btn ${primary ? 'btn-primary' : 'btn-danger'}`} disabled={!text.trim() || go.isPending} onClick={() => go.mutate()}>
          {action}
        </button>
      </div>
    </div>
  )
}

function FundFormModal({ fund, onClose, onSaved }: { fund: PettyCashFund | null; onClose: () => void; onSaved: (f: PettyCashFund, message: string) => void }) {
  const [name, setName] = useState(fund?.fund_name ?? '')
  const [custodian, setCustodian] = useState(fund?.custodian ? String(fund.custodian.id) : '')
  const [amount, setAmount] = useState(fund ? String(fund.amount) : '')
  const [cluster, setCluster] = useState(fund?.fund_cluster ?? '')
  const [status, setStatus] = useState<'active' | 'closed'>(fund?.status ?? 'active')
  const [remarks, setRemarks] = useState(fund?.remarks ?? '')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      const input = { fund_name: name, custodian_id: Number(custodian), amount: Number(amount), fund_cluster: cluster || null, remarks: remarks || null }
      return fund ? pettyCashApi.updateFund(fund.id, { ...input, status }) : pettyCashApi.createFund(input)
    },
    onSuccess: (f) => onSaved(f, fund ? 'Fund updated.' : 'Petty cash fund established.'),
    onError: (e) => setError(msg(e, 'Could not save the fund.')),
  })
  return (
    <Modal title={fund ? `Edit ${fund.fund_name}` : 'New petty cash fund'} onClose={onClose} width={620}>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-grid">
        <div className="field full">
          <label htmlFor="pf-name">Fund name *</label>
          <input id="pf-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Petty Cash Fund — Administrative Division" />
        </div>
        <div className="field">
          <label htmlFor="pf-cust">Custodian *</label>
          <PersonSelect id="pf-cust" value={custodian} onChange={setCustodian} required />
        </div>
        <div className="field">
          <label htmlFor="pf-amt">Fund amount (₱) *</label>
          <input id="pf-amt" type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
          {fund && <span className="hint">At least {formatPeso(fund.disbursed + fund.pending)} is out on vouchers.</span>}
        </div>
        <div className="field">
          <label htmlFor="pf-fc">Fund cluster</label>
          <input id="pf-fc" value={cluster} onChange={(e) => setCluster(e.target.value)} />
        </div>
        {fund && (
          <div className="field">
            <label htmlFor="pf-st">Status</label>
            <select id="pf-st" value={status} onChange={(e) => setStatus(e.target.value as 'active' | 'closed')}>
              <option value="active">Active</option>
              <option value="closed">Closed</option>
            </select>
          </div>
        )}
        <div className="field full">
          <label htmlFor="pf-rem">Remarks</label>
          <input id="pf-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onClose}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={!name.trim() || !custodian || !(Number(amount) > 0) || save.isPending} onClick={() => save.mutate()}>
          Save
        </button>
      </div>
    </Modal>
  )
}

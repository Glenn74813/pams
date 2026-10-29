import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { planningReportsApi, type TransmittalFilter } from '../../api/planningReports'
import { formatDate, formatPeso, today } from '../../lib/format'
import { fiscalYears, thisYear } from '../procurement/labels'
import { ExportButton, PreviewTable, type Column } from './shared'

const MONTH_COLS: Column[] = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'].map((m) => ({ key: m.toLowerCase(), label: m }))

function Kpi({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="kpi">
      <span className="kpi-value">{value}</span>
      <span className="kpi-label">{label}</span>
    </div>
  )
}

function Failure({ error }: { error: unknown }) {
  if (!error) return null
  return <div className="alert alert-error">{error instanceof ApiError ? error.message : 'The report could not be loaded.'}</div>
}

function YearField({ id, value, onChange }: { id: string; value: number; onChange: (y: number) => void }) {
  return (
    <div className="field">
      <label htmlFor={id}>Fiscal Year</label>
      <select id={id} value={value} onChange={(e) => onChange(Number(e.target.value))}>
        {fiscalYears().map((y) => (
          <option key={y} value={y}>
            FY {y}
          </option>
        ))}
      </select>
    </div>
  )
}

function Signers({ items }: { items: [string, string | null | undefined][] }) {
  return (
    <p className="muted small">
      {items.map(([role, name], i) => (
        <span key={role}>
          {i > 0 && ' · '}
          {role}: <strong>{name || 'not assigned'}</strong>
        </span>
      ))}
    </p>
  )
}

const usePlanningOptions = (year?: number) =>
  useQuery({ queryKey: ['planning-report-options', year ?? 'all'], queryFn: () => planningReportsApi.options(year) })

/** PPMP in the GPPB-prescribed format — one PPMP, or all PPMPs of an end-user unit for the fiscal year. */
export function PpmpGppbReport({ initialYear }: { initialYear?: number }) {
  const [year, setYear] = useState(initialYear ?? thisYear())
  const [dept, setDept] = useState('')
  const [ppmpId, setPpmpId] = useState('')
  const [status, setStatus] = useState('')
  const opts = usePlanningOptions(year)
  const ppmps = (opts.data?.ppmps ?? []).filter((p) => !dept || p.department_id === Number(dept))
  const filter = ppmpId ? { ppmp_id: Number(ppmpId) } : { department_id: dept ? Number(dept) : undefined, fiscal_year: year, status: status || undefined }
  const enabled = Boolean(ppmpId || dept)
  const q = useQuery({ queryKey: ['planning-ppmp', filter], queryFn: () => planningReportsApi.ppmp(filter), enabled, placeholderData: keepPreviousData })
  const d = enabled ? q.data : undefined
  const fileName = ppmpId ? `PPMP_${ppmps.find((p) => p.id === Number(ppmpId))?.ppmp_number ?? ppmpId}.xlsx` : `PPMP_FY${year}.xlsx`
  return (
    <>
      <div className="toolbar">
        <YearField id="pg-year" value={year} onChange={(y) => { setYear(y); setPpmpId('') }} />
        <div className="field">
          <label htmlFor="pg-dept">End-User Unit</label>
          <select id="pg-dept" value={dept} onChange={(e) => { setDept(e.target.value); setPpmpId('') }}>
            <option value="">Choose a unit…</option>
            {opts.data?.departments.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field" style={{ minWidth: 260 }}>
          <label htmlFor="pg-ppmp">PPMP</label>
          <select id="pg-ppmp" value={ppmpId} onChange={(e) => setPpmpId(e.target.value)}>
            <option value="">{dept ? 'All PPMPs of the unit' : 'Choose a PPMP…'}</option>
            {ppmps.map((p) => (
              <option key={p.id} value={p.id}>
                {p.ppmp_number} — {p.title} ({p.status.replace(/_/g, ' ')})
              </option>
            ))}
          </select>
        </div>
        {!ppmpId && (
          <div className="field">
            <label htmlFor="pg-status">Status</label>
            <select id="pg-status" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">Any status</option>
              <option value="approved">Approved only</option>
            </select>
          </div>
        )}
        <ExportButton path={planningReportsApi.exportPath('ppmp')} params={filter} name={fileName} label="Export PPMP (GPPB format)" disabled={!d} />
      </div>
      {!enabled && <p className="muted">Choose an end-user unit or a PPMP.</p>}
      <Failure error={enabled ? q.error : null} />
      {d && (
        <>
          <div className="kpi-row">
            <Kpi label="End-user unit" value={d.department} />
            <Kpi label="PPMPs" value={d.ppmps.length} />
            <Kpi label="Lines" value={d.rows.length} />
            <Kpi label="Total estimated budget" value={formatPeso(d.total_estimated_budget)} />
          </div>
          <PreviewTable
            columns={[
              { key: 'ppmp_number', label: 'PPMP' },
              { key: 'code', label: 'Code' },
              { key: 'general_description', label: 'General Description' },
              { key: 'quantity_size', label: 'Qty / Size' },
              { key: 'estimated_budget', label: 'Estimated Budget', type: 'money' },
              { key: 'procurement_mode', label: 'Mode of Procurement' },
              ...MONTH_COLS,
              { key: 'source_of_funds', label: 'Source of Funds' },
              { key: 'remarks', label: 'Remarks' },
            ]}
            rows={d.rows}
            empty="No PPMP lines."
          />
          <Signers items={[['Prepared by', d.prepared_by], ['Approved by', d.approved_by]]} />
          <p className="muted small">Each line is scheduled to one quarter; the three months of that quarter are marked X.</p>
        </>
      )}
    </>
  )
}

/** Consolidated APP in the GPPB-prescribed format for a fiscal year. */
export function AppGppbReport({ initialYear }: { initialYear?: number }) {
  const [year, setYear] = useState(initialYear ?? thisYear())
  const q = useQuery({ queryKey: ['planning-app', year], queryFn: () => planningReportsApi.app(year), placeholderData: keepPreviousData })
  const d = q.data
  return (
    <>
      <div className="toolbar">
        <YearField id="ag-year" value={year} onChange={setYear} />
        <ExportButton path={planningReportsApi.exportPath('app')} params={{ fiscal_year: year }} name={`APP_GPPB_FY${year}.xlsx`} label="Export APP (GPPB format)" disabled={!d} />
      </div>
      <Failure error={q.error} />
      {q.isLoading && <div className="page-loading">Loading…</div>}
      {d && (
        <>
          {!d.app && <p className="muted">No APP has been consolidated for FY {d.fiscal_year}.</p>}
          {d.app && (
            <p className="muted small">
              {d.app.app_number} — {d.app.title} ({d.app.status}
              {d.app.approved_at ? `, approved ${formatDate(d.app.approved_at)}` : ''})
            </p>
          )}
          <div className="kpi-row">
            <Kpi label="Lines" value={d.rows.length} />
            <Kpi label="Total" value={formatPeso(d.total_budget)} />
            <Kpi label="MOOE" value={formatPeso(d.total_mooe)} />
            <Kpi label="Capital Outlay" value={formatPeso(d.total_co)} />
          </div>
          <PreviewTable
            columns={[
              { key: 'code', label: 'Code (PAP)' },
              { key: 'procurement_project', label: 'Procurement Project' },
              { key: 'pmo_end_user', label: 'PMO / End-User' },
              { key: 'procurement_mode', label: 'Mode' },
              { key: 'advertisement_posting', label: 'Posting of IB/REI' },
              { key: 'submission_opening', label: 'Submission / Opening' },
              { key: 'notice_of_award', label: 'Notice of Award' },
              { key: 'contract_signing', label: 'Contract Signing' },
              { key: 'schedule_basis', label: 'Dates' },
              { key: 'source_of_funds', label: 'Source of Funds' },
              { key: 'estimated_budget_total', label: 'Total', type: 'money' },
              { key: 'estimated_budget_mooe', label: 'MOOE', type: 'money' },
              { key: 'estimated_budget_co', label: 'CO', type: 'money' },
              { key: 'remarks', label: 'Remarks' },
            ]}
            rows={d.rows}
            empty={d.app ? 'The APP has no lines.' : undefined}
          />
          <Signers items={[['Prepared by', d.prepared_by], ['Recommended by', d.recommended_by], ['Approved by', d.approved_by]]} />
          <p className="muted small">
            Activity months are actual where a bidding or RFQ was held for the line, otherwise planned from its quarter. MOOE/CO follows the UACS code; without one,
            infrastructure and goods at or above {formatPeso(d.capitalization_threshold)} per unit are Capital Outlay.
          </p>
        </>
      )}
    </>
  )
}

/** APP for Common-Use Supplies and Equipment, built from the Goods lines of the APP or approved PPMPs. */
export function AppCseReport({ initialYear }: { initialYear?: number }) {
  const [year, setYear] = useState(initialYear ?? thisYear())
  const [source, setSource] = useState<'app' | 'ppmp'>('app')
  const [dept, setDept] = useState('')
  const opts = usePlanningOptions(year)
  const filter = { fiscal_year: year, source, department_id: dept ? Number(dept) : undefined }
  const q = useQuery({ queryKey: ['planning-app-cse', filter], queryFn: () => planningReportsApi.appCse(filter), placeholderData: keepPreviousData })
  const d = q.data
  return (
    <>
      <div className="toolbar">
        <YearField id="cse-year" value={year} onChange={setYear} />
        <div className="field">
          <label htmlFor="cse-src">Built from</label>
          <select id="cse-src" value={source} onChange={(e) => setSource(e.target.value as 'app' | 'ppmp')}>
            <option value="app">APP lines</option>
            <option value="ppmp">Approved PPMP lines</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="cse-dept">End-User Unit</label>
          <select id="cse-dept" value={dept} onChange={(e) => setDept(e.target.value)}>
            <option value="">All units</option>
            {opts.data?.departments.map((x) => (
              <option key={x.id} value={x.id}>
                {x.name}
              </option>
            ))}
          </select>
        </div>
        <ExportButton path={planningReportsApi.exportPath('app-cse')} params={filter} name={`APP-CSE_FY${year}.xlsx`} label="Export APP-CSE" disabled={!d} />
      </div>
      <Failure error={q.error} />
      {q.isLoading && <div className="page-loading">Loading…</div>}
      {d && (
        <>
          <div className="kpi-row">
            <Kpi label="Items" value={d.rows.length} />
            <Kpi label="Total amount" value={formatPeso(d.total_amount)} />
            <Kpi label="Source" value={d.source === 'app' ? d.app_number ?? 'No APP' : 'Approved PPMPs'} />
          </div>
          <PreviewTable
            columns={[
              { key: 'no', label: 'No.', type: 'num' },
              { key: 'code', label: 'Code' },
              { key: 'item', label: 'Item & Specifications' },
              { key: 'unit', label: 'Unit' },
              { key: 'q1', label: 'Q1', type: 'num' },
              { key: 'q2', label: 'Q2', type: 'num' },
              { key: 'q3', label: 'Q3', type: 'num' },
              { key: 'q4', label: 'Q4', type: 'num' },
              { key: 'total_quantity', label: 'Total Qty', type: 'num' },
              { key: 'price_catalogue', label: 'Price Catalogue', type: 'money' },
              { key: 'total_amount', label: 'Total Amount', type: 'money' },
              { key: 'end_users', label: 'End-Users' },
              { key: 'remarks', label: 'Remarks' },
            ]}
            rows={d.rows}
            empty="No Goods lines for this fiscal year."
          />
          <Signers items={[['Prepared by', d.prepared_by], ['Funds certified by', d.certified_by], ['Approved by', d.approved_by]]} />
          <p className="muted small">{d.mapping}</p>
        </>
      )}
    </>
  )
}

/** GPPB Agency Procurement Report — projects, ABC and contract amounts per mode of procurement. */
export function AgencyProcurementReport({ initialYear }: { initialYear?: number }) {
  const [year, setYear] = useState(initialYear ?? thisYear())
  const q = useQuery({ queryKey: ['planning-apr', year], queryFn: () => planningReportsApi.apr(year), placeholderData: keepPreviousData })
  const d = q.data
  return (
    <>
      <div className="toolbar">
        <YearField id="apr-year" value={year} onChange={setYear} />
        <ExportButton path={planningReportsApi.exportPath('apr')} params={{ fiscal_year: year }} name={`APR_FY${year}.xlsx`} label="Export APR" disabled={!d} />
      </div>
      <Failure error={q.error} />
      {q.isLoading && <div className="page-loading">Loading…</div>}
      {d && (
        <>
          <div className="kpi-row">
            <Kpi label="Projects" value={d.total.projects} />
            <Kpi label="Competitive bidding" value={`${d.competitive.projects} · ${formatPeso(d.competitive.contract_amount)}`} />
            <Kpi label="Alternative modes" value={`${d.alternative.projects} · ${formatPeso(d.alternative.contract_amount)}`} />
            <Kpi label="Total contract amount" value={formatPeso(d.total.contract_amount)} />
            <Kpi label="Savings" value={formatPeso(d.total.savings)} />
          </div>
          <PreviewTable
            columns={[
              { key: 'mode', label: 'Mode of Procurement' },
              { key: 'planned_projects', label: 'Per APP', type: 'num' },
              { key: 'planned_budget', label: 'APP Budget', type: 'money' },
              { key: 'projects', label: 'Projects', type: 'num' },
              { key: 'awarded', label: 'Awarded', type: 'num' },
              { key: 'unsuccessful', label: 'Failed / Cancelled', type: 'num' },
              { key: 'ongoing', label: 'Ongoing', type: 'num' },
              { key: 'total_abc', label: 'ABC (all)', type: 'money' },
              { key: 'abc_of_awarded', label: 'ABC (awarded)', type: 'money' },
              { key: 'contract_amount', label: 'Contract Amount', type: 'money' },
              { key: 'savings', label: 'Savings', type: 'money' },
              { key: 'share', label: '% of Total' },
            ]}
            rows={d.rows.map((r) => ({
              ...r,
              mode: r.section === 'alternative' ? `↳ ${r.mode}` : r.section === 'competitive' ? r.mode : r.mode.toUpperCase(),
              share: `${Number(r.share_of_contract_amount ?? 0).toFixed(2)}%`,
            }))}
          />
          <Signers items={[['Prepared by', d.prepared_by], ['Approved by', d.approved_by]]} />
          <p className="muted small">
            Competitive bidding counts the biddings; alternative modes count the RFQs by procurement method, with the winning quotation as the contract amount. A
            project falls in its purchase request&apos;s fiscal year.
          </p>
        </>
      )}
    </>
  )
}

const monthStart = () => today().slice(0, 8) + '01'

/** Transmittal of purchase orders and contracts issued in a period to the COA auditor. */
export function CoaTransmittalReport() {
  const [from, setFrom] = useState(monthStart())
  const [to, setTo] = useState(today())
  const [type, setType] = useState('all')
  const [number, setNumber] = useState('')
  const [auditor, setAuditor] = useState('')
  const filter: TransmittalFilter = { date_from: from, date_to: to, document_type: type, transmittal_number: number.trim() || undefined, auditor: auditor.trim() || undefined }
  const enabled = Boolean(from && to)
  const q = useQuery({ queryKey: ['planning-coa-transmittal', filter], queryFn: () => planningReportsApi.coaTransmittal(filter), enabled, placeholderData: keepPreviousData })
  const d = enabled ? q.data : undefined
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="ct-from">From</label>
          <input id="ct-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="ct-to">To</label>
          <input id="ct-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="ct-type">Documents</label>
          <select id="ct-type" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="all">POs and contracts</option>
            <option value="purchase_order">Purchase orders</option>
            <option value="contract">Contracts</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="ct-no">Transmittal No.</label>
          <input id="ct-no" value={number} maxLength={60} onChange={(e) => setNumber(e.target.value)} placeholder="optional" />
        </div>
        <div className="field">
          <label htmlFor="ct-aud">COA Auditor</label>
          <input id="ct-aud" value={auditor} maxLength={120} onChange={(e) => setAuditor(e.target.value)} placeholder={d?.auditor ?? 'from Settings'} />
        </div>
        <ExportButton path={planningReportsApi.exportPath('coa-transmittal')} params={filter} name={`COA_Transmittal_${from}_to_${to}.xlsx`} label="Export Transmittal" disabled={!d} />
      </div>
      <Failure error={enabled ? q.error : null} />
      {d && (
        <>
          <div className="kpi-row">
            <Kpi label="Purchase orders" value={d.purchase_orders} />
            <Kpi label="Contracts" value={d.contracts} />
            <Kpi label="Total amount" value={formatPeso(d.total_amount)} />
          </div>
          <PreviewTable
            columns={[
              { key: 'no', label: 'No.', type: 'num' },
              { key: 'document_type', label: 'Document' },
              { key: 'document_number', label: 'Number' },
              { key: 'date', label: 'Date' },
              { key: 'supplier', label: 'Supplier / Contractor' },
              { key: 'particulars', label: 'Particulars' },
              { key: 'reference', label: 'Reference' },
              { key: 'amount', label: 'Amount', type: 'money' },
            ]}
            rows={d.rows.map((r) => ({ ...r, date: formatDate(r.document_date) }))}
            empty="No approved purchase orders or signed contracts in the period."
          />
          <Signers items={[['Transmitted by', d.transmitted_by], ['Noted by', d.noted_by], ['COA auditor', d.auditor]]} />
          <p className="muted small">Purchase orders by order date (sent to the supplier or later); contracts by signing date. The Excel file carries the “Received by COA” block.</p>
        </>
      )}
    </>
  )
}

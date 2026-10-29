import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { procurementReportsApi, type PreviewRow } from '../../api/endpoints'
import { useToast } from '../../components/toast-context'
import { formatPeso } from '../../lib/format'
import { fiscalYears, thisYear } from '../procurement/labels'
import { AgencyProcurementReport, AppCseReport, AppGppbReport, CoaTransmittalReport, PpmpGppbReport } from './PlanningForms'
import { PreviewTable } from './shared'
import '../inventory/inventory.css'
import '../issuance/issuance.css'

type Tab = 'app' | 'pmr' | 'pmr_gppb' | 'bac' | 'award' | 'f_ppmp' | 'f_app' | 'f_cse' | 'f_apr' | 'f_coa'
const TABS: [Tab, string][] = [
  ['app', 'APP Report'],
  ['pmr', 'PMR'],
  ['pmr_gppb', 'PMR (GPPB)'],
  ['bac', 'BAC Report'],
  ['award', 'Award Report'],
]
/** GPPB / COA forms, each with its own filters and Excel download. */
const FORMS: [Tab, string][] = [
  ['f_ppmp', 'PPMP (GPPB)'],
  ['f_app', 'APP (GPPB)'],
  ['f_cse', 'APP-CSE'],
  ['f_apr', 'APR'],
  ['f_coa', 'COA Transmittal'],
]

const cap = (s: unknown) => (typeof s === 'string' ? s.replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase()) : String(s ?? ''))

/** APP, Procurement Monitoring (summary and GPPB format), BAC and Award reports, with the same Excel export as before. */
export function ProcurementReportsPage() {
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('app')
  const [year, setYear] = useState(thisYear())
  const [exporting, setExporting] = useState(false)
  const isForm = tab.startsWith('f_')
  const q = useQuery({ queryKey: ['proc-report', tab, year], queryFn: () => procurementReportsApi.get(tab, year), enabled: !isForm })
  const d = q.data as Record<string, any> | undefined // eslint-disable-line @typescript-eslint/no-explicit-any
  const exportIt = async () => {
    setExporting(true)
    try {
      await procurementReportsApi.export(tab, year)
    } catch (e) {
      toast(e instanceof ApiError ? e.message : 'The export failed.', 'error')
    } finally {
      setExporting(false)
    }
  }
  return (
    <div className="page page-wide">
      <h1 className="page-title">Procurement Reports</h1>
      <p className="muted small">APP Report, Procurement Monitoring Report (PMR), BAC Report, and Award Report.</p>
      <div className="toolbar">
        <div className="tabs" role="tablist" aria-label="Reports">
          {TABS.map(([k, l]) => (
            <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>
              {l}
            </button>
          ))}
        </div>
        {!isForm && (
          <>
            <select value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label="Fiscal year">
              {fiscalYears().map((y) => (
                <option key={y} value={y}>
                  FY {y}
                </option>
              ))}
            </select>
            <button className="btn btn-primary" disabled={exporting} onClick={() => void exportIt()}>
              {exporting ? 'Exporting…' : '⬇ Export Excel'}
            </button>
          </>
        )}
      </div>
      <div className="toolbar">
        <span className="muted small">GPPB / COA forms:</span>
        <div className="tabs" role="tablist" aria-label="GPPB and COA forms">
          {FORMS.map(([k, l]) => (
            <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>
              {l}
            </button>
          ))}
        </div>
      </div>
      {tab === 'f_ppmp' && <PpmpGppbReport initialYear={year} />}
      {tab === 'f_app' && <AppGppbReport initialYear={year} />}
      {tab === 'f_cse' && <AppCseReport initialYear={year} />}
      {tab === 'f_apr' && <AgencyProcurementReport initialYear={year} />}
      {tab === 'f_coa' && <CoaTransmittalReport />}
      {!isForm && q.isLoading && <div className="page-loading">Loading…</div>}
      {d && tab === 'app' && (
        <>
          <div className="kpi-row">
            <Kpi label="Total Estimated" value={formatPeso(d.total_estimated)} />
            <Kpi label="Total Utilized" value={formatPeso(d.total_utilized)} />
            <Kpi label="Remaining" value={formatPeso(d.total_estimated - d.total_utilized)} />
          </div>
          <PreviewTable
            columns={[
              { key: 'department', label: 'Department' },
              { key: 'description', label: 'Description' },
              { key: 'source_of_funds', label: 'Source of Funds' },
              { key: 'procurement_mode', label: 'Mode' },
              { key: 'quantity', label: 'Qty', type: 'num' },
              { key: 'estimated_budget', label: 'Estimated', type: 'money' },
              { key: 'utilized_amount', label: 'Utilized', type: 'money' },
              { key: 'remaining', label: 'Remaining', type: 'money' },
              { key: 'status', label: 'Status' },
            ]}
            rows={(d.rows as PreviewRow[]).map((r) => ({ ...r, status: cap(r.status) }))}
            empty={`No APP data for ${d.fiscal_year}.`}
          />
        </>
      )}
      {d && tab === 'pmr' && (
        <div className="dash-grid">
          {(
            [
              ['purchase_requests', 'Purchase Requests'],
              ['rfqs', 'RFQs'],
              ['biddings', 'Biddings'],
              ['purchase_orders', 'Purchase Orders'],
              ['contracts', 'Contracts'],
            ] as const
          ).map(([k, l]) => (
            <section key={k} className="dash-card">
              <h2>
                {l}: {d[k].total}
                {d[k].value != null && <span className="muted small"> · {formatPeso(d[k].value)}</span>}
              </h2>
              <ul className="stat-list" style={{ listStyle: 'none' }}>
                {Object.entries(d[k].by_status as Record<string, number>).map(([s, n]) => (
                  <li key={s} style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>{cap(s)}</span>
                    <strong>{n}</strong>
                  </li>
                ))}
                {Object.keys(d[k].by_status).length === 0 && <li className="muted">None</li>}
              </ul>
            </section>
          ))}
        </div>
      )}
      {d && tab === 'pmr_gppb' && (
        <>
          <div className="kpi-row">
            <Kpi label="Projects" value={d.total_rows} />
            <Kpi label="Total ABC" value={formatPeso(d.total_abc)} />
            <Kpi label="Total Contract Cost" value={formatPeso(d.total_cost)} />
            <Kpi label="Savings" value={formatPeso(d.savings)} />
          </div>
          <PreviewTable
            columns={[
              { key: 'code', label: 'Code (PAP)' },
              { key: 'project', label: 'Procurement Program/Project' },
              { key: 'end_user', label: 'PMO/End-User' },
              { key: 'mode', label: 'Mode of Procurement' },
              { key: 'abc', label: 'ABC', type: 'money' },
              { key: 'contract_cost', label: 'Contract Cost', type: 'money' },
              { key: 'awardee', label: 'Awardee' },
              { key: 'date_advertised', label: 'Advertised/Posted' },
              { key: 'date_opened', label: 'Bid Opening' },
              { key: 'date_noa', label: 'NOA' },
              { key: 'date_contract', label: 'Contract Signing' },
              { key: 'date_ntp', label: 'NTP' },
              { key: 'date_accepted', label: 'Acceptance' },
              { key: 'source_of_funds', label: 'Source of Funds' },
              { key: 'remarks', label: 'Remarks' },
            ]}
            rows={d.rows}
            empty={`No awarded procurement in FY ${d.fiscal_year}.`}
          />
        </>
      )}
      {d && tab === 'bac' && (
        <>
          <div className="kpi-row">
            <Kpi label="Meetings" value={d.meetings.total} />
            <Kpi label="Resolutions" value={d.resolutions.total} />
            <Kpi label="Evaluations" value={d.evaluations.total} />
          </div>
          <h4>Meetings</h4>
          <PreviewTable
            columns={[
              { key: 'meeting_number', label: 'No.' },
              { key: 'title', label: 'Title' },
              { key: 'meeting_date', label: 'Date' },
              { key: 'status', label: 'Status' },
              { key: 'attendees_count', label: 'Attendees', type: 'num' },
            ]}
            rows={d.meetings.list}
            empty="No meetings."
          />
          <h4>Resolutions</h4>
          <PreviewTable
            columns={[
              { key: 'resolution_number', label: 'No.' },
              { key: 'title', label: 'Title' },
              { key: 'resolution_type', label: 'Type' },
              { key: 'status', label: 'Status' },
            ]}
            rows={d.resolutions.list}
            empty="No resolutions."
          />
        </>
      )}
      {d && tab === 'award' && (
        <>
          <div className="kpi-row">
            <Kpi label="Awards" value={d.total_awards} />
            <Kpi label="Total Value" value={formatPeso(d.total_value)} />
          </div>
          <PreviewTable
            columns={[
              { key: 'type', label: 'Type' },
              { key: 'reference', label: 'Reference' },
              { key: 'title', label: 'Title' },
              { key: 'supplier', label: 'Supplier' },
              { key: 'amount', label: 'Amount', type: 'money' },
              { key: 'noa', label: 'NOA' },
              { key: 'ntp', label: 'NTP' },
            ]}
            rows={d.rows}
            empty={`No awards in FY ${d.fiscal_year}.`}
          />
        </>
      )}
    </div>
  )
}

function Kpi({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="kpi">
      <span className="kpi-value">{value}</span>
      <span className="kpi-label">{label}</span>
    </div>
  )
}

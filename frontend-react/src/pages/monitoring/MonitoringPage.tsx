import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, monitoringApi } from '../../api/endpoints'
import type { MonitorLevel, Movement, RequestTrend, StockRow, WatchEntry } from '../../api/types'
import { paymentsApi } from '../../api/payments'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { formatDate, formatDateTime, formatPeso } from '../../lib/format'
import { WATCH_KINDS, pageFor } from '../../lib/links'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import './monitoring.css'

type Tab = 'stock' | 'reorder' | 'trends' | 'procurement' | 'alerts'

const LEVEL: Record<MonitorLevel, [string, string]> = {
  out: ['Out of stock', 'st-bad'],
  low: ['Low stock', 'st-pending'],
  normal: ['Normal', 'st-ok'],
  over: ['Overstock', 'st-partial'],
  untracked: ['No levels set', 'badge-muted'],
}

const MOVEMENT: Record<Movement, [string, string]> = {
  fast: ['Fast-moving', 'st-partial'],
  slow: ['Slow-moving', 'st-pending'],
  non_moving: ['Non-moving', 'st-bad'],
  idle: ['No stock, no issues', 'badge-muted'],
}

export function MonitoringPage() {
  const { can } = useAuth()
  const [tab, setTab] = useState<Tab>('stock')
  const seeStock = can('view_monitoring', 'view_inventory', 'manage_warehouse')
  const seeProc = can('view_monitoring', 'view_procurement', 'view_bidding', 'view_rfq', 'view_contracts', 'manage_bidding')
  const tabs: [Tab, string, boolean][] = [
    ['stock', 'Stock levels', seeStock],
    ['reorder', 'Re-order report', seeStock],
    ['trends', 'Request trends', seeStock],
    ['procurement', 'Procurement watch', seeProc],
    ['alerts', 'Scheduled alerts', can('manage_system_settings')],
  ]
  const visible = tabs.filter((t) => t[2])
  const active = visible.some((t) => t[0] === tab) ? tab : visible[0]?.[0]
  return (
    <div className="page page-wide">
      <h1 className="page-title">Inventory Monitoring &amp; Controls</h1>
      <div className="tabs tabs-scroll" role="tablist">
        {visible.map(([k, l]) => (
          <button key={k} role="tab" aria-selected={active === k} className={active === k ? 'active' : ''} onClick={() => setTab(k)}>
            {l}
          </button>
        ))}
      </div>
      {(active === 'stock' || active === 'reorder') && <StockTab reorderOnly={active === 'reorder'} />}
      {active === 'trends' && <TrendsTab />}
      {active === 'procurement' && <ProcurementTab />}
      {active === 'alerts' && <AlertsTab />}
    </div>
  )
}

function StockTab({ reorderOnly }: { reorderOnly: boolean }) {
  const [category, setCategory] = useState('')
  const [level, setLevel] = useState<MonitorLevel | ''>('')
  const [movement, setMovement] = useState<Movement | ''>('')
  const [search, setSearch] = useState('')
  const [printing, setPrinting] = useState(false)
  const q = useQuery({ queryKey: ['monitor-stock', category], queryFn: () => monitoringApi.stock(category ? Number(category) : undefined) })
  const d = q.data
  const rows = useMemo(() => {
    const s = search.trim().toLowerCase()
    return (reorderOnly ? d?.reorder : d?.items)?.filter(
      (r) =>
        (!level || r.level === level) &&
        (!movement || r.movement === movement) &&
        (!s || r.name.toLowerCase().includes(s) || r.code.toLowerCase().includes(s)),
    )
  }, [d, reorderOnly, level, movement, search])

  if (q.isLoading) return <div className="page-loading">Loading…</div>
  if (!d) return <div className="alert alert-error">Monitoring data could not be loaded.</div>
  return (
    <>
      <div className="kpi-row">
        <div className="kpi">
          <span className="kpi-value">{d.summary.total_items}</span>
          <span className="kpi-label">Items</span>
        </div>
        <div className={'kpi' + (d.summary.out_of_stock ? ' kpi-bad' : '')}>
          <span className="kpi-value">{d.summary.out_of_stock}</span>
          <span className="kpi-label">Out of stock</span>
        </div>
        <div className={'kpi' + (d.summary.low_stock ? ' kpi-warn' : '')}>
          <span className="kpi-value">{d.summary.low_stock}</span>
          <span className="kpi-label">At or below reorder point</span>
        </div>
        <div className="kpi">
          <span className="kpi-value">{formatPeso(d.summary.reorder_value)}</span>
          <span className="kpi-label">Suggested re-order value</span>
        </div>
        <div className={'kpi' + (d.summary.expired_lots ? ' kpi-bad' : '')}>
          <span className="kpi-value">{d.summary.expired_lots}</span>
          <span className="kpi-label">
            Expired lots · <Link to="/warehouse">{d.summary.expiring_lots} expiring in 30 days</Link>
          </span>
        </div>
        <div className="kpi">
          <span className="kpi-value">{d.summary.pending_requests}</span>
          <span className="kpi-label">
            <Link to="/requests">Open supply requests</Link>
          </span>
        </div>
      </div>

      {!reorderOnly && (
        <>
          <h3 className="dash-section-title">Stock level</h3>
          <div className="level-grid">
            {(Object.keys(LEVEL) as MonitorLevel[]).map((l) => (
              <button key={l} className={`level-card lv-${l}` + (level === l ? ' active' : '')} onClick={() => setLevel(level === l ? '' : l)}>
                <strong>{d.levels[l]}</strong>
                <span>{LEVEL[l][0]}</span>
              </button>
            ))}
          </div>
          <h3 className="dash-section-title">Movement — last {d.usage_days} days of issues</h3>
          <div className="level-grid">
            {(['fast', 'slow', 'non_moving'] as Movement[]).map((m) => (
              <button key={m} className={`level-card lv-${m}` + (movement === m ? ' active' : '')} onClick={() => setMovement(movement === m ? '' : m)}>
                <strong>{d.movement[m]}</strong>
                <span>{MOVEMENT[m][0]}</span>
              </button>
            ))}
          </div>
          <p className="muted small">
            Fast-moving items account for the first 80% of units issued; slow-moving items for the rest; non-moving items have stock but no issues in the period.
          </p>
        </>
      )}

      <div className="toolbar">
        <input type="search" placeholder="Search name or code…" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search items" />
        <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Category">
          <option value="">All categories</option>
          {d.categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        {reorderOnly && (
          <button className="btn btn-primary" onClick={() => setPrinting(true)} disabled={!rows?.length}>
            🖨 Print re-order report
          </button>
        )}
      </div>
      {reorderOnly && (
        <p className="muted small">
          Items at or below their reorder point. The suggested quantity tops each item up to its maximum level (twice the minimum where no maximum is
          set); monthly usage averages the last {d.usage_days} days of issues.
        </p>
      )}
      <StockTable rows={rows ?? []} reorder={reorderOnly} />
      {printing && rows && <ReorderPrint rows={rows} onClose={() => setPrinting(false)} usageDays={d.usage_days} />}
    </>
  )
}

function StockTable({ rows, reorder }: { rows: StockRow[]; reorder: boolean }) {
  if (rows.length === 0) return <p className="muted">{reorder ? 'Every item is above its reorder point.' : 'No items match.'}</p>
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Code</th>
            <th>Item</th>
            <th>Category</th>
            <th className="num">On hand</th>
            <th className="num">Min</th>
            <th className="num">Max</th>
            <th className="num">Usage / month</th>
            <th className="num">Months of cover</th>
            {reorder ? (
              <>
                <th className="num">Suggested order</th>
                <th className="num">Est. value</th>
              </>
            ) : (
              <th>Movement</th>
            )}
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td className="nowrap">{r.code}</td>
              <td>{r.name}</td>
              <td>{r.category?.name ?? '—'}</td>
              <td className="num">
                <strong>{r.current_stock.toLocaleString()}</strong> <span className="muted small">{r.unit_of_measure}</span>
              </td>
              <td className="num">{r.min_stock || '—'}</td>
              <td className="num">{r.max_stock || '—'}</td>
              <td className="num">{Number(r.monthly_usage) ? Number(r.monthly_usage).toLocaleString() : '—'}</td>
              <td className="num">{r.months_of_cover ?? '—'}</td>
              {reorder ? (
                <>
                  <td className="num">
                    <strong>{r.suggested_order.toLocaleString()}</strong>
                  </td>
                  <td className="num">{formatPeso(r.suggested_value)}</td>
                </>
              ) : (
                <td>
                  <span className={'badge ' + MOVEMENT[r.movement][1]}>{MOVEMENT[r.movement][0]}</span>
                </td>
              )}
              <td>
                <span className={'badge ' + LEVEL[r.level][1]}>{LEVEL[r.level][0]}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ReorderPrint({ rows, usageDays, onClose }: { rows: StockRow[]; usageDays: number; onClose: () => void }) {
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const total = rows.reduce((s, r) => s + Number(r.suggested_value), 0)
  return (
    <Modal title="Re-order report — print" onClose={onClose} width={1100}>
      <div className="slip-toolbar no-print">
        <button className="btn btn-secondary btn-sm" onClick={onClose}>
          ‹ Back
        </button>
        <button className="btn btn-primary btn-sm" onClick={() => window.print()}>
          🖨 Print
        </button>
      </div>
      <div className="slip print-area">
        <h2 className="slip-title">RE-ORDER REPORT</h2>
        <div className="slip-head">
          <span>
            Entity Name: <strong>{config.data?.agency_name || '________________'}</strong>
          </span>
          <span>
            As of: <strong>{formatDateTime(new Date(config.dataUpdatedAt || 0).toISOString())}</strong>
          </span>
        </div>
        <table className="slip-table">
          <thead>
            <tr>
              <th>Stock No.</th>
              <th>Description</th>
              <th>Unit</th>
              <th>On hand</th>
              <th>Reorder point</th>
              <th>Maximum</th>
              <th>Usage / month</th>
              <th>Qty to order</th>
              <th>Unit cost</th>
              <th>Amount</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.code}</td>
                <td>{r.name}</td>
                <td>{r.unit_of_measure}</td>
                <td className="num">{r.current_stock}</td>
                <td className="num">{r.min_stock}</td>
                <td className="num">{r.max_stock || '—'}</td>
                <td className="num">{Number(r.monthly_usage) || '—'}</td>
                <td className="num">
                  <strong>{r.suggested_order}</strong>
                </td>
                <td className="num">{formatPeso(r.unit_cost)}</td>
                <td className="num">{formatPeso(r.suggested_value)}</td>
              </tr>
            ))}
            <tr>
              <td colSpan={9} className="num">
                <strong>Total</strong>
              </td>
              <td className="num">
                <strong>{formatPeso(total)}</strong>
              </td>
            </tr>
          </tbody>
        </table>
        <p className="slip-purpose small">Usage averages the last {usageDays} days of issues. Quantities are suggestions for the purchase request.</p>
        <div className="slip-signatures">
          <div className="sig">
            Prepared by:
            <div className="sig-line" />
            <div className="sig-caption">Supply Officer</div>
          </div>
          <div className="sig">
            Noted by:
            <div className="sig-line" />
            <div className="sig-caption">Head of Office</div>
          </div>
        </div>
      </div>
    </Modal>
  )
}

function TrendsTab() {
  const [months, setMonths] = useState(6)
  const q = useQuery({ queryKey: ['monitor-trends', months], queryFn: () => monitoringApi.trends(months) })
  const rows = q.data ?? []
  return (
    <>
      <div className="toolbar">
        <select value={months} onChange={(e) => setMonths(Number(e.target.value))} aria-label="Period">
          <option value={3}>Last 3 months</option>
          <option value={6}>Last 6 months</option>
          <option value={12}>Last 12 months</option>
        </select>
      </div>
      {q.isLoading ? <div className="page-loading">Loading…</div> : <TrendChart rows={rows} />}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Month</th>
              <th className="num">Requests</th>
              <th className="num">Fulfilled</th>
              <th className="num">Rejected</th>
              <th className="num">Still open</th>
              <th className="num">Fulfilment rate</th>
              <th className="num">Units issued</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => (
              <tr key={t.label}>
                <td>{t.label}</td>
                <td className="num">{t.total_requests}</td>
                <td className="num">{t.fulfilled}</td>
                <td className="num">{t.rejected}</td>
                <td className="num">{t.open}</td>
                <td className="num">{t.total_requests ? `${Math.round((t.fulfilled / t.total_requests) * 100)}%` : '—'}</td>
                <td className="num">{t.units_issued.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

const SERIES: [keyof RequestTrend, string, string][] = [
  ['total_requests', 'Requests', 'var(--primary-accent)'],
  ['fulfilled', 'Fulfilled', 'var(--success)'],
  ['open', 'Still open', 'var(--warning)'],
]

function TrendChart({ rows }: { rows: RequestTrend[] }) {
  const max = Math.max(1, ...rows.map((r) => r.total_requests))
  const slot = 70
  const w = 50 + rows.length * slot
  const h = 220
  const bar = 16
  const y = (v: number) => 190 - (v / max) * 170
  return (
    <>
      <div className="trend-legend">
        {SERIES.map(([, l, c]) => (
          <span key={l}>
            <i style={{ background: c }} />
            {l}
          </span>
        ))}
      </div>
      <svg className="trend-chart" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Requests per month">
        <line x1={40} y1={190} x2={w} y2={190} stroke="var(--border-color)" />
        <text x={34} y={194} textAnchor="end">
          0
        </text>
        <text x={34} y={24} textAnchor="end">
          {max}
        </text>
        {rows.map((r, i) => (
          <g key={r.label}>
            {SERIES.map(([k, l, c], j) => {
              const v = Number(r[k])
              return (
                <rect key={l} x={50 + i * slot + j * (bar + 2)} y={y(v)} width={bar} height={190 - y(v)} fill={c}>
                  <title>{`${r.label} — ${l}: ${v}`}</title>
                </rect>
              )
            })}
            <text x={50 + i * slot + 26} y={207} textAnchor="middle">
              {r.label.split(' ')[0]}
            </text>
          </g>
        ))}
      </svg>
    </>
  )
}

function ProcurementTab() {
  const { can } = useAuth()
  const [kind, setKind] = useState('')
  const q = useQuery({ queryKey: ['monitor-procurement'], queryFn: monitoringApi.procurement })
  const payments = useQuery({ queryKey: ['monitor-payments'], queryFn: () => paymentsApi.due(7), enabled: can('view_payments', 'view_monitoring') })
  const all: WatchEntry[] = [...(q.data?.rows ?? []), ...((payments.data?.data ?? []) as WatchEntry[])]
  const rows = all.filter((r) => !kind || r.kind === kind)
  const byKind: Record<string, number> = { ...(q.data?.by_kind ?? {}), payment_due: payments.data?.data.length ?? 0 }
  const overdue = all.filter((r) => r.severity === 'overdue').length
  const dueSoon = all.length - overdue
  if (q.isLoading) return <div className="page-loading">Loading…</div>
  return (
    <>
      <div className="kpi-row">
        <div className={'kpi' + (overdue ? ' kpi-bad' : '')}>
          <span className="kpi-value">{overdue}</span>
          <span className="kpi-label">Overdue</span>
        </div>
        <div className={'kpi' + (dueSoon ? ' kpi-warn' : '')}>
          <span className="kpi-value">{dueSoon}</span>
          <span className="kpi-label">Due soon</span>
        </div>
      </div>
      <div className="toolbar">
        <select value={kind} onChange={(e) => setKind(e.target.value)} aria-label="Kind of deadline">
          <option value="">All deadlines</option>
          {Object.entries(WATCH_KINDS).map(([k, l]) => (
            <option key={k} value={k}>
              {l} ({byKind[k] ?? 0})
            </option>
          ))}
        </select>
      </div>
      <p className="muted small">
        Bidding stages against the RA 12009 maximum periods, quotation deadlines, PO deliveries, contract deliveries accruing liquidated damages, contracts
        and securities within 30 days of lapsing, supplier documents expiring, and approved APP lines due this quarter without a purchase request. The same
        list feeds the daily alerts.
      </p>
      {rows.length === 0 ? (
        <p className="muted">Nothing overdue or due soon.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Status</th>
                <th>Kind</th>
                <th>Reference</th>
                <th>Detail</th>
                <th>Deadline</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={i}>
                  <td>
                    <span className={'badge ' + (r.severity === 'overdue' ? 'st-bad' : 'st-pending')}>{r.severity === 'overdue' ? 'Overdue' : 'Due soon'}</span>
                  </td>
                  <td>{WATCH_KINDS[r.kind] ?? r.kind}</td>
                  <td>
                    {pageFor(r.related_type) ? <Link to={pageFor(r.related_type)!}>{r.reference}</Link> : r.reference}
                    {r.title && <div className="muted small">{r.title}</div>}
                  </td>
                  <td>{r.detail}</td>
                  <td className="nowrap">{formatDate(r.date)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

const JOB_LABEL: Record<string, string> = {
  low_stock: 'Reorder alerts',
  expiring_stock: 'Expired and expiring stock',
  procurement_deadlines: 'Bidding, RFQ and PO deadlines',
  contracts: 'Contract delivery, expiry and securities',
  supplier_documents: 'Supplier eligibility documents',
  procurement_schedule: 'APP lines due this quarter',
  depreciation: 'Monthly depreciation posting',
  clean_notifications: 'Delete read notifications older than 30 days',
  backup: 'Database backup (kept 7 days)',
}

function AlertsTab() {
  const toast = useToast()
  const q = useQuery({ queryKey: ['alert-jobs'], queryFn: monitoringApi.alertJobs })
  const [results, setResults] = useState<Record<string, string>>({})
  const run = useMutation({
    mutationFn: monitoringApi.runAlerts,
    onSuccess: (r) => {
      setResults((old) => ({ ...old, ...r.results }))
      toast('Done.')
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'The job could not be run.', 'error'),
  })
  return (
    <>
      <p className="muted small">These jobs run on their own at the times shown (Philippine time). Run one now to check it or to catch up after downtime.</p>
      <div className="toolbar">
        <button className="btn btn-primary" disabled={run.isPending} onClick={() => run.mutate('all')}>
          Run all now
        </button>
      </div>
      <div className="table-wrap">
        <table className="table alert-jobs">
          <thead>
            <tr>
              <th>Job</th>
              <th>Schedule</th>
              <th />
              <th>Last result (this session)</th>
            </tr>
          </thead>
          <tbody>
            {q.data?.jobs.map((j) => (
              <tr key={j}>
                <td>{JOB_LABEL[j] ?? j}</td>
                <td className="nowrap">{q.data.schedule[j]}</td>
                <td>
                  <button className="btn btn-outline btn-sm" disabled={run.isPending} onClick={() => run.mutate(j)}>
                    Run now
                  </button>
                </td>
                <td className="small">{results[j] ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

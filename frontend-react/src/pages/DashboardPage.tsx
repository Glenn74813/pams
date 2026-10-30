import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { analyticsApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { formatPeso } from '../lib/format'
import { WATCH_KINDS, pageFor } from '../lib/links'
import { BarList } from './dashboard/BarList'
import { DonutChart, TrendChart } from './dashboard/Charts'
import { PR_STATUS, fiscalYears, thisYear } from './procurement/labels'
import './dashboard/dashboard.css'

function Kpi({ label, value, tone, to }: { label: string; value: React.ReactNode; tone?: 'warn' | 'bad'; to?: string }) {
  const body = (
    <>
      <span className="kpi-value">{value}</span>
      <span className="kpi-label">{label}</span>
    </>
  )
  return <div className={'kpi' + (tone ? ` kpi-${tone}` : '')}>{to ? <Link to={to}>{body}</Link> : body}</div>
}

export function DashboardPage() {
  const { user, canOpen } = useAuth()
  const [year, setYear] = useState(thisYear())
  const q = useQuery({ queryKey: ['dashboard', year], queryFn: () => analyticsApi.dashboard(year) })
  if (!user) return null
  const d = q.data
  const warnIf = (n: number, tone: 'warn' | 'bad' = 'warn') => (n > 0 ? tone : undefined)

  return (
    <div className="page">
      <div className="dash-head">
        <h1 className="page-title">Dashboard</h1>
        {d?.procurement && (
          <div className="field">
            <label htmlFor="dash-fy">Fiscal year</label>
            <select id="dash-fy" value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {fiscalYears().map((y) => (
                <option key={y}>{y}</option>
              ))}
            </select>
          </div>
        )}
      </div>
      {q.isLoading && <div className="page-loading">Loading…</div>}
      {q.isError && <div className="alert alert-error">The dashboard could not be loaded.</div>}

      {d && (
        <>
          {d.todo.length > 0 && (
            <>
              <h2 className="dash-section-title">Waiting on you</h2>
              <ul className="todo-list">
                {d.todo.map((t) => (
                  <li key={t.key}>
                    <Link to={t.path}>
                      <span>{t.label}</span>
                      <span className="todo-count">{t.count}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}

          <h2 className="dash-section-title">Mine</h2>
          <div className="kpi-row">
            <Kpi label="My open supply requests" value={d.me.open_requests} to={canOpen('requests') ? '/requests' : undefined} />
            <Kpi label="My open purchase requests" value={d.me.open_purchase_requests} to={canOpen('purchase-requests') ? '/purchase-requests' : undefined} />
            <Kpi label={`Assets I hold · ${formatPeso(d.me.assets_value)}`} value={d.me.assets} to="/my-assets" />
          </div>

          {d.inventory && (
            <>
              <h2 className="dash-section-title">
                📦 Inventory {canOpen('monitoring') && <Link to="/monitoring">Monitoring →</Link>}
              </h2>
              <div className="kpi-row">
                <Kpi label="Items" value={d.inventory.total_items} to={canOpen('items') ? '/items' : undefined} />
                <Kpi label="Stock value" value={formatPeso(d.inventory.total_value)} />
                <Kpi label="Low stock" value={d.inventory.low_stock} tone={warnIf(d.inventory.low_stock)} />
                <Kpi label="Out of stock" value={d.inventory.out_of_stock} tone={warnIf(d.inventory.out_of_stock, 'bad')} />
                <Kpi label="Requests pending" value={d.inventory.pending_requests} tone={warnIf(d.inventory.pending_requests)} />
                <Kpi label={`Lots expiring in ${d.inventory.expiry_warning_days ?? 30} days`} value={d.inventory.expiring_lots} tone={warnIf(d.inventory.expiring_lots)} />
                <Kpi label="Expired lots on hand" value={d.inventory.expired_lots} tone={warnIf(d.inventory.expired_lots, 'bad')} />
              </div>
              <div className="dash-grid">
                <section className="dash-card">
                  <h2>Most issued items — last 30 days</h2>
                  <BarList rows={d.inventory.top_items} value="count" empty="Nothing was issued in the last 30 days." />
                </section>
                <section className="dash-card">
                  <h2>Requests by department — last 90 days</h2>
                  <BarList rows={d.inventory.by_department} value="count" unit="requests" empty="No requests in the last 90 days." />
                </section>
                <section className="dash-card">
                  <h2>Stock value by category</h2>
                  <DonutChart rows={d.inventory.by_category} value="amount" empty="No stock on hand." />
                </section>
                <section className="dash-card dash-card-wide">
                  <h2>Stock received and issued — last 12 months</h2>
                  <TrendChart months={d.inventory.trend} />
                  <p className="muted small">Valued at each item's current average unit cost.</p>
                </section>
              </div>
            </>
          )}

          {d.assets && (
            <>
              <h2 className="dash-section-title">🏢 Assets</h2>
              <div className="kpi-row">
                <Kpi label="Assets on record" value={d.assets.total} to="/properties" />
                <Kpi label="Assigned" value={d.assets.assigned} />
                <Kpi label="Available" value={d.assets.available} />
                <Kpi label="Under repair" value={d.assets.under_repair} tone={warnIf(d.assets.under_repair)} />
                <Kpi label="Acquisition cost" value={formatPeso(d.assets.acquisition_cost)} />
                <Kpi label="Book value" value={formatPeso(d.assets.book_value)} />
                <Kpi label="Disposals in progress" value={d.assets.disposal_pending} to={canOpen('disposal') ? '/disposal' : undefined} />
                <Kpi label="Maintenance overdue" value={d.assets.maintenance_overdue} tone={warnIf(d.assets.maintenance_overdue, 'bad')} to={canOpen('maintenance') ? '/maintenance' : undefined} />
              </div>
              <div className="dash-grid">
                <section className="dash-card">
                  <h2>Assets by category — acquisition cost</h2>
                  <DonutChart rows={d.assets.by_category} value="amount" empty="No assets on record." />
                </section>
              </div>
            </>
          )}

          {d.procurement && (
            <>
              <h2 className="dash-section-title">
                🛒 Procurement — FY {d.fiscal_year} {canOpen('monitoring') && <Link to="/monitoring">Procurement watch →</Link>}
              </h2>
              <div className="kpi-row">
                <Kpi label="PRs in routing" value={d.procurement.active.purchase_requests} />
                <Kpi label="Open RFQs" value={d.procurement.active.rfqs} />
                <Kpi label="Biddings under way" value={d.procurement.active.biddings} />
                <Kpi label="POs awaiting delivery" value={d.procurement.active.purchase_orders} />
                <Kpi label="Active contracts" value={d.procurement.active.contracts} />
                <Kpi label="Deadlines overdue" value={d.procurement.watch.overdue} tone={warnIf(d.procurement.watch.overdue, 'bad')} />
                <Kpi label="Deadlines due soon" value={d.procurement.watch.due_soon} tone={warnIf(d.procurement.watch.due_soon)} />
              </div>
              <div className="dash-grid">
                <section className="dash-card">
                  <h2>APP utilization</h2>
                  <div className="progress" role="progressbar" aria-valuenow={d.procurement.app_utilization.rate} aria-valuemin={0} aria-valuemax={100}>
                    <div style={{ width: `${Math.min(d.procurement.app_utilization.rate, 100)}%` }} />
                  </div>
                  <div className="progress-meta">
                    <span>
                      {d.procurement.app_utilization.rate}% of {formatPeso(d.procurement.app_utilization.estimated)}
                    </span>
                    <span>Remaining {formatPeso(d.procurement.app_utilization.remaining)}</span>
                  </div>
                  <ul className="stat-list">
                    <li>
                      <span>Biddings awarded</span>
                      <strong>{d.procurement.awards.biddings}</strong>
                    </li>
                    <li>
                      <span>RFQs awarded</span>
                      <strong>{d.procurement.awards.rfqs}</strong>
                    </li>
                    <li>
                      <span>Purchase orders issued</span>
                      <strong>{formatPeso(d.procurement.awards.po_value)}</strong>
                    </li>
                  </ul>
                </section>
                <section className="dash-card">
                  <h2>Purchase requests by status</h2>
                  {Object.keys(d.procurement.pr_by_status).length === 0 ? (
                    <p className="muted small">No purchase requests for FY {d.fiscal_year}.</p>
                  ) : (
                    <ul className="stat-list">
                      {Object.entries(d.procurement.pr_by_status).map(([s, n]) => (
                        <li key={s}>
                          <span className={'badge ' + (PR_STATUS[s]?.[1] ?? 'badge-muted')}>{PR_STATUS[s]?.[0] ?? s}</span>
                          <strong>{n}</strong>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
                <section className="dash-card">
                  <h2>Next deadlines</h2>
                  {d.procurement.watch.top.length === 0 ? (
                    <p className="muted small">Nothing overdue or due soon.</p>
                  ) : (
                    <ul className="watch-list">
                      {d.procurement.watch.top.map((w, i) => (
                        <li key={i}>
                          <span>
                            <span className={'badge ' + (w.severity === 'overdue' ? 'st-bad' : 'st-pending')}>{WATCH_KINDS[w.kind] ?? w.kind}</span>{' '}
                            {pageFor(w.related_type) ? <Link to={pageFor(w.related_type)!}>{w.reference}</Link> : w.reference}
                          </span>
                          <span className="muted small">{w.detail}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              </div>
            </>
          )}

          {d.suppliers && (
            <>
              <h2 className="dash-section-title">🏪 Suppliers</h2>
              <div className="kpi-row">
                <Kpi label="Active suppliers" value={`${d.suppliers.active} / ${d.suppliers.total}`} to="/suppliers" />
                <Kpi label="With documents expiring in 30 days" value={d.suppliers.expiring_compliance} tone={warnIf(d.suppliers.expiring_compliance)} />
              </div>
              <div className="dash-grid">
                <section className="dash-card">
                  <h2>Largest suppliers — FY {d.fiscal_year} PO value</h2>
                  <BarList rows={d.suppliers.top_awarded} value="amount" empty="No purchase orders this year." />
                </section>
                <section className="dash-card">
                  <h2>Best-rated suppliers</h2>
                  {d.suppliers.top_rated.length === 0 ? (
                    <p className="muted small">No supplier evaluations yet.</p>
                  ) : (
                    <ul className="stat-list">
                      {d.suppliers.top_rated.map((s) => (
                        <li key={s.name}>
                          <span>
                            {s.name} <span className="muted small">({s.count} evaluation{s.count === 1 ? '' : 's'})</span>
                          </span>
                          <strong>{Number(s.amount).toFixed(2)} / 5</strong>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}

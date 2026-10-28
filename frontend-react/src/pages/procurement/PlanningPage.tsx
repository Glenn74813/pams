import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { planningApi } from '../../api/endpoints'
import type { BudgetRow, Ppmp, ProcurementOptions } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { AppDetailModal, Meter } from './AppDetailModal'
import { APP_STATUS, fiscalYears, PPMP_STATUS, thisYear } from './labels'
import { PpmpDetailModal } from './PpmpDetailModal'
import { PpmpFormModal } from './PpmpFormModal'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import './procurement.css'

type Tab = 'ppmp' | 'app' | 'budget'

export function PlanningPage() {
  const { can } = useAuth()
  const seesApp = can('view_procurement_planning', 'manage_app', 'monitor_budget')
  const [tab, setTab] = useState<Tab>('ppmp')
  const [year, setYear] = useState(String(thisYear() + 1))
  const options = useQuery({ queryKey: ['procurement-options'], queryFn: planningApi.options, staleTime: 5 * 60_000 })
  return (
    <div className="page page-wide">
      <h1 className="page-title">Procurement Planning</h1>
      <div className="section-header">
        <div className="tabs" style={{ margin: 0 }}>
          <button className={tab === 'ppmp' ? 'active' : ''} onClick={() => setTab('ppmp')}>
            PPMP
          </button>
          {seesApp && (
            <>
              <button className={tab === 'app' ? 'active' : ''} onClick={() => setTab('app')}>
                Annual Procurement Plan
              </button>
              <button className={tab === 'budget' ? 'active' : ''} onClick={() => setTab('budget')}>
                Budget monitoring
              </button>
            </>
          )}
        </div>
        <div className="toolbar" style={{ margin: 0 }}>
          <select value={year} onChange={(e) => setYear(e.target.value)} aria-label="Fiscal year">
            {fiscalYears().map((y) => (
              <option key={y} value={y}>
                FY {y}
              </option>
            ))}
          </select>
        </div>
      </div>
      {tab === 'ppmp' && <PpmpList year={Number(year)} options={options.data} />}
      {tab === 'app' && <AppTab year={Number(year)} options={options.data} />}
      {tab === 'budget' && <BudgetTab year={Number(year)} />}
    </div>
  )
}

function PpmpList({ year, options }: { year: number; options?: ProcurementOptions }) {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<Ppmp | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Ppmp | null>(null)
  const q = useDebounced(search.trim())
  const key = `${year}|${status}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const list = useQuery({ queryKey: ['ppmps', page, year, status, q], queryFn: () => planningApi.ppmps({ page, fiscal_year: year, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['ppmps', 'ppmp', 'apps', 'app', 'budget']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  const remove = useMutation({
    mutationFn: (p: Ppmp) => planningApi.deletePpmp(p.id),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setDeleting(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete.', 'error')
      setDeleting(null)
    },
  })

  return (
    <>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search PPMP no. or title" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search PPMPs" />
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            {Object.entries(PPMP_STATUS).map(([k, [label]]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </select>
        </div>
        {can('create_ppmp') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New PPMP
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>PPMP no.</th>
              <th>Title</th>
              <th>Department</th>
              <th className="num">Lines</th>
              <th className="num">Est. budget</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((p) => (
              <tr key={p.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(p.id)}>
                    {p.ppmp_number}
                  </button>
                </td>
                <td className="clip">{p.title}</td>
                <td>{p.department.name}</td>
                <td className="num">{p.items_count}</td>
                <td className="num">{formatPeso(p.total_estimated_budget)}</td>
                <td>
                  <Badge map={PPMP_STATUS} value={p.status} /> {p.app_number && <span className="muted small">in {p.app_number}</span>}
                </td>
                <td>
                  {p.can_delete && (
                    <button className="icon-btn delete" title={`Delete ${p.ppmp_number}`} onClick={() => setDeleting(p)}>
                      🗑
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No PPMPs for FY {year}.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {viewing !== null && (
        <PpmpDetailModal
          ppmpId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(p) => {
            setViewing(null)
            setForm(p)
          }}
          onChanged={(m) => {
            toast(m, 'success')
            refresh()
          }}
        />
      )}
      {form && (
        <PpmpFormModal
          ppmp={form === 'new' ? null : form}
          options={options}
          onClose={() => setForm(null)}
          onSaved={(p, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(p.id)
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete PPMP"
          message={`Delete ${deleting.ppmp_number} (${deleting.title})?`}
          confirmLabel="Delete PPMP"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

function AppTab({ year, options }: { year: number; options?: ProcurementOptions }) {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [viewing, setViewing] = useState<number | null>(null)
  const list = useQuery({ queryKey: ['apps', year], queryFn: () => planningApi.apps({ page: 1, fiscal_year: year }) })
  const refresh = () => {
    for (const k of ['apps', 'app', 'ppmps', 'ppmp', 'budget']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  const generate = useMutation({
    mutationFn: () => planningApi.generateApp(year),
    onSuccess: (a) => {
      toast(`${a.app_number} consolidated from ${a.ppmp_count} PPMP(s).`, 'success')
      refresh()
      setViewing(a.id)
    },
    onError: (err) => toast(err instanceof ApiError ? err.message : 'Could not consolidate.', 'error'),
  })
  const app = list.data?.data[0]

  return (
    <>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      {list.data && !app && (
        <div className="dash-card" style={{ maxWidth: 640 }}>
          <h2>No APP for FY {year} yet</h2>
          <p className="muted">The APP is consolidated from the department PPMPs approved for the year. Run it again later to add PPMPs approved since, until the APP is approved.</p>
          {can('manage_app') && (
            <button className="btn btn-primary" onClick={() => generate.mutate()} disabled={generate.isPending}>
              {generate.isPending ? 'Consolidating…' : '⚙ Consolidate APP'}
            </button>
          )}
        </div>
      )}
      {app && (
        <div className="dash-card">
          <div className="section-header">
            <h2>
              <button className="link-btn" onClick={() => setViewing(app.id)}>
                {app.app_number}
              </button>{' '}
              — {app.title}
            </h2>
            <Badge map={APP_STATUS} value={app.status} />
          </div>
          <div className="kpi-row">
            <Kpi label="Planned" value={formatPeso(app.total_budget)} />
            <Kpi label="On purchase requests" value={formatPeso(app.requested_amount)} />
            <Kpi label="Approved PRs" value={formatPeso(app.utilized_amount)} />
            <Kpi label="Lines / PPMPs" value={`${app.items_count} / ${app.ppmp_count}`} />
          </div>
          {app.pending_ppmps > 0 && <p className="alert alert-warning">{app.pending_ppmps} approved PPMP(s) are not in this APP yet.</p>}
          <div className="workflow-actions">
            <button className="btn btn-primary" onClick={() => setViewing(app.id)}>
              Open APP
            </button>
            {app.can_edit && app.pending_ppmps > 0 && (
              <button className="btn btn-outline" onClick={() => generate.mutate()} disabled={generate.isPending}>
                {generate.isPending ? 'Adding…' : 'Reconsolidate'}
              </button>
            )}
          </div>
        </div>
      )}
      {viewing !== null && (
        <AppDetailModal
          appId={viewing}
          options={options}
          onClose={() => setViewing(null)}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
    </>
  )
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="kpi">
      <span className="kpi-label">{label}</span>
      <span className="kpi-value">{value}</span>
    </div>
  )
}

function BudgetTab({ year }: { year: number }) {
  const q = useQuery({ queryKey: ['budget', year], queryFn: () => planningApi.budget(year) })
  const b = q.data
  if (q.isError) return <div className="alert alert-error">{(q.error as Error).message}</div>
  if (!b) return <div className="page-loading">Loading…</div>
  return (
    <>
      <p className="muted">
        {b.app_number ? `${b.app_number} (${b.app_status})` : `No APP for FY ${year} yet`} · {b.ppmp_approved_count} of {b.ppmp_count} PPMP(s) approved. Utilized is the
        estimated cost of fully approved purchase requests; it will follow purchase orders once those move here.
      </p>
      <div className="kpi-row">
        <Kpi label="Planned" value={formatPeso(b.total_estimated)} />
        <Kpi label="On purchase requests" value={formatPeso(b.total_requested)} />
        <Kpi label="Utilized" value={`${formatPeso(b.total_utilized)} (${b.utilization_rate}%)`} />
        <Kpi label="Remaining" value={formatPeso(b.total_remaining)} />
      </div>
      <BudgetTable title="By department" rows={b.by_department} />
      <BudgetTable title="By mode of procurement" rows={b.by_mode} />
      <BudgetTable title="By quarter" rows={b.by_quarter} />
    </>
  )
}

function BudgetTable({ title, rows }: { title: string; rows: BudgetRow[] }) {
  return (
    <>
      <h3 style={{ margin: '1.25rem 0 0.5rem' }}>{title}</h3>
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th></th>
              <th className="num">Lines</th>
              <th className="num">Planned</th>
              <th className="num">On PRs</th>
              <th className="num">Utilized</th>
              <th className="num">Remaining</th>
              <th>Utilization</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.key}>
                <td>{r.label}</td>
                <td className="num">{r.count}</td>
                <td className="num">{formatPeso(r.estimated_budget)}</td>
                <td className="num">{formatPeso(r.requested_amount)}</td>
                <td className="num">{formatPeso(r.utilized_amount)}</td>
                <td className="num">{formatPeso(r.remaining)}</td>
                <td>
                  <Meter rate={r.utilization_rate} /> <span className="small">{r.utilization_rate}%</span>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  Nothing planned.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}

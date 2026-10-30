import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { maintenanceApi, type Maintenance } from '../../api/tor'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { CompleteForm, MaintenanceForm, MAINTENANCE_STATUS, MAINTENANCE_TYPE } from './MaintenanceForms'
import '../inventory/inventory.css'
import '../assets/assets.css'

/** Asset maintenance (TOR 1.4.5): what is due, overdue and done, and what it cost. */
export function MaintenancePage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [view, setView] = useState('open')
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Maintenance | null>(null)
  const [completing, setCompleting] = useState<Maintenance | null>(null)
  const [cancelling, setCancelling] = useState<Maintenance | null>(null)
  const q = useDebounced(search.trim())
  const filters = view === 'overdue' || view === 'upcoming' ? { due: view } : view === 'open' ? { status: 'scheduled' } : view ? { status: view } : {}
  const list = useQuery({ queryKey: ['maintenance', page, view, q], queryFn: () => maintenanceApi.list({ page, search: q || undefined, ...filters }), placeholderData: keepPreviousData })
  const summary = useQuery({ queryKey: ['maintenance', 'summary'], queryFn: maintenanceApi.summary })
  const manage = can('edit_properties')
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['maintenance'] })
  const cancel = useMutation({
    mutationFn: (m: Maintenance) => maintenanceApi.cancel(m.id),
    onSuccess: () => {
      toast('Maintenance cancelled.', 'success')
      setCancelling(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not cancel.', 'error')
      setCancelling(null)
    },
  })
  const s = summary.data

  return (
    <div className="page page-wide">
      <h1 className="page-title">Asset Maintenance</h1>
      {s && (
        <div className="kpi-row">
          <button className={'kpi' + (s.overdue > 0 ? ' kpi-bad' : '')} onClick={() => { setView('overdue'); setPage(1) }}>
            <span className="kpi-value">{s.overdue}</span>
            <span className="kpi-label">Overdue</span>
          </button>
          <button className="kpi" onClick={() => { setView('upcoming'); setPage(1) }}>
            <span className="kpi-value">{s.upcoming}</span>
            <span className="kpi-label">Due in {s.upcoming_days} days</span>
          </button>
          <div className="kpi">
            <span className="kpi-value">{s.done_this_year}</span>
            <span className="kpi-label">Done this year</span>
          </div>
          <div className="kpi">
            <span className="kpi-value">{formatPeso(s.cost_this_year)}</span>
            <span className="kpi-label">Spent this year</span>
          </div>
        </div>
      )}
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search tag no., item or work" value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} aria-label="Search maintenance" />
          <select value={view} onChange={(e) => { setView(e.target.value); setPage(1) }} aria-label="Show">
            <option value="open">Scheduled</option>
            <option value="overdue">Overdue</option>
            <option value="upcoming">Due in 30 days</option>
            <option value="done">Done</option>
            <option value="cancelled">Cancelled</option>
            <option value="">All</option>
          </select>
        </div>
        {manage && (
          <button className="btn btn-primary" onClick={() => setCreating(true)}>
            + Schedule maintenance
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Due</th>
              <th>Asset</th>
              <th>Type</th>
              <th>Work</th>
              <th>Accountable · location</th>
              <th>Status</th>
              <th>Done / cost</th>
              {manage && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((m) => (
              <tr key={m.id}>
                <td>{formatDate(m.scheduled_date)}</td>
                <td>
                  <span className="mono">{m.tag_number}</span> {m.item_name}
                </td>
                <td>{MAINTENANCE_TYPE[m.maintenance_type]}</td>
                <td className="clip">
                  {m.description}
                  {m.interval_months ? <span className="muted"> · every {m.interval_months} mo.</span> : null}
                </td>
                <td>
                  {m.holder ?? 'Property office'}
                  {m.location ? <span className="muted"> · {m.location}</span> : null}
                </td>
                <td>
                  <Badge map={MAINTENANCE_STATUS} value={m.overdue ? 'overdue' : m.status} />
                </td>
                <td>{m.completed_date ? `${formatDate(m.completed_date)} · ${formatPeso(m.cost)}` : '—'}</td>
                {manage && (
                  <td>
                    {m.status === 'scheduled' && (
                      <div className="workflow-actions">
                        <button className="btn btn-primary btn-sm" onClick={() => setCompleting(m)}>
                          Done
                        </button>
                        <button className="icon-btn edit" title="Reschedule" onClick={() => setEditing(m)}>
                          ✎
                        </button>
                        <button className="icon-btn delete" title="Cancel" onClick={() => setCancelling(m)}>
                          ✕
                        </button>
                      </div>
                    )}
                  </td>
                )}
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={manage ? 8 : 7} className="empty">
                  Nothing here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {creating && <MaintenanceForm onClose={() => setCreating(false)} onSaved={() => { setCreating(false); toast('Maintenance scheduled.', 'success'); refresh() }} />}
      {editing && <MaintenanceForm entry={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); toast('Schedule updated.', 'success'); refresh() }} />}
      {completing && <CompleteForm entry={completing} onClose={() => setCompleting(null)} onSaved={(msg) => { setCompleting(null); toast(msg, 'success'); refresh() }} />}
      {cancelling && (
        <ConfirmDialog
          title="Cancel maintenance"
          message={`Cancel "${cancelling.description}" for ${cancelling.tag_number}? A repeating schedule stops here.`}
          confirmLabel="Cancel it"
          busy={cancel.isPending}
          onConfirm={() => cancel.mutate(cancelling)}
          onCancel={() => setCancelling(null)}
        />
      )}
    </div>
  )
}

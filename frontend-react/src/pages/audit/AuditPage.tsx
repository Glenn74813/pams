import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ApiError, download } from '../../api/client'
import { auditApi, type AuditFilter } from '../../api/endpoints'
import type { AuditEntry } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { useDebounced } from '../../lib/hooks'
import { formatDateTime } from '../../lib/format'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import './audit.css'

const ACTION_CLASS: Record<string, string> = {
  created: 'st-ok',
  updated: 'st-partial',
  deleted: 'st-bad',
  approved: 'st-ok',
  rejected: 'st-bad',
  exported: 'badge-muted',
  printed: 'badge-muted',
  downloaded: 'badge-muted',
  logged_out: 'badge-muted',
}

const label = (action: string) => action.replace(/_/g, ' ')

export function AuditPage() {
  const { can } = useAuth()
  const toast = useToast()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [f, setF] = useState<Omit<AuditFilter, 'page' | 'search'>>({})
  const [viewing, setViewing] = useState<number | null>(null)
  const [printing, setPrinting] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const q = useDebounced(search.trim())
  const key = JSON.stringify([f, q])
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const filter: AuditFilter = { ...f, page, search: q || undefined }
  const options = useQuery({ queryKey: ['audit-filters'], queryFn: auditApi.filters })
  const list = useQuery({ queryKey: ['audit', filter], queryFn: () => auditApi.list(filter), placeholderData: keepPreviousData })
  const set = (patch: Partial<typeof f>) => setF((x) => ({ ...x, ...patch }))
  const exportCsv = async () => {
    setExporting(true)
    try {
      await download('/audit/export', { ...f, search: q || undefined }, 'audit_trail.csv')
      toast('Audit trail exported.', 'success')
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Export failed.', 'error')
    } finally {
      setExporting(false)
    }
  }
  const verify = async () => {
    setVerifying(true)
    try {
      const r = await auditApi.verify({ ...f, search: q || undefined })
      if (r.checked === 0) {
        toast('No entries match the filter, so there is nothing to check.', 'success')
      } else if (r.altered.length) {
        toast(`${r.altered.length}${r.complete ? '' : '+'} of ${r.checked} entries were changed outside PAMS (IDs ${r.altered.slice(0, 10).join(', ')}${r.altered.length > 10 ? ', …' : ''}).`, 'error')
      } else {
        toast(`All ${r.intact} sealed entries are intact${r.unsealed ? `; ${r.unsealed} older entries predate sealing` : ''}.`, 'success')
      }
    } catch (err) {
      toast(err instanceof ApiError ? err.message : 'Could not check the audit trail.', 'error')
    } finally {
      setVerifying(false)
    }
  }
  return (
    <div className="page page-wide">
      <h1 className="page-title">Audit Trail</h1>
      <div className="section-header">
        <div className="toolbar audit-filters" style={{ margin: 0 }}>
          <input type="search" placeholder="Search description or user" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search" />
          <select value={f.action ?? ''} onChange={(e) => set({ action: e.target.value || undefined })} aria-label="Action">
            <option value="">Any action</option>
            {options.data?.actions.map((a) => (
              <option key={a} value={a}>
                {label(a)}
              </option>
            ))}
          </select>
          <select value={f.auditable_type ?? ''} onChange={(e) => set({ auditable_type: e.target.value || undefined })} aria-label="Record type">
            <option value="">Any record</option>
            {options.data?.types.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
          <select value={f.user_id ?? ''} onChange={(e) => set({ user_id: e.target.value ? Number(e.target.value) : undefined })} aria-label="User">
            <option value="">Anyone</option>
            {options.data?.users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
          </select>
          <input type="date" value={f.start_date ?? ''} onChange={(e) => set({ start_date: e.target.value || undefined })} aria-label="From" />
          <input type="date" value={f.end_date ?? ''} onChange={(e) => set({ end_date: e.target.value || undefined })} aria-label="To" />
        </div>
        <div className="workflow-actions">
          <button className="btn btn-secondary" onClick={() => void verify()} disabled={verifying} title="Recompute each entry's seal to detect changes made outside PAMS">
            {verifying ? 'Checking…' : 'Check integrity'}
          </button>
          <button className="btn btn-secondary" onClick={() => setPrinting(true)}>
            🖨 Print
          </button>
          {can('export_audit_trail') && (
            <button className="btn btn-primary" onClick={() => void exportCsv()} disabled={exporting}>
              {exporting ? 'Exporting…' : 'Export CSV'}
            </button>
          )}
        </div>
      </div>
      <div className="quick-filters">
        <button className={`chip-btn ${f.action === 'deleted' ? 'on' : ''}`} onClick={() => set({ action: f.action === 'deleted' ? undefined : 'deleted' })}>
          Deleted records
        </button>
        <button className={`chip-btn ${f.action === 'logged_out' ? 'on' : ''}`} onClick={() => set({ action: f.action === 'logged_out' ? undefined : 'logged_out' })}>
          Sign-outs
        </button>
        <button className={`chip-btn ${f.action === 'printed' ? 'on' : ''}`} onClick={() => set({ action: f.action === 'printed' ? undefined : 'printed' })}>
          Prints
        </button>
        {list.data && <span className="muted small">{list.data.pagination.total.toLocaleString()} entries</span>}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table audit-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Who</th>
              <th>Action</th>
              <th>Record</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((e) => (
              <tr key={e.id} className="clickable" onClick={() => setViewing(e.id)}>
                <td className="nowrap">{formatDateTime(e.created_at)}</td>
                <td>
                  {e.user?.name ?? e.user_name ?? 'System'}
                  {e.user_role && <div className="small muted">{e.user_role}</div>}
                </td>
                <td>
                  <span className={`badge ${ACTION_CLASS[e.action] ?? 'badge-muted'}`}>{label(e.action)}</span>
                </td>
                <td className="nowrap">{e.auditable_type ? `${e.auditable_type}${e.auditable_id ? ` #${e.auditable_id}` : ''}` : '—'}</td>
                <td>{e.description}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  No entries match.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {viewing !== null && <AuditDetailModal id={viewing} onClose={() => setViewing(null)} />}
      {printing && <AuditPrintModal filter={{ ...f, search: q || undefined }} onClose={() => setPrinting(false)} />}
    </div>
  )
}

function show(v: unknown) {
  if (v === null || v === undefined || v === '') return <span className="muted">—</span>
  return typeof v === 'object' ? JSON.stringify(v) : String(v)
}

function AuditDetailModal({ id, onClose }: { id: number; onClose: () => void }) {
  const q = useQuery({ queryKey: ['audit-entry', id], queryFn: () => auditApi.get(id) })
  const e = q.data
  const keys = e ? [...new Set([...Object.keys(e.old_values ?? {}), ...Object.keys(e.new_values ?? {})])] : []
  return (
    <Modal title={e ? `${label(e.action)} — ${e.auditable_type ?? 'system'}${e.auditable_id ? ` #${e.auditable_id}` : ''}` : 'Audit entry'} onClose={onClose} width={760}>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {e && (
        <>
          <dl className="detail-grid">
            <dt>When</dt>
            <dd>{formatDateTime(e.created_at)}</dd>
            <dt>Who</dt>
            <dd>
              {e.user?.name ?? e.user_name ?? 'System'}
              {e.user_role ? ` (${e.user_role})` : ''}
            </dd>
            <dt>Description</dt>
            <dd>{e.description ?? '—'}</dd>
            <dt>From</dt>
            <dd className="small">
              {e.ip_address ?? '—'}
              {e.method && e.url ? ` · ${e.method} ${e.url}` : ''}
              {e.user_agent && <div className="muted">{e.user_agent}</div>}
            </dd>
          </dl>
          {keys.length > 0 && (
            <table className="table audit-diff">
              <thead>
                <tr>
                  <th>Field</th>
                  <th>Before</th>
                  <th>After</th>
                </tr>
              </thead>
              <tbody>
                {keys.map((k) => {
                  const before = e.old_values?.[k]
                  const after = e.new_values?.[k]
                  const changed = JSON.stringify(before) !== JSON.stringify(after)
                  return (
                    <tr key={k} className={changed ? 'changed' : ''}>
                      <td className="mono">{k}</td>
                      <td>{show(before)}</td>
                      <td>{show(after)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </>
      )}
    </Modal>
  )
}

function AuditPrintModal({ filter, onClose }: { filter: Omit<AuditFilter, 'page'>; onClose: () => void }) {
  const q = useQuery({ queryKey: ['audit-print', filter], queryFn: () => auditApi.list({ ...filter, page: 1, per_page: 500 }) })
  const scope = [
    filter.action && `action: ${label(filter.action)}`,
    filter.auditable_type && `record: ${filter.auditable_type}`,
    filter.search && `search: “${filter.search}”`,
    filter.start_date && `from ${filter.start_date}`,
    filter.end_date && `to ${filter.end_date}`,
  ].filter(Boolean)
  const rows: AuditEntry[] = q.data?.data ?? []
  return (
    <Modal title="Audit trail — print" onClose={onClose} width={1100}>
      <div className="slip-toolbar no-print">
        <button className="btn btn-secondary btn-sm" onClick={onClose}>
          ‹ Back
        </button>
        <button className="btn btn-primary btn-sm" onClick={() => window.print()} disabled={!q.data}>
          🖨 Print
        </button>
      </div>
      {q.isPending && <div className="page-loading">Loading…</div>}
      {q.data && (
        <div className="slip print-area">
          <h2 className="slip-title">AUDIT TRAIL</h2>
          <p className="small">
            {scope.length ? scope.join(' · ') : 'All entries'} — {rows.length} of {q.data.pagination.total} entries (newest first
            {q.data.pagination.total > rows.length ? '; export CSV for the rest' : ''}). Printed {formatDateTime(new Date(q.dataUpdatedAt).toISOString())}.
          </p>
          <table className="slip-table">
            <thead>
              <tr>
                <th>Date/time</th>
                <th>User</th>
                <th>Action</th>
                <th>Record</th>
                <th>Description</th>
                <th>IP</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((e) => (
                <tr key={e.id}>
                  <td className="nowrap">{formatDateTime(e.created_at)}</td>
                  <td>{e.user_name}</td>
                  <td>{label(e.action)}</td>
                  <td>{e.auditable_type ? `${e.auditable_type}${e.auditable_id ? ` #${e.auditable_id}` : ''}` : ''}</td>
                  <td>{e.description}</td>
                  <td>{e.ip_address}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  )
}

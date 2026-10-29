import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { physicalCountApi } from '../../api/endpoints'
import type { PhysicalCount } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { useDebounced } from '../../lib/hooks'
import { formatDate } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { CountDetailModal } from './CountDetailModal'
import { CountFormModal } from './CountFormModal'
import { COUNT_STATUS, COUNT_TYPE } from './labels'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'
import './physical.css'

export function PhysicalCountPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<PhysicalCount | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const key = `${status}|${type}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const params = { page, status: status || undefined, count_type: type || undefined, search: q || undefined }
  const list = useQuery({ queryKey: ['physical-counts', params], queryFn: () => physicalCountApi.list(params), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['physical-counts', 'physical-count']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <div className="page page-wide">
      <h1 className="page-title">Physical Count</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search count no. or purpose" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search counts" />
          <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Type">
            <option value="">All types</option>
            {Object.entries(COUNT_TYPE).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">Any status</option>
            {Object.entries(COUNT_STATUS).map(([k, [v]]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </div>
        {can('create_physical_inventory') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New count
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Count no.</th>
              <th>Type</th>
              <th>Scope</th>
              <th>Date</th>
              <th className="num">Lines</th>
              <th className="num">Discrepancies</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((c) => (
              <tr key={c.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(c.id)}>
                    {c.count_number}
                  </button>
                  {c.purpose && <div className="small muted">{c.purpose}</div>}
                </td>
                <td>{COUNT_TYPE[c.count_type]}</td>
                <td>{c.department?.name ?? c.category?.name ?? <span className="muted">All</span>}</td>
                <td>{formatDate(c.count_date)}</td>
                <td className="num">{c.status === 'completed' || c.status === 'verified' ? c.total_items_counted : '—'}</td>
                <td className="num">{c.status === 'completed' || c.status === 'verified' ? <span className={c.total_discrepancies ? 'short' : ''}>{c.total_discrepancies}</span> : '—'}</td>
                <td>
                  <Badge map={COUNT_STATUS} value={c.status} />
                  {c.can_verify && <span className="badge st-pending">Awaiting you</span>}
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No physical counts yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {viewing !== null && (
        <CountDetailModal
          countId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(c) => {
            setViewing(null)
            setForm(c)
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
      {form && (
        <CountFormModal
          count={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(d, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(d.count.id)
          }}
        />
      )}
    </div>
  )
}

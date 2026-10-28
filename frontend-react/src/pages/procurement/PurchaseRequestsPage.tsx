import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { planningApi, prApi } from '../../api/endpoints'
import type { PurchaseRequest } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { PR_STATUS, PRIORITY } from './labels'
import { PrDetailModal } from './PrDetailModal'
import { PrFormModal } from './PrFormModal'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import './procurement.css'

const SIGNATORY = ['pr_approve_department', 'pr_approve_budget', 'pr_approve_accounting', 'pr_approve_bac']

export function PurchaseRequestsPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const signatory = can(...SIGNATORY)
  const [view, setView] = useState<'awaiting' | 'all'>(signatory ? 'awaiting' : 'all')
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [priority, setPriority] = useState('')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<PurchaseRequest | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const key = `${view}|${status}|${priority}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const options = useQuery({ queryKey: ['procurement-options'], queryFn: planningApi.options, staleTime: 5 * 60_000 })
  const list = useQuery({
    queryKey: ['prs', page, view, status, priority, q],
    queryFn: () => prApi.list({ page, awaiting_me: view === 'awaiting' || undefined, status: status || undefined, priority: priority || undefined, search: q || undefined }),
    placeholderData: keepPreviousData,
  })
  const refresh = () => {
    for (const k of ['prs', 'pr', 'apps', 'app', 'budget']) void queryClient.invalidateQueries({ queryKey: [k] })
  }

  return (
    <div className="page page-wide">
      <h1 className="page-title">Purchase Requests</h1>
      {signatory && (
        <div className="tabs">
          <button className={view === 'awaiting' ? 'active' : ''} onClick={() => setView('awaiting')}>
            Awaiting my signature
          </button>
          <button className={view === 'all' ? 'active' : ''} onClick={() => setView('all')}>
            All requests
          </button>
        </div>
      )}
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search PR no. or purpose" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search purchase requests" />
          {view === 'all' && (
            <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
              <option value="">All statuses</option>
              <option value="draft">Draft</option>
              <option value="submitted">Submitted</option>
              <option value="under_review">Under review</option>
              <option value="returned">Returned</option>
              <option value="approved">Approved</option>
              <option value="cancelled">Cancelled</option>
            </select>
          )}
          <select value={priority} onChange={(e) => setPriority(e.target.value)} aria-label="Priority">
            <option value="">Any priority</option>
            <option value="high">High</option>
            <option value="medium">Medium</option>
            <option value="low">Low</option>
          </select>
        </div>
        {can('create_procurement', 'edit_procurement') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New Purchase Request
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>PR no.</th>
              <th>Date</th>
              <th>Purpose</th>
              <th>Office</th>
              <th>Priority</th>
              <th className="num">Est. cost</th>
              <th>Status</th>
              <th>Stage</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((p) => (
              <tr key={p.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(p.id)}>
                    {p.pr_number}
                  </button>
                </td>
                <td>{formatDate(p.request_date)}</td>
                <td className="clip">{p.purpose}</td>
                <td>{p.department.name}</td>
                <td>
                  <Badge map={PRIORITY} value={p.priority} />
                </td>
                <td className="num">{formatPeso(p.total_amount)}</td>
                <td>
                  <Badge map={PR_STATUS} value={p.status} />
                </td>
                <td>{p.status === 'submitted' || p.status === 'under_review' ? p.stage_label : '—'}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  {view === 'awaiting' ? 'Nothing is waiting for your signature.' : 'No purchase requests found.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {viewing !== null && (
        <PrDetailModal
          prId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(p) => {
            setViewing(null)
            setForm(p)
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
      {form && (
        <PrFormModal
          pr={form === 'new' ? null : form}
          options={options.data}
          onClose={() => setForm(null)}
          onSaved={(p, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(p.id)
          }}
        />
      )}
    </div>
  )
}

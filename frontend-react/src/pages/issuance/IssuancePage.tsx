import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { requestsApi } from '../../api/endpoints'
import type { SupplyRequest } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatQty } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { RequestStatusBadge } from './badges'
import { RequestDetailModal } from './RequestDetailModal'
import { RequestFormModal } from './RequestFormModal'
import '../inventory/inventory.css'
import './issuance.css'

export function IssuancePage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const seesAll = can('view_requests')
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState(can('approve_requests') ? 'pending' : '')
  const [search, setSearch] = useState('')
  const [mine, setMine] = useState(!seesAll)
  const [viewing, setViewing] = useState<number | null>(null)
  const [editing, setEditing] = useState<SupplyRequest | 'new' | null>(null)
  const [deleting, setDeleting] = useState<SupplyRequest | null>(null)
  const q = useDebounced(search.trim())

  const key = `${status}|${q}|${mine}`
  const [shownKey, setShownKey] = useState(key)
  if (shownKey !== key) {
    setShownKey(key)
    setPage(1)
  }

  const list = useQuery({
    queryKey: ['requests', page, status, q, mine],
    queryFn: () => requestsApi.list({ page, status: status || undefined, search: q || undefined, mine }),
    placeholderData: keepPreviousData,
  })

  const refresh = () => {
    for (const k of ['requests', 'request', 'items', 'item', 'stock-card', 'semi-expendable']) void queryClient.invalidateQueries({ queryKey: [k] })
  }

  const remove = useMutation({
    mutationFn: (r: SupplyRequest) => requestsApi.remove(r.id),
    onSuccess: () => {
      toast('Request deleted.', 'success')
      setDeleting(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete the request.', 'error')
      setDeleting(null)
    },
  })

  return (
    <div className="page page-wide">
      <h1 className="page-title">Issuance &amp; Requests</h1>
      <div className="section-header">
        <h2>{mine ? 'My requests' : 'All requests'} {list.data ? `(${list.data.pagination.total})` : ''}</h2>
        {can('create_requests') && (
          <button className="btn btn-primary" onClick={() => setEditing('new')}>
            + New Request
          </button>
        )}
      </div>

      <div className="toolbar">
        <input type="search" placeholder="Search request no., RIS no. or purpose" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search requests" />
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
          <option value="">All statuses</option>
          <option value="pending">Pending</option>
          <option value="partially_fulfilled">Partly issued</option>
          <option value="fulfilled">Issued</option>
          <option value="rejected">Rejected</option>
        </select>
        {seesAll && (
          <label className="checkbox-line">
            <input type="checkbox" checked={mine} onChange={(e) => setMine(e.target.checked)} /> Only mine
          </label>
        )}
      </div>

      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Request no.</th>
              <th>Date</th>
              <th>Requester</th>
              <th>Office</th>
              <th>Purpose</th>
              <th className="num">Items</th>
              <th>RIS no.</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((r) => (
              <tr key={r.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(r.id)}>
                    {r.request_number}
                  </button>
                  {r.priority === 'high' && <span className="badge st-bad pri">High</span>}
                </td>
                <td>{formatDate(r.request_date)}</td>
                <td>{r.requester?.name}</td>
                <td>{r.department?.name}</td>
                <td className="clip">{r.purpose}</td>
                <td className="num">
                  {r.items.length} <span className="muted">({formatQty(r.total_issued)}/{formatQty(r.total_requested)})</span>
                </td>
                <td className="mono">{r.ris_number ?? '—'}</td>
                <td>
                  <RequestStatusBadge status={r.status} />
                </td>
                <td>
                  <div className="actions">
                    {r.can_process && (
                      <button className="btn btn-primary btn-sm" onClick={() => setViewing(r.id)}>
                        Process
                      </button>
                    )}
                    {r.can_edit && (
                      <button className="icon-btn edit" title={`Edit ${r.request_number}`} onClick={() => setEditing(r)}>
                        ✎
                      </button>
                    )}
                    {r.can_delete && (
                      <button className="icon-btn delete" title={`Delete ${r.request_number}`} onClick={() => setDeleting(r)}>
                        🗑
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={9} className="empty">
                  {status === 'pending' ? 'No requests waiting for approval.' : 'No requests found.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {viewing !== null && (
        <RequestDetailModal
          requestId={viewing}
          onClose={() => setViewing(null)}
          onChanged={(message, warnings) => {
            toast(message, 'success')
            warnings?.forEach((w) => toast(w, 'info'))
            refresh()
          }}
        />
      )}
      {editing && (
        <RequestFormModal
          request={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(_, message) => {
            toast(message, 'success')
            setEditing(null)
            refresh()
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete request"
          message={`Delete ${deleting.request_number}? Only pending requests can be deleted.`}
          confirmLabel="Delete request"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </div>
  )
}

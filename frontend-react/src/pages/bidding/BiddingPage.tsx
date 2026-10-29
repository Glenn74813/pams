import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { biddingApi } from '../../api/endpoints'
import type { Bidding } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDateTime, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { BiddingDetailModal } from './BiddingDetailModal'
import { BiddingFormModal } from './BiddingFormModal'
import { BIDDING_STATUS } from './labels'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

export function BiddingPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<Bidding | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const key = `${status}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const list = useQuery({ queryKey: ['biddings', page, status, q], queryFn: () => biddingApi.list({ page, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['biddings', 'bidding', 'bidding-pr-options', 'rfq-pr-options', 'bac-resolutions', 'supplier-options']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <div className="page page-wide">
      <h1 className="page-title">Competitive Bidding</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search bidding no. or title" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search biddings" />
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            {Object.entries(BIDDING_STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        {can('manage_bidding') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New bidding
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>No.</th>
              <th>Title</th>
              <th>PR</th>
              <th className="num">ABC</th>
              <th>Bids due</th>
              <th className="num">Bids</th>
              <th>Status</th>
              <th>Awarded to</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((b) => (
              <tr key={b.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(b.id)}>
                    {b.bidding_number}
                  </button>
                </td>
                <td className="clip">{b.title}</td>
                <td className="mono">{b.purchase_request?.name}</td>
                <td className="num">{formatPeso(b.abc)}</td>
                <td>{formatDateTime(b.submission_deadline)}</td>
                <td className="num">{b.bidders_count}</td>
                <td>
                  <Badge map={BIDDING_STATUS} value={b.status} />
                </td>
                <td>{b.awarded_supplier?.name ?? '—'}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No biddings found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {viewing !== null && (
        <BiddingDetailModal
          biddingId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(b) => {
            setViewing(null)
            setForm(b)
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
      {form && (
        <BiddingFormModal
          bidding={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(b, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(b.id)
          }}
        />
      )}
    </div>
  )
}

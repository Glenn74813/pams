import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { rfqApi } from '../../api/endpoints'
import type { Rfq } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDateTime, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { RFQ_STATUS } from './labels'
import { RfqDetailModal } from './RfqDetailModal'
import { RfqFormModal } from './RfqFormModal'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

export function RfqPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<Rfq | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const key = `${status}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const list = useQuery({ queryKey: ['rfqs', page, status, q], queryFn: () => rfqApi.list({ page, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['rfqs', 'rfq', 'rfq-abstract', 'rfq-pr-options', 'bac-resolutions', 'supplier-options']) void queryClient.invalidateQueries({ queryKey: [k] })
  }

  return (
    <div className="page page-wide">
      <h1 className="page-title">RFQ / Alternative Modes</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search RFQ no. or title" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search RFQs" />
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            {Object.entries(RFQ_STATUS).map(([k, [label]]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </select>
        </div>
        {can('create_rfq') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New RFQ
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>RFQ no.</th>
              <th>Title</th>
              <th>Mode</th>
              <th>PR</th>
              <th className="num">ABC</th>
              <th>Deadline</th>
              <th>Status</th>
              <th>Awarded to</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((r) => (
              <tr key={r.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(r.id)}>
                    {r.rfq_number}
                  </button>
                </td>
                <td className="clip">{r.title}</td>
                <td>{r.procurement_method}</td>
                <td className="mono">{r.purchase_request?.name}</td>
                <td className="num">{formatPeso(r.abc)}</td>
                <td>{formatDateTime(r.deadline)}</td>
                <td>
                  <Badge map={RFQ_STATUS} value={r.status} />
                </td>
                <td>{r.awarded_supplier?.name ?? '—'}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No RFQs found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {viewing !== null && (
        <RfqDetailModal
          rfqId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(r) => {
            setViewing(null)
            setForm(r)
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
      {form && (
        <RfqFormModal
          rfq={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(r, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(r.id)
          }}
        />
      )}
    </div>
  )
}

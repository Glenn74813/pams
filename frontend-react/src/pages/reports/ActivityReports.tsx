import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { receivingApi, reportOptionsApi, reportsApi } from '../../api/endpoints'
import { Pager } from '../../components/Pager'
import { useDebounced } from '../../lib/hooks'
import { formatDate, formatDateTime } from '../../lib/format'
import { ExcelButton } from '../../components/ExcelButton'
import { PreviewTable } from './shared'

export function IarReport() {
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const term = useDebounced(search)
  const q = useQuery({ queryKey: ['report-iar', page, term], queryFn: () => receivingApi.list({ page, search: term || undefined }), placeholderData: keepPreviousData })
  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search IAR or PO number…" value={search} onChange={(e) => (setSearch(e.target.value), setPage(1))} aria-label="Search" />
      </div>
      {q.data && q.data.data.length === 0 && <p className="muted">No inspection reports.</p>}
      {q.data && q.data.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>IAR #</th>
                <th>PO Number</th>
                <th>Supplier</th>
                <th>Delivery Date</th>
                <th>Status</th>
                <th className="num">Items (Ordered / Received)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {q.data.data.map((r) => (
                <tr key={r.id}>
                  <td>{r.iar_number}</td>
                  <td>{r.purchase_order?.name ?? '—'}</td>
                  <td>{r.supplier?.name ?? '—'}</td>
                  <td className="nowrap">{formatDate(r.delivery_date)}</td>
                  <td>{r.status}</td>
                  <td className="num">
                    {r.total_items_ordered} / {r.total_items_received}
                  </td>
                  <td>
                    <ExcelButton path={`/receiving/inspection-reports/${r.id}/export`} name={`IAR_${r.iar_number}.xlsx`} label="IAR" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager pagination={q.data?.pagination} onPage={setPage} />
    </>
  )
}

export function RequestLogsReport() {
  const opts = useQuery({ queryKey: ['report-options'], queryFn: reportOptionsApi.options })
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [dept, setDept] = useState('')
  const q = useQuery({
    queryKey: ['request-logs', start, end, dept],
    queryFn: () => reportsApi.requestLogs({ start_date: start || undefined, end_date: end || undefined, department_id: dept ? Number(dept) : undefined }),
  })
  const s = q.data?.summary
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="rl-start">From</label>
          <input id="rl-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-end">To</label>
          <input id="rl-end" type="date" value={end} onChange={(e) => setEnd(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-dept-f">Department</label>
          <select id="rl-dept-f" value={dept} onChange={(e) => setDept(e.target.value)}>
            <option value="">All Departments</option>
            {opts.data?.departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
        </div>
      </div>
      {s && (
        <div className="kpi-row">
          <div className="kpi">
            <span className="kpi-value">{s.total_requests}</span>
            <span className="kpi-label">Request lines</span>
          </div>
          <div className="kpi">
            <span className="kpi-value">{s.total_quantity.toLocaleString()}</span>
            <span className="kpi-label">Units requested</span>
          </div>
        </div>
      )}
      {s && Object.keys(s.by_department).length > 0 && (
        <>
          <h4>By department</h4>
          <PreviewTable
            columns={[
              { key: 'name', label: 'Department' },
              { key: 'count', label: 'Requests', type: 'num' },
              { key: 'quantity', label: 'Quantity', type: 'num' },
            ]}
            rows={Object.entries(s.by_department).map(([name, v]) => ({ name, ...v }))}
          />
        </>
      )}
      <h4>Log</h4>
      <PreviewTable
        columns={[
          { key: 'at', label: 'Requested' },
          { key: 'department', label: 'Department' },
          { key: 'item', label: 'Item' },
          { key: 'quantity', label: 'Qty', type: 'num' },
          { key: 'requester', label: 'Requested by' },
        ]}
        rows={q.data?.logs.map((l) => ({
          at: formatDateTime(l.requested_at),
          department: l.department?.name ?? null,
          item: l.item ? `${l.item.code} — ${l.item.name}` : null,
          quantity: l.quantity,
          requester: l.requester?.name ?? null,
        }))}
        empty="No requests in this period."
      />
    </>
  )
}

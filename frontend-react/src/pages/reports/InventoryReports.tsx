import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, reportOptionsApi, reportsApi, requestsApi } from '../../api/endpoints'
import type { Item, RsmiSummary } from '../../api/types'
import { ExcelButton } from '../../components/ExcelButton'
import { ItemPicker } from '../../components/ItemPicker'
import { Pager } from '../../components/Pager'
import { useDebounced } from '../../lib/hooks'
import { formatDate, formatDateTime, formatPeso, today } from '../../lib/format'
import { MONTHS } from './labels'
import { ExportButton, PreviewTable, type Column } from './shared'

export function InventoryReport() {
  const opts = useQuery({ queryKey: ['report-options'], queryFn: reportOptionsApi.options })
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState('')
  const q = useQuery({
    queryKey: ['report-inventory', category, status],
    queryFn: () => reportsApi.inventory({ category_id: category ? Number(category) : undefined, status: status || undefined }),
  })
  const s = q.data?.summary
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="ir-cat">Category</label>
          <select id="ir-cat" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All Categories</option>
            {opts.data?.categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="ir-status">Status</label>
          <select id="ir-status" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All Statuses</option>
            <option value="in_stock">In Stock</option>
            <option value="low_stock">Low Stock</option>
            <option value="out_of_stock">Out of Stock</option>
          </select>
        </div>
        <button className="btn btn-secondary" onClick={() => window.print()} disabled={!q.data}>
          🖨 Print
        </button>
      </div>
      {s && (
        <div className="kpi-row">
          <div className="kpi">
            <span className="kpi-value">{s.total_items}</span>
            <span className="kpi-label">Items</span>
          </div>
          <div className="kpi">
            <span className="kpi-value">{formatPeso(s.total_value)}</span>
            <span className="kpi-label">Total value</span>
          </div>
          <div className={'kpi' + (s.low_stock_items ? ' kpi-warn' : '')}>
            <span className="kpi-value">{s.low_stock_items}</span>
            <span className="kpi-label">Low stock</span>
          </div>
          <div className={'kpi' + (s.out_of_stock_items ? ' kpi-bad' : '')}>
            <span className="kpi-value">{s.out_of_stock_items}</span>
            <span className="kpi-label">Out of stock</span>
          </div>
        </div>
      )}
      {q.isLoading && <div className="page-loading">Loading…</div>}
      {q.data && (
        <div className="print-area">
          <h3 className="no-screen">Inventory Report</h3>
          <PreviewTable
            columns={[
              { key: 'code', label: 'Code' },
              { key: 'name', label: 'Item' },
              { key: 'category', label: 'Category' },
              { key: 'unit_of_measure', label: 'Unit' },
              { key: 'current_stock', label: 'On hand', type: 'num' },
              { key: 'min_stock', label: 'Min', type: 'num' },
              { key: 'max_stock', label: 'Max', type: 'num' },
              { key: 'weighted_average_cost', label: 'Unit cost', type: 'money' },
              { key: 'total_value', label: 'Value', type: 'money' },
            ]}
            rows={q.data.items.map((i) => ({ ...i, category: i.category?.name ?? null }) as unknown as Record<string, string | number | null>)}
            empty="No items match."
          />
        </div>
      )}
    </>
  )
}

export function StockCardReport() {
  const [item, setItem] = useState<Item | null>(null)
  const q = useQuery({ queryKey: ['stock-card', item?.id], queryFn: () => inventoryApi.stockCard(item!.id), enabled: !!item })
  return (
    <>
      <div className="toolbar">
        <div className="field" style={{ minWidth: 320 }}>
          <label htmlFor="sc-item">Item</label>
          <ItemPicker id="sc-item" onPick={setItem} />
        </div>
        {item && <ExportButton path={`/reports/stock-card/${item.id}/export`} name={`StockCard_${item.code}.xlsx`} label="Export Stock Card (Appendix 58)" />}
      </div>
      {item && (
        <p className="muted small">
          {item.code} — {item.name} · reorder point {q.data?.item.reorder_point ?? '—'} · on hand {q.data?.item.current_stock ?? '—'}
        </p>
      )}
      <PreviewTable
        columns={[
          { key: 'date', label: 'Date' },
          { key: 'reference', label: 'Reference' },
          { key: 'receipt', label: 'Receipt Qty.', type: 'num' },
          { key: 'issue', label: 'Issue Qty.', type: 'num' },
          { key: 'office', label: 'Office' },
          { key: 'balance', label: 'Balance Qty.', type: 'num' },
        ]}
        rows={q.data?.rows}
        empty="No movements on this card yet."
      />
    </>
  )
}

export function RsmiReport() {
  const now = new Date(today())
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [error, setError] = useState<string | null>(null)
  const history = useQuery({ queryKey: ['rsmi-history'], queryFn: reportsApi.rsmiHistory })
  const gen = useMutation({
    mutationFn: () => reportsApi.generateRsmi(year, month),
    onSuccess: () => {
      setError(null)
      void history.refetch()
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'The RSMI could not be generated.'),
  })
  const r: RsmiSummary | undefined = gen.data
  const years = Array.from({ length: 6 }, (_, i) => now.getFullYear() - i)
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="rsmi-y">Year</label>
          <select id="rsmi-y" value={year} onChange={(e) => setYear(Number(e.target.value))}>
            {years.map((y) => (
              <option key={y}>{y}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="rsmi-m">Month</label>
          <select id="rsmi-m" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
            {MONTHS.map((m, i) => (
              <option key={m} value={i + 1}>
                {m}
              </option>
            ))}
          </select>
        </div>
        <button className="btn btn-secondary" disabled={gen.isPending} onClick={() => gen.mutate()}>
          {gen.isPending ? 'Generating…' : 'Generate RSMI'}
        </button>
        <ExportButton path="/reports/rsmi/export" params={{ year, month }} name={`RSMI_${MONTHS[month - 1]}_${year}.xlsx`} label="Export to Excel (Appendix 64)" />
      </div>
      {error && <div className="alert alert-error">{error}</div>}
      {r && (
        <>
          <p className="muted small">
            {r.report_number} — {MONTHS[r.month - 1]} {r.year} · generated {formatDateTime(r.generated_at)}
          </p>
          <div className="kpi-row">
            <div className="kpi">
              <span className="kpi-value">{r.total_requests}</span>
              <span className="kpi-label">Total requests</span>
            </div>
            <div className="kpi">
              <span className="kpi-value">{r.fulfilled_requests}</span>
              <span className="kpi-label">Fulfilled</span>
            </div>
            <div className="kpi kpi-warn">
              <span className="kpi-value">{r.pending_requests}</span>
              <span className="kpi-label">Pending</span>
            </div>
            <div className="kpi kpi-bad">
              <span className="kpi-value">{r.rejected_requests}</span>
              <span className="kpi-label">Rejected</span>
            </div>
          </div>
          <h4>By department</h4>
          <PreviewTable
            columns={[
              { key: 'name', label: 'Department' },
              { key: 'total', label: 'Total', type: 'num' },
              { key: 'fulfilled', label: 'Fulfilled', type: 'num' },
              { key: 'pending', label: 'Pending', type: 'num' },
            ]}
            rows={Object.entries(r.requests_by_department).map(([name, v]) => ({ name, ...v }))}
            empty="No requests this month."
          />
          <h4>Items requested</h4>
          <PreviewTable
            columns={[
              { key: 'name', label: 'Item' },
              { key: 'total_quantity', label: 'Requested', type: 'num' },
              { key: 'total_approved', label: 'Approved', type: 'num' },
              { key: 'request_count', label: 'Requests', type: 'num' },
            ]}
            rows={Object.entries(r.items_requested).map(([name, v]) => ({ name, ...v }))}
            empty="No items requested this month."
          />
        </>
      )}
      <h4>Generated RSMI summaries</h4>
      <PreviewTable
        columns={[
          { key: 'report_number', label: 'Report No.' },
          { key: 'period', label: 'Period' },
          { key: 'generated_at', label: 'Generated' },
          { key: 'generated_by', label: 'By' },
        ]}
        rows={history.data?.map((h) => ({ ...h, period: `${MONTHS[h.month - 1]} ${h.year}`, generated_at: formatDateTime(h.generated_at) }))}
        empty="None yet. The month-end job generates one automatically."
      />
    </>
  )
}

export function RisReport() {
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const term = useDebounced(search)
  const q = useQuery({
    queryKey: ['report-ris', page, term, status],
    queryFn: () => requestsApi.list({ page, search: term || undefined, status: status || undefined }),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search request number…" value={search} onChange={(e) => (setSearch(e.target.value), setPage(1))} aria-label="Search" />
        <select value={status} onChange={(e) => (setStatus(e.target.value), setPage(1))} aria-label="Status">
          <option value="">All Statuses</option>
          <option value="pending">Pending</option>
          <option value="approved">Approved</option>
          <option value="partially_fulfilled">Partially Fulfilled</option>
          <option value="rejected">Rejected</option>
          <option value="fulfilled">Fulfilled</option>
        </select>
      </div>
      {q.data && q.data.data.length === 0 && <p className="muted">No requests found.</p>}
      {q.data && q.data.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Request No.</th>
                <th>RIS No.</th>
                <th>Department</th>
                <th>Requested by</th>
                <th>Date</th>
                <th>Status</th>
                <th>Forms</th>
              </tr>
            </thead>
            <tbody>
              {q.data.data.map((r) => (
                <tr key={r.id}>
                  <td>{r.request_number}</td>
                  <td>{r.ris_number ?? '—'}</td>
                  <td>{r.department?.name ?? '—'}</td>
                  <td>{r.requester?.name ?? '—'}</td>
                  <td className="nowrap">{formatDate(r.request_date)}</td>
                  <td>{r.status.replace('_', ' ')}</td>
                  <td className="nowrap">
                    <ExcelButton path={`/requests/${r.id}/export-ris`} name={`RIS_${r.ris_number ?? r.request_number}.xlsx`} label="RIS" />{' '}
                    <ExcelButton path={`/requests/${r.id}/export-ics`} name={`ICS_${r.ics_number ?? r.request_number}.xlsx`} label="ICS" />{' '}
                    <ExcelButton path={`/requests/${r.id}/export-par`} name={`PAR_${r.request_number}.xlsx`} label="PAR" />
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

const RPCI_COLS: Column[] = [
  { key: 'article', label: 'Article' },
  { key: 'description', label: 'Description' },
  { key: 'stock_no', label: 'Stock No.' },
  { key: 'unit', label: 'Unit' },
  { key: 'unit_value', label: 'Unit Value', type: 'money' },
  { key: 'balance_per_card', label: 'Balance / Card', type: 'num' },
]

export function RpciReport() {
  const [type, setType] = useState('')
  const [asAt, setAsAt] = useState(today())
  const q = useQuery({ queryKey: ['rpci', type], queryFn: () => reportsApi.preview('rpci', { inventory_type: type || undefined }) })
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="rpci-type">Inventory Type</label>
          <select id="rpci-type" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">All (excluding PPE)</option>
            <option value="consumable">Consumable</option>
            <option value="semi_expandable">Semi-Expendable</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="rpci-date">As At Date</label>
          <input id="rpci-date" type="date" value={asAt} onChange={(e) => setAsAt(e.target.value)} />
        </div>
        <ExportButton path="/reports/rpci/export" params={{ inventory_type: type || undefined, as_at_date: asAt || undefined }} name="RPCI.xlsx" label="Export RPCI (Appendix 66)" />
      </div>
      <PreviewTable columns={RPCI_COLS} rows={q.data?.rows} empty="No items." />
    </>
  )
}

export function AgingReport() {
  const [days, setDays] = useState(180)
  const q = useQuery({ queryKey: ['aging', days], queryFn: () => reportsApi.aging(days) })
  const d = q.data
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="aging-days">Slow-moving after (days without issue)</label>
          <select id="aging-days" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {[90, 180, 365].map((n) => (
              <option key={n} value={n}>
                {n} days
              </option>
            ))}
          </select>
        </div>
      </div>
      {d && (
        <div className="kpi-row">
          {Object.entries(d.buckets).map(([b, n]) => (
            <div key={b} className="kpi">
              <span className="kpi-value">{n}</span>
              <span className="kpi-label">{b === 'never' ? 'Never issued' : `${b} days`}</span>
            </div>
          ))}
          <div className="kpi kpi-warn">
            <span className="kpi-value">{formatPeso(d.value_at_risk)}</span>
            <span className="kpi-label">{d.slow_moving_count} slow-moving · value at risk</span>
          </div>
        </div>
      )}
      <PreviewTable
        columns={[
          { key: 'stock_no', label: 'Stock No.' },
          { key: 'description', label: 'Description' },
          { key: 'category', label: 'Category' },
          { key: 'on_hand', label: 'On hand', type: 'num' },
          { key: 'value', label: 'Value', type: 'money' },
          { key: 'last_issued', label: 'Last issued' },
          { key: 'days_idle', label: 'Days idle', type: 'num' },
          { key: 'slow', label: 'Slow-moving' },
        ]}
        rows={d?.rows.map((r) => ({ ...r, slow: r.slow_moving ? 'Yes' : '' }))}
        empty="No items in stock."
      />
    </>
  )
}

export function ReconciliationReport() {
  const q = useQuery({ queryKey: ['reconciliation'], queryFn: reportsApi.reconciliation })
  return (
    <>
      <p className="muted small">Items whose Stock Card balance (receipts less issues) differs from the recorded quantity on hand.</p>
      <PreviewTable
        columns={[
          { key: 'stock_no', label: 'Stock No.' },
          { key: 'description', label: 'Description' },
          { key: 'card_balance', label: 'Card balance', type: 'num' },
          { key: 'on_hand', label: 'On hand', type: 'num' },
          { key: 'variance', label: 'Variance', type: 'num' },
          { key: 'value_variance', label: 'Value variance', type: 'money' },
        ]}
        rows={q.data?.rows}
        empty="Every card balance agrees with the quantity on hand."
      />
    </>
  )
}

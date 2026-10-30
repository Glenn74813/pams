import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { poApi, supplierApi } from '../../api/endpoints'
import type { PurchaseOrder } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { IarFormModal } from './IarFormModal'
import { PO_STATUS } from './labels'
import { PoDetailModal } from './PoDetailModal'
import { PoFormModal } from './PoFormModal'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

export function PurchaseOrdersPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const suppliers = useQuery({ queryKey: ['supplier-options'], queryFn: supplierApi.options, retry: false })
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<PurchaseOrder | 'new' | null>(null)
  const [receiving, setReceiving] = useState<PurchaseOrder | null>(null)
  const q = useDebounced(search.trim())
  const key = `${status}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const list = useQuery({ queryKey: ['pos', page, status, q, supplierId], queryFn: () => poApi.list({ page, status: status || undefined, search: q || undefined, supplier_id: supplierId ? Number(supplierId) : undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['pos', 'po', 'po-sources', 'iars', 'iar']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <div className="page page-wide">
      <h1 className="page-title">Purchase Orders</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search PO no." value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search purchase orders" />
          <select value={supplierId} onChange={(e) => { setSupplierId(e.target.value); setPage(1) }} aria-label="Supplier">
            <option value="">All suppliers</option>
            {suppliers.data?.data.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            <option value="draft,sent">Awaiting supplier</option>
            <option value="confirmed,partially_received">Awaiting delivery</option>
            {Object.entries(PO_STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        {can('edit_procurement') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New PO from award
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>PO no.</th>
              <th>Date</th>
              <th>Supplier</th>
              <th>Award</th>
              <th className="num">Amount</th>
              <th>Delivery due</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((p) => (
              <tr key={p.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(p.id)}>
                    {p.po_number}
                  </button>
                </td>
                <td>{formatDate(p.order_date)}</td>
                <td>{p.supplier.name}</td>
                <td className="mono">{p.source?.number ?? '—'}</td>
                <td className="num">{formatPeso(p.total_amount)}</td>
                <td>
                  {formatDate(p.delivery_date)} {p.late && <span className="badge st-bad">overdue</span>}
                </td>
                <td>
                  <Badge map={PO_STATUS} value={p.status} />
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No purchase orders found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {viewing !== null && (
        <PoDetailModal
          poId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(p) => {
            setViewing(null)
            setForm(p)
          }}
          onReceive={(p) => {
            setViewing(null)
            setReceiving(p)
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
      {form && (
        <PoFormModal
          po={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(p, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(p.id)
          }}
        />
      )}
      {receiving && (
        <IarFormModal
          poId={receiving.id}
          iar={null}
          onClose={() => setReceiving(null)}
          onSaved={(r) => {
            toast(`${r.iar_number} recorded; it now goes for acceptance.`, 'success')
            setReceiving(null)
            refresh()
          }}
        />
      )}
    </div>
  )
}

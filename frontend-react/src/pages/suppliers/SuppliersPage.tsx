import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { supplierApi } from '../../api/endpoints'
import type { Supplier } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { useDebounced } from '../../lib/hooks'
import { SupplierDetailModal } from './SupplierDetailModal'
import { SupplierFormModal } from './SupplierFormModal'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

export function SuppliersPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [filter, setFilter] = useState('active')
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<Supplier | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const key = `${filter}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const params = {
    page,
    search: q || undefined,
    active: filter === 'inactive' ? false : filter === 'all' ? undefined : true,
    eligible: filter === 'eligible' ? true : filter === 'unverified' ? false : undefined,
  }
  const list = useQuery({ queryKey: ['suppliers', params], queryFn: () => supplierApi.list(params), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['suppliers', 'supplier', 'supplier-options']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  const status = (s: Supplier) =>
    s.is_sanctioned ? <span className="badge st-bad">{s.sanction}</span> : !s.is_active ? <span className="badge badge-muted">Inactive</span> : s.awardable ? <span className="badge st-ok">Eligible</span> : <span className="badge st-pending">Not verified</span>
  return (
    <div className="page page-wide">
      <h1 className="page-title">Suppliers</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search name, code, TIN or contact" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search suppliers" />
          <select value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Filter">
            <option value="active">Active</option>
            <option value="eligible">Eligible</option>
            <option value="unverified">Not verified</option>
            <option value="inactive">Inactive</option>
            <option value="all">All</option>
          </select>
        </div>
        {can('create_suppliers') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + Register supplier
          </button>
        )}
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>TIN</th>
              <th>Contact</th>
              <th>Documents</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((s) => (
              <tr key={s.id}>
                <td className="mono">{s.vendor_code ?? '—'}</td>
                <td>
                  <button className="link-btn" onClick={() => setViewing(s.id)}>
                    {s.name}
                  </button>{' '}
                  {s.is_msme && <span className="badge st-partial">MSME</span>}
                </td>
                <td className="mono">{s.tin ?? '—'}</td>
                <td>{s.contact_person ?? s.email ?? '—'}</td>
                <td>
                  {s.eligibility.ready ? <span className="muted">complete</span> : <span className="small short">{s.eligibility.missing.length + s.eligibility.expired.length} to fix</span>}
                  {s.expiring_documents > 0 && <span className="badge st-pending">{s.expiring_documents} expiring</span>}
                </td>
                <td>{status(s)}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No suppliers found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {viewing !== null && (
        <SupplierDetailModal
          supplierId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(s) => {
            setViewing(null)
            setForm(s)
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
      {form && (
        <SupplierFormModal
          supplier={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(s, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(s.id)
          }}
        />
      )}
    </div>
  )
}

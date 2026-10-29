import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { contractApi } from '../../api/endpoints'
import type { Contract } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { ContractDetailModal } from './ContractDetailModal'
import { ContractFormModal } from './ContractFormModal'
import { CONTRACT_STATUS, CONTRACT_TYPES } from './labels'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

export function ContractsPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [expiring, setExpiring] = useState(false)
  const [search, setSearch] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [form, setForm] = useState<Contract | 'new' | null>(null)
  const q = useDebounced(search.trim())
  const key = `${status}|${type}|${expiring}|${q}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const list = useQuery({
    queryKey: ['contracts', page, status, type, expiring, q],
    queryFn: () => contractApi.list({ page, status: status || undefined, contract_type: type || undefined, expiring_days: expiring ? 30 : undefined, search: q || undefined }),
    placeholderData: keepPreviousData,
  })
  const refresh = () => {
    for (const k of ['contracts', 'contract', 'contract-sources']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <div className="page page-wide">
      <h1 className="page-title">Contracts</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search contract no. or title" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search contracts" />
          <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
            <option value="">All statuses</option>
            {Object.entries(CONTRACT_STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
          <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Type">
            <option value="">All types</option>
            {CONTRACT_TYPES.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
          <label className="checkbox-line">
            <input type="checkbox" checked={expiring} onChange={(e) => setExpiring(e.target.checked)} /> Ending within 30 days
          </label>
        </div>
        {can('create_contracts') && (
          <button className="btn btn-primary" onClick={() => setForm('new')}>
            + New contract
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
              <th>Supplier</th>
              <th>Type</th>
              <th className="num">Price</th>
              <th>Ends</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((c) => (
              <tr key={c.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(c.id)}>
                    {c.contract_number}
                  </button>
                </td>
                <td className="clip">{c.title}</td>
                <td>{c.supplier.name}</td>
                <td>{c.contract_type}</td>
                <td className="num">{formatPeso(c.amount)}</td>
                <td>
                  {formatDate(c.end_date)} {c.expiring && <span className="badge st-pending">{c.days_to_expiry}d</span>}
                  {c.expired && <span className="badge st-bad">past</span>}
                </td>
                <td>
                  <Badge map={CONTRACT_STATUS} value={c.status} />
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No contracts found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {viewing !== null && (
        <ContractDetailModal
          contractId={viewing}
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
        <ContractFormModal
          contract={form === 'new' ? null : form}
          onClose={() => setForm(null)}
          onSaved={(c, m) => {
            toast(m, 'success')
            setForm(null)
            refresh()
            setViewing(c.id)
          }}
        />
      )}
    </div>
  )
}

import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { semiExpendableApi } from '../../api/endpoints'
import type { SemiExpendableIssue } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatQty } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import '../inventory/inventory.css'
import './issuance.css'

const STATUS: Record<string, [string, string]> = {
  issued: ['With holder', 'st-pending'],
  partially_returned: ['Partly returned', 'st-partial'],
  returned: ['Returned', 'st-ok'],
}

/** Semi-expendable property under people's accountability (ICS), and returns. */
export function SemiExpendablePage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [outstanding, setOutstanding] = useState(true)
  const [search, setSearch] = useState('')
  const [returning, setReturning] = useState<SemiExpendableIssue | null>(null)
  const q = useDebounced(search.trim())
  const key = `${outstanding}|${q}`
  const [shownKey, setShownKey] = useState(key)
  if (shownKey !== key) {
    setShownKey(key)
    setPage(1)
  }

  const list = useQuery({
    queryKey: ['semi-expendable', page, outstanding, q],
    queryFn: () => semiExpendableApi.list({ page, outstanding, search: q || undefined }),
    placeholderData: keepPreviousData,
  })
  const canReturn = can('add_stock', 'manage_warehouse')

  return (
    <div className="page page-wide">
      <h1 className="page-title">Semi-Expendable Property</h1>
      <p className="muted" style={{ marginBottom: '1rem' }}>
        Semi-expendable items are expensed when issued but stay under the holder's accountability (ICS) until they are returned.
      </p>
      <div className="toolbar">
        <input type="search" placeholder="Search holder or ICS no." value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search" />
        <label className="checkbox-line">
          <input type="checkbox" checked={outstanding} onChange={(e) => setOutstanding(e.target.checked)} /> Only still with holders
        </label>
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ICS no.</th>
              <th>Issued</th>
              <th>Item</th>
              <th>Holder</th>
              <th>Office</th>
              <th className="num">Issued</th>
              <th className="num">Returned</th>
              <th className="num">Outstanding</th>
              <th>Status</th>
              {canReturn && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((s) => {
              const [label, cls] = STATUS[s.status] ?? [s.status, '']
              return (
                <tr key={s.id}>
                  <td className="mono">{s.ics_number ?? '—'}</td>
                  <td>{formatDate(s.issued_date)}</td>
                  <td>
                    <span className="mono">{s.item?.code}</span> {s.item?.name}
                  </td>
                  <td>{s.custodian_name ?? s.holder?.name}</td>
                  <td>{s.department?.name}</td>
                  <td className="num">{formatQty(s.quantity)}</td>
                  <td className="num">{formatQty(s.quantity_returned)}</td>
                  <td className="num">
                    <strong>{formatQty(s.outstanding)}</strong>
                  </td>
                  <td>
                    <span className={`badge ${cls}`}>{label}</span>
                  </td>
                  {canReturn && (
                    <td>
                      {s.outstanding > 0 && (
                        <button className="btn btn-outline btn-sm" onClick={() => setReturning(s)}>
                          Record return
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              )
            })}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={10} className="empty">
                  {outstanding ? 'Nothing is outstanding.' : 'No semi-expendable issues yet.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {returning && (
        <ReturnForm
          issue={returning}
          onClose={() => setReturning(null)}
          onSaved={(message) => {
            toast(message, 'success')
            setReturning(null)
            for (const k of ['semi-expendable', 'items', 'item', 'stock-card']) void queryClient.invalidateQueries({ queryKey: [k] })
          }}
        />
      )}
    </div>
  )
}

function ReturnForm({ issue, onClose, onSaved }: { issue: SemiExpendableIssue; onClose: () => void; onSaved: (m: string) => void }) {
  const [qty, setQty] = useState(String(issue.outstanding))
  const [restock, setRestock] = useState(true)
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => semiExpendableApi.recordReturn(issue.id, { quantity: Number(qty), restock, remarks }),
    onSuccess: () => onSaved(`Return of ${qty} ${issue.item?.name} recorded${restock ? ' and put back in stock' : ''}.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not record the return.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    save.mutate()
  }
  return (
    <Modal title={`Return — ${issue.item?.name}`} onClose={onClose} width={520}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <p className="muted" style={{ marginBottom: '1rem' }}>
          {issue.custodian_name ?? issue.holder?.name} · ICS {issue.ics_number ?? '—'} · {issue.outstanding} outstanding
        </p>
        <div className="field" style={{ marginBottom: '1rem', maxWidth: 200 }}>
          <label htmlFor="ret-qty">Quantity returned *</label>
          <input id="ret-qty" type="number" min={1} max={issue.outstanding} value={qty} onChange={(e) => setQty(e.target.value)} required />
        </div>
        <fieldset className="radio-group">
          <legend>Condition</legend>
          <label className="checkbox-line">
            <input type="radio" name="cond" checked={restock} onChange={() => setRestock(true)} /> Serviceable — put back in stock
          </label>
          <label className="checkbox-line">
            <input type="radio" name="cond" checked={!restock} onChange={() => setRestock(false)} /> Unserviceable — record for disposal
          </label>
        </fieldset>
        <div className="field" style={{ marginTop: '1rem' }}>
          <label htmlFor="ret-rem">Remarks</label>
          <input id="ret-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Record return'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

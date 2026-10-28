import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { assetsApi } from '../../api/endpoints'
import type { Asset } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { AssetDetailModal } from './AssetDetailModal'
import { MOVE_LABEL, STATUS_LABEL } from './labels'
import { Badge } from './pickers'
import { RegisterAssetModal } from './RegisterAssetModal'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import './assets.css'

type Tab = 'register' | 'movements' | 'depreciation'

export function AssetsPage() {
  const { can } = useAuth()
  const [tab, setTab] = useState<Tab>('register')
  const staff = can('view_properties')
  return (
    <div className="page page-wide">
      <h1 className="page-title">Assets &amp; Property</h1>
      {staff && (
        <div className="tabs">
          <button className={tab === 'register' ? 'active' : ''} onClick={() => setTab('register')}>
            Asset register
          </button>
          <button className={tab === 'movements' ? 'active' : ''} onClick={() => setTab('movements')}>
            Transfers &amp; returns
          </button>
          {can('edit_properties') && (
            <button className={tab === 'depreciation' ? 'active' : ''} onClick={() => setTab('depreciation')}>
              Depreciation
            </button>
          )}
        </div>
      )}
      {tab === 'register' && <Register />}
      {tab === 'movements' && <Movements />}
      {tab === 'depreciation' && <DepreciationRun />}
    </div>
  )
}

function Register() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [registering, setRegistering] = useState(false)
  const [deleting, setDeleting] = useState<Asset | null>(null)
  const q = useDebounced(search.trim())
  const key = `${q}|${status}`
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const list = useQuery({ queryKey: ['assets', page, q, status], queryFn: () => assetsApi.list({ page, search: q || undefined, status: status || undefined }), placeholderData: keepPreviousData })
  const refresh = () => {
    for (const k of ['assets', 'asset', 'items', 'item', 'lots', 'stock-card', 'my-assets']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  const remove = useMutation({
    mutationFn: (a: Asset) => assetsApi.remove(a.id),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setDeleting(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete.', 'error')
      setDeleting(null)
    },
  })

  return (
    <>
      <div className="section-header">
        <h2>Assets {list.data ? `(${list.data.pagination.total})` : ''}</h2>
        {can('create_properties', 'assign_properties') && (
          <button className="btn btn-primary" onClick={() => setRegistering(true)}>
            + Register Assets
          </button>
        )}
      </div>
      <div className="toolbar">
        <input type="search" placeholder="Search tag, serial, PAR/ICS no. or item" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search assets" />
        <select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Status">
          <option value="">All statuses</option>
          {Object.entries(STATUS_LABEL).map(([k, [label]]) => (
            <option key={k} value={k}>
              {label}
            </option>
          ))}
        </select>
      </div>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Tag no.</th>
              <th>Item</th>
              <th>Serial no.</th>
              <th>Accountable person</th>
              <th>PAR / ICS</th>
              <th className="num">Cost</th>
              <th className="num">Book value</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((a) => (
              <tr key={a.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(a.id)}>
                    {a.tag_number}
                  </button>
                </td>
                <td>
                  {a.item?.name} {a.brand && <span className="muted">· {a.brand}</span>}
                </td>
                <td>{a.serial_number ?? '—'}</td>
                <td>{a.holder?.name ?? <span className="muted">Property office</span>}</td>
                <td className="mono">{a.accountability_number ?? '—'}</td>
                <td className="num">{formatPeso(a.acquisition_cost)}</td>
                <td className="num">{a.is_depreciable ? formatPeso(a.book_value) : <span className="muted">expensed</span>}</td>
                <td>
                  <Badge map={STATUS_LABEL} value={a.status} />
                </td>
                <td>
                  {a.can_delete && (
                    <button className="icon-btn delete" title={`Undo registration of ${a.tag_number}`} onClick={() => setDeleting(a)}>
                      🗑
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={9} className="empty">
                  No assets found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />

      {viewing !== null && <AssetDetailModal assetId={viewing} onClose={() => setViewing(null)} onChanged={(m) => { toast(m, 'success'); refresh() }} />}
      {registering && (
        <RegisterAssetModal
          onClose={() => setRegistering(false)}
          onSaved={(m) => {
            toast(m, 'success')
            setRegistering(false)
            refresh()
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Undo registration"
          message={`Delete ${deleting.tag_number} and put the unit back in stock? Only possible for an asset with no transfers, gate passes, disposals or depreciation.`}
          confirmLabel="Delete asset"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

function Movements() {
  const [page, setPage] = useState(1)
  const [type, setType] = useState('')
  const [search, setSearch] = useState('')
  const q = useDebounced(search.trim())
  const list = useQuery({ queryKey: ['asset-moves', page, type, q], queryFn: () => assetsApi.moves(page, type || undefined, q || undefined), placeholderData: keepPreviousData })
  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search document no., tag no. or reason" value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} aria-label="Search movements" />
        <select value={type} onChange={(e) => { setType(e.target.value); setPage(1) }} aria-label="Type">
          <option value="">All</option>
          <option value="transfer">Transfers (PTR)</option>
          <option value="return">Returns</option>
          <option value="reissue">Issues</option>
        </select>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Date</th>
              <th>What</th>
              <th>Document</th>
              <th>Asset</th>
              <th>From</th>
              <th>To</th>
              <th>Reason</th>
              <th>Recorded by</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((m) => (
              <tr key={m.id}>
                <td>{formatDate(m.transfer_date)}</td>
                <td>{MOVE_LABEL[m.transfer_type]}</td>
                <td className="mono">{m.reference_number ?? '—'}</td>
                <td>
                  <span className="mono">{m.tag_number}</span> {m.item_name}
                </td>
                <td>{m.from?.name ?? 'Property office'}</td>
                <td>{m.to?.name ?? 'Property office'}</td>
                <td className="clip">{m.reason}</td>
                <td>{m.recorded_by}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No transfers or returns yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
    </>
  )
}

function DepreciationRun() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [period, setPeriod] = useState(() => monthOffset(-1))
  const [thisMonth] = useState(() => monthOffset(0))
  const run = useMutation({
    mutationFn: () => assetsApi.runDepreciation(period),
    onSuccess: (r) => {
      toast(r.message, 'success')
      for (const k of ['assets', 'asset', 'asset-dep']) void queryClient.invalidateQueries({ queryKey: [k] })
    },
    onError: (err) => toast(err instanceof ApiError ? err.message : 'Could not post depreciation.', 'error'),
  })
  return (
    <div className="dash-card" style={{ maxWidth: 640 }}>
      <h2>Post monthly depreciation</h2>
      <p className="muted">
        Straight-line, per COA: residual value 5% of cost unless set, starting the month after acquisition. Each month is posted once per
        asset, so missed months are caught up and running it again is harmless. Semi-expendable property is not depreciated.
      </p>
      <div className="toolbar" style={{ alignItems: 'flex-end' }}>
        <div className="field">
          <label htmlFor="dep-period">Post through</label>
          <input id="dep-period" type="month" value={period} max={thisMonth} onChange={(e) => setPeriod(e.target.value)} />
        </div>
        <button className="btn btn-primary" onClick={() => run.mutate()} disabled={run.isPending || !period}>
          {run.isPending ? 'Posting…' : 'Post depreciation'}
        </button>
      </div>
      {run.data && (
        <p>
          {run.data.months_posted} month(s) for {run.data.assets} asset(s), {formatPeso(run.data.amount_posted)} in total.
        </p>
      )}
    </div>
  )
}

/** YYYY-MM for the month `offset` months from now. */
function monthOffset(offset: number) {
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() + offset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

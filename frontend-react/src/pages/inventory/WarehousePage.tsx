import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi } from '../../api/endpoints'
import type { Item, StockLot } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso, formatQty, today } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { AddStockModal } from './AddStockModal'
import { StockLevelBadge } from './badges'
import { ItemDetailModal } from './ItemDetailModal'
import './inventory.css'

type Tab = 'overview' | 'received' | 'transfers' | 'expiring'

export function WarehousePage() {
  const [tab, setTab] = useState<Tab>('overview')
  const tabs: [Tab, string][] = [
    ['overview', 'Reorder & stock levels'],
    ['received', 'Stock received'],
    ['transfers', 'Transfers'],
    ['expiring', 'Expiring stock'],
  ]
  return (
    <div className="page page-wide">
      <h1 className="page-title">Warehouse / Stockroom</h1>
      <div className="tabs tabs-scroll" role="tablist">
        {tabs.map(([key, label]) => (
          <button key={key} role="tab" aria-selected={tab === key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>
            {label}
          </button>
        ))}
      </div>
      {tab === 'overview' && <Overview />}
      {tab === 'received' && <Received />}
      {tab === 'transfers' && <Transfers />}
      {tab === 'expiring' && <Expiring />}
    </div>
  )
}

/** Stock position: what needs reordering, with the reorder quantity up to the maximum. */
function Overview() {
  const [page, setPage] = useState(1)
  const [onlyReorder, setOnlyReorder] = useState(true)
  const [viewing, setViewing] = useState<number | null>(null)
  const items = useQuery({
    queryKey: ['items', 'warehouse', page, onlyReorder],
    queryFn: () => inventoryApi.items({ page, stock_level: onlyReorder ? 'attention' : undefined }),
    placeholderData: keepPreviousData,
  })
  const attention = useQuery({ queryKey: ['items', 'attention-count'], queryFn: () => inventoryApi.items({ page: 1, stock_level: 'attention' }) })
  const out = useQuery({ queryKey: ['items', 'out-count'], queryFn: () => inventoryApi.items({ page: 1, stock_level: 'out' }) })
  const all = useQuery({ queryKey: ['items', 'all-count'], queryFn: () => inventoryApi.items({ page: 1 }) })
  const expiring = useQuery({ queryKey: ['expiring', undefined], queryFn: () => inventoryApi.expiring() })

  const reorderQty = (i: Item) => (i.max_stock ? Math.max(0, i.max_stock - i.current_stock) : null)

  return (
    <>
      <div className="kpi-row">
        <Kpi label="Catalogue items" value={all.data?.pagination.total} />
        <Kpi label="Need reorder" value={attention.data?.pagination.total} tone="warn" />
        <Kpi label="Out of stock" value={out.data?.pagination.total} tone="bad" />
        <Kpi label="Lots expiring / expired" value={expiring.data ? expiring.data.summary.expiring_lots + expiring.data.summary.expired_lots : undefined} tone="warn" />
      </div>

      <div className="section-header">
        <h2>{onlyReorder ? 'Re-order report' : 'Stock position'}</h2>
        <div className="actions">
          <label className="checkbox-line">
            <input
              type="checkbox"
              checked={onlyReorder}
              onChange={(e) => {
                setOnlyReorder(e.target.checked)
                setPage(1)
              }}
            />
            Only items at or below the reorder point
          </label>
          <button className="btn btn-outline btn-sm" onClick={() => window.print()}>
            🖨 Print
          </button>
        </div>
      </div>

      <div className="table-wrap print-area">
        <table className="table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Item</th>
              <th className="num">On hand</th>
              <th className="num">Reorder pt</th>
              <th className="num">Max</th>
              <th className="num">Suggested order</th>
              <th className="num">Avg cost</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {items.data?.data.map((i) => (
              <tr key={i.id}>
                <td className="mono">{i.code}</td>
                <td>
                  <button className="link-btn" onClick={() => setViewing(i.id)}>
                    {i.name}
                  </button>
                </td>
                <td className="num">
                  {formatQty(i.current_stock)} <span className="muted">{i.unit_of_measure}</span>
                </td>
                <td className="num">{formatQty(i.min_stock)}</td>
                <td className="num">{i.max_stock != null ? formatQty(i.max_stock) : '—'}</td>
                <td className="num">{reorderQty(i) != null ? formatQty(reorderQty(i)) : <span className="muted">set a max</span>}</td>
                <td className="num">{formatPeso(i.weighted_average_cost)}</td>
                <td>
                  <StockLevelBadge level={i.stock_status} />
                </td>
              </tr>
            ))}
            {items.data && items.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  {onlyReorder ? 'Nothing needs reordering.' : 'No items yet.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={items.data?.pagination} onPage={setPage} />
      {viewing !== null && <ItemDetailModal itemId={viewing} onClose={() => setViewing(null)} />}
    </>
  )
}

function Kpi({ label, value, tone }: { label: string; value?: number; tone?: 'warn' | 'bad' }) {
  return (
    <div className={'kpi' + (tone && value ? ` kpi-${tone}` : '')}>
      <span className="kpi-value">{value == null ? '…' : formatQty(value)}</span>
      <span className="kpi-label">{label}</span>
    </div>
  )
}

/** Receipt history, and the entry point for recording a new receipt. */
function Received() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [pick, setPick] = useState('')
  const [stocking, setStocking] = useState<Item | null>(null)
  const q = useDebounced(pick.trim())
  const entries = useQuery({ queryKey: ['stock-entries', page], queryFn: () => inventoryApi.stockEntries(page), placeholderData: keepPreviousData })
  const options = useQuery({ queryKey: ['item-options', q], queryFn: () => inventoryApi.itemOptions(q || undefined), enabled: q.length > 0 })
  const canStock = can('add_stock', 'manage_warehouse')

  return (
    <>
      {canStock && (
        <div className="receive-picker">
          <label htmlFor="rcv-item">Record stock received for</label>
          <input id="rcv-item" type="search" placeholder="Type an item code or name…" value={pick} onChange={(e) => setPick(e.target.value)} autoComplete="off" />
          {q && (
            <ul className="picker-results">
              {options.data?.slice(0, 8).map((i) => (
                <li key={i.id}>
                  <button
                    onClick={() => {
                      setStocking(i)
                      setPick('')
                    }}
                  >
                    <span className="mono">{i.code}</span> {i.name} <span className="muted">· {formatQty(i.current_stock)} {i.unit_of_measure}</span>
                  </button>
                </li>
              ))}
              {options.data?.length === 0 && <li className="muted">No matching items.</li>}
            </ul>
          )}
        </div>
      )}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Received</th>
              <th>Item</th>
              <th>Brand</th>
              <th>Location</th>
              <th className="num">Qty now</th>
              <th className="num">Unit cost</th>
              <th>Lot no.</th>
              <th>Expires</th>
              <th>Supplier</th>
            </tr>
          </thead>
          <tbody>
            {entries.data?.data.map((s) => (
              <tr key={s.id}>
                <td>{formatDate(s.received_date)}</td>
                <td>
                  <span className="mono">{s.item?.code}</span> {s.item?.name}
                </td>
                <td>{s.brand_name ?? '—'}</td>
                <td>{s.location ?? '—'}</td>
                <td className="num">{formatQty(s.quantity)}</td>
                <td className="num">{formatPeso(s.unit_cost)}</td>
                <td>{s.lot_number ?? '—'}</td>
                <td>{formatDate(s.expiration_date)}</td>
                <td>{s.supplier?.name ?? '—'}</td>
              </tr>
            ))}
            {entries.data && entries.data.data.length === 0 && (
              <tr>
                <td colSpan={9} className="empty">
                  No stock received yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={entries.data?.pagination} onPage={setPage} />

      {stocking && (
        <AddStockModal
          item={stocking}
          onClose={() => setStocking(null)}
          onSaved={(message) => {
            toast(message, 'success')
            setStocking(null)
            void queryClient.invalidateQueries({ queryKey: ['stock-entries'] })
            void queryClient.invalidateQueries({ queryKey: ['items'] })
            void queryClient.invalidateQueries({ queryKey: ['locations'] })
          }}
        />
      )}
    </>
  )
}

/** Move stock between locations (warehouse to warehouse, or to a department stockroom). */
function Transfers() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [filter, setFilter] = useState('')
  const [pick, setPick] = useState('')
  const [item, setItem] = useState<Item | null>(null)
  const [lotId, setLotId] = useState('')
  const [to, setTo] = useState('')
  const [qty, setQty] = useState('')
  const [date, setDate] = useState(today())
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const q = useDebounced(pick.trim())
  const f = useDebounced(filter.trim())

  const history = useQuery({ queryKey: ['transfers', page, f], queryFn: () => inventoryApi.transfers(page, f || undefined), placeholderData: keepPreviousData })
  const options = useQuery({ queryKey: ['item-options', q], queryFn: () => inventoryApi.itemOptions(q || undefined), enabled: q.length > 0 && !item })
  const lots = useQuery({ queryKey: ['lots', item?.id], queryFn: () => inventoryApi.lots(item!.id), enabled: !!item })
  const locations = useQuery({ queryKey: ['locations'], queryFn: inventoryApi.locations })
  const lot: StockLot | undefined = lots.data?.find((l) => String(l.id) === lotId)

  const move = useMutation({
    mutationFn: () => inventoryApi.transfer({ stock_id: Number(lotId), to_location: to, quantity: Number(qty), transfer_date: date, remarks }),
    onSuccess: (res) => {
      toast(`${res.transfer.transfer_number}: moved ${formatQty(res.transfer.quantity)} to ${res.transfer.to_location}.`, 'success')
      setLotId('')
      setQty('')
      setRemarks('')
      setTo('')
      void queryClient.invalidateQueries({ queryKey: ['transfers'] })
      void queryClient.invalidateQueries({ queryKey: ['lots'] })
      void queryClient.invalidateQueries({ queryKey: ['locations'] })
      void queryClient.invalidateQueries({ queryKey: ['item'] })
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not transfer the stock.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    move.mutate()
  }

  return (
    <>
      {can('add_stock', 'manage_warehouse') && (
        <form className="transfer-form" onSubmit={submit}>
          <h2>Transfer stock between locations</h2>
          {error && <div className="alert alert-error">{error}</div>}
          <div className="form-grid three">
            <div className="field span-2 picker-field">
              <label htmlFor="tr-item">Item *</label>
              {item ? (
                <div className="chosen">
                  <span>
                    <span className="mono">{item.code}</span> {item.name}
                  </span>
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    onClick={() => {
                      setItem(null)
                      setLotId('')
                    }}
                  >
                    Change
                  </button>
                </div>
              ) : (
                <>
                  <input id="tr-item" type="search" placeholder="Type an item code or name…" value={pick} onChange={(e) => setPick(e.target.value)} autoComplete="off" />
                  {q && (
                    <ul className="picker-results">
                      {options.data?.slice(0, 8).map((i) => (
                        <li key={i.id}>
                          <button
                            type="button"
                            onClick={() => {
                              setItem(i)
                              setPick('')
                            }}
                          >
                            <span className="mono">{i.code}</span> {i.name}
                          </button>
                        </li>
                      ))}
                      {options.data?.length === 0 && <li className="muted">No matching items.</li>}
                    </ul>
                  )}
                </>
              )}
            </div>
            <div className="field">
              <label htmlFor="tr-date">Transfer date</label>
              <input id="tr-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div className="field span-2">
              <label htmlFor="tr-lot">From lot / location *</label>
              <select id="tr-lot" value={lotId} onChange={(e) => setLotId(e.target.value)} required disabled={!item}>
                <option value="">{item ? (lots.data?.length ? 'Choose a lot' : 'No stock on hand') : 'Choose an item first'}</option>
                {lots.data?.map((l) => (
                  <option key={l.id} value={l.id}>
                    {(l.location ?? '(no location)') + ' — ' + (l.brand_name ?? 'no brand') + ` — ${formatQty(l.quantity)} @ ${formatPeso(l.unit_cost)}` + (l.lot_number ? ` — lot ${l.lot_number}` : '')}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="tr-qty">Quantity *</label>
              <input id="tr-qty" type="number" min={1} max={lot?.quantity} value={qty} onChange={(e) => setQty(e.target.value)} required />
              {lot && <span className="hint">Up to {formatQty(lot.quantity)}</span>}
            </div>
            <div className="field">
              <label htmlFor="tr-to">To location *</label>
              <input id="tr-to" value={to} onChange={(e) => setTo(e.target.value)} list="tr-locs" required placeholder="e.g. Supply Room 2" />
              <datalist id="tr-locs">
                {locations.data?.filter((l) => l !== lot?.location).map((l) => (
                  <option key={l} value={l} />
                ))}
              </datalist>
            </div>
            <div className="field span-2">
              <label htmlFor="tr-rem">Remarks</label>
              <input id="tr-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
            </div>
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={move.isPending || !lotId}>
              {move.isPending ? 'Transferring…' : 'Transfer'}
            </button>
          </div>
        </form>
      )}

      <div className="section-header">
        <h2>Transfer history</h2>
        <input className="filter-input" type="search" placeholder="Filter by location" value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Filter transfers by location" />
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>No.</th>
              <th>Date</th>
              <th>Item</th>
              <th>From</th>
              <th>To</th>
              <th className="num">Qty</th>
              <th>By</th>
              <th>Remarks</th>
            </tr>
          </thead>
          <tbody>
            {history.data?.data.map((t) => (
              <tr key={t.id}>
                <td className="mono">{t.transfer_number}</td>
                <td>{formatDate(t.transfer_date)}</td>
                <td>
                  <span className="mono">{t.item?.code}</span> {t.item?.name}
                </td>
                <td>{t.from_location ?? '—'}</td>
                <td>{t.to_location}</td>
                <td className="num">{formatQty(t.quantity)}</td>
                <td>{t.transferred_by ?? '—'}</td>
                <td>{t.remarks ?? ''}</td>
              </tr>
            ))}
            {history.data && history.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  No transfers yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={history.data?.pagination} onPage={setPage} />
    </>
  )
}

/** Expired lots (for disposal) and lots expiring soon (issue these first). */
function Expiring() {
  const [days, setDays] = useState('')
  const d = useDebounced(days)
  const data = useQuery({ queryKey: ['expiring', d || undefined], queryFn: () => inventoryApi.expiring(d ? Number(d) : undefined) })
  const s = data.data?.summary

  const table = (rows: StockLot[], empty: string) => (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Item</th>
            <th>Lot no.</th>
            <th>Location</th>
            <th className="num">Qty</th>
            <th className="num">Value</th>
            <th>Expires</th>
            <th className="num">Days</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((l) => (
            <tr key={l.id}>
              <td>
                <span className="mono">{l.item?.code}</span> {l.item?.name}
              </td>
              <td>{l.lot_number ?? '—'}</td>
              <td>{l.location ?? '—'}</td>
              <td className="num">
                {formatQty(l.quantity)} <span className="muted">{l.item?.unit_of_measure}</span>
              </td>
              <td className="num">{formatPeso(l.total_value)}</td>
              <td>{formatDate(l.expiration_date)}</td>
              <td className="num">{l.days_until_expiration}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={7} className="empty">
                {empty}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )

  return (
    <>
      <div className="toolbar">
        <label className="inline-field">
          Warn within
          <input type="number" min={1} max={365} value={days} onChange={(e) => setDays(e.target.value)} placeholder={String(data.data?.warning_days ?? 30)} />
          days
        </label>
      </div>
      {data.isError && <div className="alert alert-error">{(data.error as Error).message}</div>}
      <div className="section-header">
        <h2>Expired — for disposal {s ? `(${s.expired_lots} lots, ${formatPeso(s.expired_value)})` : ''}</h2>
      </div>
      {data.data && table(data.data.expired, 'No expired stock.')}
      <div className="section-header" style={{ marginTop: '1.5rem' }}>
        <h2>
          Expiring within {data.data?.warning_days ?? '…'} days {s ? `(${s.expiring_lots} lots, ${formatPeso(s.expiring_value)})` : ''}
        </h2>
      </div>
      {data.data && table(data.data.expiring_soon, 'Nothing expires in this window.')}
    </>
  )
}

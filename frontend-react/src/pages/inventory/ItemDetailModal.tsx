import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { inventoryApi } from '../../api/endpoints'
import { Modal } from '../../components/Modal'
import { formatDate, formatPeso, formatQty } from '../../lib/format'
import { ClassificationBadge, StockLevelBadge } from './badges'
import { ExcelButton } from '../../components/ExcelButton'
import { photosApi } from '../../api/tor'
import { useAuth } from '../../auth/AuthContext'
import { PhotoPanel } from '../assets/AssetExtras'
import '../assets/assets.css'

const WARRANTY: Record<string, string> = { active: 'Active', expiring: 'Expiring', expired: 'Expired', none: '—' }

export function ItemDetailModal({ itemId, onClose }: { itemId: number; onClose: () => void }) {
  const [tab, setTab] = useState<'details' | 'card'>('details')
  const item = useQuery({ queryKey: ['item', itemId], queryFn: () => inventoryApi.item(itemId) })
  const card = useQuery({ queryKey: ['stock-card', itemId], queryFn: () => inventoryApi.stockCard(itemId), enabled: tab === 'card' })
  const it = item.data
  const { can } = useAuth()

  return (
    <Modal title={it ? `${it.code} — ${it.name}` : 'Item'} onClose={onClose} width={980}>
      {item.isPending && <div className="page-loading">Loading…</div>}
      {item.isError && <div className="alert alert-error">{(item.error as Error).message}</div>}
      {it && (
        <>
          <div className="tabs">
            <button className={tab === 'details' ? 'active' : ''} onClick={() => setTab('details')}>
              Details &amp; lots
            </button>
            <button className={tab === 'card' ? 'active' : ''} onClick={() => setTab('card')}>
              Stock card
            </button>
          </div>

          {tab === 'details' && (
            <>
              <div className="asset-details">
              <PhotoPanel
                path={photosApi.item(it.id)}
                hasPhoto={it.has_photo}
                canEdit={can('edit_inventory')}
                label={it.name}
                onUpload={async (f) => { await photosApi.uploadItem(it.id, f); await item.refetch() }}
                onRemove={async () => { await photosApi.removeItem(it.id); await item.refetch() }}
              />
              <dl className="detail-grid">
                <dt>Category</dt>
                <dd>{it.category?.name ?? '—'}</dd>
                <dt>Classification</dt>
                <dd>
                  <ClassificationBadge value={it.classification} label={it.classification_label} />
                  {it.is_assembly && <span className="badge badge-muted" style={{ marginLeft: 6 }}>Assembly</span>}
                </dd>
                <dt>On hand</dt>
                <dd>
                  {formatQty(it.current_stock)} {it.unit_of_measure} <StockLevelBadge level={it.stock_status} />
                </dd>
                <dt>Reorder point / max</dt>
                <dd>
                  {formatQty(it.min_stock)} / {it.max_stock != null ? formatQty(it.max_stock) : 'no maximum'}
                </dd>
                <dt>Weighted avg. cost</dt>
                <dd>{formatPeso(it.weighted_average_cost)}</dd>
                <dt>Inventory value</dt>
                <dd>{formatPeso(it.total_value)}</dd>
                {it.description && (
                  <>
                    <dt>Description</dt>
                    <dd>{it.description}</dd>
                  </>
                )}
              </dl>
              </div>

              <h4 style={{ margin: '1.25rem 0 0.6rem' }}>Stock lots</h4>
              <div className="table-wrap">
                <table className="table table-compact">
                  <thead>
                    <tr>
                      <th>Received</th>
                      <th>Brand</th>
                      <th>Location</th>
                      <th className="num">Qty</th>
                      <th className="num">Unit cost</th>
                      <th>Lot no.</th>
                      <th>Expires</th>
                      <th>Warranty</th>
                      <th>Supplier</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(it.stocks ?? []).map((s) => (
                      <tr key={s.id} className={s.quantity === 0 ? 'row-muted' : ''}>
                        <td>{formatDate(s.received_date)}</td>
                        <td>{s.brand_name ?? '—'}</td>
                        <td>{s.location ?? '—'}</td>
                        <td className="num">{formatQty(s.quantity)}</td>
                        <td className="num">{formatPeso(s.unit_cost)}</td>
                        <td>{s.lot_number ?? '—'}</td>
                        <td>{formatDate(s.expiration_date)}</td>
                        <td>{s.warranty_expiry ? `${WARRANTY[s.warranty_status]} (${formatDate(s.warranty_expiry)})` : '—'}</td>
                        <td>{s.supplier?.name ?? '—'}</td>
                      </tr>
                    ))}
                    {(it.stocks ?? []).length === 0 && (
                      <tr>
                        <td colSpan={9} className="empty">
                          No stock has been received for this item.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {tab === 'card' && (
            <div className="print-area">
              <div className="stock-card-head">
                <div>
                  <h3>Stock Card</h3>
                  <p className="muted">
                    {it.code} — {it.name} · Unit: {it.unit_of_measure} · Reorder point: {formatQty(it.min_stock)}
                  </p>
                </div>
                <span className="no-print">
                  <ExcelButton path={`/reports/stock-card/${it.id}/export`} name={`StockCard_${it.code}.xlsx`} label="Stock Card (Excel)" />{' '}
                  <button className="btn btn-outline btn-sm" onClick={() => window.print()}>
                    🖨 Print
                  </button>
                </span>
              </div>
              {card.isPending && <div className="page-loading">Loading…</div>}
              {card.data && (
                <div className="table-wrap">
                  <table className="table table-compact">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Reference</th>
                        <th className="num">Receipt</th>
                        <th className="num">Issue</th>
                        <th>Office</th>
                        <th className="num">Balance</th>
                      </tr>
                    </thead>
                    <tbody>
                      {card.data.rows.map((r, i) => (
                        <tr key={i}>
                          <td>{formatDate(r.date)}</td>
                          <td>{r.reference}</td>
                          <td className="num">{r.receipt != null ? formatQty(r.receipt) : ''}</td>
                          <td className="num">{r.issue != null ? formatQty(r.issue) : ''}</td>
                          <td>{r.office ?? ''}</td>
                          <td className="num">
                            <strong>{formatQty(r.balance)}</strong>
                          </td>
                        </tr>
                      ))}
                      {card.data.rows.length === 0 && (
                        <tr>
                          <td colSpan={6} className="empty">
                            No movements recorded yet.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </Modal>
  )
}

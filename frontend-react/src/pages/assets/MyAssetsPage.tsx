import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { assetsApi } from '../../api/endpoints'
import { formatDate, formatPeso } from '../../lib/format'
import { AssetDetailModal } from './AssetDetailModal'
import { STATUS_LABEL } from './labels'
import { Badge } from './pickers'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import './assets.css'

/** Everything the signed-in person is accountable for (their PAR / ICS items). */
export function MyAssetsPage() {
  const mine = useQuery({ queryKey: ['my-assets'], queryFn: assetsApi.mine })
  const [viewing, setViewing] = useState<number | null>(null)
  return (
    <div className="page page-wide">
      <h1 className="page-title">My Assets</h1>
      {mine.data && (
        <div className="kpi-row">
          <div className="kpi">
            <span className="kpi-value">{mine.data.summary.total}</span>
            <span className="kpi-label">Items under my accountability</span>
          </div>
          <div className="kpi">
            <span className="kpi-value">{formatPeso(mine.data.summary.total_value)}</span>
            <span className="kpi-label">Total acquisition cost</span>
          </div>
        </div>
      )}
      {mine.isError && <div className="alert alert-error">{(mine.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Tag no.</th>
              <th>Item</th>
              <th>Serial no.</th>
              <th>PAR / ICS</th>
              <th>Since</th>
              <th className="num">Cost</th>
              <th>Condition</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {mine.data?.data.map((a) => (
              <tr key={a.id}>
                <td>
                  <button className="link-btn mono" onClick={() => setViewing(a.id)}>
                    {a.tag_number}
                  </button>
                </td>
                <td>{a.item?.name}</td>
                <td>{a.serial_number ?? '—'}</td>
                <td className="mono">{a.accountability_number ?? '—'}</td>
                <td>{formatDate(a.assigned_date)}</td>
                <td className="num">{formatPeso(a.acquisition_cost)}</td>
                <td>{a.condition}</td>
                <td>
                  <Badge map={STATUS_LABEL} value={a.status} />
                </td>
              </tr>
            ))}
            {mine.data && mine.data.data.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  Nothing is issued to you.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {viewing !== null && <AssetDetailModal assetId={viewing} onClose={() => setViewing(null)} onChanged={() => mine.refetch()} />}
    </div>
  )
}

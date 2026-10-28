import type { InventoryConfig, PurchaseRequest } from '../../api/types'
import { formatDate, formatPeso, formatQty } from '../../lib/format'

/** GPPB / COA Purchase Request form (Appendix 60). */
export function PrSlip({ pr, config }: { pr: PurchaseRequest; config?: InventoryConfig }) {
  const signer = (perm: string) => pr.approvals?.find((a) => a.required_permission === perm && a.status === 'approved')?.actor?.name ?? ''
  return (
    <div className="slip print-area">
      <p className="slip-appendix">Appendix 60</p>
      <h2 className="slip-title">PURCHASE REQUEST</h2>
      <div className="slip-head">
        <span>
          Entity Name: <strong>{config?.agency_name || '________________'}</strong>
        </span>
        <span>
          Fund Cluster: <strong>{config?.fund_cluster || '________'}</strong>
        </span>
        <span>
          Office/Section: <strong>{pr.department.name}</strong>
        </span>
        <span>
          PR No.: <strong>{pr.pr_number}</strong>
        </span>
        <span>
          Date: <strong>{formatDate(pr.request_date)}</strong>
        </span>
        {pr.caf_number && (
          <span>
            CAF No.: <strong>{pr.caf_number}</strong>
          </span>
        )}
      </div>
      <table className="slip-table">
        <thead>
          <tr>
            <th>Stock / Property No.</th>
            <th>Unit</th>
            <th>Item Description</th>
            <th>Quantity</th>
            <th>Unit Cost</th>
            <th>Total Cost</th>
          </tr>
        </thead>
        <tbody>
          {pr.items?.map((l, i) => (
            <tr key={l.id}>
              <td>{i + 1}</td>
              <td>{l.unit}</td>
              <td>
                {l.general_description}
                {l.specifications && <div className="small">{l.specifications}</div>}
              </td>
              <td className="num">{formatQty(l.quantity)}</td>
              <td className="num">{formatPeso(l.estimated_unit_cost)}</td>
              <td className="num">{formatPeso(l.estimated_total)}</td>
            </tr>
          ))}
          <tr>
            <td colSpan={5} className="num">
              <strong>Total</strong>
            </td>
            <td className="num">
              <strong>{formatPeso(pr.total_amount)}</strong>
            </td>
          </tr>
        </tbody>
      </table>
      <p className="slip-purpose">
        Purpose: <strong>{pr.purpose}</strong>
      </p>
      <div className="slip-signatures">
        {[
          ['Requested by', pr.requester.name ?? ''],
          ['Approved by', signer('pr_approve_department')],
        ].map(([label, name]) => (
          <div key={label} className="sig">
            <span className="sig-label">{label}:</span>
            <span className="sig-line">{name}</span>
            <span className="sig-caption">Signature over printed name / Designation</span>
          </div>
        ))}
      </div>
    </div>
  )
}

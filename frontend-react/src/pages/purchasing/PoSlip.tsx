import type { InventoryConfig, PurchaseOrder } from '../../api/types'
import { formatDate, formatPeso, formatQty } from '../../lib/format'

/** Purchase Order (COA Appendix 61). */
export function PoSlip({ po, config }: { po: PurchaseOrder; config?: InventoryConfig }) {
  return (
    <div className="slip print-area">
      <p className="slip-appendix">Appendix 61</p>
      <h2 className="slip-title">PURCHASE ORDER</h2>
      <div className="slip-head">
        <span>
          Entity Name: <strong>{config?.agency_name || '________________'}</strong>
        </span>
        <span>
          Fund Cluster: <strong>{config?.fund_cluster || '________'}</strong>
        </span>
        <span>
          Supplier: <strong>{po.supplier.name}</strong>
        </span>
        <span>
          P.O. No.: <strong>{po.po_number}</strong>
        </span>
        <span>
          Date: <strong>{formatDate(po.order_date)}</strong>
        </span>
        <span>
          Mode of Procurement: <strong>{po.source?.mode ?? "—"}</strong>
        </span>
        <span>
          Place of Delivery: <strong>{po.delivery_address ?? '—'}</strong>
        </span>
        <span>
          Date of Delivery: <strong>{formatDate(po.delivery_date)}</strong>
        </span>
      </div>
      <table className="slip-table">
        <thead>
          <tr>
            <th>Stock No.</th>
            <th>Unit</th>
            <th>Description</th>
            <th>Quantity</th>
            <th>Unit Cost</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          {po.items?.map((l, i) => (
            <tr key={l.id}>
              <td>{i + 1}</td>
              <td>{l.unit}</td>
              <td>
                {l.description}
                {l.specifications && <div className="small">{l.specifications}</div>}
              </td>
              <td className="num">{formatQty(l.quantity)}</td>
              <td className="num">{formatPeso(l.unit_cost)}</td>
              <td className="num">{formatPeso(l.total_cost)}</td>
            </tr>
          ))}
          <tr>
            <td colSpan={5} className="num">
              <strong>Total</strong>
            </td>
            <td className="num">
              <strong>{formatPeso(po.total_amount)}</strong>
            </td>
          </tr>
        </tbody>
      </table>
      <p className="slip-purpose">
        In case of failure to make full delivery within the time specified above, a penalty of one-tenth (1/10) of one percent for every day of delay shall be
        imposed on the undelivered item/s. Fund source: <strong>{po.fund_source ?? '—'}</strong>
        {po.caf_number && (
          <>
            {' '}
            · CAF No. <strong>{po.caf_number}</strong>
          </>
        )}
      </p>
      <div className="slip-signatures">
        {[
          ['Conforme', 'Signature over printed name of supplier / Date'],
          ['Very truly yours', po.approver?.name ?? 'Authorized official'],
        ].map(([label, caption]) => (
          <div key={label} className="sig">
            <span className="sig-label">{label}:</span>
            <span className="sig-line"></span>
            <span className="sig-caption">{caption}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

import type { InspectionReport, InventoryConfig } from '../../api/types'
import { formatDate, formatQty } from '../../lib/format'

/** Inspection and Acceptance Report (COA Appendix 62). */
export function IarSlip({ r, config }: { r: InspectionReport; config?: InventoryConfig }) {
  return (
    <div className="slip print-area">
      <p className="slip-appendix">Appendix 62</p>
      <h2 className="slip-title">INSPECTION AND ACCEPTANCE REPORT</h2>
      <div className="slip-head">
        <span>
          Entity Name: <strong>{config?.agency_name || '________________'}</strong>
        </span>
        <span>
          Fund Cluster: <strong>{config?.fund_cluster || '________'}</strong>
        </span>
        <span>
          Supplier: <strong>{r.supplier?.name}</strong>
        </span>
        <span>
          IAR No.: <strong>{r.iar_number}</strong>
        </span>
        <span>
          PO No./Date: <strong>{r.purchase_order?.name}</strong>
        </span>
        <span>
          Requisitioning Office: <strong>{r.department?.name ?? '—'}</strong>
        </span>
      </div>
      <table className="slip-table">
        <thead>
          <tr>
            <th>Stock / Property No.</th>
            <th>Description</th>
            <th>Unit</th>
            <th>Quantity</th>
          </tr>
        </thead>
        <tbody>
          {r.items?.map((l) => (
            <tr key={l.id}>
              <td>{l.item?.code ?? ''}</td>
              <td>
                {l.description}
                {l.quantity_rejected > 0 && <div className="small">{l.quantity_rejected} rejected{l.remarks ? `: ${l.remarks}` : ''}</div>}
              </td>
              <td>{l.unit}</td>
              <td className="num">{formatQty(l.quantity_accepted)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="slip-signatures">
        <div className="sig">
          <span className="sig-label">INSPECTION — Date inspected: {formatDate(r.inspection_date)}</span>
          <span className="small">☑ Inspected, verified and found in order as to quantity and specifications</span>
          <span className="sig-line">{r.inspector.name}</span>
          <span className="sig-caption">Inspection Officer / Inspection Committee</span>
        </div>
        <div className="sig">
          <span className="sig-label">ACCEPTANCE — Date received: {formatDate(r.delivery_date)}</span>
          <span className="small">{r.acceptance_status === 'accepted' ? '☑ Complete' : r.acceptance_status === 'partially_accepted' ? '☑ Partial' : '☐ Complete ☐ Partial'}</span>
          <span className="sig-line">{r.accepter?.name ?? ''}</span>
          <span className="sig-caption">Supply and/or Property Custodian</span>
        </div>
      </div>
    </div>
  )
}

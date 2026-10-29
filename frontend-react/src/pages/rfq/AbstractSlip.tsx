import type { InventoryConfig, RfqAbstract } from '../../api/types'
import { formatDate, formatPeso, formatQty } from '../../lib/format'

/** Abstract of Quotations, as the BAC signs it. */
export function AbstractSlip({ a, config }: { a: RfqAbstract; config?: InventoryConfig }) {
  const r = a.rfq
  return (
    <div className="slip print-area">
      <h2 className="slip-title">ABSTRACT OF QUOTATIONS</h2>
      <div className="slip-head">
        <span>
          Entity Name: <strong>{config?.agency_name || '________________'}</strong>
        </span>
        <span>
          RFQ No.: <strong>{r.rfq_number}</strong>
        </span>
        <span>
          PR No.: <strong>{r.purchase_request?.name}</strong>
        </span>
        <span>
          Mode: <strong>{r.procurement_method}</strong>
        </span>
        <span>
          ABC: <strong>{formatPeso(r.abc)}</strong>
        </span>
      </div>
      <table className="slip-table">
        <thead>
          <tr>
            <th>Item</th>
            <th>Qty</th>
            <th>ABC / unit</th>
            {a.suppliers.map((s) => (
              <th key={s.id}>{s.supplier.name}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {a.rows.map((row) => (
            <tr key={row.rfq_item_id}>
              <td>{row.general_description}</td>
              <td className="num">
                {formatQty(row.quantity)} {row.unit}
              </td>
              <td className="num">{formatPeso(row.abc_unit_cost)}</td>
              {a.suppliers.map((s) => {
                const p = row.prices[String(s.id)]
                return (
                  <td key={s.id} className="num">
                    {p == null ? '—' : formatPeso(p)}
                    {p != null && p === row.lowest_unit_price ? ' *' : ''}
                  </td>
                )
              })}
            </tr>
          ))}
          <tr>
            <td colSpan={3}>
              <strong>Total quoted</strong>
            </td>
            {a.suppliers.map((s) => (
              <td key={s.id} className="num">
                <strong>{formatPeso(s.total_amount)}</strong>
                {!s.is_responsive && <div className="small">Non-responsive</div>}
                {s.over_abc && <div className="small">Over ABC</div>}
              </td>
            ))}
          </tr>
        </tbody>
      </table>
      <p className="slip-purpose">
        {r.awarded_supplier ? (
          <>
            Awarded to <strong>{r.awarded_supplier.name}</strong> on {formatDate(r.awarded_at)} ({r.bac_resolution?.name}).
          </>
        ) : a.recommended ? (
          <>
            Lowest calculated responsive quotation within the ABC: <strong>{a.recommended.supplier.name}</strong> at {formatPeso(a.recommended.total_amount)}.
          </>
        ) : (
          'No responsive quotation within the ABC.'
        )}{' '}
        * lowest unit price per item.
      </p>
      <div className="slip-signatures">
        {['BAC Member', 'BAC Member', 'BAC Vice-Chairperson', 'BAC Chairperson'].map((label, i) => (
          <div key={i} className="sig">
            <span className="sig-line"></span>
            <span className="sig-caption">{label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

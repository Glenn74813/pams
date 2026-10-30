import type { InventoryConfig, SupplyRequest } from '../../api/types'
import { formatDate, formatPeso, formatQty } from '../../lib/format'
import { SlipLogos } from '../../components/SlipLogos'

export type SlipKind = 'ris' | 'ics' | 'par'

const TITLE: Record<SlipKind, string> = {
  ris: 'REQUISITION AND ISSUE SLIP',
  ics: 'INVENTORY CUSTODIAN SLIP',
  par: 'PROPERTY ACKNOWLEDGMENT RECEIPT',
}
const APPENDIX: Record<SlipKind, string> = { ris: 'Appendix 63', ics: 'Appendix 59', par: 'Appendix 71' }

/**
 * Printable COA slip for an issued request. RIS lists every line; ICS only semi-expendable lines;
 * PAR only PPE lines. Unit costs are those at the time of issue.
 */
export function PrintSlip({ request: r, kind, config }: { request: SupplyRequest; kind: SlipKind; config?: InventoryConfig }) {
  const lines = r.items.filter((l) =>
    kind === 'ris' ? true : kind === 'ics' ? l.classification === 'semi_expendable' && l.approved_quantity > 0 : l.classification === 'ppe' && l.approved_quantity > 0,
  )
  const number = kind === 'ris' ? r.ris_number : kind === 'ics' ? r.ics_number : r.par_number
  const date = kind === 'ris' ? r.ris_date : kind === 'ics' ? r.ics_date : r.par_date
  const holder = r.custodian_name || r.requester?.name || ''

  return (
    <div className="slip print-area">
      <SlipLogos edge="top" />
      <p className="slip-appendix">{APPENDIX[kind]}</p>
      <h2 className="slip-title">{TITLE[kind]}</h2>
      <div className="slip-head">
        <span>
          Entity Name: <strong>{config?.agency_name || '________________'}</strong>
        </span>
        <span>
          Fund Cluster: <strong>{config?.fund_cluster || '________'}</strong>
        </span>
        {kind === 'ris' && (
          <span>
            Office: <strong>{r.department?.name}</strong>
          </span>
        )}
        <span>
          {kind.toUpperCase()} No.: <strong>{number ?? '—'}</strong>
        </span>
        <span>
          Date: <strong>{formatDate(date)}</strong>
        </span>
      </div>

      {kind === 'ris' ? (
        <table className="slip-table">
          <thead>
            <tr>
              <th rowSpan={2}>Stock No.</th>
              <th rowSpan={2}>Unit</th>
              <th rowSpan={2}>Description</th>
              <th>Requisition</th>
              <th colSpan={2}>Issue</th>
            </tr>
            <tr>
              <th>Quantity</th>
              <th>Quantity</th>
              <th>Remarks</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l) => (
              <tr key={l.id}>
                <td>{l.code}</td>
                <td>{l.unit_of_measure}</td>
                <td>{l.name}</td>
                <td className="num">{formatQty(l.quantity)}</td>
                <td className="num">{formatQty(l.approved_quantity)}</td>
                <td>{l.status === 'rejected' ? 'Not issued' : l.approved_quantity < l.quantity ? 'Partial' : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <table className="slip-table">
          <thead>
            <tr>
              <th>Quantity</th>
              <th>Unit</th>
              <th>Unit cost</th>
              <th>Total cost</th>
              <th>Description</th>
              <th>{kind === 'ics' ? 'Inventory Item No.' : 'Property No.'}</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l) => (
              <tr key={l.id}>
                <td className="num">{formatQty(l.approved_quantity)}</td>
                <td>{l.unit_of_measure}</td>
                <td className="num">{formatPeso(l.unit_cost)}</td>
                <td className="num">{formatPeso((l.unit_cost ?? 0) * l.approved_quantity)}</td>
                <td>{l.name}</td>
                <td>{l.code}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {kind === 'ris' && (
        <p className="slip-purpose">
          Purpose: <strong>{r.purpose}</strong>
        </p>
      )}

      <div className="slip-signatures">
        {(kind === 'ris'
          ? [
              ['Requested by', r.requester?.name ?? ''],
              ['Approved by', ''],
              ['Issued by', ''],
              ['Received by', r.requester?.name ?? ''],
            ]
          : [
              ['Received from', ''],
              ['Received by', holder],
            ]
        ).map(([label, name]) => (
          <div key={label} className="sig">
            <span className="sig-label">{label}:</span>
            <span className="sig-line">{name}</span>
            <span className="sig-caption">Signature over printed name / Date</span>
          </div>
        ))}
      </div>
      <SlipLogos edge="bottom" />
    </div>
  )
}

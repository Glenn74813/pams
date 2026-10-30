import type { Asset, AssetMove, GatePass, InventoryConfig } from '../../api/types'
import { formatDate, formatPeso } from '../../lib/format'
import { SlipLogos } from '../../components/SlipLogos'

export type AssetSlipKind = 'accountability' | 'move' | 'gate'

interface Props {
  kind: AssetSlipKind
  asset?: Asset
  move?: AssetMove
  gate?: GatePass
  config?: InventoryConfig
}

/** Printable PAR / ICS, PTR, return slip (PRS / RRSP) and gate pass. */
export function AssetSlip({ kind, asset, move, gate, config }: Props) {
  const head = (title: string, appendix: string, no: string | null | undefined, date: string | null | undefined) => (
    <>
      <SlipLogos edge="top" />
      <p className="slip-appendix">{appendix}</p>
      <h2 className="slip-title">{title}</h2>
      <div className="slip-head">
        <span>
          Entity Name: <strong>{config?.agency_name || '________________'}</strong>
        </span>
        <span>
          Fund Cluster: <strong>{config?.fund_cluster || '________'}</strong>
        </span>
        <span>
          No.: <strong>{no ?? '—'}</strong>
        </span>
        <span>
          Date: <strong>{formatDate(date)}</strong>
        </span>
      </div>
    </>
  )
  const assetRow = (a: Asset | undefined) =>
    a && (
      <table className="slip-table">
        <thead>
          <tr>
            <th>Qty</th>
            <th>Unit</th>
            <th>Description</th>
            <th>Property No.</th>
            <th>Date Acquired</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td className="num">1</td>
            <td>{a.item?.unit_of_measure}</td>
            <td>
              {a.item?.name}
              {a.brand ? `, ${a.brand}` : ''}
              {a.serial_number ? ` — S/N ${a.serial_number}` : ''}
            </td>
            <td>{a.tag_number}</td>
            <td>{formatDate(a.acquisition_date)}</td>
            <td className="num">{formatPeso(a.acquisition_cost)}</td>
          </tr>
        </tbody>
      </table>
    )
  const sigs = (pairs: [string, string][]) => (
    <div className="slip-signatures">
      {pairs.map(([label, name]) => (
        <div key={label} className="sig">
          <span className="sig-label">{label}:</span>
          <span className="sig-line">{name}</span>
          <span className="sig-caption">Signature over printed name / Date</span>
        </div>
      ))}
    </div>
  )

  if (kind === 'accountability' && asset) {
    const par = asset.item?.classification === 'ppe'
    return (
      <div className="slip print-area">
        {head(par ? 'PROPERTY ACKNOWLEDGMENT RECEIPT' : 'INVENTORY CUSTODIAN SLIP', par ? 'Appendix 71' : 'Appendix 59', asset.accountability_number, asset.assigned_date)}
        {assetRow(asset)}
        {sigs([['Received by', asset.holder?.name ?? ''], ['Issued by', '']])}
      </div>
    )
  }
  if (kind === 'move' && move && asset) {
    const isReturn = move.transfer_type === 'return'
    const title = isReturn ? (asset.item?.classification === 'ppe' ? 'PROPERTY RETURN SLIP' : 'RECEIPT OF RETURNED SEMI-EXPENDABLE PROPERTY') : move.transfer_type === 'transfer' ? 'PROPERTY TRANSFER REPORT' : asset.item?.classification === 'ppe' ? 'PROPERTY ACKNOWLEDGMENT RECEIPT' : 'INVENTORY CUSTODIAN SLIP'
    return (
      <div className="slip print-area">
        {head(title, move.transfer_type === 'transfer' ? 'Appendix 76' : isReturn ? 'Return' : 'Appendix 71', move.reference_number, move.transfer_date)}
        <div className="slip-head">
          {move.from && (
            <span>
              From accountable officer: <strong>{move.from.name}</strong>
            </span>
          )}
          {move.to && (
            <span>
              To accountable officer: <strong>{move.to.name}</strong>
            </span>
          )}
        </div>
        {assetRow(asset)}
        <p className="slip-purpose">
          Reason: <strong>{move.reason}</strong>
        </p>
        {sigs(
          isReturn
            ? [['Returned by', move.from?.name ?? ''], ['Received by', move.recorded_by ?? '']]
            : move.transfer_type === 'transfer'
              ? [['Approved by', move.recorded_by ?? ''], ['Released / issued by', move.from?.name ?? ''], ['Received by', move.to?.name ?? '']]
              : [['Received by', move.to?.name ?? ''], ['Issued by', move.recorded_by ?? '']],
        )}
      </div>
    )
  }
  if (kind === 'gate' && gate) {
    return (
      <div className="slip print-area">
        {head('GATE PASS', 'Pass-out slip', gate.gate_pass_number, gate.date_released)}
        <table className="slip-table">
          <tbody>
            <tr>
              <th>Property</th>
              <td>
                {gate.tag_number} — {gate.item_name}
                {gate.serial_number ? ` (S/N ${gate.serial_number})` : ''}
              </td>
            </tr>
            <tr>
              <th>Carried out by</th>
              <td>{gate.received_by}</td>
            </tr>
            <tr>
              <th>Purpose</th>
              <td>{gate.purpose}</td>
            </tr>
            <tr>
              <th>Condition on release</th>
              <td>{gate.condition_on_release}</td>
            </tr>
            <tr>
              <th>Expected back</th>
              <td>{gate.expected_return_date ? formatDate(gate.expected_return_date) : 'Not returnable'}</td>
            </tr>
          </tbody>
        </table>
        {sigs([['Released by', gate.released_by ?? ''], ['Carried out by', gate.received_by], ['Guard on duty', '']])}
      </div>
    )
  }
  return null
}

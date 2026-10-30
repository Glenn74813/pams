import type { Bidding, InventoryConfig } from '../../api/types'
import { formatDate, formatDateTime, formatPeso } from '../../lib/format'
import { SECURITY_FORMS, STAGES } from './labels'

/** Abstract of Bids as read and evaluated, for BAC signature. */
export function AbstractOfBids({ b, config }: { b: Bidding; config?: InventoryConfig }) {
  return (
    <div className="slip print-area">
      <h2 className="slip-title">ABSTRACT OF BIDS</h2>
      <div className="slip-head">
        <span>
          Entity Name: <strong>{config?.agency_name || '________________'}</strong>
        </span>
        <span>
          Bidding No.: <strong>{b.bidding_number}</strong>
        </span>
        <span>
          Project: <strong>{b.title}</strong>
        </span>
        <span>
          ABC: <strong>{formatPeso(b.abc)}</strong>
        </span>
        <span>
          Bid opening: <strong>{formatDateTime(b.bid_opening_date)}</strong>
        </span>
      </div>
      <table className="slip-table">
        <thead>
          <tr>
            <th>Rank</th>
            <th>Bidder</th>
            <th>Bid as read</th>
            <th>Bid security</th>
            {STAGES.map(([, l]) => (
              <th key={l}>{l}</th>
            ))}
            {b.evaluation_method !== 'price' && <th>Score</th>}
          </tr>
        </thead>
        <tbody>
          {b.bidders?.map((x) => (
            <tr key={x.id}>
              <td className="num">{x.computed_rank ?? '—'}</td>
              <td>{x.supplier.name}</td>
              <td className="num">{x.bid_amount == null ? '—' : formatPeso(x.bid_amount)}</td>
              <td>{x.bid_security_form ? `${SECURITY_FORMS[x.bid_security_form]}${x.bid_security_ok ? '' : ' — insufficient'}` : 'None'}</td>
              {STAGES.map(([s]) => (
                <td key={s}>{x.results[s]}</td>
              ))}
              {b.evaluation_method !== 'price' && <td className="num">{x.mearb_score ?? '—'}</td>}
            </tr>
          ))}
        </tbody>
      </table>
      <p className="slip-purpose">
        {b.awarded_supplier ? (
          <>
            Awarded to <strong>{b.awarded_supplier.name}</strong> at {formatPeso(b.awarded_amount)} on {formatDate(b.awarded_at)} ({b.bac_resolution?.name}).
          </>
        ) : b.status === 'failed' ? (
          <>Declared a failure of bidding: {b.failure_reason}</>
        ) : (
          'Evaluation in progress.'
        )}
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

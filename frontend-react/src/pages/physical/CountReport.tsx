import { Fragment } from 'react'
import type { CountDetail, CountTag, InventoryConfig } from '../../api/types'
import { formatDate, formatPeso, formatQty } from '../../lib/format'
import { FINDING } from './labels'

export type GroupBy = 'none' | 'holder' | 'department'

const TITLE: Record<string, [string, string]> = {
  supplies: ['Appendix 66', 'REPORT ON THE PHYSICAL COUNT OF INVENTORIES'],
  ppe: ['Appendix 73', 'REPORT ON THE PHYSICAL COUNT OF PROPERTY, PLANT AND EQUIPMENT'],
  semi_expendable: ['', 'REPORT ON THE PHYSICAL COUNT OF SEMI-EXPENDABLE PROPERTY'],
}

function tagRemark(t: CountTag) {
  const bits = [t.finding === 'found' ? '' : FINDING[t.finding][0]]
  if (t.observed_condition) bits.push(t.observed_condition)
  if (t.location) bits.push(t.location)
  if (t.remarks) bits.push(t.remarks)
  return bits.filter(Boolean).join('; ')
}

/** RPCI / RPCPPE / RPCSP as of the count's cut-off, optionally one section per accountable person or department. */
export function CountReport({ d, config, groupBy, discrepanciesOnly }: { d: CountDetail; config?: InventoryConfig; groupBy: GroupBy; discrepanciesOnly: boolean }) {
  const c = d.count
  const [appendix, title] = TITLE[c.count_type]
  const counters = [...new Map([...d.lines.map((l) => l.counted_by), ...d.properties.map((t) => t.checked_by)].filter((x) => x).map((x) => [x!.id, x!.name])).values()]
  const head = (
    <div className="slip-head">
      <span>
        Entity Name: <strong>{config?.agency_name || '________________'}</strong>
      </span>
      <span>
        Fund Cluster: <strong>{config?.fund_cluster || '________'}</strong>
      </span>
      <span>
        As at: <strong>{formatDate(c.count_date)}</strong> (cut-off {formatDate(c.started_at)})
      </span>
      <span>
        Count No.: <strong>{c.count_number}</strong>
      </span>
      <span>
        Scope: <strong>{c.department?.name ?? c.category?.name ?? 'All'}</strong>
      </span>
      {c.purpose && (
        <span>
          Purpose: <strong>{c.purpose}</strong>
        </span>
      )}
    </div>
  )

  let body
  if (c.count_type === 'supplies') {
    const lines = d.lines.filter((l) => !discrepanciesOnly || (l.variance ?? 0) !== 0)
    body = (
      <table className="slip-table">
        <thead>
          <tr>
            <th rowSpan={2}>Article / Description</th>
            <th rowSpan={2}>Stock No.</th>
            <th rowSpan={2}>Unit</th>
            <th rowSpan={2}>Unit Value</th>
            <th rowSpan={2}>Balance per Card</th>
            <th rowSpan={2}>On Hand per Count</th>
            <th colSpan={2}>Shortage / Overage</th>
            <th rowSpan={2}>Remarks</th>
          </tr>
          <tr>
            <th>Qty</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l) => (
            <tr key={l.id}>
              <td>{l.item.name}</td>
              <td>{l.item.code}</td>
              <td>{l.item.unit_of_measure}</td>
              <td className="num">{formatPeso(l.unit_cost)}</td>
              <td className="num">{formatQty(l.system_quantity)}</td>
              <td className="num">{formatQty(l.counted_quantity)}</td>
              <td className="num">{l.variance ? (l.variance > 0 ? `+${l.variance}` : l.variance) : ''}</td>
              <td className="num">{l.variance ? formatPeso(l.variance_value) : ''}</td>
              <td>{[l.remarks, l.adjustment_number].filter(Boolean).join('; ')}</td>
            </tr>
          ))}
          <tr>
            <td colSpan={7} className="num">
              <strong>Net shortage / overage</strong>
            </td>
            <td className="num">
              <strong>{formatPeso(d.summary.over_value - d.summary.short_value)}</strong>
            </td>
            <td />
          </tr>
        </tbody>
      </table>
    )
  } else {
    const tags = d.properties.filter((t) => !discrepanciesOnly || t.finding !== 'found')
    const key = (t: CountTag) => (groupBy === 'holder' ? (t.holder?.name ?? 'Unassigned') : groupBy === 'department' ? (t.department?.name ?? 'No department') : '')
    const groups = new Map<string, CountTag[]>()
    for (const t of tags) groups.set(key(t), [...(groups.get(key(t)) ?? []), t])
    const sorted = [...groups.entries()].sort(([a], [b]) => a.localeCompare(b))
    body = (
      <table className="slip-table">
        <thead>
          <tr>
            <th rowSpan={2}>Article / Description</th>
            <th rowSpan={2}>Property No.</th>
            <th rowSpan={2}>Accountable Officer</th>
            <th rowSpan={2}>Unit Value</th>
            <th rowSpan={2}>Qty per Property Card</th>
            <th rowSpan={2}>Qty per Physical Count</th>
            <th colSpan={2}>Shortage / Overage</th>
            <th rowSpan={2}>Remarks</th>
          </tr>
          <tr>
            <th>Qty</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map(([g, list]) => (
            <Fragment key={g}>
              {groupBy !== 'none' && (
                <tr className="group-row">
                  <td colSpan={9}>
                    <strong>{g}</strong> — {list.length} tag{list.length === 1 ? '' : 's'}, {list.filter((t) => t.finding === 'missing').length} missing
                  </td>
                </tr>
              )}
              {list.map((t) => {
                const card = t.in_masterlist && t.in_scope ? 1 : 0
                const counted = t.result === 'found' ? 1 : 0
                const diff = counted - card
                return (
                  <tr key={t.id}>
                    <td>{t.description}</td>
                    <td>{t.tag_number}</td>
                    <td>{t.holder?.name ?? ''}</td>
                    <td className="num">{formatPeso(t.book_value)}</td>
                    <td className="num">{card}</td>
                    <td className="num">{counted}</td>
                    <td className="num">{diff ? (diff > 0 ? '+1' : '-1') : ''}</td>
                    <td className="num">{diff && t.book_value != null ? formatPeso(diff * Number(t.book_value)) : ''}</td>
                    <td>{tagRemark(t)}</td>
                  </tr>
                )
              })}
            </Fragment>
          ))}
        </tbody>
      </table>
    )
  }

  return (
    <div className="slip print-area">
      {appendix && <p className="slip-appendix">{appendix}</p>}
      <h2 className="slip-title">{title}</h2>
      {head}
      {body}
      <div className="slip-signatures">
        <div className="sig">
          <span className="sig-label">Certified correct by:</span>
          <span className="sig-line">{counters.join(', ')}</span>
          <span className="sig-caption">Inventory Committee</span>
        </div>
        <div className="sig">
          <span className="sig-label">Approved by:</span>
          <span className="sig-line" />
          <span className="sig-caption">Head of Agency / Authorized Representative</span>
        </div>
        <div className="sig">
          <span className="sig-label">Verified by:</span>
          <span className="sig-line">{c.verified_by?.name ?? ''}</span>
          <span className="sig-caption">{c.verified_at ? formatDate(c.verified_at) : 'COA Representative / Verifier'}</span>
        </div>
      </div>
    </div>
  )
}

import type { Ranked } from '../../api/types'
import { formatPeso } from '../../lib/format'

/** A ranked list drawn as horizontal bars, scaled to the largest value. */
export function BarList({ rows, value, unit, empty }: { rows: Ranked[]; value: 'count' | 'amount'; unit?: string; empty: string }) {
  if (rows.length === 0) return <p className="muted small">{empty}</p>
  const max = Math.max(...rows.map((r) => r[value]), 1)
  return (
    <ul className="bar-list">
      {rows.map((r) => (
        <li key={r.name + (r.code ?? '')}>
          <div className="bar-label">
            <span>
              {r.name}
              {r.code && <span className="muted small"> · {r.code}</span>}
            </span>
            <strong>{value === 'amount' ? formatPeso(r.amount) : `${r.count.toLocaleString()}${unit ? ' ' + unit : ''}`}</strong>
          </div>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${(r[value] / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  )
}

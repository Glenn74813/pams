import { useState } from 'react'
import { formatPeso } from '../../lib/format'

/** Categorical slots in fixed order (validated against the card surface); "Other" is always neutral gray. */
const SERIES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300']
const OTHER = '#9ca3af'

export interface Share {
  name: string
  count: number
  amount: number
}

/**
 * Category distribution as a donut (TOR 1.1.2). Values sit in the legend beside it, so no slice
 * relies on color alone; hovering a slice or a legend row highlights the pair.
 */
export function DonutChart({ rows, value, empty, unit }: { rows: Share[]; value: 'amount' | 'count'; empty: string; unit?: string }) {
  const [active, setActive] = useState<number | null>(null)
  const total = rows.reduce((s, r) => s + Number(r[value]), 0)
  if (rows.length === 0 || total <= 0) return <p className="muted small">{empty}</p>
  const color = (i: number) => (rows[i].name === 'Other' ? OTHER : SERIES[i % SERIES.length])
  const fmt = (v: number) => (value === 'amount' ? formatPeso(v) : `${v.toLocaleString()}${unit ? ' ' + unit : ''}`)
  const R = 60
  const r = 38
  let angle = -Math.PI / 2
  const arcs = rows.map((row, i) => {
    const share = Number(row[value]) / total
    const start = angle
    const end = angle + share * Math.PI * 2
    angle = end
    return { i, share, path: slice(start, end, R, r) }
  })
  return (
    <div className="donut">
      <svg viewBox="-64 -64 128 128" role="img" aria-label={rows.map((x) => `${x.name} ${fmt(Number(x[value]))}`).join(', ')}>
        {arcs.map((a) => (
          <path
            key={a.i}
            d={a.path}
            fill={color(a.i)}
            stroke="var(--card-bg, #fff)"
            strokeWidth={2}
            opacity={active === null || active === a.i ? 1 : 0.35}
            onMouseEnter={() => setActive(a.i)}
            onMouseLeave={() => setActive(null)}
          >
            <title>{`${rows[a.i].name}: ${fmt(Number(rows[a.i][value]))} (${(a.share * 100).toFixed(1)}%)`}</title>
          </path>
        ))}
        <text className="donut-total" textAnchor="middle" dy="-0.1em">
          {active === null ? 'Total' : `${(arcs[active].share * 100).toFixed(0)}%`}
        </text>
        <text className="donut-total-sub" textAnchor="middle" dy="1.3em">
          {active === null ? rows.length + ' groups' : rows[active].name.slice(0, 16)}
        </text>
      </svg>
      <ul className="chart-legend">
        {rows.map((row, i) => (
          <li key={row.name} className={active === i ? 'active' : ''} onMouseEnter={() => setActive(i)} onMouseLeave={() => setActive(null)}>
            <span className="swatch" style={{ background: color(i) }} />
            <span className="legend-name">{row.name}</span>
            <span className="legend-value">{fmt(Number(row[value]))}</span>
            <span className="legend-share">{((Number(row[value]) / total) * 100).toFixed(1)}%</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function slice(a0: number, a1: number, R: number, r: number) {
  // A full circle cannot be one arc; stop just short of it.
  if (a1 - a0 >= Math.PI * 2 - 1e-6) a1 = a0 + Math.PI * 2 - 1e-4
  const large = a1 - a0 > Math.PI ? 1 : 0
  const p = (rad: number, ang: number) => `${(rad * Math.cos(ang)).toFixed(3)} ${(rad * Math.sin(ang)).toFixed(3)}`
  return `M ${p(R, a0)} A ${R} ${R} 0 ${large} 1 ${p(R, a1)} L ${p(r, a1)} A ${r} ${r} 0 ${large} 0 ${p(r, a0)} Z`
}

export interface MonthPoint {
  month: string
  received: number
  issued: number
  receipts: number
  issues: number
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const label = (ym: string) => {
  const [y, m] = ym.split('-').map(Number)
  return `${MONTHS[m - 1]} ${String(y).slice(2)}`
}

/** Stock received vs issued, last twelve months (TOR 1.1.4): two lines on one peso axis, crosshair tooltip on hover. */
export function TrendChart({ months }: { months: MonthPoint[] }) {
  const [hover, setHover] = useState<number | null>(null)
  if (months.length === 0) return null
  const W = 560
  const H = 200
  const L = 64
  const B = 26
  const T = 10
  const Rt = 12
  const max = Math.max(1, ...months.map((m) => Math.max(Number(m.received), Number(m.issued))))
  const nice = niceMax(max)
  const x = (i: number) => L + (i * (W - L - Rt)) / Math.max(1, months.length - 1)
  const y = (v: number) => T + (H - T - B) * (1 - v / nice)
  const line = (k: 'received' | 'issued') => months.map((m, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(Number(m[k])).toFixed(1)}`).join(' ')
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * nice)
  const empty = months.every((m) => Number(m.received) === 0 && Number(m.issued) === 0)
  const h = hover === null ? null : months[hover]
  return (
    <div className="trend">
      <ul className="chart-legend inline">
        <li>
          <span className="swatch" style={{ background: SERIES[0] }} />
          <span className="legend-name">Received</span>
        </li>
        <li>
          <span className="swatch" style={{ background: SERIES[1] }} />
          <span className="legend-name">Issued</span>
        </li>
      </ul>
      <div className="trend-plot">
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Stock received and issued per month, in pesos" onMouseLeave={() => setHover(null)}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={L} x2={W - Rt} y1={y(t)} y2={y(t)} className="grid" />
              <text x={L - 6} y={y(t)} dy="0.32em" textAnchor="end" className="axis">
                {compactPeso(t)}
              </text>
            </g>
          ))}
          {months.map((m, i) => (
            <text key={m.month} x={x(i)} y={H - 8} textAnchor="middle" className="axis">
              {i % 2 === months.length % 2 ? label(m.month) : ''}
            </text>
          ))}
          {hover !== null && <line x1={x(hover)} x2={x(hover)} y1={T} y2={H - B} className="crosshair" />}
          <path d={line('received')} fill="none" stroke={SERIES[0]} strokeWidth={2} strokeLinejoin="round" />
          <path d={line('issued')} fill="none" stroke={SERIES[1]} strokeWidth={2} strokeLinejoin="round" />
          {hover !== null && (
            <>
              <circle cx={x(hover)} cy={y(Number(months[hover].received))} r={4} fill={SERIES[0]} stroke="var(--card-bg, #fff)" strokeWidth={2} />
              <circle cx={x(hover)} cy={y(Number(months[hover].issued))} r={4} fill={SERIES[1]} stroke="var(--card-bg, #fff)" strokeWidth={2} />
            </>
          )}
          {months.map((m, i) => (
            <rect key={m.month} x={x(i) - (W - L - Rt) / (2 * Math.max(1, months.length - 1))} y={T} width={(W - L - Rt) / Math.max(1, months.length - 1)} height={H - T - B}
              fill="transparent" onMouseEnter={() => setHover(i)} />
          ))}
        </svg>
        {h && hover !== null && (
          <div className="chart-tip" style={{ left: `${Math.min(80, Math.max(20, (x(hover) / W) * 100))}%` }}>
            <strong>{label(h.month)}</strong>
            <span>
              <i className="swatch" style={{ background: SERIES[0] }} /> Received {formatPeso(Number(h.received))} · {h.receipts} receipt{h.receipts === 1 ? '' : 's'}
            </span>
            <span>
              <i className="swatch" style={{ background: SERIES[1] }} /> Issued {formatPeso(Number(h.issued))} · {h.issues} issue{h.issues === 1 ? '' : 's'}
            </span>
          </div>
        )}
      </div>
      {empty && <p className="muted small">No stock was received or issued in the last twelve months.</p>}
    </div>
  )
}

function niceMax(v: number) {
  const p = Math.pow(10, Math.floor(Math.log10(v)))
  for (const m of [1, 2, 2.5, 5, 10]) if (m * p >= v) return m * p
  return 10 * p
}

function compactPeso(v: number) {
  if (v >= 1e6) return `₱${(v / 1e6).toFixed(v % 1e6 === 0 ? 0 : 1)}M`
  if (v >= 1e3) return `₱${(v / 1e3).toFixed(v % 1e3 === 0 ? 0 : 1)}k`
  return `₱${v.toFixed(0)}`
}

import type { ApiData } from '../../api/integrations'
import { formatDateTime } from '../../lib/format'

const cell = (v: string | number | boolean | null) =>
  v === null || v === ''
    ? '—'
    : typeof v === 'boolean'
      ? v ? 'Yes' : 'No'
      : typeof v === 'number'
        ? v.toLocaleString('en-PH', { maximumFractionDigits: 2 })
        : String(v)

/** What another system returned, as a read-only table with where and when it came from. */
export function ApiDataTable({ d, title }: { d: ApiData; title?: string }) {
  return (
    <div style={{ marginBottom: '1.25rem' }}>
      <div className="section-header" style={{ marginBottom: '0.4rem' }}>
        <h4 style={{ margin: 0 }}>{title ?? d.api.name}</h4>
        <span className="hint">From {d.api.name} · read {formatDateTime(d.fetched_at)} · read-only</span>
      </div>
      {d.error && <div className="alert alert-warning">{d.error}</div>}
      {d.result && (
        <div className="table-wrap">
          <table className="table table-compact">
            <thead>
              <tr>
                {d.result.columns.map((c) => (
                  <th key={c}>{c}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {d.result.rows.map((r, i) => (
                <tr key={i}>
                  {r.map((v, j) => (
                    <td key={j} className={typeof v === 'number' ? 'num' : undefined}>
                      {cell(v)}
                    </td>
                  ))}
                </tr>
              ))}
              {d.result.rows.length === 0 && (
                <tr>
                  <td colSpan={Math.max(1, d.result.columns.length)} className="empty">
                    No records.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      {d.result?.truncated && <p className="hint">Only the first 500 records are shown.</p>}
    </div>
  )
}

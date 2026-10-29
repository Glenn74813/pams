import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { physicalCountApi, type CountLineInput } from '../../api/endpoints'
import type { CountDetail, CountLine } from '../../api/types'
import { formatPeso, formatQty } from '../../lib/format'

type Draft = { counted: string; unit_cost: string; location: string; remarks: string }

const toDraft = (l: CountLine): Draft => ({
  counted: l.counted_quantity == null ? '' : String(l.counted_quantity),
  unit_cost: '',
  location: l.location ?? '',
  remarks: l.remarks ?? '',
})

/** Count sheet for stock-room items: book balance at the cut-off against what is on the shelf. */
export function SuppliesSheet({ d, onSaved }: { d: CountDetail; onSaved: (d: CountDetail, m: string) => void }) {
  const editable = d.count.can_count
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [filter, setFilter] = useState<'all' | 'uncounted' | 'variance'>('all')
  const [search, setSearch] = useState('')
  const [error, setError] = useState<string | null>(null)
  const draft = (l: CountLine) => drafts[l.id] ?? toDraft(l)
  const edit = (l: CountLine, patch: Partial<Draft>) => setDrafts((x) => ({ ...x, [l.id]: { ...draft(l), ...patch } }))
  const changed = Object.keys(drafts).length
  const save = useMutation({
    mutationFn: () => {
      const lines: CountLineInput[] = d.lines
        .filter((l) => drafts[l.id])
        .map((l) => {
          const x = drafts[l.id]
          return {
            id: l.id,
            counted_quantity: x.counted === '' ? null : Number(x.counted),
            unit_cost: x.unit_cost === '' ? undefined : Number(x.unit_cost),
            location: x.location,
            remarks: x.remarks,
          }
        })
      return physicalCountApi.record(d.count.id, lines)
    },
    onSuccess: (r) => {
      setDrafts({})
      setError(null)
      onSaved(r, `${changed} line${changed === 1 ? '' : 's'} saved.`)
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the counts.'),
  })
  const q = search.trim().toLowerCase()
  const shown = d.lines.filter((l) => {
    const counted = draft(l).counted
    if (filter === 'uncounted' && counted !== '') return false
    if (filter === 'variance' && (counted === '' || Number(counted) === l.system_quantity)) return false
    return !q || l.item.code.toLowerCase().includes(q) || l.item.name.toLowerCase().includes(q)
  })
  const s = d.summary
  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="count-chips">
        <span className="chip">
          {s.counted} / {s.lines} counted
        </span>
        <span className="chip ok">{s.matched} agree with the book</span>
        <span className="chip bad">
          {s.short_count} short · {formatPeso(s.short_value)}
        </span>
        <span className="chip warn">
          {s.over_count} over · {formatPeso(s.over_value)}
        </span>
      </div>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Find item code or name" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Find item" />
          <select value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)} aria-label="Show">
            <option value="all">All lines</option>
            <option value="uncounted">Not yet counted</option>
            <option value="variance">With a variance</option>
          </select>
        </div>
        {editable && (
          <button className="btn btn-primary" disabled={!changed || save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? 'Saving…' : changed ? `Save ${changed} line${changed === 1 ? '' : 's'}` : 'Saved'}
          </button>
        )}
      </div>
      <div className="table-wrap">
        <table className="table count-table">
          <thead>
            <tr>
              <th>Stock no.</th>
              <th>Item</th>
              <th className="num">Book at cut-off</th>
              <th className="num">Counted</th>
              <th className="num">Variance</th>
              <th className="num">Unit cost</th>
              <th className="num">Value</th>
              <th>Location / remarks</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((l) => {
              const x = draft(l)
              const variance = x.counted === '' ? null : Number(x.counted) - l.system_quantity
              const costless = Number(l.unit_cost) === 0
              const cost = costless && x.unit_cost !== '' ? Number(x.unit_cost) : Number(l.unit_cost)
              return (
                <tr key={l.id} className={drafts[l.id] ? 'row-dirty' : undefined}>
                  <td className="mono">{l.item.code}</td>
                  <td className="item-cell">
                    {l.item.name}
                    <div className="small muted">
                      {l.item.unit_of_measure}
                      {l.item.category ? ` · ${l.item.category}` : ''}
                      {l.moved_since_cutoff !== 0 && (
                        <span className="badge st-pending" title="Receipts and issues posted after the count started. They are kept; only the variance against the cut-off is posted.">
                          {l.moved_since_cutoff > 0 ? '+' : ''}
                          {l.moved_since_cutoff} moved since cut-off
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="num">{formatQty(l.system_quantity)}</td>
                  <td className="num">
                    {editable ? (
                      <input className="qty-input" type="number" min={0} value={x.counted} onChange={(e) => edit(l, { counted: e.target.value })} aria-label={`Counted ${l.item.name}`} />
                    ) : (
                      formatQty(l.counted_quantity)
                    )}
                  </td>
                  <td className={`num ${variance && variance < 0 ? 'short' : variance && variance > 0 ? 'over' : ''}`}>{variance == null ? '—' : variance > 0 ? `+${variance}` : variance}</td>
                  <td className="num">
                    {editable && costless && variance != null && variance > 0 ? (
                      <input className="qty-input cost-input" type="number" min={0} step="0.01" placeholder="needed" value={x.unit_cost} onChange={(e) => edit(l, { unit_cost: e.target.value })} aria-label={`Unit cost ${l.item.name}`} />
                    ) : (
                      formatPeso(cost)
                    )}
                  </td>
                  <td className="num">{variance == null ? '—' : formatPeso(variance * cost)}</td>
                  <td>
                    {editable ? (
                      <div className="inline-pair">
                        <input value={x.location} placeholder="Location" onChange={(e) => edit(l, { location: e.target.value })} aria-label={`Location ${l.item.name}`} />
                        <input value={x.remarks} placeholder="Remarks" onChange={(e) => edit(l, { remarks: e.target.value })} aria-label={`Remarks ${l.item.name}`} />
                      </div>
                    ) : (
                      <span className="small">
                        {[l.location, l.remarks].filter(Boolean).join(' · ') || '—'}
                        {l.adjustment_number && <span className="badge st-ok">{l.adjustment_number}</span>}
                      </span>
                    )}
                  </td>
                </tr>
              )
            })}
            {shown.length === 0 && (
              <tr>
                <td colSpan={8} className="empty">
                  Nothing to show.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}

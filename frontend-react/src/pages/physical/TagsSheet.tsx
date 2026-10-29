import { useRef, useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { physicalCountApi } from '../../api/endpoints'
import type { CountDetail, CountTag } from '../../api/types'
import { formatPeso } from '../../lib/format'
import { Badge } from '../assets/pickers'
import { CONDITIONS, FINDING } from './labels'

/** Asset count: scan or type each tag found; whatever is left unchecked is missing when the count closes. */
export function TagsSheet({ d, onSaved }: { d: CountDetail; onSaved: (d: CountDetail, m: string) => void }) {
  const editable = d.count.can_count
  const [tag, setTag] = useState('')
  const [condition, setCondition] = useState('')
  const [location, setLocation] = useState('')
  const [remarks, setRemarks] = useState('')
  const [description, setDescription] = useState('')
  const [needsDescription, setNeedsDescription] = useState(false)
  const [filter, setFilter] = useState('all')
  const [search, setSearch] = useState('')
  const [error, setError] = useState<string | null>(null)
  const tagInput = useRef<HTMLInputElement>(null)
  const fail = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const scan = useMutation({
    mutationFn: () => physicalCountApi.scan(d.count.id, { tag_number: tag, observed_condition: condition || undefined, location, remarks, description: description || undefined }),
    onSuccess: (r) => {
      const t = r.properties.find((x) => x.tag_number.toLowerCase() === tag.trim().toLowerCase())
      onSaved(r, `${t?.tag_number ?? tag}: ${t ? FINDING[t.finding][0].toLowerCase() : 'recorded'}.`)
      setTag('')
      setCondition('')
      setRemarks('')
      setDescription('')
      setNeedsDescription(false)
      setError(null)
      tagInput.current?.focus()
    },
    onError: (err) => {
      if (err instanceof ApiError && err.message.includes('not on record')) setNeedsDescription(true)
      fail(err)
    },
  })
  const mark = useMutation({
    mutationFn: ({ t, result }: { t: CountTag; result: string }) => physicalCountApi.mark(d.count.id, t.id, { result, observed_condition: t.observed_condition, location: t.location, remarks: t.remarks }),
    onSuccess: (r, v) => onSaved(r, `${v.t.tag_number} marked ${v.result === 'pending' ? 'not yet checked' : v.result}.`),
    onError: fail,
  })
  const unscan = useMutation({
    mutationFn: (t: CountTag) => physicalCountApi.unscan(d.count.id, t.id),
    onSuccess: (r, t) => onSaved(r, `${t.tag_number} removed from the count.`),
    onError: fail,
  })
  const q = search.trim().toLowerCase()
  const shown = d.properties.filter(
    (t) => (filter === 'all' || t.finding === filter) && (!q || t.tag_number.toLowerCase().includes(q) || (t.description ?? '').toLowerCase().includes(q) || (t.holder?.name ?? '').toLowerCase().includes(q)),
  )
  const s = d.summary
  const pending = d.properties.filter((t) => t.finding === 'pending').length
  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="count-chips">
        <span className="chip">
          {s.found} / {s.expected} found
        </span>
        {pending > 0 && <span className="chip">{pending} not yet checked</span>}
        <span className="chip bad">
          {s.missing} missing · {formatPeso(s.missing_value)}
        </span>
        <span className="chip warn">{s.not_in_masterlist} not in masterlist</span>
        <span className="chip warn">{s.out_of_scope} on record elsewhere</span>
        <span className="chip">{s.condition_changed} condition changed</span>
        {s.unserviceable > 0 && <span className="chip bad">{s.unserviceable} unserviceable</span>}
      </div>
      {editable && (
        <form className="scan-bar" onSubmit={(e: FormEvent) => { e.preventDefault(); if (tag.trim()) scan.mutate() }}>
          <div className="field">
            <label htmlFor="sc-tag">Property tag *</label>
            <input id="sc-tag" ref={tagInput} autoFocus value={tag} onChange={(e) => setTag(e.target.value)} placeholder="Scan or type" />
          </div>
          <div className="field">
            <label htmlFor="sc-cond">Condition seen</label>
            <select id="sc-cond" value={condition} onChange={(e) => setCondition(e.target.value)}>
              <option value="">As on record</option>
              {CONDITIONS.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="sc-loc">Location</label>
            <input id="sc-loc" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Kept for the next scan" />
          </div>
          <div className="field">
            <label htmlFor="sc-rem">Remarks</label>
            <input id="sc-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
          {needsDescription && (
            <div className="field span-all">
              <label htmlFor="sc-desc">Description (tag not on record) *</label>
              <input id="sc-desc" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. Steel filing cabinet, 4-drawer, grey" autoFocus />
            </div>
          )}
          <button className="btn btn-primary" type="submit" disabled={scan.isPending || !tag.trim()}>
            {scan.isPending ? 'Recording…' : 'Found'}
          </button>
        </form>
      )}
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Find tag, article or holder" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Find tag" />
          <select value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Show">
            <option value="all">All tags</option>
            {Object.entries(FINDING).map(([k, [label]]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="table-wrap">
        <table className="table count-table">
          <thead>
            <tr>
              <th>Tag</th>
              <th>Article</th>
              <th>Accountable</th>
              <th className="num">Book value</th>
              <th>Condition</th>
              <th>Finding</th>
              {editable && <th />}
            </tr>
          </thead>
          <tbody>
            {shown.map((t) => (
              <tr key={t.id}>
                <td className="mono">{t.tag_number}</td>
                <td>
                  {t.description ?? '—'}
                  {(t.location || t.remarks) && <div className="small muted">{[t.location, t.remarks].filter(Boolean).join(' · ')}</div>}
                </td>
                <td>
                  {t.holder?.name ?? <span className="muted">Unassigned</span>}
                  {t.department && <div className="small muted">{t.department.name}</div>}
                </td>
                <td className="num">{formatPeso(t.book_value)}</td>
                <td>
                  {t.observed_condition && t.observed_condition !== t.condition_on_record ? (
                    <>
                      <s className="muted">{t.condition_on_record ?? '—'}</s> → <strong>{t.observed_condition}</strong>
                    </>
                  ) : (
                    (t.observed_condition ?? t.condition_on_record ?? '—')
                  )}
                </td>
                <td>
                  <Badge map={FINDING} value={t.finding} />
                  {t.checked_by && <div className="small muted">{t.checked_by.name}</div>}
                </td>
                {editable && (
                  <td className="row-actions">
                    {t.removable ? (
                      <button className="btn btn-secondary btn-sm" onClick={() => unscan.mutate(t)}>
                        Remove
                      </button>
                    ) : t.result === 'pending' ? (
                      <>
                        <button className="btn btn-secondary btn-sm" onClick={() => mark.mutate({ t: { ...t, observed_condition: null }, result: 'found' })}>
                          Found
                        </button>
                        <button className="btn btn-secondary btn-sm" onClick={() => mark.mutate({ t, result: 'missing' })}>
                          Missing
                        </button>
                      </>
                    ) : (
                      <button className="btn btn-secondary btn-sm" onClick={() => mark.mutate({ t: { ...t, observed_condition: null }, result: 'pending' })}>
                        Undo
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
            {shown.length === 0 && (
              <tr>
                <td colSpan={editable ? 7 : 6} className="empty">
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

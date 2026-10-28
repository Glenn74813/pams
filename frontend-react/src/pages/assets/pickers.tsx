import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { assetsApi, peopleApi } from '../../api/endpoints'
import type { Asset } from '../../api/types'
import { useDebounced } from '../../lib/hooks'
import { STATUS_LABEL } from './labels'

export function Badge({ map, value }: { map: Record<string, [string, string]>; value: string }) {
  const [label, cls] = map[value] ?? [value, '']
  return <span className={`badge ${cls}`}>{label}</span>
}

export function PersonSelect({ id, value, onChange, exclude, required }: { id: string; value: string; onChange: (v: string) => void; exclude?: number | null; required?: boolean }) {
  const people = useQuery({ queryKey: ['people'], queryFn: peopleApi.all })
  return (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value)} required={required}>
      <option value="">Choose a person</option>
      {people.data?.data
        .filter((p) => p.id !== exclude)
        .map((p) => (
          <option key={p.id} value={p.id}>
            {p.name} ({p.email})
          </option>
        ))}
    </select>
  )
}

/** Search the asset register by tag, serial or item name. */
export function AssetPicker({ id, onPick, filter }: { id: string; onPick: (a: Asset) => void; filter?: (a: Asset) => string | null }) {
  const [text, setText] = useState('')
  const q = useDebounced(text.trim())
  const list = useQuery({ queryKey: ['asset-pick', q], queryFn: () => assetsApi.list({ page: 1, search: q }), enabled: q.length > 0 })
  return (
    <div className="picker-field">
      <input id={id} type="search" value={text} onChange={(e) => setText(e.target.value)} placeholder="Type a tag no., serial no. or item name…" autoComplete="off" />
      {q && (
        <ul className="picker-results">
          {list.data?.data.slice(0, 10).map((a) => {
            const why = filter?.(a) ?? null
            return (
              <li key={a.id}>
                <button
                  type="button"
                  disabled={!!why}
                  onClick={() => {
                    onPick(a)
                    setText('')
                  }}
                >
                  <span className="mono">{a.tag_number}</span> {a.item?.name}{' '}
                  <span className="muted">
                    · {STATUS_LABEL[a.status]?.[0]} · {a.holder?.name ?? 'property office'}
                    {why ? ` · ${why}` : ''}
                  </span>
                </button>
              </li>
            )
          })}
          {list.data?.data.length === 0 && <li className="muted">No matching assets.</li>}
        </ul>
      )}
    </div>
  )
}

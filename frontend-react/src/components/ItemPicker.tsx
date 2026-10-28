import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { inventoryApi } from '../api/endpoints'
import type { Item } from '../api/types'
import { useDebounced } from '../lib/hooks'

interface Props {
  id: string
  onPick: (item: Item) => void
  /** Items already chosen, shown as unavailable. */
  exclude?: number[]
  /** Disable items with nothing on hand (PPE stays pickable: it is issued per unit). */
  requireStock?: boolean
  placeholder?: string
}

/** Type-ahead search over the item catalogue. */
export function ItemPicker({ id, onPick, exclude = [], requireStock, placeholder = 'Type an item code or name…' }: Props) {
  const [text, setText] = useState('')
  const q = useDebounced(text.trim())
  const options = useQuery({ queryKey: ['item-options', q], queryFn: () => inventoryApi.itemOptions(q || undefined), enabled: q.length > 0 })

  return (
    <div className="picker-field">
      <input id={id} type="search" value={text} onChange={(e) => setText(e.target.value)} placeholder={placeholder} autoComplete="off" />
      {q && (
        <ul className="picker-results">
          {options.data?.slice(0, 10).map((i) => {
            const taken = exclude.includes(i.id)
            const empty = requireStock && !i.is_available
            return (
              <li key={i.id}>
                <button
                  type="button"
                  disabled={taken || empty}
                  onClick={() => {
                    onPick(i)
                    setText('')
                  }}
                >
                  <span className="mono">{i.code}</span> {i.name}{' '}
                  <span className="muted">
                    · {i.current_stock} {i.unit_of_measure}
                    {taken ? ' · already added' : empty ? ' · out of stock' : ''}
                  </span>
                </button>
              </li>
            )
          })}
          {options.data?.length === 0 && <li className="muted">No matching items.</li>}
        </ul>
      )}
    </div>
  )
}

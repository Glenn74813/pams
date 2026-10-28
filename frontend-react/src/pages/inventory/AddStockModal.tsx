import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, supplierApi } from '../../api/endpoints'
import type { Item } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatQty, today } from '../../lib/format'
import { LotFields } from './LotFields'
import { emptyLot, lotToInput } from './model'

interface Props {
  item: Item
  onClose: () => void
  onSaved: (message: string) => void
}

/** Records a receipt of stock (a new lot) against an item. */
export function AddStockModal({ item, onClose, onSaved }: Props) {
  const [lot, setLot] = useState(() => emptyLot(today()))
  const [error, setError] = useState<string | null>(null)
  const suppliers = useQuery({ queryKey: ['supplier-options'], queryFn: supplierApi.options })
  const locations = useQuery({ queryKey: ['locations'], queryFn: inventoryApi.locations })

  const save = useMutation({
    mutationFn: () => inventoryApi.addStock(item.id, lotToInput(lot)),
    onSuccess: () => onSaved(`Received ${formatQty(Number(lot.quantity))} ${item.unit_of_measure} of ${item.name}.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not record the stock.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <Modal title={`Add stock — ${item.name}`} onClose={onClose} width={760}>
      <form onSubmit={submit}>
        <p className="muted" style={{ marginBottom: '1rem' }}>
          {item.code} · {item.classification_label} · on hand {formatQty(item.current_stock)} {item.unit_of_measure}
        </p>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field" style={{ maxWidth: 220, marginBottom: '1rem' }}>
          <label htmlFor="as-date">Received date *</label>
          <input id="as-date" type="date" value={lot.received_date} max={today()} onChange={(e) => setLot({ ...lot, received_date: e.target.value })} required />
        </div>
        <LotFields
          idPrefix="as"
          lot={lot}
          onChange={setLot}
          suppliers={suppliers.data?.data ?? []}
          locations={locations.data ?? []}
        />
        <p className="hint" style={{ marginTop: '0.75rem' }}>
          Deliveries inspected in Receiving are still recorded in the current system until the receiving module moves.
        </p>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Record stock'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

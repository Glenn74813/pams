import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, supplierApi, type ItemInput } from '../../api/endpoints'
import type { Classification, Item } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatPeso } from '../../lib/format'
import { LotFields } from './LotFields'
import { CLASSIFICATIONS, emptyLot, lotFromStock, lotToInput, type LotDraft } from './model'

interface Props {
  /** The item to edit, with its stock lots loaded; null to create. */
  item: Item | null
  onClose: () => void
  onSaved: (item: Item, message: string) => void
}

export function ItemFormModal({ item, onClose, onSaved }: Props) {
  const editing = item !== null
  const [form, setForm] = useState({
    code: item?.code ?? '',
    name: item?.name ?? '',
    description: item?.description ?? '',
    category_id: item?.category_id ? String(item.category_id) : '',
    unit_of_measure: item?.unit_of_measure ?? '',
    min_stock: String(item?.min_stock ?? 0),
    max_stock: item?.max_stock != null ? String(item.max_stock) : '',
    classification: (item?.classification ?? 'consumable') as Classification,
    is_assembly: item?.is_assembly ?? false,
  })
  const [lots, setLots] = useState<LotDraft[]>(() => (item?.stocks ?? []).map(lotFromStock))
  const [editLots, setEditLots] = useState(false)
  const [errors, setErrors] = useState<Record<string, string[]>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const categories = useQuery({ queryKey: ['categories'], queryFn: inventoryApi.categories })
  const config = useQuery({ queryKey: ['inventory-config'], queryFn: inventoryApi.config })
  const suppliers = useQuery({ queryKey: ['supplier-options'], queryFn: supplierApi.options })
  const locations = useQuery({ queryKey: ['locations'], queryFn: inventoryApi.locations })

  const sendLots = !editing || editLots
  const save = useMutation({
    mutationFn: () => {
      const input: ItemInput = {
        code: form.code.trim() || undefined,
        name: form.name,
        description: form.description,
        category_id: Number(form.category_id),
        unit_of_measure: form.unit_of_measure,
        min_stock: Number(form.min_stock) || 0,
        max_stock: Number(form.max_stock) || 0,
        classification: form.classification,
        is_assembly: form.is_assembly,
        stock_entries: sendLots ? lots.map(lotToInput) : undefined,
      }
      return editing ? inventoryApi.updateItem(item.id, input) : inventoryApi.createItem(input)
    },
    onSuccess: (saved) => onSaved(saved, editing ? 'Item updated.' : `Item ${saved.code} created.`),
    onError: (err) => {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) {
        setErrors(err.fieldErrors)
        setFormError(Object.values(err.fieldErrors).flat()[0] ?? null)
      } else {
        setErrors({})
        setFormError(err instanceof Error ? err.message : 'Could not save the item.')
      }
    },
  })

  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))
  const err = (k: string) => errors[k]?.[0]
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setErrors({})
    setFormError(null)
    save.mutate()
  }

  const parents = (categories.data ?? []).filter((c) => c.parent_id === null)
  const childrenOf = (id: number) => (categories.data ?? []).filter((c) => c.parent_id === id)
  const threshold = config.data?.capitalization_threshold

  return (
    <Modal title={editing ? `Edit ${item.code}` : 'Add New Item'} onClose={onClose} width={880}>
      <form onSubmit={submit}>
        {formError && <div className="alert alert-error">{formError}</div>}

        <div className="form-grid three">
          <div className={'field' + (err('code') ? ' invalid' : '')}>
            <label htmlFor="i-code">Item code</label>
            <input id="i-code" value={form.code} onChange={set('code')} placeholder={editing ? '' : 'Leave blank to number automatically'} maxLength={255} />
          </div>
          <div className={'field span-2' + (err('name') ? ' invalid' : '')}>
            <label htmlFor="i-name">Name *</label>
            <input id="i-name" value={form.name} onChange={set('name')} required maxLength={255} />
          </div>
          <div className={'field' + (err('category_id') ? ' invalid' : '')}>
            <label htmlFor="i-cat">Category *</label>
            <select id="i-cat" value={form.category_id} onChange={set('category_id')} required>
              <option value="">Select category</option>
              {parents.map((p) => {
                const kids = childrenOf(p.id)
                return kids.length ? (
                  <optgroup key={p.id} label={p.name}>
                    {kids.map((k) => (
                      <option key={k.id} value={k.id}>
                        {k.name}
                      </option>
                    ))}
                  </optgroup>
                ) : (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                )
              })}
            </select>
            {categories.data?.length === 0 && <span className="hint">No categories yet — add one on the Categories tab.</span>}
          </div>
          <div className={'field' + (err('unit_of_measure') ? ' invalid' : '')}>
            <label htmlFor="i-uom">Unit of measure *</label>
            <input id="i-uom" value={form.unit_of_measure} onChange={set('unit_of_measure')} required placeholder="e.g. ream, piece, box" maxLength={255} />
          </div>
          <div className="field">
            <label htmlFor="i-min">Reorder point (min)</label>
            <input id="i-min" type="number" min={0} value={form.min_stock} onChange={set('min_stock')} />
          </div>
          <div className="field">
            <label htmlFor="i-max">Maximum stock</label>
            <input id="i-max" type="number" min={0} value={form.max_stock} onChange={set('max_stock')} placeholder="No maximum" />
            {err('max_stock') && <span className="field-error">{err('max_stock')}</span>}
          </div>
          <div className="field span-2">
            <label htmlFor="i-desc">Description</label>
            <input id="i-desc" value={form.description} onChange={set('description')} />
          </div>
        </div>

        <fieldset className="classification">
          <legend>COA classification *</legend>
          <div className="classification-options">
            {CLASSIFICATIONS.map((c) => (
              <label key={c.value} className={'class-option' + (form.classification === c.value ? ' selected' : '')}>
                <input
                  type="radio"
                  name="classification"
                  value={c.value}
                  checked={form.classification === c.value}
                  onChange={() => setForm((f) => ({ ...f, classification: c.value }))}
                />
                <span>
                  <strong>{c.label}</strong>
                  <small>{c.hint}</small>
                </span>
              </label>
            ))}
          </div>
          {threshold != null && form.classification !== 'consumable' && (
            <p className="hint">Capitalization threshold: {formatPeso(threshold)} per unit.</p>
          )}
          <label className="checkbox-line">
            <input type="checkbox" checked={form.is_assembly} onChange={(e) => setForm((f) => ({ ...f, is_assembly: e.target.checked }))} />
            Assembly (e.g. a clone PC) — classified by its total assembled cost, not each part's cost
          </label>
        </fieldset>

        <section className="lots-section">
          <div className="lots-head">
            <h4>{editing ? 'Stock lots' : 'Opening stock (optional)'}</h4>
            {editing ? (
              <label className="checkbox-line">
                <input type="checkbox" checked={editLots} onChange={(e) => setEditLots(e.target.checked)} /> Edit stock lots
              </label>
            ) : (
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setLots((l) => [...l, emptyLot()])}>
                + Add lot
              </button>
            )}
          </div>
          {editing && editLots && (
            <div className="alert alert-info">
              Quantity changes made here are recorded on the stock card as an adjustment. For receipts use <em>Add stock</em>.
            </div>
          )}
          {editing && !editLots && lots.length > 0 && (
            <p className="muted">
              {lots.length} lot{lots.length === 1 ? '' : 's'}, {lots.reduce((s, l) => s + (Number(l.quantity) || 0), 0)} {form.unit_of_measure} on hand.
            </p>
          )}
          {sendLots &&
            lots.map((lot, i) => (
              <div key={lot.key} className="lot-card">
                <div className="lot-card-head">
                  <span>{lot.id ? `Lot #${lot.id}` : `New lot ${i + 1}`}</span>
                  <button type="button" className="btn btn-danger btn-sm" onClick={() => setLots((l) => l.filter((x) => x.key !== lot.key))}>
                    Remove
                  </button>
                </div>
                <LotFields
                  idPrefix={`lot-${lot.key}`}
                  lot={lot}
                  onChange={(next) => setLots((l) => l.map((x) => (x.key === lot.key ? next : x)))}
                  suppliers={suppliers.data?.data ?? []}
                  locations={locations.data ?? []}
                  showReceivedDate
                />
              </div>
            ))}
          {editing && editLots && (
            <button type="button" className="btn btn-outline btn-sm" onClick={() => setLots((l) => [...l, emptyLot()])}>
              + Add lot
            </button>
          )}
        </section>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create item'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { physicalCountApi, type CountInput } from '../../api/endpoints'
import type { CountDetail, PhysicalCount } from '../../api/types'
import { Modal } from '../../components/Modal'
import { today } from '../../lib/format'
import { COUNT_TYPE } from './labels'

export function CountFormModal({ count, onClose, onSaved }: { count: PhysicalCount | null; onClose: () => void; onSaved: (d: CountDetail, m: string) => void }) {
  const editing = count !== null
  const options = useQuery({ queryKey: ['count-options'], queryFn: physicalCountApi.options })
  const [f, setF] = useState<CountInput>(() => ({
    count_type: count?.count_type ?? 'supplies',
    count_date: count?.count_date ?? today(),
    department_id: count?.department?.id ?? null,
    category_id: count?.category?.id ?? null,
    purpose: count?.purpose ?? '',
    remarks: count?.remarks ?? '',
  }))
  const [error, setError] = useState<string | null>(null)
  const set = (patch: Partial<CountInput>) => setF((x) => ({ ...x, ...patch }))
  const supplies = f.count_type === 'supplies'
  const save = useMutation({
    mutationFn: () => {
      const input = { ...f, department_id: supplies ? null : f.department_id, category_id: supplies ? f.category_id : null }
      return editing ? physicalCountApi.update(count.id, input) : physicalCountApi.create(input)
    },
    onSuccess: (d) => onSaved(d, editing ? `${d.count.count_number} saved.` : `${d.count.count_number} created. Start it when counting begins; that freezes the book balances.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the count.'),
  })
  return (
    <Modal title={editing ? `Edit ${count.count_number}` : 'New physical count'} onClose={onClose} width={640}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field">
            <label htmlFor="pc-type">What is counted *</label>
            <select id="pc-type" value={f.count_type} onChange={(e) => set({ count_type: e.target.value as CountInput['count_type'] })} disabled={editing}>
              {Object.entries(COUNT_TYPE).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="pc-date">Count date *</label>
            <input id="pc-date" type="date" value={f.count_date} onChange={(e) => set({ count_date: e.target.value })} required />
          </div>
          {supplies ? (
            <div className="field span-2">
              <label htmlFor="pc-cat">Category</label>
              <select id="pc-cat" value={f.category_id ?? ''} onChange={(e) => set({ category_id: e.target.value ? Number(e.target.value) : null })}>
                <option value="">All items in the stock room</option>
                {options.data?.categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
              <span className="hint">Subcategories are included. Leave blank for a full count.</span>
            </div>
          ) : (
            <div className="field span-2">
              <label htmlFor="pc-dep">Department</label>
              <select id="pc-dep" value={f.department_id ?? ''} onChange={(e) => set({ department_id: e.target.value ? Number(e.target.value) : null })}>
                <option value="">Every department (and unassigned assets)</option>
                {options.data?.departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
              <span className="hint">The masterlist is the assets held by people in this department when the count starts.</span>
            </div>
          )}
          <div className="field span-2">
            <label htmlFor="pc-purpose">Purpose</label>
            <input id="pc-purpose" value={f.purpose ?? ''} onChange={(e) => set({ purpose: e.target.value })} placeholder="e.g. Year-end count, turnover of custodian" />
          </div>
          <div className="field span-2">
            <label htmlFor="pc-remarks">Remarks</label>
            <input id="pc-remarks" value={f.remarks ?? ''} onChange={(e) => set({ remarks: e.target.value })} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save' : 'Create'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

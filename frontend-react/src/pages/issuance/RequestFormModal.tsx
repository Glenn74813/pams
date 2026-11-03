import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { requestsApi, usersApi, type RequestInput } from '../../api/endpoints'
import type { SupplyRequest } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ItemPicker } from '../../components/ItemPicker'
import { Modal } from '../../components/Modal'
import { formatQty } from '../../lib/format'

interface Line {
  item_id: number
  code: string | null
  name: string
  unit: string | null
  stock: number
  quantity: string
}

interface Props {
  request: SupplyRequest | null
  onClose: () => void
  onSaved: (r: SupplyRequest, message: string) => void
}

export function RequestFormModal({ request, onClose, onSaved }: Props) {
  const { user } = useAuth()
  const editing = request !== null
  const [departmentId, setDepartmentId] = useState(String(request?.department?.id ?? user?.department_id ?? ''))
  const [priority, setPriority] = useState<RequestInput['priority']>(request?.priority ?? 'medium')
  const [purpose, setPurpose] = useState(request?.purpose ?? '')
  const [custodian, setCustodian] = useState(request?.custodian_name ?? '')
  const [lines, setLines] = useState<Line[]>(
    () =>
      request?.items.map((l) => ({
        item_id: l.item_id,
        code: l.code,
        name: l.name,
        unit: l.unit_of_measure,
        stock: l.current_stock,
        quantity: String(l.quantity),
      })) ?? [],
  )
  const [error, setError] = useState<string | null>(null)
  const departments = useQuery({ queryKey: ['departments'], queryFn: usersApi.departments })

  const save = useMutation({
    mutationFn: () => {
      const input: RequestInput = {
        department_id: departmentId ? Number(departmentId) : undefined,
        purpose,
        priority,
        custodian_name: custodian,
        items: lines.map((l) => ({ item_id: l.item_id, quantity: Number(l.quantity) || 0 })),
      }
      return editing ? requestsApi.update(request.id, input) : requestsApi.create(input)
    },
    onSuccess: (r) => onSaved(r, editing ? 'Request updated.' : `Request ${r.request_number} submitted.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the request.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (lines.length === 0) {
      setError('Add at least one item.')
      return
    }
    save.mutate()
  }

  return (
    <Modal title={editing ? `Edit ${request.request_number}` : 'New Supply Request'} onClose={onClose} width={820}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid three">
          <div className="field">
            <label htmlFor="rq-dept">Requesting office *</label>
            <select id="rq-dept" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} required>
              {departments.data?.data.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="rq-pri">Priority *</label>
            <select id="rq-pri" value={priority} onChange={(e) => setPriority(e.target.value as RequestInput['priority'])}>
              <option value="low">Low</option>
              <option value="medium">Medium</option>
              <option value="high">High</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="rq-cust">Accountable person</label>
            <input id="rq-cust" value={custodian} onChange={(e) => setCustodian(e.target.value)} placeholder={user?.name} maxLength={255} />
            <span className="hint">Holds semi-expendable items (ICS). Defaults to you.</span>
          </div>
          <div className="field span-3">
            <label htmlFor="rq-purpose">Purpose *</label>
            <textarea id="rq-purpose" rows={2} value={purpose} onChange={(e) => setPurpose(e.target.value)} required />
          </div>
        </div>

        <h4>Items *</h4>
        <ItemPicker id="rq-item" requireStock exclude={lines.map((l) => l.item_id)} onPick={(i) =>
          setLines((ls) => [...ls, { item_id: i.id, code: i.code, name: i.name, unit: i.unit_of_measure, stock: i.current_stock, quantity: '1' }])
        } />
        {lines.length > 0 && (
          <div className="table-wrap" style={{ marginTop: '0.75rem' }}>
            <table className="table table-compact">
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="num">In stock</th>
                  <th className="num">Quantity</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {lines.map((l) => (
                  <tr key={l.item_id}>
                    <td>
                      <span className="mono">{l.code}</span> {l.name}
                    </td>
                    <td className="num">
                      {formatQty(l.stock)} <span className="muted">{l.unit}</span>
                    </td>
                    <td className="num">
                      <input
                        className="qty-input"
                        type="number"
                        min={1}
                        value={l.quantity}
                        aria-label={`Quantity of ${l.name}`}
                        onChange={(e) => setLines((ls) => ls.map((x) => (x.item_id === l.item_id ? { ...x, quantity: e.target.value } : x)))}
                        required
                      />
                    </td>
                    <td>
                      <button type="button" className="icon-btn delete" title={`Remove ${l.name}`} onClick={() => setLines((ls) => ls.filter((x) => x.item_id !== l.item_id))}>
                        ×
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint" style={{ marginTop: '0.5rem' }}>
          Stock is only taken when the request is approved; you may ask for more than is in stock and receive part of it.
        </p>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Submit request'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

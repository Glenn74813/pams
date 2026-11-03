import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { planningApi, prApi, usersApi, type PrInput } from '../../api/endpoints'
import type { ProcurementOptions, PurchaseRequest } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { formatPeso, formatQty } from '../../lib/format'
import { LineEditor } from './LineEditor'
import { blankLine, lineTotal, newKey, toInput, type EditLine } from './lines'

interface Props {
  pr: PurchaseRequest | null
  options?: ProcurementOptions
  onClose: () => void
  onSaved: (p: PurchaseRequest, message: string) => void
}

export function PrFormModal({ pr, options, onClose, onSaved }: Props) {
  const { user, can } = useAuth()
  const editing = pr !== null
  const staff = can('edit_procurement', 'view_procurement')
  const [departmentId, setDepartmentId] = useState(String(pr?.department.id ?? user?.department_id ?? ''))
  const [appId, setAppId] = useState(pr?.app ? String(pr.app.id) : '')
  const [priority, setPriority] = useState<PrInput['priority']>(pr?.priority ?? 'medium')
  const [purpose, setPurpose] = useState(pr?.purpose ?? '')
  const [remarks, setRemarks] = useState(pr?.remarks ?? '')
  const [lines, setLines] = useState<EditLine[]>(() =>
    pr?.items?.map((l) => ({
      key: newKey(),
      app_item_id: l.app_item_id,
      general_description: l.general_description,
      specifications: l.specifications ?? '',
      code: '',
      unit: l.unit ?? '',
      quantity: String(l.quantity),
      estimated_unit_cost: String(l.estimated_unit_cost),
      procurement_mode: l.procurement_mode ?? '',
      procurement_category: l.procurement_category,
      schedule: '',
      fund_source: l.fund_source ?? '',
      is_sustainable: l.is_sustainable,
    })) ?? [],
  )
  const [error, setError] = useState<string | null>(null)
  const departments = useQuery({ queryKey: ['departments'], queryFn: usersApi.departments, enabled: staff })
  const apps = useQuery({ queryKey: ['apps', 'approved'], queryFn: () => planningApi.apps({ page: 1, status: 'approved' }) })
  const app = useQuery({ queryKey: ['app', Number(appId), pr?.id], queryFn: () => planningApi.app(Number(appId), pr?.id), enabled: appId !== '' })

  const save = useMutation({
    mutationFn: () => {
      const input: PrInput = {
        department_id: departmentId ? Number(departmentId) : undefined,
        app_id: appId ? Number(appId) : null,
        purpose,
        priority,
        remarks: remarks || null,
        items: lines.map(toInput),
      }
      return editing ? prApi.update(pr.id, input) : prApi.create(input)
    },
    onSuccess: (p) => onSaved(p, editing ? `${p.pr_number} saved.` : `${p.pr_number} saved as a draft.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the purchase request.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (lines.length === 0) return setError('Add at least one line.')
    save.mutate()
  }
  const changeApp = (v: string) => {
    setAppId(v)
    // Lines drawn on the old APP no longer apply.
    setLines((ls) => ls.filter((l) => !l.app_item_id))
  }
  const taken = new Set(lines.map((l) => l.app_item_id).filter(Boolean))
  const open = app.data?.items?.filter((l) => l.remaining_quantity > 0 && !taken.has(l.id) && l.department?.id === Number(departmentId)) ?? []
  const total = lines.reduce((s, l) => s + lineTotal(l), 0)

  return (
    <Modal title={editing ? `Edit ${pr.pr_number}` : 'New Purchase Request'} onClose={onClose} width={900}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        {pr?.return_reason && <div className="alert alert-warning">Returned: {pr.return_reason}</div>}
        <div className="form-grid three">
          <div className="field">
            <label htmlFor="pr-dept">Requesting office *</label>
            {staff ? (
              <select id="pr-dept" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} required disabled={editing} title={editing ? "The requesting office can't be changed after the PR is created" : undefined}>
                {departments.data?.data.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            ) : (
              <div className="readonly">{pr?.department.name ?? user?.department?.name}</div>
            )}
          </div>
          <div className="field">
            <label htmlFor="pr-app">Annual Procurement Plan</label>
            <select id="pr-app" value={appId} onChange={(e) => changeApp(e.target.value)}>
              <option value="">Not in the APP</option>
              {apps.data?.data.map((a) => (
                <option key={a.id} value={a.id} disabled={a.is_fully_requested && String(a.id) !== appId}>
                  {a.app_number} — FY {a.fiscal_year}
                  {a.is_fully_requested ? ' (fully requested)' : ''}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="pr-pri">Priority *</label>
            <select id="pr-pri" value={priority} onChange={(e) => setPriority(e.target.value as PrInput['priority'])}>
              <option value="low">Low</option>
              <option value="medium">Medium</option>
              <option value="high">High</option>
            </select>
          </div>
          <div className="field span-3">
            <label htmlFor="pr-purpose">Purpose *</label>
            <textarea id="pr-purpose" rows={2} value={purpose} onChange={(e) => setPurpose(e.target.value)} required maxLength={500} />
          </div>
          <div className="field span-3">
            <label htmlFor="pr-rem">Remarks</label>
            <input id="pr-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>

        {appId && (
          <>
            <h4>Add from {app.data?.app_number ?? 'the APP'}</h4>
            {app.isPending && <p className="muted">Loading APP lines…</p>}
            {app.data && open.length === 0 && <p className="muted">No APP lines with a balance left for this office.</p>}
            {open.length > 0 && (
              <div className="app-line-pick">
                {open.map((l) => (
                  <button
                    key={l.id}
                    type="button"
                    className="btn btn-outline btn-sm"
                    onClick={() =>
                      setLines((ls) => [
                        ...ls,
                        blankLine({
                          app_item_id: l.id,
                          app_hint: `APP: ${formatQty(l.remaining_quantity)} of ${formatQty(l.quantity)} left`,
                          general_description: l.general_description,
                          unit: l.unit ?? '',
                          quantity: String(l.remaining_quantity),
                          estimated_unit_cost: String(l.unit_cost),
                          procurement_category: l.procurement_category,
                        }),
                      ])
                    }
                  >
                    <span>
                      + {l.general_description} <span className="muted">· {l.procurement_mode}</span>
                    </span>
                    <span className="muted">
                      {formatQty(l.remaining_quantity)} {l.unit} left · {formatPeso(l.estimated_budget - l.requested_amount)}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </>
        )}

        <div className="section-header" style={{ marginTop: '1.25rem' }}>
          <h4>Lines</h4>
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setLines((ls) => [...ls, blankLine()])}>
            + Line not in the APP
          </button>
        </div>
        {lines.length === 0 && <p className="muted">No lines yet.</p>}
        <LineEditor lines={lines} onChange={setLines} options={options} />
        <p className="form-total">
          Total estimated cost <strong>{formatPeso(total)}</strong>
        </p>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Save draft'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

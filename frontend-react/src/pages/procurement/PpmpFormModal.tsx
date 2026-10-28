import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { planningApi, usersApi, type PpmpInput } from '../../api/endpoints'
import type { Ppmp, ProcurementOptions } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { formatPeso } from '../../lib/format'
import { fiscalYears, thisYear } from './labels'
import { LineEditor } from './LineEditor'
import { blankLine, lineTotal, newKey, toInput, type EditLine } from './lines'

interface Props {
  ppmp: Ppmp | null
  options?: ProcurementOptions
  onClose: () => void
  onSaved: (p: Ppmp, message: string) => void
}

export function PpmpFormModal({ ppmp, options, onClose, onSaved }: Props) {
  const { user, can } = useAuth()
  const editing = ppmp !== null
  const agencyWide = can('view_procurement_planning', 'approve_ppmp', 'manage_app')
  const [departmentId, setDepartmentId] = useState(String(ppmp?.department.id ?? user?.department_id ?? ''))
  const [year, setYear] = useState(String(ppmp?.fiscal_year ?? thisYear() + 1))
  const [title, setTitle] = useState(ppmp?.title ?? '')
  const [remarks, setRemarks] = useState(ppmp?.remarks ?? '')
  const [lines, setLines] = useState<EditLine[]>(() =>
    ppmp?.items?.map((l) => ({
      key: newKey(),
      general_description: l.general_description,
      specifications: l.specifications ?? '',
      code: l.code ?? '',
      unit: l.unit ?? '',
      quantity: String(l.quantity),
      estimated_unit_cost: String(l.estimated_unit_cost),
      procurement_mode: l.procurement_mode,
      procurement_category: l.procurement_category,
      schedule: l.schedule ?? '',
      fund_source: l.fund_source ?? '',
      is_sustainable: l.is_sustainable,
    })) ?? [blankLine()],
  )
  const [error, setError] = useState<string | null>(null)
  const departments = useQuery({ queryKey: ['departments'], queryFn: usersApi.departments, enabled: agencyWide && !editing })

  const save = useMutation({
    mutationFn: () => {
      const input: PpmpInput = {
        department_id: departmentId ? Number(departmentId) : undefined,
        fiscal_year: Number(year),
        title,
        remarks: remarks || null,
        items: lines.map(toInput),
      }
      return editing ? planningApi.updatePpmp(ppmp.id, input) : planningApi.createPpmp(input)
    },
    onSuccess: (p) => onSaved(p, editing ? `${p.ppmp_number} saved.` : `${p.ppmp_number} created as a draft.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the PPMP.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (lines.length === 0) return setError('Add at least one line.')
    save.mutate()
  }
  const total = lines.reduce((s, l) => s + lineTotal(l), 0)

  return (
    <Modal title={editing ? `Edit ${ppmp.ppmp_number}` : 'New PPMP'} onClose={onClose} width={900}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        {ppmp?.return_reason && <div className="alert alert-warning">Returned for revision: {ppmp.return_reason}</div>}
        <div className="form-grid three">
          <div className="field">
            <label htmlFor="pp-dept">Department *</label>
            {agencyWide && !editing ? (
              <select id="pp-dept" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} required>
                {departments.data?.data.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            ) : (
              <div className="readonly">{ppmp?.department.name ?? user?.department?.name}</div>
            )}
          </div>
          <div className="field">
            <label htmlFor="pp-year">Fiscal year *</label>
            {editing ? (
              <div className="readonly">{ppmp.fiscal_year}</div>
            ) : (
              <select id="pp-year" value={year} onChange={(e) => setYear(e.target.value)}>
                {fiscalYears().map((y) => (
                  <option key={y}>{y}</option>
                ))}
              </select>
            )}
          </div>
          <div className="field">
            <label htmlFor="pp-title">Title *</label>
            <input id="pp-title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} placeholder="e.g. ICT equipment and office supplies" />
          </div>
          <div className="field span-3">
            <label htmlFor="pp-rem">Remarks</label>
            <input id="pp-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>

        <div className="section-header" style={{ marginTop: '1.25rem' }}>
          <h4>Planned procurement</h4>
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setLines((ls) => [...ls, blankLine()])}>
            + Add line
          </button>
        </div>
        <LineEditor lines={lines} onChange={setLines} options={options} planning />
        <p className="form-total">
          Estimated budget <strong>{formatPeso(total)}</strong>
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

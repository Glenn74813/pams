import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { supplierApi, type SupplierInput } from '../../api/endpoints'
import type { Supplier } from '../../api/types'
import { Modal } from '../../components/Modal'

export function SupplierFormModal({ supplier, onClose, onSaved }: { supplier: Supplier | null; onClose: () => void; onSaved: (s: Supplier, m: string) => void }) {
  const editing = supplier !== null
  const [f, setF] = useState<SupplierInput>(() => ({
    name: supplier?.name ?? '',
    vendor_code: supplier?.vendor_code ?? '',
    tin: supplier?.tin ?? '',
    philgeps_reg_number: supplier?.philgeps_reg_number ?? '',
    contact_person: supplier?.contact_person ?? '',
    email: supplier?.email ?? '',
    phone: supplier?.phone ?? '',
    mobile_number: supplier?.mobile_number ?? '',
    address: supplier?.address ?? '',
    website: supplier?.website ?? '',
    credit_terms: supplier?.credit_terms ?? '',
    notes: supplier?.notes ?? '',
    is_msme: supplier?.is_msme ?? false,
    is_active: supplier?.is_active ?? true,
  }))
  const [error, setError] = useState<string | null>(null)
  const set = (patch: Partial<SupplierInput>) => setF((x) => ({ ...x, ...patch }))
  const save = useMutation({
    mutationFn: () => (editing ? supplierApi.update(supplier.id, f) : supplierApi.create(f)),
    onSuccess: (s) => onSaved(s, editing ? `${s.name} saved.` : `${s.name} registered as ${s.vendor_code}. Record its documents to make it eligible.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the supplier.'),
  })
  const field = (key: keyof SupplierInput, label: string, props: Record<string, unknown> = {}) => (
    <div className="field" key={key}>
      <label htmlFor={`sp-${key}`}>{label}</label>
      <input id={`sp-${key}`} value={(f[key] as string) ?? ''} onChange={(e) => set({ [key]: e.target.value })} {...props} />
    </div>
  )
  return (
    <Modal title={editing ? `Edit ${supplier.name}` : 'Register supplier'} onClose={onClose} width={760}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); setError(null); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid three">
          <div className="field span-2">
            <label htmlFor="sp-name">Registered name *</label>
            <input id="sp-name" value={f.name ?? ''} onChange={(e) => set({ name: e.target.value })} required maxLength={255} />
          </div>
          {field('vendor_code', 'Vendor code', { placeholder: 'Assigned if blank' })}
          {field('tin', 'TIN', { placeholder: '123-456-789-000' })}
          {field('philgeps_reg_number', 'PhilGEPS reg. no.')}
          {field('credit_terms', 'Credit terms', { placeholder: 'e.g. 30 days' })}
          {field('contact_person', 'Contact person')}
          {field('email', 'Email', { type: 'email' })}
          {field('phone', 'Phone')}
          {field('mobile_number', 'Mobile')}
          {field('website', 'Website', { placeholder: 'https://' })}
          <div className="field span-3">
            <label htmlFor="sp-address">Address</label>
            <input id="sp-address" value={f.address ?? ''} onChange={(e) => set({ address: e.target.value })} />
          </div>
          <div className="field span-3">
            <label htmlFor="sp-notes">Notes</label>
            <input id="sp-notes" value={f.notes ?? ''} onChange={(e) => set({ notes: e.target.value })} />
          </div>
          <label className="checkbox-line">
            <input type="checkbox" checked={!!f.is_msme} onChange={(e) => set({ is_msme: e.target.checked })} /> MSME
          </label>
          {editing && (
            <label className="checkbox-line">
              <input type="checkbox" checked={!!f.is_active} onChange={(e) => set({ is_active: e.target.checked })} /> Active
            </label>
          )}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : editing ? 'Save' : 'Register'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

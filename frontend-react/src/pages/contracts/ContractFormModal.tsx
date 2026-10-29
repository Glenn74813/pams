import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { contractApi, type ContractInput } from '../../api/endpoints'
import type { Contract } from '../../api/types'
import { Modal } from '../../components/Modal'
import { formatDate, formatPeso, today } from '../../lib/format'
import { CONTRACT_TYPES } from './labels'

interface Props {
  contract: Contract | null
  onClose: () => void
  onSaved: (c: Contract, message: string) => void
}

export function ContractFormModal({ contract, onClose, onSaved }: Props) {
  const editing = contract !== null
  const sources = useQuery({ queryKey: ['contract-sources'], queryFn: contractApi.sources, enabled: !editing })
  const [key, setKey] = useState('')
  const [title, setTitle] = useState(contract?.title ?? '')
  const [type, setType] = useState(contract?.contract_type ?? 'Goods')
  const [start, setStart] = useState(contract?.start_date ?? '')
  const [end, setEnd] = useState(contract?.end_date ?? '')
  const [signed, setSigned] = useState(contract?.signed_date ?? '')
  const [days, setDays] = useState(contract?.delivery_lead_days != null ? String(contract.delivery_lead_days) : '')
  const [terms, setTerms] = useState(contract?.terms ?? '')
  const [description, setDescription] = useState(contract?.description ?? '')
  const [error, setError] = useState<string | null>(null)
  const src = sources.data?.data.find((s) => `${s.type}:${s.id}` === key)
  const choose = (k: string) => {
    setKey(k)
    const s = sources.data?.data.find((x) => `${x.type}:${x.id}` === k)
    if (s) {
      setTitle(s.title)
      if (s.delivery_lead_days != null) setDays(String(s.delivery_lead_days))
      if (s.ntp_date && !start) setStart(s.ntp_date)
    }
  }
  const save = useMutation({
    mutationFn: () => {
      const input: ContractInput = {
        title,
        contract_type: type,
        start_date: start || null,
        end_date: end || null,
        signed_date: signed || null,
        delivery_lead_days: days === '' ? null : Number(days),
        terms: terms || null,
        description: description || null,
      }
      return editing ? contractApi.update(contract.id, input) : contractApi.create({ ...input, source_type: src!.type, source_id: src!.id })
    },
    onSuccess: (c) => onSaved(c, editing ? `${c.contract_number} saved.` : `${c.contract_number} drafted.`),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the contract.'),
  })
  return (
    <Modal title={editing ? `Edit ${contract.contract_number}` : 'New contract'} onClose={onClose} width={760}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          setError(null)
          if (!editing && !src) return setError('Choose the award.')
          save.mutate()
        }}
      >
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid three">
          <div className="field span-3">
            <label htmlFor="ct-src">Award *</label>
            {editing ? (
              <div className="readonly">
                {contract.source?.name} · {contract.supplier.name} · {formatPeso(contract.amount)}
              </div>
            ) : (
              <select id="ct-src" value={key} onChange={(e) => choose(e.target.value)} required>
                <option value="">{sources.isPending ? 'Loading…' : sources.data?.data.length ? 'Choose' : 'No awards with a Notice of Award waiting for a contract'}</option>
                {sources.data?.data.map((s) => (
                  <option key={`${s.type}:${s.id}`} value={`${s.type}:${s.id}`}>
                    {s.number} — {s.supplier.name} — {formatPeso(s.amount)}
                    {s.purchase_order ? ` (${s.purchase_order.name})` : ''}
                  </option>
                ))}
              </select>
            )}
            {src?.ntp_date && <span className="hint">Notice to Proceed {formatDate(src.ntp_date)}.</span>}
          </div>
          <div className="field span-2">
            <label htmlFor="ct-title">Title *</label>
            <input id="ct-title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} />
          </div>
          <div className="field">
            <label htmlFor="ct-type">Type *</label>
            <select id="ct-type" value={type} onChange={(e) => setType(e.target.value)}>
              {CONTRACT_TYPES.map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="ct-start">Effective from</label>
            <input id="ct-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="ct-end">Ends on</label>
            <input id="ct-end" type="date" value={end} min={start} onChange={(e) => setEnd(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="ct-signed">Signed on</label>
            <input id="ct-signed" type="date" value={signed} max={today()} onChange={(e) => setSigned(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="ct-days">Delivery within (days)</label>
            <input id="ct-days" type="number" min={0} value={days} onChange={(e) => setDays(e.target.value)} />
          </div>
          <div className="field span-3">
            <label htmlFor="ct-desc">Scope</label>
            <textarea id="ct-desc" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <div className="field span-3">
            <label htmlFor="ct-terms">Special conditions</label>
            <textarea id="ct-terms" rows={3} value={terms} onChange={(e) => setTerms(e.target.value)} />
          </div>
        </div>
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

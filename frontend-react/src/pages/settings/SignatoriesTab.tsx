import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { settingsApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/toast-context'

/** Who signs each COA form role; their uploaded signature is printed on the documents. */
export function SignatoriesTab() {
  const { can } = useAuth()
  const editable = can('edit_settings', 'manage_signatories')
  const toast = useToast()
  const queryClient = useQueryClient()
  const q = useQuery({ queryKey: ['settings-signatories'], queryFn: settingsApi.signatories })
  const [draft, setDraft] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const value = (key: string, current: number | null) => draft[key] ?? (current == null ? '' : String(current))
  const save = useMutation({
    mutationFn: () => settingsApi.updateSignatories(Object.entries(draft).map(([role_key, v]) => ({ role_key, user_id: v ? Number(v) : null }))),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setDraft({})
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['settings-signatories'] })
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  const people = q.data?.users ?? []
  return (
    <section className="settings-card wide">
      <h2>Authorized signatories</h2>
      <p className="small muted">Each person uploads their own signature under My account. Roles without a signature print a blank line to sign by hand.</p>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Role</th>
              <th>Signatory</th>
              <th>Signature</th>
            </tr>
          </thead>
          <tbody>
            {q.data?.signatories.map((s) => {
              const v = value(s.role_key, s.user_id)
              const person = people.find((p) => String(p.id) === v)
              return (
                <tr key={s.role_key}>
                  <td>{s.label}</td>
                  <td>
                    {editable ? (
                      <select value={v} onChange={(e) => setDraft({ ...draft, [s.role_key]: e.target.value })} aria-label={s.label}>
                        <option value="">— Not assigned —</option>
                        {people.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name}
                          </option>
                        ))}
                      </select>
                    ) : (
                      (s.user_name ?? <span className="muted">Not assigned</span>)
                    )}
                  </td>
                  <td>{!v ? '—' : person?.has_signature ? <span className="badge st-ok">On file</span> : <span className="badge st-pending">None uploaded</span>}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {editable && (
        <button className="btn btn-primary" onClick={() => save.mutate()} disabled={!Object.keys(draft).length || save.isPending}>
          {save.isPending ? 'Saving…' : 'Save signatories'}
        </button>
      )}
    </section>
  )
}

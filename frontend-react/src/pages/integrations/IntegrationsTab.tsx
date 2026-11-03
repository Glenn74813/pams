import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { integrationsApi, type ApiColumn, type ApiData, type ExternalApi, type ExternalApiInput } from '../../api/integrations'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { formatDateTime } from '../../lib/format'
import { ApiDataTable } from './ApiDataTable'

const AUTH_LABEL: Record<string, string> = { none: 'None', bearer: 'Bearer token', header: 'Key in a header', basic: 'Basic (user:password)' }
const PLACEHOLDERS = '{reference} {po_number} {contract_number} {supplier_tin} {supplier_name}'

/** Settings → Integrations: other systems' APIs whose payment records PAMS shows, read-only. */
export function IntegrationsTab() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: ['integrations'], queryFn: integrationsApi.list })
  const [editing, setEditing] = useState<ExternalApi | 'new' | null>(null)
  const [deleting, setDeleting] = useState<ExternalApi | null>(null)
  const [testing, setTesting] = useState<ExternalApi | null>(null)
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['integrations'] })
  const remove = useMutation({
    mutationFn: (a: ExternalApi) => integrationsApi.remove(a.id),
    onSuccess: (r) => {
      toast(r.message, 'success')
      setDeleting(null)
      refresh()
    },
    onError: (err) => toast(err instanceof ApiError ? err.message : 'Could not remove it.', 'error'),
  })
  return (
    <>
      <div className="section-header">
        <h2>Integrations</h2>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          + Connect a system
        </button>
      </div>
      <p className="hint" style={{ marginBottom: '1rem' }}>
        PAMS reads payment records from other systems, such as the accounting or disbursement system, and shows them read-only: as a list under
        Payments from Other Systems, or matched to each purchase order and contract. PAMS only reads; it never sends data to them.
      </p>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>System</th>
              <th>Address</th>
              <th>Shown</th>
              <th>Sign-in</th>
              <th>Last call</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((a) => (
              <tr key={a.id}>
                <td>
                  {a.name}
                  {a.description && <div className="muted small">{a.description}</div>}
                </td>
                <td className="mono small">
                  {a.base_url}
                  {a.path}
                </td>
                <td>{a.context === 'payable' ? 'On each PO and contract' : 'As a list'}</td>
                <td>{AUTH_LABEL[a.auth_type ?? 'none']}</td>
                <td className="small">
                  {a.last_called_at ? formatDateTime(a.last_called_at) : '—'}
                  {a.last_status && <div className={a.last_status.startsWith('OK') ? 'muted' : 'hint warn'}>{a.last_status}</div>}
                </td>
                <td>{a.is_active ? <span className="badge st-ok">On</span> : <span className="badge badge-muted">Off</span>}</td>
                <td>
                  <div className="workflow-actions">
                    <button className="btn btn-outline btn-sm" onClick={() => setTesting(a)}>
                      Test
                    </button>
                    <button className="icon-btn edit" title={`Edit ${a.name}`} onClick={() => setEditing(a)}>
                      ✎
                    </button>
                    <button className="icon-btn delete" title={`Remove ${a.name}`} onClick={() => setDeleting(a)}>
                      🗑
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {list.data?.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No systems connected yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {editing && (
        <ApiForm
          api={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(a) => {
            toast(`${a.name} saved.`, 'success')
            setEditing(null)
            refresh()
            setTesting(a)
          }}
        />
      )}
      {testing && <TestWindow api={testing} onClose={() => { setTesting(null); refresh() }} />}
      {deleting && (
        <ConfirmDialog
          title="Remove connection"
          message={`Remove ${deleting.name}? PAMS stops reading from it; nothing changes in that system.`}
          confirmLabel="Remove"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

function ApiForm({ api, onClose, onSaved }: { api: ExternalApi | null; onClose: () => void; onSaved: (a: ExternalApi) => void }) {
  const [f, setF] = useState<ExternalApiInput>({
    name: api?.name ?? '',
    description: api?.description ?? '',
    base_url: api?.base_url ?? 'https://',
    path: api?.path ?? '',
    auth_type: api?.auth_type ?? 'bearer',
    auth_header: api?.auth_header ?? 'X-API-Key',
    secret: '',
    data_path: api?.data_path ?? '',
    context: api?.context ?? 'payable',
    timeout_seconds: api?.timeout_seconds ?? 10,
    is_active: api?.is_active ?? true,
  })
  const [cols, setCols] = useState<ApiColumn[]>(api?.columns.length ? api.columns : [{ label: '', field: '' }])
  const [clear, setClear] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (p: Partial<ExternalApiInput>) => setF((x) => ({ ...x, ...p }))
  const save = useMutation({
    mutationFn: () => {
      const input = { ...f, columns: cols.filter((c) => c.label.trim()), clear_secret: clear, secret: f.secret || undefined }
      return api ? integrationsApi.update(api.id, input) : integrationsApi.create(input)
    },
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save.'),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    save.mutate()
  }
  const input = (key: 'name' | 'description' | 'base_url' | 'path' | 'data_path' | 'auth_header', label: string, props: Record<string, unknown> = {}) => (
    <div className="field" {...(props.span ? { className: `field ${props.span}` } : {})}>
      <label htmlFor={`ia-${key}`}>{label}</label>
      <input id={`ia-${key}`} value={(f[key] as string) ?? ''} onChange={(e) => set({ [key]: e.target.value })} placeholder={props.placeholder as string | undefined} />
      {typeof props.hint === 'string' && <span className="hint">{props.hint}</span>}
    </div>
  )
  return (
    <Modal title={api ? `Edit ${api.name}` : 'Connect a system'} onClose={onClose} width={820}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          {input('name', 'Name *', { placeholder: 'e.g. Accounting system (eNGAS)' })}
          <div className="field">
            <label htmlFor="ia-context">Show the records</label>
            <select id="ia-context" value={f.context} onChange={(e) => set({ context: e.target.value as ExternalApiInput['context'] })}>
              <option value="payable">On each purchase order and contract</option>
              <option value="none">As a list of their own</option>
            </select>
          </div>
          {input('base_url', 'Address of the API *', { placeholder: 'https://accounting.agency.gov.ph/api', span: 'span-2' })}
          {input('path', 'Path', {
            placeholder: f.context === 'payable' ? '/payments?reference={reference}' : '/payments?status=released',
            span: 'span-2',
            hint: f.context === 'payable' ? `Placeholders filled from the PO or contract: ${PLACEHOLDERS}` : 'Placeholders are only for records read by PO or contract.',
          })}
          <div className="field">
            <label htmlFor="ia-auth">Sign-in</label>
            <select id="ia-auth" value={f.auth_type} onChange={(e) => set({ auth_type: e.target.value as ExternalApiInput['auth_type'] })}>
              {Object.entries(AUTH_LABEL).map(([k, l]) => (
                <option key={k} value={k}>
                  {l}
                </option>
              ))}
            </select>
          </div>
          {f.auth_type === 'header' && input('auth_header', 'Header name *', { placeholder: 'X-API-Key' })}
          {f.auth_type !== 'none' && (
            <div className="field">
              <label htmlFor="ia-secret">{f.auth_type === 'basic' ? 'user:password' : 'Key or token'}{api?.has_secret ? '' : ' *'}</label>
              <input id="ia-secret" type="password" autoComplete="new-password" value={f.secret ?? ''} onChange={(e) => set({ secret: e.target.value })}
                placeholder={api?.has_secret ? 'Kept; type to replace' : ''} />
              <span className="hint">Stored encrypted. It is never shown again.</span>
            </div>
          )}
          {input('data_path', 'Where the records are in the answer', { placeholder: 'data.items', hint: 'Leave blank when the answer is the list itself.' })}
          <div className="field">
            <label htmlFor="ia-timeout">Wait up to (seconds)</label>
            <input id="ia-timeout" type="number" min={1} max={60} value={f.timeout_seconds ?? 10} onChange={(e) => set({ timeout_seconds: Number(e.target.value) })} />
          </div>
          {input('description', 'Notes', { span: 'span-2' })}
        </div>
        <h4>Columns to show</h4>
        <p className="hint">Heading, and the field in each record (names joined by dots, e.g. payee.name). Leave empty to show the first record's fields.</p>
        {cols.map((c, i) => (
          <div key={i} className="toolbar" style={{ marginBottom: '0.4rem' }}>
            <input aria-label={`Column ${i + 1} heading`} placeholder="Heading, e.g. Check no." value={c.label} onChange={(e) => setCols(cols.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))} />
            <input aria-label={`Column ${i + 1} field`} placeholder="Field, e.g. check_number" value={c.field} onChange={(e) => setCols(cols.map((x, j) => (j === i ? { ...x, field: e.target.value } : x)))} />
            <button type="button" className="icon-btn delete" title="Remove column" onClick={() => setCols(cols.filter((_, j) => j !== i))}>
              🗑
            </button>
          </div>
        ))}
        <button type="button" className="btn btn-outline btn-sm" onClick={() => setCols([...cols, { label: '', field: '' }])}>
          + Column
        </button>
        <div className="toolbar" style={{ marginTop: '1rem' }}>
          <label className="checkbox-line">
            <input type="checkbox" checked={!!f.is_active} onChange={(e) => set({ is_active: e.target.checked })} /> On
          </label>
          {api?.has_secret && (
            <label className="checkbox-line">
              <input type="checkbox" checked={clear} onChange={(e) => setClear(e.target.checked)} /> Forget the stored key
            </label>
          )}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save and test'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function TestWindow({ api, onClose }: { api: ExternalApi; onClose: () => void }) {
  const [values, setValues] = useState<Record<string, string>>({ reference: 'PO-2026-0001' })
  const names = Array.from((api.path ?? '').matchAll(/\{([a-z_]+)}/g)).map((m) => m[1])
  const [d, setD] = useState<ApiData | null>(null)
  const run = useMutation({ mutationFn: () => integrationsApi.test(api.id, values), onSuccess: setD })
  return (
    <Modal title={`Test ${api.name}`} onClose={onClose} width={900}>
      {names.length > 0 && (
        <div className="form-grid">
          {[...new Set(names)].map((n) => (
            <div className="field" key={n}>
              <label htmlFor={`it-${n}`}>Sample {`{${n}}`}</label>
              <input id={`it-${n}`} value={values[n] ?? ''} onChange={(e) => setValues({ ...values, [n]: e.target.value })} />
            </div>
          ))}
        </div>
      )}
      <div className="toolbar" style={{ margin: '0.75rem 0' }}>
        <button className="btn btn-primary" disabled={run.isPending} onClick={() => run.mutate()}>
          {run.isPending ? 'Calling…' : 'Call it now'}
        </button>
      </div>
      {run.isError && <div className="alert alert-error">{run.error instanceof ApiError ? run.error.message : 'The test failed.'}</div>}
      {d && <ApiDataTable d={d} title="Answer" />}
    </Modal>
  )
}

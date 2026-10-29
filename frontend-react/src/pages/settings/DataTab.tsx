import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import {
  dataioApi,
  type CommitResult,
  type ImportBatch,
  type ImportEntityKey,
  type ImportRowResult,
  type ImportStatus,
  type RowStatus,
  type TemporaryPassword,
  type ValidationResult,
} from '../../api/dataio'
import { Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDateTime } from '../../lib/format'

const ENTITY_LABEL: Record<ImportEntityKey, string> = {
  items: 'Items',
  suppliers: 'Suppliers',
  departments: 'Departments',
  users: 'Users',
}
const ROW_BADGE: Record<RowStatus, string> = {
  ok: 'st-ok',
  duplicate: 'st-pending',
  incomplete: 'st-pending',
  invalid: 'st-bad',
  aborted: 'st-bad',
}
const BATCH_BADGE: Record<ImportStatus, string> = { validated: 'st-pending', committed: 'st-ok', aborted: 'st-bad' }

const errorText = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

/** Settings → Data: full export, bulk import of master data (validate, then commit) and the import history. */
export function DataTab() {
  return (
    <div className="settings-grid" style={{ gridTemplateColumns: '1fr' }}>
      <ExportCard />
      <ImportWizard />
      <ImportHistory />
    </div>
  )
}

function ExportCard() {
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const run = async () => {
    setBusy(true)
    try {
      await dataioApi.exportAll()
      toast('Export downloaded.', 'success')
    } catch (err) {
      toast(errorText(err, 'The export failed.'), 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="settings-card">
      <h2>Export all data</h2>
      <p className="hint">
        A ZIP with one CSV file per table (UTF-8, header row, every column and record) and a manifest.json listing the tables, row counts and
        columns. Passwords, two-factor secrets and tokens are left out. The export is recorded in the audit trail.
      </p>
      <button className="btn btn-primary" onClick={run} disabled={busy}>
        {busy ? 'Preparing export…' : 'Download full export (ZIP)'}
      </button>
    </section>
  )
}

function ImportWizard() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const entitiesQ = useQuery({ queryKey: ['dataio-entities'], queryFn: dataioApi.entities, staleTime: Infinity })
  const [entity, setEntity] = useState<ImportEntityKey>('items')
  const [file, setFile] = useState<File | null>(null)
  const [createCategories, setCreateCategories] = useState(false)
  const [result, setResult] = useState<ValidationResult | null>(null)
  const [committed, setCommitted] = useState<CommitResult | null>(null)
  const [passwords, setPasswords] = useState<TemporaryPassword[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const info = entitiesQ.data?.entities.find((e) => e.key === entity)

  const refreshHistory = () => void queryClient.invalidateQueries({ queryKey: ['dataio-batches'] })

  const validate = useMutation({
    mutationFn: () => dataioApi.validate(entity, file as File, createCategories),
    onSuccess: (r) => {
      setResult(r)
      setCommitted(null)
      setError(null)
      refreshHistory()
    },
    onError: (err) => {
      setResult(null)
      setError(errorText(err, 'The file could not be checked.'))
      refreshHistory()
    },
  })

  const commit = useMutation({
    mutationFn: (id: number) => dataioApi.commit(id),
    onSuccess: (r) => {
      setCommitted(r)
      setResult(null)
      setError(null)
      toast(r.message, 'success')
      if (r.temporary_passwords?.length) setPasswords(r.temporary_passwords)
      refreshHistory()
    },
    onError: (err) => {
      setError(errorText(err, 'The import failed.'))
      setResult(null)
      refreshHistory()
    },
  })

  const reset = () => {
    setResult(null)
    setCommitted(null)
    setError(null)
    setFile(null)
    if (fileRef.current) fileRef.current.value = ''
  }

  const downloadTemplate = async () => {
    try {
      await dataioApi.template(entity)
    } catch (err) {
      toast(errorText(err, 'Could not download the template.'), 'error')
    }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (file) validate.mutate()
  }

  const counts = result?.batch.counts
  return (
    <section className="settings-card">
      <h2>Bulk import</h2>
      {error && <div className="alert alert-error">{error}</div>}

      {!result && (
        <form onSubmit={submit}>
          <div className="toolbar" style={{ alignItems: 'flex-end' }}>
            <div className="field">
              <label htmlFor="io-entity">What to import</label>
              <select
                id="io-entity"
                value={entity}
                onChange={(e) => {
                  setEntity(e.target.value as ImportEntityKey)
                  setCommitted(null)
                }}
              >
                {(Object.keys(ENTITY_LABEL) as ImportEntityKey[]).map((k) => (
                  <option key={k} value={k}>
                    {ENTITY_LABEL[k]}
                  </option>
                ))}
              </select>
            </div>
            <button type="button" className="btn btn-outline" onClick={downloadTemplate}>
              Download {ENTITY_LABEL[entity].toLowerCase()} template
            </button>
          </div>

          {info && (
            <p className="hint">
              Columns:{' '}
              {info.columns.map((c, i) => (
                <span key={c.name} title={c.hint}>
                  {i > 0 && ', '}
                  <code>{c.name}</code>
                  {c.required && ' *'}
                </span>
              ))}
              . * required. A table from the full export ZIP (or the ZIP itself) is also accepted.
              {entity === 'users' && ' Each new user gets a temporary password, shown once after the import.'}
            </p>
          )}

          <div className="field">
            <label htmlFor="io-file">CSV or ZIP file (up to 25 MB)</label>
            <input id="io-file" ref={fileRef} type="file" accept=".csv,.zip,text/csv,application/zip" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </div>
          {entity === 'items' && (
            <div className="field">
              <label>
                <input type="checkbox" checked={createCategories} onChange={(e) => setCreateCategories(e.target.checked)} style={{ width: 'auto', marginRight: 6 }} />
                Create categories that do not exist yet
              </label>
              <span className="hint">Otherwise a row with an unknown category is reported as invalid.</span>
            </div>
          )}
          <button className="btn btn-primary" type="submit" disabled={!file || validate.isPending}>
            {validate.isPending ? 'Checking…' : 'Check file (dry run)'}
          </button>
        </form>
      )}

      {result && counts && (
        <>
          <p>
            <strong>{result.batch.file_name}</strong> — {ENTITY_LABEL[result.batch.entity]}, import #{result.batch.id}. Nothing has been imported yet.
          </p>
          <div className="kpi-row">
            <Kpi label="Rows" value={counts.total} />
            <Kpi label="Ready to import" value={counts.ok} />
            <Kpi label="Duplicates" value={counts.duplicate} tone={counts.duplicate ? 'warn' : undefined} />
            <Kpi label="Incomplete" value={counts.incomplete} tone={counts.incomplete ? 'warn' : undefined} />
            <Kpi label="Invalid" value={counts.invalid} tone={counts.invalid ? 'bad' : undefined} />
          </div>
          {result.ignored_columns.length > 0 && (
            <div className="alert alert-info">Columns not used: {result.ignored_columns.join(', ')}.</div>
          )}
          {result.exceptions.length > 0 && (
            <>
              <h3>Exceptions (not imported)</h3>
              <ExceptionTable rows={result.exceptions} />
            </>
          )}
          {result.preview.length > 0 && (
            <>
              <h3>Ready to import{counts.ok > result.preview.length ? ` (first ${result.preview.length})` : ''}</h3>
              <div className="table-wrap" style={{ marginBottom: '1rem' }}>
                <table className="table">
                  <thead>
                    <tr>
                      <th>Row</th>
                      <th>Record</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.preview.map((r) => (
                      <tr key={r.row}>
                        <td>{r.row}</td>
                        <td>{r.key}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
          <div className="toolbar">
            <button className="btn btn-primary" onClick={() => commit.mutate(result.batch.id)} disabled={!counts.ok || commit.isPending}>
              {commit.isPending ? 'Importing…' : `Import ${counts.ok} row${counts.ok === 1 ? '' : 's'}`}
            </button>
            {result.exceptions.length > 0 && (
              <ExceptionsButton id={result.batch.id} />
            )}
            <button className="btn btn-secondary" onClick={reset} disabled={commit.isPending}>
              Start over
            </button>
          </div>
        </>
      )}

      {committed && (
        <div className="alert alert-success">
          {committed.message}
          {committed.categories_created > 0 && ` ${committed.categories_created} new categor${committed.categories_created === 1 ? 'y' : 'ies'} created.`}{' '}
          {committed.exceptions.length > 0 && 'Rows not imported are in the exception report in the history below.'}
        </div>
      )}

      {passwords && <PasswordsModal rows={passwords} onClose={() => setPasswords(null)} />}
    </section>
  )
}

function Kpi({ label, value, tone }: { label: string; value: number; tone?: 'warn' | 'bad' }) {
  return (
    <div className={'kpi' + (tone ? ` kpi-${tone}` : '')}>
      <span className="kpi-value">{value}</span>
      <span className="kpi-label">{label}</span>
    </div>
  )
}

function ExceptionTable({ rows }: { rows: ImportRowResult[] }) {
  return (
    <div className="table-wrap" style={{ marginBottom: '1rem', maxHeight: 420, overflowY: 'auto' }}>
      <table className="table">
        <thead>
          <tr>
            <th>Row</th>
            <th>Status</th>
            <th>Record</th>
            <th>Problems</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={`${r.row}-${i}`}>
              <td>{r.row ?? '—'}</td>
              <td>
                <span className={'badge ' + ROW_BADGE[r.status]}>{r.status}</span>
              </td>
              <td>{r.key ?? '—'}</td>
              <td>
                {r.messages.map((m, j) => (
                  <div key={j}>{m}</div>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ExceptionsButton({ id, small }: { id: number; small?: boolean }) {
  const toast = useToast()
  const run = async () => {
    try {
      await dataioApi.exceptionsCsv(id)
    } catch (err) {
      toast(errorText(err, 'Could not download the exception report.'), 'error')
    }
  }
  return (
    <button className={'btn btn-outline' + (small ? ' btn-sm' : '')} onClick={run}>
      Exception report (CSV)
    </button>
  )
}

function PasswordsModal({ rows, onClose }: { rows: TemporaryPassword[]; onClose: () => void }) {
  const toast = useToast()
  const csv = () =>
    '﻿name,email,temporary_password\r\n' + rows.map((r) => [r.name, r.email, r.temporary_password].map((v) => `"${v.replace(/"/g, '""')}"`).join(',')).join('\r\n') + '\r\n'
  const save = () => {
    const href = URL.createObjectURL(new Blob([csv()], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = href
    a.download = 'temporary_passwords.csv'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(href)
  }
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(rows.map((r) => `${r.email}\t${r.temporary_password}`).join('\n'))
      toast('Copied.', 'success')
    } catch {
      toast('Copy is not available here; download the list instead.', 'error')
    }
  }
  return (
    <Modal title="Temporary passwords" onClose={onClose} width={720}>
      <div className="alert alert-warning">
        These passwords are shown only now and are not stored anywhere readable. Hand each one to its user privately and ask them to change it at
        first sign-in.
      </div>
      <div className="table-wrap" style={{ maxHeight: 360, overflowY: 'auto' }}>
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Temporary password</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.email}>
                <td>{r.name}</td>
                <td>{r.email}</td>
                <td>
                  <code>{r.temporary_password}</code>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="modal-footer">
        <button className="btn btn-outline" onClick={copy}>
          Copy
        </button>
        <button className="btn btn-outline" onClick={save}>
          Download list
        </button>
        <button className="btn btn-primary" onClick={onClose}>
          Done
        </button>
      </div>
    </Modal>
  )
}

function ImportHistory() {
  const [page, setPage] = useState(1)
  const [entity, setEntity] = useState('')
  const [status, setStatus] = useState('')
  const [open, setOpen] = useState<number | null>(null)
  const q = useQuery({ queryKey: ['dataio-batches', page, entity, status], queryFn: () => dataioApi.batches({ page, entity, status }) })
  return (
    <section className="settings-card">
      <h2>Import history</h2>
      <div className="toolbar">
        <select value={entity} onChange={(e) => { setEntity(e.target.value); setPage(1) }} aria-label="Filter by data">
          <option value="">All data</option>
          {(Object.keys(ENTITY_LABEL) as ImportEntityKey[]).map((k) => (
            <option key={k} value={k}>
              {ENTITY_LABEL[k]}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Filter by status">
          <option value="">All statuses</option>
          <option value="validated">Validated (not imported)</option>
          <option value="committed">Committed</option>
          <option value="aborted">Aborted</option>
        </select>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>When</th>
              <th>Data</th>
              <th>File</th>
              <th>Status</th>
              <th>Rows</th>
              <th>Imported</th>
              <th>Exceptions</th>
              <th>By</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {q.data?.data.length === 0 && (
              <tr>
                <td colSpan={9} className="empty">
                  No imports yet.
                </td>
              </tr>
            )}
            {q.data?.data.map((b) => {
              const exceptions = b.counts.duplicate + b.counts.incomplete + b.counts.invalid
              return (
                <tr key={b.id}>
                  <td>{formatDateTime(b.created_at)}</td>
                  <td>{ENTITY_LABEL[b.entity]}</td>
                  <td>{b.file_name}</td>
                  <td>
                    <span className={'badge ' + BATCH_BADGE[b.status]} title={b.error_message ?? undefined}>
                      {b.status}
                    </span>
                  </td>
                  <td>{b.counts.total}</td>
                  <td>{b.counts.imported}</td>
                  <td>{exceptions}</td>
                  <td>{b.created_by?.name ?? '—'}</td>
                  <td className="actions">
                    <button className="btn btn-outline btn-sm" onClick={() => setOpen(b.id)}>
                      View
                    </button>
                    {(exceptions > 0 || b.status === 'aborted') && <ExceptionsButton id={b.id} small />}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <Pager pagination={q.data?.pagination} onPage={setPage} />
      {open !== null && <BatchModal id={open} onClose={() => setOpen(null)} />}
    </section>
  )
}

function BatchModal({ id, onClose }: { id: number; onClose: () => void }) {
  const q = useQuery({ queryKey: ['dataio-batch', id], queryFn: () => dataioApi.batch(id) })
  const b: (ImportBatch & { exceptions: ImportRowResult[] }) | undefined = q.data
  return (
    <Modal title={`Import #${id}`} onClose={onClose} width={900}>
      {q.isError && <div className="alert alert-error">{errorText(q.error, 'Could not load the import.')}</div>}
      {b && (
        <>
          <p>
            <strong>{b.file_name}</strong> — {ENTITY_LABEL[b.entity]} · <span className={'badge ' + BATCH_BADGE[b.status]}>{b.status}</span>
            <br />
            Checked {formatDateTime(b.created_at)} by {b.created_by?.name ?? '—'}
            {b.committed_at && ` · imported ${formatDateTime(b.committed_at)}${b.committed_by ? ` by ${b.committed_by.name}` : ''}`}
            {b.aborted_at && ` · aborted ${formatDateTime(b.aborted_at)}`}
          </p>
          {b.error_message && <div className="alert alert-error">{b.error_message}</div>}
          <div className="kpi-row">
            <Kpi label="Rows" value={b.counts.total} />
            <Kpi label="Ready" value={b.counts.ok} />
            <Kpi label="Imported" value={b.counts.imported} />
            <Kpi label="Duplicates" value={b.counts.duplicate} tone={b.counts.duplicate ? 'warn' : undefined} />
            <Kpi label="Incomplete" value={b.counts.incomplete} tone={b.counts.incomplete ? 'warn' : undefined} />
            <Kpi label="Invalid" value={b.counts.invalid} tone={b.counts.invalid ? 'bad' : undefined} />
          </div>
          {b.exceptions.length > 0 ? <ExceptionTable rows={b.exceptions} /> : <p className="hint">No exceptions.</p>}
          <div className="modal-footer">
            {b.exceptions.length > 0 && <ExceptionsButton id={b.id} />}
            <button className="btn btn-primary" onClick={onClose}>
              Close
            </button>
          </div>
        </>
      )}
    </Modal>
  )
}

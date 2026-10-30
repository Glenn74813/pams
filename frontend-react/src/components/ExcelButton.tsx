import { useState } from 'react'
import { ApiError } from '../api/client'
import { reportsApi } from '../api/endpoints'

/** Small per-row download links for a form: Excel, and the same form as PDF. */
export function ExcelButton({ path, name, label }: { path: string; name: string; label: string }) {
  const [busy, setBusy] = useState<'xlsx' | 'pdf' | null>(null)
  const [failed, setFailed] = useState<string | null>(null)
  const run = (kind: 'xlsx' | 'pdf') => {
    setBusy(kind)
    setFailed(null)
    reportsApi
      .exportFile(path, kind === 'pdf' ? { format: 'pdf' } : {}, kind === 'pdf' ? name.replace(/\.xlsx$/, '.pdf') : name)
      .catch((e) => setFailed(e instanceof ApiError ? e.message : 'Failed'))
      .finally(() => setBusy(null))
  }
  return (
    <>
      <button className="btn btn-outline btn-sm" title={failed ?? `Download ${label} (Excel)`} disabled={!!busy} onClick={() => run('xlsx')}>
        {busy === 'xlsx' ? '…' : failed ? `⚠ ${label}` : `⬇ ${label}`}
      </button>{' '}
      <button className="btn btn-outline btn-sm" title={`Download ${label} (PDF)`} disabled={!!busy} onClick={() => run('pdf')}>
        {busy === 'pdf' ? '…' : 'PDF'}
      </button>
    </>
  )
}

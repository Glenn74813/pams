import { useState } from 'react'
import { ApiError } from '../api/client'
import { reportsApi } from '../api/endpoints'

/** A small per-row Excel download link. */
export function ExcelButton({ path, name, label }: { path: string; name: string; label: string }) {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)
  return (
    <button
      className="btn btn-outline btn-sm"
      title={failed ?? `Download ${label} (Excel)`}
      disabled={busy}
      onClick={() => {
        setBusy(true)
        setFailed(null)
        reportsApi
          .exportFile(path, {}, name)
          .catch((e) => setFailed(e instanceof ApiError ? e.message : 'Failed'))
          .finally(() => setBusy(false))
      }}
    >
      {busy ? '…' : failed ? `⚠ ${label}` : `⬇ ${label}`}
    </button>
  )
}

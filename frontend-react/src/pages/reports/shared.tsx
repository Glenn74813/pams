import { useState } from 'react'
import { ApiError } from '../../api/client'
import { reportsApi, type PreviewRow } from '../../api/endpoints'
import { useToast } from '../../components/toast-context'
import { formatPeso } from '../../lib/format'

export interface Column {
  key: string
  label: string
  type?: 'money' | 'num'
}

export function PreviewTable({ columns, rows, empty = 'No rows.' }: { columns: Column[]; rows?: PreviewRow[]; empty?: string }) {
  if (!rows) return null
  if (rows.length === 0) return <p className="muted">{empty}</p>
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={c.type ? 'num' : undefined}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {columns.map((c) => {
                const v = r[c.key]
                return (
                  <td key={c.key} className={c.type ? 'num' : undefined}>
                    {v == null || v === '' ? '—' : c.type === 'money' ? formatPeso(Number(v)) : c.type === 'num' ? Number(v).toLocaleString() : String(v)}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Downloads a COA form in its prescribed Excel format. */
export function ExportButton({ path, params, name, label = 'Export to Excel', disabled }: {
  path: string
  params?: Record<string, string | number | undefined>
  name: string
  label?: string
  disabled?: boolean
}) {
  const toast = useToast()
  const [busy, setBusy] = useState<'file' | 'pdf' | null>(null)
  const run = async (kind: 'file' | 'pdf') => {
    setBusy(kind)
    try {
      if (kind === 'pdf') await reportsApi.exportFile(path, { ...params, format: 'pdf' }, name.replace(/\.xlsx$/, '.pdf'))
      else await reportsApi.exportFile(path, params, name)
    } catch (e) {
      toast(e instanceof ApiError ? e.message : 'The file could not be generated.', 'error')
    } finally {
      setBusy(null)
    }
  }
  // Excel forms can also be saved as PDF; list reports choose their format in their own field.
  const offerPdf = name.endsWith('.xlsx') && !params?.format
  return (
    <>
      <button className="btn btn-primary" disabled={!!busy || disabled} onClick={() => void run('file')}>
        {busy === 'file' ? 'Generating…' : `⬇ ${label}`}
      </button>
      {offerPdf && (
        <button className="btn btn-outline" disabled={!!busy || disabled} onClick={() => void run('pdf')}>
          {busy === 'pdf' ? 'Generating…' : '⬇ PDF'}
        </button>
      )}
    </>
  )
}

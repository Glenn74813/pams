import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { documentsApi } from '../../api/endpoints'
import type { RepoDocument } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { useDebounced } from '../../lib/hooks'
import { formatDate } from '../../lib/format'
import { HistoryModal } from './HistoryModal'
import { UploadModal } from './UploadModal'
import { DOC_TYPE, OWNER_LABEL } from './labels'
import '../inventory/inventory.css'
import '../issuance/issuance.css'

export function DocumentsPage() {
  const { can } = useAuth()
  const toast = useToast()
  const qc = useQueryClient()
  const [page, setPage] = useState(1)
  const [type, setType] = useState('')
  const [status, setStatus] = useState('active')
  const [search, setSearch] = useState('')
  const term = useDebounced(search)
  const [upload, setUpload] = useState<{ doc?: RepoDocument } | null>(null)
  const [history, setHistory] = useState<number | null>(null)
  const q = useQuery({
    queryKey: ['documents', page, type, status, term],
    queryFn: () => documentsApi.list({ page, type, status, search: term }),
    placeholderData: keepPreviousData,
  })
  const refresh = () => void qc.invalidateQueries({ queryKey: ['documents'] })
  const fail = (e: unknown) => toast(e instanceof ApiError ? e.message : 'Something went wrong.', 'error')
  const archive = useMutation({
    mutationFn: (d: RepoDocument) => documentsApi.archive(d.id),
    onSuccess: (d) => {
      refresh()
      toast(d.status === 'archived' ? `“${d.title}” archived.` : `“${d.title}” restored.`)
    },
    onError: fail,
  })
  const remove = useMutation({
    mutationFn: (d: RepoDocument) => documentsApi.remove(d.id),
    onSuccess: () => {
      refresh()
      toast('Document deleted.')
    },
    onError: fail,
  })
  const canUpload = can('upload_documents')

  return (
    <div className="page page-wide">
      <div className="section-header">
        <h1 className="page-title">Document Repository</h1>
        {canUpload && (
          <button className="btn btn-primary" onClick={() => setUpload({})}>
            + Upload document
          </button>
        )}
      </div>
      <p className="muted small">
        Procurement and inventory records with version history. Each version keeps its file and a SHA-256 checksum; downloads are recorded in the audit
        trail. Documents attached to a PR, PO, contract or other record also appear on that record.
      </p>
      <div className="toolbar">
        <input
          type="search"
          placeholder="Title, number or category…"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(1)
          }}
          aria-label="Search documents"
        />
        <select value={type} onChange={(e) => (setType(e.target.value), setPage(1))} aria-label="Type">
          <option value="">All types</option>
          {Object.entries(DOC_TYPE).map(([k, l]) => (
            <option key={k} value={k}>
              {l}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => (setStatus(e.target.value), setPage(1))} aria-label="Status">
          <option value="active">Active</option>
          <option value="archived">Archived</option>
          <option value="">All</option>
        </select>
      </div>
      {q.isLoading && <div className="page-loading">Loading…</div>}
      {q.data && q.data.data.length === 0 && <p className="muted">No documents found.</p>}
      {q.data && q.data.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Type</th>
                <th>Doc. no.</th>
                <th>Attached to</th>
                <th>Ver.</th>
                <th>Latest file</th>
                <th>Uploaded</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {q.data.data.map((d) => (
                <tr key={d.id}>
                  <td>
                    {d.title}
                    {d.category && <div className="muted small">{d.category}</div>}
                  </td>
                  <td>
                    <span className="badge badge-muted">{DOC_TYPE[d.type] ?? d.type}</span>
                  </td>
                  <td>{d.document_number ?? '—'}</td>
                  <td>{d.documentable_type ? `${OWNER_LABEL[d.documentable_type] ?? d.documentable_type} #${d.documentable_id}` : '—'}</td>
                  <td>v{d.current_version}</td>
                  <td className="clip" title={d.latest_version?.original_filename}>
                    {d.latest_version ? (
                      <>
                        {d.latest_version.original_filename} <span className="muted small">({d.latest_version.file_size_human})</span>
                      </>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="nowrap">
                    {d.uploader?.name ?? '—'}
                    <div className="muted small">{formatDate(d.created_at)}</div>
                  </td>
                  <td>
                    <span className={'badge ' + (d.status === 'archived' ? 'badge-muted' : 'st-ok')}>{d.status === 'archived' ? 'Archived' : 'Active'}</span>
                  </td>
                  <td className="nowrap">
                    <button className="icon-btn" title="Download latest" onClick={() => void documentsApi.download(d.id).catch(fail)}>
                      ⬇
                    </button>
                    <button className="icon-btn" title="Version history" onClick={() => setHistory(d.id)}>
                      🕘
                    </button>
                    {canUpload && d.status === 'active' && (
                      <button className="icon-btn" title="Upload new version" onClick={() => setUpload({ doc: d })}>
                        ＋
                      </button>
                    )}
                    {canUpload && (
                      <button className="icon-btn" title={d.status === 'archived' ? 'Restore' : 'Archive'} onClick={() => archive.mutate(d)}>
                        {d.status === 'archived' ? '↺' : '🗄'}
                      </button>
                    )}
                    {can('delete_documents') && (
                      <button
                        className="icon-btn"
                        title="Delete"
                        onClick={() => window.confirm(`Delete “${d.title}”? Its files are kept for audit.`) && remove.mutate(d)}
                      >
                        🗑
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager pagination={q.data?.pagination} onPage={setPage} />
      {upload && (
        <UploadModal
          document={upload.doc}
          onClose={() => setUpload(null)}
          onSaved={(_, m) => {
            setUpload(null)
            refresh()
            toast(m)
          }}
        />
      )}
      {history !== null && <HistoryModal id={history} onClose={() => setHistory(null)} />}
    </div>
  )
}

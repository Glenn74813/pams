import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { documentsApi } from '../../api/endpoints'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/toast-context'
import { formatDate } from '../../lib/format'
import { HistoryModal } from './HistoryModal'
import { UploadModal } from './UploadModal'
import { DOC_TYPE } from './labels'

interface Props {
  /** Laravel model name of the record, e.g. PurchaseRequest. */
  type: string
  id: number
  /** Shown in the upload title, e.g. "PR-2026-0001". */
  label: string
  /** Default document type for new uploads. */
  docType?: string
}

/** Documents attached to one record (TOR 5.9 — browse related documents), with upload for those allowed. */
export function AttachmentsPanel({ type, id, label, docType }: Props) {
  const { can } = useAuth()
  const toast = useToast()
  const qc = useQueryClient()
  const [uploading, setUploading] = useState(false)
  const [history, setHistory] = useState<number | null>(null)
  const allowed = can('view_documents', 'upload_documents')
  const q = useQuery({
    queryKey: ['documents', 'owner', type, id],
    queryFn: () => documentsApi.list({ page: 1, per_page: 50, documentable_type: type, documentable_id: id }),
    enabled: allowed,
  })
  if (!allowed) return null
  const docs = q.data?.data ?? []
  return (
    <>
      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>Attachments {docs.length > 0 && <span className="badge badge-muted">{docs.length}</span>}</h4>
        {can('upload_documents') && (
          <button className="btn btn-outline btn-sm" onClick={() => setUploading(true)}>
            + Attach
          </button>
        )}
      </div>
      {q.isLoading ? (
        <p className="muted small">Loading…</p>
      ) : docs.length === 0 ? (
        <p className="muted small">No documents attached.</p>
      ) : (
        <div className="table-wrap">
          <table className="table table-compact">
            <tbody>
              {docs.map((d) => (
                <tr key={d.id}>
                  <td>
                    {d.title} <span className="badge badge-muted">{DOC_TYPE[d.type] ?? d.type}</span>
                    {d.status === 'archived' && <span className="badge badge-muted">archived</span>}
                  </td>
                  <td className="small">
                    v{d.current_version} · {d.latest_version?.file_size_human} · {formatDate(d.updated_at)}
                  </td>
                  <td className="nowrap">
                    <button className="link-btn" onClick={() => void documentsApi.download(d.id).catch(() => toast('The file could not be downloaded.', 'error'))}>
                      Download
                    </button>{' '}
                    · <button className="link-btn" onClick={() => setHistory(d.id)}>History</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {uploading && (
        <UploadModal
          owner={{ type, id, label, docType }}
          onClose={() => setUploading(false)}
          onSaved={(_, m) => {
            setUploading(false)
            void qc.invalidateQueries({ queryKey: ['documents'] })
            toast(m)
          }}
        />
      )}
      {history !== null && <HistoryModal id={history} onClose={() => setHistory(null)} />}
    </>
  )
}

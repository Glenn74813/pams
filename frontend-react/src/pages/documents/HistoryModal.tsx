import { useQuery } from '@tanstack/react-query'
import { documentsApi } from '../../api/endpoints'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { formatDateTime } from '../../lib/format'

export function HistoryModal({ id, onClose }: { id: number; onClose: () => void }) {
  const toast = useToast()
  const q = useQuery({ queryKey: ['document', id], queryFn: () => documentsApi.get(id) })
  const get = (vid: number) => documentsApi.download(id, vid).catch(() => toast('The file could not be downloaded.', 'error'))
  return (
    <Modal title={`Version history — ${q.data?.title ?? '…'}`} onClose={onClose} width={900}>
      {q.isLoading && <div className="page-loading">Loading…</div>}
      {q.data && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Ver.</th>
                <th>File</th>
                <th>Size</th>
                <th>Uploaded by</th>
                <th>Date</th>
                <th>Remarks</th>
                <th>SHA-256</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {q.data.versions?.map((v) => (
                <tr key={v.id}>
                  <td>v{v.version_number}</td>
                  <td className="clip" title={v.original_filename}>
                    {v.original_filename}
                  </td>
                  <td className="nowrap">{v.file_size_human}</td>
                  <td>{v.uploader?.name ?? '—'}</td>
                  <td className="nowrap">{formatDateTime(v.created_at)}</td>
                  <td>{v.remarks ?? '—'}</td>
                  <td className="small" title={v.checksum ?? 'Uploaded before checksums were kept'}>
                    {v.checksum ? v.checksum.slice(0, 12) + '…' : '—'}
                  </td>
                  <td>
                    <button className="link-btn" onClick={() => void get(v.id)}>
                      Download
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  )
}

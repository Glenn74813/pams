import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { documentsApi, type DocumentInput } from '../../api/endpoints'
import type { RepoDocument } from '../../api/types'
import { Modal } from '../../components/Modal'
import { ACCEPT, DOC_TYPE } from './labels'

interface Props {
  /** Upload a new version of this document instead of a new document. */
  document?: RepoDocument
  /** Attach the new document to this record. */
  owner?: { type: string; id: number; label: string; docType?: string; number?: string }
  onClose: () => void
  onSaved: (d: RepoDocument, message: string) => void
}

export function UploadModal({ document, owner, onClose, onSaved }: Props) {
  const [f, setF] = useState<DocumentInput>({
    title: '',
    type: owner?.docType ?? 'OTHER',
    document_number: owner?.number ?? '',
    category: '',
    description: '',
    remarks: '',
  })
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      if (!file) throw new ApiError(422, { error: 'Choose a file to upload.' })
      return document
        ? documentsApi.addVersion(document.id, file, f.remarks ?? '')
        : documentsApi.create({ ...f, documentable_type: owner?.type, documentable_id: owner?.id }, file)
    },
    onSuccess: (d) => onSaved(d, document ? `Version ${d.current_version} of “${d.title}” uploaded.` : `“${d.title}” filed.`),
    onError: (e) => setError(e instanceof ApiError ? e.message : 'The upload failed.'),
  })
  const set = (k: keyof DocumentInput, v: string) => setF((x) => ({ ...x, [k]: v }))
  return (
    <Modal title={document ? `New version — ${document.title}` : owner ? `Attach a document to ${owner.label}` : 'Upload document'} onClose={onClose}>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-grid">
        {!document && (
          <>
            <div className="field full">
              <label htmlFor="doc-title">Title *</label>
              <input id="doc-title" value={f.title} onChange={(e) => set('title', e.target.value)} placeholder="e.g. Signed purchase request" />
            </div>
            <div className="field">
              <label htmlFor="doc-type">Type *</label>
              <select id="doc-type" value={f.type} onChange={(e) => set('type', e.target.value)}>
                {Object.entries(DOC_TYPE).map(([k, l]) => (
                  <option key={k} value={k}>
                    {l}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="doc-no">Document number</label>
              <input id="doc-no" value={f.document_number} onChange={(e) => set('document_number', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="doc-cat">Category</label>
              <input id="doc-cat" value={f.category} onChange={(e) => set('category', e.target.value)} placeholder="e.g. Technical specifications" />
            </div>
            <div className="field full">
              <label htmlFor="doc-desc">Description</label>
              <textarea id="doc-desc" rows={2} value={f.description} onChange={(e) => set('description', e.target.value)} />
            </div>
          </>
        )}
        <div className="field full">
          <label htmlFor="doc-file">File *</label>
          <input id="doc-file" type="file" accept={ACCEPT} onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <span className="muted small">PDF, Word, Excel or an image, up to 20 MB.</span>
        </div>
        <div className="field full">
          <label htmlFor="doc-remarks">Remarks</label>
          <input id="doc-remarks" value={f.remarks} onChange={(e) => set('remarks', e.target.value)} placeholder={document ? 'What changed in this version' : 'Optional'} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onClose}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? 'Uploading…' : 'Upload'}
        </button>
      </div>
    </Modal>
  )
}

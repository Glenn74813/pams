import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, download } from '../../api/client'
import { ALGORITHMS, signingApi, type SigningRequest } from '../../api/tor'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatDateTime } from '../../lib/format'
import { Badge, PersonSelect } from '../assets/pickers'
import '../inventory/inventory.css'
import '../assets/assets.css'

type Tab = 'inbox' | 'outbox' | 'all' | 'verify' | 'cas'

const SIGN_STATUS: Record<string, [string, string]> = {
  pending: ['Waiting for signature', 'st-pending'],
  signed: ['Signed', 'st-ok'],
  declined: ['Declined', 'st-bad'],
  cancelled: ['Cancelled', 'badge-muted'],
}

/**
 * Digital signatures (TOR 1.4.11): send a document for signing, sign it with a certificate from a
 * trusted CA (PNPKI or the agency's own), and check any copy of a file against the recorded signatures.
 */
export function SigningPage() {
  const { can } = useAuth()
  const admin = can('edit_settings', 'view_audit_trail')
  const [tab, setTab] = useState<Tab>('inbox')
  const tabs: [Tab, string][] = [
    ['inbox', 'To sign'],
    ['outbox', 'Sent by me'],
    ...(admin ? ([['all', 'All requests']] as [Tab, string][]) : []),
    ['verify', 'Verify a file'],
    ['cas', 'Trusted CAs'],
  ]
  return (
    <div className="page page-wide">
      <h1 className="page-title">Digital Signatures</h1>
      <div className="tabs">
        {tabs.map(([k, label]) => (
          <button key={k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>
            {label}
          </button>
        ))}
      </div>
      {(tab === 'inbox' || tab === 'outbox' || tab === 'all') && <Requests box={tab} />}
      {tab === 'verify' && <Verify />}
      {tab === 'cas' && <TrustedCas canEdit={can('edit_settings')} />}
    </div>
  )
}

function Requests({ box }: { box: 'inbox' | 'outbox' | 'all' }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState(box === 'inbox' ? 'pending' : '')
  const [sending, setSending] = useState(false)
  const [open, setOpen] = useState<number | null>(null)
  const list = useQuery({ queryKey: ['signing', box, status, page], queryFn: () => signingApi.list({ box, status: status || undefined, page }), placeholderData: keepPreviousData })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['signing'] })
  return (
    <>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="pending">Waiting</option>
            <option value="signed">Signed</option>
            <option value="declined">Declined</option>
            <option value="cancelled">Cancelled</option>
            <option value="">All</option>
          </select>
        </div>
        <button className="btn btn-primary" onClick={() => setSending(true)}>
          + Send for signing
        </button>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>No.</th>
              <th>Document</th>
              <th>From</th>
              <th>Signer</th>
              <th>Sent</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((r) => (
              <tr key={r.id}>
                <td className="mono">{r.request_number}</td>
                <td>
                  {r.title} <span className="muted small">· {r.file_name}</span>
                </td>
                <td>{r.requested_by}</td>
                <td>{r.signer}</td>
                <td>{formatDate(r.created_at)}</td>
                <td>
                  <Badge map={SIGN_STATUS} value={r.status} />
                </td>
                <td>
                  <button className={'btn btn-sm ' + (r.can_sign ? 'btn-primary' : 'btn-outline')} onClick={() => setOpen(r.id)}>
                    {r.can_sign ? 'Sign' : 'Open'}
                  </button>
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  {box === 'inbox' ? 'Nothing waiting for your signature.' : 'No signing requests here.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {sending && <SendForm onClose={() => setSending(false)} onSaved={(r) => { setSending(false); toast(`${r.request_number} sent to ${r.signer}.`, 'success'); refresh() }} />}
      {open && <RequestDetail id={open} onClose={() => setOpen(null)} onChanged={(m) => { toast(m, 'success'); refresh() }} />}
    </>
  )
}

function SendForm({ onClose, onSaved }: { onClose: () => void; onSaved: (r: SigningRequest) => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [signer, setSigner] = useState('')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => signingApi.create({ file: file!, title, signer_id: Number(signer), remarks: remarks || undefined }),
    onSuccess: onSaved,
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not send the document.'),
  })
  return (
    <Modal title="Send a document for signing" onClose={onClose} width={600}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); if (!file) return setError('Choose the document.'); save.mutate() }}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="form-grid">
          <div className="field span-2">
            <label htmlFor="sg-file">Document *</label>
            <input
              id="sg-file"
              type="file"
              onChange={(e) => {
                const f = e.target.files?.[0] ?? null
                setFile(f)
                if (f && !title) setTitle(f.name.replace(/\.[^.]+$/, ''))
              }}
            />
            <span className="hint">For example a COA form exported from Reports, or a PDF. Up to 20 MB. The signature covers this exact file.</span>
          </div>
          <div className="field span-2">
            <label htmlFor="sg-title">Title *</label>
            <input id="sg-title" value={title} maxLength={255} onChange={(e) => setTitle(e.target.value)} required />
          </div>
          <div className="field span-2">
            <label htmlFor="sg-signer">Signer *</label>
            <PersonSelect id="sg-signer" value={signer} onChange={setSigner} required />
          </div>
          <div className="field span-2">
            <label htmlFor="sg-rem">Note to the signer</label>
            <input id="sg-rem" value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Sending…' : 'Send'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function RequestDetail({ id, onClose, onChanged }: { id: number; onClose: () => void; onChanged: (m: string) => void }) {
  const r = useQuery({ queryKey: ['signing', 'one', id], queryFn: () => signingApi.show(id) })
  const [pem, setPem] = useState('')
  const [signature, setSignature] = useState('')
  const [algorithm, setAlgorithm] = useState<string>('RSA-SHA256')
  const [declining, setDeclining] = useState(false)
  const [reason, setReason] = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const after = (m: string) => {
    void r.refetch()
    onChanged(m)
  }
  const fail = (err: unknown, fallback: string) => setError(err instanceof ApiError ? err.message : fallback)
  const sign = useMutation({ mutationFn: () => signingApi.sign(id, { certificate_pem: pem, signature, algorithm }), onSuccess: () => after('Signed. The signature was checked and recorded.'), onError: (e) => fail(e, 'Could not record the signature.') })
  const decline = useMutation({ mutationFn: () => signingApi.decline(id, reason), onSuccess: () => { setDeclining(false); after('Declined.') }, onError: (e) => fail(e, 'Could not decline.') })
  const cancel = useMutation({ mutationFn: () => signingApi.cancel(id), onSuccess: () => { setCancelling(false); after('Request cancelled.') }, onError: (e) => fail(e, 'Could not cancel.') })
  const d = r.data
  const readFile = (f: File | undefined, set: (v: string) => void, asBase64: boolean) => {
    if (!f) return
    const reader = new FileReader()
    reader.onload = () => {
      if (!asBase64) return set(String(reader.result))
      const bytes = new Uint8Array(reader.result as ArrayBuffer)
      let bin = ''
      for (const b of bytes) bin += String.fromCharCode(b)
      set(btoa(bin))
    }
    if (asBase64) reader.readAsArrayBuffer(f)
    else reader.readAsText(f)
  }
  return (
    <Modal title={d ? `${d.request_number} — ${d.title}` : 'Signing request'} onClose={onClose} width={860}>
      {r.isPending && <div className="page-loading">Loading…</div>}
      {d && (
        <>
          {error && <div className="alert alert-error">{error}</div>}
          <dl className="detail-grid">
            <dt>Status</dt>
            <dd>
              <Badge map={SIGN_STATUS} value={d.status} />
              {d.platform_reference && <span className="muted small"> · platform ref. {d.platform_reference}</span>}
            </dd>
            <dt>File</dt>
            <dd>
              <button className="link-btn" onClick={() => void download(`/signing/requests/${d.id}/file`, {}, d.file_name)}>
                {d.file_name}
              </button>{' '}
              <span className="muted small">({(d.file_size / 1024).toFixed(1)} KB)</span>
            </dd>
            <dt>SHA-256</dt>
            <dd className="mono small" style={{ wordBreak: 'break-all' }}>
              {d.file_sha256}
            </dd>
            <dt>Sent</dt>
            <dd>
              {formatDateTime(d.created_at)} by {d.requested_by} to {d.signer}
            </dd>
            {d.remarks && (
              <>
                <dt>Remarks</dt>
                <dd>{d.remarks}</dd>
              </>
            )}
          </dl>

          {d.signatures.map((s) => (
            <div key={s.id} className="alert alert-success" style={{ marginTop: '1rem' }}>
              <strong>Signed by {s.signer}</strong> on {formatDateTime(s.signed_at)} ({s.algorithm}, {s.source === 'platform' ? 'through the signing platform' : 'in PAMS'})
              <div className="small">Certificate: {s.certificate_subject}</div>
              <div className="small">
                Issued by {s.certificate_issuer} · serial {s.certificate_serial}
                {s.trusted_ca ? ` · trusted CA: ${s.trusted_ca}` : ''} · valid until {formatDate(s.certificate_not_after)}
              </div>
            </div>
          ))}

          {d.can_sign && (
            <form className="inline-panel" style={{ marginTop: '1rem' }} onSubmit={(e) => { e.preventDefault(); setError(null); sign.mutate() }}>
              <h4>Sign this document</h4>
              <ol className="small">
                <li>Download the file above and sign its exact bytes with your certificate's private key (your PNPKI signing tool, a token, or for example <code>openssl dgst -sha256 -sign key.pem -out signature.bin {d.file_name}</code>).</li>
                <li>Paste or load your certificate (PEM, with any intermediate certificates after it) and the signature (Base64 or the raw .bin file).</li>
              </ol>
              <div className="form-grid">
                <div className="field span-2">
                  <label htmlFor="sg-pem">Certificate (PEM) *</label>
                  <textarea id="sg-pem" rows={5} value={pem} onChange={(e) => setPem(e.target.value)} className="mono small" placeholder="-----BEGIN CERTIFICATE-----" required />
                  <input type="file" accept=".pem,.crt,.cer" onChange={(e) => readFile(e.target.files?.[0], setPem, false)} aria-label="Load certificate file" />
                </div>
                <div className="field span-2">
                  <label htmlFor="sg-sig">Signature (Base64) *</label>
                  <textarea id="sg-sig" rows={3} value={signature} onChange={(e) => setSignature(e.target.value)} className="mono small" required />
                  <input type="file" accept=".bin,.sig" onChange={(e) => readFile(e.target.files?.[0], setSignature, true)} aria-label="Load signature file" />
                </div>
                <div className="field">
                  <label htmlFor="sg-alg">Algorithm</label>
                  <select id="sg-alg" value={algorithm} onChange={(e) => setAlgorithm(e.target.value)}>
                    {ALGORITHMS.map((a) => (
                      <option key={a}>{a}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setDeclining(true)}>
                  Decline
                </button>
                <button type="submit" className="btn btn-primary" disabled={sign.isPending}>
                  {sign.isPending ? 'Checking…' : 'Sign'}
                </button>
              </div>
            </form>
          )}
          {declining && (
            <form className="inline-panel" onSubmit={(e) => { e.preventDefault(); setError(null); decline.mutate() }}>
              <div className="field">
                <label htmlFor="sg-why">Why are you not signing? *</label>
                <input id="sg-why" value={reason} onChange={(e) => setReason(e.target.value)} required />
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setDeclining(false)}>
                  Back
                </button>
                <button type="submit" className="btn btn-danger" disabled={decline.isPending}>
                  Decline
                </button>
              </div>
            </form>
          )}
          {d.can_cancel && !d.can_sign && (
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setCancelling(true)}>
                Cancel request
              </button>
            </div>
          )}
          {cancelling && (
            <ConfirmDialog title="Cancel signing request" message={`Withdraw ${d.request_number}? ${d.signer} will no longer be able to sign it.`} confirmLabel="Cancel request" busy={cancel.isPending} onConfirm={() => cancel.mutate()} onCancel={() => setCancelling(false)} />
          )}
        </>
      )}
    </Modal>
  )
}

function Verify() {
  const [result, setResult] = useState<Awaited<ReturnType<typeof signingApi.verify>> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const check = useMutation({ mutationFn: (f: File) => signingApi.verify(f), onSuccess: setResult, onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not check the file.') })
  return (
    <div className="card" style={{ maxWidth: 820 }}>
      <p>Choose a copy of a document to see whether it is exactly the file that was signed, and by whom.</p>
      <input type="file" onChange={(e) => { const f = e.target.files?.[0]; setResult(null); setError(null); if (f) check.mutate(f) }} aria-label="File to verify" />
      {check.isPending && <p className="muted">Checking…</p>}
      {error && <div className="alert alert-error">{error}</div>}
      {result && (
        <div className={'alert ' + (result.signed ? 'alert-success' : 'alert-warning')} style={{ marginTop: '1rem' }}>
          <strong>{result.message}</strong>
          {result.requests.map((r) => (
            <div key={r.id} className="small" style={{ marginTop: '0.4rem' }}>
              {r.request_number} “{r.title}” —{' '}
              {r.signatures.map((s) => `signed by ${s.signer} on ${formatDateTime(s.signed_at)} (${s.certificate_subject})`).join('; ')}
            </div>
          ))}
          <div className="small mono muted" style={{ marginTop: '0.4rem', wordBreak: 'break-all' }}>
            SHA-256 {result.sha256}
          </div>
        </div>
      )}
    </div>
  )
}

function TrustedCas({ canEdit }: { canEdit: boolean }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const list = useQuery({ queryKey: ['trusted-cas'], queryFn: signingApi.trusted })
  const [name, setName] = useState('')
  const [pem, setPem] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [removing, setRemoving] = useState<number | null>(null)
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['trusted-cas'] })
  const add = useMutation({
    mutationFn: () => signingApi.addTrusted({ name, pem }),
    onSuccess: (c) => { toast(`${c.name} is now trusted.`, 'success'); setName(''); setPem(''); refresh() },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not add the certificate.'),
  })
  const remove = useMutation({
    mutationFn: (id: number) => signingApi.removeTrusted(id),
    onSuccess: (r) => { toast(r.message, 'success'); setRemoving(null); refresh() },
    onError: (e) => { toast(e instanceof ApiError ? e.message : 'Could not remove it.', 'error'); setRemoving(null) },
  })
  return (
    <>
      <p className="muted">
        Signatures are accepted only from certificates issued by these authorities — for example the PNPKI root and intermediate CAs of the DICT, or the agency's own private CA.
      </p>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Subject</th>
              <th>Valid until</th>
              <th>SHA-256 fingerprint</th>
              {canEdit && <th></th>}
            </tr>
          </thead>
          <tbody>
            {list.data?.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td className="clip small">{c.subject}</td>
                <td>{formatDate(c.not_after)}</td>
                <td className="mono small clip">{c.fingerprint}</td>
                {canEdit && (
                  <td>
                    <button className="icon-btn delete" title={`Remove ${c.name}`} onClick={() => setRemoving(c.id)}>
                      🗑
                    </button>
                  </td>
                )}
              </tr>
            ))}
            {list.data?.length === 0 && (
              <tr>
                <td colSpan={canEdit ? 5 : 4} className="empty">
                  No trusted certificate authority yet; documents cannot be signed until one is added.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {canEdit && (
        <form className="inline-panel" style={{ marginTop: '1rem', maxWidth: 820 }} onSubmit={(e) => { e.preventDefault(); setError(null); add.mutate() }}>
          <h4>Trust a certificate authority</h4>
          {error && <div className="alert alert-error">{error}</div>}
          <div className="form-grid">
            <div className="field span-2">
              <label htmlFor="ca-name">Name *</label>
              <input id="ca-name" value={name} maxLength={150} onChange={(e) => setName(e.target.value)} required placeholder="e.g. PNPKI Root CA" />
            </div>
            <div className="field span-2">
              <label htmlFor="ca-pem">CA certificate (PEM) *</label>
              <textarea id="ca-pem" rows={6} className="mono small" value={pem} onChange={(e) => setPem(e.target.value)} required placeholder="-----BEGIN CERTIFICATE-----" />
            </div>
          </div>
          <div className="modal-footer">
            <button type="submit" className="btn btn-primary" disabled={add.isPending}>
              {add.isPending ? 'Adding…' : 'Add'}
            </button>
          </div>
        </form>
      )}
      {removing && (
        <ConfirmDialog title="Remove certificate authority" message="New signatures from certificates it issued will be refused. Signatures already recorded stay on record." confirmLabel="Remove" busy={remove.isPending} onConfirm={() => remove.mutate(removing)} onCancel={() => setRemoving(null)} />
      )}
    </>
  )
}

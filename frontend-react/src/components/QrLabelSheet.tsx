import { useQuery } from '@tanstack/react-query'
import type { QrLabel } from '../api/types'
import { Modal } from './Modal'
import './qr.css'

/** A printable sheet of QR stickers (TOR 18.1 — print or reprint one or many). */
export function QrLabelSheet({ title, load, onClose }: { title: string; load: () => Promise<{ agency: string; labels: QrLabel[] }>; onClose: () => void }) {
  const q = useQuery({ queryKey: ['qr-sheet', title], queryFn: load, gcTime: 0 })
  return (
    <Modal title={title} onClose={onClose} width={960}>
      <div className="slip-toolbar no-print">
        <span className="muted small">{q.data ? `${q.data.labels.length} sticker(s). Scan with any phone camera to open the record.` : ''}</span>
        <button className="btn btn-primary btn-sm" disabled={!q.data} onClick={() => window.print()}>
          🖨 Print stickers
        </button>
      </div>
      {q.isLoading && <div className="page-loading">Preparing QR codes…</div>}
      {q.isError && <div className="alert alert-error">{(q.error as Error).message}</div>}
      {q.data && (
        <div className="qr-sheet print-area">
          {q.data.labels.map((l) => (
            <QrSticker key={`${l.type}-${l.id}`} label={l} agency={q.data.agency} />
          ))}
        </div>
      )}
    </Modal>
  )
}

export function QrSticker({ label: l, agency }: { label: QrLabel; agency?: string }) {
  return (
    <div className="qr-sticker">
      <img src={l.data_uri} alt={`QR code for ${l.code}`} />
      <div className="qr-sticker-text">
        {agency && <div className="qr-agency">{agency}</div>}
        <div className="qr-code">{l.code}</div>
        <div className="qr-title">{l.title}</div>
        {l.subtitle && <div className="qr-sub">{l.subtitle}</div>}
        {l.accountability && <div className="qr-sub">{l.accountability}</div>}
      </div>
    </div>
  )
}

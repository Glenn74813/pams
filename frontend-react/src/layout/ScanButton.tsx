import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { qrApi } from '../api/endpoints'
import type { ScanResult } from '../api/types'
import { Modal } from '../components/Modal'
import '../components/qr.css'

const LABEL: Record<string, string> = {
  code: 'Code / tag',
  category: 'Category',
  unit_of_measure: 'Unit',
  current_stock: 'On hand',
  min_stock: 'Reorder point',
  serial_number: 'Serial no.',
  brand: 'Brand',
  status: 'Status',
  condition: 'Condition',
  custodian: 'Custodian',
  department: 'Department',
  accountability_number: 'PAR / ICS no.',
  acquisition_date: 'Acquired',
  purpose: 'Purpose',
  received_by: 'Received by',
  date_released: 'Released',
  expected_return_date: 'Due back',
  actual_return_date: 'Returned',
  tag_number: 'Asset tag',
  item: 'Item',
}

interface Detector {
  detect: (source: CanvasImageSource) => Promise<{ rawValue: string }[]>
}

/** Scanner in the top bar: a USB/Bluetooth scanner types into the box; on phones the camera can read the QR. */
export function ScanButton() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button className="notif-bell" title="Scan a QR code or barcode" aria-label="Scan" onClick={() => setOpen(true)}>
        ⌖
      </button>
      {open && <ScanModal onClose={() => setOpen(false)} />}
    </>
  )
}

export function ScanModal({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [result, setResult] = useState<ScanResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [camera, setCamera] = useState(false)
  const video = useRef<HTMLVideoElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const canDetect = typeof window !== 'undefined' && 'BarcodeDetector' in window

  const lookup = async (data: string) => {
    if (!data.trim()) return
    setError(null)
    try {
      setResult(await qrApi.scan(data))
      setText('')
    } catch (e) {
      setResult(null)
      setError(e instanceof ApiError ? e.message : 'Could not look that up.')
    }
    input.current?.focus()
  }

  useEffect(() => {
    if (!camera) return
    let stream: MediaStream | null = null
    let stop = false
    const Ctor = (window as unknown as { BarcodeDetector: new (o: { formats: string[] }) => Detector }).BarcodeDetector
    const detector = new Ctor({ formats: ['qr_code', 'code_128', 'code_39', 'ean_13'] })
    const loop = async () => {
      if (stop || !video.current) return
      try {
        const found = await detector.detect(video.current)
        if (found.length > 0) {
          setCamera(false)
          void lookup(found[0].rawValue)
          return
        }
      } catch {
        /* frame not ready */
      }
      setTimeout(() => void loop(), 300)
    }
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' } })
      .then((s) => {
        stream = s
        if (video.current) {
          video.current.srcObject = s
          void video.current.play()
          void loop()
        }
      })
      .catch(() => {
        setError('The camera could not be opened. Allow camera access, or type the code instead.')
        setCamera(false)
      })
    return () => {
      stop = true
      stream?.getTracks().forEach((t) => t.stop())
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [camera])

  return (
    <Modal title="Scan" onClose={onClose} width={560}>
      <form
        className="toolbar"
        onSubmit={(e) => {
          e.preventDefault()
          void lookup(text)
        }}
      >
        <input
          ref={input}
          autoFocus
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Scan, or type an item code, asset tag or gate-pass number"
          aria-label="Code"
          style={{ flex: 1 }}
        />
        <button className="btn btn-primary" type="submit">
          Find
        </button>
        {canDetect && (
          <button className="btn btn-outline" type="button" onClick={() => setCamera((c) => !c)}>
            {camera ? 'Stop camera' : '📷 Camera'}
          </button>
        )}
      </form>
      {camera && <video ref={video} className="scan-video" muted playsInline />}
      {error && <div className="alert alert-error">{error}</div>}
      {result && (
        <div className="scan-result">
          <h3>{result.title}</h3>
          <dl>
            {Object.entries(result)
              .filter(([k, v]) => LABEL[k] && v != null && v !== '')
              .map(([k, v]) => (
                <div key={k} style={{ display: 'contents' }}>
                  <dt>{LABEL[k]}</dt>
                  <dd>{String(v).replace(/_/g, ' ')}</dd>
                </div>
              ))}
          </dl>
          <div className="modal-footer">
            <button
              className="btn btn-primary"
              onClick={() => {
                onClose()
                void navigate(`${result.path}?search=${encodeURIComponent(String(result.code))}`)
              }}
            >
              Open in {result.type === 'item' ? 'Items' : result.type === 'property' ? 'Assets' : 'Gate Pass'}
            </button>
          </div>
        </div>
      )}
    </Modal>
  )
}

import { useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { poApi, receivingApi } from '../../api/endpoints'
import type { InspectionReport } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { IarDetailModal } from './IarDetailModal'
import { IarFormModal } from './IarFormModal'
import { ACCEPTANCE, IAR_STATUS, PO_STATUS } from './labels'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

type Tab = 'expected' | 'reports'

export function ReceivingPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<Tab>(can('accept_delivery') && !can('create_receiving') ? 'reports' : 'expected')
  const [recording, setRecording] = useState<{ poId: number; iar: InspectionReport | null } | null>(null)
  const [viewing, setViewing] = useState<number | null>(null)
  const refresh = () => {
    for (const k of ['pos', 'po', 'iars', 'iar', 'items', 'item', 'lots', 'stock-card']) void queryClient.invalidateQueries({ queryKey: [k] })
  }
  return (
    <div className="page page-wide">
      <h1 className="page-title">Receiving</h1>
      <div className="tabs">
        <button className={tab === 'expected' ? 'active' : ''} onClick={() => setTab('expected')}>
          Expected deliveries
        </button>
        <button className={tab === 'reports' ? 'active' : ''} onClick={() => setTab('reports')}>
          Inspection &amp; acceptance
        </button>
      </div>
      {tab === 'expected' && <Expected canRecord={can('create_receiving')} onRecord={(poId) => setRecording({ poId, iar: null })} />}
      {tab === 'reports' && <Reports onView={setViewing} />}
      {recording && (
        <IarFormModal
          poId={recording.poId}
          iar={recording.iar}
          onClose={() => setRecording(null)}
          onSaved={(r) => {
            toast(`${r.iar_number} saved; it goes to the supply officer for acceptance.`, 'success')
            setRecording(null)
            refresh()
            setViewing(r.id)
          }}
        />
      )}
      {viewing !== null && (
        <IarDetailModal
          iarId={viewing}
          onClose={() => setViewing(null)}
          onEdit={(r) => {
            setViewing(null)
            setRecording({ poId: r.purchase_order!.id, iar: r })
          }}
          onChanged={(m, closed) => {
            toast(m, 'success')
            if (closed) setViewing(null)
            refresh()
          }}
        />
      )}
    </div>
  )
}

function Expected({ canRecord, onRecord }: { canRecord: boolean; onRecord: (poId: number) => void }) {
  const [page, setPage] = useState(1)
  const list = useQuery({ queryKey: ['pos', 'expected', page], queryFn: () => poApi.list({ page, status: 'confirmed,partially_received' }), placeholderData: keepPreviousData })
  return (
    <>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>PO no.</th>
              <th>Supplier</th>
              <th>Office</th>
              <th>Due</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((p) => (
              <tr key={p.id}>
                <td className="mono">{p.po_number}</td>
                <td>{p.supplier.name}</td>
                <td>{p.department?.name ?? '—'}</td>
                <td>
                  {formatDate(p.delivery_date)} {p.late && <span className="badge st-bad">overdue</span>}
                </td>
                <td>
                  <Badge map={PO_STATUS} value={p.status} />
                </td>
                <td>
                  {canRecord && (
                    <button className="btn btn-primary btn-sm" onClick={() => onRecord(p.id)}>
                      Record delivery
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No confirmed purchase orders awaiting delivery.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
    </>
  )
}

function Reports({ onView }: { onView: (id: number) => void }) {
  const [page, setPage] = useState(1)
  const [acceptance, setAcceptance] = useState('pending')
  const [search, setSearch] = useState('')
  const q = useDebounced(search.trim())
  const list = useQuery({
    queryKey: ['iars', page, acceptance, q],
    queryFn: () => receivingApi.list({ page, acceptance_status: acceptance || undefined, search: q || undefined }),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search IAR no." value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} aria-label="Search inspection reports" />
        <select value={acceptance} onChange={(e) => { setAcceptance(e.target.value); setPage(1) }} aria-label="Acceptance">
          <option value="">All</option>
          {Object.entries(ACCEPTANCE).map(([k, [l]]) => (
            <option key={k} value={k}>
              {l}
            </option>
          ))}
        </select>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>IAR no.</th>
              <th>PO</th>
              <th>Supplier</th>
              <th>Delivered</th>
              <th className="num">Accepted / rejected</th>
              <th>Inspection</th>
              <th>Acceptance</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((r) => (
              <tr key={r.id}>
                <td>
                  <button className="link-btn mono" onClick={() => onView(r.id)}>
                    {r.iar_number}
                  </button>
                </td>
                <td className="mono">{r.purchase_order?.name}</td>
                <td>{r.supplier?.name}</td>
                <td>{formatDate(r.delivery_date)}</td>
                <td className="num">
                  {r.total_items_accepted} / {r.total_items_rejected}
                </td>
                <td>
                  <Badge map={IAR_STATUS} value={r.status} />
                </td>
                <td>
                  <Badge map={ACCEPTANCE} value={r.acceptance_status} />
                </td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No inspection reports here.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
    </>
  )
}

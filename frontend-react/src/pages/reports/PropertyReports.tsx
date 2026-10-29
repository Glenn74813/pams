import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { assetsApi, disposalApi, reportsApi } from '../../api/endpoints'
import type { Asset } from '../../api/types'
import { Pager } from '../../components/Pager'
import { useDebounced } from '../../lib/hooks'
import { formatDate, formatPeso, today } from '../../lib/format'
import { AssetPicker } from '../assets/pickers'
import { ExcelButton } from '../../components/ExcelButton'
import { ExportButton, PreviewTable } from './shared'

export function PropertyCardReport() {
  const [asset, setAsset] = useState<Asset | null>(null)
  const q = useQuery({ queryKey: ['property-card', asset?.id], queryFn: () => reportsApi.propertyCard(asset!.id), enabled: !!asset })
  const p = q.data?.property
  return (
    <>
      <div className="toolbar">
        <div className="field" style={{ minWidth: 320 }}>
          <label htmlFor="pc-asset">Property</label>
          <AssetPicker id="pc-asset" onPick={setAsset} />
        </div>
        {asset && <ExportButton path={`/reports/property-card/${asset.id}/export`} name={`PropertyCard_${asset.tag_number}.xlsx`} label="Export Property Card (Appendix 69)" />}
      </div>
      {p && (
        <p className="small">
          <strong>{p.tag_number}</strong> — {p.description} · {p.article ?? '—'} · acquired {formatDate(p.acquisition_date as string | null)} @{' '}
          {formatPeso(Number(p.acquisition_cost))} · book value {formatPeso(Number(p.book_value))} · custodian {p.custodian ?? '—'} · {p.status}
        </p>
      )}
      <PreviewTable
        columns={[
          { key: 'date', label: 'Date' },
          { key: 'reference', label: 'Reference' },
          { key: 'particulars', label: 'Particulars' },
          { key: 'office', label: 'Office / Officer' },
        ]}
        rows={q.data?.rows}
        empty="No movements recorded."
      />
    </>
  )
}

export function PtrReport() {
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const term = useDebounced(search)
  const q = useQuery({ queryKey: ['report-ptr', page, term], queryFn: () => assetsApi.moves(page, undefined, term || undefined), placeholderData: keepPreviousData })
  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search tag or reference…" value={search} onChange={(e) => (setSearch(e.target.value), setPage(1))} aria-label="Search" />
      </div>
      {q.data && q.data.data.length === 0 && <p className="muted">No transfers recorded.</p>}
      {q.data && q.data.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Property Tag</th>
                <th>Item</th>
                <th>From</th>
                <th>To</th>
                <th>Transfer Date</th>
                <th>Reason</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {q.data.data.map((t) => (
                <tr key={t.id}>
                  <td>{t.tag_number}</td>
                  <td>{t.item_name}</td>
                  <td>{t.from?.name ?? '—'}</td>
                  <td>{t.to?.name ?? '—'}</td>
                  <td className="nowrap">{formatDate(t.transfer_date)}</td>
                  <td>{t.reason}</td>
                  <td>
                    <ExcelButton path={`/properties/transfers/${t.id}/export`} name={`PTR_${t.reference_number ?? t.id}.xlsx`} label="PTR" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager pagination={q.data?.pagination} onPage={setPage} />
    </>
  )
}

export function RpcppeReport() {
  const [type, setType] = useState('ppe')
  const [asAt, setAsAt] = useState(today())
  const q = useQuery({ queryKey: ['rpcppe', type], queryFn: () => reportsApi.preview('rpcppe', { ppe_type: type }) })
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="rpcppe-type">PPE Type</label>
          <select id="rpcppe-type" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="ppe">Property, Plant & Equipment</option>
            <option value="semi_expandable">Semi-Expendable</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="rpcppe-date">As At Date</label>
          <input id="rpcppe-date" type="date" value={asAt} onChange={(e) => setAsAt(e.target.value)} />
        </div>
        <ExportButton path="/reports/rpcppe/export" params={{ ppe_type: type, as_at_date: asAt || undefined }} name="RPCPPE.xlsx" label="Export RPCPPE (Appendix 73)" />
      </div>
      <PreviewTable
        columns={[
          { key: 'article', label: 'Article' },
          { key: 'description', label: 'Description' },
          { key: 'property_no', label: 'Property No.' },
          { key: 'unit_value', label: 'Unit Value', type: 'money' },
          { key: 'condition', label: 'Condition' },
          { key: 'accountable', label: 'Accountable Officer' },
        ]}
        rows={q.data?.rows}
        empty="No property on record."
      />
    </>
  )
}

export function RpcspReport() {
  const [asAt, setAsAt] = useState(today())
  const q = useQuery({ queryKey: ['rpcsp'], queryFn: () => reportsApi.preview('rpcsp') })
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label htmlFor="rpcsp-date">As At Date</label>
          <input id="rpcsp-date" type="date" value={asAt} onChange={(e) => setAsAt(e.target.value)} />
        </div>
        <ExportButton path="/reports/rpcsp/export" params={{ as_at_date: asAt || undefined }} name="RPCSP.xlsx" label="Export RPCSP (Appendix 68)" />
      </div>
      <PreviewTable
        columns={[
          { key: 'article', label: 'Article' },
          { key: 'description', label: 'Description' },
          { key: 'sep_no', label: 'Semi-exp. No.' },
          { key: 'unit', label: 'Unit' },
          { key: 'unit_value', label: 'Unit Value', type: 'money' },
          { key: 'balance_per_card', label: 'Balance / Card', type: 'num' },
        ]}
        rows={q.data?.rows}
        empty="No semi-expendable items."
      />
    </>
  )
}

function CompletedDisposals({ value, onChange, category }: { value: number[]; onChange: (ids: number[]) => void; category?: string }) {
  const q = useQuery({ queryKey: ['report-disposals', category], queryFn: () => disposalApi.list({ page: 1, status: 'completed', category }) })
  return (
    <select
      multiple
      className="multi-select"
      value={value.map(String)}
      onChange={(e) => onChange(Array.from(e.target.selectedOptions).map((o) => Number(o.value)))}
      aria-label="Completed disposals"
    >
      {q.data?.data.map((d) => (
        <option key={d.id} value={d.id}>
          {d.disposal_number} — {d.item_name ?? '—'} {d.tag_number ? `(${d.tag_number})` : ''} · {d.disposal_type}
        </option>
      ))}
    </select>
  )
}

export function WmrReport() {
  const [ids, setIds] = useState<number[]>([])
  const [storage, setStorage] = useState('')
  const q = useQuery({ queryKey: ['wmr', ids], queryFn: () => reportsApi.preview('wmr', { disposal_ids: ids.join(',') }), enabled: ids.length > 0 })
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label>Completed Disposals (Ctrl/Cmd-click for several)</label>
          <CompletedDisposals value={ids} onChange={setIds} />
        </div>
        <div className="field">
          <label htmlFor="wmr-place">Storage Place</label>
          <input id="wmr-place" value={storage} onChange={(e) => setStorage(e.target.value)} placeholder="e.g. Main Warehouse" />
        </div>
        <ExportButton path="/reports/wmr/export" params={{ disposal_ids: ids.join(','), storage_place: storage || undefined }} name="WMR.xlsx"
          label="Export WMR (Appendix 65)" disabled={ids.length === 0} />
      </div>
      <PreviewTable
        columns={[
          { key: 'qty', label: 'Qty', type: 'num' },
          { key: 'unit', label: 'Unit' },
          { key: 'description', label: 'Item / Waste Material' },
          { key: 'disposal', label: 'Disposal' },
          { key: 'reason', label: 'Reason' },
        ]}
        rows={ids.length ? q.data?.rows : undefined}
      />
      {ids.length === 0 && <p className="muted">Choose one or more completed disposals.</p>}
    </>
  )
}

export function IirupReport() {
  const [ids, setIds] = useState<number[]>([])
  const [officer, setOfficer] = useState('')
  const [designation, setDesignation] = useState('')
  const [station, setStation] = useState('')
  const q = useQuery({ queryKey: ['iirup', ids], queryFn: () => reportsApi.preview('iirup', { disposal_ids: ids.join(',') }), enabled: ids.length > 0 })
  return (
    <>
      <div className="toolbar">
        <div className="field">
          <label>Completed Disposals (Unserviceable)</label>
          <CompletedDisposals value={ids} onChange={setIds} category="property" />
        </div>
        <div className="field">
          <label htmlFor="iirup-officer">Accountable Officer</label>
          <input id="iirup-officer" value={officer} onChange={(e) => setOfficer(e.target.value)} placeholder="Full name" />
        </div>
        <div className="field">
          <label htmlFor="iirup-des">Designation</label>
          <input id="iirup-des" value={designation} onChange={(e) => setDesignation(e.target.value)} placeholder="e.g. Supply Officer" />
        </div>
        <div className="field">
          <label htmlFor="iirup-st">Station</label>
          <input id="iirup-st" value={station} onChange={(e) => setStation(e.target.value)} placeholder="e.g. Main Office" />
        </div>
        <ExportButton
          path="/reports/iirup/export"
          params={{ disposal_ids: ids.join(','), accountable_officer: officer || undefined, designation: designation || undefined, station: station || undefined }}
          name="IIRUP.xlsx"
          label="Export IIRUP (Appendix 74)"
          disabled={ids.length === 0}
        />
      </div>
      <PreviewTable
        columns={[
          { key: 'date_acquired', label: 'Date Acquired' },
          { key: 'property_no', label: 'Property No.' },
          { key: 'description', label: 'Description' },
          { key: 'unit_cost', label: 'Unit Cost', type: 'money' },
          { key: 'condition', label: 'Condition' },
          { key: 'remarks', label: 'Remarks' },
        ]}
        rows={ids.length ? q.data?.rows : undefined}
      />
      {ids.length === 0 && <p className="muted">Choose one or more completed property disposals.</p>}
    </>
  )
}

export function RlsddpReport() {
  const [asset, setAsset] = useState<Asset | null>(null)
  const [f, setF] = useState({
    status: '',
    circumstances: '',
    department: '',
    rlsddp_number: '',
    rlsddp_date: today(),
    par_number: '',
    par_date: '',
    police_notified: '',
    police_station: '',
    police_date: '',
  })
  const set = (k: keyof typeof f, v: string) => setF((x) => ({ ...x, [k]: v }))
  const params = { property_id: asset?.id, ...Object.fromEntries(Object.entries(f).filter(([, v]) => v !== '')) }
  const q = useQuery({
    queryKey: ['rlsddp', asset?.id, f.status, f.circumstances],
    queryFn: () => reportsApi.preview('rlsddp', { property_id: asset!.id, status: f.status, circumstances: f.circumstances }),
    enabled: !!asset,
  })
  return (
    <>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="rl-asset">Property *</label>
          <AssetPicker id="rl-asset" onPick={setAsset} />
          {asset && <span className="small muted">{asset.tag_number} — {asset.item?.name}</span>}
        </div>
        <div className="field">
          <label htmlFor="rl-status">Status *</label>
          <select id="rl-status" value={f.status} onChange={(e) => set('status', e.target.value)}>
            <option value="">-- Select status --</option>
            <option value="lost">Lost</option>
            <option value="stolen">Stolen</option>
            <option value="damaged">Damaged</option>
            <option value="destroyed">Destroyed</option>
          </select>
        </div>
        <div className="field full">
          <label htmlFor="rl-circ">Circumstances *</label>
          <textarea id="rl-circ" rows={3} value={f.circumstances} onChange={(e) => set('circumstances', e.target.value)}
            placeholder="Describe the circumstances of the loss, theft, damage, or destruction..." />
        </div>
        <div className="field">
          <label htmlFor="rl-dept">Department</label>
          <input id="rl-dept" value={f.department} onChange={(e) => set('department', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-no">RLSDDP No.</label>
          <input id="rl-no" value={f.rlsddp_number} onChange={(e) => set('rlsddp_number', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-date">RLSDDP Date</label>
          <input id="rl-date" type="date" value={f.rlsddp_date} onChange={(e) => set('rlsddp_date', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-par">PAR No.</label>
          <input id="rl-par" value={f.par_number} onChange={(e) => set('par_number', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-pard">PAR Date</label>
          <input id="rl-pard" type="date" value={f.par_date} onChange={(e) => set('par_date', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-police">Police notified?</label>
          <select id="rl-police" value={f.police_notified} onChange={(e) => set('police_notified', e.target.value)}>
            <option value="">—</option>
            <option value="1">Yes</option>
            <option value="0">No</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="rl-ps">Police station</label>
          <input id="rl-ps" value={f.police_station} onChange={(e) => set('police_station', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="rl-pd">Date reported to police</label>
          <input id="rl-pd" type="date" value={f.police_date} onChange={(e) => set('police_date', e.target.value)} />
        </div>
      </div>
      <div className="toolbar">
        <ExportButton path="/reports/rlsddp/export" params={params} name="RLSDDP.xlsx" label="Export RLSDDP (Appendix 75)"
          disabled={!asset || !f.status || !f.circumstances.trim()} />
      </div>
      <PreviewTable
        columns={[
          { key: 'property_no', label: 'Property No.' },
          { key: 'description', label: 'Description' },
          { key: 'unit_cost', label: 'Unit Cost', type: 'money' },
          { key: 'status', label: 'Status' },
          { key: 'circumstances', label: 'Circumstances' },
        ]}
        rows={asset ? q.data?.rows : undefined}
      />
    </>
  )
}

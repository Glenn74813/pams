import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Asset, Item } from '../../api/types'
import { ItemPicker } from '../../components/ItemPicker'
import { formatDate, today } from '../../lib/format'
import { AssetPicker } from '../assets/pickers'
import { ExportButton } from './shared'

interface Named {
  id: number
  name: string
}

interface TorOptions {
  departments: Named[]
  suppliers: Named[]
  users: (Named & { email: string; department: string | null })[]
  semi_expendable_items: (Named & { code: string })[]
  issuances: { id: number; request_number: string; par_number: string | null; ics_number: string | null; request_date: string; requester: string | null }[]
  accountability_numbers: { accountability_number: string; holder: string | null; assets: number }[]
  semi_expendable_transfers: { id: number; reference_number: string | null; transfer_date: string; tag_number: string; item_name: string; from_name: string | null; to_name: string | null }[]
  cnas_lines: { id: number; cnas_number: string | null; cnas_date: string | null; description: string; apr_number: string }[]
}

const torOptions = () => api<TorOptions>('GET', '/reports/tor/options')

type Format = 'xlsx' | 'csv' | 'pdf'

function firstOfYear() {
  return `${new Date().getFullYear()}-01-01`
}

function Section({ title, code, hint, children }: { title: string; code?: string; hint: string; children: React.ReactNode }) {
  return (
    <section style={{ padding: '1rem', marginBottom: '1rem', background: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: 10 }}>
      <h3 style={{ fontSize: '1rem', marginBottom: '0.2rem' }}>
        {title} {code && <span className="muted small">· {code}</span>}
      </h3>
      <p className="muted small" style={{ marginBottom: '0.6rem' }}>
        {hint}
      </p>
      <div className="toolbar" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
        {children}
      </div>
    </section>
  )
}

function DateField({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} type="date" value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  )
}

function FormatField({ id, value, onChange }: { id: string; value: Format; onChange: (v: Format) => void }) {
  return (
    <div className="field" style={{ minWidth: 110 }}>
      <label htmlFor={id}>Format</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value as Format)}>
        <option value="xlsx">Excel (.xlsx)</option>
        <option value="csv">CSV (.csv)</option>
        <option value="pdf">PDF (.pdf)</option>
      </select>
    </div>
  )
}

function OfficeField({ id, value, onChange, options }: { id: string; value: string; onChange: (v: string) => void; options?: TorOptions }) {
  return (
    <div className="field">
      <label htmlFor={id}>Office</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">All offices</option>
        {options?.departments.map((d) => (
          <option key={d.id} value={d.id}>
            {d.name}
          </option>
        ))}
      </select>
    </div>
  )
}

function UserField({ id, label, value, onChange, options, allLabel }: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  options?: TorOptions
  allLabel?: string
}) {
  return (
    <div className="field" style={{ minWidth: 240 }}>
      <label htmlFor={id}>{label}</label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{allLabel ?? 'Choose a person'}</option>
        {options?.users.map((u) => (
          <option key={u.id} value={u.id}>
            {u.name}
            {u.department ? ` — ${u.department}` : ''}
          </option>
        ))}
      </select>
    </div>
  )
}

/** A date-range list report (register or master list) in Excel, CSV or PDF. */
type PeriodKind = 'dates' | 'month' | 'year'

function lastDayOf(ym: string) {
  const [y, m] = ym.split('-').map(Number)
  const d = new Date(y, m, 0).getDate()
  return `${ym}-${String(d).padStart(2, '0')}`
}

function RangeReport({ id, title, code, hint, path, name, optional, office, supplier, options, extra, extraParams }: {
  id: string
  title: string
  code?: string
  hint: string
  path: string
  name: string
  /** Dates may be left blank (master lists: everything on record). */
  optional?: boolean
  office?: boolean
  /** Offer a filter by supplier. */
  supplier?: boolean
  options?: TorOptions
  /** More filter fields for this report. */
  extra?: React.ReactNode
  extraParams?: Record<string, string | undefined>
}) {
  const [kind, setKind] = useState<PeriodKind>('dates')
  const [month, setMonth] = useState(today().slice(0, 7))
  const [year, setYear] = useState(today().slice(0, 4))
  const [fromDate, setFrom] = useState(optional ? '' : firstOfYear())
  const [toDate, setTo] = useState(optional ? '' : today())
  const [dept, setDept] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const [format, setFormat] = useState<Format>('xlsx')
  const from = kind === 'month' ? (month ? `${month}-01` : '') : kind === 'year' ? (/^\d{4}$/.test(year) ? `${year}-01-01` : '') : fromDate
  const to = kind === 'month' ? (month ? lastDayOf(month) : '') : kind === 'year' ? (/^\d{4}$/.test(year) ? `${year}-12-31` : '') : toDate
  const bad = !!from && !!to && to < from
  return (
    <Section title={title} code={code} hint={hint}>
      <div className="field" style={{ minWidth: 120 }}>
        <label htmlFor={`${id}-kind`}>Period</label>
        <select id={`${id}-kind`} value={kind} onChange={(e) => setKind(e.target.value as PeriodKind)}>
          <option value="dates">Any dates</option>
          <option value="month">Monthly</option>
          <option value="year">Annual</option>
        </select>
      </div>
      {kind === 'dates' && (
        <>
          <DateField id={`${id}-from`} label={optional ? 'From (optional)' : 'From'} value={fromDate} onChange={setFrom} />
          <DateField id={`${id}-to`} label={optional ? 'To (optional)' : 'To'} value={toDate} onChange={setTo} />
        </>
      )}
      {kind === 'month' && (
        <div className="field">
          <label htmlFor={`${id}-month`}>Month</label>
          <input id={`${id}-month`} type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        </div>
      )}
      {kind === 'year' && (
        <div className="field" style={{ maxWidth: 110 }}>
          <label htmlFor={`${id}-year`}>Year</label>
          <input id={`${id}-year`} type="number" min={2000} max={2100} value={year} onChange={(e) => setYear(e.target.value)} />
        </div>
      )}
      {office && <OfficeField id={`${id}-dept`} value={dept} onChange={setDept} options={options} />}
      {supplier && (
        <div className="field">
          <label htmlFor={`${id}-sup`}>Supplier</label>
          <select id={`${id}-sup`} value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
            <option value="">All suppliers</option>
            {options?.suppliers.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
      )}
      {extra}
      <FormatField id={`${id}-fmt`} value={format} onChange={setFormat} />
      <ExportButton
        path={path}
        params={{ from: from || undefined, to: to || undefined, department_id: dept || undefined, supplier_id: supplierId || undefined, ...extraParams, format }}
        name={`${name}.${format}`}
        label="Download"
        disabled={bad || (!optional && (!from || !to))}
      />
      {bad && <span className="small" style={{ color: 'var(--danger)' }}>The end date is before the start date.</span>}
    </Section>
  )
}

/** Counts of one kind or all: the RPCI, RPCSP and RPCPPE counts held in a period. */
function PhysicalCountRegister({ options }: { options?: TorOptions }) {
  const [type, setType] = useState('')
  return (
    <RangeReport id="tor-counts" title="Physical Counts" code="RPCI · RPCSP · RPCPPE" hint="Counts held in the period, with items counted, found and discrepancies. The forms themselves export as at a date from their own tabs."
      path="/reports/registers/physical-counts/export" name="Physical_Counts" options={options} extraParams={{ count_type: type || undefined }}
      extra={
        <div className="field">
          <label htmlFor="tor-counts-type">Type</label>
          <select id="tor-counts-type" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">All</option>
            <option value="supplies">Inventories (RPCI)</option>
            <option value="semi_expendable">Semi-expendable (RPCSP)</option>
            <option value="ppe">PPE (RPCPPE)</option>
          </select>
        </div>
      }
    />
  )
}

/** Stock Card movements of one item or all. */
function StockMovements({ options }: { options?: TorOptions }) {
  const [item, setItem] = useState<Item | null>(null)
  return (
    <RangeReport id="tor-stock" title="Stock Card Movements" code="SC" hint="Receipts, issues and running balance for the period. Leave the item empty for all items."
      path="/reports/registers/stock-movements/export" name="Stock_Movements" options={options} extraParams={{ item_id: item ? String(item.id) : undefined }}
      extra={
        <div className="field" style={{ minWidth: 240 }}>
          <label htmlFor="tor-stock-item">Item (optional)</label>
          {item ? (
            <div className="chosen">
              <span>{item.name}</span>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setItem(null)}>
                Clear
              </button>
            </div>
          ) : (
            <ItemPicker id="tor-stock-item" onPick={setItem} />
          )}
        </div>
      }
    />
  )
}

function PpeLedgerCard() {
  const [asset, setAsset] = useState<Asset | null>(null)
  const [asAt, setAsAt] = useState(today())
  return (
    <Section title="PPE Ledger Card" code="Appendix 70" hint="Acquisition, accumulated depreciation by year, transfers and adjusted cost of one asset.">
      <div className="field" style={{ minWidth: 300 }}>
        <label htmlFor="tor-ppelc-asset">Asset {asset && <span className="muted">— {asset.tag_number}</span>}</label>
        <AssetPicker id="tor-ppelc-asset" onPick={setAsset} />
      </div>
      <DateField id="tor-ppelc-date" label="As at" value={asAt} onChange={setAsAt} />
      <ExportButton path={asset ? `/reports/ppe-ledger-card/${asset.id}/export` : ''} params={{ as_at_date: asAt || undefined }}
        name={`PPELedgerCard_${asset?.tag_number ?? ''}.xlsx`} label="Export PPELC" disabled={!asset} />
    </Section>
  )
}

function SuppliesLedgerCard() {
  const [item, setItem] = useState<Item | null>(null)
  return (
    <Section title="Supplies Ledger Card" code="Appendix 57" hint="Receipts and issues of one item with unit cost, amounts and the balance at moving average cost.">
      <div className="field" style={{ minWidth: 300 }}>
        <label htmlFor="tor-slc-item">Item {item && <span className="muted">— {item.code} {item.name}</span>}</label>
        <ItemPicker id="tor-slc-item" onPick={setItem} />
      </div>
      <ExportButton path={item ? `/reports/supplies-ledger-card/${item.id}/export` : ''} name={`SuppliesLedgerCard_${item?.code ?? ''}.xlsx`}
        label="Export SLC" disabled={!item} />
    </Section>
  )
}

function SemiExpendableCards({ options }: { options?: TorOptions }) {
  const [itemId, setItemId] = useState('')
  const item = options?.semi_expendable_items.find((i) => String(i.id) === itemId)
  return (
    <Section title="Semi-expendable Property Card / Ledger Card" code="Annex A.1 / A.2"
      hint="COA Circular 2022-004 cards of one semi-expendable item: receipts, ICS issues to offices/officers and balance.">
      <div className="field" style={{ minWidth: 300 }}>
        <label htmlFor="tor-spc-item">Semi-expendable item</label>
        <select id="tor-spc-item" value={itemId} onChange={(e) => setItemId(e.target.value)}>
          <option value="">Choose an item</option>
          {options?.semi_expendable_items.map((i) => (
            <option key={i.id} value={i.id}>
              {i.code} — {i.name}
            </option>
          ))}
        </select>
      </div>
      <ExportButton path={`/reports/semi-expendable-card/${itemId}/export`} name={`SPC_${item?.code ?? ''}.xlsx`} label="Property Card (SPC)" disabled={!itemId} />
      <ExportButton path={`/reports/semi-expendable-ledger-card/${itemId}/export`} name={`SPLC_${item?.code ?? ''}.xlsx`} label="Ledger Card (SPLC)" disabled={!itemId} />
    </Section>
  )
}

function SemiExpendableIssued({ options }: { options?: TorOptions }) {
  const [from, setFrom] = useState(firstOfYear())
  const [to, setTo] = useState(today())
  const [dept, setDept] = useState('')
  const [serial, setSerial] = useState('')
  const bad = !from || !to || to < from
  return (
    <Section title="Semi-expendable Property Issued" code="RegSPI · RSPI"
      hint="Registry of Semi-Expendable Property Issued (Annex A.4, optionally one office) and Report of Semi-Expendable Property Issued (Annex A.7) for a period.">
      <DateField id="tor-spi-from" label="From" value={from} onChange={setFrom} />
      <DateField id="tor-spi-to" label="To" value={to} onChange={setTo} />
      <OfficeField id="tor-spi-dept" value={dept} onChange={setDept} options={options} />
      <div className="field">
        <label htmlFor="tor-spi-serial">RSPI Serial No.</label>
        <input id="tor-spi-serial" value={serial} onChange={(e) => setSerial(e.target.value)} placeholder="optional" />
      </div>
      <ExportButton path="/reports/regspi/export" params={{ from, to, department_id: dept || undefined }} name="RegSPI.xlsx" label="RegSPI" disabled={bad} />
      <ExportButton path="/reports/rspi/export" params={{ from, to, serial_no: serial || undefined }} name="RSPI.xlsx" label="RSPI" disabled={bad} />
    </Section>
  )
}

function ItrReport({ options }: { options?: TorOptions }) {
  const [id, setId] = useState('')
  const list = options?.semi_expendable_transfers ?? []
  const t = list.find((x) => String(x.id) === id)
  return (
    <Section title="Inventory Transfer Report" code="ITR · Annex A.5" hint="Transfer of semi-expendable property between accountable officers (PPE transfers use the PTR).">
      <div className="field" style={{ minWidth: 340 }}>
        <label htmlFor="tor-itr">Semi-expendable transfer</label>
        <select id="tor-itr" value={id} onChange={(e) => setId(e.target.value)} disabled={list.length === 0}>
          <option value="">{list.length === 0 ? 'No semi-expendable transfers recorded' : 'Choose a transfer'}</option>
          {list.map((x) => (
            <option key={x.id} value={x.id}>
              {x.reference_number ?? `#${x.id}`} — {x.tag_number} {x.item_name} · {x.from_name ?? '—'} → {x.to_name ?? '—'} ({formatDate(x.transfer_date)})
            </option>
          ))}
        </select>
      </div>
      <ExportButton path={`/reports/itr/${id}/export`} name={`ITR_${t?.reference_number ?? id}.xlsx`} label="Export ITR" disabled={!id} />
    </Section>
  )
}

function IrpReport({ options }: { options?: TorOptions }) {
  const [source, setSource] = useState('')
  const [kind, id] = source.split(':')
  const params = kind === 'r' ? { request_id: id } : kind === 'p' ? { par_number: id } : undefined
  return (
    <Section title="Invoice Receipt for Property" code="IRP" hint="Receipt of property issued to an officer: a request's PAR/ICS lines, or the assets under one PAR/ICS number.">
      <div className="field" style={{ minWidth: 340 }}>
        <label htmlFor="tor-irp">Issuance</label>
        <select id="tor-irp" value={source} onChange={(e) => setSource(e.target.value)}>
          <option value="">Choose an issuance</option>
          <optgroup label="Assets by PAR/ICS number">
            {options?.accountability_numbers.map((a) => (
              <option key={a.accountability_number} value={`p:${a.accountability_number}`}>
                {a.accountability_number} — {a.holder ?? 'unassigned'} ({a.assets} asset{a.assets === 1 ? '' : 's'})
              </option>
            ))}
          </optgroup>
          <optgroup label="Requests with PAR/ICS">
            {options?.issuances.map((r) => (
              <option key={r.id} value={`r:${r.id}`}>
                {r.par_number ?? r.ics_number} — {r.request_number} · {r.requester ?? '—'}
              </option>
            ))}
          </optgroup>
        </select>
      </div>
      <ExportButton path="/reports/irp/export" params={params} name="IRP.xlsx" label="Export IRP" disabled={!params} />
    </Section>
  )
}

function PifReport({ options }: { options?: TorOptions }) {
  const [user, setUser] = useState('')
  const [format, setFormat] = useState<Format>('xlsx')
  return (
    <Section title="Property Inventory Form" code="PIF" hint="Assets held by an accountable officer (or all) with condition, location and a certification block.">
      <UserField id="tor-pif-user" label="Accountable officer" value={user} onChange={setUser} options={options} allLabel="All accountable officers" />
      <FormatField id="tor-pif-fmt" value={format} onChange={setFormat} />
      <ExportButton path="/reports/pif/export" params={{ user_id: user || undefined, format }} name={`PIF.${format}`} label="Download" />
    </Section>
  )
}

function LapsingSchedule() {
  const [asAt, setAsAt] = useState(today())
  const [format, setFormat] = useState<Format>('xlsx')
  return (
    <Section title="Depreciation Lapsing Schedule" hint="All depreciable PPE as at a date by asset category: cost, residual, monthly/annual depreciation, accumulated depreciation and book value, with subtotals.">
      <DateField id="tor-lapse-date" label="As at" value={asAt} onChange={setAsAt} />
      <FormatField id="tor-lapse-fmt" value={format} onChange={setFormat} />
      <ExportButton path="/reports/lapsing-schedule/export" params={{ as_at_date: asAt || undefined, format }} name={`LapsingSchedule.${format}`} label="Download" />
    </Section>
  )
}

function CnasReport({ options }: { options?: TorOptions }) {
  const [id, setId] = useState('')
  const list = options?.cnas_lines ?? []
  return (
    <Section title="Certificate of Non-Availability of Stock" code="CNAS" hint="PS-DBM certificate for Agency Procurement Request lines marked not available.">
      <div className="field" style={{ minWidth: 340 }}>
        <label htmlFor="tor-cnas">Line not available at PS-DBM</label>
        <select id="tor-cnas" value={id} onChange={(e) => setId(e.target.value)} disabled={list.length === 0}>
          <option value="">{list.length === 0 ? 'No lines marked not available' : 'Choose a line'}</option>
          {list.map((l) => (
            <option key={l.id} value={l.id}>
              {l.cnas_number ?? '—'} — {l.apr_number}: {l.description} ({formatDate(l.cnas_date)})
            </option>
          ))}
        </select>
      </div>
      <ExportButton path={`/reports/cnas/${id}/export`} name="CNAS.xlsx" label="Export CNAS" disabled={!id} />
    </Section>
  )
}

function CosUndertaking({ options }: { options?: TorOptions }) {
  const [user, setUser] = useState('')
  const [position, setPosition] = useState('')
  const [end, setEnd] = useState('')
  return (
    <Section title="Letter of Undertaking — COS Personnel" hint="Contract of Service worker's undertaking to be accountable for, and return, the property and ICS items issued to them.">
      <UserField id="tor-cos-user" label="COS worker" value={user} onChange={setUser} options={options} />
      <div className="field">
        <label htmlFor="tor-cos-pos">Position / designation</label>
        <input id="tor-cos-pos" value={position} maxLength={150} onChange={(e) => setPosition(e.target.value)} placeholder="e.g. Administrative Aide (COS)" />
      </div>
      <DateField id="tor-cos-end" label="Contract ends (optional)" value={end} onChange={setEnd} />
      <ExportButton path={`/reports/cos-undertaking/${user}/export`} params={{ position: position || undefined, contract_end: end || undefined }}
        name="COS_Undertaking.xlsx" label="Export letter" disabled={!user} />
    </Section>
  )
}

/** The Terms-of-Reference forms, registers and master lists not covered by the other report tabs. */
export function TorReports() {
  const opts = useQuery({ queryKey: ['tor-report-options'], queryFn: torOptions })
  const o = opts.data
  return (
    <div className="tor-reports">
      <h4 className="muted small" style={{ textTransform: 'uppercase', margin: '0 0 0.5rem' }}>Ledger cards</h4>
      <PpeLedgerCard />
      <SuppliesLedgerCard />
      <SemiExpendableCards options={o} />

      <h4 className="muted small" style={{ textTransform: 'uppercase', margin: '1rem 0 0.5rem' }}>Issuance & transfer</h4>
      <SemiExpendableIssued options={o} />
      <ItrReport options={o} />
      <IrpReport options={o} />
      <CosUndertaking options={o} />
      <CnasReport options={o} />

      <h4 className="muted small" style={{ textTransform: 'uppercase', margin: '1rem 0 0.5rem' }}>Property & depreciation</h4>
      <PifReport options={o} />
      <LapsingSchedule />

      <h4 className="muted small" style={{ textTransform: 'uppercase', margin: '1rem 0 0.5rem' }}>Registers</h4>
      <RangeReport id="tor-iar" title="IAR Monitoring" hint="Inspection and Acceptance Reports by inspection date: PO, supplier, dates, status and accepted amount."
        path="/reports/registers/iar/export" name="IAR_Monitoring" supplier options={o} />
      <RangeReport id="tor-ris" title="RIS Monitoring" hint="Requisitions with RIS numbers: requested, approved and issued dates, office, status, items and amount."
        path="/reports/registers/ris/export" name="RIS_Monitoring" office options={o} />
      <RangeReport id="tor-po" title="Summary of Purchase Orders" hint="Purchase orders by PO date: supplier, mode of procurement, amount, status and deliveries."
        path="/reports/registers/purchase-orders/export" name="PO_Summary" supplier options={o} />

      <h4 className="muted small" style={{ textTransform: 'uppercase', margin: '1rem 0 0.5rem' }}>Monthly, annual or any dates</h4>
      <RangeReport id="tor-par" title="Property Acknowledgment Receipts" code="PAR" hint="PARs issued in the period, with asset, accountable officer and amount."
        path="/reports/registers/par/export" name="PAR_Register" options={o} />
      <RangeReport id="tor-ics" title="Inventory Custodian Slips" code="ICS" hint="ICSs issued in the period, with item, custodian and amount."
        path="/reports/registers/ics/export" name="ICS_Register" options={o} />
      <RangeReport id="tor-pc" title="Property Card Transactions" code="PC" hint="Acquisitions, transfers, returns and disposals of every asset in the period."
        path="/reports/registers/property-transactions/export" name="Property_Transactions" options={o} />
      <StockMovements options={o} />
      <PhysicalCountRegister options={o} />
      <RangeReport id="tor-iirup" title="Unserviceable Property" code="IIRUP" hint="Assets disposed of in the period after inspection."
        path="/reports/registers/iirup/export" name="IIRUP_Register" options={o} />
      <RangeReport id="tor-wmr" title="Waste Materials" code="WMR" hint="Everything disposed of in the period, assets and supplies."
        path="/reports/registers/wmr/export" name="WMR_Register" options={o} />
      <RangeReport id="tor-rlsddp" title="Lost or Damaged Property" code="RLSDDP" hint="Assets reported lost or damaged in the period, from gate pass returns and asset records."
        path="/reports/registers/rlsddp/export" name="RLSDDP_Register" options={o} />
      <RangeReport id="tor-rta" title="Transfers of Accountability" code="RTA" hint="Every change of accountable officer in the period, with its reason and approver."
        path="/reports/registers/transfers/export" name="RTA_Register" options={o} />
      <RangeReport id="tor-gate" title="Gate Passes" hint="Gate passes released in the period, with what came back."
        path="/reports/registers/gate-passes/export" name="Gate_Passes" options={o} />
      <RangeReport id="tor-reorder" title="Reordering Point" hint="Items at or below their reorder point today, with what was issued in the period and what to order."
        path="/reports/registers/reorder-point/export" name="Reordering_Point" options={o} />

      <h4 className="muted small" style={{ textTransform: 'uppercase', margin: '1rem 0 0.5rem' }}>Master lists</h4>
      <RangeReport id="tor-ml-assets" title="Asset Master List" hint="Every asset with class, cost, book value, holder and status; dates filter on acquisition date."
        path="/reports/master-lists/assets/export" name="Asset_Master_List" optional options={o} />
      <RangeReport id="tor-ml-items" title="Supplies / Item Master List" hint="The item catalogue with re-order point, stock on hand, unit cost and value; dates filter on creation date."
        path="/reports/master-lists/items/export" name="Item_Master_List" optional options={o} />
      <RangeReport id="tor-ml-avail" title="Available & Commonly-used Supplies" hint="Consumables with stock on hand, most issued first; dates set the usage period."
        path="/reports/master-lists/available-supplies/export" name="Available_Supplies" optional options={o} />
      <RangeReport id="tor-ml-suppliers" title="Supplier Master List" hint="Suppliers with contact, TIN, PhilGEPS and eligibility; dates filter on registration date."
        path="/reports/master-lists/suppliers/export" name="Supplier_Master_List" optional options={o} />
      <RangeReport id="tor-ml-users" title="User Master List" hint="User accounts with role, office and status (no credentials); dates filter on creation date."
        path="/reports/master-lists/users/export" name="User_Master_List" optional options={o} />
    </div>
  )
}

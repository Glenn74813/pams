import { useState } from 'react'
import { IarReport, RequestLogsReport } from './ActivityReports'
import { AgingReport, InventoryReport, ReconciliationReport, RisReport, RpciReport, RsmiReport, StockCardReport } from './InventoryReports'
import { IirupReport, PropertyCardReport, PtrReport, RlsddpReport, RpcppeReport, RpcspReport, WmrReport } from './PropertyReports'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import './reports.css'

interface Tab {
  id: string
  name: string
  code?: string
  hint: string
  view: () => React.ReactNode
}

/** Reports grouped by COA subject area, as in the old system; each COA form exports in its prescribed Excel format. */
const GROUPS: { label: string; tabs: Tab[] }[] = [
  {
    label: 'Inventory & Supplies',
    tabs: [
      { id: 'inventory', name: 'Inventory Report', hint: 'Items, stock on hand and value by category.', view: () => <InventoryReport /> },
      { id: 'stock-card', name: 'Stock Card', code: 'App. 58', hint: 'Receipts, issues and running balance of one item.', view: () => <StockCardReport /> },
      { id: 'rsmi', name: 'Supplies & Materials Issued', code: 'RSMI', hint: 'Report of Supplies and Materials Issued (Appendix 64) for a month.', view: () => <RsmiReport /> },
      { id: 'ris', name: 'Requisition & Issue Slip', code: 'RIS', hint: 'RIS (Appendix 63), ICS (Appendix 59) and PAR (Appendix 71) per request.', view: () => <RisReport /> },
      { id: 'rpci', name: 'Physical Count — Inventories', code: 'RPCI', hint: 'Report on the Physical Count of Inventories (Appendix 66).', view: () => <RpciReport /> },
      { id: 'inventory-aging', name: 'Inventory Aging', hint: 'Days since each item was last issued; slow-moving stock and value at risk.', view: () => <AgingReport /> },
      { id: 'reconciliation', name: 'Stock Reconciliation', hint: 'Card balance against quantity on hand.', view: () => <ReconciliationReport /> },
    ],
  },
  {
    label: 'Property, Plant & Equipment',
    tabs: [
      { id: 'property-card', name: 'Property Card', code: 'App. 69', hint: 'Acquisition, transfers and disposal of one asset.', view: () => <PropertyCardReport /> },
      { id: 'ptr', name: 'Property Transfer Report', code: 'PTR', hint: 'Property Transfer Report (Appendix 76) per transfer.', view: () => <PtrReport /> },
      { id: 'rpcppe', name: 'Physical Count — PPE', code: 'RPCPPE', hint: 'Report on the Physical Count of PPE (Appendix 73).', view: () => <RpcppeReport /> },
      { id: 'rpcsp', name: 'Physical Count — Semi-Expendable', code: 'RPCSP', hint: 'Report on the Physical Count of Semi-Expendable Property (Appendix 68).', view: () => <RpcspReport /> },
      { id: 'iirup', name: 'Unserviceable Property', code: 'IIRUP', hint: 'Inventory and Inspection Report of Unserviceable Property (Appendix 74).', view: () => <IirupReport /> },
      { id: 'rlsddp', name: 'Lost / Damaged Property', code: 'RLSDDP', hint: 'Report of Lost, Stolen, Damaged or Destroyed Property (Appendix 75).', view: () => <RlsddpReport /> },
      { id: 'wmr', name: 'Waste Materials Report', code: 'WMR', hint: 'Waste Materials Report (Appendix 65) from completed disposals.', view: () => <WmrReport /> },
    ],
  },
  {
    label: 'Receiving & Activity',
    tabs: [
      { id: 'iar', name: 'Inspection & Acceptance', code: 'IAR', hint: 'Inspection and Acceptance Report (Appendix 62) per delivery.', view: () => <IarReport /> },
      { id: 'request-logs', name: 'Request Logs', hint: 'Every item requested, by department and period.', view: () => <RequestLogsReport /> },
    ],
  },
]

const ALL = GROUPS.flatMap((g) => g.tabs)

export function ReportsPage() {
  const [active, setActive] = useState('inventory')
  const tab = ALL.find((t) => t.id === active) ?? ALL[0]
  return (
    <div className="page page-wide">
      <h1 className="page-title">Reports</h1>
      <select className="report-nav-select" value={active} onChange={(e) => setActive(e.target.value)} aria-label="Report">
        {GROUPS.map((g) => (
          <optgroup key={g.label} label={g.label}>
            {g.tabs.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
                {t.code ? ` (${t.code})` : ''}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
      <div className="reports-layout">
        <nav className="report-nav no-print" aria-label="Reports">
          {GROUPS.map((g) => (
            <div key={g.label}>
              <h4>{g.label}</h4>
              {g.tabs.map((t) => (
                <button key={t.id} className={t.id === active ? 'active' : ''} onClick={() => setActive(t.id)} aria-current={t.id === active ? 'page' : undefined}>
                  <span>{t.name}</span>
                  {t.code && <span className="code">{t.code}</span>}
                </button>
              ))}
            </div>
          ))}
        </nav>
        <section className="report-panel">
          <h2>
            {tab.name} {tab.code && <span className="badge badge-muted">{tab.code}</span>}
          </h2>
          <p className="muted small">{tab.hint}</p>
          <div key={tab.id}>{tab.view()}</div>
        </section>
      </div>
    </div>
  )
}

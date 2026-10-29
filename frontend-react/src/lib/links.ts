/** The page that shows a record, by its Laravel model name (as stored in notifications and the audit trail). */
const PAGES: Record<string, string> = {
  PurchaseRequest: '/purchase-requests',
  Request: '/requests',
  Item: '/items',
  Stock: '/warehouse',
  Contract: '/contracts',
  ContractSecurity: '/contracts',
  Bidding: '/bidding',
  Rfq: '/rfq',
  PurchaseOrder: '/procurement',
  InspectionAcceptanceReport: '/receiving',
  StockAdjustment: '/stock-adjustments',
  Property: '/properties',
  DisposalRequest: '/disposal',
  GatePass: '/gate-pass',
  Supplier: '/suppliers',
  Ppmp: '/procurement-planning',
  App: '/procurement-planning',
  PhysicalInventoryCount: '/physical',
  Document: '/documents',
  ApcpiAssessment: '/apcpi',
}

export function pageFor(relatedType: string | null | undefined): string | null {
  const model = relatedType?.split('\\').pop()
  return (model && PAGES[model]) || null
}

export const WATCH_KINDS: Record<string, string> = {
  bidding_stage: 'Bidding stage',
  rfq_deadline: 'Quotation deadline',
  po_delivery: 'PO delivery',
  contract_delivery: 'Delivery delay',
  contract_expiry: 'Contract expiry',
  security_expiry: 'Security validity',
  supplier_document: 'Supplier document',
  app_schedule: 'APP schedule',
}

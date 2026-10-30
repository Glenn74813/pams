export interface MenuItem {
  path: string
  icon: string
  label: string
  /** Key in the user's accessible_modules list. */
  module: string
  /** False until the page has been moved from the Vue app to React. */
  ported: boolean
}

export interface MenuGroup {
  key: string
  label: string
  icon: string
  children: MenuItem[]
}

export const dashboardItem: MenuItem = { path: '/dashboard', icon: '📊', label: 'Dashboard', module: 'dashboard', ported: true }

/**
 * The sidebar follows the procurement and supply cycle: plan and request (RA 12009: PPMP, APP, PR),
 * procure (PS-DBM for common-use supplies first, then competitive bidding as the rule and the
 * alternative modes as exceptions), award (PO, contract), then COA's supply and property cycle:
 * receive and inspect (IAR), stock and issue supplies (RIS, ICS), keep property (PAR, PTR, gate
 * pass), count, and dispose; reports and administration last.
 */
export const menuGroups: MenuGroup[] = [
  {
    key: 'planning', label: 'Planning & Requests', icon: '🗓️',
    children: [
      { path: '/procurement-planning', icon: '🗓️', label: 'Procurement Planning', module: 'procurement-planning', ported: true },
      { path: '/purchase-requests', icon: '📝', label: 'Purchase Requests', module: 'purchase-requests', ported: true },
    ],
  },
  {
    key: 'procurement', label: 'Procurement', icon: '🛒',
    children: [
      { path: '/ps-dbm', icon: '🏬', label: 'PS-DBM (Common-Use)', module: 'ps-dbm', ported: true },
      { path: '/bidding', icon: '🏛️', label: 'Competitive Bidding', module: 'bidding', ported: true },
      { path: '/rfq', icon: '🧮', label: 'RFQ / Alternative Modes', module: 'rfq', ported: true },
      { path: '/bac', icon: '⚖️', label: 'BAC Management', module: 'bac', ported: true },
      { path: '/suppliers', icon: '🏢', label: 'Vendors / Suppliers', module: 'suppliers', ported: true },
    ],
  },
  {
    key: 'award', label: 'Award & Payment', icon: '🧾',
    children: [
      { path: '/procurement', icon: '🧾', label: 'Purchase Orders', module: 'procurement', ported: true },
      { path: '/contracts', icon: '📜', label: 'Contracts', module: 'contracts', ported: true },
      { path: '/petty-cash', icon: '💵', label: 'Petty Cash', module: 'payments', ported: true },
    ],
  },
  {
    key: 'inventory', label: 'Inventory', icon: '📦',
    children: [
      { path: '/receiving', icon: '📥', label: 'Receiving (IAR)', module: 'receiving', ported: true },
      { path: '/items', icon: '📦', label: 'Items', module: 'items', ported: true },
      { path: '/warehouse', icon: '🏬', label: 'Warehouse / Stock', module: 'warehouse', ported: true },
      { path: '/requests', icon: '📋', label: 'Issuance (RIS)', module: 'requests', ported: true },
      { path: '/semi-expendable', icon: '🧾', label: 'Semi-Expendable (ICS)', module: 'semi-expendable', ported: true },
      { path: '/stock-adjustments', icon: '⚖️', label: 'Stock Adjustments', module: 'stock-adjustments', ported: true },
      { path: '/monitoring', icon: '📊', label: 'Monitoring', module: 'monitoring', ported: true },
    ],
  },
  {
    key: 'assets', label: 'Assets / Property', icon: '🏢',
    children: [
      { path: '/properties', icon: '🏢', label: 'Assets (PAR / PTR)', module: 'properties', ported: true },
      { path: '/my-assets', icon: '🙋', label: 'My Assets', module: 'my-assets', ported: true },
      { path: '/gate-pass', icon: '🚪', label: 'Gate Pass', module: 'gate-pass', ported: true },
    ],
  },
  {
    key: 'count', label: 'Count & Disposal', icon: '🔍',
    children: [
      { path: '/physical', icon: '🔍', label: 'Physical Count', module: 'physical', ported: true },
      { path: '/disposal', icon: '♻️', label: 'Disposal', module: 'disposal', ported: true },
    ],
  },
  {
    key: 'reports', label: 'Reports & Compliance', icon: '📈',
    children: [
      { path: '/reports', icon: '📈', label: 'Reports (COA forms)', module: 'reports', ported: true },
      { path: '/procurement-reports', icon: '📑', label: 'Procurement Reports', module: 'reports', ported: true },
      { path: '/apcpi', icon: '✅', label: 'APCPI Self-Assessment', module: 'apcpi', ported: true },
      { path: '/documents', icon: '🗂️', label: 'Documents', module: 'documents', ported: true },
      { path: '/audit', icon: '🛡️', label: 'Audit Trail', module: 'audit', ported: true },
      { path: '/log-trails', icon: '📋', label: 'Log Trails', module: 'log-trails', ported: true },
    ],
  },
  {
    key: 'administration', label: 'Administration', icon: '⚙️',
    children: [
      { path: '/users', icon: '👥', label: 'Users & Roles', module: 'users', ported: true },
      { path: '/settings', icon: '⚙️', label: 'Settings', module: 'settings', ported: true },
    ],
  },
]

export const allMenuItems: MenuItem[] = [dashboardItem, ...menuGroups.flatMap((g) => g.children)]

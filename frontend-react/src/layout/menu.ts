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

export const menuGroups: MenuGroup[] = [
  {
    key: 'procurement', label: 'Procurement', icon: '🛒',
    children: [
      { path: '/procurement-planning', icon: '🗓️', label: 'Procurement Planning', module: 'procurement-planning', ported: true },
      { path: '/purchase-requests', icon: '📝', label: 'Purchase Requests', module: 'purchase-requests', ported: true },
      { path: '/rfq', icon: '🧮', label: 'RFQ / Alternative', module: 'rfq', ported: true },
      { path: '/bidding', icon: '🏛️', label: 'Competitive Bidding', module: 'bidding', ported: true },
      { path: '/bac', icon: '⚖️', label: 'BAC Management', module: 'bac', ported: true },
      { path: '/procurement', icon: '🧾', label: 'Purchase Orders', module: 'procurement', ported: true },
      { path: '/contracts', icon: '📜', label: 'Contracts', module: 'contracts', ported: false },
      { path: '/receiving', icon: '📥', label: 'Receiving', module: 'receiving', ported: true },
    ],
  },
  {
    key: 'vendor', label: 'Vendor Management', icon: '🏢',
    children: [{ path: '/suppliers', icon: '🏢', label: 'Vendors / Suppliers', module: 'suppliers', ported: false }],
  },
  {
    key: 'inventory', label: 'Inventory', icon: '📦',
    children: [
      { path: '/items', icon: '📦', label: 'Items', module: 'items', ported: true },
      { path: '/warehouse', icon: '🏬', label: 'Warehouse', module: 'warehouse', ported: true },
      { path: '/requests', icon: '📋', label: 'Issuance', module: 'requests', ported: true },
      { path: '/physical', icon: '🔍', label: 'Physical Count', module: 'physical', ported: false },
      { path: '/stock-adjustments', icon: '⚖️', label: 'Stock Adjustments', module: 'stock-adjustments', ported: true },
      { path: '/semi-expendable', icon: '🧾', label: 'Semi-Expendable', module: 'semi-expendable', ported: true },
      { path: '/monitoring', icon: '📊', label: 'Monitoring', module: 'monitoring', ported: false },
      { path: '/gate-pass', icon: '🚪', label: 'Gate Pass', module: 'gate-pass', ported: true },
      { path: '/disposal', icon: '♻️', label: 'Disposal', module: 'disposal', ported: true },
    ],
  },
  {
    key: 'assets', label: 'Assets / Property', icon: '🏢',
    children: [
      { path: '/properties', icon: '🏢', label: 'Assets', module: 'properties', ported: true },
      { path: '/my-assets', icon: '🙋', label: 'My Assets', module: 'my-assets', ported: true },
    ],
  },
  {
    key: 'reports', label: 'Reports & Compliance', icon: '📈',
    children: [
      { path: '/reports', icon: '📈', label: 'Reports', module: 'reports', ported: false },
      { path: '/procurement-reports', icon: '📑', label: 'Procurement Reports', module: 'reports', ported: false },
      { path: '/apcpi', icon: '✅', label: 'APCPI Self-Assessment', module: 'reports', ported: false },
      { path: '/documents', icon: '🗂️', label: 'Documents', module: 'documents', ported: false },
      { path: '/audit', icon: '🛡️', label: 'Audit Trail', module: 'audit', ported: false },
      { path: '/log-trails', icon: '📋', label: 'Log Trails', module: 'log-trails', ported: false },
    ],
  },
  {
    key: 'administration', label: 'Administration', icon: '⚙️',
    children: [
      { path: '/users', icon: '👥', label: 'Users & Roles', module: 'users', ported: true },
      { path: '/settings', icon: '⚙️', label: 'Settings', module: 'settings', ported: false },
    ],
  },
]

export const allMenuItems: MenuItem[] = [dashboardItem, ...menuGroups.flatMap((g) => g.children)]

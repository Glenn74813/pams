import {
  Activity, Armchair, Cable, BadgeCheck, CalendarRange, ChartColumn, ClipboardCheck, DoorOpen, FileChartColumn, FilePlus2, FileQuestion, FileSignature,
  FolderOpen, Gavel, PenLine, UserRoundCheck, Wrench, LayoutDashboard, Laptop, Package, PackageCheck, ScanLine, ScrollText, Settings, ShieldCheck, ShoppingCart,
  SlidersHorizontal, Store, Trash2, Truck, UserCheck, Users, UsersRound, Wallet, Warehouse, type LucideIcon,
} from 'lucide-react'

export interface MenuItem {
  path: string
  icon: LucideIcon
  label: string
  /** Key in the user's accessible_modules list. */
  module: string
  /** False until the page has been moved from the Vue app to React. */
  ported: boolean
}

/** A titled section of the sidebar. */
export interface MenuGroup {
  key: string
  label: string
  children: MenuItem[]
}

export const dashboardItem: MenuItem = { path: '/dashboard', icon: LayoutDashboard, label: 'Dashboard', module: 'dashboard', ported: true }

/**
 * The sidebar follows the procurement and supply cycle: plan and request (RA 12009: PPMP, APP, PR),
 * procure (PS-DBM for common-use supplies first, then competitive bidding as the rule and the
 * alternative modes as exceptions), award (PO, contract), then COA's supply and property cycle:
 * receive and inspect (IAR), stock and issue supplies (RIS, ICS), keep property (PAR, PTR, gate
 * pass), count, and dispose; reports and administration last.
 */
export const menuGroups: MenuGroup[] = [
  {
    key: 'planning', label: 'Planning & Requests',
    children: [
      { path: '/procurement-planning', icon: CalendarRange, label: 'Procurement Planning', module: 'procurement-planning', ported: true },
      { path: '/purchase-requests', icon: FilePlus2, label: 'Purchase Requests', module: 'purchase-requests', ported: true },
    ],
  },
  {
    key: 'procurement', label: 'Procurement',
    children: [
      { path: '/ps-dbm', icon: Store, label: 'PS-DBM (Common-Use)', module: 'ps-dbm', ported: true },
      { path: '/bidding', icon: Gavel, label: 'Competitive Bidding', module: 'bidding', ported: true },
      { path: '/unsolicited-offers', icon: Gavel, label: 'Unsolicited Offers', module: 'bidding', ported: true },
      { path: '/rfq', icon: FileQuestion, label: 'RFQ / Alternative Modes', module: 'rfq', ported: true },
      { path: '/bac', icon: Users, label: 'BAC Management', module: 'bac', ported: true },
      { path: '/suppliers', icon: Truck, label: 'Vendors / Suppliers', module: 'suppliers', ported: true },
    ],
  },
  {
    key: 'award', label: 'Award & Payment',
    children: [
      { path: '/procurement', icon: ShoppingCart, label: 'Purchase Orders', module: 'procurement', ported: true },
      { path: '/contracts', icon: FileSignature, label: 'Contracts', module: 'contracts', ported: true },
      { path: '/petty-cash', icon: Wallet, label: 'Petty Cash', module: 'payments', ported: true },
      { path: '/external-payments', icon: Cable, label: 'Payments from Other Systems', module: 'payments', ported: true },
    ],
  },
  {
    key: 'inventory', label: 'Inventory',
    children: [
      { path: '/receiving', icon: PackageCheck, label: 'Receiving (IAR)', module: 'receiving', ported: true },
      { path: '/items', icon: Package, label: 'Items', module: 'items', ported: true },
      { path: '/warehouse', icon: Warehouse, label: 'Warehouse / Stock', module: 'warehouse', ported: true },
      { path: '/requests', icon: ClipboardCheck, label: 'Issuance (RIS)', module: 'requests', ported: true },
      { path: '/semi-expendable', icon: Armchair, label: 'Semi-Expendable (ICS)', module: 'semi-expendable', ported: true },
      { path: '/stock-adjustments', icon: SlidersHorizontal, label: 'Stock Adjustments', module: 'stock-adjustments', ported: true },
      { path: '/monitoring', icon: Activity, label: 'Monitoring', module: 'monitoring', ported: true },
    ],
  },
  {
    key: 'assets', label: 'Assets / Property',
    children: [
      { path: '/properties', icon: Laptop, label: 'Assets (PAR / PTR)', module: 'properties', ported: true },
      { path: '/my-assets', icon: UserCheck, label: 'My Assets', module: 'my-assets', ported: true },
      { path: '/gate-pass', icon: DoorOpen, label: 'Gate Pass', module: 'gate-pass', ported: true },
      { path: '/maintenance', icon: Wrench, label: 'Maintenance', module: 'maintenance', ported: true },
      { path: '/clearance', icon: UserRoundCheck, label: 'Turnover & Clearance', module: 'clearance', ported: true },
    ],
  },
  {
    key: 'count', label: 'Count & Disposal',
    children: [
      { path: '/physical', icon: ScanLine, label: 'Physical Count', module: 'physical', ported: true },
      { path: '/disposal', icon: Trash2, label: 'Disposal', module: 'disposal', ported: true },
    ],
  },
  {
    key: 'reports', label: 'Reports & Compliance',
    children: [
      { path: '/reports', icon: ChartColumn, label: 'Reports (COA forms)', module: 'reports', ported: true },
      { path: '/procurement-reports', icon: FileChartColumn, label: 'Procurement Reports', module: 'reports', ported: true },
      { path: '/apcpi', icon: BadgeCheck, label: 'APCPI Self-Assessment', module: 'apcpi', ported: true },
      { path: '/documents', icon: FolderOpen, label: 'Documents', module: 'documents', ported: true },
      { path: '/signing', icon: PenLine, label: 'Digital Signatures', module: 'signing', ported: true },
      { path: '/audit', icon: ShieldCheck, label: 'Audit Trail', module: 'audit', ported: true },
      { path: '/log-trails', icon: ScrollText, label: 'Log Trails', module: 'log-trails', ported: true },
    ],
  },
  {
    key: 'administration', label: 'Administration',
    children: [
      { path: '/users', icon: UsersRound, label: 'Users & Roles', module: 'users', ported: true },
      { path: '/settings', icon: Settings, label: 'Settings', module: 'settings', ported: true },
    ],
  },
]

export const allMenuItems: MenuItem[] = [dashboardItem, ...menuGroups.flatMap((g) => g.children)]

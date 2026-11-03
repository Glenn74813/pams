import { Link, useLocation } from 'react-router'

/** Help article for each page, by its path. */
const ARTICLE: Record<string, string> = {
  '/dashboard': 'dashboard',
  '/procurement-planning': 'procurement-planning',
  '/purchase-requests': 'purchase-requests',
  '/rfq': 'rfq',
  '/bidding': 'bidding',
  '/unsolicited-offers': 'unsolicited-offers',
  '/bac': 'bac',
  '/procurement': 'purchase-orders',
  '/contracts': 'contracts',
  '/receiving': 'receiving',
  '/suppliers': 'suppliers',
  '/items': 'items',
  '/warehouse': 'warehouse',
  '/requests': 'issuance',
  '/physical': 'physical-count',
  '/stock-adjustments': 'stock-adjustments',
  '/semi-expendable': 'semi-expendable',
  '/monitoring': 'monitoring',
  '/gate-pass': 'gate-pass',
  '/disposal': 'disposal',
  '/properties': 'assets',
  '/my-assets': 'my-assets',
  '/reports': 'reports',
  '/procurement-reports': 'procurement-reports',
  '/apcpi': 'apcpi',
  '/documents': 'documents',
  '/audit': 'audit-trail',
  '/log-trails': 'log-trails',
  '/users': 'users-roles',
  '/settings': 'settings',
}

/** The "?" in the top bar: opens the user-manual article for the page being viewed. */
export function PageHelp() {
  const { pathname } = useLocation()
  const slug = ARTICLE['/' + pathname.split('/')[1]]
  return (
    <Link className="notif-bell" to={slug ? `/help/${slug}` : '/help'} title={slug ? 'Help for this page' : 'Help & user manual'} aria-label="Help">
      ?
    </Link>
  )
}

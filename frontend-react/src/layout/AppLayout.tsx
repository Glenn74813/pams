import { useEffect, useMemo, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { ChevronsUpDown, CircleHelp, LogOut, Settings, type LucideIcon } from 'lucide-react'
import { auditApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { dashboardItem, menuGroups } from './menu'
import { NotificationBell } from './NotificationBell'
import { PageHelp } from './PageHelp'
import { ScanButton } from './ScanButton'
import './AppLayout.css'

/** A path is active on itself and its sub-pages, but /procurement is not active on /procurement-planning. */
function isUnder(current: string, path: string) {
  return current === path || current.startsWith(path + '/')
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('')
}

function NavIcon({ icon: Icon }: { icon: LucideIcon }) {
  return <Icon className="icon" size={18} strokeWidth={1.75} aria-hidden />
}

export function AppLayout() {
  const { user, logout, canOpen } = useAuth()
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [shownPath, setShownPath] = useState(location.pathname)

  // Printing is recorded in the audit trail (TOR 6.4), whether from a Print button or the browser menu.
  useEffect(() => {
    const onPrint = () => {
      const slip = document.querySelector('.print-area .slip-title')?.textContent?.trim()
      const modal = document.querySelector('.modal h3')?.textContent?.trim()
      const what = [slip, modal].filter(Boolean).join(' — ') || document.title
      void auditApi.event('printed', `Printed ${what} (${window.location.pathname})`)
    }
    window.addEventListener('beforeprint', onPrint)
    return () => window.removeEventListener('beforeprint', onPrint)
  }, [])

  // Navigating closes the mobile sidebar and the user menu.
  if (shownPath !== location.pathname) {
    setShownPath(location.pathname)
    setSidebarOpen(false)
    setUserMenuOpen(false)
  }

  const groups = useMemo(
    () =>
      menuGroups
        .map((g) => ({ ...g, children: g.children.filter((c) => canOpen(c.module)) }))
        .filter((g) => g.children.length > 0),
    [canOpen],
  )

  if (!user) return null

  return (
    <div className="app-shell">
      <header className="navbar">
        <div className="navbar-left">
          <button className="sidebar-toggle" onClick={() => setSidebarOpen((o) => !o)} aria-label="Toggle menu">
            ☰
          </button>
          <img src="/avtech-logo.png" alt="AVTECH Solutions" className="navbar-logo" />
          <div className="navbar-welcome">Hello! Welcome {user.name}!</div>
        </div>
        <div className="navbar-right">
          <PageHelp />
          <ScanButton />
          <NotificationBell />
        </div>
      </header>

      <div className="app-body">
        <aside className={'sidebar' + (sidebarOpen ? ' sidebar-open' : '')}>
          <nav className="sidebar-nav">
            {canOpen(dashboardItem.module) && (
              <NavLink to={dashboardItem.path} className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
                <NavIcon icon={dashboardItem.icon} />
                <span className="label">{dashboardItem.label}</span>
              </NavLink>
            )}

            {groups.map((group) => (
              <section key={group.key} className="nav-group" aria-labelledby={`nav-${group.key}`}>
                <h2 id={`nav-${group.key}`} className="nav-group-label">
                  {group.label}
                </h2>
                {group.children.map((item) => (
                  <NavLink key={item.path} to={item.path} className={() => 'nav-item' + (isUnder(location.pathname, item.path) ? ' active' : '')}>
                    <NavIcon icon={item.icon} />
                    <span className="label">{item.label}</span>
                  </NavLink>
                ))}
              </section>
            ))}
          </nav>

          <div className="sidebar-user">
            {userMenuOpen && <div className="user-menu-overlay" onClick={() => setUserMenuOpen(false)} />}
            {userMenuOpen && (
              <div className="user-menu-panel" role="menu">
                <div className="user-menu-role">{user.role.name}</div>
                <NavLink className="user-menu-item" role="menuitem" to="/settings">
                  <Settings className="user-menu-icon" size={16} strokeWidth={1.75} aria-hidden /> My account
                </NavLink>
                <NavLink className="user-menu-item" role="menuitem" to="/help">
                  <CircleHelp className="user-menu-icon" size={16} strokeWidth={1.75} aria-hidden /> Help &amp; user manual
                </NavLink>
                <button className="user-menu-item logout" role="menuitem" onClick={() => void logout()}>
                  <LogOut className="user-menu-icon" size={16} strokeWidth={1.75} aria-hidden /> Logout
                </button>
              </div>
            )}
            <button
              className={'user-trigger' + (userMenuOpen ? ' open' : '')}
              onClick={() => setUserMenuOpen((o) => !o)}
              aria-haspopup="menu"
              aria-expanded={userMenuOpen}
            >
              <span className="user-avatar">{initials(user.name)}</span>
              <span className="user-trigger-name">{user.name}</span>
              <ChevronsUpDown className="user-trigger-chevron" size={16} strokeWidth={1.75} aria-hidden />
            </button>
          </div>
        </aside>
        {sidebarOpen && <div className="sidebar-backdrop" onClick={() => setSidebarOpen(false)} />}

        <main className="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

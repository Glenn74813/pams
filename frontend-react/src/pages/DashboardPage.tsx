import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { allMenuItems } from '../layout/menu'

export function DashboardPage() {
  const { user, canOpen } = useAuth()
  if (!user) return null
  const mine = allMenuItems.filter((i) => canOpen(i.module) && i.path !== '/dashboard')
  const ready = mine.filter((i) => i.ported)

  return (
    <div className="page">
      <h1 className="page-title">Dashboard</h1>

      <div className="dash-grid">
        <section className="dash-card">
          <h2>Your account</h2>
          <dl>
            <dt>Name</dt>
            <dd>{user.name}</dd>
            <dt>Email</dt>
            <dd>{user.email}</dd>
            <dt>Role</dt>
            <dd>{user.role.name}</dd>
            <dt>Department</dt>
            <dd>{user.department.name}</dd>
            <dt>Two-factor sign-in</dt>
            <dd>{user.two_factor_enabled ? 'On' : 'Off'}</dd>
          </dl>
        </section>

        <section className="dash-card">
          <h2>Available here</h2>
          {ready.length === 0 ? (
            <p className="muted">None of your modules have moved to the new system yet.</p>
          ) : (
            <ul className="dash-links">
              {ready.map((i) => (
                <li key={i.path}>
                  <Link to={i.path}>
                    {i.icon} {i.label}
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <p className="muted">
            {mine.length - ready.length} of your {mine.length} modules still run in the old system while they are
            being moved. Inventory figures appear here once the inventory module has moved.
          </p>
        </section>
      </div>
    </div>
  )
}

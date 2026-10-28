import type { MenuItem } from '../layout/menu'

const LEGACY_URL = import.meta.env.VITE_LEGACY_APP_URL ?? 'http://localhost:5180'

/** Placeholder for a module that has not been moved to the new system yet. */
export function NotPortedPage({ item }: { item: MenuItem }) {
  return (
    <div className="page">
      <h1 className="page-title">
        {item.icon} {item.label}
      </h1>
      <div className="alert alert-info">
        <p>This module has not moved to the new system yet. Until it does, use it in the current system.</p>
        <p style={{ marginTop: '0.75rem' }}>
          <a className="btn btn-primary" href={LEGACY_URL + item.path} target="_blank" rel="noreferrer">
            Open {item.label} in the current system ↗
          </a>
        </p>
      </div>
    </div>
  )
}

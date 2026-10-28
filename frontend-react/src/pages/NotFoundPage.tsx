import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="page">
      <h1 className="page-title">Page not found</h1>
      <p style={{ marginBottom: '1rem' }}>There is no page at this address.</p>
      <Link className="btn btn-primary" to="/dashboard">
        Go to the dashboard
      </Link>
    </div>
  )
}

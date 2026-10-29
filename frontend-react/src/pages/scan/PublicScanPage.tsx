import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { qrApi } from '../../api/endpoints'
import '../../components/qr.css'

const SHOW: Record<string, string> = {
  code: 'Code / tag',
  category: 'Category',
  unit_of_measure: 'Unit',
  current_stock: 'On hand',
  serial_number: 'Serial no.',
  brand: 'Brand',
  status: 'Status',
  condition: 'Condition',
  custodian: 'Accountable officer',
  department: 'Office',
  accountability_number: 'PAR / ICS no.',
  purpose: 'Purpose',
  received_by: 'Received by',
  date_released: 'Released',
  expected_return_date: 'Due back',
  actual_return_date: 'Returned',
  tag_number: 'Asset tag',
  item: 'Item',
}

/** What a phone shows after scanning a label; works without signing in, and only for a genuine (signed) code. */
export function PublicScanPage() {
  const { type = '', id = '' } = useParams()
  const [params] = useSearchParams()
  const q = useQuery({ queryKey: ['public-scan', type, id], queryFn: () => qrApi.publicLookup(type, id, params.get('c') ?? ''), retry: false })
  return (
    <div className="page" style={{ maxWidth: 560, margin: '0 auto', padding: '1.5rem 1rem' }}>
      {q.isLoading && <div className="page-loading">Checking…</div>}
      {q.isError && (
        <div className="alert alert-error">
          This QR code is not valid. It may have been altered, or the record no longer exists.
        </div>
      )}
      {q.data && (
        <div className="dash-card scan-result">
          {q.data.agency && <div className="muted small">{q.data.agency}</div>}
          <h1 className="page-title">{q.data.title}</h1>
          <span className="badge st-ok">Verified label</span>
          <dl>
            {Object.entries(q.data)
              .filter(([k, v]) => SHOW[k] && v != null && v !== '')
              .map(([k, v]) => (
                <div key={k} style={{ display: 'contents' }}>
                  <dt>{SHOW[k]}</dt>
                  <dd>{String(v).replace(/_/g, ' ')}</dd>
                </div>
              ))}
          </dl>
          <p className="small">
            <Link to={`${q.data.path}?search=${encodeURIComponent(String(q.data.code))}`}>Sign in to see the full record →</Link>
          </p>
        </div>
      )}
    </div>
  )
}

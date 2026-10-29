import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { logsApi } from '../../api/endpoints'
import { Pager } from '../../components/Pager'
import { useDebounced } from '../../lib/hooks'
import { formatDateTime } from '../../lib/format'
import '../inventory/inventory.css'
import '../audit/audit.css'

type Tab = 'online' | 'stock' | 'logins'

export function LogTrailsPage() {
  const [tab, setTab] = useState<Tab>('online')
  const tabs: [Tab, string][] = [
    ['online', 'Online now'],
    ['stock', 'Stock movements'],
    ['logins', 'Sign-in activity'],
  ]
  return (
    <div className="page page-wide">
      <h1 className="page-title">Log Trails</h1>
      <div className="tabs tabs-scroll log-tabs" role="tablist">
        {tabs.map(([k, l]) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>
            {l}
          </button>
        ))}
      </div>
      {tab === 'online' && <OnlineNow />}
      {tab === 'stock' && <StockMovements />}
      {tab === 'logins' && <SignIns />}
    </div>
  )
}

function OnlineNow() {
  const q = useQuery({ queryKey: ['logs-active'], queryFn: logsApi.active, refetchInterval: 60_000 })
  return (
    <>
      <p className="muted small">People with activity in the last {q.data?.window_minutes ?? '…'} minutes. Refreshes every minute; signing out removes a person at once.</p>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Role</th>
              <th>Department</th>
              <th>Last activity</th>
              <th>Signed in</th>
              <th>IP address</th>
            </tr>
          </thead>
          <tbody>
            {q.data?.users.map((u) => (
              <tr key={u.id}>
                <td>
                  <span className="online-dot" aria-hidden />
                  {u.name}
                  <div className="small muted">{u.email}</div>
                </td>
                <td>{u.role}</td>
                <td>{u.department ?? '—'}</td>
                <td className="nowrap">{formatDateTime(u.last_seen_at)}</td>
                <td className="nowrap">{formatDateTime(u.signed_in_at)}</td>
                <td className="mono">{u.ip_address ?? '—'}</td>
              </tr>
            ))}
            {q.data && q.data.users.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No one is active.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}

function StockMovements() {
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [action, setAction] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const q = useDebounced(search.trim())
  const key = [q, action, from, to].join('|')
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const params = { page, search: q || undefined, action: action || undefined, start_date: from || undefined, end_date: to || undefined }
  const actions = useQuery({ queryKey: ['logs-inventory-actions'], queryFn: logsApi.inventoryActions })
  const list = useQuery({ queryKey: ['logs-inventory', params], queryFn: () => logsApi.inventory(params), placeholderData: keepPreviousData })
  return (
    <>
      <div className="toolbar audit-filters">
        <input type="search" placeholder="Item, notes or person" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search" />
        <select value={action} onChange={(e) => setAction(e.target.value)} aria-label="Movement">
          <option value="">Any movement</option>
          {actions.data?.map((a) => (
            <option key={a} value={a}>
              {a.replace(/_/g, ' ')}
            </option>
          ))}
        </select>
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="From" />
        <input type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="To" />
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>When</th>
              <th>Item</th>
              <th>Movement</th>
              <th className="num">Qty</th>
              <th className="num">Balance</th>
              <th>By</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((l) => (
              <tr key={l.id}>
                <td className="nowrap">{formatDateTime(l.created_at)}</td>
                <td>
                  {l.item ? (
                    <>
                      <span className="mono small">{l.item.code}</span> {l.item.name}
                    </>
                  ) : (
                    '—'
                  )}
                </td>
                <td>{l.action.replace(/_/g, ' ')}</td>
                <td className="num">{l.quantity ?? '—'}</td>
                <td className="num nowrap">{l.previous_quantity != null && l.new_quantity != null ? `${l.previous_quantity} → ${l.new_quantity}` : '—'}</td>
                <td>{l.user?.name ?? 'System'}</td>
                <td className="small">{l.notes}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={7} className="empty">
                  No movements match.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
    </>
  )
}

function SignIns() {
  const [page, setPage] = useState(1)
  const [email, setEmail] = useState('')
  const [outcome, setOutcome] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const q = useDebounced(email.trim())
  const key = [q, outcome, from, to].join('|')
  const [shown, setShown] = useState(key)
  if (shown !== key) {
    setShown(key)
    setPage(1)
  }
  const params = { page, email: q || undefined, successful: outcome || undefined, start_date: from || undefined, end_date: to || undefined }
  const list = useQuery({ queryKey: ['logs-logins', params], queryFn: () => logsApi.logins(params), placeholderData: keepPreviousData })
  return (
    <>
      <div className="toolbar audit-filters">
        <input type="search" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} aria-label="Email" />
        <select value={outcome} onChange={(e) => setOutcome(e.target.value)} aria-label="Outcome">
          <option value="">Any outcome</option>
          <option value="true">Successful</option>
          <option value="false">Failed</option>
        </select>
        <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="From" />
        <input type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="To" />
      </div>
      <p className="muted small">Sign-outs, including automatic ones after the idle limit, are in the Audit Trail.</p>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>When</th>
              <th>Email</th>
              <th>Outcome</th>
              <th>IP address</th>
              <th>Browser</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((a) => (
              <tr key={a.id}>
                <td className="nowrap">{formatDateTime(a.created_at)}</td>
                <td>{a.email}</td>
                <td>{a.successful ? <span className="badge st-ok">Signed in</span> : <span className="badge st-bad">{a.failure_reason ?? 'Failed'}</span>}</td>
                <td className="mono">{a.ip_address}</td>
                <td className="small muted">{a.user_agent}</td>
              </tr>
            ))}
            {list.data && list.data.data.length === 0 && (
              <tr>
                <td colSpan={5} className="empty">
                  No sign-ins match.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
    </>
  )
}

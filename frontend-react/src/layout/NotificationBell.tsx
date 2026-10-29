import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { notificationsApi } from '../api/endpoints'
import type { AppNotification } from '../api/types'
import { formatDateTime } from '../lib/format'
import { pageFor } from '../lib/links'

function target(n: AppNotification) {
  return pageFor(n.related_type) ?? (n.type === 'procurement_schedule' ? '/procurement-planning' : null)
}

export function NotificationBell() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const count = useQuery({ queryKey: ['notifications', 'count'], queryFn: notificationsApi.unreadCount, refetchInterval: 60_000 })
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: () => notificationsApi.list(1), enabled: open })
  const unread = count.data?.count ?? 0

  const refresh = () => void qc.invalidateQueries({ queryKey: ['notifications'] })

  const openOne = async (n: AppNotification) => {
    if (!n.is_read) await notificationsApi.read(n.id).catch(() => undefined)
    refresh()
    const to = target(n)
    if (to) {
      setOpen(false)
      void navigate(to)
    }
  }

  return (
    <div className="notif">
      <button className="notif-bell" onClick={() => setOpen((o) => !o)} aria-label={`Notifications, ${unread} unread`} aria-expanded={open}>
        🔔{unread > 0 && <span className="notif-count">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && <div className="user-menu-overlay" onClick={() => setOpen(false)} />}
      {open && (
        <div className="notif-panel" role="dialog" aria-label="Notifications">
          <div className="notif-head">
            <strong>Notifications</strong>
            <button
              className="link-btn"
              disabled={unread === 0}
              onClick={() => void notificationsApi.readAll().then(refresh)}
            >
              Mark all read
            </button>
          </div>
          {list.isLoading && <div className="notif-empty">Loading…</div>}
          {list.data?.data.length === 0 && <div className="notif-empty">No notifications.</div>}
          <ul className="notif-list">
            {list.data?.data.map((n) => (
              <li key={n.id}>
                <button className={'notif-item' + (n.is_read ? '' : ' unread')} onClick={() => void openOne(n)}>
                  <span className="notif-title">{n.title}</span>
                  <span className="notif-msg">{n.message}</span>
                  <span className="notif-time">{formatDateTime(n.created_at)}</span>
                </button>
              </li>
            ))}
          </ul>
          {(list.data?.pagination.total ?? 0) > (list.data?.data.length ?? 0) && (
            <div className="notif-empty small">Showing the latest {list.data?.data.length} of {list.data?.pagination.total}.</div>
          )}
        </div>
      )}
    </div>
  )
}

import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { usersApi } from '../../api/endpoints'
import type { User } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { UserFormModal } from './UserFormModal'
import { useDebounced } from '../../lib/hooks'

export function UsersTab() {
  const { user: me, can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [roleId, setRoleId] = useState<number | undefined>()
  const [editing, setEditing] = useState<User | 'new' | null>(null)
  const [deleting, setDeleting] = useState<User | null>(null)
  const q = useDebounced(search.trim(), 300)
  const [filterKey, setFilterKey] = useState(`${q}|${roleId}`)

  // A new search or role filter starts again from page 1.
  if (filterKey !== `${q}|${roleId}`) {
    setFilterKey(`${q}|${roleId}`)
    setPage(1)
  }

  const users = useQuery({
    queryKey: ['users', page, q, roleId],
    queryFn: () => usersApi.list(page, q, roleId),
    placeholderData: keepPreviousData,
  })
  const roles = useQuery({ queryKey: ['user-role-options'], queryFn: usersApi.roleOptions })

  const remove = useMutation({
    mutationFn: (u: User) => usersApi.remove(u.id),
    onSuccess: (res) => {
      toast(res.message, 'success')
      setDeleting(null)
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete the user.', 'error')
      setDeleting(null)
    },
  })

  const canCreate = can('create_users')
  const canEdit = can('edit_users')
  const canDelete = can('delete_users')
  const pg = users.data?.pagination

  return (
    <>
      <div className="section-header">
        <h2>Users {pg ? `(${pg.total})` : ''}</h2>
        {canCreate && (
          <button className="btn btn-primary" onClick={() => setEditing('new')}>
            + Add New User
          </button>
        )}
      </div>

      <div className="toolbar">
        <input
          type="search"
          placeholder="Search by name or email"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Search users"
        />
        <select
          value={roleId ?? ''}
          onChange={(e) => setRoleId(e.target.value ? Number(e.target.value) : undefined)}
          aria-label="Filter by role"
        >
          <option value="">All roles</option>
          {roles.data?.data.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name}
            </option>
          ))}
        </select>
      </div>

      {users.isError && <div className="alert alert-error">{(users.error as Error).message}</div>}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Department</th>
              <th>2FA</th>
              {(canEdit || canDelete) && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {users.isPending && (
              <tr>
                <td colSpan={6} className="empty">
                  Loading users…
                </td>
              </tr>
            )}
            {users.data?.data.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>
                  <span className={'badge' + (u.role?.is_super_admin ? ' badge-gold' : '')}>{u.role?.name ?? 'No role'}</span>
                </td>
                <td>{u.department?.name ?? '—'}</td>
                <td>{u.two_factor_enabled ? 'On' : <span className="muted">Off</span>}</td>
                {(canEdit || canDelete) && (
                  <td>
                    <div className="actions">
                      {canEdit && (
                        <button className="icon-btn edit" title={`Edit ${u.name}`} onClick={() => setEditing(u)}>
                          ✎
                        </button>
                      )}
                      {canDelete && u.id !== me?.id && (
                        <button className="icon-btn delete" title={`Delete ${u.name}`} onClick={() => setDeleting(u)}>
                          🗑
                        </button>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            ))}
            {users.data && users.data.data.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  {q || roleId ? 'No users match this search.' : 'No users yet.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {pg && pg.last_page > 1 && (
        <div className="pagination">
          <button className="btn btn-outline btn-sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
            ‹ Prev
          </button>
          <span>
            Page {pg.current_page} of {pg.last_page}
          </span>
          <button className="btn btn-outline btn-sm" disabled={page >= pg.last_page} onClick={() => setPage((p) => p + 1)}>
            Next ›
          </button>
        </div>
      )}

      {editing && (
        <UserFormModal
          user={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(message) => {
            toast(message, 'success')
            setEditing(null)
            void queryClient.invalidateQueries({ queryKey: ['users'] })
          }}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title="Delete user"
          message={`Delete ${deleting.name} (${deleting.email})? This cannot be undone.`}
          confirmLabel="Delete user"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

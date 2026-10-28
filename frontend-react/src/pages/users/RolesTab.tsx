import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { rolesApi } from '../../api/endpoints'
import type { Role } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { RoleFormModal } from './RoleFormModal'

export function RolesTab() {
  const { user: me } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Role | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Role | null>(null)

  const roles = useQuery({ queryKey: ['roles'], queryFn: rolesApi.list })

  const remove = useMutation({
    mutationFn: (r: Role) => rolesApi.remove(r.id),
    onSuccess: (res) => {
      toast(res.message, 'success')
      setDeleting(null)
      void queryClient.invalidateQueries({ queryKey: ['roles'] })
      void queryClient.invalidateQueries({ queryKey: ['user-role-options'] })
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete the role.', 'error')
      setDeleting(null)
    },
  })

  return (
    <>
      <div className="section-header">
        <h2>Roles &amp; Permissions</h2>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          + Create New Role
        </button>
      </div>

      {roles.isPending && <div className="page-loading">Loading roles…</div>}
      {roles.isError && <div className="alert alert-error">{(roles.error as Error).message}</div>}

      <div className="roles-grid">
        {roles.data?.data.map((role) => {
          const locked = role.is_super_admin && !me?.is_super_admin
          const users = role.users_count ?? 0
          return (
            <article key={role.id} className="role-card">
              <header>
                <h3>{role.name}</h3>
                <span className="badge badge-muted">
                  {users} user{users === 1 ? '' : 's'}
                </span>
              </header>
              <p className="muted">{role.description || 'No description'}</p>
              <div>
                {role.permissions.includes('*') ? (
                  <span className="badge badge-gold">Full access</span>
                ) : (
                  <span className="muted">{role.permissions.length} permissions</span>
                )}
              </div>
              <footer>
                {!locked && (
                  <button className="btn btn-outline btn-sm" onClick={() => setEditing(role)}>
                    {role.is_system ? 'Edit permissions' : 'Edit'}
                  </button>
                )}
                {role.is_system ? (
                  <span className="muted system-tag">System role</span>
                ) : users === 0 ? (
                  <button className="btn btn-danger btn-sm" onClick={() => setDeleting(role)}>
                    Delete
                  </button>
                ) : null}
              </footer>
            </article>
          )
        })}
      </div>

      {editing && (
        <RoleFormModal
          role={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(message) => {
            toast(message, 'success')
            setEditing(null)
            void queryClient.invalidateQueries({ queryKey: ['roles'] })
            void queryClient.invalidateQueries({ queryKey: ['user-role-options'] })
          }}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title="Delete role"
          message={`Delete the role "${deleting.name}"? This cannot be undone.`}
          confirmLabel="Delete role"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

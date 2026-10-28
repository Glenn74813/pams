import { useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { RolesTab } from './RolesTab'
import { UsersTab } from './UsersTab'

export function UsersPage() {
  const { can } = useAuth()
  const canManageRoles = can('manage_roles')
  const [tab, setTab] = useState<'users' | 'roles'>('users')

  return (
    <div className="page">
      <h1 className="page-title">User &amp; Role Management</h1>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'users'} className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>
          Users
        </button>
        {canManageRoles && (
          <button role="tab" aria-selected={tab === 'roles'} className={tab === 'roles' ? 'active' : ''} onClick={() => setTab('roles')}>
            Roles &amp; Permissions
          </button>
        )}
      </div>
      {tab === 'users' || !canManageRoles ? <UsersTab /> : <RolesTab />}
    </div>
  )
}

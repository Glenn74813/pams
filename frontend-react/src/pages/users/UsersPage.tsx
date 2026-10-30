import { useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { DepartmentsTab } from './DepartmentsTab'
import { RolesTab } from './RolesTab'
import { UsersTab } from './UsersTab'

export function UsersPage() {
  const { can } = useAuth()
  const canManageRoles = can('manage_roles')
  const canOffices = can('view_users') || can('create_users') || can('edit_users')
  const [tab, setTab] = useState<'users' | 'offices' | 'roles'>('users')

  return (
    <div className="page">
      <h1 className="page-title">User &amp; Role Management</h1>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'users'} className={tab === 'users' ? 'active' : ''} onClick={() => setTab('users')}>
          Users
        </button>
        {canOffices && (
          <button role="tab" aria-selected={tab === 'offices'} className={tab === 'offices' ? 'active' : ''} onClick={() => setTab('offices')}>
            Offices
          </button>
        )}
        {canManageRoles && (
          <button role="tab" aria-selected={tab === 'roles'} className={tab === 'roles' ? 'active' : ''} onClick={() => setTab('roles')}>
            Roles &amp; Permissions
          </button>
        )}
      </div>
      {tab === 'roles' && canManageRoles ? <RolesTab /> : tab === 'offices' && canOffices ? <DepartmentsTab /> : <UsersTab />}
    </div>
  )
}

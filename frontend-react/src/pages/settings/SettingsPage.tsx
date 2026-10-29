import { useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { AccountTab } from './AccountTab'
import { AgencyTab } from './AgencyTab'
import { SignatoriesTab } from './SignatoriesTab'
import { SystemTab } from './SystemTab'
import '../inventory/inventory.css'
import './settings.css'

type Tab = 'account' | 'agency' | 'signatories' | 'system'

export function SettingsPage() {
  const { can } = useAuth()
  const tabs: [Tab, string, boolean][] = [
    ['account', 'My account', true],
    ['agency', 'Agency profile & logos', can('view_settings', 'edit_settings', 'manage_agency_profile')],
    ['signatories', 'Signatories', can('view_settings', 'edit_settings', 'manage_signatories')],
    ['system', 'System', can('view_settings', 'edit_settings', 'manage_system_settings')],
  ]
  const [tab, setTab] = useState<Tab>('account')
  const shown = tabs.filter(([, , ok]) => ok)
  return (
    <div className="page">
      <h1 className="page-title">Settings</h1>
      {shown.length > 1 && (
        <div className="tabs tabs-scroll" role="tablist">
          {shown.map(([key, label]) => (
            <button key={key} role="tab" aria-selected={tab === key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>
              {label}
            </button>
          ))}
        </div>
      )}
      {tab === 'account' && <AccountTab />}
      {tab === 'agency' && <AgencyTab />}
      {tab === 'signatories' && <SignatoriesTab />}
      {tab === 'system' && <SystemTab />}
    </div>
  )
}

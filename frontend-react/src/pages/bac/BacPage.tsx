import { useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { CompositionTab } from './CompositionTab'
import { MeetingsTab } from './MeetingsTab'
import { ResolutionsTab } from './ResolutionsTab'
import '../inventory/inventory.css'
import '../issuance/issuance.css'
import '../assets/assets.css'
import '../procurement/procurement.css'

type Tab = 'meetings' | 'resolutions' | 'composition'

export function BacPage() {
  const { can } = useAuth()
  const [tab, setTab] = useState<Tab>(can('manage_bac_resolutions') && !can('manage_bac_meetings') ? 'resolutions' : 'meetings')
  return (
    <div className="page page-wide">
      <h1 className="page-title">BAC Management</h1>
      <div className="tabs">
        <button className={tab === 'meetings' ? 'active' : ''} onClick={() => setTab('meetings')}>
          Meetings
        </button>
        <button className={tab === 'resolutions' ? 'active' : ''} onClick={() => setTab('resolutions')}>
          Resolutions
        </button>
        <button className={tab === 'composition' ? 'active' : ''} onClick={() => setTab('composition')}>
          Composition
        </button>
      </div>
      {tab === 'meetings' && <MeetingsTab />}
      {tab === 'resolutions' && <ResolutionsTab />}
      {tab === 'composition' && <CompositionTab />}
    </div>
  )
}

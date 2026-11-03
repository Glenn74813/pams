import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { integrationsApi } from '../../api/integrations'
import { ApiDataTable } from './ApiDataTable'

/** Payment records kept in other systems, read through their APIs and shown read-only. */
export function ExternalPaymentsPage() {
  const list = useQuery({ queryKey: ['integrations'], queryFn: integrationsApi.list })
  const lists = list.data?.data.filter((a) => a.is_active && a.context === 'none') ?? []
  const perRecord = list.data?.data.filter((a) => a.is_active && a.context === 'payable') ?? []
  const [chosen, setChosen] = useState<number | null>(null)
  const id = chosen ?? lists[0]?.id ?? null
  const data = useQuery({ queryKey: ['integration-data', id], queryFn: () => integrationsApi.data(id!), enabled: id !== null })
  return (
    <div className="page page-wide">
      <h1 className="page-title">Payments from Other Systems</h1>
      <p className="hint" style={{ marginBottom: '1rem' }}>
        Read-only records from the agency's other systems. To change them, use the system they come from.
        {perRecord.length > 0 && ` ${perRecord.map((a) => a.name).join(', ')} ${perRecord.length === 1 ? 'is' : 'are'} shown on each purchase order and contract instead.`}
      </p>
      {list.isError && <div className="alert alert-error">{(list.error as Error).message}</div>}
      {list.data && lists.length === 0 && (
        <div className="alert alert-info">
          No system is set up to list its payments here.{list.data.can_manage ? ' Connect one under Settings → Integrations.' : ' Ask your system administrator to connect one.'}
        </div>
      )}
      {lists.length > 1 && (
        <div className="tabs" role="tablist" style={{ marginBottom: '1rem' }}>
          {lists.map((a) => (
            <button key={a.id} role="tab" aria-selected={a.id === id} className={a.id === id ? 'active' : ''} onClick={() => setChosen(a.id)}>
              {a.name}
            </button>
          ))}
        </div>
      )}
      {data.isPending && id !== null && <div className="page-loading">Reading from the other system…</div>}
      {data.isError && <div className="alert alert-error">{(data.error as Error).message}</div>}
      {data.data && <ApiDataTable d={data.data} />}
    </div>
  )
}

/** For a purchase order or contract window: what each connected system holds for it. */
export function ExternalPaymentsSection({ type, id }: { type: 'purchase_order' | 'contract'; id: number }) {
  const list = useQuery({ queryKey: ['integrations'], queryFn: integrationsApi.list })
  const any = list.data?.data.some((a) => a.is_active && a.context === 'payable') ?? false
  const q = useQuery({ queryKey: ['integration-payable', type, id], queryFn: () => integrationsApi.payable(type, id), enabled: any })
  if (!any) return null
  return (
    <>
      <h4>Payments recorded in other systems</h4>
      {q.isPending && <p className="hint">Reading from the other systems…</p>}
      {q.isError && <div className="alert alert-warning">{(q.error as Error).message}</div>}
      {q.data?.data.map((d) => <ApiDataTable key={d.api.id} d={d} />)}
    </>
  )
}

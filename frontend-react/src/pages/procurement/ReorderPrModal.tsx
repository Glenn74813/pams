import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { planningApi, prApi } from '../../api/endpoints'
import type { ReorderPlan } from '../../api/types'
import { Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'
import { formatPeso, formatQty } from '../../lib/format'
import { blankLine } from './lines'
import { PrFormModal, type PrPreset } from './PrFormModal'
import './procurement.css'

interface Props {
  /** The items ticked on the reorder list; empty means every item at or below its reorder point. */
  itemIds: number[]
  onClose: () => void
}

type Group = ReorderPlan['groups'][number]

/** Matches the items to reorder with this year's APP lines, then opens a draft purchase request per requesting office. */
export function ReorderPrModal({ itemIds, onClose }: Props) {
  const toast = useToast()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const plan = useQuery({ queryKey: ['reorder-plan', itemIds], queryFn: () => prApi.reorder(itemIds) })
  const options = useQuery({ queryKey: ['procurement-options'], queryFn: planningApi.options, staleTime: 5 * 60_000 })
  const [preset, setPreset] = useState<PrPreset | null>(null)

  const open = (g: Group, app: NonNullable<ReorderPlan['app']>) =>
    setPreset({
      departmentId: g.department.id,
      appId: app.id,
      purpose: `Replenishment of stock at or below the reorder point (${g.lines.length} item${g.lines.length === 1 ? '' : 's'})`,
      lines: g.lines.map((l) =>
        blankLine({
          app_item_id: l.app_item_id,
          app_hint: `${l.item_code}: ${formatQty(l.on_hand)} on hand, reorder point ${formatQty(l.reorder_point)} · APP: ${formatQty(l.remaining_quantity)} left`,
          general_description: l.general_description,
          unit: l.unit ?? '',
          quantity: String(l.quantity),
          estimated_unit_cost: String(l.unit_cost),
          procurement_category: l.procurement_category,
        }),
      ),
    })

  if (preset) {
    return (
      <PrFormModal
        pr={null}
        preset={preset}
        options={options.data}
        onClose={() => setPreset(null)}
        onSaved={(p, message) => {
          for (const k of ['prs', 'app', 'apps', 'reorder-plan']) void qc.invalidateQueries({ queryKey: [k] })
          toast(message, 'success')
          onClose()
          navigate(`/purchase-requests?pr=${p.id}`)
        }}
      />
    )
  }

  const d = plan.data
  return (
    <Modal title="Reorder through a purchase request" onClose={onClose} width={900}>
      {plan.isPending && <p className="muted">Matching the items with the APP…</p>}
      {plan.isError && <div className="alert alert-error">Could not work out the reorder.</div>}
      {d && (
        <>
          <p className="muted">
            {d.app
              ? `Each item is drawn on the line of ${d.app.app_number} (FY ${d.fiscal_year}) that plans it, up to what is left of that line. A purchase request is for one requesting office.`
              : `There is no approved APP for FY ${d.fiscal_year}, so the items cannot be requested against the plan yet.`}
          </p>
          {d.app &&
            d.groups.map((g) => (
              <section key={g.department.id ?? 0} className="reorder-group">
                <div className="section-header">
                  <h4>{g.department.name ?? 'No office'}</h4>
                  <button type="button" className="btn btn-primary btn-sm" onClick={() => open(g, d.app!)}>
                    Create purchase request
                  </button>
                </div>
                <div className="table-wrap">
                  <table className="table table-compact">
                    <thead>
                      <tr>
                        <th>Item</th>
                        <th className="num">On hand</th>
                        <th className="num">Suggested</th>
                        <th className="num">To request</th>
                        <th className="num">Unit cost</th>
                        <th className="num">Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      {g.lines.map((l) => (
                        <tr key={l.item_id}>
                          <td>
                            <span className="mono">{l.item_code}</span> {l.item_name}
                            <div className="muted small">
                              APP: {l.general_description} · {l.procurement_mode}
                            </div>
                          </td>
                          <td className="num">{formatQty(l.on_hand)}</td>
                          <td className="num">{formatQty(l.suggested)}</td>
                          <td className="num">
                            {formatQty(l.quantity)} <span className="muted">{l.unit}</span>
                            {l.quantity < l.suggested && <div className="hint warn">only {formatQty(l.remaining_quantity)} left in the APP</div>}
                          </td>
                          <td className="num">{formatPeso(l.unit_cost)}</td>
                          <td className="num">{formatPeso(l.unit_cost * l.quantity)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            ))}
          {d.app && d.groups.length === 0 && <p>None of these items has an APP line with a balance left.</p>}
          {d.unplanned.length > 0 && (
            <section className="reorder-group">
              <h4>Not covered by the APP</h4>
              <div className="table-wrap">
                <table className="table table-compact">
                  <thead>
                    <tr>
                      <th>Item</th>
                      <th className="num">On hand</th>
                      <th className="num">Suggested</th>
                      <th>Why</th>
                    </tr>
                  </thead>
                  <tbody>
                    {d.unplanned.map((u) => (
                      <tr key={u.item_id}>
                        <td>
                          <span className="mono">{u.item_code}</span> {u.item_name}
                        </td>
                        <td className="num">
                          {formatQty(u.on_hand)} <span className="muted">{u.unit}</span>
                        </td>
                        <td className="num">{u.suggested > 0 ? formatQty(u.suggested) : '—'}</td>
                        <td className="small">{u.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}
      <div className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onClose}>
          Close
        </button>
      </div>
    </Modal>
  )
}

import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { biddingApi, supplierApi } from '../../api/endpoints'
import type { Bidder, Bidding } from '../../api/types'
import { formatPeso } from '../../lib/format'
import { SECURITY_FORMS, STAGES } from './labels'

interface Props {
  b: Bidding
  onChanged: (message: string) => void
  onError: (message: string) => void
}

const RESULT_CLASS: Record<string, string> = { passed: 'st-ok', failed: 'st-bad', pending: 'badge-muted' }

/** The bids as read at opening, and their evaluation stage by stage. */
export function BiddersPanel({ b, onChanged, onError }: Props) {
  const [adding, setAdding] = useState(false)
  const [failing, setFailing] = useState<{ bidder: Bidder; stage: string } | null>(null)
  const [why, setWhy] = useState('')
  const [scoring, setScoring] = useState<Bidder | null>(null)
  const fail = (err: unknown) => onError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const evaluate = useMutation({
    mutationFn: (v: { bidder: Bidder; stage: string; result: string; remarks?: string }) => biddingApi.evaluate(v.bidder.id, { stage: v.stage, result: v.result, remarks: v.remarks }),
    onSuccess: (_, v) => {
      setFailing(null)
      setWhy('')
      onChanged(`${v.bidder.supplier.name}: ${v.stage.replace('_', '-')} ${v.result}.`)
    },
    onError: fail,
  })
  const remove = useMutation({ mutationFn: (x: Bidder) => biddingApi.removeBidder(x.id), onSuccess: () => onChanged('Bid removed.'), onError: fail })
  const mearb = b.evaluation_method === 'mearb'
  const nextStage = (x: Bidder) => STAGES.find(([s]) => x.results[s] === 'pending')?.[0]
  const anyPostQual = (b.bidders ?? []).some((x) => x.results.post_qualification !== 'pending')

  return (
    <>
      <div className="section-header" style={{ marginTop: '1rem' }}>
        <h4>Bids ({b.bidders_count})</h4>
        {b.can_record_bids && !adding && (
          <button className="btn btn-outline btn-sm" onClick={() => setAdding(true)}>
            + Record bid
          </button>
        )}
      </div>
      {adding && <BidForm b={b} onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); onChanged('Bid recorded.') }} onError={onError} />}
      <div className="table-wrap">
        <table className="table table-compact">
          <thead>
            <tr>
              <th>#</th>
              <th>Bidder</th>
              <th className="num">Bid</th>
              <th>Bid security</th>
              {STAGES.map(([, l]) => (
                <th key={l}>{l}</th>
              ))}
              {mearb && <th className="num">Score</th>}
              <th></th>
            </tr>
          </thead>
          <tbody>
            {b.bidders?.map((x) => {
              const next = nextStage(x)
              const failedEarlier = STAGES.some(([s]) => x.results[s] === 'failed')
              const canAct = b.can_evaluate && !failedEarlier && next && (next !== 'post_qualification' || x.post_qual_turn)
              return (
                <tr key={x.id} className={failedEarlier ? 'row-muted' : ''}>
                  <td>{x.computed_rank ?? '—'}</td>
                  <td>
                    {x.is_winner && '🏆 '}
                    {x.supplier.name}
                    {!x.supplier_eligible && <span className="badge st-bad">Not eligible</span>}
                    {x.remarks && <div className="muted small">{x.remarks}</div>}
                  </td>
                  <td className="num">
                    {x.bid_amount == null ? '—' : formatPeso(x.bid_amount)}
                    {x.over_abc && <div><span className="badge st-bad">Over ABC</span></div>}
                  </td>
                  <td>
                    {x.bid_security_form ? SECURITY_FORMS[x.bid_security_form] ?? x.bid_security_form : '—'}
                    {x.bid_security_form && (
                      <div className="small">
                        {x.bid_security_ok ? <span className="badge st-ok">OK</span> : <span className="badge st-bad">Short — {formatPeso(x.bid_security_required)} needed</span>}
                      </div>
                    )}
                  </td>
                  {STAGES.map(([s]) => (
                    <td key={s}>
                      <span className={`badge ${RESULT_CLASS[x.results[s]]}`}>{x.results[s]}</span>
                    </td>
                  ))}
                  {mearb && (
                    <td className="num">
                      {x.mearb_score ?? '—'}
                      {x.rated_score != null && <div className="muted small">rated {x.rated_score}</div>}
                    </td>
                  )}
                  <td>
                    <div className="workflow-actions">
                      {canAct && next && (
                        <>
                          <button className="btn btn-primary btn-sm" disabled={evaluate.isPending} onClick={() => evaluate.mutate({ bidder: x, stage: next, result: 'passed' })}>
                            Pass {STAGES.find(([s]) => s === next)![1]}
                          </button>
                          <button className="btn btn-outline btn-sm" onClick={() => { setWhy(''); setFailing({ bidder: x, stage: next }) }}>
                            Fail
                          </button>
                        </>
                      )}
                      {mearb && b.can_evaluate && !anyPostQual && (
                        <button className="btn btn-outline btn-sm" onClick={() => setScoring(x)}>
                          Scores
                        </button>
                      )}
                      {b.can_record_bids && (
                        <button className="icon-btn delete" title={`Remove ${x.supplier.name}'s bid`} onClick={() => remove.mutate(x)}>
                          🗑
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
            {b.bidders?.length === 0 && (
              <tr>
                <td colSpan={mearb ? 10 : 9} className="empty">
                  No bids recorded.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      {b.can_evaluate && b.next_post_qual_bidder_id && (
        <p className="hint">Post-qualify the top-ranked responsive bidder; if it fails, the next one in rank is post-qualified.</p>
      )}
      {failing && (
        <div className="reject-box">
          <div className="field">
            <label htmlFor="bd-why">
              Why does {failing.bidder.supplier.name} fail {failing.stage.replace('_', '-')}? *
            </label>
            <input id="bd-why" value={why} onChange={(e) => setWhy(e.target.value)} autoFocus />
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setFailing(null)}>
              Cancel
            </button>
            <button className="btn btn-danger" disabled={!why.trim() || evaluate.isPending} onClick={() => evaluate.mutate({ bidder: failing.bidder, stage: failing.stage, result: 'failed', remarks: why })}>
              Record failure
            </button>
          </div>
        </div>
      )}
      {scoring && <ScoreForm b={b} bidder={scoring} onCancel={() => setScoring(null)} onSaved={() => { setScoring(null); onChanged('Scores saved.') }} onError={onError} />}
    </>
  )
}

function BidForm({ b, onCancel, onSaved, onError }: { b: Bidding; onCancel: () => void; onSaved: () => void; onError: (m: string) => void }) {
  const suppliers = useQuery({ queryKey: ['supplier-options'], queryFn: supplierApi.options })
  const [supplierId, setSupplierId] = useState('')
  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  const [form, setForm] = useState('bid_securing_declaration')
  const [secAmount, setSecAmount] = useState('')
  const save = useMutation({
    mutationFn: () =>
      biddingApi.addBidder(b.id, {
        supplier_id: supplierId && supplierId !== 'new' ? Number(supplierId) : null,
        supplier_name: supplierId === 'new' ? name : undefined,
        bid_amount: amount === '' ? null : Number(amount),
        bid_security_form: form,
        bid_security_amount: secAmount === '' ? null : Number(secAmount),
        bid_security_posted: true,
      }),
    onSuccess: onSaved,
    onError: (err) => onError(err instanceof ApiError ? err.message : 'Could not record the bid.'),
  })
  const bidding = new Set(b.bidders?.map((x) => x.supplier.id))
  const rate = form === 'surety_bond' ? 0.05 : form === 'bid_securing_declaration' ? 0 : 0.02
  return (
    <div className="reject-box">
      <div className="form-grid three">
        <div className="field">
          <label htmlFor="bf-sup">Bidder *</label>
          <select id="bf-sup" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
            <option value="">Choose</option>
            {suppliers.data?.data.filter((s) => !bidding.has(s.id)).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
            <option value="new">+ New supplier…</option>
          </select>
        </div>
        {supplierId === 'new' && (
          <div className="field">
            <label htmlFor="bf-name">Supplier name *</label>
            <input id="bf-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
        )}
        <div className="field">
          <label htmlFor="bf-amt">Bid as read (₱)</label>
          <input id="bf-amt" type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="bf-form">Bid security</label>
          <select id="bf-form" value={form} onChange={(e) => setForm(e.target.value)}>
            {Object.entries(SECURITY_FORMS).map(([k, l]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        {rate > 0 && (
          <div className="field">
            <label htmlFor="bf-sec">Security amount (₱)</label>
            <input id="bf-sec" type="number" min={0} step="0.01" value={secAmount} onChange={(e) => setSecAmount(e.target.value)} />
            <span className="hint">At least {formatPeso(b.abc * rate)}</span>
          </div>
        )}
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={save.isPending || !supplierId || (supplierId === 'new' && !name.trim())} onClick={() => save.mutate()}>
          Record bid
        </button>
      </div>
    </div>
  )
}

function ScoreForm({ b, bidder, onCancel, onSaved, onError }: { b: Bidding; bidder: Bidder; onCancel: () => void; onSaved: () => void; onError: (m: string) => void }) {
  const [scores, setScores] = useState<Record<number, string>>(() => Object.fromEntries((b.criteria ?? []).map((c) => [c.id, String(bidder.scores[String(c.id)] ?? '')])))
  const save = useMutation({
    mutationFn: () => biddingApi.scores(bidder.id, (b.criteria ?? []).filter((c) => scores[c.id] !== '').map((c) => ({ bidding_criterion_id: c.id, score: Number(scores[c.id]) }))),
    onSuccess: onSaved,
    onError: (err) => onError(err instanceof ApiError ? err.message : 'Could not save the scores.'),
  })
  return (
    <div className="reject-box">
      <h4 style={{ margin: '0 0 0.5rem' }}>Rated scores — {bidder.supplier.name}</h4>
      <div className="form-grid three">
        {b.criteria?.map((c) => (
          <div key={c.id} className="field">
            <label htmlFor={`sc-${c.id}`}>
              {c.name} ({c.weight}%)
            </label>
            <input id={`sc-${c.id}`} type="number" min={0} max={100} value={scores[c.id]} onChange={(e) => setScores((s) => ({ ...s, [c.id]: e.target.value }))} />
          </div>
        ))}
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={save.isPending} onClick={() => save.mutate()}>
          Save scores
        </button>
      </div>
    </div>
  )
}

import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { biddingApi, supplierApi } from '../../api/endpoints'
import { unsolicitedApi, type UnsolicitedOffer } from '../../api/unsolicited'
import { useAuth } from '../../auth/AuthContext'
import { Modal } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatDate, formatPeso } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { Badge } from '../assets/pickers'
import { SECURITY_FORMS } from './labels'
import '../inventory/inventory.css'
import '../assets/assets.css'

const STATUS: Record<string, [string, string]> = {
  received: ['Received', 'st-pending'],
  awaiting_documents: ['Awaiting documents', 'st-pending'],
  evaluation: ['Under evaluation', 'st-partial'],
  negotiation: ['Under negotiation', 'st-partial'],
  original_offeror: ['Original offeror', 'st-partial'],
  comparative_bidding: ['Comparative bidding', 'st-partial'],
  bid_matching: ['Bid matching', 'st-partial'],
  awarded_original: ['Awarded to original offeror', 'st-ok'],
  awarded_comparative: ['Awarded to lowest bid', 'st-ok'],
  rejected: ['Rejected', 'badge-muted'],
  withdrawn: ['Withdrawn', 'badge-muted'],
  failed_negotiation: ['Failed negotiation', 'st-bad'],
}

const msg = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback)

/** Unsolicited Offer with Bid Matching (IRR Section 30), step by step with each deadline. */
export function UnsolicitedOffersPage() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [adding, setAdding] = useState(false)
  const [viewing, setViewing] = useState<number | null>(null)
  const q = useDebounced(search.trim())
  const list = useQuery({ queryKey: ['unsolicited', page, status, q], queryFn: () => unsolicitedApi.list({ page, status: status || undefined, search: q || undefined }), placeholderData: keepPreviousData })
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['unsolicited'] })
  return (
    <div className="page page-wide">
      <h1 className="page-title">Unsolicited Offers</h1>
      <div className="section-header">
        <div className="toolbar" style={{ margin: 0 }}>
          <input type="search" placeholder="Search number, title or offeror" value={search} onChange={(e) => { setSearch(e.target.value); setPage(1) }} />
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }} aria-label="Status">
            <option value="">All</option>
            {Object.entries(STATUS).map(([k, [l]]) => (
              <option key={k} value={k}>
                {l}
              </option>
            ))}
          </select>
        </div>
        {can('manage_bidding') && (
          <button className="btn btn-primary" onClick={() => setAdding(true)}>
            + Record offer
          </button>
        )}
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Number</th>
              <th>Title</th>
              <th>Offeror</th>
              <th className="num">Proposed</th>
              <th>Status</th>
              <th>Next deadline</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.data.map((o) => (
              <tr key={o.id} style={{ cursor: 'pointer' }} onClick={() => setViewing(o.id)}>
                <td>{o.offer_number}</td>
                <td>{o.title}</td>
                <td>{o.supplier.name}</td>
                <td className="num">{formatPeso(o.proposed_amount)}</td>
                <td>
                  <Badge map={STATUS} value={o.status} />
                </td>
                <td>
                  {o.due_date ? formatDate(o.due_date) : '—'} {o.overdue && <span className="badge st-bad">Overdue</span>}
                  {o.due_for && <div className="muted small">{o.due_for}</div>}
                </td>
              </tr>
            ))}
            {list.data?.data.length === 0 && (
              <tr>
                <td colSpan={6} className="empty">
                  No unsolicited offers.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={list.data?.pagination} onPage={setPage} />
      {adding && (
        <NewOffer
          onClose={() => setAdding(false)}
          onSaved={(o) => {
            setAdding(false)
            refresh()
            toast(`${o.offer_number} recorded.`)
            setViewing(o.id)
          }}
        />
      )}
      {viewing !== null && <OfferDetail id={viewing} onClose={() => setViewing(null)} onChanged={refresh} />}
    </div>
  )
}

function NewOffer({ onClose, onSaved }: { onClose: () => void; onSaved: (o: UnsolicitedOffer) => void }) {
  const suppliers = useQuery({ queryKey: ['supplier-options'], queryFn: supplierApi.options })
  const [f, setF] = useState({ supplier: '', title: '', concept: '', category: 'Goods', amount: '', received: '' })
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => unsolicitedApi.create({ supplier_id: Number(f.supplier), title: f.title, concept: f.concept, category: f.category, proposed_amount: Number(f.amount), received_date: f.received || undefined }),
    onSuccess: onSaved,
    onError: (e) => setError(msg(e, 'Could not record the offer.')),
  })
  return (
    <Modal title="Record unsolicited offer" onClose={onClose} width={700}>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="uo-sup">Offeror *</label>
          <select id="uo-sup" value={f.supplier} onChange={(e) => setF({ ...f, supplier: e.target.value })}>
            <option value="">Choose</option>
            {suppliers.data?.data.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="uo-cat">Category *</label>
          <select id="uo-cat" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
            <option>Goods</option>
            <option>Consulting Services</option>
          </select>
        </div>
        <div className="field span-2">
          <label htmlFor="uo-title">Title *</label>
          <input id="uo-title" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />
        </div>
        <div className="field span-2">
          <label htmlFor="uo-concept">New concept or technology *</label>
          <textarea id="uo-concept" rows={3} value={f.concept} onChange={(e) => setF({ ...f, concept: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="uo-amount">Proposed contract cost (₱) *</label>
          <input id="uo-amount" type="number" min={0} step="0.01" value={f.amount} onChange={(e) => setF({ ...f, amount: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="uo-received">Date received</label>
          <input id="uo-received" type="date" value={f.received} onChange={(e) => setF({ ...f, received: e.target.value })} />
        </div>
      </div>
      <div className="modal-footer">
        <button className="btn btn-secondary" onClick={onClose}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled={save.isPending || !f.supplier || !f.title.trim() || !f.concept.trim() || !f.amount} onClick={() => save.mutate()}>
          Record offer
        </button>
      </div>
    </Modal>
  )
}

type Form = Record<string, string | boolean>

function OfferDetail({ id, onClose, onChanged }: { id: number; onClose: () => void; onChanged: () => void }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const offer = useQuery({ queryKey: ['unsolicited', 'one', id], queryFn: () => unsolicitedApi.show(id) })
  const options = useQuery({ queryKey: ['unsolicited-options'], queryFn: unsolicitedApi.options })
  const biddings = useQuery({ queryKey: ['unsolicited-biddings'], queryFn: () => biddingApi.list({ page: 1, status: 'draft' }) })
  const [action, setAction] = useState<string | null>(null)
  const [f, setF] = useState<Form>({})
  const [error, setError] = useState<string | null>(null)
  const str = (k: string) => (typeof f[k] === 'string' ? (f[k] as string) : '')
  const num = (k: string) => (str(k) === '' ? undefined : Number(str(k)))
  const opt = (k: string) => str(k) || undefined
  const yes = (k: string) => f[k] === true
  const act = useMutation({
    mutationFn: async () => {
      switch (action) {
        case 'needed': return unsolicitedApi.assess(id, { needed: true, notice_date: opt('date') })
        case 'not_needed': return unsolicitedApi.assess(id, { needed: false, notice_date: opt('date'), reason: opt('reason') })
        case 'documents': return unsolicitedApi.documents(id, { concept_details: yes('a'), proof_of_concept: yes('b'), eligibility_documents: yes('c'), terms_and_consent: yes('d'), date: opt('date') })
        case 'favorable': return unsolicitedApi.evaluate(id, { favorable: true, new_concept_basis: opt('basis'), funds_available: yes('funds'), eligibility_verified: yes('verified'), date: opt('date'), negotiation_start: opt('start'), extension_days: num('ext'), extension_reason: opt('extReason') })
        case 'unfavorable': return unsolicitedApi.evaluate(id, { favorable: false, date: opt('date'), reason: opt('reason') })
        case 'negotiated': return unsolicitedApi.negotiation(id, { successful: true, date: opt('date'), negotiated_amount: num('amount'), terms: opt('terms'), certification_number: opt('cert') })
        case 'negotiation_failed': return unsolicitedApi.negotiation(id, { successful: false, date: opt('date'), reason: opt('reason') })
        case 'security': return unsolicitedApi.bidSecurity(id, { form: str('form') || 'bid_securing_declaration', amount: num('amount'), date: opt('date') })
        case 'link': return unsolicitedApi.linkBidding(id, Number(str('bidding')))
        case 'matched': return unsolicitedApi.match(id, { matched: true, amount: num('amount'), date: opt('date') })
        case 'not_matched': return unsolicitedApi.match(id, { matched: false, date: opt('date') })
        case 'reject': return unsolicitedApi.reject(id, { reason: str('reason'), date: opt('date') })
        case 'withdraw': return unsolicitedApi.withdraw(id, { reason: opt('reason'), date: opt('date') })
        default: return unsolicitedApi.startMatching(id)
      }
    },
    onSuccess: () => {
      setAction(null)
      setF({})
      setError(null)
      toast('Saved.')
      onChanged()
      void queryClient.invalidateQueries({ queryKey: ['unsolicited', 'one', id] })
    },
    onError: (e) => setError(msg(e, 'Could not save this step.')),
  })
  const o = offer.data
  const start = (name: string) => {
    setError(null)
    setF({})
    setAction(name)
  }
  const input = (key: string, label: string, type = 'text') => (
    <div className="field" key={key}>
      <label htmlFor={`uo-${key}`}>{label}</label>
      <input id={`uo-${key}`} type={type} value={str(key)} onChange={(e) => setF({ ...f, [key]: e.target.value })} />
    </div>
  )
  const check = (key: string, label: string) => (
    <label className="checkbox-line" key={key}>
      <input type="checkbox" checked={yes(key)} onChange={(e) => setF({ ...f, [key]: e.target.checked })} /> {label}
    </label>
  )
  const button = (name: string, label: string, primary = true) => (
    <button key={name} className={primary ? 'btn btn-primary' : 'btn btn-secondary'} onClick={() => start(name)}>
      {label}
    </button>
  )
  if (!o) return <Modal title="Unsolicited offer" onClose={onClose} width={820}><p className="muted">Loading…</p></Modal>
  const open = !['awarded_original', 'awarded_comparative', 'rejected', 'withdrawn', 'failed_negotiation'].includes(o.status)
  const rows: [string, string | null | undefined][] = [
    ['Offeror', o.supplier.name],
    ['Category', o.category],
    ['Proposed cost', formatPeso(o.proposed_amount)],
    ['Received', formatDate(o.received_date)],
    ['Notice to offeror', o.notice_date && formatDate(o.notice_date)],
    ['Documents complete', o.documents_date && formatDate(o.documents_date)],
    ['New concept basis', o.new_concept_basis && options.data?.bases[o.new_concept_basis]],
    ['Evaluation finished', o.evaluation_date && formatDate(o.evaluation_date)],
    ['Negotiation', o.negotiation_start && `${formatDate(o.negotiation_start)}${o.negotiation_end ? ` to ${formatDate(o.negotiation_end)}` : ''}`],
    ['Negotiated price', o.negotiated_amount != null ? formatPeso(o.negotiated_amount) : null],
    ['Certification', o.certification_number && `${o.certification_number}, ${formatDate(o.certification_date)}`],
    ['Bid security', o.bid_security_form && `${SECURITY_FORMS[o.bid_security_form] ?? o.bid_security_form}${o.bid_security_amount != null ? `, ${formatPeso(o.bid_security_amount)}` : ''}, ${formatDate(o.bid_security_date)}`],
    ['Bidding', o.bidding && `${o.bidding.name} (${o.bidding_status})`],
    ['Lowest responsive bid', o.lccrb_supplier && `${o.lccrb_supplier.name}, ${formatPeso(o.lccrb_amount)}, ${formatDate(o.lccrb_date)}`],
    ['Bid matching', o.match_result && (o.match_result === 'matched' ? `Matched at ${formatPeso(o.match_amount)}` : o.match_result === 'no_comparative' ? 'No comparative bid' : 'Not matched')],
    ['Outcome', o.closing_reason],
  ]
  return (
    <Modal title={`${o.offer_number}: ${o.title}`} onClose={onClose} width={820}>
      {error && <div className="alert alert-error">{error}</div>}
      <p>
        <Badge map={STATUS} value={o.status} />{' '}
        {o.due_date && (
          <span className="small">
            {o.due_for}: by {formatDate(o.due_date)} {o.overdue && <span className="badge st-bad">Overdue</span>}
          </span>
        )}
        {!o.due_date && o.due_for && <span className="small">{o.due_for}</span>}
      </p>
      <p className="small">{o.concept}</p>
      <dl className="detail-grid">
        {rows.filter(([, v]) => v).map(([k, v]) => (
          <div key={k} style={{ display: 'contents' }}>
            <dt>{k}</dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>
      {action && (
        <div className="reject-box">
          <div className="form-grid">
            {action === 'documents' && [check('a', 'Concept details, expected output, cost-benefit and proposed cost'), check('b', 'Proof of concept or sample'), check('c', 'Legal, technical and financial eligibility documents'), check('d', 'Terms and consent to use the offer for bid matching')]}
            {action === 'favorable' && (
              <>
                <div className="field span-2">
                  <label htmlFor="uo-basis">HoPE's basis for a new concept or technology *</label>
                  <select id="uo-basis" value={str('basis')} onChange={(e) => setF({ ...f, basis: e.target.value })}>
                    <option value="">Choose</option>
                    {Object.entries(options.data?.bases ?? {}).map(([k, l]) => (
                      <option key={k} value={k}>
                        {l}
                      </option>
                    ))}
                  </select>
                </div>
                {check('funds', 'Funds are available')}
                {check('verified', 'Eligibility verified')}
                {input('start', 'Negotiation starts (within 5 days)', 'date')}
                {input('ext', "HoPE's extension (days, up to 30)", 'number')}
                {input('extReason', 'Reason for the extension')}
              </>
            )}
            {action === 'negotiated' && [input('amount', 'Negotiated price (₱) *', 'number'), input('cert', 'Original offeror certification no. *'), input('terms', 'Scope and terms agreed')]}
            {action === 'security' && (
              <>
                <div className="field">
                  <label htmlFor="uo-form">Form *</label>
                  <select id="uo-form" value={str('form') || 'bid_securing_declaration'} onChange={(e) => setF({ ...f, form: e.target.value })}>
                    {Object.entries(SECURITY_FORMS).map(([k, l]) => (
                      <option key={k} value={k}>
                        {l}
                      </option>
                    ))}
                  </select>
                </div>
                {input('amount', 'Amount (₱)', 'number')}
              </>
            )}
            {action === 'link' && (
              <div className="field span-2">
                <label htmlFor="uo-bidding">Draft bidding at lowest price *</label>
                <select id="uo-bidding" value={str('bidding')} onChange={(e) => setF({ ...f, bidding: e.target.value })}>
                  <option value="">{biddings.data?.data.length ? 'Choose' : 'No draft bidding; draft one under Competitive Bidding first'}</option>
                  {biddings.data?.data.filter((b) => b.evaluation_method === 'price').map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.bidding_number}: {b.title}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {action === 'matched' && input('amount', `Matching price (₱), at most ${formatPeso(o.lccrb_amount)} *`, 'number')}
            {['not_needed', 'unfavorable', 'negotiation_failed', 'reject', 'withdraw'].includes(action) && input('reason', action === 'withdraw' ? 'Reason' : 'Reason *')}
            {!['link', 'match_start'].includes(action) && input('date', 'Date', 'date')}
          </div>
          {action === 'withdraw' && o.notice_date && <p className="hint">Withdrawing more than 5 days after the notice bars the offeror for 1 year, 2 years on a second offense, and for good on a third.</p>}
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setAction(null)}>
              Back
            </button>
            <button className="btn btn-primary" disabled={act.isPending} onClick={() => act.mutate()}>
              Save
            </button>
          </div>
        </div>
      )}
      {!action && (
        <div className="modal-footer">
          {o.can_manage && open && !o.match_result && button('withdraw', 'Offeror withdrew', false)}
          {o.can_manage && ['received', 'awaiting_documents', 'evaluation', 'negotiation'].includes(o.status) && button('reject', 'Reject', false)}
          {o.can_manage && o.status === 'received' && [button('not_needed', 'Not needed', false), button('needed', 'Needed: notify offeror')]}
          {o.can_manage && o.status === 'awaiting_documents' && button('documents', 'Documents complete')}
          {o.can_manage && o.status === 'evaluation' && [button('unfavorable', 'Not favorable', false), button('favorable', 'Favorable')]}
          {o.can_manage && o.status === 'negotiation' && [button('negotiation_failed', 'Negotiation failed', false), button('negotiated', 'Negotiation successful')]}
          {o.can_manage && o.status === 'original_offeror' && !o.bid_security_date && button('security', 'Record bid security')}
          {o.can_manage && (o.status === 'original_offeror' || (o.status === 'comparative_bidding' && ['failed', 'cancelled'].includes(o.bidding_status ?? ''))) && o.bid_security_date && button('link', 'Link bidding')}
          {o.can_manage && o.status === 'comparative_bidding' && !['failed', 'cancelled'].includes(o.bidding_status ?? '') && (
            <button className="btn btn-primary" disabled={act.isPending} onClick={() => { setError(null); act.mutate() }}>
              Take the bidding result
            </button>
          )}
          {o.can_manage && o.status === 'bid_matching' && !o.match_result && [button('not_matched', 'Did not match', false), button('matched', 'Matched')]}
          {o.status === 'bid_matching' && o.match_result && <span className="muted footer-note">Award it on {o.bidding?.name} under Competitive Bidding.</span>}
        </div>
      )}
    </Modal>
  )
}

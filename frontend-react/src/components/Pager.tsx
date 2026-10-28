import type { Pagination } from '../api/types'

export function Pager({ pagination, onPage }: { pagination?: Pagination; onPage: (page: number) => void }) {
  if (!pagination || pagination.last_page <= 1) return null
  const { current_page: page, last_page: last } = pagination
  return (
    <div className="pagination">
      <button className="btn btn-outline btn-sm" disabled={page <= 1} onClick={() => onPage(page - 1)}>
        ‹ Prev
      </button>
      <span>
        Page {page} of {last}
      </span>
      <button className="btn btn-outline btn-sm" disabled={page >= last} onClick={() => onPage(page + 1)}>
        Next ›
      </button>
    </div>
  )
}

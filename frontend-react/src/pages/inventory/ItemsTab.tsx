import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi, type ItemFilters } from '../../api/endpoints'
import type { Classification, Item } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog } from '../../components/Modal'
import { Pager } from '../../components/Pager'
import { useToast } from '../../components/toast-context'
import { formatPeso, formatQty } from '../../lib/format'
import { useDebounced } from '../../lib/hooks'
import { AddStockModal } from './AddStockModal'
import { ClassificationBadge, StockLevelBadge } from './badges'
import { ItemDetailModal } from './ItemDetailModal'
import { ItemFormModal } from './ItemFormModal'
import { CLASSIFICATIONS } from './model'

export function ItemsTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [classification, setClassification] = useState('')
  const [level, setLevel] = useState('')
  const [viewing, setViewing] = useState<number | null>(null)
  const [editing, setEditing] = useState<Item | 'new' | null>(null)
  const [stocking, setStocking] = useState<Item | null>(null)
  const [deleting, setDeleting] = useState<Item | null>(null)
  const q = useDebounced(search.trim())

  const filterKey = `${q}|${categoryId}|${classification}|${level}`
  const [shownKey, setShownKey] = useState(filterKey)
  if (shownKey !== filterKey) {
    setShownKey(filterKey)
    setPage(1)
  }

  const filters: ItemFilters = {
    page,
    search: q || undefined,
    category_id: categoryId ? Number(categoryId) : undefined,
    classification: (classification || undefined) as Classification | undefined,
    stock_level: (level || undefined) as ItemFilters['stock_level'],
  }
  const items = useQuery({ queryKey: ['items', filters], queryFn: () => inventoryApi.items(filters), placeholderData: keepPreviousData })
  const categories = useQuery({ queryKey: ['categories'], queryFn: inventoryApi.categories })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['items'] })
    void queryClient.invalidateQueries({ queryKey: ['item'] })
    void queryClient.invalidateQueries({ queryKey: ['stock-card'] })
    void queryClient.invalidateQueries({ queryKey: ['categories'] })
  }

  const remove = useMutation({
    mutationFn: (i: Item) => inventoryApi.deleteItem(i.id),
    onSuccess: (res) => {
      toast(res.message, 'success')
      setDeleting(null)
      refresh()
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete the item.', 'error')
      setDeleting(null)
    },
  })

  // The edit form needs the item's lots, which the list doesn't carry.
  const openEdit = async (i: Item) => {
    try {
      setEditing(await queryClient.fetchQuery({ queryKey: ['item', i.id], queryFn: () => inventoryApi.item(i.id) }))
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Could not load the item.', 'error')
    }
  }

  const canCreate = can('create_inventory')
  const canEdit = can('edit_inventory')
  const canDelete = can('delete_inventory')
  const canStock = can('add_stock', 'manage_warehouse')
  const parents = (categories.data ?? []).filter((c) => c.parent_id === null)

  return (
    <>
      <div className="section-header">
        <h2>Item catalogue {items.data ? `(${items.data.pagination.total})` : ''}</h2>
        {canCreate && (
          <button className="btn btn-primary" onClick={() => setEditing('new')}>
            + Add New Item
          </button>
        )}
      </div>

      <div className="toolbar">
        <input type="search" placeholder="Search by code or name" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search items" />
        <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} aria-label="Category">
          <option value="">All categories</option>
          {parents.map((p) => [
            <option key={p.id} value={p.id}>
              {p.name}
            </option>,
            ...(categories.data ?? [])
              .filter((c) => c.parent_id === p.id)
              .map((c) => (
                <option key={c.id} value={c.id}>
                  &nbsp;&nbsp;{c.name}
                </option>
              )),
          ])}
        </select>
        <select value={classification} onChange={(e) => setClassification(e.target.value)} aria-label="Classification">
          <option value="">All classifications</option>
          {CLASSIFICATIONS.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
        <select value={level} onChange={(e) => setLevel(e.target.value)} aria-label="Stock level">
          <option value="">Any stock level</option>
          <option value="attention">Needs reorder</option>
          <option value="low">At or below reorder point</option>
          <option value="out">Out of stock</option>
        </select>
      </div>

      {items.isError && <div className="alert alert-error">{(items.error as Error).message}</div>}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>Category</th>
              <th>Class</th>
              <th className="num">On hand</th>
              <th className="num">Reorder pt</th>
              <th className="num">Avg cost</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {items.isPending && (
              <tr>
                <td colSpan={9} className="empty">
                  Loading items…
                </td>
              </tr>
            )}
            {items.data?.data.map((i) => (
              <tr key={i.id}>
                <td className="mono">{i.code}</td>
                <td>
                  <button className="link-btn" onClick={() => setViewing(i.id)}>
                    {i.name}
                  </button>
                </td>
                <td>{i.category?.name ?? '—'}</td>
                <td>
                  <ClassificationBadge value={i.classification} label={i.classification_label} />
                </td>
                <td className="num">
                  {formatQty(i.current_stock)} <span className="muted">{i.unit_of_measure}</span>
                </td>
                <td className="num">{formatQty(i.min_stock)}</td>
                <td className="num">{formatPeso(i.weighted_average_cost)}</td>
                <td>
                  <StockLevelBadge level={i.stock_status} />
                </td>
                <td>
                  <div className="actions">
                    {canStock && (
                      <button className="icon-btn stock" title={`Add stock to ${i.name}`} onClick={() => setStocking(i)}>
                        +
                      </button>
                    )}
                    {canEdit && (
                      <button className="icon-btn edit" title={`Edit ${i.name}`} onClick={() => void openEdit(i)}>
                        ✎
                      </button>
                    )}
                    {canDelete && (
                      <button className="icon-btn delete" title={`Delete ${i.name}`} onClick={() => setDeleting(i)}>
                        🗑
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {items.data && items.data.data.length === 0 && (
              <tr>
                <td colSpan={9} className="empty">
                  {q || categoryId || classification || level ? 'No items match these filters.' : 'No items in the catalogue yet.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <Pager pagination={items.data?.pagination} onPage={setPage} />

      {viewing !== null && <ItemDetailModal itemId={viewing} onClose={() => setViewing(null)} />}
      {editing && (
        <ItemFormModal
          item={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(_, message) => {
            toast(message, 'success')
            setEditing(null)
            refresh()
          }}
        />
      )}
      {stocking && (
        <AddStockModal
          item={stocking}
          onClose={() => setStocking(null)}
          onSaved={(message) => {
            toast(message, 'success')
            setStocking(null)
            refresh()
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete item"
          message={`Delete ${deleting.code} — ${deleting.name}? Items with stock on hand, requests or registered assets cannot be deleted.`}
          confirmLabel="Delete item"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

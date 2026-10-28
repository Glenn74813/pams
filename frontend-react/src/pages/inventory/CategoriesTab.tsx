import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { inventoryApi } from '../../api/endpoints'
import type { Category } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ConfirmDialog, Modal } from '../../components/Modal'
import { useToast } from '../../components/toast-context'

export function CategoriesTab() {
  const { can } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Category | { parent_id: number | null } | null>(null)
  const [deleting, setDeleting] = useState<Category | null>(null)
  const categories = useQuery({ queryKey: ['categories'], queryFn: inventoryApi.categories })

  const remove = useMutation({
    mutationFn: (c: Category) => inventoryApi.deleteCategory(c.id),
    onSuccess: (res) => {
      toast(res.message, 'success')
      setDeleting(null)
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
    },
    onError: (err) => {
      toast(err instanceof ApiError ? err.message : 'Could not delete the category.', 'error')
      setDeleting(null)
    },
  })

  const canAdd = can('create_inventory', 'edit_inventory')
  const canEdit = can('edit_inventory')
  const canDelete = can('delete_inventory')
  const all = categories.data ?? []
  const parents = all.filter((c) => c.parent_id === null)

  return (
    <>
      <div className="section-header">
        <h2>Categories</h2>
        {canAdd && (
          <button className="btn btn-primary" onClick={() => setEditing({ parent_id: null })}>
            + Add Category
          </button>
        )}
      </div>
      <p className="muted" style={{ marginBottom: '1rem' }}>
        Categories group items for reports. A category can have one level of sub-categories; items go in the most specific one.
      </p>

      {categories.isPending && <div className="page-loading">Loading…</div>}
      {categories.data && parents.length === 0 && <div className="alert alert-info">No categories yet.</div>}

      <div className="category-list">
        {parents.map((p) => {
          const kids = all.filter((c) => c.parent_id === p.id)
          return (
            <section key={p.id} className="category-card">
              <header>
                <h3>{p.name}</h3>
                <span className="muted">{kids.length ? `${kids.length} sub-categories` : `${p.items_count} items`}</span>
                <div className="actions">
                  {canAdd && p.items_count === 0 && (
                    <button className="btn btn-outline btn-sm" onClick={() => setEditing({ parent_id: p.id })}>
                      + Sub-category
                    </button>
                  )}
                  {canEdit && (
                    <button className="icon-btn edit" title={`Rename ${p.name}`} onClick={() => setEditing(p)}>
                      ✎
                    </button>
                  )}
                  {canDelete && kids.length === 0 && p.items_count === 0 && (
                    <button className="icon-btn delete" title={`Delete ${p.name}`} onClick={() => setDeleting(p)}>
                      🗑
                    </button>
                  )}
                </div>
              </header>
              {kids.length > 0 && (
                <ul>
                  {kids.map((k) => (
                    <li key={k.id}>
                      <span>{k.name}</span>
                      <span className="muted">{k.items_count} items</span>
                      <div className="actions">
                        {canEdit && (
                          <button className="icon-btn edit" title={`Edit ${k.name}`} onClick={() => setEditing(k)}>
                            ✎
                          </button>
                        )}
                        {canDelete && k.items_count === 0 && (
                          <button className="icon-btn delete" title={`Delete ${k.name}`} onClick={() => setDeleting(k)}>
                            🗑
                          </button>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )
        })}
      </div>

      {editing && (
        <CategoryFormModal
          category={'id' in editing ? editing : null}
          defaultParent={editing.parent_id}
          parents={parents}
          onClose={() => setEditing(null)}
          onSaved={(message) => {
            toast(message, 'success')
            setEditing(null)
            void queryClient.invalidateQueries({ queryKey: ['categories'] })
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete category"
          message={`Delete the category "${deleting.name}"?`}
          confirmLabel="Delete category"
          busy={remove.isPending}
          onConfirm={() => remove.mutate(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  )
}

interface FormProps {
  category: Category | null
  defaultParent: number | null
  parents: Category[]
  onClose: () => void
  onSaved: (message: string) => void
}

function CategoryFormModal({ category, defaultParent, parents, onClose, onSaved }: FormProps) {
  const [name, setName] = useState(category?.name ?? '')
  const [parentId, setParentId] = useState(defaultParent ? String(defaultParent) : '')
  const [error, setError] = useState<string | null>(null)
  const isParentWithKids = category !== null && category.parent_id === null && !category.is_leaf

  const save = useMutation({
    mutationFn: () => {
      const pid = parentId ? Number(parentId) : null
      return category ? inventoryApi.updateCategory(category.id, name, pid) : inventoryApi.createCategory(name, pid)
    },
    onSuccess: () => onSaved(category ? 'Category updated.' : 'Category added.'),
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the category.'),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    save.mutate()
  }

  return (
    <Modal title={category ? `Edit ${category.name}` : 'Add Category'} onClose={onClose} width={480}>
      <form onSubmit={submit}>
        {error && <div className="alert alert-error">{error}</div>}
        <div className="field" style={{ marginBottom: '1rem' }}>
          <label htmlFor="c-name">Name *</label>
          <input id="c-name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={255} autoFocus />
        </div>
        <div className="field">
          <label htmlFor="c-parent">Parent category</label>
          <select id="c-parent" value={parentId} onChange={(e) => setParentId(e.target.value)} disabled={isParentWithKids}>
            <option value="">None (top level)</option>
            {parents
              .filter((p) => p.id !== category?.id)
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
          </select>
          {isParentWithKids && <span className="hint">This category has sub-categories, so it stays at the top level.</span>}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

import { useState } from 'react'
import { CategoriesTab } from './CategoriesTab'
import { ItemsTab } from './ItemsTab'
import './inventory.css'

export function ItemsPage() {
  const [tab, setTab] = useState<'items' | 'categories'>('items')
  return (
    <div className="page page-wide">
      <h1 className="page-title">Item Master / Catalogue</h1>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'items'} className={tab === 'items' ? 'active' : ''} onClick={() => setTab('items')}>
          Items
        </button>
        <button role="tab" aria-selected={tab === 'categories'} className={tab === 'categories' ? 'active' : ''} onClick={() => setTab('categories')}>
          Categories
        </button>
      </div>
      {tab === 'items' ? <ItemsTab /> : <CategoriesTab />}
    </div>
  )
}

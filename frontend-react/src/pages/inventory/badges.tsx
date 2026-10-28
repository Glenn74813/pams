import type { Classification, StockLevel } from '../../api/types'

const LEVEL: Record<StockLevel, { label: string; cls: string }> = {
  out: { label: 'Out of stock', cls: 'lvl-out' },
  low: { label: 'Reorder', cls: 'lvl-low' },
  ok: { label: 'In stock', cls: 'lvl-ok' },
  over: { label: 'Over max', cls: 'lvl-over' },
}

export function StockLevelBadge({ level }: { level: StockLevel }) {
  const l = LEVEL[level]
  return <span className={`badge ${l.cls}`}>{l.label}</span>
}

const CLASS: Record<Classification, string> = {
  consumable: 'cls-consumable',
  semi_expendable: 'cls-semi',
  ppe: 'cls-ppe',
}

export function ClassificationBadge({ value, label }: { value: Classification; label: string }) {
  return <span className={`badge ${CLASS[value]}`}>{label}</span>
}

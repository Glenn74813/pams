const peso = new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP', minimumFractionDigits: 2 })
const qty = new Intl.NumberFormat('en-PH')

export const formatPeso = (v: number | null | undefined) => (v == null ? '—' : peso.format(v))
export const formatQty = (v: number | null | undefined) => (v == null ? '—' : qty.format(v))

/** "2026-10-28" -> "Oct 28, 2026" (dates are plain calendar dates, no timezone shift). */
export function formatDate(iso: string | null | undefined) {
  if (!iso) return '—'
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-PH', { year: 'numeric', month: 'short', day: 'numeric' })
}

export function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

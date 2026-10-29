import type { CSSProperties } from 'react'
import { Link } from 'react-router'

interface Props {
  /** Help article slug, e.g. "purchase-requests". */
  slug: string
  /** Optional section anchor inside the article, e.g. "certify-funds". */
  section?: string
  /** What the link explains, for screen readers and the tooltip. Defaults to "Help". */
  label?: string
  /** Open in a new tab, so a half-filled form is not lost. */
  newTab?: boolean
}

const style: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: '1.35rem',
  height: '1.35rem',
  borderRadius: '50%',
  border: '1px solid var(--border-color)',
  background: 'var(--card-bg)',
  color: 'var(--primary-accent)',
  fontSize: '0.8rem',
  fontWeight: 700,
  lineHeight: 1,
  textDecoration: 'none',
  verticalAlign: 'middle',
  marginLeft: '0.4rem',
}

/** A small "?" link to the matching help article: /help/:slug#section. */
export function HelpLink({ slug, section, label = 'Help', newTab = false }: Props) {
  const to = `/help/${slug}${section ? '#' + section : ''}`
  const title = newTab ? `${label} (opens in a new tab)` : label
  return (
    <Link
      to={to}
      className="help-link no-print"
      style={style}
      title={title}
      aria-label={title}
      {...(newTab ? { target: '_blank', rel: 'noopener' } : {})}
    >
      ?
    </Link>
  )
}

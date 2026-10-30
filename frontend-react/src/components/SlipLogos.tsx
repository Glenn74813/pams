import { useQuery } from '@tanstack/react-query'
import { settingsApi } from '../api/endpoints'

/**
 * The agency's document logos (Settings → Agency profile) along the top or bottom edge of a printed
 * slip, in their left / centre / right places. Nothing is drawn when no logo is set for that edge.
 */
export function SlipLogos({ edge }: { edge: 'top' | 'bottom' }) {
  const q = useQuery({ queryKey: ['document-logos'], queryFn: settingsApi.logos, staleTime: 5 * 60_000 })
  const logos = (q.data?.logos ?? []).filter((l) => l.data_uri && l.position.startsWith(edge + '-'))
  if (logos.length === 0) return null
  const at = (side: string) =>
    logos
      .filter((l) => l.position === `${edge}-${side}`)
      .sort((a, b) => a.sequence - b.sequence)
      .map((l) => <img key={l.id} src={l.data_uri!} alt={l.label ?? 'Logo'} style={{ maxHeight: 56, maxWidth: 160, objectFit: 'contain' }} />)
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', alignItems: 'center', gap: '0.75rem', margin: edge === 'top' ? '0 0 0.5rem' : '1rem 0 0' }}>
      <div style={{ display: 'flex', gap: '0.5rem' }}>{at('left')}</div>
      <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center' }}>{at('center')}</div>
      <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>{at('right')}</div>
    </div>
  )
}

import { adminArticles, sodArticle } from './admin'
import { assetArticles, qrArticle } from './assets'
import { dashboardArticle, faqArticle, gettingStartedArticle, notificationsArticle } from './general'
import { inventoryArticles } from './inventory'
import { procurementArticles } from './procurement'
import { reportArticles } from './reports'
import { torArticles } from './tor'
import type { Article, Block } from './types'

export { AREAS, type Area, type Article, type Block, type Section } from './types'

/** Every article, in reading order. */
export const ARTICLES: Article[] = [
  gettingStartedArticle,
  dashboardArticle,
  sodArticle,
  notificationsArticle,
  qrArticle,
  faqArticle,
  ...procurementArticles,
  ...inventoryArticles,
  ...assetArticles,
  ...reportArticles,
  ...adminArticles,
  ...torArticles,
]

export const articleBySlug = new Map(ARTICLES.map((a) => [a.slug, a]))

/** Plain text of a block, for searching. */
export function blockText(b: Block): string {
  switch (b.kind) {
    case 'p':
    case 'note':
      return b.text
    case 'steps':
    case 'list':
      return b.items.join(' ')
    case 'table':
      return [...b.head, ...b.rows.flat()].join(' ')
  }
}

export const stripMarks = (s: string) => s.replace(/\*\*/g, '')

/** Splits a query into lower-case terms; every term must match. */
export function queryTerms(q: string): string[] {
  return q.toLowerCase().split(/\s+/).map((t) => t.trim()).filter(Boolean)
}

const escapeRe = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** Matches any of the terms at the start of a word (so "ics" finds "ICS" but not "specifications"). */
export function termsRegExp(terms: string[], flags = 'giu'): RegExp {
  return new RegExp('(?<![\\p{L}\\p{N}])(' + terms.map(escapeRe).join('|') + ')', flags)
}

const has = (text: string, term: string) => termsRegExp([term], 'iu').test(text)

export interface SearchHit {
  article: Article
  /** Sections whose heading or body contains a term, with a snippet. */
  sections: { id: string; title: string; snippet: string }[]
  score: number
}

function snippet(text: string, term: string): string {
  const i = text.search(termsRegExp([term], 'iu'))
  if (i < 0) return text.slice(0, 140)
  const start = Math.max(0, i - 60)
  const end = Math.min(text.length, i + term.length + 80)
  return (start > 0 ? '…' : '') + text.slice(start, end) + (end < text.length ? '…' : '')
}

/** Case-insensitive search across titles, summaries, keywords, headings and body text. */
export function search(q: string, pool: Article[] = ARTICLES): SearchHit[] {
  const terms = queryTerms(q)
  if (terms.length === 0) return []
  const hits: SearchHit[] = []
  for (const article of pool) {
    const head = [article.title, article.summary, ...(article.keywords ?? [])].join(' ').toLowerCase()
    const sectionTexts = article.sections.map((s) => ({
      id: s.id,
      title: s.title,
      body: stripMarks(s.blocks.map(blockText).join(' ')),
    }))
    const all = head + ' ' + sectionTexts.map((s) => (s.title + ' ' + s.body).toLowerCase()).join(' ')
    if (!terms.every((t) => has(all, t))) continue
    let score = 0
    for (const t of terms) {
      if (has(article.title, t)) score += 10
      if (has(head, t)) score += 4
    }
    const sections = sectionTexts
      .filter((s) => terms.some((t) => has(s.title + ' ' + s.body, t)))
      .map((s) => {
        const first = terms.find((t) => has(s.body, t)) ?? terms[0]
        if (terms.some((t) => has(s.title, t))) score += 3
        score += 1
        return { id: s.id, title: s.title, snippet: snippet(s.body, first) }
      })
    hits.push({ article, sections, score })
  }
  return hits.sort((a, b) => b.score - a.score)
}

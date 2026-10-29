/**
 * Content model for the in-app help centre. Articles are plain data so they
 * can be searched, filtered and printed without a backend.
 *
 * Text strings may mark UI labels with **double asterisks**; the renderer
 * shows them in bold.
 */

export type Area = 'general' | 'procurement' | 'inventory' | 'assets' | 'reports' | 'admin'

export const AREAS: { key: Area; label: string }[] = [
  { key: 'general', label: 'Getting started' },
  { key: 'procurement', label: 'Procurement' },
  { key: 'inventory', label: 'Inventory' },
  { key: 'assets', label: 'Assets' },
  { key: 'reports', label: 'Reports & Compliance' },
  { key: 'admin', label: 'Administration' },
]

export type Block =
  | { kind: 'p'; text: string }
  | { kind: 'steps'; items: string[] }
  | { kind: 'list'; items: string[] }
  | { kind: 'table'; head: string[]; rows: string[][] }
  | { kind: 'note'; tone: 'tip' | 'warn'; text: string }

export interface Section {
  /** Anchor used in deep links: /help/:slug#id */
  id: string
  title: string
  blocks: Block[]
}

export interface Article {
  slug: string
  title: string
  area: Area
  /** One or two sentences shown in lists and search results. */
  summary: string
  /** The app page this article describes, for an "Open the page" link. */
  route?: string
  /** Extra words people may search for (abbreviations, form names). */
  keywords?: string[]
  sections: Section[]
}

export const p = (text: string): Block => ({ kind: 'p', text })
export const steps = (...items: string[]): Block => ({ kind: 'steps', items })
export const list = (...items: string[]): Block => ({ kind: 'list', items })
export const table = (head: string[], rows: string[][]): Block => ({ kind: 'table', head, rows })
export const tip = (text: string): Block => ({ kind: 'note', tone: 'tip', text })
export const warn = (text: string): Block => ({ kind: 'note', tone: 'warn', text })
export const sec = (id: string, title: string, ...blocks: Block[]): Section => ({ id, title, blocks })

/** Standard sections, so every module article uses the same anchors. */
export const who = (...items: string[]): Section => sec('who-can-use', 'Who can use it', list(...items))
export const purpose = (...blocks: Block[]): Section => sec('what-it-is-for', 'What it is for', ...blocks)
export const statuses = (...blocks: Block[]): Section => sec('statuses', 'Statuses and what moves them', ...blocks)
export const forms = (...blocks: Block[]): Section => sec('forms', 'Forms and documents produced', ...blocks)
export const tips = (...items: string[]): Section => sec('tips', 'Tips', list(...items))

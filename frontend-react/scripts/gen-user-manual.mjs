#!/usr/bin/env node
// Writes docs/user-manual.md from the in-app help articles, so the manual and the help centre say the same thing.
//   node frontend-react/scripts/gen-user-manual.mjs
import { registerHooks } from 'node:module'
import { writeFileSync } from 'node:fs'

// The articles import each other without file extensions, as Vite allows.
registerHooks({
  resolve(specifier, context, next) {
    if (specifier.startsWith('.') && !specifier.endsWith('.ts')) return next(`${specifier}.ts`, context)
    return next(specifier, context)
  },
})

const { ARTICLES, AREAS } = await import('../src/pages/help/articles/index.ts')

const cell = (s) => String(s).replaceAll('|', '\\|').replaceAll('\n', ' ')

function block(b) {
  switch (b.kind) {
    case 'p':
      return b.text
    case 'steps':
      return b.items.map((x, i) => `${i + 1}. ${x}`).join('\n')
    case 'list':
      return b.items.map((x) => `- ${x}`).join('\n')
    case 'note':
      return `> **${b.tone === 'warn' ? 'Caution' : 'Tip'}:** ${b.text}`
    case 'table':
      return [`| ${b.head.map(cell).join(' | ')} |`, `|${b.head.map(() => '---').join('|')}|`, ...b.rows.map((r) => `| ${r.map(cell).join(' | ')} |`)].join('\n')
    default:
      throw new Error(`Unknown help block: ${b.kind}`)
  }
}

const out = [
  '# PAMS user\'s manual',
  '',
  'How to use each page of PAMS. This file is generated from the in-app help (the **Help** menu) by `frontend-react/scripts/gen-user-manual.mjs`; change the help articles and run the script again, rather than editing it here.',
  '',
  '## Contents',
  '',
]
for (const area of AREAS) {
  out.push(`- **${area.label}**`)
  for (const a of ARTICLES.filter((x) => x.area === area.key)) out.push(`  - [${a.title}](#${a.slug})`)
}
out.push('')
for (const area of AREAS) {
  out.push(`# ${area.label}`, '')
  for (const a of ARTICLES.filter((x) => x.area === area.key)) {
    out.push(`<a id="${a.slug}"></a>`, '', `## ${a.title}`, '', a.summary, '')
    for (const s of a.sections) {
      out.push(`### ${s.title}`, '')
      for (const b of s.blocks) out.push(block(b), '')
    }
  }
}
const target = new URL('../../docs/user-manual.md', import.meta.url)
writeFileSync(target, `${out.join('\n').trimEnd()}\n`)
console.log(`wrote ${ARTICLES.length} articles to ${target.pathname}`)

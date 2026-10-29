import { type FormEvent, Fragment, type ReactNode, useEffect, useMemo } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { AREAS, ARTICLES, type Area, type Article, type Block, articleBySlug, queryTerms, search, termsRegExp } from './articles'
import './help.css'

/** Wraps every occurrence of the search terms in <mark>. */
function highlight(text: string, terms: string[]): ReactNode {
  if (terms.length === 0) return text
  return text.split(termsRegExp(terms)).map((seg, i) => (i % 2 === 1 ? <mark key={i}>{seg}</mark> : <Fragment key={i}>{seg}</Fragment>))
}

/** Renders **bold** markers and highlights search terms. */
function Rich({ text, terms }: { text: string; terms: string[] }) {
  return (
    <>
      {text.split('**').map((part, i) =>
        i % 2 === 1 ? <strong key={i}>{highlight(part, terms)}</strong> : <Fragment key={i}>{highlight(part, terms)}</Fragment>,
      )}
    </>
  )
}

function BlockView({ block, terms }: { block: Block; terms: string[] }) {
  switch (block.kind) {
    case 'p':
      return (
        <p>
          <Rich text={block.text} terms={terms} />
        </p>
      )
    case 'steps':
      return (
        <ol className="help-steps">
          {block.items.map((t, i) => (
            <li key={i}>
              <Rich text={t} terms={terms} />
            </li>
          ))}
        </ol>
      )
    case 'list':
      return (
        <ul className="help-list">
          {block.items.map((t, i) => (
            <li key={i}>
              <Rich text={t} terms={terms} />
            </li>
          ))}
        </ul>
      )
    case 'table':
      return (
        <div className="help-table-wrap">
          <table className="help-table">
            <thead>
              <tr>
                {block.head.map((h) => (
                  <th key={h} scope="col">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, r) => (
                <tr key={r}>
                  {row.map((cell, c) => (
                    <td key={c}>
                      <Rich text={cell} terms={terms} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )
    case 'note':
      return (
        <aside className={'help-note help-note-' + block.tone} aria-label={block.tone === 'tip' ? 'Tip' : 'Important'}>
          <span className="help-note-label">{block.tone === 'tip' ? 'Tip' : 'Important'}</span>
          <Rich text={block.text} terms={terms} />
        </aside>
      )
  }
}

const areaLabel = (a: Area) => AREAS.find((x) => x.key === a)?.label ?? a

/** Builds a help link that keeps the current search and area filter. */
function withQuery(path: string, keep: string, hash?: string) {
  return path + (keep ? '?' + keep : '') + (hash ? '#' + hash : '')
}

function ArticleView({ article, terms, keep }: { article: Article; terms: string[]; keep: string }) {
  return (
    <article className="help-article" aria-labelledby="help-article-title">
      <p className="help-crumb small muted no-print">
        <Link to={withQuery('/help', keep)}>Help</Link> › {areaLabel(article.area)}
      </p>
      <h1 id="help-article-title" className="page-title">
        <Rich text={article.title} terms={terms} />
      </h1>
      <p className="help-summary">
        <Rich text={article.summary} terms={terms} />
      </p>
      <div className="help-article-actions no-print">
        {article.route && (
          <Link className="btn btn-outline btn-sm" to={article.route}>
            Open the page
          </Link>
        )}
        <button type="button" className="btn btn-outline btn-sm" onClick={() => window.print()}>
          🖨 Print this article
        </button>
      </div>
      {article.sections.length > 2 && (
        <nav className="help-toc no-print" aria-label="On this page">
          <h2 className="small">On this page</h2>
          <ul>
            {article.sections.map((s) => (
              <li key={s.id}>
                <a href={'#' + s.id}>{s.title}</a>
              </li>
            ))}
          </ul>
        </nav>
      )}
      {article.sections.map((s) => (
        <section key={s.id} id={s.id} className="help-section" aria-labelledby={'h-' + s.id}>
          <h2 id={'h-' + s.id}>
            <Rich text={s.title} terms={terms} />
            <a className="help-anchor no-print" href={'#' + s.id} aria-label={'Link to ' + s.title}>
              #
            </a>
          </h2>
          {s.blocks.map((b, i) => (
            <BlockView key={i} block={b} terms={terms} />
          ))}
        </section>
      ))}
    </article>
  )
}

function Results({ q, terms, pool, keep }: { q: string; terms: string[]; pool: Article[]; keep: string }) {
  const hits = useMemo(() => search(q, pool), [q, pool])
  return (
    <section className="help-results" aria-labelledby="help-results-title">
      <h1 id="help-results-title" className="page-title">
        Search results
      </h1>
      <p className="muted" role="status">
        {hits.length === 0 ? `Nothing matches "${q}". Try fewer or different words.` : `${hits.length} article${hits.length === 1 ? '' : 's'} match "${q}".`}
      </p>
      <ul className="help-hit-list">
        {hits.map((h) => (
          <li key={h.article.slug} className="help-hit">
            <Link className="help-hit-title" to={withQuery('/help/' + h.article.slug, keep)}>
              <Rich text={h.article.title} terms={terms} />
            </Link>
            <span className="badge badge-muted">{areaLabel(h.article.area)}</span>
            <p className="small muted">
              <Rich text={h.article.summary} terms={terms} />
            </p>
            {h.sections.length > 0 && (
              <ul className="help-hit-sections">
                {h.sections.slice(0, 4).map((s) => (
                  <li key={s.id}>
                    <Link to={withQuery('/help/' + h.article.slug, keep, s.id)}>
                      <Rich text={s.title} terms={terms} />
                    </Link>
                    <span className="small"> — {highlight(s.snippet, terms)}</span>
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

function Home({ pool, keep }: { pool: Article[]; keep: string }) {
  const areas = AREAS.filter((a) => pool.some((x) => x.area === a.key))
  return (
    <section aria-labelledby="help-home-title">
      <h1 id="help-home-title" className="page-title">
        Help centre
      </h1>
      <p className="help-summary">
        How to use each part of the system: who can use it, step-by-step tasks, the statuses a record goes through, and the COA and GPPB forms it produces.
        New here? Start with <Link to="/help/getting-started">Getting started</Link>.
      </p>
      {areas.map((a) => (
        <div key={a.key} className="help-home-area">
          <h2>{a.label}</h2>
          <div className="help-cards">
            {pool
              .filter((x) => x.area === a.key)
              .map((x) => (
                <Link key={x.slug} to={withQuery('/help/' + x.slug, keep)} className="dash-card help-card">
                  <strong>{x.title}</strong>
                  <span className="small muted">{x.summary}</span>
                </Link>
              ))}
          </div>
        </div>
      ))}
    </section>
  )
}

export function HelpPage() {
  const { slug } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const areaParam = params.get('area')
  const area = AREAS.some((a) => a.key === areaParam) ? (areaParam as Area) : null
  const terms = useMemo(() => queryTerms(q), [q])
  const keep = params.toString()
  const article = slug ? articleBySlug.get(slug) : undefined

  const pool = useMemo(() => (area ? ARTICLES.filter((a) => a.area === area) : ARTICLES), [area])
  const navList = useMemo(() => (terms.length ? search(q, pool).map((h) => h.article) : pool), [q, terms, pool])

  useEffect(() => {
    if (!article) return
    const id = decodeURIComponent(location.hash.replace(/^#/, ''))
    if (id) document.getElementById(id)?.scrollIntoView({ block: 'start' })
    else window.scrollTo({ top: 0 })
  }, [article, location.hash])

  useEffect(() => {
    document.title = article ? `${article.title} · Help` : 'Help'
  }, [article])

  const update = (next: { q?: string; area?: Area | null }) => {
    const p = new URLSearchParams(params)
    if (next.q !== undefined) {
      if (next.q) p.set('q', next.q)
      else p.delete('q')
    }
    if (next.area !== undefined) {
      if (next.area) p.set('area', next.area)
      else p.delete('area')
    }
    setParams(p, { replace: true, preventScrollReset: true })
  }

  const onSearchSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (slug) navigate({ pathname: '/help', search: params.toString() ? '?' + params.toString() : '' })
  }

  return (
    <div className="page help-page">
      <div className="help-toolbar no-print">
        <form role="search" className="toolbar help-search" onSubmit={onSearchSubmit}>
          <label htmlFor="help-search-input" className="help-sr-only">
            Search help
          </label>
          <input
            id="help-search-input"
            type="search"
            placeholder="Search help, e.g. “ICS”, “certify funds”, “gate pass”"
            value={q}
            onChange={(e) => update({ q: e.target.value })}
            autoComplete="off"
          />
          {slug && q && (
            <button type="submit" className="btn btn-outline">
              Show all results
            </button>
          )}
        </form>
        <div className="help-areas" role="group" aria-label="Filter by area">
          <button type="button" className={'help-chip' + (area === null ? ' active' : '')} aria-pressed={area === null} onClick={() => update({ area: null })}>
            All
          </button>
          {AREAS.map((a) => (
            <button
              key={a.key}
              type="button"
              className={'help-chip' + (area === a.key ? ' active' : '')}
              aria-pressed={area === a.key}
              onClick={() => update({ area: area === a.key ? null : a.key })}
            >
              {a.label}
            </button>
          ))}
        </div>
      </div>

      <div className="help-layout">
        <nav className="help-nav no-print" aria-label="Help articles">
          {navList.length === 0 && <p className="small muted">No articles match.</p>}
          {AREAS.filter((a) => navList.some((x) => x.area === a.key)).map((a) => (
            <div key={a.key} className="help-nav-group">
              <h2 className="help-nav-heading">{a.label}</h2>
              <ul>
                {navList
                  .filter((x) => x.area === a.key)
                  .map((x) => (
                    <li key={x.slug}>
                      <Link
                        to={withQuery('/help/' + x.slug, keep)}
                        className={'help-nav-link' + (x.slug === slug ? ' active' : '')}
                        aria-current={x.slug === slug ? 'page' : undefined}
                      >
                        {x.title}
                      </Link>
                    </li>
                  ))}
              </ul>
            </div>
          ))}
        </nav>

        <div className="help-main">
          {slug && !article ? (
            <section>
              <h1 className="page-title">Article not found</h1>
              <p>
                There is no help article called “{slug}”. <Link to="/help">Back to the help centre</Link>.
              </p>
            </section>
          ) : article ? (
            <ArticleView article={article} terms={terms} keep={keep} />
          ) : terms.length ? (
            <Results q={q} terms={terms} pool={pool} keep={keep} />
          ) : (
            <Home pool={pool} keep={keep} />
          )}
        </div>
      </div>
    </div>
  )
}

const TOKEN_KEY = 'pams.token'

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string) => {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* private mode: the session simply won't survive a reload */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

/** An API failure with the server's message and any per-field validation errors. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string[]>
  readonly body: Record<string, unknown>

  constructor(status: number, body: Record<string, unknown>) {
    const fieldErrors = (body.errors as Record<string, string[]> | undefined) ?? {}
    const firstField = Object.values(fieldErrors).flat()[0]
    const message =
      firstField ??
      (body.error as string | undefined) ??
      (body.message as string | undefined) ??
      (body.status as string | undefined) ??
      `Request failed (${status})`
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.body = body
  }
}

let onUnauthorized: () => void = () => {}

/** The auth provider registers what to do when the session is no longer valid. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

export async function api<T>(
  method: Method,
  path: string,
  body?: unknown,
  opts: { params?: Record<string, string | number | boolean | undefined>; skipAuthRedirect?: boolean } = {},
): Promise<T> {
  const url = new URL('/api' + path, window.location.origin)
  for (const [k, v] of Object.entries(opts.params ?? {})) {
    if (v !== undefined && v !== '') url.searchParams.set(k, String(v))
  }

  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, { error: 'Cannot reach the server. Check your connection and try again.' })
  }

  const text = await res.text()
  let data: Record<string, unknown> = {}
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { error: `Unexpected response from the server (${res.status}).` }
    }
  }

  if (!res.ok) {
    if (res.status === 401 && !opts.skipAuthRedirect) onUnauthorized()
    throw new ApiError(res.status, data)
  }
  return data as T
}

/** Downloads an authenticated file (e.g. a CSV export) and saves it under the server's file name. */
export async function download(path: string, params: Record<string, string | number | undefined> = {}, fallbackName = 'download') {
  const url = new URL('/api' + path, window.location.origin)
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') url.searchParams.set(k, String(v))
  }
  const token = tokenStore.get()
  const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!res.ok) {
    let body: Record<string, unknown> = {}
    try {
      body = await res.json()
    } catch {
      /* not JSON */
    }
    if (res.status === 401) onUnauthorized()
    throw new ApiError(res.status, body)
  }
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  const name = (encoded && decodeURIComponent(encoded)) ?? /filename="([^"]+)"/.exec(disposition)?.[1] ?? fallbackName
  const href = URL.createObjectURL(await res.blob())
  const a = document.createElement('a')
  a.href = href
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(href)
}

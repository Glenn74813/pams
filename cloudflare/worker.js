// PAMS on Cloudflare Workers: serves the built React app and forwards /api/* to the PAMS backend,
// which is published through a Cloudflare Tunnel at PAMS_API_ORIGIN (e.g. https://pams-api.example.gov.ph).
// Every forwarded call carries PAMS_PROXY_SECRET, which the backend checks (PAMS_PROXY_SHARED_SECRET),
// so the tunnel address cannot be used directly, and the caller's address for login limits and the audit trail.

const SECRET_HEADER = 'X-PAMS-Proxy-Secret'
const CLIENT_IP_HEADER = 'X-PAMS-Client-IP'

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    if (!url.pathname.startsWith('/api/')) return env.ASSETS.fetch(request)

    if (!env.PAMS_API_ORIGIN || !env.PAMS_PROXY_SECRET) {
      return Response.json({ message: 'PAMS is not connected to its server yet. Set PAMS_API_ORIGIN and PAMS_PROXY_SECRET on the Worker.' }, { status: 503 })
    }

    const target = new URL(url.pathname + url.search, env.PAMS_API_ORIGIN)
    const headers = new Headers(request.headers)
    // Never pass on what a browser sent in these headers.
    headers.delete(SECRET_HEADER)
    headers.delete(CLIENT_IP_HEADER)
    headers.set(SECRET_HEADER, env.PAMS_PROXY_SECRET)
    const ip = request.headers.get('CF-Connecting-IP')
    if (ip) headers.set(CLIENT_IP_HEADER, ip)

    const hasBody = request.method !== 'GET' && request.method !== 'HEAD'
    try {
      // Redirects (single sign-on) go back to the browser as they are.
      return await fetch(target, { method: request.method, headers, body: hasBody ? request.body : null, redirect: 'manual' })
    } catch {
      return Response.json({ message: 'The PAMS server could not be reached. Check that it and its tunnel are running.' }, { status: 502 })
    }
  },
}

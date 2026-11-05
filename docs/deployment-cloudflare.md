# Publishing PAMS through Cloudflare

How to make PAMS reachable on the internet through Cloudflare, with the server itself staying on the agency's network. To install the server see [deployment-windows.md](deployment-windows.md).

## How it fits together

```
Browser ──► Cloudflare Worker "pams" ──► Cloudflare Tunnel ──► PAMS server (port 8095) ──► database
            serves the React app          no open ports;          checks the Worker's
            and forwards /api/*           cloudflared runs         secret
                                          on the server
```

- The **Worker** (`wrangler.jsonc`, `cloudflare/worker.js`) is built and deployed by Cloudflare from the GitHub repository. It serves the web app and forwards every `/api/...` call to the server.
- The **Tunnel** publishes the server at an address of its own, such as `pams-api.example.gov.ph`, without opening any port on the agency's firewall.
- The **secret** is sent by the Worker with every call. The server refuses calls that came through Cloudflare without it, so the tunnel address cannot be used to bypass the Worker. Users on the agency's own network who open the server directly are not affected.

## 1. Deploy the Worker

In Cloudflare, **Workers & Pages → Create → Import a repository**, choose the PAMS repository and branch `main`, then:

| Setting | Value |
|---|---|
| Build command | `cd frontend-react && npm ci && npm run build` |
| Deploy command | `npx wrangler deploy` |
| Root directory | `/` |

Leave **Settings → Build → Variables and secrets** empty, or add only `NODE_VERSION` = `24`. The build needs no passwords or keys; do not copy the server's `.env` into Cloudflare.

Until steps 2 to 4 are done, the site opens but signing in answers "PAMS is not connected to its server yet".

## 2. Create the tunnel

On the PAMS server:

1. In Cloudflare, **Networks → Tunnels → Create a tunnel**, type **Cloudflared**, name it `pams`.
2. Install `cloudflared` on the server with the command Cloudflare shows. On Windows this installs a service that starts with the machine.
3. Under **Public hostname**, add:
   - **Subdomain / Domain:** e.g. `pams-api` on a domain in your Cloudflare account
   - **Service:** `HTTP` and `localhost:8095` (or `HTTPS` and `localhost:443` if the server runs with its own certificate; then turn on **No TLS Verify** under the hostname's TLS settings if the certificate is self-signed)

A domain must be on your Cloudflare account to publish a hostname. The Worker's own `workers.dev` address does not need one.

## 3. Make a secret and give it to both sides

Make a long random value, for example on the server:

```bash
openssl rand -base64 48
```

On Windows PowerShell: `[Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))`.

**On the PAMS server**, add to its `.env` (or `pams.env`) and restart the service:

```
PAMS_PROXY_SHARED_SECRET=<the value>
PAMS_APP_URL=https://pams.<your-subdomain>.workers.dev
```

`PAMS_APP_URL` is the address people use, so QR labels and single sign-on point back to it. Use the custom domain instead if you add one to the Worker.

**On the Worker**, in **Settings → Variables and secrets** (the Worker's runtime settings, not the build settings):

| Type | Name | Value |
|---|---|---|
| Text | `PAMS_API_ORIGIN` | the tunnel address, e.g. `https://pams-api.example.gov.ph` |
| Secret | `PAMS_PROXY_SECRET` | the same value as `PAMS_PROXY_SHARED_SECRET` |

Deploy. `wrangler.jsonc` sets `keep_vars`, so later deploys from GitHub keep these.

## 4. Check it

- Open the Worker's address and sign in.
- Open the tunnel address directly, e.g. `https://pams-api.example.gov.ph/api/settings/public`. It must answer **403**, "This address is only reachable through the PAMS web application".
- In **Audit Trail**, the sign-in shows the user's own address, not `127.0.0.1`.

## Changing the secret

Set the new value on the server and restart it, then change `PAMS_PROXY_SECRET` on the Worker. Between the two steps, signing in through Cloudflare fails.

## When something is wrong

| Message | Cause |
|---|---|
| "PAMS is not connected to its server yet" | `PAMS_API_ORIGIN` or `PAMS_PROXY_SECRET` is missing on the Worker |
| "The PAMS server could not be reached" | The tunnel address is wrong, or the server or `cloudflared` is stopped |
| "This address is only reachable through the PAMS web application" (signing in through the Worker) | The two secrets differ |
| Error 1033 | The tunnel is not connected; check the `cloudflared` service on the server |

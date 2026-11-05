# PAMS configuration manual

What can be configured in PAMS and where. To install PAMS see [deployment-windows.md](deployment-windows.md); for daily running see [operations-manual.md](operations-manual.md).

There are two places to configure PAMS: the `.env` file, read when the service starts, and **Settings** inside the application, which takes effect at once. Changes to `.env` need a service restart.

Keep `.env` readable only by Administrators and the service account. It holds passwords and keys, and it is never committed to source control.

## 1. Settings in the `.env` file

### Database

| Setting | Meaning | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `sqlserver` runs on SQL Server; leave it out for PostgreSQL | PostgreSQL |
| `MSSQL_URL` | SQL Server connection address | `jdbc:sqlserver://127.0.0.1:14330;databaseName=pams;…` |
| `MSSQL_USERNAME`, `MSSQL_PASSWORD` | SQL Server login | `sa`; no default password |
| `DB_URL` | PostgreSQL connection address | `jdbc:postgresql://127.0.0.1:5432/inventory` |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL login | `inventory`; no default password |

PAMS creates and upgrades its tables when it starts, using the migrations packaged with it.

### Server and files

| Setting | Meaning | Default |
|---|---|---|
| `SERVER_PORT` | Port the application listens on | `8095` |
| `SERVER_SSL_*` | The TLS certificate; see section 4 of the deployment guide | Off |
| `PAMS_REQUIRE_HTTPS` | Redirect plain HTTP to HTTPS and send HSTS | See the deployment guide |
| `PAMS_FRONTEND_DIR` | Folder of the built web app, when PAMS serves it | Not set |
| `PAMS_STORAGE_DIR` | Uploaded signatures, logos, documents and photos | `storage/public` |
| `PAMS_BACKUP_DIR` | Where the daily backup is written | Beside the storage folder |
| `PAMS_APP_URL` | The public address, used on QR labels and for links back from single sign-on and signing | `http://localhost:5190` |

### Security

| Setting | Meaning | Default |
|---|---|---|
| `PAMS_JWT_SECRET` | Key for sign-in tokens and QR labels. Set it once: changing it signs everyone out and invalidates printed QR labels | Required |
| `PAMS_AUDIT_SEAL_KEY` | Key for the audit-trail seals. Set it once if the two keys should be separate | The JWT key |
| `PAMS_ADMIN_EMAIL`, `PAMS_ADMIN_PASSWORD` | First administrator on an empty database | Not set |

These are in `application.yml` and rarely change:

| Key | Meaning | Value |
|---|---|---|
| `pams.jwt.ttl-minutes` | How long a sign-in lasts | 60 minutes |
| `pams.security.max-login-attempts` | Failed logins before an account locks | 5 |
| `pams.security.lockout-minutes` | How long the lock lasts | 15 minutes |
| `pams.security.login-requests-per-minute` | Login requests allowed per address | 5 |
| `spring.servlet.multipart.max-file-size` | Largest upload | 25 MB |

### Single sign-on (optional)

Off unless an issuer and client are set. Accounts are matched by email and must already exist in PAMS; the role still comes from PAMS.

| Setting | Meaning |
|---|---|
| `PAMS_SSO_ISSUER` | The provider's issuer address, e.g. `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| `PAMS_SSO_CLIENT_ID`, `PAMS_SSO_CLIENT_SECRET` | The client registered with the provider |
| `PAMS_SSO_REDIRECT_URI` | The callback registered with the provider; defaults to `{PAMS_APP_URL}/api/auth/sso/callback` |
| `PAMS_SSO_BUTTON_LABEL` | Text on the login button |
| `PAMS_SSO_SCOPES` | Scopes requested; defaults to `openid email profile` |

### Publishing through Cloudflare (optional)

See [deployment-cloudflare.md](deployment-cloudflare.md).

| Setting | Meaning | Default |
|---|---|---|
| `PAMS_PROXY_SHARED_SECRET` | The secret the Cloudflare Worker sends with every call. Calls that come through Cloudflare without it are refused; the agency's own network is not affected | Not set (off) |

### Integrations with other systems (optional)

Connections to other systems' APIs are set up in Settings → Integrations, not here. Two settings concern them:

| Setting | Meaning | Default |
|---|---|---|
| `PAMS_INTEGRATIONS_KEY` | Key that encrypts the stored API keys. Changing it makes them unreadable; enter them again | The audit seal key, else `PAMS_JWT_SECRET` |
| `PAMS_INTEGRATIONS_ALLOWED_HOSTS` | Systems on the PAMS server's own machine that connections may call, as comma-separated `host:port`, e.g. `localhost:5174`. Write the host as it appears in the connection's address | None |

### Digital signing platform (optional)

| Setting | Meaning |
|---|---|
| `PAMS_SIGNING_PLATFORM_URL` | Where PAMS posts each new signing request |
| `PAMS_SIGNING_PLATFORM_KEY` | Shared key the platform sends in the `X-Signing-Key` header |

## 2. Settings inside the application

| Where | What | Who |
|---|---|---|
| Settings | Agency name, logo, signatories on forms, system options | `edit_settings` |
| Settings → Locations | Buildings, floors and rooms for assets | `edit_settings` |
| Administration → Roles | Roles and their permissions | Super Admin |
| Administration → Users | Accounts, department and role | Users permission |
| Settings → Integrations | Other systems' APIs whose payment records PAMS shows, read-only | `edit_settings` or `manage_system_settings` |
| Digital Signatures → Trusted CAs | Certificate authorities the agency trusts | `edit_settings` |
| Departments, categories | The libraries used across the system | Their own permissions |
| Monitoring | Scheduled jobs; run one by hand after a failure | `manage_system_settings` |

The in-app help describes each of these pages. See [user-manual.md](user-manual.md).

## 3. Procurement rules

These follow RA 12009, its IRR (first edition, as of 30 March 2026) and GPPB Circular 06-2026. They are in the code, not settings, so they change only with a new release:

| Rule | Where it lives |
|---|---|
| Modes, ceilings | `procurement/ProcurementRules.java` |
| Supplier documents by mode | `procurement/EligibilityRules.java` |
| Negotiated Procurement grounds | `rfq/Rfq.java` |
| Bid security rates, evaluation, timelines | `bidding/BiddingController.java` |
| Unsolicited Offer deadlines | `bidding/UnsolicitedOfferController.java` |
| Grounds for buying common-use items elsewhere | `psdbm/PsDbmController.java` |

Have the BAC or legal officer confirm these before official use, and again when the GPPB issues a new resolution.

## 4. Scheduled jobs

Times are Philippine time, and are listed in section 4 of the [operations manual](operations-manual.md). They are set in `monitoring/AlertJobs.java`.

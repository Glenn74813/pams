# Deploying PAMS on Windows Server with SQL Server

This guide installs PAMS the way the TOR describes it: Windows Server 2019 or later, Microsoft SQL
Server 2019 or later, served over HTTPS. One Windows service runs the application; it serves both the
API and the web app, so there is one port and one certificate to manage.

PAMS also runs on PostgreSQL (the default during development). Everything below is for SQL Server.

## 1. What you need

- Windows Server 2019 or later, 4 CPU cores and 8 GB RAM or more.
- Microsoft SQL Server 2019 or later (Standard, Enterprise, or Express for small offices). The schema
  uses UTF-8 collations, filtered indexes and STRING_AGG, which need 2019 or later.
- Java 21 (64-bit), for example Eclipse Temurin 21 JDK. Set `JAVA_HOME` system-wide.
- A TLS certificate for the address people will use (e.g. `pams.agency.gov.ph`), exported as a
  `.pfx` file **with its private key**.
- WinSW 3 (`WinSW-x64.exe`) to run PAMS as a Windows service.

## 2. Build the release (on a build machine)

```bash
cd frontend-react
npm ci
npm run build                     # writes frontend-react/dist
cd ../backend-java
./mvnw -DskipTests package        # writes target/pams-backend-0.0.1-SNAPSHOT.jar
```

Copy to the server:

| From | To |
|---|---|
| `backend-java/target/pams-backend-0.0.1-SNAPSHOT.jar` | `C:\PAMS\pams-backend.jar` |
| `frontend-react/dist\*` | `C:\PAMS\web\` |
| `backend-java/deploy/windows/pams-service.xml` | `C:\PAMS\pams-service.xml` |
| `backend-java/deploy/windows/pams.env.example` | `C:\PAMS\.env` (then edit it) |
| `WinSW-x64.exe` | `C:\PAMS\pams-service.exe` |
| your certificate | `C:\PAMS\certs\pams.pfx` |

## 3. Prepare SQL Server

Run in SQL Server Management Studio as an administrator:

```sql
CREATE DATABASE pams COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO
CREATE LOGIN pams_app WITH PASSWORD = 'a-strong-password', CHECK_POLICY = ON;
GO
USE pams;
CREATE USER pams_app FOR LOGIN pams_app;
ALTER ROLE db_owner ADD MEMBER pams_app;   -- PAMS creates and upgrades its own tables
GO
```

Enable TCP/IP in SQL Server Configuration Manager and allow port 1433 from the application server
only. Use `encrypt=true` in `MSSQL_URL`; with a self-signed SQL Server certificate add
`trustServerCertificate=true`.

On first start PAMS creates every table (Flyway, `db/migration/sqlserver`) and one administrator:
`superadmin@inventory.com` / `admin123`. **Sign in and change that password straight away.**

## 4. Configure

Edit `C:\PAMS\.env` (see the comments in it):

- `MSSQL_URL`, `MSSQL_USERNAME`, `MSSQL_PASSWORD` — the database above.
- `PAMS_JWT_SECRET` — a long random value. Changing it signs everyone out and invalidates printed QR
  labels, so set it once. It also keys the audit-trail seals unless `PAMS_AUDIT_SEAL_KEY` is set; after a
  change, older entries show as altered in Audit Trail → Check integrity. Set `PAMS_AUDIT_SEAL_KEY` once to
  keep the two apart.
- `SERVER_SSL_*` — the `.pfx` and its password. PAMS then listens on 443, redirects plain HTTP
  requests to HTTPS and sends HSTS.
- `PAMS_FRONTEND_DIR=C:/PAMS/web`, `PAMS_STORAGE_DIR=C:/PAMS/storage/public` (signatures, logos,
  uploaded documents; backups go to `C:\PAMS\storage\backups`).
- `PAMS_APP_URL` — the address on QR labels (can be changed later in Settings → System). Single sign-on
  and the signing platform also send people and files back to this address.
- Single sign-on (optional): `PAMS_SSO_ISSUER` (the provider's issuer URL, e.g.
  `https://login.microsoftonline.com/<tenant-id>/v2.0`), `PAMS_SSO_CLIENT_ID`, `PAMS_SSO_CLIENT_SECRET`, and
  optionally `PAMS_SSO_BUTTON_LABEL`. Register `https://<PAMS_APP_URL host>/api/auth/sso/callback` as the
  redirect URI with the provider (or set `PAMS_SSO_REDIRECT_URI`). Accounts are matched by email, so create
  the users in PAMS first; their role still comes from PAMS.
- Digital signing platform (optional): `PAMS_SIGNING_PLATFORM_KEY` (a long random value shared with the
  platform) and `PAMS_SIGNING_PLATFORM_URL` (where PAMS posts each new signing request). The platform fetches
  the file from `/api/signing/platform/requests/{number}/file` and posts signatures to
  `/api/signing/platform/signatures`, both with the key in the `X-Signing-Key` header. Add the agency's CA
  certificates (PNPKI root and intermediates) under Digital Signatures → Trusted CAs before anyone signs.

Restrict `C:\PAMS\.env` and `C:\PAMS\certs` to Administrators and the service account.

## 5. Install the service

From an administrator PowerShell in `C:\PAMS`:

```powershell
.\pams-service.exe install
.\pams-service.exe start
New-NetFirewallRule -DisplayName "PAMS HTTPS" -Direction Inbound -Protocol TCP -LocalPort 443 -Action Allow
```

Open `https://pams.agency.gov.ph` and sign in. Logs are in `C:\PAMS\pams-service.out.log`.

### Behind IIS instead

If IIS already serves HTTPS on the server, set `SERVER_SSL_ENABLED=false`, `SERVER_PORT=8095`,
`PAMS_REQUIRE_HTTPS=false`, bind the service to localhost only (firewall), and add an IIS site with
URL Rewrite + Application Request Routing that proxies everything to `http://localhost:8095`
(preserve the host header; IIS adds `X-Forwarded-*`, which PAMS honours).

## 6. Backups

PAMS runs a database backup every day at 02:00 (Monitoring → Scheduled alerts lists it and can run
it on demand). On SQL Server it issues `BACKUP DATABASE … TO DISK` into
`C:\PAMS\storage\backups`, keeping 7 days; the SQL Server service account needs write access to that
folder (or point `PAMS_BACKUP_DIR` at a folder it can write). Copy backups off the server as well,
or schedule SQL Server Agent full/log backups to your backup system.

Back up `C:\PAMS\storage\public` and `C:\PAMS\storage\signing` with the database: they hold the
signature images, logos, uploaded documents, asset photos and documents sent for signing that the
database refers to. To restore, and for routine checks, see the [operations manual](operations-manual.md).

## 7. Upgrading

Stop the service, replace `pams-backend.jar` and `C:\PAMS\web`, start the service. Database changes
are applied automatically on start.

## 8. Moving data from an existing PostgreSQL installation

Use Settings → Data import/export: export the full ZIP from the old server, then import items,
suppliers, departments and users into the new one. For a complete move including transactions,
restore the PostgreSQL database alongside and copy table by table with SQL Server's Import and
Export Wizard (PostgreSQL ODBC source), in the order of the foreign keys, with identity insert on.

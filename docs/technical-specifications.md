# PAMS technical specifications

## 1. Overview

PAMS (Procurement and Asset Management System) covers procurement planning, purchase requests, every procurement mode under RA 12009, purchase orders and contracts, receiving and inspection, supplies and property accountability, physical counts, disposal, and the COA and GPPB reports.

It is one application: a Spring Boot service that exposes a REST API and can serve the React web app from the same port.

```
Browser (React)  ──HTTPS / JSON──▶  Spring Boot service  ──JDBC──▶  SQL Server or PostgreSQL
                                          │
                                          ├── File storage: documents, photos, backups
                                          ├── OpenID Connect provider (optional)
                                          └── Signing platform (optional)
```

## 2. Technology

| Layer | Technology |
|---|---|
| Language and runtime | Java 21 |
| Application framework | Spring Boot 4.1 (web, security, data JPA, scheduling) |
| Data access | Hibernate and JdbcTemplate |
| Schema migrations | Flyway, with one folder per database |
| Databases | Microsoft SQL Server 2019 or later; PostgreSQL |
| Authentication | Signed bearer tokens (JWT), passwords hashed with BCrypt, optional OpenID Connect |
| Excel and PDF | Workbook builder for the COA forms; OpenPDF draws the PDF from the same form |
| Web app | React 19, TypeScript, Vite, React Router, TanStack Query |
| Server hosting | Windows Server 2019 or later as a Windows service (WinSW) |
| Minimum server | 4 CPU cores, 8 GB RAM |

Exact versions are in `backend-java/pom.xml` and `frontend-react/package.json`.

## 3. Source layout

| Path | Contents |
|---|---|
| `backend-java/src/main/java/com/avtech/pams/` | One package per module |
| `backend-java/src/main/resources/db/migration/postgresql`, `/sqlserver` | Schema migrations for each database |
| `backend-java/tools/` | Demo seed, API reference generator, schema conversion |
| `frontend-react/src/pages/` | One folder per page group |
| `frontend-react/src/api/` | Typed API client |
| `frontend-react/src/pages/help/articles/` | Help centre content, also the source of the user's manual |
| `docs/` | Manuals and references |

## 4. Modules

| Area | Backend packages |
|---|---|
| Planning and requests | `procurement` (PPMP, APP, purchase requests) |
| Procurement | `psdbm`, `bidding` (Competitive Bidding, Unsolicited Offers), `rfq` (alternative modes), `bac`, `supplier` |
| Award and contract | `purchasing`, `contract`, `payment` |
| Supplies | `inventory`, `issuance`, `adjustment`, `semiexpendable`, `physicalcount` |
| Property | `property`, `maintenance`, `location`, `clearance`, `gatepass`, `disposal`, `qr` |
| Reports and analysis | `report`, `analytics`, `monitoring` |
| Records | `document`, `signing`, `dataio`, `audit` |
| Administration | `auth`, `user`, `role`, `department`, `setting`, `notification` |
| Shared | `common`, `config` |

## 5. Data

- 87 tables on a fully migrated database, including the migration history.
- The schema is validated against the code at startup, and every change is a numbered migration present for both databases.
- SQL Server text columns are `nvarchar`, so names with ñ and the peso sign keep their characters.
- Times are stored as Philippine time.
- Documents are soft-deleted where they have a deletion date, so references stay intact.

## 6. Security

- **Access control:** 15 seeded roles, plus custom roles. Each endpoint states the permissions it needs, and the menu and page buttons follow the same permissions.
- **Separation of duties:** the person who prepares an RFQ or bidding cannot award it, except a Super Admin.
- **Sign-in:** bearer tokens that last 60 minutes, revocable on sign-out. Accounts lock for 15 minutes after 5 failed attempts, and login requests are limited to 5 a minute per address.
- **Transport:** HTTPS with HSTS and a redirect from plain HTTP when a certificate is configured.
- **Audit trail:** each change records who, when, and the values before and after. Entries are sealed so that tampering can be detected (Audit Trail → Check integrity).
- **Digital signatures:** checked against the exact file, the signer's certificate and the trusted certificate authorities.
- **Secrets:** held in `.env`, outside source control.

## 7. Interfaces

| Interface | Details |
|---|---|
| REST API | JSON with snake_case fields; 485 endpoints listed in [api-reference.md](api-reference.md) |
| Report output | Excel, CSV and PDF |
| Bulk data | Import and full export through the data tools |
| Single sign-on | OpenID Connect (Microsoft Entra ID, Google Workspace, Keycloak and others) |
| Signing platform | Push of new requests; signatures posted back with a shared key |
| QR labels | Signed links on asset tags, scanned with a phone during counts |

PAMS has no live connection to PhilGEPS, the PS-DBM Virtual Store or the DBM BTMS. Staff record those transactions and references in PAMS.

## 8. Availability and backup

- The service restarts itself after a crash.
- Scheduled jobs run the daily backup, keep 7 days, and send alerts. A failed job notifies administrators once a day.
- Backup, restore and incident response are in the [operations manual](operations-manual.md).

## 9. Testing

The project has automated API checks that run against both databases. They are not yet part of the repository: they were run from a working folder during development, so a maintainer should bring them in and add them to the build.

## 10. Related documents

| Document | Purpose |
|---|---|
| [user-manual.md](user-manual.md) | Using each page |
| [configuration-manual.md](configuration-manual.md) | Settings and where they live |
| [operations-manual.md](operations-manual.md) | Running the system day to day |
| [deployment-windows.md](deployment-windows.md) | Installing on Windows Server with SQL Server |
| [api-reference.md](api-reference.md) | Every API endpoint |

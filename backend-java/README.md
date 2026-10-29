# PAMS backend (Spring Boot)

Java 21 · Spring Boot 4 · PostgreSQL · Flyway. Replaces the Laravel API in `../Backend`, one module at a time.

## Run

```bash
./mvnw spring-boot:run
```

Listens on **8095** (`SERVER_PORT` overrides it). Settings come from `backend-java/.env` (gitignored):

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `PAMS_JWT_SECRET` | base64, at least 256 bits (`openssl rand -base64 48`) |
| `PAMS_STORAGE_DIR` | public file storage; points at Laravel's while both run |
| `PAMS_ADMIN_EMAIL`, `PAMS_ADMIN_PASSWORD` | only used on an empty database, to create the first Super Admin |

## Database

The schema was built by Laravel. `V1__baseline.sql` is a snapshot of it: on the existing database Flyway records it as
already applied, on a fresh database it creates everything. From here on, **every schema change is a new Flyway
migration** in `src/main/resources/db/migration` — don't add Laravel migrations any more.

Both backends can run against the same database during the move: the Java side reads and writes Laravel's bcrypt
hashes, Manila-time timestamps and table layout unchanged.

## Ported so far

| Module | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `logout`, `refresh`, `GET me`, `change-password`, `signature`, `2fa/*` |
| Users | `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}`, `all`, `roles`, `departments` |
| Roles | `GET/POST /api/roles`, `GET/PUT/DELETE /api/roles/{id}`, `permissions-list` |
| Categories | `GET/POST /api/inventory/categories`, `PUT/DELETE /api/inventory/categories/{id}` (create/edit/delete is new) |
| Items | `GET/POST /api/inventory/items`, `GET/PUT/DELETE /api/inventory/items/{id}`, `items/options`, `items/available`, `items/{id}/lots`, `config`, `locations` |
| Stock | `POST /api/inventory/items/{id}/stock`, `GET stock-entries`, `POST transfer-stock`, `GET stock-transfers`, `GET expiring` |
| Stock card | `GET /api/reports/stock-card/{itemId}/preview` |
| Suppliers | `GET /api/suppliers/options` (names only; the module itself is still in Laravel) |
| Requests / issuance | `GET/POST /api/requests`, `GET/PUT/DELETE /api/requests/{id}`, `PUT {id}/approve-items`, `PUT {id}/approve`, `PUT {id}/reject` |
| Stock adjustments | `GET/POST /api/stock-adjustments`, `GET/DELETE {id}`, `POST {id}/approve`, `POST {id}/reject` |
| Semi-expendable | `GET /api/semi-expendable`, `POST /api/semi-expendable/{id}/return` |
| Assets | `GET/POST /api/properties`, `GET/PUT/DELETE /api/properties/{id}`, `POST {id}/transfer`, `POST {id}/return`, `POST {id}/issue`, `POST {id}/status`, `GET {id}/transfers`, `GET {id}/depreciation`, `GET my-assets`, `GET transfers/all` |
| Depreciation | `POST /api/depreciation/run` (posts by calendar month; safe to repeat) |
| Disposal | `GET/POST /api/disposal/requests`, `GET/PUT/DELETE {id}`, `POST {id}/submit`, `approve`, `reject`, `complete` |
| Gate pass | `GET/POST /api/gate-pass`, `GET/PUT {id}`, `POST {id}/return`, `GET property/{propertyId}` |
| Procurement planning | `GET /api/procurement-planning/options`, `GET/POST ppmps`, `GET/PUT/DELETE ppmps/{id}`, `POST ppmps/{id}/submit`, `review`, `approve`, `return`, `GET apps`, `GET/PUT/DELETE apps/{id}`, `POST apps/generate`, `POST apps/{id}/approve`, `GET budget-summary` |
| Purchase requests | `GET/POST /api/purchase-requests`, `GET/PUT/DELETE {id}`, `POST {id}/submit`, `cancel`, `approve-step`, `return`, `certify-funds` |
| RFQ / alternative modes | `GET/POST /api/rfq`, `GET purchase-requests`, `GET/PUT/DELETE {id}`, `GET {id}/abstract`, `POST {id}/issue`, `close`, `cancel`, `award`, `noa`, `ntp`, `POST {id}/quotations`, `PUT/DELETE quotations/{id}` |
| Competitive bidding | `GET/POST /api/bidding`, `GET purchase-requests`, `GET/PUT/DELETE {id}`, `PUT {id}/criteria`, `POST {id}/pre-proc`, `invitation`, `open-bids`, `bidders`, `declare-failed`, `cancel`, `award`, `noa`, `ntp`, `protests`, `observers`, `PUT/DELETE bidders/{id}`, `PUT bidders/{id}/scores`, `PUT protests/{id}`, `PUT/DELETE observers/{id}` |
| BAC | `GET/POST /api/bac/members`, `PUT/DELETE members/{id}`, `GET/POST meetings`, `GET/PUT/DELETE meetings/{id}`, `POST meetings/{id}/attendance`, `GET/POST resolutions`, `GET/PUT/DELETE resolutions/{id}`, `POST resolutions/{id}/submit`, `return`, `approve`, `archive` |

Same paths and snake_case JSON as Laravel. Security and integrity changes made on the way: tokens are re-checked
against the user on every request (deleted users are locked out at once), logout and refresh revoke the old token,
one password policy everywhere, Super Admin powers checked by what a role grants rather than its name, no self role
changes, inventory reads need an inventory/warehouse permission, stock changes lock the item row, users with recorded
stock movements can't be deleted (that would erase them from stock cards), and items can't be deleted while stock is on
hand. User, role, category, item, stock and transfer changes are written to `audit_trails` in Laravel's format.

Issuance assigns RIS numbers (plus ICS for semi-expendable and PAR for PPE) when stock goes out; both "approve all" and
per-line approval issue first-expired-first-out and write the stock card. Requesters can't approve their own requests and
adjustment makers can't approve their own adjustments. Approved adjustments move real lots (Laravel only changed the
on-hand number). All stock changes go through `StockLedger`, which keeps lots, on-hand and weighted average cost in step.

Assets: every change of hands gets a document number that is never reused — registration/issue a PAR (PPE) or ICS
(semi-expendable), a transfer a PTR, a return a PRS / RRSP — and is kept in `property_transfers`. Assets must be returned
before they can be retired or proposed for disposal; one open gate pass and one open disposal per asset; assets with
history can't be deleted (only a mistaken registration can be undone, which puts the unit back in its lot).

Procurement planning: one APP per fiscal year; consolidating again adds PPMPs approved since, until the APP is approved.
Approved PPMPs and APPs are locked. Preparers can't review or approve their own PPMP, nor the consolidator the APP.
Department planners (create_ppmp without agency-wide rights) only see and edit their own department's PPMPs. APP
utilization is worked out from the purchase requests drawn on each line (the manual utilization endpoint is gone).
Purchase requests: lines drawn on the APP must come from the approved APP the PR is linked to and stay within the
line's remaining quantity and budget (drafts count); method ceilings apply to PR lines too; a requester can't sign or
certify funds on their own PR; the Budget Officer step needs a CAF covering the PR total (a new CAF number is issued if
the total later grows); only draft/returned PRs can be edited. V9 gives the PR signatory permissions to the Department
Head, Budget Officer, Accountant and BAC Secretariat roles, and lets End Users raise PRs. Not ported yet: attaching
the signed CAF or other files to a PR (needs the Documents module).

RFQ: an RFQ draws its lines from an approved PR, within each line's quantity (across all live RFQs) and estimated
cost, under the mode the PR planned; single-source methods need a justification and take one quotation; method
ceilings apply to the ABC. Quotations must price every line and arrive by the deadline; the RFQ closes only after the
deadline. The award (process_award, not the RFQ's preparer) goes to the lowest responsive, eligible quotation within the
ABC, cites an approved BAC award resolution for the same PR (one award per resolution), and needs a justification
with fewer than 3 quotations. NOA and NTP numbers are generated. Laravel's blacklist check read a column that does not
exist; eligibility now uses suppliers.is_active / is_eligible.
BAC: composition is checked against RA 12009 (chair, vice-chair, 5+ voting members, secretariat); meetings record
attendance and quorum (a majority of seated voting members); a resolution is approved by someone other than its
drafter, only after submission, and only if the meeting it cites was held with a quorum.
Competitive bidding: lines come from an approved PR, planned for bidding, within quantity and estimated cost; a PR
line is out on one RFQ or bidding at a time (failed and cancelled ones free it). ABC over ₱2M needs a pre-procurement
conference; MEARB needs rated criteria totalling 100 before the Invitation to Bid. Bids are opened only after the
submission deadline. Eligibility needs a sufficient bid security (2% cash/check/draft/LC, 5% surety bond, or a Bid
Securing Declaration); stages run in order; a bid over the ABC fails financial. Responsive bids are ranked (lowest
price, or MEARB price + rated score) and the top one is post-qualified, moving down only when it fails — Laravel
ranked only bidders that had already passed post-qualification. The award goes to the post-qualified bidder, on an
approved BAC award resolution, with no protest pending; NOA/NTP numbers are generated and share the RFQ series. A
second failed bidding for a PR flags Negotiated Procurement. The MaxPETD timeline is shown per bidding. The separate
BAC evaluations log (bac_evaluations) is not ported: each bidder's stage results are the evaluation record.

Not ported yet: QR codes, Clone-PC assembly parts, receiving stock straight from an IAR line, the RSMI report and the Excel versions of the stock
card, RIS, ICS and PAR (the React app prints RIS/ICS/PAR from the browser).

Everything else is still served by `../Backend`.

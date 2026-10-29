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
| Purchase orders | `GET /api/procurement/award-sources`, `GET/POST /api/procurement/purchase-orders`, `GET/PUT/DELETE {id}`, `POST {id}/send`, `confirm`, `cancel` |
| Receiving | `GET/POST /api/receiving/inspection-reports`, `GET/PUT/DELETE {id}`, `POST {id}/accept`, `reject` |
| Contracts | `GET/POST /api/contracts`, `GET awarded-sources`, `GET/PUT/DELETE {id}`, `POST {id}/activate`, `complete`, `terminate`, `securities`, `amendments`, `extensions`, `calloffs`, `PUT {id}/liquidated-damages`, `PUT/DELETE securities/{id}`, `POST amendments/{id}/approve`, `DELETE amendments/{id}`, `PUT extensions/{id}/approve`, `deny`, `DELETE extensions/{id}`, `PUT calloffs/{id}` |
| Suppliers | `GET /api/suppliers/options`, `GET/POST /api/suppliers`, `GET/PUT/DELETE {id}`, `POST {id}/eligibility`, `compliance`, `blacklist`, `evaluations`, `PUT/DELETE compliance/{id}`, `PUT blacklist/{id}/lift`, `DELETE evaluations/{id}` |
| Physical count | `GET/POST /api/physical-inventory/counts`, `GET options`, `GET/PUT/DELETE {id}`, `POST {id}/start`, `record`, `scan`, `complete`, `return`, `verify`, `POST/DELETE {id}/properties/{lineId}` |
| Settings | `GET/PUT /api/settings`, `GET/PUT settings/mis`, `GET/PUT settings/signatories`, `GET settings/signatory/{role}`, `GET/POST settings/document-logos`, `PUT/DELETE settings/document-logos/{id}` |
| Audit trail | `GET /api/audit`, `GET {id}`, `GET filters`, `GET export` (CSV), `POST events` (prints/downloads) |
| Log trails | `GET /api/logs/inventory`, `GET inventory/actions`, `GET login-attempts`, `GET active-users` |
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
Purchase orders are issued only on an award with its Notice of Award (Laravel allowed free-form POs and gave bidding
POs no lines): RFQ POs take the winning quotation's lines and prices; bidding POs take the bidding lines priced from
the bid form, totalling the awarded bid (never above; below only by centavo rounding). One live PO per award; creating
needs edit_procurement (End Users hold create_procurement for PRs). The preparer cannot approve/send the PO; edits are
for drafts only. V11 adds PO line units, award-line links and created_by.
Receiving: the inspection committee records each delivery on an IAR against PO lines (accepted + rejected = received,
rejections need a reason, never more than is outstanding). Someone other than the inspector then accepts it, which
receives the accepted quantities into stock as new lots at the PO price — supplier, IAR, lot no., expiry, location —
with a stock card entry and the COA classification check; Laravel never put accepted deliveries into stock. A rejected
delivery reopens the PO quantities. PO status follows deliveries (partially received / received). Not ported: the
Excel IAR export (the React app prints Appendix 61 PO and Appendix 62 IAR).
Contracts are drawn up only on an award with its Notice of Award, at the awarded price, linked to the award's PO; one
live contract per award. A contract takes effect (activated by someone other than its preparer) once signed, dated and
secured by a performance security of the required amount: 5% of the price in cash, bank draft/guarantee or letter of
credit (10% for infrastructure), 30% as a surety bond, valid to the end date. Only description and remarks change while in
force; amendments move price and end date, approved by someone other than their drafter, within 10% of the original price
in total. Extension requests must come before the deadline and are granted up to the days asked. Liquidated damages are
worked out from the PO deliveries: 1/10 of 1% per day of the value not delivered by the deadline (PO delivery date, or
NTP + delivery days, or end date, plus extensions), capped at 10% of the price, which flags termination. A contract
completes only when the PO is fully delivered; performance securities are returned only after completion; forfeiture
and termination need a ground. Framework-agreement call-offs stay within the agreement amount. Not ported: extension
request letters (file upload) and e-signatures.
Suppliers: unique name, vendor code (VND-NNNN, assigned) and email; TIN checked. A supplier is eligible for award
only once marked so, which needs PhilGEPS, DTI or SEC, Mayor's permit, BIR registration and tax clearance on file and
valid, and no blacklist or suspension in force; removing or changing a document that breaks this drops eligibility,
deactivation clears it, and a sanction makes the supplier ineligible at once (lifting it needs a reason and a fresh
verification). Suppliers first named on a quotation or bid are registered unverified, so they cannot be awarded,
post-qualified or put on a PO until verified (Laravel registered them eligible). Award, PO and contract checks all
include sanctions. Ratings are 1-5 by people who rate deliveries, one per evaluator per engagement, withdrawable only
by the evaluator; performance also comes from the record: awards, POs, contracts, on-time deliveries (IAR delivery
date vs PO date), units rejected and liquidated damages. Suppliers with history cannot be deleted. Not ported:
scanned copies of compliance documents (needs the Documents module).
Physical count: a count is of stock-room supplies (RPCI, optionally one category and its subcategories), PPE (RPCPPE)
or semi-expendable property (RPCSP), optionally one department's holders. Starting it freezes the book: one line per
item at its on-hand quantity and average cost, or one line per tag on the asset masterlist with holder, department,
book value and condition. Laravel instead read the book whenever a line was typed in, then on verification set
on-hand to the counted figure (clamped at zero) through self-approved adjustments that never moved the stock lots.
Here the variance against the cut-off is posted, so receipts and issues made during the count stand, and each line
shows its movement since the cut-off. Verification creates approved stock adjustments that move real lots (overages
on items with no cost history need a unit cost); a shortage larger than what is on hand now is refused rather than
clamped. The verifier cannot be the person who set up, closed or took part in the count (super admin exempt); a
verifier can send it back for recount with a reason. Asset counts go tag by tag: found (with the condition seen,
written to the asset on verification), missing (any tag left unchecked when the count closes), found but not in the
masterlist (description required), or on record under another department. Missing assets stay on record for
follow-up. Count numbers are PIC-YYYY-MM-NNNN.
Settings, audit and logs: every settings change (system, agency profile, signatories, logos) is audited with only
the keys that changed; Laravel audited none. System settings add an idle sign-out limit (0 = off, else 5-480 minutes,
enforced by the client across tabs and recorded as a sign-out with the reason) and a minimum password length (8-64).
The password rule also refuses commonly breached passwords and passwords containing the person's email name. Sign-out,
password changes, 2FA on/off and signature changes are audited. Every authenticated request stamps
users.last_seen_at (at most once a minute; cleared on sign-out), which drives "Online now". The audit trail exports
CSV (Excel-readable, formula injection neutralised, the export itself audited) and prints from the browser; any print
(button or browser menu) is recorded through POST /api/audit/events. Settings is open to everyone for their own
account; the admin areas check view_settings / edit_settings / manage_* permissions. Not ported: Excel (.xlsx)
export of the audit trail, and a QR code for 2FA setup (the setup key and an otpauth link are shown instead).

COA forms (report/coa): the 15 Laravel PhpSpreadsheet exports are ported line for line onto `Xlsx`, a small POI
wrapper that keeps PhpSpreadsheet's semantics (A1 references, range styles accumulated per cell, range borders by
edge, one-cell merges ignored), so the files keep the prescribed layout: RSMI (App. 64), Stock Card (58), RPCI (66),
RPCSP (68), RPCPPE (73), Property Card (69), RIS (63), ICS (59), PAR (71), IAR (62), PTR (76), WMR (65), IIRUP (74),
RLSDDP (75) and the Abstract of Bids, with configured signatories' signatures stamped as before. They are served at
the Laravel URLs and file names (CoaExportController); entity name and fund cluster default to the agency profile,
and every download is audited. WMR/IIRUP/RLSDDP accept GET or POST with query/form parameters.
Reports (/api/reports): inventory report, request logs, RSMI summaries (stored in rsmi_reports as
RSMI-YYYY-MM-NNN), previews of the physical-count, property-card and disposal forms, reconciliation and aging.
Procurement reports (/api/reports/procurement): APP, PMR, PMR in the GPPB format, BAC and Award, with the same Excel
export layout as Laravel; the GPPB PMR now fills the contract-signing date (contract) and acceptance date (latest
accepted IAR), which Laravel left blank.
APCPI (/api/apcpi): one assessment per fiscal year, 16 indicators rated 0-3, pillar averages and maturity band as
before; each score change is audited, finalizing needs every 0 to be explained in its remarks, and indicators the
system can measure (BAC composition, APP approval, award time, competition, LDs, observers, protests) show a suggested
rating with its basis.
Documents (/api/documents): repository with version history; files go to storage/public/documents/{id}, up to 20 MB
(PDF, Word, Excel, PNG, JPG). Each version stores a SHA-256 checksum (V14); an identical re-upload is refused,
downloads report whether the file still matches (X-Checksum-Verified) and are audited. Documents can be attached to a
PR, PPMP, APP, PO, IAR, contract, bidding, RFQ, BAC resolution, disposal, supplier or asset. Delete is soft.
Dashboard (/api/analytics/dashboard): what is waiting on the caller plus inventory, asset, procurement and supplier
figures, each only for roles that cover the area. Monitoring (/api/monitoring): stock levels, a re-order report with a
suggested quantity (top up to max, or twice the minimum) from 90-day usage, fast/slow/non-moving items, request
trends, and a procurement watch (bidding stages against the RA 12009 maximum periods, quotation deadlines, PO
deliveries, contract deliveries accruing LDs, contract/security/supplier-document expiry, APP lines due without a PR).
Scheduled jobs (monitoring/AlertJobs, Asia/Manila; each can also be run from Monitoring): reorder, expiry, procurement
deadline, contract and supplier-document alerts (daily, with cooldowns), the quarterly APP reminder, monthly
depreciation, weekly clean-up of read notifications, and a daily pg_dump backup to storage/backups kept 7 days.
Notifications (/api/notifications): the bell — list, unread count, mark read.
QR codes (qr): ZXing codes with high error correction that link to the app's /scan/{type}/{id} page with an HMAC
checksum (pams.app-url sets the host). Labels and sticker sheets for items and assets, gate-pass codes, an in-app
scan lookup (QR link, item code, tag, serial or gate-pass number) and a public read-only lookup that refuses a
missing or altered checksum. Laravel's QR links pointed at its own web pages and are not honoured here.

Not ported: Clone-PC assembly parts.

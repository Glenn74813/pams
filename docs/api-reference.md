# PAMS API reference

*Generated from the controllers by `backend-java/tools/gen_api_reference.py`; do not edit by hand. Run the script after changing an endpoint.*

PAMS is a JSON API over HTTPS, served from the same address as the web app (for example `https://pams.agency.gov.ph/api/...`).
During development the backend listens on `http://localhost:8095`.

## Signing in

Send the email and password to `POST /api/auth/login`:

```json
{ "email": "user@agency.gov.ph", "password": "...", "otp": "123456" }
```

`otp` is needed only for accounts with two-factor authentication; without it the answer is `{ "two_factor_required": true }`. A successful answer holds
`access_token`, `token_type`, `expires_in` (seconds) and `user`. Send the token on every other request:

```
Authorization: Bearer <access_token>
```

Tokens last 60 minutes. `POST /api/auth/refresh` gives a new one and cancels the old; `POST /api/auth/logout` cancels it. Failed logins lock the account for
15 minutes after 5 tries, and each address may try 5 times a minute.

## Conventions

- **Names** are `snake_case` in JSON and query strings.
- **Dates** are `YYYY-MM-DD`; timestamps are ISO 8601 with the Manila offset (`2026-10-30T14:45:11+08:00`).
- **Pages:** lists that page answer `{ "data": [...], "pagination": { "total", "per_page", "current_page", "last_page" } }` and take `page` (from 1).
- **Files:** uploads are `multipart/form-data`. A download answers with the file and a `Content-Disposition` naming it.
- **COA forms as PDF:** add `format=pdf` to any form's Excel download. List reports take `format=xlsx`, `csv` or `pdf`.

## Errors

| Status | Meaning | Body |
|---|---|---|
| 400 | Malformed request | `{ "error": "..." }` |
| 401 | No token, or it is expired or cancelled | `{ "status": "..." }` |
| 403 | Signed in, but not allowed | `{ "error": "..." }`; an expired password also sets `password_expired` |
| 404 | No such record or route | `{ "error": "..." }` |
| 409 | The record's state does not allow this | `{ "error": "..." }` |
| 422 | Invalid input | `{ "message": "Validation failed", "errors": { "field": ["..."] } }` |
| 429 | Too many login attempts | `{ "error": "...", "locked_until": "..." }` |

## Permissions

**Requires** below lists the permissions, any one of which opens the endpoint; Super Admin passes every check. Where it says *signed in*, any account may call it,
but the handler may still narrow what it returns (your own records, for example) or answer 403 for a record you may not touch. Roles and their permissions are
managed under Users & Roles.

## Single sign-on

With an OpenID Connect provider configured, send the browser to `GET /api/auth/sso/start`. The provider returns it to `GET /api/auth/sso/callback`, which redirects to
`/login?sso_code=...` (or `/login?sso_error=...`). Swap the one-time code for the usual token with `POST /api/auth/sso/exchange` within 60 seconds.

## Digital signing platform

An external platform is told about each new signing request by a `POST` to `PAMS_SIGNING_PLATFORM_URL`, with the header `X-Signing-Key` and this body:

```json
{ "request_number": "SIG-2026-0001", "title": "...", "sha256": "...", "signer_email": "...", "file_url": "...", "callback_url": "..." }
```

It fetches `file_url` and posts the detached signature over the file's exact bytes to `callback_url`, both with the same header. Accepted algorithms: `RSA-SHA256`, `RSA-SHA384`, `RSA-SHA512`,
`ECDSA-SHA256`, `ECDSA-SHA384`, `ECDSA-SHA512`. The signature must be Base64 and the certificate PEM, followed by any intermediates, issued by a CA under Digital Signatures → Trusted CAs.


## Endpoints

469 endpoints in 49 groups.

- [Adjustment](#adjustment)
- [Analytics](#analytics)
- [Apcpi](#apcpi)
- [Assembly](#assembly)
- [Asset Media](#asset-media)
- [Audit](#audit)
- [Auth](#auth)
- [Bac](#bac)
- [Bidding](#bidding)
- [Category](#category)
- [Clearance](#clearance)
- [Coa Export](#coa-export)
- [Contract](#contract)
- [Data Export](#data-export)
- [Data Import](#data-import)
- [Department](#department)
- [Disposal](#disposal)
- [Document](#document)
- [Gate Pass](#gate-pass)
- [Inventory](#inventory)
- [Issuance](#issuance)
- [Location](#location)
- [Log Trail](#log-trail)
- [Maintenance](#maintenance)
- [Monitoring](#monitoring)
- [Notification](#notification)
- [Payment](#payment)
- [Petty Cash](#petty-cash)
- [Physical Count](#physical-count)
- [Planning](#planning)
- [Planning Report](#planning-report)
- [Procurement Report](#procurement-report)
- [Property](#property)
- [Ps Dbm](#ps-dbm)
- [Purchase Order](#purchase-order)
- [Purchase Request](#purchase-request)
- [Qr](#qr)
- [Receiving](#receiving)
- [Report](#report)
- [Rfq](#rfq)
- [Role](#role)
- [Semi Expendable](#semi-expendable)
- [Settings](#settings)
- [Signing](#signing)
- [Sso](#sso)
- [Stock Card](#stock-card)
- [Supplier](#supplier)
- [Tor Report](#tor-report)
- [User](#user)

### Adjustment

Stock adjustments (count corrections, damage, found stock) with maker/approver separation: the person who asks for an adjustment cannot approve it. Approval moves real stock lots, so on-hand, lots, weighted average cost and the stock card stay consistent (the Laravel version only changed the on-hand number).

**`GET /api/stock-adjustments`**

- Requires: `create_adjustments`, `approve_adjustments`
- Query: `page` (optional) default `1` integer, `status` (optional), `adjustment_type` (optional), `item_id` (optional) integer
- Returns: a page of [`AdjustmentView`](#adjustmentview)

**`POST /api/stock-adjustments`**

- Requires: `create_adjustments`
- Body: `item_id` integer, `adjustment_type` string, `quantity` integer, `unit_cost` number, `reason` string, `remarks` string
- Returns: [`AdjustmentView`](#adjustmentview)

**`DELETE /api/stock-adjustments/{id}`**

- Requires: `create_adjustments`
- Returns: a JSON object

**`GET /api/stock-adjustments/{id}`**

- Requires: `create_adjustments`, `approve_adjustments`
- Returns: [`AdjustmentView`](#adjustmentview)

**`POST /api/stock-adjustments/{id}/approve`**

- Requires: `approve_adjustments`
- Returns: [`AdjustmentView`](#adjustmentview)

**`POST /api/stock-adjustments/{id}/reject`**

- Requires: `approve_adjustments`
- Body: `remarks` string
- Returns: [`AdjustmentView`](#adjustmentview)

### Analytics

The dashboard: what is waiting on the caller, then inventory, asset, procurement and supplier figures — each section only for people whose role covers that area.

**`GET /api/analytics/dashboard`**

- Requires: *signed in*
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

### Apcpi

APCPI — the GPPB Agency Procurement Compliance and Performance Indicators self-assessment. One assessment per fiscal year; each indicator is rated 0–3 and rolled up into pillar averages and a maturity band. Where the system holds the evidence, a suggested rating is computed from it.

**`GET /api/apcpi`**

- Requires: `view_procurement`, `approve_procurement`, `manage_bac_meetings`, `view_reports`
- Returns: a JSON object

**`POST /api/apcpi`**

- Requires: `manage_bac_meetings`, `approve_procurement`
- Body: `fiscal_year` integer, `remarks` string
- Returns: [`Assessment`](#assessment)

**`DELETE /api/apcpi/{id}`**

- Requires: `manage_bac_meetings`, `approve_procurement`
- Returns: a JSON object

**`GET /api/apcpi/{id}`**

- Requires: `view_procurement`, `approve_procurement`, `manage_bac_meetings`, `view_reports`
- Returns: [`Assessment`](#assessment)

**`POST /api/apcpi/{id}/finalize`**

- Requires: `manage_bac_meetings`, `approve_procurement`
- Returns: [`Assessment`](#assessment)

**`POST /api/apcpi/{id}/reopen`**

- Requires: `manage_bac_meetings`, `approve_procurement`
- Returns: [`Assessment`](#assessment)

**`PUT /api/apcpi/{id}/scores`**

- Requires: `manage_bac_meetings`, `approve_procurement`
- Body: `scores` ScoreInput[], `remarks` string
- Returns: [`Assessment`](#assessment)

### Assembly

Locally assembled units such as a Clone PC (items marked as assemblies): the parts list of one unit, each part drawn from a lot of the assembly item and returned to it when removed, with the unit recorded at its total assembled cost (COA: one PPE at the sum of its parts) and the item promoted to PPE once a unit reaches the capitalization threshold. Also the older parent/child linking of whole assets.

**`DELETE /api/properties/assembly-components/{componentId}`**

Removes a part and returns what it used to its lot.

- Requires: `edit_properties`, `create_properties`
- Returns: [`AssemblyView`](#assemblyview)

**`DELETE /api/properties/components/{childId}`**

- Requires: `edit_properties`, `create_properties`
- Returns: a JSON object

**`GET /api/properties/{id}/assembly`**

- Requires: `view_properties`, `view_own_properties`
- Returns: [`AssemblyView`](#assemblyview)

**`POST /api/properties/{id}/assembly-components`**

Adds a part, drawn from the chosen lot of the assembly item (or the lot whose brand and description match).

- Requires: `edit_properties`, `create_properties`
- Body: `description` string, `specifications` string, `serial_number` string, `quantity` integer, `remarks` string, `stock_id` integer
- Returns: [`AssemblyView`](#assemblyview)

**`GET /api/properties/{id}/components`**

- Requires: `view_properties`, `view_own_properties`
- Returns: a list of [`Linked`](#linked)

**`POST /api/properties/{id}/components`**

- Requires: `edit_properties`, `create_properties`
- Body: `property_id` integer
- Returns: a list of [`Linked`](#linked)

### Asset Media

A picture of each asset and item (TOR 1.4.4, 1.5.4), and where each asset is kept, with the history of its moves (TOR 1.4.7).

**`DELETE /api/items/{id}/photo`**

- Requires: `edit_inventory`
- Returns: a JSON object

**`GET /api/items/{id}/photo`**

Item pictures are shown wherever the item is (catalogue, requests), so anyone signed in can see them.

- Requires: *signed in*
- Returns: a file download

**`POST /api/items/{id}/photo`**

- Requires: `edit_inventory`, `create_inventory`
- Upload: `photo`
- Returns: a JSON object

**`POST /api/properties/{id}/location`**

Moves the asset to another location (or clears it); the holder does not change.

- Requires: `edit_properties`, `transfer_properties`, `assign_properties`
- Body: `location_id` integer, `remarks` string
- Returns: a JSON object

**`GET /api/properties/{id}/locations`**

- Requires: *signed in*
- Returns: a list of [`MoveView`](#moveview)

**`DELETE /api/properties/{id}/photo`**

- Requires: `edit_properties`
- Returns: a JSON object

**`GET /api/properties/{id}/photo`**

- Requires: *signed in*
- Returns: a file download

**`POST /api/properties/{id}/photo`**

- Requires: `edit_properties`, `create_properties`
- Upload: `photo`
- Returns: a JSON object

### Audit

The system-wide audit trail (TOR General 6, Audit & Compliance): filterable list, full detail with before/after values, CSV export, and a way for the client to record prints and downloads. Laravel exported Excel only and never logged printing or downloading.

**`GET /api/audit`**

- Requires: `view_audit_trail`
- Query: `page` (optional) default `1` integer, `per_page` (optional) default `20` integer, `user_id` (optional) integer, `action` (optional), `auditable_type` (optional), `search` (optional), `start_date` (optional), `end_date` (optional)
- Returns: a page of [`Entry (Audit)`](#entry-audit)

**`POST /api/audit/events`**

Lets the client record what the server cannot see: a page printed or a file saved (TOR 6.4).

- Requires: *signed in*
- Body: `action` string, `description` string, `auditable_type` string, `auditable_id` integer
- Returns: a JSON object

**`GET /api/audit/export`**

CSV of the filtered trail (newest first, at most 10,000 rows), for COA submission. The export itself is audited.

- Requires: `export_audit_trail`
- Query: `user_id` (optional) integer, `action` (optional), `auditable_type` (optional), `search` (optional), `start_date` (optional), `end_date` (optional)
- Returns: a file download

**`GET /api/audit/filters`**

Distinct values for the filter dropdowns.

- Requires: `view_audit_trail`
- Returns: a JSON object

**`GET /api/audit/verify`**

Recomputes every row's seal within the filter. Rows written before sealing began are counted as unsealed; a row whose seal does not match was changed outside PAMS. The table itself only accepts inserts, so a deleted row shows as a gap only to the database administrator.

- Requires: `view_audit_trail`
- Query: `user_id` (optional) integer, `action` (optional), `auditable_type` (optional), `search` (optional), `start_date` (optional), `end_date` (optional)
- Returns: [`Verification`](#verification)

**`GET /api/audit/{id}`**

- Requires: `view_audit_trail`
- Returns: [`Entry (Audit)`](#entry-audit)

### Auth

**`POST /api/auth/2fa/disable`**

- Requires: *signed in*
- Body: `password` string
- Returns: a JSON object

**`POST /api/auth/2fa/enable`**

- Requires: *signed in*
- Body: `otp` string
- Returns: a JSON object

**`POST /api/auth/2fa/setup`**

- Requires: *signed in*
- Returns: a JSON object

**`POST /api/auth/change-password`**

- Requires: *signed in*
- Body: `current_password` string, `password` string, `password_confirmation` string
- Returns: a JSON object

**`POST /api/auth/login`**

- Requires: nothing (public)
- Body: `email` string, `password` string, `otp` string
- Returns: a JSON object

**`POST /api/auth/logout`**

Sign-out is recorded (TOR 6.3), including the automatic one after the configured idle time.

- Requires: *signed in*
- Body: `reason` string
- Returns: a JSON object

**`GET /api/auth/me`**

- Requires: *signed in*
- Returns: [`AuthUserView`](#authuserview)

**`POST /api/auth/refresh`**

- Requires: *signed in*
- Returns: a JSON object

**`DELETE /api/auth/signature`**

- Requires: *signed in*
- Returns: a JSON object

**`POST /api/auth/signature`**

- Requires: *signed in*
- Upload: `signature`
- Returns: a JSON object

### Bac

The Bids and Awards Committee: its standing composition (RA 12009: chair, vice-chair, five or seven voting members, a secretariat), its meetings with attendance and quorum, and its resolutions. A resolution is approved by someone other than its drafter, and only once the meeting that adopted it has been held with a quorum.

**`GET /api/bac/meetings`**

- Requires: `view_bac`, `manage_bac_meetings`, `manage_bac_resolutions`, `conduct_bid_evaluation`, `process_award`, `create_rfq`, `view_rfq`
- Query: `page` (optional) default `1` integer, `status` (optional), `search` (optional)
- Returns: a page of [`MeetingView`](#meetingview)

**`POST /api/bac/meetings`**

- Requires: `manage_bac_meetings`
- Body: `title` string, `meeting_date` date-time, `venue` string, `agenda` string, `minutes` string, `status` string, `remarks` string
- Returns: [`MeetingView`](#meetingview)

**`DELETE /api/bac/meetings/{id}`**

- Requires: `manage_bac_meetings`
- Returns: a JSON object

**`GET /api/bac/meetings/{id}`**

- Requires: `view_bac`, `manage_bac_meetings`, `manage_bac_resolutions`, `conduct_bid_evaluation`, `process_award`, `create_rfq`, `view_rfq`
- Returns: [`MeetingView`](#meetingview)

**`PUT /api/bac/meetings/{id}`**

scheduled → ongoing → completed, or cancelled. Details are fixed once a meeting is over; minutes can still be written up.

- Requires: `manage_bac_meetings`
- Body: `title` string, `meeting_date` date-time, `venue` string, `agenda` string, `minutes` string, `status` string, `remarks` string
- Returns: [`MeetingView`](#meetingview)

**`POST /api/bac/meetings/{id}/attendance`**

- Requires: `manage_bac_meetings`
- Body: `attendees` AttendeeInput[]
- Returns: [`MeetingView`](#meetingview)

**`GET /api/bac/members`**

- Requires: `view_bac`, `manage_bac_meetings`, `manage_bac_resolutions`, `conduct_bid_evaluation`, `process_award`, `create_rfq`, `view_rfq`
- Returns: [`MembersView`](#membersview)

**`POST /api/bac/members`**

- Requires: `manage_bac_meetings`
- Body: `user_id` integer, `full_name` string, `position` string, `office` string, `designation` string, `appointment_order_no` string, `appointment_date` date, `term_start` date, `term_end` date, `is_active` boolean, `sequence` integer, `remarks` string
- Returns: [`MemberView`](#memberview)

**`DELETE /api/bac/members/{id}`**

Members are relieved (made inactive), not deleted, so past attendance keeps its meaning; a mistaken entry with no history can go.

- Requires: `manage_bac_meetings`
- Returns: a JSON object

**`PUT /api/bac/members/{id}`**

- Requires: `manage_bac_meetings`
- Body: `user_id` integer, `full_name` string, `position` string, `office` string, `designation` string, `appointment_order_no` string, `appointment_date` date, `term_start` date, `term_end` date, `is_active` boolean, `sequence` integer, `remarks` string
- Returns: [`MemberView`](#memberview)

**`GET /api/bac/resolutions`**

- Requires: `view_bac`, `manage_bac_meetings`, `manage_bac_resolutions`, `conduct_bid_evaluation`, `process_award`, `create_rfq`, `view_rfq`
- Query: `page` (optional) default `1` integer, `status` (optional), `resolution_type` (optional), `purchase_request_id` (optional) integer, `search` (optional)
- Returns: a page of [`ResolutionView`](#resolutionview)

**`POST /api/bac/resolutions`**

- Requires: `manage_bac_resolutions`
- Body: `title` string, `resolution_type` string, `bac_meeting_id` integer, `purchase_request_id` integer, `content` string, `remarks` string
- Returns: [`ResolutionView`](#resolutionview)

**`DELETE /api/bac/resolutions/{id}`**

- Requires: `manage_bac_resolutions`
- Returns: a JSON object

**`GET /api/bac/resolutions/{id}`**

- Requires: `view_bac`, `manage_bac_meetings`, `manage_bac_resolutions`, `conduct_bid_evaluation`, `process_award`, `create_rfq`, `view_rfq`
- Returns: [`ResolutionView`](#resolutionview)

**`PUT /api/bac/resolutions/{id}`**

- Requires: `manage_bac_resolutions`
- Body: `title` string, `resolution_type` string, `bac_meeting_id` integer, `purchase_request_id` integer, `content` string, `remarks` string
- Returns: [`ResolutionView`](#resolutionview)

**`POST /api/bac/resolutions/{id}/approve`**

- Requires: `manage_bac_resolutions`
- Returns: [`ResolutionView`](#resolutionview)

**`POST /api/bac/resolutions/{id}/archive`**

- Requires: `manage_bac_resolutions`
- Returns: [`ResolutionView`](#resolutionview)

**`POST /api/bac/resolutions/{id}/return`**

Sends a resolution back to draft for changes.

- Requires: `manage_bac_resolutions`
- Body: `remarks` string
- Returns: [`ResolutionView`](#resolutionview)

**`POST /api/bac/resolutions/{id}/submit`**

- Requires: `manage_bac_resolutions`
- Returns: [`ResolutionView`](#resolutionview)

### Bidding

Competitive (public) bidding under RA 12009: pre-procurement conference, Invitation to Bid, bid opening after the submission deadline, then evaluation stage by stage — eligibility, technical, financial — ranking of the responsive bids (lowest price, or MEARB rated score), and post-qualification of the top-ranked bidder, moving down the ranking only when it fails. The award goes to the bidder that passes post-qualification, on an approved BAC resolution and with no protest pending.

**`GET /api/bidding`**

- Requires: `view_bidding`, `manage_bidding`, `process_award`, `conduct_bid_evaluation`
- Query: `page` (optional) default `1` integer, `status` (optional), `purchase_request_id` (optional) integer, `search` (optional)
- Returns: a page of [`BiddingView`](#biddingview)

**`POST /api/bidding`**

- Requires: `manage_bidding`
- Body: `title` string, `purchase_request_id` integer, `delivery_term` string, `delivery_lead_days` integer, `evaluation_method` string, `price_weight` number, `technical_weight` number, `min_quality_score` number, `remarks` string, `items` ItemInput[]
- Returns: [`BiddingView`](#biddingview)

**`DELETE /api/bidding/bidders/{bidderId}`**

- Requires: `manage_bidding`
- Returns: [`BiddingView`](#biddingview)

**`PUT /api/bidding/bidders/{bidderId}`**

Corrects a bid as read at opening, or records one evaluation stage. Stages run in order; a bidder failed at one stage goes no further; post-qualification is for the top-ranked bidder only, and passes down the ranking when it fails.

- Requires: `manage_bidding`, `conduct_bid_evaluation`
- Body: `bid_amount` number, `bid_security_amount` number, `bid_security_form` string, `bid_security_posted` boolean, `stage` string, `result` string, `remarks` string, `eligibility_checklist` object
- Returns: [`BiddingView`](#biddingview)

**`PUT /api/bidding/bidders/{bidderId}/scores`**

- Requires: `manage_bidding`, `conduct_bid_evaluation`
- Body: `scores` ScoreInput[]
- Returns: [`BiddingView`](#biddingview)

**`DELETE /api/bidding/observers/{observerId}`**

- Requires: `manage_bidding`
- Returns: [`BiddingView`](#biddingview)

**`PUT /api/bidding/observers/{observerId}`**

- Requires: `manage_bidding`
- Body: `name` string, `organization` string, `observer_type` string, `invited` boolean, `invited_date` date, `attended` boolean, `remarks` string
- Returns: [`BiddingView`](#biddingview)

**`PUT /api/bidding/protests/{protestId}`**

Moves a protest along: under review, then upheld / denied (with the resolution) or withdrawn.

- Requires: `manage_bidding`
- Body: `protest_type` string, `supplier_id` integer, `filed_by` string, `grounds` string, `filed_date` date, `protest_fee` number, `status` string, `resolution` string, `resolved_date` date
- Returns: [`BiddingView`](#biddingview)

**`GET /api/bidding/purchase-requests`**

Approved purchase requests with lines planned for competitive bidding that are not yet out for procurement.

- Requires: `manage_bidding`
- Returns: a JSON object

**`DELETE /api/bidding/{id}`**

- Requires: `manage_bidding`
- Returns: a JSON object

**`GET /api/bidding/{id}`**

- Requires: `view_bidding`, `manage_bidding`, `process_award`, `conduct_bid_evaluation`
- Returns: [`BiddingView`](#biddingview)

**`PUT /api/bidding/{id}`**

- Requires: `manage_bidding`
- Body: `title` string, `purchase_request_id` integer, `delivery_term` string, `delivery_lead_days` integer, `evaluation_method` string, `price_weight` number, `technical_weight` number, `min_quality_score` number, `remarks` string, `items` ItemInput[]
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/award`**

- Requires: `process_award`
- Body: `bidder_id` integer, `bac_resolution_id` integer, `contract_recommendation` string
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/bidders`**

- Requires: `manage_bidding`
- Body: `supplier_id` integer, `supplier_name` string, `bid_amount` number, `bid_security_amount` number, `bid_security_form` string, `bid_security_posted` boolean, `remarks` string
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/cancel`**

- Requires: `manage_bidding`
- Body: `reason` string
- Returns: [`BiddingView`](#biddingview)

**`PUT /api/bidding/{id}/criteria`**

Rated criteria for MEARB; their weights add up to 100. Fixed once the Invitation to Bid is out.

- Requires: `manage_bidding`
- Body: `criteria` CriterionInput[]
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/declare-failed`**

- Requires: `manage_bidding`
- Body: `reason` string
- Returns: a JSON object

**`POST /api/bidding/{id}/invitation`**

- Requires: `manage_bidding`
- Body: `itb_reference` string, `itb_posted_at` date, `itb_publication` string, `submission_deadline` date-time, `bid_opening_date` date-time
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/noa`**

- Requires: `process_award`
- Body: `date` date
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/ntp`**

- Requires: `process_award`
- Body: `date` date
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/observers`**

- Requires: `manage_bidding`
- Body: `name` string, `organization` string, `observer_type` string, `invited` boolean, `invited_date` date, `attended` boolean, `remarks` string
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/open-bids`**

Opens the bids once the submission deadline has passed.

- Requires: `manage_bidding`
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/pre-proc`**

- Requires: `manage_bidding`
- Body: `pre_proc_date` date-time, `pre_proc_notes` string
- Returns: [`BiddingView`](#biddingview)

**`POST /api/bidding/{id}/protests`**

- Requires: `manage_bidding`
- Body: `protest_type` string, `supplier_id` integer, `filed_by` string, `grounds` string, `filed_date` date, `protest_fee` number, `status` string, `resolution` string, `resolved_date` date
- Returns: [`BiddingView`](#biddingview)

### Category

Item categories, one level of sub-categories deep. Listing is open to any signed-in user (pickers need it); maintaining them needs inventory rights. The Laravel app could only list them.

**`GET /api/inventory/categories`**

- Requires: *signed in*
- Returns: a list of [`CategoryView`](#categoryview)

**`POST /api/inventory/categories`**

- Requires: `create_inventory`, `edit_inventory`
- Body: `name` string, `parent_id` integer
- Returns: [`CategoryView`](#categoryview)

**`DELETE /api/inventory/categories/{id}`**

- Requires: `delete_inventory`
- Returns: a JSON object

**`PUT /api/inventory/categories/{id}`**

- Requires: `edit_inventory`
- Body: `name` string, `parent_id` integer
- Returns: [`CategoryView`](#categoryview)

### Clearance

Property turnover and clearance (TOR 1.4.12, 1.5.5). An employee leaving, retiring, transferring or ending a contract is put on the list; the property office follows up until every asset under their PAR/ICS is returned or transferred, then issues the Certificate of Property Turnover and Clearance. A lost asset stays on the employee until relieved through disposal, so it blocks the certificate too.

**`GET /api/clearances`**

- Requires: `view_properties`, `assign_properties`, `transfer_properties`, `view_reports`
- Query: `page` (optional) default `1` integer, `status` (optional), `search` (optional)
- Returns: a page of [`ClearanceView`](#clearanceview)

**`POST /api/clearances`**

- Requires: `assign_properties`, `transfer_properties`
- Body: `user_id` integer, `purpose` string, `purpose_details` string, `effective_date` date, `remarks` string
- Returns: [`ClearanceView`](#clearanceview)

**`GET /api/clearances/check/{userId}`**

What a person still holds; used before opening a clearance.

- Requires: `view_properties`, `assign_properties`, `transfer_properties`, `view_reports`
- Returns: a JSON object

**`GET /api/clearances/{id}`**

- Requires: `view_properties`, `assign_properties`, `transfer_properties`, `view_reports`
- Returns: [`ClearanceView`](#clearanceview)

**`POST /api/clearances/{id}/cancel`**

- Requires: `assign_properties`, `transfer_properties`
- Returns: [`ClearanceView`](#clearanceview)

**`GET /api/clearances/{id}/export`**

- Requires: `view_properties`, `assign_properties`, `transfer_properties`, `view_reports`
- Returns: a file download

**`POST /api/clearances/{id}/issue`**

Issues the certificate once nothing is outstanding.

- Requires: `assign_properties`, `transfer_properties`
- Returns: [`ClearanceView`](#clearanceview)

### Coa Export

The COA forms as Excel files, at the same URLs and with the same file names as the Laravel app. Entity name and fund cluster default to the agency profile in Settings. Each export is audited.

**`GET /api/bidding/{id}/abstract/export`**

Abstract of Bids as calculated.

- Requires: `view_bidding`, `manage_bidding`, `process_award`, `conduct_bid_evaluation`
- Returns: a file download

**`GET /api/properties/transfers/{transferId}/export`**

Appendix 76 — Property Transfer Report.

- Requires: `view_properties`, `transfer_properties`, `view_reports`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/receiving/inspection-reports/{id}/export`**

Appendix 62 — Inspection and Acceptance Report.

- Requires: `view_receiving`, `accept_delivery`, `view_reports`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/iirup/export`**

Appendix 74 — Inventory and Inspection Report of Unserviceable Property.

- Requires: `view_reports`, `generate_reports`
- Query: `disposal_ids` integer[], `entity_name` (optional), `fund_cluster` (optional), `accountable_officer` (optional), `designation` (optional), `station` (optional)
- Returns: a file download

**`POST /api/reports/iirup/export`**

Appendix 74 — Inventory and Inspection Report of Unserviceable Property.

- Requires: `view_reports`, `generate_reports`
- Query: `disposal_ids` integer[], `entity_name` (optional), `fund_cluster` (optional), `accountable_officer` (optional), `designation` (optional), `station` (optional)
- Returns: a file download

**`GET /api/reports/property-card/{propertyId}/export`**

Appendix 69 — Property Card.

- Requires: `view_reports`, `generate_reports`, `view_properties`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/rlsddp/export`**

Appendix 75 — Report of Lost, Stolen, Damaged or Destroyed Property.

- Requires: `view_reports`, `generate_reports`
- Query: `property_id` integer, `status`, `circumstances`, `entity_name` (optional), `fund_cluster` (optional), `department` (optional), `rlsddp_number` (optional), `rlsddp_date` (optional) date, `par_number` (optional), `par_date` (optional) date, `police_notified` (optional) boolean, `police_station` (optional), `police_date` (optional) date
- Returns: a file download

**`POST /api/reports/rlsddp/export`**

Appendix 75 — Report of Lost, Stolen, Damaged or Destroyed Property.

- Requires: `view_reports`, `generate_reports`
- Query: `property_id` integer, `status`, `circumstances`, `entity_name` (optional), `fund_cluster` (optional), `department` (optional), `rlsddp_number` (optional), `rlsddp_date` (optional) date, `par_number` (optional), `par_date` (optional) date, `police_notified` (optional) boolean, `police_station` (optional), `police_date` (optional) date
- Returns: a file download

**`GET /api/reports/rpci/export`**

Appendix 66 — Report on the Physical Count of Inventories.

- Requires: `view_reports`, `generate_reports`
- Query: `inventory_type` (optional), `as_at_date` (optional) date, `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/rpcppe/export`**

Appendix 73 — Report on the Physical Count of PPE.

- Requires: `view_reports`, `generate_reports`
- Query: `ppe_type` (optional), `as_at_date` (optional) date, `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/rpcsp/export`**

Appendix 68 — Report on the Physical Count of Semi-Expendable Property.

- Requires: `view_reports`, `generate_reports`
- Query: `as_at_date` (optional) date, `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/rsmi/export`**

Appendix 64 — Report of Supplies and Materials Issued.

- Requires: `view_reports`, `generate_reports`
- Query: `year` integer, `month` integer, `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/stock-card/{itemId}/export`**

Appendix 58 — Stock Card.

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`, `view_reports`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/wmr/export`**

Appendix 65 — Waste Materials Report (GET or POST; ids as a list or comma-separated).

- Requires: `view_reports`, `generate_reports`
- Query: `disposal_ids` integer[], `entity_name` (optional), `fund_cluster` (optional), `storage_place` (optional)
- Returns: a file download

**`POST /api/reports/wmr/export`**

Appendix 65 — Waste Materials Report (GET or POST; ids as a list or comma-separated).

- Requires: `view_reports`, `generate_reports`
- Query: `disposal_ids` integer[], `entity_name` (optional), `fund_cluster` (optional), `storage_place` (optional)
- Returns: a file download

**`GET /api/requests/{id}/export-ics`**

Appendix 59 — Inventory Custodian Slip.

- Requires: `view_requests`, `view_own_requests`, `create_requests`, `approve_requests`, `release_stock`, `view_reports`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/requests/{id}/export-par`**

Appendix 71 — Property Acknowledgment Receipt.

- Requires: `view_requests`, `view_own_requests`, `create_requests`, `approve_requests`, `release_stock`, `view_reports`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/requests/{id}/export-ris`**

Appendix 63 — Requisition and Issue Slip.

- Requires: `view_requests`, `view_own_requests`, `create_requests`, `approve_requests`, `release_stock`, `view_reports`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

### Contract

Contracts on awards (RA 12009). A contract is drawn up on an award with its Notice of Award, at the awarded price; it takes effect once signed and secured by a performance security of the required amount, activated by someone other than its preparer. Amendments change price or term within 10% of the original price; delivery extensions push the deadline; liquidated damages run at one-tenth of one percent of the undelivered value per day of delay, up to 10% of the contract.

**`GET /api/contracts`**

- Requires: `view_contracts`, `create_contracts`, `edit_contracts`
- Query: `page` (optional) default `1` integer, `status` (optional), `contract_type` (optional), `supplier_id` (optional) integer, `expiring_days` (optional) integer, `search` (optional)
- Returns: a page of [`ContractView`](#contractview)

**`POST /api/contracts`**

- Requires: `create_contracts`
- Body: `source_type` string, `source_id` integer, `title` string, `contract_type` string, `start_date` date, `end_date` date, `signed_date` date, `delivery_lead_days` integer, `description` string, `terms` string, `remarks` string
- Returns: [`ContractView`](#contractview)

**`DELETE /api/contracts/amendments/{amendmentId}`**

- Requires: `create_contracts`, `edit_contracts`
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/amendments/{amendmentId}/approve`**

- Requires: `edit_contracts`
- Returns: [`ContractView`](#contractview)

**`GET /api/contracts/awarded-sources`**

Awards with a Notice of Award and no contract yet (a terminated contract frees its award).

- Requires: `create_contracts`, `edit_contracts`
- Returns: a JSON object

**`PUT /api/contracts/calloffs/{calloffId}`**

draft → issued → completed, or cancelled.

- Requires: `create_contracts`, `edit_contracts`
- Body: `title` string, `amount` number, `calloff_date` date, `status` string, `remarks` string
- Returns: [`ContractView`](#contractview)

**`DELETE /api/contracts/extensions/{extensionId}`**

- Requires: `create_contracts`, `edit_contracts`
- Returns: [`ContractView`](#contractview)

**`PUT /api/contracts/extensions/{extensionId}/approve`**

- Requires: `edit_contracts`
- Body: `requested_days` integer, `reason` string, `requested_date` date, `approved_days` integer, `remarks` string
- Returns: [`ContractView`](#contractview)

**`PUT /api/contracts/extensions/{extensionId}/deny`**

- Requires: `edit_contracts`
- Body: `requested_days` integer, `reason` string, `requested_date` date, `approved_days` integer, `remarks` string
- Returns: [`ContractView`](#contractview)

**`DELETE /api/contracts/securities/{securityId}`**

- Requires: `create_contracts`, `edit_contracts`
- Returns: [`ContractView`](#contractview)

**`PUT /api/contracts/securities/{securityId}`**

Returns or forfeits a posted security.

- Requires: `edit_contracts`
- Body: `security_type` string, `form` string, `reference_number` string, `amount` number, `posted_date` date, `validity_date` date, `status` string, `remarks` string
- Returns: [`ContractView`](#contractview)

**`DELETE /api/contracts/{id}`**

- Requires: `create_contracts`
- Returns: a JSON object

**`GET /api/contracts/{id}`**

- Requires: `view_contracts`, `create_contracts`, `edit_contracts`
- Returns: [`ContractView`](#contractview)

**`PUT /api/contracts/{id}`**

- Requires: `create_contracts`, `edit_contracts`
- Body: `source_type` string, `source_id` integer, `title` string, `contract_type` string, `start_date` date, `end_date` date, `signed_date` date, `delivery_lead_days` integer, `description` string, `terms` string, `remarks` string
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/{id}/activate`**

Puts the signed contract in force: dates set, and a sufficient performance security posted.

- Requires: `edit_contracts`
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/{id}/amendments`**

- Requires: `create_contracts`, `edit_contracts`
- Body: `title` string, `description` string, `amount_change` number, `new_end_date` date, `remarks` string
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/{id}/calloffs`**

- Requires: `create_contracts`, `edit_contracts`
- Body: `title` string, `amount` number, `calloff_date` date, `status` string, `remarks` string
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/{id}/complete`**

- Requires: `edit_contracts`
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/{id}/extensions`**

Records a supplier's request for more time; it has to be made before the delivery deadline.

- Requires: `create_contracts`, `edit_contracts`
- Body: `requested_days` integer, `reason` string, `requested_date` date, `approved_days` integer, `remarks` string
- Returns: [`ContractView`](#contractview)

**`PUT /api/contracts/{id}/liquidated-damages`**

Records the liquidated damages assessed as of a date (today if not given).

- Requires: `edit_contracts`
- Body: `as_of_date` date, `remarks` string
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/{id}/securities`**

- Requires: `create_contracts`, `edit_contracts`
- Body: `security_type` string, `form` string, `reference_number` string, `amount` number, `posted_date` date, `validity_date` date, `status` string, `remarks` string
- Returns: [`ContractView`](#contractview)

**`POST /api/contracts/{id}/terminate`**

- Requires: `edit_contracts`
- Body: `remarks` string
- Returns: [`ContractView`](#contractview)

### Data Export

TOR Technical X: every table, every column, every row, as one UTF-8 CSV per table in a ZIP with a manifest. Rows are streamed from a database cursor straight into the response. Secrets (password hashes, two-factor secrets, remember and API tokens, revoked token ids) are left out.

**`GET /api/data/export`**

- Requires: `manage_system_settings`
- Returns: no JSON body

### Data Import

TOR Technical X / C 13.1: bulk import of master data with a dry run, commit, history and exception reports.

**`GET /api/data/import/batches`**

- Requires: `manage_system_settings`
- Query: `page` (optional) default `1` integer, `entity` (optional), `status` (optional)
- Returns: a page of objects

**`GET /api/data/import/batches/{id}`**

- Requires: `manage_system_settings`
- Returns: a JSON object

**`GET /api/data/import/batches/{id}/exceptions`**

- Requires: `manage_system_settings`
- Returns: a file download

**`GET /api/data/import/entities`**

What can be imported and the columns each takes.

- Requires: `manage_system_settings`
- Returns: a JSON object

**`GET /api/data/import/templates/{entity}`**

The CSV template: the expected header row and one example row.

- Requires: `manage_system_settings`
- Returns: a file download

**`POST /api/data/import/validate`**

Step 1, the dry run: classifies every row and keeps the upload as a validated batch.

- Requires: `manage_system_settings`
- Form fields: `entity` (optional), `create_missing_categories` (optional) default `false` boolean
- Upload: `file` (optional)
- Returns: a JSON object

**`POST /api/data/import/{id}/commit`**

Step 2: imports the rows that are (still) ok, all or nothing.

- Requires: `manage_system_settings`
- Returns: a JSON object

### Department

Offices (departments) that users belong to and that request supplies, plan procurement and hold assets.

**`GET /api/departments`**

- Requires: `view_users`, `create_users`, `edit_users`
- Returns: a JSON object

**`POST /api/departments`**

- Requires: `create_users`, `edit_users`
- Body: `name` string, `code` string
- Returns: [`DepartmentView`](#departmentview)

**`DELETE /api/departments/{id}`**

- Requires: `delete_users`
- Returns: a JSON object

**`PUT /api/departments/{id}`**

- Requires: `create_users`, `edit_users`
- Body: `name` string, `code` string
- Returns: [`DepartmentView`](#departmentview)

### Disposal

Disposal of unserviceable assets (IIRUP) and write-off of stock (WMR). draft → submitted → approved / rejected → completed. Someone other than the requester approves.

**`GET /api/disposal/requests`**

- Requires: *signed in*
- Query: `page` (optional) default `1` integer, `status` (optional), `category` (optional), `search` (optional), `per_page` (optional) default `20` integer
- Returns: a page of [`DisposalView`](#disposalview)

**`POST /api/disposal/requests`**

- Requires: `create_disposal`
- Body: `disposal_category` string, `property_id` integer, `item_id` integer, `quantity` integer, `disposal_type` string, `reason` string, `remarks` string
- Returns: [`DisposalView`](#disposalview)

**`DELETE /api/disposal/requests/{id}`**

- Requires: `create_disposal`
- Returns: a JSON object

**`GET /api/disposal/requests/{id}`**

- Requires: *signed in*
- Returns: [`DisposalView`](#disposalview)

**`PUT /api/disposal/requests/{id}`**

Fixes the TOR/audit gap: drafts can be edited (the Vue app called this but Laravel had no route).

- Requires: `create_disposal`
- Body: `disposal_category` string, `property_id` integer, `item_id` integer, `quantity` integer, `disposal_type` string, `reason` string, `remarks` string
- Returns: [`DisposalView`](#disposalview)

**`POST /api/disposal/requests/{id}/approve`**

- Requires: `approve_disposal`
- Body: `remarks` string
- Returns: [`DisposalView`](#disposalview)

**`POST /api/disposal/requests/{id}/complete`**

Carries out an approved disposal: the asset becomes disposed / the stock is written off.

- Requires: `approve_disposal`
- Body: `proceeds` number, `disposal_date` date, `remarks` string
- Returns: [`DisposalView`](#disposalview)

**`POST /api/disposal/requests/{id}/reject`**

- Requires: `approve_disposal`
- Body: `remarks` string
- Returns: [`DisposalView`](#disposalview)

**`POST /api/disposal/requests/{id}/submit`**

- Requires: `create_disposal`
- Returns: [`DisposalView`](#disposalview)

### Document

Electronic document repository with version history (TOR §15): procurement and inventory records are filed by type and may be attached to the record they belong to (a PR, PO, contract…). Every version keeps its file and a SHA-256 checksum; downloads are recorded in the audit trail.

**`GET /api/documents`**

- Requires: `view_documents`, `upload_documents`
- Query: `page` (optional) default `1` integer, `per_page` (optional) default `20` integer, `type` (optional), `status` (optional), `documentable_type` (optional), `documentable_id` (optional) integer, `search` (optional)
- Returns: a page of [`DocumentView (Document)`](#documentview-document)

**`POST /api/documents`**

- Requires: `upload_documents`
- Form fields: `title`, `type`, `document_number` (optional), `category` (optional), `description` (optional), `documentable_type` (optional), `documentable_id` (optional) integer, `remarks` (optional)
- Upload: `file` (optional)
- Returns: [`DocumentView (Document)`](#documentview-document)

**`GET /api/documents/types`**

- Requires: *signed in*
- Returns: a JSON object

**`DELETE /api/documents/{id}`**

Soft delete: the row and its files are kept for audit.

- Requires: `delete_documents`
- Returns: a JSON object

**`GET /api/documents/{id}`**

- Requires: `view_documents`, `upload_documents`
- Returns: [`DocumentView (Document)`](#documentview-document)

**`POST /api/documents/{id}/archive`**

- Requires: `upload_documents`
- Returns: [`DocumentView (Document)`](#documentview-document)

**`GET /api/documents/{id}/download`**

- Requires: `view_documents`, `upload_documents`
- Returns: a file download

**`POST /api/documents/{id}/versions`**

- Requires: `upload_documents`
- Form fields: `remarks` (optional)
- Upload: `file` (optional)
- Returns: [`DocumentView (Document)`](#documentview-document)

**`GET /api/documents/{id}/versions/{versionId}/download`**

- Requires: `view_documents`, `upload_documents`
- Returns: a file download

### Gate Pass

Gate passes for taking an asset off the premises. One open pass per asset, never for disposed or retired assets, and passes are kept (not deleted) as part of the asset's history.

**`GET /api/gate-pass`**

- Requires: *signed in*
- Query: `page` (optional) default `1` integer, `status` (optional), `search` (optional)
- Returns: a page of [`GatePassView`](#gatepassview)

**`POST /api/gate-pass`**

- Requires: `create_gate_pass`
- Body: `property_id` integer, `received_by` string, `purpose` string, `date_released` date, `expected_return_date` date, `condition_on_release` string, `remarks` string
- Returns: [`GatePassView`](#gatepassview)

**`GET /api/gate-pass/property/{propertyId}`**

- Requires: *signed in*
- Returns: a list of [`GatePassView`](#gatepassview)

**`DELETE /api/gate-pass/{id}`**

Removes a returned gate pass from the lists (kept in the database); one still out must be returned first.

- Requires: `delete_gate_pass`
- Returns: a JSON object

**`GET /api/gate-pass/{id}`**

- Requires: *signed in*
- Returns: [`GatePassView`](#gatepassview)

**`PUT /api/gate-pass/{id}`**

- Requires: `edit_gate_pass`, `create_gate_pass`
- Body: `property_id` integer, `received_by` string, `purpose` string, `date_released` date, `expected_return_date` date, `condition_on_release` string, `remarks` string
- Returns: [`GatePassView`](#gatepassview)

**`POST /api/gate-pass/{id}/return`**

- Requires: `edit_gate_pass`, `create_gate_pass`
- Body: `actual_return_date` date, `condition_on_return` string, `returned_by` string, `remarks` string
- Returns: [`GatePassView`](#gatepassview)

### Inventory

**`GET /api/inventory/config`**

Values the item and stock forms need from Settings.

- Requires: *signed in*
- Returns: a JSON object

**`GET /api/inventory/expiring`**

Expired and near-expiry lots, for first-expired-first-out monitoring and wastage disposal.

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`
- Query: `days` (optional) integer
- Returns: a JSON object

**`GET /api/inventory/items`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`
- Query: `page` (optional) default `1` integer, `per_page` (optional) default `20` integer, `search` (optional), `category_id` (optional) integer, `classification` (optional), `stock_level` (optional)
- Returns: a page of [`ItemView`](#itemview)

**`POST /api/inventory/items`**

- Requires: `create_inventory`
- Body: `code` string, `name` string, `description` string, `category_id` integer, `unit_of_measure` string, `min_stock` integer, `max_stock` integer, `classification` string, `is_assembly` boolean, `stock_entries` LotInput[]
- Returns: [`ItemView`](#itemview)

**`GET /api/inventory/items/available`**

Everything, issuable items first (in stock, or PPE); the request form greys out the rest.

- Requires: *signed in*
- Returns: a list of [`ItemView`](#itemview)

**`GET /api/inventory/items/options`**

Light list for item pickers in other modules (requests, receiving). Any signed-in user.

- Requires: *signed in*
- Query: `search` (optional)
- Returns: a list of [`ItemView`](#itemview)

**`DELETE /api/inventory/items/{id}`**

- Requires: `delete_inventory`
- Returns: a JSON object

**`GET /api/inventory/items/{id}`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`
- Returns: [`ItemView`](#itemview)

**`PUT /api/inventory/items/{id}`**

- Requires: `edit_inventory`
- Body: `code` string, `name` string, `description` string, `category_id` integer, `unit_of_measure` string, `min_stock` integer, `max_stock` integer, `classification` string, `is_assembly` boolean, `stock_entries` LotInput[]
- Returns: [`ItemView`](#itemview)

**`GET /api/inventory/items/{id}/lots`**

Lots that still hold stock, for the transfer form's source picker.

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`
- Returns: a list of [`StockView`](#stockview)

**`POST /api/inventory/items/{id}/stock`**

- Requires: `add_stock`, `manage_warehouse`
- Body: `id` integer, `brand_name` string, `specifications` string, `warranty` string, `warranty_months` integer, `quantity` integer, `unit_cost` number, `supplier_id` integer, `location` string, `lot_number` string, `expiration_date` date, `received_date` date
- Returns: a JSON object

**`GET /api/inventory/locations`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`
- Returns: a list of `String`

**`GET /api/inventory/stock-entries`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`
- Query: `page` (optional) default `1` integer, `per_page` (optional) default `25` integer, `item_id` (optional) integer
- Returns: a page of [`StockView`](#stockview)

**`GET /api/inventory/stock-transfers`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`
- Query: `page` (optional) default `1` integer, `per_page` (optional) default `20` integer, `item_id` (optional) integer, `location` (optional)
- Returns: a page of [`TransferView (InventoryViews)`](#transferview-inventoryviews)

**`POST /api/inventory/transfer-stock`**

- Requires: `add_stock`, `manage_warehouse`
- Body: `stock_id` integer, `to_location` string, `quantity` integer, `transfer_date` date, `remarks` string
- Returns: a JSON object

### Issuance

**`GET /api/requests`**

Everyone with view_requests sees all requests; requesters see only their own.

- Requires: *signed in*
- Query: `page` (optional) default `1` integer, `status` (optional), `search` (optional), `start_date` (optional) date, `end_date` (optional) date, `mine` (optional) default `false` boolean
- Returns: a page of [`RequestView (Issuance)`](#requestview-issuance)

**`POST /api/requests`**

- Requires: `create_requests`
- Body: `department_id` integer, `purpose` string, `priority` string, `custodian_name` string, `items` LineInput[]
- Returns: [`RequestView (Issuance)`](#requestview-issuance)

**`DELETE /api/requests/{id}`**

- Requires: *signed in*
- Returns: a JSON object

**`GET /api/requests/{id}`**

- Requires: *signed in*
- Returns: [`RequestView (Issuance)`](#requestview-issuance)

**`PUT /api/requests/{id}`**

- Requires: *signed in*
- Body: `department_id` integer, `purpose` string, `priority` string, `custodian_name` string, `items` LineInput[]
- Returns: [`RequestView (Issuance)`](#requestview-issuance)

**`PUT /api/requests/{id}/approve`**

- Requires: `approve_requests`
- Returns: a JSON object

**`PUT /api/requests/{id}/approve-items`**

- Requires: `approve_requests`
- Body: `items` Decision[]
- Returns: a JSON object

**`PUT /api/requests/{id}/reject`**

- Requires: `reject_requests`, `approve_requests`
- Body: `reason` string
- Returns: a JSON object

### Location

Set-up library (TOR 1.2.3): the buildings, floors and rooms where assets are kept.

**`GET /api/locations`**

Everyone signed in can read the list (asset forms pick from it).

- Requires: *signed in*
- Query: `active_only` (optional) default `false` boolean
- Returns: a list of [`LocationView`](#locationview)

**`POST /api/locations`**

- Requires: `edit_settings`
- Body: `name` string, `building` string, `floor` string, `room` string, `description` string, `is_active` boolean
- Returns: [`LocationView`](#locationview)

**`DELETE /api/locations/{id}`**

- Requires: `edit_settings`
- Returns: a JSON object

**`PUT /api/locations/{id}`**

- Requires: `edit_settings`
- Body: `name` string, `building` string, `floor` string, `room` string, `description` string, `is_active` boolean
- Returns: [`LocationView`](#locationview)

### Log Trail

Log trails: stock-card movements, sign-in activity, and who is using the system now (TOR 6.3, 6.7).

**`GET /api/logs/active-users`**

People with activity inside the idle window (or the last 15 minutes when idle sign-out is off), with their latest successful sign-in. Sign-out clears a person's activity at once.

- Requires: `view_log_trails`
- Returns: a JSON object

**`GET /api/logs/inventory`**

- Requires: `view_log_trails`
- Query: `page` (optional) default `1` integer, `action` (optional), `item_id` (optional) integer, `search` (optional), `start_date` (optional), `end_date` (optional)
- Returns: a page of [`InventoryEntry`](#inventoryentry)

**`GET /api/logs/inventory/actions`**

- Requires: `view_log_trails`
- Returns: a list of `String`

**`GET /api/logs/login-attempts`**

- Requires: `view_log_trails`
- Query: `page` (optional) default `1` integer, `email` (optional), `successful` (optional), `start_date` (optional), `end_date` (optional)
- Returns: a page of [`LoginEntry`](#loginentry)

### Maintenance

Maintenance of assets (TOR 1.4.5): preventive schedules that repeat every so many months, corrective repairs, inspections and calibrations, with what each cost. Overdue and upcoming work is listed for the property office.

**`GET /api/maintenance`**

- Requires: `view_properties`, `edit_properties`, `view_reports`
- Query: `page` (optional) default `1` integer, `status` (optional), `due` (optional), `property_id` (optional) integer, `search` (optional)
- Returns: a page of [`MaintenanceView`](#maintenanceview)

**`POST /api/maintenance`**

- Requires: `edit_properties`
- Body: `property_id` integer, `maintenance_type` string, `description` string, `scheduled_date` date, `interval_months` integer, `remarks` string
- Returns: [`MaintenanceView`](#maintenanceview)

**`GET /api/maintenance/summary`**

Counts for the dashboard and the maintenance page header.

- Requires: `view_properties`, `edit_properties`, `view_reports`
- Returns: a JSON object

**`PUT /api/maintenance/{id}`**

- Requires: `edit_properties`
- Body: `property_id` integer, `maintenance_type` string, `description` string, `scheduled_date` date, `interval_months` integer, `remarks` string
- Returns: [`MaintenanceView`](#maintenanceview)

**`POST /api/maintenance/{id}/cancel`**

- Requires: `edit_properties`
- Returns: [`MaintenanceView`](#maintenanceview)

**`POST /api/maintenance/{id}/complete`**

Records the work as done; a repeating schedule gets its next date counted from the day it was done.

- Requires: `edit_properties`
- Body: `completed_date` date, `performed_by` string, `cost` number, `remarks` string
- Returns: a JSON object

### Monitoring

Inventory monitoring and controls: stock levels against min/max, a re-order report with a suggested quantity from recent consumption (TOR C 2.5, 9.3), fast/slow/non-moving items (2.4), request trends, and the procurement watch list.

**`GET /api/monitoring/alerts`**

- Requires: `manage_system_settings`
- Returns: a JSON object

**`POST /api/monitoring/alerts/run`**

Runs one of the daily alert jobs now (or all of them), instead of waiting for its schedule.

- Requires: `manage_system_settings`
- Body: `job` string
- Returns: a JSON object

**`GET /api/monitoring/procurement`**

- Requires: `view_monitoring`, `view_procurement`, `view_bidding`, `view_rfq`, `view_contracts`, `manage_bidding`
- Returns: a JSON object

**`GET /api/monitoring/stock`**

- Requires: `view_monitoring`, `view_warehouse`, `manage_warehouse`
- Query: `category_id` (optional) integer
- Returns: a JSON object

**`GET /api/monitoring/trends`**

- Requires: `view_monitoring`, `view_warehouse`, `manage_warehouse`, `view_requests`
- Query: `months` (optional) default `6` integer
- Returns: a list of [`Trend`](#trend)

### Notification

The caller's own in-app notifications (the bell).

**`GET /api/notifications`**

- Requires: *signed in*
- Query: `page` (optional) default `1` integer, `per_page` (optional) default `20` integer, `unread` (optional) default `false` boolean
- Returns: a page of [`NotificationView`](#notificationview)

**`PUT /api/notifications/read-all`**

- Requires: *signed in*
- Returns: a JSON object

**`GET /api/notifications/unread-count`**

- Requires: *signed in*
- Returns: a JSON object

**`PUT /api/notifications/{id}/read`**

- Requires: *signed in*
- Returns: a JSON object

### Payment

Payment schedules (TOR B 7.4): the tranches a contract or purchase order is paid in, each with its deliverable and due date, moving pending → for_payment → paid. Approval for payment and the release of payment are done by different people from whoever scheduled or approved the tranche. Prints the Obligation Request and Status (ORS/BURS) and the Disbursement Voucher (COA Appendix 32).

**`GET /api/payments/due`**

Unpaid tranches due within `days` (default 7) or already overdue, in the shape of the Monitoring page's entries (kind "payment_due"), so it can merge them with the procurement watch.

- Requires: `view_payments`, `manage_payments`, `approve_payments`, `view_monitoring`
- Query: `days` (optional) integer
- Returns: a JSON object

**`GET /api/payments/schedule`**

- Requires: `view_payments`, `manage_payments`, `approve_payments`
- Query: `type`, `id` integer
- Returns: [`ScheduleView`](#scheduleview)

**`GET /api/payments/tranches`**

Every tranche, for a payments register: by status, or only the unpaid ones past due.

- Requires: `view_payments`, `manage_payments`, `approve_payments`
- Query: `status` (optional), `overdue` (optional) default `false` boolean
- Returns: a JSON object

**`POST /api/payments/tranches`**

- Requires: `manage_payments`
- Body: `type` string, `id` integer, `description` string, `percentage` number, `amount` number, `due_date` date, `deliverable` string, `responsibility_center` string, `mfo_pap` string, `uacs_code` string, `remarks` string
- Returns: [`ScheduleView`](#scheduleview)

**`DELETE /api/payments/tranches/{id}`**

- Requires: `manage_payments`
- Returns: [`ScheduleView`](#scheduleview)

**`PUT /api/payments/tranches/{id}`**

- Requires: `manage_payments`
- Body: `type` string, `id` integer, `description` string, `percentage` number, `amount` number, `due_date` date, `deliverable` string, `responsibility_center` string, `mfo_pap` string, `uacs_code` string, `remarks` string
- Returns: [`ScheduleView`](#scheduleview)

**`GET /api/payments/tranches/{id}/dv`**

Disbursement Voucher (COA Appendix 32) for a tranche.

- Requires: `view_payments`, `manage_payments`, `approve_payments`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`POST /api/payments/tranches/{id}/for-payment`**

Approves a pending tranche for payment (DV Box D), assigning the ORS and DV numbers.

- Requires: `approve_payments`
- Body: `ors_number` string, `dv_number` string, `responsibility_center` string, `mfo_pap` string, `uacs_code` string, `remarks` string
- Returns: [`ScheduleView`](#scheduleview)

**`GET /api/payments/tranches/{id}/ors`**

Obligation Request and Status for a tranche.

- Requires: `view_payments`, `manage_payments`, `approve_payments`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`POST /api/payments/tranches/{id}/pay`**

Records the release: check/ADA/LDDAP reference, deductions, and the net paid (gross less deductions).

- Requires: `manage_payments`
- Body: `paid_date` date, `payment_method` string, `payment_reference` string, `amount_paid` number, `retention_amount` number, `ld_deducted` number, `withholding_tax` number, `remarks` string
- Returns: [`ScheduleView`](#scheduleview)

### Petty Cash

The petty cash fund: a custodian disburses from it on Petty Cash Vouchers (COA Appendix 50), each approved by someone other than whoever recorded it, and asks for the approved vouchers to be replenished on a Report of Petty Cash replenishment. The fund's cash on hand is its amount less the approved vouchers not yet replenished.

**`GET /api/petty-cash/funds`**

- Requires: `view_payments`, `manage_payments`, `approve_payments`, `manage_petty_cash`
- Returns: a JSON object

**`POST /api/petty-cash/funds`**

- Requires: `manage_payments`
- Body: `fund_name` string, `custodian_id` integer, `amount` number, `fund_cluster` string, `status` string, `remarks` string
- Returns: [`FundView`](#fundview)

**`GET /api/petty-cash/funds/{id}`**

- Requires: `view_payments`, `manage_payments`, `approve_payments`, `manage_petty_cash`
- Returns: [`FundView`](#fundview)

**`PUT /api/petty-cash/funds/{id}`**

- Requires: `manage_payments`
- Body: `fund_name` string, `custodian_id` integer, `amount` number, `fund_cluster` string, `status` string, `remarks` string
- Returns: [`FundView`](#fundview)

**`POST /api/petty-cash/funds/{id}/replenishments`**

The custodian reports the approved vouchers not yet reported, asking for them to be replenished.

- Requires: `manage_petty_cash`
- Body: `report_date` date, `reference` string, `replenished_date` date, `remarks` string
- Returns: [`FundView`](#fundview)

**`POST /api/petty-cash/funds/{id}/vouchers`**

The custodian records a voucher; it waits for someone else's approval before the cash goes out.

- Requires: `manage_petty_cash`
- Body: `voucher_date` date, `payee` string, `particulars` string, `amount` number, `responsibility_center` string, `remarks` string
- Returns: [`FundView`](#fundview)

**`POST /api/petty-cash/replenishments/{id}/replenish`**

Records the check/ADA that restores the fund; done by someone other than the custodian who asked.

- Requires: `approve_payments`
- Body: `report_date` date, `reference` string, `replenished_date` date, `remarks` string
- Returns: [`FundView`](#fundview)

**`GET /api/petty-cash/replenishments/{id}/report`**

Report of Petty Cash replenishment: the vouchers it restores and the fund's position.

- Requires: `view_payments`, `manage_payments`, `approve_payments`, `manage_petty_cash`
- Query: `entity_name` (optional)
- Returns: a file download

**`POST /api/petty-cash/vouchers/{id}/approve`**

- Requires: `approve_payments`
- Returns: [`FundView`](#fundview)

**`POST /api/petty-cash/vouchers/{id}/cancel`**

- Requires: `manage_petty_cash`, `approve_payments`
- Body: `remarks` string
- Returns: [`FundView`](#fundview)

**`GET /api/petty-cash/vouchers/{id}/pcv`**

Petty Cash Voucher, COA Appendix 50.

- Requires: `view_payments`, `manage_payments`, `approve_payments`, `manage_petty_cash`
- Query: `entity_name` (optional)
- Returns: a file download

### Physical Count

Physical counts of stock-room supplies (RPCI), PPE (RPCPPE) and semi-expendable property (RPCSP). <p>Laravel captured the book quantity whenever a line was typed in and on verification overwrote on-hand stock (clamped at zero) with self-approved adjustments that never touched the stock lots. Here the book balances are frozen when counting starts, the variance against that cut-off is posted through approved stock adjustments that move real lots, and the person who verifies the count can be none of the people who initiated, counted or closed it. Asset counts go tag by tag: found, missing, found but not in the masterlist, or on record under another department.

**`GET /api/physical-inventory/counts`**

- Requires: `view_physical_inventory`, `create_physical_inventory`, `conduct_physical_inventory`
- Query: `page` (optional) default `1` integer, `status` (optional), `count_type` (optional), `search` (optional)
- Returns: a page of [`CountView`](#countview)

**`POST /api/physical-inventory/counts`**

- Requires: `create_physical_inventory`
- Body: `count_type` string, `count_date` date, `department_id` integer, `category_id` integer, `purpose` string, `remarks` string
- Returns: [`Detail`](#detail)

**`GET /api/physical-inventory/counts/options`**

- Requires: `view_physical_inventory`, `create_physical_inventory`, `conduct_physical_inventory`
- Returns: a JSON object

**`DELETE /api/physical-inventory/counts/{id}`**

- Requires: `create_physical_inventory`
- Returns: a JSON object

**`GET /api/physical-inventory/counts/{id}`**

- Requires: `view_physical_inventory`, `create_physical_inventory`, `conduct_physical_inventory`
- Returns: [`Detail`](#detail)

**`PUT /api/physical-inventory/counts/{id}`**

- Requires: `create_physical_inventory`
- Body: `count_type` string, `count_date` date, `department_id` integer, `category_id` integer, `purpose` string, `remarks` string
- Returns: [`Detail`](#detail)

**`POST /api/physical-inventory/counts/{id}/complete`**

- Requires: `conduct_physical_inventory`
- Returns: [`Detail`](#detail)

**`DELETE /api/physical-inventory/counts/{id}/properties/{lineId}`**

- Requires: `conduct_physical_inventory`
- Returns: [`Detail`](#detail)

**`POST /api/physical-inventory/counts/{id}/properties/{lineId}`**

- Requires: `conduct_physical_inventory`
- Body: `result` string, `observed_condition` string, `location` string, `remarks` string
- Returns: [`Detail`](#detail)

**`POST /api/physical-inventory/counts/{id}/record`**

- Requires: `conduct_physical_inventory`
- Body: `lines` LineInput[]
- Returns: [`Detail`](#detail)

**`POST /api/physical-inventory/counts/{id}/return`**

Sends a completed count back for recounting. Tags marked missing only because they were never checked go back to pending.

- Requires: `conduct_physical_inventory`
- Body: `remarks` string
- Returns: [`Detail`](#detail)

**`POST /api/physical-inventory/counts/{id}/scan`**

Marks a tag found. A tag not on this count's masterlist is added so it shows up in the results.

- Requires: `conduct_physical_inventory`
- Body: `tag_number` string, `observed_condition` string, `location` string, `remarks` string, `description` string
- Returns: [`Detail`](#detail)

**`POST /api/physical-inventory/counts/{id}/start`**

Freezes the book: one line per stock-room item, or one line per tag on the asset masterlist.

- Requires: `conduct_physical_inventory`
- Returns: [`Detail`](#detail)

**`POST /api/physical-inventory/counts/{id}/verify`**

Posts the count. Supply variances become approved stock adjustments against the cut-off balance (so issues and receipts made during the count are kept), moving lots through the ledger. For assets, the condition seen on the floor becomes the condition on record.

- Requires: `conduct_physical_inventory`
- Returns: [`Detail`](#detail)

### Planning

Procurement planning: department PPMPs (draft → submitted → under review → approved, or returned for revision), the agency APP consolidated from the approved ones, and budget monitoring of the APP against the purchase requests drawn on it.

**`GET /api/procurement-planning/apps`**

- Requires: `view_procurement_planning`, `manage_app`, `monitor_budget`, `create_procurement`, `edit_procurement`
- Query: `page` (optional) default `1` integer, `fiscal_year` (optional) integer, `status` (optional)
- Returns: a page of [`AppView`](#appview)

**`POST /api/procurement-planning/apps/generate`**

Consolidates the year's approved PPMPs. The first run creates the year's APP; later runs add the lines of PPMPs approved since, until the APP itself is approved.

- Requires: `manage_app`
- Body: `fiscal_year` integer, `title` string
- Returns: [`AppView`](#appview)

**`DELETE /api/procurement-planning/apps/{id}`**

- Requires: `manage_app`
- Returns: a JSON object

**`GET /api/procurement-planning/apps/{id}`**

- Requires: `view_procurement_planning`, `manage_app`, `monitor_budget`, `create_procurement`, `edit_procurement`
- Query: `exclude_pr_id` (optional) integer
- Returns: [`AppView`](#appview)

**`PUT /api/procurement-planning/apps/{id}`**

- Requires: `manage_app`
- Body: `title` string, `remarks` string, `items` AppLineInput[]
- Returns: [`AppView`](#appview)

**`POST /api/procurement-planning/apps/{id}/approve`**

- Requires: `manage_app`
- Returns: [`AppView`](#appview)

**`GET /api/procurement-planning/budget-summary`**

Utilization is the estimated cost of fully approved purchase requests drawn on each APP line.

- Requires: `view_procurement_planning`, `manage_app`, `monitor_budget`
- Query: `fiscal_year` (optional) integer
- Returns: [`BudgetSummary`](#budgetsummary)

**`GET /api/procurement-planning/options`**

- Requires: *signed in*
- Returns: [`Options`](#options)

**`GET /api/procurement-planning/ppmps`**

- Requires: `view_procurement_planning`, `create_ppmp`, `view_own_ppmp`, `edit_own_ppmp`, `delete_own_ppmp`, `submit_own_ppmp`, `approve_ppmp`, `manage_app`
- Query: `page` (optional) default `1` integer, `fiscal_year` (optional) integer, `status` (optional), `department_id` (optional) integer, `search` (optional)
- Returns: a page of [`PpmpView`](#ppmpview)

**`POST /api/procurement-planning/ppmps`**

- Requires: `create_ppmp`
- Body: `department_id` integer, `fiscal_year` integer, `title` string, `remarks` string, `items` LineInput[]
- Returns: [`PpmpView`](#ppmpview)

**`DELETE /api/procurement-planning/ppmps/{id}`**

- Requires: `create_ppmp`, `delete_own_ppmp`
- Returns: a JSON object

**`GET /api/procurement-planning/ppmps/{id}`**

- Requires: `view_procurement_planning`, `create_ppmp`, `view_own_ppmp`, `edit_own_ppmp`, `delete_own_ppmp`, `submit_own_ppmp`, `approve_ppmp`, `manage_app`
- Returns: [`PpmpView`](#ppmpview)

**`PUT /api/procurement-planning/ppmps/{id}`**

- Requires: `create_ppmp`, `edit_own_ppmp`
- Body: `department_id` integer, `fiscal_year` integer, `title` string, `remarks` string, `items` LineInput[]
- Returns: [`PpmpView`](#ppmpview)

**`POST /api/procurement-planning/ppmps/{id}/approve`**

- Requires: `approve_ppmp`
- Returns: [`PpmpView`](#ppmpview)

**`POST /api/procurement-planning/ppmps/{id}/return`**

- Requires: `approve_ppmp`
- Body: `return_reason` string
- Returns: [`PpmpView`](#ppmpview)

**`POST /api/procurement-planning/ppmps/{id}/review`**

- Requires: `approve_ppmp`
- Returns: [`PpmpView`](#ppmpview)

**`POST /api/procurement-planning/ppmps/{id}/submit`**

- Requires: `create_ppmp`, `submit_own_ppmp`
- Returns: [`PpmpView`](#ppmpview)

### Planning Report

Government procurement planning and reporting forms: PPMP and APP in the GPPB-prescribed formats, APP-CSE, the GPPB Agency Procurement Report and the COA transmittal of POs and contracts — each a JSON preview and an Excel download laid out as the form. Every download is audited.

**`GET /api/reports/planning/app`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

**`GET /api/reports/planning/app-cse`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `fiscal_year` (optional) integer, `source` (optional), `department_id` (optional) integer
- Returns: a JSON object

**`GET /api/reports/planning/app-cse/export`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `fiscal_year` (optional) integer, `source` (optional), `department_id` (optional) integer, `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/planning/app/export`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `fiscal_year` (optional) integer, `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/planning/apr`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

**`GET /api/reports/planning/apr/export`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `fiscal_year` (optional) integer, `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/planning/coa-transmittal`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `date_from` (optional) date, `date_to` (optional) date, `document_type` (optional), `transmittal_number` (optional), `auditor` (optional)
- Returns: a JSON object

**`GET /api/reports/planning/coa-transmittal/export`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `date_from` (optional) date, `date_to` (optional) date, `document_type` (optional), `transmittal_number` (optional), `auditor` (optional), `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/planning/options`**

Filter choices: end-user units and the PPMPs of the fiscal year (all years when none is given).

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

**`GET /api/reports/planning/ppmp`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `ppmp_id` (optional) integer, `department_id` (optional) integer, `fiscal_year` (optional) integer, `status` (optional)
- Returns: a JSON object

**`GET /api/reports/planning/ppmp/export`**

- Requires: `view_reports`, `generate_reports`, `view_procurement_planning`, `view_procurement`
- Query: `ppmp_id` (optional) integer, `department_id` (optional) integer, `fiscal_year` (optional) integer, `status` (optional), `entity_name` (optional)
- Returns: a file download

### Procurement Report

Procurement Reports (TOR §19): APP Report, Procurement Monitoring Report (summary and GPPB format), BAC Report and Award Report — on screen and as the same Excel export as the Laravel app.

**`GET /api/reports/procurement/app`**

APP Report — consolidated plan vs. utilization for a fiscal year.

- Requires: `view_reports`, `generate_reports`, `view_procurement`, `approve_procurement`, `manage_bac_meetings`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

**`GET /api/reports/procurement/award`**

Award Report — awarded biddings and RFQs.

- Requires: `view_reports`, `generate_reports`, `view_procurement`, `approve_procurement`, `manage_bac_meetings`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

**`GET /api/reports/procurement/bac`**

BAC Report — meetings, resolutions and evaluations for a fiscal year.

- Requires: `view_reports`, `generate_reports`, `view_procurement`, `approve_procurement`, `manage_bac_meetings`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

**`GET /api/reports/procurement/export`**

Excel export for any of the reports, laid out as the Laravel export: ?type=app|pmr|pmr_gppb|bac|award&fiscal_year=

- Requires: `view_reports`, `generate_reports`, `view_procurement`, `approve_procurement`, `manage_bac_meetings`
- Query: `type` (optional) default `app`, `fiscal_year` (optional) integer
- Returns: a file download

**`GET /api/reports/procurement/pmr`**

Procurement Monitoring Report — counts and values by stage/status.

- Requires: `view_reports`, `generate_reports`, `view_procurement`, `approve_procurement`, `manage_bac_meetings`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

**`GET /api/reports/procurement/pmr-gppb`**

GPPB-prescribed Procurement Monitoring Report — one row per awarded procurement with its mode, ABC, contract cost and milestone dates (posting → opening → NOA → contract signing → NTP → acceptance). Contract signing and acceptance come from the contract and the accepted IARs.

- Requires: `view_reports`, `generate_reports`, `view_procurement`, `approve_procurement`, `manage_bac_meetings`
- Query: `fiscal_year` (optional) integer
- Returns: a JSON object

### Property

**`POST /api/depreciation/run`**

Posts monthly depreciation for every depreciable asset through the given month (default: last month).

- Requires: `edit_properties`
- Body: `period` string
- Returns: a JSON object

**`GET /api/properties`**

- Requires: *signed in*
- Query: `page` (optional) default `1` integer, `search` (optional), `status` (optional), `condition` (optional), `mine` (optional) default `false` boolean
- Returns: a page of [`PropertyView`](#propertyview)

**`POST /api/properties`**

- Requires: `create_properties`, `assign_properties`
- Body: `item_id` integer, `stock_id` integer, `assigned_to` integer, `assigned_date` date, `condition` string, `useful_life_years` integer, `salvage_value` number, `units` UnitInput[]
- Returns: a JSON object

**`GET /api/properties/my-assets`**

Assets the signed-in person is accountable for. Everyone can see their own.

- Requires: *signed in*
- Returns: a JSON object

**`GET /api/properties/transfers/all`**

- Requires: `view_properties`, `transfer_properties`, `view_reports`, `generate_reports`
- Query: `page` (optional) default `1` integer, `type` (optional), `search` (optional)
- Returns: a page of [`TransferView (Property)`](#transferview-property)

**`DELETE /api/properties/{id}`**

- Requires: `delete_properties`
- Returns: a JSON object

**`GET /api/properties/{id}`**

- Requires: *signed in*
- Returns: [`PropertyView`](#propertyview)

**`PUT /api/properties/{id}`**

- Requires: `edit_properties`
- Body: `tag_number` string, `serial_number` string, `brand` string, `specifications` string, `warranty` string, `warranty_months` integer, `condition` string, `useful_life_years` integer, `salvage_value` number
- Returns: [`PropertyView`](#propertyview)

**`GET /api/properties/{id}/depreciation`**

- Requires: *signed in*
- Returns: a JSON object

**`POST /api/properties/{id}/issue`**

- Requires: `assign_properties`, `transfer_properties`
- Body: `to_user` integer, `reason` string, `date` date, `condition` string
- Returns: a JSON object

**`POST /api/properties/{id}/return`**

- Requires: `transfer_properties`, `assign_properties`
- Body: `to_user` integer, `reason` string, `date` date, `condition` string
- Returns: a JSON object

**`POST /api/properties/{id}/status`**

- Requires: `edit_properties`
- Body: `action` string, `remarks` string
- Returns: [`PropertyView`](#propertyview)

**`POST /api/properties/{id}/transfer`**

- Requires: `transfer_properties`
- Body: `to_user` integer, `reason` string, `date` date, `condition` string
- Returns: a JSON object

**`GET /api/properties/{id}/transfers`**

- Requires: *signed in*
- Returns: a list of [`TransferView (Property)`](#transferview-property)

### Ps Dbm

Common-use supplies and equipment (CSE) bought from the Procurement Service of the DBM, without bidding (LOI 755, EO 359 s. 1989, AO 17 s. 2011). The PS price catalogue; and the Agency Procurement Request (APR) raised from the PS-DBM lines of an approved PR: placed through the PhilGEPS Virtual Store, paid ahead (cash, LDDAP-ADA, check, or funds already with PS), then delivered or picked up against a PS Delivery Receipt. Recording the DR opens a confirmed order to PS-DBM, so the delivery is inspected and taken into stock through Receiving like any other. A line PS cannot supply takes PS's Certificate of Non-Availability of Stock (CNAS); for 15 days that line may then be bought by Shopping or SVP.

**`GET /api/ps-dbm/catalogue`**

- Requires: `view_procurement`, `edit_procurement`, `approve_procurement`, `view_reports`
- Query: `search` (optional), `active_only` (optional) default `false` boolean
- Returns: a JSON object

**`POST /api/ps-dbm/catalogue`**

- Requires: `edit_procurement`
- Body: `code` string, `description` string, `unit` string, `unit_price` number, `item_id` integer, `is_active` boolean, `price_as_of` date
- Returns: [`CatalogueItem`](#catalogueitem)

**`DELETE /api/ps-dbm/catalogue/{id}`**

A catalogue item already on a request is kept (deactivate it instead).

- Requires: `edit_procurement`
- Returns: a JSON object

**`PUT /api/ps-dbm/catalogue/{id}`**

- Requires: `edit_procurement`
- Body: `code` string, `description` string, `unit` string, `unit_price` number, `item_id` integer, `is_active` boolean, `price_as_of` date
- Returns: [`CatalogueItem`](#catalogueitem)

**`GET /api/ps-dbm/orders`**

- Requires: `view_procurement`, `edit_procurement`, `approve_procurement`, `view_reports`
- Query: `page` (optional) default `1` integer, `status` (optional), `search` (optional)
- Returns: a page of [`OrderView`](#orderview)

**`POST /api/ps-dbm/orders`**

- Requires: `edit_procurement`
- Body: `purchase_request_id` integer, `remarks` string, `items` LineInput[]
- Returns: [`OrderView`](#orderview)

**`DELETE /api/ps-dbm/orders/{id}`**

- Requires: `edit_procurement`
- Returns: a JSON object

**`GET /api/ps-dbm/orders/{id}`**

- Requires: `view_procurement`, `edit_procurement`, `approve_procurement`, `view_reports`
- Returns: [`OrderView`](#orderview)

**`PUT /api/ps-dbm/orders/{id}`**

- Requires: `edit_procurement`
- Body: `purchase_request_id` integer, `remarks` string, `items` LineInput[]
- Returns: [`OrderView`](#orderview)

**`POST /api/ps-dbm/orders/{id}/cancel`**

Before payment only; once paid, unused amounts stay with PS as the agency's unutilized funds or come back by refund.

- Requires: `edit_procurement`
- Body: `reason` string
- Returns: [`OrderView`](#orderview)

**`POST /api/ps-dbm/orders/{id}/deliver`**

PS issued its Delivery Receipt: the goods are picked up or delivered in one lot. A confirmed order to PS-DBM opens, numbered as the APR, for Receiving to inspect and take into stock.

- Requires: `edit_procurement`
- Body: `dr_number` string, `dr_date` date, `delivery_mode` string, `delivery_address` string
- Returns: [`OrderView`](#orderview)

**`POST /api/ps-dbm/orders/{id}/items/{lineId}/not-available`**

This line cannot come from PS on a ground of IRR 40.2. It frees the quantity for other sources; a Certificate of Non-Availability of Stock does so while it is valid (15 days), the other grounds without a limit.

- Requires: `edit_procurement`
- Body: `release_ground` string, `cnas_number` string, `cnas_date` date
- Returns: [`OrderView`](#orderview)

**`POST /api/ps-dbm/orders/{id}/pay`**

PS releases stock only against payment of the whole request.

- Requires: `edit_procurement`
- Body: `payment_mode` string, `payment_reference` string, `payment_date` date, `amount_paid` number
- Returns: [`OrderView`](#orderview)

**`POST /api/ps-dbm/orders/{id}/submit`**

Placed in the PhilGEPS Virtual Store; its order reference is recorded here.

- Requires: `edit_procurement`
- Body: `vs_reference` string, `submitted_date` date
- Returns: [`OrderView`](#orderview)

**`GET /api/ps-dbm/purchase-requests`**

Approved purchase requests with PS-DBM lines not yet on a live request.

- Requires: `edit_procurement`
- Returns: a JSON object

### Purchase Order

Purchase orders, issued only on an award with its Notice of Award: an RFQ's lines at the winning quotation's prices, or a bidding's lines priced to the awarded bid. Prepared by procurement, approved and sent to the supplier by someone else, then confirmed (the supplier's conformé). Deliveries against it are recorded on IARs.

**`GET /api/procurement/award-sources`**

Awards with a Notice of Award and no live purchase order. RFQ lines carry the winning quotation's prices; bidding lines are pre-priced in proportion to the awarded bid and can be adjusted, as long as they total the awarded amount.

- Requires: `edit_procurement`
- Returns: a JSON object

**`GET /api/procurement/purchase-orders`**

- Requires: `view_procurement`, `approve_procurement`, `edit_procurement`, `create_procurement`, `view_receiving`, `create_receiving`, `accept_delivery`
- Query: `page` (optional) default `1` integer, `status` (optional), `supplier_id` (optional) integer, `search` (optional)
- Returns: a page of [`PoView`](#poview)

**`POST /api/procurement/purchase-orders`**

- Requires: `edit_procurement`
- Body: `source_type` string, `source_id` integer, `delivery_date` date, `delivery_address` string, `remarks` string, `prices` PriceInput[]
- Returns: [`PoView`](#poview)

**`DELETE /api/procurement/purchase-orders/{id}`**

- Requires: `edit_procurement`
- Returns: a JSON object

**`GET /api/procurement/purchase-orders/{id}`**

- Requires: `view_procurement`, `approve_procurement`, `edit_procurement`, `create_procurement`, `view_receiving`, `create_receiving`, `accept_delivery`
- Returns: [`PoView`](#poview)

**`PUT /api/procurement/purchase-orders/{id}`**

- Requires: `edit_procurement`
- Body: `source_type` string, `source_id` integer, `delivery_date` date, `delivery_address` string, `remarks` string, `prices` PriceInput[]
- Returns: [`PoView`](#poview)

**`POST /api/procurement/purchase-orders/{id}/cancel`**

- Requires: `edit_procurement`
- Body: `reason` string
- Returns: [`PoView`](#poview)

**`POST /api/procurement/purchase-orders/{id}/confirm`**

Records the supplier's conformé; deliveries can then be received.

- Requires: `edit_procurement`
- Returns: [`PoView`](#poview)

**`POST /api/procurement/purchase-orders/{id}/send`**

Approves the PO and issues it to the supplier; the one who prepared it cannot.

- Requires: `edit_procurement`, `approve_procurement`
- Returns: [`PoView`](#poview)

### Purchase Request

Purchase requests: drafted by the end user, routed Department Head → Budget Officer (with the Certificate of Availability of Funds) → Accounting → BAC Secretariat. Any signatory may return it for revision. Lines drawn on the APP may not exceed the APP line's quantity or budget.

**`GET /api/purchase-requests`**

- Requires: `view_procurement`, `create_procurement`, `approve_procurement`, `pr_approve_department`, `pr_approve_budget`, `pr_approve_accounting`, `pr_approve_bac`
- Query: `page` (optional) default `1` integer, `status` (optional), `priority` (optional), `department_id` (optional) integer, `fiscal_year` (optional) integer, `search` (optional), `awaiting_me` (optional) default `false` boolean, `mine` (optional) default `false` boolean
- Returns: a page of [`PrView`](#prview)

**`POST /api/purchase-requests`**

- Requires: `create_procurement`, `edit_procurement`
- Body: `department_id` integer, `app_id` integer, `fiscal_year` integer, `purpose` string, `priority` string, `remarks` string, `items` LineInput[]
- Returns: [`PrView`](#prview)

**`DELETE /api/purchase-requests/{id}`**

- Requires: `create_procurement`, `edit_procurement`
- Returns: a JSON object

**`GET /api/purchase-requests/{id}`**

- Requires: `view_procurement`, `create_procurement`, `approve_procurement`, `pr_approve_department`, `pr_approve_budget`, `pr_approve_accounting`, `pr_approve_bac`
- Returns: [`PrView`](#prview)

**`PUT /api/purchase-requests/{id}`**

- Requires: `create_procurement`, `edit_procurement`
- Body: `department_id` integer, `app_id` integer, `fiscal_year` integer, `purpose` string, `priority` string, `remarks` string, `items` LineInput[]
- Returns: [`PrView`](#prview)

**`POST /api/purchase-requests/{id}/approve-step`**

- Requires: `approve_procurement`, `pr_approve_department`, `pr_approve_budget`, `pr_approve_accounting`, `pr_approve_bac`
- Body: `remarks` string
- Returns: [`PrView`](#prview)

**`POST /api/purchase-requests/{id}/cancel`**

- Requires: `create_procurement`, `edit_procurement`
- Returns: [`PrView`](#prview)

**`POST /api/purchase-requests/{id}/certify-funds`**

Certificate of Availability of Funds. A new CAF can be issued when the PR total has grown past the last one.

- Requires: `pr_approve_budget`, `pr_approve_accounting`
- Body: `caf_amount` number, `caf_remarks` string
- Returns: [`PrView`](#prview)

**`POST /api/purchase-requests/{id}/return`**

- Requires: `approve_procurement`, `pr_approve_department`, `pr_approve_budget`, `pr_approve_accounting`, `pr_approve_bac`
- Body: `return_reason` string
- Returns: [`PrView`](#prview)

**`POST /api/purchase-requests/{id}/submit`**

- Requires: `create_procurement`, `edit_procurement`
- Returns: [`PrView`](#prview)

### Qr

QR codes and printable labels for items and assets (TOR C 3.2, 18.1), the gate-pass QR, the in-app scanner lookup (keyboard-wedge scanner or camera, TOR 12.1) and the public scan page data.

**`GET /api/gate-pass/{id}/qr`**

- Requires: `view_gate_pass`
- Returns: a JSON object

**`POST /api/inventory/bulk-qr`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`
- Body: `item_ids` integer[], `property_ids` integer[]
- Returns: a JSON object

**`GET /api/inventory/items/{id}/qr`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`
- Query: `size` (optional) default `300` integer
- Returns: a file download

**`GET /api/inventory/items/{id}/qr-base64`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`
- Query: `size` (optional) default `300` integer
- Returns: [`Label`](#label)

**`GET /api/inventory/items/{id}/qr/printable`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`
- Query: `size` (optional) default `300` integer
- Returns: [`Label`](#label)

**`POST /api/inventory/items/{id}/qr/regenerate`**

Codes are made on request, so "regenerate" only re-issues the label; it is kept for the old API.

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`
- Returns: [`Label`](#label)

**`POST /api/inventory/scan-qr`**

Resolves what a scanner read — one of our QR links, an item code, an asset tag/serial, or a gate-pass number — to the record. Links must carry a valid checksum.

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `view_properties`, `view_own_properties`, `view_gate_pass`
- Body: `data` string
- Returns: a JSON object

**`POST /api/properties/qr-labels`**

A sheet of stickers for several assets at once.

- Requires: `view_properties`
- Body: `item_ids` integer[], `property_ids` integer[]
- Returns: a JSON object

**`GET /api/properties/{id}/qr`**

- Requires: `view_properties`, `view_own_properties`
- Query: `size` (optional) default `300` integer
- Returns: a file download

**`GET /api/properties/{id}/qr-base64`**

- Requires: `view_properties`, `view_own_properties`
- Query: `size` (optional) default `300` integer
- Returns: [`Label`](#label)

**`GET /api/properties/{id}/qr/printable`**

- Requires: `view_properties`, `view_own_properties`
- Query: `size` (optional) default `300` integer
- Returns: [`Label`](#label)

**`POST /api/properties/{id}/qr/regenerate`**

- Requires: `view_properties`, `view_own_properties`
- Returns: [`Label`](#label)

**`GET /api/public/qr/{type}/{id}`**

The public page behind a QR link: minimal facts, only with a valid checksum.

- Requires: nothing (public)
- Query: `c` (optional)
- Returns: a JSON object

### Receiving

Receiving against purchase orders. The inspection committee records each delivery on an IAR — received, accepted and rejected per PO line, never more than is still outstanding. A supply or property officer other than the inspector then accepts it, which puts the accepted quantities into stock as new lots at the PO price (stock card, weighted average cost), or rejects the delivery.

**`GET /api/receiving/inspection-reports`**

- Requires: `view_receiving`, `create_receiving`, `accept_delivery`, `view_procurement`, `view_reports`, `generate_reports`
- Query: `page` (optional) default `1` integer, `acceptance_status` (optional), `purchase_order_id` (optional) integer, `search` (optional)
- Returns: a page of [`IarView`](#iarview)

**`POST /api/receiving/inspection-reports`**

- Requires: `create_receiving`
- Body: `purchase_order_id` integer, `delivery_date` date, `inspection_date` date, `observations` string, `remarks` string, `items` LineInput[]
- Returns: [`IarView`](#iarview)

**`DELETE /api/receiving/inspection-reports/{id}`**

- Requires: `create_receiving`
- Returns: a JSON object

**`GET /api/receiving/inspection-reports/{id}`**

- Requires: `view_receiving`, `create_receiving`, `accept_delivery`, `view_procurement`, `view_reports`, `generate_reports`
- Returns: [`IarView`](#iarview)

**`PUT /api/receiving/inspection-reports/{id}`**

- Requires: `create_receiving`
- Body: `purchase_order_id` integer, `delivery_date` date, `inspection_date` date, `observations` string, `remarks` string, `items` LineInput[]
- Returns: [`IarView`](#iarview)

**`POST /api/receiving/inspection-reports/{id}/accept`**

Accepts the delivery into stock: each accepted line goes into the inventory item chosen for it, as a new lot at the PO unit price, received on the delivery date, from the PO's supplier.

- Requires: `accept_delivery`
- Body: `lines` AcceptLine[]
- Returns: [`IarView`](#iarview)

**`POST /api/receiving/inspection-reports/{id}/reject`**

- Requires: `accept_delivery`
- Body: `remarks` string
- Returns: [`IarView`](#iarview)

### Report

The Reports page: the inventory and request-log reports, the RSMI summary, and the on-screen previews of the COA forms (RPCI, RPCSP, RPCPPE, Property Card, WMR, IIRUP, RLSDDP) plus the reconciliation and aging reports — the same data the Laravel ReportController returned. The Excel forms themselves are produced by the report.coa forms.

**`GET /api/reports/iirup/preview`**

IIRUP preview (Appendix 74) from selected unserviceable-property disposals.

- Requires: `view_reports`, `generate_reports`
- Query: `disposal_ids` (optional) integer[]
- Returns: a JSON object

**`GET /api/reports/inventory`**

- Requires: `view_reports`, `generate_reports`
- Query: `category_id` (optional) integer, `status` (optional)
- Returns: a JSON object

**`GET /api/reports/inventory-aging/preview`**

- Requires: `view_reports`, `generate_reports`
- Query: `days` (optional) default `180` integer
- Returns: a JSON object

**`GET /api/reports/options`**

Filter choices for the report forms.

- Requires: `view_reports`, `generate_reports`
- Returns: a JSON object

**`GET /api/reports/property-card/{propertyId}/preview`**

Property Card (COA Appendix 69) — acquisition, transfers and disposal/status of one asset.

- Requires: `view_reports`, `generate_reports`
- Returns: a JSON object

**`GET /api/reports/reconciliation/preview`**

Items whose Stock Card balance (receipts − issues logged) differs from the recorded on-hand quantity.

- Requires: `view_reports`, `generate_reports`
- Returns: a JSON object

**`GET /api/reports/request-logs`**

- Requires: `view_reports`, `generate_reports`
- Query: `start_date` (optional) date, `end_date` (optional) date, `department_id` (optional) integer
- Returns: a JSON object

**`GET /api/reports/rlsddp/preview`**

RLSDDP preview (Appendix 75) for a single lost/damaged property.

- Requires: `view_reports`, `generate_reports`
- Query: `property_id` (optional) integer, `status` (optional), `circumstances` (optional)
- Returns: a JSON object

**`GET /api/reports/rpci/preview`**

- Requires: `view_reports`, `generate_reports`
- Query: `inventory_type` (optional)
- Returns: a JSON object

**`GET /api/reports/rpcppe/preview`**

- Requires: `view_reports`, `generate_reports`
- Query: `ppe_type` (optional)
- Returns: a JSON object

**`GET /api/reports/rpcsp/preview`**

- Requires: `view_reports`, `generate_reports`
- Returns: a JSON object

**`POST /api/reports/rsmi`**

Generates and stores an RSMI summary (rsmi_reports), numbered RSMI-YYYY-MM-NNN as before.

- Requires: `view_reports`, `generate_reports`
- Body: `year` integer, `month` integer
- Returns: a JSON object

**`POST /api/reports/rsmi/generate`**

Generates and stores an RSMI summary (rsmi_reports), numbered RSMI-YYYY-MM-NNN as before.

- Requires: `view_reports`, `generate_reports`
- Body: `year` integer, `month` integer
- Returns: a JSON object

**`GET /api/reports/rsmi/history`**

- Requires: `view_reports`, `generate_reports`
- Returns: a list of objects

**`GET /api/reports/wmr/preview`**

Waste Materials Report preview (Appendix 65) from selected disposals.

- Requires: `view_reports`, `generate_reports`
- Query: `disposal_ids` (optional) integer[]
- Returns: a JSON object

### Rfq

Requests for quotation under the alternative modes (Shopping, SVP, negotiated, direct contracting, …). An RFQ draws its lines from an approved purchase request, within the PR line's quantity and estimated cost, under the mode the PR planned. Quotations are recorded until the RFQ closes; the award goes to the lowest responsive quotation within the ABC, on an approved BAC resolution, followed by the Notice of Award and Notice to Proceed.

**`GET /api/rfq`**

- Requires: `view_rfq`, `create_rfq`, `generate_abstract`, `process_award`
- Query: `page` (optional) default `1` integer, `status` (optional), `procurement_method` (optional), `purchase_request_id` (optional) integer, `search` (optional)
- Returns: a page of [`RfqView`](#rfqview)

**`POST /api/rfq`**

- Requires: `create_rfq`
- Body: `title` string, `procurement_method` string, `purchase_request_id` integer, `deadline` date-time, `delivery_term` string, `delivery_lead_days` integer, `description` string, `justification` string, `negotiated_ground` string, `invited_suppliers` string[], `items` ItemInput[]
- Returns: [`RfqView`](#rfqview)

**`GET /api/rfq/purchase-requests`**

Approved purchase requests with lines not yet on an RFQ, for the new-RFQ form.

- Requires: `create_rfq`
- Returns: a JSON object

**`DELETE /api/rfq/quotations/{quotationId}`**

- Requires: `create_rfq`
- Returns: [`RfqView`](#rfqview)

**`PUT /api/rfq/quotations/{quotationId}/responsive`**

- Requires: `create_rfq`
- Body: `is_responsive` boolean, `remarks` string
- Returns: [`RfqView`](#rfqview)

**`DELETE /api/rfq/{id}`**

- Requires: `create_rfq`
- Returns: a JSON object

**`GET /api/rfq/{id}`**

- Requires: `view_rfq`, `create_rfq`, `generate_abstract`, `process_award`
- Returns: [`RfqView`](#rfqview)

**`PUT /api/rfq/{id}`**

- Requires: `create_rfq`
- Body: `title` string, `procurement_method` string, `purchase_request_id` integer, `deadline` date-time, `delivery_term` string, `delivery_lead_days` integer, `description` string, `justification` string, `negotiated_ground` string, `invited_suppliers` string[], `items` ItemInput[]
- Returns: [`RfqView`](#rfqview)

**`GET /api/rfq/{id}/abstract`**

Abstract of Quotations: every line priced by every supplier, and the lowest responsive quotation within the ABC.

- Requires: `generate_abstract`, `view_rfq`, `create_rfq`, `process_award`
- Returns: [`AbstractView`](#abstractview)

**`POST /api/rfq/{id}/award`**

- Requires: `process_award`
- Body: `quotation_id` integer, `bac_resolution_id` integer, `remarks` string
- Returns: [`RfqView`](#rfqview)

**`POST /api/rfq/{id}/cancel`**

- Requires: `create_rfq`
- Body: `remarks` string
- Returns: [`RfqView`](#rfqview)

**`POST /api/rfq/{id}/close`**

Closes the RFQ for quotations once the deadline has passed; quotations are then opened and compared.

- Requires: `create_rfq`
- Returns: [`RfqView`](#rfqview)

**`POST /api/rfq/{id}/issue`**

- Requires: `create_rfq`
- Returns: [`RfqView`](#rfqview)

**`POST /api/rfq/{id}/noa`**

- Requires: `process_award`
- Body: `date` date
- Returns: [`RfqView`](#rfqview)

**`POST /api/rfq/{id}/ntp`**

- Requires: `process_award`
- Body: `date` date
- Returns: [`RfqView`](#rfqview)

**`POST /api/rfq/{id}/quotations`**

- Requires: `create_rfq`
- Body: `supplier_id` integer, `supplier_name` string, `quotation_number` string, `submitted_at` date, `remarks` string, `items` QuoteLineInput[]
- Returns: [`RfqView`](#rfqview)

### Role

Role management. Needs manage_roles (Laravel only let Super Admin in and ignored that permission). Granting the wildcard, or touching a Super Admin role, stays Super Admin only.

**`GET /api/roles`**

- Requires: `manage_roles`
- Returns: a JSON object

**`POST /api/roles`**

- Requires: `manage_roles`
- Body: `name` string, `description` string, `permissions` string[]
- Returns: a JSON object

**`GET /api/roles/permissions-list`**

- Requires: `manage_roles`
- Returns: a JSON object

**`DELETE /api/roles/{id}`**

- Requires: `manage_roles`
- Returns: a JSON object

**`GET /api/roles/{id}`**

- Requires: `manage_roles`
- Returns: a JSON object

**`PUT /api/roles/{id}`**

- Requires: `manage_roles`
- Body: `name` string, `description` string, `permissions` string[]
- Returns: a JSON object

### Semi Expendable

Semi-expendable property under someone's accountability (ICS), and recording its return.

**`GET /api/semi-expendable`**

Staff with inventory rights see the whole ledger; anyone else sees only what they hold.

- Requires: *signed in*
- Query: `page` (optional) default `1` integer, `status` (optional), `outstanding` (optional) default `false` boolean, `item_id` (optional) integer, `search` (optional), `mine` (optional) default `false` boolean
- Returns: a page of [`IssueView`](#issueview)

**`POST /api/semi-expendable/{id}/return`**

Records units handed back. Serviceable returns go back into stock as a lot at the item's weighted average cost; unserviceable ones are only recorded (for disposal).

- Requires: `add_stock`, `manage_warehouse`
- Body: `quantity` integer, `restock` boolean, `remarks` string
- Returns: [`IssueView`](#issueview)

### Settings

System settings, the agency profile printed on COA forms, authorized signatories per role and document logos. Every change is audited with its old and new values (Laravel did not audit settings at all).

**`GET /api/settings`**

Read by every signed-in client: the idle sign-out and password rules apply to everyone.

- Requires: *signed in*
- Returns: a JSON object

**`PUT /api/settings`**

- Requires: `edit_settings`, `manage_system_settings`
- Body: `capitalization_threshold` number, `idle_timeout_minutes` integer, `password_min_length` integer, `app_url` string, `password_expiry_days` integer
- Returns: a JSON object

**`GET /api/settings/document-logos`**

- Requires: *signed in*
- Returns: a JSON object

**`POST /api/settings/document-logos`**

- Requires: `edit_settings`, `manage_agency_profile`
- Form fields: `position` (optional), `label` (optional)
- Upload: `logo` (optional)
- Returns: a JSON object

**`DELETE /api/settings/document-logos/{id}`**

- Requires: `edit_settings`, `manage_agency_profile`
- Returns: a JSON object

**`PUT /api/settings/document-logos/{id}`**

- Requires: `edit_settings`, `manage_agency_profile`
- Body: `position` string, `sequence` integer, `label` string
- Returns: a JSON object

**`GET /api/settings/mis`**

- Requires: *signed in*
- Returns: a JSON object

**`PUT /api/settings/mis`**

- Requires: `edit_settings`, `manage_agency_profile`
- Body: JSON object
- Returns: a JSON object

**`GET /api/settings/signatories`**

- Requires: `view_settings`, `edit_settings`, `manage_signatories`
- Returns: a JSON object

**`PUT /api/settings/signatories`**

- Requires: `edit_settings`, `manage_signatories`
- Body: `signatories` SignatoryRow[]
- Returns: a JSON object

**`GET /api/settings/signatory/{role}`**

One role's signatory for embedding in a document: name, designation and signature image.

- Requires: *signed in*
- Returns: a JSON object

### Signing

Digital signing of documents (TOR 1.4.11; other functional requirements 1–2). Anyone can send a document (a COA form they exported, a PDF) to a colleague for signing. The signer signs it with a certificate from a trusted CA — the PNPKI or the agency's private CA — either in their own signing tool, pasting the signature here, or through an external signing platform that is told about the request and posts the signature back over the platform API. Every signature is checked against the document's exact bytes and the trusted CAs before it is kept, and any copy of a file can be verified later.

**`GET /api/signing/platform/requests/{number}/file`**

The signing platform fetches the document it was told about. Authenticated by the platform key, not a user token.

- Requires: the `X-Signing-Key` header
- Returns: a file download

**`POST /api/signing/platform/signatures`**

The signing platform posts the signature it collected from the signer.

- Requires: the `X-Signing-Key` header
- Body: `request_number` string, `certificate_pem` string, `signature` string, `algorithm` string, `platform_reference` string
- Returns: a JSON object

**`GET /api/signing/requests`**

`box`: inbox (to sign), outbox (sent) or all (administrators).

- Requires: *signed in*
- Query: `box` (optional) default `inbox`, `status` (optional), `page` (optional) default `1` integer
- Returns: a page of [`RequestView (Signing)`](#requestview-signing)

**`POST /api/signing/requests`**

- Requires: *signed in*
- Form fields: `title`, `signer_id` integer, `remarks` (optional)
- Upload: `file`
- Returns: [`RequestView (Signing)`](#requestview-signing)

**`GET /api/signing/requests/{id}`**

- Requires: *signed in*
- Returns: [`RequestView (Signing)`](#requestview-signing)

**`POST /api/signing/requests/{id}/cancel`**

- Requires: *signed in*
- Returns: [`RequestView (Signing)`](#requestview-signing)

**`POST /api/signing/requests/{id}/decline`**

- Requires: *signed in*
- Body: `remarks` string
- Returns: [`RequestView (Signing)`](#requestview-signing)

**`GET /api/signing/requests/{id}/file`**

- Requires: *signed in*
- Returns: a file download

**`POST /api/signing/requests/{id}/sign`**

The signer signs the exact bytes of the file (download it, sign it, then paste the signature).

- Requires: *signed in*
- Body: `certificate_pem` string, `signature` string, `algorithm` string
- Returns: [`RequestView (Signing)`](#requestview-signing)

**`GET /api/signing/trusted-cas`**

- Requires: *signed in*
- Returns: a list of [`TrustedView`](#trustedview)

**`POST /api/signing/trusted-cas`**

- Requires: `edit_settings`
- Body: `name` string, `pem` string
- Returns: [`TrustedView`](#trustedview)

**`DELETE /api/signing/trusted-cas/{id}`**

- Requires: `edit_settings`
- Returns: a JSON object

**`POST /api/signing/verify`**

Checks any copy of a file: every signature recorded for exactly these bytes.

- Requires: *signed in*
- Upload: `file`
- Returns: a JSON object

### Sso

Single sign-on (TOR 1.3.1) with an OpenID Connect provider, authorization-code flow with PKCE. The browser goes to /api/auth/sso/start, signs in at the provider, and comes back to /api/auth/sso/callback; the ID token's signature, issuer, audience, expiry and nonce are checked, the person is matched to an existing active PAMS account (by the provider's subject id once linked, else by email), and the browser is sent to the login page with a one-time code that it swaps for the usual PAMS token. PAMS roles and permissions still apply.

**`GET /api/auth/sso/callback`**

- Requires: nothing (public)
- Query: `code` (optional), `state` (optional), `error` (optional)
- Returns: no JSON body

**`GET /api/auth/sso/config`**

What the login page needs to show (or hide) the SSO button.

- Requires: nothing (public)
- Returns: a JSON object

**`POST /api/auth/sso/exchange`**

Swaps the one-time code from the callback for a PAMS token (the same response as a password login).

- Requires: nothing (public)
- Body: `code` string
- Returns: a JSON object

**`GET /api/auth/sso/start`**

- Requires: nothing (public)
- Returns: no JSON body

### Stock Card

Stock Card (COA Appendix 58): every receipt and issue of an item with its running balance. Same rules as the Laravel preview, so both systems print the same card.

**`GET /api/reports/stock-card/{itemId}/preview`**

- Requires: `view_inventory`, `view_warehouse`, `manage_warehouse`, `add_stock`, `view_reports`
- Returns: a JSON object

### Supplier

The supplier register (TOR §9): vendors, their compliance documents, blacklisting and suspension, performance ratings, and performance worked out from their actual awards and deliveries. A vendor is eligible for award only once marked so, which needs every required document on file and valid and no sanction in force; a sanction makes it ineligible at once.

**`GET /api/suppliers`**

- Requires: `view_suppliers`, `create_suppliers`, `edit_suppliers`, `view_procurement`, `edit_procurement`, `create_rfq`, `manage_bidding`, `process_award`, `view_contracts`
- Query: `page` (optional) default `1` integer, `active` (optional) boolean, `eligible` (optional) boolean, `search` (optional)
- Returns: a page of [`SupplierView`](#supplierview)

**`POST /api/suppliers`**

- Requires: `create_suppliers`
- Body: `vendor_code` string, `name` string, `tin` string, `philgeps_reg_number` string, `contact_person` string, `email` string, `phone` string, `mobile_number` string, `address` string, `website` string, `credit_terms` string, `notes` string, `is_active` boolean, `is_msme` boolean
- Returns: [`SupplierView`](#supplierview)

**`PUT /api/suppliers/blacklist/{sanctionId}/lift`**

Lifts a sanction; the supplier stays ineligible until it is marked eligible again.

- Requires: `edit_suppliers`
- Body: `sanction_type` string, `grounds` string, `reference_number` string, `start_date` date, `end_date` date, `lifted_reason` string
- Returns: [`SupplierView`](#supplierview)

**`DELETE /api/suppliers/compliance/{documentId}`**

- Requires: `edit_suppliers`
- Returns: [`SupplierView`](#supplierview)

**`PUT /api/suppliers/compliance/{documentId}`**

- Requires: `edit_suppliers`, `create_suppliers`
- Body: `document_type` string, `reference_number` string, `issued_date` date, `expiry_date` date, `remarks` string
- Returns: [`SupplierView`](#supplierview)

**`DELETE /api/suppliers/evaluations/{evaluationId}`**

An evaluator can withdraw their own rating.

- Requires: `edit_suppliers`, `accept_delivery`, `edit_procurement`
- Returns: [`SupplierView`](#supplierview)

**`GET /api/suppliers/options`**

Supplier names for pickers.

- Requires: `view_suppliers`, `create_inventory`, `edit_inventory`, `add_stock`, `manage_warehouse`, `create_rfq`, `manage_bidding`, `conduct_bid_evaluation`, `create_receiving`, `view_procurement`, `view_contracts`, `view_receiving`, `view_payments`, `view_reports`, `generate_reports`
- Returns: a JSON object

**`DELETE /api/suppliers/{id}`**

A supplier with any procurement or stock history stays on the register; deactivate it instead.

- Requires: `delete_suppliers`
- Returns: a JSON object

**`GET /api/suppliers/{id}`**

- Requires: `view_suppliers`, `create_suppliers`, `edit_suppliers`, `view_procurement`, `edit_procurement`, `create_rfq`, `manage_bidding`, `process_award`, `view_contracts`
- Returns: [`SupplierView`](#supplierview)

**`PUT /api/suppliers/{id}`**

- Requires: `edit_suppliers`
- Body: `vendor_code` string, `name` string, `tin` string, `philgeps_reg_number` string, `contact_person` string, `email` string, `phone` string, `mobile_number` string, `address` string, `website` string, `credit_terms` string, `notes` string, `is_active` boolean, `is_msme` boolean
- Returns: [`SupplierView`](#supplierview)

**`POST /api/suppliers/{id}/blacklist`**

- Requires: `edit_suppliers`
- Body: `sanction_type` string, `grounds` string, `reference_number` string, `start_date` date, `end_date` date, `lifted_reason` string
- Returns: [`SupplierView`](#supplierview)

**`POST /api/suppliers/{id}/compliance`**

- Requires: `edit_suppliers`, `create_suppliers`
- Body: `document_type` string, `reference_number` string, `issued_date` date, `expiry_date` date, `remarks` string
- Returns: [`SupplierView`](#supplierview)

**`POST /api/suppliers/{id}/eligibility`**

Marks a supplier eligible (documents complete and valid, no sanction) or ineligible (with a reason).

- Requires: `edit_suppliers`
- Body: `eligible` boolean, `reason` string
- Returns: [`SupplierView`](#supplierview)

**`POST /api/suppliers/{id}/evaluations`**

- Requires: `edit_suppliers`, `accept_delivery`, `edit_procurement`
- Body: `evaluation_date` date, `delivery_rating` integer, `quality_rating` integer, `timeliness_rating` integer, `compliance_rating` integer, `after_sales_rating` integer, `reference` string, `remarks` string
- Returns: [`SupplierView`](#supplierview)

### Tor Report

The forms and registers required by the Terms of Reference (sections 1.4.2, 1.5.2 and 1.6) that the COA exports did not cover: ledger cards, the COA Circular 2022-004 semi-expendable forms, IRP, CNAS, the COS letter of undertaking, and the list reports (lapsing schedule, PIF, registers and master lists) in Excel, CSV or PDF. Entity and fund cluster default to Settings; each download is audited.

**`GET /api/reports/cnas/{lineId}/export`**

Certificate of Non-Availability of Stock for a PS-DBM order line marked not available.

- Requires: `view_reports`, `generate_reports`, `view_procurement`
- Query: `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/cos-undertaking/{userId}/export`**

Letter of Undertaking of a Contract of Service worker for the property issued to them.

- Requires: `view_reports`, `generate_reports`, `view_users`
- Query: `position` (optional), `contract_end` (optional) date, `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/irp/export`**

Invoice Receipt for Property, for a request (request_id) or the assets under one PAR/ICS number (par_number).

- Requires: `view_reports`, `generate_reports`, `view_requests`, `release_stock`, `view_properties`
- Query: `request_id` (optional) integer, `par_number` (optional), `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/itr/{transferId}/export`**

Annex A.5 — Inventory Transfer Report, for a transfer of semi-expendable property.

- Requires: `view_reports`, `generate_reports`, `view_properties`, `transfer_properties`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/lapsing-schedule/export`**

Depreciation lapsing schedule of PPE as at a date, by category with subtotals.

- Requires: `view_reports`, `generate_reports`
- Query: `as_at_date` (optional) date, `format` (optional), `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/master-lists/{list}/export`**

`list` is assets, items, suppliers, users or available-supplies.

- Requires: `view_reports`, `generate_reports`
- Query: `from` (optional) date, `to` (optional) date, `format` (optional), `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/pif/export`**

Property Inventory Form, for one accountable officer (user_id) or everyone.

- Requires: `view_reports`, `generate_reports`, `view_properties`
- Query: `user_id` (optional) integer, `format` (optional), `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/ppe-ledger-card/{propertyId}/export`**

Appendix 70 — Property, Plant and Equipment Ledger Card.

- Requires: `view_reports`, `generate_reports`, `view_properties`
- Query: `as_at_date` (optional) date, `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/registers/iar/export`**

IAR monitoring register.

- Requires: `view_reports`, `generate_reports`
- Query: `from` (optional) date, `to` (optional) date, `format` (optional), `supplier_id` (optional) integer, `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/registers/purchase-orders/export`**

Summary of purchase orders.

- Requires: `view_reports`, `generate_reports`
- Query: `from` (optional) date, `to` (optional) date, `format` (optional), `supplier_id` (optional) integer, `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/registers/ris/export`**

RIS monitoring register (optionally one office).

- Requires: `view_reports`, `generate_reports`
- Query: `from` (optional) date, `to` (optional) date, `department_id` (optional) integer, `format` (optional), `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/registers/{report}/export`**

The reports of TOR 1.6.4 for any dates (the UI offers a month or a year too). `report` is par, ics, property-transactions, stock-movements, physical-counts, wmr, iirup, rlsddp, gate-passes, transfers or reorder-point.

- Requires: `view_reports`, `generate_reports`
- Query: `from` (optional) date, `to` (optional) date, `format` (optional), `item_id` (optional) integer, `count_type` (optional), `entity_name` (optional)
- Returns: a file download

**`GET /api/reports/regspi/export`**

Annex A.4 — Registry of Semi-Expendable Property Issued, for a period and optionally one office.

- Requires: `view_reports`, `generate_reports`
- Query: `from` date, `to` date, `department_id` (optional) integer, `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/rspi/export`**

Annex A.7 — Report of Semi-Expendable Property Issued, for a period.

- Requires: `view_reports`, `generate_reports`
- Query: `from` date, `to` date, `serial_no` (optional), `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/semi-expendable-card/{itemId}/export`**

COA Circular 2022-004 Annex A.1 — Semi-expendable Property Card.

- Requires: `view_reports`, `generate_reports`, `view_inventory`, `view_warehouse`, `manage_warehouse`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/semi-expendable-ledger-card/{itemId}/export`**

COA Circular 2022-004 Annex A.2 — Semi-expendable Property Ledger Card.

- Requires: `view_reports`, `generate_reports`, `view_inventory`, `view_warehouse`, `manage_warehouse`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/supplies-ledger-card/{itemId}/export`**

Appendix 57 — Supplies Ledger Card.

- Requires: `view_reports`, `generate_reports`, `view_inventory`, `view_warehouse`, `manage_warehouse`
- Query: `entity_name` (optional), `fund_cluster` (optional)
- Returns: a file download

**`GET /api/reports/tor/options`**

Choices for the report forms: offices, people, semi-expendable items, issuances, semi-expendable transfers and CNAS lines.

- Requires: `view_reports`, `generate_reports`
- Returns: a JSON object

### User

**`GET /api/users`**

- Requires: `view_users`
- Query: `page` (optional) default `1` integer, `search` (optional), `role_id` (optional) integer
- Returns: a page of [`UserView`](#userview)

**`POST /api/users`**

- Requires: `create_users`
- Body: `name` string, `email` string, `password` string, `password_confirmation` string, `role_id` integer, `department_id` integer
- Returns: a JSON object

**`GET /api/users/all`**

Names for pickers (assignees, signatories). Any signed-in user; no roles or other details.

- Requires: *signed in*
- Returns: a JSON object

**`GET /api/users/departments`**

- Requires: *signed in*
- Returns: a JSON object

**`GET /api/users/roles`**

Roles a user may be given, for the user form.

- Requires: `view_users`, `create_users`, `edit_users`
- Returns: a JSON object

**`DELETE /api/users/{id}`**

- Requires: `delete_users`
- Returns: a JSON object

**`GET /api/users/{id}`**

- Requires: `view_users`
- Returns: a JSON object

**`PUT /api/users/{id}`**

- Requires: `edit_users`
- Body: `name` string, `email` string, `password` string, `password_confirmation` string, `role_id` integer, `department_id` integer
- Returns: a JSON object

**`POST /api/users/{id}/activate`**

- Requires: `edit_users`
- Returns: a JSON object

**`POST /api/users/{id}/deactivate`**

Deactivation keeps the account and everything it recorded; the person can no longer sign in.

- Requires: `edit_users`
- Returns: a JSON object

**`POST /api/users/{id}/unlock`**

Ends a lockout from failed sign-ins before it times out.

- Requires: `edit_users`
- Returns: a JSON object

## Response types

### AbstractRow

`rfq_item_id` integer, `general_description` string, `unit` string, `quantity` integer, `abc_unit_cost` number, `prices` object, `lowest_unit_price` number

### AbstractView

`rfq` RfqView, `suppliers` QuotationView[], `rows` AbstractRow[], `recommended` QuotationView, `quotation_count` integer, `single_source` boolean, `insufficient_quotations` boolean

### AdjustmentView

`id` integer, `adjustment_number` string, `item` ItemRef, `adjustment_type` string, `quantity` integer, `unit_cost` number, `reason` string, `remarks` string, `status` string, `previous_quantity` integer, `new_quantity` integer, `requested_by` string, `requested_by_id` integer, `approved_by` string, `approved_at` date-time, `created_at` date-time, `can_approve` boolean, `can_delete` boolean

### AmendmentView

`id` integer, `amendment_number` integer, `title` string, `description` string, `amount_change` number, `new_end_date` date, `status` string, `preparer` Ref, `approver` Ref, `approved_at` date-time, `remarks` string, `can_approve` boolean

### AppLineView

`id` integer, `ppmp_id` integer, `ppmp_number` string, `department` Ref, `general_description` string, `specifications` string, `unit` string, `procurement_mode` string, `procurement_category` string, `quantity` integer, `estimated_budget` number, `unit_cost` number, `schedule` string, `fund_source` string, `requested_quantity` integer, `remaining_quantity` integer, `requested_amount` number, `utilized_amount` number, `remaining_budget` number, `utilization_rate` number, `status` string

### AppRef

`id` integer, `app_number` string, `fiscal_year` integer

### AppView

`id` integer, `app_number` string, `fiscal_year` integer, `title` string, `status` string, `total_budget` number, `preparer` Ref, `approver` Ref, `approved_at` date-time, `remarks` string, `items_count` integer, `ppmp_count` integer, `requested_amount` number, `utilized_amount` number, `has_pr` boolean, `is_fully_requested` boolean, `pending_ppmps` integer, `items` AppLineView[], `can_edit` boolean, `can_approve` boolean, `can_delete` boolean, `created_at` date-time

### AssemblyView

`is_assembly` boolean, `parts` Part[], `total` number, `threshold` number, `classification` string, `lots` Lot[], `linked` Linked[], `parent` Linked

### Assessment

`id` integer, `fiscal_year` integer, `status` string, `remarks` string, `conducted_by` string, `finalized_at` date-time, `catalogue` Pillar[], `scores` object, `summary` Summary, `evidence` object

### AssetLine

`id` integer, `tag_number` string, `item_name` string, `status` string, `acquisition_cost` number, `accountability_number` string

### AttendeeView

`id` integer, `user_id` integer, `name` string, `committee_role` string, `present` boolean, `remarks` string

### AuthUserView

`id` integer, `name` string, `email` string, `role_id` integer, `department_id` integer, `role` RoleRef, `department` DepartmentView, `permissions` string[], `accessible_modules` string[], `is_super_admin` boolean, `two_factor_enabled` boolean, `has_signature` boolean, `signature_image` string, `password_expired` boolean

### BidderView

`id` integer, `supplier` Ref, `supplier_eligible` boolean, `bid_amount` number, `over_abc` boolean, `bid_security_form` string, `bid_security_amount` number, `bid_security_posted` boolean, `bid_security_required` number, `bid_security_ok` boolean, `results` object, `scores` object, `rated_score` number, `mearb_score` number, `computed_rank` integer, `post_qual_turn` boolean, `is_winner` boolean, `remarks` string

### BiddingView

`id` integer, `bidding_number` string, `title` string, `status` string, `abc` number, `purchase_request` Ref, `department` Ref, `preparer` Ref, `delivery_term` string, `delivery_lead_days` integer, `evaluation_method` string, `price_weight` number, `technical_weight` number, `min_quality_score` number, `pre_proc_date` date-time, `pre_proc_notes` string, `pre_proc_required` boolean, `itb_reference` string, `itb_posted_at` date, `itb_publication` string, `submission_deadline` date-time, `deadline_passed` boolean, `bid_opening_date` date-time, `awarded_supplier` Ref, `awarded_amount` number, `awarded_at` date-time, `bac_resolution` Ref, `noa_number` string, `noa_date` date, `ntp_number` string, `ntp_date` date, `contract_recommendation` string, `failure_reason` string, `failed_count` integer, `remarks` string, `bidders_count` integer, `items` LineView[], `criteria` CriterionView[], `bidders` BidderView[], `protests` ProtestView[], `observers` ObserverView[], `recommended_bidder_id` integer, `next_post_qual_bidder_id` integer, `open_protests` integer, `observers_invited` integer, `timeline` Interval[], `can_edit` boolean, `can_schedule` boolean, `can_open` boolean, `can_record_bids` boolean, `can_evaluate` boolean, `can_award` boolean, `can_noa` boolean, `can_ntp` boolean, `can_fail` boolean, `can_cancel` boolean, `can_delete` boolean, `created_at` date-time

### BudgetRow

`key` string, `label` string, `count` integer, `estimated_budget` number, `requested_amount` number, `utilized_amount` number, `remaining` number, `utilization_rate` number

### BudgetSummary

`fiscal_year` integer, `app_number` string, `app_status` string, `total_estimated` number, `total_requested` number, `total_utilized` number, `total_remaining` number, `utilization_rate` number, `ppmp_count` integer, `ppmp_approved_count` integer, `by_department` BudgetRow[], `by_mode` BudgetRow[], `by_quarter` BudgetRow[]

### CalloffView

`id` integer, `calloff_number` string, `title` string, `supplier` Ref, `amount` number, `calloff_date` date, `status` string, `remarks` string

### CatalogueItem

`id` integer, `code` string, `description` string, `unit` string, `unit_price` number, `item_id` integer, `item_name` string, `is_active` boolean, `price_as_of` date

### CategoryView

`id` integer, `name` string, `parent_id` integer, `is_leaf` boolean, `items_count` integer

### ClearanceView

`id` integer, `certificate_number` string, `user_id` integer, `employee` string, `department` string, `purpose` string, `purpose_details` string, `effective_date` date, `status` string, `outstanding` integer, `assets_turned_over` integer, `requested_by` string, `issued_by` string, `issued_at` date-time, `remarks` string, `created_at` date-time, `outstanding_assets` AssetLine[]

### Compliance

`has_chairperson` boolean, `has_vice_chairperson` boolean, `voting_members` integer, `has_secretariat` boolean, `has_twg` boolean, `is_properly_constituted` boolean, `issues` string[]

### ContractView

`id` integer, `contract_number` string, `title` string, `contract_type` string, `status` string, `supplier` Ref, `purchase_request` Ref, `source` Ref, `source_type` string, `purchase_order` Ref, `amount` number, `original_amount` number, `variation_percent` number, `start_date` date, `end_date` date, `signed_date` date, `delivery_lead_days` integer, `days_to_expiry` integer, `expiring` boolean, `expired` boolean, `description` string, `terms` string, `remarks` string, `preparer` Ref, `delivery_deadline` date, `approved_extension_days` integer, `delivery` Delivery, `ld_estimate` LdEstimate, `ld_amount` number, `ld_days_delayed` integer, `ld_as_of_date` date, `ld_remarks` string, `fund_source` string, `caf_number` string, `calloffs_total` number, `amendments` AmendmentView[], `securities` SecurityView[], `extensions` ExtensionView[], `calloffs` CalloffView[], `performance_secured` boolean, `can_edit` boolean, `can_activate` boolean, `can_complete` boolean, `can_terminate` boolean, `can_delete` boolean, `created_at` date-time

### CountView

`id` integer, `count_number` string, `count_type` string, `count_date` date, `status` string, `purpose` string, `remarks` string, `department` Ref, `category` Ref, `initiated_by` Ref, `started_at` date-time, `completed_by` Ref, `completed_at` date-time, `verified_by` Ref, `verified_at` date-time, `total_items_counted` integer, `total_items_found` integer, `total_discrepancies` integer, `created_at` date-time, `can_edit` boolean, `can_start` boolean, `can_count` boolean, `can_complete` boolean, `can_return` boolean, `can_verify` boolean, `can_delete` boolean, `verify_blocked` string

### CriterionView

`id` integer, `name` string, `weight` number, `is_environmental` boolean

### Delivery

`order_value` number, `delivered_value` number, `undelivered_value` number, `complete` boolean, `last_delivery` date

### DepartmentView

`id` integer, `name` string, `code` string

### Detail

`count` CountView, `summary` Summary, `lines` LineView[], `properties` TagView[]

### DisposalView

`id` integer, `disposal_number` string, `category` string, `disposal_type` string, `status` string, `reason` string, `remarks` string, `approval_remarks` string, `request_date` date, `requester` Ref, `approver` Ref, `approval_date` date, `disposal_date` date, `property_id` integer, `tag_number` string, `serial_number` string, `item_id` integer, `item_code` string, `item_name` string, `unit_of_measure` string, `quantity` integer, `unit_cost` number, `value` number, `book_value` number, `proceeds` number, `can_edit` boolean, `can_submit` boolean, `can_decide` boolean, `can_complete` boolean

### DocumentView (Document)

`id` integer, `title` string, `document_number` string, `type` string, `category` string, `description` string, `documentable_type` string, `documentable_id` integer, `status` string, `current_version` integer, `uploader` Ref, `latest_version` VersionView, `versions` VersionView[], `created_at` date-time, `updated_at` date-time

### DocumentView (Supplier)

`id` integer, `document_type` string, `reference_number` string, `issued_date` date, `expiry_date` date, `status` string, `days_to_expiry` integer, `remarks` string

### Entry (Audit)

`id` integer, `user` Ref, `user_name` string, `user_role` string, `action` string, `auditable_type` string, `auditable_id` integer, `description` string, `ip_address` string, `created_at` date-time, `old_values` JsonNode, `new_values` JsonNode, `user_agent` string, `url` string, `method` string

### EvaluationView

`id` integer, `evaluation_date` date, `delivery_rating` integer, `quality_rating` integer, `timeliness_rating` integer, `compliance_rating` integer, `after_sales_rating` integer, `overall_rating` number, `reference` string, `remarks` string, `evaluator` Ref, `can_delete` boolean

### Evidence

`suggested` integer, `basis` string

### ExtensionView

`id` integer, `requested_days` integer, `approved_days` integer, `reason` string, `status` string, `requested_date` date, `approver` Ref, `approved_at` date-time, `remarks` string

### FundView

`id` integer, `fund_name` string, `custodian` Ref, `amount` number, `fund_cluster` string, `status` string, `remarks` string, `disbursed` number, `pending` number, `in_replenishment` number, `balance` number, `available` number, `unrequested` number, `voucher_count` integer, `max_per_voucher` number, `can_manage` boolean, `can_record` boolean, `can_request_replenishment` boolean, `created_at` date-time, `vouchers` VoucherView[], `replenishments` ReplenishmentView[]

### GatePassView

`id` integer, `gate_pass_number` string, `property_id` integer, `tag_number` string, `serial_number` string, `item_name` string, `holder` string, `received_by` string, `purpose` string, `date_released` date, `expected_return_date` date, `actual_return_date` date, `condition_on_release` string, `condition_on_return` string, `status` string, `overdue` boolean, `remarks` string, `released_by` string, `returned_by` string, `received_back_by` string, `can_edit` boolean, `can_return` boolean

### IarRef

`id` integer, `iar_number` string, `status` string, `acceptance_status` string, `delivery_date` date

### IarView

`id` integer, `iar_number` string, `purchase_order` Ref, `supplier` Ref, `purchase_request` Ref, `department` Ref, `delivery_date` date, `inspection_date` date, `inspector` Ref, `status` string, `total_items_ordered` integer, `total_items_received` integer, `total_items_accepted` integer, `total_items_rejected` integer, `observations` string, `remarks` string, `acceptance_status` string, `accepter` Ref, `accepted_at` date-time, `items` LineView[], `can_edit` boolean, `can_accept` boolean, `can_reject` boolean, `can_delete` boolean, `created_at` date-time

### Interval

`key` string, `label` string, `from` date, `to` date, `max_days` integer, `elapsed_days` integer, `breached` boolean, `pending` boolean

### InventoryEntry

`id` integer, `item` ItemRef, `user` Ref, `action` string, `quantity` integer, `previous_quantity` integer, `new_quantity` integer, `reference_type` string, `reference_id` integer, `notes` string, `created_at` date-time

### IssueView

`id` integer, `item` ItemRef, `request_id` integer, `holder` Ref, `department` Ref, `custodian_name` string, `ics_number` string, `quantity` integer, `quantity_returned` integer, `outstanding` integer, `issued_date` date, `status` string, `remarks` string

### ItemRef (Adjustment)

`id` integer, `code` string, `name` string, `unit_of_measure` string, `current_stock` integer

### ItemRef (InventoryViews)

`id` integer, `code` string, `name` string, `unit_of_measure` string

### ItemRef (LogTrail)

`id` integer, `code` string, `name` string

### ItemRef (PhysicalCount)

`id` integer, `code` string, `name` string, `unit_of_measure` string, `category` string, `on_hand` integer

### ItemRef (Property)

`id` integer, `code` string, `name` string, `unit_of_measure` string, `classification` Classification

### ItemRef (Receiving)

`id` integer, `code` string, `name` string, `unit_of_measure` string

### ItemRef (SemiExpendable)

`id` integer, `code` string, `name` string, `unit_of_measure` string

### ItemView

`id` integer, `code` string, `name` string, `description` string, `category_id` integer, `category` CategoryRef, `unit_of_measure` string, `weighted_average_cost` number, `current_stock` integer, `min_stock` integer, `max_stock` integer, `classification` Classification, `classification_label` string, `is_expandable` boolean, `is_semi_expandable` boolean, `is_assembly` boolean, `is_available` boolean, `stock_status` StockStatus, `total_value` number, `suggested_order` integer, `has_photo` boolean, `stocks` StockView[], `created_at` date-time, `updated_at` date-time

### Label

`type` string, `id` integer, `code` string, `title` string, `subtitle` string, `accountability` string, `url` string, `data_uri` string

### LdEstimate

`deadline` date, `as_of` date, `days_delayed` integer, `basis` number, `rate` number, `amount` number, `capped` boolean, `may_terminate` boolean

### Line (PsDbm)

`id` integer, `purchase_request_item_id` integer, `ps_catalogue_item_id` integer, `catalogue_code` string, `description` string, `unit` string, `quantity` integer, `unit_price` number, `amount` number, `availability` string, `release_ground` string, `cnas_number` string, `cnas_date` date, `cnas_valid_until` date

### LineView (Bidding)

`id` integer, `purchase_request_item_id` integer, `general_description` string, `specifications` string, `unit` string, `quantity` integer, `abc_unit_cost` number, `abc_total` number

### LineView (Issuance)

`id` integer, `item_id` integer, `code` string, `name` string, `unit_of_measure` string, `classification` Classification, `current_stock` integer, `usable_stock` integer, `unit_cost` number, `quantity` integer, `approved_quantity` integer, `status` string

### LineView (PhysicalCount)

`id` integer, `item` ItemRef, `system_quantity` integer, `counted_quantity` integer, `variance` integer, `unit_cost` number, `variance_value` number, `location` string, `remarks` string, `counted_by` Ref, `counted_at` date-time, `moved_since_cutoff` integer, `adjustment_number` string

### LineView (PurchaseOrder)

`id` integer, `description` string, `specifications` string, `unit` string, `quantity` integer, `unit_cost` number, `total_cost` number, `delivered` integer, `accepted` integer, `outstanding` integer, `rfq_item_id` integer, `bidding_item_id` integer

### LineView (PurchaseRequest)

`id` integer, `app_item_id` integer, `general_description` string, `unit` string, `quantity` integer, `estimated_unit_cost` number, `estimated_total` number, `specifications` string, `procurement_mode` string, `procurement_category` string, `fund_source` string, `is_sustainable` boolean, `remarks` string

### LineView (Receiving)

`id` integer, `purchase_order_item_id` integer, `description` string, `specifications` string, `unit` string, `quantity_ordered` integer, `quantity_received` integer, `quantity_accepted` integer, `quantity_rejected` integer, `status` string, `remarks` string, `unit_cost` number, `item` ItemRef, `stock_id` integer

### LineView (Rfq)

`id` integer, `purchase_request_item_id` integer, `general_description` string, `specifications` string, `unit` string, `quantity` integer, `abc_unit_cost` number, `abc_total` number, `delivery_lead_time` string

### Linked

`id` integer, `tag_number` string, `serial_number` string, `item` string, `holder` string

### LocationView

`id` integer, `name` string, `building` string, `floor` string, `room` string, `description` string, `is_active` boolean, `assets` integer

### LoginEntry

`id` integer, `email` string, `ip_address` string, `user_agent` string, `successful` boolean, `failure_reason` string, `created_at` date-time

### Lot

`id` integer, `label` string, `brand` string, `specifications` string, `quantity` integer, `unit_cost` number

### MaintenanceView

`id` integer, `property_id` integer, `tag_number` string, `item_name` string, `holder` string, `location` string, `maintenance_type` string, `description` string, `scheduled_date` date, `interval_months` integer, `status` string, `overdue` boolean, `completed_date` date, `performed_by` string, `cost` number, `remarks` string, `created_by` string, `completed_by` string

### MeetingView

`id` integer, `meeting_number` string, `title` string, `meeting_date` date-time, `venue` string, `agenda` string, `status` string, `minutes` string, `caller` Ref, `remarks` string, `attendees_count` integer, `quorum` Quorum, `attendees` AttendeeView[], `resolutions` ResolutionRef[], `can_edit` boolean, `created_at` date-time

### MemberView

`id` integer, `user` Ref, `full_name` string, `position` string, `office` string, `designation` string, `appointment_order_no` string, `appointment_date` date, `term_start` date, `term_end` date, `is_active` boolean, `term_expired` boolean, `sequence` integer, `remarks` string

### MembersView

`data` MemberView[], `compliance` Compliance

### MoveView

`id` integer, `from` string, `to` string, `moved_by` string, `remarks` string, `moved_at` date-time

### NotificationView

`id` integer, `type` string, `title` string, `message` string, `related_type` string, `related_id` integer, `is_read` boolean, `created_at` date-time

### ObserverView

`id` integer, `name` string, `organization` string, `observer_type` string, `invited` boolean, `invited_date` date, `attended` boolean, `remarks` string

### Options

`modes` string[], `categories` string[], `fund_sources` string[], `quarters` string[], `ceilings` object

### OrderView

`id` integer, `apr_number` string, `status` string, `purchase_request` Ref, `department` string, `vs_reference` string, `submitted_date` date, `payment_mode` string, `payment_reference` string, `payment_date` date, `amount_paid` number, `dr_number` string, `dr_date` date, `delivery_mode` string, `purchase_order` Ref, `purchase_order_status` string, `total_amount` number, `remarks` string, `prepared_by` Ref, `items` Line[], `can_edit` boolean, `can_submit` boolean, `can_pay` boolean, `can_deliver` boolean, `can_cancel` boolean, `can_mark_unavailable` boolean

### Part

`id` integer, `description` string, `specifications` string, `serial_number` string, `quantity` integer, `remarks` string, `stock_id` integer, `unit_cost` number, `amount` number, `warranty` string, `warranty_months` integer, `warranty_start` date

### Performance

`ratings` integer, `avg_overall` number, `avg_delivery` number, `avg_quality` number, `satisfactory` boolean, `purchase_orders` integer, `purchase_orders_value` number, `contracts` integer, `contracts_value` number, `rfqs_won` integer, `biddings_won` integer, `deliveries` integer, `on_time_deliveries` integer, `on_time_rate` number, `units_received` integer, `units_rejected` integer, `rejection_rate` number, `rejected_deliveries` integer, `liquidated_damages` number

### Pillar

`key` string, `title` string, `indicators` String[][]

### PillarSummary

`title` string, `average` number, `maturity` string

### PoView

`id` integer, `po_number` string, `status` string, `supplier` Ref, `purchase_request` Ref, `department` Ref, `source` Source, `order_date` date, `delivery_date` date, `late` boolean, `delivery_address` string, `total_amount` number, `remarks` string, `preparer` Ref, `approver` Ref, `fund_source` string, `caf_number` string, `items_count` integer, `items` LineView[], `inspections` IarRef[], `can_edit` boolean, `can_send` boolean, `can_confirm` boolean, `can_cancel` boolean, `can_delete` boolean, `can_receive` boolean, `created_at` date-time

### PpmpLineView

`id` integer, `general_description` string, `specifications` string, `code` string, `unit` string, `quantity` integer, `estimated_unit_cost` number, `estimated_total` number, `procurement_mode` string, `procurement_category` string, `is_sustainable` boolean, `schedule` string, `fund_source` string, `remarks` string

### PpmpView

`id` integer, `ppmp_number` string, `department` Ref, `fiscal_year` integer, `title` string, `status` string, `total_estimated_budget` number, `revision` integer, `preparer` Ref, `reviewer` Ref, `approver` Ref, `submitted_at` date-time, `approved_at` date-time, `return_reason` string, `remarks` string, `items_count` integer, `items` PpmpLineView[], `app_number` string, `can_edit` boolean, `can_submit` boolean, `can_review` boolean, `can_decide` boolean, `can_delete` boolean, `created_at` date-time

### PrView

`id` integer, `pr_number` string, `department` Ref, `app` AppRef, `fiscal_year` integer, `requester` Ref, `request_date` date, `purpose` string, `status` string, `stage_label` string, `current_step` integer, `priority` string, `total_amount` number, `caf_number` string, `caf_certifier` Ref, `caf_certified_at` date-time, `caf_amount` number, `caf_remarks` string, `remarks` string, `return_reason` string, `cancelled_at` date-time, `items_count` integer, `items` LineView[], `approvals` StepView[], `procurement_modes` string[], `procurement_categories` string[], `fund_sources` string[], `funds_covered` boolean, `can_edit` boolean, `can_submit` boolean, `can_cancel` boolean, `can_delete` boolean, `can_act` boolean, `can_certify` boolean, `created_at` date-time

### PropertyView

`id` integer, `tag_number` string, `serial_number` string, `item` ItemRef, `brand` string, `specifications` string, `warranty` string, `warranty_months` integer, `warranty_expiry` date, `holder` Ref, `holder_office` string, `assigned_date` date, `accountability_number` string, `status` string, `condition` string, `acquisition_cost` number, `acquisition_date` date, `useful_life_years` integer, `salvage_value` number, `is_depreciable` boolean, `accumulated_depreciation` number, `book_value` number, `monthly_depreciation` number, `location` Ref, `has_photo` boolean, `can_edit` boolean, `can_transfer` boolean, `can_delete` boolean

### ProtestView

`id` integer, `protest_type` string, `supplier` Ref, `filed_by` string, `grounds` string, `filed_date` date, `protest_fee` number, `status` string, `resolution` string, `resolved_date` date

### Quorum

`voting_seated` integer, `voting_present` integer, `needed` integer, `met` boolean

### QuotationView

`id` integer, `supplier` Ref, `quotation_number` string, `submitted_at` date, `total_amount` number, `is_responsive` boolean, `is_winner` boolean, `over_abc` boolean, `supplier_eligible` boolean, `remarks` string, `lines` QuoteLineView[]

### QuoteLineView

`rfq_item_id` integer, `unit_price` number, `total_price` number, `over_abc` boolean

### Readiness

`required` Requirement[], `missing` string[], `expired` string[], `ready` boolean

### Ref

`id` integer, `name` string

### ReplenishmentView

`id` integer, `report_number` string, `report_date` date, `total` number, `voucher_count` integer, `status` string, `requested_by` Ref, `replenished_by` Ref, `replenished_date` date, `reference` string, `remarks` string, `can_replenish` boolean

### RequestView (Issuance)

`id` integer, `request_number` string, `status` string, `priority` string, `purpose` string, `request_date` date, `department` Ref, `requester` Ref, `custodian_name` string, `ris_number` string, `ris_date` date, `ics_number` string, `ics_date` date, `par_number` string, `par_date` date, `remarks` string, `items` LineView[], `total_requested` integer, `total_issued` integer, `can_edit` boolean, `can_delete` boolean, `can_process` boolean, `can_reject` boolean, `created_at` date-time, `updated_at` date-time

### RequestView (Signing)

`id` integer, `request_number` string, `title` string, `file_name` string, `file_size` integer, `file_sha256` string, `requested_by_id` integer, `requested_by` string, `signer_id` integer, `signer` string, `status` string, `platform_reference` string, `remarks` string, `signed_at` date-time, `created_at` date-time, `can_sign` boolean, `can_cancel` boolean, `signatures` SignatureView[]

### Requirement

`document_type` string, `present` boolean, `valid` boolean, `expiry_date` date

### ResolutionRef

`id` integer, `resolution_number` string, `title` string, `status` string

### ResolutionView

`id` integer, `resolution_number` string, `title` string, `resolution_type` string, `content` string, `status` string, `meeting` Ref, `purchase_request` Ref, `preparer` Ref, `approver` Ref, `approved_at` date-time, `remarks` string, `used_by` string[], `can_edit` boolean, `can_submit` boolean, `can_approve` boolean, `can_archive` boolean, `can_delete` boolean, `created_at` date-time

### RfqView

`id` integer, `rfq_number` string, `title` string, `procurement_method` string, `single_source` boolean, `status` string, `abc` number, `deadline` date-time, `deadline_passed` boolean, `delivery_term` string, `delivery_lead_days` integer, `description` string, `justification` string, `negotiated_ground` string, `invited_suppliers` string[], `purchase_request` Ref, `department` Ref, `preparer` Ref, `awarded_supplier` Ref, `awarded_at` date-time, `bac_resolution` Ref, `remarks` string, `noa_number` string, `noa_date` date, `ntp_number` string, `ntp_date` date, `items_count` integer, `quotations_count` integer, `items` LineView[], `quotations` QuotationView[], `can_edit` boolean, `can_issue` boolean, `can_close` boolean, `can_cancel` boolean, `can_delete` boolean, `can_quote` boolean, `can_award` boolean, `can_noa` boolean, `can_ntp` boolean, `created_at` date-time

### SanctionView

`id` integer, `sanction_type` string, `grounds` string, `reference_number` string, `start_date` date, `end_date` date, `status` string, `in_force` boolean, `lifted_at` date, `lifted_reason` string, `recorded_by` Ref

### ScheduleView

`type` string, `id` integer, `reference` string, `title` string, `status` string, `supplier` Ref, `base_amount` number, `scheduled_total` number, `unscheduled` number, `paid_total` number, `released_total` number, `outstanding` number, `overdue_count` integer, `ld_assessed` number, `ld_deducted` number, `ld_available` number, `linked_notice` string, `can_manage` boolean, `can_schedule` boolean, `tranches` TrancheView[]

### Score

`score` integer, `remarks` string, `means_of_verification` string

### SecurityView

`id` integer, `security_type` string, `form` string, `reference_number` string, `amount` number, `percentage` number, `posted_date` date, `validity_date` date, `status` string, `remarks` string, `required` number, `sufficient` boolean

### SignatureView

`id` integer, `signer` string, `algorithm` string, `certificate_subject` string, `certificate_issuer` string, `certificate_serial` string, `certificate_not_after` date-time, `trusted_ca` string, `source` string, `signed_at` date-time

### Source

`type` string, `id` integer, `number` string, `noa_number` string, `mode` string

### StepView

`step` integer, `label` string, `required_permission` string, `status` string, `actor` Ref, `acted_at` date-time, `remarks` string

### StockView

`id` integer, `item_id` integer, `item` ItemRef, `brand_name` string, `specifications` string, `warranty` string, `warranty_months` integer, `warranty_expiry` date, `warranty_status` string, `quantity` integer, `unit_cost` number, `total_value` number, `lot_number` string, `expiration_date` date, `days_until_expiration` integer, `received_date` date, `supplier_id` integer, `supplier` SupplierRef, `location` string

### Summary (Apcpi)

`pillars` object, `overall` number, `maturity` string

### Summary (PhysicalCount)

`lines` integer, `counted` integer, `matched` integer, `short_count` integer, `short_value` number, `over_count` integer, `over_value` number, `expected` integer, `found` integer, `missing` integer, `missing_value` number, `not_in_masterlist` integer, `out_of_scope` integer, `condition_changed` integer, `unserviceable` integer

### SupplierView

`id` integer, `vendor_code` string, `name` string, `tin` string, `philgeps_reg_number` string, `contact_person` string, `email` string, `phone` string, `mobile_number` string, `address` string, `website` string, `credit_terms` string, `notes` string, `is_active` boolean, `is_eligible` boolean, `is_msme` boolean, `is_sanctioned` boolean, `sanction` string, `awardable` boolean, `eligibility` Readiness, `expiring_documents` integer, `documents` DocumentView[], `sanctions` SanctionView[], `evaluations` EvaluationView[], `performance` Performance, `can_edit` boolean, `can_delete` boolean, `created_at` date-time

### TagView

`id` integer, `property_id` integer, `tag_number` string, `description` string, `holder` Ref, `department` Ref, `book_value` number, `condition_on_record` string, `in_masterlist` boolean, `in_scope` boolean, `result` string, `finding` string, `observed_condition` string, `location` string, `remarks` string, `checked_by` Ref, `checked_at` date-time, `removable` boolean

### TrancheView

`id` integer, `payable_type` string, `payable_id` integer, `payable_reference` string, `supplier` string, `sequence_no` integer, `description` string, `percentage` number, `amount` number, `due_date` date, `deliverable` string, `responsibility_center` string, `mfo_pap` string, `uacs_code` string, `status` string, `overdue` boolean, `days_overdue` integer, `ors_number` string, `dv_number` string, `paid_date` date, `payment_method` string, `payment_reference` string, `amount_paid` number, `retention_amount` number, `ld_deducted` number, `withholding_tax` number, `remarks` string, `created_by` Ref, `approved_by` Ref, `approved_at` date-time, `paid_by` Ref, `can_edit` boolean, `can_approve` boolean, `can_pay` boolean

### TransferView (InventoryViews)

`id` integer, `transfer_number` string, `item` ItemRef, `from_location` string, `to_location` string, `quantity` integer, `transfer_date` date, `remarks` string, `transferred_by` string

### TransferView (Property)

`id` integer, `transfer_type` string, `reference_number` string, `transfer_date` date, `from` Ref, `to` Ref, `reason` string, `recorded_by` string, `property_id` integer, `tag_number` string, `item_name` string

### Trend

`year` integer, `month` integer, `label` string, `total_requests` integer, `fulfilled` integer, `rejected` integer, `open` integer, `units_issued` integer

### TrustedView

`id` integer, `name` string, `subject` string, `fingerprint` string, `not_after` date-time, `created_at` date-time

### UserView

`id` integer, `name` string, `email` string, `role_id` integer, `department_id` integer, `role` RoleSummary, `department` DepartmentView, `two_factor_enabled` boolean, `has_signature` boolean, `email_verified_at` date-time, `password_changed_at` date-time, `created_at` date-time, `updated_at` date-time, `is_active` boolean, `locked_until` date-time

### Verification

`checked` integer, `intact` integer, `unsealed` integer, `altered` integer[], `complete` boolean

### VersionView

`id` integer, `version_number` integer, `original_filename` string, `mime_type` string, `file_size` integer, `file_size_human` string, `checksum` string, `remarks` string, `uploader` Ref, `created_at` date-time

### VoucherView

`id` integer, `pcv_number` string, `voucher_date` date, `payee` string, `particulars` string, `amount` number, `responsibility_center` string, `status` string, `recorded_by` Ref, `approved_by` Ref, `approved_at` date-time, `replenishment` Ref, `replenished` boolean, `remarks` string, `can_approve` boolean, `can_cancel` boolean

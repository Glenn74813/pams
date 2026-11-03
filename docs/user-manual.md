# PAMS user's manual

How to use each page of PAMS. This file is generated from the in-app help (the **Help** menu) by `frontend-react/scripts/gen-user-manual.mjs`; change the help articles and run the script again, rather than editing it here.

## Contents

- **Getting started**
  - [Getting started](#getting-started)
  - [Dashboard](#dashboard)
  - [Separation of Duties](#separation-of-duties)
  - [Notifications](#notifications)
  - [QR Codes and Scanning](#qr-codes)
  - [Frequently asked questions](#faq)
- **Procurement**
  - [Procurement Planning (PPMP and APP)](#procurement-planning)
  - [Purchase Requests](#purchase-requests)
  - [PS-DBM (Common-Use Supplies)](#ps-dbm)
  - [RFQ and Alternative Modes](#rfq)
  - [Competitive Bidding](#bidding)
  - [Unsolicited Offers with Bid Matching](#unsolicited-offers)
  - [BAC Management](#bac)
  - [Purchase Orders](#purchase-orders)
  - [Contracts](#contracts)
  - [Suppliers (Vendors)](#suppliers)
- **Inventory**
  - [Receiving and Inspection (IAR)](#receiving)
  - [Items (Item Master)](#items)
  - [Warehouse / Stockroom](#warehouse)
  - [Issuance (RIS, ICS and PAR)](#issuance)
  - [Physical Count](#physical-count)
  - [Stock Adjustments](#stock-adjustments)
  - [Semi-Expendable Property](#semi-expendable)
  - [Inventory Monitoring](#monitoring)
  - [Disposal and Write-Off](#disposal)
- **Assets**
  - [Gate Pass](#gate-pass)
  - [Assets and Property](#assets)
  - [My Assets](#my-assets)
  - [Asset Maintenance](#maintenance)
  - [Property Turnover & Clearance](#clearance)
- **Reports & Compliance**
  - [Reports (COA forms)](#reports)
  - [Procurement Reports](#procurement-reports)
  - [APCPI Self-Assessment](#apcpi)
  - [Documents](#documents)
  - [Audit Trail](#audit-trail)
  - [Log Trails](#log-trails)
  - [Digital Signatures](#digital-signatures)
  - [Ledgers, Registers & Master Lists](#ledgers-registers)
- **Administration**
  - [Users and Roles](#users-roles)
  - [Settings](#settings)
  - [Roles and Permissions](#roles-and-permissions)
  - [Payments from Other Systems (Integrations)](#external-payments)
  - [Locations, photos and single sign-on](#locations-sso)

# Getting started

<a id="getting-started"></a>

## Getting started

Signing in, two-factor sign-in, the password rules, finding your way around the menu, and signing out.

### Sign in

1. Open the system address your administrator gave you.
2. Enter your **Email Address** and **Password** and click **Login**.
3. If two-factor sign-in is on for your account, enter the 6-digit code from your authenticator app in **Authenticator Code** and click **Verify & Login**.

You land on the page you first asked for, or the Dashboard. There is no self-service password reset; "forgot password?" tells you to contact your system administrator.

> **Caution:** After 5 failed attempts within 15 minutes the account is locked for 15 minutes. Wrong passwords and wrong 2FA codes both count, and attempts made while locked keep the lock going, so wait before trying again. The page tells you how many attempts are left.

### Turn on two-factor sign-in

1. Go to **Settings → My account → Two-factor sign-in** and click **Set up**.
2. In your authenticator app (Google Authenticator, Microsoft Authenticator, Authy), add an account using the setup key shown. On a phone you can tap "open this link" instead.
3. Type the 6-digit code the app shows and click **Turn on**.

To turn it off, enter your password and click **Turn off**. If you lose your phone, ask the administrator for help.

### Password rules

- At least 8 characters (your administrator may require more), at most 72.
- At least one uppercase letter, one lowercase letter, one number and one special character.
- Not a commonly used password (for example "p@ssw0rd" or "admin@123").
- Must not contain the name part of your email address.

Change your password under **Settings → My account → Change password**.

### Finding your way around

- The sidebar shows only the modules your role can open, grouped under Procurement, Vendor Management, Inventory, Assets / Property, Reports & Compliance and Administration. Click a group to open or close it. On a phone, ☰ opens the menu.
- The top bar has **⌖ Scan** (look up items, assets and gate passes) and the 🔔 notification bell.
- Most pages have a search box and filters above the list. Click a record's number or name to open its details, where the action buttons are.
- Buttons only appear for actions you are allowed to take on that record right now.

### Signing out

Click your name at the bottom of the sidebar, then **Logout**. You are also signed out after a period without activity (30 minutes by default) and 60 minutes after signing in, so save your work as you go.

### Tips

- Use the "?" link on a page to jump to its help.
- Printing and downloads are recorded in the audit trail.

<a id="dashboard"></a>

## Dashboard

Your to-do list ("Waiting on you"), your own open items, and summaries of inventory, assets, procurement and suppliers for the roles that need them.

### Who can use it

- The menu entry needs view_dashboard (all seeded roles have it). Each section below is shown only to people with the related permissions.

### What it is for

Start here every day. Each line in **Waiting on you** links to the page where you can act.

### Waiting on you

| Line | Shown to |
|---|---|
| Purchase requests awaiting your approval | PR signatories (excluding your own PRs) |
| Supply requests to approve | approve_requests |
| Stock adjustments to approve | approve_adjustments |
| Disposal requests to approve | approve_disposal |
| Inspection reports awaiting acceptance | accept_delivery |
| Physical counts to verify | approve_adjustments or manage_warehouse |
| Gate passes past their return date | view_gate_pass |

### Other sections

- **Mine** (everyone): my open supply requests, my open purchase requests, assets I hold and their value.
- **📦 Inventory**: items, stock value, low and out of stock, pending requests, expiring and expired lots, most issued items (30 days), requests by department (90 days).
- **🏢 Assets**: assets on record, assigned, available, under repair, acquisition cost, book value, disposals in progress.
- **🛒 Procurement — FY** (choose the **Fiscal year**): PRs in routing, open RFQs, biddings under way, POs awaiting delivery, active contracts, overdue and due-soon deadlines, APP utilisation, PRs by status, next deadlines.
- **🏪 Suppliers**: active suppliers, those with documents expiring in 30 days, largest suppliers by PO value, best-rated suppliers.

### Statuses and what moves them

Not applicable; the dashboard only reads.

### Tips

- If a count looks wrong, open the linked page and check its filters; the dashboard uses the same data.

<a id="separation-of-duties"></a>

## Separation of Duties

The rules that make sure the person who prepares a record is not the one who approves it.

### What it is for

These checks are built in and cannot be switched off. If you see a message like "…so someone else has to…", ask a colleague with the right permission to take the next step. Super Admin is exempt from all of them, so keep Super Admin accounts for system administration only.

### The rules

| Module | Rule |
|---|---|
| PPMP | The preparer cannot review, approve or return it |
| APP | The person who consolidated it cannot approve it |
| Purchase request | The requester cannot sign any step or certify funds |
| BAC resolution | The drafter cannot approve or return it |
| RFQ | The preparer cannot award it |
| Competitive bidding | The preparer cannot award it |
| Purchase order | The preparer cannot approve and send it |
| Receiving | The inspector cannot accept or reject the delivery |
| Contract | The preparer cannot put it in force; an amendment's drafter cannot approve it |
| Issuance | The requester cannot approve their own request |
| Stock adjustment | The person who filed it cannot approve it |
| Physical count | Whoever set up, closed or took part in the count cannot verify it |
| Disposal | The requester cannot approve or reject it; only the drafter can edit it |
| Users | You cannot change your own role or delete your own account |
| Supplier ratings | Only the evaluator can withdraw a rating |

### Setting up roles for it

- Put makers and approvers in different roles, or at least make sure at least two people hold each approval permission.
- Receiving: the seeded Warehouseman and Supply Officer roles can both inspect and accept, so two different people must do the two steps.

### Tips

- Everything each person does is recorded in the audit trail, so the trail shows who prepared and who approved.

<a id="notifications"></a>

## Notifications

The bell in the top bar tells you when something needs your action or concerns you, plus daily alerts for stock and deadlines.

### Who can use it

- Everyone. You receive a notification when your role holds the permission for the next step, or when the record is yours. You are not notified of your own actions.

### Using the bell

1. The 🔔 shows how many unread notifications you have (refreshed every minute).
2. Click it to see the latest 20.
3. Click a notification to open the related page; it is marked read. **Mark all read** clears the count.

Read notifications older than 30 days are deleted every Sunday.

### What sends a notification

| Event | Who is told |
|---|---|
| PPMP submitted / approved / returned | PPMP reviewers / the preparer |
| APP approved (procurement schedule) | Procurement, BAC and RFQ staff |
| PR waiting at a step / approved / returned | That step's signatories / the requester |
| BAC meeting scheduled, resolution for approval / returned / approved | BAC users / the drafter |
| RFQ ready for award, RFQ awarded | Award users / RFQ and procurement staff |
| Invitation to Bid posted, protest filed, bidding awarded | BAC and bidding staff |
| PO confirmed by the supplier | Receiving staff (delivery expected) |
| Delivery inspected; short or rejected delivery | Acceptance staff; procurement staff |
| Contract terminated, extension requested, LDs reached 10% | Contract staff |
| Supplier sanctioned | Procurement, BAC and award staff |
| Supply request approved or rejected; low stock after issue | The requester; warehouse staff |
| Asset issued, transferred or returned | The person concerned |
| Disposal submitted / decided | Disposal approvers / the requester |
| Physical count ready for verification / sent back / verified | Count staff / the initiator |

Daily alerts (reorder, expiring stock, bidding/RFQ/PO deadlines, contracts, supplier documents) and a quarterly "items due for procurement" reminder are also sent; see Inventory Monitoring for times.

### Tips

- Reorder and expiry alerts for the same item are repeated at most once every 7 days.

<a id="qr-codes"></a>

## QR Codes and Scanning

Print QR stickers for items, assets and gate passes, and look records up with a scanner, the camera or by typing.

### Who can use it

- Item labels: view_inventory, view_warehouse or manage_warehouse. Asset stickers: view_properties, or the holder for their own asset. Gate pass codes: view_gate_pass.
- The **⌖ Scan** button in the top bar: everyone signed in.

### What it is for

Every sticker holds a link to the record with a short check code, so a scan can show that the label is genuine.

### Print labels

- Items: **▦ QR labels for this page** on the Items tab.
- Assets: **▦ QR sticker** in an asset, or **▦ QR stickers for this page** on the register (up to 200).
- Gate passes: shown on the gate pass print view.

A sticker shows the agency, the code (item code, tag or GP number), the name, and the category and unit or the serial number and PAR/ICS number.

### Scan or look up a record

1. Click **⌖ Scan** in the top bar.
2. Scan with a USB or Bluetooth scanner, or type an item code, asset tag, gate-pass number, serial number or the QR link, then click **Find**.
3. On phones and browsers that support it, click **📷 Camera** to use the camera.
4. Click **Open in Items / Assets / Gate Pass** to go to the record.

### Scanning without signing in

Anyone who scans a sticker with a phone sees a "Verified label" page without signing in. For an asset this shows its tag, serial, brand, status, condition, and the accountable person, office and PAR/ICS number. For an item: code, category, unit and quantity on hand. For a gate pass: status, purpose, who carried it, dates and the asset. A tampered or unknown code shows "This QR code is not valid".

> **Caution:** Because these details are visible to anyone holding the sticker, do not stick asset labels where the public can scan them if that is a concern for your office.

### Statuses and what moves them

Not applicable.

### Forms and documents produced

QR label sheets.

### Tips

- Changing an asset tag makes its old sticker invalid; reprint it.
- Physical counts of assets accept scanned tags in the Property tag field.

<a id="faq"></a>

## Frequently asked questions

Quick answers to the questions supply and procurement staff ask most.

### I cannot see a module in the menu.

Your role does not hold any permission that opens it. Ask your administrator to check "Roles and permissions" and update your role.

### The button I need is missing.

Buttons appear only when your role allows the action and the record is in the right status (for example, only drafts can be edited). If you prepared the record, the approval button is hidden from you: see Separation of Duties.

### The message says "someone else has to…".

This is a separation-of-duties rule. Ask a colleague with the approval permission to do the next step.

### I forgot my password, or my account is locked.

Ask your system administrator to set a new password (Users & Roles → ✎). A lock clears itself 15 minutes after the last failed attempt; stop trying in the meantime.

### Why was I signed out?

After the idle limit (30 minutes by default) or 60 minutes after signing in. Sign in again; save often when filling long forms.

### Will my item get an ICS or a PAR?

It follows the item's COA classification: Semi-Expendable gets an ICS, PPE gets a PAR, and consumables get a RIS only. The capitalisation threshold in Settings → System (default ₱50,000) decides the classification.

### I submitted a request but stock has not gone down.

Stock is taken only when an approver clicks **Approve & issue**, not when the request is submitted.

### Issuing fails because of expired stock.

Expired lots are never issued. Lower the issue quantity to what is usable, then write off the expired lots through Disposal (stock write-off) and the WMR.

### A delivery was inspected but is not in stock.

A second person with accept_delivery must open the IAR in Receiving and click **Accept into stock…**.

### My purchase request does not appear when creating an RFQ or bidding.

Only fully approved PRs are listed, and only lines not already on another live RFQ or bidding. For RFQs, the line's planned mode must match the RFQ mode.

### The award is refused.

Check that the supplier is active and eligible (documents complete and not expired, no sanction), the BAC resolution is approved and for the same PR, no protest is pending, and you did not prepare the RFQ or bidding.

### I cannot delete a record.

Most records can be deleted only as drafts. Items with stock, suppliers with history, users with stock transactions and assets with movements are kept for audit; deactivate, cancel or dispose of them instead.

### An Excel download fails with an access error.

Some exports need view_reports or generate_reports in addition to the module permission. Ask your administrator.

### How do I print a form?

Use the 🖨 button on the record; it opens a print-ready form. Use your browser's print dialog to choose the printer or "Save as PDF". COA Excel forms are under Reports.

### Still stuck?

> **Tip:** Note the record number and the exact message, and contact your system administrator.

# Procurement

<a id="procurement-planning"></a>

## Procurement Planning (PPMP and APP)

Departments prepare their PPMPs; approved PPMPs are consolidated into one Annual Procurement Plan per fiscal year, and budget use is tracked against purchase requests.

### Who can use it

- The page opens for anyone with one of: view_procurement_planning, create_ppmp, view_own_ppmp, edit_own_ppmp, delete_own_ppmp, submit_own_ppmp, approve_ppmp, manage_app or monitor_budget.
- The **PPMP** tab is always shown. The **Annual Procurement Plan** and **Budget monitoring** tabs need view_procurement_planning, manage_app or monitor_budget.
- Agency-wide users (view_procurement_planning, approve_ppmp or manage_app) see every PPMP. Users with create_ppmp only see their own department's PPMPs. Users with only the "own PPMP" permissions see only the PPMPs they prepared.
- Create a PPMP: create_ppmp. Submit: create_ppmp or submit_own_ppmp. Review, approve or return: approve_ppmp. Consolidate, edit and approve the APP: manage_app.
- Seeded roles: Department Head and Purchaser can prepare PPMPs; Budget Officer reviews PPMPs and manages the APP.

### What it is for

Each office lists what it plans to buy next fiscal year in a Project Procurement Management Plan (PPMP). Once approved, the PPMPs are consolidated into the Annual Procurement Plan (APP). Purchase requests are then drawn on APP lines, and the **Budget monitoring** tab shows how much of the plan has been used.

Use the **FY** selector at the top to switch fiscal year. It offers last year up to two years ahead and starts on next year.

### Prepare a PPMP

1. On the **PPMP** tab, click **+ New PPMP**.
2. Choose the **Department** (only agency-wide users get a choice; everyone else uses their own department), the **Fiscal year**, and type a **Title**. **Remarks** are optional.
3. Click **+ Add line** for each item or project. Fill in **Description**, **Quantity** (at least 1), **Est. unit cost (₱)** and **Mode of procurement**. Optional: **Category** (Goods by default), **Source of funds**, **Schedule** (Q1 to Q4), **Accounting code** (UACS object code), **Technical specifications** and **Green / sustainable procurement**.
4. Click **Save draft**. The PPMP gets a number like PPMP-2027-0001 (the fiscal year, not the calendar year).
5. When it is complete, open it and click **Submit for review**.

A PPMP needs at least 1 line and can have up to 200.

> **Caution:** Small Value Procurement is capped at ₱2,000,000 and Direct Acquisition at ₱200,000 per line by default (the administrator can change the caps). Shopping is no longer a mode under RA 12009. A low amount does not by itself allow Negotiated Procurement, which has its own grounds. The form warns "Over the ₱… ceiling for this mode", and saving is refused until you lower the amount or choose another mode such as Competitive Bidding.

### Review a PPMP

1. Open a submitted PPMP and click **Take up for review** to show the preparer it is being looked at (optional).
2. Click **Approve**, or click **Return** and say what needs revising.

Returning a PPMP sends it back to the preparer for editing and raises its revision number by one. The preparer is notified either way.

### Consolidate and approve the APP

1. Go to the **Annual Procurement Plan** tab and click **⚙ Consolidate APP**. This creates APP-<FY>-001, titled "Annual Procurement Plan <FY>", from every approved PPMP of that year.
2. If more PPMPs are approved later, a warning appears with a **Reconsolidate** button. Click it to add them (allowed until the APP is approved).
3. Click **Open APP** and then **Edit lines** to adjust description, mode, schedule, quantity or budget.
4. Someone other than the person who consolidated it clicks **Approve APP**.

Approving the APP notifies procurement, BAC and RFQ staff that the procurement schedule is out. An APP can be deleted only before approval and only if no purchase request uses it; its PPMPs can then be consolidated again.

### Budget monitoring

The **Budget monitoring** tab shows, by department, by mode and by quarter: Planned, On purchase requests, Utilized (fully approved PRs only) and Remaining.

Each APP line moves from Planned to In progress to Completed as purchase requests draw on it.

### Statuses and what moves them

| Status | What moves it | Who |
|---|---|---|
| Draft | Created with + New PPMP | Preparer |
| Submitted | Submit for review | create_ppmp or submit_own_ppmp |
| Under review | Take up for review | approve_ppmp |
| Approved | Approve (from Submitted or Under review) | approve_ppmp, not the preparer |
| Returned | Return, with a reason | approve_ppmp, not the preparer |

Only Draft and Returned PPMPs can be edited or deleted. Approved PPMPs are locked; once consolidated the list shows "in APP-…".

The APP is Consolidated until someone clicks **Approve APP**; an approved APP cannot be edited, reconsolidated or deleted.

### Forms and documents produced

This module prints nothing itself. The APP Report (with Excel export) is on the **Procurement Reports** page.

### Tips

- Plan each line with the mode you will actually use. RFQs and biddings only accept PR lines whose planned mode matches.
- You cannot lower an APP line's quantity below what purchase requests already take.
- The person who prepared a PPMP cannot review it, and the person who consolidated the APP cannot approve it.

<a id="purchase-requests"></a>

## Purchase Requests

Raise a purchase request (usually drawn on the approved APP), route it through four signatures and a Certificate of Availability of Funds, and print the Appendix 60 form.

### Who can use it

- The page opens for: view_procurement, create_procurement, approve_procurement, or any of the signatory permissions pr_approve_department, pr_approve_budget, pr_approve_accounting, pr_approve_bac.
- Holders of view_procurement, approve_procurement or any pr_approve_* permission see every PR. Everyone else sees only the PRs they raised.
- Raise a PR: create_procurement or edit_procurement (seeded: Purchaser, End User).
- Each signature needs its own permission: Department Head (pr_approve_department), Budget Officer (pr_approve_budget), Accounting (pr_approve_accounting), BAC Secretariat (pr_approve_bac).
- Certify funds (CAF): pr_approve_budget or pr_approve_accounting.

### What it is for

A purchase request (PR) is the formal request to buy. Once all four signatories have signed, the PR can go to an RFQ (alternative modes) or to Competitive Bidding.

### Raise a purchase request

1. Click **+ New Purchase Request**.
2. Check the **Requesting office** (only users with edit_procurement or view_procurement can change it). Pick the **Annual Procurement Plan**, or "Not in the APP". Set **Priority** and type the **Purpose**. **Remarks** are optional.
3. Add lines. Under **Add from APP-…**, click a line to bring it in with its remaining quantity and unit cost (only your office's lines that still have a balance are shown). Use **+ Line not in the APP** for anything else.
4. Click **Save draft**. The PR gets a number like PR-202610-00001 (year and month of creation).
5. Open it and click **Submit for approval**.

> **Caution:** Quantities and costs cannot exceed what is left on the APP line. Drafts count against the balance too, so delete drafts you will not use.

### Sign a purchase request

Signatories start on the **Awaiting my signature** tab. Open a PR and click **Sign as <step>**. The steps go in this order:

1. Department Head
2. Budget Officer
3. Accounting
4. BAC Secretariat

To send it back, click **Return** and say what needs revising. The requester is notified and can edit and click **Resubmit**; routing then starts again from step 1.

### Certify funds (CAF)

1. While the PR is in routing, a Budget Officer or Accounting user clicks **Certify funds**.
2. Enter the **Amount certified (₱)** (at least the PR total) and any **Remarks**, then click **Issue CAF**. A CAF number like CAF-2026-0001 is assigned.

The Budget Officer cannot sign until funds are certified; the button is greyed out with "Certify funds first". If the PR total later goes above the certified amount, a new CAF is needed.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | Save draft |
| In routing (Submitted / Under review) | Submit for approval; the Stage column shows whose signature is next |
| Approved | The BAC Secretariat signs (the last step) |
| Returned | The current signatory clicks Return |
| Cancelled | Cancel PR by the requester or an edit_procurement user, from Draft, Returned or In routing |

Only Draft and Returned PRs can be edited. Draft, Returned and Cancelled PRs can be deleted; the number is not reused. An approved PR cannot be cancelled. Cancelling gives the APP balance back.

### Forms and documents produced

**🖨 PR form** prints the Purchase Request (Appendix 60) in any status, drafts included. "Requested by" is the requester and "Approved by" is whoever signed the Department Head step.

### Tips

- You cannot sign or certify funds for a PR you raised yourself.
- approve_procurement makes you an alternate signatory for any step (e.g. an officer-in-charge), but not for two steps of the same PR.
- If you start typing a return reason and then decide to sign instead, clear the text box first: the text is sent with the signature as remarks.
- The office of a PR is fixed when it is created. To change it, cancel the PR and raise a new one.

<a id="ps-dbm"></a>

## PS-DBM (Common-Use Supplies)

Buy common-use supplies and equipment from the Procurement Service of the DBM without bidding: Agency Procurement Request, Virtual Store order, payment, PS Delivery Receipt, then inspection in Receiving.

### Who can use it

- The page opens for view_procurement, edit_procurement or approve_procurement.
- edit_procurement: prepare, place, pay, record delivery and cancel requests, and keep the PS price catalogue (seeded: Admin, Purchaser).

### What it is for

Government agencies buy common-use supplies and equipment (CSE) from the Procurement Service of the DBM (LOI 755, EO 359 s. 1989, AO 17 s. 2011) instead of bidding them out. PS sells through the PhilGEPS **Virtual Store**: the agency uploads its APP-CSE, funds its e-Wallet, orders, pays, and picks up or receives the goods against PS's Delivery Receipt.

In PAMS, plan those lines as **Procurement from PS-DBM** on the PPMP, APP and purchase request. The APP-CSE form in Procurement Reports lists them for upload to the Virtual Store.

### Keep the PS price catalogue

1. Open the **PS price catalogue** tab and click **+ Add item**.
2. Enter the **PS item code**, **Description**, **Unit**, **PS price** and **Price as of**, as shown in the Virtual Store.
3. When PS changes a price, edit the item. Untick **In the current PS catalogue** for items PS no longer sells.

### Prepare the Agency Procurement Request

1. Click **+ New request** and choose the **Approved purchase request**. Only approved PRs with PS-DBM lines not yet on a request are listed.
2. Tick the lines, pick the **PS catalogue item** (or type the PS price) and set the **Qty**. The PS price may not exceed the PR estimate.
3. Click **Save draft**. The request is numbered APR-YYYY-NNNN.

### Place, pay and receive

1. Order the same items in the Virtual Store, then click **Placed in Virtual Store…** and enter its order reference.
2. If a line cannot come from PS, click **Cannot buy from PS…** and choose the ground: zero inventory (the CNAS, downloaded from the PhilGEPS Electronic Catalogue), specifications that do not meet the need, too little stock, or a BAC resolution on efficiency, practicality or economic viability. Enter the supporting document. With too little stock, enter what PS can still supply: that part stays on the request and only the shortfall leaves. Otherwise the whole line drops off the request and can go to Small Value Procurement, Direct Acquisition or Competitive Bidding.
3. Pay PS (cash, LDDAP-ADA, check, or funds already with PS) and click **Record payment…**. The amount must equal the request total.
4. When PS issues its Delivery Receipt, click **Record Delivery Receipt…**: pick-up at the depot or delivery. PS releases a request in one lot.
5. An order to PS-DBM opens in **Receiving**, numbered as the APR. Inspect, accept and take the items into stock there as with any delivery.

> **Caution:** A paid request cannot be cancelled here. Ask PS for a refund, or leave the amount with PS as unutilized funds for the next order. Wrongly ordered items may be returned to PS within a month of the Delivery Receipt, with a letter of explanation.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | + New request |
| In Virtual Store | Placed in Virtual Store… |
| Paid | Record payment… |
| Delivered (DR) | Record Delivery Receipt… (then Receiving) |
| Cancelled | Cancel request (before payment), or every line not available |

### Tips

- PS-DBM lines cannot go on an RFQ or bidding unless they were first marked as not available from PS on one of those grounds.
- The Agency Procurement Report (Procurement Reports) counts these requests under "Procurement from PS-DBM".

<a id="rfq"></a>

## RFQ and Alternative Modes

Buy through alternative modes (Small Value Procurement, Negotiated, Direct Contracting and others): issue the RFQ, record quotations, award, and issue the NOA and NTP.

### Who can use it

- The page opens for view_rfq, create_rfq or generate_abstract.
- create_rfq: create, edit, issue, close, cancel and delete RFQs, and record or evaluate quotations (seeded: Purchaser, BAC Secretariat).
- process_award: award, issue the Notice of Award and Notice to Proceed (seeded: BAC Chairperson).

### What it is for

Use this module for any approved PR line planned under an alternative mode: Small Value Procurement, Negotiated Procurement, Direct Contracting, Direct Acquisition, Repeat Order, Limited Source Bidding, Competitive Dialogue, Unsolicited Offer with Bid Matching, Direct Sales or Direct Procurement for Science, Technology and Innovation. Lines approved earlier as Shopping go to Small Value Procurement.

Negotiated Procurement needs a ground from Section 35, such as two failed biddings, an emergency or a take-over. After two failed biddings, choose at least three invited suppliers from the list (including those disqualified earlier), or type a name that is not on it; the ABC may rise at most 20% over the last failed bidding, and each supplier's best offer is recorded as its quotation. The award may proceed even with one response. Every other ground needs a written justification.

Direct Contracting, Direct Acquisition, Repeat Order, Direct Sales and Direct Procurement for Science, Technology and Innovation are single-source: they need a written justification and take one quotation only. Unsolicited Offer with Bid Matching needs the justification but can take matching offers.

### Failed Limited Source Bidding or Competitive Dialogue

These two modes can fail as a bidding does (IRR 64.1). Click **Declare failure** with the ground. The lines are free for a re-bid, and the failure counts towards the two failed biddings that allow Negotiated Procurement, together with any failed Competitive Bidding on the same purchase request.

### Create and issue an RFQ

1. Click **+ New RFQ** and choose the **Approved purchase request**. Only approved PRs with lines not already on a live RFQ or bidding are listed.
2. Pick the **Mode**. It must match the mode planned on the PR lines (shown as "Planned: …").
3. Fill in **Title**, **Deadline for quotations**, **Delivery term**, **Delivery within (days)** and **Instructions to suppliers**. For single-source modes, fill in the **Justification**.
4. Tick the lines to include and set **Quantity** and **ABC / unit** (not more than the PR unit cost).
5. Choose when **Supplier documents are submitted**: with the offer, during evaluation or before the Notice of Award. Direct Contracting, SVP and most Negotiated grounds need this to be issued.
6. Click **Save draft**, then **Issue to suppliers** when ready. The deadline must still be in the future.

### Record and evaluate quotations

1. Click **+ Record quotation**. Choose the **Supplier** (or **+ New supplier…**), enter the **Quotation no.**, the **Received** date and the **Quoted / unit** for every line.
2. Mark a quotation **Non-responsive** (with a reason) if it fails the requirements. **Restore** undoes this.
3. After the deadline, click **Close & open quotations**. Users with process_award are notified that it is ready for award.

> **Caution:** Late quotations are refused: the received date cannot be after the deadline. A supplier added here for the first time is registered as not verified and not eligible, so verify their documents in Suppliers before the award.

### Award, NOA and NTP

1. On a closed RFQ, click **Award…**. Choose **Award to** and the approved **BAC resolution** for this PR.
2. If fewer than 3 quotations were received (other than single-source modes), explain why in **Remarks**.
3. Click **Issue Notice of Award**, then **Issue Notice to Proceed**. Dates default to today.

The award must go to the lowest responsive quotation from an eligible supplier within the ABC. To pass over a lower quotation, mark it non-responsive first. The BAC resolution must be approved, be a Resolution to award, Award or Recommendation, be for the same PR, and not already back another award.

Once the NOA is issued, the award appears in **Purchase Orders** under **+ New PO from award**.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | Save draft |
| Open for quotations | Issue to suppliers |
| Closed — for award | Close & open quotations (only after the deadline) |
| Awarded | Award… by a process_award user who did not prepare the RFQ |
| Cancelled | Cancel RFQ, any time before the award (a reason is needed unless it is a draft) |

Only drafts can be edited or deleted.

### Forms and documents produced

**🖨 Abstract** prints the Abstract of Quotations (any status except Draft), with signature lines for two BAC Members, the Vice-Chairperson and the Chairperson. NOA and NTP numbers (NOA-YYYY-NNNN, NTP-YYYY-NNNN) are recorded; the notices themselves are not printed from here.

### Tips

- Check the supplier is eligible before you award. Ineligible suppliers appear in the award list but the award is refused.
- The person who prepared the RFQ cannot award it.
- After two failed biddings of a PR, its Competitive Bidding lines can go to an RFQ under Negotiated Procurement directly.

<a id="bidding"></a>

## Competitive Bidding

Run public bidding under RA 12009 from the pre-procurement conference to the Notice to Proceed, including bid security, four-stage evaluation, protests and failure of bidding.

### Who can use it

- The page opens for view_bidding, manage_bidding or process_award.
- manage_bidding: create and edit, set criteria, pre-procurement conference, Invitation to Bid, open bids, record bids, declare failure, cancel, protests and observers (seeded: BAC Chairperson, BAC Secretariat).
- Evaluation and scores: manage_bidding or conduct_bid_evaluation.
- Award, NOA and NTP: process_award.

### What it is for

For PR lines planned as Competitive Bidding (or with no mode). Evaluation is "Lowest calculated responsive bid", "MEARB" (rated criteria plus price, which weighs 15–40%, with an optional minimum quality score) or "MARB" (the highest-rated bid whose price is within the ABC).

### Set up the bidding

1. Click **+ New bidding** and choose the **Approved purchase request**. A warning shows if the PR already has failed biddings.
2. Choose the **Evaluation** method (for MEARB, set the **Price weight (%)**, default 70), fill in **Title**, **Delivery term**, **Delivery within (days)**, and the lines with quantity and ABC per unit (not more than the PR cost). Save.
3. MEARB only: click **Set criteria**. The weights must total 100, and this must be done before the Invitation to Bid.
4. **Pre-procurement conference**: record the date and notes. Required when the ABC is over ₱5,000,000 for goods, ₱10,000,000 for infrastructure or ₱2,000,000 for consulting; otherwise optional (shown as "skipped").
5. **Post Invitation to Bid**: enter the **ITB reference**, **Posted on** (not in the future), **Posted at**, the **Deadline for bids** (after posting) and **Bid opening** (defaults to the deadline).

### Open and record bids

1. After the deadline, click **Open bids**.
2. For each bidder, click **+ Record bid** and enter the **Bidder**, **Bid as read (₱)**, the **Bid security** form and **Security amount (₱)**.

| Bid security form | Minimum |
|---|---|
| Cash, cashier's or manager's check | 2% of the ABC |
| Bank draft, guarantee or letter of credit | 5% of the ABC |
| Surety bond | 5% of the ABC |
| Bid Securing Declaration | None |

> **Caution:** Record every bid read at the opening. A bid entered late can still be added during evaluation until post-qualification starts, and a bid can be corrected until it has been evaluated; striking out a bid is only possible before evaluation.

### Evaluate

For each bid, click **Pass <stage>** or **Fail** (with a reason). The stages go in order: Eligibility, Technical, Financial, Post-qualification.

- Eligibility cannot pass without enough bid security.
- Financial cannot pass a bid above the ABC.
- Post-qualification starts only when every bid has finished the first three stages. It goes to the top-ranked bidder and moves down only if that bidder fails. An ineligible supplier cannot pass.
- MEARB and MARB: prices stay sealed (IRR 58). Record each bid without a price, pass eligibility and technical, enter **Scores**, then click **Open financial proposals**. MEARB opens those at or above the minimum quality score; MARB opens only the highest rating. Then **Record price** and evaluate. Scores and technical results are final from that point.

### Award, NOA and NTP

1. Click **Award…**. The winner is fixed as the post-qualified bidder. Choose the approved **BAC resolution** and, optionally, a **Contract recommendation**.
2. Click **Issue Notice of Award**, then **Issue Notice to Proceed**.

Open protests block the award. The award then appears in **Purchase Orders** and **Contracts**.

### Protests, observers and failure

- **Reconsideration & protests**: file a Request for Reconsideration or a Protest once the bidding is advertised. Then **Take up**, **Deny** or **Uphold** (BAC resolution text required), or mark **Withdrawn**.
- **Observers**: record COA, CSO, professional organisation or other observers. Invite at least two.
- **Declare failure** (with the ground) during Invitation, Bid opening or Evaluation. The PR lines are freed. After two failures the system points to Negotiated Procurement.
- The **Timeline** panel shows the legal maximums: ITB to opening 50 days, opening to NOA 40, ITB to NOA 90, NOA to NTP 17.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | Save |
| Pre-procurement | Pre-procurement conference recorded |
| Invitation | Post Invitation to Bid |
| Bid opening | Open bids |
| Evaluation | Automatically, at the first evaluation result or score |
| Awarded | Award… by a process_award user who did not prepare the bidding |
| Failed | Declare failure |
| Cancelled | Cancel bidding, before award or failure |

Terms and criteria can be edited only in Draft or Pre-procurement. Only drafts can be deleted.

### Forms and documents produced

From Bid opening onward: **🖨 Abstract of Bids** (printed) and **Abstract (Excel)**. The ITB, NOA and NTP are recorded by reference number; they are not printed from here.

### Tips

- Numbering: PB-YYYY-NNNN for biddings; NOA and NTP numbers are shared with RFQs.
- The person who prepared the bidding cannot award it.

<a id="unsolicited-offers"></a>

## Unsolicited Offers with Bid Matching

Follow an unsolicited offer of a new concept or technology through assessment, evaluation and negotiation, then invite comparative bids and let the original offeror match the lowest.

### Who can use it

- The page opens for view_bidding, manage_bidding, process_award or conduct_bid_evaluation.
- manage_bidding: record each step. process_award: award on the bidding.

### What it is for

RA 12009 Section 30 lets an agency consider an offer it did not ask for when the offer brings a new concept or technology, for Goods or Consulting Services only. PAMS shows the next deadline of each offer and refuses a step once its deadline has passed.

### The steps

| Step | Deadline | If missed |
|---|---|---|
| Assess the need and notify the offeror | 20 days from receipt | Deemed rejected |
| Complete documents: concept and cost, proof of concept, eligibility, terms with consent to bid matching | 30 days from the notice | Deemed rejected; a resubmission is a new offer |
| Evaluate: new concept basis, funds, eligibility | 60 days, plus up to 30 with the HoPE's reason | Record the extension, or reject |
| Negotiate | Starts within 5 days of the notice; ends within 15 days | Failure of negotiation ends the procurement |
| Certify the original offeror; bid security | 10 days from the certification | Refused |
| Publish the Invitation to Bid | 30 days from the certification | Refused on the bidding |
| Original offeror matches the lowest responsive bid | 15 days from the bidding result | The award goes to the lowest bid |

### Comparative bids and the award

1. Once the bid security is recorded, draft a lowest-price bidding under **Competitive Bidding** from the approved purchase request.
2. On the offer, click **Link bidding**, then post the Invitation to Bid and run the bidding as usual.
3. After post-qualification, or if no bid came in or all failed at opening, click **Take the bidding result**.
4. Record whether the original offeror matched, then award on the bidding. The award and price follow the result.

> **Caution:** An offeror that withdraws more than 5 days after the notice is barred for 1 year, then 2 years, then for good. PAMS records this as a sanction on the supplier.

### Statuses and what moves them

Received, Awaiting documents, Under evaluation, Under negotiation, Original offeror, Comparative bidding, Bid matching, then Awarded, Rejected, Withdrawn or Failed negotiation.

### Forms and documents produced

None of its own; the Notice of Award and contract come from the linked bidding.

### Tips

- If several offers cover the same concept, the first complete one prevails; reject the others with that reason. They can still bid.

<a id="bac"></a>

## BAC Management

Keep the Bids and Awards Committee roster, schedule and record meetings with attendance, and draft and approve BAC resolutions.

### Who can use it

- The page opens for view_bac, manage_bac_meetings, manage_bac_resolutions or conduct_bid_evaluation.
- Meetings and the **Composition** roster: manage_bac_meetings.
- Resolutions: manage_bac_resolutions.
- Seeded: BAC Chairperson and BAC Secretariat have both.

### What it is for

Three tabs: **Meetings**, **Resolutions** and **Composition**. Approved award resolutions are required before any RFQ or bidding can be awarded.

### Composition (members)

1. Click **+ Add member**.
2. Optionally link a **System user**, then fill in **Full name** and **Designation** (Chairperson, Vice-Chairperson, Member, Provisional member, Secretariat, Technical Working Group).
3. Add **Position**, **Office**, **Office order no.**, **Order date** and **Term from / to**, and tick **Active**.

A banner checks the RA 12009 composition: a chairperson, a vice-chairperson, 5 or 7 voting members and a secretariat. It is a reminder only; nothing is blocked. Removing a member who has attended meetings makes them inactive instead of deleting them.

### Meetings

1. Click **New meeting** and enter the **Title** and **Date and time** (plus **Venue** and **Agenda**).
2. At the meeting, click **Start**. Attendance is pre-filled from active members whose term has not ended; add others if needed.
3. Record the attendance and click **Mark held**.

Quorum is a majority of the seated voting members. Once held, only the minutes and remarks can change, and the meeting cannot be deleted. A meeting cited by a resolution cannot be cancelled.

### Resolutions

1. Click **Draft BAC resolution**. Enter the **Title** and **Type** (Resolution to award, Recommendation, Award, Mode of procurement, Declaration of failure, Other).
2. For Resolution to award and Award, choose the **Purchase request**. Optionally choose the meeting it was **Adopted at**, and type the **Resolution** text.
3. Click **Save draft**, then **Submit for approval**.
4. Another resolutions user clicks **Approve**, or **Return** (with a reason) to send it back to draft.
5. Approved resolutions can be **Archived** later.

If a meeting is cited, it must be held and have had a quorum, or approval is refused.

### Statuses and what moves them

| Record | Statuses |
|---|---|
| Meeting | Scheduled → In session (Start) → Held (Mark held); or Cancelled |
| Resolution | Draft → For approval (Submit) → Approved; Return sends it to Draft; Approved → Archived |

Only draft resolutions can be edited or deleted, and not once an award cites them.

### Forms and documents produced

Meetings and resolutions are numbered BAC-MTG-YYYY-001 and BAC-RES-YYYY-001. There is no printed resolution or minutes form; attach the signed copy with **+ Attach** in Documents if needed.

### Tips

- Do not archive a resolution before its award is made: the award needs an approved (not archived) resolution.
- The person who drafted a resolution cannot approve or return it.
- One resolution can back only one live award.

<a id="purchase-orders"></a>

## Purchase Orders

Issue the purchase order on an award that has its Notice of Award, have it approved and sent, record the supplier's conforme, and track delivery.

### Who can use it

- The page opens for view_procurement.
- Create, edit, delete, record "Supplier confirmed" and cancel: edit_procurement (seeded: Purchaser).
- Approve & send: edit_procurement or approve_procurement, and not the person who prepared the PO.
- Users without procurement permissions see only POs for their own PRs.

### What it is for

A PO is issued against an RFQ or bidding award that already has its Notice of Award. Delivery against the PO is then recorded in **Receiving**.

### Create a PO

1. Click **+ New PO from award** and choose the **Award**. Only awards with a Notice of Award and no live PO are listed.
2. Check the **Delivery date** (defaults to today plus the award's delivery days) and **Place of delivery**, and add **Remarks** if needed.
3. Prices: RFQ POs use the winning quotation. Bidding POs are pre-priced in proportion to the winning bid; you may adjust lines, but they must add up to the awarded bid.
4. Click **Save draft**. The PO gets a number like PO-2026-0001.

### Approve, send and confirm

1. Someone other than the preparer opens the draft and clicks **Approve & send**. A delivery date is required.
2. When the supplier signs the conforme, click **Supplier confirmed**. Receiving staff are notified that a delivery is expected.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | Save draft |
| Sent to supplier | Approve & send |
| Confirmed | Supplier confirmed |
| Partly delivered | Automatically, when IARs cover part of the order |
| Delivered | Automatically, when IARs cover the whole order |
| Cancelled | Cancel PO (draft, sent or confirmed; a reason is needed unless it is a draft) |

Only drafts can be edited or deleted; deleting frees the award for a new PO. A PO cannot be cancelled once any delivery has been inspected against it. Rejected IARs do not count as delivered.

### Forms and documents produced

**🖨 PO form** prints the Purchase Order (Appendix 61) with the penalty clause, fund source, CAF number and Conforme signature block. Attach the signed copy in the Attachments panel.

### Tips

- Only active, eligible suppliers can receive a PO.
- The delivery date cannot be before the order date.

<a id="contracts"></a>

## Contracts

Manage the contract on an award: performance security, amendments, time extensions, liquidated damages and framework call-offs.

### Who can use it

- The page opens for view_contracts, create_contracts or edit_contracts.
- Create and delete drafts: create_contracts. Edit, record securities, amendments, extension requests and call-offs: create_contracts or edit_contracts.
- Put in force, complete, terminate, approve amendments, grant or deny extensions, return or forfeit securities and record LD assessments: edit_contracts.

### What it is for

For awards that need a formal contract. Types: Goods, Services, Infrastructure, Consulting and Framework Agreement.

### Draft a contract

1. Click **+ New contract** and choose the **Award** (it must have its NOA and no live contract).
2. Fill in **Title**, **Type**, **Effective from**, **Ends on**, **Signed on**, **Delivery within (days)**, **Scope** and **Special conditions**. The price is the awarded amount.
3. Click **Save draft**. The number looks like CON-2026-0001.

> **Tip:** Create the PO before the contract. The contract links to the PO only if it already exists, and delivery tracking and LDs rely on that link.

### Put the contract in force

1. In **Securities**, click **+ Record security** and enter the Kind, Form, **Amount**, Reference no. and Valid until.
2. Make sure the start, end and signed dates are set.
3. Someone other than the preparer clicks **Put in force**.

| Performance security form | Minimum |
|---|---|
| Cash/check, bank draft or guarantee, letter of credit | 5% of the price (10% for Infrastructure) |
| Surety bond | 30% of the price |

The security must be valid until the end date. Framework Agreements do not need one.

### While the contract is in force

- **Amendments**: **+ Amendment**, describe what changes, any price change and new end date, then **Draft amendment**. Someone else approves it. Total changes are capped at 10% of the original price.
- **Extension requests**: **+ Record request** with the days asked and the ground. It must be filed before the deadline; only one can be pending. Grant (up to the days asked) or Deny (with remarks).
- **Delivery & liquidated damages**: shows the deadline, delivered value and LDs (days late × 0.1% × undelivered value, capped at 10%; at the cap the contract may be terminated). Click **Record assessment** to record them.
- **Call-offs** (Framework Agreements): **+ Call-off** with a title and amount within the agreement, then Issue and Complete (or Cancel). Numbered CO-YYYY-NNNN.
- The term and price can only change through an amendment. Scope, conditions and remarks can still be edited.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | Save draft |
| In force | Put in force (not by the preparer) |
| Completed | Complete, once everything is delivered and no extension is pending; unrecorded LDs are assessed automatically |
| Terminated | Terminate, from Draft or In force, with a ground |

### Forms and documents produced

No printed form; attach the signed contract in the Attachments panel. Securities can be returned (the performance security only after completion) or forfeited with a ground.

### Tips

- Use the **Ending within 30 days** filter to see contracts about to expire.

<a id="suppliers"></a>

## Suppliers (Vendors)

Register suppliers, keep their eligibility documents, verify eligibility, record sanctions and rate their performance.

### Who can use it

- The page opens for view_suppliers, create_suppliers or edit_suppliers.
- Register: create_suppliers. Edit, verify eligibility, sanctions and removing documents: edit_suppliers. Add documents: create_suppliers or edit_suppliers. Delete: delete_suppliers.
- Rate a supplier: edit_suppliers, accept_delivery or edit_procurement.

### What it is for

A supplier under a sanction, or inactive, cannot be awarded under any mode. What else it needs depends on the mode, following GPPB Circular 06-2026, whose list is exclusive: nothing more may be asked.

### Register a supplier

1. Click **+ Register supplier**.
2. Fill in the **Registered name** (must be unique) and as many details as you have: Vendor code (VND-NNNN is assigned if blank), TIN (123-456-789-000), PhilGEPS reg. no., Credit terms, Contact, Email, Phone, Mobile, Website, Address, Notes and MSME.
3. Save, then open the supplier to upload documents.

### Verify eligibility

**Competitive selection** (Competitive Bidding, Limited Source Bidding, Competitive Dialogue, Unsolicited Offer): a valid PhilGEPS Certificate of Registration (Platinum) on file and **Mark eligible**. Post-qualification also needs the Income or Business Tax Return. The rest of that list (ongoing contracts, SLCC, NFCC, bid security) is checked bid by bid.

**Other modes in the circular** (Direct Contracting, SVP and seven Negotiated grounds) go by the documents on file, with no need to mark the supplier eligible. The **Documents by mode** table shows what each needs. Amounts matter: the OSS applies above ₱50,000 and the tax return above ₱500,000 where the circular says so; PCAB and NFCC apply to infrastructure, and the license or CV to consulting.

**Modes outside the circular** (Direct Acquisition, Repeat Order, Direct Sales, Agency-to-Agency, UN agencies, direct retail, Defense, NGO, Community, Science and Technology, Sagip Saka, international events) ask for no documents here; follow their own guidelines.

Set the supplier's **Kind**: a Platinum certificate replaces the ID and permit; an individual may show BIR registration instead of a permit on the scientific and highly technical grounds; government lessors and artists not in business are exempt where the circular says. An expired permit counts with its renewal receipt; collect the valid permit before paying.

1. Upload each document with its expiry date.
2. For competitive selection, click **Mark eligible**. If something is missing or expired, the message lists it.

Use **Mark ineligible…** (with a reason) to withdraw eligibility. Removing or changing a required document also drops eligibility. Deactivating a supplier clears it too.

### Sanctions

**+ Record sanction**: Suspension (with an Until date) or Blacklist, plus the grounds. The supplier becomes ineligible at once and procurement staff are notified. **Lift…** (with a reason) removes it; verify the supplier again afterwards.

### Ratings and performance

Rate **Delivery** and **Quality** (1 to 5), and optionally Timeliness, Compliance and After-sales, against a PO or contract reference. One rating per evaluator per reference; only the evaluator can withdraw it. An average of 3 or more counts as satisfactory.

The **Performance** panel shows awards, POs and their value, on-time deliveries, units rejected, average rating and liquidated damages.

### Statuses and what moves them

The list shows the supplier as Eligible, Not verified, Inactive, or the sanction type. Filter by Active, Eligible, Not verified, Inactive or All.

### Tips

- A supplier with transaction history cannot be deleted; deactivate it instead.
- Suppliers added from an RFQ quotation start as not verified.

# Inventory

<a id="receiving"></a>

## Receiving and Inspection (IAR)

Record deliveries against confirmed POs, inspect them, and accept them into stock with the Inspection and Acceptance Report.

### Who can use it

- The page opens for view_receiving.
- Record, edit or delete an inspection: create_receiving (the inspection committee).
- Accept into stock or reject: accept_delivery, and not the person who recorded the inspection.
- Seeded: Warehouseman and Supply Officer have both; make sure two different people do the two steps.

### What it is for

Two tabs: **Expected deliveries** (confirmed or partly delivered POs) and **Inspection & acceptance**. Accepting a delivery creates stock lots in Items at the PO unit cost.

### Record and inspect a delivery

1. On **Expected deliveries** (or in the PO), click **Record delivery**.
2. Enter **Delivered on** (not before the PO date, not in the future) and **Inspected on** (between delivery and today), plus **Observations**.
3. For each line enter **Accepted** and **Rejected**. They must add up to what was received. If anything is rejected, fill in **Why rejected?**.
4. Click **Save inspection**. Users with accept_delivery are notified.

Accepted quantities cannot be more than what is still outstanding on the PO.

### Accept into stock

1. Open the IAR and click **Accept into stock…**.
2. For each accepted line pick the **Inventory item** (or **+ New catalogue item**), and optionally a **Lot no.**, **Expires** date (not before delivery) and **Location**.
3. Confirm. Each line becomes a new stock lot dated the delivery date, shown on the stock card as a receipt.

The COA capitalisation threshold is checked: a semi-expendable item at or above the threshold, or a PPE item below it, is refused. If anything was short or rejected, procurement staff are notified to follow up with the supplier.

To refuse the whole delivery, click **Reject** and say why; the quantities go back to outstanding. If nothing passed inspection, reject it instead of accepting.

### Statuses and what moves them

| Kind | Values |
|---|---|
| Inspection result | Complete (all passed), Partial (fewer than outstanding or some rejected), Failed (nothing accepted) |
| Acceptance | For acceptance → In stock, In stock (partial) or Rejected |

An IAR can be edited or deleted only while it is For acceptance. Accepted quantities update the PO to Partly delivered or Delivered.

### Forms and documents produced

**🖨 IAR** prints the Inspection and Acceptance Report (Appendix 62). **⬇ IAR (Excel)** downloads it with the Inspection Officer and Supply Officer signatories from Settings (needs view_receiving, accept_delivery or view_reports).

### Tips

- Accepting creates stock only. PPE and semi-expendable units are registered as property later, in Assets or through Issuance.
- Numbering: IAR-YYYY-NNNN.

<a id="items"></a>

## Items (Item Master)

The catalogue of everything the agency stocks: item codes, categories, COA classification, reorder levels, stock lots and the stock card.

### Who can use it

- The page opens for view_inventory.
- Add items: create_inventory. Edit items and their lots: edit_inventory. Delete: delete_inventory.
- Add stock: add_stock or manage_warehouse.
- Categories: add with create_inventory or edit_inventory; edit with edit_inventory; delete with delete_inventory.

### What it is for

Page title "Item Master / Catalogue", with two tabs: **Items** and **Categories**. Every item has a COA classification (Consumable, Semi-Expendable or PPE) that decides which slip is issued later (RIS only, ICS, or PAR).

### Add an item

1. Click **+ Add New Item**.
2. Leave **Item code** blank to get the next number (ITEM-00001), or type your own unique code.
3. Fill in **Name**, **Category**, **Unit of measure** and **COA classification**. Set the **Reorder point (min)** and **Maximum stock** (the maximum must be at least the minimum).
4. Tick **Assembly** if the item is classified by its total assembled cost; this skips the cost check below.
5. Optional: under **Opening stock**, click **+ Add lot** for stock already on hand (quantity, unit cost, brand, supplier, location, lot/batch no., expiry and received dates, warranty, specifications).
6. Click **Create item**.

> **Caution:** Lot costs are checked against the COA capitalisation threshold (Settings → System, default ₱50,000). A Semi-Expendable lot at or above it must be PPE; a PPE lot below it must be Semi-Expendable.

### Add stock

1. Click **+** on the item row (or use **Warehouse → Stock received**).
2. Enter the **Received date** (not in the future), **Quantity**, **Unit cost** and other lot details.
3. Click **Record stock**. The lot is on hand at once and appears on the stock card.

> **Tip:** For deliveries under a PO, use Receiving instead. Accepting the IAR creates the lots for you.

### Edit an item or its lots

Click **✎**. To change lot quantities, tick **Edit stock lots**. Quantity changes made here post to the stock card straight away as an adjustment; they do not go through approval. Use **Stock Adjustments** when a second person should approve the change.

### Stock card

Click the item name, then the **Stock card** tab: Date, Reference, Receipt, Issue, Office and Balance. **Stock Card (Excel)** downloads the Appendix 58 layout; **🖨 Print** prints it.

### Categories

Categories have one level of sub-categories. **+ Sub-category** appears only while the parent has no items. Move or delete sub-categories and items before deleting a category.

### Statuses and what moves them

| Badge | Meaning |
|---|---|
| Out of stock | Nothing on hand |
| Reorder | On hand at or below the reorder point |
| Over max | On hand above the maximum |
| In stock | Anything else |

### Forms and documents produced

Stock Card (Appendix 58) in Excel or print. **▦ QR labels for this page** prints QR stickers for the items listed.

### Tips

- An item with stock on hand cannot be deleted; issue, adjust or dispose of the stock first. Items with requests or registered assets cannot be deleted either.
- Use the stock-level filter "Needs reorder" to see everything at or below the reorder point.

<a id="warehouse"></a>

## Warehouse / Stockroom

See stock levels and what to re-order, record stock received, move stock between locations and watch expiring lots.

### Who can use it

- The page opens for view_warehouse.
- Record stock received and transfers: add_stock or manage_warehouse.

### What it is for

Four tabs: **Reorder & stock levels**, **Stock received**, **Transfers** and **Expiring stock**.

### Reorder and stock levels

Shows catalogue items, how many need reorder or are out of stock, and lots expiring or expired. Tick **Only items at or below the reorder point** to turn the list into a re-order report, then **🖨 Print**. "Suggested order" is the maximum minus what is on hand ("set a max" means no maximum is set).

### Stock received

Use **Record stock received for** to search an item and record a lot (same form as Add stock in Items). The tab lists lots with received date, brand, location, quantity now, unit cost, lot no., expiry and supplier.

### Transfer stock between locations

1. On **Transfers**, choose the **Item** and **Transfer date**.
2. Choose the **From lot / location**, the **Quantity** (up to what the lot holds) and the **To location**. Add **Remarks** if needed.
3. Save. The transfer is numbered STR-YYYY-NNNN.

The total on hand does not change. If an identical lot is already at the destination, the stock is merged into it. **Transfer history** can be filtered by location.

### Expiring stock

Set **Warn within N days** (1 to 365; starts at the Expiry warning in Settings). Two lists: "Expired — for disposal" and "Expiring within N days", each with lot count and value.

### Statuses and what moves them

Stock status badges are the same as in Items (Out of stock, Reorder, Over max, In stock).

### Forms and documents produced

Printable re-order report.

### Tips

- Expired lots are never issued. Write them off through Disposal (stock write-off) or a Stock Adjustment.
- The re-order quantity here and in Monitoring is the same: top up to the maximum (or twice the minimum when no maximum is set).

<a id="issuance"></a>

## Issuance (RIS, ICS and PAR)

Offices request supplies; an approver issues them, stock is taken first-expiry-first-out, and the RIS, ICS or PAR is produced.

### Who can use it

- The page opens for view_requests, view_own_requests or create_requests. Without view_requests you only see your own requests.
- New request: create_requests. Edit a pending request: edit_requests, or edit_own_requests for your own. Delete your own pending request: delete_own_requests.
- Approve and issue, or reject: approve_requests, and not on your own request (seeded: Department Head, Supply Officer).

### What it is for

Page title "Issuance & Requests". This is where offices requisition supplies and where the issuing officer releases them. The system assigns the RIS, ICS and PAR numbers on issue.

### Request supplies

1. Click **+ New Request**.
2. Choose the **Requesting office** and **Priority** (Medium by default). **Accountable person** is who will hold any semi-expendable items under the ICS; it defaults to you.
3. Type the **Purpose** and add **Items** with quantities. Out-of-stock items are greyed out. Each item can appear only once.
4. Click **Submit request**. The request is numbered REQ-YYYY-MM-NNNN and is Pending.

You may ask for more than is in stock; you may then receive part of it. Stock is only taken when the request is approved.

### Approve and issue

1. Click **Process** on a pending request (not your own).
2. For each line set the **Issue** quantity (it starts at the smaller of requested and in stock). Set a line to 0 to reject it.
3. Click **Approve & issue**.

Stock is taken lot by lot: soonest expiry first, then oldest receipt; lots without an expiry date go last. Expired lots are never issued. You get a notice if an issued lot expires soon.

To refuse the whole request, click **Reject request** and give a reason. The requester is notified either way.

The stock column and the default issue quantity count only usable stock; expired lots are shown apart. Dispose of expired lots through a stock write-off (Disposal).

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Pending | Submit request |
| Issued | Approve & issue, with every line fully issued |
| Partly issued | Approve & issue, with some lines short or rejected |
| Rejected | Reject request, or every line set to 0 |

Requests can be edited or deleted only while Pending.

### Which slip: RIS, ICS or PAR

- A RIS is issued for every request where anything was issued.
- If any Semi-Expendable item was issued, an ICS is also issued, and a semi-expendable accountability record is created for the accountable person.
- If any PPE item was issued, a PAR is also issued. Register the units as property in Assets.
- The slip follows the item's COA classification, which the capitalisation threshold (Settings → System, default ₱50,000) controls when the item is classified and when stock is received.

### Forms and documents produced

Shown once a request is Issued or Partly issued, each with **🖨 Print** and an Excel button:

- Requisition and Issue Slip (RIS), Appendix 63
- Inventory Custodian Slip (ICS), Appendix 59: semi-expendable lines only
- Property Acknowledgment Receipt (PAR), Appendix 71: PPE lines only

### Tips

- After an issue, warehouse staff are alerted if the item drops to low stock.
- You cannot approve your own request; another approver has to.

<a id="physical-count"></a>

## Physical Count

Count supplies, PPE or semi-expendable property against the books, then have someone independent verify and post the differences.

### Who can use it

- The page opens for view_physical_inventory.
- Set up, edit the draft and delete: create_physical_inventory (delete only by the person who set it up).
- Start, record, complete, send back and verify: conduct_physical_inventory.
- Seeded: Warehouseman, Supply Officer and Property Officer have all three.

### What it is for

A count covers one kind of stock: supplies (for the RPCI), PPE (RPCPPE) or semi-expendable property (RPCSP).

### Set up and start a count

1. Click **+ New count**. Choose **What is counted** and the **Count date** (not more than a year ago). For supplies you can limit it to a **Category** (sub-categories included); for assets, to a **Department**. Add **Purpose** and **Remarks**.
2. Save. The count is numbered PIC-YYYY-MM-NNNN.
3. On or after the count date, click **Start counting**. This freezes the book figures: one line per item (supplies) or per property tag (assets).

### Count

- **Supplies**: enter **Counted**, **Location** and **Remarks** per line, then **Save N lines**. Movements since the cut-off are shown so you can reconcile. Enter 0 for items not on the shelf.
- **Assets**: scan or type the **Property tag**, set **Condition seen**, **Location** and **Remarks**, then click **Found**. A tag not on record needs a description. Per-tag buttons mark Found, Missing or Not yet checked.

### Complete, verify and post

1. Click **Complete count**. Supplies need every line counted. For assets, tags not checked become Missing. Count staff are notified.
2. A different person (who did not set up, close or take part in the count) reviews it and clicks **Verify & post**, or **Send back for recount** with a reason.

Supplies: each difference becomes an approved Stock Adjustment that moves real lots (overages as a new lot, shortages written off expired-first). Assets: the condition seen becomes the condition on record; missing assets stay on record for follow-up (for example an RLSDDP in Reports).

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | Save |
| Counting | Start counting; also Send back for recount |
| For verification | Complete count |
| Verified | Verify & post |

A count can be deleted only while Draft or Counting.

### Forms and documents produced

**🖨 Report** (any status after Draft) prints the Report on the Physical Count of Inventories (Appendix 66), of PPE (Appendix 73) or of Semi-Expendable Property, grouped by holder or department, optionally discrepancies only. The Excel RPCI, RPCPPE and RPCSP are in Reports.

### Tips

- Verification is refused if a shortage is now larger than what is on hand; recount or adjust first.
- Separation of duties: whoever set up, closed or counted cannot verify.

<a id="stock-adjustments"></a>

## Stock Adjustments

Correct stock on hand for shortages, damage, loss or overages, with a second person approving before stock changes.

### Who can use it

- The page opens for create_adjustments or approve_adjustments.
- File and withdraw: create_adjustments. Approve or reject: approve_adjustments, and not on your own adjustment.
- No seeded role except Super Admin has these permissions; an administrator must add them to a role.

### What it is for

A controlled way to change stock outside receipts and issues. Stock only changes when the adjustment is approved.

### File an adjustment

1. Click **+ New Adjustment**.
2. Choose the **Item** and **Change**: "Decrease (shortage, damage, loss)" or "Increase (overage, found)". Enter the **Quantity**.
3. For increases, the **Unit cost** defaults to the item's average cost when left blank (required if the item has no cost history).
4. Type the **Reason** and any **Remarks**, then click **File for approval**. It is numbered ADJ-YYYY-NNNN.

A decrease cannot be more than what is on hand.

### Approve or reject

Another user clicks **Approve** or **Reject**. An increase adds a lot located "Stock adjustment ADJ-…"; a decrease writes off expired lots first, then first-expiry-first-out. If stock has dropped since filing, approval is refused: reject it and file a new one.

The person who filed it can **Withdraw** it while it is pending; this removes it.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Pending approval | File for approval |
| Approved | Approve |
| Rejected | Reject |

### Forms and documents produced

No printed form; every approved adjustment appears on the item's stock card.

### Tips

- Physical count verification creates adjustments that are already approved.

<a id="semi-expendable"></a>

## Semi-Expendable Property

The ledger of semi-expendable items issued under an ICS: who holds what, and returns back to stock.

### Who can use it

- The page opens for view_inventory, manage_warehouse or add_stock.
- Record a return: add_stock or manage_warehouse.

### What it is for

Records are created automatically when semi-expendable lines are issued through Issuance. Search by holder or ICS number; **Only still with holders** is on by default.

### Record a return

1. Click **Record return** on the row.
2. Enter the **Quantity returned** (up to what is outstanding).
3. Choose the **Condition**: "Serviceable — put back in stock" adds a lot at the average cost (location "Returned (semi-expendable)") and records it on the stock card; "Unserviceable — record for disposal" only logs it.
4. Add **Remarks** and save.

### Statuses and what moves them

| Status | Meaning |
|---|---|
| With holder | Nothing returned |
| Partly returned | Some returned |
| Returned | All returned |

### Forms and documents produced

No forms on this page. The ICS is printed from the request in Issuance; the RPCSP is in Reports.

### Tips

- Semi-expendable units registered one by one with tags are managed in Assets instead (with the RRSP return slip).

<a id="monitoring"></a>

## Inventory Monitoring

Stock levels and movement analysis, the re-order report, request trends, procurement deadlines and the scheduled alert jobs.

### Who can use it

- The page opens for view_monitoring.
- **Scheduled alerts** tab: manage_system_settings.

### What it is for

Page title "Inventory Monitoring & Controls". A read-only control panel; it does not change any records (except running alert jobs).

### Tabs

- **Stock levels**: counts of items, out of stock, at or below reorder point, suggested re-order value, expired and expiring lots, open requests. Items are grouped as Out of stock / Low / Normal / Overstock / No levels set, and by movement over the last 90 days (Fast-moving: top 80% of units issued; Slow-moving; Non-moving), with usage per month and months of cover. PPE is left out.
- **Re-order report**: items at or below the reorder point; the suggested quantity tops up to the maximum, or to twice the minimum when no maximum is set. **🖨 Print re-order report**.
- **Request trends**: over 3 to 24 months, requests, fulfilled, rejected, still open, fulfilment rate and units issued, with a chart.
- **Procurement watch**: overdue and due-soon deadlines for bidding stages, quotation deadlines, PO deliveries, delivery delays, contract expiry, security validity, supplier documents and APP schedules.
- **Scheduled alerts**: run all alert jobs now, or one at a time.

### Scheduled jobs (Philippine time)

| Job | When |
|---|---|
| Bidding, RFQ and PO deadlines | Daily 08:15 |
| Contract delivery, expiry and securities | Daily 08:30 |
| Supplier eligibility documents | Daily 08:40 |
| Reorder alerts (7-day cooldown per item) | Daily 08:45 |
| Expired and expiring stock | Daily 08:50 |
| APP lines due this quarter | First day of each quarter |
| Monthly depreciation posting | 01:00 on the 1st |
| Delete read notifications older than 30 days | Sundays 03:00 |
| Database backup (kept 7 days) | Daily 02:00 |

### Statuses and what moves them

Not applicable; the page reports on other modules.

### Forms and documents produced

Printable re-order report.

### Tips

- The expiring count uses the expiry warning days from the settings, the same as the Warehouse **Expiring stock** tab.

<a id="disposal"></a>

## Disposal and Write-Off

Propose and carry out the disposal of an unserviceable asset or the write-off of consumable stock, feeding the IIRUP and WMR.

### Who can use it

- The page opens for view_disposal (seeded: Property Officer). It sits in the Inventory menu group.
- Create, edit, withdraw and submit: create_disposal, and only the person who drafted it.
- Approve, reject and complete: approve_disposal (seeded: Admin), and not on your own request.

### What it is for

Page title "Disposal & Write-Off". Two kinds: an **Asset (PPE / semi-expendable)**, or a **Stock write-off** of a quantity of an item.

### Request a disposal

1. Click **+ New Disposal Request** and choose the tab.
2. Pick the **Asset**, or the **Item** and **Quantity** (not more than on hand).
3. Choose the **Mode of disposal**: Condemnation, Destruction, Public auction, Sale through negotiation, Sale, Barter, Donation or Transfer to another agency.
4. Type the **Reason** and **Remarks**, then click **Save draft** (numbered DSP-YYYY-MM-NNNN).
5. Click **Submit**. Approvers are notified.

> **Caution:** An asset must not be issued to anyone (record its return first), must not be out on a gate pass, and must not already have an open disposal request.

### Approve and complete

1. An approver opens **Review…**, checks the attached documents (inspection report, photos, appraisal), adds **Remarks** (required to reject) and clicks **Approve** or **Reject**.
2. After the actual disposal, click **Complete…** and enter **Date disposed**, **Proceeds (₱)** for sale modes, and **Remarks**.

Completing an asset disposal records its book value and marks it Disposed. Completing a stock write-off removes the quantity, expired lots first.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | Save draft |
| Awaiting approval | Submit |
| Approved | Approve |
| Rejected | Reject (final; file a new request if needed) |
| Completed | Complete… |

Only drafts can be withdrawn.

### Forms and documents produced

Completed disposals are selected in **Reports** to produce the IIRUP (Appendix 74) and the Waste Materials Report (Appendix 65).

### Tips

- The asset does not need to be retired first.

# Assets

<a id="gate-pass"></a>

## Gate Pass

Record an asset leaving the premises with a printed gate pass, and its return.

### Who can use it

- The page opens for view_gate_pass (seeded: Property Officer). It sits in the Inventory menu group.
- Issue a gate pass: create_gate_pass. Mark returned: create_gate_pass or edit_gate_pass.

### What it is for

There is no separate approval step: issuing the pass is the release. Passes are kept as history and are not deleted.

### Issue a gate pass

1. Click **+ New Gate Pass**.
2. Search the **Asset** by tag, serial or item. Retired and disposed assets cannot leave on a gate pass, and an asset can only have one open pass.
3. Check **Carried out by** (pre-filled with the holder), type the **Purpose**, and set **Date released**, **Expected back** (blank if it is not coming back) and **Condition on release**.
4. Click **Issue gate pass**. It is numbered GP-YYYY-MM-NNNN and the print view opens.

### Mark returned

1. Click **Mark returned** on the row.
2. Enter **Date back** (not before release, not in the future), **Condition** (good, fair, poor, damaged, lost), **Returned by** and **Remarks**.

Good, fair and poor are copied to the asset's condition; damaged sets it to poor. A returned pass cannot be changed.

### Statuses and what moves them

| Status | Meaning |
|---|---|
| Out | Released and not back |
| Overdue | Still out after the expected date |
| Returned | Mark returned done |

The list starts on **Out**; switch to Overdue, Returned or All.

### Forms and documents produced

**🖨** prints the Gate Pass (pass-out slip) with signature lines for Released by, Carried out by and Guard on duty.

### Tips

- While an asset is out on a gate pass it cannot be returned, retired or disposed of.
- A pass returned as "lost" marks the asset Lost; it stays on its custodian's PAR/ICS until relieved. File the RLSDDP in Reports, then record the turn-in and a disposal. **Mark found** on the asset reverses it.
- Returned passes can be deleted by holders of delete_gate_pass; passes still out can be edited.

<a id="assets"></a>

## Assets and Property

The masterlist of PPE (under a PAR) and tagged semi-expendable property (under an ICS): register units, transfer, return, repair, retire and post depreciation.

### Who can use it

- The page opens for view_properties or view_own_properties. Without view_properties you see only the assets you are accountable for.
- The tabs **Asset register**, **Transfers & returns** and **Depreciation** need view_properties; **Depreciation** also needs edit_properties.
- Register: create_properties or assign_properties. Edit, repair, back in service, retire, reinstate, post depreciation: edit_properties.
- Transfer: transfer_properties. Return: transfer_properties or assign_properties. Issue to someone: assign_properties or transfer_properties. Undo a registration: delete_properties.
- Seeded: Property Officer has everything except delete.

### What it is for

Page title "Assets & Property". Each unit has a tag number, its accountable person, condition, status, and (for PPE) a depreciation schedule.

### Register assets

1. Click **+ Register Assets**.
2. Choose the **Item (PPE or semi-expendable)** and the **From stock lot** the units come from. Consumables cannot be registered.
3. Set **Date issued** (not in the future), the **Accountable person** and **Condition**. For PPE, set **Useful life (years)** (1 to 50, default 5) and **Residual value (₱)** (blank means 5% of cost).
4. Click **+ Add unit** for each unit and enter its **Serial no.** and **Tag no.** (blank gives an automatic PROP-YYYY-… tag). Tags and serials must be unique.
5. Click **Register N units**.

All units share one PAR (PPE) or ICS (semi-expendable) number. They leave the stock lot, and the holder is notified. Brand, specifications, warranty and cost come from the lot.

### Transfer, return and re-issue

Click the tag number to open the asset, then:

- **➜ Transfer** (asset in use): choose **To**, **Date** and **Reason**. Creates a Property Transfer Report (PTR).
- **↩ Record return**: choose **Date**, **Condition** and **Reason**. Creates a Property Return Slip (PPE) or Receipt of Returned Semi-Expendable Property (RRSP). The asset becomes Available with no holder.
- **Issue to someone** (available, no holder): choose **To**, **Date** and an optional **Reason**. Creates a new PAR or ICS.

After each movement the printable slip opens. Holders are notified of transfers and returns.

> **Caution:** An asset out on a gate pass cannot be returned or retired. Record its return through the gate first.

### Repair, retire and reinstate

- **🔧 Send for repair** (in use or available) → Under repair. **Back in service** returns it to In use (if it has a holder) or Available.
- **Retire** (available or under repair, no holder, not on a gate pass) → Retired. **Reinstate** → Available.
- Disposed is set only by completing a disposal in the Disposal module.

### Edit details

**✎ Edit details** changes tag no., serial no., condition, brand/model, warranty months, useful life and specifications. Once depreciation has been posted, useful life and residual value are fixed. Disposed assets cannot be edited.

**🗑** undoes a registration and puts the unit back into its stock lot, but only if the asset has no transfers, gate passes, disposals or posted depreciation.

### Depreciation

Straight-line, residual value 5% of cost unless set, starting the month after acquisition. On the **Depreciation** tab choose **Post through** (a month) and click **Post depreciation**. Each month is posted once per asset, so running it again is safe; future months are refused. A scheduled job also posts it at 01:00 on the 1st of each month. Each asset's Depreciation tab shows the yearly schedule.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| In use | Register, Issue to someone, Back in service (with holder), Transfer |
| Available | Record return, Back in service (no holder), Reinstate |
| Under repair | Send for repair |
| Retired | Retire |
| Disposed | Completing a disposal |

### Forms and documents produced

- Property Acknowledgment Receipt (PAR), Appendix 71, and Inventory Custodian Slip (ICS), Appendix 59: **🖨 PAR** / **🖨 ICS**
- Property Transfer Report (PTR), Appendix 76: after a transfer, and **PTR** (Excel) in the History tab
- Property Return Slip / Receipt of Returned Semi-Expendable Property
- Property Card, Appendix 69 (Excel)
- **▦ QR sticker** for one asset, or **▦ QR stickers for this page**

### Tips

- The asset detail also shows its history, gate passes and attachments.
- Changing a tag number makes its printed QR sticker invalid; print a new one.
- Property Card and PTR Excel downloads need view_reports, generate_reports or view_properties.

<a id="my-assets"></a>

## My Assets

Everything you are accountable for under a PAR or ICS, with its cost and condition.

### Who can use it

- Everyone who can sign in.

### What it is for

A read-only list of the assets under your name: tag no., item, serial no., PAR/ICS, since when, cost, condition and status, with totals for "Items under my accountability" and "Total acquisition cost".

### See an asset

1. Click the tag number.
2. View its details, history and depreciation, print your PAR/ICS, or print its **▦ QR sticker**.

### Statuses and what moves them

Same as Assets: In use, Available, Under repair, Retired, Disposed.

### Forms and documents produced

Your PAR or ICS for each asset.

### Tips

- If something listed is not with you, tell the Property Officer so it can be transferred or reported (RLSDDP).
- To give an asset back, ask the Property Officer to record the return; you cannot do it from this page.

<a id="maintenance"></a>

## Asset Maintenance

Schedule preventive maintenance, inspections and repairs of assets, see what is overdue, and record what each job cost.

### Who can use it

- Open the page: view_properties or edit_properties. Schedule, reschedule, complete or cancel: edit_properties.
- The asset window shows its own maintenance on the **Maintenance** tab.

### What it is for

Keeps assets in service by tracking each job against a due date. A schedule can repeat: when it is done, the next one is booked that many months after the date done. Corrective repairs done here also appear in the Repair History of the PPE Ledger Card.

### Schedule maintenance

1. Click **+ Schedule maintenance** (or open the asset → **Maintenance** tab → **+ Schedule maintenance**).
2. Choose the asset, the **Type** (preventive, corrective / repair, inspection or calibration), the **Due on** date and the **Work to do**.
3. For recurring work, fill in **Repeat every (months)**, e.g. 3 for quarterly aircon cleaning.
4. Click **Schedule**.

### Record the work as done

1. Click **Done** on the row.
2. Enter the date done, the cost, who did it and any findings.
3. Click **Record as done**. A repeating schedule books the next one.

> **Tip:** Sending an asset for repair (🔧 in the asset window) changes its status; the maintenance record holds the cost and what was done.

### Statuses and what moves them

| Status | Meaning |
|---|---|
| Scheduled | Due on the date shown |
| Overdue | Scheduled and the due date has passed (also on the dashboard) |
| Done | Recorded as done, with cost |
| Cancelled | Will not be done; a repeating schedule stops |

### Forms and documents produced

Repair history on the PPE Ledger Card (Appendix 70).

### Tips

- The tiles at the top filter the list: click **Overdue** to see only overdue work.

<a id="clearance"></a>

## Property Turnover & Clearance

Follow an employee's turnover of property before they leave, and issue the Certificate of Property Turnover and Clearance once nothing is outstanding.

### Who can use it

- Open the page: view_properties, assign_properties or transfer_properties. Start, issue or cancel a clearance: assign_properties or transfer_properties.

### What it is for

An employee who resigns, retires, transfers or ends a contract must turn over every asset under their PAR or ICS. The clearance lists what they still hold, and the certificate can only be issued when that list is empty.

### Start a clearance

1. Click **+ Start clearance**.
2. Choose the employee; the form shows how many assets they still hold.
3. Choose the reason and, if known, the effective date.
4. Click **Start clearance**.

### Turn over the property

Open each asset (Assets) and use **↩ Record return** or **➜ Transfer**. The clearance updates as each one comes off the employee's name.

> **Caution:** A lost asset stays on the employee until they are relieved through the RLSDDP and disposal, so it blocks the certificate.

### Issue the certificate

1. Open the clearance.
2. When "Everything has been turned over" shows, click **Issue certificate**. It gets a number like CPT-2026-0001.
3. Click **⬇ Certificate** to download it for signing.

### Statuses and what moves them

| Status | Meaning |
|---|---|
| Turnover in progress | Started; the employee may still hold assets |
| Cleared | Certificate issued |
| Cancelled | Withdrawn before issue |

### Forms and documents produced

Certificate of Property Turnover and Clearance (agency format), listing what was turned over while the clearance was open, signed by the Property Officer and the Head of Agency.

### Tips

- The dashboard tells property staff how many clearances are waiting for turnover.

# Reports & Compliance

<a id="reports"></a>

## Reports (COA forms)

All COA inventory and property forms in one place, with Excel exports filled from the system and signed by the signatories in Settings.

### Who can use it

- The page opens for view_reports (seeded: most roles except Requestor, Custodian and End User).
- Some tabs list records from other modules (requests, movements, receiving, disposals), so they also need access to those modules to show data.

### What it is for

Pick a report on the left (or from the drop-down on narrow screens). Entity name and fund cluster come from Settings → Agency profile. Every download is recorded in the audit trail.

### Inventory and supplies

| Report | Options | Output |
|---|---|---|
| Inventory Report | Category, status | Print |
| Stock Card (Appendix 58) | Item | Excel |
| Report of Supplies and Materials Issued, RSMI (Appendix 64) | Year, month | **Generate RSMI** saves a numbered summary (RSMI-YYYY-MM-NNN) with history; Excel |
| Requisition and Issue Slip, RIS (Appendix 63), with ICS (59) and PAR (71) | Search, status | Excel per request |
| Report on the Physical Count of Inventories, RPCI (Appendix 66) | Inventory type, as-at date | Excel |
| Inventory Aging | 90, 180 or 365 days | On screen |
| Stock Reconciliation | None | On screen |

### Property, plant and equipment

| Report | Options | Output |
|---|---|---|
| Property Card (Appendix 69) | Asset | Excel |
| Property Transfer Report, PTR (Appendix 76) | Search | Excel per movement |
| Report on the Physical Count of PPE, RPCPPE (Appendix 73) | PPE type, as-at date | Excel |
| Report on the Physical Count of Semi-Expendable Property, RPCSP (Appendix 68) | As-at date | Excel |
| Inventory and Inspection Report of Unserviceable Property, IIRUP (Appendix 74) | Completed disposals; accountable officer, designation, station | Excel |
| Report of Lost, Stolen, Damaged or Destroyed Property, RLSDDP (Appendix 75) | Property, status, circumstances, police report details | Excel |
| Waste Materials Report, WMR (Appendix 65) | Completed disposals, storage place | Excel |

### Receiving and activity

| Report | Options | Output |
|---|---|---|
| Inspection and Acceptance Report, IAR (Appendix 62) | Search | Excel per IAR |
| Request Logs | From, to, department | On screen |

### Produce a COA form

1. Check Settings → Agency profile (agency name, fund cluster) and Settings → Signatories once; they are printed on the forms.
2. Choose the report on the left.
3. Fill in the options (for example the as-at date or the disposals to include).
4. Click the export button, open the Excel file, review it and print it for signature.

> **Caution:** The as-at date affects the Excel file only; the on-screen preview always shows today's figures.

### Statuses and what moves them

Not applicable. The RSMI history shows each generated summary.

### Forms and documents produced

See the tables above. Signatories used: Property Card and RLSDDP (Property Officer, Head of Agency); IIRUP (Property Officer, Head of Agency, Inspection Officer); WMR (Supply Officer, Head of Agency); IAR (Inspection Officer, Supply Officer).

### Tips

- Generate the RSMI yourself at month end; it is not generated automatically.
- For IIRUP and WMR, complete the disposal in Disposal first; only completed disposals can be selected.

<a id="procurement-reports"></a>

## Procurement Reports

APP, Procurement Monitoring Report (summary and GPPB format), BAC and award reports per fiscal year, with Excel export.

### Who can use it

- The page opens for view_reports.

### What it is for

Choose the report tab and the **FY**, then **⬇ Export Excel** if you need the file. Exports are recorded in the audit trail.

### Available reports

- **APP Report**: APP lines by department with source of funds, mode, quantity, estimated cost, utilised, remaining and status, with totals.
- **PMR**: counts and values by status for PRs (by fiscal year), and RFQs, biddings, POs and contracts (by year created).
- **PMR (GPPB)**: awarded biddings and RFQs in the GPPB layout: PAP code, project, end user, mode, ABC, contract cost, awardee, the key dates (posting, opening, NOA, contract signing, NTP, acceptance), funds and remarks, with totals and savings.
- **BAC Report**: numbers of meetings, resolutions and evaluations, with meeting and resolution tables.
- **Award Report**: type, reference, title, supplier, amount, NOA and NTP, with totals.

### Statuses and what moves them

Not applicable.

### Forms and documents produced

Excel file per report: {TYPE}_report_{year}.xlsx.

### Tips

- The PMR (GPPB) is the one usually submitted to GPPB; check the dates against the records before submitting.

<a id="apcpi"></a>

## APCPI Self-Assessment

Score the 16 Agency Procurement Compliance and Performance Indicators for a fiscal year, with system suggestions and a printable result.

### Who can use it

- The page opens for view_reports.
- Create, score, finalize, reopen and delete: manage_bac_meetings or approve_procurement.

### What it is for

16 indicators across four pillars: Legislative and Regulatory; Institutional and Capacity; Operations and Market; Integrity and Transparency. Averages map to maturity bands: Advanced (2.51 and up), Maturing (1.51), Beginning (0.51), otherwise Negligible.

### Do an assessment

1. Click **+ New Assessment**, enter the **Fiscal year** and click Create. There can be one per fiscal year.
2. Open it. For each indicator set the **Score** (0 to 3), **Means of Verification** and **Remarks**.
3. Optionally click **Use system suggestions** to fill in scores the system can work out from its records. Review them; they are not saved until you save.
4. Click **💾 Save Scores**.
5. Click **✔ Finalize** when done. Any indicator still at 0 needs a remark first.

### Statuses and what moves them

| Status | What moves it |
|---|---|
| Draft | + New Assessment; ↩ Reopen |
| Finalized | ✔ Finalize |

Only drafts can be deleted.

### Forms and documents produced

**🖨 Print** prints the assessment from the browser.

### Tips

- Every score change is recorded in the audit trail.

<a id="documents"></a>

## Documents

The document repository: upload procurement and inventory files, keep versions with checksums, and attach files to records.

### Who can use it

- The page opens for view_documents.
- Download: view_documents or upload_documents. Upload, add a version, archive and restore: upload_documents. Delete: delete_documents.

### What it is for

Page title "Document Repository". Types: PPMP, APP, Purchase Request, Request for Quotation, Abstract, BAC Resolution, Contract, Purchase Order, IAR, Notice of Award, Notice to Proceed, Disposal, Other.

### Upload a document

1. Click **+ Upload document**.
2. Enter the **Title** and **Type**; optionally the **Document number**, **Category**, **Description** and **Remarks**.
3. Choose the **File** (PDF, Word, Excel, PNG or JPG, up to 20 MB) and click **Upload**.

### Versions, archive and delete

- **⬇** downloads the latest version.
- **🕘 Version history** lists each version (v1, v2, …) with its SHA-256 checksum and its own download.
- **＋ Upload new version** adds a version (not allowed while archived; a file identical to the current version is refused).
- **🗄 Archive** / **↺ Restore**.
- **🗑 Delete** hides the document; its files are kept for audit.

### Attachments on records

The detail windows of PPMPs, the APP, purchase requests, RFQs, biddings, POs, contracts, IARs, suppliers and assets have an **Attachments** panel: **+ Attach**, download and history. Attached files also appear in the repository with the record they belong to.

### Statuses and what moves them

Active or Archived; deleted documents disappear from the list but are kept.

### Forms and documents produced

None; this module stores files.

### Tips

- Scan and attach signed copies (PO with conforme, contract, NOA) to the record so auditors find them in one place.
- Downloads are checked against the stored checksum and recorded in the audit trail.

<a id="audit-trail"></a>

## Audit Trail

Who did what and when: every change with before and after values, plus prints, downloads and sign-outs.

### Who can use it

- The page opens for view_audit_trail (seeded: Admin, Budget Officer, Accountant, Auditor). **Export CSV** also needs export_audit_trail (Admin, Auditor).

### What it is for

Each entry has the user and their role at the time, the action, the record, a description, the changed fields (before and after), IP address, browser and time. Prints and downloads are logged too. Sign-ins are in **Log Trails**, not here.

### Find an entry

1. Type in **Search description or user**, or pick an **Action**, **Record type** or **User**.
2. Set **From** and **To** dates if needed.
3. Use the quick chips **Deleted records**, **Sign-outs** and **Prints** for common checks.
4. Click a row to see the Field / Before / After table; changed fields are highlighted.

### Print or export

- **🖨 Print**: up to 500 rows for the current filters.
- **Export CSV**: up to 10,000 rows, newest first, opens directly in Excel. The export itself is logged.

### Statuses and what moves them

Not applicable; entries cannot be edited or deleted from the app.

### Forms and documents produced

Audit trail printout and CSV file.

### Tips

- Narrow by date and record type before exporting large periods.

<a id="log-trails"></a>

## Log Trails

Who is online now, every sign-in attempt, and the stock movement ledger.

### Who can use it

- The page opens for view_log_trails (seeded: Admin, Auditor).

### What it is for

Three views: **Online now**, **Stock movements** and **Sign-in activity**. Use the Audit Trail for record changes.

### What each view shows

- **Online now**: people active within the idle limit (15 minutes if idle sign-out is off), with role, department, last activity, sign-in time and IP. Refreshes every minute.
- **Stock movements**: every stock-card entry with item, movement, quantity, balance, who (or "System") and notes. Filter by item/notes/person, movement and dates.
- **Sign-in activity**: every attempt with email, outcome ("Signed in", "Invalid credentials", "Account locked", "Invalid 2FA code"), IP and browser. Filter by email, outcome and dates.

### Statuses and what moves them

Not applicable.

### Forms and documents produced

None; there is no export on this page.

### Tips

- Repeated "Invalid credentials" from one email followed by "Account locked" usually means a forgotten password; an administrator can set a new one in Users & Roles.

<a id="digital-signatures"></a>

## Digital Signatures

Send documents for digital signing with certificates from a trusted certificate authority (PNPKI or the agency CA), and check any copy of a signed file.

### Who can use it

- Everyone signed in can send documents and sign what is sent to them.
- Trusting or removing a certificate authority: edit_settings. The **All requests** tab: edit_settings or view_audit_trail.

### What it is for

A digital signature proves who signed a document and that it has not changed since. PAMS checks each signature against the exact bytes of the file, the signer's certificate, and the certificate authorities the agency trusts, before it is recorded.

An external signing platform can also collect signatures: PAMS tells it about each new request and the platform posts the signature back through the platform API (see the deployment guide).

### Set up the trusted certificate authorities (administrator)

1. Open **Trusted CAs**.
2. Paste a CA certificate in PEM format, e.g. the PNPKI root and intermediate CAs from the DICT, give it a name and click **Add**.

> **Caution:** Until at least one CA is trusted, no signature can be accepted.

### Send a document

1. Click **+ Send for signing**.
2. Choose the file (for example a COA form exported from Reports), give it a title and choose the signer.
3. Click **Send**. The signer is notified.

### Sign a document

1. On **To sign**, click **Sign**.
2. Download the file and sign it, as is, with your signing tool or token (for example `openssl dgst -sha256 -sign key.pem -out signature.bin file`).
3. Paste or load your certificate (with any intermediate certificates after it) and the signature, choose the algorithm and click **Sign**.

- Refused if the signature does not match the file, the certificate is expired, not for signing, not issued by a trusted CA, or issued to a different email address than the signer's.

### Verify a copy

On **Verify a file**, choose any copy of a document. PAMS says whether it is exactly a file that was signed, by whom, and with which certificate. Re-saving a file, even without changes, makes it a different file.

### Statuses and what moves them

| Status | Meaning |
|---|---|
| Waiting for signature | Sent; the signer can sign or decline |
| Signed | A verified signature is recorded |
| Declined | The signer refused, with a reason |
| Cancelled | The sender withdrew it |

### Forms and documents produced

None; the signature record (signer, certificate subject, issuer, serial and validity) is kept with the request and in the audit trail.

### Tips

- Signatures already recorded stay on record if their CA is later removed.

<a id="ledgers-registers"></a>

## Ledgers, Registers & Master Lists

Ledger cards, semi-expendable registers, ITR, IRP, CNAS, the depreciation lapsing schedule, monitoring registers and master lists, in Excel, CSV or PDF for any dates.

### Who can use it

- Reports → **Ledgers, Registers & Master Lists**: view_reports or generate_reports (some per-record forms also open for the module's own viewers).

### What it is for

The rest of the forms and lists in the IMS Terms of Reference, beside the COA forms on the other tabs. The COA forms keep their prescribed layouts. For each list, choose **Monthly**, **Annual** or **Any dates**, then Excel, CSV or PDF.

### What is there

| Report | Notes |
|---|---|
| PPE Ledger Card (Appendix 70) | Per asset, as at a date; depreciation by year and repair history |
| Supplies Ledger Card (Appendix 57) | Per item, with costs |
| Semi-expendable Property Card / Ledger Card | Per semi-expendable item (COA Circular 2022-004) |
| RegSPI, RSPI | Registry and Report of Semi-expendable Property Issued, for a date range |
| ITR | Inventory Transfer Report for a transfer of semi-expendable property |
| IRP | Invoice Receipt for Property, per issuance |
| CNAS | Certificate of Non-Availability of Stock, per PS-DBM line marked not available |
| Letter of Undertaking (COS) | For contract-of-service personnel holding property |
| Depreciation Lapsing Schedule | By category, as at a date |
| Property Inventory Form (PIF) | Per accountable officer or all |
| IAR, RIS and PO registers | Monitoring registers for any date range; the PO register is the Summary of Purchase Orders |
| PAR, ICS, Property Card, Stock Card, physical count, IIRUP, WMR, RLSDDP, RTA and gate pass lists | What each covered in a month, a year or any dates |
| Reordering Point | Items at or below their reorder point, with what was issued in the period and what to order |
| Master lists | Assets, items, suppliers, users, and available common-use supplies |

### PDF copies of the COA forms

Every COA form with an Excel button also has a **PDF** button: the same form, the same layout, ready to print or attach. On the lists, choose Excel, CSV or PDF in the **Format** field.

### Statuses and what moves them

Not applicable.

### Forms and documents produced

As listed above.

### Tips

- CSV files open in Excel with Filipino characters intact (UTF-8).
- Leave the dates empty on a master list to get everything.

# Administration

<a id="users-roles"></a>

## Users and Roles

Create user accounts, set or reset their passwords, and define roles and the permissions they carry.

### Who can use it

- The page opens for view_users. Add: create_users. Edit: edit_users. Delete: delete_users.
- The **Roles & Permissions** tab needs manage_roles (only Super Admin by default).
- Only a Super Admin can give someone the Super Admin role, or edit or delete a Super Admin user.

### What it is for

Page title "User & Role Management". Each user has one role and one department.

### Add a user

1. Click **+ Add New User**.
2. Enter **Full name**, **Email** (must be unique), **Password** and **Confirm password**, and choose the **Role** and **Department**.
3. Save. Give the user their password in person and ask them to change it under Settings → My account.

Passwords must meet the password policy (see Getting started).

### Reset a password

1. Click **✎** on the user.
2. Type a **New password** and confirm it (leave blank to keep the current one).
3. Save.

There is no "forgot password" email; users are told to contact the administrator. A locked account unlocks by itself after 15 minutes; an administrator can clear it sooner with **Unlock** on the Users list.

### Delete a user

Click **🗑**. You cannot delete yourself. A user who has recorded inventory transactions cannot be deleted, because that would remove them from the stock cards; change their role or password instead.

> **Tip:** To stop someone signing in, click **Deactivate** on the Users list: they are signed out at once and cannot sign in, and their records stay. **Activate** reverses it. Password expiry is set in Settings → System.

### Create or edit a role

1. On **Roles & Permissions**, click to create a role, or **Edit** / **Edit permissions** on a card.
2. Enter the **Role name** and **Description**.
3. Tick permissions by group (each group has a check-all; **Select all** and **Clear all** are at the top).
4. Click **Create role** or **Save role**.

System roles cannot be deleted. A role with users cannot be deleted until they are moved. You cannot change your own role.

### Statuses and what moves them

Not applicable. The users list shows whether each user has two-factor sign-in On or Off.

### Forms and documents produced

None. Every user and role change is recorded in the audit trail with before and after values.

### Tips

- See "Roles and permissions" for what each permission opens.
- Offices (departments) are added, renamed and deleted on the **Offices** tab; one still used by users or records cannot be deleted.

<a id="settings"></a>

## Settings

Your own account (password, two-factor sign-in, signature) and, for administrators, the agency profile, signatories and system settings.

### Who can use it

- **My account**: everyone.
- **Agency profile & logos**: view with view_settings, edit_settings or manage_agency_profile; edit with edit_settings or manage_agency_profile.
- **Signatories**: view with view_settings, edit_settings or manage_signatories; edit with edit_settings or manage_signatories.
- **System**: view with view_settings, edit_settings or manage_system_settings; edit with edit_settings or manage_system_settings.

### What it is for

Open it from the menu (Administration → Settings) or from your name at the bottom of the sidebar → **My account**.

### My account

- **Change password**: current password, new password, confirm.
- **Two-factor sign-in**: see Getting started for setup.
- **My signature**: upload a PNG or JPG (white or transparent background, up to 2 MB). It is printed on forms where you are a signatory.

### Agency profile and logos

- **Agency name** (printed as Entity Name on COA forms), **Fund cluster**, **Head of agency** and title, **Address**.
- **Expiry warning (days)** (default 30): lots expiring within this many days are flagged on the expiring-stock list and when issuing.
- **Document logos**: PNG, JPG or SVG up to 2 MB, at a chosen position, with an optional label.

### Signatories

Assign a user to each slot: Inventory Committee Chair, Supply Officer, Property Officer, Accountant, Head of Agency / Authorized Representative, COA Representative, BAC Chairperson, Inspection Officer. Slots without a signature print a blank line to sign by hand.

| Form (Excel) | Signatories used |
|---|---|
| Property Card | Property Officer, Head of Agency |
| IIRUP | Property Officer, Head of Agency, Inspection Officer |
| WMR | Supply Officer, Head of Agency |
| IAR | Inspection Officer, Supply Officer |
| RLSDDP | Property Officer, Head of Agency |

### System

- **COA capitalization threshold (₱)**, default 50,000: unit cost at or above this is PPE; below it, semi-expendable. It drives item classification and whether a PAR or an ICS is issued.
- **Sign out after inactivity (minutes)**: 5 to 480, or 0 for off; default 30.
- **Minimum password length**: 8 to 64; default 8.

> **Caution:** Changing the capitalisation threshold affects new stock and classification checks from then on. Agree the value with Accounting first.

### Statuses and what moves them

Not applicable.

### Forms and documents produced

None. Every settings change is recorded in the audit trail.

### Tips

- The fiscal year is chosen on each page; there is no global fiscal-year setting.
- Sign-in sessions also end 60 minutes after signing in, so an idle limit above 60 minutes has no extra effect.

<a id="roles-and-permissions"></a>

## Roles and Permissions

How access works: each role carries permissions, the menu shows only the modules your permissions open, and buttons appear only for actions you may take.

### How access works

- Every user has exactly one role. A role is a list of permissions.
- A menu item appears if your role holds any one of the permissions that open that module. Groups with no visible items disappear.
- Inside a page, each button needs its own permission. The server checks every action again, so a hidden button cannot be reached another way.
- Super Admin has every permission, including ones added later, and is exempt from the separation-of-duties rules.
- Some lists are limited to your own records when you do not hold the "view all" permission (for example own requests, own PRs, own PPMPs, own assets).

### What opens each module

| Module | Opens with any of |
|---|---|
| Dashboard | view_dashboard |
| Procurement Planning | view_procurement_planning, create_ppmp, view/edit/delete/submit_own_ppmp, approve_ppmp, manage_app, monitor_budget |
| Purchase Requests | view_procurement, create_procurement, approve_procurement, pr_approve_department/budget/accounting/bac |
| RFQ / Alternative | view_rfq, create_rfq, generate_abstract |
| Competitive Bidding | view_bidding, manage_bidding, process_award |
| BAC Management | view_bac, manage_bac_meetings, manage_bac_resolutions, conduct_bid_evaluation |
| Purchase Orders | view_procurement |
| Contracts | view_contracts, create_contracts, edit_contracts |
| Receiving | view_receiving |
| Suppliers | view_suppliers, create_suppliers, edit_suppliers |
| Items | view_inventory |
| Warehouse | view_warehouse |
| Issuance | view_requests, view_own_requests, create_requests |
| Physical Count | view_physical_inventory |
| Stock Adjustments | create_adjustments, approve_adjustments |
| Semi-Expendable | view_inventory, manage_warehouse, add_stock |
| Monitoring | view_monitoring |
| Gate Pass | view_gate_pass |
| Disposal | view_disposal |
| Assets | view_properties, view_own_properties |
| My Assets, Settings | Everyone |
| Reports, Procurement Reports, APCPI | view_reports |
| Documents | view_documents |
| Audit Trail | view_audit_trail |
| Log Trails | view_log_trails |
| Users & Roles | view_users |

### Roles that come with the system

| Role | Typical work |
|---|---|
| Super Admin | Everything |
| Admin | Almost everything except role management, gate passes and stock adjustments |
| Purchaser | PPMPs, PRs, POs, RFQs, contracts, suppliers |
| Requestor | Own supply requests, own assets |
| End User | Like Requestor, plus raising purchase requests |
| Custodian | Own assets, items view |
| Warehouseman | Items, warehouse, receiving (inspect and accept), physical counts |
| Supply Officer | As Warehouseman, plus approving supply requests and reports |
| Property Officer | Assets, gate passes, disposal requests, physical counts |
| Department Head | PPMPs, approving supply requests, PR Department Head signature |
| Budget Officer | Reviewing PPMPs, the APP, budget monitoring, PR Budget signature and CAF |
| Accountant | Budget monitoring, PR Accounting signature and CAF |
| BAC Chairperson | BAC, bidding, award, NOA and NTP |
| BAC Secretariat | BAC, RFQs, bidding, PR BAC signature |
| Auditor | Read-only across modules, audit trail export, log trails |

> **Caution:** As shipped, no role except Super Admin has the Stock Adjustment permissions, only Property Officer has gate-pass permissions, and only Admin can approve disposals. Add these permissions to the right roles before using those modules.

### Permission groups

- Dashboard; Inventory / Items; Properties / Assets; Procurement; Purchase Request Workflow; Receiving; Warehouse; Requests / Issuance; Monitoring; Physical Inventory; Stock Adjustments; Gate Pass; Disposal; Reports; Analytics; Log Trails; Suppliers; Procurement Planning; BAC Management; RFQ & Alternative Procurement; Competitive Bidding; Contract Management; Document Management; Audit & Compliance; User Management; Role Management; Settings.
- The role editor in Users & Roles lists every permission under these groups.

### Statuses and what moves them

Not applicable.

### Forms and documents produced

None.

### Tips

- If someone cannot see a module, check which permissions open it in the table above, then edit their role.
- Give each person the smallest role that lets them do their job, and keep makers and approvers in different roles.

<a id="external-payments"></a>

## Payments from Other Systems (Integrations)

Connect PAMS to another system's API, such as the accounting or disbursement system, and show its payment records in PAMS, read-only.

### Who can use it

- Connect, change, test or remove a system: edit_settings or manage_system_settings (Settings → **Integrations**).
- See the records: view_payments, manage_payments or approve_payments.

### What it is for

PAMS calls the other system's API and shows what it returns. It only reads: nothing in PAMS is sent to the other system, and nothing there can be changed from PAMS.

A connection shows its records in one of two places: as a list on **Payments from Other Systems**, or in each purchase order and contract window under **Payments recorded in other systems**, looked up by that PO or contract.

### Connect a system

1. Get from the other system's administrator: the API address, the path that lists payments, and a key or token for PAMS.
2. Open Settings → **Integrations** → **+ Connect a system**.
3. Enter a **Name**, choose where to **Show the records**, and enter the **Address of the API** and the **Path**. To look records up by PO or contract, put a placeholder in the path, e.g. `/payments?reference={reference}`.
4. Choose the **Sign-in**: a bearer token, a key in a named header (e.g. `X-API-Key`), or basic `user:password`, and enter it.
5. If the records sit inside the answer, enter **Where the records are**, e.g. `data.items`. Add **Columns to show**, with a heading and the field of each record, e.g. `Check no.` and `check_number`.
6. Click **Save and test**, enter sample values and click **Call it now**. The table shows what PAMS will display.

| Placeholder | Filled with |
|---|---|
| {reference} | The PO number on a purchase order, the contract number on a contract |
| {po_number} | The PO number (a contract's own PO, if any) |
| {contract_number} | The contract number |
| {supplier_tin} | The supplier's TIN |
| {supplier_name} | The supplier's name |

### What PAMS guards against

- The key is stored encrypted, never shown again, and hidden from people who only view payments, as is the address.
- PAMS only sends GET requests, does not follow redirects, waits at most the time you set (60 seconds at most), and reads at most 2 MB and 500 records.
- It refuses addresses on the PAMS server itself, link-local addresses and cloud metadata addresses.
- Adding, changing and removing a connection is recorded in the audit trail; the last call's result shows on the Integrations tab.

### Statuses and what moves them

| Status | Meaning |
|---|---|
| On | PAMS reads from it |
| Off | Kept, but not read |

### Forms and documents produced

None; the records stay in the other system.

### Tips

- If a call fails, the message says why: the address, the credentials, the time limit, or the answer not being JSON.
- Changing PAMS_JWT_SECRET (or PAMS_INTEGRATIONS_KEY) makes stored keys unreadable; enter them again.

<a id="locations-sso"></a>

## Locations, photos and single sign-on

Set up where assets are kept, add pictures of assets and items, and sign in through the agency's identity provider.

### Who can use it

- Settings → **Locations**: anyone with view_settings or view_properties can see it; edit_settings adds and changes locations.
- Asset photos: edit_properties or create_properties. Item photos: edit_inventory or create_inventory. Moving an asset: edit_properties, transfer_properties or assign_properties.

### What it is for

Locations record where each asset physically is, separately from who is accountable for it. Each move is kept in the asset's **Location** tab. A photo helps identify an asset during the physical count.

### Locations

1. Settings → **Locations** → **+ Add location**: name, building, floor and room.
2. In an asset, click **⌖ Move location** and choose where it now is.

> **Tip:** A location that has ever held assets cannot be deleted; set it to Inactive instead.

### Photos

Open the asset or item and click **Add photo**. On a phone this opens the camera. PNG, JPEG or WebP up to 5 MB.

### Single sign-on

When the administrator connects PAMS to the agency's identity provider (Microsoft Entra ID, Google Workspace, Keycloak or any OpenID Connect provider), the login page shows **Sign in with single sign-on**. Your PAMS account must already exist with the same email and be active; your PAMS role still decides what you can do.

### Statuses and what moves them

Not applicable.

### Forms and documents produced

Locations appear on the Property Inventory Form and the asset master list.

### Tips

- Password login keeps working alongside SSO.

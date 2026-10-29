# PAMS frontend (React)

React 19 · TypeScript · Vite · React Router · TanStack Query. Replaced the Vue app; every module has moved.

## Run

```bash
npm install
npm run dev
```

Opens on **5190** and proxies `/api` to the Spring Boot backend on 8095 (`PAMS_API_URL` overrides it).
For a release, `npm run build` writes `dist/`; the Spring Boot service serves it when `PAMS_FRONTEND_DIR` points
there (see `../docs/deployment-windows.md`).

## Adding a page

1. Add its endpoints in `backend-java`.
2. Add the page under `src/pages`, its calls to `src/api/endpoints.ts` (or a module file such as `src/api/payments.ts`)
   and types to `src/api/types.ts`.
3. Register the page in `ported` in `src/App.tsx` and add its entry in `src/layout/menu.ts` with the module key the
   backend's `Permissions.MODULES` grants.
4. Add or update its article in `src/pages/help/articles/`.

Every route is guarded by the user's `accessible_modules`, so typing a URL does not get round the menu.

Moved so far: login, layout, dashboard (account summary only), Users & Roles, Items & Categories, Warehouse
(reorder report, stock received, transfers, expiring stock), the stock card, Issuance & Requests (with printable
RIS / ICS / PAR), Stock Adjustments, Semi-Expendable Property, Assets & Property (register, transfers/returns,
depreciation, printable PAR / ICS / PTR / return slips), My Assets, Disposal, Gate Pass, Procurement Planning
(PPMP, APP, budget monitoring) and Purchase Requests (approval routing, CAF, printable Appendix 60 PR form), RFQ / alternative modes (quotations,
printable abstract, award, NOA/NTP), Competitive Bidding (ITB, bid opening, staged evaluation, MEARB,
post-qualification, protests, observers, timeline, Abstract of Bids), Purchase Orders (from awards, printable
Appendix 61), Receiving (inspection, acceptance into stock, printable Appendix 62 IAR), Contracts (securities, amendments,
extensions, liquidated damages, framework call-offs), Suppliers (register, eligibility documents, sanctions,
ratings and delivery performance), Physical Count (supplies count sheet against a frozen cut-off, tag-by-tag asset
count with found / missing / not in masterlist, independent verification, printable RPCI / RPCPPE / RPCSP by person or
department), Settings (own password / 2FA / signature, agency profile, document logos, signatories, idle sign-out
and password length), Audit Trail (filters, before/after detail, CSV export, print) and Log Trails (online now, stock
movements, sign-ins). The app signs people out after the configured idle time and records every print. Also BAC Management (meetings and attendance, resolutions, composition). Also Dashboard (waiting on you, inventory, assets, procurement, suppliers),
Monitoring (stock levels, re-order report, movement classes, request trends, procurement watch, scheduled alerts),
Reports (the COA forms with on-screen previews and their Excel downloads in the prescribed format), Procurement
Reports, APCPI, Documents (repository, versions, attachments panel on record windows), the notification bell,
QR labels and sticker sheets, and a scanner (USB scanner or phone camera) in the top bar. `/scan/:type/:id` is the
public page a phone opens from a label.

Everything in the old menu has moved.
Also Petty Cash, payment schedules on contract and PO windows, GPPB/COA planning forms on Procurement Reports, Data
import/export under Settings, assembled-unit parts on asset windows, and the in-app help centre (`/help`, user menu →
Help & user manual) with searchable articles for every module.

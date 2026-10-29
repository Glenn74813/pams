# PAMS frontend (React)

React 19 · TypeScript · Vite · React Router · TanStack Query. Replaces the Vue app in `../frontend`, one module at a
time.

## Run

```bash
npm install
npm run dev
```

Opens on **5190** and proxies `/api` to the Spring Boot backend on 8095 (`PAMS_API_URL` overrides it).
`VITE_LEGACY_APP_URL` (default `http://localhost:5180`) is where links to not-yet-moved modules point.

## Moving a module over

1. Port its endpoints in `backend-java`.
2. Add the page under `src/pages`, its calls to `src/api/endpoints.ts` and types to `src/api/types.ts`.
3. Register the page in `ported` in `src/App.tsx` and set `ported: true` on its entry in `src/layout/menu.ts`.

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
movements, sign-ins). The app signs people out after the configured idle time and records every print. Also BAC Management (meetings and attendance, resolutions, composition).

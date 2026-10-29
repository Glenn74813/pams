import { type Article, forms, list, p, purpose, sec, statuses, steps, table, tips, warn, who } from './types'

export const reportArticles: Article[] = [
  {
    slug: 'reports',
    title: 'Reports (COA forms)',
    area: 'reports',
    route: '/reports',
    keywords: ['COA', 'appendix', 'RSMI', 'RIS', 'RPCI', 'RPCPPE', 'RPCSP', 'IIRUP', 'RLSDDP', 'WMR', 'PTR', 'IAR', 'stock card', 'property card', 'excel', 'aging', 'reconciliation'],
    summary: 'All COA inventory and property forms in one place, with Excel exports filled from the system and signed by the signatories in Settings.',
    sections: [
      who(
        'The page opens for view_reports (seeded: most roles except Requestor, Custodian and End User).',
        'Some tabs list records from other modules (requests, movements, receiving, disposals), so they also need access to those modules to show data.',
      ),
      purpose(p('Pick a report on the left (or from the drop-down on narrow screens). Entity name and fund cluster come from Settings → Agency profile. Every download is recorded in the audit trail.')),
      sec(
        'inventory-reports',
        'Inventory and supplies',
        table(
          ['Report', 'Options', 'Output'],
          [
            ['Inventory Report', 'Category, status', 'Print'],
            ['Stock Card (Appendix 58)', 'Item', 'Excel'],
            ['Report of Supplies and Materials Issued, RSMI (Appendix 64)', 'Year, month', '**Generate RSMI** saves a numbered summary (RSMI-YYYY-MM-NNN) with history; Excel'],
            ['Requisition and Issue Slip, RIS (Appendix 63), with ICS (59) and PAR (71)', 'Search, status', 'Excel per request'],
            ['Report on the Physical Count of Inventories, RPCI (Appendix 66)', 'Inventory type, as-at date', 'Excel'],
            ['Inventory Aging', '90, 180 or 365 days', 'On screen'],
            ['Stock Reconciliation', 'None', 'On screen'],
          ],
        ),
      ),
      sec(
        'property-reports',
        'Property, plant and equipment',
        table(
          ['Report', 'Options', 'Output'],
          [
            ['Property Card (Appendix 69)', 'Asset', 'Excel'],
            ['Property Transfer Report, PTR (Appendix 76)', 'Search', 'Excel per movement'],
            ['Report on the Physical Count of PPE, RPCPPE (Appendix 73)', 'PPE type, as-at date', 'Excel'],
            ['Report on the Physical Count of Semi-Expendable Property, RPCSP (Appendix 68)', 'As-at date', 'Excel'],
            ['Inventory and Inspection Report of Unserviceable Property, IIRUP (Appendix 74)', 'Completed disposals; accountable officer, designation, station', 'Excel'],
            ['Report of Lost, Stolen, Damaged or Destroyed Property, RLSDDP (Appendix 75)', 'Property, status, circumstances, police report details', 'Excel'],
            ['Waste Materials Report, WMR (Appendix 65)', 'Completed disposals, storage place', 'Excel'],
          ],
        ),
      ),
      sec(
        'activity-reports',
        'Receiving and activity',
        table(['Report', 'Options', 'Output'], [['Inspection and Acceptance Report, IAR (Appendix 62)', 'Search', 'Excel per IAR'], ['Request Logs', 'From, to, department', 'On screen']]),
      ),
      sec(
        'how-to',
        'Produce a COA form',
        steps(
          'Check Settings → Agency profile (agency name, fund cluster) and Settings → Signatories once; they are printed on the forms.',
          'Choose the report on the left.',
          'Fill in the options (for example the as-at date or the disposals to include).',
          'Click the export button, open the Excel file, review it and print it for signature.',
        ),
        warn('The as-at date affects the Excel file only; the on-screen preview always shows today\'s figures.'),
      ),
      statuses(p('Not applicable. The RSMI history shows each generated summary.')),
      forms(p('See the tables above. Signatories used: Property Card and RLSDDP (Property Officer, Head of Agency); IIRUP (Property Officer, Head of Agency, Inspection Officer); WMR (Supply Officer, Head of Agency); IAR (Inspection Officer, Supply Officer).')),
      tips('Generate the RSMI yourself at month end; it is not generated automatically.', 'For IIRUP and WMR, complete the disposal in Disposal first; only completed disposals can be selected.'),
    ],
  },

  {
    slug: 'procurement-reports',
    title: 'Procurement Reports',
    area: 'reports',
    route: '/procurement-reports',
    keywords: ['PMR', 'procurement monitoring report', 'GPPB', 'APP report', 'BAC report', 'award report', 'excel'],
    summary: 'APP, Procurement Monitoring Report (summary and GPPB format), BAC and award reports per fiscal year, with Excel export.',
    sections: [
      who('The page opens for view_reports.'),
      purpose(p('Choose the report tab and the **FY**, then **⬇ Export Excel** if you need the file. Exports are recorded in the audit trail.')),
      sec(
        'list',
        'Available reports',
        list(
          '**APP Report**: APP lines by department with source of funds, mode, quantity, estimated cost, utilised, remaining and status, with totals.',
          '**PMR**: counts and values by status for PRs (by fiscal year), and RFQs, biddings, POs and contracts (by year created).',
          '**PMR (GPPB)**: awarded biddings and RFQs in the GPPB layout: PAP code, project, end user, mode, ABC, contract cost, awardee, the key dates (posting, opening, NOA, contract signing, NTP, acceptance), funds and remarks, with totals and savings.',
          '**BAC Report**: numbers of meetings, resolutions and evaluations, with meeting and resolution tables.',
          '**Award Report**: type, reference, title, supplier, amount, NOA and NTP, with totals.',
        ),
      ),
      statuses(p('Not applicable.')),
      forms(p('Excel file per report: {TYPE}_report_{year}.xlsx.')),
      tips('The PMR (GPPB) is the one usually submitted to GPPB; check the dates against the records before submitting.'),
    ],
  },

  {
    slug: 'apcpi',
    title: 'APCPI Self-Assessment',
    area: 'reports',
    route: '/apcpi',
    keywords: ['agency procurement compliance and performance indicators', 'GPPB', 'self-assessment', 'pillar', 'indicator', 'maturity'],
    summary: 'Score the 16 Agency Procurement Compliance and Performance Indicators for a fiscal year, with system suggestions and a printable result.',
    sections: [
      who('The page opens for view_reports.', 'Create, score, finalize, reopen and delete: manage_bac_meetings or approve_procurement.'),
      purpose(p('16 indicators across four pillars: Legislative and Regulatory; Institutional and Capacity; Operations and Market; Integrity and Transparency. Averages map to maturity bands: Advanced (2.51 and up), Maturing (1.51), Beginning (0.51), otherwise Negligible.')),
      sec(
        'assess',
        'Do an assessment',
        steps(
          'Click **+ New Assessment**, enter the **Fiscal year** and click Create. There can be one per fiscal year.',
          'Open it. For each indicator set the **Score** (0 to 3), **Means of Verification** and **Remarks**.',
          'Optionally click **Use system suggestions** to fill in scores the system can work out from its records. Review them; they are not saved until you save.',
          'Click **💾 Save Scores**.',
          'Click **✔ Finalize** when done. Any indicator still at 0 needs a remark first.',
        ),
      ),
      statuses(table(['Status', 'What moves it'], [['Draft', '+ New Assessment; ↩ Reopen'], ['Finalized', '✔ Finalize']]), p('Only drafts can be deleted.')),
      forms(p('**🖨 Print** prints the assessment from the browser.')),
      tips('Every score change is recorded in the audit trail.'),
    ],
  },

  {
    slug: 'documents',
    title: 'Documents',
    area: 'reports',
    route: '/documents',
    keywords: ['repository', 'upload', 'attachment', 'version', 'checksum', 'archive', 'file'],
    summary: 'The document repository: upload procurement and inventory files, keep versions with checksums, and attach files to records.',
    sections: [
      who(
        'The page opens for view_documents.',
        'Download: view_documents or upload_documents. Upload, add a version, archive and restore: upload_documents. Delete: delete_documents.',
      ),
      purpose(p('Page title "Document Repository". Types: PPMP, APP, Purchase Request, Request for Quotation, Abstract, BAC Resolution, Contract, Purchase Order, IAR, Notice of Award, Notice to Proceed, Disposal, Other.')),
      sec(
        'upload',
        'Upload a document',
        steps(
          'Click **+ Upload document**.',
          'Enter the **Title** and **Type**; optionally the **Document number**, **Category**, **Description** and **Remarks**.',
          'Choose the **File** (PDF, Word, Excel, PNG or JPG, up to 20 MB) and click **Upload**.',
        ),
      ),
      sec(
        'versions',
        'Versions, archive and delete',
        list(
          '**⬇** downloads the latest version.',
          '**🕘 Version history** lists each version (v1, v2, …) with its SHA-256 checksum and its own download.',
          '**＋ Upload new version** adds a version (not allowed while archived; a file identical to the current version is refused).',
          '**🗄 Archive** / **↺ Restore**.',
          '**🗑 Delete** hides the document; its files are kept for audit.',
        ),
      ),
      sec(
        'attachments',
        'Attachments on records',
        p('The detail windows of PPMPs, the APP, purchase requests, RFQs, biddings, POs, contracts, IARs, suppliers and assets have an **Attachments** panel: **+ Attach**, download and history. Attached files also appear in the repository with the record they belong to.'),
      ),
      statuses(p('Active or Archived; deleted documents disappear from the list but are kept.')),
      forms(p('None; this module stores files.')),
      tips('Scan and attach signed copies (PO with conforme, contract, NOA) to the record so auditors find them in one place.', 'Downloads are checked against the stored checksum and recorded in the audit trail.'),
    ],
  },

  {
    slug: 'audit-trail',
    title: 'Audit Trail',
    area: 'reports',
    route: '/audit',
    keywords: ['audit', 'history', 'changes', 'before', 'after', 'export', 'CSV', 'who changed'],
    summary: 'Who did what and when: every change with before and after values, plus prints, downloads and sign-outs.',
    sections: [
      who('The page opens for view_audit_trail (seeded: Admin, Budget Officer, Accountant, Auditor). **Export CSV** also needs export_audit_trail (Admin, Auditor).'),
      purpose(p('Each entry has the user and their role at the time, the action, the record, a description, the changed fields (before and after), IP address, browser and time. Prints and downloads are logged too. Sign-ins are in **Log Trails**, not here.')),
      sec(
        'find',
        'Find an entry',
        steps(
          'Type in **Search description or user**, or pick an **Action**, **Record type** or **User**.',
          'Set **From** and **To** dates if needed.',
          'Use the quick chips **Deleted records**, **Sign-outs** and **Prints** for common checks.',
          'Click a row to see the Field / Before / After table; changed fields are highlighted.',
        ),
      ),
      sec(
        'export',
        'Print or export',
        list('**🖨 Print**: up to 500 rows for the current filters.', '**Export CSV**: up to 10,000 rows, newest first, opens directly in Excel. The export itself is logged.'),
      ),
      statuses(p('Not applicable; entries cannot be edited or deleted from the app.')),
      forms(p('Audit trail printout and CSV file.')),
      tips('Narrow by date and record type before exporting large periods.'),
    ],
  },

  {
    slug: 'log-trails',
    title: 'Log Trails',
    area: 'reports',
    route: '/log-trails',
    keywords: ['sign-in', 'login attempts', 'online', 'sessions', 'stock movements', 'failed login'],
    summary: 'Who is online now, every sign-in attempt, and the stock movement ledger.',
    sections: [
      who('The page opens for view_log_trails (seeded: Admin, Auditor).'),
      purpose(p('Three views: **Online now**, **Stock movements** and **Sign-in activity**. Use the Audit Trail for record changes.')),
      sec(
        'views',
        'What each view shows',
        list(
          '**Online now**: people active within the idle limit (15 minutes if idle sign-out is off), with role, department, last activity, sign-in time and IP. Refreshes every minute.',
          '**Stock movements**: every stock-card entry with item, movement, quantity, balance, who (or "System") and notes. Filter by item/notes/person, movement and dates.',
          '**Sign-in activity**: every attempt with email, outcome ("Signed in", "Invalid credentials", "Account locked", "Invalid 2FA code"), IP and browser. Filter by email, outcome and dates.',
        ),
      ),
      statuses(p('Not applicable.')),
      forms(p('None; there is no export on this page.')),
      tips('Repeated "Invalid credentials" from one email followed by "Account locked" usually means a forgotten password; an administrator can set a new one in Users & Roles.'),
    ],
  },
]

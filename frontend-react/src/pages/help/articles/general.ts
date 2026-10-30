import { type Article, list, p, purpose, sec, statuses, steps, table, tip, tips, warn, who } from './types'

export const gettingStartedArticle: Article = {
  slug: 'getting-started',
  title: 'Getting started',
  area: 'general',
  keywords: ['login', 'sign in', 'password', 'two-factor', '2FA', 'authenticator', 'lockout', 'logout', 'menu', 'idle'],
  summary: 'Signing in, two-factor sign-in, the password rules, finding your way around the menu, and signing out.',
  sections: [
    sec(
      'sign-in',
      'Sign in',
      steps(
        'Open the system address your administrator gave you.',
        'Enter your **Email Address** and **Password** and click **Login**.',
        'If two-factor sign-in is on for your account, enter the 6-digit code from your authenticator app in **Authenticator Code** and click **Verify & Login**.',
      ),
      p('You land on the page you first asked for, or the Dashboard. There is no self-service password reset; "forgot password?" tells you to contact your system administrator.'),
      warn('After 5 failed attempts within 15 minutes the account is locked for 15 minutes. Wrong passwords and wrong 2FA codes both count, and attempts made while locked keep the lock going, so wait before trying again. The page tells you how many attempts are left.'),
    ),
    sec(
      'two-factor',
      'Turn on two-factor sign-in',
      steps(
        'Go to **Settings → My account → Two-factor sign-in** and click **Set up**.',
        'In your authenticator app (Google Authenticator, Microsoft Authenticator, Authy), add an account using the setup key shown. On a phone you can tap "open this link" instead.',
        'Type the 6-digit code the app shows and click **Turn on**.',
      ),
      p('To turn it off, enter your password and click **Turn off**. If you lose your phone, ask the administrator for help.'),
    ),
    sec(
      'passwords',
      'Password rules',
      list(
        'At least 8 characters (your administrator may require more), at most 72.',
        'At least one uppercase letter, one lowercase letter, one number and one special character.',
        'Not a commonly used password (for example "p@ssw0rd" or "admin@123").',
        'Must not contain the name part of your email address.',
      ),
      p('Change your password under **Settings → My account → Change password**.'),
    ),
    sec(
      'around',
      'Finding your way around',
      list(
        'The sidebar shows only the modules your role can open, grouped under Procurement, Vendor Management, Inventory, Assets / Property, Reports & Compliance and Administration. Click a group to open or close it. On a phone, ☰ opens the menu.',
        'The top bar has **⌖ Scan** (look up items, assets and gate passes) and the 🔔 notification bell.',
        'Most pages have a search box and filters above the list. Click a record\'s number or name to open its details, where the action buttons are.',
        'Buttons only appear for actions you are allowed to take on that record right now.',
      ),
    ),
    sec(
      'sign-out',
      'Signing out',
      p('Click your name at the bottom of the sidebar, then **Logout**. You are also signed out after a period without activity (30 minutes by default) and 60 minutes after signing in, so save your work as you go.'),
    ),
    tips('Use the "?" link on a page to jump to its help.', 'Printing and downloads are recorded in the audit trail.'),
  ],
}

export const dashboardArticle: Article = {
  slug: 'dashboard',
  title: 'Dashboard',
  area: 'general',
  route: '/dashboard',
  keywords: ['home', 'waiting on you', 'to do', 'pending', 'KPI', 'summary'],
  summary: 'Your to-do list ("Waiting on you"), your own open items, and summaries of inventory, assets, procurement and suppliers for the roles that need them.',
  sections: [
    who('The menu entry needs view_dashboard (all seeded roles have it). Each section below is shown only to people with the related permissions.'),
    purpose(p('Start here every day. Each line in **Waiting on you** links to the page where you can act.')),
    sec(
      'waiting',
      'Waiting on you',
      table(
        ['Line', 'Shown to'],
        [
          ['Purchase requests awaiting your approval', 'PR signatories (excluding your own PRs)'],
          ['Supply requests to approve', 'approve_requests'],
          ['Stock adjustments to approve', 'approve_adjustments'],
          ['Disposal requests to approve', 'approve_disposal'],
          ['Inspection reports awaiting acceptance', 'accept_delivery'],
          ['Physical counts to verify', 'approve_adjustments or manage_warehouse'],
          ['Gate passes past their return date', 'view_gate_pass'],
        ],
      ),
    ),
    sec(
      'sections',
      'Other sections',
      list(
        '**Mine** (everyone): my open supply requests, my open purchase requests, assets I hold and their value.',
        '**📦 Inventory**: items, stock value, low and out of stock, pending requests, expiring and expired lots, most issued items (30 days), requests by department (90 days).',
        '**🏢 Assets**: assets on record, assigned, available, under repair, acquisition cost, book value, disposals in progress.',
        '**🛒 Procurement — FY** (choose the **Fiscal year**): PRs in routing, open RFQs, biddings under way, POs awaiting delivery, active contracts, overdue and due-soon deadlines, APP utilisation, PRs by status, next deadlines.',
        '**🏪 Suppliers**: active suppliers, those with documents expiring in 30 days, largest suppliers by PO value, best-rated suppliers.',
      ),
    ),
    statuses(p('Not applicable; the dashboard only reads.')),
    tips('If a count looks wrong, open the linked page and check its filters; the dashboard uses the same data.'),
  ],
}

export const notificationsArticle: Article = {
  slug: 'notifications',
  title: 'Notifications',
  area: 'general',
  keywords: ['bell', 'alerts', 'unread', 'reminders', 'deadlines'],
  summary: 'The bell in the top bar tells you when something needs your action or concerns you, plus daily alerts for stock and deadlines.',
  sections: [
    who('Everyone. You receive a notification when your role holds the permission for the next step, or when the record is yours. You are not notified of your own actions.'),
    sec(
      'use',
      'Using the bell',
      steps(
        'The 🔔 shows how many unread notifications you have (refreshed every minute).',
        'Click it to see the latest 20.',
        'Click a notification to open the related page; it is marked read. **Mark all read** clears the count.',
      ),
      p('Read notifications older than 30 days are deleted every Sunday.'),
    ),
    sec(
      'events',
      'What sends a notification',
      table(
        ['Event', 'Who is told'],
        [
          ['PPMP submitted / approved / returned', 'PPMP reviewers / the preparer'],
          ['APP approved (procurement schedule)', 'Procurement, BAC and RFQ staff'],
          ['PR waiting at a step / approved / returned', 'That step\'s signatories / the requester'],
          ['BAC meeting scheduled, resolution for approval / returned / approved', 'BAC users / the drafter'],
          ['RFQ ready for award, RFQ awarded', 'Award users / RFQ and procurement staff'],
          ['Invitation to Bid posted, protest filed, bidding awarded', 'BAC and bidding staff'],
          ['PO confirmed by the supplier', 'Receiving staff (delivery expected)'],
          ['Delivery inspected; short or rejected delivery', 'Acceptance staff; procurement staff'],
          ['Contract terminated, extension requested, LDs reached 10%', 'Contract staff'],
          ['Supplier sanctioned', 'Procurement, BAC and award staff'],
          ['Supply request approved or rejected; low stock after issue', 'The requester; warehouse staff'],
          ['Asset issued, transferred or returned', 'The person concerned'],
          ['Disposal submitted / decided', 'Disposal approvers / the requester'],
          ['Physical count ready for verification / sent back / verified', 'Count staff / the initiator'],
        ],
      ),
      p('Daily alerts (reorder, expiring stock, bidding/RFQ/PO deadlines, contracts, supplier documents) and a quarterly "items due for procurement" reminder are also sent; see Inventory Monitoring for times.'),
    ),
    tips('Reorder and expiry alerts for the same item are repeated at most once every 7 days.'),
  ],
}

export const faqArticle: Article = {
  slug: 'faq',
  title: 'Frequently asked questions',
  area: 'general',
  keywords: ['help', 'problem', 'error', 'cannot', 'why'],
  summary: 'Quick answers to the questions supply and procurement staff ask most.',
  sections: [
    sec('cant-see-module', 'I cannot see a module in the menu.', p('Your role does not hold any permission that opens it. Ask your administrator to check "Roles and permissions" and update your role.')),
    sec('cant-see-button', 'The button I need is missing.', p('Buttons appear only when your role allows the action and the record is in the right status (for example, only drafts can be edited). If you prepared the record, the approval button is hidden from you: see Separation of Duties.')),
    sec('someone-else', 'The message says "someone else has to…".', p('This is a separation-of-duties rule. Ask a colleague with the approval permission to do the next step.')),
    sec('forgot-password', 'I forgot my password, or my account is locked.', p('Ask your system administrator to set a new password (Users & Roles → ✎). A lock clears itself 15 minutes after the last failed attempt; stop trying in the meantime.')),
    sec('signed-out', 'Why was I signed out?', p('After the idle limit (30 minutes by default) or 60 minutes after signing in. Sign in again; save often when filling long forms.')),
    sec('ics-or-par', 'Will my item get an ICS or a PAR?', p('It follows the item\'s COA classification: Semi-Expendable gets an ICS, PPE gets a PAR, and consumables get a RIS only. The capitalisation threshold in Settings → System (default ₱50,000) decides the classification.')),
    sec('stock-not-deducted', 'I submitted a request but stock has not gone down.', p('Stock is taken only when an approver clicks **Approve & issue**, not when the request is submitted.')),
    sec('expired-stock', 'Issuing fails because of expired stock.', p('Expired lots are never issued. Lower the issue quantity to what is usable, then write off the expired lots through Disposal (stock write-off) and the WMR.')),
    sec('delivery-not-in-stock', 'A delivery was inspected but is not in stock.', p('A second person with accept_delivery must open the IAR in Receiving and click **Accept into stock…**.')),
    sec('pr-not-available', 'My purchase request does not appear when creating an RFQ or bidding.', p('Only fully approved PRs are listed, and only lines not already on another live RFQ or bidding. For RFQs, the line\'s planned mode must match the RFQ mode.')),
    sec('cant-award', 'The award is refused.', p('Check that the supplier is active and eligible (documents complete and not expired, no sanction), the BAC resolution is approved and for the same PR, no protest is pending, and you did not prepare the RFQ or bidding.')),
    sec('cant-delete', 'I cannot delete a record.', p('Most records can be deleted only as drafts. Items with stock, suppliers with history, users with stock transactions and assets with movements are kept for audit; deactivate, cancel or dispose of them instead.')),
    sec('excel-error', 'An Excel download fails with an access error.', p('Some exports need view_reports or generate_reports in addition to the module permission. Ask your administrator.')),
    sec('print', 'How do I print a form?', p('Use the 🖨 button on the record; it opens a print-ready form. Use your browser\'s print dialog to choose the printer or "Save as PDF". COA Excel forms are under Reports.')),
    sec('more-help', 'Still stuck?', tip('Note the record number and the exact message, and contact your system administrator.')),
  ],
}


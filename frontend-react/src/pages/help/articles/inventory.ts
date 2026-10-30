import { type Article, forms, list, p, purpose, sec, statuses, steps, table, tip, tips, warn, who } from './types'

export const inventoryArticles: Article[] = [
  {
    slug: 'items',
    title: 'Items (Item Master)',
    area: 'inventory',
    route: '/items',
    keywords: ['catalogue', 'category', 'stock card', 'appendix 58', 'lot', 'reorder point', 'classification', 'consumable', 'semi-expendable', 'PPE', 'add stock', 'opening stock'],
    summary: 'The catalogue of everything the agency stocks: item codes, categories, COA classification, reorder levels, stock lots and the stock card.',
    sections: [
      who(
        'The page opens for view_inventory.',
        'Add items: create_inventory. Edit items and their lots: edit_inventory. Delete: delete_inventory.',
        'Add stock: add_stock or manage_warehouse.',
        'Categories: add with create_inventory or edit_inventory; edit with edit_inventory; delete with delete_inventory.',
      ),
      purpose(p('Page title "Item Master / Catalogue", with two tabs: **Items** and **Categories**. Every item has a COA classification (Consumable, Semi-Expendable or PPE) that decides which slip is issued later (RIS only, ICS, or PAR).')),
      sec(
        'add-item',
        'Add an item',
        steps(
          'Click **+ Add New Item**.',
          'Leave **Item code** blank to get the next number (ITEM-00001), or type your own unique code.',
          'Fill in **Name**, **Category**, **Unit of measure** and **COA classification**. Set the **Reorder point (min)** and **Maximum stock** (the maximum must be at least the minimum).',
          'Tick **Assembly** if the item is classified by its total assembled cost; this skips the cost check below.',
          'Optional: under **Opening stock**, click **+ Add lot** for stock already on hand (quantity, unit cost, brand, supplier, location, lot/batch no., expiry and received dates, warranty, specifications).',
          'Click **Create item**.',
        ),
        warn('Lot costs are checked against the COA capitalisation threshold (Settings → System, default ₱50,000). A Semi-Expendable lot at or above it must be PPE; a PPE lot below it must be Semi-Expendable.'),
      ),
      sec(
        'add-stock',
        'Add stock',
        steps(
          'Click **+** on the item row (or use **Warehouse → Stock received**).',
          'Enter the **Received date** (not in the future), **Quantity**, **Unit cost** and other lot details.',
          'Click **Record stock**. The lot is on hand at once and appears on the stock card.',
        ),
        tip('For deliveries under a PO, use Receiving instead. Accepting the IAR creates the lots for you.'),
      ),
      sec(
        'edit-item',
        'Edit an item or its lots',
        p('Click **✎**. To change lot quantities, tick **Edit stock lots**. Quantity changes made here post to the stock card straight away as an adjustment; they do not go through approval. Use **Stock Adjustments** when a second person should approve the change.'),
      ),
      sec(
        'stock-card',
        'Stock card',
        p('Click the item name, then the **Stock card** tab: Date, Reference, Receipt, Issue, Office and Balance. **Stock Card (Excel)** downloads the Appendix 58 layout; **🖨 Print** prints it.'),
      ),
      sec(
        'categories',
        'Categories',
        p('Categories have one level of sub-categories. **+ Sub-category** appears only while the parent has no items. Move or delete sub-categories and items before deleting a category.'),
      ),
      statuses(
        table(
          ['Badge', 'Meaning'],
          [
            ['Out of stock', 'Nothing on hand'],
            ['Reorder', 'On hand at or below the reorder point'],
            ['Over max', 'On hand above the maximum'],
            ['In stock', 'Anything else'],
          ],
        ),
      ),
      forms(p('Stock Card (Appendix 58) in Excel or print. **▦ QR labels for this page** prints QR stickers for the items listed.')),
      tips(
        'An item with stock on hand cannot be deleted; issue, adjust or dispose of the stock first. Items with requests or registered assets cannot be deleted either.',
        'Use the stock-level filter "Needs reorder" to see everything at or below the reorder point.',
      ),
    ],
  },

  {
    slug: 'warehouse',
    title: 'Warehouse / Stockroom',
    area: 'inventory',
    route: '/warehouse',
    keywords: ['reorder', 'transfer', 'location', 'expiring', 'expired', 'stock received', 're-order report'],
    summary: 'See stock levels and what to re-order, record stock received, move stock between locations and watch expiring lots.',
    sections: [
      who('The page opens for view_warehouse.', 'Record stock received and transfers: add_stock or manage_warehouse.'),
      purpose(p('Four tabs: **Reorder & stock levels**, **Stock received**, **Transfers** and **Expiring stock**.')),
      sec(
        'reorder',
        'Reorder and stock levels',
        p('Shows catalogue items, how many need reorder or are out of stock, and lots expiring or expired. Tick **Only items at or below the reorder point** to turn the list into a re-order report, then **🖨 Print**. "Suggested order" is the maximum minus what is on hand ("set a max" means no maximum is set).'),
      ),
      sec('stock-received', 'Stock received', p('Use **Record stock received for** to search an item and record a lot (same form as Add stock in Items). The tab lists lots with received date, brand, location, quantity now, unit cost, lot no., expiry and supplier.')),
      sec(
        'transfer',
        'Transfer stock between locations',
        steps(
          'On **Transfers**, choose the **Item** and **Transfer date**.',
          'Choose the **From lot / location**, the **Quantity** (up to what the lot holds) and the **To location**. Add **Remarks** if needed.',
          'Save. The transfer is numbered STR-YYYY-NNNN.',
        ),
        p('The total on hand does not change. If an identical lot is already at the destination, the stock is merged into it. **Transfer history** can be filtered by location.'),
      ),
      sec('expiring', 'Expiring stock', p('Set **Warn within N days** (1 to 365; starts at the Expiry warning in Settings). Two lists: "Expired — for disposal" and "Expiring within N days", each with lot count and value.')),
      statuses(p('Stock status badges are the same as in Items (Out of stock, Reorder, Over max, In stock).')),
      forms(p('Printable re-order report.')),
      tips('Expired lots are never issued. Write them off through Disposal (stock write-off) or a Stock Adjustment.', 'The re-order quantity here and in Monitoring is the same: top up to the maximum (or twice the minimum when no maximum is set).'),
    ],
  },

  {
    slug: 'issuance',
    title: 'Issuance (RIS, ICS and PAR)',
    area: 'inventory',
    route: '/requests',
    keywords: ['RIS', 'requisition and issue slip', 'ICS', 'inventory custodian slip', 'PAR', 'property acknowledgment receipt', 'supply request', 'appendix 63', 'appendix 59', 'appendix 71', 'FEFO', 'approve', 'issue'],
    summary: 'Offices request supplies; an approver issues them, stock is taken first-expiry-first-out, and the RIS, ICS or PAR is produced.',
    sections: [
      who(
        'The page opens for view_requests, view_own_requests or create_requests. Without view_requests you only see your own requests.',
        'New request: create_requests. Edit a pending request: edit_requests, or edit_own_requests for your own. Delete your own pending request: delete_own_requests.',
        'Approve and issue, or reject: approve_requests, and not on your own request (seeded: Department Head, Supply Officer).',
      ),
      purpose(p('Page title "Issuance & Requests". This is where offices requisition supplies and where the issuing officer releases them. The system assigns the RIS, ICS and PAR numbers on issue.')),
      sec(
        'request',
        'Request supplies',
        steps(
          'Click **+ New Request**.',
          'Choose the **Requesting office** and **Priority** (Medium by default). **Accountable person** is who will hold any semi-expendable items under the ICS; it defaults to you.',
          'Type the **Purpose** and add **Items** with quantities. Out-of-stock items are greyed out. Each item can appear only once.',
          'Click **Submit request**. The request is numbered REQ-YYYY-MM-NNNN and is Pending.',
        ),
        p('You may ask for more than is in stock; you may then receive part of it. Stock is only taken when the request is approved.'),
      ),
      sec(
        'process',
        'Approve and issue',
        steps(
          'Click **Process** on a pending request (not your own).',
          'For each line set the **Issue** quantity (it starts at the smaller of requested and in stock). Set a line to 0 to reject it.',
          'Click **Approve & issue**.',
        ),
        p('Stock is taken lot by lot: soonest expiry first, then oldest receipt; lots without an expiry date go last. Expired lots are never issued. You get a notice if an issued lot expires soon.'),
        p('To refuse the whole request, click **Reject request** and give a reason. The requester is notified either way.'),
        p('The stock column and the default issue quantity count only usable stock; expired lots are shown apart. Dispose of expired lots through a stock write-off (Disposal).'),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Pending', 'Submit request'],
            ['Issued', 'Approve & issue, with every line fully issued'],
            ['Partly issued', 'Approve & issue, with some lines short or rejected'],
            ['Rejected', 'Reject request, or every line set to 0'],
          ],
        ),
        p('Requests can be edited or deleted only while Pending.'),
      ),
      sec(
        'ics-or-par',
        'Which slip: RIS, ICS or PAR',
        list(
          'A RIS is issued for every request where anything was issued.',
          'If any Semi-Expendable item was issued, an ICS is also issued, and a semi-expendable accountability record is created for the accountable person.',
          'If any PPE item was issued, a PAR is also issued. Register the units as property in Assets.',
          'The slip follows the item\'s COA classification, which the capitalisation threshold (Settings → System, default ₱50,000) controls when the item is classified and when stock is received.',
        ),
      ),
      forms(
        p('Shown once a request is Issued or Partly issued, each with **🖨 Print** and an Excel button:'),
        list('Requisition and Issue Slip (RIS), Appendix 63', 'Inventory Custodian Slip (ICS), Appendix 59: semi-expendable lines only', 'Property Acknowledgment Receipt (PAR), Appendix 71: PPE lines only'),
      ),
      tips('After an issue, warehouse staff are alerted if the item drops to low stock.', 'You cannot approve your own request; another approver has to.'),
    ],
  },

  {
    slug: 'physical-count',
    title: 'Physical Count',
    area: 'inventory',
    route: '/physical',
    keywords: ['RPCI', 'RPCPPE', 'RPCSP', 'inventory count', 'appendix 66', 'appendix 73', 'variance', 'missing', 'found', 'recount', 'verify'],
    summary: 'Count supplies, PPE or semi-expendable property against the books, then have someone independent verify and post the differences.',
    sections: [
      who(
        'The page opens for view_physical_inventory.',
        'Set up, edit the draft and delete: create_physical_inventory (delete only by the person who set it up).',
        'Start, record, complete, send back and verify: conduct_physical_inventory.',
        'Seeded: Warehouseman, Supply Officer and Property Officer have all three.',
      ),
      purpose(p('A count covers one kind of stock: supplies (for the RPCI), PPE (RPCPPE) or semi-expendable property (RPCSP).')),
      sec(
        'set-up',
        'Set up and start a count',
        steps(
          'Click **+ New count**. Choose **What is counted** and the **Count date** (not more than a year ago). For supplies you can limit it to a **Category** (sub-categories included); for assets, to a **Department**. Add **Purpose** and **Remarks**.',
          'Save. The count is numbered PIC-YYYY-MM-NNNN.',
          'On or after the count date, click **Start counting**. This freezes the book figures: one line per item (supplies) or per property tag (assets).',
        ),
      ),
      sec(
        'count',
        'Count',
        list(
          '**Supplies**: enter **Counted**, **Location** and **Remarks** per line, then **Save N lines**. Movements since the cut-off are shown so you can reconcile. Enter 0 for items not on the shelf.',
          '**Assets**: scan or type the **Property tag**, set **Condition seen**, **Location** and **Remarks**, then click **Found**. A tag not on record needs a description. Per-tag buttons mark Found, Missing or Not yet checked.',
        ),
      ),
      sec(
        'complete-verify',
        'Complete, verify and post',
        steps(
          'Click **Complete count**. Supplies need every line counted. For assets, tags not checked become Missing. Count staff are notified.',
          'A different person (who did not set up, close or take part in the count) reviews it and clicks **Verify & post**, or **Send back for recount** with a reason.',
        ),
        p('Supplies: each difference becomes an approved Stock Adjustment that moves real lots (overages as a new lot, shortages written off expired-first). Assets: the condition seen becomes the condition on record; missing assets stay on record for follow-up (for example an RLSDDP in Reports).'),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Draft', 'Save'],
            ['Counting', 'Start counting; also Send back for recount'],
            ['For verification', 'Complete count'],
            ['Verified', 'Verify & post'],
          ],
        ),
        p('A count can be deleted only while Draft or Counting.'),
      ),
      forms(p('**🖨 Report** (any status after Draft) prints the Report on the Physical Count of Inventories (Appendix 66), of PPE (Appendix 73) or of Semi-Expendable Property, grouped by holder or department, optionally discrepancies only. The Excel RPCI, RPCPPE and RPCSP are in Reports.')),
      tips('Verification is refused if a shortage is now larger than what is on hand; recount or adjust first.', 'Separation of duties: whoever set up, closed or counted cannot verify.'),
    ],
  },

  {
    slug: 'stock-adjustments',
    title: 'Stock Adjustments',
    area: 'inventory',
    route: '/stock-adjustments',
    keywords: ['ADJ', 'shortage', 'overage', 'damage', 'loss', 'write-off', 'correction'],
    summary: 'Correct stock on hand for shortages, damage, loss or overages, with a second person approving before stock changes.',
    sections: [
      who(
        'The page opens for create_adjustments or approve_adjustments.',
        'File and withdraw: create_adjustments. Approve or reject: approve_adjustments, and not on your own adjustment.',
        'No seeded role except Super Admin has these permissions; an administrator must add them to a role.',
      ),
      purpose(p('A controlled way to change stock outside receipts and issues. Stock only changes when the adjustment is approved.')),
      sec(
        'file',
        'File an adjustment',
        steps(
          'Click **+ New Adjustment**.',
          'Choose the **Item** and **Change**: "Decrease (shortage, damage, loss)" or "Increase (overage, found)". Enter the **Quantity**.',
          'For increases, the **Unit cost** defaults to the item\'s average cost when left blank (required if the item has no cost history).',
          'Type the **Reason** and any **Remarks**, then click **File for approval**. It is numbered ADJ-YYYY-NNNN.',
        ),
        p('A decrease cannot be more than what is on hand.'),
      ),
      sec(
        'approve',
        'Approve or reject',
        p('Another user clicks **Approve** or **Reject**. An increase adds a lot located "Stock adjustment ADJ-…"; a decrease writes off expired lots first, then first-expiry-first-out. If stock has dropped since filing, approval is refused: reject it and file a new one.'),
        p('The person who filed it can **Withdraw** it while it is pending; this removes it.'),
      ),
      statuses(table(['Status', 'What moves it'], [['Pending approval', 'File for approval'], ['Approved', 'Approve'], ['Rejected', 'Reject']])),
      forms(p('No printed form; every approved adjustment appears on the item\'s stock card.')),
      tips('Physical count verification creates adjustments that are already approved.'),
    ],
  },

  {
    slug: 'semi-expendable',
    title: 'Semi-Expendable Property',
    area: 'inventory',
    route: '/semi-expendable',
    keywords: ['ICS', 'accountability', 'return', 'holder', 'RRSP'],
    summary: 'The ledger of semi-expendable items issued under an ICS: who holds what, and returns back to stock.',
    sections: [
      who('The page opens for view_inventory, manage_warehouse or add_stock.', 'Record a return: add_stock or manage_warehouse.'),
      purpose(p('Records are created automatically when semi-expendable lines are issued through Issuance. Search by holder or ICS number; **Only still with holders** is on by default.')),
      sec(
        'return',
        'Record a return',
        steps(
          'Click **Record return** on the row.',
          'Enter the **Quantity returned** (up to what is outstanding).',
          'Choose the **Condition**: "Serviceable — put back in stock" adds a lot at the average cost (location "Returned (semi-expendable)") and records it on the stock card; "Unserviceable — record for disposal" only logs it.',
          'Add **Remarks** and save.',
        ),
      ),
      statuses(table(['Status', 'Meaning'], [['With holder', 'Nothing returned'], ['Partly returned', 'Some returned'], ['Returned', 'All returned']])),
      forms(p('No forms on this page. The ICS is printed from the request in Issuance; the RPCSP is in Reports.')),
      tips('Semi-expendable units registered one by one with tags are managed in Assets instead (with the RRSP return slip).'),
    ],
  },

  {
    slug: 'monitoring',
    title: 'Inventory Monitoring',
    area: 'inventory',
    route: '/monitoring',
    keywords: ['fast-moving', 'slow-moving', 'non-moving', 're-order report', 'request trends', 'procurement watch', 'scheduled alerts', 'deadlines'],
    summary: 'Stock levels and movement analysis, the re-order report, request trends, procurement deadlines and the scheduled alert jobs.',
    sections: [
      who('The page opens for view_monitoring.', '**Scheduled alerts** tab: manage_system_settings.'),
      purpose(p('Page title "Inventory Monitoring & Controls". A read-only control panel; it does not change any records (except running alert jobs).')),
      sec(
        'tabs',
        'Tabs',
        list(
          '**Stock levels**: counts of items, out of stock, at or below reorder point, suggested re-order value, expired and expiring lots, open requests. Items are grouped as Out of stock / Low / Normal / Overstock / No levels set, and by movement over the last 90 days (Fast-moving: top 80% of units issued; Slow-moving; Non-moving), with usage per month and months of cover. PPE is left out.',
          '**Re-order report**: items at or below the reorder point; the suggested quantity tops up to the maximum, or to twice the minimum when no maximum is set. **🖨 Print re-order report**.',
          '**Request trends**: over 3 to 24 months, requests, fulfilled, rejected, still open, fulfilment rate and units issued, with a chart.',
          '**Procurement watch**: overdue and due-soon deadlines for bidding stages, quotation deadlines, PO deliveries, delivery delays, contract expiry, security validity, supplier documents and APP schedules.',
          '**Scheduled alerts**: run all alert jobs now, or one at a time.',
        ),
      ),
      sec(
        'jobs',
        'Scheduled jobs (Philippine time)',
        table(
          ['Job', 'When'],
          [
            ['Bidding, RFQ and PO deadlines', 'Daily 08:15'],
            ['Contract delivery, expiry and securities', 'Daily 08:30'],
            ['Supplier eligibility documents', 'Daily 08:40'],
            ['Reorder alerts (7-day cooldown per item)', 'Daily 08:45'],
            ['Expired and expiring stock', 'Daily 08:50'],
            ['APP lines due this quarter', 'First day of each quarter'],
            ['Monthly depreciation posting', '01:00 on the 1st'],
            ['Delete read notifications older than 30 days', 'Sundays 03:00'],
            ['Database backup (kept 7 days)', 'Daily 02:00'],
          ],
        ),
      ),
      statuses(p('Not applicable; the page reports on other modules.')),
      forms(p('Printable re-order report.')),
      tips('The expiring count uses the expiry warning days from the settings, the same as the Warehouse **Expiring stock** tab.'),
    ],
  },

  {
    slug: 'gate-pass',
    title: 'Gate Pass',
    area: 'assets',
    route: '/gate-pass',
    keywords: ['pass-out slip', 'GP', 'overdue', 'released', 'returned', 'guard'],
    summary: 'Record an asset leaving the premises with a printed gate pass, and its return.',
    sections: [
      who(
        'The page opens for view_gate_pass (seeded: Property Officer). It sits in the Inventory menu group.',
        'Issue a gate pass: create_gate_pass. Mark returned: create_gate_pass or edit_gate_pass.',
      ),
      purpose(p('There is no separate approval step: issuing the pass is the release. Passes are kept as history and are not deleted.')),
      sec(
        'issue',
        'Issue a gate pass',
        steps(
          'Click **+ New Gate Pass**.',
          'Search the **Asset** by tag, serial or item. Retired and disposed assets cannot leave on a gate pass, and an asset can only have one open pass.',
          'Check **Carried out by** (pre-filled with the holder), type the **Purpose**, and set **Date released**, **Expected back** (blank if it is not coming back) and **Condition on release**.',
          'Click **Issue gate pass**. It is numbered GP-YYYY-MM-NNNN and the print view opens.',
        ),
      ),
      sec(
        'return',
        'Mark returned',
        steps(
          'Click **Mark returned** on the row.',
          'Enter **Date back** (not before release, not in the future), **Condition** (good, fair, poor, damaged, lost), **Returned by** and **Remarks**.',
        ),
        p('Good, fair and poor are copied to the asset\'s condition; damaged sets it to poor. A returned pass cannot be changed.'),
      ),
      statuses(table(['Status', 'Meaning'], [['Out', 'Released and not back'], ['Overdue', 'Still out after the expected date'], ['Returned', 'Mark returned done']]), p('The list starts on **Out**; switch to Overdue, Returned or All.')),
      forms(p('**🖨** prints the Gate Pass (pass-out slip) with signature lines for Released by, Carried out by and Guard on duty.')),
      tips('While an asset is out on a gate pass it cannot be returned, retired or disposed of.', 'A pass returned as "lost" marks the asset Lost; it stays on its custodian\'s PAR/ICS until relieved. File the RLSDDP in Reports, then record the turn-in and a disposal. **Mark found** on the asset reverses it.', 'Returned passes can be deleted by holders of delete_gate_pass; passes still out can be edited.'),
    ],
  },

  {
    slug: 'disposal',
    title: 'Disposal and Write-Off',
    area: 'inventory',
    route: '/disposal',
    keywords: ['IIRUP', 'WMR', 'unserviceable', 'condemnation', 'auction', 'donation', 'write-off', 'waste materials', 'DSP'],
    summary: 'Propose and carry out the disposal of an unserviceable asset or the write-off of consumable stock, feeding the IIRUP and WMR.',
    sections: [
      who(
        'The page opens for view_disposal (seeded: Property Officer). It sits in the Inventory menu group.',
        'Create, edit, withdraw and submit: create_disposal, and only the person who drafted it.',
        'Approve, reject and complete: approve_disposal (seeded: Admin), and not on your own request.',
      ),
      purpose(p('Page title "Disposal & Write-Off". Two kinds: an **Asset (PPE / semi-expendable)**, or a **Stock write-off** of a quantity of an item.')),
      sec(
        'request',
        'Request a disposal',
        steps(
          'Click **+ New Disposal Request** and choose the tab.',
          'Pick the **Asset**, or the **Item** and **Quantity** (not more than on hand).',
          'Choose the **Mode of disposal**: Condemnation, Destruction, Public auction, Sale through negotiation, Sale, Barter, Donation or Transfer to another agency.',
          'Type the **Reason** and **Remarks**, then click **Save draft** (numbered DSP-YYYY-MM-NNNN).',
          'Click **Submit**. Approvers are notified.',
        ),
        warn('An asset must not be issued to anyone (record its return first), must not be out on a gate pass, and must not already have an open disposal request.'),
      ),
      sec(
        'decide',
        'Approve and complete',
        steps(
          'An approver opens **Review…**, checks the attached documents (inspection report, photos, appraisal), adds **Remarks** (required to reject) and clicks **Approve** or **Reject**.',
          'After the actual disposal, click **Complete…** and enter **Date disposed**, **Proceeds (₱)** for sale modes, and **Remarks**.',
        ),
        p('Completing an asset disposal records its book value and marks it Disposed. Completing a stock write-off removes the quantity, expired lots first.'),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Draft', 'Save draft'],
            ['Awaiting approval', 'Submit'],
            ['Approved', 'Approve'],
            ['Rejected', 'Reject (final; file a new request if needed)'],
            ['Completed', 'Complete…'],
          ],
        ),
        p('Only drafts can be withdrawn.'),
      ),
      forms(p('Completed disposals are selected in **Reports** to produce the IIRUP (Appendix 74) and the Waste Materials Report (Appendix 65).')),
      tips('The asset does not need to be retired first.'),
    ],
  },
]

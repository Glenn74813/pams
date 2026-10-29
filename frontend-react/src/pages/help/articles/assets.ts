import { type Article, forms, list, p, purpose, sec, statuses, steps, table, tips, warn, who } from './types'

export const assetArticles: Article[] = [
  {
    slug: 'assets',
    title: 'Assets and Property',
    area: 'assets',
    route: '/properties',
    keywords: ['PPE', 'property', 'tag', 'PAR', 'ICS', 'PTR', 'property transfer report', 'PRS', 'RRSP', 'return', 'repair', 'retire', 'depreciation', 'property card', 'appendix 69', 'appendix 76', 'masterlist'],
    summary: 'The masterlist of PPE (under a PAR) and tagged semi-expendable property (under an ICS): register units, transfer, return, repair, retire and post depreciation.',
    sections: [
      who(
        'The page opens for view_properties or view_own_properties. Without view_properties you see only the assets you are accountable for.',
        'The tabs **Asset register**, **Transfers & returns** and **Depreciation** need view_properties; **Depreciation** also needs edit_properties.',
        'Register: create_properties or assign_properties. Edit, repair, back in service, retire, reinstate, post depreciation: edit_properties.',
        'Transfer: transfer_properties. Return: transfer_properties or assign_properties. Issue to someone: assign_properties or transfer_properties. Undo a registration: delete_properties.',
        'Seeded: Property Officer has everything except delete.',
      ),
      purpose(p('Page title "Assets & Property". Each unit has a tag number, its accountable person, condition, status, and (for PPE) a depreciation schedule.')),
      sec(
        'register',
        'Register assets',
        steps(
          'Click **+ Register Assets**.',
          'Choose the **Item (PPE or semi-expendable)** and the **From stock lot** the units come from. Consumables cannot be registered.',
          'Set **Date issued** (not in the future), the **Accountable person** and **Condition**. For PPE, set **Useful life (years)** (1 to 50, default 5) and **Residual value (₱)** (blank means 5% of cost).',
          'Click **+ Add unit** for each unit and enter its **Serial no.** and **Tag no.** (blank gives an automatic PROP-YYYY-… tag). Tags and serials must be unique.',
          'Click **Register N units**.',
        ),
        p('All units share one PAR (PPE) or ICS (semi-expendable) number. They leave the stock lot, and the holder is notified. Brand, specifications, warranty and cost come from the lot.'),
      ),
      sec(
        'movements',
        'Transfer, return and re-issue',
        p('Click the tag number to open the asset, then:'),
        list(
          '**➜ Transfer** (asset in use): choose **To**, **Date** and **Reason**. Creates a Property Transfer Report (PTR).',
          '**↩ Record return**: choose **Date**, **Condition** and **Reason**. Creates a Property Return Slip (PPE) or Receipt of Returned Semi-Expendable Property (RRSP). The asset becomes Available with no holder.',
          '**Issue to someone** (available, no holder): choose **To**, **Date** and an optional **Reason**. Creates a new PAR or ICS.',
        ),
        p('After each movement the printable slip opens. Holders are notified of transfers and returns.'),
        warn('An asset out on a gate pass cannot be returned or retired. Record its return through the gate first.'),
      ),
      sec(
        'status-changes',
        'Repair, retire and reinstate',
        list(
          '**🔧 Send for repair** (in use or available) → Under repair. **Back in service** returns it to In use (if it has a holder) or Available.',
          '**Retire** (available or under repair, no holder, not on a gate pass) → Retired. **Reinstate** → Available.',
          'Disposed is set only by completing a disposal in the Disposal module.',
        ),
      ),
      sec(
        'edit',
        'Edit details',
        p('**✎ Edit details** changes tag no., serial no., condition, brand/model, warranty months, useful life and specifications. Once depreciation has been posted, useful life and residual value are fixed. Disposed assets cannot be edited.'),
        p('**🗑** undoes a registration and puts the unit back into its stock lot, but only if the asset has no transfers, gate passes, disposals or posted depreciation.'),
      ),
      sec(
        'depreciation',
        'Depreciation',
        p('Straight-line, residual value 5% of cost unless set, starting the month after acquisition. On the **Depreciation** tab choose **Post through** (a month) and click **Post depreciation**. Each month is posted once per asset, so running it again is safe; future months are refused. A scheduled job also posts it at 01:00 on the 1st of each month. Each asset\'s Depreciation tab shows the yearly schedule.'),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['In use', 'Register, Issue to someone, Back in service (with holder), Transfer'],
            ['Available', 'Record return, Back in service (no holder), Reinstate'],
            ['Under repair', 'Send for repair'],
            ['Retired', 'Retire'],
            ['Disposed', 'Completing a disposal'],
          ],
        ),
      ),
      forms(
        list(
          'Property Acknowledgment Receipt (PAR), Appendix 71, and Inventory Custodian Slip (ICS), Appendix 59: **🖨 PAR** / **🖨 ICS**',
          'Property Transfer Report (PTR), Appendix 76: after a transfer, and **PTR** (Excel) in the History tab',
          'Property Return Slip / Receipt of Returned Semi-Expendable Property',
          'Property Card, Appendix 69 (Excel)',
          '**▦ QR sticker** for one asset, or **▦ QR stickers for this page**',
        ),
      ),
      tips(
        'The asset detail also shows its history, gate passes and attachments.',
        'Changing a tag number makes its printed QR sticker invalid; print a new one.',
        'Property Card and PTR Excel downloads need view_reports, generate_reports or view_properties.',
      ),
    ],
  },

  {
    slug: 'my-assets',
    title: 'My Assets',
    area: 'assets',
    route: '/my-assets',
    keywords: ['accountability', 'my PAR', 'my ICS', 'holder', 'custodian'],
    summary: 'Everything you are accountable for under a PAR or ICS, with its cost and condition.',
    sections: [
      who('Everyone who can sign in.'),
      purpose(p('A read-only list of the assets under your name: tag no., item, serial no., PAR/ICS, since when, cost, condition and status, with totals for "Items under my accountability" and "Total acquisition cost".')),
      sec('view', 'See an asset', steps('Click the tag number.', 'View its details, history and depreciation, print your PAR/ICS, or print its **▦ QR sticker**.')),
      statuses(p('Same as Assets: In use, Available, Under repair, Retired, Disposed.')),
      forms(p('Your PAR or ICS for each asset.')),
      tips(
        'If something listed is not with you, tell the Property Officer so it can be transferred or reported (RLSDDP).',
        'To give an asset back, ask the Property Officer to record the return; you cannot do it from this page.',
      ),
    ],
  },
]

export const qrArticle: Article = {
  slug: 'qr-codes',
  title: 'QR Codes and Scanning',
  area: 'general',
  keywords: ['scan', 'barcode', 'sticker', 'label', 'camera', 'scanner'],
  summary: 'Print QR stickers for items, assets and gate passes, and look records up with a scanner, the camera or by typing.',
  sections: [
    who(
      'Item labels: view_inventory, view_warehouse or manage_warehouse. Asset stickers: view_properties, or the holder for their own asset. Gate pass codes: view_gate_pass.',
      'The **⌖ Scan** button in the top bar: everyone signed in.',
    ),
    purpose(p('Every sticker holds a link to the record with a short check code, so a scan can show that the label is genuine.')),
    sec(
      'print',
      'Print labels',
      list('Items: **▦ QR labels for this page** on the Items tab.', 'Assets: **▦ QR sticker** in an asset, or **▦ QR stickers for this page** on the register (up to 200).', 'Gate passes: shown on the gate pass print view.'),
      p('A sticker shows the agency, the code (item code, tag or GP number), the name, and the category and unit or the serial number and PAR/ICS number.'),
    ),
    sec(
      'scan',
      'Scan or look up a record',
      steps(
        'Click **⌖ Scan** in the top bar.',
        'Scan with a USB or Bluetooth scanner, or type an item code, asset tag, gate-pass number, serial number or the QR link, then click **Find**.',
        'On phones and browsers that support it, click **📷 Camera** to use the camera.',
        'Click **Open in Items / Assets / Gate Pass** to go to the record.',
      ),
    ),
    sec(
      'public-scan',
      'Scanning without signing in',
      p('Anyone who scans a sticker with a phone sees a "Verified label" page without signing in. For an asset this shows its tag, serial, brand, status, condition, and the accountable person, office and PAR/ICS number. For an item: code, category, unit and quantity on hand. For a gate pass: status, purpose, who carried it, dates and the asset. A tampered or unknown code shows "This QR code is not valid".'),
      warn('Because these details are visible to anyone holding the sticker, do not stick asset labels where the public can scan them if that is a concern for your office.'),
    ),
    statuses(p('Not applicable.')),
    forms(p('QR label sheets.')),
    tips('Changing an asset tag makes its old sticker invalid; reprint it.', 'Physical counts of assets accept scanned tags in the Property tag field.'),
  ],
}

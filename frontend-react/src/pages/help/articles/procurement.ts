import { type Article, forms, list, p, purpose, sec, statuses, steps, table, tip, tips, warn, who } from './types'

export const procurementArticles: Article[] = [
  {
    slug: 'procurement-planning',
    title: 'Procurement Planning (PPMP and APP)',
    area: 'procurement',
    route: '/procurement-planning',
    keywords: ['PPMP', 'APP', 'annual procurement plan', 'project procurement management plan', 'budget', 'consolidate', 'ceiling'],
    summary:
      'Departments prepare their PPMPs; approved PPMPs are consolidated into one Annual Procurement Plan per fiscal year, and budget use is tracked against purchase requests.',
    sections: [
      who(
        'The page opens for anyone with one of: view_procurement_planning, create_ppmp, view_own_ppmp, edit_own_ppmp, delete_own_ppmp, submit_own_ppmp, approve_ppmp, manage_app or monitor_budget.',
        'The **PPMP** tab is always shown. The **Annual Procurement Plan** and **Budget monitoring** tabs need view_procurement_planning, manage_app or monitor_budget.',
        'Agency-wide users (view_procurement_planning, approve_ppmp or manage_app) see every PPMP. Users with create_ppmp only see their own department\'s PPMPs. Users with only the "own PPMP" permissions see only the PPMPs they prepared.',
        'Create a PPMP: create_ppmp. Submit: create_ppmp or submit_own_ppmp. Review, approve or return: approve_ppmp. Consolidate, edit and approve the APP: manage_app.',
        'Seeded roles: Department Head and Purchaser can prepare PPMPs; Budget Officer reviews PPMPs and manages the APP.',
      ),
      purpose(
        p('Each office lists what it plans to buy next fiscal year in a Project Procurement Management Plan (PPMP). Once approved, the PPMPs are consolidated into the Annual Procurement Plan (APP). Purchase requests are then drawn on APP lines, and the **Budget monitoring** tab shows how much of the plan has been used.'),
        p('Use the **FY** selector at the top to switch fiscal year. It offers last year up to two years ahead and starts on next year.'),
      ),
      sec(
        'prepare-ppmp',
        'Prepare a PPMP',
        steps(
          'On the **PPMP** tab, click **+ New PPMP**.',
          'Choose the **Department** (only agency-wide users get a choice; everyone else uses their own department), the **Fiscal year**, and type a **Title**. **Remarks** are optional.',
          'Click **+ Add line** for each item or project. Fill in **Description**, **Quantity** (at least 1), **Est. unit cost (₱)** and **Mode of procurement**. Optional: **Category** (Goods by default), **Source of funds**, **Schedule** (Q1 to Q4), **Accounting code** (UACS object code), **Technical specifications** and **Green / sustainable procurement**.',
          'Click **Save draft**. The PPMP gets a number like PPMP-2027-0001 (the fiscal year, not the calendar year).',
          'When it is complete, open it and click **Submit for review**.',
        ),
        p('A PPMP needs at least 1 line and can have up to 200.'),
        warn('Shopping and Small Value Procurement are capped at ₱2,000,000 per line by default (the cap can be changed by the administrator). The form warns "Over the ₱… ceiling for this mode", and saving is refused until you lower the amount or choose another mode such as Competitive Bidding.'),
      ),
      sec(
        'review-ppmp',
        'Review a PPMP',
        steps(
          'Open a submitted PPMP and click **Take up for review** to show the preparer it is being looked at (optional).',
          'Click **Approve**, or click **Return** and say what needs revising.',
        ),
        p('Returning a PPMP sends it back to the preparer for editing and raises its revision number by one. The preparer is notified either way.'),
      ),
      sec(
        'consolidate-app',
        'Consolidate and approve the APP',
        steps(
          'Go to the **Annual Procurement Plan** tab and click **⚙ Consolidate APP**. This creates APP-<FY>-001, titled "Annual Procurement Plan <FY>", from every approved PPMP of that year.',
          'If more PPMPs are approved later, a warning appears with a **Reconsolidate** button. Click it to add them (allowed until the APP is approved).',
          'Click **Open APP** and then **Edit lines** to adjust description, mode, schedule, quantity or budget.',
          'Someone other than the person who consolidated it clicks **Approve APP**.',
        ),
        p('Approving the APP notifies procurement, BAC and RFQ staff that the procurement schedule is out. An APP can be deleted only before approval and only if no purchase request uses it; its PPMPs can then be consolidated again.'),
      ),
      sec(
        'budget-monitoring',
        'Budget monitoring',
        p('The **Budget monitoring** tab shows, by department, by mode and by quarter: Planned, On purchase requests, Utilized (fully approved PRs only) and Remaining.'),
        p('Each APP line moves from Planned to In progress to Completed as purchase requests draw on it.'),
      ),
      statuses(
        table(
          ['Status', 'What moves it', 'Who'],
          [
            ['Draft', 'Created with + New PPMP', 'Preparer'],
            ['Submitted', 'Submit for review', 'create_ppmp or submit_own_ppmp'],
            ['Under review', 'Take up for review', 'approve_ppmp'],
            ['Approved', 'Approve (from Submitted or Under review)', 'approve_ppmp, not the preparer'],
            ['Returned', 'Return, with a reason', 'approve_ppmp, not the preparer'],
          ],
        ),
        p('Only Draft and Returned PPMPs can be edited or deleted. Approved PPMPs are locked; once consolidated the list shows "in APP-…".'),
        p('The APP is Consolidated until someone clicks **Approve APP**; an approved APP cannot be edited, reconsolidated or deleted.'),
      ),
      forms(p('This module prints nothing itself. The APP Report (with Excel export) is on the **Procurement Reports** page.')),
      tips(
        'Plan each line with the mode you will actually use. RFQs and biddings only accept PR lines whose planned mode matches.',
        'You cannot lower an APP line\'s quantity below what purchase requests already take.',
        'The person who prepared a PPMP cannot review it, and the person who consolidated the APP cannot approve it.',
      ),
    ],
  },

  {
    slug: 'purchase-requests',
    title: 'Purchase Requests',
    area: 'procurement',
    route: '/purchase-requests',
    keywords: ['PR', 'CAF', 'certificate of availability of funds', 'appendix 60', 'signatory', 'routing', 'department head', 'budget officer', 'accounting', 'BAC secretariat'],
    summary:
      'Raise a purchase request (usually drawn on the approved APP), route it through four signatures and a Certificate of Availability of Funds, and print the Appendix 60 form.',
    sections: [
      who(
        'The page opens for: view_procurement, create_procurement, approve_procurement, or any of the signatory permissions pr_approve_department, pr_approve_budget, pr_approve_accounting, pr_approve_bac.',
        'Holders of view_procurement, approve_procurement or any pr_approve_* permission see every PR. Everyone else sees only the PRs they raised.',
        'Raise a PR: create_procurement or edit_procurement (seeded: Purchaser, End User).',
        'Each signature needs its own permission: Department Head (pr_approve_department), Budget Officer (pr_approve_budget), Accounting (pr_approve_accounting), BAC Secretariat (pr_approve_bac).',
        'Certify funds (CAF): pr_approve_budget or pr_approve_accounting.',
      ),
      purpose(p('A purchase request (PR) is the formal request to buy. Once all four signatories have signed, the PR can go to an RFQ (alternative modes) or to Competitive Bidding.')),
      sec(
        'raise-pr',
        'Raise a purchase request',
        steps(
          'Click **+ New Purchase Request**.',
          'Check the **Requesting office** (only users with edit_procurement or view_procurement can change it). Pick the **Annual Procurement Plan**, or "Not in the APP". Set **Priority** and type the **Purpose**. **Remarks** are optional.',
          'Add lines. Under **Add from APP-…**, click a line to bring it in with its remaining quantity and unit cost (only your office\'s lines that still have a balance are shown). Use **+ Line not in the APP** for anything else.',
          'Click **Save draft**. The PR gets a number like PR-202610-00001 (year and month of creation).',
          'Open it and click **Submit for approval**.',
        ),
        warn('Quantities and costs cannot exceed what is left on the APP line. Drafts count against the balance too, so delete drafts you will not use.'),
      ),
      sec(
        'sign-pr',
        'Sign a purchase request',
        p('Signatories start on the **Awaiting my signature** tab. Open a PR and click **Sign as <step>**. The steps go in this order:'),
        steps('Department Head', 'Budget Officer', 'Accounting', 'BAC Secretariat'),
        p('To send it back, click **Return** and say what needs revising. The requester is notified and can edit and click **Resubmit**; routing then starts again from step 1.'),
      ),
      sec(
        'certify-funds',
        'Certify funds (CAF)',
        steps(
          'While the PR is in routing, a Budget Officer or Accounting user clicks **Certify funds**.',
          'Enter the **Amount certified (₱)** (at least the PR total) and any **Remarks**, then click **Issue CAF**. A CAF number like CAF-2026-0001 is assigned.',
        ),
        p('The Budget Officer cannot sign until funds are certified; the button is greyed out with "Certify funds first". If the PR total later goes above the certified amount, a new CAF is needed.'),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Draft', 'Save draft'],
            ['In routing (Submitted / Under review)', 'Submit for approval; the Stage column shows whose signature is next'],
            ['Approved', 'The BAC Secretariat signs (the last step)'],
            ['Returned', 'The current signatory clicks Return'],
            ['Cancelled', 'Cancel PR by the requester or an edit_procurement user, from Draft, Returned or In routing'],
          ],
        ),
        p('Only Draft and Returned PRs can be edited. Draft, Returned and Cancelled PRs can be deleted; the number is not reused. An approved PR cannot be cancelled. Cancelling gives the APP balance back.'),
      ),
      forms(p('**🖨 PR form** prints the Purchase Request (Appendix 60) in any status, drafts included. "Requested by" is the requester and "Approved by" is whoever signed the Department Head step.')),
      tips(
        'You cannot sign or certify funds for a PR you raised yourself.',
        'approve_procurement makes you an alternate signatory for any step (e.g. an officer-in-charge), but not for two steps of the same PR.',
        'If you start typing a return reason and then decide to sign instead, clear the text box first: the text is sent with the signature as remarks.',
        'The office of a PR is fixed when it is created. To change it, cancel the PR and raise a new one.',
      ),
    ],
  },

  {
    slug: 'rfq',
    title: 'RFQ and Alternative Modes',
    area: 'procurement',
    route: '/rfq',
    keywords: ['request for quotation', 'shopping', 'small value procurement', 'SVP', 'negotiated', 'direct contracting', 'repeat order', 'abstract of quotations', 'NOA', 'NTP', 'notice of award', 'notice to proceed'],
    summary:
      'Buy through alternative modes (Shopping, Small Value Procurement, Negotiated, Direct Contracting and others): issue the RFQ, record quotations, award, and issue the NOA and NTP.',
    sections: [
      who(
        'The page opens for view_rfq, create_rfq or generate_abstract.',
        'create_rfq: create, edit, issue, close, cancel and delete RFQs, and record or evaluate quotations (seeded: Purchaser, BAC Secretariat).',
        'process_award: award, issue the Notice of Award and Notice to Proceed (seeded: BAC Chairperson).',
      ),
      purpose(
        p('Use this module for any approved PR line planned under an alternative mode: Shopping, Small Value Procurement, Negotiated Procurement, Direct Contracting, Direct Acquisition, Repeat Order, Limited Source Bidding, Competitive Dialogue, Unsolicited Offer or Direct Sales.'),
        p('Direct Contracting, Direct Acquisition, Repeat Order, Direct Sales and Unsolicited Offer are single-source: they need a written justification and take one quotation only.'),
      ),
      sec(
        'create-rfq',
        'Create and issue an RFQ',
        steps(
          'Click **+ New RFQ** and choose the **Approved purchase request**. Only approved PRs with lines not already on a live RFQ or bidding are listed.',
          'Pick the **Mode**. It must match the mode planned on the PR lines (shown as "Planned: …").',
          'Fill in **Title**, **Deadline for quotations**, **Delivery term**, **Delivery within (days)** and **Instructions to suppliers**. For single-source modes, fill in the **Justification**.',
          'Tick the lines to include and set **Quantity** and **ABC / unit** (not more than the PR unit cost).',
          'Click **Save draft**, then **Issue to suppliers** when ready. The deadline must still be in the future.',
        ),
      ),
      sec(
        'quotations',
        'Record and evaluate quotations',
        steps(
          'Click **+ Record quotation**. Choose the **Supplier** (or **+ New supplier…**), enter the **Quotation no.**, the **Received** date and the **Quoted / unit** for every line.',
          'Mark a quotation **Non-responsive** (with a reason) if it fails the requirements. **Restore** undoes this.',
          'After the deadline, click **Close & open quotations**. Users with process_award are notified that it is ready for award.',
        ),
        warn('Late quotations are refused: the received date cannot be after the deadline. A supplier added here for the first time is registered as not verified and not eligible, so verify their documents in Suppliers before the award.'),
      ),
      sec(
        'award',
        'Award, NOA and NTP',
        steps(
          'On a closed RFQ, click **Award…**. Choose **Award to** and the approved **BAC resolution** for this PR.',
          'If fewer than 3 quotations were received (other than single-source modes), explain why in **Remarks**.',
          'Click **Issue Notice of Award**, then **Issue Notice to Proceed**. Dates default to today.',
        ),
        p('The award must go to the lowest responsive quotation from an eligible supplier within the ABC. To pass over a lower quotation, mark it non-responsive first. The BAC resolution must be approved, be a Resolution to award, Award or Recommendation, be for the same PR, and not already back another award.'),
        p('Once the NOA is issued, the award appears in **Purchase Orders** under **+ New PO from award**.'),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Draft', 'Save draft'],
            ['Open for quotations', 'Issue to suppliers'],
            ['Closed — for award', 'Close & open quotations (only after the deadline)'],
            ['Awarded', 'Award… by a process_award user who did not prepare the RFQ'],
            ['Cancelled', 'Cancel RFQ, any time before the award (a reason is needed unless it is a draft)'],
          ],
        ),
        p('Only drafts can be edited or deleted.'),
      ),
      forms(p('**🖨 Abstract** prints the Abstract of Quotations (any status except Draft), with signature lines for two BAC Members, the Vice-Chairperson and the Chairperson. NOA and NTP numbers (NOA-YYYY-NNNN, NTP-YYYY-NNNN) are recorded; the notices themselves are not printed from here.')),
      tips(
        'Check the supplier is eligible before you award. Ineligible suppliers appear in the award list but the award is refused.',
        'The person who prepared the RFQ cannot award it.',
        'After two failed biddings of a PR, its Competitive Bidding lines can go to an RFQ under Negotiated Procurement directly.',
      ),
    ],
  },

  {
    slug: 'bidding',
    title: 'Competitive Bidding',
    area: 'procurement',
    route: '/bidding',
    keywords: ['public bidding', 'ITB', 'invitation to bid', 'bid opening', 'post-qualification', 'MEARB', 'LCRB', 'bid security', 'protest', 'reconsideration', 'observers', 'abstract of bids', 'failure of bidding', 'RA 12009'],
    summary:
      'Run public bidding under RA 12009 from the pre-procurement conference to the Notice to Proceed, including bid security, four-stage evaluation, protests and failure of bidding.',
    sections: [
      who(
        'The page opens for view_bidding, manage_bidding or process_award.',
        'manage_bidding: create and edit, set criteria, pre-procurement conference, Invitation to Bid, open bids, record bids, declare failure, cancel, protests and observers (seeded: BAC Chairperson, BAC Secretariat).',
        'Evaluation and scores: manage_bidding or conduct_bid_evaluation.',
        'Award, NOA and NTP: process_award.',
      ),
      purpose(p('For PR lines planned as Competitive Bidding (or with no mode). Evaluation is either "Lowest calculated responsive bid" or "MEARB (price + rated criteria)".')),
      sec(
        'set-up',
        'Set up the bidding',
        steps(
          'Click **+ New bidding** and choose the **Approved purchase request**. A warning shows if the PR already has failed biddings.',
          'Choose the **Evaluation** method (for MEARB, set the **Price weight (%)**, default 70), fill in **Title**, **Delivery term**, **Delivery within (days)**, and the lines with quantity and ABC per unit (not more than the PR cost). Save.',
          'MEARB only: click **Set criteria**. The weights must total 100, and this must be done before the Invitation to Bid.',
          '**Pre-procurement conference**: record the date and notes. Required when the ABC is over ₱2,000,000; otherwise optional (shown as "skipped").',
          '**Post Invitation to Bid**: enter the **ITB reference**, **Posted on** (not in the future), **Posted at**, the **Deadline for bids** (after posting) and **Bid opening** (defaults to the deadline).',
        ),
      ),
      sec(
        'open-and-record',
        'Open and record bids',
        steps(
          'After the deadline, click **Open bids**.',
          'For each bidder, click **+ Record bid** and enter the **Bidder**, **Bid as read (₱)**, the **Bid security** form and **Security amount (₱)**.',
        ),
        table(
          ['Bid security form', 'Minimum'],
          [
            ['Cash, cashier\'s/manager\'s check, bank draft or letter of credit', '2% of the ABC'],
            ['Surety bond', '5% of the ABC'],
            ['Bid Securing Declaration', 'None'],
          ],
        ),
        warn('Record every bid read at the opening. A bid entered late can still be added during evaluation until post-qualification starts, and a bid can be corrected until it has been evaluated; striking out a bid is only possible before evaluation.'),
      ),
      sec(
        'evaluate',
        'Evaluate',
        p('For each bid, click **Pass <stage>** or **Fail** (with a reason). The stages go in order: Eligibility, Technical, Financial, Post-qualification.'),
        list(
          'Eligibility cannot pass without enough bid security.',
          'Financial cannot pass a bid above the ABC.',
          'Post-qualification starts only when every bid has finished the first three stages. It goes to the top-ranked bidder and moves down only if that bidder fails. An ineligible supplier cannot pass.',
          'MEARB: enter **Scores** per bidder until post-qualification starts.',
        ),
      ),
      sec(
        'award',
        'Award, NOA and NTP',
        steps(
          'Click **Award…**. The winner is fixed as the post-qualified bidder. Choose the approved **BAC resolution** and, optionally, a **Contract recommendation**.',
          'Click **Issue Notice of Award**, then **Issue Notice to Proceed**.',
        ),
        p('Open protests block the award. The award then appears in **Purchase Orders** and **Contracts**.'),
      ),
      sec(
        'protests',
        'Protests, observers and failure',
        list(
          '**Reconsideration & protests**: file a Request for Reconsideration or a Protest once the bidding is advertised. Then **Take up**, **Deny** or **Uphold** (BAC resolution text required), or mark **Withdrawn**.',
          '**Observers**: record COA, CSO, professional organisation or other observers. Invite at least two.',
          '**Declare failure** (with the ground) during Invitation, Bid opening or Evaluation. The PR lines are freed. After two failures the system points to Negotiated Procurement.',
          'The **Timeline** panel shows the legal maximums: ITB to opening 50 days, opening to NOA 40, ITB to NOA 90, NOA to NTP 17.',
        ),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Draft', 'Save'],
            ['Pre-procurement', 'Pre-procurement conference recorded'],
            ['Invitation', 'Post Invitation to Bid'],
            ['Bid opening', 'Open bids'],
            ['Evaluation', 'Automatically, at the first evaluation result or score'],
            ['Awarded', 'Award… by a process_award user who did not prepare the bidding'],
            ['Failed', 'Declare failure'],
            ['Cancelled', 'Cancel bidding, before award or failure'],
          ],
        ),
        p('Terms and criteria can be edited only in Draft or Pre-procurement. Only drafts can be deleted.'),
      ),
      forms(p('From Bid opening onward: **🖨 Abstract of Bids** (printed) and **Abstract (Excel)**. The ITB, NOA and NTP are recorded by reference number; they are not printed from here.')),
      tips('Numbering: PB-YYYY-NNNN for biddings; NOA and NTP numbers are shared with RFQs.', 'The person who prepared the bidding cannot award it.'),
    ],
  },

  {
    slug: 'bac',
    title: 'BAC Management',
    area: 'procurement',
    route: '/bac',
    keywords: ['bids and awards committee', 'meeting', 'resolution', 'quorum', 'composition', 'members', 'attendance', 'minutes'],
    summary: 'Keep the Bids and Awards Committee roster, schedule and record meetings with attendance, and draft and approve BAC resolutions.',
    sections: [
      who(
        'The page opens for view_bac, manage_bac_meetings, manage_bac_resolutions or conduct_bid_evaluation.',
        'Meetings and the **Composition** roster: manage_bac_meetings.',
        'Resolutions: manage_bac_resolutions.',
        'Seeded: BAC Chairperson and BAC Secretariat have both.',
      ),
      purpose(p('Three tabs: **Meetings**, **Resolutions** and **Composition**. Approved award resolutions are required before any RFQ or bidding can be awarded.')),
      sec(
        'composition',
        'Composition (members)',
        steps(
          'Click **+ Add member**.',
          'Optionally link a **System user**, then fill in **Full name** and **Designation** (Chairperson, Vice-Chairperson, Member, Provisional member, Secretariat, Technical Working Group).',
          'Add **Position**, **Office**, **Office order no.**, **Order date** and **Term from / to**, and tick **Active**.',
        ),
        p('A banner checks the RA 12009 composition: a chairperson, a vice-chairperson, at least 5 voting members and a secretariat. It is a reminder only; nothing is blocked. Removing a member who has attended meetings makes them inactive instead of deleting them.'),
      ),
      sec(
        'meetings',
        'Meetings',
        steps(
          'Click **New meeting** and enter the **Title** and **Date and time** (plus **Venue** and **Agenda**).',
          'At the meeting, click **Start**. Attendance is pre-filled from active members whose term has not ended; add others if needed.',
          'Record the attendance and click **Mark held**.',
        ),
        p('Quorum is a majority of the seated voting members. Once held, only the minutes and remarks can change, and the meeting cannot be deleted. A meeting cited by a resolution cannot be cancelled.'),
      ),
      sec(
        'resolutions',
        'Resolutions',
        steps(
          'Click **Draft BAC resolution**. Enter the **Title** and **Type** (Resolution to award, Recommendation, Award, Mode of procurement, Declaration of failure, Other).',
          'For Resolution to award and Award, choose the **Purchase request**. Optionally choose the meeting it was **Adopted at**, and type the **Resolution** text.',
          'Click **Save draft**, then **Submit for approval**.',
          'Another resolutions user clicks **Approve**, or **Return** (with a reason) to send it back to draft.',
          'Approved resolutions can be **Archived** later.',
        ),
        p('If a meeting is cited, it must be held and have had a quorum, or approval is refused.'),
      ),
      statuses(
        table(
          ['Record', 'Statuses'],
          [
            ['Meeting', 'Scheduled → In session (Start) → Held (Mark held); or Cancelled'],
            ['Resolution', 'Draft → For approval (Submit) → Approved; Return sends it to Draft; Approved → Archived'],
          ],
        ),
        p('Only draft resolutions can be edited or deleted, and not once an award cites them.'),
      ),
      forms(p('Meetings and resolutions are numbered BAC-MTG-YYYY-001 and BAC-RES-YYYY-001. There is no printed resolution or minutes form; attach the signed copy with **+ Attach** in Documents if needed.')),
      tips(
        'Do not archive a resolution before its award is made: the award needs an approved (not archived) resolution.',
        'The person who drafted a resolution cannot approve or return it.',
        'One resolution can back only one live award.',
      ),
    ],
  },

  {
    slug: 'purchase-orders',
    title: 'Purchase Orders',
    area: 'procurement',
    route: '/procurement',
    keywords: ['PO', 'appendix 61', 'conforme', 'supplier confirmed', 'award'],
    summary: 'Issue the purchase order on an award that has its Notice of Award, have it approved and sent, record the supplier\'s conforme, and track delivery.',
    sections: [
      who(
        'The page opens for view_procurement.',
        'Create, edit, delete, record "Supplier confirmed" and cancel: edit_procurement (seeded: Purchaser).',
        'Approve & send: edit_procurement or approve_procurement, and not the person who prepared the PO.',
        'Users without procurement permissions see only POs for their own PRs.',
      ),
      purpose(p('A PO is issued against an RFQ or bidding award that already has its Notice of Award. Delivery against the PO is then recorded in **Receiving**.')),
      sec(
        'create-po',
        'Create a PO',
        steps(
          'Click **+ New PO from award** and choose the **Award**. Only awards with a Notice of Award and no live PO are listed.',
          'Check the **Delivery date** (defaults to today plus the award\'s delivery days) and **Place of delivery**, and add **Remarks** if needed.',
          'Prices: RFQ POs use the winning quotation. Bidding POs are pre-priced in proportion to the winning bid; you may adjust lines, but they must add up to the awarded bid.',
          'Click **Save draft**. The PO gets a number like PO-2026-0001.',
        ),
      ),
      sec(
        'send-confirm',
        'Approve, send and confirm',
        steps(
          'Someone other than the preparer opens the draft and clicks **Approve & send**. A delivery date is required.',
          'When the supplier signs the conforme, click **Supplier confirmed**. Receiving staff are notified that a delivery is expected.',
        ),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Draft', 'Save draft'],
            ['Sent to supplier', 'Approve & send'],
            ['Confirmed', 'Supplier confirmed'],
            ['Partly delivered', 'Automatically, when IARs cover part of the order'],
            ['Delivered', 'Automatically, when IARs cover the whole order'],
            ['Cancelled', 'Cancel PO (draft, sent or confirmed; a reason is needed unless it is a draft)'],
          ],
        ),
        p('Only drafts can be edited or deleted; deleting frees the award for a new PO. A PO cannot be cancelled once any delivery has been inspected against it. Rejected IARs do not count as delivered.'),
      ),
      forms(p('**🖨 PO form** prints the Purchase Order (Appendix 61) with the penalty clause, fund source, CAF number and Conforme signature block. Attach the signed copy in the Attachments panel.')),
      tips('Only active, eligible suppliers can receive a PO.', 'The delivery date cannot be before the order date.'),
    ],
  },

  {
    slug: 'contracts',
    title: 'Contracts',
    area: 'procurement',
    route: '/contracts',
    keywords: ['performance security', 'amendment', 'extension', 'liquidated damages', 'LD', 'framework agreement', 'call-off', 'termination'],
    summary: 'Manage the contract on an award: performance security, amendments, time extensions, liquidated damages and framework call-offs.',
    sections: [
      who(
        'The page opens for view_contracts, create_contracts or edit_contracts.',
        'Create and delete drafts: create_contracts. Edit, record securities, amendments, extension requests and call-offs: create_contracts or edit_contracts.',
        'Put in force, complete, terminate, approve amendments, grant or deny extensions, return or forfeit securities and record LD assessments: edit_contracts.',
      ),
      purpose(p('For awards that need a formal contract. Types: Goods, Services, Infrastructure, Consulting and Framework Agreement.')),
      sec(
        'create-contract',
        'Draft a contract',
        steps(
          'Click **+ New contract** and choose the **Award** (it must have its NOA and no live contract).',
          'Fill in **Title**, **Type**, **Effective from**, **Ends on**, **Signed on**, **Delivery within (days)**, **Scope** and **Special conditions**. The price is the awarded amount.',
          'Click **Save draft**. The number looks like CON-2026-0001.',
        ),
        tip('Create the PO before the contract. The contract links to the PO only if it already exists, and delivery tracking and LDs rely on that link.'),
      ),
      sec(
        'put-in-force',
        'Put the contract in force',
        steps(
          'In **Securities**, click **+ Record security** and enter the Kind, Form, **Amount**, Reference no. and Valid until.',
          'Make sure the start, end and signed dates are set.',
          'Someone other than the preparer clicks **Put in force**.',
        ),
        table(
          ['Performance security form', 'Minimum'],
          [
            ['Cash/check, bank draft or guarantee, letter of credit', '5% of the price (10% for Infrastructure)'],
            ['Surety bond', '30% of the price'],
          ],
        ),
        p('The security must be valid until the end date. Framework Agreements do not need one.'),
      ),
      sec(
        'during',
        'While the contract is in force',
        list(
          '**Amendments**: **+ Amendment**, describe what changes, any price change and new end date, then **Draft amendment**. Someone else approves it. Total changes are capped at 10% of the original price.',
          '**Extension requests**: **+ Record request** with the days asked and the ground. It must be filed before the deadline; only one can be pending. Grant (up to the days asked) or Deny (with remarks).',
          '**Delivery & liquidated damages**: shows the deadline, delivered value and LDs (days late × 0.1% × undelivered value, capped at 10%; at the cap the contract may be terminated). Click **Record assessment** to record them.',
          '**Call-offs** (Framework Agreements): **+ Call-off** with a title and amount within the agreement, then Issue and Complete (or Cancel). Numbered CO-YYYY-NNNN.',
          'The term and price can only change through an amendment. Scope, conditions and remarks can still be edited.',
        ),
      ),
      statuses(
        table(
          ['Status', 'What moves it'],
          [
            ['Draft', 'Save draft'],
            ['In force', 'Put in force (not by the preparer)'],
            ['Completed', 'Complete, once everything is delivered and no extension is pending; unrecorded LDs are assessed automatically'],
            ['Terminated', 'Terminate, from Draft or In force, with a ground'],
          ],
        ),
      ),
      forms(p('No printed form; attach the signed contract in the Attachments panel. Securities can be returned (the performance security only after completion) or forfeited with a ground.')),
      tips('Use the **Ending within 30 days** filter to see contracts about to expire.'),
    ],
  },

  {
    slug: 'receiving',
    title: 'Receiving and Inspection (IAR)',
    area: 'procurement',
    route: '/receiving',
    keywords: ['IAR', 'inspection and acceptance report', 'appendix 62', 'delivery', 'accept', 'reject', 'lot', 'expiry'],
    summary: 'Record deliveries against confirmed POs, inspect them, and accept them into stock with the Inspection and Acceptance Report.',
    sections: [
      who(
        'The page opens for view_receiving.',
        'Record, edit or delete an inspection: create_receiving (the inspection committee).',
        'Accept into stock or reject: accept_delivery, and not the person who recorded the inspection.',
        'Seeded: Warehouseman and Supply Officer have both; make sure two different people do the two steps.',
      ),
      purpose(p('Two tabs: **Expected deliveries** (confirmed or partly delivered POs) and **Inspection & acceptance**. Accepting a delivery creates stock lots in Items at the PO unit cost.')),
      sec(
        'record-delivery',
        'Record and inspect a delivery',
        steps(
          'On **Expected deliveries** (or in the PO), click **Record delivery**.',
          'Enter **Delivered on** (not before the PO date, not in the future) and **Inspected on** (between delivery and today), plus **Observations**.',
          'For each line enter **Accepted** and **Rejected**. They must add up to what was received. If anything is rejected, fill in **Why rejected?**.',
          'Click **Save inspection**. Users with accept_delivery are notified.',
        ),
        p('Accepted quantities cannot be more than what is still outstanding on the PO.'),
      ),
      sec(
        'accept',
        'Accept into stock',
        steps(
          'Open the IAR and click **Accept into stock…**.',
          'For each accepted line pick the **Inventory item** (or **+ New catalogue item**), and optionally a **Lot no.**, **Expires** date (not before delivery) and **Location**.',
          'Confirm. Each line becomes a new stock lot dated the delivery date, shown on the stock card as a receipt.',
        ),
        p('The COA capitalisation threshold is checked: a semi-expendable item at or above the threshold, or a PPE item below it, is refused. If anything was short or rejected, procurement staff are notified to follow up with the supplier.'),
        p('To refuse the whole delivery, click **Reject** and say why; the quantities go back to outstanding. If nothing passed inspection, reject it instead of accepting.'),
      ),
      statuses(
        table(
          ['Kind', 'Values'],
          [
            ['Inspection result', 'Complete (all passed), Partial (fewer than outstanding or some rejected), Failed (nothing accepted)'],
            ['Acceptance', 'For acceptance → In stock, In stock (partial) or Rejected'],
          ],
        ),
        p('An IAR can be edited or deleted only while it is For acceptance. Accepted quantities update the PO to Partly delivered or Delivered.'),
      ),
      forms(p('**🖨 IAR** prints the Inspection and Acceptance Report (Appendix 62). **⬇ IAR (Excel)** downloads it with the Inspection Officer and Supply Officer signatories from Settings (needs view_receiving, accept_delivery or view_reports).')),
      tips(
        'Accepting creates stock only. PPE and semi-expendable units are registered as property later, in Assets or through Issuance.',
        'Numbering: IAR-YYYY-NNNN.',
      ),
    ],
  },

  {
    slug: 'suppliers',
    title: 'Suppliers (Vendors)',
    area: 'procurement',
    route: '/suppliers',
    keywords: ['vendor', 'eligibility', 'PhilGEPS', 'blacklist', 'suspension', 'sanction', 'rating', 'performance', 'mayor\'s permit', 'tax clearance'],
    summary: 'Register suppliers, keep their eligibility documents, verify eligibility, record sanctions and rate their performance.',
    sections: [
      who(
        'The page opens for view_suppliers, create_suppliers or edit_suppliers.',
        'Register: create_suppliers. Edit, verify eligibility, sanctions and removing documents: edit_suppliers. Add documents: create_suppliers or edit_suppliers. Delete: delete_suppliers.',
        'Rate a supplier: edit_suppliers, accept_delivery or edit_procurement.',
      ),
      purpose(p('Only active, eligible suppliers with no sanction can be awarded, given a PO or a contract.')),
      sec(
        'register',
        'Register a supplier',
        steps(
          'Click **+ Register supplier**.',
          'Fill in the **Registered name** (must be unique) and as many details as you have: Vendor code (VND-NNNN is assigned if blank), TIN (123-456-789-000), PhilGEPS reg. no., Credit terms, Contact, Email, Phone, Mobile, Website, Address, Notes and MSME.',
          'Save, then open the supplier to upload documents.',
        ),
      ),
      sec(
        'eligibility',
        'Verify eligibility',
        p('These documents must be on file and not expired: PhilGEPS registration, DTI or SEC registration, Mayor\'s Permit, BIR Registration and Tax Clearance.'),
        steps('Upload each document with its expiry date.', 'Click **Mark eligible**. If something is missing or expired, the message lists it.'),
        p('Use **Mark ineligible…** (with a reason) to withdraw eligibility. Removing or changing a required document also drops eligibility. Deactivating a supplier clears it too.'),
      ),
      sec(
        'sanctions',
        'Sanctions',
        p('**+ Record sanction**: Suspension (with an Until date) or Blacklist, plus the grounds. The supplier becomes ineligible at once and procurement staff are notified. **Lift…** (with a reason) removes it; verify the supplier again afterwards.'),
      ),
      sec(
        'ratings',
        'Ratings and performance',
        p('Rate **Delivery** and **Quality** (1 to 5), and optionally Timeliness, Compliance and After-sales, against a PO or contract reference. One rating per evaluator per reference; only the evaluator can withdraw it. An average of 3 or more counts as satisfactory.'),
        p('The **Performance** panel shows awards, POs and their value, on-time deliveries, units rejected, average rating and liquidated damages.'),
      ),
      statuses(p('The list shows the supplier as Eligible, Not verified, Inactive, or the sanction type. Filter by Active, Eligible, Not verified, Inactive or All.')),
      tips('A supplier with transaction history cannot be deleted; deactivate it instead.', 'Suppliers added from an RFQ quotation start as not verified.'),
    ],
  },
]

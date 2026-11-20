package com.avtech.pams.report.outgoing;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.avtech.pams.report.outgoing.OutgoingDocs.*;

/**
 * Letters to suppliers: the Notice of Award and Notice to Proceed (for a bidding or an RFQ award), the Invitation to Bid,
 * and the notice of liquidated damages. The Head of the Procuring Entity signs the award, proceed and damages notices;
 * the BAC Chairperson signs the Invitation to Bid. The supplier's conforme is left for a wet signature.
 */
@Component
public class NoticeForms {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.ENGLISH);
    private static final String LAST = "F";
    private static final int CHARS = 112;

    private final JdbcTemplate jdbc;
    private final OutgoingDocs docs;

    public NoticeForms(JdbcTemplate jdbc, OutgoingDocs docs) {
        this.jdbc = jdbc;
        this.docs = docs;
    }

    /** The award a notice is about, from a bidding or an RFQ. */
    private record Award(String reference, String title, String supplier, String address, BigDecimal amount, Object offerDate, String offerKind,
                         String basis, boolean bidding, String noaNumber, Object noaDate, String ntpNumber, Object ntpDate, String deliveryTerm,
                         Object leadDays, String contractNumber) {
    }

    private Award award(String source, Long id) {
        boolean bidding = "bidding".equals(source);
        List<Map<String, Object>> found = bidding
                ? jdbc.queryForList("""
                        select b.bidding_number ref, b.title, s.name, s.address, b.awarded_amount amount, b.bid_opening_date offered, b.evaluation_method method,
                               b.noa_number, b.noa_date, b.ntp_number, b.ntp_date, b.delivery_term, b.delivery_lead_days,
                               (select min(c.contract_number) from contracts c where c.bidding_id = b.id and c.deleted_at is null) contract_number
                        from biddings b left join suppliers s on s.id = b.awarded_supplier_id where b.id = ? and b.deleted_at is null""", id)
                : jdbc.queryForList("""
                        select r.rfq_number ref, r.title, s.name, s.address, q.total_amount amount, q.submitted_at offered, r.procurement_method method,
                               r.noa_number, r.noa_date, r.ntp_number, r.ntp_date, r.delivery_term, r.delivery_lead_days,
                               (select min(c.contract_number) from contracts c where c.rfq_id = r.id and c.deleted_at is null) contract_number
                        from rfqs r left join suppliers s on s.id = r.awarded_supplier_id
                        left join rfq_quotations q on q.rfq_id = r.id and q.supplier_id = r.awarded_supplier_id and q.is_winner = ?
                        where r.id = ? and r.deleted_at is null""", true, id);
        if (found.isEmpty()) throw ApiException.notFound(bidding ? "Bidding" : "RFQ");
        Map<String, Object> a = found.get(0);
        if (a.get("name") == null) throw ApiException.of(org.springframework.http.HttpStatus.CONFLICT, "No supplier has been awarded yet.");
        String method = str(a.get("method"));
        String basis = !bidding ? "Lowest Calculated and Responsive Quotation"
                : "mearb".equals(method) ? "Most Economically Advantageous Responsive Bid"
                : "marb".equals(method) ? "Most Advantageous Responsive Bid" : "Lowest Calculated Responsive Bid";
        return new Award(str(a.get("ref")), str(a.get("title")), str(a.get("name")), str(a.get("address")), num(a.get("amount")), a.get("offered"),
                bidding ? "bid" : "quotation", basis, bidding, (String) a.get("noa_number"), a.get("noa_date"), (String) a.get("ntp_number"),
                a.get("ntp_date"), (String) a.get("delivery_term"), a.get("delivery_lead_days"), (String) a.get("contract_number"));
    }

    /** Letter head: date and reference on the right, then the addressee. Returns the next row. */
    private int addressee(Xlsx x, int row, String number, Object date, String supplier, String address) {
        x.merge("D" + row + ":F" + row);
        x.set("D" + row, OutgoingDocs.date(date));
        x.style("D" + row).right();
        row++;
        x.merge("D" + row + ":F" + row);
        x.set("D" + row, blank(number) ? "Reference No. ____________" : number);
        x.style("D" + row).bold().right();
        row += 2;
        x.merge("A" + row + ":D" + row);
        x.set("A" + row, supplier.toUpperCase(Locale.ROOT));
        x.style("A" + row).bold();
        row++;
        x.merge("A" + row + ":D" + row);
        x.set("A" + row, address);
        x.style("A" + row).wrap();
        return row + 2;
    }

    private Xlsx letter(String title) {
        return docs.sheet(title, 16, 16, 16, 16, 16, 16);
    }

    public byte[] noticeOfAward(String source, Long id) {
        Award a = award(source, id);
        Xlsx x = letter("Notice of Award");
        int row = docs.letterhead(x, LAST);
        row = docs.title(x, row, LAST, null, "NOTICE OF AWARD");
        row = addressee(x, row, a.noaNumber(), a.noaDate(), a.supplier(), a.address());
        row = docs.para(x, row, "A", LAST, "Dear Sir/Madam:", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "We are pleased to notify you that your " + a.offerKind() + (a.offerDate() == null ? "" : " dated " + OutgoingDocs.date(a.offerDate()))
                + " for the " + a.title() + " (" + a.reference() + "), for a contract price equivalent to " + words(a.amount()).toUpperCase(Locale.ROOT)
                + " (" + peso(a.amount()) + "), is hereby accepted, being the " + a.basis() + ".", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, a.bidding()
                ? "You are hereby required to provide, within ten (10) calendar days from receipt of this Notice, the performance security in the form and amount "
                + "stipulated in the Bidding Documents, and to enter into a contract with this Office. Failure to provide the performance security shall constitute "
                + "sufficient ground for the cancellation of the award and the forfeiture of your bid security."
                : "You are hereby required to sign the contract or Purchase Order within ten (10) calendar days from receipt of this Notice, and to post the "
                + "performance security where the Request for Quotation requires it. Failure to do so shall be sufficient ground for the cancellation of the award.", CHARS);
        row += 2;
        int sig = row;
        row = docs.sign(x, row, "D", "F", "Very truly yours,", docs.hope(), a.noaNumber() != null);
        docs.conforme(x, sig, "A", "C", a.supplier());
        x.page().printArea("A1:F" + (row + 1));
        return x.bytes();
    }

    public byte[] noticeToProceed(String source, Long id) {
        Award a = award(source, id);
        Xlsx x = letter("Notice to Proceed");
        int row = docs.letterhead(x, LAST);
        row = docs.title(x, row, LAST, null, "NOTICE TO PROCEED");
        row = addressee(x, row, a.ntpNumber(), a.ntpDate(), a.supplier(), a.address());
        row = docs.para(x, row, "A", LAST, "Dear Sir/Madam:", CHARS);
        row++;
        String days = a.leadDays() == null ? "" : " within " + a.leadDays() + " calendar days";
        row = docs.para(x, row, "A", LAST, "The attached " + (blank(a.contractNumber()) ? "Contract" : "Contract No. " + a.contractNumber())
                + " entered into by this Office and your firm for the " + a.title() + " (" + a.reference() + "), at the contract price equivalent to "
                + words(a.amount()).toUpperCase(Locale.ROOT) + " (" + peso(a.amount()) + "), is hereby transmitted.", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "Accordingly, you are hereby notified to commence the delivery of the goods and services under the Contract"
                + days + " from the date of your receipt of this Notice" + (blank(a.deliveryTerm()) ? "" : ", in accordance with the delivery term: "
                + a.deliveryTerm()) + ". The contract time shall start to run upon your receipt of this Notice.", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "Please acknowledge receipt and acceptance of this Notice by signing both copies in the space provided below. "
                + "Keep one copy and return the other to this Office.", CHARS);
        row += 2;
        int sig = row;
        row = docs.sign(x, row, "D", "F", "Very truly yours,", docs.hope(), a.ntpNumber() != null);
        docs.conforme(x, sig, "A", "C", a.supplier());
        x.page().printArea("A1:F" + (row + 1));
        return x.bytes();
    }

    public byte[] invitationToBid(Long id) {
        List<Map<String, Object>> found = jdbc.queryForList("select * from biddings where id = ? and deleted_at is null", id);
        if (found.isEmpty()) throw ApiException.notFound("Bidding");
        Map<String, Object> b = found.get(0);
        String funds = b.get("purchase_request_id") == null ? "" : String.join(", ", jdbc.queryForList(
                "select distinct fund_source from purchase_request_items where purchase_request_id = ? and fund_source is not null", String.class,
                b.get("purchase_request_id")));
        String agency = or(docs.setting("agency_name"), "the Procuring Entity");
        String method = str(b.get("evaluation_method"));
        String criterion = "mearb".equals(method)
                ? "the Most Economically Advantageous Responsive Bid (MEARB), rating the bids on the criteria and weights stated in the Bidding Documents, "
                + "the financial proposals of the bidders that pass the minimum requirements being opened on the scheduled date"
                : "marb".equals(method)
                ? "the Most Advantageous Responsive Bid (MARB), rating the bids on the criteria and weights stated in the Bidding Documents"
                : "a non-discretionary \"pass/fail\" criterion, the second envelope of the bidders that pass the eligibility and technical requirements being "
                + "opened the same day";

        Xlsx x = letter("Invitation to Bid");
        int row = docs.letterhead(x, LAST);
        row = docs.title(x, row, LAST, null, "INVITATION TO BID");
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, "for the " + str(b.get("title")));
        x.style("A" + row).bold().center().wrap();
        x.height(row, 30);
        row++;
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, str(b.get("bidding_number")) + (blank(str(b.get("itb_reference"))) ? "" : "  ·  " + b.get("itb_reference")));
        x.style("A" + row).size(9).center();
        row += 2;
        String[] paras = {
                "The " + agency + (blank(funds) ? "" : ", through the " + funds + ",") + " intends to apply the sum of "
                        + words(b.get("abc")).toUpperCase(Locale.ROOT) + " (" + peso(b.get("abc")) + "), being the Approved Budget for the Contract (ABC), "
                        + "to payments under the contract for the " + b.get("title") + ". Bids received in excess of the ABC shall be automatically rejected at bid opening.",
                "The " + agency + " now invites bids for the above Procurement Project. Delivery of the goods is required "
                        + (b.get("delivery_lead_days") != null ? "within " + b.get("delivery_lead_days") + " calendar days from receipt of the Notice to Proceed" : "as stated in the Bidding Documents")
                        + (blank(str(b.get("delivery_term"))) ? "" : " (" + b.get("delivery_term") + ")") + ".",
                "Bidding will be conducted through open competitive bidding procedures in accordance with Republic Act No. 12009 (the New Government "
                        + "Procurement Act) and its Implementing Rules and Regulations, using " + criterion + ".",
                "Interested bidders may obtain further information from the BAC Secretariat of the " + agency + " and inspect the Bidding Documents at the "
                        + "address below during office hours, and download them from the PhilGEPS website and the website of the Procuring Entity.",
                "Bids must be delivered to the address below on or before " + when(b.get("submission_deadline")) + ". Late bids shall not be accepted. "
                        + "All bids must be accompanied by a bid security in any of the acceptable forms and in the amount stated in the Bidding Documents.",
                "Bid opening shall be on " + when(b.get("bid_opening_date")) + " at the address below. Bids will be opened in the presence of the bidders' "
                        + "authorized representatives who choose to attend.",
                "The " + agency + " reserves the right to reject any and all bids, declare a failure of bidding, or not award the contract at any time prior "
                        + "to contract award, in accordance with the IRR of Republic Act No. 12009, without thereby incurring any liability to the affected bidder or bidders.",
                "For further information, please refer to: BAC Secretariat, " + agency + (blank(docs.setting("agency_address")) ? "" : ", " + docs.setting("agency_address")) + ".",
        };
        for (int i = 0; i < paras.length; i++) {
            x.set("A" + row, (i + 1) + ".");
            x.style("A" + row).right().valign(Xlsx.V.TOP);
            row = docs.para(x, row, "B", LAST, paras[i], 96);
            row++;
        }
        x.merge("A" + row + ":C" + row);
        x.set("A" + row, b.get("itb_posted_at") == null ? "Date of posting: ____________________" : "Posted: " + OutgoingDocs.date(b.get("itb_posted_at"))
                + (blank(str(b.get("itb_publication"))) ? "" : " · " + b.get("itb_publication")));
        x.style("A" + row).size(9);
        row = docs.sign(x, row, "D", "F", null, docs.bacChair(), b.get("itb_posted_at") != null);
        x.page().printArea("A1:F" + row);
        return x.bytes();
    }

    public byte[] liquidatedDamages(Long contractId) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select c.*, s.name supplier, s.address from contracts c left join suppliers s on s.id = c.supplier_id where c.id = ? and c.deleted_at is null""", contractId);
        if (found.isEmpty()) throw ApiException.notFound("Contract");
        Map<String, Object> c = found.get(0);
        if (num(c.get("ld_amount")).signum() <= 0) {
            throw ApiException.of(org.springframework.http.HttpStatus.CONFLICT, "No liquidated damages have been assessed on this contract.");
        }
        BigDecimal amount = num(c.get("amount"));
        BigDecimal ld = num(c.get("ld_amount"));
        BigDecimal rate = num(c.get("ld_rate")).movePointRight(2).stripTrailingZeros();
        boolean overCap = amount.signum() > 0 && ld.compareTo(amount.multiply(new BigDecimal("0.10"))) >= 0;

        Xlsx x = letter("Liquidated Damages");
        int row = docs.letterhead(x, LAST);
        row = docs.title(x, row, LAST, null, "NOTICE OF LIQUIDATED DAMAGES");
        row = addressee(x, row, str(c.get("contract_number")), c.get("ld_as_of_date"), str(c.get("supplier")), str(c.get("address")));
        row = docs.para(x, row, "A", LAST, "Dear Sir/Madam:", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "This is to inform you that the delivery under Contract No. " + c.get("contract_number") + " for the " + c.get("title")
                + ", which was due on " + OutgoingDocs.date(c.get("end_date")) + ", is delayed. Under the contract and the IRR of Republic Act No. 12009, "
                + "liquidated damages are imposed at the rate of " + rate.toPlainString() + "% of the cost of the delayed goods for every day of delay.", CHARS);
        row++;
        String[][] lines = {
                {"Cost of the delayed goods", peso(c.get("ld_basis_amount"))},
                {"Days of delay as of " + OutgoingDocs.date(c.get("ld_as_of_date")), str(c.get("ld_days_delayed"))},
                {"Rate per day", rate.toPlainString() + "%"},
                {"Liquidated damages", peso(ld)},
                {"Contract price", peso(amount)},
                {"Liquidated damages as a share of the contract price", amount.signum() > 0 ? ld.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP) + "%" : "—"},
        };
        for (String[] l : lines) {
            x.merge("B" + row + ":D" + row);
            x.set("B" + row, l[0]);
            x.merge("E" + row + ":F" + row);
            x.set("E" + row, l[1]);
            x.style("E" + row).bold().right();
            x.style("B" + row + ":F" + row).border(Xlsx.Edge.BOTTOM, Xlsx.Line.THIN);
            row++;
        }
        row++;
        row = docs.para(x, row, "A", LAST, "The amount shall be deducted from any money due or which may become due to you under the contract, or collected from "
                + "any securities or warranties posted by you, whichever is convenient to this Office."
                + (overCap ? " The liquidated damages have reached ten percent (10%) of the contract price, which is a ground for the termination of the contract."
                : " Once the liquidated damages reach ten percent (10%) of the contract price, this Office may terminate the contract.")
                + (blank(str(c.get("ld_remarks"))) ? "" : "\n\nRemarks: " + c.get("ld_remarks")), CHARS);
        row += 2;
        int sig = row;
        row = docs.sign(x, row, "D", "F", "Very truly yours,", docs.hope(), c.get("ld_as_of_date") != null);
        docs.supplier(x, sig, "A", "C", "Received by:", str(c.get("supplier")));
        x.page().printArea("A1:F" + (row + 1));
        return x.bytes();
    }

    private static String when(Object v) {
        return v instanceof Timestamp t ? t.toLocalDateTime().format(WHEN) : OutgoingDocs.date(v);
    }
}

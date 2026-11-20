package com.avtech.pams.report.outgoing;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.avtech.pams.report.outgoing.OutgoingDocs.*;

/**
 * The Request for Quotation sent to suppliers (GPPB form for Small Value Procurement and the other alternative modes),
 * signed by the BAC Chairperson once issued, and the BAC's Abstract of Quotations, signed by the BAC members once the
 * BAC has recommended the award.
 */
@Component
public class RfqForms {

    private final JdbcTemplate jdbc;
    private final OutgoingDocs docs;

    public RfqForms(JdbcTemplate jdbc, OutgoingDocs docs) {
        this.jdbc = jdbc;
        this.docs = docs;
    }

    private Map<String, Object> rfq(Long id) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select r.*, pr.pr_number from rfqs r left join purchase_requests pr on pr.id = r.purchase_request_id
                where r.id = ? and r.deleted_at is null""", id);
        if (found.isEmpty()) throw ApiException.notFound("RFQ");
        return found.get(0);
    }

    public byte[] request(Long id) {
        Map<String, Object> r = rfq(id);
        List<Map<String, Object>> items = jdbc.queryForList(
                "select general_description, specifications, unit, quantity, abc_unit_cost, delivery_lead_time from rfq_items where rfq_id = ? order by id", id);

        Xlsx x = docs.sheet("RFQ", 7, 38, 8, 8, 13, 16, 13, 14);
        int row = docs.letterhead(x, "H");
        row = docs.title(x, row, "H", null, "REQUEST FOR QUOTATION");
        int top = row;
        docs.field(x, "A" + row, "Company Name:", "C" + row + ":E" + row, "");
        docs.field(x, "F" + row, "RFQ No.:", "G" + row + ":H" + row, str(r.get("rfq_number")));
        row++;
        docs.field(x, "A" + row, "Address:", "C" + row + ":E" + row, "");
        docs.field(x, "F" + row, "Date:", "G" + row + ":H" + row, date(r.get("created_at")));
        row++;
        docs.field(x, "A" + row, "TIN / PhilGEPS No.:", "C" + row + ":E" + row, "");
        docs.field(x, "F" + row, "PR No.:", "G" + row + ":H" + row, str(r.get("pr_number")));
        row++;
        docs.field(x, "A" + row, "Project:", "C" + row + ":E" + row, str(r.get("title")));
        docs.field(x, "F" + row, "Mode:", "G" + row + ":H" + row, str(r.get("procurement_method")));
        x.style("A" + top + ":H" + row).border(Edge.OUTLINE, Line.THIN);
        row += 2;
        row = docs.para(x, row, "A", "H", "Sir/Madam:\nPlease quote your lowest price on the item/s listed below, subject to the General Conditions stated herein, "
                + "stating the shortest time of delivery, and submit your quotation duly signed by you or your duly authorized representative not later than "
                + deadline(r.get("deadline")) + ".", 105);
        row++;

        // The RFQ is the BAC's: the Chairperson signs it once it is issued.
        row = docs.sign(x, row, "F", "H", null, docs.bacChair(), !"draft".equals(r.get("status")));
        row++;

        x.merge("A" + row + ":H" + row);
        x.set("A" + row, "GENERAL CONDITIONS");
        x.style("A" + row).bold().size(9);
        row++;
        String lead = r.get("delivery_lead_days") == null ? "____" : str(r.get("delivery_lead_days"));
        String[] notes = {
                "All entries must be typewritten or legibly written.",
                "Delivery period within " + lead + " calendar days from receipt of the Purchase Order" + (blank(str(r.get("delivery_term"))) ? "." : " (" + r.get("delivery_term") + ")."),
                "Warranty shall be for a period of six (6) months for supplies and materials and one (1) year for equipment, from the date of acceptance by the Procuring Entity.",
                "Price validity shall be for a period of thirty (30) calendar days from the deadline of submission.",
                "A PhilGEPS Certificate of Registration and the Mayor's/Business Permit shall be attached upon submission of the quotation.",
                "Quotations exceeding the Approved Budget for the Contract (ABC) of " + peso(r.get("abc")) + " shall be rejected.",
        };
        for (int i = 0; i < notes.length; i++) row = docs.para(x, row, "A", "H", (i + 1) + ".  " + notes[i], 110);
        x.style("A" + (row - notes.length) + ":H" + (row - 1)).size(9);
        row++;

        String[] head = {"Item No.", "Item & Description", "Qty", "Unit", "ABC Unit Cost", "Brand / Model Offered", "Unit Price", "Total Price"};
        for (int i = 0; i < head.length; i++) x.set(Xlsx.col(i + 1) + row, head[i]);
        x.style("A" + row + ":H" + row).bold().center().wrap().border(Edge.ALL, Line.THIN);
        x.height(row, 30);
        int first = ++row;
        int n = 1;
        for (Map<String, Object> l : items) {
            String desc = str(l.get("general_description")) + (blank(str(l.get("specifications"))) ? "" : "\n" + l.get("specifications"));
            x.set("A" + row, n++);
            x.set("B" + row, desc);
            x.style("B" + row).wrap();
            x.height(row, Math.max(18, (desc.length() / 36 + desc.split("\n").length) * 13.0 + 3));
            x.set("C" + row, num(l.get("quantity")).doubleValue());
            x.set("D" + row, str(l.get("unit")));
            x.set("E" + row, num(l.get("abc_unit_cost")).doubleValue());
            row++;
        }
        x.style("A" + first + ":A" + (row - 1)).center();
        x.style("C" + first + ":D" + (row - 1)).center();
        x.style("E" + first + ":E" + (row - 1)).numberFormat("#,##0.00");
        x.style("A" + first + ":H" + (row - 1)).border(Edge.ALL, Line.THIN).valign(Xlsx.V.TOP);
        x.merge("A" + row + ":G" + row);
        x.set("A" + row, "TOTAL");
        x.style("A" + row).bold().right();
        x.style("A" + row + ":H" + row).border(Edge.ALL, Line.THIN);
        row += 2;
        row = docs.para(x, row, "A", "H", "After having carefully read and accepted your General Conditions, I/We quote you on the item/s at the prices noted above.", 110);
        row++;
        int sig = row;
        row = docs.supplier(x, row, "E", "H", "Submitted by:", null);
        // The quoting supplier's details, beside the signature.
        x.set("A" + (sig + 2), "Telephone / Mobile No.: ____________________");
        x.set("A" + (sig + 3), "Email Address: ____________________");
        x.style("A" + (sig + 2) + ":A" + (sig + 3)).size(9);
        x.page().printArea("A1:H" + row);
        return x.bytes();
    }

    public byte[] abstractOfQuotations(Long id) {
        Map<String, Object> r = rfq(id);
        List<Map<String, Object>> items = jdbc.queryForList(
                "select id, general_description, unit, quantity, abc_unit_cost from rfq_items where rfq_id = ? order by id", id);
        List<Map<String, Object>> quotes = jdbc.queryForList("""
                select q.id, q.total_amount, q.is_winner, q.is_responsive, s.name from rfq_quotations q left join suppliers s on s.id = q.supplier_id
                where q.rfq_id = ? order by q.total_amount, q.id""", id);
        Map<String, BigDecimal> price = new HashMap<>();
        jdbc.query("select qi.rfq_quotation_id, qi.rfq_item_id, qi.unit_price from rfq_quotation_items qi join rfq_quotations q on q.id = qi.rfq_quotation_id where q.rfq_id = ?",
                rs -> {
                    price.put(rs.getLong(1) + ":" + rs.getLong(2), rs.getBigDecimal(3));
                }, id);

        int cols = 5 + quotes.size() * 2;
        String last = Xlsx.col(Math.max(cols, 8));
        double[] widths = new double[Math.max(cols, 8)];
        double[] base = {6, 34, 7, 7, 13};
        for (int i = 0; i < widths.length; i++) widths[i] = i < base.length ? base[i] : 13;
        Xlsx x = docs.sheet("Abstract of Quotations", widths);
        x.page().landscape();
        int row = docs.letterhead(x, last);
        row = docs.title(x, row, last, null, "ABSTRACT OF QUOTATIONS");
        docs.field(x, "A" + row, "Project:", "B" + row + ":E" + row, str(r.get("title")));
        docs.field(x, "F" + row, "RFQ No.:", "G" + row + ":" + last + row, str(r.get("rfq_number")));
        row++;
        docs.field(x, "A" + row, "ABC:", "B" + row + ":E" + row, peso(r.get("abc")));
        docs.field(x, "F" + row, "Mode:", "G" + row + ":" + last + row, str(r.get("procurement_method")));
        row += 2;

        String[] head = {"Item", "Description", "Qty", "Unit", "ABC Unit Cost"};
        for (int i = 0; i < head.length; i++) {
            x.merge(Xlsx.col(i + 1) + row + ":" + Xlsx.col(i + 1) + (row + 1));
            x.set(Xlsx.col(i + 1) + row, head[i]);
        }
        for (int q = 0; q < quotes.size(); q++) {
            String a = Xlsx.col(6 + q * 2), b = Xlsx.col(7 + q * 2);
            x.merge(a + row + ":" + b + row);
            x.set(a + row, str(quotes.get(q).get("name")));
            x.set(a + (row + 1), "Unit Price");
            x.set(b + (row + 1), "Total");
        }
        String lastData = Xlsx.col(cols);
        x.style("A" + row + ":" + lastData + (row + 1)).bold().center().wrap().vcenter().border(Edge.ALL, Line.THIN);
        x.height(row, 30);
        row += 2;
        int first = row;
        int n = 1;
        for (Map<String, Object> it : items) {
            x.set("A" + row, n++);
            x.set("B" + row, str(it.get("general_description")));
            x.style("B" + row).wrap();
            x.set("C" + row, num(it.get("quantity")).doubleValue());
            x.set("D" + row, str(it.get("unit")));
            x.set("E" + row, num(it.get("abc_unit_cost")).doubleValue());
            for (int q = 0; q < quotes.size(); q++) {
                BigDecimal p = price.get(quotes.get(q).get("id") + ":" + it.get("id"));
                if (p == null) continue;
                x.set(Xlsx.col(6 + q * 2) + row, p.doubleValue());
                x.set(Xlsx.col(7 + q * 2) + row, p.multiply(num(it.get("quantity"))).doubleValue());
            }
            row++;
        }
        x.merge("A" + row + ":E" + row);
        x.set("A" + row, "TOTAL QUOTED");
        x.style("A" + row).bold().right();
        for (int q = 0; q < quotes.size(); q++) {
            x.merge(Xlsx.col(6 + q * 2) + row + ":" + Xlsx.col(7 + q * 2) + row);
            x.set(Xlsx.col(6 + q * 2) + row, num(quotes.get(q).get("total_amount")).doubleValue());
        }
        x.style("A" + row + ":" + lastData + row).bold();
        row++;
        x.merge("A" + row + ":E" + row);
        x.set("A" + row, "REMARKS");
        x.style("A" + row).bold().right();
        for (int q = 0; q < quotes.size(); q++) {
            Map<String, Object> qt = quotes.get(q);
            x.merge(Xlsx.col(6 + q * 2) + row + ":" + Xlsx.col(7 + q * 2) + row);
            x.set(Xlsx.col(6 + q * 2) + row, Boolean.TRUE.equals(qt.get("is_winner")) ? "Lowest Calculated and Responsive"
                    : Boolean.FALSE.equals(qt.get("is_responsive")) ? "Not responsive" : "Responsive");
        }
        x.style("A" + row + ":" + lastData + row).center().wrap();
        x.height(row, 26);
        x.style("C" + first + ":D" + (row - 2)).center();
        x.style("E" + first + ":" + lastData + (row - 1)).numberFormat("#,##0.00");
        x.style("A" + first + ":" + lastData + row).border(Edge.ALL, Line.THIN);
        row += 2;

        Map<String, Object> winner = quotes.stream().filter(q -> Boolean.TRUE.equals(q.get("is_winner"))).findFirst().orElse(null);
        row = docs.para(x, row, "A", last, winner == null
                ? "No quotation has been recommended for award yet."
                : "Based on the above abstract, the BAC recommends the award of the contract to " + winner.get("name") + ", whose quotation of "
                + peso(winner.get("total_amount")) + " is the Lowest Calculated and Responsive Quotation, within the ABC of " + peso(r.get("abc")) + ".", 140);
        row++;
        row = bacSignatures(x, row, last, r.get("awarded_at") != null);
        x.page().printArea("A1:" + last + row);
        return x.bytes();
    }

    /** The BAC members' signatures, three to a row, across A..{@code last}. */
    int bacSignatures(Xlsx x, int row, String last, boolean signed) {
        return bacSignatures(docs, x, row, last, signed);
    }

    public static int bacSignatures(OutgoingDocs docs, Xlsx x, int row, String last, boolean signed) {
        List<Signer> members = docs.bac();
        if (members.isEmpty()) members = List.of(docs.bacChair());
        int width = Xlsx.colIndex(last);
        int per = 3;
        int span = Math.max(1, width / per);
        for (int i = 0; i < members.size(); i += per) {
            int next = row;
            for (int j = 0; j < per && i + j < members.size(); j++) {
                String from = Xlsx.col(1 + j * span), to = Xlsx.col(Math.min(width, (j + 1) * span));
                next = Math.max(next, docs.sign(x, row, from, to, null, members.get(i + j), signed));
            }
            row = next + 1;
        }
        return row;
    }

    private static String deadline(Object v) {
        if (v instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", java.util.Locale.ENGLISH));
        }
        return date(v);
    }
}

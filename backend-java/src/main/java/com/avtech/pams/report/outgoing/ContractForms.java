package com.avtech.pams.report.outgoing;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.avtech.pams.report.outgoing.OutgoingDocs.*;

/**
 * The Contract Agreement (Philippine Bidding Documents, Form of Contract Agreement), a Contract Amendment, and the approval
 * of a delivery extension. The Head of the Procuring Entity signs for the agency once the contract is signed or the
 * change approved; the supplier and the witnesses sign by hand, and the acknowledgment is left for the notary.
 */
@Component
public class ContractForms {

    private static final String LAST = "F";
    private static final int CHARS = 112;

    private final JdbcTemplate jdbc;
    private final OutgoingDocs docs;

    public ContractForms(JdbcTemplate jdbc, OutgoingDocs docs) {
        this.jdbc = jdbc;
        this.docs = docs;
    }

    private Map<String, Object> contract(Long id) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select c.*, s.name supplier, s.address supplier_address, s.tin supplier_tin,
                       coalesce(b.bidding_number, r.rfq_number) source_number, coalesce(b.noa_number, r.noa_number) noa_number,
                       coalesce(b.noa_date, r.noa_date) noa_date, case when c.bidding_id is not null then 'Competitive Bidding' else r.procurement_method end mode,
                       po.po_number
                from contracts c left join suppliers s on s.id = c.supplier_id left join biddings b on b.id = c.bidding_id
                left join rfqs r on r.id = c.rfq_id left join purchase_orders po on po.id = c.purchase_order_id
                where c.id = ? and c.deleted_at is null""", id);
        if (found.isEmpty()) throw ApiException.notFound("Contract");
        return found.get(0);
    }

    public byte[] agreement(Long id) {
        Map<String, Object> c = contract(id);
        String agency = or(docs.setting("agency_name"), "[Name of the Procuring Entity]");
        String address = or(docs.setting("agency_address"), "[address]");
        String supplier = str(c.get("supplier"));
        BigDecimal price = num(c.get("amount"));
        String priceText = words(price).toUpperCase(Locale.ROOT) + " (" + peso(price) + ")";

        Xlsx x = docs.sheet("Contract Agreement", 16, 16, 16, 16, 16, 16);
        int row = docs.letterhead(x, LAST);
        row = docs.title(x, row, LAST, null, "CONTRACT AGREEMENT");
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, "Contract No. " + str(c.get("contract_number")) + "  ·  " + str(c.get("title")));
        x.style("A" + row).bold().center().wrap();
        x.height(row, 28);
        row += 2;
        row = docs.para(x, row, "A", LAST, "THIS AGREEMENT made on " + (c.get("signed_date") == null ? "____________________" : date(c.get("signed_date")))
                + " between the " + agency + ", with office address at " + address + " (hereinafter called \"the Entity\"), of the one part, and " + supplier
                + (blank(str(c.get("supplier_address"))) ? "" : ", with business address at " + c.get("supplier_address"))
                + " (hereinafter called \"the Supplier\"), of the other part.", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "WHEREAS, the Entity invited " + ("Competitive Bidding".equals(c.get("mode")) ? "bids" : "quotations")
                + " for certain goods and ancillary services, viz., " + c.get("title") + (blank(str(c.get("source_number"))) ? "" : " (" + c.get("source_number") + ")")
                + ", through " + or(str(c.get("mode")), "the applicable mode of procurement") + ", and has accepted the offer of the Supplier for the supply of those goods "
                + "and services in the sum of " + priceText + " (hereinafter called \"the Contract Price\").", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "NOW THIS AGREEMENT WITNESSETH AS FOLLOWS:", CHARS);
        x.style("A" + (row - 1)).bold();
        String[] clauses = {
                "In this Agreement, words and expressions shall have the same meanings as are respectively assigned to them in the Conditions of Contract referred to.",
                "The following documents, as required by Republic Act No. 12009 and its Implementing Rules and Regulations, shall be deemed to form and be read and "
                        + "construed as part of this Agreement: (i) the Philippine Bidding Documents or the Request for Quotation, including the General and Special "
                        + "Conditions of Contract; (ii) the Supplier's bid or quotation, including the eligibility requirements, technical and financial proposals, "
                        + "and all other documents submitted; (iii) the performance security; (iv) the Notice of Award"
                        + (blank(str(c.get("noa_number"))) ? "" : " (" + c.get("noa_number") + ", " + date(c.get("noa_date")) + ")")
                        + " and the Supplier's conforme thereto; (v) the Purchase Order" + (blank(str(c.get("po_number"))) ? "" : " (" + c.get("po_number") + ")")
                        + "; and (vi) other contract documents that may be required by existing laws or by the Entity.",
                "In consideration of the payments to be made by the Entity to the Supplier as hereinafter mentioned, the Supplier hereby covenants with the Entity "
                        + "to provide the goods and services and to remedy defects therein in conformity in all respects with the provisions of the Contract"
                        + (c.get("start_date") == null ? "" : ", from " + date(c.get("start_date")) + (c.get("end_date") == null ? "" : " to " + date(c.get("end_date"))))
                        + ".",
                "The Entity hereby covenants to pay the Supplier, in consideration of the provision of the goods and services and the remedying of defects therein, "
                        + "the Contract Price of " + priceText + ", or such other sum as may become payable under the provisions of the Contract, at the times and in "
                        + "the manner prescribed by the Contract.",
        };
        for (int i = 0; i < clauses.length; i++) {
            x.set("A" + row, (i + 1) + ".");
            x.style("A" + row).right().valign(Xlsx.V.TOP);
            row = docs.para(x, row, "B", LAST, clauses[i], 96);
            row++;
        }
        if (!blank(str(c.get("terms")))) {
            row = docs.para(x, row, "A", LAST, "Special terms: " + c.get("terms"), CHARS);
            row++;
        }
        row = docs.para(x, row, "A", LAST, "IN WITNESS WHEREOF, the parties hereto have caused this Agreement to be executed in accordance with the laws of the "
                + "Republic of the Philippines on the day and year first above written.", CHARS);
        row += 2;
        int sig = row;
        int left = docs.sign(x, row, "A", "C", "For the " + agency + ":", docs.hope(), c.get("signed_date") != null);
        int right = docs.supplier(x, sig, "D", "F", "For " + supplier + ":", null);
        row = Math.max(left, right) + 1;
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, "SIGNED IN THE PRESENCE OF:");
        x.style("A" + row).bold().center();
        row += 2;
        for (String col : new String[]{"A", "D"}) {
            String end = "A".equals(col) ? "C" : "F";
            x.merge(col + row + ":" + end + row);
            x.style(col + row + ":" + end + row).border(Edge.BOTTOM, Line.THIN);
            x.merge(col + (row + 1) + ":" + end + (row + 1));
            x.set(col + (row + 1), "Witness");
            x.style(col + (row + 1)).size(9).center();
        }
        x.height(row, 30);
        row += 3;

        // Acknowledgment, completed by the notary public.
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, "ACKNOWLEDGMENT");
        x.style("A" + row).bold().center();
        row++;
        row = docs.para(x, row, "A", LAST, "REPUBLIC OF THE PHILIPPINES )\n____________________________ ) S.S.", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "BEFORE ME, a Notary Public for and in ____________________, this ____ day of ____________, personally appeared the "
                + "persons named above, with their competent evidence of identity, known to me to be the same persons who executed the foregoing Contract "
                + "Agreement, consisting of ____ pages including this page, and they acknowledged to me that the same is their free and voluntary act and deed "
                + "and that of the entities they represent.\n\nWITNESS MY HAND AND SEAL on the date and place first above written.", CHARS);
        row += 2;
        x.merge("D" + row + ":F" + row);
        x.style("D" + row + ":F" + row).border(Edge.BOTTOM, Line.THIN);
        x.merge("D" + (row + 1) + ":F" + (row + 1));
        x.set("D" + (row + 1), "Notary Public");
        x.style("D" + (row + 1)).size(9).center();
        row += 2;
        x.set("A" + row, "Doc. No. _____;  Page No. _____;  Book No. _____;  Series of " + java.time.LocalDate.now().getYear() + ".");
        x.style("A" + row).size(9);
        x.page().printArea("A1:F" + row);
        return x.bytes();
    }

    public byte[] amendment(Long amendmentId) {
        List<Map<String, Object>> found = jdbc.queryForList("select * from contract_amendments where id = ?", amendmentId);
        if (found.isEmpty()) throw ApiException.notFound("Contract amendment");
        Map<String, Object> a = found.get(0);
        Map<String, Object> c = contract(((Number) a.get("contract_id")).longValue());
        boolean approved = "approved".equals(a.get("status"));
        BigDecimal change = num(a.get("amount_change"));
        BigDecimal before = approved ? num(c.get("amount")).subtract(change) : num(c.get("amount"));
        BigDecimal after = before.add(change);

        Xlsx x = docs.sheet("Contract Amendment", 16, 16, 16, 16, 16, 16);
        int row = docs.letterhead(x, LAST);
        row = docs.title(x, row, LAST, null, "CONTRACT AMENDMENT NO. " + a.get("amendment_number"));
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, "to Contract No. " + c.get("contract_number") + "  ·  " + c.get("title"));
        x.style("A" + row).bold().center().wrap();
        x.height(row, 28);
        row += 2;
        row = docs.para(x, row, "A", LAST, "The " + or(docs.setting("agency_name"), "Procuring Entity") + " (the Entity) and " + c.get("supplier")
                + " (the Supplier) agree to amend the above contract as follows, in accordance with the IRR of Republic Act No. 12009 on contract variations, "
                + "which cumulatively may not exceed ten percent (10%) of the original contract price:", CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, str(a.get("title")) + (blank(str(a.get("description"))) ? "" : "\n" + a.get("description")), CHARS);
        x.style("A" + (row - 1)).bold();
        row++;
        String[][] lines = {
                {"Contract price before this amendment", peso(before)},
                {"Change in the contract price", (change.signum() > 0 ? "+" : "") + peso(change)},
                {"Revised contract price", peso(after)},
                {"Revised delivery / completion date", a.get("new_end_date") == null ? "No change" : date(a.get("new_end_date"))},
        };
        for (String[] l : lines) {
            x.merge("B" + row + ":D" + row);
            x.set("B" + row, l[0]);
            x.merge("E" + row + ":F" + row);
            x.set("E" + row, l[1]);
            x.style("E" + row).bold().right();
            x.style("B" + row + ":F" + row).border(Edge.BOTTOM, Line.THIN);
            row++;
        }
        row++;
        row = docs.para(x, row, "A", LAST, "All other terms and conditions of the contract remain in full force and effect.", CHARS);
        row += 2;
        int sig = row;
        int left = docs.sign(x, row, "A", "C", "For the Entity:", docs.hope(), approved);
        int right = docs.conforme(x, sig, "D", "F", str(c.get("supplier")));
        row = Math.max(left, right);
        x.page().printArea("A1:F" + row);
        return x.bytes();
    }

    public byte[] extension(Long extensionId) {
        List<Map<String, Object>> found = jdbc.queryForList("select * from delivery_extensions where id = ?", extensionId);
        if (found.isEmpty()) throw ApiException.notFound("Delivery extension");
        Map<String, Object> e = found.get(0);
        Map<String, Object> c = contract(((Number) e.get("contract_id")).longValue());
        String status = str(e.get("status"));
        boolean decided = "approved".equals(status) || "denied".equals(status);

        Xlsx x = docs.sheet("Contract Time Extension", 16, 16, 16, 16, 16, 16);
        int row = docs.letterhead(x, LAST);
        row = docs.title(x, row, LAST, null, "approved".equals(status) ? "APPROVAL OF CONTRACT TIME EXTENSION"
                : "denied".equals(status) ? "DENIAL OF CONTRACT TIME EXTENSION" : "REQUEST FOR CONTRACT TIME EXTENSION");
        x.merge("D" + row + ":F" + row);
        x.set("D" + row, decided ? date(e.get("approved_at")) : date(e.get("requested_date")));
        x.style("D" + row).right();
        row += 2;
        x.merge("A" + row + ":D" + row);
        x.set("A" + row, str(c.get("supplier")).toUpperCase(Locale.ROOT));
        x.style("A" + row).bold();
        row++;
        x.merge("A" + row + ":D" + row);
        x.set("A" + row, str(c.get("supplier_address")));
        row += 2;
        row = docs.para(x, row, "A", LAST, "Dear Sir/Madam:", CHARS);
        row++;
        Object approvedDays = e.get("approved_days");
        String body = "This refers to your request dated " + date(e.get("requested_date")) + " for an extension of " + e.get("requested_days")
                + " calendar day(s) of the contract time of Contract No. " + c.get("contract_number") + " for the " + c.get("title")
                + (blank(str(e.get("reason"))) ? "" : ", on the ground that " + e.get("reason")) + ".";
        row = docs.para(x, row, "A", LAST, body, CHARS);
        row++;
        row = docs.para(x, row, "A", LAST, "approved".equals(status)
                ? "Please be informed that the request is APPROVED for " + approvedDays + " calendar day(s). The revised delivery date is "
                + date(c.get("end_date")) + ". No liquidated damages shall be imposed for the extended period. All other terms of the contract remain unchanged."
                : "denied".equals(status)
                ? "Please be informed that the request is DENIED. Deliveries made after the contract time shall be subject to liquidated damages."
                : "The request is under evaluation. You will be informed of the decision in writing.", CHARS);
        if (!blank(str(e.get("remarks")))) {
            row++;
            row = docs.para(x, row, "A", LAST, "Remarks: " + e.get("remarks"), CHARS);
        }
        row += 2;
        row = docs.sign(x, row, "D", "F", "Very truly yours,", docs.hope(), decided);
        x.page().printArea("A1:F" + row);
        return x.bytes();
    }
}

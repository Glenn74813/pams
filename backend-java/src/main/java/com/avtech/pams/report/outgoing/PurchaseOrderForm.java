package com.avtech.pams.report.outgoing;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.procurement.ProcurementRules;
import com.avtech.pams.report.coa.Xlsx;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.avtech.pams.report.outgoing.OutgoingDocs.*;

/**
 * Purchase Order — COA Appendix 61. Signed by the authorized official who approved it ("Very truly yours") and the
 * Chief Accountant on the availability of funds; the supplier signs the conforme on receipt.
 */
@Component
public class PurchaseOrderForm {

    private final JdbcTemplate jdbc;
    private final OutgoingDocs docs;

    public PurchaseOrderForm(JdbcTemplate jdbc, OutgoingDocs docs) {
        this.jdbc = jdbc;
        this.docs = docs;
    }

    public byte[] generate(Long id) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select po.*, s.name supplier_name, s.address supplier_address, s.tin supplier_tin, s.credit_terms,
                       pr.pr_number, pr.caf_number, pr.caf_certified_at, pr.caf_amount,
                       r.procurement_method rfq_mode, r.delivery_term rfq_term, b.delivery_term bid_term
                from purchase_orders po
                left join suppliers s on s.id = po.supplier_id
                left join purchase_requests pr on pr.id = po.purchase_request_id
                left join rfqs r on r.id = po.rfq_id
                left join biddings b on b.id = po.bidding_id
                where po.id = ? and po.deleted_at is null""", id);
        if (found.isEmpty()) throw ApiException.notFound("Purchase order");
        Map<String, Object> po = found.get(0);
        List<Map<String, Object>> items = jdbc.queryForList(
                "select description, specifications, unit, quantity, unit_cost, total_cost from purchase_order_items where purchase_order_id = ? order by id", id);
        String mode = po.get("rfq_mode") != null ? str(po.get("rfq_mode")) : po.get("bidding_id") != null ? "Competitive Bidding"
                : po.get("ps_order_id") != null ? ProcurementRules.PS_DBM : "";
        String funds = po.get("purchase_request_id") == null ? "" : String.join(", ", jdbc.queryForList(
                "select distinct fund_source from purchase_request_items where purchase_request_id = ? and fund_source is not null", String.class,
                po.get("purchase_request_id")));

        Xlsx x = docs.sheet("Purchase Order", 16, 8, 34, 18, 12, 15);
        int r = docs.letterhead(x, "F");
        r = docs.title(x, r, "F", "Appendix 61", "PURCHASE ORDER");

        docs.field(x, "A" + r, "Entity Name:", "B" + r + ":C" + r, docs.setting("agency_name"));
        docs.field(x, "D" + r, "Fund Cluster:", "E" + r + ":F" + r, docs.setting("fund_cluster"));
        r += 2;
        int top = r;
        docs.field(x, "A" + r, "Supplier:", "B" + r + ":C" + r, str(po.get("supplier_name")));
        docs.field(x, "D" + r, "P.O. No.:", "E" + r + ":F" + r, str(po.get("po_number")));
        r++;
        docs.field(x, "A" + r, "Address:", "B" + r + ":C" + r, str(po.get("supplier_address")));
        docs.field(x, "D" + r, "Date:", "E" + r + ":F" + r, date(po.get("order_date")));
        r++;
        docs.field(x, "A" + r, "TIN:", "B" + r + ":C" + r, str(po.get("supplier_tin")));
        docs.field(x, "D" + r, "Mode:", "E" + r + ":F" + r, mode);
        x.style("A" + top + ":F" + r).border(Edge.OUTLINE, Line.THIN);
        r += 2;
        r = docs.para(x, r, "A", "F", "Gentlemen:\nPlease furnish this Office the following articles subject to the terms and conditions contained herein:", 95);
        r++;
        top = r;
        docs.field(x, "A" + r, "Place of Delivery:", "B" + r + ":C" + r, str(po.get("delivery_address")));
        docs.field(x, "D" + r, "Delivery Term:", "E" + r + ":F" + r, or(str(po.get("rfq_term")), str(po.get("bid_term"))));
        r++;
        docs.field(x, "A" + r, "Date of Delivery:", "B" + r + ":C" + r, date(po.get("delivery_date")));
        docs.field(x, "D" + r, "Payment Term:", "E" + r + ":F" + r, str(po.get("credit_terms")));
        x.style("A" + top + ":F" + r).border(Edge.OUTLINE, Line.THIN);
        r += 2;

        String[] head = {"Stock/ Property No.", "Unit", "Description", "Quantity", "Unit Cost", "Amount"};
        for (int i = 0; i < head.length; i++) x.set(Xlsx.col(i + 1) + r, head[i]);
        x.style("A" + r + ":F" + r).bold().center().wrap().border(Edge.ALL, Line.THIN);
        x.height(r, 30);
        int first = ++r;
        int n = 1;
        for (Map<String, Object> l : items) {
            x.set("A" + r, n++);
            x.set("B" + r, str(l.get("unit")));
            String desc = str(l.get("description")) + (blank(str(l.get("specifications"))) ? "" : "\n" + l.get("specifications"));
            x.set("C" + r, desc);
            x.style("C" + r).wrap();
            x.height(r, Math.max(16, (desc.length() / 36 + 1 + desc.split("\n").length - 1) * 13.0 + 3));
            x.set("D" + r, num(l.get("quantity")).doubleValue());
            x.set("E" + r, num(l.get("unit_cost")).doubleValue());
            x.set("F" + r, num(l.get("total_cost")).doubleValue());
            r++;
        }
        x.style("A" + first + ":B" + (r - 1)).center();
        x.style("D" + first + ":D" + (r - 1)).center();
        x.style("E" + first + ":F" + (r - 1)).numberFormat("#,##0.00");
        x.style("A" + first + ":F" + (r - 1)).border(Edge.ALL, Line.THIN).valign(Xlsx.V.TOP);
        x.merge("A" + r + ":B" + r);
        x.set("A" + r, "(Total Amount in Words)");
        x.style("A" + r).italic().size(8);
        x.merge("C" + r + ":E" + r);
        x.set("C" + r, words(po.get("total_amount")));
        x.style("C" + r).bold().wrap();
        x.set("F" + r, num(po.get("total_amount")).doubleValue());
        x.style("F" + r).bold().numberFormat("#,##0.00");
        x.style("A" + r + ":F" + r).border(Edge.ALL, Line.THIN);
        x.height(r, 28);
        r += 2;
        r = docs.para(x, r, "A", "F", "In case of failure to make the full delivery within the time specified above, a penalty of one-tenth (1/10) of one percent "
                + "for every day of delay shall be imposed on the undelivered item/s.", 95);
        r++;

        // Conforme (supplier) and the authorized official who approved the PO.
        int left = docs.conforme(x, r, "A", "C", str(po.get("supplier_name")));
        int right = docs.sign(x, r, "D", "F", "Very truly yours:", docs.user(id(po.get("approved_by")), "Authorized Official / Designation"), po.get("approved_by") != null);
        r = Math.max(left, right) + 1;

        // Availability of funds: the Chief Accountant, once the funds behind the request are certified.
        int top2 = r;
        docs.field(x, "A" + r, "Fund Cluster:", "B" + r + ":C" + r, docs.setting("fund_cluster"));
        docs.field(x, "D" + r, "ORS/BURS No.:", "E" + r + ":F" + r, "");
        r++;
        docs.field(x, "A" + r, "Funds Available:", "B" + r + ":C" + r, or(funds, ""));
        docs.field(x, "D" + r, "Date of the ORS/BURS:", "E" + r + ":F" + r, "");
        r++;
        docs.field(x, "A" + r, "CAF No.:", "B" + r + ":C" + r, str(po.get("caf_number")));
        docs.field(x, "D" + r, "Amount:", "E" + r + ":F" + r, po.get("caf_amount") == null ? "" : peso(po.get("caf_amount")));
        r += 1;
        r = docs.sign(x, r, "A", "C", null, docs.role("accountant", "Signature over Printed Name of Chief Accountant / Head of Accounting Division / Unit"),
                po.get("caf_certified_at") != null);
        x.style("A" + top2 + ":F" + (r - 1)).border(Edge.OUTLINE, Line.THIN);
        x.page().printArea("A1:F" + r);
        return x.bytes();
    }
}

package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transmittal of procurement documents to the COA Audit Team (COA Circular No. 2009-001: copies of
 * contracts and purchase orders within five working days of their execution): a covering letter and
 * the list of purchase orders (approved, by order date) and contracts (by signing date) issued in the
 * period, with a "Received by COA" block for the auditor's acknowledgment.
 */
@Component
public class CoaTransmittalForm {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    public static final List<String> TYPES = List.of("all", "purchase_order", "contract");

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public CoaTransmittalForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    public Map<String, Object> data(LocalDate from, LocalDate to, String type, String transmittalNumber, String auditor) {
        if (from == null) throw ApiException.validation("date_from", "Enter the start of the period.");
        if (to == null) throw ApiException.validation("date_to", "Enter the end of the period.");
        if (to.isBefore(from)) throw ApiException.validation("date_to", "The end of the period is before its start.");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw ApiException.validation("date_to", "Choose a period of at most one year.");
        String t = type == null || type.isBlank() ? "all" : type.trim();
        if (!TYPES.contains(t)) throw ApiException.validation("document_type", "Document type is all, purchase_order or contract.");

        List<Map<String, Object>> rows = new ArrayList<>();
        if (!"contract".equals(t)) {
            jdbc.query("""
                    select po.id, po.po_number, po.order_date, s.name, po.total_amount, pr.pr_number, coalesce(pr.purpose, po.remarks),
                           coalesce(b.bidding_number, r.rfq_number), po.status
                    from purchase_orders po join suppliers s on s.id = po.supplier_id
                    left join purchase_requests pr on pr.id = po.purchase_request_id
                    left join biddings b on b.id = po.bidding_id left join rfqs r on r.id = po.rfq_id
                    where po.deleted_at is null and po.status in ('sent', 'confirmed', 'partially_received', 'received')
                      and po.order_date between ? and ?""", rs -> {
                rows.add(row("purchase_order", "Purchase Order", rs.getLong(1), rs.getString(2), rs.getDate(3), rs.getString(4), rs.getBigDecimal(5),
                        rs.getString(7), join(rs.getString(6), rs.getString(8)), rs.getString(9)));
            }, from, to);
        }
        if (!"purchase_order".equals(t)) {
            jdbc.query("""
                    select c.id, c.contract_number, c.signed_date, s.name, c.amount, c.title, pr.pr_number,
                           coalesce(b.bidding_number, r.rfq_number), po.po_number, c.status
                    from contracts c join suppliers s on s.id = c.supplier_id
                    left join purchase_requests pr on pr.id = c.purchase_request_id
                    left join biddings b on b.id = c.bidding_id left join rfqs r on r.id = c.rfq_id
                    left join purchase_orders po on po.id = c.purchase_order_id
                    where c.deleted_at is null and c.signed_date between ? and ?""", rs -> {
                rows.add(row("contract", "Contract", rs.getLong(1), rs.getString(2), rs.getDate(3), rs.getString(4), rs.getBigDecimal(5),
                        rs.getString(6), join(join(rs.getString(7), rs.getString(8)), rs.getString(9)), rs.getString(10)));
            }, from, to);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (String) r.get("document_date"))
                .thenComparing(r -> (String) r.get("document_type")).thenComparing(r -> (String) r.get("document_number")));
        BigDecimal total = BigDecimal.ZERO;
        int no = 1;
        for (Map<String, Object> r : rows) {
            r.put("no", no++);
            total = total.add((BigDecimal) r.get("amount"));
        }
        Signatories.Signatory coa = signatories.resolve("coa_representative");
        Signatories.Signatory supply = signatories.resolve("supply_officer");
        Signatories.Signatory head = signatories.resolve("head_of_agency");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Transmittal of Procurement Documents to COA");
        out.put("date_from", from.toString());
        out.put("date_to", to.toString());
        out.put("document_type", t);
        out.put("transmittal_number", blank(transmittalNumber));
        out.put("transmittal_date", LocalDate.now(Times.ZONE).toString());
        out.put("auditor", blank(auditor) != null ? blank(auditor) : coa.name());
        out.put("purchase_orders", rows.stream().filter(r -> "purchase_order".equals(r.get("document_kind"))).count());
        out.put("contracts", rows.stream().filter(r -> "contract".equals(r.get("document_kind"))).count());
        out.put("total_amount", total.setScale(2, RoundingMode.HALF_UP));
        out.put("transmitted_by", supply.name());
        out.put("transmitted_by_designation", supply.designation());
        out.put("noted_by", head.name());
        out.put("noted_by_designation", head.designation());
        out.put("rows", rows);
        return out;
    }

    private static Map<String, Object> row(String kind, String label, long id, String number, Date date, String supplier, BigDecimal amount,
                                           String particulars, String reference, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("no", null);
        m.put("document_kind", kind);
        m.put("document_type", label);
        m.put("id", id);
        m.put("document_number", number);
        m.put("document_date", date == null ? null : date.toLocalDate().toString());
        m.put("supplier", supplier);
        m.put("particulars", particulars);
        m.put("reference", reference);
        m.put("amount", amount == null ? BigDecimal.ZERO.setScale(2) : amount);
        m.put("status", status);
        return m;
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " / " + b;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String longDate(Object iso) {
        return iso == null ? "" : LocalDate.parse(iso.toString()).format(LONG);
    }

    @SuppressWarnings("unchecked")
    public byte[] generate(Map<String, Object> d, String entityName) {
        Xlsx x = new Xlsx("COA Transmittal");
        x.defaultFont(FONT, 11);
        x.page().portrait().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0).margins(0.75, 0.6, 0.75, 0.6);
        // A No | B Document | C Number | D Date | E Supplier | F Particulars | G Reference | H Amount
        String[] widths = {"A:5", "B:15", "C:18", "D:12", "E:24", "F:30", "G:20", "H:15"};
        for (String w : widths) x.width(w.split(":")[0], Double.parseDouble(w.split(":")[1]));

        x.merge("A1:H1");
        x.set("A1", PpmpGppbForm.or(entityName, "______________________________"));
        x.style("A1").bold().size(13).center();
        x.merge("A3:H3");
        x.set("A3", "TRANSMITTAL OF PROCUREMENT DOCUMENTS");
        x.style("A3").bold().size(14).center();
        x.merge("A5:E5");
        x.set("A5", "Transmittal No.: " + PpmpGppbForm.or((String) d.get("transmittal_number"), "____________"));
        x.merge("F5:H5");
        x.set("F5", "Date: " + longDate(d.get("transmittal_date")));
        x.style("F5").right();

        x.set("A7", "To:");
        x.merge("B7:H7");
        x.set("B7", PpmpGppbForm.or((String) d.get("auditor"), "______________________________"));
        x.style("B7").bold();
        x.merge("B8:H8");
        x.set("B8", "Audit Team Leader / State Auditor, Commission on Audit");
        x.merge("A10:H10");
        x.set("A10", "Sir/Madam:");
        x.merge("A11:H12");
        x.set("A11", "In compliance with COA Circular No. 2009-001, we respectfully transmit the following purchase orders and contracts issued from "
                + longDate(d.get("date_from")) + " to " + longDate(d.get("date_to")) + ", together with their supporting documents, for your audit.");
        x.style("A11").wrap().valign(Xlsx.V.TOP);
        x.height(11, 18);
        x.height(12, 18);

        int h = 14;
        String[] heads = {"No.", "Document", "Number", "Date", "Supplier / Contractor", "Particulars", "Reference (PR / Bidding / RFQ / PO)", "Amount"};
        for (int i = 0; i < heads.length; i++) x.set(Xlsx.col(i + 1) + h, heads[i]);
        x.style("A" + h + ":H" + h).bold().center().vcenter().wrap().size(10).border(Edge.ALL, Line.THIN);
        x.height(h, 30);
        int r = h + 1;
        for (Map<String, Object> row : (List<Map<String, Object>>) d.get("rows")) {
            x.set("A" + r, row.get("no"));
            x.set("B" + r, row.get("document_type"));
            x.set("C" + r, row.get("document_number"));
            x.set("D" + r, row.get("document_date"));
            x.set("E" + r, row.get("supplier"));
            x.set("F" + r, row.get("particulars"));
            x.set("G" + r, row.get("reference"));
            x.set("H" + r, row.get("amount"));
            r++;
        }
        int first = h + 1;
        if (r == first) {
            x.merge("A" + r + ":H" + r);
            x.set("A" + r, "No purchase orders or contracts in the period.");
            x.style("A" + r).italic().center();
            r++;
        }
        x.style("A" + first + ":H" + (r - 1)).border(Edge.ALL, Line.THIN).vcenter().wrap().size(10);
        x.style("A" + first + ":A" + (r - 1)).center();
        x.style("D" + first + ":D" + (r - 1)).center();
        x.style("H" + first + ":H" + r).numberFormat("#,##0.00");
        x.merge("A" + r + ":G" + r);
        x.set("A" + r, "TOTAL (" + d.get("purchase_orders") + " purchase order(s), " + d.get("contracts") + " contract(s))");
        x.set("H" + r, d.get("total_amount"));
        x.style("A" + r + ":H" + r).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + r).right();

        int s = r + 2;
        x.set("A" + s, "Transmitted by:");
        x.merge("A" + (s + 3) + ":D" + (s + 3));
        x.set("A" + (s + 3), PpmpGppbForm.or((String) d.get("transmitted_by"), "________________________________"));
        x.style("A" + (s + 3) + ":D" + (s + 3)).bold().center().border(Edge.BOTTOM, Line.THIN);
        x.merge("A" + (s + 4) + ":D" + (s + 4));
        x.set("A" + (s + 4), PpmpGppbForm.or((String) d.get("transmitted_by_designation"), "Supply Officer"));
        x.style("A" + (s + 4)).italic().center();
        x.set("F" + s, "Noted by:");
        x.merge("F" + (s + 3) + ":H" + (s + 3));
        x.set("F" + (s + 3), PpmpGppbForm.or((String) d.get("noted_by"), "________________________________"));
        x.style("F" + (s + 3) + ":H" + (s + 3)).bold().center().border(Edge.BOTTOM, Line.THIN);
        x.merge("F" + (s + 4) + ":H" + (s + 4));
        x.set("F" + (s + 4), PpmpGppbForm.or((String) d.get("noted_by_designation"), "Head of Agency"));
        x.style("F" + (s + 4)).italic().center();

        int c = s + 7;
        x.merge("A" + c + ":H" + c);
        x.set("A" + c, "RECEIVED BY COA");
        x.style("A" + c).bold().center();
        x.merge("A" + (c + 1) + ":H" + (c + 1));
        x.set("A" + (c + 1), "Received the above documents, complete with attachments, except as noted:");
        x.style("A" + (c + 1)).italic();
        x.merge("A" + (c + 3) + ":D" + (c + 3));
        x.set("A" + (c + 3), "Signature over Printed Name: ______________________");
        x.merge("E" + (c + 3) + ":H" + (c + 3));
        x.set("E" + (c + 3), "Position: ______________________");
        x.merge("A" + (c + 5) + ":D" + (c + 5));
        x.set("A" + (c + 5), "Date / Time Received: ______________________");
        x.merge("E" + (c + 5) + ":H" + (c + 5));
        x.set("E" + (c + 5), "Remarks: ______________________");
        x.style("A" + c + ":H" + (c + 6)).border(Edge.OUTLINE, Line.MEDIUM);
        return x.bytes();
    }
}

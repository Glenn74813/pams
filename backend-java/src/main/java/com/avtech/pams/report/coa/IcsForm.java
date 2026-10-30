package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.H;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inventory Custodian Slip — COA GAM Appendix 59.
 *
 * Issued for SEMI-EXPENDABLE property (tangible items below the ₱50,000 capitalization threshold
 * with a useful life beyond one year). Counterpart of the PAR (Appendix 71), which covers PPE at or
 * above the threshold.
 */
@Component
public class IcsForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;

    public IcsForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] generate(Long requestId, String entityName, String fundCluster) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select r.*, d.name as department_name, u.name as requester_name
                from requests r
                left join departments d on d.id = r.department_id
                left join users u on u.id = r.requested_by
                where r.id = ?""", requestId);
        if (found.isEmpty()) throw ApiException.notFound("Request");
        Map<String, Object> request = found.get(0);

        Xlsx x = new Xlsx("ICS", FONT, 12);

        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(1.0).bottom(0.5).left(1.0).right(0.7);

        // Columns: A Qty | B Unit | C Unit Cost | D Total Cost | E Description | F Item No. | G Useful Life
        x.width("A", 9);
        x.width("B", 8);
        x.width("C", 12);
        x.width("D", 13);
        x.width("E", 34);
        x.width("F", 12);
        x.width("G", 12);

        // Appendix tag
        x.merge("A1:G1");
        x.set("A1", "Appendix 59");
        x.style("A1").italic().size(11).align(H.RIGHT);

        // Title
        x.merge("A3:G3");
        x.set("A3", "INVENTORY CUSTODIAN SLIP");
        x.style("A3").bold().size(14).align(H.CENTER);

        // Entity / Fund / ICS No.
        String entityValue = empty(entityName) ? "__________________________________" : entityName;
        String fundValue = empty(fundCluster) ? "______________________" : fundCluster;
        Object icsNo = request.get("ics_number") != null ? request.get("ics_number")
                : request.get("request_number") != null ? request.get("request_number") : request.get("id");
        x.merge("A5:E5");
        x.set("A5", "Entity Name : " + entityValue);
        x.merge("A6:D6");
        x.set("A6", "Fund Cluster : " + fundValue);
        x.merge("E6:G6");
        x.set("E6", "ICS No. : " + icsNo);
        x.style("E6").align(H.RIGHT);
        x.style("A5:G6").bold();

        // Header row (8) with a grouped Amount over C:D on row 8, sub-labels row 9
        x.merge("A8:A9").set("A8", "Quantity");
        x.merge("B8:B9").set("B8", "Unit");
        x.merge("C8:D8").set("C8", "Amount");
        x.set("C9", "Unit Cost");
        x.set("D9", "Total Cost");
        x.merge("E8:E9").set("E8", "Description");
        x.merge("F8:F9").set("F8", "Inventory Item No.");
        x.merge("G8:G9").set("G8", "Estimated Useful Life");
        x.style("A8:G9").bold().size(11).align(H.CENTER).valign(V.CENTER).wrap().border(Edge.ALL, Line.THIN);

        // Semi-expendable lines only.
        List<Map<String, Object>> lines = jdbc.queryForList("""
                select ri.quantity, ri.approved_quantity, i.name, i.description, i.unit_of_measure,
                       coalesce(ri.unit_cost, i.weighted_average_cost) as weighted_average_cost, i.code
                from request_items ri
                join items i on i.id = ri.item_id
                where ri.request_id = ? and i.is_semi_expandable = ?
                order by ri.id""", requestId, true);

        int start = 10;
        int end = Math.max(start + 14, start + lines.size() - 1); // at least 15 rows
        for (int row = start; row <= end; row++) {
            int idx = row - start;
            if (idx < lines.size()) {
                Map<String, Object> ri = lines.get(idx);
                int qty = ((Number) (ri.get("approved_quantity") != null ? ri.get("approved_quantity") : ri.get("quantity"))).intValue();
                BigDecimal unitCost = ri.get("weighted_average_cost") instanceof BigDecimal b ? b : BigDecimal.ZERO;
                String description = (String) ri.get("description");
                String desc = (str(ri.get("name")) + (empty(description) ? "" : "\n" + description)).trim();
                x.set("A" + row, qty);
                x.set("B" + row, str(ri.get("unit_of_measure")));
                x.set("C" + row, Xlsx.bind(numberFormat(unitCost)));
                x.set("D" + row, Xlsx.bind(numberFormat(unitCost.multiply(BigDecimal.valueOf(qty)))));
                x.set("E" + row, desc);
                x.set("F" + row, str(ri.get("code")));
                x.set("G" + row, "");
                x.style("E" + row).wrap();
            }
            x.style("A" + row + ":G" + row).border(Edge.ALL, Line.THIN);
            x.style("A" + row + ":B" + row).align(H.CENTER);
            x.style("C" + row + ":D" + row).align(H.RIGHT);
        }

        // Signature blocks: Received from (custodian) / Received by (end user).
        int sigRow = end + 2;
        x.merge("A" + sigRow + ":D" + sigRow).set("A" + sigRow, "Received from:");
        x.merge("E" + sigRow + ":G" + sigRow).set("E" + sigRow, "Received by:");
        x.style("A" + sigRow + ":G" + sigRow).bold();

        int nameRow = sigRow + 2;
        String custodian = "Supply/Property Custodian";
        String endUser = str(request.get("requester_name"));
        x.merge("A" + nameRow + ":D" + nameRow).set("A" + nameRow, "____________________________");
        x.merge("E" + nameRow + ":G" + nameRow).set("E" + nameRow, empty(endUser) ? "____________________________" : asciiUpper(endUser));
        int labelRow = nameRow + 1;
        x.merge("A" + labelRow + ":D" + labelRow).set("A" + labelRow, "Signature over Printed Name (" + custodian + ")");
        x.merge("E" + labelRow + ":G" + labelRow).set("E" + labelRow, "Signature over Printed Name (End User)");
        x.style("A" + labelRow + ":G" + labelRow).size(10);

        int dateRow = labelRow + 2;
        x.merge("A" + dateRow + ":D" + dateRow).set("A" + dateRow, "Date : ____________________");
        x.merge("E" + dateRow + ":G" + dateRow).set("E" + dateRow, "Date : ____________________");

        return x.bytes();
    }

    /**
     * PHP number_format($n, 2). The PHP passed this string to setCellValue, whose value binder turned
     * amounts below 1,000 ("12.50") into plain numbers and left "1,250.00" as text; callers wrap it in
     * {@link Xlsx#bind} to keep that.
     */
    private static String numberFormat(BigDecimal n) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(n);
    }

    /** PHP 8 strtoupper: ASCII letters only. */
    private static String asciiUpper(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) b.append(c >= 'a' && c <= 'z' ? (char) (c - 32) : c);
        return b.toString();
    }

    /** PHP's empty() for a string: null, "" or "0". */
    private static boolean empty(String s) {
        return s == null || s.isEmpty() || "0".equals(s);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}

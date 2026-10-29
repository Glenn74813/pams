package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stock Card (SC) — COA Appendix 58: one item's receipts and issues with a running balance, from
 * the same inventory logs and balance logic as the on-screen Stock Card preview.
 */
@Component
public class StockCardForm {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final Set<String> RECEIPTS = Set.of("stock_in", "delivery_accepted", "adjustment_in");
    private static final Set<String> ISSUES = Set.of("stock_out", "item_issued", "adjustment_out");
    private static final Set<String> ADJUSTMENTS = Set.of("adjustment", "adjustment_in", "adjustment_out", "stock_edited");
    private static final Pattern NET_CHANGE = Pattern.compile("net change (-?\\d+)");

    private final JdbcTemplate jdbc;

    public StockCardForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] generate(int itemId, String entityName, String fundCluster) {
        List<Map<String, Object>> found = jdbc.queryForList(
                "select id, code, name, description, unit_of_measure, min_stock, current_stock from items where id = ?", (long) itemId);
        if (found.isEmpty()) throw ApiException.notFound("Item");
        Map<String, Object> item = found.get(0);

        // Get inventory logs for this item (receipts and issues)
        List<Map<String, Object>> logs = jdbc.queryForList("""
                select l.id, l.action, l.quantity, l.previous_quantity, l.new_quantity, l.notes, l.created_at,
                       u.id as user_id, d.name as department_name
                from inventory_logs l
                left join users u on u.id = l.user_id
                left join departments d on d.id = u.department_id
                where l.item_id = ?
                  and l.action in ('stock_in', 'stock_out', 'delivery_accepted', 'item_issued', 'adjustment', 'adjustment_in', 'adjustment_out', 'stock_edited')
                order by l.created_at asc, l.id asc
                """, (long) itemId);

        // Opening balance anchors the card to the item's real current stock.
        int net = 0;
        for (Map<String, Object> log : logs) net += signedDelta(log);
        int openingBalance = intOf(item.get("current_stock")) - net;

        Xlsx x = new Xlsx("Stock Card");
        x.defaultFont(FONT, 10);

        // Page Setup
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.75).right(0.5);

        // Column Widths
        x.width("A", 12);
        x.width("B", 14);
        x.width("C", 12);
        x.width("D", 12);
        x.width("E", 14);
        x.width("F", 12);
        x.width("G", 18);

        // Row 1 - Appendix 58
        // The PHP merged G1:G1, a one-cell range that changes nothing (and POI refuses it).
        x.set("G1", "Appendix 58");
        x.style("G1").size(9).italic();
        x.style("G1").right();

        // Row 4 - Title
        x.merge("A4:G4");
        x.set("A4", "STOCK CARD");
        x.style("A4").size(14).bold();
        x.style("A4").center();

        // Row 6 - Entity & Fund Cluster
        x.set("A6", "Entity Name: " + or(entityName, "___________________________________"));
        x.set("F6", "Fund Cluster: " + or(fundCluster, "_________________"));
        x.style("A6:G6").size(10);

        // Row 8 - Item & Stock No
        x.set("A8", "Item: " + item.get("name"));
        x.set("F8", "Stock No.: " + item.get("code"));
        x.style("A8:G8").size(10);

        // Row 9 - Description & Re-order Point
        x.set("A9", "Description: " + or((String) item.get("description"), ""));
        x.set("F9", "Re-order Point: " + item.get("min_stock"));
        x.style("A9:G9").size(10);

        // Row 10 - Unit of Measurement
        x.set("A10", "Unit of Measurement: " + item.get("unit_of_measure"));
        x.style("A10").size(10);

        // Row 11-12 - Column Headers
        x.set("A11", "Date");
        x.set("B11", "Reference");
        x.set("C11", "Receipt");
        x.set("D11", "Issue");
        x.set("F11", "Balance");
        x.set("G11", "No. of Days to Consume");
        x.set("C12", "Qty.");
        x.set("D12", "Qty.");
        x.set("E12", "Office");
        x.set("F12", "Qty.");

        // Merge header cells
        x.merge("A11:A12");
        x.merge("B11:B12");
        x.merge("D11:E11");
        x.merge("F11:F12");
        x.merge("G11:G12");

        // Header styles
        x.style("A11:G12").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        // Data rows
        int row = 13;
        int runningBalance = openingBalance;

        // Beginning balance line.
        x.set("A" + row, logs.isEmpty() ? "" : mdy(logs.get(0).get("created_at")));
        x.set("B" + row, "Beginning balance");
        x.set("F" + row, runningBalance);
        x.style("A" + row + ":G" + row).border(Edge.ALL, Line.THIN).vcenter();
        x.style("C" + row + ":F" + row).center();
        x.style("A" + row).size(9);
        row++;

        for (Map<String, Object> log : logs) {
            Object receiptQty = "";
            Object issueQty = "";
            String office = "";
            int signed = signedDelta(log);
            runningBalance += signed;
            boolean isAdj = ADJUSTMENTS.contains((String) log.get("action"));
            String notes = (String) log.get("notes");
            String reference = truthy(notes) ? substr25(notes) : (isAdj ? "Adjustment" : (signed >= 0 ? "Stock In" : "Issued"));
            if (signed > 0) {
                receiptQty = signed;
            } else if (signed < 0) {
                issueQty = Math.abs(signed);
                if (log.get("user_id") != null) {
                    office = log.get("department_name") != null ? (String) log.get("department_name") : "";
                }
            }

            x.set("A" + row, mdy(log.get("created_at")));
            x.set("B" + row, reference);
            x.set("C" + row, receiptQty);
            x.set("D" + row, issueQty);
            x.set("E" + row, office);
            x.set("F" + row, runningBalance);

            x.style("A" + row + ":G" + row).border(Edge.ALL, Line.THIN).vcenter();
            x.style("C" + row + ":F" + row).center();
            x.style("A" + row).size(9);

            row++;
        }

        // If no data, add empty rows
        if (logs.isEmpty()) {
            for (int i = 0; i < 20; i++) {
                x.style("A" + row + ":G" + row).border(Edge.ALL, Line.THIN);
                row++;
            }
        }

        return x.bytes();
    }

    /** Signed change per movement (positive = receipt, negative = issue). */
    private static int signedDelta(Map<String, Object> log) {
        int qty = Math.abs(intOf(log.get("quantity")));
        String action = (String) log.get("action");
        if (RECEIPTS.contains(action)) return qty;
        if (ISSUES.contains(action)) return -qty;
        String notes = (String) log.get("notes");
        if (notes != null) {
            Matcher m = NET_CHANGE.matcher(notes);
            if (m.find()) return (int) Long.parseLong(m.group(1));
        }
        int d = intOf(log.get("new_quantity")) - intOf(log.get("previous_quantity"));
        return d != 0 ? d : qty;
    }

    private static String mdy(Object createdAt) {
        if (createdAt == null) return null;
        LocalDateTime t = createdAt instanceof Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) createdAt;
        return t.format(MDY);
    }

    /** PHP {@code substr($s, 0, 25)}: the first 25 bytes, without splitting a UTF-8 character. */
    private static String substr25(String s) {
        if (s.getBytes(StandardCharsets.UTF_8).length <= 25) return s;
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int n = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + n > 25) break;
            out.appendCodePoint(cp);
            bytes += n;
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static boolean truthy(String s) {
        return s != null && !s.isEmpty() && !s.equals("0");
    }

    /** PHP {@code $a ?: $b} for strings. */
    private static String or(String a, String b) {
        return truthy(a) ? a : b;
    }

    private static int intOf(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }
}

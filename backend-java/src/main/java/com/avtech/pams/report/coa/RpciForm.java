package com.avtech.pams.report.coa;

import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Report on the Physical Count of Inventories (RPCI) — COA Appendix 66. Lists all consumable /
 * semi-expandable items with card balance vs physical count.
 */
@Component
public class RpciForm {

    private static final String FONT = "Times New Roman";
    static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH);

    private final JdbcTemplate jdbc;

    public RpciForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code countData} is the physical count per item, keyed by item id (PHP {@code [$itemId => $qty]});
     * without it the on-hand and shortage columns stay blank.
     */
    public byte[] generate(String inventoryType, LocalDate asAtDate, String fundCluster, Map<Long, ? extends Number> countData) {
        // Get items that are NOT expandable (i.e., consumable or semi-expandable inventory)
        String where;
        Object[] args;
        if ("consumable".equals(inventoryType)) {
            where = "i.is_expandable = ? and i.is_semi_expandable = ?";
            args = new Object[]{false, false};
        } else if ("semi_expandable".equals(inventoryType)) {
            where = "i.is_semi_expandable = ?";
            args = new Object[]{true};
        } else {
            // All inventory items (non-PPE)
            where = "i.is_expandable = ?";
            args = new Object[]{false};
        }
        List<Map<String, Object>> items = jdbc.queryForList("""
                select i.id, i.code, i.name, i.unit_of_measure, i.weighted_average_cost, i.current_stock, c.name as category_name
                from items i
                left join categories c on c.id = i.category_id
                where %s
                order by i.name, i.id
                """.formatted(where), args);

        Xlsx x = new Xlsx("RPCI");
        x.defaultFont(FONT, 10);

        // Page Setup
        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        // Column Widths
        Map<String, Double> colWidths = new LinkedHashMap<>();
        colWidths.put("A", 3.0);
        colWidths.put("B", 14.0);
        colWidths.put("C", 20.0);
        colWidths.put("D", 12.0);
        colWidths.put("E", 12.0);
        colWidths.put("F", 12.0);
        colWidths.put("G", 12.0);
        colWidths.put("H", 12.0);
        colWidths.put("I", 12.0);
        colWidths.put("J", 12.0);
        colWidths.put("K", 16.0);
        colWidths.forEach(x::width);

        // Row 1 - Appendix 66
        // The PHP merged K1:K1, a one-cell range that changes nothing (and POI refuses it).
        x.set("K1", "Appendix 66");
        x.style("K1").size(9).italic();
        x.style("K1").right();

        // Row 3 - Title
        x.merge("B3:K3");
        x.set("B3", "REPORT ON THE PHYSICAL COUNT OF INVENTORIES");
        x.style("B3").size(14).bold();
        x.style("B3").center();

        // Row 4-5 - Type of Inventory
        String typeLabel = "consumable".equals(inventoryType) ? "Consumable Supplies"
                : ("semi_expandable".equals(inventoryType) ? "Semi-Expandable Items" : "All Inventory Items");
        x.merge("B4:K4");
        x.set("B4", typeLabel);
        x.style("B4").center();
        x.style("B4").underline();
        x.merge("B5:K5");
        x.set("B5", "(Type of Inventory Item)");
        x.style("B5").size(8).italic();
        x.style("B5").center();

        // Row 6 - As at date
        x.merge("B6:K6");
        x.set("B6", "As at " + (asAtDate != null ? asAtDate : LocalDate.now(Times.ZONE)).format(LONG_DATE));
        x.style("B6").center();

        // Row 8 - Fund Cluster
        x.set("B8", "Fund Cluster: " + or(fundCluster, "________________________________"));

        // Row 12-14 - Column Headers
        countHeaders(x, "Stock Number");

        // Data rows
        int row = 15;
        for (Map<String, Object> item : items) {
            countRow(x, row, item, countData);
            row++;
        }

        // Fill empty rows if no data
        if (items.isEmpty()) {
            for (int i = 0; i < 20; i++) {
                x.style("B" + row + ":K" + row).border(Edge.ALL, Line.THIN);
                row++;
            }
        }

        // Signature section
        countSignatures(x, row + 2);

        return x.bytes();
    }

    /** Rows 12-14 of the physical-count forms (RPCI and RPCSP differ only in column D's label). */
    static void countHeaders(Xlsx x, String numberLabel) {
        x.set("B12", "Article");
        x.set("C12", "Description");
        x.set("D12", numberLabel);
        x.set("E12", "Unit of Measure");
        x.set("F12", "Unit Value");
        x.set("G12", "Balance Per Card");
        x.set("H12", "On Hand Per Count");
        x.set("I12", "Shortage/Overage");
        x.set("K12", "Remarks");
        x.set("G14", "(Quantity)");
        x.set("H14", "(Quantity)");
        x.set("I14", "Quantity");
        x.set("J14", "Value");

        // Merge header cells
        for (String m : new String[]{"B12:B14", "C12:C14", "D12:D14", "E12:E14", "F12:F14", "G12:G13", "H12:H13", "I12:J13", "K12:K14"}) {
            x.merge(m);
        }

        x.style("B12:K14").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);
    }

    /** One item line: card balance, and the count with its shortage/overage when a count was given. */
    static void countRow(Xlsx x, int row, Map<String, Object> item, Map<Long, ? extends Number> countData) {
        int cardQty = ((Number) item.get("current_stock")).intValue();
        double wac = item.get("weighted_average_cost") == null ? 0 : ((Number) item.get("weighted_average_cost")).doubleValue();
        // If physical count data provided, use it; otherwise leave blank
        Object onHandQty = "";
        Object shortageQty = "";
        Object shortageVal = "";
        Long id = ((Number) item.get("id")).longValue();
        if (countData != null && !countData.isEmpty() && countData.get(id) != null) {
            Number onHand = countData.get(id);
            onHandQty = onHand;
            Number diff = isIntegral(onHand) ? (Number) (onHand.longValue() - cardQty) : (Number) (onHand.doubleValue() - cardQty);
            shortageQty = diff;
            shortageVal = diff.doubleValue() * wac;
        }

        x.set("B" + row, item.get("category_name") != null ? item.get("category_name") : "");
        x.set("C" + row, item.get("name"));
        x.set("D" + row, item.get("code"));
        x.set("E" + row, item.get("unit_of_measure"));
        x.set("F" + row, wac);
        x.style("F" + row).numberFormat("#,##0.00");
        x.set("G" + row, cardQty);
        x.set("H" + row, onHandQty);
        x.set("I" + row, shortageQty);
        if (!"".equals(shortageVal)) {
            x.set("J" + row, shortageVal);
            x.style("J" + row).numberFormat("#,##0.00");
        }

        x.style("B" + row + ":K" + row).border(Edge.ALL, Line.THIN).vcenter();
        x.style("D" + row + ":J" + row).center();
    }

    /** The certification block, starting {@code row} (two rows below the table). */
    static void countSignatures(Xlsx x, int row) {
        x.set("B" + row, "Certified Correct by:");
        x.set("F" + row, "Approved by:");
        x.set("K" + row, "Verified by:");
        row += 2;
        x.set("C" + row, "________________________________");
        x.set("K" + row, "_______________________________");
        x.style("C" + row).center();
        x.style("K" + row).center();
        row++;
        x.set("C" + row, "Signature over Printed Name of Inventory Committee Chair and Members");
        x.style("C" + row).size(8).italic();
        x.style("C" + row).center();
        x.merge("G" + row + ":I" + row);
        x.set("G" + row, "Signature over Printed Name of Head of Agency/Entity or Authorized Representative");
        x.style("G" + row).size(8).italic();
        x.style("G" + row).center().wrap();
        x.set("K" + row, "Signature over Printed Name of COA Representative");
        x.style("K" + row).size(8).italic();
        x.style("K" + row).center().wrap();
    }

    private static boolean isIntegral(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }

    /** PHP {@code $a ?: $b} for strings. */
    static String or(String a, String b) {
        return a == null || a.isEmpty() || a.equals("0") ? b : a;
    }
}

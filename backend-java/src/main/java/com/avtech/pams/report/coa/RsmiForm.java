package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.H;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Report of Supplies and Materials Issued (RSMI) — COA Appendix 64: every approved line of the
 * month's approved/fulfilled requests, with a recapitulation by stock number.
 */
@Component
public class RsmiForm {

    private static final String FONT = "Times New Roman";
    private static final String[] COLS = {"A", "B", "C", "D", "E", "F", "G", "H"};

    private final JdbcTemplate jdbc;

    public RsmiForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final class Recap {
        final String stockNo;
        long quantity;
        final double unitCost;
        double totalCost;

        Recap(String stockNo, double unitCost) {
            this.stockNo = stockNo;
            this.unitCost = unitCost;
        }
    }

    public byte[] generate(int year, int month, String entityName, String fundCluster) {
        Xlsx x = new Xlsx("RSMI");

        // ── Set default font for entire sheet ───────────────────────────
        x.defaultFont(FONT, 12);

        // ── Fetch data ──────────────────────────────────────────────────
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // One row per request line, in request order (Request::with('department', 'items.item.category')).
        List<Map<String, Object>> lines = jdbc.queryForList("""
                select r.ris_number, r.request_number, d.name as department_name, ri.approved_quantity,
                       i.id as item_id, i.code, i.name, i.unit_of_measure, coalesce(ri.unit_cost, i.weighted_average_cost) as weighted_average_cost
                from requests r
                left join departments d on d.id = r.department_id
                join request_items ri on ri.request_id = r.id
                left join items i on i.id = ri.item_id
                where r.request_date between ? and ?
                  and r.status in ('approved', 'fulfilled', 'partially_fulfilled')
                order by r.request_date, r.id, ri.id
                """, startDate, endDate);

        // ── Column widths (matching original) ───────────────────────────
        x.width("A", 12);
        x.width("B", 16.56);
        x.width("C", 14.56);
        x.width("D", 14.56);
        x.width("E", 13.89);
        x.width("F", 13.33);
        x.width("G", 18.89);
        x.width("H", 24.44);

        // ROW 1: "Appendix 64" — top right, italic
        x.set("H1", "Appendix 64");
        x.style("H1").font(FONT).italic().size(18);

        // ROW 3: Title — centered, bold, 14pt
        x.merge("A3:H3");
        x.set("A3", " REPORT OF SUPPLIES AND MATERIALS ISSUED ");
        x.style("A3").font(FONT).bold().size(14).center();

        // ROW 6: Entity Name / Serial No.
        x.merge("A6:F6");
        x.set("A6", "Entity Name: " + or(entityName, "___________________________________"));
        x.style("A6").font(FONT).bold().size(11);

        x.merge("G6:H6");
        x.set("G6", "Serial No. : _______________________");
        x.style("G6").font(FONT).bold().size(11);

        // ROW 7: Fund Cluster / Date
        x.merge("A7:F7");
        x.set("A7", "Fund Cluster: " + or(fundCluster, "__________________________________"));
        x.style("A7").font(FONT).bold().size(11);

        String monthName = startDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        x.merge("G7:H7");
        x.set("G7", "Date : " + monthName);
        x.style("G7").font(FONT).bold().size(11);

        // ROW 9: Section sub-headers — italic
        x.merge("A9:F9");
        x.set("A9", "To be filled up by the Supply and/or Property Division/Unit");
        x.style("A9").font(FONT).italic().size(11);

        x.merge("G9:H9");
        x.set("G9", "To be filled up by the Accounting Division/Unit");
        x.style("G9").font(FONT).italic().size(11);

        // Row 9 borders: medium all around the two merged sections
        x.style("A9:F9").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);
        x.style("G9:H9").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);

        // ROW 10-11: Table column headers — merged vertically, bold, 11pt
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("A", "RIS No.");
        headers.put("B", "Responsibility Center Code ");
        headers.put("C", "Stock No.");
        headers.put("D", "Item");
        headers.put("E", "Unit");
        headers.put("F", "Quantity \nIssued");
        headers.put("G", "Unit Cost");
        headers.put("H", "Amount");

        headers.forEach((col, label) -> {
            x.merge(col + "10:" + col + "11");
            x.set(col + "10", label);
        });

        // Header style: bold 11pt, center, wrap, NO fill (original has no fill)
        x.style("A10:H11").font(FONT).bold().size(11).center().vcenter().wrap();

        // Header borders: each cell has medium left, medium right, top row medium top, bottom row medium bottom
        for (String col : COLS) {
            x.style(col + "10").borders(Line.MEDIUM, Edge.TOP, Edge.LEFT, Edge.RIGHT);
            x.style(col + "11").borders(Line.MEDIUM, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);
        }

        x.height(10, 30);
        x.height(11, 15);

        // DATA ROWS (starting from row 12)
        int dataRow = 12;
        int lastDataRow = 32; // minimum last data row to match form

        for (Map<String, Object> line : lines) {
            int approved = intOf(line.get("approved_quantity"));
            if (approved <= 0) {
                continue;
            }

            boolean hasItem = line.get("item_id") != null;
            double unitCost = hasItem ? doubleOf(line.get("weighted_average_cost")) : 0;
            double amount = approved * unitCost;

            x.set("A" + dataRow, or((String) line.get("ris_number"), (String) line.get("request_number")));
            x.set("B" + dataRow, line.get("department_name") != null ? line.get("department_name") : "");
            x.set("C" + dataRow, hasItem ? line.get("code") : "");
            x.set("D" + dataRow, hasItem ? line.get("name") : "");
            x.set("E" + dataRow, hasItem ? line.get("unit_of_measure") : "");
            x.set("F" + dataRow, approved);
            x.set("G" + dataRow, unitCost);
            x.set("H" + dataRow, amount);

            // Number formatting
            x.style("F" + dataRow).numberFormat("#,##0");
            x.style("G" + dataRow).numberFormat("#,##0.00");
            x.style("H" + dataRow).numberFormat("#,##0.00");

            dataRow++;
        }

        // If data exceeds row 32, adjust lastDataRow
        if (dataRow - 1 > lastDataRow) {
            lastDataRow = dataRow - 1;
        }

        // Apply borders to data rows (12 through lastDataRow) matching original:
        // A: left=medium, right=thin
        // B,C,D: left=thin, right=thin
        // E: left=thin, right=none
        // F: left=thin, right=thin
        // G: left=none, right=thin
        // H: left=none, right=medium
        // Top/bottom: none (except row 12 col F has top=medium from header)
        for (int r = 12; r <= lastDataRow; r++) {
            x.style("A" + r).border(Edge.LEFT, Line.MEDIUM).border(Edge.RIGHT, Line.THIN);
            x.style("B" + r).border(Edge.LEFT, Line.THIN).border(Edge.RIGHT, Line.THIN);
            x.style("C" + r).border(Edge.LEFT, Line.THIN).border(Edge.RIGHT, Line.THIN);
            x.style("D" + r).border(Edge.LEFT, Line.THIN).border(Edge.RIGHT, Line.THIN);
            x.style("E" + r).border(Edge.LEFT, Line.THIN);
            x.style("F" + r).border(Edge.LEFT, Line.THIN).border(Edge.RIGHT, Line.THIN);
            x.style("G" + r).border(Edge.RIGHT, Line.THIN);
            x.style("H" + r).border(Edge.RIGHT, Line.MEDIUM);
        }

        // Row 12 column F gets top=medium (transition from header)
        x.style("F12").border(Edge.TOP, Line.MEDIUM);

        // Last data row column F gets bottom=medium
        x.style("F" + lastDataRow).border(Edge.BOTTOM, Line.MEDIUM);

        // Set font for data rows
        x.style("A12:H" + lastDataRow).font(FONT).size(11);

        // RECAPITULATION SECTION (row after last data row)
        int recapStartRow = lastDataRow + 1; // row 33 in original

        // Left recapitulation header "Recapitulation:" in B:C
        x.merge("B" + recapStartRow + ":C" + recapStartRow);
        x.set("B" + recapStartRow, "Recapitulation:");
        x.style("B" + recapStartRow).font(FONT).bold().size(12);

        // Right recapitulation header "Recapitulation:" in F:H
        x.merge("F" + recapStartRow + ":H" + recapStartRow);
        x.set("F" + recapStartRow, "Recapitulation:");
        x.style("F" + recapStartRow).font(FONT).bold().size(12);

        // Recap header row borders
        // Left side: B-C medium border box
        x.style("B" + recapStartRow + ":C" + recapStartRow).borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);
        // Right side: F-H medium border box
        x.style("F" + recapStartRow + ":H" + recapStartRow).borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);

        // Outer frame borders for this row
        x.style("A" + recapStartRow).border(Edge.LEFT, Line.MEDIUM);
        x.style("D" + recapStartRow).border(Edge.RIGHT, Line.THIN);
        x.style("E" + recapStartRow).border(Edge.LEFT, Line.THIN);

        // ── Recap column headers (2 rows merged vertically) ─────────────
        int recapHeaderRow = recapStartRow + 1; // row 34 in original
        int recapHeaderRow2 = recapHeaderRow + 1; // row 35

        // Left: Stock No. and Quantity
        x.merge("B" + recapHeaderRow + ":B" + recapHeaderRow2);
        x.set("B" + recapHeaderRow, "Stock No.");
        x.merge("C" + recapHeaderRow + ":C" + recapHeaderRow2);
        x.set("C" + recapHeaderRow, "Quantity");

        // Right: Unit Cost, Total Cost, UACS Object Code
        x.merge("F" + recapHeaderRow + ":F" + recapHeaderRow2);
        x.set("F" + recapHeaderRow, "Unit Cost");
        x.merge("G" + recapHeaderRow + ":G" + recapHeaderRow2);
        x.set("G" + recapHeaderRow, "Total Cost");
        x.merge("H" + recapHeaderRow + ":H" + recapHeaderRow2);
        x.set("H" + recapHeaderRow, "UACS Object Code");

        x.style("B" + recapHeaderRow + ":C" + recapHeaderRow2).font(FONT).bold().size(12).center().vcenter().wrap();
        x.style("F" + recapHeaderRow + ":H" + recapHeaderRow2).font(FONT).bold().size(12).center().vcenter().wrap();

        // Recap column header borders — medium box per cell
        for (String col : new String[]{"B", "C"}) {
            x.style(col + recapHeaderRow).borders(Line.MEDIUM, Edge.TOP, Edge.LEFT, Edge.RIGHT);
            x.style(col + recapHeaderRow2).borders(Line.MEDIUM, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);
        }
        for (String col : new String[]{"F", "G", "H"}) {
            x.style(col + recapHeaderRow).borders(Line.MEDIUM, Edge.TOP, Edge.LEFT, Edge.RIGHT);
            x.style(col + recapHeaderRow2).borders(Line.MEDIUM, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);
        }

        // Outer frame for recap header rows
        x.style("A" + recapHeaderRow).border(Edge.LEFT, Line.MEDIUM);
        x.style("A" + recapHeaderRow2).border(Edge.LEFT, Line.MEDIUM);
        x.style("D" + recapHeaderRow).border(Edge.RIGHT, Line.THIN);
        x.style("E" + recapHeaderRow).border(Edge.LEFT, Line.THIN);
        x.style("D" + recapHeaderRow2).border(Edge.RIGHT, Line.THIN);
        x.style("E" + recapHeaderRow2).border(Edge.LEFT, Line.THIN);

        // ── Recap data rows ─────────────────────────────────────────────
        Map<String, Recap> recapData = new LinkedHashMap<>();
        for (Map<String, Object> line : lines) {
            int approved = intOf(line.get("approved_quantity"));
            if (approved <= 0) {
                continue;
            }
            if (line.get("item_id") == null) continue;

            String code = (String) line.get("code");
            double wac = doubleOf(line.get("weighted_average_cost"));
            Recap recap = recapData.computeIfAbsent(code, c -> new Recap(c, wac));
            recap.quantity += approved;
            recap.totalCost += approved * wac;
        }

        int recapDataStartRow = recapHeaderRow2 + 1; // row 36 in original
        int recapDataRow = recapDataStartRow;

        for (Recap recap : recapData.values()) {
            x.set("B" + recapDataRow, recap.stockNo);
            x.set("C" + recapDataRow, recap.quantity);
            x.set("F" + recapDataRow, recap.unitCost);
            x.set("G" + recapDataRow, recap.totalCost);
            x.set("H" + recapDataRow, "");

            x.style("C" + recapDataRow).numberFormat("#,##0");
            x.style("F" + recapDataRow).numberFormat("#,##0.00");
            x.style("G" + recapDataRow).numberFormat("#,##0.00");

            recapDataRow++;
        }

        // Minimum recap end = 14 rows of data space (up to row 49 in original = recapDataStartRow + 13)
        int recapLastRow = Math.max(recapDataRow - 1, recapDataStartRow + 13);

        // Apply borders to ALL recap data rows (filled or empty)
        for (int r = recapDataStartRow; r <= recapLastRow; r++) {
            // Left recap columns B-C
            x.style("B" + r).border(Edge.LEFT, Line.MEDIUM).border(Edge.RIGHT, Line.MEDIUM);
            x.style("C" + r).border(Edge.RIGHT, Line.MEDIUM);

            // Right recap columns F-H
            x.style("F" + r).border(Edge.LEFT, Line.MEDIUM).border(Edge.RIGHT, Line.MEDIUM);
            x.style("G" + r).border(Edge.LEFT, Line.MEDIUM).border(Edge.RIGHT, Line.MEDIUM);
            x.style("H" + r).border(Edge.RIGHT, Line.MEDIUM);

            // Outer frame
            x.style("A" + r).border(Edge.LEFT, Line.MEDIUM);
            x.style("D" + r).border(Edge.RIGHT, Line.THIN);
            x.style("E" + r).border(Edge.LEFT, Line.THIN);
        }

        // Bottom border on last recap row
        for (String col : new String[]{"B", "C"}) {
            x.style(col + recapLastRow).border(Edge.BOTTOM, Line.MEDIUM);
        }
        for (String col : new String[]{"F", "G", "H"}) {
            x.style(col + recapLastRow).border(Edge.BOTTOM, Line.MEDIUM);
        }

        // Set recap data font
        x.style("B" + recapDataStartRow + ":H" + recapLastRow).font(FONT).size(12);

        // FOOTER / CERTIFICATION (rows 50-54 of original)

        // ── Row 50: top border + "Posted by:" ───────────────────────────
        int row50 = recapLastRow + 1;
        x.height(row50, 18);

        // Top medium border across entire row (separates recap from footer)
        for (String c : COLS) {
            x.style(c + row50).border(Edge.TOP, Line.MEDIUM);
        }
        footerFrame(x, row50);

        x.set("F" + row50, "Posted by:");
        x.style("F" + row50).font(FONT).size(12);

        // ── Row 51: "I hereby certify ..." ──────────────────────────────
        int row51 = row50 + 1;
        x.height(row51, 18);
        x.merge("A" + row51 + ":E" + row51);
        x.set("A" + row51, "         I hereby certify to the correctness of the above information.");
        x.style("A" + row51).font(FONT).size(12).left();
        x.merge("F" + row51 + ":G" + row51);
        footerFrame(x, row51);

        // ── Row 52: Signature underlines ────────────────────────────────
        int row52 = row51 + 1;
        x.height(row52, 18);

        x.merge("A" + row52 + ":E" + row52);
        x.set("A" + row52, "_____________________________________________");
        x.style("A" + row52).font(FONT).size(12).center();

        x.merge("F" + row52 + ":G" + row52);
        x.set("F" + row52, "__________________________");
        x.style("F" + row52).font(FONT).size(12).center();

        x.set("H" + row52, "______________");
        x.style("H" + row52).font(FONT).size(12).center();

        footerFrame(x, row52);

        // ── Row 53: Signature labels (wrap text, taller row) ────────────
        int row53 = row52 + 1;
        x.height(row53, 33.75);

        x.merge("B" + row53 + ":D" + row53);
        x.set("B" + row53, "Signature over Printed Name of Supply and/or Property Custodian");
        x.style("B" + row53).font(FONT).size(12).center().valign(V.BOTTOM).wrap();

        x.merge("F" + row53 + ":G" + row53);
        x.set("F" + row53, "Signature over Printed Name of Designated Accounting Staff");
        x.style("F" + row53).font(FONT).size(12).center().valign(V.BOTTOM).wrap();

        x.set("H" + row53, "Date");
        x.style("H" + row53).font(FONT).size(12).align(H.CENTER).valign(V.TOP);

        footerFrame(x, row53);

        // ── Row 54: Bottom border (closes the form) ─────────────────────
        int row54 = row53 + 1;
        x.height(row54, 13.5);
        x.merge("B" + row54 + ":D" + row54);
        x.merge("G" + row54 + ":H" + row54);

        for (String c : COLS) {
            x.style(c + row54).border(Edge.BOTTOM, Line.MEDIUM);
        }
        footerFrame(x, row54);

        // PRINT SETTINGS
        x.page().portrait().paper(Xlsx.LEGAL).fitToWidth(1).fitToHeight(0)
                .top(0.5).bottom(0.5).left(0.5).right(0.5);

        return x.bytes();
    }

    /** Left/right outer frame borders of a footer row. */
    private static void footerFrame(Xlsx x, int row) {
        x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("E" + row).border(Edge.RIGHT, Line.MEDIUM);
        x.style("F" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("H" + row).border(Edge.RIGHT, Line.MEDIUM);
    }

    /** PHP {@code $a ?: $b} for strings: null, "" and "0" fall through. */
    private static String or(String a, String b) {
        return a == null || a.isEmpty() || a.equals("0") ? b : a;
    }

    private static int intOf(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    private static double doubleOf(Object v) {
        return v == null ? 0 : ((Number) v).doubleValue();
    }
}

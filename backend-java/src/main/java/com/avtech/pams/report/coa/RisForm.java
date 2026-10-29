package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.H;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Requisition and Issue Slip — COA GAM Appendix 63, for one request: what was asked for, whether it
 * was in stock, and what was issued.
 */
@Component
public class RisForm {

    private static final String FONT = "Times New Roman";
    private static final String[] AH = {"A", "B", "C", "D", "E", "F", "G", "H"};
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final JdbcTemplate jdbc;

    public RisForm(JdbcTemplate jdbc) {
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
        List<Map<String, Object>> items = jdbc.queryForList("""
                select ri.quantity, ri.approved_quantity, ri.status, i.code, i.unit_of_measure, i.name, i.current_stock
                from request_items ri
                left join items i on i.id = ri.item_id
                where ri.request_id = ?
                order by ri.id""", requestId);

        Xlsx x = new Xlsx("RIS");
        x.defaultFont(FONT, 12);

        // ── Page Setup ──
        x.page().paper(Xlsx.A4).portrait().scale(61).fitToWidth(1).fitToHeight(10);
        x.page().top(1.0).bottom(0.5).left(1.25).right(1.0).header(0.5).footer(0.5);
        x.page().printArea("A1:H39");

        // ── Column Widths ──
        x.width("A", 11.89);
        x.width("B", 6.55);
        x.width("C", 24.66);
        x.width("D", 13.66);
        x.width("E", 13.44);
        x.width("F", 13.66);
        x.width("G", 14.0);
        x.width("H", 30.11);

        // ── Row Heights ──
        x.height(1, 9.75);
        x.height(2, 24.75);
        // Row 3 default
        x.height(4, 26.25);
        x.height(5, 14.25);
        x.height(6, 22.5);
        x.height(7, 4.5);
        x.height(8, 22.5);
        x.height(9, 18.6);
        x.height(10, 26.1);
        x.height(11, 26.1);
        for (int r = 12; r <= 30; r++) {
            x.height(r, 26.1);
        }
        x.height(31, 0.75);
        x.height(32, 26.1);
        x.height(33, 26.1);
        x.height(34, 26.1);
        x.height(35, 29.1);
        x.height(36, 22.5);
        x.height(37, 22.65);
        x.height(38, 22.65);
        x.height(39, 22.65);

        // ROW 2 — Appendix 63
        x.set("H2", "Appendix 63\n");
        x.style("H2").font(FONT).size(16).italic().align(H.RIGHT).valign(V.TOP).wrap();

        // ROW 4 — Title
        x.merge("A4:H4");
        x.set("A4", "REQUISITION AND ISSUE SLIP");
        x.style("A4").font(FONT).size(14).bold().align(H.CENTER).valign(V.CENTER);

        // ROW 6 — Entity Name / Fund Cluster
        String entityValue = empty(entityName) ? "__________________________________" : entityName;
        x.set("A6", "Entity Name : " + entityValue);
        x.style("A6").font(FONT).size(12).bold();

        String fundValue = empty(fundCluster) ? "______________________" : fundCluster;
        x.set("G6", "Fund Cluster : " + fundValue);
        x.style("G6").font(FONT).size(12).bold();

        // ROW 7 — Thin spacer with left border
        x.style("A7").border(Edge.LEFT, Line.MEDIUM);

        // ROWS 8-9 — Division, Office, RIS No.
        String division = str(request.get("department_name"));
        x.set("A8", "Division : " + division);
        x.style("A8").font(FONT).size(12);

        x.set("F8", "Responsibility Center Code : ______________________");
        x.style("F8").font(FONT).size(12);

        String office = str(request.get("department_name"));
        x.set("A9", "Office : " + office);
        x.style("A9").font(FONT).size(12);

        Object risNo = request.get("ris_number") != null ? request.get("ris_number") : request.get("request_number");
        x.set("F9", "RIS No. : " + str(risNo));
        x.style("F9").font(FONT).size(12);

        // Borders for rows 8-9 (outer box with vertical divider at E/F)
        // Row 8
        for (String col : AH) {
            x.style(col + "8").border(Edge.TOP, Line.MEDIUM);
        }
        x.style("A8").border(Edge.LEFT, Line.MEDIUM);
        x.style("E8").border(Edge.RIGHT, Line.MEDIUM);
        x.style("F8").border(Edge.LEFT, Line.MEDIUM);
        x.style("H8").border(Edge.RIGHT, Line.MEDIUM);

        // Row 9
        for (String col : AH) {
            x.style(col + "9").border(Edge.BOTTOM, Line.MEDIUM);
        }
        x.style("A9").border(Edge.LEFT, Line.MEDIUM);
        x.style("E9").border(Edge.RIGHT, Line.MEDIUM);
        x.style("F9").border(Edge.LEFT, Line.MEDIUM);
        x.style("H9").border(Edge.RIGHT, Line.MEDIUM);

        // ROW 10 — Section Headers
        x.merge("A10:D10");
        x.set("A10", "Requisition");
        sectionStyle(x, "A10:D10");

        x.merge("E10:F10");
        x.set("E10", "Stock Available?");
        sectionStyle(x, "E10:F10");

        x.merge("G10:H10");
        x.set("G10", "Issue");
        sectionStyle(x, "G10:H10");

        // Row 10 borders: all medium top/bottom, section separators medium
        for (String col : AH) {
            x.style(col + "10").border(Edge.TOP, Line.MEDIUM);
            x.style(col + "10").border(Edge.BOTTOM, Line.MEDIUM);
        }
        x.style("A10").border(Edge.LEFT, Line.MEDIUM);
        x.style("D10").border(Edge.RIGHT, Line.MEDIUM);
        x.style("E10").border(Edge.LEFT, Line.MEDIUM);
        x.style("F10").border(Edge.RIGHT, Line.MEDIUM);
        x.style("G10").border(Edge.LEFT, Line.MEDIUM);
        x.style("H10").border(Edge.RIGHT, Line.MEDIUM);

        // ROW 11 — Column Headers
        String[] colHeaders = {"Stock No.", "Unit", "Description", "Quantity", "Yes", "No", "Quantity", "Remarks"};
        for (int i = 0; i < AH.length; i++) {
            String col = AH[i];
            x.set(col + "11", colHeaders[i]);
            x.style(col + "11").font(FONT).size(12).align(H.CENTER).valign(V.CENTER);
            x.style(col + "11").border(Edge.TOP, Line.MEDIUM);
            x.style(col + "11").border(Edge.BOTTOM, Line.MEDIUM);
        }
        // Column header vertical borders (same pattern as data)
        applyDataRowVerticalBorders(x, 11);

        // ROWS 12-30 — Data Rows
        int dataStartRow = 12;
        int dataEndRow = 30;

        for (int row = dataStartRow; row <= dataEndRow; row++) {
            int idx = row - dataStartRow;

            if (idx < items.size()) {
                Map<String, Object> ri = items.get(idx);

                x.set("A" + row, str(ri.get("code")));
                x.set("B" + row, str(ri.get("unit_of_measure")));
                x.set("C" + row, str(ri.get("name")));
                x.set("D" + row, ri.get("quantity"));

                // Stock Available?
                boolean isAvailable = num(ri.get("current_stock")) > 0;
                x.set("E" + row, isAvailable ? "✓" : "");
                x.set("F" + row, !isAvailable ? "✓" : "");

                // Issue
                int issuedQty = (int) num(ri.get("approved_quantity"));
                x.set("G" + row, issuedQty > 0 ? (Object) issuedQty : "");

                // Remarks
                String remarkText = "";
                if ("rejected".equals(ri.get("status"))) {
                    remarkText = "Rejected";
                } else if ("fulfilled".equals(ri.get("status"))) {
                    remarkText = "Fulfilled";
                } else if (issuedQty > 0 && issuedQty < num(ri.get("quantity"))) {
                    remarkText = "Partial";
                }
                x.set("H" + row, remarkText);
            }

            // Font styles for data cells
            x.style("A" + row).font(FONT).size(10);
            x.style("B" + row).font(FONT).size(10);
            x.style("C" + row).font(FONT).size(10).align(H.CENTER).valign(V.BOTTOM);
            for (String col : new String[]{"D", "E", "F", "G", "H"}) {
                x.style(col + row).font(FONT).size(10).bold().align(H.CENTER).valign(V.BOTTOM);
            }

            // Horizontal borders
            Line bottomBorder = row == dataEndRow ? Line.MEDIUM : Line.THIN;
            for (String col : AH) {
                x.style(col + row).border(Edge.BOTTOM, bottomBorder);
            }

            // Vertical borders
            applyDataRowVerticalBorders(x, row);
        }

        // ROW 31 — Near-invisible separator
        x.style("A31").border(Edge.LEFT, Line.MEDIUM);
        x.style("H31").border(Edge.RIGHT, Line.THIN);

        // ROWS 32-34 — Purpose Section
        x.set("A32", "   Purpose:");
        x.style("A32").font(FONT).size(12);

        String purpose = str(request.get("purpose"));
        List<String> purposeLines = splitTextToLines(purpose, 90, 3);

        for (int row = 32; row <= 34; row++) {
            x.merge("B" + row + ":H" + row);
            int lineIdx = row - 32;
            if (lineIdx < purposeLines.size()) {
                x.set("B" + row, purposeLines.get(lineIdx));
            }

            // Borders
            x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("H" + row).border(Edge.RIGHT, Line.MEDIUM);

            if (row == 32) {
                for (String col : AH) {
                    x.style(col + "32").border(Edge.TOP, Line.MEDIUM);
                }
                x.style("B32").border(Edge.BOTTOM, Line.THIN);
            } else if (row == 33) {
                x.style("B33").border(Edge.TOP, Line.THIN);
                x.style("B33").border(Edge.BOTTOM, Line.THIN);
            } else {
                for (String col : AH) {
                    x.style(col + "34").border(Edge.BOTTOM, Line.MEDIUM);
                }
            }
        }

        // ROW 35 — Signature Labels
        x.set("C35", "Requested by:");
        x.style("C35").font(FONT).size(12).bold().valign(V.CENTER);

        x.merge("D35:E35");
        x.set("D35", "Approved by:");
        x.style("D35").font(FONT).size(12).bold().align(H.LEFT).valign(V.CENTER);

        x.merge("F35:G35");
        x.set("F35", "Issued by:");
        x.style("F35").font(FONT).size(12).bold().align(H.LEFT).valign(V.CENTER);

        x.set("H35", "Received by:");
        x.style("H35").font(FONT).size(12).bold().valign(V.CENTER);

        // Row 35 borders
        for (String col : AH) {
            x.style(col + "35").border(Edge.TOP, Line.MEDIUM);
        }
        x.style("A35").border(Edge.LEFT, Line.MEDIUM);
        x.style("B35").border(Edge.RIGHT, Line.THIN);
        x.style("C35").border(Edge.LEFT, Line.THIN);
        x.style("D35").border(Edge.LEFT, Line.THIN);
        x.style("E35").border(Edge.RIGHT, Line.THIN);
        x.style("F35").border(Edge.LEFT, Line.THIN);
        x.style("G35").border(Edge.RIGHT, Line.THIN);
        x.style("H35").border(Edge.RIGHT, Line.MEDIUM);

        // ROWS 36-39 — Signature Detail Lines
        String requesterName = str(request.get("requester_name"));

        String[] signatureLabels = {"Signature :", "Printed Name :", "Designation :", "Date :"};

        for (int row = 36; row <= 39; row++) {
            x.height(row, row == 36 ? 22.5 : 22.65);

            // Label in column A
            x.set("A" + row, signatureLabels[row - 36]);
            x.style("A" + row).font(FONT).size(12);

            // Merge D:E and F:G
            x.merge("D" + row + ":E" + row);
            x.merge("F" + row + ":G" + row);

            // Font for data cells
            for (String col : new String[]{"B", "C", "D", "F", "H"}) {
                x.style(col + row).font(FONT).size(10).bold().align(H.CENTER).valign(V.BOTTOM);
            }

            // Horizontal borders
            Line bottomBorder = row == 39 ? Line.MEDIUM : Line.THIN;
            for (String col : AH) {
                x.style(col + row).border(Edge.BOTTOM, bottomBorder);
            }

            // Vertical borders
            x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("B" + row).border(Edge.RIGHT, Line.THIN);
            x.style("C" + row).border(Edge.LEFT, Line.THIN);
            x.style("D" + row).border(Edge.LEFT, Line.THIN);
            x.style("E" + row).border(Edge.RIGHT, Line.THIN);
            x.style("F" + row).border(Edge.LEFT, Line.THIN);
            x.style("G" + row).border(Edge.RIGHT, Line.THIN);
            x.style("H" + row).border(Edge.RIGHT, Line.MEDIUM);

            // Fill printed name
            if (row == 37) {
                x.set("C" + row, requesterName); // Requested by
            }

            // Fill date
            if (row == 39) {
                Object date = request.get("request_date");
                String dateStr = date instanceof Date d ? d.toLocalDate().format(MDY) : "";
                x.set("C" + row, dateStr);
            }
        }

        return x.bytes();
    }

    private static void sectionStyle(Xlsx x, String range) {
        x.style(range).font(FONT).size(14).bold().italic().align(H.CENTER).valign(V.CENTER);
    }

    /** The vertical border pattern for data rows (and header row 11). */
    private static void applyDataRowVerticalBorders(Xlsx x, int row) {
        // A: left=medium, right=thin
        x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("A" + row).border(Edge.RIGHT, Line.THIN);
        // B: left=thin, right=thin
        x.style("B" + row).border(Edge.LEFT, Line.THIN);
        x.style("B" + row).border(Edge.RIGHT, Line.THIN);
        // C: left=thin (no right)
        x.style("C" + row).border(Edge.LEFT, Line.THIN);
        // D: left=thin, right=medium
        x.style("D" + row).border(Edge.LEFT, Line.THIN);
        x.style("D" + row).border(Edge.RIGHT, Line.MEDIUM);
        // E: left=medium, right=thin
        x.style("E" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("E" + row).border(Edge.RIGHT, Line.THIN);
        // F: left=thin, right=medium
        x.style("F" + row).border(Edge.LEFT, Line.THIN);
        x.style("F" + row).border(Edge.RIGHT, Line.MEDIUM);
        // G: left=medium, right=thin
        x.style("G" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("G" + row).border(Edge.RIGHT, Line.THIN);
        // H: left=thin, right=medium
        x.style("H" + row).border(Edge.LEFT, Line.THIN);
        x.style("H" + row).border(Edge.RIGHT, Line.MEDIUM);
    }

    /** Word-wraps the purpose onto at most {@code maxLines} lines of {@code maxChars} bytes, as the PHP did. */
    private static List<String> splitTextToLines(String text, int maxChars, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (empty(text)) return lines;

        String currentLine = "";
        for (String word : text.split(" ", -1)) {
            if ((currentLine + " " + word).getBytes(StandardCharsets.UTF_8).length > maxChars && !empty(currentLine)) {
                lines.add(currentLine);
                currentLine = word;
                if (lines.size() >= maxLines) break;
            } else {
                currentLine = empty(currentLine) ? word : currentLine + " " + word;
            }
        }

        if (lines.size() < maxLines && !empty(currentLine)) {
            lines.add(currentLine);
        }
        return lines;
    }

    /** PHP's empty() for a string: null, "" or "0". */
    private static boolean empty(String s) {
        return s == null || s.isEmpty() || "0".equals(s);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}

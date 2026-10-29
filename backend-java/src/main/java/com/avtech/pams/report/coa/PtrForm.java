package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Property Transfer Report (COA Appendix 76) for one property transfer: the transferred property on
 * the first line, the transfer type ticked from the reason, the reason itself, and the approver,
 * releasing and receiving officers.
 */
@Component
public class PtrForm {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final JdbcTemplate jdbc;

    public PtrForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** PTR for a specific transfer. */
    public byte[] generate(long transferId, String entityName, String fundCluster) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select t.reference_number, t.transfer_date, t.transfer_reason,
                       fu.name as from_name, tu.name as to_name, ap.name as approver_name,
                       p.id as property_id, p.assigned_date, p.tag_number, p.serial_number, p.condition,
                       i.id as item_id, i.name as item_name, i.weighted_average_cost
                from property_transfers t
                left join users fu on fu.id = t.from_user
                left join users tu on tu.id = t.to_user
                left join users ap on ap.id = t.approved_by
                left join properties p on p.id = t.property_id
                left join items i on i.id = p.item_id
                where t.id = ?""", transferId);
        if (found.isEmpty()) throw ApiException.notFound("Property transfer");
        Map<String, Object> transfer = found.get(0);

        Xlsx x = new Xlsx("PTR", FONT, 11);

        // ── Page Setup ──
        x.page().paper(Xlsx.A4).portrait().scale(79);
        x.page().top(1.0).bottom(0.5).left(1.19).right(0.26).header(0).footer(0.5);

        // ── Column Widths ──
        x.width("A", 14.89);
        x.width("B", 4.33);
        x.width("C", 13.89);
        x.width("D", 4.89);
        x.width("E", 2.66);
        x.width("F", 27.11);
        x.width("G", 2.33);
        x.width("H", 12.44);
        x.width("I", 14.55);

        // ── Row Heights ──
        x.height(1, 18);
        // Row 2 auto
        x.height(3, 17.4);
        x.height(4, 15);
        x.height(5, 15);
        x.height(6, 13.8);
        x.height(7, 6);
        x.height(8, 20.25);
        x.height(9, 13.8);
        x.height(10, 7.95);
        x.height(11, 13.8);
        x.height(12, 4.5);
        x.height(13, 13.8);
        x.height(14, 13.8);
        x.height(15, 4.5);
        x.height(16, 13.8);
        x.height(17, 14.4);

        // ROW 1 — Appendix 76
        x.merge("H1:I1");
        x.set("H1", "Appendix 76");
        x.style("H1").font(FONT).size(14).italic().right().vcenter();

        // ROW 3 — Title
        x.merge("A3:I3");
        x.set("A3", "PROPERTY TRANSFER REPORT");
        x.style("A3").font(FONT).size(14).bold().center().valign(V.BOTTOM);

        // ROW 5 — Spacer
        x.merge("A5:I5");

        // ROW 6 — Entity Name / Fund Cluster
        String entityValue = phpEmpty(entityName) ? "_".repeat(30) : entityName;
        x.set("A6", "Entity Name : " + entityValue);
        x.style("A6").font(FONT).size(11).bold();

        x.merge("G6:I6");
        String fundValue = phpEmpty(fundCluster) ? "_".repeat(13) : fundCluster;
        x.set("G6", "Fund Cluster : " + fundValue);
        x.style("G6").font(FONT).size(11).bold().left().valign(V.BOTTOM);

        // ROW 7 — Spacer (height 6)

        // ROWS 8-10 — From/To Accountable Officer, PTR No., Date
        String fromOfficer = str(transfer.get("from_name"));
        x.set("A8", "From Accountable Officer/Agency/Fund Cluster :  " + fromOfficer);
        x.style("A8").font(FONT).size(11);

        String ptrNo = str(transfer.get("reference_number"));
        x.set("H8", "PTR No. :  " + ptrNo);
        x.style("H8").font(FONT).size(11);

        String toOfficer = str(transfer.get("to_name"));
        x.set("A9", "To Accountable Officer/Agency/Fund Cluster : " + toOfficer);
        x.style("A9").font(FONT).size(11);

        String transferDate = mdy(transfer.get("transfer_date"));
        x.set("H9", "Date :  " + transferDate);
        x.style("H9").font(FONT).size(11);

        // ── Borders for rows 8-10 (header info box) ──
        // Row 8: top=medium
        x.style("A8").border(Edge.TOP, Line.MEDIUM);
        x.style("A8").border(Edge.LEFT, Line.MEDIUM);
        for (String col : range('B', 'G')) x.style(col + "8").border(Edge.TOP, Line.MEDIUM);
        x.style("H8").border(Edge.TOP, Line.MEDIUM);
        x.style("H8").border(Edge.LEFT, Line.MEDIUM);
        x.style("I8").border(Edge.TOP, Line.MEDIUM);
        x.style("I8").border(Edge.RIGHT, Line.MEDIUM);

        // Row 9: left, right, left on H
        x.style("A9").border(Edge.LEFT, Line.MEDIUM);
        x.style("H9").border(Edge.LEFT, Line.MEDIUM);
        x.style("I9").border(Edge.RIGHT, Line.MEDIUM);

        // Row 10: bottom=medium
        x.style("A10").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("A10").border(Edge.LEFT, Line.MEDIUM);
        for (String col : range('B', 'G')) x.style(col + "10").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("H10").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("H10").border(Edge.LEFT, Line.MEDIUM);
        x.style("I10").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I10").border(Edge.RIGHT, Line.MEDIUM);

        // ROWS 11-15 — Transfer Type section
        x.set("A11", "Transfer Type: (check only one)");
        x.style("A11").font(FONT).size(11);

        // Row 11 borders: top=medium, left=medium on A, right=medium on I
        x.style("A11").border(Edge.TOP, Line.MEDIUM);
        x.style("A11").border(Edge.LEFT, Line.MEDIUM);
        for (String col : range('B', 'H')) x.style(col + "11").border(Edge.TOP, Line.MEDIUM);
        x.style("I11").border(Edge.TOP, Line.MEDIUM);
        x.style("I11").border(Edge.RIGHT, Line.MEDIUM);

        // Row 12 spacer borders
        x.style("A12").border(Edge.LEFT, Line.MEDIUM);
        x.style("I12").border(Edge.RIGHT, Line.MEDIUM);

        // Row 13: checkbox options
        x.style("A13").border(Edge.LEFT, Line.MEDIUM);
        x.style("I13").border(Edge.RIGHT, Line.MEDIUM);

        // Determine transfer type from reason
        String reason = str(transfer.get("transfer_reason")).toLowerCase(Locale.ROOT);
        boolean isDonation = reason.contains("donat");
        boolean isRelocate = reason.contains("relocat");
        boolean isReassignment = reason.contains("reassign") || (!isDonation && !isRelocate);

        String donationCheck = isDonation ? "☑" : "☐";
        String relocateCheck = isRelocate ? "☑" : "☐";
        String reassignCheck = isReassignment ? "☑" : "☐";

        x.set("B13", donationCheck);
        x.set("C13", "Donation");
        x.style("C13").font(FONT).size(11);
        x.set("E13", relocateCheck);
        x.set("F13", "Relocate");
        x.style("F13").font(FONT).size(11);

        // Row 14: checkbox options
        x.style("A14").border(Edge.LEFT, Line.MEDIUM);
        x.style("I14").border(Edge.RIGHT, Line.MEDIUM);

        x.set("B14", reassignCheck);
        x.set("C14", "Reassignment");
        x.style("C14").font(FONT).size(11);
        x.set("E14", "☐");
        x.set("F14", "Others (Specify) " + "_".repeat(17));
        x.style("F14").font(FONT).size(11);

        // Row 15: bottom=medium
        x.style("A15").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("A15").border(Edge.LEFT, Line.MEDIUM);
        for (String col : range('B', 'H')) x.style(col + "15").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I15").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I15").border(Edge.RIGHT, Line.MEDIUM);

        // ROWS 16-17 — Table Headers

        // Date Acquired
        x.merge("A16:A17");
        x.set("A16", "Date Acquired");
        header(x, "A16:A17");
        x.style("A16").border(Edge.TOP, Line.MEDIUM);
        x.style("A16").border(Edge.LEFT, Line.MEDIUM);
        x.style("A16").border(Edge.RIGHT, Line.MEDIUM);
        x.style("A17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("A17").border(Edge.LEFT, Line.MEDIUM);
        x.style("A17").border(Edge.RIGHT, Line.MEDIUM);

        // Property No.
        x.merge("B16:C17");
        x.set("B16", "Property No.");
        header(x, "B16:C17");
        x.style("B16").border(Edge.TOP, Line.MEDIUM);
        x.style("B16").border(Edge.LEFT, Line.MEDIUM);
        x.style("C16").border(Edge.TOP, Line.MEDIUM);
        x.style("C16").border(Edge.RIGHT, Line.MEDIUM);
        x.style("B17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("B17").border(Edge.LEFT, Line.MEDIUM);
        x.style("C17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("C17").border(Edge.RIGHT, Line.MEDIUM);

        // Description
        x.merge("D16:G17");
        x.set("D16", "Description");
        header(x, "D16:G17");
        x.style("D16").border(Edge.TOP, Line.MEDIUM);
        x.style("D16").border(Edge.LEFT, Line.MEDIUM);
        x.style("E16").border(Edge.TOP, Line.MEDIUM);
        x.style("F16").border(Edge.TOP, Line.MEDIUM);
        x.style("G16").border(Edge.TOP, Line.MEDIUM);
        x.style("G16").border(Edge.RIGHT, Line.MEDIUM);
        x.style("D17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("D17").border(Edge.LEFT, Line.MEDIUM);
        x.style("E17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("F17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("G17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("G17").border(Edge.RIGHT, Line.MEDIUM);

        // Amount
        x.merge("H16:H17");
        x.set("H16", "Amount");
        header(x, "H16:H17");
        x.style("H16").border(Edge.TOP, Line.MEDIUM);
        x.style("H16").border(Edge.LEFT, Line.MEDIUM);
        x.style("H16").border(Edge.RIGHT, Line.MEDIUM);
        x.style("H17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("H17").border(Edge.LEFT, Line.MEDIUM);
        x.style("H17").border(Edge.RIGHT, Line.MEDIUM);

        // Condition of PPE
        x.merge("I16:I17");
        x.set("I16", "Condition of\nPPE");
        header(x, "I16:I17");
        x.style("I16:I17").wrap();
        x.style("I16").border(Edge.TOP, Line.MEDIUM);
        x.style("I16").border(Edge.RIGHT, Line.MEDIUM);
        x.style("I17").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I17").border(Edge.RIGHT, Line.MEDIUM);

        // ROWS 18-42 — Data Rows (25 rows)
        int dataStartRow = 18;
        int dataEndRow = 42;

        // Fill first data row with transfer data
        boolean hasItem = transfer.get("item_id") != null;

        String acquiredDate = mdy(transfer.get("assigned_date"));
        Object propertyNo = transfer.get("tag_number") != null ? transfer.get("tag_number")
                : transfer.get("serial_number") != null ? transfer.get("serial_number") : "";
        String description = hasItem ? str(transfer.get("item_name")) : "";
        BigDecimal amount = hasItem ? (BigDecimal) transfer.get("weighted_average_cost") : null;
        String condition = ucfirst(str(transfer.get("condition")));

        x.set("A" + dataStartRow, acquiredDate);
        x.set("B" + dataStartRow, propertyNo);
        x.set("D" + dataStartRow, description);
        // weighted_average_cost is a decimal:2 string in Laravel, so even "0.00" is written.
        if (amount != null) {
            x.set("H" + dataStartRow, amount.doubleValue());
            x.style("H" + dataStartRow).numberFormat("#,##0.00");
        }
        x.set("I" + dataStartRow, condition);

        // Apply borders and merge cells for all data rows
        for (int row = dataStartRow; row <= dataEndRow; row++) {
            x.height(row, row == dataEndRow ? 14.4 : 13.8);

            // Merge cells
            x.merge("B" + row + ":C" + row);
            x.merge("D" + row + ":G" + row);

            // Alignment
            x.style("B" + row).center();
            x.style("D" + row).center();

            // Determine bottom border
            Line bottomBorder = row == dataEndRow ? Line.MEDIUM : Line.THIN;
            // Determine top border
            Line topBorder = row == dataStartRow ? Line.MEDIUM : Line.THIN;

            // Column A
            x.style("A" + row).border(Edge.BOTTOM, bottomBorder);
            x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("A" + row).border(Edge.RIGHT, Line.MEDIUM);
            if (row == dataStartRow) x.style("A" + row).border(Edge.TOP, Line.MEDIUM);

            // Columns B-C (merged)
            x.style("B" + row).border(Edge.TOP, topBorder);
            x.style("B" + row).border(Edge.BOTTOM, bottomBorder);
            x.style("B" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("C" + row).border(Edge.TOP, topBorder);
            x.style("C" + row).border(Edge.BOTTOM, bottomBorder);
            x.style("C" + row).border(Edge.RIGHT, Line.MEDIUM);

            // Columns D-G (merged)
            x.style("D" + row).border(Edge.TOP, topBorder);
            x.style("D" + row).border(Edge.BOTTOM, bottomBorder);
            x.style("D" + row).border(Edge.LEFT, Line.MEDIUM);
            for (String col : new String[]{"E", "F"}) {
                x.style(col + row).border(Edge.TOP, topBorder);
                x.style(col + row).border(Edge.BOTTOM, bottomBorder);
            }
            x.style("G" + row).border(Edge.TOP, topBorder);
            x.style("G" + row).border(Edge.BOTTOM, bottomBorder);
            x.style("G" + row).border(Edge.RIGHT, Line.MEDIUM);

            // Column H
            x.style("H" + row).border(Edge.BOTTOM, bottomBorder);
            x.style("H" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("H" + row).border(Edge.RIGHT, Line.MEDIUM);

            // Column I
            x.style("I" + row).border(Edge.BOTTOM, bottomBorder);
            x.style("I" + row).border(Edge.RIGHT, Line.MEDIUM);
        }

        // ROW 43 — Reason for Transfer label
        x.height(43, 13.8);
        x.set("A43", "Reason for Transfer:");
        x.style("A43").font(FONT).size(11).bold();
        // Row 43 borders
        x.style("A43").border(Edge.TOP, Line.MEDIUM);
        x.style("A43").border(Edge.LEFT, Line.MEDIUM);
        for (String col : range('B', 'H')) x.style(col + "43").border(Edge.TOP, Line.MEDIUM);
        x.style("I43").border(Edge.TOP, Line.MEDIUM);
        x.style("I43").border(Edge.RIGHT, Line.MEDIUM);

        // ROWS 44-48 — Reason Lines
        String reasonText = str(transfer.get("transfer_reason"));
        // Split reason into lines (max 5 lines, ~75 chars each)
        List<String> reasonLines = splitReasonToLines(reasonText, 75, 5);

        for (int row = 44; row <= 48; row++) {
            x.height(row, 13.8);
            x.merge("A" + row + ":I" + row);

            int lineIdx = row - 44;
            String lineText = lineIdx < reasonLines.size() ? reasonLines.get(lineIdx) : "_".repeat(75);
            x.set("A" + row, lineText);
            x.style("A" + row).font(FONT).size(11).center().valign(V.BOTTOM);

            x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("I" + row).border(Edge.RIGHT, Line.MEDIUM);
        }

        // ROW 49 — Bottom border of reason section
        x.height(49, 13.8);
        x.merge("C49:H49");
        x.style("A49").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("A49").border(Edge.LEFT, Line.MEDIUM);
        x.style("B49").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("C49").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I49").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I49").border(Edge.RIGHT, Line.MEDIUM);

        // ROW 50 — Signature section top border
        x.height(50, 13.8);
        for (String col : range('A', 'I')) x.style(col + "50").border(Edge.TOP, Line.MEDIUM);
        x.style("A50").border(Edge.LEFT, Line.MEDIUM);
        x.style("I50").border(Edge.RIGHT, Line.MEDIUM);

        // ROW 51 — Signature Labels
        x.height(51, 13.8);
        x.style("A51").border(Edge.LEFT, Line.MEDIUM);
        x.style("I51").border(Edge.RIGHT, Line.MEDIUM);

        x.merge("B51:C51");
        x.set("B51", "Approved by:");
        x.style("B51").font(FONT).size(11).bold().left().valign(V.BOTTOM);

        x.set("F51", "Released/Issued by:");
        x.style("F51").font(FONT).size(11).bold();

        x.set("H51", "Received by:");
        x.style("H51").font(FONT).size(11).bold();

        // ROWS 52-55 — Signature Detail Lines
        String approverName = str(transfer.get("approver_name"));
        String fromName = str(transfer.get("from_name"));
        String toName = str(transfer.get("to_name"));

        Map<Integer, String> signatureLabels = new LinkedHashMap<>();
        signatureLabels.put(52, "Signature :");
        signatureLabels.put(53, "Printed Name :");
        signatureLabels.put(54, "Designation :");
        signatureLabels.put(55, "Date :");

        Map<Integer, String[]> signatureData = Map.of(
                53, new String[]{approverName, fromName, toName}); // Printed Name row

        for (Map.Entry<Integer, String> e : signatureLabels.entrySet()) {
            int row = e.getKey();
            x.height(row, 13.8);

            // Column A — label
            x.set("A" + row, e.getValue());
            x.style("A" + row).font(FONT).size(11).bold().left().valign(V.BOTTOM);
            x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);

            // Approved by column (B:D merged)
            x.merge("B" + row + ":D" + row);
            x.style("B" + row).font(FONT).size(11).bold().center().valign(V.BOTTOM);
            x.style("B" + row).border(Edge.BOTTOM, Line.THIN);
            if (row > 52) x.style("B" + row).border(Edge.TOP, Line.THIN);

            // Released/Issued by column (F)
            x.style("F" + row).left().valign(V.BOTTOM);
            x.style("F" + row).border(Edge.BOTTOM, Line.THIN);
            if (row > 52) x.style("F" + row).border(Edge.TOP, Line.THIN);

            // Received by column (H:I merged)
            x.merge("H" + row + ":I" + row);
            x.style("H" + row).center().valign(V.BOTTOM);
            x.style("H" + row).border(Edge.BOTTOM, Line.THIN);
            x.style("I" + row).border(Edge.BOTTOM, Line.THIN);
            x.style("I" + row).border(Edge.RIGHT, Line.MEDIUM);
            if (row > 52) {
                x.style("H" + row).border(Edge.TOP, Line.THIN);
                x.style("I" + row).border(Edge.TOP, Line.THIN);
            }

            // Fill printed name data
            String[] data = signatureData.get(row);
            if (data != null) {
                x.set("B" + row, data[0]); // Approved by
                x.set("F" + row, data[1]); // Released/Issued by
                x.set("H" + row, data[2]); // Received by
            }

            // Fill date
            if (row == 55 && !transferDate.isEmpty()) {
                x.set("B" + row, transferDate);
                x.set("F" + row, transferDate);
                x.set("H" + row, transferDate);
            }
        }

        // ROW 56 — Bottom Border
        x.height(56, 6);
        x.style("A56").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("A56").border(Edge.LEFT, Line.MEDIUM);
        for (String col : range('B', 'H')) x.style(col + "56").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I56").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("I56").border(Edge.RIGHT, Line.MEDIUM);

        return x.bytes();
    }

    /** PTR for all transfers of a specific property: the most recent one. */
    public byte[] generateForProperty(long propertyId, String entityName, String fundCluster) {
        List<Long> transfers = jdbc.queryForList(
                "select id from property_transfers where property_id = ? order by transfer_date desc, id desc", Long.class, propertyId);
        if (transfers.isEmpty()) throw ApiException.of(HttpStatus.NOT_FOUND, "No transfers found for this property.");

        // Export the most recent transfer
        return generate(transfers.get(0), entityName, fundCluster);
    }

    /** The table header style: bold 11pt, centred both ways. */
    private static void header(Xlsx x, String range) {
        x.style(range).font(FONT).size(11).bold().center().vcenter();
    }

    /** Split reason text into multiple lines (lengths in bytes, as PHP's strlen). */
    private static List<String> splitReasonToLines(String text, int maxChars, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (phpEmpty(text)) return lines;

        String currentLine = "";
        for (String word : text.split(" ", -1)) {
            if ((currentLine + " " + word).getBytes(StandardCharsets.UTF_8).length > maxChars && !phpEmpty(currentLine)) {
                lines.add(currentLine);
                currentLine = word;
                if (lines.size() >= maxLines) break;
            } else {
                currentLine = phpEmpty(currentLine) ? word : currentLine + " " + word;
            }
        }

        if (lines.size() < maxLines && !phpEmpty(currentLine)) lines.add(currentLine);
        return lines;
    }

    /** PHP range('B', 'G') over column letters. */
    private static List<String> range(char from, char to) {
        List<String> cols = new ArrayList<>();
        for (char c = from; c <= to; c++) cols.add(String.valueOf(c));
        return cols;
    }

    private static String mdy(Object date) {
        return date instanceof Date d ? d.toLocalDate().format(MDY) : "";
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String ucfirst(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** PHP empty() for a string: null, "" and "0". */
    private static boolean phpEmpty(String s) {
        return s == null || s.isEmpty() || "0".equals(s);
    }
}

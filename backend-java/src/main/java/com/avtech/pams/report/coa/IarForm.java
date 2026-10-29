package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inspection and Acceptance Report (COA Appendix 62) for one IAR: the accepted delivery lines (or the
 * PO lines while nothing has been inspected), the acceptance stamp, and the inspector's and supply
 * officer's signatures.
 */
@Component
public class IarForm {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter MON_D_Y = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    private static final Map<String, String> ACCEPT_LABELS = Map.of(
            "accepted", "Accepted", "partially_accepted", "Partially Accepted", "rejected", "Rejected");
    private static final String[] COLS = {"A", "B", "C", "D", "E", "F"};

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public IarForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    public byte[] generate(long iarId, String entityName, String fundCluster) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select r.iar_number, r.inspection_date, r.delivery_date, r.status, r.acceptance_status, r.accepted_at,
                       acc.name as accepter_name, po.id as po_id, po.po_number, po.order_date, s.name as supplier_name,
                       d.name as department_name
                from inspection_acceptance_reports r
                left join users acc on acc.id = r.accepted_by
                left join purchase_orders po on po.id = r.purchase_order_id and po.deleted_at is null
                left join suppliers s on s.id = po.supplier_id
                left join purchase_requests pr on pr.id = po.purchase_request_id and pr.deleted_at is null
                left join departments d on d.id = pr.department_id
                where r.id = ?""", iarId);
        if (found.isEmpty()) throw ApiException.notFound("Inspection and acceptance report");
        Map<String, Object> iar = found.get(0);
        boolean hasPo = iar.get("po_id") != null;

        Xlsx x = new Xlsx("IAR");

        // Default font
        x.defaultFont(FONT, 11);

        // ── Column widths (matching original) ───────────────────────────
        x.width("A", 15.44);
        x.width("B", 33.89);
        x.width("C", 10.89);
        x.width("D", 12.89);
        x.width("E", 10.66);
        x.width("F", 8.11);

        // ROW 1: "Appendix 62"
        x.height(1, 30);
        x.merge("D1:F1");
        x.set("D1", "Appendix 62");
        x.style("D1").font(FONT).size(14).italic().right().vcenter();

        // ROW 3: Title
        x.height(3, 20.1);
        x.merge("A3:F3");
        x.set("A3", "INSPECTION AND ACCEPTANCE REPORT");
        x.style("A3").font(FONT).size(14).bold().center();

        // ROW 4-5: spacers
        x.merge("A4:F4");
        x.height(4, 15);
        x.height(5, 15);

        // ROW 6: Entity Name / Fund Cluster
        x.height(6, 16.5);
        x.merge("A6:C6");
        String entityVal = blank(entityName) ? "_".repeat(30) : entityName;
        x.set("A6", "Entity Name : " + entityVal);
        x.style("A6").font(FONT).bold().size(11);

        x.merge("D6:F6");
        String fundVal = blank(fundCluster) ? "_".repeat(11) : fundCluster;
        x.set("D6", "Fund Cluster : " + fundVal);
        x.style("D6").font(FONT).bold().size(11);

        // ROW 7: spacer
        x.height(7, 7.5);

        // ROWS 8-11: Form fields
        String supplierName = hasPo && iar.get("supplier_name") != null ? (String) iar.get("supplier_name") : "_".repeat(15);
        String poNumber = hasPo ? (String) iar.get("po_number") : "_".repeat(11);
        String poDate = hasPo ? mdy(iar.get("order_date")) : "";
        String deptName = iar.get("department_name") != null ? (String) iar.get("department_name") : "_".repeat(30);
        String iarNumber = (String) iar.get("iar_number");
        String inspDate = mdy(iar.get("inspection_date"));
        String delivDate = mdy(iar.get("delivery_date"));

        Object[][] formRows = {
                {8, " Supplier : " + supplierName, "IAR No. : " + iarNumber, 17.4},
                {9, " PO No./Date : " + poNumber + " / " + poDate, "Date : " + inspDate, 17.4},
                {10, " Requisitioning Office/Dept. : " + deptName, "Invoice No. : " + "_".repeat(12), 17.25},
                {11, " Responsibility Center Code : " + "_".repeat(31), "Date : " + "_".repeat(17), 17.25},
        };
        for (Object[] data : formRows) {
            int row = (Integer) data[0];
            x.height(row, (Double) data[3]);
            x.merge("A" + row + ":C" + row);
            x.set("A" + row, data[1]);
            x.style("A" + row).font(FONT).size(11);
            x.set("D" + row, data[2]);
            x.style("D" + row).font(FONT).size(11);
        }

        // ROW 12: thin spacer before table
        x.height(12, 6);
        // Bottom medium border on row 12 (top of table)
        for (String c : new String[]{"A", "B", "C"}) x.style(c + "12").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("A12").border(Edge.LEFT, Line.MEDIUM);
        x.style("D12").border(Edge.BOTTOM, Line.MEDIUM).border(Edge.LEFT, Line.THIN);
        for (String c : new String[]{"E", "F"}) x.style(c + "12").border(Edge.BOTTOM, Line.MEDIUM);
        x.style("F12").border(Edge.RIGHT, Line.MEDIUM);

        // ROW 13: Table column headers
        x.height(13, 29.4);
        x.set("A13", "Stock/ \nProperty No.");
        x.merge("B13:C13");
        x.set("B13", "Description");
        x.set("D13", "Unit");
        x.merge("E13:F13");
        x.set("E13", "Quantity");

        x.style("A13:F13").font(FONT).size(11).bold().italic().center().vcenter().wrap();

        // Header borders: medium all around each group
        x.style("A13").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT);
        x.style("B13").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT);
        x.style("C13").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.RIGHT);
        x.style("D13").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT);
        x.style("E13").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.LEFT);
        x.style("F13").borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM, Edge.RIGHT);

        // DATA ROWS (14-26)
        int dataStartRow = 14;
        int dataEndRow = 26; // minimum last data row
        int dataRow = dataStartRow;

        // Accepted delivery items come from the inspection lines (quantity_accepted).
        List<Map<String, Object>> lines = jdbc.queryForList("""
                select l.description, l.unit, l.quantity_accepted, i.id as item_id, i.code, i.name, i.unit_of_measure
                from iar_items l left join items i on i.id = l.item_id
                where l.inspection_acceptance_report_id = ? order by l.id""", iarId);

        if (!lines.isEmpty()) {
            for (Map<String, Object> line : lines) {
                if (dataRow > dataEndRow) break;
                boolean item = line.get("item_id") != null; // linked catalog item, if any
                x.set("A" + dataRow, item ? str(line.get("code")) : "");
                x.merge("B" + dataRow + ":C" + dataRow);
                x.set("B" + dataRow, !phpEmpty(line.get("description")) ? line.get("description") : (item ? str(line.get("name")) : ""));
                x.set("D" + dataRow, !phpEmpty(line.get("unit")) ? line.get("unit") : (item ? str(line.get("unit_of_measure")) : ""));
                x.merge("E" + dataRow + ":F" + dataRow);
                // The IAR "quantity accepted" column reflects what actually passed inspection.
                x.set("E" + dataRow, line.get("quantity_accepted"));
                dataRow++;
            }
        } else if (hasPo) {
            // Fallback: show PO line items if delivery not yet accepted
            List<Map<String, Object>> poItems = jdbc.queryForList(
                    "select description, quantity from purchase_order_items where purchase_order_id = ? order by id", iar.get("po_id"));
            for (Map<String, Object> poItem : poItems) {
                if (dataRow > dataEndRow) break;
                x.set("A" + dataRow, "");
                x.merge("B" + dataRow + ":C" + dataRow);
                x.set("B" + dataRow, str(poItem.get("description")));
                x.set("D" + dataRow, "");
                x.merge("E" + dataRow + ":F" + dataRow);
                x.set("E" + dataRow, poItem.get("quantity") == null ? "" : poItem.get("quantity"));
                dataRow++;
            }
        }

        // Ensure merges for empty rows too
        for (int r = dataRow; r <= dataEndRow; r++) {
            x.merge("B" + r + ":C" + r);
            x.merge("E" + r + ":F" + r);
        }

        // If data exceeds row 26, adjust
        if (dataRow - 1 > dataEndRow) dataEndRow = dataRow - 1;

        // Apply borders to data rows
        for (int r = dataStartRow; r <= dataEndRow; r++) {
            x.height(r, 29.1);

            x.style("A" + r).borders(Line.THIN, Edge.TOP, Edge.BOTTOM).border(Edge.LEFT, Line.MEDIUM);
            x.style("B" + r).borders(Line.THIN, Edge.TOP, Edge.BOTTOM).border(Edge.LEFT, Line.MEDIUM);
            x.style("C" + r).borders(Line.THIN, Edge.TOP, Edge.BOTTOM).border(Edge.RIGHT, Line.MEDIUM);
            x.style("D" + r).borders(Line.THIN, Edge.TOP, Edge.BOTTOM).borders(Line.MEDIUM, Edge.LEFT, Edge.RIGHT);
            x.style("E" + r).borders(Line.THIN, Edge.TOP, Edge.BOTTOM).border(Edge.LEFT, Line.MEDIUM);
            x.style("F" + r).borders(Line.THIN, Edge.TOP, Edge.BOTTOM).border(Edge.RIGHT, Line.MEDIUM);

            // Center unit and quantity
            x.style("D" + r).center().vcenter();
            x.style("E" + r).center().vcenter();
        }

        // Last data row bottom = medium
        for (String c : COLS) x.style(c + dataEndRow).border(Edge.BOTTOM, Line.MEDIUM);

        // ROW 27: INSPECTION / ACCEPTANCE section headers
        int row27 = dataEndRow + 1;
        x.height(row27, 29.1);
        x.merge("A" + row27 + ":B" + row27);
        x.set("A" + row27, "INSPECTION");
        x.style("A" + row27).font(FONT).size(12).bold().italic().center().vcenter();

        x.merge("C" + row27 + ":F" + row27);
        x.set("C" + row27, "ACCEPTANCE");
        x.style("C" + row27).font(FONT).size(12).bold().italic().center().vcenter();

        // Row 27 borders: medium all around
        for (String c : new String[]{"A", "B"}) x.style(c + row27).borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM);
        x.style("A" + row27).border(Edge.LEFT, Line.MEDIUM);
        x.style("B" + row27).border(Edge.RIGHT, Line.MEDIUM);
        for (String c : new String[]{"C", "D", "E", "F"}) x.style(c + row27).borders(Line.MEDIUM, Edge.TOP, Edge.BOTTOM);
        x.style("C" + row27).border(Edge.LEFT, Line.MEDIUM);
        x.style("F" + row27).border(Edge.RIGHT, Line.MEDIUM);

        // ROW 28-29: Date Inspected / Date Received
        int row28 = row27 + 1;
        x.height(row28, 23.25);
        x.merge("A" + row28 + ":B" + (row28 + 1));
        x.set("A" + row28, "Date Inspected : " + inspDate);
        x.style("A" + row28).font(FONT).size(11).bold().left().vcenter();

        // Acceptance stamp (status · date · accepting officer) — from the IAR
        // acceptance workflow, shown alongside Date Received.
        String acceptText = "Date Received : " + delivDate;
        String acceptance = (String) iar.get("acceptance_status");
        if (!phpEmpty(acceptance) && !"pending".equals(acceptance)) {
            String acceptedDate = iar.get("accepted_at") instanceof Timestamp t ? t.toLocalDateTime().format(MON_D_Y) : "";
            String accepterName = (String) iar.get("accepter_name");
            acceptText += "\nAcceptance : " + ACCEPT_LABELS.getOrDefault(acceptance, acceptance)
                    + (!acceptedDate.isEmpty() ? " (" + acceptedDate + ")" : "")
                    + (!phpEmpty(accepterName) ? " — " + accepterName : "");
        }
        x.merge("C" + row28 + ":F" + (row28 + 1));
        x.set("C" + row28, acceptText);
        x.style("C" + row28).font(FONT).size(11).bold().left().vcenter().wrap();

        int row29 = row28 + 1;
        x.height(row29, 12.75);

        // Borders for rows 28-29
        x.style("A" + row28).border(Edge.TOP, Line.MEDIUM);
        x.style("B" + row28).border(Edge.TOP, Line.MEDIUM);
        x.style("C" + row28).border(Edge.TOP, Line.MEDIUM);
        x.style("F" + row28).border(Edge.TOP, Line.MEDIUM);
        for (int r : new int[]{row28, row29}) lrBorders(x, r);

        // ROW 30-33: Inspection text / Complete/Partial
        int row30 = row29 + 1;
        x.height(row30, 15.75);
        x.height(row30 + 1, 15);

        x.merge("A" + row30 + ":B" + (row30 + 2));
        x.set("A" + row30, "Inspected, verified and found in order as to quantity and specifications");
        x.style("A" + row30).font(FONT).size(12).left().vcenter().wrap();

        // Complete checkbox
        boolean isComplete = "passed".equals(iar.get("status"));
        x.merge("C" + row30 + ":F" + (row30 + 1));
        x.set("C" + row30, (isComplete ? "[X] " : "[  ] ") + "Complete");
        x.style("C" + row30).font(FONT).size(12).left().vcenter().wrap();

        int row32 = row30 + 2;
        x.height(row32, 12.75);

        // Partial checkbox
        boolean isPartial = "partial".equals(iar.get("status"));
        x.merge("C" + row32 + ":F" + (row32 + 1));
        x.set("C" + row32, (isPartial ? "[X] " : "[  ] ") + "Partial (pls. specify quantity)");
        x.style("C" + row32).font(FONT).size(12).left().vcenter().wrap();

        int row33 = row32 + 1;
        x.height(row33, 17.25);

        // Borders rows 30-33
        for (int r = row30; r <= row33; r++) lrBorders(x, r);

        // ROW 34: spacer
        int row34 = row33 + 1;
        x.height(row34, 18.75);
        x.style("A" + row34).border(Edge.LEFT, Line.MEDIUM);
        x.style("B" + row34).border(Edge.RIGHT, Line.MEDIUM);
        x.style("F" + row34).border(Edge.RIGHT, Line.MEDIUM);

        // ROW 35: Signature underlines
        int row35 = row34 + 1;
        x.height(row35, 19.5);

        x.merge("A" + row35 + ":B" + row35);
        x.set("A" + row35, "_".repeat(44));
        x.style("A" + row35).font(FONT).size(10).center();

        x.merge("C" + row35 + ":F" + row35);
        x.set("C" + row35, "_".repeat(35));
        x.style("C" + row35).font(FONT).size(10).center();

        lrBorders(x, row35);

        // ROW 36: Signature labels
        int row36 = row35 + 1;
        x.height(row36, 23.25);

        x.merge("A" + row36 + ":B" + row36);
        x.set("A" + row36, "Inspection Officer/Inspection Committee");
        x.style("A" + row36).font(FONT).size(12).center().valign(V.TOP);

        x.merge("C" + row36 + ":F" + row36);
        x.set("C" + row36, "Supply and/or Property Custodian");
        x.style("C" + row36).font(FONT).size(12).center().valign(V.TOP);

        lrBorders(x, row36);

        // Embed configured signatories' uploaded signatures on the signature lines.
        Signatories.stamp(x, "A" + row35, "A" + row35, signatories.resolve("inspector"), 28);
        Signatories.stamp(x, "C" + row35, "C" + row35, signatories.resolve("supply_officer"), 28);

        // ROW 37: Bottom border (closes form)
        int row37 = row36 + 1;
        x.height(row37, 16.5);

        for (String c : COLS) x.style(c + row37).border(Edge.BOTTOM, Line.MEDIUM);
        x.style("A" + row37).border(Edge.LEFT, Line.MEDIUM);
        x.style("B" + row37).border(Edge.RIGHT, Line.MEDIUM);
        x.style("F" + row37).border(Edge.RIGHT, Line.MEDIUM);

        // PRINT SETTINGS
        x.page().portrait().paper(Xlsx.LEGAL).fitToWidth(1).fitToHeight(0)
                .top(0.5).bottom(0.5).left(0.75).right(0.5);

        return x.bytes();
    }

    /** Outer frame for the inspection/acceptance section. */
    private static void lrBorders(Xlsx x, int row) {
        x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("B" + row).border(Edge.RIGHT, Line.MEDIUM);
        x.style("C" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("F" + row).border(Edge.RIGHT, Line.MEDIUM);
    }

    private static String mdy(Object date) {
        return date instanceof Date d ? d.toLocalDate().format(MDY) : "";
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isEmpty() || "0".equals(s);
    }

    /** PHP empty() for a string column: null, "" and "0". */
    private static boolean phpEmpty(Object v) {
        return v == null || "".equals(v) || "0".equals(v);
    }
}

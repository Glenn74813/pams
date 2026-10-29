package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.H;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.report.coa.Xlsx.V;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Property Acknowledgment Receipt — COA GAM Appendix 71, for one request. Only includes
 * non-expendable and semi-expendable items.
 */
@Component
public class ParForm {

    private static final String FONT = "Times New Roman";
    private static final String[] AF = {"A", "B", "C", "D", "E", "F"};
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final JdbcTemplate jdbc;

    public ParForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] generate(Long requestId, String entityName, String fundCluster) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select r.*, u.name as requester_name
                from requests r
                left join users u on u.id = r.requested_by
                where r.id = ?""", requestId);
        if (found.isEmpty()) throw ApiException.notFound("Request");
        Map<String, Object> request = found.get(0);

        Xlsx x = new Xlsx("PAR", FONT, 12);

        // ── Page Setup ──
        x.page().paper(Xlsx.A4).portrait().scale(65).fitToWidth(1).fitToHeight(10);
        x.page().top(1.0).bottom(0.5).left(1.25).right(1.0).header(0.5).footer(0.5);
        x.page().printArea("A1:F50");

        // ── Column Widths ──
        x.width("A", 12);
        x.width("B", 10);
        x.width("C", 40);
        x.width("D", 20);
        x.width("E", 18);
        x.width("F", 18);

        // ── Row Heights ──
        x.height(1, 18);
        x.height(2, 6);
        x.height(3, 24);
        x.height(4, 6);
        x.height(5, 6);
        x.height(6, 20);
        x.height(7, 20);
        x.height(8, 6);
        x.height(9, 30);

        for (int r = 10; r <= 43; r++) {
            x.height(r, 18);
        }

        x.height(44, 22);
        for (int r = 45; r <= 50; r++) {
            x.height(r, 20);
        }

        // ROW 1 — Appendix 71
        x.merge("A1:F1");
        x.set("A1", "Appendix 71");
        x.style("A1").font(FONT).size(12).italic().align(H.RIGHT).valign(V.CENTER);

        // ROW 3 — Title
        x.merge("A3:F3");
        x.set("A3", "PROPERTY ACKNOWLEDGMENT RECEIPT");
        x.style("A3").font(FONT).size(14).bold().align(H.CENTER).valign(V.CENTER);

        // ROW 6 — Entity Name
        String entityValue = empty(entityName) ? "__________________________________" : entityName;
        x.merge("A6:F6");
        x.set("A6", "Entity Name : " + entityValue);
        x.style("A6").font(FONT).size(12).bold();

        // ROW 7 — Fund Cluster & PAR No.
        String fundValue = empty(fundCluster) ? "______________________" : fundCluster;
        x.merge("A7:C7");
        x.set("A7", "Fund Cluster : " + fundValue);
        x.style("A7").font(FONT).size(12).bold();

        Object parNo = request.get("par_number") != null ? request.get("par_number") : request.get("request_number");
        x.merge("D7:F7");
        x.set("D7", "PAR No. : " + str(parNo));
        x.style("D7").font(FONT).size(12).bold().align(H.RIGHT);

        // ROW 9 — Column Headers
        String[] colHeaders = {"Quantity", "Unit", "Description", "Property Number", "Date Acquired", "Amount"};
        for (int i = 0; i < AF.length; i++) {
            String col = AF[i];
            x.set(col + "9", colHeaders[i]);
            x.style(col + "9").font(FONT).size(12).bold().align(H.CENTER).valign(V.CENTER).wrap();
            // Top and bottom borders for header
            x.style(col + "9").border(Edge.TOP, Line.MEDIUM);
            x.style(col + "9").border(Edge.BOTTOM, Line.MEDIUM);
        }
        // Vertical borders for header
        applyRowVerticalBorders(x, 9);

        // ROWS 10-43 — Data Rows
        // Filter only non-expendable and semi-expendable items; the item's first recorded property
        // supplies the property number, acquisition date (its stock lot's received date first) and cost.
        List<Map<String, Object>> parItems = jdbc.queryForList("""
                select ri.quantity, ri.approved_quantity, i.name, i.description, i.unit_of_measure, i.weighted_average_cost,
                       p.id as property_id, p.serial_number, p.tag_number, p.acquisition_cost,
                       coalesce(s.received_date, p.acquisition_date) as acquisition_date
                from request_items ri
                join items i on i.id = ri.item_id
                left join properties p on p.id = (select min(pp.id) from properties pp where pp.item_id = i.id)
                left join stocks s on s.id = p.stock_id
                where ri.request_id = ? and i.is_expandable = ?
                order by ri.id""", requestId, false);

        int dataStartRow = 10;
        int dataEndRow = 43;

        for (int row = dataStartRow; row <= dataEndRow; row++) {
            int idx = row - dataStartRow;

            if (idx < parItems.size()) {
                Map<String, Object> ri = parItems.get(idx);
                boolean hasProperty = ri.get("property_id") != null;

                // Quantity
                int qty = ((Number) (ri.get("approved_quantity") != null ? ri.get("approved_quantity") : ri.get("quantity"))).intValue();
                x.set("A" + row, qty);

                // Unit
                x.set("B" + row, str(ri.get("unit_of_measure")));

                // Description
                String description = str(ri.get("name"));
                String itemDescription = (String) ri.get("description");
                if (!empty(itemDescription)) {
                    description += "\n" + itemDescription;
                }
                x.set("C" + row, description);
                x.style("C" + row).wrap();

                // Property Number — look for assigned property
                String propertyNumber = "";
                if (hasProperty) {
                    propertyNumber = str(ri.get("serial_number") != null ? ri.get("serial_number") : ri.get("tag_number"));
                }
                x.set("D" + row, propertyNumber);

                // Date Acquired
                String dateAcquired = "";
                if (hasProperty && ri.get("acquisition_date") instanceof Date d) {
                    dateAcquired = d.toLocalDate().format(MDY);
                } else if (request.get("request_date") instanceof Date d) {
                    dateAcquired = d.toLocalDate().format(MDY);
                }
                x.set("E" + row, dateAcquired);

                // Amount
                BigDecimal amount = BigDecimal.ZERO;
                BigDecimal cost = (BigDecimal) ri.get("acquisition_cost");
                BigDecimal wac = (BigDecimal) ri.get("weighted_average_cost");
                if (hasProperty && cost != null && cost.signum() > 0) {
                    amount = cost.multiply(BigDecimal.valueOf(qty));
                } else if (wac != null && wac.signum() > 0) {
                    amount = wac.multiply(BigDecimal.valueOf(qty));
                }
                if (amount.signum() > 0) {
                    x.set("F" + row, amount);
                    x.style("F" + row).numberFormat("#,##0.00");
                }
            }

            // Font styles for data cells
            for (String col : new String[]{"A", "B", "D", "E"}) {
                x.style(col + row).font(FONT).size(10).align(H.CENTER).valign(V.CENTER);
            }
            x.style("C" + row).font(FONT).size(10).align(H.LEFT).valign(V.CENTER).wrap();
            x.style("F" + row).font(FONT).size(10).align(H.RIGHT).valign(V.CENTER);

            // Horizontal borders
            Line bottomBorder = row == dataEndRow ? Line.MEDIUM : Line.THIN;
            for (String col : AF) {
                x.style(col + row).border(Edge.BOTTOM, bottomBorder);
            }

            // Vertical borders
            applyRowVerticalBorders(x, row);
        }

        // ROW 44 — Received by / Issued by
        x.merge("A44:C44");
        x.set("A44", "Received by:");
        x.style("A44").font(FONT).size(12).bold().valign(V.CENTER);

        x.merge("D44:F44");
        x.set("D44", "Issued by:");
        x.style("D44").font(FONT).size(12).bold().valign(V.CENTER);

        // Borders for row 44
        for (String col : AF) {
            x.style(col + "44").border(Edge.TOP, Line.MEDIUM);
        }
        x.style("A44").border(Edge.LEFT, Line.MEDIUM);
        x.style("C44").border(Edge.RIGHT, Line.MEDIUM);
        x.style("D44").border(Edge.LEFT, Line.MEDIUM);
        x.style("F44").border(Edge.RIGHT, Line.MEDIUM);

        // ROWS 45-50 — Signature Blocks
        String[] signatureLabels = {"Signature :", "Printed Name :", "Position :", "Date :"};

        Object requesterName = request.get("employee_name") != null ? request.get("employee_name") : str(request.get("requester_name"));
        String requesterPosition = str(request.get("position"));

        for (int row = 45; row <= 48; row++) {
            String label = signatureLabels[row - 45];

            // Left side (Received by - End User)
            x.set("A" + row, label);
            x.style("A" + row).font(FONT).size(10).valign(V.BOTTOM);

            // Merge B-C for signature value (left side)
            x.merge("B" + row + ":C" + row);
            x.style("B" + row).font(FONT).size(10).bold().align(H.CENTER).valign(V.BOTTOM);

            // Right side (Issued by - Supply/Property Custodian)
            x.set("D" + row, label);
            x.style("D" + row).font(FONT).size(10).valign(V.BOTTOM);

            // Merge E-F for signature value (right side)
            x.merge("E" + row + ":F" + row);
            x.style("E" + row).font(FONT).size(10).bold().align(H.CENTER).valign(V.BOTTOM);

            // Borders
            Line bottomBorder = row == 48 ? Line.MEDIUM : Line.THIN;
            for (String col : AF) {
                x.style(col + row).border(Edge.BOTTOM, bottomBorder);
            }
            x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("C" + row).border(Edge.RIGHT, Line.MEDIUM);
            x.style("D" + row).border(Edge.LEFT, Line.MEDIUM);
            x.style("F" + row).border(Edge.RIGHT, Line.MEDIUM);

            // Fill data
            if (row == 46) {
                // Printed Name
                x.set("B" + row, requesterName);
            }
            if (row == 47) {
                // Position
                x.set("B" + row, requesterPosition);
            }
            if (row == 48) {
                // Date
                String dateStr = request.get("par_date") instanceof Date d ? d.toLocalDate().format(MDY)
                        : request.get("request_date") instanceof Date d ? d.toLocalDate().format(MDY) : "";
                x.set("B" + row, dateStr);
            }
        }

        return x.bytes();
    }

    /** The vertical border pattern for data rows. */
    private static void applyRowVerticalBorders(Xlsx x, int row) {
        x.style("A" + row).border(Edge.LEFT, Line.MEDIUM);
        x.style("A" + row).border(Edge.RIGHT, Line.THIN);
        x.style("B" + row).border(Edge.LEFT, Line.THIN);
        x.style("B" + row).border(Edge.RIGHT, Line.THIN);
        x.style("C" + row).border(Edge.LEFT, Line.THIN);
        x.style("C" + row).border(Edge.RIGHT, Line.THIN);
        x.style("D" + row).border(Edge.LEFT, Line.THIN);
        x.style("D" + row).border(Edge.RIGHT, Line.THIN);
        x.style("E" + row).border(Edge.LEFT, Line.THIN);
        x.style("E" + row).border(Edge.RIGHT, Line.THIN);
        x.style("F" + row).border(Edge.LEFT, Line.THIN);
        x.style("F" + row).border(Edge.RIGHT, Line.MEDIUM);
    }

    /** PHP's empty() for a string: null, "" or "0". */
    private static boolean empty(String s) {
        return s == null || s.isEmpty() || "0".equals(s);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}

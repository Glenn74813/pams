package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Property Card (COA Appendix 69) for one PPE asset — acquisition, transfers,
 * and disposal, with receipt/transfer/balance columns and a depreciation header.
 */
@Component
public class PropertyCardForm {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public PropertyCardForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    /** One movement line of the card; null in a cost column means PHP's '' (cell left unset). */
    private record Movement(String date, String ref, Object rQty, Double unitCost, Double totalCost,
                            Object tQty, String office, int bal, double amount, String remarks) {
    }

    public byte[] generate(Long propertyId, String entityName, String fundCluster) {
        // Property::with(['item.category', 'assignedUser']); acquisition_date is the linked stock lot's received_date when set.
        List<Map<String, Object>> found = jdbc.queryForList("""
                select p.id, p.tag_number, p.serial_number, p.useful_life_years, p.accumulated_depreciation, p.current_book_value,
                       p.acquisition_cost, p.condition, p.status, coalesce(s.received_date, p.acquisition_date) as acquisition_date,
                       i.name as item_name, c.name as category_name, u.name as assigned_name
                from properties p
                left join stocks s on s.id = p.stock_id
                left join items i on i.id = p.item_id
                left join categories c on c.id = i.category_id
                left join users u on u.id = p.assigned_to
                where p.id = ?
                """, propertyId);
        if (found.isEmpty()) throw ApiException.notFound("Property");
        Map<String, Object> p = found.get(0);

        Xlsx x = new Xlsx("Property Card");
        x.defaultFont(FONT, 10);

        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        Map<String, Double> colWidths = new LinkedHashMap<>();
        colWidths.put("A", 12.0);
        colWidths.put("B", 16.0);
        colWidths.put("C", 8.0);
        colWidths.put("D", 14.0);
        colWidths.put("E", 14.0);
        colWidths.put("F", 8.0);
        colWidths.put("G", 20.0);
        colWidths.put("H", 8.0);
        colWidths.put("I", 14.0);
        colWidths.put("J", 18.0);
        colWidths.forEach(x::width);

        // Appendix 69
        x.set("J1", "Appendix 69");
        x.style("J1").size(9).italic();
        x.style("J1").right();

        // Title
        x.merge("A3:J3");
        x.set("A3", "PROPERTY CARD");
        x.style("A3").size(14).bold();
        x.style("A3").center();

        // Entity / Fund Cluster
        x.set("A5", "Entity Name: " + RpciForm.or(entityName, "___________________________________"));
        x.set("G5", "Fund Cluster: " + RpciForm.or(fundCluster, "_________________"));

        // Asset identity
        String assignedName = (String) p.get("assigned_name");
        String serial = (String) p.get("serial_number");
        Number usefulLife = (Number) p.get("useful_life_years");
        x.set("A6", "Property, Plant and Equipment: " + str(p.get("item_name")));
        x.set("G6", "Property Number: " + str(p.get("tag_number")));
        x.set("A7", "Description: " + str(p.get("category_name")) + (truthy(serial) ? "  (S/N: " + serial + ")" : ""));
        x.set("G7", "Est. Useful Life: " + (usefulLife != null && usefulLife.intValue() != 0 ? usefulLife.intValue() + " yrs" : "-"));
        x.set("A8", "Accountable Officer: " + (assignedName != null ? assignedName : "-"));
        x.set("G8", "Accum. Depreciation: ₱" + numberFormat(dbl(p.get("accumulated_depreciation"))));
        x.set("A9", "Condition: " + ucfirst(p.get("condition") != null ? (String) p.get("condition") : "-")
                + " · Status: " + ucfirst(p.get("status") != null ? (String) p.get("status") : "-"));
        // decimal:2 cast: "0.00" is truthy in PHP, so only a null book value falls back to the cost.
        x.set("G9", "Book Value: ₱" + numberFormat(dbl(p.get("current_book_value") != null ? p.get("current_book_value") : p.get("acquisition_cost"))));
        x.style("A5:J9").size(10);

        // Column headers (rows 11-12)
        x.set("A11", "Date");
        x.set("B11", "Reference");
        x.set("C11", "Receipt");
        x.set("F11", "Transfer / Issue");
        x.set("H11", "Balance");
        x.set("I11", "Amount");
        x.set("J11", "Remarks");
        x.set("C12", "Qty");
        x.set("D12", "Unit Cost");
        x.set("E12", "Total Cost");
        x.set("F12", "Qty");
        x.set("G12", "Office / Officer");
        x.set("H12", "Qty");

        x.merge("A11:A12");
        x.merge("B11:B12");
        x.merge("C11:E11");
        x.merge("F11:G11");
        x.merge("H11:H12");
        x.merge("I11:I12");
        x.merge("J11:J12");

        x.style("A11:J12").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        // Build movement rows
        List<Movement> rows = new ArrayList<>();
        double acqCost = dbl(p.get("acquisition_cost"));
        Object acqDate = p.get("acquisition_date");
        if (acqDate != null || acqCost != 0) {
            rows.add(new Movement(mdy(acqDate), "Acquisition", 1, acqCost, acqCost,
                    "", assignedName != null ? assignedName : "", 1, acqCost, ""));
        }
        List<Map<String, Object>> transfers = jdbc.queryForList("""
                select t.transfer_date, t.reference_number, t.transfer_reason, tu.name as to_name
                from property_transfers t
                left join users tu on tu.id = t.to_user
                where t.property_id = ?
                order by t.transfer_date, t.id
                """, propertyId);
        for (Map<String, Object> t : transfers) {
            String refNo = (String) t.get("reference_number");
            String reason = (String) t.get("transfer_reason");
            rows.add(new Movement(mdy(t.get("transfer_date")), truthy(refNo) ? refNo : "Transfer",
                    "", null, null, 1, t.get("to_name") != null ? (String) t.get("to_name") : "",
                    1, acqCost, truthy(reason) ? reason : ""));
        }
        String status = (String) p.get("status");
        if ("disposed".equals(status) || "retired".equals(status)) {
            rows.add(new Movement("", ucfirst(status), "", null, null, 1, "", 0, 0, "Asset " + status));
        }

        int row = 13;
        for (Movement r : rows) {
            x.set("A" + row, r.date());
            x.set("B" + row, r.ref());
            x.set("C" + row, r.rQty());
            if (r.unitCost() != null) { x.set("D" + row, r.unitCost()); x.style("D" + row).numberFormat("#,##0.00"); }
            if (r.totalCost() != null) { x.set("E" + row, r.totalCost()); x.style("E" + row).numberFormat("#,##0.00"); }
            x.set("F" + row, r.tQty());
            x.set("G" + row, r.office());
            x.set("H" + row, r.bal());
            x.set("I" + row, r.amount());
            x.style("I" + row).numberFormat("#,##0.00");
            x.set("J" + row, r.remarks());
            x.style("A" + row + ":J" + row).border(Edge.ALL, Line.THIN);
            x.style("A" + row + ":C" + row).center();
            x.style("F" + row + ":H" + row).center();
            row++;
        }
        // Pad empty rows
        for (int i = rows.size(); i < 12; i++) {
            x.style("A" + row + ":J" + row).border(Edge.ALL, Line.THIN);
            row++;
        }

        // Signature — embed configured signatories' uploaded signatures per role.
        row += 2;
        x.set("A" + row, "Verified by:");
        x.set("G" + row, "Certified Correct:");
        int imgRow = row + 1;        // signature image sits above the name line
        int nameRow = row + 3;
        Signatories.stamp(x, "A" + imgRow, "A" + nameRow, signatories.resolve("property_officer"));
        Signatories.stamp(x, "G" + imgRow, "G" + nameRow, signatories.resolve("head_of_agency"));
        row = nameRow + 1;
        x.set("A" + row, "Property/Supply Officer");
        x.set("G" + row, "Head of Agency / Authorized Representative");
        x.style("A" + row + ":J" + row).size(8).italic();

        return x.bytes();
    }

    static String mdy(Object date) {
        if (date == null) return null;
        LocalDate d = date instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) date;
        return d.format(MDY);
    }

    /** PHP number_format($n, 2). */
    static String numberFormat(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    static String ucfirst(String s) {
        return s == null || s.isEmpty() ? (s == null ? "" : s) : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static double dbl(Object v) {
        return v == null ? 0 : ((Number) v).doubleValue();
    }

    static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    static boolean truthy(String s) {
        return s != null && !s.isEmpty() && !s.equals("0");
    }
}

package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inventory Transfer Report (ITR) — COA Circular 2022-004, Annex A.5 — for one transfer of
 * semi-expendable property between accountable officers; the semi-expendable counterpart of the
 * Property Transfer Report (Appendix 76), which covers PPE.
 */
@Component
public class ItrForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;

    public ItrForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public byte[] generate(long transferId, String entityName, String fundCluster) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select t.reference_number, t.transfer_date, t.transfer_reason, t.transfer_type,
                       fu.name as from_name, tu.name as to_name, ap.name as approver_name,
                       p.tag_number, p.serial_number, p.condition, p.acquisition_cost, p.accountability_number, p.assigned_date,
                       coalesce(s.received_date, p.acquisition_date) as acquisition_date,
                       i.name as item_name, i.description, i.is_semi_expandable
                from property_transfers t
                left join users fu on fu.id = t.from_user
                left join users tu on tu.id = t.to_user
                left join users ap on ap.id = t.approved_by
                join properties p on p.id = t.property_id
                left join stocks s on s.id = p.stock_id
                left join items i on i.id = p.item_id
                where t.id = ?""", transferId);
        if (found.isEmpty()) throw ApiException.notFound("Property transfer");
        Map<String, Object> t = found.get(0);
        Object semi = t.get("is_semi_expandable");
        if (!Boolean.TRUE.equals(semi) && !Integer.valueOf(1).equals(semi)) {
            throw ApiException.of(HttpStatus.UNPROCESSABLE_CONTENT, "This transfer is of PPE, not semi-expendable property; use the Property Transfer Report (Appendix 76).");
        }

        Xlsx x = new Xlsx("ITR", FONT, 11);
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.75).bottom(0.5).left(0.75).right(0.5);

        // A Date Acquired | B Item No. | C ICS No./Date | D Description | E Amount | F Condition of Inventory
        double[] widths = {13, 16, 18, 34, 13, 16};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.set("F1", "Annex A.5");
        x.style("F1").size(10).italic().right();
        x.merge("A3:F3");
        x.set("A3", "INVENTORY TRANSFER REPORT");
        x.style("A3").size(14).bold().center();

        x.merge("A5:D5");
        x.set("A5", "Entity Name : " + RpciForm.or(entityName, "______________________________"));
        x.merge("E5:F5");
        x.set("E5", "Fund Cluster : " + RpciForm.or(fundCluster, "_____________"));
        x.style("A5:F5").bold();

        x.merge("A7:D7");
        x.set("A7", "From Accountable Officer/Agency/Fund Cluster : " + ItemMovements.str(t.get("from_name")));
        x.merge("E7:F7");
        x.set("E7", "ITR No. : " + ItemMovements.str(t.get("reference_number")));
        x.merge("A8:D8");
        x.set("A8", "To Accountable Officer/Agency/Fund Cluster : " + ItemMovements.str(t.get("to_name")));
        x.merge("E8:F8");
        x.set("E8", "Date : " + RpciForm.or(PropertyCardForm.mdy(t.get("transfer_date")), ""));
        x.style("A7:F8").border(Edge.OUTLINE, Line.THIN);

        // Transfer type, ticked from the recorded type and reason.
        String reason = ItemMovements.str(t.get("transfer_reason"));
        String type = ItemMovements.str(t.get("transfer_type"));
        String r = (reason + " " + type).toLowerCase(Locale.ROOT);
        String kind = r.contains("donat") ? "donation" : r.contains("relocat") ? "relocate" : "reissue".equals(type) || r.contains("reassign") ? "reassignment" : "others";
        x.set("A10", "Transfer Type: (check only one)");
        x.style("A10").bold();
        x.set("A11", box("donation".equals(kind)) + " Donation");
        x.set("C11", box("relocate".equals(kind)) + " Relocate");
        x.set("A12", box("reassignment".equals(kind)) + " Reassignment");
        x.set("C12", box("others".equals(kind)) + " Others (Specify) " + ("others".equals(kind) && !type.isEmpty() ? "_" + type + "_" : "________________"));

        x.set("A14", "Date Acquired");
        x.set("B14", "Item No.");
        x.set("C14", "ICS No./Date");
        x.set("D14", "Description");
        x.set("E14", "Amount");
        x.set("F14", "Condition of Inventory");
        x.style("A14:F14").bold().size(10).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        x.set("A15", PropertyCardForm.mdy(t.get("acquisition_date")));
        x.set("B15", ItemMovements.str(t.get("tag_number")));
        String ics = ItemMovements.str(t.get("accountability_number"));
        String icsDate = PropertyCardForm.mdy(t.get("assigned_date"));
        x.set("C15", ics.isEmpty() ? "" : ics + (icsDate == null ? "" : " / " + icsDate));
        String serial = ItemMovements.str(t.get("serial_number"));
        String desc = ItemMovements.str(t.get("item_name")) + (serial.isEmpty() ? "" : " (S/N: " + serial + ")");
        String more = ItemMovements.str(t.get("description"));
        x.set("D15", more.isEmpty() ? desc : desc + "\n" + more);
        x.set("E15", ItemMovements.money(t.get("acquisition_cost")));
        x.set("F15", PropertyCardForm.ucfirst(ItemMovements.str(t.get("condition"))));
        int last = 26;
        for (int row = 15; row <= last; row++) {
            x.style("A" + row + ":F" + row).border(Edge.LEFT, Line.THIN).border(Edge.RIGHT, Line.THIN).border(Edge.VERTICAL, Line.THIN).vcenter();
        }
        x.style("A" + last + ":F" + last).border(Edge.BOTTOM, Line.THIN);
        x.style("D15").wrap();
        x.style("A15:C15").center();
        x.style("E15").numberFormat("#,##0.00");

        int row = last + 1;
        x.merge("A" + row + ":F" + (row + 2));
        x.set("A" + row, "Reason for Transfer: " + reason);
        x.style("A" + row + ":F" + (row + 2)).wrap().valign(Xlsx.V.TOP).border(Edge.OUTLINE, Line.THIN);

        row += 4;
        x.set("B" + row, "Approved by:");
        x.set("D" + row, "Released/Issued by:");
        x.set("E" + row, "Received by:");
        x.style("A" + row + ":F" + row).bold();
        String[] labels = {"Signature :", "Printed Name :", "Designation :", "Date :"};
        for (int i = 0; i < labels.length; i++) x.set("A" + (row + 1 + i), labels[i]);
        x.set("B" + (row + 2), ItemMovements.str(t.get("approver_name")));
        x.set("D" + (row + 2), ItemMovements.str(t.get("from_name")));
        x.set("E" + (row + 2), ItemMovements.str(t.get("to_name")));
        x.style("B" + (row + 2) + ":F" + (row + 2)).bold();
        x.style("A" + (row + 1) + ":F" + (row + 4)).size(10);
        return x.bytes();
    }

    private static String box(boolean on) {
        return on ? "[X]" : "[  ]";
    }
}

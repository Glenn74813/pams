package com.avtech.pams.clearance;

import com.avtech.pams.report.coa.Signatories;
import com.avtech.pams.report.coa.Xlsx;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Certificate of Property Turnover and Clearance (agency format; COA prescribes none): certifies that
 * the employee has turned over every property under their PAR/ICS, and lists what they returned or
 * transferred while the clearance was open.
 */
@Component
public class ClearanceCertificateForm {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public ClearanceCertificateForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    public byte[] generate(Long id, String agency) {
        Map<String, Object> c = jdbc.queryForMap("""
                select c.*, u.name as employee, d.name as department, r.name as role_name
                from clearance_certificates c join users u on u.id = c.user_id
                left join departments d on d.id = u.department_id
                left join roles r on r.id = u.role_id
                where c.id = ?""", id);
        LocalDate issued = ((Timestamp) c.get("issued_at")).toLocalDateTime().toLocalDate();
        LocalDate opened = ((Timestamp) c.get("created_at")).toLocalDateTime().toLocalDate();

        Xlsx x = new Xlsx("Clearance", FONT, 11);
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0).margins(0.75, 0.75, 0.75, 0.75);
        x.width("A", 6).width("B", 18).width("C", 34).width("D", 16).width("E", 16);

        x.merge("A1:E1");
        x.set("A1", agency == null || agency.isBlank() ? "Entity Name" : agency);
        x.style("A1").bold().size(12).center();
        x.merge("A3:E3");
        x.set("A3", "CERTIFICATE OF PROPERTY TURNOVER AND CLEARANCE");
        x.style("A3").bold().size(14).center();
        x.merge("A4:E4");
        x.set("A4", "No. " + c.get("certificate_number"));
        x.style("A4").center();

        String purpose = ClearanceController.PURPOSE_LABELS.getOrDefault((String) c.get("purpose"), (String) c.get("purpose"));
        if (c.get("purpose_details") != null) purpose = purpose + " (" + c.get("purpose_details") + ")";
        String effective = c.get("effective_date") instanceof Date d ? " effective " + d.toLocalDate().format(LONG) : "";
        x.merge("A6:E9");
        x.set("A6", "This is to certify that " + c.get("employee")
                + (c.get("role_name") != null ? ", " + c.get("role_name") : "")
                + (c.get("department") != null ? " of the " + c.get("department") : "")
                + ", has turned over to the Property/Supply Office all government property issued under Property Acknowledgment Receipts"
                + " and Inventory Custodian Slips, and is hereby CLEARED of property and supply accountability as of " + issued.format(LONG) + ".");
        x.style("A6").wrap().valign(Xlsx.V.TOP).align(Xlsx.H.JUSTIFY);
        x.height(6, 18).height(7, 18).height(8, 18).height(9, 18);
        x.merge("A10:E10");
        x.set("A10", "This certification is issued for: " + purpose + effective + ".");
        x.style("A10").wrap();

        x.set("A12", "Property turned over since " + opened.format(LONG) + ":");
        x.style("A12").bold();
        x.set("A13", "No.");
        x.set("B13", "Property No.");
        x.set("C13", "Description");
        x.set("D13", "Date");
        x.set("E13", "Turned over to");
        x.style("A13:E13").bold().center().border(Edge.ALL, Line.THIN);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select t.transfer_date, t.transfer_type, p.tag_number, i.name as item_name, tu.name as to_name
                from property_transfers t join properties p on p.id = t.property_id join items i on i.id = p.item_id
                left join users tu on tu.id = t.to_user
                where t.from_user = ? and t.transfer_type in ('return', 'transfer') and t.transfer_date >= ?
                order by t.transfer_date, t.id""", c.get("user_id"), Date.valueOf(opened));
        int row = 14;
        int n = 1;
        for (Map<String, Object> r : rows) {
            x.set("A" + row, n++);
            x.set("B" + row, r.get("tag_number"));
            x.set("C" + row, r.get("item_name"));
            x.set("D" + row, ((Date) r.get("transfer_date")).toLocalDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
            x.set("E" + row, "return".equals(r.get("transfer_type")) ? "Property Office" : r.get("to_name"));
            x.style("A" + row + ":E" + row).border(Edge.ALL, Line.THIN);
            x.style("A" + row).center();
            row++;
        }
        if (rows.isEmpty()) {
            x.merge("A" + row + ":E" + row);
            x.set("A" + row, "No property was held at the time the clearance was opened.");
            x.style("A" + row + ":E" + row).italic().center().border(Edge.ALL, Line.THIN);
            row++;
        }

        row += 2;
        x.set("A" + row, "Certified by:");
        x.set("D" + row, "Noted by:");
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve("property_officer"));
        Signatories.stamp(x, "D" + (row + 1), "D" + (row + 3), signatories.resolve("head_of_agency"));
        x.set("A" + (row + 4), "Property/Supply Officer");
        x.set("D" + (row + 4), "Head of Agency / Authorized Representative");
        x.style("A" + (row + 4) + ":E" + (row + 4)).italic().size(9);
        x.set("A" + (row + 6), "Date issued: " + issued.format(LONG));
        return x.bytes();
    }
}

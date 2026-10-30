package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Certificate of Non-Availability of Stock (CNAS) in the PS-DBM format, for the Agency Procurement
 * Request lines PS-DBM could not supply under one CNAS number — the basis for buying them through
 * another procurement mode. Valid for 15 days from its date, as the PS-DBM module tracks.
 */
@Component
public class CnasForm {

    private static final String FONT = "Arial";
    /** Same validity as PsDbmController.CNAS_DAYS. */
    private static final int VALID_DAYS = 15;

    private final JdbcTemplate jdbc;

    public CnasForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The CNAS number of the line, for the file name. */
    public String number(long lineId) {
        List<String> no = jdbc.queryForList("select coalesce(cnas_number, cast(id as varchar(20))) from ps_order_items where id = ?", String.class, lineId);
        if (no.isEmpty()) throw ApiException.notFound("PS-DBM order line");
        return no.get(0);
    }

    public byte[] generate(long lineId, String agencyName) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select oi.ps_order_id, oi.availability, oi.cnas_number, oi.cnas_date, o.apr_number, o.submitted_date, o.created_at,
                       pr.pr_number, d.name as department_name
                from ps_order_items oi join ps_orders o on o.id = oi.ps_order_id
                left join purchase_requests pr on pr.id = o.purchase_request_id left join departments d on d.id = pr.department_id
                where oi.id = ?""", lineId);
        if (found.isEmpty()) throw ApiException.notFound("PS-DBM order line");
        Map<String, Object> head = found.get(0);
        if (!"not_available".equals(head.get("availability"))) {
            throw ApiException.of(HttpStatus.UNPROCESSABLE_CONTENT, "This line is available at PS-DBM; a CNAS is issued only for lines marked not available.");
        }
        List<Map<String, Object>> lines = jdbc.queryForList("""
                select oi.description, oi.unit, oi.quantity, oi.unit_price, oi.amount, c.code
                from ps_order_items oi left join ps_catalogue_items c on c.id = oi.ps_catalogue_item_id
                where oi.ps_order_id = ? and oi.availability = 'not_available' and coalesce(oi.cnas_number, '') = ?
                order by oi.id""", head.get("ps_order_id"), ItemMovements.str(head.get("cnas_number")));

        Xlsx x = new Xlsx("CNAS", FONT, 11);
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.75).bottom(0.5).left(0.75).right(0.6);
        // A Item No. | B Item Code | C Item Description | D Unit | E Quantity | F Remarks
        double[] widths = {8, 16, 42, 9, 10, 20};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.merge("A1:F1");
        x.set("A1", "Republic of the Philippines");
        x.merge("A2:F2");
        x.set("A2", "DEPARTMENT OF BUDGET AND MANAGEMENT");
        x.merge("A3:F3");
        x.set("A3", "PROCUREMENT SERVICE");
        x.style("A1:F3").center();
        x.style("A2:F3").bold();
        x.merge("A5:F5");
        x.set("A5", "CERTIFICATE OF NON-AVAILABILITY OF STOCK");
        x.style("A5").size(14).bold().center();

        LocalDate date = ItemMovements.date(head.get("cnas_date"));
        x.set("A7", "CNAS No.: " + ItemMovements.str(head.get("cnas_number")));
        x.set("E7", "Date: " + RpciForm.or(PropertyCardForm.mdy(date), "____________"));
        x.set("A8", "Agency: " + RpciForm.or(agencyName, "______________________________"));
        x.set("E8", "APR No.: " + ItemMovements.str(head.get("apr_number")));
        x.set("A9", "End-User / Office: " + ItemMovements.str(head.get("department_name")));
        x.set("E9", "PR No.: " + ItemMovements.str(head.get("pr_number")));
        x.style("A7:F9").bold();

        LocalDate aprDate = head.get("submitted_date") != null ? ItemMovements.date(head.get("submitted_date")) : ItemMovements.date(head.get("created_at"));
        x.merge("A11:F13");
        x.set("A11", "This is to certify that the following common-use supplies and equipment requested by the above agency under Agency "
                + "Procurement Request (APR) No. " + ItemMovements.str(head.get("apr_number")) + (aprDate == null ? "" : " dated " + PropertyCardForm.mdy(aprDate))
                + " are NOT AVAILABLE in the PS-DBM depots/warehouses as of the date of this certificate:");
        x.style("A11").wrap().valign(Xlsx.V.TOP);

        String[] heads = {"Item No.", "Item Code", "Item Description", "Unit", "Quantity", "Remarks"};
        for (int i = 0; i < heads.length; i++) x.set(Xlsx.col(i + 1) + "15", heads[i]);
        x.style("A15:F15").bold().size(10).center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        int row = 16;
        int n = 1;
        for (Map<String, Object> l : lines) {
            x.set("A" + row, n++);
            x.set("B" + row, ItemMovements.str(l.get("code")));
            x.set("C" + row, ItemMovements.str(l.get("description")));
            x.set("D" + row, ItemMovements.str(l.get("unit")));
            x.set("E" + row, l.get("quantity"));
            x.set("F" + row, "Not available");
            x.style("A" + row + ":F" + row).border(Edge.ALL, Line.THIN).vcenter();
            x.style("A" + row).center();
            x.style("C" + row).wrap();
            x.style("D" + row + ":E" + row).center();
            row++;
        }
        for (int i = lines.size(); i < 8; i++) {
            x.style("A" + row + ":F" + row).border(Edge.ALL, Line.THIN);
            row++;
        }
        x.merge("A" + row + ":F" + row);
        x.set("A" + row, "*** nothing follows ***");
        x.style("A" + row).italic().center().size(9);

        row += 2;
        x.merge("A" + row + ":F" + (row + 2));
        x.set("A" + row, "This certification is issued to allow the agency to procure the above item(s) through other modes of procurement under "
                + "R.A. No. 12009 and its IRR. It is valid for " + VALID_DAYS + " calendar days from the date of issue"
                + (date == null ? "." : ", until " + PropertyCardForm.mdy(date.plusDays(VALID_DAYS)) + "."));
        x.style("A" + row).wrap().valign(Xlsx.V.TOP);

        row += 5;
        x.set("D" + row, "Certified by:");
        x.set("D" + (row + 3), "________________________________");
        x.set("D" + (row + 4), "Authorized Officer, Procurement Service - DBM");
        x.style("D" + (row + 4)).size(9).italic();
        return x.bytes();
    }
}

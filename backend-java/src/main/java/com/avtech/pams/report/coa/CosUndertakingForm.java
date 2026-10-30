package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Letter of Undertaking of a Contract of Service (COS) worker — the agency's letter in which a
 * non-plantilla worker issued government property accepts accountability for it and undertakes to
 * return it at the end of the contract. Lists the assets (PAR) and outstanding ICS items held.
 */
@Component
public class CosUndertakingForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public CosUndertakingForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    public byte[] generate(long userId, String entityName, String position, LocalDate contractEnd) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select u.name, d.name as department_name from users u left join departments d on d.id = u.department_id where u.id = ?""", userId);
        if (found.isEmpty()) throw ApiException.notFound("User");
        Map<String, Object> u = found.get(0);
        String name = ItemMovements.str(u.get("name"));
        String office = ItemMovements.str(u.get("department_name"));

        // Assets on the worker's PAR/ICS, then semi-expendable items still outstanding on their ICS.
        List<Map<String, Object>> assets = jdbc.queryForList("""
                select p.tag_number as ref, p.accountability_number as doc, i.name, p.serial_number, 1 as qty, p.acquisition_cost as unit_cost, p.assigned_date as issued
                from properties p left join items i on i.id = p.item_id
                where p.assigned_to = ? and p.status = 'assigned' order by p.assigned_date, p.tag_number""", userId);
        List<Map<String, Object>> semi = jdbc.queryForList("""
                select i.code as ref, s.ics_number as doc, i.name, s.quantity - coalesce(s.quantity_returned, 0) as qty,
                       coalesce(ri.unit_cost, i.weighted_average_cost) as unit_cost, s.issued_date as issued
                from semi_expendable_issues s join items i on i.id = s.item_id
                left join request_items ri on ri.id = (select min(x.id) from request_items x where x.request_id = s.request_id and x.item_id = s.item_id)
                where s.issued_to = ? and s.quantity > coalesce(s.quantity_returned, 0) order by s.issued_date, s.id""", userId);

        Xlsx x = new Xlsx("Undertaking", FONT, 11);
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.75).bottom(0.6).left(0.9).right(0.75);
        // A No. | B Property/ICS No. | C Description | D Qty | E Amount | F Date Issued
        double[] widths = {6, 22, 40, 7, 14, 13};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.merge("A1:F1");
        x.set("A1", RpciForm.or(entityName, "______________________________"));
        x.style("A1").bold().center().size(13);
        x.merge("A3:F3");
        x.set("A3", "LETTER OF UNDERTAKING");
        x.style("A3").bold().center().size(14);
        x.merge("A4:F4");
        x.set("A4", "(Contract of Service Personnel)");
        x.style("A4").italic().center();

        x.set("A6", PropertyCardForm.mdy(LocalDate.now(Times.ZONE)));
        x.set("A8", RpciForm.nameOr(signatories.resolve("head_of_agency"), "THE HEAD OF AGENCY"));
        x.style("A8").bold();
        x.set("A9", "Head of Agency");
        x.set("A10", RpciForm.or(entityName, ""));
        x.set("A12", "Sir/Madam:");

        String pos = position == null || position.isBlank() ? "Contract of Service worker" : position.trim();
        x.merge("A14:F17");
        x.set("A14", "I, " + name + ", " + pos + (office.isEmpty() ? "" : " assigned to the " + office) + ", engaged under a Contract of Service"
                + (contractEnd == null ? "" : " ending on " + PropertyCardForm.mdy(contractEnd))
                + ", acknowledge receipt of the government property listed below, issued to me for the performance of my work. "
                + "I understand that, not being a plantilla employee, I am not a regular accountable officer, and I voluntarily undertake to:");
        x.style("A14").wrap().valign(Xlsx.V.TOP).align(Xlsx.H.JUSTIFY);
        String[] terms = {
                "1. use the property solely for official purposes and take good care of it;",
                "2. report any loss, theft or damage immediately to the Property/Supply Office;",
                "3. be personally liable for its loss or damage due to my fault or negligence; and",
                "4. return all of it in good condition to the Property/Supply Office upon the end or termination of my contract, "
                        + "before my clearance is processed and my final payment is released."};
        int row = 18;
        for (String t : terms) {
            x.merge("A" + row + ":F" + row);
            x.set("A" + row, t);
            x.style("A" + row).wrap();
            x.height(row, t.length() > 95 ? 30 : 15);
            row++;
        }

        row++;
        String[] heads = {"No.", "Property / ICS No.", "Description", "Qty.", "Amount", "Date Issued"};
        for (int i = 0; i < heads.length; i++) x.set(Xlsx.col(i + 1) + row, heads[i]);
        x.style("A" + row + ":F" + row).bold().size(10).center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        row++;
        int n = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (List<Map<String, Object>> list : List.of(assets, semi)) {
            for (Map<String, Object> a : list) {
                int qty = ((Number) a.get("qty")).intValue();
                BigDecimal amount = ItemMovements.money(a.get("unit_cost")).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                total = total.add(amount);
                String doc = ItemMovements.str(a.get("doc"));
                String serial = ItemMovements.str(a.get("serial_number"));
                x.set("A" + row, n++);
                x.set("B" + row, ItemMovements.str(a.get("ref")) + (doc.isEmpty() ? "" : "\n" + doc));
                x.set("C" + row, ItemMovements.str(a.get("name")) + (serial.isEmpty() ? "" : " (S/N: " + serial + ")"));
                x.set("D" + row, qty);
                x.set("E" + row, amount);
                x.set("F" + row, PropertyCardForm.mdy(a.get("issued")));
                x.style("A" + row + ":F" + row).border(Edge.ALL, Line.THIN).vcenter().size(10);
                x.style("B" + row + ":C" + row).wrap();
                x.style("A" + row).center();
                x.style("D" + row).center();
                x.style("F" + row).center();
                x.style("E" + row).numberFormat("#,##0.00");
                row++;
            }
        }
        if (n == 1) {
            x.merge("A" + row + ":F" + row);
            x.set("A" + row, "No property is currently issued to this person.");
            x.style("A" + row + ":F" + row).italic().center().border(Edge.ALL, Line.THIN);
            row++;
        }
        x.merge("A" + row + ":D" + row);
        x.set("A" + row, "TOTAL");
        x.set("E" + row, total);
        x.style("A" + row + ":F" + row).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + row).right();
        x.style("E" + row).numberFormat("#,##0.00");

        row += 3;
        x.set("D" + row, "Very truly yours,");
        x.set("D" + (row + 3), name);
        x.style("D" + (row + 3)).bold();
        x.set("D" + (row + 4), "Signature over Printed Name (COS Worker)");
        x.set("D" + (row + 5), "Date: ________________");
        x.style("D" + (row + 4)).size(9).italic();

        row += 7;
        x.set("A" + row, "Noted by:");
        x.set("D" + row, "Approved:");
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve("property_officer"));
        Signatories.stamp(x, "D" + (row + 1), "D" + (row + 3), signatories.resolve("head_of_agency"));
        x.set("A" + (row + 4), "Property/Supply Officer");
        x.set("D" + (row + 4), "Head of Agency / Authorized Representative");
        x.style("A" + (row + 4) + ":F" + (row + 4)).size(8).italic();
        return x.bytes();
    }
}

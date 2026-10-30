package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Invoice Receipt for Property (IRP) — the classic COA form acknowledging property issued to an
 * officer — for one issuance: either a request's non-expendable lines (as on its PAR/ICS) or the
 * assets registered under one accountability (PAR/ICS) number.
 */
@Component
public class IrpForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public IrpForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    private record Item(int qty, String unit, String description, String propertyNo, BigDecimal unitCost) {
    }

    /** The IRP number printed on the form: the accountability number, else the request's PAR/ICS/request number. */
    public String number(Long requestId, String parNumber) {
        if (parNumber != null) return parNumber;
        List<String> no = jdbc.queryForList("select coalesce(par_number, ics_number, request_number) from requests where id = ?", String.class, requestId);
        if (no.isEmpty()) throw ApiException.notFound("Request");
        return no.get(0);
    }

    public byte[] generate(Long requestId, String parNumber, String entityName, String fundCluster) {
        List<Item> items = new ArrayList<>();
        String receivedBy;
        String office;
        Object date;
        if (parNumber != null) {
            List<Map<String, Object>> props = jdbc.queryForList("""
                    select p.tag_number, p.serial_number, p.acquisition_cost, p.assigned_date, i.name, i.unit_of_measure,
                           u.name as holder, d.name as department_name
                    from properties p left join items i on i.id = p.item_id left join users u on u.id = p.assigned_to
                    left join departments d on d.id = u.department_id
                    where p.accountability_number = ? order by p.tag_number, p.id""", parNumber);
            if (props.isEmpty()) throw ApiException.notFound("Accountability number");
            for (Map<String, Object> p : props) {
                String serial = ItemMovements.str(p.get("serial_number"));
                items.add(new Item(1, RpciForm.or((String) p.get("unit_of_measure"), "unit"),
                        ItemMovements.str(p.get("name")) + (serial.isEmpty() ? "" : " (S/N: " + serial + ")"),
                        ItemMovements.str(p.get("tag_number")), ItemMovements.money(p.get("acquisition_cost"))));
            }
            receivedBy = ItemMovements.str(props.get(0).get("holder"));
            office = ItemMovements.str(props.get(0).get("department_name"));
            date = props.get(0).get("assigned_date");
        } else {
            List<Map<String, Object>> found = jdbc.queryForList("""
                    select r.request_date, r.par_date, r.ics_date, u.name as requester_name, d.name as department_name
                    from requests r left join users u on u.id = r.requested_by left join departments d on d.id = r.department_id
                    where r.id = ?""", requestId);
            if (found.isEmpty()) throw ApiException.notFound("Request");
            Map<String, Object> r = found.get(0);
            // Property lines only (PPE, flagged is_expandable, and semi-expendable); consumables go on the RIS.
            for (Map<String, Object> ri : jdbc.queryForList("""
                    select coalesce(ri.approved_quantity, ri.quantity) as qty, i.name, i.code, i.unit_of_measure,
                           coalesce(ri.unit_cost, i.weighted_average_cost) as unit_cost
                    from request_items ri join items i on i.id = ri.item_id
                    where ri.request_id = ? and (i.is_expandable = ? or i.is_semi_expandable = ?) order by ri.id""", requestId, true, true)) {
                items.add(new Item(((Number) ri.get("qty")).intValue(), ItemMovements.str(ri.get("unit_of_measure")), ItemMovements.str(ri.get("name")),
                        ItemMovements.str(ri.get("code")), ItemMovements.money(ri.get("unit_cost"))));
            }
            receivedBy = ItemMovements.str(r.get("requester_name"));
            office = ItemMovements.str(r.get("department_name"));
            date = r.get("par_date") != null ? r.get("par_date") : r.get("ics_date") != null ? r.get("ics_date") : r.get("request_date");
        }

        Xlsx x = new Xlsx("IRP", FONT, 11);
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.75).bottom(0.5).left(0.75).right(0.5);

        // A Quantity | B Unit | C Description | D Property No. | E Unit Cost | F Total Cost
        double[] widths = {10, 10, 40, 18, 14, 15};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.merge("A2:F2");
        x.set("A2", "INVOICE RECEIPT FOR PROPERTY");
        x.style("A2").size(14).bold().center();
        x.merge("A3:F3");
        x.set("A3", RpciForm.or(entityName, "______________________________"));
        x.style("A3").center().bold();
        x.merge("A4:F4");
        x.set("A4", "(Agency)");
        x.style("A4").center().italic().size(9);

        x.set("A6", "Fund Cluster : " + RpciForm.or(fundCluster, "_____________"));
        x.set("E6", "IRP No. : " + number(requestId, parNumber));
        x.set("A7", "Office : " + RpciForm.or(office, "_____________"));
        x.set("E7", "Date : " + RpciForm.or(PropertyCardForm.mdy(date), "____________"));
        x.style("A6:F7").bold();

        x.merge("A9:F9");
        x.set("A9", "Received from the Property/Supply Officer the following property for which I am accountable:");
        x.style("A9").italic();

        String[] heads = {"Quantity", "Unit", "Description", "Property No.", "Unit Cost", "Total Cost"};
        for (int i = 0; i < heads.length; i++) x.set(Xlsx.col(i + 1) + "11", heads[i]);
        x.style("A11:F11").bold().size(10).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        int row = 12;
        BigDecimal total = BigDecimal.ZERO;
        for (Item it : items) {
            BigDecimal amount = it.unitCost().multiply(BigDecimal.valueOf(it.qty())).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            x.set("A" + row, it.qty());
            x.set("B" + row, it.unit());
            x.set("C" + row, it.description());
            x.set("D" + row, it.propertyNo());
            x.set("E" + row, it.unitCost());
            x.set("F" + row, amount);
            x.style("A" + row + ":F" + row).border(Edge.ALL, Line.THIN).vcenter();
            x.style("A" + row + ":B" + row).center();
            x.style("C" + row).wrap();
            x.style("E" + row + ":F" + row).numberFormat("#,##0.00");
            row++;
        }
        for (int i = items.size(); i < 15; i++) {
            x.style("A" + row + ":F" + row).border(Edge.ALL, Line.THIN);
            row++;
        }
        x.merge("A" + row + ":E" + row);
        x.set("A" + row, "TOTAL");
        x.set("F" + row, total);
        x.style("A" + row + ":F" + row).bold().border(Edge.ALL, Line.THIN);
        x.style("A" + row).right();
        x.style("F" + row).numberFormat("#,##0.00");

        row += 3;
        x.set("A" + row, "Issued by:");
        x.set("D" + row, "Received by:");
        x.style("A" + row + ":F" + row).bold();
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve("property_officer"));
        x.set("D" + (row + 3), receivedBy.isEmpty() ? "________________________" : receivedBy);
        x.style("D" + (row + 3)).bold().size(9);
        x.set("A" + (row + 4), "Property/Supply Officer");
        x.set("D" + (row + 4), "Signature over Printed Name of Accountable Officer");
        x.style("A" + (row + 4) + ":F" + (row + 4)).size(8).italic();
        x.set("A" + (row + 5), "Date: ________________");
        x.set("D" + (row + 5), "Date: ________________");
        return x.bytes();
    }
}

package com.avtech.pams.report.coa;

import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Waste Materials Report (WMR) — COA Appendix 65, from the selected disposal requests
 * (one line per disposed property).
 */
@Component
public class WmrForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public WmrForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    public byte[] generate(List<Long> disposalIds, String entityName, String fundCluster, String storagePlace) {
        List<Map<String, Object>> disposals = disposals(jdbc, disposalIds);

        Xlsx x = new Xlsx("WMR");
        x.defaultFont(FONT, 10);

        // Page Setup
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.75).right(0.5);

        // Column Widths
        Map<String, Double> colWidths = new LinkedHashMap<>();
        colWidths.put("A", 6.0);
        colWidths.put("B", 10.0);
        colWidths.put("C", 8.0);
        colWidths.put("D", 16.0);
        colWidths.put("E", 10.0);
        colWidths.put("F", 10.0);
        colWidths.put("G", 10.0);
        colWidths.put("H", 10.0);
        colWidths.put("I", 12.0);
        colWidths.forEach(x::width);

        // Row 1 - Appendix 65
        x.merge("G1:I1");
        x.set("G1", "Appendix 65");
        x.style("G1").size(9).italic();
        x.style("G1").right();

        // Row 4 - Title
        x.merge("A4:I4");
        x.set("A4", "WASTE MATERIALS REPORT");
        x.style("A4").size(14).bold();
        x.style("A4").center();

        // Row 7 - Entity & Fund Cluster
        x.merge("A7:D7");
        x.set("A7", "Entity Name: " + RpciForm.or(entityName, "____________________________________"));
        x.set("G7", "Fund Cluster: " + RpciForm.or(fundCluster, "_____________________"));

        // Row 8 - Place of Storage & Date
        x.set("A8", "Place of Storage: " + RpciForm.or(storagePlace, "___________________________________________"));
        x.set("G8", "Date: " + LocalDate.now(Times.ZONE).format(RpciForm.LONG_DATE));

        // Row 9 - Section Title
        x.merge("A9:I9");
        x.set("A9", "ITEMS FOR DISPOSAL");
        x.style("A9").bold();
        x.style("A9").center();

        // Row 10-12 - Column headers
        x.merge("A10:A12");
        x.set("A10", "Item");
        x.merge("B10:B12");
        x.set("B10", "Quantity");
        x.merge("C10:C12");
        x.set("C10", "Unit");
        x.merge("D10:F12");
        x.set("D10", "Description");
        x.merge("G10:I10");
        x.set("G10", "Record of Sales");
        x.merge("G11:I11");
        x.set("G11", "Official Receipt");
        x.set("G12", "No.");
        x.set("H12", "Date");
        x.set("I12", "Amount");

        x.style("A10:I12").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        // Data rows
        int row = 13;
        int itemNum = 1;
        for (Map<String, Object> disposal : disposals) {
            boolean hasProperty = disposal.get("property_id") != null;

            x.set("A" + row, itemNum);
            // One unit per asset; an inventory write-off carries its own quantity.
            x.set("B" + row, hasProperty || disposal.get("quantity") == null ? 1 : ((Number) disposal.get("quantity")).intValue());
            x.set("C" + row, disposal.get("unit_of_measure") != null ? disposal.get("unit_of_measure") : "unit");
            x.set("D" + row, (disposal.get("item_name") != null ? disposal.get("item_name") : "N/A")
                    + (hasProperty ? " (Tag: " + disposal.get("tag_number") + ")" : ""));

            x.style("A" + row + ":I" + row).border(Edge.ALL, Line.THIN).vcenter();
            x.style("A" + row).center();
            x.style("B" + row).center();
            x.style("C" + row).center();
            x.merge("D" + row + ":F" + row);

            itemNum++;
            row++;
        }

        // Fill empty rows to minimum 10
        while (itemNum <= 10) {
            x.set("A" + row, itemNum);
            x.style("A" + row + ":I" + row).border(Edge.ALL, Line.THIN);
            x.merge("D" + row + ":F" + row);
            itemNum++;
            row++;
        }

        // TOTAL row
        x.merge("D" + row + ":F" + row);
        x.set("D" + row, "TOTAL");
        x.style("D" + row).bold();
        x.style("D" + row).center();
        x.style("A" + row + ":I" + row).border(Edge.ALL, Line.THIN);
        row++;

        // Certification section
        row++;
        x.set("A" + row, "Certified Correct:");
        x.set("F" + row, "Disposal Approved:");
        int nameRow1 = row + 1; // blank line above captions for printed name + signature
        row += 2;
        x.merge("B" + row + ":D" + row);
        x.set("B" + row, "Signature over Printed Name of Supply and/or Property Custodian");
        x.style("B" + row).size(8);
        x.merge("G" + row + ":I" + row);
        x.set("G" + row, "Signature over Printed Name of Head of Agency/Entity\nor his/her Authorized Representative");
        x.style("G" + row).size(8);
        x.style("B" + row + ":I" + row).center().wrap();

        // Embed configured signatories per role.
        Signatories.stamp(x, "B" + nameRow1, "B" + nameRow1, signatories.resolve("supply_officer"), 28);
        Signatories.stamp(x, "G" + nameRow1, "G" + nameRow1, signatories.resolve("head_of_agency"), 28);

        // Certificate of Inspection
        row += 2;
        x.merge("A" + row + ":I" + row);
        x.set("A" + row, "CERTIFICATE OF INSPECTION");
        x.style("A" + row).bold().size(11);
        x.style("A" + row).center();

        row += 2;
        x.set("A" + row, "          I hereby certify that the property enumerated above was disposed of as follows:");

        row += 2;
        String[] disposalTypes = {"Destroyed", "Sold at private sale", "Sold at public auction", ""};
        for (String dtype : disposalTypes) {
            x.set("B" + row, "Item");
            x.set("C" + row, "________");
            x.set("D" + row, dtype);
            row++;
        }

        row += 2;
        x.set("A" + row, "Certified Correct:");
        x.set("F" + row, "Witness to Disposal:");
        row++;
        x.merge("B" + row + ":D" + row);
        x.set("B" + row, "Signature over Printed Name of Inspection Officer");
        x.style("B" + row).size(8);
        x.merge("G" + row + ":H" + row);
        x.set("G" + row, "Signature over Printed Name of Witness");
        x.style("G" + row).size(8);
        x.style("B" + row + ":H" + row).center();

        return x.bytes();
    }

    /**
     * DisposalRequest::with('property.item.category')->whereIn('id', $ids)->orderBy('id') (soft-deleted rows excluded):
     * one row per disposal with its property and that property's item/category columns.
     */
    static List<Map<String, Object>> disposals(JdbcTemplate jdbc, List<Long> disposalIds) {
        if (disposalIds == null || disposalIds.isEmpty()) return List.of();
        return new NamedParameterJdbcTemplate(jdbc).queryForList("""
                select d.id, d.disposal_type, d.reason, d.property_id, d.quantity, d.unit_cost,
                       p.tag_number, p.assigned_date, p.acquisition_date, p.acquisition_cost,
                       i.name as item_name, i.unit_of_measure, i.weighted_average_cost, c.name as category_name
                from disposal_requests d
                left join properties p on p.id = d.property_id
                left join items i on i.id = coalesce(p.item_id, d.item_id)
                left join categories c on c.id = i.category_id
                where d.id in (:ids) and d.deleted_at is null
                order by d.id
                """, new MapSqlParameterSource("ids", disposalIds));
    }
}

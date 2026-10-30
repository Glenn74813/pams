package com.avtech.pams.report.coa;

import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report on the Physical Count of Property, Plant and Equipment (RPCPPE) — COA Appendix 73.
 * One line per PPE asset (each property is one unit) with card vs physical count.
 */
@Component
public class RpcppeForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public RpcppeForm(JdbcTemplate jdbc, Signatories signatories) {
        this.signatories = signatories;
        this.jdbc = jdbc;
    }

    /**
     * {@code countData} is the physical count per property, keyed by property id (PHP {@code [$propertyId => $qty]});
     * without it the on-hand and shortage columns stay blank.
     */
    public byte[] generate(String ppeType, LocalDate asAtDate, String fundCluster, Map<Long, ? extends Number> countData) {
        // Get PPE properties (items that are expandable/PPE)
        String where = "semi_expandable".equals(ppeType) ? "i.is_semi_expandable = ?" : "i.is_expandable = ?";
        List<Map<String, Object>> properties = jdbc.queryForList("""
                select p.id, p.tag_number, p.condition, p.status, p.acquisition_cost,
                       i.name as item_name, i.unit_of_measure, i.weighted_average_cost, c.name as category_name
                from properties p
                join items i on i.id = p.item_id
                left join categories c on c.id = i.category_id
                where %s and p.status <> 'disposed'
                order by p.tag_number, p.id
                """.formatted(where), true);

        Xlsx x = new Xlsx("RPCPPE");
        x.defaultFont(FONT, 10);

        // Page Setup
        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        // Column Widths
        Map<String, Double> colWidths = new LinkedHashMap<>();
        colWidths.put("A", 3.0);
        colWidths.put("B", 3.0);
        colWidths.put("C", 14.0);
        colWidths.put("D", 20.0);
        colWidths.put("E", 18.0);
        colWidths.put("F", 10.0);
        colWidths.put("G", 12.0);
        colWidths.put("H", 12.0);
        colWidths.put("I", 12.0);
        colWidths.put("J", 10.0);
        colWidths.put("K", 10.0);
        colWidths.put("L", 14.0);
        colWidths.forEach(x::width);

        // Row 1 - Appendix 73
        x.merge("K1:L1");
        x.set("K1", "Appendix 73");
        x.style("K1").size(9).italic();
        x.style("K1").right();

        // Row 3 - Title
        x.merge("C3:L3");
        x.set("C3", "REPORT ON THE PHYSICAL COUNT OF PROPERTY, PLANT AND EQUIPMENT");
        x.style("C3").size(12).bold();
        x.style("C3").center();

        // Row 4-5 - Type
        String typeLabel = "semi_expandable".equals(ppeType) ? "Semi-Expendable Property" : "Property, Plant and Equipment";
        x.merge("C4:L4");
        x.set("C4", typeLabel);
        x.style("C4").center();
        x.style("C4").underline();
        x.merge("C5:L5");
        x.set("C5", "(Type of Property, Plant and Equipment)");
        x.style("C5").size(8).italic();
        x.style("C5").center();

        // Row 6 - As at date
        x.merge("C6:L6");
        x.set("C6", "As at " + (asAtDate != null ? asAtDate : LocalDate.now(Times.ZONE)).format(RpciForm.LONG_DATE));
        x.style("C6").center();

        // Row 9 - Fund Cluster
        x.set("C9", "Fund Cluster: " + RpciForm.or(fundCluster, "________________________________"));

        // Row 12-14 - Column Headers
        x.merge("C12:C14"); x.set("C12", "ARTICLE");
        x.merge("D12:D14"); x.set("D12", "DESCRIPTION");
        x.merge("E12:E14"); x.set("E12", "PROPERTY NUMBER");
        x.merge("F12:F14"); x.set("F12", "UNIT OF MEASURE");
        x.merge("G12:G14"); x.set("G12", "UNIT VALUE");

        x.set("H12", "QUANTITY");
        x.set("I12", "QUANTITY");
        x.set("H13", "per");
        x.set("I13", "per");
        x.set("H14", "PROPERTY CARD");
        x.set("I14", "PHYSICAL COUNT");

        x.merge("J12:K13");
        x.set("J12", "SHORTAGE/OVERAGE");
        x.set("J14", "Quantity");
        x.set("K14", "Value");

        x.set("L12", "REMARKS");
        x.merge("L12:L14");

        x.style("C12:L14").bold().size(8).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        // Data rows
        int row = 15;
        for (Map<String, Object> property : properties) {
            int cardQty = 1; // Each property is 1 unit
            Object onHandQty = "";
            Object shortageQty = "";
            Object shortageVal = "";
            // Each asset at its own acquisition cost; disposed assets are no longer counted.
            Object cost = property.get("acquisition_cost") != null ? property.get("acquisition_cost") : property.get("weighted_average_cost");
            double wac = cost == null ? 0 : ((Number) cost).doubleValue();

            Long id = ((Number) property.get("id")).longValue();
            if (countData != null && !countData.isEmpty() && countData.get(id) != null) {
                Number onHand = countData.get(id);
                onHandQty = onHand;
                Number diff = isIntegral(onHand) ? (Number) (onHand.longValue() - cardQty) : (Number) (onHand.doubleValue() - cardQty);
                shortageQty = diff;
                shortageVal = diff.doubleValue() * wac;
            }

            x.set("C" + row, property.get("category_name") != null ? property.get("category_name") : "");
            x.set("D" + row, property.get("item_name") != null ? property.get("item_name") : "N/A");
            x.set("E" + row, property.get("tag_number"));
            x.set("F" + row, property.get("unit_of_measure") != null ? property.get("unit_of_measure") : "unit");
            x.set("G" + row, wac);
            x.style("G" + row).numberFormat("#,##0.00");
            x.set("H" + row, cardQty);
            x.set("I" + row, onHandQty);
            x.set("J" + row, shortageQty);
            if (!"".equals(shortageVal)) {
                x.set("K" + row, shortageVal);
                x.style("K" + row).numberFormat("#,##0.00");
            }
            x.set("L" + row, property.get("condition") + " / " + property.get("status"));

            x.style("C" + row + ":L" + row).border(Edge.ALL, Line.THIN).vcenter();
            x.style("C" + row + ":E" + row).wrap();
            x.style("E" + row + ":K" + row).center();

            row++;
        }

        // Fill empty rows
        if (properties.isEmpty()) {
            for (int i = 0; i < 20; i++) {
                x.style("C" + row + ":L" + row).border(Edge.ALL, Line.THIN);
                row++;
            }
        }

        // Signature section
        row += 2;
        x.set("C" + row, "Certified Correct by:");
        x.set("F" + row, "Approved by:");
        x.merge("J" + row + ":K" + row);
        x.set("J" + row, "Verified by:");
        row += 2;
        x.merge("G" + row + ":I" + row);
        x.merge("K" + row + ":L" + row);
        // Names from Settings → Signatories on the signature lines; unset ones keep the blank line.
        String chair = signatories.resolve("inventory_committee_chair").name();
        if (chair != null) {
            x.set("D" + row, chair.toUpperCase(java.util.Locale.ROOT));
            x.style("D" + row).bold();
        }
        String head = signatories.resolve("head_of_agency").name();
        if (head != null) {
            x.set("G" + row, head.toUpperCase(java.util.Locale.ROOT));
            x.style("G" + row).bold().center();
        }
        x.set("K" + row, RpciForm.nameOr(signatories.resolve("coa_representative"), "_______________________________"));
        x.style("K" + row).center();
        row++;
        x.set("D" + row, "Signature over Printed Name of Inventory Committee Chair and Members");
        x.style("D" + row).size(7).italic();
        x.merge("G" + row + ":I" + row);
        x.set("G" + row, "Signature over Printed Name of Head of Agency/Entity\nor Authorized Representative");
        x.style("G" + row).size(7).italic();
        x.style("G" + row).center().wrap();
        x.merge("K" + row + ":L" + row);
        x.set("K" + row, "Signature over Printed Name of COA Representative");
        x.style("K" + row).size(7).italic();
        x.style("K" + row).center().wrap();

        return x.bytes();
    }

    private static boolean isIntegral(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }
}

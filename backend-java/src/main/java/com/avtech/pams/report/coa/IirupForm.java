package com.avtech.pams.report.coa;

import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory and Inspection Report of Unserviceable Property (IIRUP) — COA Appendix 74,
 * from the selected disposal requests.
 */
@Component
public class IirupForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public IirupForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    public byte[] generate(List<Long> disposalIds, String entityName, String fundCluster, String accountableOfficer, String designation, String station) {
        List<Map<String, Object>> disposals = WmrForm.disposals(jdbc, disposalIds);

        Xlsx x = new Xlsx("IIRUP");
        x.defaultFont(FONT, 9);

        // Page Setup - Landscape for wide table
        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.3).bottom(0.3).left(0.3).right(0.3);

        // Column Widths (A=margin, B-S = data columns)
        Map<String, Double> colWidths = new LinkedHashMap<>();
        colWidths.put("A", 2.0);
        colWidths.put("B", 10.0);
        colWidths.put("C", 18.0);
        colWidths.put("D", 10.0);
        colWidths.put("E", 6.0);
        colWidths.put("F", 9.0);
        colWidths.put("G", 10.0);
        colWidths.put("H", 10.0);
        colWidths.put("I", 10.0);
        colWidths.put("J", 10.0);
        colWidths.put("K", 10.0);
        colWidths.put("L", 7.0);
        colWidths.put("M", 7.0);
        colWidths.put("N", 7.0);
        colWidths.put("O", 7.0);
        colWidths.put("P", 7.0);
        colWidths.put("Q", 9.0);
        colWidths.put("R", 8.0);
        colWidths.put("S", 9.0);
        colWidths.forEach(x::width);

        // Row 1 - Appendix 74
        x.merge("Q1:S1");
        x.set("Q1", "Appendix 74");
        x.style("Q1").size(9).italic();
        x.style("Q1").right();

        // Row 3 - Title
        x.merge("B3:S3");
        x.set("B3", "INVENTORY AND INSPECTION REPORT OF UNSERVICEABLE PROPERTY");
        x.style("B3").size(12).bold();
        x.style("B3").center();

        // Row 4 - As at date
        x.merge("B4:S4");
        x.set("B4", "As at " + LocalDate.now(Times.ZONE).format(RpciForm.LONG_DATE));
        x.style("B4").center();

        // Row 6 - Entity & Fund Cluster
        x.set("B6", "Entity Name: " + RpciForm.or(entityName, "_______________________________________"));
        x.merge("P6:S6");
        x.set("P6", "Fund Cluster: " + RpciForm.or(fundCluster, "_____________________"));

        // Row 7-8 - Accountable Officer
        x.set("B7", RpciForm.or(accountableOfficer, "______________________________"));
        x.set("F7", RpciForm.or(designation, "______________________________"));
        x.set("K7", RpciForm.or(station, "______________________________"));
        x.set("B8", "(Name of Accountable Officer)");
        x.set("F8", "(Designation)");
        x.set("K8", "(Station)");
        x.style("B8:K8").size(7).italic();

        // Row 10 - Section headers
        x.merge("B10:K10");
        x.set("B10", "INVENTORY");
        x.style("B10").bold();
        x.style("B10").center();
        x.merge("L10:S10");
        x.set("L10", "INSPECTION and DISPOSAL");
        x.style("L10").bold();
        x.style("L10").center();
        x.style("B10:S10").border(Edge.ALL, Line.THIN);

        // Row 11-13 - Column headers
        x.set("B11", "Date\nAcquired");
        x.set("C11", "Particulars/\nArticles");
        x.set("D11", "Property\nNo.");
        x.set("E11", "Qty");
        x.set("F11", "Unit\nCost");
        x.set("G11", "Total\nCost");
        x.set("H11", "Accumulated\nDepreciation");
        x.set("I11", "Accumulated\nImpairment Losses");
        x.set("J11", "Carrying\nAmount");
        x.set("K11", "Remarks");
        x.set("L11", "DISPOSAL");
        x.set("Q11", "Appraised\nValue");
        x.set("R11", "RECORD OF SALES");

        // Disposal sub-headers (row 12)
        x.set("L12", "Sale");
        x.set("M12", "Transfer");
        x.set("N12", "Destruction");
        x.set("O12", "Others\n(Specify)");
        x.set("P12", "Total");
        x.set("R12", "OR No.");
        x.set("S12", "Amount");

        // Row 14 - Column numbers
        for (int c = Xlsx.colIndex("B"); c <= Xlsx.colIndex("S"); c++) {
            x.set(Xlsx.col(c) + "14", "(" + (c - 1) + ")");
        }

        // Merge multi-row headers
        for (String m : new String[]{"B11:B13", "C11:C13", "D11:D13", "E11:E13", "F11:F13", "G11:G13",
                "H11:H13", "I11:I13", "J11:J13", "K11:K13", "L11:P11", "Q11:Q13", "R11:S11"}) {
            x.merge(m);
        }
        // Sub-header merges
        for (String m : new String[]{"L12:L13", "M12:M13", "N12:N13", "O12:O13", "P12:P13", "R12:R13", "S12:S13"}) {
            x.merge(m);
        }

        x.style("B11:S14").bold().size(7).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        // Data rows
        int row = 15;
        for (Map<String, Object> disposal : disposals) {
            double unitCost = disposal.get("weighted_average_cost") == null ? 0 : ((Number) disposal.get("weighted_average_cost")).doubleValue();

            x.set("B" + row, disposal.get("assigned_date") != null ? PropertyCardForm.mdy(disposal.get("assigned_date")) : "");
            x.set("C" + row, disposal.get("item_name") != null ? disposal.get("item_name") : "N/A");
            x.set("D" + row, disposal.get("tag_number") != null ? disposal.get("tag_number") : "");
            x.set("E" + row, 1);
            x.set("F" + row, unitCost);
            x.set("G" + row, unitCost);
            x.style("F" + row + ":G" + row).numberFormat("#,##0.00");
            String reason = (String) disposal.get("reason");
            x.set("K" + row, PropertyCardForm.truthy(reason) ? substrBytes(reason, 30) : "");

            // Disposal type column
            String dtype = (String) disposal.get("disposal_type");
            if ("sale".equals(dtype)) x.set("L" + row, "✓");
            else if ("transfer".equals(dtype)) x.set("M" + row, "✓");
            else if ("destruction".equals(dtype) || "condemnation".equals(dtype)) x.set("N" + row, "✓");
            else x.set("O" + row, "✓");

            x.style("B" + row + ":S" + row).border(Edge.ALL, Line.THIN).vcenter();
            x.style("D" + row + ":S" + row).center();

            row++;
        }

        // Fill empty rows
        if (disposals.isEmpty()) {
            for (int i = 0; i < 15; i++) {
                x.style("B" + row + ":S" + row).border(Edge.ALL, Line.THIN);
                row++;
            }
        }

        // Certification text
        row += 2;
        x.merge("B" + row + ":K" + row);
        x.set("B" + row, "     I HEREBY request inspection and disposition, pursuant to Section 79 of PD 1445, of the property enumerated above.");
        x.style("B" + row).size(8);
        x.style("B" + row).wrap();
        x.merge("L" + row + ":O" + row);
        x.set("L" + row, "     I CERTIFY that I have inspected each and every article enumerated in this report, and that the disposition made thereof was, in my judgment, the best for the public interest.");
        x.style("L" + row).size(8);
        x.style("L" + row).wrap();
        x.merge("Q" + row + ":S" + row);
        x.set("Q" + row, "    I CERTIFY that I have witnessed the disposition of the articles enumerated on this report this ____day of _____________, _____.");
        x.style("Q" + row).size(8);
        x.style("Q" + row).wrap();

        // Signature section
        row += 4;
        x.set("B" + row, "Requested by:");
        x.set("G" + row, "Approved by:");
        int nameRow = row + 1; // blank line above captions — printed name + signature image
        row += 2;
        x.merge("C" + row + ":E" + row);
        x.set("C" + row, "(Signature over Printed Name of Accountable Officer)");
        x.style("C" + row).size(7).italic();
        x.style("C" + row).center();
        x.merge("H" + row + ":J" + row);
        x.set("H" + row, "(Signature over Printed Name of Authorized Official)");
        x.style("H" + row).size(7).italic();
        x.style("H" + row).center();
        x.merge("L" + row + ":O" + row);
        x.set("L" + row, "(Signature over Printed Name of Inspection Officer)");
        x.style("L" + row).size(7).italic();
        x.style("L" + row).center();
        x.merge("Q" + row + ":S" + row);
        x.set("Q" + row, "(Signature over Printed Name of Witness)");
        x.style("Q" + row).size(7).italic();
        x.style("Q" + row).center();

        // Embed configured signatories per role.
        Signatories.stamp(x, "C" + nameRow, "C" + nameRow, signatories.resolve("property_officer"), 28);
        Signatories.stamp(x, "H" + nameRow, "H" + nameRow, signatories.resolve("head_of_agency"), 28);
        Signatories.stamp(x, "L" + nameRow, "L" + nameRow, signatories.resolve("inspector"), 28);

        return x.bytes();
    }

    /** PHP {@code substr($s, 0, $n)}: the first n bytes, without splitting a UTF-8 character. */
    static String substrBytes(String s, int n) {
        if (s.getBytes(StandardCharsets.UTF_8).length <= n) return s;
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + len > n) break;
            out.appendCodePoint(cp);
            bytes += len;
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}

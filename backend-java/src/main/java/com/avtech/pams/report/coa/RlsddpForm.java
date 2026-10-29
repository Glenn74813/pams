package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report of Lost, Stolen, Damaged or Destroyed Property (RLSDDP) — COA Appendix 75, for one asset.
 */
@Component
public class RlsddpForm {

    private static final String FONT = "Times New Roman";
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final JdbcTemplate jdbc;
    private final Signatories signatories;

    public RlsddpForm(JdbcTemplate jdbc, Signatories signatories) {
        this.jdbc = jdbc;
        this.signatories = signatories;
    }

    /**
     * {@code status} is lost, stolen, damaged or destroyed. The three dates are printed exactly as given
     * (PHP concatenates the request strings), so they stay strings.
     */
    public byte[] generate(Long propertyId, String status, String circumstances, String entityName, String fundCluster,
                           String department, String rlsddpNumber, String rlsddpDate, String parNumber, String parDate,
                           Boolean policeNotified, String policeStation, String policeDate) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select p.id, p.tag_number, p.serial_number, i.name as item_name, i.weighted_average_cost, u.name as assigned_name
                from properties p
                left join items i on i.id = p.item_id
                left join users u on u.id = p.assigned_to
                where p.id = ?
                """, propertyId);
        if (found.isEmpty()) throw ApiException.notFound("Property");
        Map<String, Object> property = found.get(0);

        Xlsx x = new Xlsx("RLSDDP");
        x.defaultFont(FONT, 10);

        // Page Setup
        x.page().paper(Xlsx.A4).portrait().fitToWidth(1).fitToHeight(0);
        x.page().top(0.75).bottom(0.5).left(0.75).right(0.5);

        // Column Widths
        Map<String, Double> colWidths = new LinkedHashMap<>();
        colWidths.put("A", 2.0);
        colWidths.put("B", 14.0);
        colWidths.put("C", 8.0);
        colWidths.put("D", 14.0);
        colWidths.put("E", 8.0);
        colWidths.put("F", 12.0);
        colWidths.put("G", 12.0);
        colWidths.put("H", 12.0);
        colWidths.forEach(x::width);

        // Row 1 - Appendix 75
        x.merge("G1:H1");
        x.set("G1", "Appendix 75");
        x.style("G1").size(9).italic();
        x.style("G1").right();

        // Row 4 - Title
        x.merge("B4:H4");
        x.set("B4", "REPORT OF LOST, STOLEN, DAMAGED OR DESTROYED PROPERTY");
        x.style("B4").size(12).bold();
        x.style("B4").center();

        // Row 6 - Entity & Fund Cluster
        x.merge("B6:E6");
        x.set("B6", "Entity Name: " + RpciForm.or(entityName, "__________________________"));
        x.merge("G6:H6");
        x.set("G6", "Fund Cluster: " + RpciForm.or(fundCluster, "______________"));

        // Row 8 - Department & RLSDDP No.
        x.merge("B8:F8");
        x.set("B8", "Department/Office: " + RpciForm.or(department, "___________________________________________"));
        x.merge("G8:H8");
        x.set("G8", "RLSDDP No.: " + RpciForm.or(rlsddpNumber, "______________"));

        // Row 9 - Accountable Officer & RLSDDP Date
        x.merge("B9:F9");
        x.set("B9", "Accountable Officer: " + RpciForm.or((String) property.get("assigned_name"), "_________________________________________"));
        x.merge("G9:H9");
        x.set("G9", "RLSDDP Date: " + RpciForm.or(rlsddpDate, LocalDate.now(Times.ZONE).format(MDY)));

        // Row 10 - Designation & PAR No.
        x.merge("B10:F10");
        x.set("B10", "Designation: ________________________________________________");
        x.merge("G10:H10");
        x.set("G10", "PAR No.: " + RpciForm.or(parNumber, "_________________"));

        // Row 11-13 - Police notification
        x.set("B11", "Police Notified:");
        x.set("C11", Boolean.TRUE.equals(policeNotified) ? "Yes" : "");
        x.merge("D11:F11");
        x.set("D11", "Police Station: " + RpciForm.or(policeStation, "____________________"));
        x.merge("G11:H11");
        x.set("G11", "PAR Date: " + RpciForm.or(parDate, "________________"));

        x.set("D12", "Date: " + RpciForm.or(policeDate, "___________________________"));
        x.set("C13", Boolean.FALSE.equals(policeNotified) ? "No" : "");

        // Row 15 - Status of Property
        x.set("B15", "Status of Property: (check applicable box)");
        x.style("B15").bold();

        // Row 17-18 - Checkboxes
        String lost = "lost".equals(status) ? "☑" : "☐";
        String stolen = "stolen".equals(status) ? "☑" : "☐";
        String damaged = "damaged".equals(status) ? "☑" : "☐";
        String destroyed = "destroyed".equals(status) ? "☑" : "☐";

        x.set("C17", lost);
        x.set("D17", "Lost");
        x.set("E17", damaged);
        x.set("F17", "Damaged");
        x.set("C18", stolen);
        x.set("D18", "Stolen");
        x.set("E18", destroyed);
        x.set("F18", "Destroyed");
        x.style("C17:C18").size(14);
        x.style("E17:E18").size(14);

        // Row 20-21 - Property details header
        x.merge("B20:B21");
        x.set("B20", "Property No.");
        x.merge("C20:F21");
        x.set("C20", "Description");
        x.merge("G20:H21");
        x.set("G20", "Acquisition Cost");
        x.style("B20:H21").bold().center().vcenter().border(Edge.ALL, Line.THIN);

        // Property data rows
        int row = 22;
        x.set("B" + row, property.get("tag_number"));
        x.merge("C" + row + ":F" + row);
        x.set("C" + row, PropertyCardForm.str(property.get("item_name")) + " (S/N: " + PropertyCardForm.str(property.get("serial_number")) + ")");
        x.merge("G" + row + ":H" + row);
        x.set("G" + row, PropertyCardForm.dbl(property.get("weighted_average_cost")));
        x.style("G" + row).numberFormat("#,##0.00");
        x.style("B" + row + ":H" + row).border(Edge.ALL, Line.THIN);

        // Fill empty rows
        for (int i = row + 1; i <= row + 5; i++) {
            x.merge("C" + i + ":F" + i);
            x.merge("G" + i + ":H" + i);
            x.style("B" + i + ":H" + i).border(Edge.ALL, Line.THIN);
        }

        // Circumstances
        int cRow = row + 7;
        x.set("B" + cRow, "Circumstances:");
        x.style("B" + cRow).bold();
        cRow++;

        // Wrap circumstances text into multiple lines
        String lines = wordwrap(circumstances, 80);
        for (String line : lines.split("\n", -1)) {
            x.merge("B" + cRow + ":H" + cRow);
            x.set("B" + cRow, line);
            cRow++;
        }
        // Fill at least 4 lines
        while (cRow < row + 12) {
            x.merge("B" + cRow + ":H" + cRow);
            x.set("B" + cRow, "________________________________________________________________________________");
            cRow++;
        }

        // Certification
        cRow += 2;
        x.merge("B" + cRow + ":E" + cRow);
        x.set("B" + cRow, "I hereby certify that the item/s and circumstances stated above are true and correct.");
        x.style("B" + cRow).size(9);
        x.set("F" + cRow, "Noted by:");

        // Signatures
        cRow += 2;
        x.merge("B" + cRow + ":E" + cRow);
        x.set("B" + cRow, "__________________________________________");
        x.style("B" + cRow).center();
        x.merge("F" + cRow + ":H" + cRow);
        x.set("F" + cRow, "__________________________________");
        x.style("F" + cRow).center();

        // Embed configured signatories per role (on the signature lines).
        Signatories.stamp(x, "B" + cRow, "B" + cRow, signatories.resolve("property_officer"), 26);
        Signatories.stamp(x, "F" + cRow, "F" + cRow, signatories.resolve("head_of_agency"), 26);

        cRow++;
        x.merge("B" + cRow + ":E" + cRow);
        x.set("B" + cRow, "Signature over Printed Name of the Accountable Officer");
        x.style("B" + cRow).size(8).italic();
        x.style("B" + cRow).center();
        x.merge("F" + cRow + ":H" + cRow);
        x.set("F" + cRow, "Signature over Printed Name of the Immediate Supervisor");
        x.style("F" + cRow).size(8).italic();
        x.style("F" + cRow).center();

        return x.bytes();
    }

    /**
     * PHP {@code wordwrap($text, $width, "\n", true)} — the same byte-wise algorithm as php_wordwrap's
     * general (cutting) branch, so lines break at the same places.
     */
    static String wordwrap(String text, int width) {
        byte[] t = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        int len = t.length;
        if (len == 0) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream(len + len / Math.max(width, 1) + 1);
        int laststart = 0, lastspace = 0, current;
        for (current = 0; current < len; current++) {
            if (t[current] == '\n' && current + 1 < len) {
                // An existing break: copy through it.
                out.write(t, laststart, current - laststart + 1);
                laststart = lastspace = current + 1;
            } else if (t[current] == ' ') {
                if (current - laststart >= width) {
                    out.write(t, laststart, current - laststart);
                    out.write('\n');
                    laststart = current + 1;
                }
                lastspace = current;
            } else if (current - laststart >= width && laststart >= lastspace) {
                // Cutting: no space to land on, so break mid-word.
                out.write(t, laststart, current - laststart);
                out.write('\n');
                laststart = lastspace = current;
            } else if (current - laststart >= width && laststart < lastspace) {
                // The word runs past the width: break at the last space.
                out.write(t, laststart, lastspace - laststart);
                out.write('\n');
                laststart = lastspace = lastspace + 1;
            }
        }
        if (laststart != current) out.write(t, laststart, current - laststart);
        return out.toString(StandardCharsets.UTF_8);
    }
}

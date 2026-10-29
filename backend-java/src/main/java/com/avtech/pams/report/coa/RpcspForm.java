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
 * Report on the Physical Count of Semi-Expendable Property (RPCSP) — COA Appendix 68.
 * Lists semi-expendable property (tangible items below the capitalization threshold
 * with a serviceable life of more than one year) with card balance vs. physical count.
 */
@Component
public class RpcspForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;

    public RpcspForm(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code countData}: physical count per item id, as in {@link RpciForm#generate}. */
    public byte[] generate(LocalDate asAtDate, String fundCluster, Map<Long, ? extends Number> countData) {
        // Semi-expendable items only.
        List<Map<String, Object>> items = jdbc.queryForList("""
                select i.id, i.code, i.name, i.unit_of_measure, i.weighted_average_cost, i.current_stock, c.name as category_name
                from items i
                left join categories c on c.id = i.category_id
                where i.is_semi_expandable = ?
                order by i.name, i.id
                """, true);

        Xlsx x = new Xlsx("RPCSP");
        x.defaultFont(FONT, 10);

        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        Map<String, Double> colWidths = new LinkedHashMap<>();
        colWidths.put("A", 3.0);
        colWidths.put("B", 14.0);
        colWidths.put("C", 20.0);
        colWidths.put("D", 16.0);
        colWidths.put("E", 12.0);
        colWidths.put("F", 12.0);
        colWidths.put("G", 12.0);
        colWidths.put("H", 12.0);
        colWidths.put("I", 12.0);
        colWidths.put("J", 12.0);
        colWidths.put("K", 16.0);
        colWidths.forEach(x::width);

        // Appendix 68
        x.set("K1", "Appendix 68");
        x.style("K1").size(9).italic();
        x.style("K1").right();

        // Title
        x.merge("B3:K3");
        x.set("B3", "REPORT ON THE PHYSICAL COUNT OF SEMI-EXPENDABLE PROPERTY");
        x.style("B3").size(14).bold();
        x.style("B3").center();

        // As at date
        x.merge("B5:K5");
        x.set("B5", "As at " + (asAtDate != null ? asAtDate : LocalDate.now(Times.ZONE)).format(RpciForm.LONG_DATE));
        x.style("B5").center();

        // Fund Cluster
        x.set("B8", "Fund Cluster: " + RpciForm.or(fundCluster, "________________________________"));

        // Column headers
        RpciForm.countHeaders(x, "Semi-expendable Property No.");

        // Data rows
        int row = 15;
        for (Map<String, Object> item : items) {
            RpciForm.countRow(x, row, item, countData);
            row++;
        }

        if (items.isEmpty()) {
            for (int i = 0; i < 20; i++) {
                x.style("B" + row + ":K" + row).border(Edge.ALL, Line.THIN);
                row++;
            }
        }

        // Signature section
        RpciForm.countSignatures(x, row + 2);

        return x.bytes();
    }
}

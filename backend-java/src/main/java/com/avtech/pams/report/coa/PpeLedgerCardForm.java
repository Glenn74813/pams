package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.property.DepreciationService;
import com.avtech.pams.property.Property;
import com.avtech.pams.property.PropertyRepository;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Property, Plant and Equipment Ledger Card (PPELC) — COA GAM Appendix 70 — for one asset:
 * acquisition, accumulated depreciation at each year end (straight line, per DepreciationService)
 * up to the as-at date, transfers and disposal, with the adjusted cost after each line.
 */
@Component
public class PpeLedgerCardForm {

    private static final String FONT = "Times New Roman";

    private final JdbcTemplate jdbc;
    private final PropertyRepository properties;
    private final DepreciationService depreciation;
    private final Signatories signatories;

    public PpeLedgerCardForm(JdbcTemplate jdbc, PropertyRepository properties, DepreciationService depreciation, Signatories signatories) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.depreciation = depreciation;
        this.signatories = signatories;
    }

    private record Entry(LocalDate date, String ref, boolean receipt, BigDecimal accumulated, String adjustment, BigDecimal adjusted,
                         String repair, BigDecimal repairCost) {
        Entry(LocalDate date, String ref, boolean receipt, BigDecimal accumulated, String adjustment, BigDecimal adjusted) {
            this(date, ref, receipt, accumulated, adjustment, adjusted, null, null);
        }
    }

    public byte[] generate(long propertyId, LocalDate asAt, String entityName, String fundCluster) {
        Property p = properties.findById(propertyId).orElseThrow(() -> ApiException.notFound("Property"));
        LocalDate through = asAt != null ? asAt : LocalDate.now(Times.ZONE);
        Map<String, Object> info = jdbc.queryForList("""
                select i.name as item_name, i.description, c.name as category_name, u.name as holder
                from properties p left join items i on i.id = p.item_id left join categories c on c.id = i.category_id
                left join users u on u.id = p.assigned_to where p.id = ?""", propertyId).get(0);
        BigDecimal cost = p.getAcquisitionCost() == null ? BigDecimal.ZERO : p.getAcquisitionCost();
        LocalDate acquired = p.getAcquisitionDate() != null ? p.getAcquisitionDate() : p.getAssignedDate();

        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(acquired, RpciForm.or(p.getAccountabilityNumber(), "Acquisition"), true, BigDecimal.ZERO.setScale(2), "", cost));
        // Year-end depreciation, and the as-at date for the current year.
        if (p.isDepreciable() && cost.signum() > 0 && acquired != null) {
            for (int y = depreciation.start(p).getYear(); y <= through.getYear(); y++) {
                LocalDate end = y == through.getYear() ? through : LocalDate.of(y, 12, 31);
                BigDecimal acc = accumulatedAt(depreciation, p, end);
                if (acc.signum() == 0) continue;
                entries.add(new Entry(end, "Depreciation " + (y == through.getYear() && !end.equals(LocalDate.of(y, 12, 31)) ? "as at " + PropertyCardForm.mdy(end) : "CY " + y),
                        false, acc, "", cost.subtract(acc)));
                if (acc.compareTo(depreciation.base(p)) >= 0) break;
            }
        }
        for (Map<String, Object> t : jdbc.queryForList("""
                select t.transfer_date, t.reference_number, tu.name as to_name from property_transfers t
                left join users tu on tu.id = t.to_user where t.property_id = ? order by t.transfer_date, t.id""", propertyId)) {
            LocalDate d = ItemMovements.date(t.get("transfer_date"));
            if (d != null && d.isAfter(through)) continue;
            BigDecimal acc = d == null ? BigDecimal.ZERO : accumulatedAt(depreciation, p, d);
            entries.add(new Entry(d, RpciForm.or((String) t.get("reference_number"), "Transfer"), false, acc,
                    "Transferred to " + RpciForm.or((String) t.get("to_name"), "—"), cost.subtract(acc)));
        }
        for (Map<String, Object> d : jdbc.queryForList("""
                select disposal_number, coalesce(disposal_date, approval_date, request_date) as disposal_date, disposal_type from disposal_requests
                where property_id = ? and status = 'completed' and deleted_at is null""", propertyId)) {
            LocalDate dd = ItemMovements.date(d.get("disposal_date"));
            if (dd != null && dd.isAfter(through)) continue;
            BigDecimal acc = dd == null ? BigDecimal.ZERO : accumulatedAt(depreciation, p, dd);
            entries.add(new Entry(dd, ItemMovements.str(d.get("disposal_number")), false, acc,
                    "Disposed (" + ItemMovements.str(d.get("disposal_type")) + ")", BigDecimal.ZERO.setScale(2)));
        }
        // Repair history: corrective maintenance done through the as-at date (Asset Maintenance).
        for (Map<String, Object> m : jdbc.queryForList("""
                select completed_date, description, performed_by, cost from property_maintenance
                where property_id = ? and status = 'done' and maintenance_type = 'corrective' and completed_date <= ? order by completed_date, id""",
                propertyId, java.sql.Date.valueOf(through))) {
            LocalDate md = ((java.sql.Date) m.get("completed_date")).toLocalDate();
            String by = (String) m.get("performed_by");
            entries.add(new Entry(md, "Repair", false, accumulatedAt(depreciation, p, md), "", null,
                    m.get("description") + (by == null ? "" : " (" + by + ")"), (BigDecimal) m.get("cost")));
        }
        // The acquisition opens the card; the rest follow by date.
        entries.subList(1, entries.size()).sort(Comparator.comparing(Entry::date, Comparator.nullsFirst(Comparator.naturalOrder())));

        Xlsx x = new Xlsx("PPE Ledger Card");
        x.defaultFont(FONT, 10);
        x.page().paper(Xlsx.A4).landscape().fitToWidth(1).fitToHeight(0);
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        // A Date | B Reference | C-E Receipt (Qty, Unit Cost, Total Cost) | F Accumulated Depreciation | G Accumulated Impairment Losses
        // | H Issues/Transfers/Adjustment/s | I Adjusted Cost | J-K Repair History (Nature of Repair, Amount)
        double[] widths = {11, 18, 7, 13, 13, 15, 14, 24, 14, 20, 12};
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);

        x.set("K1", "Appendix 70");
        x.style("K1").size(9).italic().right();
        x.merge("A3:K3");
        x.set("A3", "PROPERTY, PLANT AND EQUIPMENT LEDGER CARD");
        x.style("A3").size(14).bold().center();

        x.set("A5", "Entity Name: " + RpciForm.or(entityName, "___________________________________"));
        x.set("H5", "Fund Cluster: " + RpciForm.or(fundCluster, "_________________"));
        x.style("A5:K5").bold();
        x.set("A7", "Property, Plant and Equipment: " + ItemMovements.str(info.get("category_name")));
        x.set("H7", "Object Account Code: ____________");
        x.set("A8", "Description: " + ItemMovements.str(info.get("item_name"))
                + (p.getSerialNumber() != null && !p.getSerialNumber().isBlank() ? " (S/N: " + p.getSerialNumber() + ")" : "")
                + " — Property No. " + ItemMovements.str(p.getTagNumber()));
        int life = Math.max(1, p.getUsefulLifeYears());
        x.set("H8", "Estimated Useful Life: " + life + " yrs");
        x.set("A9", "Accountable Officer: " + RpciForm.or((String) info.get("holder"), "-"));
        x.set("H9", "Rate of Depreciation: " + BigDecimal.valueOf(100).divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP) + "% per year");

        x.set("A11", "Date");
        x.set("B11", "Reference");
        x.set("C11", "Receipt");
        x.set("C12", "Qty.");
        x.set("D12", "Unit Cost");
        x.set("E12", "Total Cost");
        x.set("F11", "Accumulated Depreciation");
        x.set("G11", "Accumulated Impairment Losses");
        x.set("H11", "Issues/Transfers/ Adjustment/s");
        x.set("I11", "Adjusted Cost");
        x.set("J11", "Repair History");
        x.set("J12", "Nature of Repair");
        x.set("K12", "Amount");
        for (String m : new String[]{"A11:A12", "B11:B12", "C11:E11", "F11:F12", "G11:G12", "H11:H12", "I11:I12", "J11:K11"}) x.merge(m);
        x.style("A11:K12").bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);

        int row = 13;
        for (Entry e : entries) {
            x.set("A" + row, PropertyCardForm.mdy(e.date()));
            x.set("B" + row, e.ref());
            if (e.receipt()) {
                x.set("C" + row, 1);
                x.set("D" + row, cost);
                x.set("E" + row, cost);
            }
            x.set("F" + row, e.accumulated());
            x.set("H" + row, e.adjustment());
            x.set("I" + row, e.adjusted());
            if (e.repair() != null) {
                x.set("J" + row, e.repair());
                x.set("K" + row, e.repairCost());
                x.style("J" + row).wrap();
            }
            x.style("A" + row + ":K" + row).border(Edge.ALL, Line.THIN).vcenter();
            x.style("A" + row).center();
            x.style("C" + row).center();
            x.style("H" + row).wrap();
            for (String c : new String[]{"D", "E", "F", "G", "I", "K"}) x.style(c + row).numberFormat("#,##0.00");
            row++;
        }
        for (int i = entries.size(); i < 12; i++) {
            x.style("A" + row + ":K" + row).border(Edge.ALL, Line.THIN);
            row++;
        }

        row += 2;
        x.set("A" + row, "Prepared by:");
        x.set("H" + row, "Certified Correct:");
        Signatories.stamp(x, "A" + (row + 1), "A" + (row + 3), signatories.resolve("accountant"));
        Signatories.stamp(x, "H" + (row + 1), "H" + (row + 3), signatories.resolve("head_of_agency"));
        x.set("A" + (row + 4), "Accountant / Bookkeeper");
        x.set("H" + (row + 4), "Head of Agency / Authorized Representative");
        x.style("A" + (row + 4) + ":K" + (row + 4)).size(8).italic();
        return x.bytes();
    }

    /**
     * Straight-line depreciation recognised through {@code date}: one monthly amount for every month
     * from the start month whose last day is on or before the date, never beyond cost less residual.
     */
    public static BigDecimal accumulatedAt(DepreciationService ds, Property p, LocalDate date) {
        BigDecimal cost = p.getAcquisitionCost() == null ? BigDecimal.ZERO : p.getAcquisitionCost();
        if (!p.isDepreciable() || cost.signum() <= 0 || (p.getAcquisitionDate() == null && p.getAssignedDate() == null && p.getDepreciationStartDate() == null)) {
            return BigDecimal.ZERO.setScale(2);
        }
        YearMonth start = ds.start(p);
        YearMonth last = YearMonth.from(date);
        if (date.isBefore(last.atEndOfMonth())) last = last.minusMonths(1);
        long months = last.isBefore(start) ? 0 : start.until(last, java.time.temporal.ChronoUnit.MONTHS) + 1;
        BigDecimal acc = ds.monthly(p).multiply(BigDecimal.valueOf(months));
        return acc.min(ds.base(p)).setScale(2, RoundingMode.HALF_UP);
    }
}

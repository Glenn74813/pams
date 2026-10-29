package com.avtech.pams.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Sequential document numbers such as RIS-2026-10-0001. Each series is serialised with a
 * transaction-scoped advisory lock, so concurrent requests never get the same number.
 * Must be called inside a transaction.
 */
@Component
public class DocumentNumbers {

    /** table.column pairs a number may be read from; keeps the SQL below injection-free. */
    private static final Set<String> COLUMNS = Set.of(
            "requests.request_number", "requests.ris_number", "requests.ics_number", "requests.par_number",
            "stock_adjustments.adjustment_number", "properties.tag_number", "properties.accountability_number",
            "property_transfers.reference_number", "disposal_requests.disposal_number", "gate_passes.gate_pass_number",
            "ppmps.ppmp_number", "apps.app_number", "purchase_requests.pr_number", "purchase_requests.caf_number",
            "rfqs.rfq_number", "rfqs.noa_number", "rfqs.ntp_number", "biddings.bidding_number", "biddings.noa_number", "biddings.ntp_number",
            "bac_meetings.meeting_number", "bac_resolutions.resolution_number", "purchase_orders.po_number",
            "inspection_acceptance_reports.iar_number", "contracts.contract_number", "framework_calloffs.calloff_number", "suppliers.vendor_code",
            "physical_inventory_counts.count_number");

    private final JdbcTemplate jdbc;
    private final Db db;

    public DocumentNumbers(JdbcTemplate jdbc, Db db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    /** PREFIX-YYYY-MM-NNNN, numbered within the month across every "table.column" given. */
    public String monthly(String prefix, String... columns) {
        LocalDate d = LocalDate.now(Times.ZONE);
        return next(String.format("%s-%d-%02d-", prefix, d.getYear(), d.getMonthValue()), columns);
    }

    /** PREFIX-YYYY-NNNN, numbered within the year across every "table.column" given. */
    public String yearly(String prefix, String... columns) {
        return next(prefix + "-" + LocalDate.now(Times.ZONE).getYear() + "-", columns);
    }

    /** stem + a zero-padded sequence of the given width, e.g. series("PPMP-2027-", 4, ...) → PPMP-2027-0001. */
    public String series(String stem, int width, String... columns) {
        return next(stem, width, columns);
    }

    private String next(String stem, String... columns) {
        return next(stem, 4, columns);
    }

    private String next(String stem, int width, String... columns) {
        db.namedLock("pams.number." + stem);
        int max = 0;
        for (String tc : columns) {
            if (!COLUMNS.contains(tc)) throw new IllegalArgumentException("Not a numbered column: " + tc);
            String[] p = tc.split("\\.");
            for (String t : jdbc.queryForList("select " + p[1] + " from " + p[0] + " where " + p[1] + " like ?", String.class, stem + "%")) {
                String tail = t.substring(stem.length());
                if (tail.matches("\\d+")) max = Math.max(max, Integer.parseInt(tail));
            }
        }
        return stem + String.format("%0" + width + "d", max + 1);
    }
}

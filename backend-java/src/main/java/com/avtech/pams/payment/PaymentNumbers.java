package com.avtech.pams.payment;

import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * Monthly serials for the payment documents (ORS-2026-10-0001, DV-…, PCV-…, RPCF-…), serialised
 * with a transaction-scoped advisory lock as {@link com.avtech.pams.common.DocumentNumbers} does.
 */
@Component
public class PaymentNumbers {

    private static final Set<String> COLUMNS = Set.of("payment_tranches.ors_number", "payment_tranches.dv_number",
            "petty_cash_vouchers.pcv_number", "petty_cash_replenishments.report_number");

    private final JdbcTemplate jdbc;
    private final Db db;

    public PaymentNumbers(JdbcTemplate jdbc, Db db) {
        this.jdbc = jdbc;
        this.db = db;
    }

    public String monthly(String prefix, String column) {
        if (!COLUMNS.contains(column)) throw new IllegalArgumentException("Not a numbered column: " + column);
        LocalDate d = LocalDate.now(Times.ZONE);
        String stem = String.format("%s-%d-%02d-", prefix, d.getYear(), d.getMonthValue());
        db.namedLock("pams.number." + stem);
        String[] p = column.split("\\.");
        int max = 0;
        for (String t : jdbc.queryForList("select " + p[1] + " from " + p[0] + " where " + p[1] + " like ?", String.class, stem + "%")) {
            String tail = t.substring(stem.length());
            if (tail.matches("\\d+")) max = Math.max(max, Integer.parseInt(tail));
        }
        return stem + String.format("%04d", max + 1);
    }
}

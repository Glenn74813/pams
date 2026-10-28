package com.avtech.pams.property;

import com.avtech.pams.common.Times;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Straight-line depreciation per COA GAM: residual value defaults to 5% of cost, depreciation
 * starts the month after the asset was acquired, and is recognised one calendar month at a time.
 */
@Service
public class DepreciationService {

    private static final BigDecimal DEFAULT_RESIDUAL_RATE = new BigDecimal("0.05");

    private final DepreciationLogRepository logs;

    public DepreciationService(DepreciationLogRepository logs) {
        this.logs = logs;
    }

    public BigDecimal residual(Property p) {
        BigDecimal cost = cost(p);
        return p.getSalvageValue() != null && p.getSalvageValue().signum() > 0
                ? p.getSalvageValue() : cost.multiply(DEFAULT_RESIDUAL_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal base(Property p) {
        return cost(p).subtract(residual(p)).max(BigDecimal.ZERO);
    }

    public BigDecimal monthly(Property p) {
        int months = Math.max(1, p.getUsefulLifeYears()) * 12;
        return base(p).divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    public YearMonth start(Property p) {
        if (p.getDepreciationStartDate() != null) return YearMonth.from(p.getDepreciationStartDate());
        LocalDate acquired = p.getAcquisitionDate() != null ? p.getAcquisitionDate() : p.getAssignedDate();
        return YearMonth.from(acquired).plusMonths(1);
    }

    public BigDecimal bookValue(Property p) {
        return cost(p).subtract(p.getAccumulatedDepreciation()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Posts every month from the start through {@code through} that hasn't been posted yet, until
     * the asset reaches its residual value. Returns the number of months posted.
     */
    public int postThrough(Property p, YearMonth through) {
        if (!p.isDepreciable() || cost(p).signum() <= 0) return 0;
        Set<String> posted = new HashSet<>();
        logs.findByPropertyIdOrderByDepreciationDateAscIdAsc(p.getId()).forEach(l -> {
            if (l.getPeriod() != null) posted.add(l.getPeriod());
        });
        BigDecimal base = base(p);
        BigDecimal monthly = monthly(p);
        int count = 0;
        for (YearMonth m = start(p); !m.isAfter(through); m = m.plusMonths(1)) {
            if (posted.contains(m.toString())) continue;
            BigDecimal remaining = base.subtract(p.getAccumulatedDepreciation());
            if (remaining.signum() <= 0) break;
            BigDecimal amount = monthly.min(remaining);
            p.setAccumulatedDepreciation(p.getAccumulatedDepreciation().add(amount));
            p.setCurrentBookValue(bookValue(p));
            logs.save(new DepreciationLog(p.getId(), m.toString(), m.atEndOfMonth(), amount, p.getAccumulatedDepreciation(),
                    p.getCurrentBookValue(), "Straight-line depreciation for " + m));
            count++;
        }
        if (count > 0) p.setLastDepreciationCalculation(Times.now());
        return count;
    }

    public record ScheduleRow(int year, BigDecimal depreciation, BigDecimal accumulated, BigDecimal bookValue) {
    }

    /** Projected depreciation by calendar year over the whole useful life. */
    public List<ScheduleRow> schedule(Property p) {
        List<ScheduleRow> rows = new ArrayList<>();
        if (cost(p).signum() <= 0) return rows;
        BigDecimal base = base(p);
        BigDecimal monthly = monthly(p);
        BigDecimal acc = BigDecimal.ZERO;
        YearMonth m = start(p);
        int year = m.getYear();
        BigDecimal yearAmount = BigDecimal.ZERO;
        while (acc.compareTo(base) < 0) {
            if (m.getYear() != year) {
                rows.add(new ScheduleRow(year, yearAmount, acc, cost(p).subtract(acc)));
                year = m.getYear();
                yearAmount = BigDecimal.ZERO;
            }
            BigDecimal amount = monthly.min(base.subtract(acc));
            acc = acc.add(amount);
            yearAmount = yearAmount.add(amount);
            m = m.plusMonths(1);
        }
        rows.add(new ScheduleRow(year, yearAmount, acc, cost(p).subtract(acc)));
        return rows;
    }

    private static BigDecimal cost(Property p) {
        return p.getAcquisitionCost() == null ? BigDecimal.ZERO : p.getAcquisitionCost();
    }
}

package com.avtech.pams.setting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Key/value system settings (the `settings` table). */
@Service
public class SettingService {

    public static final String CAPITALIZATION_THRESHOLD = "capitalization_threshold";
    public static final String FEFO_WARNING_DAYS = "fefo_expiry_warning_days";

    private static final Map<String, String> DEFAULTS = Map.of(
            CAPITALIZATION_THRESHOLD, "50000",
            FEFO_WARNING_DAYS, "30");

    private final JdbcTemplate jdbc;

    public SettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String get(String key) {
        List<String> values = jdbc.queryForList("select value from settings where key = ?", String.class, key);
        return values.isEmpty() || values.get(0) == null ? DEFAULTS.get(key) : values.get(0);
    }

    /** COA capitalization threshold: unit cost at or above it is PPE, below it Semi-Expendable. */
    public BigDecimal capitalizationThreshold() {
        return new BigDecimal(get(CAPITALIZATION_THRESHOLD));
    }

    public int fefoWarningDays() {
        return Integer.parseInt(get(FEFO_WARNING_DAYS));
    }
}

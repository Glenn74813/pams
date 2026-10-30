package com.avtech.pams.setting;

import com.avtech.pams.common.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Key/value system settings (the `settings` table). */
@Service
public class SettingService {

    public static final String CAPITALIZATION_THRESHOLD = "capitalization_threshold";
    public static final String FEFO_WARNING_DAYS = "fefo_expiry_warning_days";
    public static final String IDLE_TIMEOUT_MINUTES = "idle_timeout_minutes";
    public static final String PASSWORD_MIN_LENGTH = "password_min_length";
    /** Days before a password must be changed; 0 turns expiry off. */
    public static final String PASSWORD_EXPIRY_DAYS = "password_expiry_days";
    /** Address people open the app at; QR labels link here. Empty = the pams.app-url setting. */
    public static final String APP_URL = "app_url";

    private static final Map<String, String> DEFAULTS = Map.of(
            CAPITALIZATION_THRESHOLD, "50000",
            FEFO_WARNING_DAYS, "30",
            IDLE_TIMEOUT_MINUTES, "30",
            PASSWORD_MIN_LENGTH, "8",
            PASSWORD_EXPIRY_DAYS, "0");

    private final JdbcTemplate jdbc;

    public SettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String get(String key, String fallback) {
        String v = get(key);
        return v == null ? fallback : v;
    }

    /** Inserts or replaces one setting (null stores an empty value, as Laravel's Setting::put did). */
    public void put(String key, String value) {
        String v = value == null ? "" : value;
        Timestamp now = Timestamp.valueOf(Times.now());
        if (jdbc.update("update settings set value = ?, updated_at = ? where \"key\" = ?", v, now, key) == 0) {
            jdbc.update("insert into settings (\"key\", value, created_at, updated_at) values (?, ?, ?, ?)", key, v, now, now);
        }
    }

    public String get(String key) {
        List<String> values = jdbc.queryForList("select value from settings where \"key\" = ?", String.class, key);
        return values.isEmpty() || values.get(0) == null ? DEFAULTS.get(key) : values.get(0);
    }

    /** COA capitalization threshold: unit cost at or above it is PPE, below it Semi-Expendable. */
    public BigDecimal capitalizationThreshold() {
        return new BigDecimal(get(CAPITALIZATION_THRESHOLD));
    }

    /** Minutes without activity before the app signs a person out; 0 turns it off. */
    public int idleTimeoutMinutes() {
        return parse(get(IDLE_TIMEOUT_MINUTES), 30);
    }

    public int passwordMinLength() {
        return Math.max(8, parse(get(PASSWORD_MIN_LENGTH), 8));
    }

    public int passwordExpiryDays() {
        return Math.max(0, parse(get(PASSWORD_EXPIRY_DAYS), 0));
    }

    private static int parse(String v, int fallback) {
        try {
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int fefoWarningDays() {
        return Integer.parseInt(get(FEFO_WARNING_DAYS));
    }
}

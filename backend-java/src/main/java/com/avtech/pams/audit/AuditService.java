package com.avtech.pams.audit;

import com.avtech.pams.auth.AuthPrincipal;
import com.avtech.pams.auth.ClientIp;
import com.avtech.pams.common.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.Map;

/**
 * Writes rows to audit_trails in the same shape as Laravel's Auditable trait, so the existing Audit
 * Trail screen shows changes made through either backend. auditable_type keeps Laravel's class names.
 */
@Service
public class AuditService {

    public static final String ITEM = "App\\Models\\Item";
    public static final String CATEGORY = "App\\Models\\Category";
    public static final String STOCK = "App\\Models\\Stock";
    public static final String STOCK_TRANSFER = "App\\Models\\StockTransfer";
    public static final String USER = "App\\Models\\User";
    public static final String ROLE = "App\\Models\\Role";

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records one change. Runs inside the caller's transaction, so a rolled-back change leaves no
     * audit row behind; a failure to write the row is logged but never breaks the change itself.
     */
    public void record(String action, String type, Long id, String description,
                       Map<String, ?> oldValues, Map<String, ?> newValues) {
        try {
            AuthPrincipal p = principal();
            var req = request();
            Timestamp now = Timestamp.valueOf(Times.now());
            jdbc.update("""
                            insert into audit_trails (user_id, user_name, user_role, action, auditable_type, auditable_id,
                              description, old_values, new_values, ip_address, user_agent, url, method, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, cast(? as json), cast(? as json), ?, ?, ?, ?, ?, ?)""",
                    p == null ? null : p.user().getId(),
                    p == null ? "System" : p.user().getName(),
                    p == null ? null : p.user().getRole().getName(),
                    action, type, id, description,
                    oldValues == null ? null : JSON.writeValueAsString(oldValues),
                    newValues == null ? null : JSON.writeValueAsString(newValues),
                    req == null ? null : ClientIp.of(req),
                    req == null ? null : truncate(req.getHeader("User-Agent"), 255),
                    req == null ? null : truncate(req.getRequestURL().toString(), 255),
                    req == null ? null : req.getMethod(),
                    now, now);
        } catch (RuntimeException e) {
            log.warn("Could not write audit row for {} {} #{}: {}", action, type, id, e.getMessage());
        }
    }

    private static AuthPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }

    private static jakarta.servlet.http.HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}

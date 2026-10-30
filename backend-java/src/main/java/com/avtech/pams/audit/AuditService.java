package com.avtech.pams.audit;

import com.avtech.pams.auth.AuthPrincipal;
import com.avtech.pams.auth.ClientIp;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
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

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final Db db;
    private final SecretKeySpec sealKey;

    public AuditService(JdbcTemplate jdbc, Db db, @Value("${pams.audit.seal-key:${pams.jwt.secret}}") String sealKey) {
        this.jdbc = jdbc;
        this.db = db;
        this.sealKey = new SecretKeySpec(("pams-audit:" + sealKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Records one change. Runs inside the caller's transaction, so a rolled-back change leaves no
     * audit row behind, and a change whose audit row cannot be written fails with it (TOR: every
     * change is logged). The row is sealed with an HMAC of its contents; the table is append-only.
     */
    public void record(String action, String type, Long id, String description,
                       Map<String, ?> oldValues, Map<String, ?> newValues) {
        AuthPrincipal p = principal();
        var req = request();
        // Stored to the second on both databases, so the seal covers exactly what is kept.
        LocalDateTime now = Times.now().withNano(0);
        Row row = new Row(p == null ? "System" : p.user().getName(), p == null ? null : p.user().getRole().getName(),
                action, type, id, description,
                oldValues == null ? null : JSON.writeValueAsString(oldValues),
                newValues == null ? null : JSON.writeValueAsString(newValues),
                req == null ? null : ClientIp.of(req),
                req == null ? null : truncate(req.getHeader("User-Agent"), 255),
                req == null ? null : truncate(req.getRequestURL().toString(), 255),
                req == null ? null : req.getMethod(), now);
        jdbc.update("""
                        insert into audit_trails (user_id, user_name, user_role, action, auditable_type, auditable_id,
                          description, old_values, new_values, ip_address, user_agent, url, method, created_at, updated_at, seal)
                        values (?, ?, ?, ?, ?, ?, ?, """ + db.jsonParam() + ", " + db.jsonParam() + ", ?, ?, ?, ?, ?, ?, ?)",
                p == null ? null : p.user().getId(), row.userName(), row.userRole(), row.action(), row.type(), row.auditableId(), row.description(),
                row.oldValues(), row.newValues(), row.ip(), row.userAgent(), row.url(), row.method(),
                Timestamp.valueOf(now), Timestamp.valueOf(now), seal(row));
    }

    /** The sealed fields of one audit row, as stored. user_id is left out: deleting a user clears it. */
    public record Row(String userName, String userRole, String action, String type, Long auditableId, String description,
                      String oldValues, String newValues, String ip, String userAgent, String url, String method, LocalDateTime createdAt) {
    }

    /** HMAC-SHA256 of the row's fields; a row whose seal no longer matches was changed outside PAMS. */
    public String seal(Row r) {
        String text = String.join("\u001f", n(r.userName()), n(r.userRole()), n(r.action()), n(r.type()),
                r.auditableId() == null ? "" : r.auditableId().toString(), n(r.description()), n(r.oldValues()), n(r.newValues()),
                n(r.ip()), n(r.userAgent()), n(r.url()), n(r.method()), r.createdAt().withNano(0).toString());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(sealKey);
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static String n(String s) {
        return s == null ? "" : s;
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

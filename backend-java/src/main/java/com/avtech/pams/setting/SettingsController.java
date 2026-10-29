package com.avtech.pams.setting;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.user.SignatureStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * System settings, the agency profile printed on COA forms, authorized signatories per role and
 * document logos. Every change is audited with its old and new values (Laravel did not audit
 * settings at all).
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final String TYPE = "App\\Models\\Setting";

    /** Agency profile and operational keys, in display order. */
    static final List<String> MIS_KEYS = List.of("agency_name", "agency_head_name", "agency_head_title", "agency_address",
            "fund_cluster", SettingService.FEFO_WARNING_DAYS);

    /** COA signatory roles used across the prescribed forms (same keys as Laravel's SignatureService). */
    public static final Map<String, String> SIGNATORY_ROLES = new LinkedHashMap<>();

    static {
        SIGNATORY_ROLES.put("inventory_committee_chair", "Inventory Committee Chair");
        SIGNATORY_ROLES.put("supply_officer", "Supply Officer");
        SIGNATORY_ROLES.put("property_officer", "Property Officer");
        SIGNATORY_ROLES.put("accountant", "Accountant");
        SIGNATORY_ROLES.put("head_of_agency", "Head of Agency / Authorized Representative");
        SIGNATORY_ROLES.put("coa_representative", "COA Representative");
        SIGNATORY_ROLES.put("bac_chairperson", "BAC Chairperson");
        SIGNATORY_ROLES.put("inspector", "Inspection Officer");
    }

    static final List<String> POSITIONS = List.of("top-left", "top-center", "top-right", "bottom-left", "bottom-center", "bottom-right");

    private final SettingService settings;
    private final SignatureStore signatures;
    private final LogoStore logos;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public SettingsController(SettingService settings, SignatureStore signatures, LogoStore logos, AuditService audit, JdbcTemplate jdbc) {
        this.settings = settings;
        this.signatures = signatures;
        this.logos = logos;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- system

    /** Read by every signed-in client: the idle sign-out and password rules apply to everyone. */
    @GetMapping
    public Map<String, Object> index() {
        return system();
    }

    private Map<String, Object> system() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("capitalization_threshold", settings.capitalizationThreshold());
        m.put("idle_timeout_minutes", settings.idleTimeoutMinutes());
        m.put("password_min_length", settings.passwordMinLength());
        return m;
    }

    public record SystemInput(BigDecimal capitalizationThreshold, Integer idleTimeoutMinutes, Integer passwordMinLength) {
    }

    @PutMapping
    @PreAuthorize("@perm.any('edit_settings', 'manage_system_settings')")
    @Transactional
    public Map<String, Object> update(@RequestBody SystemInput in) {
        if (in.capitalizationThreshold() == null || in.capitalizationThreshold().signum() <= 0) {
            throw ApiException.validation("capitalization_threshold", "Enter the capitalization threshold (COA sets ₱50,000).");
        }
        if (in.idleTimeoutMinutes() != null && in.idleTimeoutMinutes() != 0 && (in.idleTimeoutMinutes() < 5 || in.idleTimeoutMinutes() > 480)) {
            throw ApiException.validation("idle_timeout_minutes", "Idle sign-out must be between 5 and 480 minutes, or 0 to turn it off.");
        }
        if (in.passwordMinLength() != null && (in.passwordMinLength() < 8 || in.passwordMinLength() > 64)) {
            throw ApiException.validation("password_min_length", "Minimum password length must be between 8 and 64.");
        }
        Map<String, Object> before = system();
        settings.put(SettingService.CAPITALIZATION_THRESHOLD, in.capitalizationThreshold().stripTrailingZeros().toPlainString());
        if (in.idleTimeoutMinutes() != null) settings.put(SettingService.IDLE_TIMEOUT_MINUTES, String.valueOf(in.idleTimeoutMinutes()));
        if (in.passwordMinLength() != null) settings.put(SettingService.PASSWORD_MIN_LENGTH, String.valueOf(in.passwordMinLength()));
        Map<String, Object> after = system();
        changed("Updated system settings", before, after);
        Map<String, Object> out = new LinkedHashMap<>(after);
        out.put("message", "Settings updated.");
        return out;
    }

    // ---------------------------------------------------------------- agency profile

    @GetMapping("/mis")
    public Map<String, Object> mis() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : MIS_KEYS) m.put(k, settings.get(k, ""));
        m.put(SettingService.FEFO_WARNING_DAYS, settings.fefoWarningDays());
        return m;
    }

    @PutMapping("/mis")
    @PreAuthorize("@perm.any('edit_settings', 'manage_agency_profile')")
    @Transactional
    public Map<String, Object> updateMis(@RequestBody Map<String, Object> in) {
        Map<String, Object> before = mis();
        for (String k : MIS_KEYS) {
            if (!in.containsKey(k)) continue;
            Object raw = in.get(k);
            String v = raw == null ? "" : String.valueOf(raw).trim();
            if (k.equals(SettingService.FEFO_WARNING_DAYS)) {
                int days;
                try {
                    days = v.isEmpty() ? 30 : Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw ApiException.validation(k, "Enter a whole number of days.");
                }
                if (days < 0 || days > 3650) throw ApiException.validation(k, "Expiry warning must be between 0 and 3650 days.");
                v = String.valueOf(days);
            } else if (v.length() > (k.equals("agency_address") ? 500 : 255)) {
                throw ApiException.validation(k, "That is too long.");
            }
            settings.put(k, v);
        }
        Map<String, Object> after = mis();
        changed("Updated agency profile", before, after);
        Map<String, Object> out = new LinkedHashMap<>(after);
        out.put("message", "MIS settings updated.");
        return out;
    }

    // ---------------------------------------------------------------- signatories

    @GetMapping("/signatories")
    @PreAuthorize("@perm.any('view_settings', 'edit_settings', 'manage_signatories')")
    public Map<String, Object> signatories() {
        List<Map<String, Object>> users = jdbc.query("select id, name, signature_path is not null from users order by name", (rs, i) -> {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("id", rs.getLong(1));
            u.put("name", rs.getString(2));
            u.put("has_signature", rs.getBoolean(3));
            return u;
        });
        return Map.of("signatories", configuration(), "users", users);
    }

    private List<Map<String, Object>> configuration() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : SIGNATORY_ROLES.entrySet()) {
            Long userId = userId(e.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role_key", e.getKey());
            row.put("label", e.getValue());
            row.put("user_id", null);
            row.put("user_name", null);
            row.put("has_signature", false);
            if (userId != null) {
                jdbc.query("select id, name, signature_path is not null from users where id = ?", rs -> {
                    row.put("user_id", rs.getLong(1));
                    row.put("user_name", rs.getString(2));
                    row.put("has_signature", rs.getBoolean(3));
                }, userId);
            }
            out.add(row);
        }
        return out;
    }

    private Long userId(String roleKey) {
        String v = settings.get("signatory_" + roleKey, "");
        try {
            return v.isBlank() ? null : Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One role's signatory for embedding in a document: name, designation and signature image. */
    @GetMapping("/signatory/{role}")
    public Map<String, Object> signatory(@PathVariable String role) {
        if (!SIGNATORY_ROLES.containsKey(role)) throw ApiException.notFound("Signatory role");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", null);
        out.put("designation", SIGNATORY_ROLES.get(role));
        out.put("data_uri", null);
        Long id = userId(role);
        if (id != null) {
            jdbc.query("select name, signature_path from users where id = ?", rs -> {
                out.put("name", rs.getString(1));
                out.put("data_uri", signatures.dataUri(rs.getString(2)));
            }, id);
        }
        out.put("has_image", out.get("data_uri") != null);
        return out;
    }

    public record SignatoryRow(String roleKey, Long userId) {
    }

    public record SignatoriesInput(List<SignatoryRow> signatories) {
    }

    @PutMapping("/signatories")
    @PreAuthorize("@perm.any('edit_settings', 'manage_signatories')")
    @Transactional
    public Map<String, Object> updateSignatories(@RequestBody SignatoriesInput in) {
        if (in.signatories() == null) throw ApiException.validation("signatories", "Send the signatory assignments.");
        Map<String, Object> before = assignments();
        for (int i = 0; i < in.signatories().size(); i++) {
            SignatoryRow row = in.signatories().get(i);
            if (row.roleKey() == null || !SIGNATORY_ROLES.containsKey(row.roleKey())) {
                throw ApiException.validation("signatories." + i + ".role_key", "Unknown signatory role.");
            }
            if (row.userId() != null && !exists(row.userId())) {
                throw ApiException.validation("signatories." + i + ".user_id", "That person no longer exists.");
            }
            settings.put("signatory_" + row.roleKey(), row.userId() == null ? "" : String.valueOf(row.userId()));
        }
        changed("Updated authorized signatories", before, assignments());
        return Map.of("message", "Authorized signatories updated.", "signatories", configuration());
    }

    private Map<String, Object> assignments() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : SIGNATORY_ROLES.keySet()) m.put(k, userId(k));
        return m;
    }

    private boolean exists(Long userId) {
        Integer n = jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, userId);
        return n != null && n > 0;
    }

    // ---------------------------------------------------------------- document logos

    @GetMapping("/document-logos")
    public Map<String, Object> documentLogos() {
        return Map.of("logos", logoRows(), "positions", POSITIONS);
    }

    private List<Map<String, Object>> logoRows() {
        return jdbc.query("select id, path, position, sequence, label from document_logos order by position, sequence, id", (rs, i) -> logo(rs.getLong(1),
                rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)));
    }

    private Map<String, Object> logo(Long id, String path, String position, int sequence, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("position", position);
        m.put("sequence", sequence);
        m.put("label", label);
        m.put("data_uri", logos.dataUri(path));
        return m;
    }

    @PostMapping("/document-logos")
    @PreAuthorize("@perm.any('edit_settings', 'manage_agency_profile')")
    @Transactional
    public ResponseEntity<Map<String, Object>> storeLogo(@RequestParam(required = false) MultipartFile logo,
                                                         @RequestParam(required = false) String position,
                                                         @RequestParam(required = false) String label) {
        if (position == null || !POSITIONS.contains(position)) throw ApiException.validation("position", "Choose where the logo goes.");
        String cleanLabel = label == null || label.isBlank() ? null : label.trim();
        if (cleanLabel != null && cleanLabel.length() > 100) throw ApiException.validation("label", "Keep the label under 100 characters.");
        String path = logos.save(logo);
        Integer next = jdbc.queryForObject("select coalesce(max(sequence), 0) + 1 from document_logos where position = ?", Integer.class, position);
        Long id = jdbc.queryForObject("""
                insert into document_logos (path, position, sequence, label, created_at, updated_at)
                values (?, ?, ?, ?, now(), now()) returning id
                """, Long.class, path, position, next, cleanLabel);
        audit.record("created", "App\\Models\\DocumentLogo", id, "Added a document logo (" + position + ")", null,
                Map.of("position", position, "label", Objects.toString(cleanLabel, "")));
        return ResponseEntity.status(HttpStatus.CREATED).body(logo(id, path, position, next == null ? 1 : next, cleanLabel));
    }

    public record LogoInput(String position, Integer sequence, String label) {
    }

    @PutMapping("/document-logos/{id}")
    @PreAuthorize("@perm.any('edit_settings', 'manage_agency_profile')")
    @Transactional
    public Map<String, Object> updateLogo(@PathVariable Long id, @RequestBody LogoInput in) {
        Map<String, Object> row = logoRow(id);
        if (in.position() != null && !POSITIONS.contains(in.position())) throw ApiException.validation("position", "Choose where the logo goes.");
        if (in.sequence() != null && in.sequence() < 0) throw ApiException.validation("sequence", "Order cannot be negative.");
        String label = in.label() == null || in.label().isBlank() ? null : in.label().trim();
        if (label != null && label.length() > 100) throw ApiException.validation("label", "Keep the label under 100 characters.");
        jdbc.update("update document_logos set position = coalesce(?, position), sequence = coalesce(?, sequence), label = ?, updated_at = now() where id = ?",
                in.position(), in.sequence(), label, id);
        Map<String, Object> after = logoRow(id);
        audit.record("updated", "App\\Models\\DocumentLogo", id, "Updated a document logo",
                Map.of("position", row.get("position"), "sequence", row.get("sequence"), "label", Objects.toString(row.get("label"), "")),
                Map.of("position", after.get("position"), "sequence", after.get("sequence"), "label", Objects.toString(after.get("label"), "")));
        return after;
    }

    @DeleteMapping("/document-logos/{id}")
    @PreAuthorize("@perm.any('edit_settings', 'manage_agency_profile')")
    @Transactional
    public Map<String, Object> destroyLogo(@PathVariable Long id) {
        Map<String, Object> row = logoRow(id);
        String path = jdbc.queryForObject("select path from document_logos where id = ?", String.class, id);
        jdbc.update("delete from document_logos where id = ?", id);
        logos.delete(path);
        audit.record("deleted", "App\\Models\\DocumentLogo", id, "Removed a document logo (" + row.get("position") + ")",
                Map.of("position", row.get("position"), "label", Objects.toString(row.get("label"), "")), null);
        return Map.of("message", "Logo removed.");
    }

    private Map<String, Object> logoRow(Long id) {
        List<Map<String, Object>> rows = jdbc.query("select id, path, position, sequence, label from document_logos where id = ?",
                (rs, i) -> logo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)), id);
        if (rows.isEmpty()) throw ApiException.notFound("Logo");
        return rows.get(0);
    }

    // ---------------------------------------------------------------- helpers

    /** Audits only the keys that actually changed. */
    private void changed(String description, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> was = new LinkedHashMap<>();
        Map<String, Object> now = new LinkedHashMap<>();
        for (String k : after.keySet()) {
            if (!Objects.equals(String.valueOf(before.get(k)), String.valueOf(after.get(k)))) {
                was.put(k, before.get(k));
                now.put(k, after.get(k));
            }
        }
        if (!now.isEmpty()) audit.record("updated", TYPE, null, description + ": " + String.join(", ", now.keySet()), was, now);
    }
}

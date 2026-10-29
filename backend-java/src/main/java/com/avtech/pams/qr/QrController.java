package com.avtech.pams.qr;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.setting.SettingService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QR codes and printable labels for items and assets (TOR C 3.2, 18.1), the gate-pass QR, the
 * in-app scanner lookup (keyboard-wedge scanner or camera, TOR 12.1) and the public scan page data.
 */
@RestController
public class QrController {

    private static final String ITEMS = "@perm.any('view_inventory', 'view_warehouse', 'manage_warehouse')";
    private static final String ASSETS = "@perm.any('view_properties', 'view_own_properties')";
    private static final Pattern SCAN_URL = Pattern.compile("/scan/(item|property|gate-pass)/(\\d+)");

    private final JdbcTemplate jdbc;
    private final QrService qr;
    private final SettingService settings;
    private final AuditService audit;
    private final CurrentUser current;

    public QrController(JdbcTemplate jdbc, QrService qr, SettingService settings, AuditService audit, CurrentUser current) {
        this.jdbc = jdbc;
        this.qr = qr;
        this.settings = settings;
        this.audit = audit;
        this.current = current;
    }

    /** What goes on a sticker. */
    public record Label(String type, Long id, String code, String title, String subtitle, String accountability, String url, String dataUri) {
    }

    // ───────────────────────── items ─────────────────────────

    @GetMapping("/api/inventory/items/{id}/qr")
    @PreAuthorize(ITEMS)
    public ResponseEntity<byte[]> itemPng(@PathVariable long id, @RequestParam(defaultValue = "300") int size) {
        Label l = itemLabel(id, size);
        return png(qr.png(l.url(), size));
    }

    @GetMapping({"/api/inventory/items/{id}/qr-base64", "/api/inventory/items/{id}/qr/printable"})
    @PreAuthorize(ITEMS)
    public Label itemLabel(@PathVariable long id, @RequestParam(defaultValue = "300") int size) {
        List<Label> l = jdbc.query("""
                select i.id, i.code, i.name, c.name, i.unit_of_measure from items i left join categories c on c.id = i.category_id where i.id = ?""",
                (rs, n) -> label("item", rs.getLong(1), rs.getString(2), rs.getString(3),
                        (rs.getString(4) == null ? "" : rs.getString(4) + " · ") + rs.getString(5), null, size), id);
        if (l.isEmpty()) throw ApiException.notFound("Item");
        return l.get(0);
    }

    /** Codes are made on request, so "regenerate" only re-issues the label; it is kept for the old API. */
    @PostMapping("/api/inventory/items/{id}/qr/regenerate")
    @PreAuthorize(ITEMS)
    public Label regenerateItem(@PathVariable long id) {
        return itemLabel(id, QrService.DEFAULT_SIZE);
    }

    public record BulkInput(List<Long> itemIds, List<Long> propertyIds) {
    }

    @PostMapping("/api/inventory/bulk-qr")
    @PreAuthorize(ITEMS)
    public Map<String, Object> bulkItems(@RequestBody BulkInput in) {
        List<Long> ids = in.itemIds() == null ? List.of() : in.itemIds().stream().distinct().toList();
        if (ids.isEmpty() || ids.size() > 200) throw ApiException.validation("item_ids", "Choose 1 to 200 items.");
        List<Label> out = new ArrayList<>();
        for (Long id : ids) out.add(itemLabel(id, 240));
        audit.record("printed", null, null, "Prepared " + out.size() + " item QR label(s)", null, Map.of("count", out.size()));
        return Map.of("agency", settings.get("agency_name", ""), "labels", out);
    }

    // ───────────────────────── assets ─────────────────────────

    @GetMapping("/api/properties/{id}/qr")
    @PreAuthorize(ASSETS)
    public ResponseEntity<byte[]> propertyPng(@PathVariable long id, @RequestParam(defaultValue = "300") int size) {
        return png(qr.png(propertyLabel(id, size).url(), size));
    }

    @GetMapping({"/api/properties/{id}/qr-base64", "/api/properties/{id}/qr/printable"})
    @PreAuthorize(ASSETS)
    public Label propertyLabel(@PathVariable long id, @RequestParam(defaultValue = "300") int size) {
        List<Label> l = jdbc.query("""
                select p.id, p.tag_number, i.name, p.serial_number, p.accountability_number, p.assigned_to
                from properties p left join items i on i.id = p.item_id where p.id = ?""", (rs, n) -> {
            Long holder = (Long) rs.getObject(6);
            if (!current.any("view_properties") && (holder == null || !holder.equals(current.user().getId()))) throw ApiException.forbidden("You can only print the label of an asset you hold.");
            return label("property", rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getString(4) == null ? null : "S/N " + rs.getString(4), rs.getString(5), size);
        }, id);
        if (l.isEmpty()) throw ApiException.notFound("Property");
        return l.get(0);
    }

    @PostMapping("/api/properties/{id}/qr/regenerate")
    @PreAuthorize(ASSETS)
    public Label regenerateProperty(@PathVariable long id) {
        return propertyLabel(id, QrService.DEFAULT_SIZE);
    }

    /** A sheet of stickers for several assets at once. */
    @PostMapping("/api/properties/qr-labels")
    @PreAuthorize("@perm.any('view_properties')")
    public Map<String, Object> bulkProperties(@RequestBody BulkInput in) {
        List<Long> ids = in.propertyIds() == null ? List.of() : in.propertyIds().stream().distinct().toList();
        if (ids.isEmpty() || ids.size() > 200) throw ApiException.validation("property_ids", "Choose 1 to 200 assets.");
        List<Label> out = new ArrayList<>();
        for (Long id : ids) out.add(propertyLabel(id, 240));
        audit.record("printed", null, null, "Prepared " + out.size() + " asset QR sticker(s)", null, Map.of("count", out.size()));
        return Map.of("agency", settings.get("agency_name", ""), "labels", out);
    }

    // ───────────────────────── gate pass ─────────────────────────

    @GetMapping("/api/gate-pass/{id}/qr")
    @PreAuthorize("@perm.any('view_gate_pass')")
    public Map<String, Object> gatePassQr(@PathVariable long id) {
        List<Map<String, Object>> g = jdbc.queryForList("select gate_pass_number, property_id from gate_passes where id = ? and deleted_at is null", id);
        if (g.isEmpty()) throw ApiException.notFound("Gate pass");
        String number = (String) g.get(0).get("gate_pass_number");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gate_pass", label("gate-pass", id, number, "Gate Pass " + number, null, null, 240));
        Object prop = g.get(0).get("property_id");
        out.put("unit", prop == null ? null : propertyLabel(((Number) prop).longValue(), 240));
        return out;
    }

    // ───────────────────────── scanning ─────────────────────────

    public record ScanInput(String data) {
    }

    /**
     * Resolves what a scanner read — one of our QR links, an item code, an asset tag/serial, or a
     * gate-pass number — to the record. Links must carry a valid checksum.
     */
    @PostMapping("/api/inventory/scan-qr")
    @PreAuthorize("@perm.any('view_inventory', 'view_warehouse', 'manage_warehouse', 'view_properties', 'view_own_properties', 'view_gate_pass')")
    public Map<String, Object> scan(@RequestBody ScanInput in) {
        Map<String, Object> rec = resolve(in);
        String type = (String) rec.get("type");
        boolean allowed = switch (type) {
            case "item" -> current.any("view_inventory", "view_warehouse", "manage_warehouse");
            case "property" -> current.any("view_properties") || current.user().getId().equals(rec.get("holder_id"));
            default -> current.any("view_gate_pass");
        };
        if (!allowed) throw ApiException.forbidden("You do not have access to that record.");
        rec.remove("holder_id");
        return rec;
    }

    private Map<String, Object> resolve(ScanInput in) {
        String raw = in.data() == null ? "" : in.data().trim();
        if (raw.isEmpty()) throw ApiException.validation("data", "Scan or type a code.");
        Matcher m = SCAN_URL.matcher(raw);
        if (m.find()) {
            String type = m.group(1);
            long id = Long.parseLong(m.group(2));
            String c = null;
            try {
                String q = URI.create(raw).getQuery();
                if (q != null) for (String part : q.split("&")) if (part.startsWith("c=")) c = part.substring(2);
            } catch (IllegalArgumentException ignored) {
                // Not a well-formed URL; the checksum check below fails.
            }
            Map<String, Object> rec = lookup(type, id);
            if (rec == null || !qr.verify(type, id, (String) rec.get("code"), c)) throw ApiException.validation("data", "This QR code is not valid for this system.");
            return rec;
        }
        Long id = jdbc.query("select id from properties where lower(tag_number) = lower(?) or lower(serial_number) = lower(?) order by id offset 0 rows fetch next 1 rows only",
                rs -> rs.next() ? rs.getLong(1) : null, raw, raw);
        if (id != null) return lookup("property", id);
        id = jdbc.query("select id from items where lower(code) = lower(?) order by id offset 0 rows fetch next 1 rows only", rs -> rs.next() ? rs.getLong(1) : null, raw);
        if (id != null) return lookup("item", id);
        id = jdbc.query("select id from gate_passes where lower(gate_pass_number) = lower(?) and deleted_at is null order by id offset 0 rows fetch next 1 rows only", rs -> rs.next() ? rs.getLong(1) : null, raw);
        if (id != null) return lookup("gate-pass", id);
        throw ApiException.of(org.springframework.http.HttpStatus.NOT_FOUND, "Nothing matches “" + raw + "”.");
    }

    /** The public page behind a QR link: minimal facts, only with a valid checksum. */
    @GetMapping("/api/public/qr/{type}/{id}")
    public Map<String, Object> publicScan(@PathVariable String type, @PathVariable long id, @RequestParam(name = "c", required = false) String c) {
        Map<String, Object> rec = lookup(type, id);
        if (rec == null || !qr.verify(type, id, (String) rec.get("code"), c)) throw ApiException.notFound("Record");
        // Anyone holding the label can open this page: show what confirms the label, not who holds the asset.
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : PUBLIC.getOrDefault(type, List.of())) if (rec.get(k) != null) out.put(k, rec.get(k));
        out.put("agency", settings.get("agency_name", ""));
        return out;
    }

    private static final Map<String, List<String>> PUBLIC = Map.of(
            "item", List.of("type", "code", "title", "category", "unit_of_measure", "path"),
            "property", List.of("type", "code", "title", "serial_number", "brand", "status", "condition", "department", "path"),
            "gate-pass", List.of("type", "code", "title", "status", "purpose", "received_by", "date_released", "expected_return_date",
                    "actual_return_date", "tag_number", "item", "path"));

    private Map<String, Object> lookup(String type, long id) {
        List<Map<String, Object>> rows = switch (type) {
            case "item" -> jdbc.queryForList("""
                    select 'item' as type, i.id, i.code, i.name as title, c.name as category, i.unit_of_measure, i.current_stock, i.min_stock,
                           '/items' as path from items i left join categories c on c.id = i.category_id where i.id = ?""", id);
            case "property" -> jdbc.queryForList("""
                    select 'property' as type, p.id, p.tag_number as code, i.name as title, p.serial_number, p.brand, p.status, p.condition,
                           u.name as custodian, d.name as department, p.accountability_number, p.acquisition_date, p.assigned_to as holder_id,
                           '/properties' as path
                    from properties p left join items i on i.id = p.item_id left join users u on u.id = p.assigned_to
                    left join departments d on d.id = u.department_id where p.id = ?""", id);
            case "gate-pass" -> jdbc.queryForList("""
                    select 'gate-pass' as type, g.id, g.gate_pass_number as code, concat('Gate Pass ', g.gate_pass_number) as title, g.status, g.purpose,
                           g.received_by, g.date_released, g.expected_return_date, g.actual_return_date, p.tag_number, i.name as item, '/gate-pass' as path
                    from gate_passes g left join properties p on p.id = g.property_id left join items i on i.id = p.item_id
                    where g.id = ? and g.deleted_at is null""", id);
            default -> List.of();
        };
        if (rows.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        rows.get(0).forEach((k, v) -> out.put(k, v instanceof java.sql.Date d ? d.toLocalDate().toString() : v instanceof java.sql.Timestamp t ? t.toLocalDateTime().toString() : v));
        return out;
    }

    private Label label(String type, long id, String code, String title, String subtitle, String accountability, int size) {
        String url = qr.url(type, id, code);
        return new Label(type, id, code, title, subtitle, accountability, url, qr.dataUri(url, size));
    }

    private static ResponseEntity<byte[]> png(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(bytes);
    }
}

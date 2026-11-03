package com.avtech.pams.integration;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Inserts;
import com.avtech.pams.common.Times;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Other systems' APIs, set up by an administrator and shown read-only in PAMS (TOR: integration with the agency's
 * other systems). First for payments: the accounting or disbursement system's records, either as a list of their
 * own or matched to a PAMS purchase order or contract through {reference}, {po_number}, {contract_number},
 * {supplier_tin} or {supplier_name} in the path.
 */
@RestController
@RequestMapping("/api/integrations")
public class ExternalApiController {

    private static final String TYPE = "App\\Models\\ExternalApi";
    private static final String ADMIN = "@perm.any('edit_settings', 'manage_system_settings')";
    private static final String VIEW = "@perm.any('view_payments', 'manage_payments', 'approve_payments', 'edit_settings', 'manage_system_settings')";
    static final Set<String> AUTH_TYPES = Set.of("none", "bearer", "header", "basic");
    static final Set<String> CONTEXTS = Set.of("none", "payable");
    private static final Set<String> RESERVED_HEADERS = Set.of("host", "content-length", "connection", "expect", "upgrade");

    private final JdbcTemplate jdbc;
    private final Db db;
    private final ExternalApiClient client;
    private final ObjectMapper json;
    private final CurrentUser current;
    private final AuditService audit;

    public ExternalApiController(JdbcTemplate jdbc, Db db, ExternalApiClient client, ObjectMapper json, CurrentUser current, AuditService audit) {
        this.jdbc = jdbc;
        this.db = db;
        this.client = client;
        this.json = json;
        this.current = current;
        this.audit = audit;
    }

    public record ApiView(Long id, String name, String description, String category, String baseUrl, String path, String authType, String authHeader,
                          boolean hasSecret, String dataPath, List<ExternalApiClient.Column> columns, String context, int timeoutSeconds, boolean isActive,
                          String lastStatus, OffsetDateTime lastCalledAt) {
    }

    public record DataView(ApiView api, ExternalApiClient.Result result, String error, OffsetDateTime fetchedAt) {
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public Map<String, Object> index() {
        boolean admin = current.any("edit_settings", "manage_system_settings");
        List<ApiView> rows = jdbc.queryForList("select * from external_apis order by name").stream().map(this::view)
                .filter(v -> admin || v.isActive()).map(v -> admin ? v : hideSetup(v)).toList();
        return Map.of("data", rows, "can_manage", admin);
    }

    public record ApiInput(String name, String description, String baseUrl, String path, String authType, String authHeader, String secret,
                           Boolean clearSecret, String dataPath, List<ExternalApiClient.Column> columns, String context, Integer timeoutSeconds, Boolean isActive) {
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    @Transactional
    public ResponseEntity<ApiView> store(@RequestBody ApiInput in) {
        Map<String, Object> v = validate(in, null);
        Timestamp now = Timestamp.valueOf(Times.now());
        Long id = Inserts.insert(jdbc, """
                insert into external_apis (name, description, category, base_url, path, auth_type, auth_header, secret_cipher, data_path, columns, context,
                                           timeout_seconds, is_active, created_by, created_at, updated_at)
                values (?, ?, 'payments', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", v.get("name"), v.get("description"), v.get("base_url"), v.get("path"),
                v.get("auth_type"), v.get("auth_header"), client.encrypt(in.secret()), v.get("data_path"), v.get("columns"), v.get("context"),
                v.get("timeout_seconds"), v.get("is_active"), current.user().getId(), now, now);
        audit.record("created", TYPE, id, "Connected the external API " + v.get("name") + " (" + v.get("base_url") + ")", null, Map.of("base_url", v.get("base_url")));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(row(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    @Transactional
    public ApiView update(@PathVariable Long id, @RequestBody ApiInput in) {
        Map<String, Object> before = row(id);
        Map<String, Object> v = validate(in, id);
        String cipher = Boolean.TRUE.equals(in.clearSecret()) ? null : in.secret() != null && !in.secret().isEmpty() ? client.encrypt(in.secret()) : (String) before.get("secret_cipher");
        jdbc.update("""
                update external_apis set name = ?, description = ?, base_url = ?, path = ?, auth_type = ?, auth_header = ?, secret_cipher = ?, data_path = ?,
                       columns = ?, context = ?, timeout_seconds = ?, is_active = ?, updated_at = ? where id = ?""", v.get("name"), v.get("description"), v.get("base_url"),
                v.get("path"), v.get("auth_type"), v.get("auth_header"), cipher, v.get("data_path"), v.get("columns"), v.get("context"), v.get("timeout_seconds"),
                v.get("is_active"), Timestamp.valueOf(Times.now()), id);
        audit.record("updated", TYPE, id, "Changed the external API " + v.get("name") + (cipher == null ? "" : in.secret() != null && !in.secret().isEmpty() ? " (new secret)" : ""),
                Map.of("base_url", before.get("base_url"), "path", before.get("path")), Map.of("base_url", v.get("base_url"), "path", v.get("path")));
        return view(row(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ADMIN)
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        Map<String, Object> r = row(id);
        jdbc.update("delete from external_apis where id = ?", id);
        audit.record("deleted", TYPE, id, "Removed the external API " + r.get("name"), Map.of("base_url", r.get("base_url")), null);
        return Map.of("message", r.get("name") + " removed.");
    }

    public record TestInput(Map<String, String> values) {
    }

    /** Calls the API once with sample values for its placeholders, to check the address, credentials and columns. */
    @PostMapping("/{id}/test")
    @PreAuthorize(ADMIN)
    public DataView test(@PathVariable Long id, @RequestBody(required = false) TestInput in) {
        Map<String, Object> r = row(id);
        return call(r, in == null || in.values() == null ? Map.of() : in.values());
    }

    /** The records of an API with no PAMS context: a list of its own. */
    @GetMapping("/{id}/data")
    @PreAuthorize(VIEW)
    public DataView data(@PathVariable Long id) {
        Map<String, Object> r = row(id);
        if (!Boolean.TRUE.equals(bool(r.get("is_active")))) throw ApiException.of(HttpStatus.CONFLICT, r.get("name") + " is switched off.");
        if (!"none".equals(r.get("context"))) throw ApiException.of(HttpStatus.CONFLICT, r.get("name") + " is read from a purchase order or contract.");
        return hideSetup(call(r, Map.of()));
    }

    /** What every active payment API holds for one purchase order or contract. */
    @GetMapping("/payable")
    @PreAuthorize(VIEW)
    public Map<String, Object> payable(@RequestParam String type, @RequestParam Long id) {
        Map<String, String> values = payableValues(type, id);
        List<DataView> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("select * from external_apis where context = 'payable' and is_active = " + db.t() + " order by name")) {
            out.add(hideSetup(call(r, values)));
        }
        return Map.of("data", out, "reference", values.get("reference"));
    }

    // ───────────────────────── helpers ─────────────────────────

    private DataView call(Map<String, Object> r, Map<String, String> values) {
        ApiView v = view(r);
        Long id = ((Number) r.get("id")).longValue();
        OffsetDateTime at = Times.toOffset(Times.now());
        try {
            ExternalApiClient.Result result = client.fetch(new ExternalApiClient.Call(v.baseUrl(), v.path(), v.authType(), v.authHeader(),
                    client.decrypt((String) r.get("secret_cipher")), v.dataPath(), v.columns(), v.timeoutSeconds()), values);
            status(id, "OK: " + result.rows().size() + " record(s)");
            return new DataView(view(row(id)), result, null, at);
        } catch (ApiException e) {
            status(id, "Failed: " + e.getMessage());
            return new DataView(view(row(id)), null, e.getMessage(), at);
        }
    }

    private void status(Long id, String text) {
        jdbc.update("update external_apis set last_status = ?, last_called_at = ? where id = ?", text.length() > 300 ? text.substring(0, 300) : text, Timestamp.valueOf(Times.now()), id);
    }

    /** The PO or contract's numbers and supplier, read here so a caller cannot put arbitrary text into another system's address. */
    private Map<String, String> payableValues(String type, Long id) {
        List<Map<String, Object>> rows;
        if ("purchase_order".equals(type)) {
            if (!current.any("view_payments", "manage_payments", "approve_payments", "view_procurement", "edit_settings", "manage_system_settings")) throw ApiException.forbidden("You cannot view this purchase order.");
            rows = jdbc.queryForList("""
                    select po.po_number, null as contract_number, s.tin, s.name from purchase_orders po left join suppliers s on s.id = po.supplier_id
                    where po.id = ? and po.deleted_at is null""", id);
            if (rows.isEmpty()) throw ApiException.notFound("Purchase order");
            Map<String, Object> m = rows.get(0);
            return ExternalApiClient.payableValues((String) m.get("po_number"), (String) m.get("po_number"), null, (String) m.get("tin"), (String) m.get("name"));
        }
        if ("contract".equals(type)) {
            rows = jdbc.queryForList("""
                    select c.contract_number, po.po_number, s.tin, s.name from contracts c left join purchase_orders po on po.id = c.purchase_order_id
                    left join suppliers s on s.id = c.supplier_id where c.id = ?""", id);
            if (rows.isEmpty()) throw ApiException.notFound("Contract");
            Map<String, Object> m = rows.get(0);
            return ExternalApiClient.payableValues((String) m.get("contract_number"), (String) m.get("po_number"), (String) m.get("contract_number"), (String) m.get("tin"), (String) m.get("name"));
        }
        throw ApiException.validation("type", "Choose purchase_order or contract.");
    }

    private Map<String, Object> validate(ApiInput in, Long id) {
        Map<String, Object> v = new LinkedHashMap<>();
        String name = text(in.name());
        if (name == null || name.length() > 120) throw ApiException.validation("name", "Name the connection, up to 120 characters.");
        Integer clash = jdbc.queryForObject("select count(*) from external_apis where lower(name) = lower(?) and id <> ?", Integer.class, name, id == null ? -1L : id);
        if (clash != null && clash > 0) throw ApiException.validation("name", "Another connection has that name.");
        String base = text(in.baseUrl());
        if (base == null || base.length() > 500) throw ApiException.validation("base_url", "Enter the other system's address.");
        String path = in.path() == null ? "" : in.path().trim();
        if (path.length() > 500) throw ApiException.validation("path", "The path is too long.");
        String context = in.context() == null ? "none" : in.context();
        if (!CONTEXTS.contains(context)) throw ApiException.validation("context", "Choose a list of its own, or by purchase order or contract.");
        Set<String> allowed = "payable".equals(context) ? Set.of("reference", "po_number", "contract_number", "supplier_tin", "supplier_name") : Set.of();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([a-z_]+)}").matcher(path);
        while (m.find()) {
            if (!allowed.contains(m.group(1))) {
                throw ApiException.validation("path", "{" + m.group(1) + "} cannot be used here." + ("payable".equals(context) ? " Use {reference}, {po_number}, {contract_number}, {supplier_tin} or {supplier_name}." : " Read it by purchase order or contract to use placeholders."));
            }
        }
        client.checkAddress(java.net.URI.create(base.replaceAll("/+$", "")));
        String auth = in.authType() == null ? "none" : in.authType();
        if (!AUTH_TYPES.contains(auth)) throw ApiException.validation("auth_type", "Choose none, bearer token, header key or basic.");
        String header = text(in.authHeader());
        if ("header".equals(auth)) {
            if (header == null || !header.matches("[A-Za-z0-9-]{1,100}") || RESERVED_HEADERS.contains(header.toLowerCase())) {
                throw ApiException.validation("auth_header", "Enter the header name the other system expects, e.g. X-API-Key.");
            }
        } else {
            header = null;
        }
        if (!"none".equals(auth) && id == null && (in.secret() == null || in.secret().isEmpty())) throw ApiException.validation("secret", "Enter the key or token the other system gave you.");
        String dataPath = text(in.dataPath());
        if (dataPath != null && (dataPath.length() > 200 || !dataPath.matches("[A-Za-z0-9_.-]+"))) throw ApiException.validation("data_path", "Write the path to the records as names joined by dots, e.g. data.items.");
        List<ExternalApiClient.Column> cols = in.columns() == null ? List.of() : in.columns().stream()
                .filter(c -> c != null && text(c.label()) != null).map(c -> new ExternalApiClient.Column(c.label().trim(), c.field() == null ? "" : c.field().trim())).toList();
        if (cols.size() > 20) throw ApiException.validation("columns", "Show up to 20 columns.");
        for (ExternalApiClient.Column c : cols) {
            if (c.label().length() > 80 || !c.field().matches("[A-Za-z0-9_.-]*")) throw ApiException.validation("columns", "Each column needs a heading and a field written as names joined by dots.");
        }
        int timeout = in.timeoutSeconds() == null ? 10 : in.timeoutSeconds();
        if (timeout < 1 || timeout > 60) throw ApiException.validation("timeout_seconds", "Wait between 1 and 60 seconds.");
        v.put("name", name);
        v.put("description", text(in.description()));
        v.put("base_url", base.replaceAll("/+$", ""));
        v.put("path", path);
        v.put("auth_type", auth);
        v.put("auth_header", header);
        v.put("data_path", dataPath);
        v.put("columns", cols.isEmpty() ? null : json.writeValueAsString(cols));
        v.put("context", context);
        v.put("timeout_seconds", timeout);
        v.put("is_active", in.isActive() == null || in.isActive());
        return v;
    }

    private Map<String, Object> row(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from external_apis where id = ?", id);
        if (rows.isEmpty()) throw ApiException.notFound("External API");
        return rows.get(0);
    }

    private ApiView view(Map<String, Object> r) {
        String cols = (String) r.get("columns");
        List<ExternalApiClient.Column> columns = cols == null ? List.of() : json.readValue(cols, new TypeReference<List<ExternalApiClient.Column>>() { });
        Object last = r.get("last_called_at");
        return new ApiView(((Number) r.get("id")).longValue(), (String) r.get("name"), (String) r.get("description"), (String) r.get("category"), (String) r.get("base_url"),
                (String) r.get("path"), (String) r.get("auth_type"), (String) r.get("auth_header"), r.get("secret_cipher") != null, (String) r.get("data_path"), columns,
                (String) r.get("context"), ((Number) r.get("timeout_seconds")).intValue(), Boolean.TRUE.equals(bool(r.get("is_active"))), (String) r.get("last_status"),
                last == null ? null : Times.toOffset(((Timestamp) last).toLocalDateTime()));
    }

    /** People who only view payments see the name and data, not where it comes from or how PAMS signs in. */
    private ApiView hideSetup(ApiView v) {
        if (current.any("edit_settings", "manage_system_settings")) return v;
        return new ApiView(v.id(), v.name(), v.description(), v.category(), null, null, null, null, false, null, v.columns(), v.context(), v.timeoutSeconds(),
                v.isActive(), null, v.lastCalledAt());
    }

    private DataView hideSetup(DataView d) {
        return new DataView(hideSetup(d.api()), d.result(), d.error(), d.fetchedAt());
    }

    private static Boolean bool(Object o) {
        return o instanceof Boolean b ? b : o instanceof Number n ? n.intValue() != 0 : null;
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

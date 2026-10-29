package com.avtech.pams.dataio;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.auth.PasswordPolicy;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.dataio.ImportRow.DepartmentPlan;
import com.avtech.pams.dataio.ImportRow.ItemPlan;
import com.avtech.pams.dataio.ImportRow.SupplierPlan;
import com.avtech.pams.dataio.ImportRow.UserPlan;
import com.avtech.pams.dataio.ImportValidator.Parsed;
import com.avtech.pams.dataio.ImportValidator.UploadException;
import com.avtech.pams.department.Department;
import com.avtech.pams.department.DepartmentRepository;
import com.avtech.pams.inventory.Category;
import com.avtech.pams.inventory.CategoryRepository;
import com.avtech.pams.inventory.InventoryService;
import com.avtech.pams.inventory.InventoryService.ItemInput;
import com.avtech.pams.inventory.InventoryService.LotInput;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.role.RoleRepository;
import com.avtech.pams.setting.SettingService;
import com.avtech.pams.supplier.Supplier;
import com.avtech.pams.supplier.SupplierRepository;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Two-step bulk import of master data. Validation (a dry run) stores the upload as a batch with its
 * per-row exceptions; commit re-checks every row against the data as it is then and creates the ok
 * rows in one transaction. Any failure while creating rolls the whole batch back and records it as
 * aborted, with the row that stopped it.
 */
@Service
public class DataImportService {

    static final String VALIDATED = "validated";
    static final String COMMITTED = "committed";
    static final String ABORTED = "aborted";
    private static final int MAX_BYTES = 25 * 1024 * 1024;
    private static final int PER_PAGE = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ImportValidator validator;
    private final InventoryService inventory;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final RoleRepository roles;
    private final DocumentNumbers numbers;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SettingService settings;
    private final AuditService audit;
    private final CurrentUser current;

    public DataImportService(JdbcTemplate jdbc, TransactionTemplate tx, ImportValidator validator, InventoryService inventory,
                             CategoryRepository categories, SupplierRepository suppliers, DepartmentRepository departments,
                             UserRepository users, RoleRepository roles, DocumentNumbers numbers, PasswordEncoder passwordEncoder,
                             PasswordPolicy passwordPolicy, SettingService settings, AuditService audit, CurrentUser current) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.validator = validator;
        this.inventory = inventory;
        this.categories = categories;
        this.suppliers = suppliers;
        this.departments = departments;
        this.users = users;
        this.roles = roles;
        this.numbers = numbers;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.settings = settings;
        this.audit = audit;
        this.current = current;
    }

    // ───────────────────────── validate (dry run) ─────────────────────────

    public Map<String, Object> validate(ImportEntity entity, String fileName, byte[] bytes, boolean createCategories) {
        String name = fileName == null || fileName.isBlank() ? entity.key + ".csv" : fileName.replaceAll("[\\\\/\\r\\n]", "_");
        if (name.length() > 255) name = name.substring(name.length() - 255);
        Map<String, Object> options = new LinkedHashMap<>();
        if (entity == ImportEntity.ITEMS) options.put("create_missing_categories", createCategories);

        String text;
        Parsed parsed;
        try {
            text = text(entity, bytes);
            parsed = validator.parse(entity, text);
        } catch (UploadException e) {
            long id = insert(entity, name, ABORTED, null, List.of(), options, e.getMessage(), null);
            audit.record("import_aborted", null, null, "Upload of " + name + " (" + entity.key + ") refused: " + e.getMessage(), null,
                    Map.of("import_batch_id", id, "entity", entity.key));
            throw ApiException.validation("file", e.getMessage()).with("batch", batch(id));
        }
        validator.validate(entity, parsed, createCategories);
        long id = insert(entity, name, VALIDATED, parsed, parsed.rows(), options, null, text);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batch", batch(id));
        out.put("ignored_columns", parsed.header().ignored());
        out.put("exceptions", parsed.rows().stream().filter(r -> !r.ok()).map(ImportRow::view).toList());
        out.put("preview", parsed.rows().stream().filter(ImportRow::ok).limit(50).map(ImportRow::view).toList());
        return out;
    }

    private static String text(ImportEntity entity, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new UploadException("The file is empty.");
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4) {
            String wanted = entity.key + ".csv";
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                    String base = e.getName().substring(e.getName().lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                    if (!e.isDirectory() && base.equals(wanted)) return Csv.decode(readCapped(zip));
                }
            } catch (IOException e) {
                throw new UploadException("The ZIP file could not be read: " + e.getMessage());
            }
            throw new UploadException("The ZIP file has no " + wanted + ".");
        }
        return Csv.decode(bytes);
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new UploadException("The file inside the ZIP is larger than 25 MB.");
        }
        return out.toByteArray();
    }

    private long insert(ImportEntity entity, String fileName, String status, Parsed parsed, List<ImportRow> rows,
                        Map<String, Object> options, String error, String payload) {
        int[] c = counts(rows);
        User me = current.user();
        Timestamp now = Timestamp.valueOf(Times.now());
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    insert into import_batches (entity, file_name, status, total_rows, ok_rows, duplicate_rows, incomplete_rows,
                      invalid_rows, imported_rows, options, exceptions, error_message, payload, created_by, created_by_name,
                      aborted_at, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", new String[]{"id"});
            int i = 1;
            ps.setString(i++, entity.key);
            ps.setString(i++, fileName);
            ps.setString(i++, status);
            ps.setInt(i++, parsed == null ? 0 : rows.size());
            for (int k = 0; k < 4; k++) ps.setInt(i++, c[k]);
            ps.setString(i++, JSON.writeValueAsString(options));
            ps.setString(i++, JSON.writeValueAsString(exceptions(rows)));
            ps.setString(i++, error);
            ps.setString(i++, payload);
            ps.setLong(i++, me.getId());
            ps.setString(i++, me.getName());
            ps.setTimestamp(i++, ABORTED.equals(status) ? now : null);
            ps.setTimestamp(i++, now);
            ps.setTimestamp(i, now);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** ok, duplicate, incomplete, invalid. */
    private static int[] counts(List<ImportRow> rows) {
        int[] c = new int[4];
        for (ImportRow r : rows) {
            switch (r.status()) {
                case ImportRow.OK -> c[0]++;
                case ImportRow.DUPLICATE -> c[1]++;
                case ImportRow.INCOMPLETE -> c[2]++;
                default -> c[3]++;
            }
        }
        return c;
    }

    private static List<Map<String, Object>> exceptions(List<ImportRow> rows) {
        return rows.stream().filter(r -> !r.ok()).map(ImportRow::view).toList();
    }

    // ───────────────────────── commit ─────────────────────────

    private enum Stage { LOCK, CHECK, IMPORT }

    public Map<String, Object> commit(long id) {
        Map<String, Object> b = row(id);
        if (!VALIDATED.equals(b.get("status")) || b.get("payload") == null) {
            throw ApiException.of(HttpStatus.CONFLICT, "This import is already " + b.get("status") + "; upload the file again to import it.");
        }
        ImportEntity entity = ImportEntity.of((String) b.get("entity"));
        String fileName = (String) b.get("file_name");
        Map<String, Object> options = map((String) b.get("options"));
        boolean createCategories = Boolean.TRUE.equals(options.get("create_missing_categories"));
        Stage[] stage = {Stage.LOCK};
        ImportRow[] at = {null};
        try {
            Map<String, Object> out = tx.execute(s -> {
                int locked = jdbc.update("update import_batches set updated_at = ? where id = ? and status = ?",
                        Timestamp.valueOf(Times.now()), id, VALIDATED);
                if (locked == 0) throw ApiException.of(HttpStatus.CONFLICT, "This import was committed or aborted meanwhile.");
                stage[0] = Stage.CHECK;
                Parsed parsed = validator.parse(entity, (String) b.get("payload"));
                validator.validate(entity, parsed, createCategories);
                List<ImportRow> ready = parsed.rows().stream().filter(ImportRow::ok).toList();
                if (ready.isEmpty()) {
                    throw ApiException.validation("rows", "No row is ready to import. Fix the exceptions and upload the file again.");
                }
                stage[0] = Stage.IMPORT;
                List<Map<String, Object>> created = new ArrayList<>();
                List<Map<String, Object>> passwords = new ArrayList<>();
                Map<String, Long> newCategories = new HashMap<>();
                for (ImportRow r : ready) {
                    at[0] = r;
                    created.add(create(r, newCategories, passwords));
                }
                at[0] = null;
                int[] c = counts(parsed.rows());
                LocalDateTime now = Times.now();
                jdbc.update("""
                                update import_batches set status = ?, total_rows = ?, ok_rows = ?, duplicate_rows = ?, incomplete_rows = ?,
                                  invalid_rows = ?, imported_rows = ?, exceptions = ?, payload = null, committed_by = ?, committed_at = ?,
                                  updated_at = ?
                                 where id = ?""",
                        COMMITTED, parsed.rows().size(), c[0], c[1], c[2], c[3], ready.size(),
                        JSON.writeValueAsString(exceptions(parsed.rows())), current.user().getId(),
                        Timestamp.valueOf(now), Timestamp.valueOf(now), id);
                Map<String, Object> scope = new LinkedHashMap<>();
                scope.put("import_batch_id", id);
                scope.put("entity", entity.key);
                scope.put("file_name", fileName);
                scope.put("imported", ready.size());
                scope.put("skipped", parsed.rows().size() - ready.size());
                if (!newCategories.isEmpty()) scope.put("categories_created", newCategories.size());
                audit.record("imported", null, null, "Imported " + ready.size() + " " + entity.label.toLowerCase(Locale.ROOT)
                        + " from " + fileName + " (import #" + id + ")", null, scope);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", "Imported " + ready.size() + " of " + parsed.rows().size() + " rows.");
                result.put("imported", ready.size());
                result.put("categories_created", newCategories.size());
                result.put("created", created);
                if (entity == ImportEntity.USERS) result.put("temporary_passwords", passwords);
                result.put("exceptions", exceptions(parsed.rows()));
                return result;
            });
            out.put("batch", batch(id));
            return out;
        } catch (RuntimeException e) {
            if (stage[0] != Stage.IMPORT) throw e;
            String reason = reason(e);
            ImportRow failed = at[0];
            abort(id, entity, fileName, failed, reason);
            String where = failed == null ? "" : " at row " + failed.row + (failed.key == null ? "" : " (" + failed.key + ")");
            throw ApiException.of(HttpStatus.UNPROCESSABLE_CONTENT, "The import stopped" + where + ": " + reason
                    + " Nothing was imported; the batch is recorded as aborted.").with("batch", batch(id));
        }
    }

    private Map<String, Object> create(ImportRow r, Map<String, Long> newCategories, List<Map<String, Object>> passwords) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", r.row);
        switch (r.plan) {
            case ItemPlan p -> {
                Long categoryId = p.categoryId();
                if (p.newCategory() != null) {
                    categoryId = newCategories.computeIfAbsent(p.newCategory().toLowerCase(Locale.ROOT), k -> {
                        Category c = new Category();
                        c.setName(p.newCategory());
                        categories.saveAndFlush(c);
                        audit.record("created", AuditService.CATEGORY, c.getId(), "Created Category " + c.getName() + " (bulk import)", null,
                                Map.of("name", c.getName(), "parent_id", "null"));
                        return c.getId();
                    });
                }
                List<LotInput> lots = p.quantity() == null ? List.of() : List.of(new LotInput(null, null, null, null, null, p.quantity(),
                        p.unitCost(), null, p.location(), null, null, LocalDate.now(Times.ZONE)));
                Item item = inventory.create(new ItemInput(p.code(), p.name(), p.description(), categoryId, p.unitOfMeasure(),
                        p.minStock(), p.maxStock(), p.classification(), p.assembly(), lots));
                m.put("id", item.getId());
                m.put("key", item.getCode() + " " + item.getName());
            }
            case SupplierPlan p -> {
                Supplier s = new Supplier();
                s.setName(p.name());
                s.setTin(p.tin());
                s.setPhilgepsRegNumber(p.philgepsRegNumber());
                s.setContactPerson(p.contactPerson());
                s.setEmail(p.email());
                s.setPhone(p.phone());
                s.setMobileNumber(p.mobileNumber());
                s.setAddress(p.address());
                s.setWebsite(p.website());
                s.setCreditTerms(p.creditTerms());
                s.setNotes(p.notes());
                s.setActive(p.active());
                s.setMsme(p.msme());
                s.setVendorCode(p.vendorCode() != null ? p.vendorCode() : numbers.series("VND-", 4, "suppliers.vendor_code"));
                suppliers.saveAndFlush(s);
                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("vendor_code", s.getVendorCode());
                snap.put("name", s.getName());
                snap.put("tin", s.getTin());
                snap.put("email", s.getEmail());
                snap.put("is_active", s.isActive());
                audit.record("created", "App\\Models\\Supplier", s.getId(),
                        "Registered supplier " + s.getName() + " (" + s.getVendorCode() + ") by bulk import", null, snap);
                m.put("id", s.getId());
                m.put("key", s.getVendorCode() + " " + s.getName());
            }
            case DepartmentPlan p -> {
                Department d = new Department();
                d.setCode(p.code());
                d.setName(p.name());
                departments.saveAndFlush(d);
                audit.record("created", "App\\Models\\Department", d.getId(), "Created Department " + d.getName() + " (bulk import)", null,
                        Map.of("code", d.getCode(), "name", d.getName()));
                m.put("id", d.getId());
                m.put("key", d.getCode() + " " + d.getName());
            }
            case UserPlan p -> {
                String password = temporaryPassword(p.email());
                User u = new User();
                u.setName(p.name());
                u.setEmail(p.email());
                u.setPasswordHash(passwordEncoder.encode(password));
                u.setPasswordChangedAt(Times.now());
                u.setRole(roles.findById(p.roleId()).orElseThrow(() -> new IllegalStateException("The role no longer exists.")));
                u.setDepartment(departments.findById(p.departmentId())
                        .orElseThrow(() -> new IllegalStateException("The department no longer exists.")));
                users.saveAndFlush(u);
                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("name", u.getName());
                snap.put("email", u.getEmail());
                snap.put("role", u.getRole().getName());
                snap.put("department", u.getDepartment().getName());
                audit.record("created", AuditService.USER, u.getId(), "Created User " + u.getName() + " (bulk import)", null, snap);
                m.put("id", u.getId());
                m.put("key", u.getEmail());
                Map<String, Object> pw = new LinkedHashMap<>();
                pw.put("row", r.row);
                pw.put("name", u.getName());
                pw.put("email", u.getEmail());
                pw.put("temporary_password", password);
                passwords.add(pw);
            }
            default -> throw new IllegalStateException("Nothing to import for row " + r.row);
        }
        return m;
    }

    /** Random, and checked against the same policy as a password typed by an administrator. */
    private String temporaryPassword(String email) {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digit = "23456789";
        String symbol = "#$%&*+=?@!";
        String all = upper + lower + digit + symbol;
        int length = Math.min(72, Math.max(16, settings.passwordMinLength()));
        for (int attempt = 0; ; attempt++) {
            List<Character> chars = new ArrayList<>();
            chars.add(upper.charAt(RANDOM.nextInt(upper.length())));
            chars.add(lower.charAt(RANDOM.nextInt(lower.length())));
            chars.add(digit.charAt(RANDOM.nextInt(digit.length())));
            chars.add(symbol.charAt(RANDOM.nextInt(symbol.length())));
            while (chars.size() < length) chars.add(all.charAt(RANDOM.nextInt(all.length())));
            java.util.Collections.shuffle(chars, RANDOM);
            StringBuilder sb = new StringBuilder();
            chars.forEach(sb::append);
            String pw = sb.toString();
            try {
                passwordPolicy.check("password", pw, pw, email);
                return pw;
            } catch (ApiException e) {
                if (attempt >= 20) throw e;
            }
        }
    }

    private void abort(long id, ImportEntity entity, String fileName, ImportRow failed, String reason) {
        tx.executeWithoutResult(s -> {
            Map<String, Object> b = row(id);
            List<Map<String, Object>> ex = new ArrayList<>(list((String) b.get("exceptions")));
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("row", failed == null ? null : failed.row);
            e.put("status", ABORTED);
            e.put("key", failed == null ? null : failed.key);
            e.put("messages", List.of(reason));
            e.put("data", failed == null ? Map.of() : failed.data);
            ex.add(e);
            String message = (failed == null ? "Stopped" : "Stopped at row " + failed.row) + ": " + reason + " Nothing was imported.";
            Timestamp now = Timestamp.valueOf(Times.now());
            jdbc.update("""
                            update import_batches set status = ?, exceptions = ?, error_message = ?, payload = null, imported_rows = 0,
                              aborted_at = ?, updated_at = ?
                             where id = ?""",
                    ABORTED, JSON.writeValueAsString(ex), message, now, now, id);
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("import_batch_id", id);
            scope.put("entity", entity.key);
            scope.put("file_name", fileName);
            if (failed != null) scope.put("row", failed.row);
            scope.put("reason", reason);
            audit.record("import_aborted", null, null, "Import #" + id + " of " + fileName + " aborted and rolled back: " + message, null, scope);
        });
    }

    private static String reason(RuntimeException e) {
        if (e instanceof ApiException ae) {
            Object errors = ae.getBody().get("errors");
            if (errors instanceof Map<?, ?> m && !m.isEmpty()) {
                Map.Entry<?, ?> first = m.entrySet().iterator().next();
                Object msgs = first.getValue();
                String msg = msgs instanceof List<?> l && !l.isEmpty() ? String.valueOf(l.getFirst()) : String.valueOf(msgs);
                return first.getKey() + ": " + msg;
            }
            return ae.getMessage();
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(e);
        String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        msg = msg.lines().findFirst().orElse(msg);
        return msg.length() > 500 ? msg.substring(0, 500) + "…" : msg;
    }

    // ───────────────────────── history ─────────────────────────

    private static final String COLUMNS = """
            b.id, b.entity, b.file_name, b.status, b.total_rows, b.ok_rows, b.duplicate_rows, b.incomplete_rows, b.invalid_rows,
            b.imported_rows, b.options, b.error_message, b.created_by, b.created_by_name, b.committed_by, b.committed_at,
            b.aborted_at, b.created_at, cu.name as committed_by_name""";

    public PageResponse<Map<String, Object>> list(int page, String entity, String status) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (entity != null && !entity.isBlank()) {
            where.append(" and b.entity = ?");
            args.add(ImportEntity.of(entity).key);
        }
        if (status != null && !status.isBlank()) {
            if (!Set.of(VALIDATED, COMMITTED, ABORTED).contains(status)) throw ApiException.validation("status", "Unknown status.");
            where.append(" and b.status = ?");
            args.add(status);
        }
        Long total = jdbc.queryForObject("select count(*) from import_batches b" + where, Long.class, args.toArray());
        int p = Math.max(1, page);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((p - 1) * PER_PAGE);
        pageArgs.add(PER_PAGE);
        List<Map<String, Object>> rows = jdbc.query("select " + COLUMNS
                        + " from import_batches b left join users cu on cu.id = b.committed_by" + where
                        + " order by b.created_at desc, b.id desc offset ? rows fetch next ? rows only",
                (rs, i) -> view(rs), pageArgs.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PageResponse.Pagination(t, PER_PAGE, p, (int) Math.max(1, (t + PER_PAGE - 1) / PER_PAGE)));
    }

    public Map<String, Object> batch(long id) {
        List<Map<String, Object>> rows = jdbc.query("select " + COLUMNS
                + " from import_batches b left join users cu on cu.id = b.committed_by where b.id = ?", (rs, i) -> view(rs), id);
        if (rows.isEmpty()) throw ApiException.notFound("Import");
        return rows.getFirst();
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> m = batch(id);
        m.put("exceptions", exceptionList(id));
        return m;
    }

    List<Map<String, Object>> exceptionList(long id) {
        return list((String) row(id).get("exceptions"));
    }

    /** The exception report: one line per row that was not imported, with its values. */
    public byte[] exceptionsCsv(long id) {
        Map<String, Object> b = batch(id);
        List<Map<String, Object>> ex = exceptionList(id);
        Set<String> columns = new LinkedHashSet<>();
        ImportEntity entity = ImportEntity.of((String) b.get("entity"));
        for (Map<String, Object> e : ex) {
            if (e.get("data") instanceof Map<?, ?> d) d.keySet().forEach(k -> columns.add(String.valueOf(k)));
        }
        List<String> ordered = new ArrayList<>();
        entity.columns.forEach(c -> {
            if (columns.remove(c.name())) ordered.add(c.name());
        });
        ordered.addAll(columns);
        StringBuilder sb = new StringBuilder(Csv.BOM);
        List<String> head = new ArrayList<>(List.of("Row", "Status", "Record", "Problems"));
        head.addAll(ordered);
        sb.append(String.join(",", head.stream().map(Csv::safe).toList())).append("\r\n");
        for (Map<String, Object> e : ex) {
            List<String> cells = new ArrayList<>();
            cells.add(Csv.safe(e.get("row") == null ? "" : String.valueOf(e.get("row"))));
            cells.add(Csv.safe(String.valueOf(e.get("status"))));
            cells.add(Csv.safe(e.get("key") == null ? "" : String.valueOf(e.get("key"))));
            cells.add(Csv.safe(e.get("messages") instanceof List<?> l ? String.join("; ", l.stream().map(String::valueOf).toList()) : ""));
            Map<?, ?> d = e.get("data") instanceof Map<?, ?> m ? m : Map.of();
            for (String c : ordered) cells.add(Csv.safe(d.get(c) == null ? "" : String.valueOf(d.get(c))));
            sb.append(String.join(",", cells)).append("\r\n");
        }
        audit.record("exported", null, null, "Downloaded the exception report of import #" + id + " (" + b.get("file_name") + ", "
                + ex.size() + " rows)", null, Map.of("import_batch_id", id, "rows", ex.size()));
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private Map<String, Object> row(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, entity, file_name, status, options, exceptions, payload from import_batches where id = ?", id);
        if (rows.isEmpty()) throw ApiException.notFound("Import");
        return rows.getFirst();
    }

    private static Map<String, Object> view(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("entity", rs.getString("entity"));
        m.put("file_name", rs.getString("file_name"));
        m.put("status", rs.getString("status"));
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", rs.getInt("total_rows"));
        counts.put("ok", rs.getInt("ok_rows"));
        counts.put("duplicate", rs.getInt("duplicate_rows"));
        counts.put("incomplete", rs.getInt("incomplete_rows"));
        counts.put("invalid", rs.getInt("invalid_rows"));
        counts.put("imported", rs.getInt("imported_rows"));
        m.put("counts", counts);
        m.put("options", map(rs.getString("options")));
        m.put("error_message", rs.getString("error_message"));
        Long by = (Long) rs.getObject("created_by", Long.class);
        m.put("created_by", by == null && rs.getString("created_by_name") == null ? null : ref(by, rs.getString("created_by_name")));
        Long cb = (Long) rs.getObject("committed_by", Long.class);
        m.put("committed_by", cb == null ? null : ref(cb, rs.getString("committed_by_name")));
        m.put("created_at", offset(rs.getTimestamp("created_at")));
        m.put("committed_at", offset(rs.getTimestamp("committed_at")));
        m.put("aborted_at", offset(rs.getTimestamp("aborted_at")));
        return m;
    }

    private static Map<String, Object> ref(Long id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    private static Object offset(Timestamp t) {
        return t == null ? null : Times.toOffset(t.toLocalDateTime());
    }

    private static Map<String, Object> map(String s) {
        return s == null || s.isBlank() ? new LinkedHashMap<>() : JSON.readValue(s, new TypeReference<Map<String, Object>>() { });
    }

    private static List<Map<String, Object>> list(String s) {
        return s == null || s.isBlank() ? new ArrayList<>() : JSON.readValue(s, new TypeReference<List<Map<String, Object>>>() { });
    }
}

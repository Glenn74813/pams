package com.avtech.pams.clearance;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.Inserts;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.setting.SettingService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Property turnover and clearance (TOR 1.4.12, 1.5.5). An employee leaving, retiring, transferring
 * or ending a contract is put on the list; the property office follows up until every asset under
 * their PAR/ICS is returned or transferred, then issues the Certificate of Property Turnover and
 * Clearance. A lost asset stays on the employee until relieved through disposal, so it blocks the
 * certificate too.
 */
@RestController
@RequestMapping("/api/clearances")
public class ClearanceController {

    static final Set<String> PURPOSES = Set.of("resignation", "retirement", "transfer", "end_of_contract", "leave", "other");
    private static final String READ = "@perm.any('view_properties', 'assign_properties', 'transfer_properties', 'view_reports')";
    private static final String MANAGE = "@perm.any('assign_properties', 'transfer_properties')";

    private final JdbcTemplate jdbc;
    private final Db db;
    private final DocumentNumbers numbers;
    private final CurrentUser current;
    private final AuditService audit;
    private final SettingService settings;
    private final ClearanceCertificateForm form;

    public ClearanceController(JdbcTemplate jdbc, Db db, DocumentNumbers numbers, CurrentUser current, AuditService audit,
                               SettingService settings, ClearanceCertificateForm form) {
        this.jdbc = jdbc;
        this.db = db;
        this.numbers = numbers;
        this.current = current;
        this.audit = audit;
        this.settings = settings;
        this.form = form;
    }

    public record AssetLine(Long id, String tagNumber, String itemName, String status, BigDecimal acquisitionCost, String accountabilityNumber) {
    }

    public record ClearanceView(Long id, String certificateNumber, Long userId, String employee, String department, String purpose,
                                String purposeDetails, LocalDate effectiveDate, String status, long outstanding, int assetsTurnedOver,
                                String requestedBy, String issuedBy, OffsetDateTime issuedAt, String remarks, OffsetDateTime createdAt,
                                List<AssetLine> outstandingAssets) {
    }

    public record RequestInput(Long userId, String purpose, String purposeDetails, LocalDate effectiveDate, String remarks) {
    }

    @GetMapping
    @PreAuthorize(READ)
    public PageResponse<ClearanceView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String search) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            if (!Set.of("pending", "issued", "cancelled").contains(status)) throw ApiException.validation("status", "Unknown status.");
            where.append(" and c.status = ?");
            args.add(status);
        }
        if (search != null && !search.isBlank()) {
            where.append(" and (lower(u.name) like ? or lower(coalesce(c.certificate_number, '')) like ?)");
            String like = "%" + search.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        String from = " from clearance_certificates c join users u on u.id = c.user_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
        int size = 20;
        int pageNo = Math.max(1, page);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((pageNo - 1) * size);
        pageArgs.add(size);
        List<Long> ids = jdbc.queryForList("select c.id" + from + where
                + " order by case when c.status = 'pending' then 0 else 1 end, c.created_at desc, c.id desc offset ? rows fetch next ? rows only", Long.class, pageArgs.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(ids.stream().map(id -> view(id, false)).toList(),
                new PageResponse.Pagination(t, size, pageNo, (int) Math.max(1, (t + size - 1) / size)));
    }

    /** What a person still holds; used before opening a clearance. */
    @GetMapping("/check/{userId}")
    @PreAuthorize(READ)
    public Map<String, Object> check(@PathVariable Long userId) {
        List<Map<String, Object>> u = jdbc.queryForList("select id, name from users where id = ?", userId);
        if (u.isEmpty()) throw ApiException.notFound("User");
        List<AssetLine> assets = outstanding(userId);
        return Map.of("user_id", userId, "name", u.get(0).get("name"), "outstanding", assets.size(), "assets", assets);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ClearanceView show(@PathVariable Long id) {
        return view(id, true);
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<ClearanceView> open(@RequestBody RequestInput in) {
        if (in.userId() == null) throw ApiException.validation("user_id", "Choose the employee.");
        List<Map<String, Object>> u = jdbc.queryForList("select id, name from users where id = ?", in.userId());
        if (u.isEmpty()) throw ApiException.validation("user_id", "Choose the employee.");
        if (in.purpose() == null || !PURPOSES.contains(in.purpose())) throw ApiException.validation("purpose", "Choose why the clearance is needed.");
        String details = blank(in.purposeDetails());
        if (details != null && details.length() > 255) throw ApiException.validation("purpose_details", "Keep this within 255 characters.");
        if ("other".equals(in.purpose()) && details == null) throw ApiException.validation("purpose_details", "Say what the clearance is for.");
        db.namedLock("pams.clearance." + in.userId());
        Long open = jdbc.queryForObject("select count(*) from clearance_certificates where user_id = ? and status = 'pending'", Long.class, in.userId());
        if (open != null && open > 0) throw ApiException.of(HttpStatus.CONFLICT, u.get(0).get("name") + " already has a clearance in progress.");
        Timestamp now = Timestamp.valueOf(Times.now());
        Long id = Inserts.insert(jdbc, """
                insert into clearance_certificates (user_id, purpose, purpose_details, effective_date, status, requested_by, assets_turned_over, remarks, created_at, updated_at)
                values (?, ?, ?, ?, 'pending', ?, 0, ?, ?, ?)""", in.userId(), in.purpose(), details,
                in.effectiveDate() == null ? null : Date.valueOf(in.effectiveDate()), current.user().getId(), blank(in.remarks()), now, now);
        audit.record("created", "ClearanceCertificate", id, "Opened a property clearance for " + u.get(0).get("name"), null, Map.of("purpose", in.purpose()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(id, true));
    }

    /** Issues the certificate once nothing is outstanding. */
    @PostMapping("/{id}/issue")
    @PreAuthorize(MANAGE)
    @Transactional
    public ClearanceView issue(@PathVariable Long id) {
        ClearanceView c = pending(id);
        List<AssetLine> left = outstanding(c.userId());
        if (!left.isEmpty()) {
            throw ApiException.of(HttpStatus.CONFLICT, c.employee() + " still holds " + left.size() + " asset(s); they must be returned or transferred first.")
                    .with("outstanding", left);
        }
        String number = numbers.yearly("CPT", "clearance_certificates.certificate_number");
        Timestamp now = Timestamp.valueOf(Times.now());
        int turned = turnedOver(c.userId(), c.createdAt() == null ? null : c.createdAt().toLocalDate());
        jdbc.update("update clearance_certificates set status = 'issued', certificate_number = ?, issued_by = ?, issued_at = ?, assets_turned_over = ?, updated_at = ? where id = ?",
                number, current.user().getId(), now, turned, now, id);
        audit.record("updated", "ClearanceCertificate", id, "Issued clearance certificate " + number + " to " + c.employee(), null, Map.of("certificate_number", number));
        return view(id, true);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGE)
    @Transactional
    public ClearanceView cancel(@PathVariable Long id) {
        ClearanceView c = pending(id);
        jdbc.update("update clearance_certificates set status = 'cancelled', updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), id);
        audit.record("updated", "ClearanceCertificate", id, "Cancelled the property clearance of " + c.employee(), null, null);
        return view(id, true);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        ClearanceView c = view(id, false);
        if (!"issued".equals(c.status())) throw ApiException.of(HttpStatus.CONFLICT, "Only an issued certificate can be printed.");
        byte[] file = form.generate(id, settings.get("agency_name", ""));
        String name = "Clearance_" + c.certificateNumber() + ".xlsx";
        audit.record("downloaded", "ClearanceCertificate", id, "Downloaded clearance certificate " + c.certificateNumber() + " (" + com.avtech.pams.report.PdfFormAdvice.fileName(name) + ")", null, Map.of("file", com.avtech.pams.report.PdfFormAdvice.fileName(name)));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(file);
    }

    // ───────────────────────── helpers ─────────────────────────

    /** Assets still under the person's PAR/ICS: anything not disposed (a lost one waits for relief through disposal). */
    List<AssetLine> outstanding(Long userId) {
        return jdbc.query("""
                select p.id, p.tag_number, i.name as item_name, p.status, p.acquisition_cost, p.accountability_number
                from properties p join items i on i.id = p.item_id
                where p.assigned_to = ? and p.status not in ('disposed', 'available')
                order by p.tag_number""", (rs, n) -> new AssetLine(rs.getLong("id"), rs.getString("tag_number"), rs.getString("item_name"),
                rs.getString("status"), rs.getBigDecimal("acquisition_cost"), rs.getString("accountability_number")), userId);
    }

    /** Assets the person returned or handed over since the clearance was opened. */
    private int turnedOver(Long userId, LocalDate since) {
        Integer n = jdbc.queryForObject("select count(distinct property_id) from property_transfers where from_user = ? and transfer_type in ('return', 'transfer') and transfer_date >= ?",
                Integer.class, userId, Date.valueOf(since == null ? LocalDate.now(Times.ZONE) : since));
        return n == null ? 0 : n;
    }

    private ClearanceView pending(Long id) {
        ClearanceView c = view(id, false);
        if (!"pending".equals(c.status())) throw ApiException.of(HttpStatus.CONFLICT, "This clearance is already " + c.status() + ".");
        return c;
    }

    private ClearanceView view(Long id, boolean withAssets) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.*, u.name as employee, d.name as department, r.name as requested_name, s.name as issued_name
                from clearance_certificates c join users u on u.id = c.user_id
                left join departments d on d.id = u.department_id
                left join users r on r.id = c.requested_by
                left join users s on s.id = c.issued_by
                where c.id = ?""", id);
        if (rows.isEmpty()) throw ApiException.notFound("Clearance");
        Map<String, Object> r = rows.get(0);
        Long userId = ((Number) r.get("user_id")).longValue();
        String status = (String) r.get("status");
        List<AssetLine> assets = "pending".equals(status) ? outstanding(userId) : List.of();
        return new ClearanceView(id, (String) r.get("certificate_number"), userId, (String) r.get("employee"), (String) r.get("department"),
                (String) r.get("purpose"), (String) r.get("purpose_details"), date(r.get("effective_date")), status, assets.size(),
                ((Number) r.get("assets_turned_over")).intValue(), (String) r.get("requested_name"), (String) r.get("issued_name"),
                stamp(r.get("issued_at")), (String) r.get("remarks"), stamp(r.get("created_at")), withAssets ? assets : null);
    }

    static LocalDate date(Object v) {
        return v instanceof Date d ? d.toLocalDate() : v instanceof LocalDate l ? l : null;
    }

    static OffsetDateTime stamp(Object v) {
        return v instanceof Timestamp t ? Times.toOffset(t.toLocalDateTime()) : v instanceof java.time.LocalDateTime l ? Times.toOffset(l) : null;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Labels for the certificate and the UI. */
    static final Map<String, String> PURPOSE_LABELS = new LinkedHashMap<>();

    static {
        PURPOSE_LABELS.put("resignation", "Resignation");
        PURPOSE_LABELS.put("retirement", "Retirement");
        PURPOSE_LABELS.put("transfer", "Transfer to another office or agency");
        PURPOSE_LABELS.put("end_of_contract", "End of contract");
        PURPOSE_LABELS.put("leave", "Extended leave");
        PURPOSE_LABELS.put("other", "Other");
    }
}

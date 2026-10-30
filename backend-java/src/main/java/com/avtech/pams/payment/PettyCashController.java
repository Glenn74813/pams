package com.avtech.pams.payment;

import com.avtech.pams.common.Db;
import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.payment.PaymentController.Ref;
import com.avtech.pams.procurement.ProcurementRules;
import com.avtech.pams.report.coa.PettyCashVoucherForm;
import com.avtech.pams.setting.SettingService;
import com.avtech.pams.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The petty cash fund: a custodian disburses from it on Petty Cash Vouchers (COA Appendix 50),
 * each approved by someone other than whoever recorded it, and asks for the approved vouchers to be
 * replenished on a Report of Petty Cash replenishment. The fund's cash on hand is its amount less
 * the approved vouchers not yet replenished.
 */
@RestController
@RequestMapping("/api/petty-cash")
public class PettyCashController {

    private static final String FUND = "App\\Models\\PettyCashFund";
    private static final String VOUCHER = "App\\Models\\PettyCashVoucher";
    private static final String REPLENISHMENT = "App\\Models\\PettyCashReplenishment";
    private static final String READ = "@perm.any('view_payments', 'manage_payments', 'approve_payments', 'manage_petty_cash')";
    /** Payments out of the fund are limited per transaction (GAM for NGAs); larger ones go through a DV. */
    static final BigDecimal MAX_PER_VOUCHER = new BigDecimal("15000.00");

    private final JdbcTemplate jdbc;
    private final Db db;
    private final AuditService audit;
    private final CurrentUser current;
    private final PaymentNumbers numbers;
    private final SettingService settings;
    private final PettyCashVoucherForm form;
    private final PaymentController payments;

    public PettyCashController(JdbcTemplate jdbc, Db db, AuditService audit, CurrentUser current, PaymentNumbers numbers, SettingService settings,
                               PettyCashVoucherForm form, PaymentController payments) {
        this.jdbc = jdbc;
        this.db = db;
        this.audit = audit;
        this.current = current;
        this.numbers = numbers;
        this.settings = settings;
        this.form = form;
        this.payments = payments;
    }

    // ───────────────────────── records & views ─────────────────────────

    record Fund(Long id, String name, Long custodianId, BigDecimal amount, String fundCluster, String status, String remarks, BigDecimal disbursed,
                BigDecimal pending, BigDecimal inReplenishment, int vouchers, LocalDateTime createdAt) {

        BigDecimal balance() {
            return amount.subtract(disbursed);
        }

        BigDecimal available() {
            return balance().subtract(pending);
        }
    }

    record Voucher(Long id, Long fundId, String number, LocalDate date, String payee, String particulars, BigDecimal amount, String rc, String status,
                   Long recordedBy, Long approvedBy, LocalDateTime approvedAt, Long replenishmentId, String remarks) {
    }

    record Replenishment(Long id, Long fundId, String number, LocalDate date, BigDecimal total, String status, Long requestedBy, Long replenishedBy,
                         LocalDate replenishedDate, String reference, String remarks) {
    }

    public record VoucherView(Long id, String pcvNumber, LocalDate voucherDate, String payee, String particulars, BigDecimal amount,
                              String responsibilityCenter, String status, Ref recordedBy, Ref approvedBy, OffsetDateTime approvedAt, Ref replenishment,
                              boolean replenished, String remarks, boolean canApprove, boolean canCancel) {
    }

    public record ReplenishmentView(Long id, String reportNumber, LocalDate reportDate, BigDecimal total, int voucherCount, String status, Ref requestedBy,
                                    Ref replenishedBy, LocalDate replenishedDate, String reference, String remarks, boolean canReplenish) {
    }

    public record FundView(Long id, String fundName, Ref custodian, BigDecimal amount, String fundCluster, String status, String remarks,
                           BigDecimal disbursed, BigDecimal pending, BigDecimal inReplenishment, BigDecimal balance, BigDecimal available,
                           BigDecimal unrequested, int voucherCount, BigDecimal maxPerVoucher, boolean canManage, boolean canRecord,
                           boolean canRequestReplenishment, OffsetDateTime createdAt, List<VoucherView> vouchers, List<ReplenishmentView> replenishments) {
    }

    // ───────────────────────── funds ─────────────────────────

    @GetMapping("/funds")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public Map<String, Object> index() {
        List<Fund> list = jdbc.query(FUNDS + " order by f.status, f.fund_name, f.id", (rs, i) -> fund(rs));
        Map<Long, String> names = payments.names(ids(list.stream().map(Fund::custodianId).toList()));
        return Map.of("data", list.stream().map(f -> view(f, names, false)).toList());
    }

    @GetMapping("/funds/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public FundView show(@PathVariable Long id) {
        return full(fund(id, false));
    }

    public record FundInput(String fundName, Long custodianId, BigDecimal amount, String fundCluster, String status, String remarks) {
    }

    @PostMapping("/funds")
    @PreAuthorize("@perm.any('manage_payments')")
    @Transactional
    public ResponseEntity<FundView> store(@RequestBody FundInput in) {
        String name = PaymentController.text(in.fundName(), 255, "fund_name");
        if (name == null) throw ApiException.validation("fund_name", "Name the fund.");
        custodian(in.custodianId());
        BigDecimal amount = amount(in.amount(), "amount", "Enter the fund amount in pesos.");
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                insert into petty_cash_funds (fund_name, custodian_id, amount, fund_cluster, status, remarks, created_by, created_at, updated_at)
                values (?, ?, ?, ?, 'active', ?, ?, ?, ?)""",
                name, in.custodianId(), amount, PaymentController.text(in.fundCluster(), 50, "fund_cluster"), PaymentController.blank(in.remarks()),
                current.user().getId(), now, now);
        Long id = jdbc.queryForObject("select max(id) from petty_cash_funds where created_by = ?", Long.class, current.user().getId());
        Fund f = fund(id, false);
        audit.record("created", FUND, id, "Petty cash fund " + name + " (₱" + ProcurementRules.peso(amount) + ") established", null, snapshot(f));
        return ResponseEntity.status(HttpStatus.CREATED).body(full(f));
    }

    @PutMapping("/funds/{id}")
    @PreAuthorize("@perm.any('manage_payments')")
    @Transactional
    public FundView update(@PathVariable Long id, @RequestBody FundInput in) {
        Fund f = fund(id, true);
        Map<String, Object> before = snapshot(f);
        String name = in.fundName() == null ? f.name() : PaymentController.text(in.fundName(), 255, "fund_name");
        if (name == null) throw ApiException.validation("fund_name", "Name the fund.");
        Long custodian = in.custodianId() == null ? f.custodianId() : in.custodianId();
        if (in.custodianId() != null) custodian(in.custodianId());
        BigDecimal amount = in.amount() == null ? f.amount() : amount(in.amount(), "amount", "Enter the fund amount in pesos.");
        BigDecimal committed = f.disbursed().add(f.pending());
        if (amount.compareTo(committed) < 0) {
            throw ApiException.validation("amount", "₱" + ProcurementRules.peso(committed) + " is already out on vouchers not yet replenished; the fund cannot be smaller.");
        }
        String status = in.status() == null ? f.status() : in.status();
        if (!List.of("active", "closed").contains(status)) throw ApiException.validation("status", "A fund is active or closed.");
        if ("closed".equals(status) && !"closed".equals(f.status()) && committed.signum() > 0) {
            throw ApiException.of(HttpStatus.CONFLICT, "Settle the pending vouchers and replenish the approved ones before closing the fund.");
        }
        jdbc.update("update petty_cash_funds set fund_name = ?, custodian_id = ?, amount = ?, fund_cluster = ?, status = ?, remarks = ?, updated_at = ? where id = ?",
                name, custodian, amount, in.fundCluster() == null ? f.fundCluster() : PaymentController.text(in.fundCluster(), 50, "fund_cluster"), status,
                in.remarks() == null ? f.remarks() : PaymentController.blank(in.remarks()), Timestamp.valueOf(Times.now()), id);
        Fund after = fund(id, false);
        audit.record("updated", FUND, id, "Edited petty cash fund " + name, before, snapshot(after));
        return full(after);
    }

    // ───────────────────────── vouchers ─────────────────────────

    public record VoucherInput(LocalDate voucherDate, String payee, String particulars, BigDecimal amount, String responsibilityCenter, String remarks) {
    }

    /** The custodian records a voucher; it waits for someone else's approval before the cash goes out. */
    @PostMapping("/funds/{id}/vouchers")
    @PreAuthorize("@perm.any('manage_petty_cash')")
    @Transactional
    public ResponseEntity<FundView> storeVoucher(@PathVariable Long id, @RequestBody VoucherInput in) {
        Fund f = fund(id, true);
        if (!"active".equals(f.status())) throw ApiException.of(HttpStatus.CONFLICT, "The fund is closed.");
        User me = current.user();
        if (!me.getId().equals(f.custodianId()) && !me.isSuperAdmin()) throw ApiException.forbidden("Only the fund's custodian records vouchers on it.");
        String payee = PaymentController.text(in.payee(), 255, "payee");
        if (payee == null) throw ApiException.validation("payee", "Name the payee.");
        String particulars = PaymentController.blank(in.particulars());
        if (particulars == null) throw ApiException.validation("particulars", "State what the payment is for.");
        BigDecimal amount = amount(in.amount(), "amount", "Enter the amount in pesos.");
        if (amount.compareTo(MAX_PER_VOUCHER) > 0) {
            throw ApiException.validation("amount", "A petty cash payment is at most ₱" + ProcurementRules.peso(MAX_PER_VOUCHER) + "; pay larger amounts by disbursement voucher.");
        }
        if (amount.compareTo(f.available()) > 0) {
            throw ApiException.validation("amount", "Only ₱" + ProcurementRules.peso(f.available().max(BigDecimal.ZERO)) + " of the fund is available; request a replenishment.");
        }
        LocalDate date = in.voucherDate() == null ? LocalDate.now(Times.ZONE) : in.voucherDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("voucher_date", "The voucher date cannot be in the future.");
        String number = numbers.monthly("PCV", "petty_cash_vouchers.pcv_number");
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                insert into petty_cash_vouchers (petty_cash_fund_id, pcv_number, voucher_date, payee, particulars, amount, responsibility_center, status,
                  recorded_by, remarks, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?)""",
                id, number, java.sql.Date.valueOf(date), payee, particulars, amount, PaymentController.text(in.responsibilityCenter(), 100, "responsibility_center"),
                me.getId(), PaymentController.blank(in.remarks()), now, now);
        Long vid = jdbc.queryForObject("select id from petty_cash_vouchers where pcv_number = ?", Long.class, number);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pcv_number", number);
        values.put("payee", payee);
        values.put("amount", amount);
        audit.record("created", VOUCHER, vid, "Petty cash voucher " + number + " (₱" + ProcurementRules.peso(amount) + " to " + payee + ") recorded on " + f.name(),
                null, values);
        return ResponseEntity.status(HttpStatus.CREATED).body(full(fund(id, false)));
    }

    @PostMapping("/vouchers/{id}/approve")
    @PreAuthorize("@perm.any('approve_payments')")
    @Transactional
    public FundView approve(@PathVariable Long id) {
        Voucher v = voucher(id);
        Fund f = fund(v.fundId(), true);
        v = voucher(id);
        if (!"pending".equals(v.status())) throw ApiException.of(HttpStatus.CONFLICT, "This voucher is already " + v.status() + ".");
        User me = current.user();
        if (me.getId().equals(v.recordedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You recorded this voucher, so someone else has to approve it.");
        if (v.amount().compareTo(f.balance()) > 0) throw ApiException.of(HttpStatus.CONFLICT, "The fund has only ₱" + ProcurementRules.peso(f.balance()) + " on hand.");
        jdbc.update("update petty_cash_vouchers set status = 'approved', approved_by = ?, approved_at = ?, updated_at = ? where id = ?",
                me.getId(), Timestamp.valueOf(Times.now()), Timestamp.valueOf(Times.now()), id);
        audit.record("approved", VOUCHER, id, "Petty cash voucher " + v.number() + " (₱" + ProcurementRules.peso(v.amount()) + ") approved",
                Map.of("status", "pending"), Map.of("status", "approved"));
        return full(fund(f.id(), false));
    }

    public record ReasonInput(String remarks) {
    }

    @PostMapping("/vouchers/{id}/cancel")
    @PreAuthorize("@perm.any('manage_petty_cash', 'approve_payments')")
    @Transactional
    public FundView cancel(@PathVariable Long id, @RequestBody(required = false) ReasonInput in) {
        String why = in == null ? null : PaymentController.blank(in.remarks());
        if (why == null) throw ApiException.validation("remarks", "Say why the voucher is cancelled.");
        Voucher v = voucher(id);
        Fund f = fund(v.fundId(), true);
        v = voucher(id);
        if (!"pending".equals(v.status())) throw ApiException.of(HttpStatus.CONFLICT, "Only a voucher awaiting approval can be cancelled; this one is " + v.status() + ".");
        jdbc.update("update petty_cash_vouchers set status = 'cancelled', remarks = ?, updated_at = ? where id = ?", why, Timestamp.valueOf(Times.now()), id);
        audit.record("cancelled", VOUCHER, id, "Petty cash voucher " + v.number() + " cancelled: " + why, Map.of("status", "pending"), Map.of("status", "cancelled"));
        return full(fund(f.id(), false));
    }

    // ───────────────────────── replenishment ─────────────────────────

    public record ReplenishmentInput(LocalDate reportDate, String reference, LocalDate replenishedDate, String remarks) {
    }

    /** The custodian reports the approved vouchers not yet reported, asking for them to be replenished. */
    @PostMapping("/funds/{id}/replenishments")
    @PreAuthorize("@perm.any('manage_petty_cash')")
    @Transactional
    public ResponseEntity<FundView> requestReplenishment(@PathVariable Long id, @RequestBody(required = false) ReplenishmentInput body) {
        ReplenishmentInput in = body == null ? new ReplenishmentInput(null, null, null, null) : body;
        Fund f = fund(id, true);
        User me = current.user();
        if (!me.getId().equals(f.custodianId()) && !me.isSuperAdmin()) throw ApiException.forbidden("Only the fund's custodian requests its replenishment.");
        BigDecimal total = jdbc.queryForObject("select coalesce(sum(amount), 0) from petty_cash_vouchers where petty_cash_fund_id = ? and status = 'approved' and petty_cash_replenishment_id is null",
                BigDecimal.class, id);
        if (total == null || total.signum() == 0) throw ApiException.of(HttpStatus.CONFLICT, "There are no approved vouchers to replenish.");
        LocalDate date = in.reportDate() == null ? LocalDate.now(Times.ZONE) : in.reportDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("report_date", "The report date cannot be in the future.");
        String number = numbers.monthly("RPCF", "petty_cash_replenishments.report_number");
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                insert into petty_cash_replenishments (petty_cash_fund_id, report_number, report_date, total, status, requested_by, remarks, created_at, updated_at)
                values (?, ?, ?, ?, 'requested', ?, ?, ?, ?)""", id, number, java.sql.Date.valueOf(date), total, me.getId(), PaymentController.blank(in.remarks()), now, now);
        Long rid = jdbc.queryForObject("select id from petty_cash_replenishments where report_number = ?", Long.class, number);
        int n = jdbc.update("update petty_cash_vouchers set petty_cash_replenishment_id = ?, updated_at = ? where petty_cash_fund_id = ? and status = 'approved' and petty_cash_replenishment_id is null",
                rid, now, id);
        audit.record("created", REPLENISHMENT, rid, "Replenishment " + number + " of " + f.name() + " requested: " + n + " voucher(s), ₱" + ProcurementRules.peso(total),
                null, Map.of("total", total, "vouchers", n));
        return ResponseEntity.status(HttpStatus.CREATED).body(full(fund(id, false)));
    }

    /** Records the check/ADA that restores the fund; done by someone other than the custodian who asked. */
    @PostMapping("/replenishments/{id}/replenish")
    @PreAuthorize("@perm.any('approve_payments')")
    @Transactional
    public FundView replenish(@PathVariable Long id, @RequestBody ReplenishmentInput in) {
        Replenishment r = replenishment(id);
        Fund f = fund(r.fundId(), true);
        r = replenishment(id);
        if (!"requested".equals(r.status())) throw ApiException.of(HttpStatus.CONFLICT, "This replenishment was already made.");
        User me = current.user();
        if (me.getId().equals(r.requestedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You requested this replenishment, so someone else has to make it.");
        String ref = PaymentController.text(in == null ? null : in.reference(), 100, "reference");
        if (ref == null) throw ApiException.validation("reference", "Enter the check or ADA number that replenishes the fund.");
        LocalDate date = in.replenishedDate() == null ? LocalDate.now(Times.ZONE) : in.replenishedDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("replenished_date", "The date cannot be in the future.");
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                update petty_cash_replenishments set status = 'replenished', replenished_by = ?, replenished_at = ?, replenished_date = ?, reference = ?,
                  remarks = coalesce(?, remarks), updated_at = ?
                where id = ?""", me.getId(), now, java.sql.Date.valueOf(date), ref, PaymentController.blank(in.remarks()), now, id);
        audit.record("replenished", REPLENISHMENT, id, "Petty cash fund " + f.name() + " replenished with ₱" + ProcurementRules.peso(r.total()) + " (" + ref + ", "
                + r.number() + ")", Map.of("status", "requested"), Map.of("status", "replenished", "reference", ref));
        return full(fund(f.id(), false));
    }

    // ───────────────────────── forms ─────────────────────────

    /** Petty Cash Voucher, COA Appendix 50. */
    @GetMapping("/vouchers/{id}/pcv")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> pcv(@PathVariable Long id, @RequestParam(name = "entity_name", required = false) String entity) {
        Voucher v = voucher(id);
        Fund f = fund(v.fundId(), false);
        Map<Long, String> names = payments.names(ids(List.of(v.approvedBy(), f.custodianId())));
        byte[] bytes = form.voucher(new PettyCashVoucherForm.Voucher(given(entity, "agency_name"), f.fundCluster() != null ? f.fundCluster() : settings.get("fund_cluster", ""),
                f.name(), v.number(), v.date(), v.payee(), v.particulars(), v.rc(), v.amount(), names.get(v.approvedBy()), names.get(f.custodianId()), v.status()));
        String file = "PCV_" + v.number() + ".xlsx";
        audit.record("downloaded", VOUCHER, id, "Downloaded PCV " + v.number() + " (" + com.avtech.pams.report.PdfFormAdvice.fileName(file) + ")", null, Map.of("file", com.avtech.pams.report.PdfFormAdvice.fileName(file)));
        return PaymentController.xlsx(bytes, file);
    }

    /** Report of Petty Cash replenishment: the vouchers it restores and the fund's position. */
    @GetMapping("/replenishments/{id}/report")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> report(@PathVariable Long id, @RequestParam(name = "entity_name", required = false) String entity) {
        Replenishment r = replenishment(id);
        Fund f = fund(r.fundId(), false);
        List<PettyCashVoucherForm.Entry> lines = jdbc.query("""
                select voucher_date, pcv_number, payee, particulars, responsibility_center, amount from petty_cash_vouchers
                where petty_cash_replenishment_id = ? order by voucher_date, id""", (rs, i) -> new PettyCashVoucherForm.Entry(rs.getDate(1).toLocalDate(),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6)), id);
        Map<Long, String> names = payments.names(ids(List.of(f.custodianId())));
        BigDecimal onHand = "replenished".equals(r.status()) ? f.amount().subtract(r.total()) : f.balance();
        byte[] bytes = form.replenishment(new PettyCashVoucherForm.Replenishment(given(entity, "agency_name"),
                f.fundCluster() != null ? f.fundCluster() : settings.get("fund_cluster", ""), f.name(), names.get(f.custodianId()), r.number(), r.date(),
                f.amount(), onHand, lines, r.total(), r.status(), r.reference(), r.replenishedDate()));
        String file = "PettyCashReplenishment_" + r.number() + ".xlsx";
        audit.record("downloaded", REPLENISHMENT, id, "Downloaded Report of Petty Cash replenishment " + r.number() + " (" + com.avtech.pams.report.PdfFormAdvice.fileName(file) + ")", null, Map.of("file", com.avtech.pams.report.PdfFormAdvice.fileName(file)));
        return PaymentController.xlsx(bytes, file);
    }

    private String given(String v, String setting) {
        return v != null && !v.isBlank() ? v.trim() : settings.get(setting, "");
    }

    // ───────────────────────── loading ─────────────────────────

    private static final String FUNDS = """
            select f.id, f.fund_name, f.custodian_id, f.amount, f.fund_cluster, f.status, f.remarks, f.created_at,
                   (select coalesce(sum(v.amount), 0) from petty_cash_vouchers v left join petty_cash_replenishments r on r.id = v.petty_cash_replenishment_id
                    where v.petty_cash_fund_id = f.id and v.status = 'approved' and (r.id is null or r.status <> 'replenished')) as disbursed,
                   (select coalesce(sum(v.amount), 0) from petty_cash_vouchers v where v.petty_cash_fund_id = f.id and v.status = 'pending') as pending,
                   (select coalesce(sum(v.amount), 0) from petty_cash_vouchers v join petty_cash_replenishments r on r.id = v.petty_cash_replenishment_id
                    where v.petty_cash_fund_id = f.id and v.status = 'approved' and r.status = 'requested') as in_replenishment,
                   (select count(*) from petty_cash_vouchers v where v.petty_cash_fund_id = f.id) as vouchers
            from petty_cash_funds f""";

    private Fund fund(Long id, boolean lock) {
        if (lock) db.lockRows("petty_cash_funds", "id = ?", id);
        List<Fund> list = jdbc.query(FUNDS + " where f.id = ?", (rs, i) -> fund(rs), id);
        if (list.isEmpty()) throw ApiException.notFound("Petty cash fund");
        return list.get(0);
    }

    private static Fund fund(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new Fund(rs.getLong("id"), rs.getString("fund_name"), rs.getObject("custodian_id", Long.class), rs.getBigDecimal("amount"),
                rs.getString("fund_cluster"), rs.getString("status"), rs.getString("remarks"), rs.getBigDecimal("disbursed"), rs.getBigDecimal("pending"),
                rs.getBigDecimal("in_replenishment"), rs.getInt("vouchers"), created == null ? null : created.toLocalDateTime());
    }

    private static final String VOUCHERS = """
            select id, petty_cash_fund_id, pcv_number, voucher_date, payee, particulars, amount, responsibility_center, status, recorded_by, approved_by,
                   approved_at, petty_cash_replenishment_id, remarks
            from petty_cash_vouchers""";

    private static Voucher voucher(ResultSet rs) throws SQLException {
        Timestamp at = rs.getTimestamp(12);
        return new Voucher(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getDate(4).toLocalDate(), rs.getString(5), rs.getString(6), rs.getBigDecimal(7),
                rs.getString(8), rs.getString(9), rs.getObject(10, Long.class), rs.getObject(11, Long.class), at == null ? null : at.toLocalDateTime(),
                rs.getObject(13, Long.class), rs.getString(14));
    }

    private Voucher voucher(Long id) {
        List<Voucher> list = jdbc.query(VOUCHERS + " where id = ?", (rs, i) -> voucher(rs), id);
        if (list.isEmpty()) throw ApiException.notFound("Petty cash voucher");
        return list.get(0);
    }

    private static final String REPLENISHMENTS = """
            select id, petty_cash_fund_id, report_number, report_date, total, status, requested_by, replenished_by, replenished_date, reference, remarks
            from petty_cash_replenishments""";

    private static Replenishment replenishment(ResultSet rs) throws SQLException {
        java.sql.Date d = rs.getDate(9);
        return new Replenishment(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getDate(4).toLocalDate(), rs.getBigDecimal(5), rs.getString(6),
                rs.getObject(7, Long.class), rs.getObject(8, Long.class), d == null ? null : d.toLocalDate(), rs.getString(10), rs.getString(11));
    }

    private Replenishment replenishment(Long id) {
        List<Replenishment> list = jdbc.query(REPLENISHMENTS + " where id = ?", (rs, i) -> replenishment(rs), id);
        if (list.isEmpty()) throw ApiException.notFound("Replenishment");
        return list.get(0);
    }

    private FundView full(Fund f) {
        List<Voucher> vouchers = jdbc.query(VOUCHERS + " where petty_cash_fund_id = ? order by voucher_date desc, id desc", (rs, i) -> voucher(rs), f.id());
        List<Replenishment> reps = jdbc.query(REPLENISHMENTS + " where petty_cash_fund_id = ? order by report_date desc, id desc", (rs, i) -> replenishment(rs), f.id());
        Set<Long> people = new HashSet<>();
        people.add(f.custodianId());
        vouchers.forEach(v -> {
            people.add(v.recordedBy());
            people.add(v.approvedBy());
        });
        reps.forEach(r -> {
            people.add(r.requestedBy());
            people.add(r.replenishedBy());
        });
        Map<Long, String> names = payments.names(ids(people));
        FundView base = view(f, names, true);
        User me = current.user();
        boolean sa = me.isSuperAdmin();
        boolean approver = current.any("approve_payments");
        boolean keeper = current.any("manage_petty_cash");
        Map<Long, Replenishment> byId = new LinkedHashMap<>();
        reps.forEach(r -> byId.put(r.id(), r));
        List<VoucherView> vv = vouchers.stream().map(v -> {
            Replenishment r = v.replenishmentId() == null ? null : byId.get(v.replenishmentId());
            boolean pending = "pending".equals(v.status());
            return new VoucherView(v.id(), v.number(), v.date(), v.payee(), v.particulars(), v.amount(), v.rc(), v.status(),
                    PaymentController.ref(v.recordedBy(), names), PaymentController.ref(v.approvedBy(), names), Times.toOffset(v.approvedAt()),
                    r == null ? null : new Ref(r.id(), r.number()), r != null && "replenished".equals(r.status()), v.remarks(),
                    approver && pending && (sa || !me.getId().equals(v.recordedBy())), (approver || keeper) && pending);
        }).toList();
        List<ReplenishmentView> rv = reps.stream().map(r -> new ReplenishmentView(r.id(), r.number(), r.date(), r.total(),
                (int) vouchers.stream().filter(v -> r.id().equals(v.replenishmentId())).count(), r.status(), PaymentController.ref(r.requestedBy(), names),
                PaymentController.ref(r.replenishedBy(), names), r.replenishedDate(), r.reference(), r.remarks(),
                approver && "requested".equals(r.status()) && (sa || !me.getId().equals(r.requestedBy())))).toList();
        return new FundView(base.id(), base.fundName(), base.custodian(), base.amount(), base.fundCluster(), base.status(), base.remarks(), base.disbursed(),
                base.pending(), base.inReplenishment(), base.balance(), base.available(), base.unrequested(), base.voucherCount(), base.maxPerVoucher(),
                base.canManage(), base.canRecord(), base.canRequestReplenishment(), base.createdAt(), vv, rv);
    }

    private FundView view(Fund f, Map<Long, String> names, boolean full) {
        User me = current.user();
        boolean custodian = current.any("manage_petty_cash") && (me.isSuperAdmin() || me.getId().equals(f.custodianId()));
        BigDecimal unrequested = f.disbursed().subtract(f.inReplenishment());
        boolean active = "active".equals(f.status());
        return new FundView(f.id(), f.name(), PaymentController.ref(f.custodianId(), names), f.amount(), f.fundCluster(), f.status(), f.remarks(), f.disbursed(),
                f.pending(), f.inReplenishment(), f.balance(), f.available(), unrequested, f.vouchers(), MAX_PER_VOUCHER, current.any("manage_payments"),
                custodian && active, custodian && unrequested.signum() > 0, Times.toOffset(f.createdAt()), full ? List.of() : null, full ? List.of() : null);
    }

    private void custodian(Long userId) {
        if (userId == null) throw ApiException.validation("custodian_id", "Choose the custodian.");
        Integer n = jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, userId);
        if (n == null || n == 0) throw ApiException.validation("custodian_id", "That person does not exist.");
    }

    private static BigDecimal amount(BigDecimal v, String field, String message) {
        if (v == null || v.signum() <= 0 || v.scale() > 2) throw ApiException.validation(field, message);
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Set<Long> ids(java.util.Collection<Long> ids) {
        Set<Long> s = new HashSet<>();
        ids.stream().filter(Objects::nonNull).forEach(s::add);
        return s;
    }

    private static Map<String, Object> snapshot(Fund f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fund_name", f.name());
        m.put("custodian_id", f.custodianId());
        m.put("amount", f.amount());
        m.put("status", f.status());
        return m;
    }
}

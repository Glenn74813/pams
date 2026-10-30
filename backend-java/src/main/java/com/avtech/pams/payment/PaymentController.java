package com.avtech.pams.payment;

import com.avtech.pams.common.Db;
import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.monitoring.ProcurementWatch;
import com.avtech.pams.procurement.ProcurementRules;
import com.avtech.pams.report.coa.DisbursementVoucherForm;
import com.avtech.pams.report.coa.OrsForm;
import com.avtech.pams.report.coa.PaymentClaim;
import com.avtech.pams.setting.SettingService;
import com.avtech.pams.user.User;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Payment schedules (TOR B 7.4): the tranches a contract or purchase order is paid in, each with
 * its deliverable and due date, moving pending → for_payment → paid. Approval for payment and the
 * release of payment are done by different people from whoever scheduled or approved the tranche.
 * Prints the Obligation Request and Status (ORS/BURS) and the Disbursement Voucher (COA Appendix 32).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    static final String TYPE = "App\\Models\\PaymentTranche";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String READ = "@perm.any('view_payments', 'manage_payments', 'approve_payments')";
    private static final String MANAGE = "@perm.any('manage_payments')";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final List<String> METHODS = List.of("check", "ada", "lddap_ada");
    private static final int DUE_WARN = 7;

    private final JdbcTemplate jdbc;
    private final Db db;
    private final AuditService audit;
    private final CurrentUser current;
    private final PaymentNumbers numbers;
    private final SettingService settings;
    private final OrsForm orsForm;
    private final DisbursementVoucherForm dvForm;

    public PaymentController(JdbcTemplate jdbc, Db db, AuditService audit, CurrentUser current, PaymentNumbers numbers, SettingService settings,
                             OrsForm orsForm, DisbursementVoucherForm dvForm) {
        this.jdbc = jdbc;
        this.db = db;
        this.audit = audit;
        this.current = current;
        this.numbers = numbers;
        this.settings = settings;
        this.orsForm = orsForm;
        this.dvForm = dvForm;
    }

    // ───────────────────────── records & views ─────────────────────────

    public record Ref(Long id, String name) {
    }

    /** The contract or purchase order a schedule is drawn on; contractId/poId are the pair it shares liquidated damages with. */
    record Payable(String type, Long id, String number, String title, String status, Long supplierId, String supplier, String tin, String address,
                   BigDecimal base, Long contractId, Long poId, BigDecimal ldAssessed) {

        boolean schedulable() {
            return "contract".equals(type) ? List.of("draft", "active", "completed").contains(status) : !"cancelled".equals(status);
        }

        boolean payable() {
            return "contract".equals(type) ? List.of("active", "completed").contains(status)
                    : List.of("sent", "confirmed", "partially_received", "received").contains(status);
        }

        String label() {
            return ("contract".equals(type) ? "Contract " : "PO ") + number;
        }
    }

    record Tranche(Long id, Long contractId, Long poId, int seq, String description, BigDecimal percentage, BigDecimal amount, LocalDate dueDate,
                   String deliverable, String rc, String mfoPap, String uacs, String status, String ors, String dv, Long createdBy, Long approvedBy,
                   LocalDateTime approvedAt, Long paidBy, LocalDate paidDate, String method, String reference, BigDecimal amountPaid,
                   BigDecimal retention, BigDecimal ld, BigDecimal wtax, String remarks, String payableNumber, String supplier, String parentStatus) {

        String payableType() {
            return contractId != null ? "contract" : "purchase_order";
        }

        Long payableId() {
            return contractId != null ? contractId : poId;
        }

        boolean open() {
            return !"paid".equals(status);
        }
    }

    public record TrancheView(Long id, String payableType, Long payableId, String payableReference, String supplier, int sequenceNo, String description,
                              BigDecimal percentage, BigDecimal amount, LocalDate dueDate, String deliverable, String responsibilityCenter, String mfoPap,
                              String uacsCode, String status, boolean overdue, Integer daysOverdue, String orsNumber, String dvNumber, LocalDate paidDate,
                              String paymentMethod, String paymentReference, BigDecimal amountPaid, BigDecimal retentionAmount, BigDecimal ldDeducted,
                              BigDecimal withholdingTax, String remarks, Ref createdBy, Ref approvedBy, OffsetDateTime approvedAt, Ref paidBy,
                              boolean canEdit, boolean canApprove, boolean canPay) {
    }

    public record ScheduleView(String type, Long id, String reference, String title, String status, Ref supplier, BigDecimal baseAmount,
                               BigDecimal scheduledTotal, BigDecimal unscheduled, BigDecimal paidTotal, BigDecimal releasedTotal, BigDecimal outstanding,
                               int overdueCount, BigDecimal ldAssessed, BigDecimal ldDeducted, BigDecimal ldAvailable, String linkedNotice,
                               boolean canManage, boolean canSchedule, List<TrancheView> tranches) {
    }

    // ───────────────────────── reads ─────────────────────────

    @GetMapping("/schedule")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public ScheduleView schedule(@RequestParam String type, @RequestParam Long id) {
        return scheduleView(payable(type, id, false));
    }

    /** Every tranche, for a payments register: by status, or only the unpaid ones past due. */
    @GetMapping("/tranches")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public Map<String, Object> tranches(@RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean overdue) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" and t.status = ?");
            args.add(status.trim());
        }
        if (overdue) {
            where.append(" and t.status <> 'paid' and t.due_date < ?");
            args.add(java.sql.Date.valueOf(LocalDate.now(Times.ZONE)));
        }
        List<Tranche> list = query(where + " order by case when t.due_date is null then 1 else 0 end, t.due_date, t.id", args.toArray());
        return Map.of("data", views(list, null));
    }

    /**
     * Unpaid tranches due within {@code days} (default 7) or already overdue, in the shape of the
     * Monitoring page's entries (kind "payment_due"), so it can merge them with the procurement watch.
     */
    @GetMapping("/due")
    @PreAuthorize("@perm.any('view_payments', 'manage_payments', 'approve_payments', 'view_monitoring')")
    @Transactional(readOnly = true)
    public Map<String, Object> due(@RequestParam(required = false) Integer days) {
        return Map.of("data", dueEntries(days == null ? DUE_WARN : Math.max(0, Math.min(days, 90))));
    }

    public List<ProcurementWatch.Entry> dueEntries(int days) {
        LocalDate today = LocalDate.now(Times.ZONE);
        List<Tranche> list = query(" and t.status <> 'paid' and t.due_date is not null and t.due_date <= ?"
                        + " and (c.id is null or c.status <> 'terminated') and (po.id is null or po.status <> 'cancelled') order by t.due_date, t.id",
                java.sql.Date.valueOf(today.plusDays(days)));
        List<ProcurementWatch.Entry> out = new ArrayList<>();
        for (Tranche t : list) {
            int left = (int) ChronoUnit.DAYS.between(today, t.dueDate());
            String what = "Tranche " + t.seq() + " (" + t.description() + ", ₱" + ProcurementRules.peso(t.amount()) + ")"
                    + ("for_payment".equals(t.status()) ? ", approved for payment," : "");
            String detail = left < 0 ? what + " is " + (-left) + " day(s) overdue" : what + " is due " + (left == 0 ? "today" : "in " + left + " day(s)");
            out.add(new ProcurementWatch.Entry("payment_due", left < 0 ? ProcurementWatch.OVERDUE : ProcurementWatch.DUE_SOON, t.payableNumber(),
                    t.supplier(), detail, t.dueDate(), left, t.contractId() != null ? "App\\Models\\Contract" : "App\\Models\\PurchaseOrder", t.payableId()));
        }
        out.sort(Comparator.comparing((ProcurementWatch.Entry e) -> ProcurementWatch.OVERDUE.equals(e.severity()) ? 0 : 1).thenComparing(ProcurementWatch.Entry::days));
        return out;
    }

    // ───────────────────────── scheduling ─────────────────────────

    public record TrancheInput(String type, Long id, String description, BigDecimal percentage, BigDecimal amount, LocalDate dueDate, String deliverable,
                               String responsibilityCenter, String mfoPap, String uacsCode, String remarks) {
    }

    @PostMapping("/tranches")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<ScheduleView> store(@RequestBody TrancheInput in) {
        Payable p = payable(in.type(), in.id(), true);
        if (!p.schedulable()) throw ApiException.of(HttpStatus.CONFLICT, p.label() + " is " + p.status() + "; it cannot be scheduled for payment.");
        String linked = linkedNotice(p);
        if (linked != null) throw ApiException.of(HttpStatus.CONFLICT, linked);
        String description = text(in.description(), 255, "description");
        if (description == null) throw ApiException.validation("description", "Describe the tranche (e.g. 30% upon delivery).");
        BigDecimal amount = amount(p, in.percentage(), in.amount());
        checkTotal(p, amount, null);
        Integer next = jdbc.queryForObject("select coalesce(max(sequence_no), 0) + 1 from payment_tranches where " + owner(p) + " = ?", Integer.class, p.id());
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                insert into payment_tranches (contract_id, purchase_order_id, sequence_no, description, percentage, amount, due_date, deliverable,
                  responsibility_center, mfo_pap, uacs_code, status, remarks, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?)""",
                "contract".equals(p.type()) ? p.id() : null, "contract".equals(p.type()) ? null : p.id(), next, description,
                in.percentage() == null ? null : in.percentage().setScale(4, RoundingMode.HALF_UP), amount,
                in.dueDate() == null ? null : java.sql.Date.valueOf(in.dueDate()), blank(in.deliverable()), text(in.responsibilityCenter(), 100, "responsibility_center"),
                text(in.mfoPap(), 255, "mfo_pap"), text(in.uacsCode(), 50, "uacs_code"), blank(in.remarks()), current.user().getId(), now, now);
        Long tid = jdbc.queryForObject("select max(id) from payment_tranches where " + owner(p) + " = ?", Long.class, p.id());
        audit.record("created", TYPE, tid, "Payment tranche " + next + " (₱" + ProcurementRules.peso(amount) + ", " + description + ") scheduled on " + p.label(),
                null, snapshot(tranche(tid, false)));
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleView(p));
    }

    @PutMapping("/tranches/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public ScheduleView update(@PathVariable Long id, @RequestBody TrancheInput in) {
        Tranche t = tranche(id, true);
        Payable p = payable(t.payableType(), t.payableId(), true);
        if (!"pending".equals(t.status())) throw ApiException.of(HttpStatus.CONFLICT, "Only a pending tranche can be edited; this one is " + t.status() + ".");
        Map<String, Object> before = snapshot(t);
        String description = in.description() == null ? t.description() : text(in.description(), 255, "description");
        if (description == null) throw ApiException.validation("description", "Describe the tranche.");
        BigDecimal percentage = in.percentage() != null ? in.percentage() : in.amount() != null ? null : t.percentage();
        BigDecimal amount = in.percentage() == null && in.amount() == null ? t.amount() : amount(p, percentage, in.amount());
        checkTotal(p, amount, id);
        jdbc.update("""
                update payment_tranches set description = ?, percentage = ?, amount = ?, due_date = ?, deliverable = ?, responsibility_center = ?,
                  mfo_pap = ?, uacs_code = ?, remarks = ?, updated_at = ?
                where id = ?""",
                description, percentage == null ? null : percentage.setScale(4, RoundingMode.HALF_UP), amount,
                pick(in.dueDate(), t.dueDate()) == null ? null : java.sql.Date.valueOf(pick(in.dueDate(), t.dueDate())),
                in.deliverable() == null ? t.deliverable() : blank(in.deliverable()),
                in.responsibilityCenter() == null ? t.rc() : text(in.responsibilityCenter(), 100, "responsibility_center"), in.mfoPap() == null ? t.mfoPap() : text(in.mfoPap(), 255, "mfo_pap"),
                in.uacsCode() == null ? t.uacs() : text(in.uacsCode(), 50, "uacs_code"), in.remarks() == null ? t.remarks() : blank(in.remarks()),
                Timestamp.valueOf(Times.now()), id);
        audit.record("updated", TYPE, id, "Edited payment tranche " + t.seq() + " on " + p.label(), before, snapshot(tranche(id, false)));
        return scheduleView(p);
    }

    @DeleteMapping("/tranches/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public ScheduleView destroy(@PathVariable Long id) {
        Tranche t = tranche(id, true);
        Payable p = payable(t.payableType(), t.payableId(), true);
        if (!"pending".equals(t.status())) throw ApiException.of(HttpStatus.CONFLICT, "A tranche approved for payment or paid is part of the record.");
        jdbc.update("delete from payment_tranches where id = ?", id);
        audit.record("deleted", TYPE, id, "Removed payment tranche " + t.seq() + " from " + p.label(), snapshot(t), null);
        return scheduleView(p);
    }

    // ───────────────────────── approval & payment ─────────────────────────

    public record ApproveInput(String orsNumber, String dvNumber, String responsibilityCenter, String mfoPap, String uacsCode, String remarks) {
    }

    /** Approves a pending tranche for payment (DV Box D), assigning the ORS and DV numbers. */
    @PostMapping("/tranches/{id}/for-payment")
    @PreAuthorize("@perm.any('approve_payments')")
    @Transactional
    public ScheduleView forPayment(@PathVariable Long id, @RequestBody(required = false) ApproveInput body) {
        ApproveInput in = body == null ? new ApproveInput(null, null, null, null, null, null) : body;
        Tranche t = tranche(id, true);
        Payable p = payable(t.payableType(), t.payableId(), true);
        if (!"pending".equals(t.status())) throw ApiException.of(HttpStatus.CONFLICT, "This tranche is already " + t.status().replace('_', ' ') + ".");
        if (!p.payable()) throw ApiException.of(HttpStatus.CONFLICT, p.label() + " is " + p.status() + "; payments are approved once it is in force.");
        User me = current.user();
        if (me.getId().equals(t.createdBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You scheduled this tranche, so someone else has to approve it for payment.");
        String ors = text(in.orsNumber(), 50, "ors_number");
        String dv = text(in.dvNumber(), 50, "dv_number");
        if (ors != null && taken("ors_number", ors, id)) throw ApiException.validation("ors_number", "ORS " + ors + " is already used.");
        if (dv != null && taken("dv_number", dv, id)) throw ApiException.validation("dv_number", "DV " + dv + " is already used.");
        if (ors == null) ors = t.ors() != null ? t.ors() : numbers.monthly("ORS", "payment_tranches.ors_number");
        if (dv == null) dv = t.dv() != null ? t.dv() : numbers.monthly("DV", "payment_tranches.dv_number");
        jdbc.update("""
                update payment_tranches set status = 'for_payment', ors_number = ?, dv_number = ?, responsibility_center = ?, mfo_pap = ?, uacs_code = ?,
                  remarks = ?, approved_by = ?, approved_at = ?, updated_at = ?
                where id = ?""",
                ors, dv, in.responsibilityCenter() == null ? t.rc() : text(in.responsibilityCenter(), 100, "responsibility_center"), in.mfoPap() == null ? t.mfoPap() : text(in.mfoPap(), 255, "mfo_pap"),
                in.uacsCode() == null ? t.uacs() : text(in.uacsCode(), 50, "uacs_code"), in.remarks() == null ? t.remarks() : blank(in.remarks()),
                me.getId(), Timestamp.valueOf(Times.now()), Timestamp.valueOf(Times.now()), id);
        audit.record("approved", TYPE, id, "Payment tranche " + t.seq() + " of " + p.label() + " (₱" + ProcurementRules.peso(t.amount()) + ") approved for payment; ORS "
                + ors + ", DV " + dv, Map.of("status", "pending"), Map.of("status", "for_payment", "ors_number", ors, "dv_number", dv));
        return scheduleView(p);
    }

    public record PayInput(LocalDate paidDate, String paymentMethod, String paymentReference, BigDecimal amountPaid, BigDecimal retentionAmount,
                           BigDecimal ldDeducted, BigDecimal withholdingTax, String remarks) {
    }

    /** Records the release: check/ADA/LDDAP reference, deductions, and the net paid (gross less deductions). */
    @PostMapping("/tranches/{id}/pay")
    @PreAuthorize(MANAGE)
    @Transactional
    public ScheduleView pay(@PathVariable Long id, @RequestBody PayInput in) {
        Tranche t = tranche(id, true);
        Payable p = payable(t.payableType(), t.payableId(), true);
        if (!"for_payment".equals(t.status())) {
            throw ApiException.of(HttpStatus.CONFLICT, "pending".equals(t.status()) ? "The tranche has to be approved for payment first." : "This tranche is already paid.");
        }
        User me = current.user();
        if (me.getId().equals(t.approvedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You approved this tranche for payment, so someone else has to release it.");
        if (in.paymentMethod() == null || !METHODS.contains(in.paymentMethod())) throw ApiException.validation("payment_method", "Choose check, ADA or LDDAP-ADA.");
        String ref = text(in.paymentReference(), 100, "payment_reference");
        if (ref == null) throw ApiException.validation("payment_reference", "Enter the check, ADA or LDDAP-ADA number.");
        LocalDate date = in.paidDate() == null ? LocalDate.now(Times.ZONE) : in.paidDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("paid_date", "The payment date cannot be in the future.");
        BigDecimal retention = money(in.retentionAmount(), "retention_amount");
        BigDecimal ld = money(in.ldDeducted(), "ld_deducted");
        BigDecimal wtax = money(in.withholdingTax(), "withholding_tax");
        BigDecimal ldLeft = ldAvailable(p);
        if (ld.compareTo(ldLeft) > 0) {
            throw ApiException.validation("ld_deducted", ldLeft.signum() == 0 ? "No liquidated damages are assessed and undeducted on " + p.label() + "."
                    : "Only ₱" + ProcurementRules.peso(ldLeft) + " of assessed liquidated damages is left to deduct.");
        }
        BigDecimal deductions = retention.add(ld).add(wtax);
        if (deductions.compareTo(t.amount()) > 0) throw ApiException.validation("retention_amount", "Deductions (₱" + ProcurementRules.peso(deductions) + ") exceed the tranche.");
        BigDecimal net = t.amount().subtract(deductions);
        if (in.amountPaid() != null && in.amountPaid().compareTo(net) != 0) {
            throw ApiException.validation("amount_paid", "The amount paid is the tranche less deductions: ₱" + ProcurementRules.peso(net) + ".");
        }
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                update payment_tranches set status = 'paid', paid_date = ?, payment_method = ?, payment_reference = ?, amount_paid = ?, retention_amount = ?,
                  ld_deducted = ?, withholding_tax = ?, remarks = ?, paid_by = ?, paid_at = ?, updated_at = ?
                where id = ?""",
                java.sql.Date.valueOf(date), in.paymentMethod(), ref, net, retention, ld, wtax, in.remarks() == null ? t.remarks() : blank(in.remarks()),
                me.getId(), now, now, id);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "paid");
        after.put("amount_paid", net);
        after.put("retention_amount", retention);
        after.put("ld_deducted", ld);
        after.put("withholding_tax", wtax);
        after.put("payment_reference", ref);
        audit.record("paid", TYPE, id, "Payment tranche " + t.seq() + " of " + p.label() + " paid: ₱" + ProcurementRules.peso(net) + " net by "
                + label(in.paymentMethod()) + " " + ref, Map.of("status", "for_payment"), after);
        return scheduleView(p);
    }

    // ───────────────────────── forms ─────────────────────────

    /** Obligation Request and Status for a tranche. */
    @GetMapping("/tranches/{id}/ors")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> ors(@PathVariable Long id, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        Tranche t = tranche(id, false);
        PaymentClaim claim = claim(t, entity, fund);
        String no = t.ors() != null ? t.ors() : t.payableNumber() + "-T" + t.seq();
        return file(orsForm.generate(claim), "ORS_" + no + ".xlsx", "ORS for tranche " + t.seq() + " of " + t.payableNumber(), id);
    }

    /** Disbursement Voucher (COA Appendix 32) for a tranche. */
    @GetMapping("/tranches/{id}/dv")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> dv(@PathVariable Long id, @RequestParam(name = "entity_name", required = false) String entity,
                                     @RequestParam(name = "fund_cluster", required = false) String fund) {
        Tranche t = tranche(id, false);
        PaymentClaim claim = claim(t, entity, fund);
        String no = t.dv() != null ? t.dv() : t.payableNumber() + "-T" + t.seq();
        return file(dvForm.generate(claim), "DV_" + no + ".xlsx", "DV for tranche " + t.seq() + " of " + t.payableNumber(), id);
    }

    private PaymentClaim claim(Tranche t, String entity, String fund) {
        Payable p = payable(t.payableType(), t.payableId(), false);
        StringBuilder particulars = new StringBuilder("Payment of tranche " + t.seq() + " — " + t.description() + " — under " + p.label());
        if (p.title() != null && !p.title().equals(p.number())) particulars.append(" (").append(p.title()).append(")");
        if (t.percentage() != null) particulars.append(", ").append(t.percentage().stripTrailingZeros().toPlainString()).append("% of ₱").append(ProcurementRules.peso(p.base()));
        if (t.deliverable() != null) particulars.append(". Deliverable: ").append(t.deliverable());
        boolean paid = "paid".equals(t.status());
        BigDecimal ld = paid ? t.ld() : BigDecimal.ZERO;
        BigDecimal retention = paid ? t.retention() : BigDecimal.ZERO;
        BigDecimal wtax = paid ? t.wtax() : BigDecimal.ZERO;
        LocalDate date = t.approvedAt() != null ? t.approvedAt().toLocalDate() : LocalDate.now(Times.ZONE);
        return new PaymentClaim(given(entity, "agency_name"), given(fund, "fund_cluster"), t.ors(), t.dv(), date, p.supplier(), p.tin(), p.address(),
                particulars.toString(), t.rc(), t.mfoPap(), t.uacs(), t.amount(), retention, ld, wtax,
                paid ? t.amountPaid() : t.amount(), t.method(), t.reference(), t.paidDate(), t.status());
    }

    private String given(String v, String setting) {
        return v != null && !v.isBlank() ? v.trim() : settings.get(setting, "");
    }

    ResponseEntity<byte[]> file(byte[] bytes, String filename, String what, Long id) {
        audit.record("downloaded", TYPE, id, "Downloaded " + what + " (" + com.avtech.pams.report.PdfFormAdvice.fileName(filename) + ")", null, Map.of("file", com.avtech.pams.report.PdfFormAdvice.fileName(filename)));
        return xlsx(bytes, filename);
    }

    static ResponseEntity<byte[]> xlsx(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename.replaceAll("[\\\\/:*?\"<>|]", "-"), StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    // ───────────────────────── rules ─────────────────────────

    private BigDecimal amount(Payable p, BigDecimal percentage, BigDecimal amount) {
        if (percentage != null) {
            if (percentage.signum() <= 0 || percentage.compareTo(HUNDRED) > 0 || percentage.scale() > 4) throw ApiException.validation("percentage", "Enter a percentage above 0 and up to 100.");
            BigDecimal a = p.base().multiply(percentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (a.signum() <= 0) throw ApiException.validation("percentage", "That share of ₱" + ProcurementRules.peso(p.base()) + " is nothing.");
            return a;
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) throw ApiException.validation("amount", "Enter the tranche as a percentage or an amount in pesos.");
        return amount;
    }

    /** Tranches together stay within the contract or PO amount. */
    private void checkTotal(Payable p, BigDecimal amount, Long except) {
        BigDecimal others = jdbc.queryForObject("select coalesce(sum(amount), 0) from payment_tranches where " + owner(p) + " = ? and id <> ?",
                BigDecimal.class, p.id(), except == null ? -1L : except);
        BigDecimal total = (others == null ? BigDecimal.ZERO : others).add(amount);
        if (total.compareTo(p.base()) > 0) {
            BigDecimal room = p.base().subtract(others == null ? BigDecimal.ZERO : others).max(BigDecimal.ZERO);
            throw ApiException.validation("amount", "The schedule would total ₱" + ProcurementRules.peso(total) + ", over the ₱" + ProcurementRules.peso(p.base())
                    + " of " + p.label() + "; ₱" + ProcurementRules.peso(room) + " is left to schedule.");
        }
    }

    /** A contract and its purchase order are one obligation: its payments are scheduled on one or the other. */
    private String linkedNotice(Payable p) {
        if ("contract".equals(p.type()) && p.poId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from payment_tranches where purchase_order_id = ?", Integer.class, p.poId());
            if (n != null && n > 0) return "Payments for this contract are already scheduled on its purchase order.";
        }
        if ("purchase_order".equals(p.type()) && p.contractId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from payment_tranches where contract_id = ?", Integer.class, p.contractId());
            if (n != null && n > 0) return "Payments for this purchase order are already scheduled on its contract.";
        }
        return null;
    }

    private BigDecimal ldDeducted(Payable p) {
        BigDecimal v = jdbc.queryForObject("select coalesce(sum(ld_deducted), 0) from payment_tranches where status = 'paid' and (contract_id = ? or purchase_order_id = ?)",
                BigDecimal.class, p.contractId() == null ? -1L : p.contractId(), p.poId() == null ? -1L : p.poId());
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal ldAvailable(Payable p) {
        return p.ldAssessed().subtract(ldDeducted(p)).max(BigDecimal.ZERO);
    }

    private boolean taken(String column, String value, Long except) {
        Integer n = jdbc.queryForObject("select count(*) from payment_tranches where " + column + " = ? and id <> ?", Integer.class, value, except);
        return n != null && n > 0;
    }

    // ───────────────────────── loading ─────────────────────────

    private static String owner(Payable p) {
        return "contract".equals(p.type()) ? "contract_id" : "purchase_order_id";
    }

    Payable payable(String type, Long id, boolean lock) {
        if (id == null || !List.of("contract", "purchase_order").contains(type)) throw ApiException.validation("type", "Schedule payments on a contract or a purchase order.");
        if ("contract".equals(type)) {
            if (lock) db.lockRows("contracts", "id = ?", id);
            List<Payable> rows = jdbc.query("""
                    select c.id, c.contract_number, c.title, c.status, c.supplier_id, s.name, s.tin, s.address, c.amount, c.purchase_order_id, c.ld_amount
                    from contracts c left join suppliers s on s.id = c.supplier_id
                    where c.id = ? and c.deleted_at is null""", (rs, i) -> new Payable("contract", rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getBigDecimal(9), rs.getLong(1),
                    (Long) rs.getObject(10, Long.class), nz(rs.getBigDecimal(11))), id);
            if (rows.isEmpty()) throw ApiException.notFound("Contract");
            return rows.get(0);
        }
        if (lock) db.lockRows("purchase_orders", "id = ?", id);
        List<Payable> rows = jdbc.query("""
                select po.id, po.po_number, coalesce((select r.title from rfqs r where r.id = po.rfq_id), (select b.title from biddings b where b.id = po.bidding_id)),
                       po.status, po.supplier_id, s.name, s.tin, s.address, po.total_amount,
                       (select max(c.id) from contracts c where c.purchase_order_id = po.id and c.deleted_at is null and c.status <> 'terminated'),
                       (select max(c.ld_amount) from contracts c where c.purchase_order_id = po.id and c.deleted_at is null)
                from purchase_orders po left join suppliers s on s.id = po.supplier_id
                where po.id = ? and po.deleted_at is null""", (rs, i) -> new Payable("purchase_order", rs.getLong(1), rs.getString(2),
                rs.getString(3) == null ? "Purchase order " + rs.getString(2) : rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getBigDecimal(9), (Long) rs.getObject(10, Long.class), rs.getLong(1), nz(rs.getBigDecimal(11))), id);
        if (rows.isEmpty()) throw ApiException.notFound("Purchase order");
        return rows.get(0);
    }

    private static final String SELECT = """
            select t.id, t.contract_id, t.purchase_order_id, t.sequence_no, t.description, t.percentage, t.amount, t.due_date, t.deliverable,
                   t.responsibility_center, t.mfo_pap, t.uacs_code, t.status, t.ors_number, t.dv_number, t.created_by, t.approved_by, t.approved_at,
                   t.paid_by, t.paid_date, t.payment_method, t.payment_reference, t.amount_paid, t.retention_amount, t.ld_deducted, t.withholding_tax,
                   t.remarks, coalesce(c.contract_number, po.po_number) as payable_number, coalesce(s1.name, s2.name) as supplier_name,
                   coalesce(c.status, po.status) as parent_status
            from payment_tranches t
            left join contracts c on c.id = t.contract_id
            left join purchase_orders po on po.id = t.purchase_order_id
            left join suppliers s1 on s1.id = c.supplier_id
            left join suppliers s2 on s2.id = po.supplier_id
            where (c.id is null or c.deleted_at is null) and (po.id is null or po.deleted_at is null)""";

    private List<Tranche> query(String tail, Object... args) {
        return jdbc.query(SELECT + tail, (rs, i) -> row(rs), args);
    }

    Tranche tranche(Long id, boolean lock) {
        if (lock) db.lockRows("payment_tranches", "id = ?", id);
        List<Tranche> list = query(" and t.id = ?", id);
        if (list.isEmpty()) throw ApiException.notFound("Payment tranche");
        return list.get(0);
    }

    private static Tranche row(ResultSet rs) throws SQLException {
        return new Tranche(rs.getLong("id"), rs.getObject("contract_id", Long.class), rs.getObject("purchase_order_id", Long.class), rs.getInt("sequence_no"),
                rs.getString("description"), rs.getBigDecimal("percentage"), rs.getBigDecimal("amount"), date(rs, "due_date"), rs.getString("deliverable"),
                rs.getString("responsibility_center"), rs.getString("mfo_pap"), rs.getString("uacs_code"), rs.getString("status"), rs.getString("ors_number"),
                rs.getString("dv_number"), rs.getObject("created_by", Long.class), rs.getObject("approved_by", Long.class),
                rs.getTimestamp("approved_at") == null ? null : rs.getTimestamp("approved_at").toLocalDateTime(), rs.getObject("paid_by", Long.class),
                date(rs, "paid_date"), rs.getString("payment_method"), rs.getString("payment_reference"), rs.getBigDecimal("amount_paid"),
                rs.getBigDecimal("retention_amount"), rs.getBigDecimal("ld_deducted"), rs.getBigDecimal("withholding_tax"), rs.getString("remarks"),
                rs.getString("payable_number"), rs.getString("supplier_name"), rs.getString("parent_status"));
    }

    private ScheduleView scheduleView(Payable p) {
        List<Tranche> list = query(" and t." + owner(p) + " = ? order by t.sequence_no, t.id", p.id());
        List<TrancheView> views = views(list, p);
        BigDecimal scheduled = sum(list, Tranche::amount, t -> true);
        BigDecimal paid = sum(list, Tranche::amount, t -> "paid".equals(t.status()));
        BigDecimal released = sum(list, Tranche::amountPaid, t -> "paid".equals(t.status()));
        BigDecimal ldDone = ldDeducted(p);
        boolean manage = current.any("manage_payments");
        return new ScheduleView(p.type(), p.id(), p.number(), p.title(), p.status(), new Ref(p.supplierId(), p.supplier()), p.base(), scheduled,
                p.base().subtract(scheduled), paid, released, scheduled.subtract(paid), (int) views.stream().filter(TrancheView::overdue).count(),
                p.ldAssessed(), ldDone, p.ldAssessed().subtract(ldDone).max(BigDecimal.ZERO), linkedNotice(p), manage,
                manage && p.schedulable() && linkedNotice(p) == null, views);
    }

    /** {@code p} is the payable when every tranche is on it (the schedule); null for a register across payables. */
    private List<TrancheView> views(List<Tranche> list, Payable p) {
        Map<Long, String> names = names(list.stream().flatMap(t -> java.util.stream.Stream.of(t.createdBy(), t.approvedBy(), t.paidBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        User me = current.user();
        boolean sa = me.isSuperAdmin();
        boolean manage = current.any("manage_payments");
        boolean approve = current.any("approve_payments");
        LocalDate today = LocalDate.now(Times.ZONE);
        List<TrancheView> out = new ArrayList<>();
        for (Tranche t : list) {
            boolean overdue = t.open() && t.dueDate() != null && t.dueDate().isBefore(today);
            boolean parentPayable = p != null ? p.payable() : "contract".equals(t.payableType())
                    ? List.of("active", "completed").contains(t.parentStatus()) : List.of("sent", "confirmed", "partially_received", "received").contains(t.parentStatus());
            out.add(new TrancheView(t.id(), t.payableType(), t.payableId(), t.payableNumber(), t.supplier(), t.seq(), t.description(), t.percentage(), t.amount(),
                    t.dueDate(), t.deliverable(), t.rc(), t.mfoPap(), t.uacs(), t.status(), overdue, overdue ? (int) ChronoUnit.DAYS.between(t.dueDate(), today) : null,
                    t.ors(), t.dv(), t.paidDate(), t.method(), t.reference(), t.amountPaid(), t.retention(), t.ld(), t.wtax(), t.remarks(),
                    ref(t.createdBy(), names), ref(t.approvedBy(), names), Times.toOffset(t.approvedAt()), ref(t.paidBy(), names),
                    manage && "pending".equals(t.status()),
                    approve && "pending".equals(t.status()) && parentPayable && (sa || !me.getId().equals(t.createdBy())),
                    manage && "for_payment".equals(t.status()) && (sa || !me.getId().equals(t.approvedBy()))));
        }
        return out;
    }

    Map<Long, String> names(Set<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        jdbc.query("select id, name from users where id in (" + in + ")", rs -> {
            m.put(rs.getLong(1), rs.getString(2));
        }, ids.toArray());
        return m;
    }

    static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id, names.get(id));
    }

    private static BigDecimal sum(List<Tranche> list, java.util.function.Function<Tranche, BigDecimal> f, java.util.function.Predicate<Tranche> when) {
        return list.stream().filter(when).map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, Object> snapshot(Tranche t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sequence_no", t.seq());
        m.put("description", t.description());
        m.put("percentage", t.percentage());
        m.put("amount", t.amount());
        m.put("due_date", t.dueDate() == null ? null : t.dueDate().toString());
        m.put("status", t.status());
        return m;
    }

    static String label(String method) {
        return switch (method) {
            case "check" -> "check";
            case "ada" -> "ADA";
            case "lddap_ada" -> "LDDAP-ADA";
            default -> method;
        };
    }

    private static BigDecimal money(BigDecimal v, String field) {
        if (v == null) return BigDecimal.ZERO.setScale(2);
        if (v.signum() < 0 || v.scale() > 2) throw ApiException.validation(field, "Enter a peso amount of zero or more.");
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static <T> T pick(T given, T current) {
        return given != null ? given : current;
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String text(String s, int max, String field) {
        String v = blank(s);
        if (v != null && v.length() > max) throw ApiException.validation(field, "Keep it within " + max + " characters.");
        return v;
    }
}

package com.avtech.pams.contract;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.procurement.AwardSupport;
import com.avtech.pams.procurement.ProcurementRules;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contracts on awards (RA 12009). A contract is drawn up on an award with its Notice of Award, at
 * the awarded price; it takes effect once signed and secured by a performance security of the
 * required amount, activated by someone other than its preparer. Amendments change price or term
 * within 10% of the original price; delivery extensions push the deadline; liquidated damages run
 * at one-tenth of one percent of the undelivered value per day of delay, up to 10% of the contract.
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private static final String TYPE = "App\\Models\\Contract";
    private static final String READ = "@perm.any('view_contracts', 'create_contracts', 'edit_contracts')";
    private static final String MANAGE = "@perm.any('create_contracts', 'edit_contracts')";
    private static final BigDecimal LD_RATE = new BigDecimal("0.001");
    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    /** Performance security by form: share of the contract price for goods/services/consulting, and for infrastructure. */
    static final Map<String, BigDecimal[]> PERFORMANCE_FORMS = new LinkedHashMap<>();

    static {
        PERFORMANCE_FORMS.put("Cash / Cashier's or Manager's Check", new BigDecimal[]{new BigDecimal("0.05"), new BigDecimal("0.10")});
        PERFORMANCE_FORMS.put("Bank Draft / Guarantee", new BigDecimal[]{new BigDecimal("0.05"), new BigDecimal("0.10")});
        PERFORMANCE_FORMS.put("Irrevocable Letter of Credit", new BigDecimal[]{new BigDecimal("0.05"), new BigDecimal("0.10")});
        PERFORMANCE_FORMS.put("Surety Bond", new BigDecimal[]{new BigDecimal("0.30"), new BigDecimal("0.30")});
    }

    private final ContractRepository contracts;
    private final UserRepository users;
    private final AwardSupport awards;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final Db db;

    public ContractController(ContractRepository contracts, UserRepository users, AwardSupport awards, DocumentNumbers numbers,
                              NotificationService notifications, AuditService audit, CurrentUser current, JdbcTemplate jdbc, Db db) {
        this.contracts = contracts;
        this.users = users;
        this.awards = awards;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
        this.db = db;
    }

    // ───────────────────────── views ─────────────────────────

    public record Ref(Long id, String name) {
    }

    public record AmendmentView(Long id, int amendmentNumber, String title, String description, BigDecimal amountChange, LocalDate newEndDate,
                                String status, Ref preparer, Ref approver, OffsetDateTime approvedAt, String remarks, boolean canApprove) {
    }

    public record SecurityView(Long id, String securityType, String form, String referenceNumber, BigDecimal amount, BigDecimal percentage,
                               LocalDate postedDate, LocalDate validityDate, String status, String remarks, BigDecimal required, boolean sufficient) {
    }

    public record ExtensionView(Long id, int requestedDays, Integer approvedDays, String reason, String status, LocalDate requestedDate,
                                Ref approver, OffsetDateTime approvedAt, String remarks) {
    }

    public record CalloffView(Long id, String calloffNumber, String title, Ref supplier, BigDecimal amount, LocalDate calloffDate, String status, String remarks) {
    }

    public record Delivery(BigDecimal orderValue, BigDecimal deliveredValue, BigDecimal undeliveredValue, boolean complete, LocalDate lastDelivery) {
    }

    public record LdEstimate(LocalDate deadline, LocalDate asOf, int daysDelayed, BigDecimal basis, BigDecimal rate, BigDecimal amount, boolean capped,
                             boolean mayTerminate) {
    }

    public record ContractView(Long id, String contractNumber, String title, String contractType, String status, Ref supplier, Ref purchaseRequest,
                               Ref source, String sourceType, Ref purchaseOrder, BigDecimal amount, BigDecimal originalAmount, BigDecimal variationPercent,
                               LocalDate startDate, LocalDate endDate, LocalDate signedDate, Integer deliveryLeadDays, Integer daysToExpiry,
                               boolean expiring, boolean expired, String description, String terms, String remarks, Ref preparer,
                               LocalDate deliveryDeadline, int approvedExtensionDays, Delivery delivery, LdEstimate ldEstimate,
                               BigDecimal ldAmount, Integer ldDaysDelayed, LocalDate ldAsOfDate, String ldRemarks, String fundSource, String cafNumber,
                               BigDecimal calloffsTotal, List<AmendmentView> amendments, List<SecurityView> securities, List<ExtensionView> extensions,
                               List<CalloffView> calloffs, boolean performanceSecured, boolean canEdit, boolean canActivate, boolean canComplete,
                               boolean canTerminate, boolean canDelete, OffsetDateTime createdAt) {
    }

    // ───────────────────────── reads ─────────────────────────

    @GetMapping
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<ContractView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                            @RequestParam(name = "contract_type", required = false) String type,
                                            @RequestParam(name = "supplier_id", required = false) Long supplier,
                                            @RequestParam(name = "expiring_days", required = false) Integer expiringDays,
                                            @RequestParam(required = false) String search) {
        LocalDate today = LocalDate.now(Times.ZONE);
        Page<Contract> result = contracts.search(blank(status), blank(type), supplier, today, expiringDays == null ? null : today.plusDays(expiringDays),
                blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<Contract, ContractView> v = views(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public ContractView show(@PathVariable Long id) {
        return view(contracts.findLive(id).orElseThrow(() -> ApiException.notFound("Contract")));
    }

    public record AwardSource(String type, Long id, String number, String title, Ref supplier, Ref purchaseRequest, BigDecimal amount, String noaNumber,
                              LocalDate ntpDate, Integer deliveryLeadDays, String deliveryTerm, Ref purchaseOrder) {
    }

    /** Awards with a Notice of Award and no contract yet (a terminated contract frees its award). */
    @GetMapping("/awarded-sources")
    @PreAuthorize(MANAGE)
    @Transactional(readOnly = true)
    public Map<String, Object> awardedSources() {
        return Map.of("data", sources());
    }

    private List<AwardSource> sources() {
        List<AwardSource> out = new ArrayList<>();
        jdbc.query("""
                select r.id, r.rfq_number, r.title, r.awarded_supplier_id, s.name, r.purchase_request_id, pr.pr_number, q.total_amount, r.noa_number,
                       r.ntp_date, r.delivery_lead_days, r.delivery_term, po.id, po.po_number
                from rfqs r join rfq_quotations q on q.rfq_id = r.id and q.is_winner = %s join suppliers s on s.id = r.awarded_supplier_id
                left join purchase_requests pr on pr.id = r.purchase_request_id
                left join purchase_orders po on po.rfq_id = r.id and po.deleted_at is null and po.status <> 'cancelled'
                where r.status = 'awarded' and r.noa_number is not null and r.deleted_at is null
                  and not exists (select 1 from contracts c where c.rfq_id = r.id and c.deleted_at is null and c.status <> 'terminated')
                order by r.id""".formatted(db.t()), rs -> {
            out.add(source("rfq", rs));
        });
        jdbc.query("""
                select b.id, b.bidding_number, b.title, b.awarded_supplier_id, s.name, b.purchase_request_id, pr.pr_number, b.awarded_amount, b.noa_number,
                       b.ntp_date, b.delivery_lead_days, b.delivery_term, po.id, po.po_number
                from biddings b join suppliers s on s.id = b.awarded_supplier_id left join purchase_requests pr on pr.id = b.purchase_request_id
                left join purchase_orders po on po.bidding_id = b.id and po.deleted_at is null and po.status <> 'cancelled'
                where b.status = 'awarded' and b.noa_number is not null and b.deleted_at is null
                  and not exists (select 1 from contracts c where c.bidding_id = b.id and c.deleted_at is null and c.status <> 'terminated')
                order by b.id""", rs -> {
            out.add(source("bidding", rs));
        });
        return out;
    }

    private static AwardSource source(String type, java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AwardSource(type, rs.getLong(1), rs.getString(2), rs.getString(3), new Ref(rs.getLong(4), rs.getString(5)),
                rs.getObject(6) == null ? null : new Ref(rs.getLong(6), rs.getString(7)), rs.getBigDecimal(8), rs.getString(9),
                rs.getObject(10) == null ? null : rs.getDate(10).toLocalDate(), (Integer) rs.getObject(11), rs.getString(12),
                rs.getObject(13) == null ? null : new Ref(rs.getLong(13), rs.getString(14)));
    }

    // ───────────────────────── lifecycle ─────────────────────────

    public record ContractInput(String sourceType, Long sourceId, String title, String contractType, LocalDate startDate, LocalDate endDate,
                                LocalDate signedDate, Integer deliveryLeadDays, String description, String terms, String remarks) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_contracts')")
    @Transactional
    public ResponseEntity<ContractView> store(@RequestBody ContractInput in) {
        if (in.sourceId() == null || !List.of("rfq", "bidding").contains(in.sourceType())) {
            throw ApiException.validation("source_id", "A contract rests on an award: choose the awarded RFQ or bidding.");
        }
        db.namedLock("pams.contract." + in.sourceType() + "." + in.sourceId());
        AwardSource src = sources().stream().filter(s -> s.type().equals(in.sourceType()) && s.id().equals(in.sourceId())).findFirst()
                .orElseThrow(() -> ApiException.validation("source_id", "That award has no Notice of Award yet, or already has a contract."));
        if (!awards.eligible(src.supplier().id())) throw ApiException.validation("source_id", src.supplier().name() + " is inactive or not eligible.");
        Contract c = new Contract(numbers.yearly("CON", "contracts.contract_number"), current.user().getId());
        c.setSupplierId(src.supplier().id());
        c.setPurchaseRequestId(src.purchaseRequest() == null ? null : src.purchaseRequest().id());
        if ("rfq".equals(src.type())) c.setRfqId(src.id());
        else c.setBiddingId(src.id());
        c.setPurchaseOrderId(src.purchaseOrder() == null ? null : src.purchaseOrder().id());
        c.setAmount(src.amount());
        c.setDeliveryLeadDays(src.deliveryLeadDays());
        c.setTitle(src.title());
        apply(c, in);
        contracts.saveAndFlush(c);
        audit.record("created", TYPE, c.getId(), "Contract " + c.getContractNumber() + " drafted on " + src.number() + " (" + src.supplier().name() + ", ₱"
                + ProcurementRules.peso(c.getAmount()) + ")", null, snapshot(c));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public ContractView update(@PathVariable Long id, @RequestBody ContractInput in) {
        Contract c = lock(id);
        if (Contract.ACTIVE.equals(c.getStatus())) {
            // In force, only the description and remarks change; the term and price move by amendment.
            if (in.title() != null || in.contractType() != null || in.startDate() != null || in.endDate() != null || in.signedDate() != null || in.deliveryLeadDays() != null) {
                throw ApiException.of(HttpStatus.CONFLICT, "The contract is in force; change its term or price through an amendment.");
            }
            if (in.description() != null) c.setDescription(blank(in.description()));
            if (in.terms() != null) c.setTerms(blank(in.terms()));
            if (in.remarks() != null) c.setRemarks(blank(in.remarks()));
        } else if (Contract.DRAFT.equals(c.getStatus())) {
            apply(c, in);
        } else {
            throw ApiException.of(HttpStatus.CONFLICT, "This contract is " + c.getStatus() + ".");
        }
        contracts.flush();
        audit.record("updated", TYPE, c.getId(), "Edited contract " + c.getContractNumber(), null, snapshot(c));
        return view(c);
    }

    /** Puts the signed contract in force: dates set, and a sufficient performance security posted. */
    @PostMapping("/{id}/activate")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView activate(@PathVariable Long id) {
        Contract c = lock(id);
        if (!Contract.DRAFT.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft contract can be put in force.");
        User me = current.user();
        if (me.getId().equals(c.getPreparedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You prepared this contract, so someone else has to put it in force.");
        if (c.getStartDate() == null || c.getEndDate() == null) throw ApiException.validation("start_date", "Set the start and end dates first.");
        if (c.getSignedDate() == null) throw ApiException.validation("signed_date", "Record the date the contract was signed.");
        if (!"Framework Agreement".equals(c.getContractType()) && c.getSecurities().stream().noneMatch(s -> isPerformance(s) && "posted".equals(s.getStatus()) && sufficient(c, s))) {
            throw ApiException.of(HttpStatus.CONFLICT, "Post a performance security of the required amount before the contract takes effect ("
                    + PERFORMANCE_FORMS.keySet().stream().map(f -> f + " ₱" + ProcurementRules.peso(required(c, f))).collect(Collectors.joining("; ")) + ").");
        }
        c.setStatus(Contract.ACTIVE);
        contracts.flush();
        audit.record("activated", TYPE, c.getId(), "Contract " + c.getContractNumber() + " in force from " + c.getStartDate(), Map.of("status", "draft"), Map.of("status", "active"));
        return view(c);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView complete(@PathVariable Long id) {
        Contract c = lock(id);
        if (!Contract.ACTIVE.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a contract in force can be completed.");
        Delivery d = delivery(c);
        if (d != null && !d.complete()) {
            throw ApiException.of(HttpStatus.CONFLICT, "₱" + ProcurementRules.peso(d.undeliveredValue()) + " of the purchase order is still undelivered.");
        }
        if (c.getExtensions().stream().anyMatch(e -> "pending".equals(e.getStatus()))) throw ApiException.of(HttpStatus.CONFLICT, "Decide the pending extension request first.");
        c.setStatus(Contract.COMPLETED);
        LdEstimate ld = ld(c, null);
        if (ld != null && ld.amount().signum() > 0 && c.getLdAsOfDate() == null) record(c, ld, "Assessed on completion");
        contracts.flush();
        audit.record("completed", TYPE, c.getId(), "Contract " + c.getContractNumber() + " completed" + (c.getLdAmount().signum() > 0
                ? "; liquidated damages ₱" + ProcurementRules.peso(c.getLdAmount()) : ""), Map.of("status", "active"), Map.of("status", "completed"));
        return view(c);
    }

    public record ReasonInput(String remarks) {
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView terminate(@PathVariable Long id, @RequestBody ReasonInput in) {
        String why = in == null ? null : blank(in.remarks());
        if (why == null) throw ApiException.validation("remarks", "State the ground for termination.");
        Contract c = lock(id);
        if (!c.isOpen()) throw ApiException.of(HttpStatus.CONFLICT, "This contract is already " + c.getStatus() + ".");
        String was = c.getStatus();
        c.setStatus(Contract.TERMINATED);
        c.setRemarks(why);
        contracts.flush();
        audit.record("terminated", TYPE, c.getId(), "Contract " + c.getContractNumber() + " terminated: " + why, Map.of("status", was), Map.of("status", "terminated"));
        notifications.notifyPermission(List.of("view_contracts", "edit_contracts"), current.user().getId(), "contract_terminated", "Contract terminated",
                c.getContractNumber() + " (" + awards.supplierName(c.getSupplierId()) + ") was terminated: " + why, TYPE, c.getId());
        return view(c);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('create_contracts')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        Contract c = lock(id);
        if (!Contract.DRAFT.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft contract can be deleted; terminate it instead.");
        c.setDeletedAt(Times.now());
        audit.record("deleted", TYPE, c.getId(), "Deleted contract " + c.getContractNumber(), snapshot(c), null);
        return Map.of("message", c.getContractNumber() + " deleted.");
    }

    // ───────────────────────── securities ─────────────────────────

    public record SecurityInput(String securityType, String form, String referenceNumber, BigDecimal amount, LocalDate postedDate, LocalDate validityDate,
                                String status, String remarks) {
    }

    @PostMapping("/{id}/securities")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<ContractView> storeSecurity(@PathVariable Long id, @RequestBody SecurityInput in) {
        Contract c = lock(id);
        if (!c.isOpen()) throw ApiException.of(HttpStatus.CONFLICT, "This contract is " + c.getStatus() + ".");
        if (in.securityType() == null || !ContractSecurity.TYPES.contains(in.securityType())) throw ApiException.validation("security_type", "Choose the kind of security.");
        String form = blank(in.form());
        if ("Performance Security".equals(in.securityType()) && (form == null || !PERFORMANCE_FORMS.containsKey(form))) {
            throw ApiException.validation("form", "Choose the form of the performance security.");
        }
        if (in.amount() == null || in.amount().signum() <= 0 || in.amount().scale() > 2) throw ApiException.validation("amount", "Enter the amount in pesos.");
        LocalDate posted = in.postedDate() == null ? LocalDate.now(Times.ZONE) : in.postedDate();
        if (posted.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("posted_date", "The posting date cannot be in the future.");
        if (in.validityDate() != null && in.validityDate().isBefore(posted)) throw ApiException.validation("validity_date", "Validity ends before it was posted.");
        if (isPerformanceType(in.securityType()) && c.getEndDate() != null && in.validityDate() != null && in.validityDate().isBefore(c.getEndDate())) {
            throw ApiException.validation("validity_date", "A performance security must stay valid until the contract ends (" + c.getEndDate() + ").");
        }
        ContractSecurity s = new ContractSecurity(c);
        s.setSecurityType(in.securityType());
        s.setForm(form);
        s.setReferenceNumber(blank(in.referenceNumber()));
        s.setAmount(in.amount());
        s.setPercentage(c.originalAmount().signum() == 0 ? null : in.amount().multiply(BigDecimal.valueOf(100)).divide(c.originalAmount(), 2, RoundingMode.HALF_UP));
        s.setPostedDate(posted);
        s.setValidityDate(in.validityDate());
        s.setRemarks(blank(in.remarks()));
        c.getSecurities().add(s);
        contracts.flush();
        audit.record("security_posted", TYPE, c.getId(), in.securityType() + " (" + (form == null ? "" : form + ", ") + "₱" + ProcurementRules.peso(in.amount())
                + ") posted for " + c.getContractNumber(), null, Map.of("security_type", in.securityType(), "amount", in.amount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c));
    }

    /** Returns or forfeits a posted security. */
    @PutMapping("/securities/{securityId}")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView updateSecurity(@PathVariable Long securityId, @RequestBody SecurityInput in) {
        Contract c = lock(contracts.contractOfSecurity(securityId).orElseThrow(() -> ApiException.notFound("Security")));
        ContractSecurity s = c.getSecurities().stream().filter(x -> x.getId().equals(securityId)).findFirst().orElseThrow();
        if (!"posted".equals(s.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This security was already " + s.getStatus() + ".");
        if (!List.of("returned", "forfeited").contains(in.status())) throw ApiException.validation("status", "A posted security is returned or forfeited.");
        if (isPerformance(s) && "returned".equals(in.status()) && Contract.ACTIVE.equals(c.getStatus())) {
            throw ApiException.of(HttpStatus.CONFLICT, "The performance security is returned only after the contract is completed.");
        }
        String why = blank(in.remarks());
        if ("forfeited".equals(in.status()) && why == null) throw ApiException.validation("remarks", "State the ground for forfeiture.");
        s.setStatus(in.status());
        if (why != null) s.setRemarks(why);
        contracts.flush();
        audit.record("security_" + in.status(), TYPE, c.getId(), s.getSecurityType() + " for " + c.getContractNumber() + " " + in.status() + (why == null ? "" : ": " + why),
                Map.of("status", "posted"), Map.of("status", in.status()));
        return view(c);
    }

    @DeleteMapping("/securities/{securityId}")
    @PreAuthorize(MANAGE)
    @Transactional
    public ContractView destroySecurity(@PathVariable Long securityId) {
        Contract c = lock(contracts.contractOfSecurity(securityId).orElseThrow(() -> ApiException.notFound("Security")));
        if (!Contract.DRAFT.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Securities on a contract in force are part of the record; return or forfeit them.");
        c.getSecurities().removeIf(x -> x.getId().equals(securityId));
        contracts.flush();
        return view(c);
    }

    // ───────────────────────── amendments ─────────────────────────

    public record AmendmentInput(String title, String description, BigDecimal amountChange, LocalDate newEndDate, String remarks) {
    }

    @PostMapping("/{id}/amendments")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<ContractView> storeAmendment(@PathVariable Long id, @RequestBody AmendmentInput in) {
        Contract c = lock(id);
        if (!Contract.ACTIVE.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Amendments are made to a contract in force; edit a draft directly.");
        String title = blank(in.title());
        if (title == null) throw ApiException.validation("title", "Give the amendment a title.");
        BigDecimal change = in.amountChange() == null ? BigDecimal.ZERO : in.amountChange();
        if (change.scale() > 2) throw ApiException.validation("amount_change", "Enter the change in pesos.");
        if (change.signum() == 0 && in.newEndDate() == null) throw ApiException.validation("amount_change", "An amendment changes the price, the end date, or both.");
        if (in.newEndDate() != null && c.getStartDate() != null && in.newEndDate().isBefore(c.getStartDate())) throw ApiException.validation("new_end_date", "The new end date is before the start.");
        checkVariation(c, change, null);
        int next = c.getAmendments().stream().mapToInt(ContractAmendment::getAmendmentNumber).max().orElse(0) + 1;
        ContractAmendment a = new ContractAmendment(c, next, current.user().getId());
        a.setTitle(title);
        a.setDescription(blank(in.description()));
        a.setAmountChange(change.setScale(2, RoundingMode.HALF_UP));
        a.setNewEndDate(in.newEndDate());
        a.setRemarks(blank(in.remarks()));
        c.getAmendments().add(a);
        contracts.flush();
        audit.record("amendment_drafted", TYPE, c.getId(), "Amendment #" + next + " drafted for " + c.getContractNumber(), null, Map.of("amount_change", change));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c));
    }

    @PostMapping("/amendments/{amendmentId}/approve")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView approveAmendment(@PathVariable Long amendmentId) {
        Contract c = lock(contracts.contractOfAmendment(amendmentId).orElseThrow(() -> ApiException.notFound("Amendment")));
        ContractAmendment a = c.getAmendments().stream().filter(x -> x.getId().equals(amendmentId)).findFirst().orElseThrow();
        if (!"draft".equals(a.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This amendment was already " + a.getStatus() + ".");
        if (!Contract.ACTIVE.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "The contract is no longer in force.");
        User me = current.user();
        if (me.getId().equals(a.getPreparedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You drafted this amendment, so someone else has to approve it.");
        checkVariation(c, a.getAmountChange(), a.getId());
        Map<String, Object> before = snapshot(c);
        c.setAmount(c.getAmount().add(a.getAmountChange()));
        if (a.getNewEndDate() != null) c.setEndDate(a.getNewEndDate());
        a.setStatus("approved");
        a.setApprovedBy(me.getId());
        a.setApprovedAt(Times.now());
        contracts.flush();
        audit.record("amendment_approved", TYPE, c.getId(), "Amendment #" + a.getAmendmentNumber() + " approved for " + c.getContractNumber(), before, snapshot(c));
        return view(c);
    }

    @DeleteMapping("/amendments/{amendmentId}")
    @PreAuthorize(MANAGE)
    @Transactional
    public ContractView destroyAmendment(@PathVariable Long amendmentId) {
        Contract c = lock(contracts.contractOfAmendment(amendmentId).orElseThrow(() -> ApiException.notFound("Amendment")));
        ContractAmendment a = c.getAmendments().stream().filter(x -> x.getId().equals(amendmentId)).findFirst().orElseThrow();
        if (!"draft".equals(a.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "An approved amendment is part of the contract.");
        c.getAmendments().remove(a);
        contracts.flush();
        return view(c);
    }

    /** Approved price changes, plus this one, stay within 10% of the original contract price either way. */
    private void checkVariation(Contract c, BigDecimal change, Long exceptAmendment) {
        BigDecimal original = c.originalAmount();
        BigDecimal approved = c.getAmendments().stream().filter(x -> "approved".equals(x.getStatus()) && !x.getId().equals(exceptAmendment))
                .map(ContractAmendment::getAmountChange).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cumulative = approved.add(change).abs();
        BigDecimal cap = original.multiply(TEN_PERCENT).setScale(2, RoundingMode.HALF_UP);
        if (cumulative.compareTo(cap) > 0) {
            throw ApiException.validation("amount_change", "Amendments would change the price by ₱" + ProcurementRules.peso(cumulative) + " in all, over the ₱"
                    + ProcurementRules.peso(cap) + " (10% of the original ₱" + ProcurementRules.peso(original) + ") allowed; a larger change needs a new procurement.");
        }
    }

    // ───────────────────────── delivery extensions ─────────────────────────

    public record ExtensionInput(Integer requestedDays, String reason, LocalDate requestedDate, Integer approvedDays, String remarks) {
    }

    /** Records a supplier's request for more time; it has to be made before the delivery deadline. */
    @PostMapping("/{id}/extensions")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<ContractView> storeExtension(@PathVariable Long id, @RequestBody ExtensionInput in) {
        Contract c = lock(id);
        if (!Contract.ACTIVE.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Extensions are requested on a contract in force.");
        if (in.requestedDays() == null || in.requestedDays() < 1) throw ApiException.validation("requested_days", "Enter the days requested.");
        String reason = blank(in.reason());
        if (reason == null) throw ApiException.validation("reason", "State the ground given by the supplier.");
        LocalDate asked = in.requestedDate() == null ? LocalDate.now(Times.ZONE) : in.requestedDate();
        if (asked.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("requested_date", "The request date cannot be in the future.");
        LocalDate deadline = deadline(c);
        if (deadline != null && asked.isAfter(deadline)) {
            throw ApiException.validation("requested_date", "The request came after the " + deadline + " delivery deadline; an extension has to be asked for before it lapses.");
        }
        if (c.getExtensions().stream().anyMatch(e -> "pending".equals(e.getStatus()))) throw ApiException.of(HttpStatus.CONFLICT, "Another request is still pending.");
        c.getExtensions().add(new DeliveryExtension(c, in.requestedDays(), reason, asked, blank(in.remarks())));
        contracts.flush();
        audit.record("delivery_extension_requested", TYPE, c.getId(), "Delivery extension of " + in.requestedDays() + " day(s) requested for " + c.getContractNumber(), null,
                Map.of("requested_days", in.requestedDays()));
        notifications.notifyPermission(List.of("edit_contracts"), current.user().getId(), "delivery_extension", "Delivery extension requested",
                c.getContractNumber() + ": " + in.requestedDays() + " day(s) requested — " + reason, TYPE, c.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c));
    }

    @PutMapping("/extensions/{extensionId}/approve")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView approveExtension(@PathVariable Long extensionId, @RequestBody ExtensionInput in) {
        Contract c = lock(contracts.contractOfExtension(extensionId).orElseThrow(() -> ApiException.notFound("Extension")));
        DeliveryExtension e = pending(c, extensionId);
        int days = in.approvedDays() == null ? e.getRequestedDays() : in.approvedDays();
        if (days < 1 || days > e.getRequestedDays()) throw ApiException.validation("approved_days", "Grant between 1 and the " + e.getRequestedDays() + " day(s) requested.");
        e.setStatus("approved");
        e.setApprovedDays(days);
        decide(c, e, in);
        audit.record("delivery_extension_approved", TYPE, c.getId(), "Delivery extension of " + days + " day(s) granted for " + c.getContractNumber(),
                Map.of("status", "pending"), Map.of("approved_days", days));
        return view(c);
    }

    @PutMapping("/extensions/{extensionId}/deny")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView denyExtension(@PathVariable Long extensionId, @RequestBody(required = false) ExtensionInput in) {
        Contract c = lock(contracts.contractOfExtension(extensionId).orElseThrow(() -> ApiException.notFound("Extension")));
        DeliveryExtension e = pending(c, extensionId);
        if (in == null || blank(in.remarks()) == null) throw ApiException.validation("remarks", "Say why the extension is denied.");
        e.setStatus("denied");
        decide(c, e, in);
        audit.record("delivery_extension_denied", TYPE, c.getId(), "Delivery extension denied for " + c.getContractNumber() + ": " + in.remarks(), Map.of("status", "pending"), Map.of("status", "denied"));
        return view(c);
    }

    @DeleteMapping("/extensions/{extensionId}")
    @PreAuthorize(MANAGE)
    @Transactional
    public ContractView destroyExtension(@PathVariable Long extensionId) {
        Contract c = lock(contracts.contractOfExtension(extensionId).orElseThrow(() -> ApiException.notFound("Extension")));
        pending(c, extensionId);
        c.getExtensions().removeIf(x -> x.getId().equals(extensionId));
        contracts.flush();
        return view(c);
    }

    private DeliveryExtension pending(Contract c, Long extensionId) {
        DeliveryExtension e = c.getExtensions().stream().filter(x -> x.getId().equals(extensionId)).findFirst().orElseThrow();
        if (!"pending".equals(e.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This request was already " + e.getStatus() + ".");
        return e;
    }

    private void decide(Contract c, DeliveryExtension e, ExtensionInput in) {
        e.setApprovedBy(current.user().getId());
        e.setApprovedAt(Times.now());
        if (in != null && blank(in.remarks()) != null) e.setRemarks(blank(in.remarks()));
        // A recorded assessment follows the new deadline.
        if (c.getLdAsOfDate() != null) {
            LdEstimate ld = ld(c, c.getLdAsOfDate());
            if (ld != null) record(c, ld, c.getLdRemarks());
        }
        contracts.flush();
    }

    // ───────────────────────── liquidated damages ─────────────────────────

    public record LdInput(LocalDate asOfDate, String remarks) {
    }

    /** Records the liquidated damages assessed as of a date (today if not given). */
    @PutMapping("/{id}/liquidated-damages")
    @PreAuthorize("@perm.any('edit_contracts')")
    @Transactional
    public ContractView assessLiquidatedDamages(@PathVariable Long id, @RequestBody LdInput in) {
        Contract c = lock(id);
        if (Contract.DRAFT.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "The contract is not in force yet.");
        LocalDate asOf = in == null || in.asOfDate() == null ? null : in.asOfDate();
        if (asOf != null && asOf.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("as_of_date", "The as-of date cannot be in the future.");
        LdEstimate ld = ld(c, asOf);
        if (ld == null) throw ApiException.of(HttpStatus.CONFLICT, "There is no delivery deadline to measure delay against (set the dates, delivery days or a PO).");
        record(c, ld, in == null ? null : blank(in.remarks()));
        contracts.flush();
        audit.record("liquidated_damages", TYPE, c.getId(), "Liquidated damages for " + c.getContractNumber() + ": " + ld.daysDelayed() + " day(s) late, ₱"
                + ProcurementRules.peso(ld.amount()), null, Map.of("ld_amount", ld.amount(), "days", ld.daysDelayed()));
        if (ld.mayTerminate()) {
            notifications.notifyPermission(List.of("edit_contracts"), current.user().getId(), "ld_cap", "Liquidated damages reached 10%",
                    c.getContractNumber() + ": liquidated damages have reached 10% of the contract price; the contract may be terminated.", TYPE, c.getId());
        }
        return view(c);
    }

    private void record(Contract c, LdEstimate ld, String remarks) {
        c.setLdAsOfDate(ld.asOf());
        c.setLdDaysDelayed(ld.daysDelayed());
        c.setLdBasisAmount(ld.basis());
        c.setLdAmount(ld.amount());
        c.setLdRemarks(remarks);
    }

    /**
     * The delivery deadline: the PO delivery date, else the Notice to Proceed (or start date) plus
     * the delivery days, else the end date — in each case plus the extensions granted.
     */
    private LocalDate deadline(Contract c) {
        LocalDate base = null;
        if (c.getPurchaseOrderId() != null) {
            base = jdbc.query("select delivery_date from purchase_orders where id = ?", rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null, c.getPurchaseOrderId());
        }
        if (base == null && c.getDeliveryLeadDays() != null) {
            LocalDate ntp = ntpDate(c);
            LocalDate reckon = ntp != null ? ntp : c.getStartDate();
            if (reckon != null) base = reckon.plusDays(c.getDeliveryLeadDays());
        }
        if (base == null) base = c.getEndDate();
        return base == null ? null : base.plusDays(c.approvedExtensionDays());
    }

    private LocalDate ntpDate(Contract c) {
        String sql = c.getRfqId() != null ? "select ntp_date from rfqs where id = ?" : c.getBiddingId() != null ? "select ntp_date from biddings where id = ?" : null;
        if (sql == null) return null;
        Long src = c.getRfqId() != null ? c.getRfqId() : c.getBiddingId();
        return jdbc.query(sql, rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null, src);
    }

    /** What the linked PO has had accepted into stock, by value. */
    public Delivery delivery(Contract c) {
        if (c.getPurchaseOrderId() == null) return null;
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO};
        LocalDate[] last = {null};
        jdbc.query("""
                select poi.quantity, poi.unit_cost,
                       (select coalesce(sum(ii.quantity_accepted), 0) from iar_items ii join inspection_acceptance_reports r on r.id = ii.inspection_acceptance_report_id
                        where ii.purchase_order_item_id = poi.id and r.acceptance_status in ('accepted', 'partially_accepted')),
                       (select max(r.delivery_date) from iar_items ii join inspection_acceptance_reports r on r.id = ii.inspection_acceptance_report_id
                        where ii.purchase_order_item_id = poi.id and r.acceptance_status in ('accepted', 'partially_accepted') and ii.quantity_accepted > 0)
                from purchase_order_items poi where poi.purchase_order_id = ?""", rs -> {
            BigDecimal unit = rs.getBigDecimal(2);
            int ordered = rs.getInt(1), accepted = Math.min(rs.getInt(3), rs.getInt(1));
            totals[0] = totals[0].add(unit.multiply(BigDecimal.valueOf(ordered)));
            totals[1] = totals[1].add(unit.multiply(BigDecimal.valueOf(accepted)));
            if (rs.getDate(4) != null) {
                LocalDate d = rs.getDate(4).toLocalDate();
                if (last[0] == null || d.isAfter(last[0])) last[0] = d;
            }
        }, c.getPurchaseOrderId());
        BigDecimal undelivered = totals[0].subtract(totals[1]);
        return new Delivery(totals[0], totals[1], undelivered, undelivered.signum() == 0 && totals[0].signum() > 0, last[0]);
    }

    /** Value accepted on deliveries made on or before the date. */
    private BigDecimal deliveredBy(Contract c, LocalDate date) {
        if (c.getPurchaseOrderId() == null) return BigDecimal.ZERO;
        BigDecimal v = jdbc.queryForObject("""
                select coalesce(sum(ii.quantity_accepted * poi.unit_cost), 0) from iar_items ii
                join inspection_acceptance_reports r on r.id = ii.inspection_acceptance_report_id
                join purchase_order_items poi on poi.id = ii.purchase_order_item_id
                where r.purchase_order_id = ? and r.acceptance_status in ('accepted', 'partially_accepted') and r.delivery_date <= ?""",
                BigDecimal.class, c.getPurchaseOrderId(), java.sql.Date.valueOf(date));
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * Liquidated damages: 1/10 of 1% of the value not delivered by the deadline, per day from the
     * deadline to the last delivery (or the as-of date), capped at 10% of the contract price.
     */
    /** Exposed for the overdue-delivery watch; null when the contract has no deadline. */
    public LdEstimate ld(Contract c, LocalDate asOfInput) {
        LocalDate deadline = deadline(c);
        if (deadline == null) return null;
        Delivery d = delivery(c);
        LocalDate asOf = asOfInput != null ? asOfInput : d != null && d.complete() && d.lastDelivery() != null ? d.lastDelivery() : LocalDate.now(Times.ZONE);
        int days = asOf.isAfter(deadline) ? (int) ChronoUnit.DAYS.between(deadline, asOf) : 0;
        BigDecimal basis = d == null ? c.getAmount() : d.orderValue().subtract(deliveredBy(c, deadline)).max(BigDecimal.ZERO);
        BigDecimal raw = basis.multiply(LD_RATE).multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cap = c.getAmount().multiply(TEN_PERCENT).setScale(2, RoundingMode.HALF_UP);
        boolean capped = raw.compareTo(cap) >= 0 && days > 0;
        return new LdEstimate(deadline, asOf, days, basis.setScale(2, RoundingMode.HALF_UP), LD_RATE, raw.min(cap), capped, capped);
    }

    // ───────────────────────── framework call-offs ─────────────────────────

    public record CalloffInput(String title, BigDecimal amount, LocalDate calloffDate, String status, String remarks) {
    }

    @PostMapping("/{id}/calloffs")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<ContractView> storeCalloff(@PathVariable Long id, @RequestBody CalloffInput in) {
        Contract c = lock(id);
        if (!"Framework Agreement".equals(c.getContractType())) throw ApiException.validation("contract_type", "Call-offs apply to framework agreements only.");
        if (!Contract.ACTIVE.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Call-offs are made under an agreement in force.");
        String title = blank(in.title());
        if (title == null) throw ApiException.validation("title", "Describe the call-off.");
        if (in.amount() == null || in.amount().signum() <= 0 || in.amount().scale() > 2) throw ApiException.validation("amount", "Enter the amount in pesos.");
        LocalDate date = in.calloffDate() == null ? LocalDate.now(Times.ZONE) : in.calloffDate();
        if (c.getEndDate() != null && date.isAfter(c.getEndDate())) throw ApiException.validation("calloff_date", "The agreement ends on " + c.getEndDate() + ".");
        BigDecimal used = calloffsTotal(c);
        if (used.add(in.amount()).compareTo(c.getAmount()) > 0) {
            throw ApiException.validation("amount", "Only ₱" + ProcurementRules.peso(c.getAmount().subtract(used)) + " of the ₱" + ProcurementRules.peso(c.getAmount())
                    + " agreement is left for call-offs.");
        }
        FrameworkCalloff f = new FrameworkCalloff(c, numbers.yearly("CO", "framework_calloffs.calloff_number"), current.user().getId());
        f.setTitle(title);
        f.setSupplierId(c.getSupplierId());
        f.setAmount(in.amount());
        f.setCalloffDate(date);
        f.setRemarks(blank(in.remarks()));
        c.getCalloffs().add(f);
        contracts.flush();
        audit.record("calloff_created", TYPE, c.getId(), "Call-off " + f.getCalloffNumber() + " (₱" + ProcurementRules.peso(in.amount()) + ") under " + c.getContractNumber(), null,
                Map.of("amount", in.amount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c));
    }

    /** draft → issued → completed, or cancelled. */
    @PutMapping("/calloffs/{calloffId}")
    @PreAuthorize(MANAGE)
    @Transactional
    public ContractView updateCalloff(@PathVariable Long calloffId, @RequestBody CalloffInput in) {
        Contract c = lock(contracts.contractOfCalloff(calloffId).orElseThrow(() -> ApiException.notFound("Call-off")));
        FrameworkCalloff f = c.getCalloffs().stream().filter(x -> x.getId().equals(calloffId)).findFirst().orElseThrow();
        String to = in.status();
        boolean ok = to != null && switch (f.getStatus()) {
            case "draft" -> List.of("issued", "cancelled").contains(to);
            case "issued" -> List.of("completed", "cancelled").contains(to);
            default -> false;
        };
        if (!ok) throw ApiException.validation("status", "A " + f.getStatus() + " call-off cannot become " + to + ".");
        String was = f.getStatus();
        f.setStatus(to);
        if (blank(in.remarks()) != null) f.setRemarks(blank(in.remarks()));
        contracts.flush();
        audit.record("calloff_" + to, TYPE, c.getId(), "Call-off " + f.getCalloffNumber() + ": " + was + " → " + to, Map.of("status", was), Map.of("status", to));
        return view(c);
    }

    private static BigDecimal calloffsTotal(Contract c) {
        return c.getCalloffs().stream().filter(x -> !"cancelled".equals(x.getStatus())).map(FrameworkCalloff::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ───────────────────────── helpers ─────────────────────────

    private void apply(Contract c, ContractInput in) {
        if (in.title() != null) {
            String t = blank(in.title());
            if (t == null || t.length() > 255) throw ApiException.validation("title", "Give the contract a title of up to 255 characters.");
            c.setTitle(t);
        }
        if (in.contractType() != null) {
            if (!Contract.TYPES.contains(in.contractType())) throw ApiException.validation("contract_type", "Unknown contract type.");
            c.setContractType(in.contractType());
        }
        if (in.startDate() != null) c.setStartDate(in.startDate());
        if (in.endDate() != null) c.setEndDate(in.endDate());
        if (c.getStartDate() != null && c.getEndDate() != null && c.getEndDate().isBefore(c.getStartDate())) throw ApiException.validation("end_date", "The contract cannot end before it starts.");
        if (in.signedDate() != null) {
            if (in.signedDate().isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("signed_date", "The signing date cannot be in the future.");
            c.setSignedDate(in.signedDate());
        }
        if (in.deliveryLeadDays() != null) {
            if (in.deliveryLeadDays() < 0) throw ApiException.validation("delivery_lead_days", "Delivery days cannot be negative.");
            c.setDeliveryLeadDays(in.deliveryLeadDays());
        }
        if (in.description() != null) c.setDescription(blank(in.description()));
        if (in.terms() != null) c.setTerms(blank(in.terms()));
        if (in.remarks() != null) c.setRemarks(blank(in.remarks()));
    }

    private static boolean isPerformanceType(String type) {
        return "Performance Security".equals(type);
    }

    private static boolean isPerformance(ContractSecurity s) {
        return isPerformanceType(s.getSecurityType());
    }

    private static BigDecimal required(Contract c, String form) {
        BigDecimal[] rates = PERFORMANCE_FORMS.get(form);
        if (rates == null) return null;
        BigDecimal rate = "Infrastructure".equals(c.getContractType()) ? rates[1] : rates[0];
        return c.originalAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean sufficient(Contract c, ContractSecurity s) {
        BigDecimal need = required(c, s.getForm());
        return need != null && s.getAmount().compareTo(need) >= 0;
    }

    private Contract lock(Long id) {
        return contracts.lockById(id).orElseThrow(() -> ApiException.notFound("Contract"));
    }

    private ContractView view(Contract c) {
        return views(List.of(c), true).get(c);
    }

    private Map<Contract, ContractView> views(Collection<Contract> list, boolean full) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().flatMap(c -> Stream.concat(Stream.of(c.getPreparedBy()), full ? Stream.concat(
                        c.getAmendments().stream().flatMap(a -> Stream.of(a.getPreparedBy(), a.getApprovedBy())),
                        c.getExtensions().stream().map(DeliveryExtension::getApprovedBy)) : Stream.empty()))
                .filter(Objects::nonNull).collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        User me = current.user();
        boolean manager = current.any("create_contracts", "edit_contracts");
        boolean editor = current.any("edit_contracts");
        LocalDate today = LocalDate.now(Times.ZONE);
        Map<Contract, ContractView> out = new LinkedHashMap<>();
        for (Contract c : list) {
            String supplier = awards.supplierName(c.getSupplierId());
            String[] pr = c.getPurchaseRequestId() == null ? null : jdbc.query("select pr_number, caf_number from purchase_requests where id = ?",
                    rs -> rs.next() ? new String[]{rs.getString(1), rs.getString(2)} : null, c.getPurchaseRequestId());
            Ref source = c.getRfqId() != null ? numbered("select rfq_number from rfqs where id = ?", c.getRfqId())
                    : c.getBiddingId() != null ? numbered("select bidding_number from biddings where id = ?", c.getBiddingId()) : null;
            Ref po = c.getPurchaseOrderId() == null ? null : numbered("select po_number from purchase_orders where id = ?", c.getPurchaseOrderId());
            Integer toExpiry = c.getEndDate() == null ? null : (int) ChronoUnit.DAYS.between(today, c.getEndDate());
            boolean active = Contract.ACTIVE.equals(c.getStatus());
            BigDecimal original = c.originalAmount();
            BigDecimal variation = original.signum() == 0 ? BigDecimal.ZERO
                    : c.getAmount().subtract(original).multiply(BigDecimal.valueOf(100)).divide(original, 2, RoundingMode.HALF_UP);
            boolean secured = c.getSecurities().stream().anyMatch(s -> isPerformance(s) && "posted".equals(s.getStatus()) && sufficient(c, s));
            boolean own = me.getId().equals(c.getPreparedBy()) && !me.isSuperAdmin();
            String fund = !full || c.getPurchaseRequestId() == null ? null : String.join("; ", jdbc.queryForList(
                    "select distinct fund_source from purchase_request_items where purchase_request_id = ? and fund_source is not null order by 1", String.class, c.getPurchaseRequestId()));
            out.put(c, new ContractView(c.getId(), c.getContractNumber(), c.getTitle(), c.getContractType(), c.getStatus(), new Ref(c.getSupplierId(), supplier),
                    pr == null ? null : new Ref(c.getPurchaseRequestId(), pr[0]), source, c.getRfqId() != null ? "rfq" : c.getBiddingId() != null ? "bidding" : null, po,
                    c.getAmount(), original, variation, c.getStartDate(), c.getEndDate(), c.getSignedDate(), c.getDeliveryLeadDays(), toExpiry,
                    active && toExpiry != null && toExpiry >= 0 && toExpiry <= 30, active && toExpiry != null && toExpiry < 0,
                    c.getDescription(), c.getTerms(), c.getRemarks(), c.getPreparedBy() == null ? null : new Ref(c.getPreparedBy(), names.get(c.getPreparedBy())),
                    full ? deadline(c) : null, c.approvedExtensionDays(), full ? delivery(c) : null, full && !Contract.DRAFT.equals(c.getStatus()) ? ld(c, null) : null,
                    c.getLdAmount(), c.getLdAsOfDate() == null ? null : c.getLdDaysDelayed(), c.getLdAsOfDate(), c.getLdRemarks(), fund, pr == null ? null : pr[1],
                    calloffsTotal(c),
                    !full ? null : c.getAmendments().stream().map(a -> new AmendmentView(a.getId(), a.getAmendmentNumber(), a.getTitle(), a.getDescription(),
                            a.getAmountChange(), a.getNewEndDate(), a.getStatus(), a.getPreparedBy() == null ? null : new Ref(a.getPreparedBy(), names.get(a.getPreparedBy())),
                            a.getApprovedBy() == null ? null : new Ref(a.getApprovedBy(), names.get(a.getApprovedBy())), Times.toOffset(a.getApprovedAt()), a.getRemarks(),
                            editor && active && "draft".equals(a.getStatus()) && (me.isSuperAdmin() || !me.getId().equals(a.getPreparedBy())))).toList(),
                    !full ? null : c.getSecurities().stream().map(s -> new SecurityView(s.getId(), s.getSecurityType(), s.getForm(), s.getReferenceNumber(), s.getAmount(),
                            s.getPercentage(), s.getPostedDate(), s.getValidityDate(), s.getStatus(), s.getRemarks(), isPerformance(s) ? required(c, s.getForm()) : null,
                            !isPerformance(s) || sufficient(c, s))).toList(),
                    !full ? null : c.getExtensions().stream().map(e -> new ExtensionView(e.getId(), e.getRequestedDays(), e.getApprovedDays(), e.getReason(), e.getStatus(),
                            e.getRequestedDate(), e.getApprovedBy() == null ? null : new Ref(e.getApprovedBy(), names.get(e.getApprovedBy())),
                            Times.toOffset(e.getApprovedAt()), e.getRemarks())).toList(),
                    !full ? null : c.getCalloffs().stream().map(f -> new CalloffView(f.getId(), f.getCalloffNumber(), f.getTitle(), new Ref(f.getSupplierId(), supplier),
                            f.getAmount(), f.getCalloffDate(), f.getStatus(), f.getRemarks())).toList(),
                    secured, manager && c.isOpen(), editor && !own && Contract.DRAFT.equals(c.getStatus()), editor && active, editor && c.isOpen(),
                    current.any("create_contracts") && Contract.DRAFT.equals(c.getStatus()), Times.toOffset(c.getCreatedAt())));
        }
        return out;
    }

    private Ref numbered(String sql, Long id) {
        return jdbc.query(sql, rs -> rs.next() ? new Ref(id, rs.getString(1)) : null, id);
    }

    private static Map<String, Object> snapshot(Contract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", c.getStatus());
        m.put("amount", c.getAmount());
        m.put("start_date", c.getStartDate() == null ? null : c.getStartDate().toString());
        m.put("end_date", c.getEndDate() == null ? null : c.getEndDate().toString());
        m.put("contract_type", c.getContractType());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

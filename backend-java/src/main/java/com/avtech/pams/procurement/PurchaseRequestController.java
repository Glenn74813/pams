package com.avtech.pams.procurement;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.department.DepartmentRepository;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Purchase requests: drafted by the end user, routed Department Head → Budget Officer (with the
 * Certificate of Availability of Funds) → Accounting → BAC Secretariat. Any signatory may return
 * it for revision. Lines drawn on the APP may not exceed the APP line's quantity or budget.
 */
@RestController
@RequestMapping("/api/purchase-requests")
public class PurchaseRequestController {

    private static final String TYPE = "App\\Models\\PurchaseRequest";
    private static final List<String> STEP_PERMISSIONS = PurchaseRequest.CHAIN.stream().map(s -> s[2]).toList();
    private static final String[] SEE_ALL = {"view_procurement", "approve_procurement", "pr_approve_department", "pr_approve_budget",
            "pr_approve_accounting", "pr_approve_bac"};
    private static final Set<String> STATUSES = Set.of("draft", "submitted", "under_review", "approved", "returned", "cancelled");
    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high");

    private final PurchaseRequestRepository requests;
    private final AppPlanRepository apps;
    private final AppItemRepository appItems;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final ProcurementRules rules;
    private final AppUsage usage;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;

    public PurchaseRequestController(PurchaseRequestRepository requests, AppPlanRepository apps, AppItemRepository appItems,
                                     DepartmentRepository departments, UserRepository users, ProcurementRules rules, AppUsage usage,
                                     DocumentNumbers numbers, NotificationService notifications, AuditService audit, CurrentUser current) {
        this.requests = requests;
        this.apps = apps;
        this.appItems = appItems;
        this.departments = departments;
        this.users = users;
        this.rules = rules;
        this.usage = usage;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
    }

    // ───────────────────────── views ─────────────────────────

    public record Ref(Long id, String name) {
    }

    public record AppRef(Long id, String appNumber, Integer fiscalYear) {
    }

    public record LineView(Long id, Long appItemId, String generalDescription, String unit, int quantity, BigDecimal estimatedUnitCost,
                           BigDecimal estimatedTotal, String specifications, String procurementMode, String procurementCategory,
                           String fundSource, boolean isSustainable, String remarks) {
    }

    public record StepView(int step, String label, String requiredPermission, String status, Ref actor, OffsetDateTime actedAt, String remarks) {
    }

    public record PrView(Long id, String prNumber, Ref department, AppRef app, Integer fiscalYear, Ref requester, LocalDate requestDate,
                         String purpose, String status, String stageLabel, Integer currentStep, String priority, BigDecimal totalAmount,
                         String cafNumber, Ref cafCertifier, OffsetDateTime cafCertifiedAt, BigDecimal cafAmount, String cafRemarks,
                         String remarks, String returnReason, OffsetDateTime cancelledAt, int itemsCount, List<LineView> items,
                         List<StepView> approvals, List<String> procurementModes, List<String> procurementCategories, List<String> fundSources,
                         boolean fundsCovered, boolean canEdit, boolean canSubmit, boolean canCancel, boolean canDelete, boolean canAct,
                         boolean canCertify, OffsetDateTime createdAt) {
    }

    // ───────────────────────── endpoints ─────────────────────────

    @GetMapping
    @PreAuthorize("@perm.any('view_procurement', 'create_procurement', 'approve_procurement', 'pr_approve_department', 'pr_approve_budget', 'pr_approve_accounting', 'pr_approve_bac')")
    @Transactional(readOnly = true)
    public PageResponse<PrView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String priority,
                                      @RequestParam(name = "department_id", required = false) Long department,
                                      @RequestParam(name = "fiscal_year", required = false) Integer year,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(name = "awaiting_me", defaultValue = "false") boolean awaiting,
                                      @RequestParam(defaultValue = "false") boolean mine) {
        User me = current.user();
        Long only = mine || !current.any(SEE_ALL) ? me.getId() : null;
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw ApiException.validation("status", "Unknown status.");
        List<String> steps = me.isSuperAdmin() ? STEP_PERMISSIONS : STEP_PERMISSIONS.stream().filter(me::hasPermission).toList();
        Page<PurchaseRequest> result = requests.search(only, blank(status), blank(priority), department, year, blank(search), awaiting,
                steps.isEmpty() ? List.of("__none__") : steps, PageRequest.of(Math.max(0, page - 1), 20));
        Map<PurchaseRequest, PrView> v = views(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.any('view_procurement', 'create_procurement', 'approve_procurement', 'pr_approve_department', 'pr_approve_budget', 'pr_approve_accounting', 'pr_approve_bac')")
    @Transactional(readOnly = true)
    public PrView show(@PathVariable Long id) {
        PurchaseRequest pr = requests.findLive(id).orElseThrow(() -> ApiException.notFound("Purchase request"));
        if (!current.any(SEE_ALL) && !current.user().getId().equals(pr.getRequestedBy())) {
            throw ApiException.forbidden("You can only view your own purchase requests.");
        }
        return views(List.of(pr), true).get(pr);
    }

    public record PrInput(Long departmentId, Long appId, Integer fiscalYear, String purpose, String priority, String remarks,
                          List<ProcurementRules.LineInput> items) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_procurement', 'edit_procurement')")
    @Transactional
    public ResponseEntity<PrView> store(@RequestBody PrInput in) {
        User me = current.user();
        // Procurement staff may file for any department; everyone else files for their own.
        Long dept = current.any("edit_procurement", "view_procurement") && in.departmentId() != null ? in.departmentId() : me.getDepartment().getId();
        if (!departments.existsById(dept)) throw ApiException.validation("department_id", "That department does not exist.");
        LocalDate today = LocalDate.now(Times.ZONE);
        PurchaseRequest pr = new PurchaseRequest(numbers.series(String.format("PR-%d%02d-", today.getYear(), today.getMonthValue()), 5,
                "purchase_requests.pr_number"), dept, me.getId(), today);
        apply(pr, in, true);
        requests.saveAndFlush(pr);
        audit.record("created", TYPE, pr.getId(), "Created purchase request " + pr.getPrNumber(), null, snapshot(pr));
        return ResponseEntity.status(HttpStatus.CREATED).body(views(List.of(pr), true).get(pr));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('create_procurement', 'edit_procurement')")
    @Transactional
    public PrView update(@PathVariable Long id, @RequestBody PrInput in) {
        PurchaseRequest pr = lock(id);
        ownerOr(pr);
        if (!pr.isEditable()) throw ApiException.of(HttpStatus.CONFLICT, "Only draft or returned purchase requests can be edited.");
        Map<String, Object> before = snapshot(pr);
        apply(pr, in, false);
        requests.flush();
        audit.record("updated", TYPE, pr.getId(), "Edited purchase request " + pr.getPrNumber(), before, snapshot(pr));
        return views(List.of(pr), true).get(pr);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@perm.any('create_procurement', 'edit_procurement')")
    @Transactional
    public PrView submit(@PathVariable Long id) {
        PurchaseRequest pr = lock(id);
        ownerOr(pr);
        if (!pr.isEditable()) throw ApiException.of(HttpStatus.CONFLICT, "Only draft or returned purchase requests can be submitted.");
        if (pr.getItems().isEmpty()) throw ApiException.validation("items", "Add at least one line before submitting.");
        String was = pr.getStatus();
        // A fresh routing every time: earlier rounds stay in the audit trail.
        pr.getApprovals().clear();
        requests.flush();
        for (int i = 0; i < PurchaseRequest.CHAIN.size(); i++) pr.getApprovals().add(new PurchaseRequestApproval(pr, i + 1, PurchaseRequest.CHAIN.get(i)));
        pr.setStatus(PurchaseRequest.SUBMITTED);
        pr.setCurrentStep(1);
        pr.setReturnReason(null);
        requests.flush();
        audit.record("submitted", TYPE, pr.getId(), "PR " + pr.getPrNumber() + " submitted for approval routing", Map.of("status", was), Map.of("status", pr.getStatus()));
        notifyStep(pr);
        return views(List.of(pr), true).get(pr);
    }

    public record StepInput(String remarks) {
    }

    @PostMapping("/{id}/approve-step")
    @PreAuthorize("@perm.any('approve_procurement', 'pr_approve_department', 'pr_approve_budget', 'pr_approve_accounting', 'pr_approve_bac')")
    @Transactional
    public PrView approveStep(@PathVariable Long id, @RequestBody(required = false) StepInput in) {
        PurchaseRequest pr = lock(id);
        PurchaseRequestApproval step = actionableStep(pr);
        if ("pr_approve_budget".equals(step.getRequiredPermission()) && !fundsCovered(pr)) {
            throw ApiException.of(HttpStatus.CONFLICT, "Certify the availability of funds (CAF) for ₱" + ProcurementRules.peso(pr.getTotalAmount())
                    + " before signing the Budget Officer step.");
        }
        String remarks = in == null ? null : ProcurementRules.text(in.remarks());
        if (remarks != null && remarks.length() > 1000) throw ApiException.validation("remarks", "Keep remarks under 1000 characters.");
        step.act("approved", current.user().getId(), Times.now(), remarks);
        PurchaseRequestApproval next = pr.getApprovals().stream().filter(a -> a.getStep() > step.getStep()).findFirst().orElse(null);
        if (next != null) {
            pr.setStatus(PurchaseRequest.UNDER_REVIEW);
            pr.setCurrentStep(next.getStep());
        } else {
            pr.setStatus(PurchaseRequest.APPROVED);
            pr.setCurrentStep(null);
        }
        requests.flush();
        audit.record("approved", TYPE, pr.getId(), "PR " + pr.getPrNumber() + " — '" + step.getLabel() + "' step approved", null, Map.of("status", pr.getStatus()));
        if (next != null) {
            notifyStep(pr);
        } else {
            notifications.notifyUser(pr.getRequestedBy(), "pr_status", "Purchase Request approved",
                    "Your purchase request " + pr.getPrNumber() + " has been fully approved.", TYPE, pr.getId());
        }
        return views(List.of(pr), true).get(pr);
    }

    public record ReturnInput(String returnReason) {
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("@perm.any('approve_procurement', 'pr_approve_department', 'pr_approve_budget', 'pr_approve_accounting', 'pr_approve_bac')")
    @Transactional
    public PrView returnStep(@PathVariable Long id, @RequestBody ReturnInput in) {
        String reason = in == null ? null : ProcurementRules.text(in.returnReason());
        if (reason == null) throw ApiException.validation("return_reason", "Say what needs revising.");
        if (reason.length() > 1000) throw ApiException.validation("return_reason", "Keep the reason under 1000 characters.");
        PurchaseRequest pr = lock(id);
        PurchaseRequestApproval step = actionableStep(pr);
        step.act("returned", current.user().getId(), Times.now(), reason);
        pr.setStatus(PurchaseRequest.RETURNED);
        pr.setCurrentStep(null);
        pr.setReturnReason(reason);
        requests.flush();
        audit.record("returned", TYPE, pr.getId(), "PR " + pr.getPrNumber() + " returned at '" + step.getLabel() + "': " + reason, null, Map.of("status", pr.getStatus()));
        notifications.notifyUser(pr.getRequestedBy(), "pr_status", "Purchase Request returned",
                "Your purchase request " + pr.getPrNumber() + " was returned at '" + step.getLabel() + "': " + reason, TYPE, pr.getId());
        return views(List.of(pr), true).get(pr);
    }

    public record CafInput(BigDecimal cafAmount, String cafRemarks) {
    }

    /** Certificate of Availability of Funds. A new CAF can be issued when the PR total has grown past the last one. */
    @PostMapping("/{id}/certify-funds")
    @PreAuthorize("@perm.any('pr_approve_budget', 'pr_approve_accounting')")
    @Transactional
    public PrView certifyFunds(@PathVariable Long id, @RequestBody(required = false) CafInput in) {
        PurchaseRequest pr = lock(id);
        if (!pr.isRouting()) throw ApiException.of(HttpStatus.CONFLICT, "Funds are certified while the purchase request is in routing.");
        notRequester(pr, "certify funds for");
        if (fundsCovered(pr)) throw ApiException.of(HttpStatus.CONFLICT, "Funds are already certified for this purchase request (" + pr.getCafNumber() + ").");
        BigDecimal amount = in == null || in.cafAmount() == null ? pr.getTotalAmount() : in.cafAmount();
        if (amount.scale() > 2) throw ApiException.validation("caf_amount", "Enter the amount in pesos and centavos.");
        if (amount.compareTo(pr.getTotalAmount()) < 0) {
            throw ApiException.validation("caf_amount", "The certified amount must cover the PR total of ₱" + ProcurementRules.peso(pr.getTotalAmount()) + ".");
        }
        String remarks = in == null ? null : ProcurementRules.text(in.cafRemarks());
        if (remarks != null && remarks.length() > 1000) throw ApiException.validation("caf_remarks", "Keep remarks under 1000 characters.");
        String previous = pr.getCafNumber();
        pr.setCafNumber(numbers.yearly("CAF", "purchase_requests.caf_number"));
        pr.setCafCertifiedBy(current.user().getId());
        pr.setCafCertifiedAt(Times.now());
        pr.setCafAmount(amount.setScale(2, RoundingMode.HALF_UP));
        pr.setCafRemarks(remarks);
        requests.flush();
        audit.record("funds_certified", TYPE, pr.getId(), "CAF " + pr.getCafNumber() + " certified for PR " + pr.getPrNumber()
                + (previous == null ? "" : " (replaces " + previous + ")"), previous == null ? null : Map.of("caf_number", previous),
                Map.of("caf_number", pr.getCafNumber(), "caf_amount", pr.getCafAmount()));
        return views(List.of(pr), true).get(pr);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@perm.any('create_procurement', 'edit_procurement')")
    @Transactional
    public PrView cancel(@PathVariable Long id) {
        PurchaseRequest pr = lock(id);
        ownerOr(pr);
        if (!pr.isEditable() && !pr.isRouting()) throw ApiException.of(HttpStatus.CONFLICT, "This purchase request can no longer be cancelled.");
        String was = pr.getStatus();
        pr.setStatus(PurchaseRequest.CANCELLED);
        pr.setCurrentStep(null);
        pr.setCancelledAt(Times.now());
        requests.flush();
        audit.record("cancelled", TYPE, pr.getId(), "PR " + pr.getPrNumber() + " cancelled", Map.of("status", was), Map.of("status", pr.getStatus()));
        return views(List.of(pr), true).get(pr);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('create_procurement', 'edit_procurement')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        PurchaseRequest pr = lock(id);
        ownerOr(pr);
        if (!pr.isEditable() && !PurchaseRequest.CANCELLED.equals(pr.getStatus())) {
            throw ApiException.of(HttpStatus.CONFLICT, "Only draft, returned or cancelled purchase requests can be deleted.");
        }
        pr.setDeletedAt(Times.now());
        audit.record("deleted", TYPE, pr.getId(), "Deleted purchase request " + pr.getPrNumber(), snapshot(pr), null);
        return Map.of("message", "Purchase request " + pr.getPrNumber() + " deleted.");
    }

    // ───────────────────────── helpers ─────────────────────────

    private void apply(PurchaseRequest pr, PrInput in, boolean creating) {
        String purpose = ProcurementRules.text(in.purpose());
        if (purpose == null) throw ApiException.validation("purpose", "State the purpose of the request.");
        if (purpose.length() > 500) throw ApiException.validation("purpose", "Keep the purpose under 500 characters.");
        String priority = in.priority() == null || in.priority().isBlank() ? "medium" : in.priority();
        if (!PRIORITIES.contains(priority)) throw ApiException.validation("priority", "Priority is low, medium or high.");
        AppPlan app = null;
        if (in.appId() != null) {
            app = apps.findLive(in.appId()).orElseThrow(() -> ApiException.validation("app_id", "That APP does not exist."));
            if (!app.isApproved()) throw ApiException.validation("app_id", app.getAppNumber() + " is not approved yet; purchase requests draw on approved APPs only.");
        }
        Integer year = app != null ? app.getFiscalYear() : in.fiscalYear() != null ? in.fiscalYear() : creating ? LocalDate.now(Times.ZONE).getYear() : pr.getFiscalYear();
        if (year == null || year < 2000 || year > 2100) throw ApiException.validation("fiscal_year", "Enter the fiscal year.");
        if (app != null && in.fiscalYear() != null && !in.fiscalYear().equals(app.getFiscalYear())) {
            throw ApiException.validation("fiscal_year", app.getAppNumber() + " is for FY " + app.getFiscalYear() + ".");
        }
        String remarks = ProcurementRules.text(in.remarks());

        List<ProcurementRules.Line> lines = rules.lines(in.items(), false);
        Set<Long> ids = lines.stream().map(ProcurementRules.Line::appItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AppItem> planned = ids.isEmpty() ? Map.of()
                : appItems.lockAll(ids).stream().collect(Collectors.toMap(AppItem::getId, Function.identity()));
        Map<Long, AppUsage.Use> used = usage.forItems(ids, pr.getId());
        Map<Long, Integer> qty = new HashMap<>();
        Map<Long, BigDecimal> amt = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            ProcurementRules.Line l = lines.get(i);
            String f = "items." + i + ".";
            AppItem from = null;
            if (l.appItemId() != null) {
                from = planned.get(l.appItemId());
                if (from == null) throw ApiException.validation(f + "app_item_id", "Line " + (i + 1) + ": that APP line does not exist.");
                if (app == null || !from.getApp().getId().equals(app.getId())) {
                    throw ApiException.validation(f + "app_item_id", "Line " + (i + 1) + " is from another APP; choose lines from the APP this request is linked to.");
                }
                qty.merge(from.getId(), l.quantity(), Integer::sum);
                amt.merge(from.getId(), l.total(), BigDecimal::add);
            }
            String mode = l.mode() != null ? l.mode() : from == null ? null : from.getProcurementMode();
            rules.checkCeiling(f + "procurement_mode", l.description(), mode, l.total());
        }
        for (Map.Entry<Long, Integer> e : qty.entrySet()) {
            AppItem it = planned.get(e.getKey());
            AppUsage.Use u = usage.of(used, it.getId());
            int left = it.getQuantity() - u.quantity();
            if (e.getValue() > left) {
                throw ApiException.validation("items", "Quantity for \"" + it.getGeneralDescription() + "\" (" + e.getValue() + ") exceeds the remaining APP balance ("
                        + Math.max(0, left) + " of " + it.getQuantity() + " planned).");
            }
            BigDecimal budgetLeft = it.getEstimatedBudget().subtract(u.requested());
            if (amt.get(e.getKey()).compareTo(budgetLeft) > 0) {
                throw ApiException.validation("items", "The estimated cost for \"" + it.getGeneralDescription() + "\" (₱" + ProcurementRules.peso(amt.get(e.getKey()))
                        + ") exceeds what is left of its APP budget (₱" + ProcurementRules.peso(budgetLeft.max(BigDecimal.ZERO)) + " of ₱"
                        + ProcurementRules.peso(it.getEstimatedBudget()) + ").");
            }
        }
        pr.setPurpose(purpose);
        pr.setPriority(priority);
        pr.setAppId(app == null ? null : app.getId());
        pr.setFiscalYear(year);
        pr.setRemarks(remarks);
        pr.getItems().clear();
        for (ProcurementRules.Line l : lines) pr.getItems().add(new PurchaseRequestItem(pr, l, l.appItemId() == null ? null : planned.get(l.appItemId())));
        pr.recalc();
    }

    /** The step the caller may sign now, or an error saying why not. */
    private PurchaseRequestApproval actionableStep(PurchaseRequest pr) {
        PurchaseRequestApproval step = pr.currentApproval();
        if (step == null) throw ApiException.of(HttpStatus.CONFLICT, "This purchase request is not awaiting approval.");
        User me = current.user();
        if (!maySign(me, pr, step)) {
            throw ApiException.forbidden(me.hasPermission(ALTERNATE)
                    ? "You already signed another step of this purchase request; the '" + step.getLabel() + "' step needs a different signatory."
                    : "You are not authorized to act on the '" + step.getLabel() + "' step.");
        }
        notRequester(pr, "sign");
        return step;
    }

    /** "Approve Purchase Requests": an alternate signatory for any step (e.g. an officer-in-charge), never for two steps of one PR. */
    private static final String ALTERNATE = "approve_procurement";

    private static boolean maySign(User me, PurchaseRequest pr, PurchaseRequestApproval step) {
        if (me.isSuperAdmin() || me.hasPermission(step.getRequiredPermission())) return true;
        return me.hasPermission(ALTERNATE) && pr.getApprovals().stream()
                .noneMatch(a -> a != step && "approved".equals(a.getStatus()) && me.getId().equals(a.getActedBy()));
    }

    private void notRequester(PurchaseRequest pr, String what) {
        User me = current.user();
        if (me.getId().equals(pr.getRequestedBy()) && !me.isSuperAdmin()) {
            throw ApiException.forbidden("You raised this purchase request, so someone else has to " + what + " it.");
        }
    }

    private static boolean fundsCovered(PurchaseRequest pr) {
        return pr.getCafNumber() != null && pr.getCafAmount() != null && pr.getCafAmount().compareTo(pr.getTotalAmount()) >= 0;
    }

    private void notifyStep(PurchaseRequest pr) {
        PurchaseRequestApproval step = pr.currentApproval();
        if (step == null) return;
        notifications.notifyPermission(List.of(step.getRequiredPermission()), pr.getRequestedBy(), "pr_approval", "Purchase Request awaiting approval",
                "PR " + pr.getPrNumber() + " is awaiting your approval (" + step.getLabel() + ").", TYPE, pr.getId());
    }

    private PurchaseRequest lock(Long id) {
        return requests.lockById(id).orElseThrow(() -> ApiException.notFound("Purchase request"));
    }

    private boolean owns(PurchaseRequest pr) {
        User me = current.user();
        return me.isSuperAdmin() || me.getId().equals(pr.getRequestedBy()) || me.hasPermission("edit_procurement");
    }

    private void ownerOr(PurchaseRequest pr) {
        if (!owns(pr)) throw ApiException.forbidden("You do not have permission to modify this purchase request.");
    }

    private Map<PurchaseRequest, PrView> views(Collection<PurchaseRequest> list, boolean withItems) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().flatMap(p -> Stream.concat(Stream.of(p.getRequestedBy(), p.getCafCertifiedBy()),
                        withItems ? p.getApprovals().stream().map(PurchaseRequestApproval::getActedBy) : Stream.empty()))
                .filter(Objects::nonNull).collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        Map<Long, String> depts = new HashMap<>();
        departments.findAllById(list.stream().map(PurchaseRequest::getDepartmentId).collect(Collectors.toSet())).forEach(d -> depts.put(d.getId(), d.getName()));
        Map<Long, AppPlan> byApp = new HashMap<>();
        apps.findAllById(list.stream().map(PurchaseRequest::getAppId).filter(Objects::nonNull).collect(Collectors.toSet())).forEach(a -> byApp.put(a.getId(), a));
        User me = current.user();
        boolean certifier = current.any("pr_approve_budget", "pr_approve_accounting");
        Map<PurchaseRequest, PrView> out = new LinkedHashMap<>();
        for (PurchaseRequest p : list) {
            boolean mine = me.getId().equals(p.getRequestedBy()) && !me.isSuperAdmin();
            boolean owner = owns(p);
            PurchaseRequestApproval step = p.currentApproval();
            boolean canAct = step != null && !mine && maySign(me, p, step);
            AppPlan a = p.getAppId() == null ? null : byApp.get(p.getAppId());
            List<LineView> items = !withItems ? null : p.getItems().stream().map(i -> new LineView(i.getId(), i.getAppItemId(), i.getGeneralDescription(),
                    i.getUnit(), i.getQuantity(), i.getEstimatedUnitCost(), i.getEstimatedTotal(), i.getSpecifications(), i.getProcurementMode(),
                    i.getProcurementCategory(), i.getFundSource(), i.isSustainable(), i.getRemarks())).toList();
            List<StepView> steps = !withItems ? null : p.getApprovals().stream().map(s -> new StepView(s.getStep(), s.getLabel(), s.getRequiredPermission(),
                    s.getStatus(), s.getActedBy() == null ? null : new Ref(s.getActedBy(), names.get(s.getActedBy())), Times.toOffset(s.getActedAt()), s.getRemarks())).toList();
            out.put(p, new PrView(p.getId(), p.getPrNumber(), new Ref(p.getDepartmentId(), depts.get(p.getDepartmentId())),
                    a == null ? null : new AppRef(a.getId(), a.getAppNumber(), a.getFiscalYear()), p.getFiscalYear(),
                    new Ref(p.getRequestedBy(), names.get(p.getRequestedBy())), p.getRequestDate(), p.getPurpose(), p.getStatus(), p.stageLabel(),
                    p.getCurrentStep(), p.getPriority(), p.getTotalAmount(), p.getCafNumber(),
                    p.getCafCertifiedBy() == null ? null : new Ref(p.getCafCertifiedBy(), names.get(p.getCafCertifiedBy())),
                    Times.toOffset(p.getCafCertifiedAt()), p.getCafAmount(), p.getCafRemarks(), p.getRemarks(), p.getReturnReason(),
                    Times.toOffset(p.getCancelledAt()), p.getItems().size(), items, steps,
                    distinct(p, PurchaseRequestItem::getProcurementMode), distinct(p, PurchaseRequestItem::getProcurementCategory),
                    distinct(p, PurchaseRequestItem::getFundSource), fundsCovered(p),
                    owner && p.isEditable(), owner && p.isEditable(), owner && (p.isEditable() || p.isRouting()),
                    owner && (p.isEditable() || PurchaseRequest.CANCELLED.equals(p.getStatus())), canAct,
                    certifier && p.isRouting() && !mine && !fundsCovered(p), Times.toOffset(p.getCreatedAt())));
        }
        return out;
    }

    private static List<String> distinct(PurchaseRequest p, Function<PurchaseRequestItem, String> f) {
        return p.getItems().stream().map(f).filter(Objects::nonNull).distinct().toList();
    }

    private static Map<String, Object> snapshot(PurchaseRequest p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("purpose", p.getPurpose());
        m.put("priority", p.getPriority());
        m.put("status", p.getStatus());
        m.put("app_id", p.getAppId());
        m.put("lines", p.getItems().size());
        m.put("total_amount", p.getTotalAmount());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

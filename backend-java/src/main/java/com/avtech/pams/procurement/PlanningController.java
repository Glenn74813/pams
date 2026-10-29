package com.avtech.pams.procurement;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Procurement planning: department PPMPs (draft → submitted → under review → approved, or
 * returned for revision), the agency APP consolidated from the approved ones, and budget
 * monitoring of the APP against the purchase requests drawn on it.
 */
@RestController
@RequestMapping("/api/procurement-planning")
public class PlanningController {

    private static final String PPMP_TYPE = "App\\Models\\Ppmp";
    private static final String APP_TYPE = "App\\Models\\App";
    /** Permissions that see and work on every department's PPMP. */
    private static final String[] AGENCY_WIDE = {"view_procurement_planning", "approve_ppmp", "manage_app"};

    private final PpmpRepository ppmps;
    private final AppPlanRepository apps;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final ProcurementRules rules;
    private final AppUsage usage;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final Db db;

    public PlanningController(PpmpRepository ppmps, AppPlanRepository apps, DepartmentRepository departments, UserRepository users,
                              ProcurementRules rules, AppUsage usage, DocumentNumbers numbers, NotificationService notifications,
                              AuditService audit, CurrentUser current, JdbcTemplate jdbc, Db db) {
        this.ppmps = ppmps;
        this.apps = apps;
        this.departments = departments;
        this.users = users;
        this.rules = rules;
        this.usage = usage;
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

    public record Options(List<String> modes, List<String> categories, List<String> fundSources, List<String> quarters,
                          Map<String, BigDecimal> ceilings) {
    }

    public record PpmpLineView(Long id, String generalDescription, String specifications, String code, String unit, int quantity,
                               BigDecimal estimatedUnitCost, BigDecimal estimatedTotal, String procurementMode,
                               String procurementCategory, boolean isSustainable, String schedule, String fundSource, String remarks) {
    }

    public record PpmpView(Long id, String ppmpNumber, Ref department, Integer fiscalYear, String title, String status,
                           BigDecimal totalEstimatedBudget, int revision, Ref preparer, Ref reviewer, Ref approver,
                           OffsetDateTime submittedAt, OffsetDateTime approvedAt, String returnReason, String remarks,
                           int itemsCount, List<PpmpLineView> items, String appNumber,
                           boolean canEdit, boolean canSubmit, boolean canReview, boolean canDecide, boolean canDelete,
                           OffsetDateTime createdAt) {
    }

    public record AppLineView(Long id, Long ppmpId, String ppmpNumber, Ref department, String generalDescription, String specifications,
                              String unit, String procurementMode, String procurementCategory, int quantity, BigDecimal estimatedBudget,
                              BigDecimal unitCost, String schedule, String fundSource, int requestedQuantity, int remainingQuantity,
                              BigDecimal requestedAmount, BigDecimal utilizedAmount, BigDecimal remainingBudget,
                              BigDecimal utilizationRate, String status) {
    }

    public record AppView(Long id, String appNumber, Integer fiscalYear, String title, String status, BigDecimal totalBudget,
                          Ref preparer, Ref approver, OffsetDateTime approvedAt, String remarks, int itemsCount, int ppmpCount,
                          BigDecimal requestedAmount, BigDecimal utilizedAmount, boolean hasPr, boolean isFullyRequested,
                          int pendingPpmps, List<AppLineView> items, boolean canEdit, boolean canApprove, boolean canDelete,
                          OffsetDateTime createdAt) {
    }

    @GetMapping("/options")
    public Options options() {
        Map<String, BigDecimal> ceilings = new TreeMap<>();
        for (String m : ProcurementRules.MODES) {
            BigDecimal c = rules.ceiling(m);
            if (c != null) ceilings.put(m, c);
        }
        return new Options(ProcurementRules.MODES, ProcurementRules.CATEGORIES, ProcurementRules.FUND_SOURCES, ProcurementRules.QUARTERS, ceilings);
    }

    // ───────────────────────── PPMP ─────────────────────────

    @GetMapping("/ppmps")
    @PreAuthorize("@perm.any('view_procurement_planning', 'create_ppmp', 'view_own_ppmp', 'edit_own_ppmp', 'delete_own_ppmp', 'submit_own_ppmp', 'approve_ppmp', 'manage_app')")
    @Transactional(readOnly = true)
    public PageResponse<PpmpView> indexPpmp(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(name = "fiscal_year", required = false) Integer year,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(name = "department_id", required = false) Long department,
                                            @RequestParam(required = false) String search) {
        User me = current.user();
        boolean all = current.any(AGENCY_WIDE);
        Long dept = !all && current.any("create_ppmp") ? me.getDepartment().getId() : null;
        Page<Ppmp> result = ppmps.search(!all, me.getId(), dept, year, blank(status), department, blank(search),
                PageRequest.of(Math.max(0, page - 1), 20));
        Map<Ppmp, PpmpView> v = ppmpViews(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/ppmps/{id}")
    @PreAuthorize("@perm.any('view_procurement_planning', 'create_ppmp', 'view_own_ppmp', 'edit_own_ppmp', 'delete_own_ppmp', 'submit_own_ppmp', 'approve_ppmp', 'manage_app')")
    @Transactional(readOnly = true)
    public PpmpView showPpmp(@PathVariable Long id) {
        Ppmp p = ppmps.findLive(id).orElseThrow(() -> ApiException.notFound("PPMP"));
        if (!canSee(p)) throw ApiException.forbidden("You can only view your own department's PPMPs.");
        return ppmpViews(List.of(p), true).get(p);
    }

    public record PpmpInput(Long departmentId, Integer fiscalYear, String title, String remarks, List<ProcurementRules.LineInput> items) {
    }

    @PostMapping("/ppmps")
    @PreAuthorize("@perm.any('create_ppmp')")
    @Transactional
    public ResponseEntity<PpmpView> storePpmp(@RequestBody PpmpInput in) {
        User me = current.user();
        Integer year = in.fiscalYear();
        if (year == null || year < 2000 || year > 2100) throw ApiException.validation("fiscal_year", "Enter the fiscal year.");
        // Only agency-wide planners file for another department.
        Long dept = current.any(AGENCY_WIDE) && in.departmentId() != null ? in.departmentId() : me.getDepartment().getId();
        if (!departments.existsById(dept)) throw ApiException.validation("department_id", "That department does not exist.");
        Ppmp p = new Ppmp(numbers.series("PPMP-" + year + "-", 4, "ppmps.ppmp_number"), dept, year, me.getId());
        apply(p, in);
        ppmps.save(p);
        audit.record("created", PPMP_TYPE, p.getId(), "Created PPMP " + p.getPpmpNumber(), null, snapshot(p));
        return ResponseEntity.status(HttpStatus.CREATED).body(ppmpViews(List.of(p), true).get(p));
    }

    @PutMapping("/ppmps/{id}")
    @PreAuthorize("@perm.any('create_ppmp', 'edit_own_ppmp')")
    @Transactional
    public PpmpView updatePpmp(@PathVariable Long id, @RequestBody PpmpInput in) {
        Ppmp p = lockPpmp(id);
        guardManage(p, "edit_own_ppmp");
        if (!p.isEditable()) throw ApiException.of(HttpStatus.CONFLICT, "Only draft or returned PPMPs can be edited.");
        Map<String, Object> before = snapshot(p);
        apply(p, in);
        ppmps.flush();
        audit.record("updated", PPMP_TYPE, p.getId(), "Edited PPMP " + p.getPpmpNumber(), before, snapshot(p));
        return ppmpViews(List.of(p), true).get(p);
    }

    @DeleteMapping("/ppmps/{id}")
    @PreAuthorize("@perm.any('create_ppmp', 'delete_own_ppmp')")
    @Transactional
    public Map<String, Object> destroyPpmp(@PathVariable Long id) {
        Ppmp p = lockPpmp(id);
        guardManage(p, "delete_own_ppmp");
        if (!p.isEditable()) throw ApiException.of(HttpStatus.CONFLICT, "Only draft or returned PPMPs can be deleted.");
        p.setDeletedAt(Times.now());
        audit.record("deleted", PPMP_TYPE, p.getId(), "Deleted PPMP " + p.getPpmpNumber(), snapshot(p), null);
        return Map.of("message", "PPMP " + p.getPpmpNumber() + " deleted.");
    }

    @PostMapping("/ppmps/{id}/submit")
    @PreAuthorize("@perm.any('create_ppmp', 'submit_own_ppmp')")
    @Transactional
    public PpmpView submitPpmp(@PathVariable Long id) {
        Ppmp p = lockPpmp(id);
        guardManage(p, "submit_own_ppmp");
        if (!p.isEditable()) throw ApiException.of(HttpStatus.CONFLICT, "Only draft or returned PPMPs can be submitted.");
        if (p.getItems().isEmpty()) throw ApiException.validation("items", "Add at least one line before submitting.");
        String was = p.getStatus();
        p.setStatus(Ppmp.SUBMITTED);
        p.setSubmittedAt(Times.now());
        p.setReturnReason(null);
        ppmps.flush();
        audit.record("submitted", PPMP_TYPE, p.getId(), "PPMP " + p.getPpmpNumber() + " submitted for review", Map.of("status", was), Map.of("status", p.getStatus()));
        notifications.notifyPermission(List.of("approve_ppmp"), current.user().getId(), "ppmp_review", "PPMP awaiting review",
                "PPMP " + p.getPpmpNumber() + " (" + p.getTitle() + ") was submitted for review.", PPMP_TYPE, p.getId());
        return ppmpViews(List.of(p), true).get(p);
    }

    @PostMapping("/ppmps/{id}/review")
    @PreAuthorize("@perm.any('approve_ppmp')")
    @Transactional
    public PpmpView reviewPpmp(@PathVariable Long id) {
        Ppmp p = lockPpmp(id);
        notOwn(p);
        if (!Ppmp.SUBMITTED.equals(p.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only submitted PPMPs can be taken up for review.");
        p.setStatus(Ppmp.UNDER_REVIEW);
        p.setReviewedBy(current.user().getId());
        ppmps.flush();
        audit.record("reviewed", PPMP_TYPE, p.getId(), "PPMP " + p.getPpmpNumber() + " under review", Map.of("status", "submitted"), Map.of("status", p.getStatus()));
        return ppmpViews(List.of(p), true).get(p);
    }

    @PostMapping("/ppmps/{id}/approve")
    @PreAuthorize("@perm.any('approve_ppmp')")
    @Transactional
    public PpmpView approvePpmp(@PathVariable Long id) {
        Ppmp p = lockPpmp(id);
        notOwn(p);
        if (!p.isInReview()) throw ApiException.of(HttpStatus.CONFLICT, "Only submitted or under-review PPMPs can be approved.");
        String was = p.getStatus();
        Long me = current.user().getId();
        p.setStatus(Ppmp.APPROVED);
        if (p.getReviewedBy() == null) p.setReviewedBy(me);
        p.setApprovedBy(me);
        p.setApprovedAt(Times.now());
        ppmps.flush();
        audit.record("approved", PPMP_TYPE, p.getId(), "PPMP " + p.getPpmpNumber() + " approved", Map.of("status", was), Map.of("status", p.getStatus()));
        notifications.notifyUser(p.getPreparedBy(), "ppmp_status", "PPMP approved", "Your PPMP " + p.getPpmpNumber() + " was approved.", PPMP_TYPE, p.getId());
        return ppmpViews(List.of(p), true).get(p);
    }

    public record ReturnInput(String returnReason) {
    }

    @PostMapping("/ppmps/{id}/return")
    @PreAuthorize("@perm.any('approve_ppmp')")
    @Transactional
    public PpmpView returnPpmp(@PathVariable Long id, @RequestBody ReturnInput in) {
        String reason = in == null ? null : ProcurementRules.text(in.returnReason());
        if (reason == null) throw ApiException.validation("return_reason", "Say what needs revising.");
        if (reason.length() > 1000) throw ApiException.validation("return_reason", "Keep the reason under 1000 characters.");
        Ppmp p = lockPpmp(id);
        notOwn(p);
        if (!p.isInReview()) throw ApiException.of(HttpStatus.CONFLICT, "Only submitted or under-review PPMPs can be returned.");
        String was = p.getStatus();
        p.setStatus(Ppmp.RETURNED);
        p.setReturnReason(reason);
        p.setRevision(p.getRevision() + 1);
        ppmps.flush();
        audit.record("returned", PPMP_TYPE, p.getId(), "PPMP " + p.getPpmpNumber() + " returned: " + reason, Map.of("status", was), Map.of("status", p.getStatus()));
        notifications.notifyUser(p.getPreparedBy(), "ppmp_status", "PPMP returned for revision",
                "Your PPMP " + p.getPpmpNumber() + " was returned: " + reason, PPMP_TYPE, p.getId());
        return ppmpViews(List.of(p), true).get(p);
    }

    // ───────────────────────── APP ─────────────────────────

    @GetMapping("/apps")
    @PreAuthorize("@perm.any('view_procurement_planning', 'manage_app', 'monitor_budget', 'create_procurement', 'edit_procurement')")
    @Transactional(readOnly = true)
    public PageResponse<AppView> indexApp(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(name = "fiscal_year", required = false) Integer year,
                                          @RequestParam(required = false) String status) {
        // People who only raise purchase requests see approved plans, which are the ones they can draw on.
        String s = current.any("view_procurement_planning", "manage_app", "monitor_budget") ? blank(status) : AppPlan.APPROVED;
        Page<AppPlan> result = apps.search(year, s, PageRequest.of(Math.max(0, page - 1), 20));
        Map<AppPlan, AppView> v = appViews(result.getContent(), false, null);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/apps/{id}")
    @PreAuthorize("@perm.any('view_procurement_planning', 'manage_app', 'monitor_budget', 'create_procurement', 'edit_procurement')")
    @Transactional(readOnly = true)
    public AppView showApp(@PathVariable Long id, @RequestParam(name = "exclude_pr_id", required = false) Long excludePr) {
        AppPlan a = apps.findLive(id).orElseThrow(() -> ApiException.notFound("APP"));
        if (!a.isApproved() && !current.any("view_procurement_planning", "manage_app", "monitor_budget")) throw ApiException.notFound("APP");
        return appViews(List.of(a), true, excludePr).get(a);
    }

    public record GenerateInput(Integer fiscalYear, String title) {
    }

    /**
     * Consolidates the year's approved PPMPs. The first run creates the year's APP; later runs add
     * the lines of PPMPs approved since, until the APP itself is approved.
     */
    @PostMapping("/apps/generate")
    @PreAuthorize("@perm.any('manage_app')")
    @Transactional
    public ResponseEntity<AppView> generateApp(@RequestBody GenerateInput in) {
        Integer year = in == null ? null : in.fiscalYear();
        if (year == null || year < 2000 || year > 2100) throw ApiException.validation("fiscal_year", "Enter the fiscal year.");
        db.namedLock("pams.app." + year);
        AppPlan a = apps.lockForYear(year).orElse(null);
        boolean created = a == null;
        if (a != null && a.isApproved()) {
            throw ApiException.of(HttpStatus.CONFLICT, a.getAppNumber() + " for FY " + year + " is already approved; it can no longer be reconsolidated.");
        }
        List<Ppmp> approved = ppmps.lockApproved(year);
        if (approved.isEmpty()) throw ApiException.validation("fiscal_year", "No approved PPMPs for fiscal year " + year + ".");
        Set<Long> already = new HashSet<>(jdbc.queryForList(
                "select ppmp_item_id from app_items where ppmp_item_id is not null", Long.class));
        if (created) {
            String title = ProcurementRules.text(in.title());
            a = new AppPlan(numbers.series("APP-" + year + "-", 3, "apps.app_number"), year,
                    title == null ? "Annual Procurement Plan " + year : title, current.user().getId());
        }
        int added = 0;
        Set<String> from = new TreeSet<>();
        for (Ppmp p : approved) {
            for (PpmpItem it : p.getItems()) {
                if (already.contains(it.getId())) continue;
                a.getItems().add(new AppItem(a, p, it));
                from.add(p.getPpmpNumber());
                added++;
            }
        }
        if (added == 0) throw ApiException.of(HttpStatus.CONFLICT, "Every approved PPMP for FY " + year + " is already in " + a.getAppNumber() + ".");
        a.recalc();
        apps.saveAndFlush(a);
        audit.record(created ? "generated" : "reconsolidated", APP_TYPE, a.getId(),
                (created ? "APP " + a.getAppNumber() + " consolidated from " : "Added to APP " + a.getAppNumber() + " from ")
                        + String.join(", ", from) + " (" + added + " line(s))", null, Map.of("lines_added", added, "total_budget", a.getTotalBudget()));
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(appViews(List.of(a), true, null).get(a));
    }

    public record AppLineInput(Long id, String generalDescription, String specifications, String unit, String procurementMode,
                               String procurementCategory, Integer quantity, BigDecimal estimatedBudget, String schedule, String fundSource) {
    }

    public record AppInput(String title, String remarks, List<AppLineInput> items) {
    }

    @PutMapping("/apps/{id}")
    @PreAuthorize("@perm.any('manage_app')")
    @Transactional
    public AppView updateApp(@PathVariable Long id, @RequestBody AppInput in) {
        AppPlan a = apps.lockById(id).orElseThrow(() -> ApiException.notFound("APP"));
        if (a.isApproved()) throw ApiException.of(HttpStatus.CONFLICT, "An approved APP can no longer be edited.");
        Map<String, Object> before = Map.of("title", a.getTitle(), "total_budget", a.getTotalBudget());
        if (in.title() != null) {
            String t = ProcurementRules.text(in.title());
            if (t == null || t.length() > 255) throw ApiException.validation("title", "Give the APP a title of up to 255 characters.");
            a.setTitle(t);
        }
        if (in.remarks() != null) a.setRemarks(ProcurementRules.text(in.remarks()));
        if (in.items() != null) {
            Map<Long, AppItem> byId = a.getItems().stream().collect(Collectors.toMap(AppItem::getId, Function.identity()));
            Map<Long, AppUsage.Use> used = usage.forItems(byId.keySet(), null);
            for (int i = 0; i < in.items().size(); i++) {
                AppLineInput l = in.items().get(i);
                String f = "items." + i + ".";
                AppItem it = l == null || l.id() == null ? null : byId.get(l.id());
                if (it == null) throw ApiException.validation(f + "id", "Line " + (i + 1) + " is not part of this APP.");
                String desc = ProcurementRules.text(l.generalDescription());
                if (desc == null || desc.length() > 255) throw ApiException.validation(f + "general_description", "Line " + (i + 1) + " needs a description of up to 255 characters.");
                if (l.quantity() == null || l.quantity() < 1) throw ApiException.validation(f + "quantity", "Line " + (i + 1) + ": quantity must be at least 1.");
                int taken = usage.of(used, it.getId()).quantity();
                if (l.quantity() < taken) throw ApiException.validation(f + "quantity", "Line " + (i + 1) + ": " + taken + " are already on purchase requests.");
                if (l.estimatedBudget() == null || l.estimatedBudget().signum() < 0 || l.estimatedBudget().scale() > 2) {
                    throw ApiException.validation(f + "estimated_budget", "Line " + (i + 1) + ": enter the budget in pesos.");
                }
                String mode = ProcurementRules.text(l.procurementMode());
                if (mode != null && !ProcurementRules.MODES.contains(mode)) throw ApiException.validation(f + "procurement_mode", "Line " + (i + 1) + ": unknown mode.");
                String cat = ProcurementRules.text(l.procurementCategory());
                if (cat != null && !ProcurementRules.CATEGORIES.contains(cat)) throw ApiException.validation(f + "procurement_category", "Line " + (i + 1) + ": unknown category.");
                String fund = ProcurementRules.text(l.fundSource());
                if (fund != null && !ProcurementRules.FUND_SOURCES.contains(fund)) throw ApiException.validation(f + "fund_source", "Line " + (i + 1) + ": unknown source of funds.");
                String sched = ProcurementRules.text(l.schedule());
                if (sched != null && !ProcurementRules.QUARTERS.contains(sched)) throw ApiException.validation(f + "schedule", "Line " + (i + 1) + ": schedule is Q1 to Q4.");
                rules.checkCeiling(f + "procurement_mode", desc, mode != null ? mode : it.getProcurementMode(), l.estimatedBudget());
                it.setGeneralDescription(desc);
                it.setSpecifications(ProcurementRules.text(l.specifications()));
                it.setUnit(ProcurementRules.text(l.unit()));
                if (mode != null) it.setProcurementMode(mode);
                if (cat != null) it.setProcurementCategory(cat);
                it.setQuantity(l.quantity());
                it.setEstimatedBudget(l.estimatedBudget().setScale(2, RoundingMode.HALF_UP));
                it.setSchedule(sched);
                it.setFundSource(fund);
            }
            a.recalc();
        }
        apps.flush();
        audit.record("updated", APP_TYPE, a.getId(), "APP " + a.getAppNumber() + " updated", before, Map.of("title", a.getTitle(), "total_budget", a.getTotalBudget()));
        return appViews(List.of(a), true, null).get(a);
    }

    @PostMapping("/apps/{id}/approve")
    @PreAuthorize("@perm.any('manage_app')")
    @Transactional
    public AppView approveApp(@PathVariable Long id) {
        AppPlan a = apps.lockById(id).orElseThrow(() -> ApiException.notFound("APP"));
        if (a.isApproved()) throw ApiException.of(HttpStatus.CONFLICT, "This APP is already approved.");
        User me = current.user();
        if (me.getId().equals(a.getPreparedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You consolidated this APP, so someone else has to approve it.");
        if (a.getItems().isEmpty()) throw ApiException.of(HttpStatus.CONFLICT, "The APP has no lines.");
        a.setStatus(AppPlan.APPROVED);
        a.setApprovedBy(me.getId());
        a.setApprovedAt(Times.now());
        apps.flush();
        audit.record("approved", APP_TYPE, a.getId(), "APP " + a.getAppNumber() + " approved", Map.of("status", AppPlan.CONSOLIDATED), Map.of("status", a.getStatus()));
        notifySchedule(a);
        return appViews(List.of(a), true, null).get(a);
    }

    @DeleteMapping("/apps/{id}")
    @PreAuthorize("@perm.any('manage_app')")
    @Transactional
    public Map<String, Object> destroyApp(@PathVariable Long id) {
        AppPlan a = apps.lockById(id).orElseThrow(() -> ApiException.notFound("APP"));
        if (a.isApproved()) throw ApiException.of(HttpStatus.CONFLICT, "An approved APP cannot be deleted.");
        Integer lines = jdbc.queryForObject("""
                select count(*) from purchase_request_items pri join app_items ai on ai.id = pri.app_item_id
                join purchase_requests pr on pr.id = pri.purchase_request_id where ai.app_id = ? and pr.deleted_at is null""", Integer.class, a.getId());
        if (lines != null && lines > 0) throw ApiException.of(HttpStatus.CONFLICT, "Cannot delete: " + lines + " purchase request line(s) draw on this APP.");
        // The lines go, so their PPMPs can be consolidated again; the number stays used.
        a.getItems().clear();
        a.recalc();
        a.setDeletedAt(Times.now());
        audit.record("deleted", APP_TYPE, a.getId(), "Deleted APP " + a.getAppNumber(), Map.of("app_number", a.getAppNumber()), null);
        return Map.of("message", "APP " + a.getAppNumber() + " deleted.");
    }

    // ───────────────────────── budget monitoring ─────────────────────────

    public record BudgetRow(String key, String label, int count, BigDecimal estimatedBudget, BigDecimal requestedAmount,
                            BigDecimal utilizedAmount, BigDecimal remaining, BigDecimal utilizationRate) {
    }

    public record BudgetSummary(int fiscalYear, String appNumber, String appStatus, BigDecimal totalEstimated, BigDecimal totalRequested,
                                BigDecimal totalUtilized, BigDecimal totalRemaining, BigDecimal utilizationRate, int ppmpCount,
                                int ppmpApprovedCount, List<BudgetRow> byDepartment, List<BudgetRow> byMode, List<BudgetRow> byQuarter) {
    }

    /** Utilization is the estimated cost of fully approved purchase requests drawn on each APP line. */
    @GetMapping("/budget-summary")
    @PreAuthorize("@perm.any('view_procurement_planning', 'manage_app', 'monitor_budget')")
    @Transactional(readOnly = true)
    public BudgetSummary budgetSummary(@RequestParam(name = "fiscal_year", required = false) Integer year) {
        int fy = year == null ? LocalDate.now(Times.ZONE).getYear() : year;
        AppPlan a = apps.search(fy, null, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        List<AppItem> lines = a == null ? List.of() : a.getItems();
        Map<Long, AppUsage.Use> used = a == null ? Map.of() : usage.forApps(List.of(a.getId()));
        Map<Long, String> deptNames = new HashMap<>();
        departments.findAllById(lines.stream().map(AppItem::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(d -> deptNames.put(d.getId(), d.getName()));
        Integer ppmpCount = jdbc.queryForObject("select count(*) from ppmps where fiscal_year = ? and deleted_at is null", Integer.class, fy);
        Integer approvedCount = jdbc.queryForObject("select count(*) from ppmps where fiscal_year = ? and status = 'approved' and deleted_at is null", Integer.class, fy);
        BudgetRow total = row("all", "All", lines, used);
        return new BudgetSummary(fy, a == null ? null : a.getAppNumber(), a == null ? null : a.getStatus(), total.estimatedBudget(),
                total.requestedAmount(), total.utilizedAmount(), total.remaining(), total.utilizationRate(),
                ppmpCount == null ? 0 : ppmpCount, approvedCount == null ? 0 : approvedCount,
                group(lines, used, i -> String.valueOf(i.getDepartmentId()), i -> deptNames.getOrDefault(i.getDepartmentId(), "Unassigned")),
                group(lines, used, AppItem::getProcurementMode, AppItem::getProcurementMode),
                group(lines, used, i -> i.getSchedule() == null ? "none" : i.getSchedule(), i -> i.getSchedule() == null ? "Unscheduled" : quarterLabel(i.getSchedule())));
    }

    // ───────────────────────── helpers ─────────────────────────

    private List<BudgetRow> group(List<AppItem> lines, Map<Long, AppUsage.Use> used, Function<AppItem, String> key, Function<AppItem, String> label) {
        Map<String, List<AppItem>> groups = new LinkedHashMap<>();
        lines.forEach(i -> groups.computeIfAbsent(key.apply(i), k -> new ArrayList<>()).add(i));
        return groups.entrySet().stream().map(e -> row(e.getKey(), label.apply(e.getValue().get(0)), e.getValue(), used))
                .sorted((x, y) -> y.estimatedBudget().compareTo(x.estimatedBudget())).toList();
    }

    private BudgetRow row(String key, String label, List<AppItem> lines, Map<Long, AppUsage.Use> used) {
        BigDecimal est = BigDecimal.ZERO, req = BigDecimal.ZERO, util = BigDecimal.ZERO;
        for (AppItem i : lines) {
            AppUsage.Use u = usage.of(used, i.getId());
            est = est.add(i.getEstimatedBudget());
            req = req.add(u.requested());
            util = util.add(u.committed());
        }
        return new BudgetRow(key, label, lines.size(), est, req, util, est.subtract(util), rate(util, est));
    }

    private static BigDecimal rate(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO.setScale(1) : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    static String quarterLabel(String q) {
        return switch (q) {
            case "Q1" -> "Q1 (Jan–Mar)";
            case "Q2" -> "Q2 (Apr–Jun)";
            case "Q3" -> "Q3 (Jul–Sep)";
            case "Q4" -> "Q4 (Oct–Dec)";
            default -> q;
        };
    }

    private void notifySchedule(AppPlan a) {
        Map<String, List<AppItem>> byQ = new TreeMap<>();
        a.getItems().stream().filter(i -> i.getSchedule() != null).forEach(i -> byQ.computeIfAbsent(i.getSchedule(), k -> new ArrayList<>()).add(i));
        if (byQ.isEmpty()) return;
        String summary = byQ.entrySet().stream().map(e -> quarterLabel(e.getKey()) + ": " + e.getValue().size() + " item(s) (₱"
                + ProcurementRules.peso(e.getValue().stream().map(AppItem::getEstimatedBudget).reduce(BigDecimal.ZERO, BigDecimal::add)) + ")")
                .collect(Collectors.joining("; "));
        String now = "Q" + ((LocalDate.now(Times.ZONE).getMonthValue() + 2) / 3);
        String due = byQ.containsKey(now) && a.getFiscalYear() == LocalDate.now(Times.ZONE).getYear()
                ? " " + byQ.get(now).size() + " item(s) are due this quarter (" + quarterLabel(now) + "); start the purchase requests." : "";
        notifications.notifyPermission(List.of("view_procurement", "view_bac", "manage_bidding", "create_rfq"),
                current.user().getId(), "procurement_schedule", "APP " + a.getAppNumber() + " approved — procurement schedule",
                "APP " + a.getAppNumber() + " (FY " + a.getFiscalYear() + ") is approved. Scheduled procurement: " + summary + "." + due, APP_TYPE, a.getId());
    }

    private void apply(Ppmp p, PpmpInput in) {
        String title = ProcurementRules.text(in.title());
        if (title == null) throw ApiException.validation("title", "Give the PPMP a title.");
        if (title.length() > 255) throw ApiException.validation("title", "Keep the title under 255 characters.");
        List<ProcurementRules.Line> lines = rules.lines(in.items(), true);
        for (int i = 0; i < lines.size(); i++) {
            ProcurementRules.Line l = lines.get(i);
            rules.checkCeiling("items." + i + ".procurement_mode", l.description(), l.mode(), l.total());
        }
        p.setTitle(title);
        p.setRemarks(ProcurementRules.text(in.remarks()));
        p.getItems().clear();
        lines.forEach(l -> p.getItems().add(new PpmpItem(p, l.category() == null ? withCategory(l) : l)));
        p.recalc();
    }

    private static ProcurementRules.Line withCategory(ProcurementRules.Line l) {
        return new ProcurementRules.Line(l.description(), l.specifications(), l.code(), l.unit(), l.quantity(), l.unitCost(), l.total(),
                l.mode(), "Goods", l.sustainable(), l.schedule(), l.fundSource(), l.remarks(), null);
    }

    private Ppmp lockPpmp(Long id) {
        return ppmps.lockById(id).orElseThrow(() -> ApiException.notFound("PPMP"));
    }

    private boolean canSee(Ppmp p) {
        User me = current.user();
        if (current.any(AGENCY_WIDE) || me.getId().equals(p.getPreparedBy())) return true;
        return current.any("create_ppmp") && p.getDepartmentId().equals(me.getDepartment().getId());
    }

    /** create_ppmp works on PPMPs of its own department; the *_own_ppmp permissions only on PPMPs the user prepared. */
    private boolean canManage(Ppmp p, String ownPermission) {
        User me = current.user();
        if (me.isSuperAdmin()) return true;
        boolean mine = me.getId().equals(p.getPreparedBy());
        if (current.any("create_ppmp") && (mine || current.any(AGENCY_WIDE) || p.getDepartmentId().equals(me.getDepartment().getId()))) return true;
        return mine && me.hasPermission(ownPermission);
    }

    private void guardManage(Ppmp p, String ownPermission) {
        if (!canManage(p, ownPermission)) throw ApiException.forbidden("You can only manage your own department's PPMPs.");
    }

    private void notOwn(Ppmp p) {
        User me = current.user();
        if (me.getId().equals(p.getPreparedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You prepared this PPMP, so someone else has to review it.");
    }

    private Map<Ppmp, PpmpView> ppmpViews(Collection<Ppmp> list, boolean withItems) {
        Map<Long, String> names = names(list.stream().flatMap(p -> Stream.of(p.getPreparedBy(), p.getReviewedBy(), p.getApprovedBy())));
        Map<Long, String> depts = new HashMap<>();
        departments.findAllById(list.stream().map(Ppmp::getDepartmentId).collect(Collectors.toSet())).forEach(d -> depts.put(d.getId(), d.getName()));
        Map<Long, String> inApp = new HashMap<>();
        if (!list.isEmpty()) {
            String in = list.stream().map(p -> "?").collect(Collectors.joining(","));
            jdbc.query("select distinct ai.ppmp_id, a.app_number from app_items ai join apps a on a.id = ai.app_id where a.deleted_at is null and ai.ppmp_id in (" + in + ")",
                    rs -> {
                        inApp.put(rs.getLong(1), rs.getString(2));
                    }, list.stream().map(Ppmp::getId).toArray());
        }
        boolean approver = current.any("approve_ppmp");
        User me = current.user();
        Map<Ppmp, PpmpView> out = new LinkedHashMap<>();
        for (Ppmp p : list) {
            boolean own = me.getId().equals(p.getPreparedBy()) && !me.isSuperAdmin();
            boolean editable = p.isEditable();
            List<PpmpLineView> items = !withItems ? null : p.getItems().stream().map(i -> new PpmpLineView(i.getId(), i.getGeneralDescription(),
                    i.getSpecifications(), i.getCode(), i.getUnit(), i.getQuantity(), i.getEstimatedUnitCost(), i.getEstimatedTotal(),
                    i.getProcurementMode(), i.getProcurementCategory(), i.isSustainable(), i.getSchedule(), i.getFundSource(), i.getRemarks())).toList();
            out.put(p, new PpmpView(p.getId(), p.getPpmpNumber(), new Ref(p.getDepartmentId(), depts.get(p.getDepartmentId())), p.getFiscalYear(),
                    p.getTitle(), p.getStatus(), p.getTotalEstimatedBudget(), p.getRevision(), ref(p.getPreparedBy(), names),
                    ref(p.getReviewedBy(), names), ref(p.getApprovedBy(), names), Times.toOffset(p.getSubmittedAt()), Times.toOffset(p.getApprovedAt()),
                    p.getReturnReason(), p.getRemarks(), p.getItems().size(), items, inApp.get(p.getId()),
                    editable && canManage(p, "edit_own_ppmp"), editable && canManage(p, "submit_own_ppmp"),
                    approver && !own && Ppmp.SUBMITTED.equals(p.getStatus()), approver && !own && p.isInReview(),
                    editable && canManage(p, "delete_own_ppmp"), Times.toOffset(p.getCreatedAt())));
        }
        return out;
    }

    private Map<AppPlan, AppView> appViews(Collection<AppPlan> list, boolean withItems, Long excludePr) {
        Map<Long, String> names = names(list.stream().flatMap(a -> Stream.of(a.getPreparedBy(), a.getApprovedBy())));
        Map<Long, AppUsage.Use> used = withItems
                ? usage.forItems(list.stream().flatMap(a -> a.getItems().stream()).map(AppItem::getId).toList(), excludePr)
                : usage.forApps(list.stream().map(AppPlan::getId).toList());
        Map<Long, String> depts = new HashMap<>();
        Map<Long, String> ppmpNumbers = new HashMap<>();
        if (withItems) {
            departments.findAllById(list.stream().flatMap(a -> a.getItems().stream()).map(AppItem::getDepartmentId).filter(Objects::nonNull)
                    .collect(Collectors.toSet())).forEach(d -> depts.put(d.getId(), d.getName()));
            ppmps.findAllById(list.stream().flatMap(a -> a.getItems().stream()).map(AppItem::getPpmpId).filter(Objects::nonNull)
                    .collect(Collectors.toSet())).forEach(p -> ppmpNumbers.put(p.getId(), p.getPpmpNumber()));
        }
        boolean manager = current.any("manage_app");
        User me = current.user();
        Map<AppPlan, AppView> out = new LinkedHashMap<>();
        for (AppPlan a : list) {
            BigDecimal requested = BigDecimal.ZERO, utilized = BigDecimal.ZERO;
            boolean hasPr = false, anyLeft = false;
            List<AppLineView> items = withItems ? new ArrayList<>() : null;
            for (AppItem i : a.getItems()) {
                AppUsage.Use u = usage.of(used, i.getId());
                requested = requested.add(u.requested());
                utilized = utilized.add(u.committed());
                if (u.quantity() > 0) hasPr = true;
                if (u.quantity() < i.getQuantity()) anyLeft = true;
                if (items != null) {
                    int left = Math.max(0, i.getQuantity() - u.quantity());
                    String status = u.quantity() == 0 ? "planned" : left == 0 && u.committed().compareTo(u.requested()) == 0 ? "completed" : "ongoing";
                    items.add(new AppLineView(i.getId(), i.getPpmpId(), ppmpNumbers.get(i.getPpmpId()),
                            i.getDepartmentId() == null ? null : new Ref(i.getDepartmentId(), depts.get(i.getDepartmentId())),
                            i.getGeneralDescription(), i.getSpecifications(), i.getUnit(), i.getProcurementMode(), i.getProcurementCategory(),
                            i.getQuantity(), i.getEstimatedBudget(),
                            i.getEstimatedBudget().divide(BigDecimal.valueOf(i.getQuantity()), 2, RoundingMode.HALF_UP),
                            i.getSchedule(), i.getFundSource(), u.quantity(), left, u.requested(), u.committed(),
                            i.getEstimatedBudget().subtract(u.committed()), rate(u.committed(), i.getEstimatedBudget()), status));
                }
            }
            int pending = a.isApproved() ? 0 : pendingPpmps(a.getFiscalYear());
            int ppmpCount = (int) a.getItems().stream().map(AppItem::getPpmpId).filter(Objects::nonNull).distinct().count();
            boolean own = me.getId().equals(a.getPreparedBy()) && !me.isSuperAdmin();
            out.put(a, new AppView(a.getId(), a.getAppNumber(), a.getFiscalYear(), a.getTitle(), a.getStatus(), a.getTotalBudget(),
                    ref(a.getPreparedBy(), names), ref(a.getApprovedBy(), names), Times.toOffset(a.getApprovedAt()), a.getRemarks(),
                    a.getItems().size(), ppmpCount, requested, utilized, hasPr, !a.getItems().isEmpty() && !anyLeft, pending, items,
                    manager && !a.isApproved(), manager && !a.isApproved() && !own, manager && !a.isApproved() && !hasPr,
                    Times.toOffset(a.getCreatedAt())));
        }
        return out;
    }

    /** Approved PPMPs of the year with lines not yet in any APP. */
    private int pendingPpmps(Integer year) {
        Integer n = jdbc.queryForObject("""
                select count(distinct p.id) from ppmps p join ppmp_items pi on pi.ppmp_id = p.id
                where p.fiscal_year = ? and p.status = 'approved' and p.deleted_at is null
                  and not exists (select 1 from app_items ai where ai.ppmp_item_id = pi.id)""", Integer.class, year);
        return n == null ? 0 : n;
    }

    private Map<Long, String> names(Stream<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(ids.filter(Objects::nonNull).collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        return names;
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id, names.get(id));
    }

    private static Map<String, Object> snapshot(Ppmp p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", p.getTitle());
        m.put("status", p.getStatus());
        m.put("lines", p.getItems().size());
        m.put("total_estimated_budget", p.getTotalEstimatedBudget());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

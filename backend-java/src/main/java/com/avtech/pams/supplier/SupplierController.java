package com.avtech.pams.supplier;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The supplier register (TOR §9): vendors, their compliance documents, blacklisting and suspension,
 * performance ratings, and performance worked out from their actual awards and deliveries. A vendor
 * is eligible for award only once marked so, which needs every required document on file and valid
 * and no sanction in force; a sanction makes it ineligible at once.
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private static final String TYPE = "App\\Models\\Supplier";
    private static final String READ = "@perm.any('view_suppliers', 'create_suppliers', 'edit_suppliers', 'view_procurement', 'edit_procurement', 'create_rfq', 'manage_bidding', 'process_award', 'view_contracts')";
    private static final String RATE = "@perm.any('edit_suppliers', 'accept_delivery', 'edit_procurement')";

    private final SupplierRepository suppliers;
    private final UserRepository users;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final Db db;

    public SupplierController(SupplierRepository suppliers, UserRepository users, DocumentNumbers numbers, NotificationService notifications,
                              AuditService audit, CurrentUser current, JdbcTemplate jdbc, Db db) {
        this.suppliers = suppliers;
        this.users = users;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
        this.db = db;
    }

    /** Supplier names for pickers. */
    @GetMapping("/options")
    @PreAuthorize("@perm.any('view_suppliers', 'create_inventory', 'edit_inventory', 'add_stock', 'manage_warehouse', 'create_rfq', 'manage_bidding', 'conduct_bid_evaluation', 'create_receiving')")
    public Map<String, Object> options() {
        return Map.of("data", jdbc.queryForList("select id, name from suppliers where is_active = " + db.t() + " order by name"));
    }

    // ───────────────────────── views ─────────────────────────

    public record Ref(Long id, String name) {
    }

    public record DocumentView(Long id, String documentType, String referenceNumber, LocalDate issuedDate, LocalDate expiryDate, String status,
                               Integer daysToExpiry, String remarks) {
    }

    public record Requirement(String documentType, boolean present, boolean valid, LocalDate expiryDate) {
    }

    public record Readiness(List<Requirement> required, List<String> missing, List<String> expired, boolean ready) {
    }

    public record SanctionView(Long id, String sanctionType, String grounds, String referenceNumber, LocalDate startDate, LocalDate endDate, String status,
                               boolean inForce, LocalDate liftedAt, String liftedReason, Ref recordedBy) {
    }

    public record EvaluationView(Long id, LocalDate evaluationDate, Integer deliveryRating, Integer qualityRating, Integer timelinessRating,
                                 Integer complianceRating, Integer afterSalesRating, BigDecimal overallRating, String reference, String remarks,
                                 Ref evaluator, boolean canDelete) {
    }

    public record Performance(int ratings, BigDecimal avgOverall, BigDecimal avgDelivery, BigDecimal avgQuality, Boolean satisfactory,
                              int purchaseOrders, BigDecimal purchaseOrdersValue, int contracts, BigDecimal contractsValue, int rfqsWon, int biddingsWon,
                              int deliveries, int onTimeDeliveries, BigDecimal onTimeRate, int unitsReceived, int unitsRejected, BigDecimal rejectionRate,
                              int rejectedDeliveries, BigDecimal liquidatedDamages) {
    }

    public record SupplierView(Long id, String vendorCode, String name, String tin, String philgepsRegNumber, String contactPerson, String email,
                               String phone, String mobileNumber, String address, String website, String creditTerms, String notes, boolean isActive,
                               boolean isEligible, boolean isMsme, boolean isSanctioned, String sanction, boolean awardable, Readiness eligibility,
                               int expiringDocuments, List<DocumentView> documents, List<SanctionView> sanctions, List<EvaluationView> evaluations,
                               Performance performance, boolean canEdit, boolean canDelete, OffsetDateTime createdAt) {
    }

    // ───────────────────────── register ─────────────────────────

    @GetMapping
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<SupplierView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) Boolean active,
                                            @RequestParam(required = false) Boolean eligible, @RequestParam(required = false) String search) {
        Page<Supplier> result = suppliers.search(active, eligible, blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<Supplier, SupplierView> v = views(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public SupplierView show(@PathVariable Long id) {
        return view(suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier")));
    }

    public record SupplierInput(String vendorCode, String name, String tin, String philgepsRegNumber, String contactPerson, String email, String phone,
                                String mobileNumber, String address, String website, String creditTerms, String notes, Boolean isActive, Boolean isMsme) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_suppliers')")
    @Transactional
    public ResponseEntity<SupplierView> store(@RequestBody SupplierInput in) {
        Supplier s = new Supplier();
        apply(s, in);
        if (s.getVendorCode() == null) s.setVendorCode(numbers.series("VND-", 4, "suppliers.vendor_code"));
        suppliers.saveAndFlush(s);
        audit.record("created", TYPE, s.getId(), "Registered supplier " + s.getName() + " (" + s.getVendorCode() + ")", null, snapshot(s));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(s));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('edit_suppliers')")
    @Transactional
    public SupplierView update(@PathVariable Long id, @RequestBody SupplierInput in) {
        Supplier s = lock(id);
        Map<String, Object> before = snapshot(s);
        apply(s, in);
        suppliers.flush();
        audit.record("updated", TYPE, s.getId(), "Updated supplier " + s.getName(), before, snapshot(s));
        return view(s);
    }

    /** A supplier with any procurement or stock history stays on the register; deactivate it instead. */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('delete_suppliers')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        Supplier s = lock(id);
        Integer refs = jdbc.queryForObject("""
                select (select count(*) from purchase_orders where supplier_id = ?) + (select count(*) from rfq_quotations where supplier_id = ?)
                     + (select count(*) from bidding_bidders where supplier_id = ?) + (select count(*) from contracts where supplier_id = ?)
                     + (select count(*) from stocks where supplier_id = ?)""", Integer.class, id, id, id, id, id);
        if (refs != null && refs > 0) throw ApiException.of(HttpStatus.CONFLICT, s.getName() + " has quotations, bids, orders, contracts or stock on record; deactivate it instead.");
        Map<String, Object> before = snapshot(s);
        suppliers.delete(s);
        audit.record("deleted", TYPE, id, "Deleted supplier " + s.getName(), before, null);
        return Map.of("message", s.getName() + " deleted.");
    }

    public record EligibilityInput(Boolean eligible, String reason) {
    }

    /** Marks a supplier eligible (documents complete and valid, no sanction) or ineligible (with a reason). */
    @PostMapping("/{id}/eligibility")
    @PreAuthorize("@perm.any('edit_suppliers')")
    @Transactional
    public SupplierView setEligibility(@PathVariable Long id, @RequestBody EligibilityInput in) {
        Supplier s = lock(id);
        boolean to = in != null && Boolean.TRUE.equals(in.eligible());
        LocalDate today = LocalDate.now(Times.ZONE);
        if (to) {
            if (!s.isActive()) throw ApiException.of(HttpStatus.CONFLICT, s.getName() + " is inactive.");
            SupplierSanction sanction = s.activeSanction(today);
            if (sanction != null) throw ApiException.of(HttpStatus.CONFLICT, s.getName() + " is under " + sanction.getSanctionType().toLowerCase() + " until "
                    + (sanction.getEndDate() == null ? "lifted" : sanction.getEndDate()) + ".");
            Readiness r = readiness(s, today);
            if (!r.ready()) {
                List<String> problems = new ArrayList<>();
                if (!r.missing().isEmpty()) problems.add("missing " + String.join(", ", r.missing()));
                if (!r.expired().isEmpty()) problems.add("expired " + String.join(", ", r.expired()));
                throw ApiException.of(HttpStatus.CONFLICT, "Required documents are not complete: " + String.join("; ", problems) + ".");
            }
        } else if (blank(in == null ? null : in.reason()) == null) {
            throw ApiException.validation("reason", "Say why the supplier is no longer eligible.");
        }
        boolean was = s.isEligible();
        s.setEligible(to);
        suppliers.flush();
        audit.record("updated", TYPE, s.getId(), s.getName() + (to ? " marked eligible" : " marked ineligible: " + in.reason()), Map.of("is_eligible", was), Map.of("is_eligible", to));
        return view(s);
    }

    // ───────────────────────── compliance documents ─────────────────────────

    public record DocumentInput(String documentType, String referenceNumber, LocalDate issuedDate, LocalDate expiryDate, String remarks) {
    }

    @PostMapping("/{id}/compliance")
    @PreAuthorize("@perm.any('edit_suppliers', 'create_suppliers')")
    @Transactional
    public ResponseEntity<SupplierView> storeDocument(@PathVariable Long id, @RequestBody DocumentInput in) {
        Supplier s = lock(id);
        ComplianceDocument d = new ComplianceDocument(s);
        applyDocument(d, in);
        s.getDocuments().add(d);
        suppliers.flush();
        audit.record("compliance_added", TYPE, s.getId(), d.getDocumentType() + " recorded for " + s.getName() + (d.getExpiryDate() == null ? "" : ", valid to " + d.getExpiryDate()),
                null, Map.of("document_type", d.getDocumentType()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(s));
    }

    @PutMapping("/compliance/{documentId}")
    @PreAuthorize("@perm.any('edit_suppliers', 'create_suppliers')")
    @Transactional
    public SupplierView updateDocument(@PathVariable Long documentId, @RequestBody DocumentInput in) {
        Supplier s = lock(suppliers.supplierOfDocument(documentId).orElseThrow(() -> ApiException.notFound("Document")));
        ComplianceDocument d = s.getDocuments().stream().filter(x -> x.getId().equals(documentId)).findFirst().orElseThrow();
        applyDocument(d, in);
        keepEligibilityHonest(s, "a document was changed");
        suppliers.flush();
        audit.record("compliance_updated", TYPE, s.getId(), d.getDocumentType() + " updated for " + s.getName(), null, Map.of("document_type", d.getDocumentType()));
        return view(s);
    }

    @DeleteMapping("/compliance/{documentId}")
    @PreAuthorize("@perm.any('edit_suppliers')")
    @Transactional
    public SupplierView destroyDocument(@PathVariable Long documentId) {
        Supplier s = lock(suppliers.supplierOfDocument(documentId).orElseThrow(() -> ApiException.notFound("Document")));
        ComplianceDocument d = s.getDocuments().stream().filter(x -> x.getId().equals(documentId)).findFirst().orElseThrow();
        s.getDocuments().remove(d);
        keepEligibilityHonest(s, d.getDocumentType() + " was removed");
        suppliers.flush();
        audit.record("compliance_removed", TYPE, s.getId(), d.getDocumentType() + " removed for " + s.getName(), Map.of("document_type", d.getDocumentType()), null);
        return view(s);
    }

    /** An eligible supplier whose documents no longer qualify it loses eligibility. */
    private void keepEligibilityHonest(Supplier s, String why) {
        if (s.isEligible() && !readiness(s, LocalDate.now(Times.ZONE)).ready()) {
            s.setEligible(false);
            audit.record("updated", TYPE, s.getId(), s.getName() + " no longer eligible: " + why, Map.of("is_eligible", true), Map.of("is_eligible", false));
        }
    }

    private void applyDocument(ComplianceDocument d, DocumentInput in) {
        if (in.documentType() == null || !ComplianceDocument.TYPES.contains(in.documentType())) throw ApiException.validation("document_type", "Choose the kind of document.");
        if (in.issuedDate() != null && in.issuedDate().isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("issued_date", "The issue date cannot be in the future.");
        if (in.issuedDate() != null && in.expiryDate() != null && in.expiryDate().isBefore(in.issuedDate())) throw ApiException.validation("expiry_date", "It expires before it was issued.");
        d.setDocumentType(in.documentType());
        d.setReferenceNumber(blank(in.referenceNumber()));
        d.setIssuedDate(in.issuedDate());
        d.setExpiryDate(in.expiryDate());
        d.setRemarks(blank(in.remarks()));
    }

    /** Each required document (or one of its alternatives) on file and not expired. */
    private static Readiness readiness(Supplier s, LocalDate today) {
        List<Requirement> items = new ArrayList<>();
        List<String> missing = new ArrayList<>(), expired = new ArrayList<>();
        for (List<String> alternatives : Supplier.REQUIRED_DOCUMENTS) {
            String label = String.join(" / ", alternatives);
            List<ComplianceDocument> matches = s.getDocuments().stream().filter(d -> alternatives.contains(d.getDocumentType())).toList();
            ComplianceDocument best = matches.stream().filter(d -> d.validOn(today)).findFirst()
                    .orElse(matches.stream().max(Comparator.comparing(d -> d.getExpiryDate() == null ? LocalDate.MAX : d.getExpiryDate())).orElse(null));
            boolean valid = best != null && best.validOn(today);
            items.add(new Requirement(label, best != null, valid, best == null ? null : best.getExpiryDate()));
            if (best == null) missing.add(label);
            else if (!valid) expired.add(label);
        }
        return new Readiness(items, missing, expired, missing.isEmpty() && expired.isEmpty());
    }

    // ───────────────────────── sanctions ─────────────────────────

    public record SanctionInput(String sanctionType, String grounds, String referenceNumber, LocalDate startDate, LocalDate endDate, String liftedReason) {
    }

    @PostMapping("/{id}/blacklist")
    @PreAuthorize("@perm.any('edit_suppliers')")
    @Transactional
    public ResponseEntity<SupplierView> sanction(@PathVariable Long id, @RequestBody SanctionInput in) {
        Supplier s = lock(id);
        if (in.sanctionType() == null || !SupplierSanction.TYPES.contains(in.sanctionType())) throw ApiException.validation("sanction_type", "Blacklisting or suspension?");
        String grounds = blank(in.grounds());
        if (grounds == null) throw ApiException.validation("grounds", "State the grounds.");
        LocalDate start = in.startDate() == null ? LocalDate.now(Times.ZONE) : in.startDate();
        if (in.endDate() != null && in.endDate().isBefore(start)) throw ApiException.validation("end_date", "The sanction cannot end before it starts.");
        if ("Suspension".equals(in.sanctionType()) && in.endDate() == null) throw ApiException.validation("end_date", "A suspension runs for a set period; give its end date.");
        SupplierSanction x = new SupplierSanction(s, current.user().getId());
        x.setSanctionType(in.sanctionType());
        x.setGrounds(grounds);
        x.setReferenceNumber(blank(in.referenceNumber()));
        x.setStartDate(start);
        x.setEndDate(in.endDate());
        s.getSanctions().add(x);
        boolean was = s.isEligible();
        s.setEligible(false);
        suppliers.flush();
        audit.record("blacklisted", TYPE, s.getId(), in.sanctionType() + " of " + s.getName() + " from " + start + (in.endDate() == null ? "" : " to " + in.endDate()) + ": " + grounds,
                Map.of("is_eligible", was), Map.of("is_eligible", false, "sanction", in.sanctionType()));
        notifications.notifyPermission(List.of("edit_procurement", "manage_bidding", "create_rfq", "process_award"), current.user().getId(), "supplier_sanctioned",
                "Supplier " + in.sanctionType().toLowerCase() + "ed", s.getName() + " is under " + in.sanctionType().toLowerCase() + " from " + start + ": " + grounds, TYPE, s.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(s));
    }

    /** Lifts a sanction; the supplier stays ineligible until it is marked eligible again. */
    @PutMapping("/blacklist/{sanctionId}/lift")
    @PreAuthorize("@perm.any('edit_suppliers')")
    @Transactional
    public SupplierView lift(@PathVariable Long sanctionId, @RequestBody SanctionInput in) {
        Supplier s = lock(suppliers.supplierOfSanction(sanctionId).orElseThrow(() -> ApiException.notFound("Sanction")));
        SupplierSanction x = s.getSanctions().stream().filter(y -> y.getId().equals(sanctionId)).findFirst().orElseThrow();
        if (!"active".equals(x.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This sanction was already lifted.");
        String why = in == null ? null : blank(in.liftedReason());
        if (why == null) throw ApiException.validation("lifted_reason", "State why the sanction is lifted (e.g. order of the HOPE, reconsideration granted).");
        x.setStatus("lifted");
        x.setLiftedAt(LocalDate.now(Times.ZONE));
        x.setLiftedReason(why);
        suppliers.flush();
        audit.record("blacklist_lifted", TYPE, s.getId(), x.getSanctionType() + " of " + s.getName() + " lifted: " + why, Map.of("status", "active"), Map.of("status", "lifted"));
        return view(s);
    }

    // ───────────────────────── evaluations ─────────────────────────

    public record EvaluationInput(LocalDate evaluationDate, Integer deliveryRating, Integer qualityRating, Integer timelinessRating, Integer complianceRating,
                                  Integer afterSalesRating, String reference, String remarks) {
    }

    @PostMapping("/{id}/evaluations")
    @PreAuthorize(RATE)
    @Transactional
    public ResponseEntity<SupplierView> storeEvaluation(@PathVariable Long id, @RequestBody EvaluationInput in) {
        Supplier s = lock(id);
        LocalDate date = in.evaluationDate() == null ? LocalDate.now(Times.ZONE) : in.evaluationDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("evaluation_date", "The evaluation date cannot be in the future.");
        rating("delivery_rating", in.deliveryRating(), true);
        rating("quality_rating", in.qualityRating(), true);
        rating("timeliness_rating", in.timelinessRating(), false);
        rating("compliance_rating", in.complianceRating(), false);
        rating("after_sales_rating", in.afterSalesRating(), false);
        String ref = blank(in.reference());
        if (ref != null && s.getEvaluations().stream().anyMatch(e -> ref.equalsIgnoreCase(e.getReference()) && current.user().getId().equals(e.getEvaluatedBy()))) {
            throw ApiException.validation("reference", "You have already rated " + s.getName() + " on " + ref + ".");
        }
        SupplierEvaluation e = new SupplierEvaluation(s, current.user().getId());
        e.setEvaluationDate(date);
        e.setDeliveryRating(in.deliveryRating());
        e.setQualityRating(in.qualityRating());
        e.setTimelinessRating(in.timelinessRating());
        e.setComplianceRating(in.complianceRating());
        e.setAfterSalesRating(in.afterSalesRating());
        List<Integer> given = Stream.of(in.deliveryRating(), in.qualityRating(), in.timelinessRating(), in.complianceRating(), in.afterSalesRating()).filter(Objects::nonNull).toList();
        e.setOverallRating(BigDecimal.valueOf(given.stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(given.size()), 2, RoundingMode.HALF_UP));
        e.setReference(ref);
        e.setRemarks(blank(in.remarks()));
        s.getEvaluations().add(e);
        suppliers.flush();
        audit.record("evaluated", TYPE, s.getId(), s.getName() + " rated " + e.getOverallRating() + "/5" + (ref == null ? "" : " on " + ref), null, Map.of("overall_rating", e.getOverallRating()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(s));
    }

    /** An evaluator can withdraw their own rating. */
    @DeleteMapping("/evaluations/{evaluationId}")
    @PreAuthorize(RATE)
    @Transactional
    public SupplierView destroyEvaluation(@PathVariable Long evaluationId) {
        Supplier s = lock(suppliers.supplierOfEvaluation(evaluationId).orElseThrow(() -> ApiException.notFound("Evaluation")));
        SupplierEvaluation e = s.getEvaluations().stream().filter(x -> x.getId().equals(evaluationId)).findFirst().orElseThrow();
        User me = current.user();
        if (!me.getId().equals(e.getEvaluatedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("Only the evaluator can withdraw a rating.");
        s.getEvaluations().remove(e);
        suppliers.flush();
        audit.record("evaluation_removed", TYPE, s.getId(), "Rating of " + s.getName() + " withdrawn", Map.of("overall_rating", e.getOverallRating()), null);
        return view(s);
    }

    private static void rating(String field, Integer v, boolean required) {
        if (v == null) {
            if (required) throw ApiException.validation(field, "Rate it from 1 to 5.");
            return;
        }
        if (v < 1 || v > 5) throw ApiException.validation(field, "Ratings run from 1 to 5.");
    }

    // ───────────────────────── helpers ─────────────────────────

    private void apply(Supplier s, SupplierInput in) {
        if (in.name() != null || s.getName() == null) {
            String name = blank(in.name());
            if (name == null || name.length() > 255) throw ApiException.validation("name", "Enter the supplier's registered name.");
            if (suppliers.nameTaken(name, s.getId())) throw ApiException.validation("name", "A supplier named " + name + " is already registered.");
            s.setName(name);
        }
        if (in.vendorCode() != null) {
            String code = blank(in.vendorCode());
            if (code != null && suppliers.codeTaken(code, s.getId())) throw ApiException.validation("vendor_code", "Vendor code " + code + " is taken.");
            if (code != null) s.setVendorCode(code);
        }
        if (in.email() != null) {
            String email = blank(in.email());
            if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw ApiException.validation("email", "Enter a valid email address.");
            if (email != null && suppliers.emailTaken(email, s.getId())) throw ApiException.validation("email", "Another supplier uses " + email + ".");
            s.setEmail(email);
        }
        if (in.tin() != null) {
            String tin = blank(in.tin());
            if (tin != null && !tin.matches("^\\d{3}-?\\d{3}-?\\d{3}(-?\\d{3,5})?$")) throw ApiException.validation("tin", "TIN is 9 digits plus the branch code, e.g. 123-456-789-000.");
            s.setTin(tin);
        }
        if (in.website() != null) {
            String w = blank(in.website());
            if (w != null && !w.matches("^https?://\\S+$")) throw ApiException.validation("website", "Enter the website as https://…");
            s.setWebsite(w);
        }
        if (in.philgepsRegNumber() != null) s.setPhilgepsRegNumber(blank(in.philgepsRegNumber()));
        if (in.contactPerson() != null) s.setContactPerson(blank(in.contactPerson()));
        if (in.phone() != null) s.setPhone(blank(in.phone()));
        if (in.mobileNumber() != null) s.setMobileNumber(blank(in.mobileNumber()));
        if (in.address() != null) s.setAddress(blank(in.address()));
        if (in.creditTerms() != null) s.setCreditTerms(blank(in.creditTerms()));
        if (in.notes() != null) s.setNotes(blank(in.notes()));
        if (in.isMsme() != null) s.setMsme(in.isMsme());
        if (in.isActive() != null) {
            s.setActive(in.isActive());
            if (!in.isActive()) s.setEligible(false);
        }
    }

    private Performance performance(Supplier s) {
        List<SupplierEvaluation> ev = s.getEvaluations();
        BigDecimal avgOverall = avg(ev.stream().map(SupplierEvaluation::getOverallRating).toList());
        BigDecimal avgDelivery = avg(ev.stream().map(e -> BigDecimal.valueOf(e.getDeliveryRating())).toList());
        BigDecimal avgQuality = avg(ev.stream().map(e -> BigDecimal.valueOf(e.getQualityRating())).toList());
        Map<String, Object> po = jdbc.queryForMap("select count(*) n, coalesce(sum(total_amount), 0) v from purchase_orders where supplier_id = ? and deleted_at is null and status <> 'cancelled'", s.getId());
        Map<String, Object> ct = jdbc.queryForMap("select count(*) n, coalesce(sum(amount), 0) v, coalesce(sum(ld_amount), 0) ld from contracts where supplier_id = ? and deleted_at is null", s.getId());
        Integer rfqs = jdbc.queryForObject("select count(*) from rfqs where awarded_supplier_id = ? and deleted_at is null and status = 'awarded'", Integer.class, s.getId());
        Integer bids = jdbc.queryForObject("select count(*) from biddings where awarded_supplier_id = ? and deleted_at is null and status = 'awarded'", Integer.class, s.getId());
        Map<String, Object> dl = jdbc.queryForMap("""
                select coalesce(sum(case when r.acceptance_status in ('accepted', 'partially_accepted') then 1 else 0 end), 0) delivered,
                       coalesce(sum(case when r.acceptance_status in ('accepted', 'partially_accepted') and po.delivery_date is not null
                                          and r.delivery_date <= po.delivery_date then 1 else 0 end), 0) on_time,
                       coalesce(sum(case when r.acceptance_status = 'rejected' then 1 else 0 end), 0) rejected,
                       coalesce(sum(case when r.acceptance_status <> 'pending' then r.total_items_received end), 0) units,
                       coalesce(sum(case when r.acceptance_status <> 'pending' then r.total_items_rejected end), 0)
                         + coalesce(sum(case when r.acceptance_status = 'rejected' then r.total_items_accepted end), 0) units_rejected
                from inspection_acceptance_reports r join purchase_orders po on po.id = r.purchase_order_id where po.supplier_id = ?""", s.getId());
        int delivered = ((Number) dl.get("delivered")).intValue(), onTime = ((Number) dl.get("on_time")).intValue();
        int units = ((Number) dl.get("units")).intValue(), unitsRejected = ((Number) dl.get("units_rejected")).intValue();
        return new Performance(ev.size(), avgOverall, avgDelivery, avgQuality, ev.isEmpty() ? null : avgOverall.compareTo(BigDecimal.valueOf(3)) >= 0,
                ((Number) po.get("n")).intValue(), (BigDecimal) po.get("v"), ((Number) ct.get("n")).intValue(), (BigDecimal) ct.get("v"),
                rfqs == null ? 0 : rfqs, bids == null ? 0 : bids, delivered, onTime, rate(onTime, delivered), units, unitsRejected, rate(unitsRejected, units),
                ((Number) dl.get("rejected")).intValue(), (BigDecimal) ct.get("ld"));
    }

    private static BigDecimal avg(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(int part, int whole) {
        return whole == 0 ? null : BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private Supplier lock(Long id) {
        return suppliers.lockById(id).orElseThrow(() -> ApiException.notFound("Supplier"));
    }

    private SupplierView view(Supplier s) {
        return views(List.of(s), true).get(s);
    }

    private Map<Supplier, SupplierView> views(Collection<Supplier> list, boolean full) {
        Map<Long, String> names = new HashMap<>();
        if (full) users.findAllById(list.stream().flatMap(s -> Stream.concat(s.getSanctions().stream().map(SupplierSanction::getCreatedBy),
                s.getEvaluations().stream().map(SupplierEvaluation::getEvaluatedBy))).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getId(), u.getName()));
        LocalDate today = LocalDate.now(Times.ZONE);
        User me = current.user();
        boolean editor = current.any("edit_suppliers");
        boolean deleter = current.any("delete_suppliers");
        Map<Supplier, SupplierView> out = new LinkedHashMap<>();
        for (Supplier s : list) {
            SupplierSanction sanction = s.activeSanction(today);
            Readiness r = readiness(s, today);
            int expiring = (int) s.getDocuments().stream().filter(d -> d.getExpiryDate() != null && !d.getExpiryDate().isBefore(today) && !d.getExpiryDate().isAfter(today.plusDays(30))).count();
            out.put(s, new SupplierView(s.getId(), s.getVendorCode(), s.getName(), s.getTin(), s.getPhilgepsRegNumber(), s.getContactPerson(), s.getEmail(), s.getPhone(),
                    s.getMobileNumber(), s.getAddress(), s.getWebsite(), s.getCreditTerms(), s.getNotes(), s.isActive(), s.isEligible(), s.isMsme(), sanction != null,
                    sanction == null ? null : sanction.getSanctionType() + (sanction.getEndDate() == null ? "" : " until " + sanction.getEndDate()),
                    s.isActive() && s.isEligible() && sanction == null, r, expiring,
                    !full ? null : s.getDocuments().stream().map(d -> new DocumentView(d.getId(), d.getDocumentType(), d.getReferenceNumber(), d.getIssuedDate(),
                            d.getExpiryDate(), !d.validOn(today) ? "expired" : d.getExpiryDate() != null && !d.getExpiryDate().isAfter(today.plusDays(30)) ? "expiring" : "valid",
                            d.getExpiryDate() == null ? null : (int) ChronoUnit.DAYS.between(today, d.getExpiryDate()), d.getRemarks())).toList(),
                    !full ? null : s.getSanctions().stream().map(x -> new SanctionView(x.getId(), x.getSanctionType(), x.getGrounds(), x.getReferenceNumber(), x.getStartDate(),
                            x.getEndDate(), x.getStatus(), x.inForce(today), x.getLiftedAt(), x.getLiftedReason(),
                            x.getCreatedBy() == null ? null : new Ref(x.getCreatedBy(), names.get(x.getCreatedBy())))).toList(),
                    !full ? null : s.getEvaluations().stream().map(e -> new EvaluationView(e.getId(), e.getEvaluationDate(), e.getDeliveryRating(), e.getQualityRating(),
                            e.getTimelinessRating(), e.getComplianceRating(), e.getAfterSalesRating(), e.getOverallRating(), e.getReference(), e.getRemarks(),
                            e.getEvaluatedBy() == null ? null : new Ref(e.getEvaluatedBy(), names.get(e.getEvaluatedBy())),
                            me.isSuperAdmin() || me.getId().equals(e.getEvaluatedBy()))).toList(),
                    full ? performance(s) : null, editor, deleter, Times.toOffset(s.getCreatedAt())));
        }
        return out;
    }

    private static Map<String, Object> snapshot(Supplier s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.getName());
        m.put("vendor_code", s.getVendorCode());
        m.put("tin", s.getTin());
        m.put("is_active", s.isActive());
        m.put("is_eligible", s.isEligible());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

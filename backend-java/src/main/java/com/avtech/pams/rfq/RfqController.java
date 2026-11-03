package com.avtech.pams.rfq;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.bac.BacResolution;
import com.avtech.pams.bac.BacResolutionRepository;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.procurement.AwardSupport;
import com.avtech.pams.procurement.PrLineUsage;
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
import java.time.LocalDateTime;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Requests for quotation under the alternative modes (Shopping, SVP, negotiated, direct
 * contracting, …). An RFQ draws its lines from an approved purchase request, within the PR line's
 * quantity and estimated cost, under the mode the PR planned. Quotations are recorded until the
 * RFQ closes; the award goes to the lowest responsive quotation within the ABC, on an approved
 * BAC resolution, followed by the Notice of Award and Notice to Proceed.
 */
@RestController
@RequestMapping("/api/rfq")
public class RfqController {

    private static final String TYPE = "App\\Models\\Rfq";
    private static final String READ = "@perm.any('view_rfq', 'create_rfq', 'generate_abstract', 'process_award')";
    /** Quotations needed before an award without a recorded justification (SVP, …). */
    private static final int MIN_QUOTATIONS = 3;

    private final RfqRepository rfqs;
    private final BacResolutionRepository resolutions;
    private final UserRepository users;
    private final ProcurementRules rules;
    private final PrLineUsage usage;
    private final AwardSupport awards;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final Db db;

    public RfqController(RfqRepository rfqs, BacResolutionRepository resolutions, UserRepository users, ProcurementRules rules, PrLineUsage usage, AwardSupport awards,
                         DocumentNumbers numbers, NotificationService notifications, AuditService audit, CurrentUser current, JdbcTemplate jdbc, Db db) {
        this.rfqs = rfqs;
        this.resolutions = resolutions;
        this.users = users;
        this.rules = rules;
        this.usage = usage;
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

    public record LineView(Long id, Long purchaseRequestItemId, String generalDescription, String specifications, String unit, int quantity,
                           BigDecimal abcUnitCost, BigDecimal abcTotal, String deliveryLeadTime) {
    }

    public record QuoteLineView(Long rfqItemId, BigDecimal unitPrice, BigDecimal totalPrice, boolean overAbc) {
    }

    public record QuotationView(Long id, Ref supplier, String quotationNumber, LocalDate submittedAt, BigDecimal totalAmount, boolean isResponsive,
                                boolean isWinner, boolean overAbc, boolean supplierEligible, String remarks, List<QuoteLineView> lines) {
    }

    public record RfqView(Long id, String rfqNumber, String title, String procurementMethod, boolean singleSource, String status, BigDecimal abc,
                          OffsetDateTime deadline, boolean deadlinePassed, String deliveryTerm, Integer deliveryLeadDays, String description,
                          String justification, String negotiatedGround, List<String> invitedSuppliers, Ref purchaseRequest, Ref department, Ref preparer, Ref awardedSupplier, OffsetDateTime awardedAt,
                          Ref bacResolution, String remarks, String noaNumber, LocalDate noaDate, String ntpNumber, LocalDate ntpDate,
                          int itemsCount, int quotationsCount, List<LineView> items, List<QuotationView> quotations,
                          boolean canEdit, boolean canIssue, boolean canClose, boolean canCancel, boolean canDelete, boolean canQuote,
                          boolean canAward, boolean canNoa, boolean canNtp, OffsetDateTime createdAt) {
    }

    // ───────────────────────── reads ─────────────────────────

    @GetMapping
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<RfqView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                       @RequestParam(name = "procurement_method", required = false) String method,
                                       @RequestParam(name = "purchase_request_id", required = false) Long pr,
                                       @RequestParam(required = false) String search) {
        Page<Rfq> result = rfqs.search(blank(status), blank(method), pr, blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<Rfq, RfqView> v = views(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public RfqView show(@PathVariable Long id) {
        Rfq r = rfqs.findLive(id).orElseThrow(() -> ApiException.notFound("RFQ"));
        return views(List.of(r), true).get(r);
    }

    public record PrLineOption(Long id, String generalDescription, String specifications, String unit, int quantity, int remainingQuantity,
                               BigDecimal estimatedUnitCost, String procurementMode) {
    }

    public record PrOption(Long id, String prNumber, String purpose, String department, BigDecimal totalAmount, List<String> modes,
                           List<PrLineOption> lines) {
    }

    /** Approved purchase requests with lines not yet on an RFQ, for the new-RFQ form. */
    @GetMapping("/purchase-requests")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional(readOnly = true)
    public Map<String, Object> prOptions() {
        Map<Long, Integer> taken = usage.taken(null, null, null);
        Map<Long, PrOption> byPr = new LinkedHashMap<>();
        jdbc.query("""
                select pr.id, pr.pr_number, pr.purpose, d.name, pr.total_amount,
                       pri.id, pri.general_description, pri.specifications, pri.unit, pri.quantity, pri.estimated_unit_cost, pri.procurement_mode
                from purchase_requests pr join departments d on d.id = pr.department_id
                join purchase_request_items pri on pri.purchase_request_id = pr.id
                where pr.status = 'approved' and pr.deleted_at is null
                order by pr.id desc, pri.id""", rs -> {
            int left = rs.getInt(10) - taken.getOrDefault(rs.getLong(6), 0);
            if (left <= 0) return;
            PrOption o = byPr.computeIfAbsent(rs.getLong(1), k -> {
                try {
                    return new PrOption(k, rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), new ArrayList<>(), new ArrayList<>());
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            String mode = rs.getString(12);
            if (mode != null && !o.modes().contains(mode)) o.modes().add(mode);
            o.lines().add(new PrLineOption(rs.getLong(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getInt(10), left, rs.getBigDecimal(11), mode));
        });
        return Map.of("data", byPr.values(), "methods", Rfq.METHODS, "single_source", Rfq.SINGLE_SOURCE, "negotiated_grounds", Rfq.NEGOTIATED_GROUNDS);
    }

    public record AbstractRow(Long rfqItemId, String generalDescription, String unit, int quantity, BigDecimal abcUnitCost,
                              Map<Long, BigDecimal> prices, BigDecimal lowestUnitPrice) {
    }

    public record AbstractView(RfqView rfq, List<QuotationView> suppliers, List<AbstractRow> rows, QuotationView recommended,
                               int quotationCount, boolean singleSource, boolean insufficientQuotations) {
    }

    /** Abstract of Quotations: every line priced by every supplier, and the lowest responsive quotation within the ABC. */
    @GetMapping("/{id}/abstract")
    @PreAuthorize("@perm.any('generate_abstract', 'view_rfq', 'create_rfq', 'process_award')")
    @Transactional(readOnly = true)
    public AbstractView abstractOf(@PathVariable Long id) {
        Rfq r = rfqs.findLive(id).orElseThrow(() -> ApiException.notFound("RFQ"));
        RfqView v = views(List.of(r), true).get(r);
        List<AbstractRow> rows = r.getItems().stream().map(it -> {
            Map<Long, BigDecimal> prices = new LinkedHashMap<>();
            BigDecimal lowest = null;
            for (RfqQuotation q : r.getQuotations()) {
                BigDecimal p = q.getItems().stream().filter(x -> x.getRfqItemId().equals(it.getId())).map(RfqQuotationItem::getUnitPrice).findFirst().orElse(null);
                prices.put(q.getId(), p);
                if (p != null && (lowest == null || p.compareTo(lowest) < 0)) lowest = p;
            }
            return new AbstractRow(it.getId(), it.getGeneralDescription(), it.getUnit(), it.getQuantity(), it.getAbcUnitCost(), prices, lowest);
        }).toList();
        QuotationView best = v.quotations().stream().filter(this::awardable).min((a, b) -> a.totalAmount().compareTo(b.totalAmount())).orElse(null);
        return new AbstractView(v, v.quotations(), rows, best, v.quotations().size(), r.isSingleSource(),
                !r.isSingleSource() && v.quotations().size() < MIN_QUOTATIONS);
    }

    // ───────────────────────── drafting ─────────────────────────

    public record ItemInput(Long purchaseRequestItemId, Integer quantity, BigDecimal abcUnitCost, String specifications, String deliveryLeadTime) {
    }

    public record RfqInput(String title, String procurementMethod, Long purchaseRequestId, LocalDateTime deadline, String deliveryTerm,
                           Integer deliveryLeadDays, String description, String justification, String negotiatedGround, List<String> invitedSuppliers,
                           List<ItemInput> items) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public ResponseEntity<RfqView> store(@RequestBody RfqInput in) {
        Rfq r = new Rfq(numbers.yearly("RFQ", "rfqs.rfq_number"), current.user().getId());
        apply(r, in, true);
        rfqs.saveAndFlush(r);
        audit.record("created", TYPE, r.getId(), "Drafted RFQ " + r.getRfqNumber() + " (" + r.getProcurementMethod() + ")", null, snapshot(r));
        return ResponseEntity.status(HttpStatus.CREATED).body(views(List.of(r), true).get(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public RfqView update(@PathVariable Long id, @RequestBody RfqInput in) {
        Rfq r = lock(id);
        if (!Rfq.DRAFT.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only draft RFQs can be edited.");
        if (in.purchaseRequestId() != null && !in.purchaseRequestId().equals(r.getPurchaseRequestId())) {
            throw ApiException.validation("purchase_request_id", "An RFQ stays with its purchase request; start a new RFQ instead.");
        }
        Map<String, Object> before = snapshot(r);
        apply(r, new RfqInput(in.title(), in.procurementMethod(), r.getPurchaseRequestId(), in.deadline(), in.deliveryTerm(),
                in.deliveryLeadDays(), in.description(), in.justification(), in.negotiatedGround(), in.invitedSuppliers(), in.items()), true);
        rfqs.flush();
        audit.record("updated", TYPE, r.getId(), "Edited RFQ " + r.getRfqNumber(), before, snapshot(r));
        return views(List.of(r), true).get(r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        Rfq r = lock(id);
        if (!Rfq.DRAFT.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only draft RFQs can be deleted; cancel an issued one.");
        r.setDeletedAt(Times.now());
        audit.record("deleted", TYPE, r.getId(), "Deleted RFQ " + r.getRfqNumber(), snapshot(r), null);
        return Map.of("message", "RFQ " + r.getRfqNumber() + " deleted.");
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public RfqView issue(@PathVariable Long id) {
        Rfq r = lock(id);
        if (!Rfq.DRAFT.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only draft RFQs can be issued.");
        if (r.getDeadline() == null) throw ApiException.validation("deadline", "Set the deadline for quotations before issuing.");
        if (!r.getDeadline().isAfter(Times.now())) throw ApiException.validation("deadline", "The deadline for quotations has to be in the future.");
        // Re-check against the PR in case it changed since the draft was saved; the lines themselves stay as they are.
        apply(r, inputOf(r), false);
        r.setStatus(Rfq.ISSUED);
        rfqs.flush();
        audit.record("issued", TYPE, r.getId(), "RFQ " + r.getRfqNumber() + " issued (" + r.getProcurementMethod() + ")", Map.of("status", "draft"), Map.of("status", "issued"));
        return views(List.of(r), true).get(r);
    }

    /** Closes the RFQ for quotations once the deadline has passed; quotations are then opened and compared. */
    @PostMapping("/{id}/close")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public RfqView close(@PathVariable Long id) {
        Rfq r = lock(id);
        if (!Rfq.ISSUED.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only issued RFQs can be closed.");
        if (r.getDeadline() != null && r.getDeadline().isAfter(Times.now())) {
            throw ApiException.of(HttpStatus.CONFLICT, "Quotations are open until " + r.getDeadline().toLocalDate() + " " + r.getDeadline().toLocalTime() + "; close it after the deadline.");
        }
        r.setStatus(Rfq.CLOSED);
        rfqs.flush();
        audit.record("closed", TYPE, r.getId(), "RFQ " + r.getRfqNumber() + " closed with " + r.getQuotations().size() + " quotation(s)", Map.of("status", "issued"), Map.of("status", "closed"));
        notifications.notifyPermission(List.of("process_award"), current.user().getId(), "rfq_closed", "RFQ ready for award",
                r.getRfqNumber() + " closed with " + r.getQuotations().size() + " quotation(s).", TYPE, r.getId());
        return views(List.of(r), true).get(r);
    }

    public record ReasonInput(String remarks) {
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public RfqView cancel(@PathVariable Long id, @RequestBody(required = false) ReasonInput in) {
        Rfq r = lock(id);
        if (Rfq.AWARDED.equals(r.getStatus()) || Rfq.CANCELLED.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This RFQ can no longer be cancelled.");
        String why = in == null ? null : text(in.remarks());
        if (why == null && !Rfq.DRAFT.equals(r.getStatus())) throw ApiException.validation("remarks", "Say why an issued RFQ is cancelled (e.g. failure of quotations).");
        String was = r.getStatus();
        r.setStatus(Rfq.CANCELLED);
        if (why != null) r.setRemarks(why);
        rfqs.flush();
        audit.record("cancelled", TYPE, r.getId(), "RFQ " + r.getRfqNumber() + " cancelled" + (why == null ? "" : ": " + why), Map.of("status", was), Map.of("status", "cancelled"));
        return views(List.of(r), true).get(r);
    }

    // ───────────────────────── quotations ─────────────────────────

    public record QuoteLineInput(Long rfqItemId, BigDecimal unitPrice, String remarks) {
    }

    public record QuotationInput(Long supplierId, String supplierName, String quotationNumber, LocalDate submittedAt, String remarks, List<QuoteLineInput> items) {
    }

    @PostMapping("/{id}/quotations")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public ResponseEntity<RfqView> storeQuotation(@PathVariable Long id, @RequestBody QuotationInput in) {
        Rfq r = lock(id);
        if (!r.isOpenForQuotes()) throw ApiException.of(HttpStatus.CONFLICT, "Quotations are recorded for issued or closed RFQs only.");
        if (r.isSingleSource() && !r.getQuotations().isEmpty()) {
            throw ApiException.of(HttpStatus.CONFLICT, r.getProcurementMethod() + " is a single-source method; this RFQ already has its quotation.");
        }
        Long supplierId = awards.supplier(in.supplierId(), in.supplierName());
        if (r.getQuotations().stream().anyMatch(q -> q.getSupplierId().equals(supplierId))) {
            throw ApiException.validation("supplier_id", "This supplier already has a quotation on this RFQ.");
        }
        LocalDate submitted = in.submittedAt() == null ? LocalDate.now(Times.ZONE) : in.submittedAt();
        if (submitted.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("submitted_at", "The submission date cannot be in the future.");
        if (r.getDeadline() != null && submitted.isAfter(r.getDeadline().toLocalDate())) {
            throw ApiException.validation("submitted_at", "Submitted after the " + r.getDeadline().toLocalDate() + " deadline; late quotations are not accepted.");
        }
        Map<Long, RfqItem> lines = r.getItems().stream().collect(Collectors.toMap(RfqItem::getId, x -> x));
        Map<Long, QuoteLineInput> priced = new HashMap<>();
        for (QuoteLineInput l : in.items() == null ? List.<QuoteLineInput>of() : in.items()) {
            if (l == null || l.rfqItemId() == null || !lines.containsKey(l.rfqItemId())) throw ApiException.validation("items", "A quoted line is not part of this RFQ.");
            if (l.unitPrice() == null || l.unitPrice().signum() < 0 || l.unitPrice().scale() > 2) {
                throw ApiException.validation("items", "Enter a unit price in pesos for \"" + lines.get(l.rfqItemId()).getGeneralDescription() + "\".");
            }
            priced.put(l.rfqItemId(), l);
        }
        List<String> missing = r.getItems().stream().filter(it -> !priced.containsKey(it.getId())).map(RfqItem::getGeneralDescription).toList();
        if (!missing.isEmpty()) throw ApiException.validation("items", "A quotation prices every line; missing: " + String.join(", ", missing) + ".");
        RfqQuotation q = new RfqQuotation(r, supplierId, text(in.quotationNumber()), submitted, text(in.remarks()));
        for (RfqItem it : r.getItems()) {
            QuoteLineInput l = priced.get(it.getId());
            BigDecimal unit = l.unitPrice().setScale(2, RoundingMode.HALF_UP);
            q.getItems().add(new RfqQuotationItem(q, it.getId(), unit, unit.multiply(BigDecimal.valueOf(it.getQuantity())).setScale(2, RoundingMode.HALF_UP), text(l.remarks())));
        }
        q.recalc();
        r.getQuotations().add(q);
        rfqs.flush();
        audit.record("quotation_recorded", TYPE, r.getId(), "Quotation of " + awards.supplierName(supplierId) + " (₱" + ProcurementRules.peso(q.getTotalAmount()) + ") recorded for RFQ " + r.getRfqNumber(),
                null, Map.of("supplier_id", supplierId, "total_amount", q.getTotalAmount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(views(List.of(r), true).get(r));
    }

    public record ResponsiveInput(Boolean isResponsive, String remarks) {
    }

    @PutMapping("/quotations/{quotationId}/responsive")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public RfqView setResponsive(@PathVariable Long quotationId, @RequestBody ResponsiveInput in) {
        RfqQuotation found = rfqs.findQuotation(quotationId).orElseThrow(() -> ApiException.notFound("Quotation"));
        Rfq r = lock(found.getRfq().getId());
        RfqQuotation q = r.getQuotations().stream().filter(x -> x.getId().equals(quotationId)).findFirst().orElseThrow();
        if (!r.isOpenForQuotes()) throw ApiException.of(HttpStatus.CONFLICT, "Quotations can no longer be re-evaluated on this RFQ.");
        boolean responsive = !Boolean.FALSE.equals(in.isResponsive());
        String why = text(in.remarks());
        if (!responsive && why == null) throw ApiException.validation("remarks", "Say why the quotation is not responsive (e.g. specifications not met).");
        q.setResponsive(responsive);
        if (why != null) q.setRemarks(why);
        rfqs.flush();
        audit.record("updated", TYPE, r.getId(), "Quotation of " + awards.supplierName(q.getSupplierId()) + " marked " + (responsive ? "responsive" : "non-responsive: " + why),
                null, Map.of("quotation_id", q.getId(), "is_responsive", responsive));
        return views(List.of(r), true).get(r);
    }

    @DeleteMapping("/quotations/{quotationId}")
    @PreAuthorize("@perm.any('create_rfq')")
    @Transactional
    public RfqView destroyQuotation(@PathVariable Long quotationId) {
        RfqQuotation found = rfqs.findQuotation(quotationId).orElseThrow(() -> ApiException.notFound("Quotation"));
        Rfq r = lock(found.getRfq().getId());
        if (!r.isOpenForQuotes()) throw ApiException.of(HttpStatus.CONFLICT, "Quotations on an awarded or cancelled RFQ are part of the record.");
        RfqQuotation q = r.getQuotations().stream().filter(x -> x.getId().equals(quotationId)).findFirst().orElseThrow();
        r.getQuotations().remove(q);
        rfqs.flush();
        audit.record("updated", TYPE, r.getId(), "Removed the quotation of " + awards.supplierName(q.getSupplierId()) + " from RFQ " + r.getRfqNumber(),
                Map.of("supplier_id", q.getSupplierId(), "total_amount", q.getTotalAmount()), null);
        return views(List.of(r), true).get(r);
    }

    // ───────────────────────── award ─────────────────────────

    public record AwardInput(Long quotationId, Long bacResolutionId, String remarks) {
    }

    @PostMapping("/{id}/award")
    @PreAuthorize("@perm.any('process_award')")
    @Transactional
    public RfqView award(@PathVariable Long id, @RequestBody AwardInput in) {
        Rfq r = lock(id);
        if (!Rfq.CLOSED.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Close the RFQ (after its deadline) before awarding.");
        User me = current.user();
        if (me.getId().equals(r.getPreparedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You prepared this RFQ, so someone else has to award it.");
        RfqQuotation q = r.getQuotations().stream().filter(x -> x.getId().equals(in.quotationId())).findFirst()
                .orElseThrow(() -> ApiException.validation("quotation_id", "Choose a quotation on this RFQ."));
        if (!q.isResponsive()) throw ApiException.validation("quotation_id", "That quotation was found non-responsive.");
        if (!awards.eligible(q.getSupplierId())) throw ApiException.validation("quotation_id", awards.supplierName(q.getSupplierId()) + " is inactive or not eligible.");
        if (r.getAbc().signum() > 0 && q.getTotalAmount().compareTo(r.getAbc()) > 0) {
            throw ApiException.validation("quotation_id", "₱" + ProcurementRules.peso(q.getTotalAmount()) + " exceeds the ABC of ₱" + ProcurementRules.peso(r.getAbc()) + " (RA 12009).");
        }
        BigDecimal lowest = r.getQuotations().stream().filter(x -> x.isResponsive() && awards.eligible(x.getSupplierId())
                        && (r.getAbc().signum() == 0 || x.getTotalAmount().compareTo(r.getAbc()) <= 0))
                .map(RfqQuotation::getTotalAmount).min(BigDecimal::compareTo).orElseThrow();
        if (q.getTotalAmount().compareTo(lowest) > 0) {
            throw ApiException.validation("quotation_id", "The award goes to the lowest responsive quotation (₱" + ProcurementRules.peso(lowest)
                    + "). To pass over a lower one, mark it non-responsive with the reason first.");
        }
        String why = text(in.remarks());
        // Negotiated Procurement proceeds even with one response (IRR 35.1.1): the recorded quotations are the best offers.
        if (!r.isSingleSource() && !r.isNegotiated() && r.getQuotations().size() < MIN_QUOTATIONS && why == null) {
            throw ApiException.validation("remarks", "Only " + r.getQuotations().size() + " quotation(s) were received; at least " + MIN_QUOTATIONS
                    + " are expected. Record the justification for awarding anyway.");
        }
        BacResolution res = awards.resolution(in.bacResolutionId(), r.getPurchaseRequestId(), r.getId(), null);
        r.getQuotations().forEach(x -> x.setWinner(false));
        q.setWinner(true);
        r.setStatus(Rfq.AWARDED);
        r.setAwardedSupplierId(q.getSupplierId());
        r.setAwardedAt(Times.now());
        r.setBacResolutionId(res.getId());
        if (why != null) r.setRemarks(why);
        rfqs.flush();
        audit.record("awarded", TYPE, r.getId(), "RFQ " + r.getRfqNumber() + " awarded to " + awards.supplierName(q.getSupplierId()) + " (₱" + ProcurementRules.peso(q.getTotalAmount())
                + ") on " + res.getResolutionNumber(), Map.of("status", "closed"), Map.of("status", "awarded", "supplier_id", q.getSupplierId(), "amount", q.getTotalAmount()));
        notifications.notifyPermission(List.of("create_rfq", "view_procurement"), me.getId(), "rfq_awarded", "RFQ awarded",
                r.getRfqNumber() + " was awarded to " + awards.supplierName(q.getSupplierId()) + ".", TYPE, r.getId());
        return views(List.of(r), true).get(r);
    }

    public record NoticeInput(LocalDate date) {
    }

    @PostMapping("/{id}/noa")
    @PreAuthorize("@perm.any('process_award')")
    @Transactional
    public RfqView issueNoa(@PathVariable Long id, @RequestBody(required = false) NoticeInput in) {
        Rfq r = lock(id);
        if (!Rfq.AWARDED.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "The Notice of Award follows the award.");
        if (r.getNoaNumber() != null) throw ApiException.of(HttpStatus.CONFLICT, "The Notice of Award " + r.getNoaNumber() + " was already issued.");
        LocalDate date = in == null || in.date() == null ? LocalDate.now(Times.ZONE) : in.date();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("date", "The date cannot be in the future.");
        if (date.isBefore(r.getAwardedAt().toLocalDate())) throw ApiException.validation("date", "The Notice of Award cannot predate the award.");
        r.setNoaNumber(numbers.yearly("NOA", "rfqs.noa_number", "biddings.noa_number"));
        r.setNoaDate(date);
        rfqs.flush();
        audit.record("noa_issued", TYPE, r.getId(), "Notice of Award " + r.getNoaNumber() + " issued for RFQ " + r.getRfqNumber(), null, Map.of("noa_number", r.getNoaNumber()));
        return views(List.of(r), true).get(r);
    }

    @PostMapping("/{id}/ntp")
    @PreAuthorize("@perm.any('process_award')")
    @Transactional
    public RfqView issueNtp(@PathVariable Long id, @RequestBody(required = false) NoticeInput in) {
        Rfq r = lock(id);
        if (r.getNoaNumber() == null) throw ApiException.of(HttpStatus.CONFLICT, "Issue the Notice of Award before the Notice to Proceed.");
        if (r.getNtpNumber() != null) throw ApiException.of(HttpStatus.CONFLICT, "The Notice to Proceed " + r.getNtpNumber() + " was already issued.");
        LocalDate date = in == null || in.date() == null ? LocalDate.now(Times.ZONE) : in.date();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("date", "The date cannot be in the future.");
        if (date.isBefore(r.getNoaDate())) throw ApiException.validation("date", "The Notice to Proceed cannot predate the Notice of Award.");
        r.setNtpNumber(numbers.yearly("NTP", "rfqs.ntp_number", "biddings.ntp_number"));
        r.setNtpDate(date);
        rfqs.flush();
        audit.record("ntp_issued", TYPE, r.getId(), "Notice to Proceed " + r.getNtpNumber() + " issued for RFQ " + r.getRfqNumber(), null, Map.of("ntp_number", r.getNtpNumber()));
        return views(List.of(r), true).get(r);
    }

    // ───────────────────────── helpers ─────────────────────────

    private record PrLine(Long id, String description, String specifications, String unit, int quantity, BigDecimal unitCost, String mode) {
    }

    /** Validates the RFQ against its purchase request; with replace, also writes the fields and lines. */
    private void apply(Rfq r, RfqInput in, boolean replace) {
        String title = text(in.title());
        if (title == null || title.length() > 255) throw ApiException.validation("title", "Give the RFQ a title of up to 255 characters.");
        String method = in.procurementMethod();
        if (ProcurementRules.SHOPPING.equals(method)) throw ApiException.validation("procurement_method", "Shopping is no longer a mode under RA 12009; use Small Value Procurement.");
        if (method == null || !Rfq.METHODS.contains(method)) throw ApiException.validation("procurement_method", "Choose an alternative mode of procurement.");
        boolean negotiated = ProcurementRules.NEGOTIATED.equals(method);
        String ground = negotiated ? text(in.negotiatedGround()) : null;
        if (negotiated && (ground == null || !Rfq.NEGOTIATED_GROUNDS.containsKey(ground))) {
            throw ApiException.validation("negotiated_ground", "Choose the ground for Negotiated Procurement; it is allowed only in the instances of Section 35.");
        }
        if (in.purchaseRequestId() == null) throw ApiException.validation("purchase_request_id", "Choose the approved purchase request this RFQ is for.");
        // Locking the PR serialises RFQs drawing on the same lines.
        List<Map<String, Object>> pr = db.lockRows("purchase_requests", "id = ? and deleted_at is null", in.purchaseRequestId());
        if (pr.isEmpty()) throw ApiException.validation("purchase_request_id", "That purchase request does not exist.");
        if (!"approved".equals(pr.get(0).get("status"))) throw ApiException.validation("purchase_request_id", "Only fully approved purchase requests go to RFQ.");
        Map<Long, PrLine> prLines = new HashMap<>();
        jdbc.query("select id, general_description, specifications, unit, quantity, estimated_unit_cost, procurement_mode from purchase_request_items where purchase_request_id = ?",
                rs -> {
                    prLines.put(rs.getLong(1), new PrLine(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBigDecimal(6), rs.getString(7)));
                }, in.purchaseRequestId());
        if (in.items() == null || in.items().isEmpty()) throw ApiException.validation("items", "Add at least one line from the purchase request.");
        Map<Long, Integer> taken = usage.taken(in.purchaseRequestId(), r.getId(), null);
        // IRR 64.5 and 35.1: after two failed biddings, the bidding lines may go to Negotiated Procurement.
        Integer failedBiddings = jdbc.queryForObject("select count(*) from biddings where purchase_request_id = ? and status = 'failed' and deleted_at is null",
                Integer.class, in.purchaseRequestId());
        boolean twoFailed = failedBiddings != null && failedBiddings >= 2;
        boolean onTwoFailed = Rfq.TWO_FAILED.equals(ground);
        if (onTwoFailed && !twoFailed) {
            throw ApiException.validation("negotiated_ground", "This purchase request has " + (failedBiddings == null ? 0 : failedBiddings) + " failed bidding(s); Negotiated Procurement on this ground needs two failed biddings.");
        }
        // The ABC may rise at most 20% over the last failed bidding (IRR 35.1.1(a)).
        Map<Long, BigDecimal> failedAbc = new HashMap<>();
        if (onTwoFailed) {
            jdbc.query("""
                    select bi.purchase_request_item_id, bi.abc_unit_cost from bidding_items bi join biddings b on b.id = bi.bidding_id
                    where b.purchase_request_id = ? and b.status = 'failed' and b.deleted_at is null order by b.id""",
                    rs -> { failedAbc.put(rs.getLong(1), rs.getBigDecimal(2)); }, in.purchaseRequestId());
        }
        Set<Long> seen = new HashSet<>();
        List<RfqItem> lines = new ArrayList<>();
        for (int i = 0; i < in.items().size(); i++) {
            ItemInput it = in.items().get(i);
            String f = "items." + i + ".";
            PrLine p = it == null || it.purchaseRequestItemId() == null ? null : prLines.get(it.purchaseRequestItemId());
            if (p == null) throw ApiException.validation(f + "purchase_request_item_id", "Line " + (i + 1) + " is not on the purchase request.");
            if (!seen.add(p.id())) throw ApiException.validation(f + "purchase_request_item_id", "\"" + p.description() + "\" is listed twice.");
            boolean afterFailure = onTwoFailed && "Competitive Bidding".equals(p.mode());
            // Common-use supplies come from PS-DBM unless released on a ground of IRR 40.2; SVP and Direct Acquisition both cover those not available from it.
            boolean psOut = ProcurementRules.PS_DBM.equals(p.mode()) && List.of(ProcurementRules.SVP, "Direct Acquisition").contains(method) && usage.releasedFromPs(p.id());
            // A line approved as Shopping before the change is bought by SVP, which took its place.
            boolean wasShopping = ProcurementRules.SHOPPING.equals(p.mode()) && ProcurementRules.SVP.equals(method);
            if (ProcurementRules.PS_DBM.equals(p.mode()) && !psOut) {
                throw ApiException.validation("procurement_method", "\"" + p.description() + "\" is a common-use item for PS-DBM; buy it through a PS-DBM "
                        + "Agency Procurement Request, or record there why it cannot come from PS (then SVP or Direct Acquisition).");
            }
            if (p.mode() != null && !p.mode().equals(method) && !afterFailure && !psOut && !wasShopping) {
                throw ApiException.validation("procurement_method", "\"" + p.description() + "\" was planned under " + p.mode() + "; the RFQ method must match the approved mode (RA 12009)"
                        + ("Competitive Bidding".equals(p.mode()) && "Negotiated Procurement".equals(method) ? ", or choose the ground of two failed biddings once the PR has them." : "."));
            }
            int qty = it.quantity() == null ? p.quantity() - taken.getOrDefault(p.id(), 0) : it.quantity();
            int left = p.quantity() - taken.getOrDefault(p.id(), 0);
            if (qty < 1) throw ApiException.validation(f + "quantity", "Nothing is left of \"" + p.description() + "\"; it is already on another RFQ or bidding.");
            if (qty > left) throw ApiException.validation(f + "quantity", "\"" + p.description() + "\": " + qty + " asked, but only " + left + " of " + p.quantity() + " are not already on another RFQ or bidding.");
            BigDecimal cost = it.abcUnitCost() == null ? p.unitCost() : it.abcUnitCost();
            if (cost.signum() < 0 || cost.scale() > 2) throw ApiException.validation(f + "abc_unit_cost", "Line " + (i + 1) + ": enter the ABC per unit in pesos.");
            if (cost.compareTo(p.unitCost()) > 0) {
                throw ApiException.validation(f + "abc_unit_cost", "\"" + p.description() + "\": the ABC of ₱" + ProcurementRules.peso(cost)
                        + " per unit is above the ₱" + ProcurementRules.peso(p.unitCost()) + " the purchase request was approved for.");
            }
            BigDecimal lastAbc = failedAbc.get(p.id());
            if (lastAbc != null && cost.compareTo(lastAbc.multiply(new BigDecimal("1.20"))) > 0) {
                throw ApiException.validation(f + "abc_unit_cost", "\"" + p.description() + "\": the ABC cannot rise more than 20% over the last failed bidding (₱" + ProcurementRules.peso(lastAbc) + " per unit).");
            }
            String spec = text(it.specifications());
            lines.add(new RfqItem(r, p.id(), p.description(), spec != null ? spec : p.specifications(), p.unit(), qty, cost.setScale(2, RoundingMode.HALF_UP), text(it.deliveryLeadTime())));
        }
        String justification = text(in.justification());
        if (Rfq.SINGLE_SOURCE.contains(method) && justification == null) {
            throw ApiException.validation("justification", method + " is a single or exclusive-source method; record the justification "
                    + "(e.g. exclusive distributorship, proprietary nature, legal ground) as RA 12009 requires.");
        }
        if (negotiated && !onTwoFailed && justification == null) {
            throw ApiException.validation("justification", "Record how this purchase meets the conditions of its Section 35 ground.");
        }
        if (ProcurementRules.UNSOLICITED.equals(method) && justification == null) {
            throw ApiException.validation("justification", "Record the new concept or technology that the HoPE determined the offer involves (IRR 30.1).");
        }
        List<String> invited = in.invitedSuppliers() == null ? List.of() : in.invitedSuppliers().stream().map(RfqController::text).filter(Objects::nonNull).distinct().toList();
        if (onTwoFailed && invited.size() < MIN_QUOTATIONS) {
            throw ApiException.validation("invited_suppliers", "Invite at least " + MIN_QUOTATIONS + " suppliers, including those disqualified in the earlier biddings (IRR 35.1.1).");
        }
        BigDecimal abc = lines.stream().map(RfqItem::getAbcTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        rules.checkCeiling("procurement_method", title, method, abc);
        if (in.deliveryLeadDays() != null && in.deliveryLeadDays() < 0) throw ApiException.validation("delivery_lead_days", "Delivery days cannot be negative.");
        if (!replace) return;
        r.setTitle(title);
        r.setProcurementMethod(method);
        r.setPurchaseRequestId(in.purchaseRequestId());
        r.setDeadline(in.deadline());
        r.setDeliveryTerm(text(in.deliveryTerm()));
        r.setDeliveryLeadDays(in.deliveryLeadDays());
        r.setDescription(text(in.description()));
        r.setJustification(justification);
        r.setNegotiatedGround(ground);
        r.setInvitedSuppliers(invited.isEmpty() ? null : String.join("\n", invited));
        r.getItems().clear();
        rfqs.flush();
        r.getItems().addAll(lines);
        r.recalc();
    }

    private static RfqInput inputOf(Rfq r) {
        return new RfqInput(r.getTitle(), r.getProcurementMethod(), r.getPurchaseRequestId(), r.getDeadline(), r.getDeliveryTerm(), r.getDeliveryLeadDays(),
                r.getDescription(), r.getJustification(), r.getNegotiatedGround(), invitedList(r), r.getItems().stream().map(i -> new ItemInput(i.getPurchaseRequestItemId(), i.getQuantity(),
                i.getAbcUnitCost(), i.getSpecifications(), i.getDeliveryLeadTime())).toList());
    }

    private boolean awardable(QuotationView q) {
        return q.isResponsive() && q.supplierEligible() && !q.overAbc();
    }

    private Rfq lock(Long id) {
        return rfqs.lockById(id).orElseThrow(() -> ApiException.notFound("RFQ"));
    }

    private Map<Rfq, RfqView> views(Collection<Rfq> list, boolean full) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().map(Rfq::getPreparedBy).filter(Objects::nonNull).collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        Set<Long> supplierIds = list.stream().flatMap(r -> Stream.concat(Stream.of(r.getAwardedSupplierId()),
                full ? r.getQuotations().stream().map(RfqQuotation::getSupplierId) : Stream.empty())).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> suppliers = new HashMap<>();
        Map<Long, Boolean> okSupplier = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            jdbc.query("select s.id, s.name from suppliers s where s.id in (" + supplierIds.stream().map(x -> "?").collect(Collectors.joining(",")) + ")", rs -> {
                suppliers.put(rs.getLong(1), rs.getString(2));
            }, supplierIds.toArray());
            // Eligibility through AwardSupport, which owns the (dialect-aware) rule.
            suppliers.keySet().forEach(id -> okSupplier.put(id, awards.eligible(id)));
        }
        Map<Long, String[]> prs = new HashMap<>();
        List<Long> prIds = list.stream().map(Rfq::getPurchaseRequestId).filter(Objects::nonNull).distinct().toList();
        if (!prIds.isEmpty()) {
            jdbc.query("select pr.id, pr.pr_number, d.id, d.name from purchase_requests pr join departments d on d.id = pr.department_id where pr.id in ("
                    + prIds.stream().map(x -> "?").collect(Collectors.joining(",")) + ")", rs -> {
                prs.put(rs.getLong(1), new String[]{rs.getString(2), rs.getString(3), rs.getString(4)});
            }, prIds.toArray());
        }
        Map<Long, String> resNumbers = new HashMap<>();
        resolutions.findAllById(list.stream().map(Rfq::getBacResolutionId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(res -> resNumbers.put(res.getId(), res.getResolutionNumber()));
        User me = current.user();
        boolean maker = current.any("create_rfq");
        boolean awarder = current.any("process_award");
        LocalDateTime now = Times.now();
        Map<Rfq, RfqView> out = new LinkedHashMap<>();
        for (Rfq r : list) {
            Map<Long, RfqItem> byLine = r.getItems().stream().collect(Collectors.toMap(RfqItem::getId, x -> x));
            List<LineView> items = !full ? null : r.getItems().stream().map(i -> new LineView(i.getId(), i.getPurchaseRequestItemId(), i.getGeneralDescription(),
                    i.getSpecifications(), i.getUnit(), i.getQuantity(), i.getAbcUnitCost(), i.getAbcTotal(), i.getDeliveryLeadTime())).toList();
            List<QuotationView> quotes = !full ? null : r.getQuotations().stream().map(q -> new QuotationView(q.getId(), new Ref(q.getSupplierId(), suppliers.get(q.getSupplierId())),
                    q.getQuotationNumber(), q.getSubmittedAt(), q.getTotalAmount(), q.isResponsive(), q.isWinner(),
                    r.getAbc().signum() > 0 && q.getTotalAmount().compareTo(r.getAbc()) > 0, Boolean.TRUE.equals(okSupplier.get(q.getSupplierId())), q.getRemarks(),
                    q.getItems().stream().map(l -> {
                        RfqItem it = byLine.get(l.getRfqItemId());
                        return new QuoteLineView(l.getRfqItemId(), l.getUnitPrice(), l.getTotalPrice(), it != null && l.getUnitPrice().compareTo(it.getAbcUnitCost()) > 0);
                    }).toList())).toList();
            String[] pr = r.getPurchaseRequestId() == null ? null : prs.get(r.getPurchaseRequestId());
            boolean own = me.getId().equals(r.getPreparedBy()) && !me.isSuperAdmin();
            boolean draft = Rfq.DRAFT.equals(r.getStatus());
            boolean pastDeadline = r.getDeadline() != null && !r.getDeadline().isAfter(now);
            out.put(r, new RfqView(r.getId(), r.getRfqNumber(), r.getTitle(), r.getProcurementMethod(), r.isSingleSource(), r.getStatus(), r.getAbc(),
                    Times.toOffset(r.getDeadline()), pastDeadline, r.getDeliveryTerm(), r.getDeliveryLeadDays(), r.getDescription(), r.getJustification(),
                    r.getNegotiatedGround(), invitedList(r),
                    pr == null ? null : new Ref(r.getPurchaseRequestId(), pr[0]), pr == null ? null : new Ref(Long.valueOf(pr[1]), pr[2]),
                    r.getPreparedBy() == null ? null : new Ref(r.getPreparedBy(), names.get(r.getPreparedBy())),
                    r.getAwardedSupplierId() == null ? null : new Ref(r.getAwardedSupplierId(), suppliers.get(r.getAwardedSupplierId())), Times.toOffset(r.getAwardedAt()),
                    r.getBacResolutionId() == null ? null : new Ref(r.getBacResolutionId(), resNumbers.get(r.getBacResolutionId())), r.getRemarks(),
                    r.getNoaNumber(), r.getNoaDate(), r.getNtpNumber(), r.getNtpDate(), r.getItems().size(), r.getQuotations().size(), items, quotes,
                    maker && draft, maker && draft, maker && Rfq.ISSUED.equals(r.getStatus()) && pastDeadline,
                    maker && !Rfq.AWARDED.equals(r.getStatus()) && !Rfq.CANCELLED.equals(r.getStatus()), maker && draft,
                    maker && r.isOpenForQuotes() && !(r.isSingleSource() && !r.getQuotations().isEmpty()),
                    awarder && !own && Rfq.CLOSED.equals(r.getStatus()) && !r.getQuotations().isEmpty(),
                    awarder && Rfq.AWARDED.equals(r.getStatus()) && r.getNoaNumber() == null,
                    awarder && r.getNoaNumber() != null && r.getNtpNumber() == null, Times.toOffset(r.getCreatedAt())));
        }
        return out;
    }

    private static Map<String, Object> snapshot(Rfq r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", r.getTitle());
        m.put("procurement_method", r.getProcurementMethod());
        m.put("status", r.getStatus());
        m.put("abc", r.getAbc());
        m.put("lines", r.getItems().size());
        return m;
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blank(String s) {
        return text(s);
    }

    private static List<String> invitedList(Rfq r) {
        return r.getInvitedSuppliers() == null ? List.of() : List.of(r.getInvitedSuppliers().split("\n"));
    }

}

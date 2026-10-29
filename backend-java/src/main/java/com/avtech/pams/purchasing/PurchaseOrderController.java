package com.avtech.pams.purchasing;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Purchase orders, issued only on an award with its Notice of Award: an RFQ's lines at the winning
 * quotation's prices, or a bidding's lines priced to the awarded bid. Prepared by procurement,
 * approved and sent to the supplier by someone else, then confirmed (the supplier's conformé).
 * Deliveries against it are recorded on IARs.
 */
@RestController
@RequestMapping("/api/procurement")
public class PurchaseOrderController {

    private static final String TYPE = "App\\Models\\PurchaseOrder";
    private static final String READ = "@perm.any('view_procurement', 'approve_procurement', 'edit_procurement', 'create_procurement', 'view_receiving', 'create_receiving', 'accept_delivery')";
    private static final Set<String> STATUSES = Set.of("draft", "sent", "confirmed", "partially_received", "received", "cancelled");

    private final PurchaseOrderRepository orders;
    private final InspectionReportRepository reports;
    private final UserRepository users;
    private final AwardSupport awards;
    private final Deliveries deliveries;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final Db db;

    public PurchaseOrderController(PurchaseOrderRepository orders, InspectionReportRepository reports, UserRepository users, AwardSupport awards,
                                   Deliveries deliveries, DocumentNumbers numbers, NotificationService notifications, AuditService audit,
                                   CurrentUser current, JdbcTemplate jdbc, Db db) {
        this.orders = orders;
        this.reports = reports;
        this.users = users;
        this.awards = awards;
        this.deliveries = deliveries;
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

    /** {@code mode} is the mode of procurement printed on the PO (COA Appendix 61). */
    public record Source(String type, Long id, String number, String noaNumber, String mode) {
    }

    public record LineView(Long id, String description, String specifications, String unit, int quantity, BigDecimal unitCost, BigDecimal totalCost,
                           int delivered, int accepted, int outstanding, Long rfqItemId, Long biddingItemId) {
    }

    public record IarRef(Long id, String iarNumber, String status, String acceptanceStatus, LocalDate deliveryDate) {
    }

    public record PoView(Long id, String poNumber, String status, Ref supplier, Ref purchaseRequest, Ref department, Source source, LocalDate orderDate,
                         LocalDate deliveryDate, boolean late, String deliveryAddress, BigDecimal totalAmount, String remarks, Ref preparer, Ref approver,
                         String fundSource, String cafNumber, int itemsCount, List<LineView> items, List<IarRef> inspections,
                         boolean canEdit, boolean canSend, boolean canConfirm, boolean canCancel, boolean canDelete, boolean canReceive,
                         OffsetDateTime createdAt) {
    }

    // ───────────────────────── award sources ─────────────────────────

    public record SourceLine(Long sourceItemId, String description, String specifications, String unit, int quantity, BigDecimal unitCost) {
    }

    public record AwardSource(String type, Long id, String number, String title, Ref supplier, Ref purchaseRequest, String noaNumber,
                              BigDecimal amount, Integer deliveryLeadDays, String deliveryTerm, boolean pricesFixed, List<SourceLine> lines) {
    }

    /**
     * Awards with a Notice of Award and no live purchase order. RFQ lines carry the winning
     * quotation's prices; bidding lines are pre-priced in proportion to the awarded bid and can
     * be adjusted, as long as they total the awarded amount.
     */
    @GetMapping("/award-sources")
    @PreAuthorize("@perm.any('edit_procurement')")
    @Transactional(readOnly = true)
    public Map<String, Object> awardSources() {
        return Map.of("data", sources());
    }

    private List<AwardSource> sources() {
        List<AwardSource> out = new ArrayList<>();
        jdbc.query("""
                select r.id, r.rfq_number, r.title, r.awarded_supplier_id, s.name, r.purchase_request_id, pr.pr_number, r.noa_number,
                       q.total_amount, r.delivery_lead_days, r.delivery_term, q.id
                from rfqs r join rfq_quotations q on q.rfq_id = r.id and q.is_winner = """ + db.t() + " " + """
                join suppliers s on s.id = r.awarded_supplier_id left join purchase_requests pr on pr.id = r.purchase_request_id
                where r.status = 'awarded' and r.noa_number is not null and r.deleted_at is null
                  and not exists (select 1 from purchase_orders po where po.rfq_id = r.id and po.deleted_at is null and po.status <> 'cancelled')
                order by r.id""", rs -> {
            long quote = rs.getLong(12);
            List<SourceLine> lines = jdbc.query("""
                    select ri.id, ri.general_description, ri.specifications, ri.unit, ri.quantity, qi.unit_price
                    from rfq_items ri join rfq_quotation_items qi on qi.rfq_item_id = ri.id and qi.rfq_quotation_id = ? where ri.rfq_id = ? order by ri.id""",
                    (l, n) -> new SourceLine(l.getLong(1), l.getString(2), l.getString(3), l.getString(4), l.getInt(5), l.getBigDecimal(6)), quote, rs.getLong(1));
            out.add(new AwardSource("rfq", rs.getLong(1), rs.getString(2), rs.getString(3), new Ref(rs.getLong(4), rs.getString(5)),
                    rs.getObject(6) == null ? null : new Ref(rs.getLong(6), rs.getString(7)), rs.getString(8), rs.getBigDecimal(9),
                    (Integer) rs.getObject(10), rs.getString(11), true, lines));
        });
        jdbc.query("""
                select b.id, b.bidding_number, b.title, b.awarded_supplier_id, s.name, b.purchase_request_id, pr.pr_number, b.noa_number,
                       b.awarded_amount, b.delivery_lead_days, b.delivery_term, b.abc
                from biddings b join suppliers s on s.id = b.awarded_supplier_id left join purchase_requests pr on pr.id = b.purchase_request_id
                where b.status = 'awarded' and b.noa_number is not null and b.deleted_at is null
                  and not exists (select 1 from purchase_orders po where po.bidding_id = b.id and po.deleted_at is null and po.status <> 'cancelled')
                order by b.id""", rs -> {
            List<SourceLine> raw = jdbc.query("select id, general_description, specifications, unit, quantity, abc_unit_cost from bidding_items where bidding_id = ? order by id",
                    (l, n) -> new SourceLine(l.getLong(1), l.getString(2), l.getString(3), l.getString(4), l.getInt(5), l.getBigDecimal(6)), rs.getLong(1));
            out.add(new AwardSource("bidding", rs.getLong(1), rs.getString(2), rs.getString(3), new Ref(rs.getLong(4), rs.getString(5)),
                    rs.getObject(6) == null ? null : new Ref(rs.getLong(6), rs.getString(7)), rs.getString(8), rs.getBigDecimal(9),
                    (Integer) rs.getObject(10), rs.getString(11), false, prorate(raw, rs.getBigDecimal(12), rs.getBigDecimal(9))));
        });
        return out;
    }

    /**
     * ABC unit costs scaled to the awarded amount, rounded down so the lines never exceed it; what
     * rounding leaves over goes to the first line (from the last) whose quantity divides it evenly.
     */
    private static List<SourceLine> prorate(List<SourceLine> lines, BigDecimal abc, BigDecimal awarded) {
        if (abc == null || abc.signum() == 0 || awarded == null) return lines;
        BigDecimal ratio = awarded.divide(abc, 10, RoundingMode.HALF_UP);
        List<SourceLine> out = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (SourceLine l : lines) {
            BigDecimal unit = l.unitCost().multiply(ratio).setScale(2, RoundingMode.DOWN);
            out.add(new SourceLine(l.sourceItemId(), l.description(), l.specifications(), l.unit(), l.quantity(), unit));
            sum = sum.add(unit.multiply(BigDecimal.valueOf(l.quantity())));
        }
        BigDecimal left = awarded.subtract(sum);
        for (int i = out.size() - 1; i >= 0 && left.signum() > 0; i--) {
            SourceLine l = out.get(i);
            BigDecimal qty = BigDecimal.valueOf(l.quantity());
            BigDecimal perUnit = left.divide(qty, 2, RoundingMode.DOWN);
            if (perUnit.multiply(qty).compareTo(left) == 0) {
                out.set(i, new SourceLine(l.sourceItemId(), l.description(), l.specifications(), l.unit(), l.quantity(), l.unitCost().add(perUnit)));
                left = BigDecimal.ZERO;
            }
        }
        return out;
    }

    // ───────────────────────── reads ─────────────────────────

    @GetMapping("/purchase-orders")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<PoView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                      @RequestParam(name = "supplier_id", required = false) Long supplier, @RequestParam(required = false) String search) {
        List<String> statuses = status == null || status.isBlank() ? List.of() : Arrays.stream(status.split(",")).map(String::trim).filter(STATUSES::contains).toList();
        Long only = current.any("view_procurement", "approve_procurement", "edit_procurement", "view_receiving", "create_receiving", "accept_delivery")
                ? null : current.user().getId();
        Page<PurchaseOrder> result = orders.search(statuses.isEmpty(), statuses.isEmpty() ? List.of("-") : statuses, supplier, only, blank(search),
                PageRequest.of(Math.max(0, page - 1), 20));
        Map<PurchaseOrder, PoView> v = views(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PoView show(@PathVariable Long id) {
        PurchaseOrder po = orders.findLive(id).orElseThrow(() -> ApiException.notFound("Purchase order"));
        if (!current.any("view_procurement", "approve_procurement", "edit_procurement", "view_receiving", "create_receiving", "accept_delivery")) {
            Long requester = po.getPurchaseRequestId() == null ? null
                    : jdbc.query("select requested_by from purchase_requests where id = ?", rs -> rs.next() ? rs.getLong(1) : null, po.getPurchaseRequestId());
            if (!current.user().getId().equals(requester)) throw ApiException.forbidden("You can only view purchase orders for your own requests.");
        }
        return view(po);
    }

    // ───────────────────────── lifecycle ─────────────────────────

    public record PriceInput(Long sourceItemId, BigDecimal unitCost) {
    }

    public record PoInput(String sourceType, Long sourceId, LocalDate deliveryDate, String deliveryAddress, String remarks, List<PriceInput> prices) {
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("@perm.any('edit_procurement')")
    @Transactional
    public ResponseEntity<PoView> store(@RequestBody PoInput in) {
        if (in.sourceId() == null || !List.of("rfq", "bidding").contains(in.sourceType())) {
            throw ApiException.validation("source_id", "A purchase order is issued on an award: choose the awarded RFQ or bidding.");
        }
        db.namedLock("pams.po." + in.sourceType() + "." + in.sourceId());
        AwardSource src = sources().stream()
                .filter(s -> s.type().equals(in.sourceType()) && s.id().equals(in.sourceId())).findFirst()
                .orElseThrow(() -> ApiException.validation("source_id", "That award has no Notice of Award yet, or already has a purchase order."));
        if (!awards.eligible(src.supplier().id())) throw ApiException.validation("source_id", src.supplier().name() + " is inactive or not eligible.");
        LocalDate today = LocalDate.now(Times.ZONE);
        PurchaseOrder po = new PurchaseOrder(numbers.yearly("PO", "purchase_orders.po_number"), current.user().getId(), today);
        po.setSupplierId(src.supplier().id());
        po.setPurchaseRequestId(src.purchaseRequest() == null ? null : src.purchaseRequest().id());
        if ("rfq".equals(src.type())) po.setRfqId(src.id());
        else po.setBiddingId(src.id());
        for (SourceLine l : src.lines()) {
            po.getItems().add(new PurchaseOrderItem(po, l.description(), l.specifications(), l.unit(), l.quantity(), l.unitCost(),
                    "rfq".equals(src.type()) ? l.sourceItemId() : null, "bidding".equals(src.type()) ? l.sourceItemId() : null));
        }
        applyTerms(po, in, src);
        orders.saveAndFlush(po);
        audit.record("created", TYPE, po.getId(), "Purchase order " + po.getPoNumber() + " drafted on " + src.number() + " (" + src.supplier().name() + ", ₱"
                + ProcurementRules.peso(po.getTotalAmount()) + ")", null, snapshot(po));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(po));
    }

    @PutMapping("/purchase-orders/{id}")
    @PreAuthorize("@perm.any('edit_procurement')")
    @Transactional
    public PoView update(@PathVariable Long id, @RequestBody PoInput in) {
        PurchaseOrder po = lock(id);
        if (!PurchaseOrder.DRAFT.equals(po.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft purchase order can be changed.");
        Map<String, Object> before = snapshot(po);
        BigDecimal awarded = po.getBiddingId() == null ? null
                : jdbc.queryForObject("select awarded_amount from biddings where id = ?", BigDecimal.class, po.getBiddingId());
        applyTerms(po, in, new AwardSource(po.getBiddingId() == null ? "rfq" : "bidding", null, null, null, null, null, null, awarded, null, null,
                po.getBiddingId() == null, List.of()));
        orders.flush();
        audit.record("updated", TYPE, po.getId(), "Edited purchase order " + po.getPoNumber(), before, snapshot(po));
        return view(po);
    }

    /** Approves the PO and issues it to the supplier; the one who prepared it cannot. */
    @PostMapping("/purchase-orders/{id}/send")
    @PreAuthorize("@perm.any('edit_procurement', 'approve_procurement')")
    @Transactional
    public PoView send(@PathVariable Long id) {
        PurchaseOrder po = lock(id);
        if (!PurchaseOrder.DRAFT.equals(po.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft purchase order can be approved and sent.");
        User me = current.user();
        if (me.getId().equals(po.getCreatedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You prepared this purchase order, so someone else has to approve it.");
        if (po.getDeliveryDate() == null) throw ApiException.validation("delivery_date", "Set the delivery date before sending.");
        po.setStatus(PurchaseOrder.SENT);
        po.setApprovedBy(me.getId());
        orders.flush();
        audit.record("approved", TYPE, po.getId(), "Purchase order " + po.getPoNumber() + " approved and sent to " + awards.supplierName(po.getSupplierId()),
                Map.of("status", "draft"), Map.of("status", "sent"));
        return view(po);
    }

    /** Records the supplier's conformé; deliveries can then be received. */
    @PostMapping("/purchase-orders/{id}/confirm")
    @PreAuthorize("@perm.any('edit_procurement')")
    @Transactional
    public PoView confirm(@PathVariable Long id) {
        PurchaseOrder po = lock(id);
        if (!PurchaseOrder.SENT.equals(po.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a sent purchase order can be confirmed.");
        po.setStatus(PurchaseOrder.CONFIRMED);
        orders.flush();
        audit.record("confirmed", TYPE, po.getId(), "Supplier conformé recorded for " + po.getPoNumber(), Map.of("status", "sent"), Map.of("status", "confirmed"));
        notifications.notifyPermission(List.of("create_receiving", "accept_delivery"), current.user().getId(), "po_confirmed", "Delivery expected",
                po.getPoNumber() + " (" + awards.supplierName(po.getSupplierId()) + ") is confirmed; delivery due " + po.getDeliveryDate() + ".", TYPE, po.getId());
        return view(po);
    }

    public record ReasonInput(String reason) {
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize("@perm.any('edit_procurement')")
    @Transactional
    public PoView cancel(@PathVariable Long id, @RequestBody(required = false) ReasonInput in) {
        PurchaseOrder po = lock(id);
        if (!List.of(PurchaseOrder.DRAFT, PurchaseOrder.SENT, PurchaseOrder.CONFIRMED).contains(po.getStatus())) {
            throw ApiException.of(HttpStatus.CONFLICT, "A purchase order with deliveries, or already closed, cannot be cancelled.");
        }
        if (!reports.findByPurchaseOrderIdOrderByIdAsc(po.getId()).isEmpty()) throw ApiException.of(HttpStatus.CONFLICT, "Deliveries have been inspected against this purchase order.");
        String why = in == null ? null : blank(in.reason());
        if (why == null && !PurchaseOrder.DRAFT.equals(po.getStatus())) throw ApiException.validation("reason", "Say why the purchase order is cancelled.");
        String was = po.getStatus();
        po.setStatus(PurchaseOrder.CANCELLED);
        if (why != null) po.setRemarks(why);
        orders.flush();
        audit.record("cancelled", TYPE, po.getId(), "Purchase order " + po.getPoNumber() + " cancelled" + (why == null ? "" : ": " + why), Map.of("status", was), Map.of("status", "cancelled"));
        return view(po);
    }

    @DeleteMapping("/purchase-orders/{id}")
    @PreAuthorize("@perm.any('edit_procurement')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        PurchaseOrder po = lock(id);
        if (!PurchaseOrder.DRAFT.equals(po.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft purchase order can be deleted; cancel it instead.");
        po.setDeletedAt(Times.now());
        audit.record("deleted", TYPE, po.getId(), "Deleted purchase order " + po.getPoNumber(), snapshot(po), null);
        return Map.of("message", po.getPoNumber() + " deleted.");
    }

    // ───────────────────────── helpers ─────────────────────────

    private void applyTerms(PurchaseOrder po, PoInput in, AwardSource src) {
        LocalDate today = LocalDate.now(Times.ZONE);
        LocalDate delivery = in.deliveryDate();
        if (delivery == null && po.getDeliveryDate() == null && src.deliveryLeadDays() != null) delivery = today.plusDays(src.deliveryLeadDays());
        if (delivery != null) {
            if (delivery.isBefore(po.getOrderDate())) throw ApiException.validation("delivery_date", "Delivery cannot be due before the order date.");
            po.setDeliveryDate(delivery);
        }
        String address = blank(in.deliveryAddress());
        if (address == null && po.getDeliveryAddress() == null && src.deliveryTerm() != null) address = src.deliveryTerm();
        if (address != null) po.setDeliveryAddress(address);
        if (in.remarks() != null) po.setRemarks(blank(in.remarks()));
        if (in.prices() != null && !in.prices().isEmpty()) {
            if (src.pricesFixed()) throw ApiException.validation("prices", "An RFQ purchase order is priced at the winning quotation.");
            Map<Long, PurchaseOrderItem> byLine = po.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getBiddingItemId, x -> x));
            for (PriceInput p : in.prices()) {
                PurchaseOrderItem line = p == null ? null : byLine.get(p.sourceItemId());
                if (line == null) throw ApiException.validation("prices", "A price is for a line not on this purchase order.");
                if (p.unitCost() == null || p.unitCost().signum() < 0 || p.unitCost().scale() > 2) throw ApiException.validation("prices", "Enter unit prices in pesos.");
                line.setUnitCost(p.unitCost());
            }
        }
        po.recalc();
        // The PO is the contract price: never above the awarded bid, and below it only by centavo rounding.
        if (!src.pricesFixed() && src.amount() != null
                && (po.getTotalAmount().compareTo(src.amount()) > 0 || src.amount().subtract(po.getTotalAmount()).compareTo(BigDecimal.ONE) >= 0)) {
            throw ApiException.validation("prices", "The lines total ₱" + ProcurementRules.peso(po.getTotalAmount()) + "; they must add up to the awarded bid of ₱"
                    + ProcurementRules.peso(src.amount()) + ".");
        }
    }

    private PurchaseOrder lock(Long id) {
        return orders.lockById(id).orElseThrow(() -> ApiException.notFound("Purchase order"));
    }

    private PoView view(PurchaseOrder po) {
        return views(List.of(po), true).get(po);
    }

    private Map<PurchaseOrder, PoView> views(Collection<PurchaseOrder> list, boolean full) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().flatMap(p -> Stream.of(p.getCreatedBy(), p.getApprovedBy())).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getId(), u.getName()));
        Map<Long, String> suppliers = new HashMap<>();
        Set<Long> sIds = list.stream().map(PurchaseOrder::getSupplierId).collect(Collectors.toSet());
        if (!sIds.isEmpty()) jdbc.query("select id, name from suppliers where id in (" + sIds.stream().map(x -> "?").collect(Collectors.joining(",")) + ")",
                rs -> {
                    suppliers.put(rs.getLong(1), rs.getString(2));
                }, sIds.toArray());
        Map<Long, String[]> prs = new HashMap<>();
        Set<Long> prIds = list.stream().map(PurchaseOrder::getPurchaseRequestId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!prIds.isEmpty()) jdbc.query("""
                select pr.id, pr.pr_number, d.id, d.name, pr.caf_number from purchase_requests pr join departments d on d.id = pr.department_id
                where pr.id in (""" + prIds.stream().map(x -> "?").collect(Collectors.joining(",")) + ")", rs -> {
            prs.put(rs.getLong(1), new String[]{rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)});
        }, prIds.toArray());
        User me = current.user();
        boolean editor = current.any("edit_procurement");
        boolean approver = current.any("edit_procurement", "approve_procurement");
        boolean receiver = current.any("create_receiving");
        LocalDate today = LocalDate.now(Times.ZONE);
        Map<PurchaseOrder, PoView> out = new LinkedHashMap<>();
        for (PurchaseOrder po : list) {
            Map<Long, Integer> delivered = full ? deliveries.delivered(po.getId(), null) : Map.of();
            Map<Long, Integer> accepted = full ? deliveries.accepted(po.getId()) : Map.of();
            List<LineView> lines = !full ? null : po.getItems().stream().map(i -> new LineView(i.getId(), i.getDescription(), i.getSpecifications(), i.getUnit(),
                    i.getQuantity(), i.getUnitCost(), i.getTotalCost(), delivered.getOrDefault(i.getId(), 0), accepted.getOrDefault(i.getId(), 0),
                    Math.max(0, i.getQuantity() - delivered.getOrDefault(i.getId(), 0)), i.getRfqItemId(), i.getBiddingItemId())).toList();
            List<InspectionReport> iars = full ? reports.findByPurchaseOrderIdOrderByIdAsc(po.getId()) : List.of();
            String[] pr = po.getPurchaseRequestId() == null ? null : prs.get(po.getPurchaseRequestId());
            Source source = po.getRfqId() != null ? sourceOf("rfq", po.getRfqId()) : po.getBiddingId() != null ? sourceOf("bidding", po.getBiddingId()) : null;
            boolean own = me.getId().equals(po.getCreatedBy()) && !me.isSuperAdmin();
            boolean open = !PurchaseOrder.RECEIVED.equals(po.getStatus()) && !PurchaseOrder.CANCELLED.equals(po.getStatus());
            out.put(po, new PoView(po.getId(), po.getPoNumber(), po.getStatus(), new Ref(po.getSupplierId(), suppliers.get(po.getSupplierId())),
                    pr == null ? null : new Ref(po.getPurchaseRequestId(), pr[0]), pr == null ? null : new Ref(Long.valueOf(pr[1]), pr[2]), source,
                    po.getOrderDate(), po.getDeliveryDate(), open && po.getDeliveryDate() != null && po.getDeliveryDate().isBefore(today) && !PurchaseOrder.DRAFT.equals(po.getStatus()),
                    po.getDeliveryAddress(), po.getTotalAmount(), po.getRemarks(), ref(po.getCreatedBy(), names), ref(po.getApprovedBy(), names),
                    full ? fundSource(po.getPurchaseRequestId()) : null, pr == null ? null : pr[3], po.getItems().size(), lines,
                    !full ? null : iars.stream().map(r -> new IarRef(r.getId(), r.getIarNumber(), r.getStatus(), r.getAcceptanceStatus(), r.getDeliveryDate())).toList(),
                    editor && PurchaseOrder.DRAFT.equals(po.getStatus()), approver && !own && PurchaseOrder.DRAFT.equals(po.getStatus()),
                    editor && PurchaseOrder.SENT.equals(po.getStatus()),
                    editor && List.of(PurchaseOrder.DRAFT, PurchaseOrder.SENT, PurchaseOrder.CONFIRMED).contains(po.getStatus()) && (!full || iars.isEmpty()),
                    editor && PurchaseOrder.DRAFT.equals(po.getStatus()), receiver && po.isReceivable(), Times.toOffset(po.getCreatedAt())));
        }
        return out;
    }

    private Source sourceOf(String type, Long id) {
        String sql = "rfq".equals(type) ? "select rfq_number, noa_number, procurement_method from rfqs where id = ?"
                : "select bidding_number, noa_number, 'Competitive Bidding' from biddings where id = ?";
        return jdbc.query(sql, rs -> rs.next() ? new Source(type, id, rs.getString(1), rs.getString(2), rs.getString(3)) : null, id);
    }

    private String fundSource(Long prId) {
        if (prId == null) return null;
        List<String> f = jdbc.queryForList("select distinct fund_source from purchase_request_items where purchase_request_id = ? and fund_source is not null order by 1", String.class, prId);
        return f.isEmpty() ? null : String.join("; ", f);
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id, names.get(id));
    }

    private static Map<String, Object> snapshot(PurchaseOrder po) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", po.getStatus());
        m.put("supplier_id", po.getSupplierId());
        m.put("total_amount", po.getTotalAmount());
        m.put("delivery_date", po.getDeliveryDate() == null ? null : po.getDeliveryDate().toString());
        m.put("lines", po.getItems().size());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

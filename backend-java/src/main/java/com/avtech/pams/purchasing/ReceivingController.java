package com.avtech.pams.purchasing;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.InventoryLog;
import com.avtech.pams.inventory.InventoryService;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.inventory.Stock;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.procurement.AwardSupport;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Receiving against purchase orders. The inspection committee records each delivery on an IAR —
 * received, accepted and rejected per PO line, never more than is still outstanding. A supply or
 * property officer other than the inspector then accepts it, which puts the accepted quantities
 * into stock as new lots at the PO price (stock card, weighted average cost), or rejects the delivery.
 */
@RestController
@RequestMapping("/api/receiving")
public class ReceivingController {

    private static final String TYPE = "App\\Models\\InspectionAcceptanceReport";
    private static final String READ = "@perm.any('view_receiving', 'create_receiving', 'accept_delivery', 'view_procurement', 'view_reports', 'generate_reports')";

    private final InspectionReportRepository reports;
    private final PurchaseOrderRepository orders;
    private final ItemRepository items;
    private final UserRepository users;
    private final StockLedger ledger;
    private final InventoryService inventory;
    private final Deliveries deliveries;
    private final AwardSupport awards;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;

    public ReceivingController(InspectionReportRepository reports, PurchaseOrderRepository orders, ItemRepository items, UserRepository users,
                               StockLedger ledger, InventoryService inventory, Deliveries deliveries, AwardSupport awards, DocumentNumbers numbers,
                               NotificationService notifications, AuditService audit, CurrentUser current, JdbcTemplate jdbc) {
        this.reports = reports;
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.ledger = ledger;
        this.inventory = inventory;
        this.deliveries = deliveries;
        this.awards = awards;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
    }

    public record Ref(Long id, String name) {
    }

    public record ItemRef(Long id, String code, String name, String unitOfMeasure) {
    }

    public record LineView(Long id, Long purchaseOrderItemId, String description, String specifications, String unit, int quantityOrdered,
                           int quantityReceived, int quantityAccepted, int quantityRejected, String status, String remarks, BigDecimal unitCost,
                           ItemRef item, Long stockId) {
    }

    public record IarView(Long id, String iarNumber, Ref purchaseOrder, Ref supplier, Ref purchaseRequest, Ref department, LocalDate deliveryDate,
                          LocalDate inspectionDate, Ref inspector, String status, int totalItemsOrdered, int totalItemsReceived,
                          int totalItemsAccepted, int totalItemsRejected, String observations, String remarks, String acceptanceStatus,
                          Ref accepter, OffsetDateTime acceptedAt, List<LineView> items, boolean canEdit, boolean canAccept, boolean canReject,
                          boolean canDelete, OffsetDateTime createdAt) {
    }

    @GetMapping("/inspection-reports")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<IarView> index(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "acceptance_status", required = false) String acceptance,
                                       @RequestParam(name = "purchase_order_id", required = false) Long po, @RequestParam(required = false) String search) {
        Page<InspectionReport> result = reports.search(blank(acceptance), po, blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<InspectionReport, IarView> v = views(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/inspection-reports/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public IarView show(@PathVariable Long id) {
        InspectionReport r = reports.findById(id).orElseThrow(() -> ApiException.notFound("Inspection report"));
        return view(r);
    }

    public record LineInput(Long purchaseOrderItemId, Integer quantityReceived, Integer quantityAccepted, Integer quantityRejected, String remarks) {
    }

    public record IarInput(Long purchaseOrderId, LocalDate deliveryDate, LocalDate inspectionDate, String observations, String remarks, List<LineInput> items) {
    }

    @PostMapping("/inspection-reports")
    @PreAuthorize("@perm.any('create_receiving')")
    @Transactional
    public ResponseEntity<IarView> store(@RequestBody IarInput in) {
        if (in.purchaseOrderId() == null) throw ApiException.validation("purchase_order_id", "Choose the purchase order delivered against.");
        PurchaseOrder po = orders.lockById(in.purchaseOrderId()).orElseThrow(() -> ApiException.validation("purchase_order_id", "That purchase order does not exist."));
        if (!po.isReceivable()) {
            throw ApiException.validation("purchase_order_id", PurchaseOrder.RECEIVED.equals(po.getStatus()) ? po.getPoNumber() + " has been fully delivered."
                    : po.getPoNumber() + " is not confirmed by the supplier yet.");
        }
        InspectionReport r = new InspectionReport(numbers.yearly("IAR", "inspection_acceptance_reports.iar_number"), po.getId(), current.user().getId());
        apply(r, po, in);
        reports.saveAndFlush(r);
        deliveries.sync(po);
        orders.flush();
        audit.record("created", TYPE, r.getId(), "Inspection report " + r.getIarNumber() + " for " + po.getPoNumber() + ": " + r.getTotalAccepted() + " accepted, "
                + r.getTotalRejected() + " rejected (" + r.getStatus() + ")", null, snapshot(r));
        notifications.notifyPermission(List.of("accept_delivery"), current.user().getId(), "delivery_inspected", "Delivery inspected — for acceptance",
                r.getIarNumber() + " (" + po.getPoNumber() + ", " + awards.supplierName(po.getSupplierId()) + ") is ready for acceptance into stock.", TYPE, r.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(r));
    }

    @PutMapping("/inspection-reports/{id}")
    @PreAuthorize("@perm.any('create_receiving')")
    @Transactional
    public IarView update(@PathVariable Long id, @RequestBody IarInput in) {
        InspectionReport r = lockReport(id);
        if (!r.isPending()) throw ApiException.of(HttpStatus.CONFLICT, "This delivery has been " + r.getAcceptanceStatus().replace('_', ' ') + "; the report is final.");
        PurchaseOrder po = orders.lockById(r.getPurchaseOrderId()).orElseThrow();
        Map<String, Object> before = snapshot(r);
        apply(r, po, in);
        reports.flush();
        deliveries.sync(po);
        orders.flush();
        audit.record("updated", TYPE, r.getId(), "Edited inspection report " + r.getIarNumber(), before, snapshot(r));
        return view(r);
    }

    @DeleteMapping("/inspection-reports/{id}")
    @PreAuthorize("@perm.any('create_receiving')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        InspectionReport r = lockReport(id);
        if (!r.isPending()) throw ApiException.of(HttpStatus.CONFLICT, "Only a report awaiting acceptance can be deleted.");
        PurchaseOrder po = orders.lockById(r.getPurchaseOrderId()).orElseThrow();
        Map<String, Object> before = snapshot(r);
        reports.delete(r);
        reports.flush();
        deliveries.sync(po);
        orders.flush();
        audit.record("deleted", TYPE, r.getId(), "Deleted inspection report " + r.getIarNumber(), before, null);
        return Map.of("message", r.getIarNumber() + " deleted.");
    }

    public record AcceptLine(Long iarItemId, Long itemId, String lotNumber, LocalDate expirationDate, String brandName, String location,
                             Integer warrantyMonths) {
    }

    public record AcceptInput(List<AcceptLine> lines) {
    }

    /**
     * Accepts the delivery into stock: each accepted line goes into the inventory item chosen for
     * it, as a new lot at the PO unit price, received on the delivery date, from the PO's supplier.
     */
    @PostMapping("/inspection-reports/{id}/accept")
    @PreAuthorize("@perm.any('accept_delivery')")
    @Transactional
    public IarView accept(@PathVariable Long id, @RequestBody AcceptInput in) {
        InspectionReport r = lockReport(id);
        if (!r.isPending()) throw ApiException.of(HttpStatus.CONFLICT, "This delivery has already been " + r.getAcceptanceStatus().replace('_', ' ') + ".");
        if ("failed".equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Nothing in this delivery passed inspection; reject it instead.");
        User me = current.user();
        if (me.getId().equals(r.getInspectedBy()) && !me.isSuperAdmin()) {
            throw ApiException.forbidden("You inspected this delivery, so the acceptance has to be signed by someone else.");
        }
        PurchaseOrder po = orders.lockById(r.getPurchaseOrderId()).orElseThrow();
        Map<Long, PurchaseOrderItem> poLines = po.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        Map<Long, AcceptLine> chosen = new HashMap<>();
        for (AcceptLine l : in == null || in.lines() == null ? List.<AcceptLine>of() : in.lines()) {
            if (l != null && l.iarItemId() != null) chosen.put(l.iarItemId(), l);
        }
        // Check everything first, so a bad line leaves no stock behind.
        Set<Long> seenItems = new HashSet<>();
        for (IarItem line : r.getItems()) {
            if (line.getQuantityAccepted() <= 0) continue;
            AcceptLine c = chosen.get(line.getId());
            if (c == null || c.itemId() == null) throw ApiException.validation("lines", "Choose the inventory item \"" + line.getDescription() + "\" goes into.");
            Item item = items.findById(c.itemId()).orElseThrow(() -> ApiException.validation("lines", "That inventory item does not exist."));
            PurchaseOrderItem pl = poLines.get(line.getPurchaseOrderItemId());
            if (pl == null) throw ApiException.validation("lines", "\"" + line.getDescription() + "\" is no longer on the purchase order.");
            inventory.checkUnitCost(item, pl.getUnitCost());
            if (c.expirationDate() != null && c.expirationDate().isBefore(r.getDeliveryDate())) {
                throw ApiException.validation("lines", "\"" + line.getDescription() + "\": the expiry date is before the delivery date.");
            }
            if (c.warrantyMonths() != null && c.warrantyMonths() < 0) throw ApiException.validation("lines", "Warranty months cannot be negative.");
            seenItems.add(item.getId());
        }
        int lots = 0;
        for (IarItem line : r.getItems()) {
            if (line.getQuantityAccepted() <= 0) continue;
            AcceptLine c = chosen.get(line.getId());
            PurchaseOrderItem pl = poLines.get(line.getPurchaseOrderItemId());
            Item item = ledger.lock(c.itemId());
            int before = item.getCurrentStock();
            Stock lot = new Stock();
            lot.setItemId(item.getId());
            lot.setQuantity(line.getQuantityAccepted());
            lot.setUnitCost(pl.getUnitCost());
            lot.setSupplierId(po.getSupplierId());
            lot.setIarId(r.getId());
            lot.setReceivedDate(r.getDeliveryDate());
            lot.setLotNumber(blank(c.lotNumber()));
            lot.setExpirationDate(c.expirationDate());
            lot.setBrandName(blank(c.brandName()));
            lot.setLocation(blank(c.location()));
            lot.setWarrantyMonths(c.warrantyMonths());
            lot.setSpecifications(line.getSpecifications());
            Stock saved = ledger.receive(item, lot);
            ledger.log(item, me.getId(), InventoryLog.STOCK_IN, saved.getQuantity(), before, TYPE, r.getId(),
                    "Received on " + r.getIarNumber() + " (" + po.getPoNumber() + "): +" + saved.getQuantity() + " " + item.getUnitOfMeasure());
            line.setItemId(item.getId());
            lots++;
        }
        r.setAcceptanceStatus("partial".equals(r.getStatus()) ? InspectionReport.PARTIALLY_ACCEPTED : InspectionReport.ACCEPTED);
        r.setAcceptedBy(me.getId());
        r.setAcceptedAt(Times.now());
        reports.flush();
        deliveries.sync(po);
        orders.flush();
        audit.record("accepted", TYPE, r.getId(), r.getIarNumber() + " accepted into stock: " + lots + " lot(s) across " + seenItems.size() + " item(s)",
                Map.of("acceptance_status", "pending"), Map.of("acceptance_status", r.getAcceptanceStatus()));
        if (r.getTotalRejected() > 0 || "partial".equals(r.getStatus())) {
            notifications.notifyPermission(List.of("edit_procurement"), me.getId(), "delivery_short", "Delivery short or rejected — notify the supplier",
                    r.getIarNumber() + " (" + po.getPoNumber() + "): " + r.getTotalRejected() + " unit(s) rejected; " + po.getPoNumber() + " is " + po.getStatus().replace('_', ' ') + ".",
                    TYPE, r.getId());
        }
        return view(r);
    }

    public record RejectInput(String remarks) {
    }

    @PostMapping("/inspection-reports/{id}/reject")
    @PreAuthorize("@perm.any('accept_delivery')")
    @Transactional
    public IarView reject(@PathVariable Long id, @RequestBody RejectInput in) {
        String why = in == null ? null : blank(in.remarks());
        if (why == null) throw ApiException.validation("remarks", "Say why the delivery is rejected.");
        InspectionReport r = lockReport(id);
        if (!r.isPending()) throw ApiException.of(HttpStatus.CONFLICT, "This delivery has already been " + r.getAcceptanceStatus().replace('_', ' ') + ".");
        User me = current.user();
        if (me.getId().equals(r.getInspectedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You inspected this delivery, so someone else has to act on it.");
        PurchaseOrder po = orders.lockById(r.getPurchaseOrderId()).orElseThrow();
        r.setAcceptanceStatus(InspectionReport.REJECTED);
        r.setAcceptedBy(me.getId());
        r.setAcceptedAt(Times.now());
        r.setRemarks(why);
        reports.flush();
        deliveries.sync(po);
        orders.flush();
        audit.record("rejected", TYPE, r.getId(), r.getIarNumber() + " rejected: " + why, Map.of("acceptance_status", "pending"), Map.of("acceptance_status", "rejected"));
        notifications.notifyPermission(List.of("edit_procurement"), me.getId(), "delivery_rejected", "Delivery rejected — notify the supplier",
                r.getIarNumber() + " (" + po.getPoNumber() + ", " + awards.supplierName(po.getSupplierId()) + ") was rejected: " + why, TYPE, r.getId());
        return view(r);
    }

    // ───────────────────────── helpers ─────────────────────────

    private void apply(InspectionReport r, PurchaseOrder po, IarInput in) {
        LocalDate today = LocalDate.now(Times.ZONE);
        if (in.deliveryDate() == null) throw ApiException.validation("delivery_date", "Enter the delivery date.");
        if (in.deliveryDate().isAfter(today)) throw ApiException.validation("delivery_date", "The delivery date cannot be in the future.");
        if (in.deliveryDate().isBefore(po.getOrderDate())) throw ApiException.validation("delivery_date", "Delivered before the purchase order was issued?");
        LocalDate inspected = in.inspectionDate() == null ? today : in.inspectionDate();
        if (inspected.isBefore(in.deliveryDate()) || inspected.isAfter(today)) throw ApiException.validation("inspection_date", "Inspection falls between the delivery and today.");
        if (in.items() == null || in.items().isEmpty()) throw ApiException.validation("items", "Record at least one delivered line.");
        Map<Long, PurchaseOrderItem> poLines = po.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));
        Map<Long, Integer> done = deliveries.delivered(po.getId(), r.getId());
        Set<Long> seen = new HashSet<>();
        List<IarItem> lines = new ArrayList<>();
        for (int i = 0; i < in.items().size(); i++) {
            LineInput l = in.items().get(i);
            String f = "items." + i + ".";
            PurchaseOrderItem pl = l == null || l.purchaseOrderItemId() == null ? null : poLines.get(l.purchaseOrderItemId());
            if (pl == null) throw ApiException.validation(f + "purchase_order_item_id", "Line " + (i + 1) + " is not on " + po.getPoNumber() + ".");
            if (!seen.add(pl.getId())) throw ApiException.validation(f + "purchase_order_item_id", "\"" + pl.getDescription() + "\" is listed twice.");
            int accepted = l.quantityAccepted() == null ? 0 : l.quantityAccepted();
            int rejected = l.quantityRejected() == null ? 0 : l.quantityRejected();
            int received = l.quantityReceived() == null ? accepted + rejected : l.quantityReceived();
            if (accepted < 0 || rejected < 0 || received < 0) throw ApiException.validation(f + "quantity_received", "Quantities cannot be negative.");
            if (received == 0) throw ApiException.validation(f + "quantity_received", "\"" + pl.getDescription() + "\": nothing received; leave the line out.");
            if (accepted + rejected != received) throw ApiException.validation(f + "quantity_received", "\"" + pl.getDescription() + "\": accepted and rejected must add up to the "
                    + received + " received.");
            int outstanding = pl.getQuantity() - done.getOrDefault(pl.getId(), 0);
            if (accepted > outstanding) throw ApiException.validation(f + "quantity_accepted", "\"" + pl.getDescription() + "\": " + accepted + " accepted, but only "
                    + Math.max(0, outstanding) + " of " + pl.getQuantity() + " are still to be delivered.");
            String remarks = blank(l.remarks());
            if (rejected > 0 && remarks == null) throw ApiException.validation(f + "remarks", "\"" + pl.getDescription() + "\": say why " + rejected + " were rejected.");
            lines.add(new IarItem(r, pl, outstanding, received, accepted, rejected, remarks));
        }
        r.setDeliveryDate(in.deliveryDate());
        r.setInspectionDate(inspected);
        r.setObservations(blank(in.observations()));
        r.setRemarks(blank(in.remarks()));
        r.getItems().clear();
        reports.flush();
        r.getItems().addAll(lines);
        r.recalc();
    }

    private InspectionReport lockReport(Long id) {
        return reports.lockById(id).orElseThrow(() -> ApiException.notFound("Inspection report"));
    }

    private IarView view(InspectionReport r) {
        return views(List.of(r), true).get(r);
    }

    private Map<InspectionReport, IarView> views(Collection<InspectionReport> list, boolean full) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().flatMap(r -> Stream.of(r.getInspectedBy(), r.getAcceptedBy())).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getId(), u.getName()));
        Map<Long, PurchaseOrder> pos = new HashMap<>();
        orders.findAllById(list.stream().map(InspectionReport::getPurchaseOrderId).collect(Collectors.toSet())).forEach(p -> pos.put(p.getId(), p));
        Map<Long, String> suppliers = new HashMap<>();
        Map<Long, String[]> prs = new HashMap<>();
        for (PurchaseOrder p : pos.values()) {
            suppliers.computeIfAbsent(p.getSupplierId(), awards::supplierName);
            if (p.getPurchaseRequestId() != null && !prs.containsKey(p.getPurchaseRequestId())) {
                jdbc.query("select pr.pr_number, d.id, d.name from purchase_requests pr join departments d on d.id = pr.department_id where pr.id = ?", rs -> {
                    prs.put(p.getPurchaseRequestId(), new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }, p.getPurchaseRequestId());
            }
        }
        Map<Long, Item> itemById = new HashMap<>();
        if (full) items.findAllById(list.stream().flatMap(r -> r.getItems().stream()).map(IarItem::getItemId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(i -> itemById.put(i.getId(), i));
        Map<String, Long> lotOf = new HashMap<>();
        if (full) for (InspectionReport r : list) {
            jdbc.query("select item_id, id from stocks where iar_id = ?", rs -> {
                lotOf.put(r.getId() + ":" + rs.getLong(1), rs.getLong(2));
            }, r.getId());
        }
        User me = current.user();
        boolean inspector = current.any("create_receiving");
        boolean accepter = current.any("accept_delivery");
        Map<InspectionReport, IarView> out = new LinkedHashMap<>();
        for (InspectionReport r : list) {
            PurchaseOrder po = pos.get(r.getPurchaseOrderId());
            Map<Long, BigDecimal> cost = po == null ? Map.of() : po.getItems().stream().collect(Collectors.toMap(PurchaseOrderItem::getId, PurchaseOrderItem::getUnitCost));
            String[] pr = po == null || po.getPurchaseRequestId() == null ? null : prs.get(po.getPurchaseRequestId());
            boolean own = me.getId().equals(r.getInspectedBy()) && !me.isSuperAdmin();
            List<LineView> lines = !full ? null : r.getItems().stream().map(l -> {
                Item it = l.getItemId() == null ? null : itemById.get(l.getItemId());
                return new LineView(l.getId(), l.getPurchaseOrderItemId(), l.getDescription(), l.getSpecifications(), l.getUnit(), l.getQuantityOrdered(),
                        l.getQuantityReceived(), l.getQuantityAccepted(), l.getQuantityRejected(), l.getStatus(), l.getRemarks(), cost.get(l.getPurchaseOrderItemId()),
                        it == null ? null : new ItemRef(it.getId(), it.getCode(), it.getName(), it.getUnitOfMeasure()),
                        it == null ? null : lotOf.get(r.getId() + ":" + it.getId()));
            }).toList();
            out.put(r, new IarView(r.getId(), r.getIarNumber(), po == null ? null : new Ref(po.getId(), po.getPoNumber()),
                    po == null ? null : new Ref(po.getSupplierId(), suppliers.get(po.getSupplierId())),
                    pr == null ? null : new Ref(po.getPurchaseRequestId(), pr[0]), pr == null ? null : new Ref(Long.valueOf(pr[1]), pr[2]),
                    r.getDeliveryDate(), r.getInspectionDate(), new Ref(r.getInspectedBy(), names.get(r.getInspectedBy())), r.getStatus(), r.getTotalOrdered(),
                    r.getTotalReceived(), r.getTotalAccepted(), r.getTotalRejected(), r.getObservations(), r.getRemarks(), r.getAcceptanceStatus(),
                    r.getAcceptedBy() == null ? null : new Ref(r.getAcceptedBy(), names.get(r.getAcceptedBy())), Times.toOffset(r.getAcceptedAt()), lines,
                    inspector && r.isPending(), accepter && !own && r.isPending() && !"failed".equals(r.getStatus()), accepter && !own && r.isPending(),
                    inspector && r.isPending(), Times.toOffset(r.getCreatedAt())));
        }
        return out;
    }

    private static Map<String, Object> snapshot(InspectionReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("acceptance_status", r.getAcceptanceStatus());
        m.put("delivery_date", r.getDeliveryDate() == null ? null : r.getDeliveryDate().toString());
        m.put("accepted", r.getTotalAccepted());
        m.put("rejected", r.getTotalRejected());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

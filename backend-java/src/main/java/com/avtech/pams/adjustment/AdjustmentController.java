package com.avtech.pams.adjustment;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.inventory.Stock;
import com.avtech.pams.inventory.StockLedger;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stock adjustments (count corrections, damage, found stock) with maker/approver separation: the
 * person who asks for an adjustment cannot approve it. Approval moves real stock lots, so on-hand,
 * lots, weighted average cost and the stock card stay consistent (the Laravel version only changed
 * the on-hand number).
 */
@RestController
@RequestMapping("/api/stock-adjustments")
public class AdjustmentController {

    private static final String TYPE = "App\\Models\\StockAdjustment";

    private final StockAdjustmentRepository adjustments;
    private final ItemRepository items;
    private final UserRepository users;
    private final StockLedger ledger;
    private final DocumentNumbers numbers;
    private final AuditService audit;
    private final CurrentUser current;

    public AdjustmentController(StockAdjustmentRepository adjustments, ItemRepository items, UserRepository users,
                                StockLedger ledger, DocumentNumbers numbers, AuditService audit, CurrentUser current) {
        this.adjustments = adjustments;
        this.items = items;
        this.users = users;
        this.ledger = ledger;
        this.numbers = numbers;
        this.audit = audit;
        this.current = current;
    }

    public record ItemRef(Long id, String code, String name, String unitOfMeasure, int currentStock) {
    }

    public record AdjustmentView(Long id, String adjustmentNumber, ItemRef item, String adjustmentType, int quantity,
                                 BigDecimal unitCost, String reason, String remarks, String status, Integer previousQuantity,
                                 Integer newQuantity, String requestedBy, Long requestedById, String approvedBy,
                                 OffsetDateTime approvedAt, OffsetDateTime createdAt, boolean canApprove, boolean canDelete) {
    }

    @GetMapping
    @PreAuthorize("@perm.any('create_adjustments', 'approve_adjustments')")
    @Transactional(readOnly = true)
    public PageResponse<AdjustmentView> index(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(name = "adjustment_type", required = false) String type,
                                              @RequestParam(name = "item_id", required = false) Long itemId) {
        Page<StockAdjustment> result = adjustments.search(blank(status), blank(type), itemId, PageRequest.of(Math.max(0, page - 1), 20));
        Map<StockAdjustment, AdjustmentView> v = views(result.getContent());
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.any('create_adjustments', 'approve_adjustments')")
    @Transactional(readOnly = true)
    public AdjustmentView show(@PathVariable Long id) {
        StockAdjustment a = adjustments.findById(id).orElseThrow(() -> ApiException.notFound("Adjustment"));
        return views(List.of(a)).get(a);
    }

    public record AdjustmentInput(Long itemId, String adjustmentType, Integer quantity, BigDecimal unitCost, String reason, String remarks) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_adjustments')")
    @Transactional
    public ResponseEntity<AdjustmentView> store(@RequestBody AdjustmentInput in) {
        if (in.itemId() == null) throw ApiException.validation("item_id", "Choose the item to adjust.");
        if (!Set.of(StockAdjustment.INCREASE, StockAdjustment.DECREASE).contains(in.adjustmentType())) {
            throw ApiException.validation("adjustment_type", "Choose increase or decrease.");
        }
        if (in.quantity() == null || in.quantity() < 1) throw ApiException.validation("quantity", "Enter a quantity of at least 1.");
        if (in.reason() == null || in.reason().isBlank()) throw ApiException.validation("reason", "Give the reason for the adjustment.");
        if (in.reason().length() > 255) throw ApiException.validation("reason", "Keep the reason under 255 characters; use remarks for detail.");
        if (in.unitCost() != null && in.unitCost().signum() < 0) throw ApiException.validation("unit_cost", "Unit cost cannot be negative.");
        Item item = items.findById(in.itemId()).orElseThrow(() -> ApiException.validation("item_id", "That item no longer exists."));
        boolean increase = StockAdjustment.INCREASE.equals(in.adjustmentType());
        if (!increase && in.quantity() > item.getCurrentStock()) {
            throw ApiException.validation("quantity", "Decrease (" + in.quantity() + ") exceeds the " + item.getCurrentStock() + " on hand.");
        }
        if (increase && in.unitCost() == null && item.getWeightedAverageCost().signum() == 0) {
            throw ApiException.validation("unit_cost", "This item has no cost history; enter the unit cost of the units being added.");
        }
        StockAdjustment a = adjustments.save(new StockAdjustment(numbers.yearly("ADJ", "stock_adjustments.adjustment_number"),
                item.getId(), in.adjustmentType(), in.quantity(), increase ? in.unitCost() : null, in.reason().trim(),
                blank(in.remarks()), current.user().getId(), item.getCurrentStock()));
        audit.record("created", TYPE, a.getId(), "Requested StockAdjustment " + a.getAdjustmentNumber(), null, snapshot(a));
        return ResponseEntity.status(HttpStatus.CREATED).body(views(List.of(a)).get(a));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@perm.any('approve_adjustments')")
    @Transactional
    public AdjustmentView approve(@PathVariable Long id) {
        User me = current.user();
        StockAdjustment a = adjustments.lockById(id).orElseThrow(() -> ApiException.notFound("Adjustment"));
        if (!StockAdjustment.PENDING.equals(a.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only pending adjustments can be approved.");
        if (me.getId().equals(a.getRequestedBy()) && !me.isSuperAdmin()) {
            throw ApiException.forbidden("You requested this adjustment, so someone else has to approve it.");
        }
        Item item = ledger.lock(a.getItemId());
        int before = item.getCurrentStock();
        String note = "Stock adjustment " + a.getAdjustmentNumber() + " (" + a.getAdjustmentType() + ") — " + a.getReason();
        if (a.isIncrease()) {
            Stock lot = new Stock();
            lot.setItemId(item.getId());
            lot.setQuantity(a.getQuantity());
            lot.setUnitCost((a.getUnitCost() != null ? a.getUnitCost() : item.getWeightedAverageCost()).setScale(2, RoundingMode.HALF_UP));
            lot.setReceivedDate(LocalDate.now(Times.ZONE));
            lot.setLocation("Stock adjustment " + a.getAdjustmentNumber());
            ledger.receive(item, lot);
            ledger.log(item, me.getId(), "adjustment_in", a.getQuantity(), before, TYPE, a.getId(), note);
        } else {
            if (a.getQuantity() > item.getCurrentStock()) {
                throw ApiException.of(HttpStatus.CONFLICT, "Stock has changed since this was requested: only " + item.getCurrentStock()
                        + " on hand, so a decrease of " + a.getQuantity() + " is no longer possible. Reject it and file a new one.");
            }
            ledger.writeOff(item, a.getQuantity());
            ledger.log(item, me.getId(), "adjustment_out", a.getQuantity(), before, TYPE, a.getId(), note);
        }
        a.decide(StockAdjustment.APPROVED, me.getId(), Times.now(), before, item.getCurrentStock());
        adjustments.flush();
        audit.record("updated", TYPE, a.getId(), "Approved StockAdjustment " + a.getAdjustmentNumber(),
                Map.of("status", "pending"), Map.of("status", "approved", "previous_quantity", before, "new_quantity", item.getCurrentStock()));
        return views(List.of(a)).get(a);
    }

    public record RejectInput(String remarks) {
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@perm.any('approve_adjustments')")
    @Transactional
    public AdjustmentView reject(@PathVariable Long id, @RequestBody(required = false) RejectInput body) {
        StockAdjustment a = adjustments.lockById(id).orElseThrow(() -> ApiException.notFound("Adjustment"));
        if (!StockAdjustment.PENDING.equals(a.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only pending adjustments can be rejected.");
        if (body != null && body.remarks() != null && !body.remarks().isBlank()) a.setRemarks(body.remarks().trim());
        a.decide(StockAdjustment.REJECTED, current.user().getId(), Times.now(), null, null);
        adjustments.flush();
        audit.record("updated", TYPE, a.getId(), "Rejected StockAdjustment " + a.getAdjustmentNumber(),
                Map.of("status", "pending"), Map.of("status", "rejected"));
        return views(List.of(a)).get(a);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('create_adjustments')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        StockAdjustment a = adjustments.lockById(id).orElseThrow(() -> ApiException.notFound("Adjustment"));
        if (!StockAdjustment.PENDING.equals(a.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only pending adjustments can be withdrawn.");
        if (!current.user().getId().equals(a.getRequestedBy()) && !current.superAdmin()) {
            throw ApiException.forbidden("Only the person who requested it can withdraw this adjustment.");
        }
        adjustments.delete(a);
        audit.record("deleted", TYPE, id, "Withdrew StockAdjustment " + a.getAdjustmentNumber(), snapshot(a), null);
        return Map.of("message", "Adjustment withdrawn.");
    }

    private Map<StockAdjustment, AdjustmentView> views(Collection<StockAdjustment> list) {
        Map<Long, Item> byItem = new HashMap<>();
        items.findAllById(list.stream().map(StockAdjustment::getItemId).collect(Collectors.toSet())).forEach(i -> byItem.put(i.getId(), i));
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().flatMap(a -> Stream.of(a.getRequestedBy(), a.getApprovedBy())).filter(Objects::nonNull)
                .collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        User me = current.user();
        boolean approver = current.any("approve_adjustments");
        Map<StockAdjustment, AdjustmentView> out = new LinkedHashMap<>();
        for (StockAdjustment a : list) {
            Item i = byItem.get(a.getItemId());
            boolean pending = StockAdjustment.PENDING.equals(a.getStatus());
            boolean mine = me.getId().equals(a.getRequestedBy());
            out.put(a, new AdjustmentView(a.getId(), a.getAdjustmentNumber(),
                    i == null ? null : new ItemRef(i.getId(), i.getCode(), i.getName(), i.getUnitOfMeasure(), i.getCurrentStock()),
                    a.getAdjustmentType(), a.getQuantity(), a.getUnitCost(), a.getReason(), a.getRemarks(), a.getStatus(),
                    a.getPreviousQuantity(), a.getNewQuantity(), names.get(a.getRequestedBy()), a.getRequestedBy(),
                    names.get(a.getApprovedBy()), Times.toOffset(a.getApprovedAt()), Times.toOffset(a.getCreatedAt()),
                    pending && approver && (!mine || me.isSuperAdmin()), pending && (mine || me.isSuperAdmin())));
        }
        return out;
    }

    private static Map<String, Object> snapshot(StockAdjustment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item_id", a.getItemId());
        m.put("adjustment_type", a.getAdjustmentType());
        m.put("quantity", a.getQuantity());
        m.put("reason", a.getReason());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

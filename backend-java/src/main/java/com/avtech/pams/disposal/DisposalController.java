package com.avtech.pams.disposal;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.property.DepreciationService;
import com.avtech.pams.property.Property;
import com.avtech.pams.property.PropertyRepository;
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
 * Disposal of unserviceable assets (IIRUP) and write-off of stock (WMR). draft → submitted →
 * approved / rejected → completed. Someone other than the requester approves.
 */
@RestController
@RequestMapping("/api/disposal/requests")
public class DisposalController {

    private static final String TYPE = "App\\Models\\DisposalRequest";
    static final Set<String> MODES = Set.of("condemnation", "destruction", "donation", "sale", "public_auction",
            "negotiated_sale", "barter", "transfer");
    private static final Set<String> SALES = Set.of("sale", "public_auction", "negotiated_sale");

    private final DisposalRepository disposals;
    private final PropertyRepository properties;
    private final ItemRepository items;
    private final UserRepository users;
    private final StockLedger ledger;
    private final DepreciationService depreciation;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;

    public DisposalController(DisposalRepository disposals, PropertyRepository properties, ItemRepository items, UserRepository users,
                              StockLedger ledger, DepreciationService depreciation, DocumentNumbers numbers,
                              NotificationService notifications, AuditService audit, CurrentUser current, JdbcTemplate jdbc) {
        this.disposals = disposals;
        this.properties = properties;
        this.items = items;
        this.users = users;
        this.ledger = ledger;
        this.depreciation = depreciation;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
    }

    public record Ref(Long id, String name) {
    }

    public record DisposalView(Long id, String disposalNumber, String category, String disposalType, String status, String reason,
                               String remarks, LocalDate requestDate, Ref requester, Ref approver, LocalDate approvalDate,
                               LocalDate disposalDate, Long propertyId, String tagNumber, String serialNumber, Long itemId,
                               String itemCode, String itemName, String unitOfMeasure, Integer quantity, BigDecimal unitCost,
                               BigDecimal value, BigDecimal bookValue, BigDecimal proceeds,
                               boolean canEdit, boolean canSubmit, boolean canDecide, boolean canComplete) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<DisposalView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String category, @RequestParam(required = false) String search) {
        // Reviewers see every request; people who can only file them see their own.
        Long only = null;
        if (!current.any("view_disposal", "approve_disposal")) {
            if (!current.any("create_disposal")) throw ApiException.forbidden("You do not have access to disposals.");
            only = current.user().getId();
        }
        Page<DisposalRequest> result = disposals.search(only, blank(status), blank(category), blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<DisposalRequest, DisposalView> v = views(result.getContent());
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public DisposalView show(@PathVariable Long id) {
        DisposalRequest d = disposals.findById(id).filter(x -> x.getDeletedAt() == null).orElseThrow(() -> ApiException.notFound("Disposal request"));
        if (!current.any("view_disposal", "approve_disposal") && !current.user().getId().equals(d.getRequestedBy())) {
            throw ApiException.forbidden("You can only view your own disposal requests.");
        }
        return views(List.of(d)).get(d);
    }

    public record DisposalInput(String disposalCategory, Long propertyId, Long itemId, Integer quantity, String disposalType,
                                String reason, String remarks) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_disposal')")
    @Transactional
    public ResponseEntity<DisposalView> create(@RequestBody DisposalInput in) {
        String category = in.disposalCategory() == null || in.disposalCategory().isBlank() ? "property" : in.disposalCategory();
        if (!Set.of("property", "inventory").contains(category)) throw ApiException.validation("disposal_category", "Choose an asset or stock disposal.");
        DisposalRequest d = new DisposalRequest(numbers.monthly("DSP", "disposal_requests.disposal_number"), category,
                current.user().getId(), LocalDate.now(Times.ZONE));
        apply(d, in);
        disposals.save(d);
        audit.record("created", TYPE, d.getId(), "Created disposal request " + d.getDisposalNumber(), null, snapshot(d));
        return ResponseEntity.status(HttpStatus.CREATED).body(views(List.of(d)).get(d));
    }

    /** Fixes the TOR/audit gap: drafts can be edited (the Vue app called this but Laravel had no route). */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('create_disposal')")
    @Transactional
    public DisposalView update(@PathVariable Long id, @RequestBody DisposalInput in) {
        DisposalRequest d = lock(id);
        ownDraft(d);
        Map<String, Object> before = snapshot(d);
        apply(d, in);
        disposals.flush();
        audit.record("updated", TYPE, d.getId(), "Edited disposal request " + d.getDisposalNumber(), before, snapshot(d));
        return views(List.of(d)).get(d);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('create_disposal')")
    @Transactional
    public Map<String, Object> withdraw(@PathVariable Long id) {
        DisposalRequest d = lock(id);
        ownDraft(d);
        d.setDeletedAt(Times.now());
        audit.record("deleted", TYPE, d.getId(), "Withdrew disposal request " + d.getDisposalNumber(), snapshot(d), null);
        return Map.of("message", "Draft withdrawn.");
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("@perm.any('create_disposal')")
    @Transactional
    public DisposalView submit(@PathVariable Long id) {
        DisposalRequest d = lock(id);
        ownDraft(d);
        d.setStatus(DisposalRequest.SUBMITTED);
        disposals.flush();
        audit.record("updated", TYPE, d.getId(), "Submitted disposal request " + d.getDisposalNumber(), Map.of("status", "draft"), Map.of("status", "submitted"));
        notifications.notifyPermission(List.of("approve_disposal"), current.user().getId(), "disposal", "Disposal awaiting approval",
                d.getDisposalNumber() + " was submitted for approval.", TYPE, d.getId());
        return views(List.of(d)).get(d);
    }

    public record DecisionInput(String remarks) {
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@perm.any('approve_disposal')")
    @Transactional
    public DisposalView approve(@PathVariable Long id, @RequestBody(required = false) DecisionInput in) {
        return decide(id, DisposalRequest.APPROVED, in);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@perm.any('approve_disposal')")
    @Transactional
    public DisposalView reject(@PathVariable Long id, @RequestBody(required = false) DecisionInput in) {
        return decide(id, DisposalRequest.REJECTED, in);
    }

    public record CompleteInput(BigDecimal proceeds, LocalDate disposalDate, String remarks) {
    }

    /** Carries out an approved disposal: the asset becomes disposed / the stock is written off. */
    @PostMapping("/{id}/complete")
    @PreAuthorize("@perm.any('approve_disposal')")
    @Transactional
    public DisposalView complete(@PathVariable Long id, @RequestBody(required = false) CompleteInput in) {
        DisposalRequest d = lock(id);
        if (!DisposalRequest.APPROVED.equals(d.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only approved disposals can be completed.");
        LocalDate date = in == null || in.disposalDate() == null ? LocalDate.now(Times.ZONE) : in.disposalDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("disposal_date", "The disposal date cannot be in the future.");
        BigDecimal proceeds = in == null ? null : in.proceeds();
        if (proceeds != null && proceeds.signum() < 0) throw ApiException.validation("proceeds", "Proceeds cannot be negative.");
        if (proceeds != null && !SALES.contains(d.getDisposalType())) throw ApiException.validation("proceeds", "Proceeds only apply to sales and auctions.");
        Long me = current.user().getId();

        if (d.isProperty()) {
            Property p = properties.lockById(d.getPropertyId()).orElseThrow(() -> ApiException.notFound("Asset"));
            if (p.isGone()) throw ApiException.of(HttpStatus.CONFLICT, "This asset has already been disposed.");
            if (p.getAssignedTo() != null) throw ApiException.of(HttpStatus.CONFLICT, "The asset was issued to someone again; record its return first.");
            d.setBookValue(depreciation.bookValue(p));
            p.setStatus(Property.DISPOSED);
            p.setCurrentBookValue(BigDecimal.ZERO.setScale(2));
            ledger.event(p.getItemId(), me, "disposal_completed", TYPE, d.getId(), "Disposal " + d.getDisposalNumber() + " completed for asset "
                    + p.getTagNumber() + " (" + d.getDisposalType().replace('_', ' ') + "), book value " + d.getBookValue());
        } else {
            Item item = ledger.lock(d.getItemId());
            if (d.getQuantity() > item.getCurrentStock()) {
                throw ApiException.of(HttpStatus.CONFLICT, "Only " + item.getCurrentStock() + " on hand now; the write-off of " + d.getQuantity() + " cannot be completed.");
            }
            int before = item.getCurrentStock();
            d.setBookValue(item.getWeightedAverageCost().multiply(BigDecimal.valueOf(d.getQuantity())).setScale(2, RoundingMode.HALF_UP));
            ledger.writeOff(item, d.getQuantity());
            ledger.log(item, me, "stock_out", d.getQuantity(), before, TYPE, d.getId(),
                    "Disposal write-off " + d.getDisposalNumber() + " (" + d.getDisposalType().replace('_', ' ') + "): " + d.getReason());
        }
        d.setProceeds(proceeds);
        d.setDisposalDate(date);
        if (in != null && in.remarks() != null && !in.remarks().isBlank()) d.setRemarks(in.remarks().trim());
        d.setStatus(DisposalRequest.COMPLETED);
        disposals.flush();
        audit.record("updated", TYPE, d.getId(), "Completed disposal " + d.getDisposalNumber(), Map.of("status", "approved"),
                Map.of("status", "completed", "book_value", String.valueOf(d.getBookValue()), "proceeds", String.valueOf(proceeds)));
        return views(List.of(d)).get(d);
    }

    // ───────────────────────── helpers ─────────────────────────

    private DisposalView decide(Long id, String outcome, DecisionInput in) {
        DisposalRequest d = lock(id);
        if (!DisposalRequest.SUBMITTED.equals(d.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only submitted disposals can be approved or rejected.");
        User me = current.user();
        if (me.getId().equals(d.getRequestedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You requested this disposal, so someone else has to decide on it.");
        d.setStatus(outcome);
        d.setApprovedBy(me.getId());
        d.setApprovalDate(LocalDate.now(Times.ZONE));
        if (in != null && in.remarks() != null && !in.remarks().isBlank()) d.setRemarks(in.remarks().trim());
        disposals.flush();
        audit.record("updated", TYPE, d.getId(), (outcome.equals(DisposalRequest.APPROVED) ? "Approved" : "Rejected") + " disposal " + d.getDisposalNumber(),
                Map.of("status", "submitted"), Map.of("status", outcome));
        notifications.notifyUser(d.getRequestedBy(), "disposal", "Disposal " + outcome, d.getDisposalNumber() + " was " + outcome + ".", TYPE, d.getId());
        return views(List.of(d)).get(d);
    }

    private void apply(DisposalRequest d, DisposalInput in) {
        if (in.disposalType() == null || !MODES.contains(in.disposalType())) throw ApiException.validation("disposal_type", "Choose the mode of disposal.");
        if (in.reason() == null || in.reason().isBlank()) throw ApiException.validation("reason", "Give the reason for disposal.");
        if (in.reason().length() > 500) throw ApiException.validation("reason", "Keep the reason under 500 characters; use remarks for detail.");
        if (d.isProperty()) {
            if (in.propertyId() == null) throw ApiException.validation("property_id", "Choose the asset.");
            Property p = properties.findById(in.propertyId()).orElseThrow(() -> ApiException.validation("property_id", "That asset no longer exists."));
            if (p.isGone()) throw ApiException.validation("property_id", "That asset has already been disposed.");
            if (p.getAssignedTo() != null) {
                throw ApiException.validation("property_id", "Asset " + p.getTagNumber() + " is still issued to someone; record its return (turn-in) first.");
            }
            Integer out = jdbc.queryForObject("select count(*) from gate_passes where property_id = ? and status <> 'returned' and deleted_at is null", Integer.class, p.getId());
            if (out != null && out > 0) throw ApiException.validation("property_id", "Asset " + p.getTagNumber() + " is out on a gate pass.");
            if (disposals.openForProperty(p.getId(), d.getId())) throw ApiException.validation("property_id", "Asset " + p.getTagNumber() + " already has an open disposal request.");
            d.setPropertyId(p.getId());
            d.setItemId(p.getItemId());
            d.setQuantity(1);
            d.setUnitCost(p.getAcquisitionCost());
        } else {
            if (in.itemId() == null) throw ApiException.validation("item_id", "Choose the item.");
            if (in.quantity() == null || in.quantity() < 1) throw ApiException.validation("quantity", "Enter a quantity of at least 1.");
            Item item = items.findById(in.itemId()).orElseThrow(() -> ApiException.validation("item_id", "That item no longer exists."));
            if (in.quantity() > item.getCurrentStock()) throw ApiException.validation("quantity", "Only " + item.getCurrentStock() + " " + item.getUnitOfMeasure() + " on hand.");
            d.setItemId(item.getId());
            d.setQuantity(in.quantity());
            d.setUnitCost(item.getWeightedAverageCost());
        }
        d.setDisposalType(in.disposalType());
        d.setReason(in.reason().trim());
        d.setRemarks(in.remarks() == null || in.remarks().isBlank() ? null : in.remarks().trim());
    }

    private DisposalRequest lock(Long id) {
        return disposals.lockById(id).orElseThrow(() -> ApiException.notFound("Disposal request"));
    }

    private void ownDraft(DisposalRequest d) {
        if (!DisposalRequest.DRAFT.equals(d.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only drafts can be changed.");
        if (!current.user().getId().equals(d.getRequestedBy()) && !current.superAdmin()) throw ApiException.forbidden("Only the person who drafted it can change it.");
    }

    private Map<DisposalRequest, DisposalView> views(Collection<DisposalRequest> list) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().flatMap(d -> Stream.of(d.getRequestedBy(), d.getApprovedBy())).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getId(), u.getName()));
        Map<Long, Item> byItem = new HashMap<>();
        items.findAllById(list.stream().map(DisposalRequest::getItemId).filter(Objects::nonNull).collect(Collectors.toSet())).forEach(i -> byItem.put(i.getId(), i));
        Map<Long, Property> byProp = new HashMap<>();
        properties.findAllById(list.stream().map(DisposalRequest::getPropertyId).filter(Objects::nonNull).collect(Collectors.toSet())).forEach(p -> byProp.put(p.getId(), p));
        User me = current.user();
        boolean approver = current.any("approve_disposal");
        Map<DisposalRequest, DisposalView> out = new LinkedHashMap<>();
        for (DisposalRequest d : list) {
            Item i = d.getItemId() == null ? null : byItem.get(d.getItemId());
            Property p = d.getPropertyId() == null ? null : byProp.get(d.getPropertyId());
            boolean mine = me.getId().equals(d.getRequestedBy());
            boolean draft = DisposalRequest.DRAFT.equals(d.getStatus());
            BigDecimal value = d.getUnitCost() == null || d.getQuantity() == null ? null
                    : d.getUnitCost().multiply(BigDecimal.valueOf(d.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            out.put(d, new DisposalView(d.getId(), d.getDisposalNumber(), d.getCategory(), d.getDisposalType(), d.getStatus(), d.getReason(),
                    d.getRemarks(), d.getRequestDate(), new Ref(d.getRequestedBy(), names.get(d.getRequestedBy())),
                    d.getApprovedBy() == null ? null : new Ref(d.getApprovedBy(), names.get(d.getApprovedBy())), d.getApprovalDate(),
                    d.getDisposalDate(), d.getPropertyId(), p == null ? null : p.getTagNumber(), p == null ? null : p.getSerialNumber(),
                    d.getItemId(), i == null ? null : i.getCode(), i == null ? null : i.getName(), i == null ? null : i.getUnitOfMeasure(),
                    d.getQuantity(), d.getUnitCost(), value, d.getBookValue(), d.getProceeds(),
                    draft && (mine || me.isSuperAdmin()), draft && (mine || me.isSuperAdmin()),
                    DisposalRequest.SUBMITTED.equals(d.getStatus()) && approver && (!mine || me.isSuperAdmin()),
                    DisposalRequest.APPROVED.equals(d.getStatus()) && approver));
        }
        return out;
    }

    private static Map<String, Object> snapshot(DisposalRequest d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", d.getCategory());
        m.put("property_id", d.getPropertyId());
        m.put("item_id", d.getItemId());
        m.put("quantity", d.getQuantity());
        m.put("disposal_type", d.getDisposalType());
        m.put("reason", d.getReason());
        m.put("status", d.getStatus());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

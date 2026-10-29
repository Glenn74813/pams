package com.avtech.pams.physicalcount;

import com.avtech.pams.adjustment.StockAdjustment;
import com.avtech.pams.adjustment.StockAdjustmentRepository;
import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.Stock;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.property.Property;
import com.avtech.pams.property.PropertyRepository;
import com.avtech.pams.user.User;
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

/**
 * Physical counts of stock-room supplies (RPCI), PPE (RPCPPE) and semi-expendable property (RPCSP).
 *
 * <p>Laravel captured the book quantity whenever a line was typed in and on verification overwrote
 * on-hand stock (clamped at zero) with self-approved adjustments that never touched the stock lots.
 * Here the book balances are frozen when counting starts, the variance against that cut-off is
 * posted through approved stock adjustments that move real lots, and the person who verifies the
 * count can be none of the people who initiated, counted or closed it. Asset counts go tag by tag:
 * found, missing, found but not in the masterlist, or on record under another department.
 */
@RestController
@RequestMapping("/api/physical-inventory/counts")
public class PhysicalCountController {

    private static final String TYPE = "App\\Models\\PhysicalInventoryCount";
    private static final String ADJUSTMENT = "App\\Models\\StockAdjustment";
    private static final Set<String> TYPES = Set.of(PhysicalCount.SUPPLIES, PhysicalCount.PPE, PhysicalCount.SEMI_EXPENDABLE);
    private static final Set<String> OBSERVED = Set.of("excellent", "good", "fair", "poor", "unserviceable");
    private static final Set<String> RECORDABLE = Set.of("excellent", "good", "fair", "poor");
    private static final String VIEW = "@perm.any('view_physical_inventory', 'create_physical_inventory', 'conduct_physical_inventory')";

    private final PhysicalCountRepository counts;
    private final CountLineRepository lines;
    private final CountPropertyRepository tags;
    private final PropertyRepository properties;
    private final StockAdjustmentRepository adjustments;
    private final StockLedger ledger;
    private final DocumentNumbers numbers;
    private final AuditService audit;
    private final NotificationService notifications;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final Db db;

    public PhysicalCountController(PhysicalCountRepository counts, CountLineRepository lines, CountPropertyRepository tags,
                                   PropertyRepository properties, StockAdjustmentRepository adjustments, StockLedger ledger,
                                   DocumentNumbers numbers, AuditService audit, NotificationService notifications,
                                   CurrentUser current, JdbcTemplate jdbc, Db db) {
        this.counts = counts;
        this.lines = lines;
        this.tags = tags;
        this.properties = properties;
        this.adjustments = adjustments;
        this.ledger = ledger;
        this.numbers = numbers;
        this.audit = audit;
        this.notifications = notifications;
        this.current = current;
        this.jdbc = jdbc;
        this.db = db;
    }

    public record Ref(Long id, String name) {
    }

    public record CountView(Long id, String countNumber, String countType, LocalDate countDate, String status, String purpose,
                            String remarks, Ref department, Ref category, Ref initiatedBy, OffsetDateTime startedAt,
                            Ref completedBy, OffsetDateTime completedAt, Ref verifiedBy, OffsetDateTime verifiedAt,
                            int totalItemsCounted, int totalItemsFound, int totalDiscrepancies, OffsetDateTime createdAt,
                            boolean canEdit, boolean canStart, boolean canCount, boolean canComplete, boolean canReturn,
                            boolean canVerify, boolean canDelete, String verifyBlocked) {
    }

    public record ItemRef(Long id, String code, String name, String unitOfMeasure, String category, int onHand) {
    }

    public record LineView(Long id, ItemRef item, int systemQuantity, Integer countedQuantity, Integer variance,
                           BigDecimal unitCost, BigDecimal varianceValue, String location, String remarks, Ref countedBy,
                           OffsetDateTime countedAt, int movedSinceCutoff, String adjustmentNumber) {
    }

    public record TagView(Long id, Long propertyId, String tagNumber, String description, Ref holder, Ref department,
                          BigDecimal bookValue, String conditionOnRecord, boolean inMasterlist, boolean inScope, String result,
                          String finding, String observedCondition, String location, String remarks, Ref checkedBy,
                          OffsetDateTime checkedAt, boolean removable) {
    }

    public record Summary(int lines, int counted, int matched, int shortCount, BigDecimal shortValue, int overCount, BigDecimal overValue,
                          int expected, int found, int missing, BigDecimal missingValue, int notInMasterlist, int outOfScope,
                          int conditionChanged, int unserviceable) {
    }

    public record Detail(CountView count, Summary summary, List<LineView> lines, List<TagView> properties) {
    }

    // ---------------------------------------------------------------- reading

    @GetMapping
    @PreAuthorize(VIEW)
    @Transactional(readOnly = true)
    public PageResponse<CountView> index(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(name = "count_type", required = false) String type,
                                         @RequestParam(required = false) String search) {
        Page<PhysicalCount> result = counts.search(blank(status), blank(type), blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<PhysicalCount, CountView> v = views(result.getContent());
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/options")
    @PreAuthorize(VIEW)
    @Transactional(readOnly = true)
    public Map<String, Object> options() {
        return Map.of(
                "departments", jdbc.query("select id, name from departments order by name", (rs, i) -> new Ref(rs.getLong(1), rs.getString(2))),
                "categories", jdbc.query("select id, name from categories order by name", (rs, i) -> new Ref(rs.getLong(1), rs.getString(2))));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    @Transactional(readOnly = true)
    public Detail show(@PathVariable Long id) {
        return detail(counts.findById(id).orElseThrow(() -> ApiException.notFound("Physical count")));
    }

    // ---------------------------------------------------------------- setting up

    public record CountInput(String countType, LocalDate countDate, Long departmentId, Long categoryId, String purpose, String remarks) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_physical_inventory')")
    @Transactional
    public ResponseEntity<Detail> store(@RequestBody CountInput in) {
        if (in.countType() == null || !TYPES.contains(in.countType())) {
            throw ApiException.validation("count_type", "Choose what is being counted: supplies, PPE or semi-expendable property.");
        }
        PhysicalCount c = new PhysicalCount(numbers.monthly("PIC", "physical_inventory_counts.count_number"), in.countType(), current.user().getId());
        apply(c, in);
        counts.save(c);
        audit.record("created", TYPE, c.getId(), "Created physical count " + c.getCountNumber(), null, snapshot(c));
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(c));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('create_physical_inventory')")
    @Transactional
    public Detail update(@PathVariable Long id, @RequestBody CountInput in) {
        PhysicalCount c = lock(id);
        if (!PhysicalCount.DRAFT.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft count can be edited.");
        Map<String, Object> before = snapshot(c);
        apply(c, in);
        counts.flush();
        audit.record("updated", TYPE, c.getId(), "Updated physical count " + c.getCountNumber(), before, snapshot(c));
        return detail(c);
    }

    private void apply(PhysicalCount c, CountInput in) {
        if (in.countDate() == null) throw ApiException.validation("count_date", "Enter the date of the count.");
        if (in.countDate().isBefore(LocalDate.now(Times.ZONE).minusYears(1))) {
            throw ApiException.validation("count_date", "The count date is more than a year ago.");
        }
        if (in.departmentId() != null && c.isSupplies()) {
            throw ApiException.validation("department_id", "Supplies are counted in the stock room; limit by category instead.");
        }
        if (in.categoryId() != null && !c.isSupplies()) {
            throw ApiException.validation("category_id", "Asset counts are limited by department, not category.");
        }
        if (in.departmentId() != null && !exists("departments", in.departmentId())) {
            throw ApiException.validation("department_id", "The selected department is invalid.");
        }
        if (in.categoryId() != null && !exists("categories", in.categoryId())) {
            throw ApiException.validation("category_id", "The selected category is invalid.");
        }
        c.setCountDate(in.countDate());
        c.setDepartmentId(in.departmentId());
        c.setCategoryId(in.categoryId());
        c.setPurpose(blank(in.purpose()));
        c.setRemarks(blank(in.remarks()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('create_physical_inventory')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        PhysicalCount c = lock(id);
        if (!Set.of(PhysicalCount.DRAFT, PhysicalCount.IN_PROGRESS).contains(c.getStatus())) {
            throw ApiException.of(HttpStatus.CONFLICT, "A completed count is part of the record and cannot be deleted.");
        }
        if (!current.user().getId().equals(c.getInitiatedBy()) && !current.superAdmin()) {
            throw ApiException.forbidden("Only the person who set up this count can delete it.");
        }
        counts.delete(c);
        audit.record("deleted", TYPE, id, "Deleted physical count " + c.getCountNumber(), snapshot(c), null);
        return Map.of("message", "Physical count deleted.");
    }

    /** Freezes the book: one line per stock-room item, or one line per tag on the asset masterlist. */
    @PostMapping("/{id}/start")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail start(@PathVariable Long id) {
        PhysicalCount c = lock(id);
        if (!PhysicalCount.DRAFT.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This count has already started.");
        if (c.getCountDate().isAfter(LocalDate.now(Times.ZONE))) {
            throw ApiException.of(HttpStatus.CONFLICT, "This count is scheduled for " + c.getCountDate() + "; it cannot start before then.");
        }
        LocalDateTime now = Times.now();
        c.setCutoffLogId(jdbc.queryForObject("select coalesce(max(id), 0) from inventory_logs", Long.class));
        int n;
        if (c.isSupplies()) {
            List<Object> args = new ArrayList<>(List.of(c.getId(), now, now));
            String scope = "";
            if (c.getCategoryId() != null) {
                List<Long> cats = categoryAndDescendants(c.getCategoryId());
                scope = " where i.category_id in (" + cats.stream().map(x -> "?").collect(Collectors.joining(", ")) + ")";
                args.addAll(cats);
            }
            n = jdbc.update("""
                    insert into physical_inventory_count_items
                        (physical_inventory_count_id, item_id, system_quantity, unit_cost, created_at, updated_at)
                    select ?, i.id, i.current_stock, i.weighted_average_cost, ?, ?
                    from items i""" + scope, args.toArray());
        } else {
            boolean ppe = PhysicalCount.PPE.equals(c.getCountType());
            n = jdbc.update("""
                    insert into physical_count_properties
                        (physical_inventory_count_id, property_id, tag_number, description, holder_id, department_id,
                         book_value, condition_on_record, created_at, updated_at)
                    select ?, p.id, p.tag_number, %s, p.assigned_to, u.department_id,
                           coalesce(p.current_book_value, p.acquisition_cost), p.condition, ?, ?
                    from properties p join items i on i.id = p.item_id left join users u on u.id = p.assigned_to
                    where p.status <> 'disposed' and %s and (cast(? as bigint) is null or u.department_id = ?)
                    """.formatted(DESCRIPTION, ppe ? "i.is_expandable = " + db.t()
                            : "i.is_semi_expandable = " + db.t() + " and i.is_expandable = " + db.f()),
                    c.getId(), now, now, c.getDepartmentId(), c.getDepartmentId());
        }
        if (n == 0) {
            throw ApiException.of(HttpStatus.CONFLICT, c.isSupplies()
                    ? "There are no items in that scope to count."
                    : "There are no assets on record in that scope to count.");
        }
        c.setStatus(PhysicalCount.IN_PROGRESS);
        c.setStartedAt(now);
        counts.flush();
        audit.record("updated", TYPE, c.getId(), "Started physical count " + c.getCountNumber() + " (" + n + " lines frozen at cut-off)",
                Map.of("status", "draft"), Map.of("status", "in_progress", "lines", n));
        return detail(c);
    }

    private static final String DESCRIPTION =
            "concat(i.name, case when p.brand <> '' then concat(' — ', p.brand) else '' end,"
            + " case when p.serial_number <> '' then concat(' (S/N ', p.serial_number, ')') else '' end)";

    // ---------------------------------------------------------------- counting

    public record LineInput(Long id, Integer countedQuantity, BigDecimal unitCost, String location, String remarks) {
    }

    public record RecordInput(List<LineInput> lines) {
    }

    @PostMapping("/{id}/record")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail record(@PathVariable Long id, @RequestBody RecordInput in) {
        PhysicalCount c = counting(id);
        if (!c.isSupplies()) throw ApiException.of(HttpStatus.CONFLICT, "Asset counts are recorded tag by tag.");
        if (in.lines() == null || in.lines().isEmpty()) throw ApiException.validation("lines", "Enter at least one counted quantity.");
        Long me = current.user().getId();
        LocalDateTime now = Times.now();
        for (int k = 0; k < in.lines().size(); k++) {
            LineInput li = in.lines().get(k);
            String f = "lines." + k + ".";
            CountLine line = li.id() == null ? null : lines.findByIdAndCountId(li.id(), c.getId()).orElse(null);
            if (line == null) throw ApiException.validation(f + "id", "That line is not on this count.");
            if (li.countedQuantity() != null && li.countedQuantity() < 0) {
                throw ApiException.validation(f + "counted_quantity", "A counted quantity cannot be negative.");
            }
            if (li.unitCost() != null) {
                if (li.unitCost().signum() < 0) throw ApiException.validation(f + "unit_cost", "Unit cost cannot be negative.");
                if (line.getUnitCost().signum() > 0 && li.unitCost().compareTo(line.getUnitCost()) != 0) {
                    throw ApiException.validation(f + "unit_cost", "This item already has a book cost (₱" + line.getUnitCost()
                            + "); only items with no cost history take a unit cost here.");
                }
                line.setUnitCost(li.unitCost().setScale(2, RoundingMode.HALF_UP));
            }
            boolean same = Objects.equals(line.getCountedQuantity(), li.countedQuantity());
            line.record(li.countedQuantity(), blank(li.location()), blank(li.remarks()),
                    same && line.getCountedBy() != null ? line.getCountedBy() : me, same && line.getCountedAt() != null ? line.getCountedAt() : now);
        }
        lines.flush();
        return detail(c);
    }

    public record ScanInput(String tagNumber, String observedCondition, String location, String remarks, String description) {
    }

    /** Marks a tag found. A tag not on this count's masterlist is added so it shows up in the results. */
    @PostMapping("/{id}/scan")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail scan(@PathVariable Long id, @RequestBody ScanInput in) {
        PhysicalCount c = counting(id);
        if (c.isSupplies()) throw ApiException.of(HttpStatus.CONFLICT, "Supplies are counted by quantity, not by tag.");
        String tag = blank(in.tagNumber());
        if (tag == null) throw ApiException.validation("tag_number", "Enter or scan the property tag number.");
        String condition = observed(in.observedCondition());
        CountProperty line = tags.findByCountIdAndTagNumberIgnoreCase(c.getId(), tag).orElse(null);
        if (line == null) {
            Property p = properties.findFirstByTagNumberIgnoreCase(tag).orElse(null);
            line = new CountProperty(c.getId(), p == null ? tag : p.getTagNumber());
            if (p == null) {
                String description = blank(in.description());
                if (description == null) {
                    throw ApiException.validation("description", "Tag " + tag + " is not on record. Describe the article so it can be reported as found but not in the masterlist.");
                }
                line.setInMasterlist(false);
                line.setDescription(description);
            } else {
                describe(line, p.getId());
                line.setInScope(false);
            }
        }
        line.check(CountProperty.FOUND, condition != null ? condition : line.getConditionOnRecord(), blank(in.location()),
                blank(in.remarks()) != null ? blank(in.remarks()) : line.getRemarks(), current.user().getId(), Times.now());
        tags.save(line);
        tags.flush();
        return detail(c);
    }

    public record TagInput(String result, String observedCondition, String location, String remarks) {
    }

    @PostMapping("/{id}/properties/{lineId}")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail mark(@PathVariable Long id, @PathVariable Long lineId, @RequestBody TagInput in) {
        PhysicalCount c = counting(id);
        CountProperty line = tags.findByIdAndCountId(lineId, c.getId()).orElseThrow(() -> ApiException.notFound("Count line"));
        if (!Set.of(CountProperty.PENDING, CountProperty.FOUND, CountProperty.MISSING).contains(in.result())) {
            throw ApiException.validation("result", "Mark the tag found, missing or not yet checked.");
        }
        if (!CountProperty.FOUND.equals(in.result()) && (!line.isInMasterlist() || !line.isInScope())) {
            throw ApiException.validation("result", "This tag was added because it was found; remove it instead.");
        }
        String condition = observed(in.observedCondition());
        if (CountProperty.FOUND.equals(in.result()) && condition == null) condition = line.getConditionOnRecord();
        line.check(in.result(), CountProperty.FOUND.equals(in.result()) ? condition : null,
                blank(in.location()), blank(in.remarks()), current.user().getId(), Times.now());
        tags.flush();
        return detail(c);
    }

    @DeleteMapping("/{id}/properties/{lineId}")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail unscan(@PathVariable Long id, @PathVariable Long lineId) {
        PhysicalCount c = counting(id);
        CountProperty line = tags.findByIdAndCountId(lineId, c.getId()).orElseThrow(() -> ApiException.notFound("Count line"));
        if (line.isInMasterlist() && line.isInScope()) {
            throw ApiException.of(HttpStatus.CONFLICT, "Masterlist tags stay on the count; mark it not yet checked instead.");
        }
        tags.delete(line);
        tags.flush();
        return detail(c);
    }

    // ---------------------------------------------------------------- closing

    @PostMapping("/{id}/complete")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail complete(@PathVariable Long id) {
        PhysicalCount c = counting(id);
        Long me = current.user().getId();
        LocalDateTime now = Times.now();
        if (c.isSupplies()) {
            List<CountLine> all = lines.findByCountIdOrderById(c.getId());
            long uncounted = all.stream().filter(l -> l.getCountedQuantity() == null).count();
            if (uncounted > 0) {
                throw ApiException.of(HttpStatus.CONFLICT, uncounted + " of " + all.size() + " lines have no counted quantity yet. Enter 0 for items not on the shelf.");
            }
            List<Long> costless = all.stream().filter(l -> l.variance() > 0 && l.getUnitCost().signum() == 0).map(CountLine::getItemId).toList();
            if (!costless.isEmpty()) {
                throw ApiException.of(HttpStatus.CONFLICT, "Overages on items with no cost history need a unit cost: " + itemCodes(costless) + ".");
            }
            int matched = (int) all.stream().filter(l -> l.variance() == 0).count();
            c.setTotals(all.size(), matched, all.size() - matched);
        } else {
            List<CountProperty> all = tags.findByCountIdOrderByTagNumber(c.getId());
            for (CountProperty t : all) {
                if (CountProperty.PENDING.equals(t.getResult())) t.check(CountProperty.MISSING, null, null, t.getRemarks(), null, null);
            }
            int found = (int) all.stream().filter(t -> CountProperty.FOUND.equals(t.getResult())).count();
            int discrepancies = (int) all.stream().filter(t -> !"found".equals(finding(t)) && !"condition_changed".equals(finding(t))).count();
            c.setTotals(all.size(), found, discrepancies);
        }
        c.setStatus(PhysicalCount.COMPLETED);
        c.complete(me, now);
        counts.flush();
        audit.record("updated", TYPE, c.getId(), "Completed physical count " + c.getCountNumber(),
                Map.of("status", "in_progress"), Map.of("status", "completed", "discrepancies", c.getTotalDiscrepancies()));
        notifications.notifyPermission(List.of("conduct_physical_inventory"), me, "physical_count_completed",
                "Physical count ready for verification",
                c.getCountNumber() + " is complete with " + c.getTotalDiscrepancies() + " discrepanc" + (c.getTotalDiscrepancies() == 1 ? "y" : "ies")
                        + ". Someone who did not take part in the count needs to verify it.",
                TYPE, c.getId());
        return detail(c);
    }

    public record ReturnInput(String remarks) {
    }

    /** Sends a completed count back for recounting. Tags marked missing only because they were never checked go back to pending. */
    @PostMapping("/{id}/return")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail sendBack(@PathVariable Long id, @RequestBody(required = false) ReturnInput in) {
        PhysicalCount c = lock(id);
        if (!PhysicalCount.COMPLETED.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a completed count can be sent back.");
        String reason = in == null ? null : blank(in.remarks());
        if (reason == null) throw ApiException.validation("remarks", "Say what needs to be recounted.");
        for (CountProperty t : tags.findByCountIdOrderByTagNumber(c.getId())) {
            if (CountProperty.MISSING.equals(t.getResult()) && t.getCheckedBy() == null) t.check(CountProperty.PENDING, null, null, t.getRemarks(), null, null);
        }
        c.setStatus(PhysicalCount.IN_PROGRESS);
        c.complete(null, null);
        c.setRemarks(join(c.getRemarks(), "Sent back for recount: " + reason));
        counts.flush();
        audit.record("updated", TYPE, c.getId(), "Sent physical count " + c.getCountNumber() + " back for recount",
                Map.of("status", "completed"), Map.of("status", "in_progress", "reason", reason));
        if (!current.user().getId().equals(c.getInitiatedBy())) {
            notifications.notifyUser(c.getInitiatedBy(), "physical_count_returned", "Physical count sent back",
                    c.getCountNumber() + " was sent back for recount: " + reason, TYPE, c.getId());
        }
        return detail(c);
    }

    /**
     * Posts the count. Supply variances become approved stock adjustments against the cut-off balance
     * (so issues and receipts made during the count are kept), moving lots through the ledger. For
     * assets, the condition seen on the floor becomes the condition on record.
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("@perm.any('conduct_physical_inventory')")
    @Transactional
    public Detail verify(@PathVariable Long id) {
        PhysicalCount c = lock(id);
        if (!PhysicalCount.COMPLETED.equals(c.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a completed count can be verified.");
        User me = current.user();
        String blocked = verifyBlocked(c, me);
        if (blocked != null) throw ApiException.forbidden(blocked);
        LocalDateTime now = Times.now();
        int posted = 0;
        if (c.isSupplies()) {
            for (CountLine l : lines.findByCountIdOrderById(c.getId())) {
                int variance = l.variance();
                if (variance == 0) continue;
                Item item = ledger.lock(l.getItemId());
                int before = item.getCurrentStock();
                boolean increase = variance > 0;
                int qty = Math.abs(variance);
                if (!increase && qty > before) {
                    throw ApiException.of(HttpStatus.CONFLICT, item.getCode() + " " + item.getName() + " is short " + qty
                            + " against the cut-off, but only " + before + " are on hand now. Check the movements made during the count, then send it back for recount.");
                }
                String reason = "Physical count " + c.getCountNumber() + ": " + (increase ? "overage" : "shortage") + " of " + qty;
                StockAdjustment a = adjustments.save(new StockAdjustment(numbers.yearly("ADJ", "stock_adjustments.adjustment_number"),
                        item.getId(), increase ? StockAdjustment.INCREASE : StockAdjustment.DECREASE, qty,
                        increase ? l.getUnitCost() : null, reason, l.getRemarks(), c.getCompletedBy(), before));
                String note = "Stock adjustment " + a.getAdjustmentNumber() + " — " + reason;
                if (increase) {
                    Stock lot = new Stock();
                    lot.setItemId(item.getId());
                    lot.setQuantity(qty);
                    lot.setUnitCost(l.getUnitCost().signum() > 0 ? l.getUnitCost() : item.getWeightedAverageCost().setScale(2, RoundingMode.HALF_UP));
                    lot.setReceivedDate(c.getCountDate());
                    lot.setLocation(l.getLocation() != null ? l.getLocation() : "Physical count " + c.getCountNumber());
                    ledger.receive(item, lot);
                    ledger.log(item, me.getId(), "adjustment_in", qty, before, ADJUSTMENT, a.getId(), note);
                } else {
                    ledger.writeOff(item, qty);
                    ledger.log(item, me.getId(), "adjustment_out", qty, before, ADJUSTMENT, a.getId(), note);
                }
                a.decide(StockAdjustment.APPROVED, me.getId(), now, before, item.getCurrentStock());
                l.setStockAdjustmentId(a.getId());
                audit.record("created", ADJUSTMENT, a.getId(), "Posted StockAdjustment " + a.getAdjustmentNumber() + " from physical count " + c.getCountNumber(),
                        null, Map.of("item_id", item.getId(), "adjustment_type", a.getAdjustmentType(), "quantity", qty,
                                "previous_quantity", before, "new_quantity", item.getCurrentStock()));
                posted++;
            }
        } else {
            for (CountProperty t : tags.findByCountIdOrderByTagNumber(c.getId())) {
                if (t.getPropertyId() == null || !CountProperty.FOUND.equals(t.getResult()) || !RECORDABLE.contains(t.getObservedCondition())) continue;
                Property p = properties.lockById(t.getPropertyId()).orElse(null);
                if (p == null || t.getObservedCondition().equals(p.getCondition())) continue;
                String was = p.getCondition();
                p.setCondition(t.getObservedCondition());
                audit.record("updated", "App\\Models\\Property", p.getId(), "Condition of " + p.getTagNumber() + " updated by physical count " + c.getCountNumber(),
                        Map.of("condition", was), Map.of("condition", p.getCondition()));
                posted++;
            }
        }
        c.setStatus(PhysicalCount.VERIFIED);
        c.verify(me.getId(), now);
        counts.flush();
        audit.record("updated", TYPE, c.getId(), "Verified physical count " + c.getCountNumber(),
                Map.of("status", "completed"), Map.of("status", "verified", c.isSupplies() ? "adjustments_posted" : "conditions_updated", posted));
        if (!me.getId().equals(c.getInitiatedBy())) {
            notifications.notifyUser(c.getInitiatedBy(), "physical_count_verified", "Physical count verified",
                    c.getCountNumber() + " was verified by " + me.getName() + ".", TYPE, c.getId());
        }
        return detail(c);
    }

    /** Why the given user may not verify this count, or null. Super admin is exempt. */
    private String verifyBlocked(PhysicalCount c, User me) {
        if (me.isSuperAdmin()) return null;
        if (me.getId().equals(c.getInitiatedBy())) return "You set up this count, so someone else has to verify it.";
        if (me.getId().equals(c.getCompletedBy())) return "You closed this count, so someone else has to verify it.";
        Integer counted = jdbc.queryForObject("""
                select (select count(*) from physical_inventory_count_items where physical_inventory_count_id = ? and counted_by = ?)
                     + (select count(*) from physical_count_properties where physical_inventory_count_id = ? and checked_by = ?)
                """, Integer.class, c.getId(), me.getId(), c.getId(), me.getId());
        return counted != null && counted > 0 ? "You took part in this count, so someone else has to verify it." : null;
    }

    // ---------------------------------------------------------------- helpers

    private PhysicalCount lock(Long id) {
        return counts.lockById(id).orElseThrow(() -> ApiException.notFound("Physical count"));
    }

    private PhysicalCount counting(Long id) {
        PhysicalCount c = lock(id);
        if (!PhysicalCount.IN_PROGRESS.equals(c.getStatus())) {
            throw ApiException.of(HttpStatus.CONFLICT, PhysicalCount.DRAFT.equals(c.getStatus())
                    ? "Start the count first; that freezes the book balances."
                    : "Counting on this count is closed.");
        }
        return c;
    }

    private String observed(String condition) {
        String v = blank(condition);
        if (v != null && !OBSERVED.contains(v)) throw ApiException.validation("observed_condition", "Choose excellent, good, fair, poor or unserviceable.");
        return v;
    }

    private void describe(CountProperty line, Long propertyId) {
        jdbc.query("""
                select p.id, %s, p.assigned_to, u.department_id, coalesce(p.current_book_value, p.acquisition_cost), p.condition, p.status
                from properties p join items i on i.id = p.item_id left join users u on u.id = p.assigned_to where p.id = ?
                """.formatted(DESCRIPTION), rs -> {
            line.setPropertyId(rs.getLong(1));
            line.setDescription(rs.getString(2) + ("disposed".equals(rs.getString(7)) ? " [disposed on record]" : ""));
            line.setHolderId(rs.getObject(3, Long.class));
            line.setDepartmentId(rs.getObject(4, Long.class));
            line.setBookValue(rs.getBigDecimal(5));
            line.setConditionOnRecord(rs.getString(6));
        }, propertyId);
    }

    /** What a tag line means for the report. */
    static String finding(CountProperty t) {
        if (!t.isInMasterlist()) return "not_in_masterlist";
        if (!t.isInScope()) return "out_of_scope";
        if (CountProperty.MISSING.equals(t.getResult())) return "missing";
        if (CountProperty.PENDING.equals(t.getResult())) return "pending";
        if (t.getObservedCondition() != null && !t.getObservedCondition().equals(t.getConditionOnRecord())) return "condition_changed";
        return "found";
    }

    private Detail detail(PhysicalCount c) {
        CountView view = views(List.of(c)).get(c);
        Set<Long> userIds = new HashSet<>();
        List<CountLine> ls = c.isSupplies() ? lines.findByCountIdOrderById(c.getId()) : List.of();
        List<CountProperty> ts = c.isSupplies() ? List.of() : tags.findByCountIdOrderByTagNumber(c.getId());
        ls.forEach(l -> userIds.add(l.getCountedBy()));
        ts.forEach(t -> {
            userIds.add(t.getCheckedBy());
            userIds.add(t.getHolderId());
        });
        Map<Long, String> names = names(userIds);

        List<LineView> lineViews = new ArrayList<>();
        int counted = 0, matched = 0, shortN = 0, overN = 0;
        BigDecimal shortV = BigDecimal.ZERO, overV = BigDecimal.ZERO;
        if (!ls.isEmpty()) {
            Map<Long, ItemRef> items = new HashMap<>();
            jdbc.query("""
                    select i.id, i.code, i.name, i.unit_of_measure, c.name, i.current_stock
                    from items i left join categories c on c.id = i.category_id
                    where i.id in (select item_id from physical_inventory_count_items where physical_inventory_count_id = ?)
                    """, rs -> {
                items.put(rs.getLong(1), new ItemRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6)));
            }, c.getId());
            Map<Long, Integer> moved = new HashMap<>();
            if (c.getCutoffLogId() != null) {
                // Net stock-card movement after the cut-off, leaving out this count's own postings. Stored times are
                // rounded to the second, so the verification time gets a second of slack.
                List<Object> args = new ArrayList<>(List.of(c.getCutoffLogId(), c.getId()));
                String until = "";
                if (c.getVerifiedAt() != null) {
                    until = " and l.created_at <= ?";
                    args.add(java.sql.Timestamp.valueOf(c.getVerifiedAt().plusSeconds(1)));
                }
                args.add(ADJUSTMENT);
                args.add(c.getId());
                jdbc.query("""
                        select l.item_id, sum(l.new_quantity - l.previous_quantity) from inventory_logs l
                        where l.id > ? and l.item_id in (select item_id from physical_inventory_count_items where physical_inventory_count_id = ?)"""
                        + until + " " + """

                          and not (l.reference_type = ? and l.reference_id in
                                   (select stock_adjustment_id from physical_inventory_count_items where physical_inventory_count_id = ? and stock_adjustment_id is not null))
                        group by l.item_id
                        """, rs -> {
                    moved.put(rs.getLong(1), rs.getInt(2));
                }, args.toArray());
            }
            Map<Long, String> adjNumbers = new HashMap<>();
            Set<Long> adjIds = ls.stream().map(CountLine::getStockAdjustmentId).filter(Objects::nonNull).collect(Collectors.toSet());
            adjustments.findAllById(adjIds).forEach(a -> adjNumbers.put(a.getId(), a.getAdjustmentNumber()));
            for (CountLine l : ls) {
                Integer v = l.variance();
                BigDecimal value = v == null ? null : l.getUnitCost().multiply(BigDecimal.valueOf(v)).setScale(2, RoundingMode.HALF_UP);
                if (v != null) {
                    counted++;
                    if (v == 0) matched++;
                    else if (v < 0) { shortN++; shortV = shortV.add(value.negate()); }
                    else { overN++; overV = overV.add(value); }
                }
                lineViews.add(new LineView(l.getId(), items.get(l.getItemId()), l.getSystemQuantity(), l.getCountedQuantity(), v,
                        l.getUnitCost(), value, l.getLocation(), l.getRemarks(), ref(l.getCountedBy(), names),
                        Times.toOffset(l.getCountedAt()), moved.getOrDefault(l.getItemId(), 0), adjNumbers.get(l.getStockAdjustmentId())));
            }
        }

        List<TagView> tagViews = new ArrayList<>();
        int expected = 0, found = 0, missing = 0, notListed = 0, outside = 0, changed = 0, unserviceable = 0;
        BigDecimal missingV = BigDecimal.ZERO;
        if (!ts.isEmpty()) {
            Map<Long, String> departments = new HashMap<>();
            jdbc.query("select id, name from departments", rs -> {
                departments.put(rs.getLong(1), rs.getString(2));
            });
            boolean open = PhysicalCount.IN_PROGRESS.equals(c.getStatus());
            for (CountProperty t : ts) {
                String f = finding(t);
                if (t.isInMasterlist() && t.isInScope()) expected++;
                switch (f) {
                    case "found" -> found++;
                    case "condition_changed" -> { found++; changed++; }
                    case "missing" -> { missing++; if (t.getBookValue() != null) missingV = missingV.add(t.getBookValue()); }
                    case "not_in_masterlist" -> notListed++;
                    case "out_of_scope" -> outside++;
                    default -> { }
                }
                if ("unserviceable".equals(t.getObservedCondition())) unserviceable++;
                tagViews.add(new TagView(t.getId(), t.getPropertyId(), t.getTagNumber(), t.getDescription(), ref(t.getHolderId(), names),
                        ref(t.getDepartmentId(), departments), t.getBookValue(), t.getConditionOnRecord(), t.isInMasterlist(), t.isInScope(),
                        t.getResult(), f, t.getObservedCondition(), t.getLocation(), t.getRemarks(), ref(t.getCheckedBy(), names),
                        Times.toOffset(t.getCheckedAt()), open && (!t.isInMasterlist() || !t.isInScope())));
            }
        }
        Summary summary = new Summary(ls.size(), counted, matched, shortN, shortV, overN, overV,
                expected, found, missing, missingV, notListed, outside, changed, unserviceable);
        return new Detail(view, summary, lineViews, tagViews);
    }

    private Map<PhysicalCount, CountView> views(Collection<PhysicalCount> list) {
        Set<Long> userIds = new HashSet<>();
        list.forEach(c -> {
            userIds.add(c.getInitiatedBy());
            userIds.add(c.getCompletedBy());
            userIds.add(c.getVerifiedBy());
        });
        Map<Long, String> names = names(userIds);
        Map<Long, String> departments = lookup("departments", list.stream().map(PhysicalCount::getDepartmentId).collect(Collectors.toSet()));
        Map<Long, String> categories = lookup("categories", list.stream().map(PhysicalCount::getCategoryId).collect(Collectors.toSet()));
        User me = current.user();
        boolean creator = current.any("create_physical_inventory");
        boolean conductor = current.any("conduct_physical_inventory");
        Map<PhysicalCount, CountView> out = new LinkedHashMap<>();
        for (PhysicalCount c : list) {
            boolean draft = PhysicalCount.DRAFT.equals(c.getStatus());
            boolean counting = PhysicalCount.IN_PROGRESS.equals(c.getStatus());
            boolean completed = PhysicalCount.COMPLETED.equals(c.getStatus());
            boolean owner = me.getId().equals(c.getInitiatedBy()) || me.isSuperAdmin();
            String blocked = completed ? verifyBlocked(c, me) : null;
            out.put(c, new CountView(c.getId(), c.getCountNumber(), c.getCountType(), c.getCountDate(), c.getStatus(), c.getPurpose(),
                    c.getRemarks(), ref(c.getDepartmentId(), departments), ref(c.getCategoryId(), categories),
                    ref(c.getInitiatedBy(), names), Times.toOffset(c.getStartedAt()), ref(c.getCompletedBy(), names),
                    Times.toOffset(c.getCompletedAt()), ref(c.getVerifiedBy(), names), Times.toOffset(c.getVerifiedAt()),
                    c.getTotalItemsCounted(), c.getTotalItemsFound(), c.getTotalDiscrepancies(), Times.toOffset(c.getCreatedAt()),
                    draft && creator, draft && conductor, counting && conductor, counting && conductor, completed && conductor,
                    completed && conductor && blocked == null, (draft || counting) && creator && owner, blocked));
        }
        return out;
    }

    private Map<Long, String> names(Set<Long> ids) {
        return lookup("users", ids);
    }

    private Map<Long, String> lookup(String table, Set<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        List<Long> clean = ids.stream().filter(Objects::nonNull).toList();
        if (clean.isEmpty()) return out;
        String in = clean.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbc.query("select id, name from " + table + " where id in (" + in + ")", rs -> {
            out.put(rs.getLong(1), rs.getString(2));
        });
        return out;
    }

    private String itemCodes(List<Long> itemIds) {
        String in = itemIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return String.join(", ", jdbc.queryForList("select code from items where id in (" + in + ") order by code", String.class));
    }

    /** The category and every category below it (the tree is small, so it is walked in memory). */
    private List<Long> categoryAndDescendants(Long root) {
        Map<Long, List<Long>> children = new HashMap<>();
        jdbc.query("select id, parent_id from categories where parent_id is not null", rs -> {
            children.computeIfAbsent(rs.getLong(2), k -> new ArrayList<>()).add(rs.getLong(1));
        });
        List<Long> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        java.util.ArrayDeque<Long> todo = new java.util.ArrayDeque<>(List.of(root));
        while (!todo.isEmpty()) {
            Long id = todo.pop();
            if (!seen.add(id)) continue;
            out.add(id);
            todo.addAll(children.getOrDefault(id, List.of()));
        }
        return out;
    }

    private boolean exists(String table, Long id) {
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id, names.get(id));
    }

    private static Map<String, Object> snapshot(PhysicalCount c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count_type", c.getCountType());
        m.put("count_date", String.valueOf(c.getCountDate()));
        m.put("department_id", c.getDepartmentId());
        m.put("category_id", c.getCategoryId());
        m.put("purpose", c.getPurpose());
        return m;
    }

    private static String join(String a, String b) {
        return a == null || a.isBlank() ? b : a + "\n" + b;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.avtech.pams.property;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.Stock;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The accountability life of an asset. Every change of hands produces a numbered document:
 * registration/re-issue a PAR (PPE) or ICS (semi-expendable), a transfer a PTR, and a return a
 * PRS (PPE) or RRSP (semi-expendable) — and is kept in the transfer history.
 */
@Service
public class PropertyService {

    static final String TYPE = "App\\Models\\Property";
    // Numbers stay taken after an asset moves on, so the history table is part of the series too.
    private static final String[] ACCOUNTABILITY_COLS = {"requests.par_number", "requests.ics_number", "properties.accountability_number",
            "property_transfers.reference_number"};

    private final PropertyRepository properties;
    private final PropertyTransferRepository transfers;
    private final StockLedger ledger;
    private final DocumentNumbers numbers;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;

    public PropertyService(PropertyRepository properties, PropertyTransferRepository transfers, StockLedger ledger, DocumentNumbers numbers, UserRepository users, NotificationService notifications,
                           AuditService audit, CurrentUser current, JdbcTemplate jdbc) {
        this.properties = properties;
        this.transfers = transfers;
        this.ledger = ledger;
        this.numbers = numbers;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
    }

    public record UnitInput(String serialNumber, String tagNumber, String condition) {
    }

    public record RegisterInput(Long itemId, Long stockId, Long assignedTo, LocalDate assignedDate, String condition,
                                Integer usefulLifeYears, BigDecimal salvageValue, List<UnitInput> units) {
    }

    public record Registered(String accountabilityNumber, List<Property> properties) {
    }

    /** Moves units out of a stock lot into accountable property under one PAR or ICS. */
    @Transactional
    public Registered register(RegisterInput in) {
        if (in.itemId() == null) throw ApiException.validation("item_id", "Choose the item.");
        if (in.stockId() == null) throw ApiException.validation("stock_id", "Choose the stock lot the units come from.");
        if (in.assignedTo() == null) throw ApiException.validation("assigned_to", "Choose who will be accountable for the asset.");
        if (in.units() == null || in.units().isEmpty()) throw ApiException.validation("units", "Add at least one unit.");
        String condition = condition(in.condition(), "good");
        if (!users.existsById(in.assignedTo())) throw ApiException.validation("assigned_to", "That person no longer exists.");
        int life = in.usefulLifeYears() == null ? 5 : in.usefulLifeYears();
        if (life < 1 || life > 50) throw ApiException.validation("useful_life_years", "Useful life must be between 1 and 50 years.");
        if (in.salvageValue() != null && in.salvageValue().signum() < 0) throw ApiException.validation("salvage_value", "Salvage value cannot be negative.");

        Item item = ledger.lock(in.itemId());
        if (!item.isPpe() && !item.isSemiExpendable()) {
            throw ApiException.validation("item_id", "Only PPE and semi-expendable items are registered as property; consumables are issued through requests.");
        }
        if (item.isAssembly()) {
            throw ApiException.validation("item_id", "Assembled units (e.g. a clone PC) are still registered in the current system.");
        }

        Set<String> tags = new HashSet<>();
        Set<String> serials = new HashSet<>();
        for (UnitInput u : in.units()) {
            if (u.tagNumber() != null && !u.tagNumber().isBlank()) {
                String t = u.tagNumber().trim();
                if (!tags.add(t.toLowerCase()) || properties.existsByTagNumberIgnoreCase(t)) {
                    throw ApiException.validation("units", "Tag number " + t + " is already used.");
                }
            }
            if (u.serialNumber() != null && !u.serialNumber().isBlank()) {
                String s = u.serialNumber().trim();
                if (!serials.add(s.toLowerCase()) || properties.existsBySerialNumberIgnoreCase(s)) {
                    throw ApiException.validation("units", "Serial number " + s + " is already registered.");
                }
            }
            if (u.condition() != null && !u.condition().isBlank()) condition(u.condition(), null);
        }

        int qty = in.units().size();
        int before = item.getCurrentStock();
        Stock lot = ledger.takeFromLot(item, in.stockId(), qty);
        String doc = numbers.monthly(item.isPpe() ? "PAR" : "ICS", ACCOUNTABILITY_COLS);
        LocalDate date = in.assignedDate() == null ? LocalDate.now(Times.ZONE) : in.assignedDate();

        List<Property> created = new ArrayList<>();
        for (UnitInput u : in.units()) {
            Property p = new Property();
            p.setItemId(item.getId());
            p.setStockId(lot.getId());
            p.setTagNumber(u.tagNumber() != null && !u.tagNumber().isBlank() ? u.tagNumber().trim() : numbers.yearly("PROP", "properties.tag_number"));
            p.setSerialNumber(blank(u.serialNumber()));
            p.setBrand(lot.getBrandName());
            p.setSpecifications(lot.getSpecifications());
            p.setWarranty(lot.getWarranty());
            p.setWarrantyMonths(lot.getWarrantyMonths());
            p.setAssignedTo(in.assignedTo());
            p.setAssignedDate(date);
            p.setAccountabilityNumber(doc);
            p.setStatus(Property.ASSIGNED);
            p.setCondition(u.condition() != null && !u.condition().isBlank() ? u.condition() : condition);
            p.setAcquisitionCost(lot.getUnitCost());
            p.setAcquisitionDate(lot.getReceivedDate());
            p.setUsefulLifeYears(life);
            p.setSalvageValue(in.salvageValue());
            // Semi-expendable is expensed on issue; only PPE depreciates.
            p.setDepreciable(item.isPpe());
            p.setCurrentBookValue(lot.getUnitCost());
            properties.saveAndFlush(p);
            // The original PAR/ICS opens the asset's accountability history.
            transfers.save(new PropertyTransfer(p.getId(), PropertyTransfer.REISSUE, null, in.assignedTo(), date,
                    "Registered from stock", current.user().getId(), doc));
            created.add(p);
        }
        ledger.log(item, current.user().getId(), "stock_out", qty, before, TYPE, created.get(0).getId(),
                "Registered " + qty + " unit(s) as property under " + doc + (lot.getBrandName() == null ? "" : " (" + lot.getBrandName() + ")"));
        audit.record("created", TYPE, created.get(0).getId(), "Registered " + qty + " " + item.getName() + " under " + doc, null,
                Map.of("tags", created.stream().map(Property::getTagNumber).toList(), "assigned_to", in.assignedTo(), "document", doc));
        notifications.notifyUser(in.assignedTo(), "property_transfer_in", "Property issued to you",
                qty + " " + item.getName() + " issued to you under " + doc + ".", TYPE, created.get(0).getId());
        return new Registered(doc, created);
    }

    public record DetailsInput(String tagNumber, String serialNumber, String brand, String specifications, String warranty,
                               Integer warrantyMonths, String condition, Integer usefulLifeYears, BigDecimal salvageValue) {
    }

    /** Descriptive details only; who holds it and its status change through their own actions. */
    @Transactional
    public Property update(Long id, DetailsInput in) {
        Property p = lock(id);
        if (p.isGone()) throw ApiException.of(HttpStatus.CONFLICT, "Disposed assets cannot be edited.");
        Map<String, Object> before = snapshot(p);
        if (in.tagNumber() != null && !in.tagNumber().isBlank() && !in.tagNumber().trim().equalsIgnoreCase(p.getTagNumber())) {
            if (properties.existsByTagNumberIgnoreCaseAndIdNot(in.tagNumber().trim(), id)) throw ApiException.validation("tag_number", "That tag number is already used.");
            p.setTagNumber(in.tagNumber().trim());
        }
        if (in.serialNumber() != null) {
            String s = blank(in.serialNumber());
            if (s != null && properties.existsBySerialNumberIgnoreCaseAndIdNot(s, id)) throw ApiException.validation("serial_number", "That serial number is already registered.");
            p.setSerialNumber(s);
        }
        if (in.brand() != null) p.setBrand(blank(in.brand()));
        if (in.specifications() != null) p.setSpecifications(blank(in.specifications()));
        if (in.warranty() != null) p.setWarranty(blank(in.warranty()));
        if (in.warrantyMonths() != null) {
            if (in.warrantyMonths() < 0) throw ApiException.validation("warranty_months", "Warranty months cannot be negative.");
            p.setWarrantyMonths(in.warrantyMonths());
        }
        if (in.condition() != null && !in.condition().isBlank()) p.setCondition(condition(in.condition(), null));
        boolean lifeChange = in.usefulLifeYears() != null && in.usefulLifeYears() != p.getUsefulLifeYears();
        boolean salvageChange = in.salvageValue() != null && (p.getSalvageValue() == null || in.salvageValue().compareTo(p.getSalvageValue()) != 0);
        if (lifeChange || salvageChange) {
            if (p.getAccumulatedDepreciation().signum() > 0) {
                throw ApiException.of(HttpStatus.CONFLICT, "Depreciation has already been posted for this asset, so its useful life and salvage value are fixed.");
            }
            if (lifeChange) {
                if (in.usefulLifeYears() < 1 || in.usefulLifeYears() > 50) throw ApiException.validation("useful_life_years", "Useful life must be between 1 and 50 years.");
                p.setUsefulLifeYears(in.usefulLifeYears());
            }
            if (salvageChange) {
                if (in.salvageValue().signum() < 0) throw ApiException.validation("salvage_value", "Salvage value cannot be negative.");
                p.setSalvageValue(in.salvageValue());
            }
        }
        properties.flush();
        changed(p, "Updated asset " + p.getTagNumber(), before);
        return p;
    }

    public record MoveInput(Long toUser, String reason, LocalDate date, String condition) {
    }

    /** PTR: from the current holder to someone else. */
    @Transactional
    public PropertyTransfer transfer(Long id, MoveInput in) {
        Property p = lock(id);
        if (p.getAssignedTo() == null) throw ApiException.of(HttpStatus.CONFLICT, "This asset has no holder; issue it instead of transferring it.");
        if (!Property.ASSIGNED.equals(p.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only assets in use can be transferred (this one is " + p.getStatus().replace('_', ' ') + ").");
        requireReason(in);
        if (in.toUser() == null || !users.existsById(in.toUser())) throw ApiException.validation("to_user", "Choose who the asset goes to.");
        if (in.toUser().equals(p.getAssignedTo())) throw ApiException.validation("to_user", "The asset is already with that person.");
        Long from = p.getAssignedTo();
        String ref = numbers.monthly("PTR", "property_transfers.reference_number");
        LocalDate date = date(in);
        PropertyTransfer t = transfers.save(new PropertyTransfer(p.getId(), PropertyTransfer.TRANSFER, from, in.toUser(), date, in.reason().trim(), current.user().getId(), ref));
        p.setAssignedTo(in.toUser());
        p.setAssignedDate(date);
        p.setAccountabilityNumber(ref);
        if (in.condition() != null && !in.condition().isBlank()) p.setCondition(condition(in.condition(), null));
        properties.flush();
        audit.record("updated", TYPE, p.getId(), "Transferred asset " + p.getTagNumber() + " (" + ref + ")",
                Map.of("assigned_to", from), Map.of("assigned_to", in.toUser(), "reference", ref));
        notifications.notifyUser(in.toUser(), "property_transfer_in", "Property transferred to you",
                "Asset " + p.getTagNumber() + " was transferred to you under " + ref + ".", TYPE, p.getId());
        notifications.notifyUser(from, "property_transfer_out", "Property transferred from you",
                "Asset " + p.getTagNumber() + " is no longer under your accountability (" + ref + ").", TYPE, p.getId());
        return t;
    }

    /** PRS / RRSP: the holder turns the asset in to the property office. */
    @Transactional
    public PropertyTransfer turnIn(Long id, MoveInput in) {
        Property p = lock(id);
        if (p.getAssignedTo() == null) throw ApiException.of(HttpStatus.CONFLICT, "This asset has already been returned.");
        if (p.isGone()) throw ApiException.of(HttpStatus.CONFLICT, "Disposed assets cannot be returned.");
        requireOnSite(p);
        requireReason(in);
        boolean ppe = jdbc.queryForObject("select is_expandable from items where id = ?", Boolean.class, p.getItemId());
        String ref = numbers.monthly(ppe ? "PRS" : "RRSP", "property_transfers.reference_number");
        Long from = p.getAssignedTo();
        PropertyTransfer t = transfers.save(new PropertyTransfer(p.getId(), PropertyTransfer.RETURN, from, null, date(in), in.reason().trim(), current.user().getId(), ref));
        p.setAssignedTo(null);
        p.setAccountabilityNumber(null);
        if (Property.ASSIGNED.equals(p.getStatus())) p.setStatus(Property.AVAILABLE);
        if (in.condition() != null && !in.condition().isBlank()) p.setCondition(condition(in.condition(), null));
        properties.flush();
        audit.record("updated", TYPE, p.getId(), "Returned asset " + p.getTagNumber() + " (" + ref + ")",
                Map.of("assigned_to", from), Map.of("assigned_to", "property office", "reference", ref));
        notifications.notifyUser(from, "property_transfer_out", "Property return recorded",
                "Your return of asset " + p.getTagNumber() + " was recorded (" + ref + ").", TYPE, p.getId());
        return t;
    }

    /** A new PAR/ICS for a returned asset going back into use. */
    @Transactional
    public PropertyTransfer reissue(Long id, MoveInput in) {
        Property p = lock(id);
        if (p.getAssignedTo() != null) throw ApiException.of(HttpStatus.CONFLICT, "This asset is already issued; transfer it instead.");
        if (!Property.AVAILABLE.equals(p.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only available assets can be issued (this one is " + p.getStatus().replace('_', ' ') + ").");
        if (in.toUser() == null || !users.existsById(in.toUser())) throw ApiException.validation("to_user", "Choose who the asset goes to.");
        String reason = in.reason() == null || in.reason().isBlank() ? "Re-issued" : in.reason().trim();
        boolean ppe = jdbc.queryForObject("select is_expandable from items where id = ?", Boolean.class, p.getItemId());
        String doc = numbers.monthly(ppe ? "PAR" : "ICS", ACCOUNTABILITY_COLS);
        LocalDate date = date(in);
        PropertyTransfer t = transfers.save(new PropertyTransfer(p.getId(), PropertyTransfer.REISSUE, null, in.toUser(), date, reason, current.user().getId(), doc));
        p.setAssignedTo(in.toUser());
        p.setAssignedDate(date);
        p.setAccountabilityNumber(doc);
        p.setStatus(Property.ASSIGNED);
        properties.flush();
        audit.record("updated", TYPE, p.getId(), "Issued asset " + p.getTagNumber() + " (" + doc + ")", null, Map.of("assigned_to", in.toUser(), "document", doc));
        notifications.notifyUser(in.toUser(), "property_transfer_in", "Property issued to you",
                "Asset " + p.getTagNumber() + " was issued to you under " + doc + ".", TYPE, p.getId());
        return t;
    }

    public record StatusInput(String action, String remarks) {
    }

    /**
     * repair: in use or available → under repair; in_service: back from repair; retire: a returned
     * asset taken out of service; reinstate: a retired asset back to available. Disposal goes through
     * a disposal request.
     */
    @Transactional
    public Property changeStatus(Long id, StatusInput in) {
        Property p = lock(id);
        String before = p.getStatus();
        String action = in.action() == null ? "" : in.action();
        switch (action) {
            case "repair" -> {
                if (!Set.of(Property.ASSIGNED, Property.AVAILABLE).contains(before)) throw conflict(p, "sent for repair");
                p.setStatus(Property.UNDER_REPAIR);
            }
            case "in_service" -> {
                if (!Property.UNDER_REPAIR.equals(before)) throw conflict(p, "returned to service");
                p.setStatus(p.getAssignedTo() == null ? Property.AVAILABLE : Property.ASSIGNED);
            }
            case "retire" -> {
                if (p.getAssignedTo() != null) throw ApiException.of(HttpStatus.CONFLICT, "Record the asset's return before retiring it.");
                if (!Set.of(Property.AVAILABLE, Property.UNDER_REPAIR).contains(before)) throw conflict(p, "retired");
                requireOnSite(p);
                p.setStatus(Property.RETIRED);
            }
            case "reinstate" -> {
                if (!Property.RETIRED.equals(before)) throw conflict(p, "reinstated");
                p.setStatus(Property.AVAILABLE);
            }
            default -> throw ApiException.validation("action", "Unknown status change.");
        }
        properties.flush();
        Map<String, Object> to = new LinkedHashMap<>();
        to.put("status", p.getStatus());
        if (in.remarks() != null && !in.remarks().isBlank()) to.put("remarks", in.remarks().trim());
        audit.record("updated", TYPE, p.getId(), "Asset " + p.getTagNumber() + ": " + action.replace('_', ' '), Map.of("status", before), to);
        return p;
    }

    /**
     * Undoes a mistaken registration: only for an asset with no history (no transfers, gate passes,
     * disposals or posted depreciation). The unit goes back into the lot it came from.
     */
    @Transactional
    public void delete(Long id) {
        Property p = lock(id);
        // The registration's own issue row doesn't count; anything after it does.
        List<PropertyTransfer> moves = transfers.findByPropertyIdOrderByTransferDateDescIdDesc(id);
        boolean moved = moves.size() > 1 || (moves.size() == 1 && !PropertyTransfer.REISSUE.equals(moves.get(0).getTransferType()));
        boolean history = moved
                || count("gate_passes", id) > 0 || count("disposal_requests", id) > 0
                || count("property_depreciation_logs", id) > 0;
        if (history) {
            throw ApiException.of(HttpStatus.CONFLICT, "This asset has transfers, gate passes, disposals or depreciation on record, so it cannot be deleted. Return or dispose of it instead.");
        }
        Item item = ledger.lock(p.getItemId());
        int before = item.getCurrentStock();
        ledger.putBack(item, p.getStockId(), 1, p.getAcquisitionCost(), "Returned from deleted asset " + p.getTagNumber());
        ledger.log(item, current.user().getId(), "stock_in", 1, before, TYPE, p.getId(), "Asset " + p.getTagNumber() + " deleted — unit returned to stock");
        Map<String, Object> snap = snapshot(p);
        transfers.deleteAll(moves);
        properties.delete(p);
        properties.flush();
        audit.record("deleted", TYPE, id, "Deleted asset " + p.getTagNumber() + " (registration undone)", snap, null);
    }

    // ───────────────────────── helpers ─────────────────────────

    private Property lock(Long id) {
        return properties.lockById(id).orElseThrow(() -> ApiException.notFound("Asset"));
    }

    private int count(String table, Long propertyId) {
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where property_id = ?", Integer.class, propertyId);
        return n == null ? 0 : n;
    }

    /** An asset out on a gate pass isn't physically here to be turned in or retired. */
    private void requireOnSite(Property p) {
        Integer out = jdbc.queryForObject("select count(*) from gate_passes where property_id = ? and status <> 'returned' and deleted_at is null",
                Integer.class, p.getId());
        if (out != null && out > 0) throw ApiException.of(HttpStatus.CONFLICT, "Asset " + p.getTagNumber() + " is out on a gate pass; record its return through the gate first.");
    }

    private static ApiException conflict(Property p, String what) {
        return ApiException.of(HttpStatus.CONFLICT, "An asset that is " + p.getStatus().replace('_', ' ') + " cannot be " + what + ".");
    }

    private static void requireReason(MoveInput in) {
        if (in.reason() == null || in.reason().isBlank()) throw ApiException.validation("reason", "Give the reason.");
    }

    private static LocalDate date(MoveInput in) {
        LocalDate d = in.date() == null ? LocalDate.now(Times.ZONE) : in.date();
        if (d.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("date", "The date cannot be in the future.");
        return d;
    }

    private static String condition(String c, String fallback) {
        if (c == null || c.isBlank()) return fallback;
        if (!Property.CONDITIONS.contains(c)) throw ApiException.validation("condition", "Condition must be excellent, good, fair or poor.");
        return c;
    }

    private void changed(Property p, String description, Map<String, Object> before) {
        Map<String, Object> after = snapshot(p);
        Map<String, Object> o = new LinkedHashMap<>();
        Map<String, Object> n = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!java.util.Objects.equals(before.get(k), v)) {
                o.put(k, before.get(k));
                n.put(k, v);
            }
        });
        if (!n.isEmpty()) audit.record("updated", TYPE, p.getId(), description, o, n);
    }

    private static Map<String, Object> snapshot(Property p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag_number", p.getTagNumber());
        m.put("serial_number", p.getSerialNumber());
        m.put("brand", p.getBrand());
        m.put("condition", p.getCondition());
        m.put("status", p.getStatus());
        m.put("assigned_to", p.getAssignedTo());
        m.put("useful_life_years", p.getUsefulLifeYears());
        m.put("salvage_value", p.getSalvageValue() == null ? null : p.getSalvageValue().toPlainString());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}

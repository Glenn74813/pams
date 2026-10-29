package com.avtech.pams.property;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Classification;
import com.avtech.pams.inventory.InventoryLog;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.Stock;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.setting.SettingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Locally assembled units such as a Clone PC (items marked as assemblies): the parts list of one
 * unit, each part drawn from a lot of the assembly item and returned to it when removed, with the
 * unit recorded at its total assembled cost (COA: one PPE at the sum of its parts) and the item
 * promoted to PPE once a unit reaches the capitalization threshold. Also the older parent/child
 * linking of whole assets.
 */
@RestController
@RequestMapping("/api/properties")
public class AssemblyController {

    private static final String MANAGE = "@perm.any('edit_properties', 'create_properties')";
    private static final String TYPE = "App\\Models\\Property";

    private final JdbcTemplate jdbc;
    private final PropertyRepository properties;
    private final StockLedger ledger;
    private final SettingService settings;
    private final CurrentUser current;
    private final AuditService audit;

    public AssemblyController(JdbcTemplate jdbc, PropertyRepository properties, StockLedger ledger, SettingService settings, CurrentUser current,
                              AuditService audit) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.ledger = ledger;
        this.settings = settings;
        this.current = current;
        this.audit = audit;
    }

    public record Part(Long id, String description, String specifications, String serialNumber, int quantity, String remarks, Long stockId,
                       BigDecimal unitCost, BigDecimal amount, String warranty, Integer warrantyMonths, LocalDate warrantyStart) {
    }

    public record Lot(Long id, String label, String brand, String specifications, int quantity, BigDecimal unitCost) {
    }

    public record Linked(Long id, String tagNumber, String serialNumber, String item, String holder) {
    }

    public record AssemblyView(boolean isAssembly, List<Part> parts, BigDecimal total, BigDecimal threshold, String classification,
                               List<Lot> lots, List<Linked> linked, Linked parent) {
    }

    @GetMapping("/{id}/assembly")
    @PreAuthorize("@perm.any('view_properties', 'view_own_properties')")
    @Transactional(readOnly = true)
    public AssemblyView show(@PathVariable Long id) {
        Property p = visible(id);
        boolean assembly = Boolean.TRUE.equals(jdbc.queryForObject("select is_assembly from items where id = ?", Boolean.class, p.getItemId()));
        List<Part> parts = parts(id);
        BigDecimal total = parts.stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal threshold = settings.capitalizationThreshold();
        List<Lot> lots = !assembly ? List.of() : jdbc.query("""
                select id, coalesce(nullif(nullif(specifications, '-'), ''), brand_name, concat('Lot ', id)), brand_name, specifications, quantity, unit_cost
                from stocks where item_id = ? and quantity > 0 order by received_date, id""",
                (rs, n) -> new Lot(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBigDecimal(6)), p.getItemId());
        List<Linked> linked = jdbc.query(LINKED + " where p.parent_id = ? order by p.tag_number", (rs, n) -> linked(rs), id);
        Linked parent = p.getParentId() == null ? null : jdbc.query(LINKED + " where p.id = ?", rs -> rs.next() ? linked(rs) : null, p.getParentId());
        String classification = !assembly ? null : total.compareTo(threshold) >= 0 ? "ppe" : "semi_expendable";
        return new AssemblyView(assembly, parts, total, threshold, classification, lots, linked, parent);
    }

    public record PartInput(String description, String specifications, String serialNumber, Integer quantity, String remarks, Long stockId) {
    }

    /** Adds a part, drawn from the chosen lot of the assembly item (or the lot whose brand and description match). */
    @PostMapping("/{id}/assembly-components")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<AssemblyView> addPart(@PathVariable Long id, @RequestBody PartInput in) {
        Property p = properties.lockById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        Item item = ledger.lock(p.getItemId());
        if (!item.isAssembly()) {
            throw ApiException.validation("property_id", "This asset is not an assembly. Only items marked as assembled (e.g. a Clone PC) can list component parts.");
        }
        String desc = blank(in.description());
        if (desc == null || desc.length() > 255) throw ApiException.validation("description", "Describe the part (up to 255 characters).");
        String spec = blank(in.specifications());
        int qty = in.quantity() == null ? 1 : in.quantity();
        if (qty < 1) throw ApiException.validation("quantity", "Quantity is at least 1.");
        Long dupes = jdbc.queryForObject("""
                select count(*) from property_components where property_id = ? and lower(trim(description)) = ? and lower(trim(coalesce(specifications, ''))) = ?""",
                Long.class, id, desc.toLowerCase(Locale.ROOT), spec == null ? "" : spec.toLowerCase(Locale.ROOT));
        if (dupes != null && dupes > 0) throw ApiException.validation("description", "That component is already listed for this unit.");

        Long lotId = in.stockId() != null ? in.stockId() : jdbc.query("""
                select id from stocks where item_id = ? and lower(trim(coalesce(brand_name, ''))) = ?
                  and lower(trim(case when specifications is not null and specifications not in ('', '-') then specifications else coalesce(brand_name, '') end)) = ?
                order by received_date, id offset 0 rows fetch next 1 rows only""", rs -> rs.next() ? rs.getLong(1) : null,
                item.getId(), spec == null ? "" : spec.toLowerCase(Locale.ROOT), desc.toLowerCase(Locale.ROOT));
        Stock lot = null;
        if (lotId != null) {
            int before = item.getCurrentStock();
            lot = ledger.takeFromLot(item, lotId, qty);
            ledger.log(item, current.user().getId(), "stock_out", qty, before, TYPE, p.getId(), "Consumed by " + p.getTagNumber() + " (" + desc + ")");
        }
        String warranty = lot == null ? null : lot.getWarranty() != null && !lot.getWarranty().isBlank() ? lot.getWarranty()
                : lot.getWarrantyMonths() != null ? lot.getWarrantyMonths() + " month(s)" : null;
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                insert into property_components (property_id, description, specifications, serial_number, quantity, remarks, stock_id, warranty,
                                                 warranty_months, warranty_start, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, desc, spec, blank(in.serialNumber()), qty, blank(in.remarks()),
                lot == null ? null : lot.getId(), warranty, lot == null ? null : lot.getWarrantyMonths(),
                lot == null || lot.getReceivedDate() == null ? null : java.sql.Date.valueOf(lot.getReceivedDate()), now, now);
        BigDecimal total = syncCost(p, item);
        audit.record("component_added", TYPE, id, "Added " + qty + " × " + desc + (spec == null ? "" : " (" + spec + ")") + " to " + p.getTagNumber()
                + "; unit now ₱" + total.toPlainString(), null, Map.of("description", desc, "quantity", qty));
        return ResponseEntity.status(HttpStatus.CREATED).body(show(id));
    }

    /** Removes a part and returns what it used to its lot. */
    @DeleteMapping("/assembly-components/{componentId}")
    @PreAuthorize(MANAGE)
    @Transactional
    public AssemblyView removePart(@PathVariable Long componentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from property_components where id = ?", componentId);
        if (rows.isEmpty()) throw ApiException.notFound("Component");
        Map<String, Object> c = rows.get(0);
        Long propertyId = ((Number) c.get("property_id")).longValue();
        Property p = properties.lockById(propertyId).orElseThrow(() -> ApiException.notFound("Asset"));
        Item item = ledger.lock(p.getItemId());
        int qty = ((Number) c.get("quantity")).intValue();
        Object stockId = c.get("stock_id");
        if (stockId != null) {
            int before = item.getCurrentStock();
            ledger.putBack(item, ((Number) stockId).longValue(), qty, null, null);
            ledger.log(item, current.user().getId(), InventoryLog.STOCK_IN, qty, before, TYPE, p.getId(),
                    "Returned from " + p.getTagNumber() + " (" + c.get("description") + ")");
        }
        jdbc.update("delete from property_components where id = ?", componentId);
        syncCost(p, item);
        audit.record("component_removed", TYPE, propertyId, "Removed " + c.get("description") + " from " + p.getTagNumber()
                + (stockId == null ? "" : "; returned " + qty + " to stock"), Map.of("description", c.get("description"), "quantity", qty), null);
        return show(propertyId);
    }

    // ───────────────────────── parent/child linking of whole assets ─────────────────────────

    @GetMapping("/{id}/components")
    @PreAuthorize("@perm.any('view_properties', 'view_own_properties')")
    public List<Linked> components(@PathVariable Long id) {
        visible(id);
        return jdbc.query(LINKED + " where p.parent_id = ? order by p.tag_number", (rs, n) -> linked(rs), id);
    }

    public record LinkInput(Long propertyId) {
    }

    @PostMapping("/{id}/components")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<List<Linked>> link(@PathVariable Long id, @RequestBody LinkInput in) {
        Property parent = properties.lockById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select is_assembly from items where id = ?", Boolean.class, parent.getItemId()))) {
            throw ApiException.validation("property_id", "This asset is not an assembly. Only items marked as assembled (e.g. a Clone PC) can have components attached.");
        }
        if (in.propertyId() == null) throw ApiException.validation("property_id", "Choose the asset to attach.");
        if (in.propertyId().equals(id)) throw ApiException.validation("property_id", "An asset cannot be a component of itself.");
        Property child = properties.lockById(in.propertyId()).orElseThrow(() -> ApiException.notFound("Asset"));
        Long kids = jdbc.queryForObject("select count(*) from properties where parent_id = ?", Long.class, child.getId());
        if (kids != null && kids > 0) throw ApiException.validation("property_id", "That asset is itself a parent with components; nest only single assets.");
        if (child.getParentId() != null) throw ApiException.validation("property_id", "That asset is already a component of another asset.");
        jdbc.update("update properties set parent_id = ?, updated_at = ? where id = ?", id, Timestamp.valueOf(Times.now()), child.getId());
        audit.record("component_linked", TYPE, id, child.getTagNumber() + " attached to " + parent.getTagNumber(), null, Map.of("child", child.getTagNumber()));
        return ResponseEntity.status(HttpStatus.CREATED).body(components(id));
    }

    @DeleteMapping("/components/{childId}")
    @PreAuthorize(MANAGE)
    @Transactional
    public Map<String, String> unlink(@PathVariable Long childId) {
        Property child = properties.lockById(childId).orElseThrow(() -> ApiException.notFound("Asset"));
        if (child.getParentId() == null) throw ApiException.validation("property_id", "That asset is not attached to another.");
        Long parent = child.getParentId();
        jdbc.update("update properties set parent_id = null, updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), childId);
        audit.record("component_unlinked", TYPE, parent, child.getTagNumber() + " detached", Map.of("child", child.getTagNumber()), null);
        return Map.of("message", "Component detached");
    }

    // ───────────────────────── helpers ─────────────────────────

    private static final String LINKED = """
            select p.id, p.tag_number, p.serial_number, i.name, u.name from properties p left join items i on i.id = p.item_id
            left join users u on u.id = p.assigned_to""";

    private static Linked linked(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Linked(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
    }

    private List<Part> parts(Long propertyId) {
        return jdbc.query("""
                select c.*, coalesce(s.unit_cost, 0) unit_cost from property_components c left join stocks s on s.id = c.stock_id
                where c.property_id = ? order by c.id""", (rs, n) -> {
            BigDecimal unit = rs.getBigDecimal("unit_cost");
            int q = rs.getInt("quantity");
            java.sql.Date ws = rs.getDate("warranty_start");
            return new Part(rs.getLong("id"), rs.getString("description"), rs.getString("specifications"), rs.getString("serial_number"), q,
                    rs.getString("remarks"), (Long) rs.getObject("stock_id"), unit, unit.multiply(BigDecimal.valueOf(q)).setScale(2, RoundingMode.HALF_UP),
                    rs.getString("warranty"), (Integer) rs.getObject("warranty_months"), ws == null ? null : ws.toLocalDate());
        }, propertyId);
    }

    /** The unit's acquisition cost is the sum of its parts; the item becomes PPE once a unit reaches the threshold (never demoted). */
    private BigDecimal syncCost(Property p, Item item) {
        BigDecimal total = parts(p.getId()).stream().map(Part::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        boolean ppe = total.signum() > 0 && total.compareTo(settings.capitalizationThreshold()) >= 0;
        // Until depreciation has started, the book value is the assembled cost; each unit depreciates by its own total (COA: PPE at or above the threshold).
        jdbc.update("""
                update properties set acquisition_cost = ?, is_depreciable = ?,
                  current_book_value = case when exists (select 1 from property_depreciation_logs d where d.property_id = properties.id) then current_book_value else ? end,
                  updated_at = ?
                where id = ?""", total, ppe, total, Timestamp.valueOf(Times.now()), p.getId());
        if (ppe && item.classification() != Classification.PPE) {
            item.setClassification(Classification.PPE);
        }
        return total;
    }

    private Property visible(Long id) {
        Property p = properties.findById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        if (!current.any("view_properties") && !current.user().getId().equals(p.getAssignedTo())) {
            throw ApiException.forbidden("You can only view assets you are accountable for.");
        }
        return p;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.avtech.pams.procurement;

import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the items at or below their reorder point into purchase request lines. Each item is matched to the line of this
 * year's approved APP that plans it (a PPMP line naming the item), up to what is left of that line. Items with no such
 * line are listed apart: they need a PPMP and APP amendment before they can be requested against the plan.
 */
@RestController
@RequestMapping("/api/purchase-requests/reorder")
public class ReorderController {

    public record AppRef(Long id, String appNumber, Integer fiscalYear) {
    }

    public record Ref(Long id, String name) {
    }

    public record Line(Long itemId, String itemCode, String itemName, int onHand, int reorderPoint, int suggested, Long appItemId,
                       String generalDescription, String unit, int quantity, BigDecimal unitCost, String procurementMode,
                       String procurementCategory, int remainingQuantity) {
    }

    /** The lines one requesting office can put on a single purchase request. */
    public record Group(Ref department, List<Line> lines) {
    }

    public record Unplanned(Long itemId, String itemCode, String itemName, String unit, int onHand, int reorderPoint, int suggested, String reason) {
    }

    public record Plan(int fiscalYear, AppRef app, List<Group> groups, List<Unplanned> unplanned) {
    }

    private record Stock(Long id, String code, String name, String unit, int onHand, int reorderPoint, int suggested) {
    }

    private record Planned(Long id, Long itemId, Long departmentId, String departmentName, String description, String unit, int quantity,
                           BigDecimal budget, String mode, String category) {
    }

    private final JdbcTemplate jdbc;
    private final AppUsage usage;
    private final Db db;

    public ReorderController(JdbcTemplate jdbc, AppUsage usage, Db db) {
        this.jdbc = jdbc;
        this.usage = usage;
        this.db = db;
    }

    /** item_ids: the items to reorder; left out, every item at or below its reorder point. */
    @GetMapping
    @PreAuthorize("@perm.any('create_procurement', 'edit_procurement')")
    public Plan plan(@RequestParam(name = "item_ids", required = false) List<Long> itemIds) {
        int year = LocalDate.now(Times.ZONE).getYear();
        List<Object> args = new ArrayList<>();
        // PPE is bought and tracked per asset, not restocked to a level.
        String where = "i.is_expandable = " + db.f() + " and i.min_stock > 0 and i.current_stock <= i.min_stock";
        if (itemIds != null && !itemIds.isEmpty()) {
            where = "i.is_expandable = " + db.f() + " and i.id in (" + String.join(",", itemIds.stream().map(x -> "?").toList()) + ")";
            args.addAll(itemIds);
        }
        List<Stock> stock = jdbc.query("select i.id, i.code, i.name, i.unit_of_measure, i.current_stock, i.min_stock, coalesce(i.max_stock, 0), i.is_expandable"
                + " from items i where " + where + " order by i.name", (rs, n) -> new Stock(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getInt(6), Item.suggestedOrder(rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getBoolean(8))), args.toArray());

        Optional<AppRef> app = jdbc.query("select id, app_number, fiscal_year from apps where fiscal_year = ? and status = ? and deleted_at is null",
                (rs, n) -> new AppRef(rs.getLong(1), rs.getString(2), rs.getInt(3)), year, AppPlan.APPROVED).stream().findFirst();
        List<Unplanned> unplanned = new ArrayList<>();
        Map<Long, Group> groups = new LinkedHashMap<>();
        if (app.isEmpty()) {
            for (Stock s : stock) unplanned.add(unplanned(s, "There is no approved APP for FY " + year + "."));
            return new Plan(year, null, List.of(), unplanned);
        }

        Map<Long, List<Planned>> byItem = new HashMap<>();
        if (!stock.isEmpty()) {
            List<Object> a = new ArrayList<>();
            a.add(app.get().id());
            stock.forEach(s -> a.add(s.id()));
            jdbc.query("""
                    select ai.id, ai.item_id, ai.department_id, d.name, ai.general_description, ai.unit, ai.quantity, ai.estimated_budget,
                           ai.procurement_mode, ai.procurement_category
                    from app_items ai left join departments d on d.id = ai.department_id
                    where ai.app_id = ? and ai.item_id in (""" + String.join(",", stock.stream().map(x -> "?").toList()) + ") order by ai.id",
                    rs -> {
                        Planned p = new Planned(rs.getLong(1), rs.getLong(2), rs.getObject(3, Long.class), rs.getString(4), rs.getString(5),
                                rs.getString(6), rs.getInt(7), rs.getBigDecimal(8), rs.getString(9), rs.getString(10));
                        byItem.computeIfAbsent(p.itemId(), k -> new ArrayList<>()).add(p);
                    }, a.toArray());
        }
        Map<Long, AppUsage.Use> used = usage.forItems(byItem.values().stream().flatMap(List::stream).map(Planned::id).toList(), null);

        for (Stock s : stock) {
            if (s.suggested() <= 0) {
                unplanned.add(unplanned(s, s.onHand() > s.reorderPoint() ? "Above its reorder point." : "Set a reorder point to work out how much to order."));
                continue;
            }
            List<Planned> lines = byItem.getOrDefault(s.id(), List.of());
            if (lines.isEmpty()) {
                unplanned.add(unplanned(s, "Not planned in " + app.get().appNumber() + "; add it to a PPMP and amend the APP first."));
                continue;
            }
            // The planned line with the most left, so one request covers as much of the reorder as it can.
            Planned best = null;
            int bestQty = 0;
            BigDecimal bestCost = BigDecimal.ZERO;
            for (Planned p : lines) {
                AppUsage.Use u = usage.of(used, p.id());
                BigDecimal cost = p.budget().divide(BigDecimal.valueOf(p.quantity()), 2, RoundingMode.HALF_UP);
                int left = p.quantity() - u.quantity();
                BigDecimal budgetLeft = p.budget().subtract(u.requested());
                if (cost.signum() > 0) left = Math.min(left, budgetLeft.divide(cost, 0, RoundingMode.FLOOR).intValue());
                if (left > bestQty) {
                    best = p;
                    bestQty = left;
                    bestCost = cost;
                }
            }
            if (best == null) {
                unplanned.add(unplanned(s, "Its line in " + app.get().appNumber() + " is fully requested; amend the APP to buy more."));
                continue;
            }
            Planned p = best;
            groups.computeIfAbsent(p.departmentId() == null ? 0L : p.departmentId(), k -> new Group(new Ref(p.departmentId(), p.departmentName()), new ArrayList<>()))
                    .lines().add(new Line(s.id(), s.code(), s.name(), s.onHand(), s.reorderPoint(), s.suggested(), p.id(), p.description(),
                            p.unit() != null ? p.unit() : s.unit(), Math.min(s.suggested(), bestQty), bestCost, p.mode(), p.category(), bestQty));
        }
        List<Group> out = new ArrayList<>(groups.values());
        out.sort(Comparator.comparingInt((Group g) -> -g.lines().size()));
        return new Plan(year, app.get(), out, unplanned);
    }

    private static Unplanned unplanned(Stock s, String reason) {
        return new Unplanned(s.id(), s.code(), s.name(), s.unit(), s.onHand(), s.reorderPoint(), s.suggested(), reason);
    }
}

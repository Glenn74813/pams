package com.avtech.pams.inventory;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stock Card (COA Appendix 58): every receipt and issue of an item with its running balance.
 * Same rules as the Laravel preview, so both systems print the same card.
 */
@RestController
@RequestMapping("/api/reports/stock-card")
public class StockCardController {

    private static final Set<String> RECEIPTS = Set.of("stock_in", "delivery_accepted", "adjustment_in");
    private static final Set<String> ISSUES = Set.of("stock_out", "item_issued", "adjustment_out");
    private static final Set<String> ADJUSTMENTS = Set.of("adjustment", "adjustment_in", "adjustment_out", "stock_edited");
    private static final List<String> MOVEMENTS = List.of("stock_in", "stock_out", "delivery_accepted", "item_issued",
            "adjustment", "adjustment_in", "adjustment_out", "stock_edited");
    private static final Pattern NET_CHANGE = Pattern.compile("net change (-?\\d+)");

    private final ItemRepository items;
    private final InventoryLogRepository logs;
    private final JdbcTemplate jdbc;

    public StockCardController(ItemRepository items, InventoryLogRepository logs, JdbcTemplate jdbc) {
        this.items = items;
        this.logs = logs;
        this.jdbc = jdbc;
    }

    public record Row(String date, String reference, Integer receipt, Integer issue, String office, int balance) {
    }

    @GetMapping("/{itemId}/preview")
    @PreAuthorize("@perm.any('view_inventory', 'view_warehouse', 'manage_warehouse', 'add_stock', 'view_reports')")
    @Transactional(readOnly = true)
    public Map<String, Object> preview(@PathVariable Long itemId) {
        Item item = items.findWithCategoryById(itemId).orElseThrow(() -> ApiException.notFound("Item"));
        List<InventoryLog> movements = logs.movements(itemId, MOVEMENTS);

        // Anchor the running balance to the real on-hand figure: the opening balance absorbs
        // anything recorded before logging began, so the card always ends at current stock.
        int net = movements.stream().mapToInt(StockCardController::signedDelta).sum();
        int opening = item.getCurrentStock() - net;
        Map<Long, String> offices = officesByUser(movements);

        List<Row> rows = new ArrayList<>();
        if (opening != 0 || !movements.isEmpty()) {
            String first = movements.isEmpty() ? null : date(movements.get(0));
            rows.add(new Row(first, "Beginning balance", null, null, null, opening));
        }
        int balance = opening;
        for (InventoryLog l : movements) {
            int signed = signedDelta(l);
            balance += signed;
            String reference = l.getNotes() != null && !l.getNotes().isBlank() ? l.getNotes()
                    : ADJUSTMENTS.contains(l.getAction()) ? "Adjustment" : signed >= 0 ? "Stock In" : "Issued";
            rows.add(new Row(date(l), reference, signed > 0 ? signed : null, signed < 0 ? -signed : null,
                    signed < 0 ? offices.get(l.getUserId()) : null, balance));
        }

        Map<String, Object> head = new LinkedHashMap<>();
        head.put("code", item.getCode());
        head.put("name", item.getName());
        head.put("description", item.getDescription());
        head.put("unit_of_measure", item.getUnitOfMeasure());
        head.put("reorder_point", item.getMinStock());
        head.put("current_stock", item.getCurrentStock());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("item", head);
        res.put("rows", rows);
        return res;
    }

    /** Positive for receipts, negative for issues; adjustments carry their own sign. */
    private static int signedDelta(InventoryLog l) {
        int qty = Math.abs(l.getQuantity() == null ? 0 : l.getQuantity());
        if (RECEIPTS.contains(l.getAction())) return qty;
        if (ISSUES.contains(l.getAction())) return -qty;
        if (l.getNotes() != null) {
            Matcher m = NET_CHANGE.matcher(l.getNotes());
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        int d = (l.getNewQuantity() == null ? 0 : l.getNewQuantity()) - (l.getPreviousQuantity() == null ? 0 : l.getPreviousQuantity());
        return d != 0 ? d : qty;
    }

    private static String date(InventoryLog l) {
        return l.getCreatedAt() == null ? null : l.getCreatedAt().atZone(Times.ZONE).toLocalDate().toString();
    }

    /** The department of whoever recorded each issue (the office column). */
    private Map<Long, String> officesByUser(List<InventoryLog> movements) {
        Map<Long, String> out = new HashMap<>();
        List<Long> ids = movements.stream().map(InventoryLog::getUserId).distinct().toList();
        if (ids.isEmpty()) return out;
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        jdbc.query("select u.id, d.name from users u join departments d on d.id = u.department_id where u.id in (" + in + ")",
                rs -> { out.put(rs.getLong(1), rs.getString(2)); }, ids.toArray());
        return out;
    }
}

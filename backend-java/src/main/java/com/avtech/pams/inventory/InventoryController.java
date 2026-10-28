package com.avtech.pams.inventory;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.InventoryService.ItemInput;
import com.avtech.pams.inventory.InventoryService.LotInput;
import com.avtech.pams.inventory.InventoryService.TransferInput;
import com.avtech.pams.inventory.InventoryViews.ItemRef;
import com.avtech.pams.inventory.InventoryViews.ItemView;
import com.avtech.pams.inventory.InventoryViews.StockView;
import com.avtech.pams.inventory.InventoryViews.TransferView;
import com.avtech.pams.setting.SettingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    /** Who may look at the catalogue and stock. Laravel let any signed-in user read these. */
    static final String READ = "@perm.any('view_inventory', 'view_warehouse', 'manage_warehouse', 'add_stock')";
    private static final String MOVE_STOCK = "@perm.any('add_stock', 'manage_warehouse')";
    private static final Set<String> LEVELS = Set.of("out", "low", "attention");

    private final InventoryService service;
    private final ItemRepository items;
    private final StockRepository stocks;
    private final StockTransferRepository transfers;
    private final SettingService settings;
    private final JdbcTemplate jdbc;

    public InventoryController(InventoryService service, ItemRepository items, StockRepository stocks,
                               StockTransferRepository transfers, SettingService settings, JdbcTemplate jdbc) {
        this.service = service;
        this.items = items;
        this.stocks = stocks;
        this.transfers = transfers;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    /** Values the item and stock forms need from Settings. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("capitalization_threshold", settings.capitalizationThreshold());
        m.put("fefo_expiry_warning_days", settings.fefoWarningDays());
        // Printed on RIS / ICS / PAR slips.
        m.put("agency_name", settings.get("agency_name"));
        m.put("fund_cluster", settings.get("fund_cluster"));
        return m;
    }

    // ───────────────────────── items ─────────────────────────

    @GetMapping("/items")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<ItemView> index(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(name = "per_page", defaultValue = "20") int perPage,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(name = "category_id", required = false) Long categoryId,
                                        @RequestParam(required = false) String classification,
                                        @RequestParam(name = "stock_level", required = false) String level) {
        if (classification != null && !classification.isBlank()) {
            try {
                Classification.fromKey(classification);
            } catch (IllegalArgumentException e) {
                throw ApiException.validation("classification", "Unknown classification.");
            }
        }
        if (level != null && !level.isBlank() && !LEVELS.contains(level)) {
            throw ApiException.validation("stock_level", "Unknown stock level filter.");
        }
        var pageable = PageRequest.of(Math.max(0, page - 1), Math.clamp(perPage, 1, 100), Sort.by("name"));
        Page<Item> result = items.search(blank(search), categoryId, blank(classification), blank(level), pageable);
        return PageResponse.of(result, ItemView::of);
    }

    /** Light list for item pickers in other modules (requests, receiving). Any signed-in user. */
    @GetMapping("/items/options")
    @Transactional(readOnly = true)
    public List<ItemView> options(@RequestParam(required = false) String search) {
        return items.options(blank(search)).stream().map(ItemView::of).toList();
    }

    /** Everything, issuable items first (in stock, or PPE); the request form greys out the rest. */
    @GetMapping("/items/available")
    @Transactional(readOnly = true)
    public List<ItemView> available() {
        return items.options(null).stream()
                .sorted((a, b) -> Boolean.compare(b.isAvailable(), a.isAvailable()))
                .map(ItemView::of).toList();
    }

    @GetMapping("/items/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public ItemView show(@PathVariable Long id) {
        Item item = items.findWithCategoryById(id).orElseThrow(() -> ApiException.notFound("Item"));
        List<Stock> lots = stocks.findByItemIdOrderByReceivedDateAscIdAsc(id);
        Map<Long, String> suppliers = supplierNames(lots.stream().map(Stock::getSupplierId).collect(Collectors.toSet()));
        return ItemView.of(item, lots.stream().map(s -> StockView.of(s, null, suppliers)).toList());
    }

    @PostMapping("/items")
    @PreAuthorize("@perm.any('create_inventory')")
    public ResponseEntity<ItemView> store(@RequestBody ItemInput body) {
        Item item = service.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(show(item.getId()));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("@perm.any('edit_inventory')")
    public ItemView update(@PathVariable Long id, @RequestBody ItemInput body) {
        service.update(id, body);
        return show(id);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("@perm.any('delete_inventory')")
    public Map<String, Object> destroy(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "Item deleted successfully");
    }

    // ───────────────────────── stock ─────────────────────────

    @PostMapping("/items/{id}/stock")
    @PreAuthorize(MOVE_STOCK)
    public ResponseEntity<Map<String, Object>> addStock(@PathVariable Long id, @RequestBody LotInput body) {
        Stock s = service.addStock(id, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Stock received.", "stock",
                StockView.of(s, null, supplierNames(java.util.Collections.singletonList(s.getSupplierId())))));
    }

    @GetMapping("/stock-entries")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<StockView> stockEntries(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(name = "per_page", defaultValue = "25") int perPage,
                                                @RequestParam(name = "item_id", required = false) Long itemId) {
        Page<Stock> result = stocks.entries(itemId, PageRequest.of(Math.max(0, page - 1), Math.clamp(perPage, 1, 100)));
        Map<Long, Item> byId = itemsById(result.getContent().stream().map(Stock::getItemId).toList());
        Map<Long, String> suppliers = supplierNames(result.getContent().stream().map(Stock::getSupplierId).collect(Collectors.toSet()));
        return PageResponse.of(result, s -> StockView.of(s, byId.get(s.getItemId()), suppliers));
    }

    /** Lots that still hold stock, for the transfer form's source picker. */
    @GetMapping("/items/{id}/lots")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public List<StockView> lots(@PathVariable Long id) {
        List<Stock> lots = stocks.findByItemIdOrderByReceivedDateAscIdAsc(id).stream().filter(s -> s.getQuantity() > 0).toList();
        Map<Long, String> suppliers = supplierNames(lots.stream().map(Stock::getSupplierId).collect(Collectors.toSet()));
        return lots.stream().map(s -> StockView.of(s, null, suppliers)).toList();
    }

    @GetMapping("/locations")
    @PreAuthorize(READ)
    public List<String> locations() {
        return stocks.locations();
    }

    @PostMapping("/transfer-stock")
    @PreAuthorize(MOVE_STOCK)
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody TransferInput body) {
        StockTransfer t = service.transfer(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Stock transferred.",
                "transfer", transferView(t, itemsById(List.of(t.getItemId())), Map.of())));
    }

    @GetMapping("/stock-transfers")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<TransferView> stockTransfers(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(name = "per_page", defaultValue = "20") int perPage,
                                                     @RequestParam(name = "item_id", required = false) Long itemId,
                                                     @RequestParam(required = false) String location) {
        Page<StockTransfer> result = transfers.history(itemId, blank(location),
                PageRequest.of(Math.max(0, page - 1), Math.clamp(perPage, 1, 100)));
        Map<Long, Item> byId = itemsById(result.getContent().stream().map(StockTransfer::getItemId).toList());
        Map<Long, String> userNames = names("users",
                result.getContent().stream().map(StockTransfer::getTransferredBy).collect(Collectors.toSet()));
        return PageResponse.of(result, t -> transferView(t, byId, userNames));
    }

    /** Expired and near-expiry lots, for first-expired-first-out monitoring and wastage disposal. */
    @GetMapping("/expiring")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public Map<String, Object> expiring(@RequestParam(required = false) Integer days) {
        int window = days == null ? settings.fefoWarningDays() : Math.clamp(days, 1, 365);
        LocalDate today = LocalDate.now(Times.ZONE);
        List<Stock> expired = stocks.expired(today);
        List<Stock> soon = stocks.expiringBetween(today, today.plusDays(window));
        Map<Long, Item> byId = itemsById(java.util.stream.Stream.concat(expired.stream(), soon.stream()).map(Stock::getItemId).toList());
        Map<Long, String> suppliers = supplierNames(java.util.stream.Stream.concat(expired.stream(), soon.stream())
                .map(Stock::getSupplierId).collect(Collectors.toSet()));
        Function<Stock, StockView> view = s -> StockView.of(s, byId.get(s.getItemId()), suppliers);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("expired_lots", expired.size());
        summary.put("expired_qty", expired.stream().mapToInt(Stock::getQuantity).sum());
        summary.put("expired_value", value(expired));
        summary.put("expiring_lots", soon.size());
        summary.put("expiring_qty", soon.stream().mapToInt(Stock::getQuantity).sum());
        summary.put("expiring_value", value(soon));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("warning_days", window);
        res.put("expired", expired.stream().map(view).toList());
        res.put("expiring_soon", soon.stream().map(view).toList());
        res.put("summary", summary);
        return res;
    }

    // ───────────────────────── helpers ─────────────────────────

    private TransferView transferView(StockTransfer t, Map<Long, Item> byId, Map<Long, String> userNames) {
        return new TransferView(t.getId(), t.getTransferNumber(), ItemRef.of(byId.get(t.getItemId())), t.getFromLocation(),
                t.getToLocation(), t.getQuantity(), t.getTransferDate(), t.getRemarks(), userNames.get(t.getTransferredBy()));
    }

    private Map<Long, Item> itemsById(Collection<Long> ids) {
        Map<Long, Item> out = new HashMap<>();
        items.findAllById(Set.copyOf(ids)).forEach(i -> out.put(i.getId(), i));
        return out;
    }

    private Map<Long, String> supplierNames(Collection<Long> ids) {
        return names("suppliers", ids);
    }

    /** id -> name for a fixed table ("users" or "suppliers"), in one query. */
    private Map<Long, String> names(String table, Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        List<Long> wanted = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) return out;
        String in = wanted.stream().map(x -> "?").collect(Collectors.joining(","));
        jdbc.query("select id, name from " + table + " where id in (" + in + ")",
                rs -> { out.put(rs.getLong(1), rs.getString(2)); }, wanted.toArray());
        return out;
    }

    private static BigDecimal value(List<Stock> lots) {
        return lots.stream().map(s -> s.getUnitCost().multiply(BigDecimal.valueOf(s.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

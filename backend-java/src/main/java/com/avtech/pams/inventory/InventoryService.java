package com.avtech.pams.inventory;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.setting.SettingService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Catalogue and stock changes. Every method that moves stock locks the item row first. */
@Service
public class InventoryService {

    private static final String STOCK_TYPE = "App\\Models\\Stock";
    private static final String ITEM_TYPE = "App\\Models\\Item";
    private static final String TRANSFER_TYPE = "App\\Models\\StockTransfer";

    private final ItemRepository items;
    private final CategoryRepository categories;
    private final StockRepository stocks;
    private final StockTransferRepository transfers;
    private final InventoryLogRepository logs;
    private final SettingService settings;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final StockLedger ledger;

    public InventoryService(ItemRepository items, CategoryRepository categories, StockRepository stocks,
                            StockTransferRepository transfers, InventoryLogRepository logs, SettingService settings,
                            AuditService audit, CurrentUser current, JdbcTemplate jdbc,
                            StockLedger ledger) {
        this.items = items;
        this.categories = categories;
        this.stocks = stocks;
        this.transfers = transfers;
        this.logs = logs;
        this.settings = settings;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    // ───────────────────────── requests ─────────────────────────

    public record LotInput(Long id, String brandName, String specifications, String warranty, Integer warrantyMonths,
                           Integer quantity, BigDecimal unitCost, Long supplierId, String location, String lotNumber,
                           LocalDate expirationDate, LocalDate receivedDate) {
    }

    public record ItemInput(String code, String name, String description, Long categoryId, String unitOfMeasure,
                            Integer minStock, Integer maxStock, String classification, Boolean isAssembly,
                            List<LotInput> stockEntries) {
    }

    public record TransferInput(Long stockId, String toLocation, Integer quantity, LocalDate transferDate, String remarks) {
    }

    // ───────────────────────── items ─────────────────────────

    @Transactional
    public Item create(ItemInput in) {
        Classification cls = classification(in.classification());
        boolean assembly = Boolean.TRUE.equals(in.isAssembly());
        String code = in.code() == null || in.code().isBlank() ? nextItemCode() : in.code().trim();
        if (items.existsByCodeIgnoreCase(code)) {
            throw ApiException.validation("code", "The item code \"" + code + "\" is already used.");
        }
        List<LotInput> lots = in.stockEntries() == null ? List.of() : in.stockEntries().stream()
                .filter(l -> l.quantity() != null && l.quantity() > 0 && l.unitCost() != null && l.unitCost().signum() > 0)
                .toList();
        lots.forEach(this::validateLot);
        if (!assembly) checkClassification(cls, maxCost(lots));

        Item item = new Item();
        item.setCode(code);
        applyFields(item, in, cls, assembly);
        items.saveAndFlush(item);

        int onHand = 0;
        for (LotInput l : lots) {
            Stock s = stocks.save(newLot(item.getId(), l));
            logs.save(InventoryLog.movement(item.getId(), userId(), InventoryLog.STOCK_IN, s.getQuantity(), onHand,
                    onHand + s.getQuantity(), STOCK_TYPE, s.getId(),
                    "Initial stock for " + item.getCode() + ": +" + s.getQuantity() + " units"));
            onHand += s.getQuantity();
        }
        item.setCurrentStock(onHand);
        ledger.recompute(item);

        logs.save(InventoryLog.event(item.getId(), userId(), InventoryLog.ITEM_CREATED, ITEM_TYPE, item.getId(),
                "Created item: " + item.getCode() + " - " + item.getName()));
        audit.record("created", AuditService.ITEM, item.getId(), "Created Item " + item.getName(), null, snapshot(item));
        return item;
    }

    /**
     * Updates the item. When stockEntries is present the lots are reconciled — matched by id are
     * updated, new ones added, missing ones removed — and the net change is logged for the stock card.
     */
    @Transactional
    public Item update(Long id, ItemInput in) {
        Item item = items.lockById(id).orElseThrow(() -> ApiException.notFound("Item"));
        Map<String, Object> before = snapshot(item);
        Classification cls = in.classification() == null ? item.classification() : classification(in.classification());
        boolean assembly = in.isAssembly() == null ? item.isAssembly() : in.isAssembly();

        if (in.code() != null && !in.code().isBlank() && !in.code().trim().equalsIgnoreCase(item.getCode())) {
            String code = in.code().trim();
            if (items.existsByCodeIgnoreCase(code)) {
                throw ApiException.validation("code", "The item code \"" + code + "\" is already used.");
            }
            item.setCode(code);
        }

        if (in.stockEntries() != null) {
            in.stockEntries().forEach(this::validateLot);
            if (!assembly) checkClassification(cls, maxCost(in.stockEntries()));
        } else if (!assembly && cls != item.classification()) {
            // Reclassifying without touching lots: check against the lots already on hand.
            checkClassification(cls, stocks.findByItemIdOrderByReceivedDateAscIdAsc(id).stream()
                    .filter(s -> s.getQuantity() > 0).map(Stock::getUnitCost).max(BigDecimal::compareTo).orElse(null));
        }

        applyFields(item, in, cls, assembly);

        if (in.stockEntries() != null) {
            reconcileLots(item, in.stockEntries());
        }
        items.flush();

        Map<String, Object> after = snapshot(item);
        Map<String, Object> oldChanged = new LinkedHashMap<>();
        Map<String, Object> newChanged = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                oldChanged.put(k, before.get(k));
                newChanged.put(k, v);
            }
        });
        if (!newChanged.isEmpty() || in.stockEntries() != null) {
            logs.save(InventoryLog.event(item.getId(), userId(), InventoryLog.ITEM_UPDATED, ITEM_TYPE, item.getId(),
                    "Updated item: " + item.getCode() + " - fields: "
                            + (newChanged.isEmpty() ? "stock" : String.join(", ", newChanged.keySet()))));
        }
        if (!newChanged.isEmpty()) {
            audit.record("updated", AuditService.ITEM, item.getId(), "Updated Item " + item.getName(), oldChanged, newChanged);
        }
        return item;
    }

    /**
     * Deletes an item that has no history that matters for accountability: nothing on hand, no
     * requests and no registered property.
     */
    @Transactional
    public void delete(Long id) {
        Item item = items.lockById(id).orElseThrow(() -> ApiException.notFound("Item"));
        if (item.getCurrentStock() > 0) {
            throw ApiException.of(HttpStatus.CONFLICT, "This item still has " + item.getCurrentStock() + " "
                    + item.getUnitOfMeasure() + " on hand. Issue, adjust or dispose of the stock before deleting the item.");
        }
        Integer requests = jdbc.queryForObject("select count(*) from request_items where item_id = ?", Integer.class, id);
        Integer properties = jdbc.queryForObject("select count(*) from properties where item_id = ?", Integer.class, id);
        if ((requests != null && requests > 0) || (properties != null && properties > 0)) {
            throw ApiException.of(HttpStatus.CONFLICT,
                    "Cannot delete an item that has requests or registered assets. Remove those first.");
        }
        Map<String, Object> before = snapshot(item);
        stocks.deleteAll(stocks.findByItemIdOrderByReceivedDateAscIdAsc(id));
        items.delete(item);
        items.flush();
        logs.save(InventoryLog.event(null, userId(), InventoryLog.ITEM_DELETED, null, null,
                "Deleted item: " + item.getCode() + " - " + item.getName()));
        audit.record("deleted", AuditService.ITEM, id, "Deleted Item " + item.getName(), before, null);
    }

    // ───────────────────────── stock ─────────────────────────

    @Transactional
    public Stock addStock(Long itemId, LotInput in) {
        Item item = items.lockById(itemId).orElseThrow(() -> ApiException.notFound("Item"));
        if (in.quantity() == null || in.quantity() < 1) {
            throw ApiException.validation("quantity", "Enter a quantity of at least 1.");
        }
        if (in.unitCost() == null) {
            throw ApiException.validation("unit_cost", "Enter the unit cost.");
        }
        if (in.receivedDate() == null) {
            throw ApiException.validation("received_date", "Enter the date the stock was received.");
        }
        validateLot(in);
        if (!item.isAssembly()) checkClassification(item.classification(), in.unitCost());

        int before = item.getCurrentStock();
        Stock s = ledger.receive(item, newLot(itemId, in));
        logs.save(InventoryLog.movement(itemId, userId(), InventoryLog.STOCK_IN, s.getQuantity(), before,
                item.getCurrentStock(), STOCK_TYPE, s.getId(),
                "Stock added to " + item.getCode() + ": +" + s.getQuantity() + " units"));
        audit.record("created", AuditService.STOCK, s.getId(),
                "Received " + s.getQuantity() + " " + item.getUnitOfMeasure() + " of " + item.getName(), null, lotSnapshot(s));
        return s;
    }

    /** Moves part of a lot to another location; merges into an identical lot already there. */
    @Transactional
    public StockTransfer transfer(TransferInput in) {
        if (in.stockId() == null) throw ApiException.validation("stock_id", "Choose the lot to move.");
        if (in.quantity() == null || in.quantity() < 1) throw ApiException.validation("quantity", "Enter a quantity of at least 1.");
        String to = in.toLocation() == null ? "" : in.toLocation().trim();
        if (to.isEmpty()) throw ApiException.validation("to_location", "Enter the destination location.");

        Stock source = stocks.findById(in.stockId()).orElseThrow(() -> ApiException.validation("stock_id", "That lot no longer exists."));
        Item item = items.lockById(source.getItemId()).orElseThrow(() -> ApiException.notFound("Item"));
        source = stocks.findById(in.stockId()).orElseThrow();
        int qty = in.quantity();
        if (qty > source.getQuantity()) {
            throw ApiException.validation("quantity", "Transfer (" + qty + ") exceeds the lot's on-hand quantity (" + source.getQuantity() + ").");
        }
        String from = source.getLocation();
        if (from != null && from.trim().equalsIgnoreCase(to)) {
            throw ApiException.validation("to_location", "Source and destination locations are the same.");
        }

        source.setQuantity(source.getQuantity() - qty);
        Stock dest = stocks.firstSameLotAt(source, to).orElse(null);
        if (dest != null) {
            dest.setQuantity(dest.getQuantity() + qty);
        } else {
            stocks.save(source.splitTo(to, qty));
        }

        StockTransfer t = transfers.save(new StockTransfer(nextTransferNumber(), item.getId(), source.getId(), from, to, qty,
                userId(), in.transferDate() == null ? LocalDate.now(Times.ZONE) : in.transferDate(), blankToNull(in.remarks())));
        logs.save(InventoryLog.movement(item.getId(), userId(), InventoryLog.TRANSFER, qty, item.getCurrentStock(),
                item.getCurrentStock(), TRANSFER_TYPE, t.getId(),
                "Transfer " + t.getTransferNumber() + ": " + qty + " of \"" + item.getName() + "\" from "
                        + (from == null || from.isBlank() ? "(unspecified)" : from) + " to " + to + "."));
        audit.record("created", AuditService.STOCK_TRANSFER, t.getId(), "Created StockTransfer " + t.getTransferNumber(), null,
                Map.of("item_id", item.getId(), "from_location", String.valueOf(from), "to_location", to, "quantity", qty));
        return t;
    }

    // ───────────────────────── helpers ─────────────────────────

    private void reconcileLots(Item item, List<LotInput> wanted) {
        List<Stock> existing = stocks.findByItemIdOrderByReceivedDateAscIdAsc(item.getId());
        Map<Long, Stock> byId = new LinkedHashMap<>();
        existing.forEach(s -> byId.put(s.getId(), s));
        Set<Long> kept = new HashSet<>();
        int delta = 0;
        for (LotInput l : wanted) {
            int qty = l.quantity() == null ? 0 : l.quantity();
            Stock s = l.id() == null ? null : byId.get(l.id());
            if (s != null) {
                delta += qty - s.getQuantity();
                copyLot(s, l);
                kept.add(s.getId());
            } else {
                Stock created = stocks.save(newLot(item.getId(), l));
                delta += created.getQuantity();
                kept.add(created.getId());
            }
        }
        for (Stock s : existing) {
            if (!kept.contains(s.getId())) {
                delta -= s.getQuantity();
                stocks.delete(s);
            }
        }
        int before = item.getCurrentStock();
        int after = Math.max(0, before + delta);
        item.setCurrentStock(after);
        stocks.flush();
        ledger.recompute(item);
        if (delta != 0) {
            logs.save(InventoryLog.movement(item.getId(), userId(), InventoryLog.STOCK_EDITED, delta, before, after,
                    ITEM_TYPE, item.getId(), "Stock entries edited via item form (net change " + delta + ")"));
        }
    }

    /**
     * COA Circular 2022-004: a unit cost at or above the capitalization threshold is PPE, below it
     * Semi-Expendable. (Assemblies are judged by their assembled total, so they skip this.)
     */
    private void checkClassification(Classification cls, BigDecimal unitCost) {
        if (unitCost == null || unitCost.signum() <= 0) return;
        BigDecimal threshold = settings.capitalizationThreshold();
        if (cls == Classification.SEMI_EXPENDABLE && unitCost.compareTo(threshold) >= 0) {
            throw ApiException.validation("unit_cost", "Unit cost " + peso(unitCost) + " meets or exceeds the "
                    + peso(threshold) + " capitalization threshold. COA requires this item to be classified as PPE, not Semi-Expendable.");
        }
        if (cls == Classification.PPE && unitCost.compareTo(threshold) < 0) {
            throw ApiException.validation("unit_cost", "Unit cost " + peso(unitCost) + " is below the "
                    + peso(threshold) + " capitalization threshold. COA classifies this as Semi-Expendable Property, not PPE.");
        }
    }

    private void applyFields(Item item, ItemInput in, Classification cls, boolean assembly) {
        if (in.name() != null) item.setName(required("name", in.name(), 255));
        if (item.getName() == null) throw ApiException.validation("name", "Enter the item name.");
        if (in.description() != null) item.setDescription(blankToNull(in.description()));
        if (in.unitOfMeasure() != null) item.setUnitOfMeasure(required("unit_of_measure", in.unitOfMeasure(), 255));
        if (item.getUnitOfMeasure() == null) throw ApiException.validation("unit_of_measure", "Enter the unit of measure.");
        if (in.categoryId() != null) {
            item.setCategory(categories.findById(in.categoryId())
                    .orElseThrow(() -> ApiException.validation("category_id", "Choose a valid category.")));
        }
        if (item.getCategory() == null) throw ApiException.validation("category_id", "Choose a category.");
        if (in.minStock() != null) {
            if (in.minStock() < 0) throw ApiException.validation("min_stock", "The minimum stock cannot be negative.");
            item.setMinStock(in.minStock());
        }
        // Left out = unchanged; 0 = no maximum.
        if (in.maxStock() != null) {
            if (in.maxStock() < 0) throw ApiException.validation("max_stock", "The maximum stock cannot be negative.");
            item.setMaxStock(in.maxStock() == 0 ? null : in.maxStock());
        }
        if (item.getMaxStock() != null && item.getMaxStock() > 0 && item.getMaxStock() < item.getMinStock()) {
            throw ApiException.validation("max_stock", "The maximum stock must be at least the minimum stock.");
        }
        item.setClassification(cls);
        item.setAssembly(assembly);
    }

    private void validateLot(LotInput l) {
        if (l.quantity() != null && l.quantity() < 0) throw ApiException.validation("quantity", "Quantities cannot be negative.");
        if (l.unitCost() != null && l.unitCost().signum() < 0) throw ApiException.validation("unit_cost", "Unit cost cannot be negative.");
        if (l.warrantyMonths() != null && l.warrantyMonths() < 0) throw ApiException.validation("warranty_months", "Warranty months cannot be negative.");
        if (l.supplierId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from suppliers where id = ?", Integer.class, l.supplierId());
            if (n == null || n == 0) throw ApiException.validation("supplier_id", "Choose a valid supplier.");
        }
        if (l.expirationDate() != null && l.receivedDate() != null && l.expirationDate().isBefore(l.receivedDate())) {
            throw ApiException.validation("expiration_date", "The expiration date is before the received date.");
        }
    }

    private Stock newLot(Long itemId, LotInput l) {
        Stock s = new Stock();
        s.setItemId(itemId);
        copyLot(s, l);
        s.setReceivedDate(l.receivedDate() == null ? LocalDate.now(Times.ZONE) : l.receivedDate());
        return s;
    }

    private static void copyLot(Stock s, LotInput l) {
        s.setBrandName(blankToNull(l.brandName()));
        s.setSpecifications(blankToNull(l.specifications()));
        s.setWarranty(blankToNull(l.warranty()));
        s.setWarrantyMonths(l.warrantyMonths());
        s.setQuantity(l.quantity() == null ? 0 : l.quantity());
        s.setUnitCost(l.unitCost() == null ? BigDecimal.ZERO : l.unitCost().setScale(2, RoundingMode.HALF_UP));
        s.setSupplierId(l.supplierId());
        s.setLocation(blankToNull(l.location()));
        s.setLotNumber(blankToNull(l.lotNumber()));
        s.setExpirationDate(l.expirationDate());
        if (l.receivedDate() != null) s.setReceivedDate(l.receivedDate());
    }

    /** ITEM-00001, ITEM-00002, … — serialised with an advisory lock so two creates can't take the same number. */
    private String nextItemCode() {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtext('pams.item_code'))");
        int max = 0;
        for (String c : items.codesWithPrefix("ITEM")) {
            String digits = c.substring(c.indexOf('-') + 1);
            if (digits.matches("\\d+")) max = Math.max(max, Integer.parseInt(digits));
        }
        return String.format("ITEM-%05d", max + 1);
    }

    /** STR-2026-0001 … per year, serialised the same way. */
    private String nextTransferNumber() {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtext('pams.transfer_number'))");
        String prefix = "STR-" + LocalDate.now(Times.ZONE).getYear() + "-";
        return prefix + String.format("%04d", transfers.countWithPrefix(prefix) + 1);
    }

    private static Classification classification(String key) {
        if (key == null || key.isBlank()) throw ApiException.validation("classification", "Choose a COA classification.");
        try {
            return Classification.fromKey(key);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("classification", "Choose a valid COA classification.");
        }
    }

    private static BigDecimal maxCost(List<LotInput> lots) {
        return lots.stream().map(LotInput::unitCost).filter(c -> c != null && c.signum() > 0)
                .max(BigDecimal::compareTo).orElse(null);
    }

    private static String required(String field, String value, int max) {
        String v = value.trim();
        if (v.isEmpty()) throw ApiException.validation(field, "This field is required.");
        if (v.length() > max) throw ApiException.validation(field, "This field may not exceed " + max + " characters.");
        return v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String peso(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "₱" + f.format(v);
    }

    private Long userId() {
        return current.user().getId();
    }

    private static Map<String, Object> snapshot(Item i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", i.getCode());
        m.put("name", i.getName());
        m.put("description", i.getDescription());
        m.put("category_id", i.getCategory() == null ? null : i.getCategory().getId());
        m.put("unit_of_measure", i.getUnitOfMeasure());
        m.put("min_stock", i.getMinStock());
        m.put("max_stock", i.getMaxStock());
        m.put("classification", i.classification().key());
        m.put("is_assembly", i.isAssembly());
        m.put("current_stock", i.getCurrentStock());
        m.put("weighted_average_cost", i.getWeightedAverageCost() == null ? null : i.getWeightedAverageCost().toPlainString());
        return m;
    }

    private static Map<String, Object> lotSnapshot(Stock s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item_id", s.getItemId());
        m.put("quantity", s.getQuantity());
        m.put("unit_cost", s.getUnitCost().toPlainString());
        m.put("brand_name", s.getBrandName());
        m.put("lot_number", s.getLotNumber());
        m.put("location", s.getLocation());
        m.put("received_date", String.valueOf(s.getReceivedDate()));
        m.put("expiration_date", s.getExpirationDate() == null ? null : s.getExpirationDate().toString());
        return m;
    }
}

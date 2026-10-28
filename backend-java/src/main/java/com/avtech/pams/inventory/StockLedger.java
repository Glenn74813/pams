package com.avtech.pams.inventory;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.setting.SettingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every change to on-hand stock goes through here, so lots, the item's current_stock, its
 * weighted average cost and the stock card always agree. Callers must hold the item's row lock
 * ({@link #lock}) inside their transaction.
 */
@Component
public class StockLedger {

    /** Issued first: soonest expiry, then oldest receipt; lots without an expiry date go last. */
    private static final Comparator<Stock> FEFO = Comparator
            .comparing((Stock s) -> s.getExpirationDate() == null)
            .thenComparing(Stock::getExpirationDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Stock::getReceivedDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Stock::getId);

    private final ItemRepository items;
    private final StockRepository stocks;
    private final InventoryLogRepository logs;
    private final SettingService settings;

    public StockLedger(ItemRepository items, StockRepository stocks, InventoryLogRepository logs, SettingService settings) {
        this.items = items;
        this.stocks = stocks;
        this.logs = logs;
        this.settings = settings;
    }

    public record Consumed(Long lotId, String lotNumber, int quantity, LocalDate expirationDate) {
    }

    public record Issue(List<Consumed> consumed, List<String> warnings) {
    }

    public Item lock(Long itemId) {
        return items.lockById(itemId).orElseThrow(() -> ApiException.notFound("Item"));
    }

    /**
     * Takes stock out first-expired-first-out and never from an expired lot. Fails when there isn't
     * enough non-expired stock. Lots expiring within the warning window produce warnings.
     */
    public Issue issue(Item item, int qty) {
        LocalDate today = LocalDate.now(Times.ZONE);
        List<Stock> lots = onHandLots(item.getId());
        int usable = lots.stream().filter(l -> !isExpired(l, today)).mapToInt(Stock::getQuantity).sum();
        int expired = lots.stream().filter(l -> isExpired(l, today)).mapToInt(Stock::getQuantity).sum();
        if (usable < qty) {
            String msg = "Not enough usable stock of \"" + item.getName() + "\": " + usable + " available, " + qty + " requested.";
            if (expired > 0) {
                msg += " " + expired + " unit(s) are expired and cannot be issued; dispose of them through a Waste Materials Report.";
            }
            throw ApiException.of(HttpStatus.UNPROCESSABLE_CONTENT, msg);
        }

        int warnDays = settings.fefoWarningDays();
        List<Consumed> consumed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int remaining = qty;
        for (Stock lot : lots) {
            if (remaining == 0) break;
            if (isExpired(lot, today)) continue;
            int take = Math.min(remaining, lot.getQuantity());
            lot.setQuantity(lot.getQuantity() - take);
            remaining -= take;
            consumed.add(new Consumed(lot.getId(), lot.getLotNumber(), take, lot.getExpirationDate()));
            if (lot.getExpirationDate() != null) {
                long days = ChronoUnit.DAYS.between(today, lot.getExpirationDate());
                if (days <= warnDays) {
                    warnings.add(item.getName() + ": issued " + take + " from lot " + (lot.getLotNumber() == null ? "(no lot no.)" : lot.getLotNumber())
                            + " expiring " + lot.getExpirationDate() + " (" + days + " day(s) left).");
                }
            }
        }
        item.setCurrentStock(item.getCurrentStock() - qty);
        recompute(item);
        return new Issue(consumed, warnings);
    }

    /** On-hand stock that can still be issued (not in an expired lot). */
    public int usable(Long itemId) {
        LocalDate today = LocalDate.now(Times.ZONE);
        return onHandLots(itemId).stream().filter(l -> !isExpired(l, today)).mapToInt(Stock::getQuantity).sum();
    }

    /** Removes stock for a write-off: expired lots first, then first-expired-first-out. */
    public List<Consumed> writeOff(Item item, int qty) {
        LocalDate today = LocalDate.now(Times.ZONE);
        List<Stock> lots = new ArrayList<>(onHandLots(item.getId()));
        lots.sort(Comparator.comparing((Stock l) -> !isExpired(l, today)).thenComparing(FEFO));
        int available = lots.stream().mapToInt(Stock::getQuantity).sum();
        if (available < qty) {
            throw ApiException.of(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Only " + available + " " + item.getUnitOfMeasure() + " of \"" + item.getName() + "\" are on hand.");
        }
        List<Consumed> consumed = new ArrayList<>();
        int remaining = qty;
        for (Stock lot : lots) {
            if (remaining == 0) break;
            int take = Math.min(remaining, lot.getQuantity());
            lot.setQuantity(lot.getQuantity() - take);
            remaining -= take;
            consumed.add(new Consumed(lot.getId(), lot.getLotNumber(), take, lot.getExpirationDate()));
        }
        item.setCurrentStock(item.getCurrentStock() - qty);
        recompute(item);
        return consumed;
    }

    /** Takes units out of one specific lot (e.g. registering serialised assets from a chosen brand lot). */
    public Stock takeFromLot(Item item, Long lotId, int qty) {
        Stock lot = stocks.findById(lotId).filter(l -> l.getItemId().equals(item.getId()))
                .orElseThrow(() -> ApiException.validation("stock_id", "That lot does not belong to this item."));
        if (qty > lot.getQuantity()) {
            throw ApiException.validation("units", "Only " + lot.getQuantity() + " unit(s) left in that lot.");
        }
        lot.setQuantity(lot.getQuantity() - qty);
        item.setCurrentStock(item.getCurrentStock() - qty);
        recompute(item);
        return lot;
    }

    /** Puts units back into the lot they came from (or a new lot at the given cost if it is gone). */
    public void putBack(Item item, Long lotId, int qty, BigDecimal unitCost, String location) {
        Stock lot = lotId == null ? null : stocks.findById(lotId).orElse(null);
        if (lot != null) {
            lot.setQuantity(lot.getQuantity() + qty);
            item.setCurrentStock(item.getCurrentStock() + qty);
            recompute(item);
        } else {
            Stock fresh = new Stock();
            fresh.setItemId(item.getId());
            fresh.setQuantity(qty);
            fresh.setUnitCost(unitCost == null ? item.getWeightedAverageCost() : unitCost);
            fresh.setReceivedDate(LocalDate.now(Times.ZONE));
            fresh.setLocation(location);
            receive(item, fresh);
        }
    }

    /** Adds a new lot and brings it into the on-hand count. */
    public Stock receive(Item item, Stock lot) {
        Stock saved = stocks.save(lot);
        item.setCurrentStock(item.getCurrentStock() + saved.getQuantity());
        recompute(item);
        return saved;
    }

    /** Weighted average unit cost over the lots still holding stock. */
    public void recompute(Item item) {
        stocks.flush();
        BigDecimal value = BigDecimal.ZERO;
        long qty = 0;
        for (Stock s : stocks.findByItemIdOrderByReceivedDateAscIdAsc(item.getId())) {
            if (s.getQuantity() > 0) {
                value = value.add(s.getUnitCost().multiply(BigDecimal.valueOf(s.getQuantity())));
                qty += s.getQuantity();
            }
        }
        item.setWeightedAverageCost(qty == 0 ? BigDecimal.ZERO.setScale(2)
                : value.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP));
    }

    /** One stock-card row. quantity is positive; the action says which direction. */
    public void log(Item item, Long userId, String action, int quantity, int before, String referenceType, Long referenceId,
                    String notes) {
        logs.save(InventoryLog.movement(item.getId(), userId, action, quantity, before, item.getCurrentStock(),
                referenceType, referenceId, notes));
    }

    public void event(Long itemId, Long userId, String action, String referenceType, Long referenceId, String notes) {
        logs.save(InventoryLog.event(itemId, userId, action, referenceType, referenceId, notes));
    }

    private List<Stock> onHandLots(Long itemId) {
        List<Stock> lots = new ArrayList<>(stocks.findByItemIdOrderByReceivedDateAscIdAsc(itemId).stream()
                .filter(s -> s.getQuantity() > 0).toList());
        lots.sort(FEFO);
        return lots;
    }

    private static boolean isExpired(Stock lot, LocalDate today) {
        return lot.getExpirationDate() != null && lot.getExpirationDate().isBefore(today);
    }
}

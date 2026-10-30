package com.avtech.pams.inventory;

import com.avtech.pams.common.Times;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** JSON shapes for the inventory endpoints. */
public final class InventoryViews {

    private InventoryViews() {
    }

    public record CategoryRef(Long id, String name) {
    }

    public record CategoryView(Long id, String name, Long parentId, boolean isLeaf, long itemsCount) {
    }

    public record SupplierRef(Long id, String name) {
    }

    public record ItemRef(Long id, String code, String name, String unitOfMeasure) {
        static ItemRef of(Item i) {
            return i == null ? null : new ItemRef(i.getId(), i.getCode(), i.getName(), i.getUnitOfMeasure());
        }
    }

    public record ItemView(
            Long id,
            String code,
            String name,
            String description,
            Long categoryId,
            CategoryRef category,
            String unitOfMeasure,
            BigDecimal weightedAverageCost,
            int currentStock,
            int minStock,
            Integer maxStock,
            Classification classification,
            String classificationLabel,
            boolean isExpandable,
            boolean isSemiExpandable,
            boolean isAssembly,
            boolean isAvailable,
            StockStatus stockStatus,
            BigDecimal totalValue,
            int suggestedOrder,
            boolean hasPhoto,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<StockView> stocks,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static ItemView of(Item i) {
            return of(i, null);
        }

        public static ItemView of(Item i, List<StockView> stocks) {
            var c = i.getCategory();
            return new ItemView(i.getId(), i.getCode(), i.getName(), i.getDescription(),
                    c == null ? null : c.getId(), c == null ? null : new CategoryRef(c.getId(), c.getName()),
                    i.getUnitOfMeasure(), i.getWeightedAverageCost(), i.getCurrentStock(), i.getMinStock(), i.getMaxStock(),
                    i.classification(), i.classification().label(), i.isPpe(), i.isSemiExpendable(), i.isAssembly(),
                    i.isAvailable(), i.stockStatus(),
                    i.getWeightedAverageCost().multiply(BigDecimal.valueOf(Math.max(0, i.getCurrentStock())))
                            .setScale(2, RoundingMode.HALF_UP),
                    i.suggestedOrder(), i.hasPhoto(), stocks, Times.toOffset(i.getCreatedAt()), Times.toOffset(i.getUpdatedAt()));
        }
    }

    public record StockView(
            Long id,
            Long itemId,
            @JsonInclude(JsonInclude.Include.NON_NULL) ItemRef item,
            String brandName,
            String specifications,
            String warranty,
            Integer warrantyMonths,
            LocalDate warrantyExpiry,
            String warrantyStatus,
            int quantity,
            BigDecimal unitCost,
            BigDecimal totalValue,
            String lotNumber,
            LocalDate expirationDate,
            Long daysUntilExpiration,
            LocalDate receivedDate,
            Long supplierId,
            SupplierRef supplier,
            String location) {

        public static StockView of(Stock s, Item item, Map<Long, String> supplierNames) {
            LocalDate today = LocalDate.now(Times.ZONE);
            LocalDate wExpiry = s.warrantyExpiry();
            String wStatus;
            if (wExpiry == null) wStatus = "none";
            else if (wExpiry.isBefore(today)) wStatus = "expired";
            else if (!wExpiry.isAfter(today.plusDays(30))) wStatus = "expiring";
            else wStatus = "active";
            Long sid = s.getSupplierId();
            return new StockView(s.getId(), s.getItemId(), ItemRef.of(item), s.getBrandName(), s.getSpecifications(),
                    s.getWarranty(), s.getWarrantyMonths(), wExpiry, wStatus, s.getQuantity(), s.getUnitCost(),
                    s.getUnitCost().multiply(BigDecimal.valueOf(s.getQuantity())).setScale(2, RoundingMode.HALF_UP),
                    s.getLotNumber(), s.getExpirationDate(),
                    s.getExpirationDate() == null ? null : ChronoUnit.DAYS.between(today, s.getExpirationDate()),
                    s.getReceivedDate(), sid, sid == null ? null : new SupplierRef(sid, supplierNames.get(sid)),
                    s.getLocation());
        }
    }

    public record TransferView(Long id, String transferNumber, ItemRef item, String fromLocation, String toLocation,
                               int quantity, LocalDate transferDate, String remarks, String transferredBy) {
    }
}

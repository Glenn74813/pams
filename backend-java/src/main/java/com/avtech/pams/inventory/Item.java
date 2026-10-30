package com.avtech.pams.inventory;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A catalogue item. COA classification is stored in two flags inherited from the Laravel schema:
 * {@code is_expandable} means PPE (despite the name) and {@code is_semi_expandable} means
 * Semi-Expendable Property; neither set means consumable supplies.
 */
@Entity
@Table(name = "items")
public class Item extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "unit_of_measure")
    private String unitOfMeasure;

    @Column(name = "weighted_average_cost", nullable = false)
    private BigDecimal weightedAverageCost = BigDecimal.ZERO;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(name = "max_stock")
    private Integer maxStock;

    /** Set through the photo endpoints only, never by saving the item. */
    @Column(name = "photo_path", insertable = false, updatable = false)
    private String photoPath;

    @Column(name = "is_expandable", nullable = false)
    private boolean ppe;

    @Column(name = "is_semi_expandable", nullable = false)
    private boolean semiExpendable;

    @Column(name = "is_assembly", nullable = false)
    private boolean assembly;

    @Column(name = "qr_code_path")
    private String qrCodePath;

    public Classification classification() {
        if (ppe) return Classification.PPE;
        if (semiExpendable) return Classification.SEMI_EXPENDABLE;
        return Classification.CONSUMABLE;
    }

    public void setClassification(Classification c) {
        this.ppe = c == Classification.PPE;
        this.semiExpendable = c == Classification.SEMI_EXPENDABLE;
    }

    /** PPE is tracked per unit as property, so it can be requested even with no loose stock. */
    public boolean isAvailable() {
        return currentStock > 0 || ppe;
    }

    /**
     * The re-order quantity Warehouse and Monitoring both show: when stock is at or below the minimum,
     * top up to the maximum (or to twice the minimum when no maximum is set). PPE is tracked per asset.
     */
    public static int suggestedOrder(int current, int min, int max, boolean ppe) {
        if (ppe || min <= 0 || current > min) return 0;
        int target = max > 0 ? max : min * 2;
        return Math.max(0, Math.max(target - current, min - current + 1));
    }

    public int suggestedOrder() {
        return suggestedOrder(currentStock, minStock, maxStock == null ? 0 : maxStock, ppe);
    }

    public StockStatus stockStatus() {
        if (currentStock <= 0) return StockStatus.OUT;
        if (currentStock <= minStock) return StockStatus.LOW;
        if (maxStock != null && maxStock > 0 && currentStock > maxStock) return StockStatus.OVER;
        return StockStatus.OK;
    }

    public Long getId() {
        return id;
    }

    public boolean hasPhoto() {
        return photoPath != null && !photoPath.isBlank();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    public void setUnitOfMeasure(String unitOfMeasure) {
        this.unitOfMeasure = unitOfMeasure;
    }

    public BigDecimal getWeightedAverageCost() {
        return weightedAverageCost;
    }

    public void setWeightedAverageCost(BigDecimal weightedAverageCost) {
        this.weightedAverageCost = weightedAverageCost;
    }

    public int getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(int currentStock) {
        this.currentStock = currentStock;
    }

    public int getMinStock() {
        return minStock;
    }

    public void setMinStock(int minStock) {
        this.minStock = minStock;
    }

    public Integer getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(Integer maxStock) {
        this.maxStock = maxStock;
    }

    public boolean isPpe() {
        return ppe;
    }

    public boolean isSemiExpendable() {
        return semiExpendable;
    }

    public boolean isAssembly() {
        return assembly;
    }

    public void setAssembly(boolean assembly) {
        this.assembly = assembly;
    }

    public String getQrCodePath() {
        return qrCodePath;
    }
}

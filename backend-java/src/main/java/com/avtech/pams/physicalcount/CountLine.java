package com.avtech.pams.physicalcount;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A stock-room item on a supplies count: the book balance at the cut-off and what was counted. */
@Entity
@Table(name = "physical_inventory_count_items")
public class CountLine extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "physical_inventory_count_id", nullable = false)
    private Long countId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "system_quantity", nullable = false)
    private int systemQuantity;

    @Column(name = "counted_quantity")
    private Integer countedQuantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost = BigDecimal.ZERO;

    private String location;
    private String remarks;

    @Column(name = "counted_by")
    private Long countedBy;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    @Column(name = "stock_adjustment_id")
    private Long stockAdjustmentId;

    protected CountLine() {
    }

    /** Variance against the cut-off balance; null until counted. */
    public Integer variance() {
        return countedQuantity == null ? null : countedQuantity - systemQuantity;
    }

    public Long getId() { return id; }
    public Long getCountId() { return countId; }
    public Long getItemId() { return itemId; }
    public int getSystemQuantity() { return systemQuantity; }
    public Integer getCountedQuantity() { return countedQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getLocation() { return location; }
    public String getRemarks() { return remarks; }
    public Long getCountedBy() { return countedBy; }
    public LocalDateTime getCountedAt() { return countedAt; }
    public Long getStockAdjustmentId() { return stockAdjustmentId; }
    public void setStockAdjustmentId(Long stockAdjustmentId) { this.stockAdjustmentId = stockAdjustmentId; }

    public void record(Integer counted, String location, String remarks, Long by, LocalDateTime at) {
        this.countedQuantity = counted;
        this.location = location;
        this.remarks = remarks;
        this.countedBy = counted == null ? null : by;
        this.countedAt = counted == null ? null : at;
    }
}

package com.avtech.pams.adjustment;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A correction to an item's on-hand quantity. Takes effect only when someone else approves it. */
@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment extends Timestamped {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String INCREASE = "increase";
    public static final String DECREASE = "decrease";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjustment_number", nullable = false)
    private String adjustmentNumber;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "adjustment_type", nullable = false)
    private String adjustmentType;

    @Column(nullable = false)
    private int quantity;

    /** For increases: cost of the units added. Empty means the item's weighted average cost. */
    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status = PENDING;

    @Column(name = "previous_quantity")
    private Integer previousQuantity;

    @Column(name = "new_quantity")
    private Integer newQuantity;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private String remarks;

    protected StockAdjustment() {
    }

    public StockAdjustment(String number, Long itemId, String type, int quantity, BigDecimal unitCost, String reason,
                           String remarks, Long requestedBy, int onHand) {
        this.adjustmentNumber = number;
        this.itemId = itemId;
        this.adjustmentType = type;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.reason = reason;
        this.remarks = remarks;
        this.requestedBy = requestedBy;
        this.previousQuantity = onHand;
    }

    public boolean isIncrease() {
        return INCREASE.equals(adjustmentType);
    }

    public void decide(String status, Long by, LocalDateTime at, Integer before, Integer after) {
        this.status = status;
        this.approvedBy = by;
        this.approvedAt = at;
        if (before != null) this.previousQuantity = before;
        this.newQuantity = after;
    }

    public Long getId() {
        return id;
    }

    public String getAdjustmentNumber() {
        return adjustmentNumber;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getAdjustmentType() {
        return adjustmentType;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public Integer getPreviousQuantity() {
        return previousQuantity;
    }

    public Integer getNewQuantity() {
        return newQuantity;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}

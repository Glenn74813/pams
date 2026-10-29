package com.avtech.pams.purchasing;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One PO line, copied from the awarded RFQ or bidding line. */
@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder order;

    @Column(nullable = false)
    private String description;

    private String specifications;
    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false)
    private BigDecimal totalCost;

    @Column(name = "rfq_item_id")
    private Long rfqItemId;

    @Column(name = "bidding_item_id")
    private Long biddingItemId;

    protected PurchaseOrderItem() {
    }

    public PurchaseOrderItem(PurchaseOrder order, String description, String specifications, String unit, int quantity, BigDecimal unitCost,
                             Long rfqItemId, Long biddingItemId) {
        this.order = order;
        this.description = description;
        this.specifications = specifications;
        this.unit = unit;
        this.quantity = quantity;
        this.rfqItemId = rfqItemId;
        this.biddingItemId = biddingItemId;
        setUnitCost(unitCost);
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
        this.totalCost = this.unitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public String getSpecifications() { return specifications; }
    public String getUnit() { return unit; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public Long getRfqItemId() { return rfqItemId; }
    public Long getBiddingItemId() { return biddingItemId; }
}

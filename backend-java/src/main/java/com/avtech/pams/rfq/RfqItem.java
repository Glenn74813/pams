package com.avtech.pams.rfq;

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
import java.math.RoundingMode;

/** One line of an RFQ, drawn from a purchase request line. */
@Entity
@Table(name = "rfq_items")
public class RfqItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id")
    private Rfq rfq;

    @Column(name = "purchase_request_item_id")
    private Long purchaseRequestItemId;

    @Column(name = "general_description", nullable = false)
    private String generalDescription;

    private String specifications;
    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "abc_unit_cost", nullable = false)
    private BigDecimal abcUnitCost;

    @Column(name = "delivery_lead_time")
    private String deliveryLeadTime;

    private String remarks;

    protected RfqItem() {
    }

    public RfqItem(Rfq rfq, Long prItemId, String description, String specifications, String unit, int quantity, BigDecimal abcUnitCost, String leadTime) {
        this.rfq = rfq;
        this.purchaseRequestItemId = prItemId;
        this.generalDescription = description;
        this.specifications = specifications;
        this.unit = unit;
        this.quantity = quantity;
        this.abcUnitCost = abcUnitCost;
        this.deliveryLeadTime = leadTime;
    }

    public BigDecimal getAbcTotal() {
        return abcUnitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Long getPurchaseRequestItemId() { return purchaseRequestItemId; }
    public String getGeneralDescription() { return generalDescription; }
    public String getSpecifications() { return specifications; }
    public String getUnit() { return unit; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAbcUnitCost() { return abcUnitCost; }
    public String getDeliveryLeadTime() { return deliveryLeadTime; }
    public String getRemarks() { return remarks; }
}

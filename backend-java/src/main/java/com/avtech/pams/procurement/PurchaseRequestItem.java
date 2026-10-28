package com.avtech.pams.procurement;

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

@Entity
@Table(name = "purchase_request_items")
public class PurchaseRequestItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_request_id")
    private PurchaseRequest request;

    @Column(name = "app_item_id")
    private Long appItemId;

    @Column(name = "general_description", nullable = false)
    private String generalDescription;

    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "estimated_unit_cost", nullable = false)
    private BigDecimal estimatedUnitCost;

    @Column(name = "estimated_total", nullable = false)
    private BigDecimal estimatedTotal;

    private String specifications;
    private String remarks;

    @Column(name = "is_sustainable", nullable = false)
    private boolean sustainable;

    @Column(name = "fund_source")
    private String fundSource;

    @Column(name = "procurement_mode")
    private String procurementMode;

    @Column(name = "procurement_category", nullable = false)
    private String procurementCategory;

    protected PurchaseRequestItem() {
    }

    /** A line, with blanks filled from the APP line it draws on (if any). */
    public PurchaseRequestItem(PurchaseRequest request, ProcurementRules.Line l, AppItem from) {
        this.request = request;
        this.appItemId = from == null ? null : from.getId();
        this.generalDescription = l.description();
        this.unit = l.unit() != null ? l.unit() : from == null ? null : from.getUnit();
        this.quantity = l.quantity();
        this.estimatedUnitCost = l.unitCost();
        this.estimatedTotal = l.total();
        this.specifications = l.specifications() != null ? l.specifications() : from == null ? null : from.getSpecifications();
        this.remarks = l.remarks();
        this.sustainable = l.sustainable();
        this.fundSource = l.fundSource() != null ? l.fundSource() : from == null ? null : from.getFundSource();
        this.procurementMode = l.mode() != null ? l.mode() : from == null ? null : from.getProcurementMode();
        this.procurementCategory = l.category() != null ? l.category() : from != null ? from.getProcurementCategory() : "Goods";
    }

    public Long getId() { return id; }
    public Long getAppItemId() { return appItemId; }
    public String getGeneralDescription() { return generalDescription; }
    public String getUnit() { return unit; }
    public int getQuantity() { return quantity; }
    public BigDecimal getEstimatedUnitCost() { return estimatedUnitCost; }
    public BigDecimal getEstimatedTotal() { return estimatedTotal; }
    public String getSpecifications() { return specifications; }
    public String getRemarks() { return remarks; }
    public boolean isSustainable() { return sustainable; }
    public String getFundSource() { return fundSource; }
    public String getProcurementMode() { return procurementMode; }
    public String getProcurementCategory() { return procurementCategory; }
}

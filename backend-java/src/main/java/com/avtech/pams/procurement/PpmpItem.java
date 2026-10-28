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

/** One planned procurement in a PPMP. */
@Entity
@Table(name = "ppmp_items")
public class PpmpItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ppmp_id")
    private Ppmp ppmp;

    @Column(name = "general_description", nullable = false)
    private String generalDescription;

    private String specifications;

    /** Accounting (UACS object) code the line is charged to. */
    private String code;

    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "estimated_unit_cost", nullable = false)
    private BigDecimal estimatedUnitCost;

    @Column(name = "estimated_total", nullable = false)
    private BigDecimal estimatedTotal;

    @Column(name = "procurement_mode", nullable = false)
    private String procurementMode;

    @Column(name = "procurement_category", nullable = false)
    private String procurementCategory;

    @Column(name = "is_sustainable", nullable = false)
    private boolean sustainable;

    private String schedule;

    @Column(name = "fund_source")
    private String fundSource;

    private String remarks;

    protected PpmpItem() {
    }

    public PpmpItem(Ppmp ppmp, ProcurementRules.Line l) {
        this.ppmp = ppmp;
        this.generalDescription = l.description();
        this.specifications = l.specifications();
        this.code = l.code();
        this.unit = l.unit();
        this.quantity = l.quantity();
        this.estimatedUnitCost = l.unitCost();
        this.estimatedTotal = l.total();
        this.procurementMode = l.mode();
        this.procurementCategory = l.category();
        this.sustainable = l.sustainable();
        this.schedule = l.schedule();
        this.fundSource = l.fundSource();
        this.remarks = l.remarks();
    }

    public Long getId() { return id; }
    public Ppmp getPpmp() { return ppmp; }
    public String getGeneralDescription() { return generalDescription; }
    public String getSpecifications() { return specifications; }
    public String getCode() { return code; }
    public String getUnit() { return unit; }
    public int getQuantity() { return quantity; }
    public BigDecimal getEstimatedUnitCost() { return estimatedUnitCost; }
    public BigDecimal getEstimatedTotal() { return estimatedTotal; }
    public String getProcurementMode() { return procurementMode; }
    public String getProcurementCategory() { return procurementCategory; }
    public boolean isSustainable() { return sustainable; }
    public String getSchedule() { return schedule; }
    public String getFundSource() { return fundSource; }
    public String getRemarks() { return remarks; }
}

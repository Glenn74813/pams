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

/**
 * One APP line, copied from a PPMP line. How much of it has been requested and committed is
 * worked out from the purchase requests that draw on it, not stored here.
 */
@Entity
@Table(name = "app_items")
public class AppItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id")
    private AppPlan app;

    @Column(name = "ppmp_id")
    private Long ppmpId;

    @Column(name = "ppmp_item_id")
    private Long ppmpItemId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "general_description", nullable = false)
    private String generalDescription;

    private String specifications;
    private String unit;

    @Column(name = "procurement_mode", nullable = false)
    private String procurementMode;

    @Column(name = "procurement_category", nullable = false)
    private String procurementCategory;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "estimated_budget", nullable = false)
    private BigDecimal estimatedBudget;

    private String schedule;

    @Column(name = "fund_source")
    private String fundSource;

    @Column(nullable = false)
    private String status = "planned";

    private String remarks;

    protected AppItem() {
    }

    public AppItem(AppPlan app, Ppmp ppmp, PpmpItem p) {
        this.app = app;
        this.ppmpId = ppmp.getId();
        this.ppmpItemId = p.getId();
        this.departmentId = ppmp.getDepartmentId();
        this.generalDescription = p.getGeneralDescription();
        this.specifications = p.getSpecifications();
        this.unit = p.getUnit();
        this.procurementMode = p.getProcurementMode();
        this.procurementCategory = p.getProcurementCategory();
        this.quantity = p.getQuantity();
        this.estimatedBudget = p.getEstimatedTotal();
        this.schedule = p.getSchedule();
        this.fundSource = p.getFundSource();
        this.remarks = p.getRemarks();
    }

    public Long getId() { return id; }
    public AppPlan getApp() { return app; }
    public Long getPpmpId() { return ppmpId; }
    public Long getPpmpItemId() { return ppmpItemId; }
    public Long getDepartmentId() { return departmentId; }
    public String getGeneralDescription() { return generalDescription; }
    public void setGeneralDescription(String generalDescription) { this.generalDescription = generalDescription; }
    public String getSpecifications() { return specifications; }
    public void setSpecifications(String specifications) { this.specifications = specifications; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getProcurementMode() { return procurementMode; }
    public void setProcurementMode(String procurementMode) { this.procurementMode = procurementMode; }
    public String getProcurementCategory() { return procurementCategory; }
    public void setProcurementCategory(String procurementCategory) { this.procurementCategory = procurementCategory; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getEstimatedBudget() { return estimatedBudget; }
    public void setEstimatedBudget(BigDecimal estimatedBudget) { this.estimatedBudget = estimatedBudget; }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public String getFundSource() { return fundSource; }
    public void setFundSource(String fundSource) { this.fundSource = fundSource; }
    public String getRemarks() { return remarks; }
}

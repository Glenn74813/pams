package com.avtech.pams.property;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * One physical asset (PPE under a PAR, or semi-expendable under an ICS) with its holder, status
 * and depreciation figures.
 */
@Entity
@Table(name = "properties")
public class Property extends Timestamped {

    public static final String ASSIGNED = "assigned";
    public static final String AVAILABLE = "available";
    public static final String UNDER_REPAIR = "under_repair";
    public static final String RETIRED = "retired";
    public static final String DISPOSED = "disposed";
    /** Reported lost (e.g. not returned from a gate pass); stays with its custodian until relieved through disposal. */
    public static final String LOST = "lost";
    public static final Set<String> CONDITIONS = Set.of("excellent", "good", "fair", "poor");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "tag_number", nullable = false)
    private String tagNumber;

    @Column(name = "serial_number")
    private String serialNumber;

    private String brand;
    private String specifications;
    private String warranty;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "accountability_number")
    private String accountabilityNumber;

    @Column(nullable = false)
    private String status = ASSIGNED;

    @Column(nullable = false)
    private String condition = "good";

    @Column(name = "acquisition_cost")
    private BigDecimal acquisitionCost;

    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;

    @Column(name = "salvage_value")
    private BigDecimal salvageValue;

    @Column(name = "useful_life_years", nullable = false)
    private int usefulLifeYears = 5;

    @Column(name = "depreciation_method", nullable = false)
    private String depreciationMethod = "straight-line";

    @Column(name = "depreciation_rate")
    private BigDecimal depreciationRate;

    @Column(name = "accumulated_depreciation", nullable = false)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Column(name = "depreciation_start_date")
    private LocalDate depreciationStartDate;

    @Column(name = "last_depreciation_calculation")
    private LocalDateTime lastDepreciationCalculation;

    @Column(name = "is_depreciable", nullable = false)
    private boolean depreciable = true;

    @Column(name = "current_book_value")
    private BigDecimal currentBookValue;

    @Column(name = "qr_code_path")
    private String qrCodePath;

    public boolean isGone() {
        return DISPOSED.equals(status);
    }

    // Accessors

    public Long getId() { return id; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getStockId() { return stockId; }
    public void setStockId(Long stockId) { this.stockId = stockId; }
    public Long getParentId() { return parentId; }
    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getSpecifications() { return specifications; }
    public void setSpecifications(String specifications) { this.specifications = specifications; }
    public String getWarranty() { return warranty; }
    public void setWarranty(String warranty) { this.warranty = warranty; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public Long getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Long assignedTo) { this.assignedTo = assignedTo; }
    public LocalDate getAssignedDate() { return assignedDate; }
    public void setAssignedDate(LocalDate assignedDate) { this.assignedDate = assignedDate; }
    public String getAccountabilityNumber() { return accountabilityNumber; }
    public void setAccountabilityNumber(String accountabilityNumber) { this.accountabilityNumber = accountabilityNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public BigDecimal getAcquisitionCost() { return acquisitionCost; }
    public void setAcquisitionCost(BigDecimal acquisitionCost) { this.acquisitionCost = acquisitionCost; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public void setAcquisitionDate(LocalDate acquisitionDate) { this.acquisitionDate = acquisitionDate; }
    public BigDecimal getSalvageValue() { return salvageValue; }
    public void setSalvageValue(BigDecimal salvageValue) { this.salvageValue = salvageValue; }
    public int getUsefulLifeYears() { return usefulLifeYears; }
    public void setUsefulLifeYears(int usefulLifeYears) { this.usefulLifeYears = usefulLifeYears; }
    public String getDepreciationMethod() { return depreciationMethod; }
    public BigDecimal getAccumulatedDepreciation() { return accumulatedDepreciation; }
    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) { this.accumulatedDepreciation = accumulatedDepreciation; }
    public LocalDate getDepreciationStartDate() { return depreciationStartDate; }
    public void setDepreciationStartDate(LocalDate depreciationStartDate) { this.depreciationStartDate = depreciationStartDate; }
    public LocalDateTime getLastDepreciationCalculation() { return lastDepreciationCalculation; }
    public void setLastDepreciationCalculation(LocalDateTime t) { this.lastDepreciationCalculation = t; }
    public boolean isDepreciable() { return depreciable; }
    public void setDepreciable(boolean depreciable) { this.depreciable = depreciable; }
    public BigDecimal getCurrentBookValue() { return currentBookValue; }
    public void setCurrentBookValue(BigDecimal currentBookValue) { this.currentBookValue = currentBookValue; }
}

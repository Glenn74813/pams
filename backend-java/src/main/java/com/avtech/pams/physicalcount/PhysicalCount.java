package com.avtech.pams.physicalcount;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One physical count: draft → in_progress (book balances frozen at the cut-off) → completed
 * (counting closed, unchecked tags marked missing) → verified (variances posted by someone other
 * than the people who counted).
 */
@Entity
@Table(name = "physical_inventory_counts")
public class PhysicalCount extends Timestamped {

    public static final String DRAFT = "draft";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";
    public static final String VERIFIED = "verified";

    public static final String SUPPLIES = "supplies";
    public static final String PPE = "ppe";
    public static final String SEMI_EXPENDABLE = "semi_expendable";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "count_number", nullable = false)
    private String countNumber;

    @Column(name = "count_type", nullable = false)
    private String countType;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false)
    private String status = DRAFT;

    private String purpose;
    private String remarks;

    @Column(name = "total_items_counted", nullable = false)
    private int totalItemsCounted;

    @Column(name = "total_items_found", nullable = false)
    private int totalItemsFound;

    @Column(name = "total_discrepancies", nullable = false)
    private int totalDiscrepancies;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "cutoff_log_id")
    private Long cutoffLogId;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    protected PhysicalCount() {
    }

    public PhysicalCount(String countNumber, String countType, Long initiatedBy) {
        this.countNumber = countNumber;
        this.countType = countType;
        this.initiatedBy = initiatedBy;
    }

    public boolean isSupplies() { return SUPPLIES.equals(countType); }

    public Long getId() { return id; }
    public String getCountNumber() { return countNumber; }
    public String getCountType() { return countType; }
    public LocalDate getCountDate() { return countDate; }
    public void setCountDate(LocalDate countDate) { this.countDate = countDate; }
    public Long getInitiatedBy() { return initiatedBy; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public int getTotalItemsCounted() { return totalItemsCounted; }
    public int getTotalItemsFound() { return totalItemsFound; }
    public int getTotalDiscrepancies() { return totalDiscrepancies; }
    public void setTotals(int counted, int found, int discrepancies) {
        this.totalItemsCounted = counted;
        this.totalItemsFound = found;
        this.totalDiscrepancies = discrepancies;
    }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public Long getCutoffLogId() { return cutoffLogId; }
    public void setCutoffLogId(Long cutoffLogId) { this.cutoffLogId = cutoffLogId; }
    public Long getCompletedBy() { return completedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void complete(Long by, LocalDateTime at) {
        this.completedBy = by;
        this.completedAt = at;
    }
    public Long getVerifiedBy() { return verifiedBy; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void verify(Long by, LocalDateTime at) {
        this.verifiedBy = by;
        this.verifiedAt = at;
    }
}

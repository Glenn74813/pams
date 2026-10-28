package com.avtech.pams.procurement;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** The agency's Annual Procurement Plan for a fiscal year, consolidated from approved PPMPs. */
@Entity
@Table(name = "apps")
public class AppPlan extends Timestamped {

    public static final String CONSOLIDATED = "consolidated";
    public static final String APPROVED = "approved";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_number", nullable = false)
    private String appNumber;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String status = CONSOLIDATED;

    @Column(name = "total_budget", nullable = false)
    private BigDecimal totalBudget = BigDecimal.ZERO;

    @Column(name = "prepared_by")
    private Long preparedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private String remarks;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "app", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<AppItem> items = new ArrayList<>();

    protected AppPlan() {
    }

    public AppPlan(String number, Integer fiscalYear, String title, Long preparedBy) {
        this.appNumber = number;
        this.fiscalYear = fiscalYear;
        this.title = title;
        this.preparedBy = preparedBy;
    }

    public boolean isApproved() {
        return APPROVED.equals(status);
    }

    public void recalc() {
        totalBudget = items.stream().map(AppItem::getEstimatedBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getAppNumber() { return appNumber; }
    public Integer getFiscalYear() { return fiscalYear; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalBudget() { return totalBudget; }
    public Long getPreparedBy() { return preparedBy; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<AppItem> getItems() { return items; }
}

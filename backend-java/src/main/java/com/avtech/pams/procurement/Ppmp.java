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

/** A department's Project Procurement Management Plan for one fiscal year. */
@Entity
@Table(name = "ppmps")
public class Ppmp extends Timestamped {

    public static final String DRAFT = "draft";
    public static final String SUBMITTED = "submitted";
    public static final String UNDER_REVIEW = "under_review";
    public static final String APPROVED = "approved";
    public static final String RETURNED = "returned";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ppmp_number", nullable = false)
    private String ppmpNumber;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(name = "total_estimated_budget", nullable = false)
    private BigDecimal totalEstimatedBudget = BigDecimal.ZERO;

    @Column(nullable = false)
    private int revision;

    @Column(name = "prepared_by")
    private Long preparedBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "return_reason")
    private String returnReason;

    private String remarks;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "ppmp", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PpmpItem> items = new ArrayList<>();

    protected Ppmp() {
    }

    public Ppmp(String number, Long departmentId, Integer fiscalYear, Long preparedBy) {
        this.ppmpNumber = number;
        this.departmentId = departmentId;
        this.fiscalYear = fiscalYear;
        this.preparedBy = preparedBy;
    }

    public boolean isEditable() {
        return DRAFT.equals(status) || RETURNED.equals(status);
    }

    public boolean isInReview() {
        return SUBMITTED.equals(status) || UNDER_REVIEW.equals(status);
    }

    public void recalc() {
        totalEstimatedBudget = items.stream().map(PpmpItem::getEstimatedTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getPpmpNumber() { return ppmpNumber; }
    public Long getDepartmentId() { return departmentId; }
    public Integer getFiscalYear() { return fiscalYear; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalEstimatedBudget() { return totalEstimatedBudget; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public Long getPreparedBy() { return preparedBy; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<PpmpItem> getItems() { return items; }
}

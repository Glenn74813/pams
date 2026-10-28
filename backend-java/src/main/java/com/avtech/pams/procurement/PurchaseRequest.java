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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A purchase request, routed through the fixed RA 9184 / RA 12009 approval chain. */
@Entity
@Table(name = "purchase_requests")
public class PurchaseRequest extends Timestamped {

    public static final String DRAFT = "draft";
    public static final String SUBMITTED = "submitted";
    public static final String UNDER_REVIEW = "under_review";
    public static final String APPROVED = "approved";
    public static final String RETURNED = "returned";
    public static final String CANCELLED = "cancelled";

    /** role_key, label, permission that signs the step. */
    public static final List<String[]> CHAIN = List.of(
            new String[]{"department_head", "Department Head", "pr_approve_department"},
            new String[]{"budget_officer", "Budget Officer", "pr_approve_budget"},
            new String[]{"accounting", "Accounting", "pr_approve_accounting"},
            new String[]{"bac_secretariat", "BAC Secretariat", "pr_approve_bac"});

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pr_number", nullable = false)
    private String prNumber;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "app_id")
    private Long appId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    private String purpose;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(name = "current_step")
    private Integer currentStep;

    @Column(nullable = false)
    private String priority = "medium";

    @Column(name = "total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "caf_number")
    private String cafNumber;

    @Column(name = "caf_certified_by")
    private Long cafCertifiedBy;

    @Column(name = "caf_certified_at")
    private LocalDateTime cafCertifiedAt;

    @Column(name = "caf_amount")
    private BigDecimal cafAmount;

    @Column(name = "caf_remarks")
    private String cafRemarks;

    private String remarks;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseRequestItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("step")
    private List<PurchaseRequestApproval> approvals = new ArrayList<>();

    protected PurchaseRequest() {
    }

    public PurchaseRequest(String number, Long departmentId, Long requestedBy, LocalDate requestDate) {
        this.prNumber = number;
        this.departmentId = departmentId;
        this.requestedBy = requestedBy;
        this.requestDate = requestDate;
    }

    public boolean isEditable() {
        return DRAFT.equals(status) || RETURNED.equals(status);
    }

    public boolean isRouting() {
        return SUBMITTED.equals(status) || UNDER_REVIEW.equals(status);
    }

    public PurchaseRequestApproval currentApproval() {
        if (currentStep == null || !isRouting()) return null;
        return approvals.stream().filter(a -> a.getStep() == currentStep).findFirst().orElse(null);
    }

    public String stageLabel() {
        return switch (status) {
            case DRAFT -> "Draft";
            case SUBMITTED, UNDER_REVIEW -> currentStep != null && currentStep >= 1 && currentStep <= CHAIN.size()
                    ? CHAIN.get(currentStep - 1)[1] : "Step " + currentStep;
            case APPROVED -> "Completed";
            case RETURNED -> "Returned";
            case CANCELLED -> "Cancelled";
            default -> "—";
        };
    }

    public void recalc() {
        totalAmount = items.stream().map(PurchaseRequestItem::getEstimatedTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getPrNumber() { return prNumber; }
    public Long getDepartmentId() { return departmentId; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }
    public Long getRequestedBy() { return requestedBy; }
    public LocalDate getRequestDate() { return requestDate; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCafNumber() { return cafNumber; }
    public void setCafNumber(String cafNumber) { this.cafNumber = cafNumber; }
    public Long getCafCertifiedBy() { return cafCertifiedBy; }
    public void setCafCertifiedBy(Long cafCertifiedBy) { this.cafCertifiedBy = cafCertifiedBy; }
    public LocalDateTime getCafCertifiedAt() { return cafCertifiedAt; }
    public void setCafCertifiedAt(LocalDateTime cafCertifiedAt) { this.cafCertifiedAt = cafCertifiedAt; }
    public BigDecimal getCafAmount() { return cafAmount; }
    public void setCafAmount(BigDecimal cafAmount) { this.cafAmount = cafAmount; }
    public String getCafRemarks() { return cafRemarks; }
    public void setCafRemarks(String cafRemarks) { this.cafRemarks = cafRemarks; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<PurchaseRequestItem> getItems() { return items; }
    public List<PurchaseRequestApproval> getApprovals() { return approvals; }
}

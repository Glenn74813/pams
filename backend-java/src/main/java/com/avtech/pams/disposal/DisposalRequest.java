package com.avtech.pams.disposal;

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

/** Disposal of one asset (category "property") or a quantity of stock (category "inventory"). */
@Entity
@Table(name = "disposal_requests")
public class DisposalRequest extends Timestamped {

    public static final String DRAFT = "draft";
    public static final String SUBMITTED = "submitted";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String COMPLETED = "completed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disposal_number", nullable = false)
    private String disposalNumber;

    @Column(name = "disposal_category", nullable = false)
    private String category;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "item_id")
    private Long itemId;

    private Integer quantity;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "disposal_type", nullable = false)
    private String disposalType;

    @Column(nullable = false)
    private String status = DRAFT;

    private String reason;
    private String remarks;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "disposal_date")
    private LocalDate disposalDate;

    @Column(name = "book_value")
    private BigDecimal bookValue;

    private BigDecimal proceeds;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected DisposalRequest() {
    }

    public DisposalRequest(String number, String category, Long requestedBy, LocalDate requestDate) {
        this.disposalNumber = number;
        this.category = category;
        this.requestedBy = requestedBy;
        this.requestDate = requestDate;
    }

    public boolean isProperty() {
        return "property".equals(category);
    }

    public Long getId() { return id; }
    public String getDisposalNumber() { return disposalNumber; }
    public String getCategory() { return category; }
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getDisposalType() { return disposalType; }
    public void setDisposalType(String disposalType) { this.disposalType = disposalType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Long getRequestedBy() { return requestedBy; }
    public LocalDate getRequestDate() { return requestDate; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDate getApprovalDate() { return approvalDate; }
    public void setApprovalDate(LocalDate approvalDate) { this.approvalDate = approvalDate; }
    public LocalDate getDisposalDate() { return disposalDate; }
    public void setDisposalDate(LocalDate disposalDate) { this.disposalDate = disposalDate; }
    public BigDecimal getBookValue() { return bookValue; }
    public void setBookValue(BigDecimal bookValue) { this.bookValue = bookValue; }
    public BigDecimal getProceeds() { return proceeds; }
    public void setProceeds(BigDecimal proceeds) { this.proceeds = proceeds; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}

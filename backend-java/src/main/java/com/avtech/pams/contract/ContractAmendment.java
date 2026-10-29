package com.avtech.pams.contract;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** An amendment / variation order: a change in price and/or end date, approved by someone other than its preparer. */
@Entity
@Table(name = "contract_amendments")
public class ContractAmendment extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Column(name = "amendment_number", nullable = false)
    private int amendmentNumber;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "amount_change", nullable = false)
    private BigDecimal amountChange = BigDecimal.ZERO;

    @Column(name = "new_end_date")
    private LocalDate newEndDate;

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "prepared_by")
    private Long preparedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private String remarks;

    protected ContractAmendment() {
    }

    public ContractAmendment(Contract contract, int number, Long preparedBy) {
        this.contract = contract;
        this.amendmentNumber = number;
        this.preparedBy = preparedBy;
    }

    public Long getId() { return id; }
    public int getAmendmentNumber() { return amendmentNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmountChange() { return amountChange; }
    public void setAmountChange(BigDecimal amountChange) { this.amountChange = amountChange; }
    public LocalDate getNewEndDate() { return newEndDate; }
    public void setNewEndDate(LocalDate newEndDate) { this.newEndDate = newEndDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPreparedBy() { return preparedBy; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

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

/** A call-off order under a framework agreement. */
@Entity
@Table(name = "framework_calloffs")
public class FrameworkCalloff extends Timestamped {

    public static final List<String> STATUSES = List.of("draft", "issued", "completed", "cancelled");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Column(name = "calloff_number", nullable = false)
    private String calloffNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "calloff_date")
    private LocalDate calloffDate;

    @Column(nullable = false)
    private String status = "draft";

    private String remarks;

    @Column(name = "created_by")
    private Long createdBy;

    protected FrameworkCalloff() {
    }

    public FrameworkCalloff(Contract contract, String number, Long createdBy) {
        this.contract = contract;
        this.calloffNumber = number;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public String getCalloffNumber() { return calloffNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getCalloffDate() { return calloffDate; }
    public void setCalloffDate(LocalDate calloffDate) { this.calloffDate = calloffDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

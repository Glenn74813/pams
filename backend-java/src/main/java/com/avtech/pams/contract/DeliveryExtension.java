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

/** A supplier's request for more time to deliver, granted in full or in part, or denied. */
@Entity
@Table(name = "delivery_extensions")
public class DeliveryExtension extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Column(name = "requested_days", nullable = false)
    private int requestedDays;

    @Column(name = "approved_days")
    private Integer approvedDays;

    @Column(nullable = false)
    private String reason;

    @Column(name = "letter_path")
    private String letterPath;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private String remarks;

    protected DeliveryExtension() {
    }

    public DeliveryExtension(Contract contract, int requestedDays, String reason, LocalDate requestedDate, String remarks) {
        this.contract = contract;
        this.requestedDays = requestedDays;
        this.reason = reason;
        this.requestedDate = requestedDate;
        this.remarks = remarks;
    }

    public Long getId() { return id; }
    public int getRequestedDays() { return requestedDays; }
    public Integer getApprovedDays() { return approvedDays; }
    public void setApprovedDays(Integer approvedDays) { this.approvedDays = approvedDays; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

package com.avtech.pams.supplier;

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
import java.util.ArrayList;
import java.util.List;

/** A blacklisting or suspension: in force from its start date until its end date or until lifted. */
@Entity
@Table(name = "supplier_blacklists")
public class SupplierSanction extends Timestamped {

    public static final List<String> TYPES = List.of("Blacklist", "Suspension");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "sanction_type", nullable = false)
    private String sanctionType;

    @Column(nullable = false)
    private String grounds;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "lifted_at")
    private LocalDate liftedAt;

    @Column(name = "lifted_reason")
    private String liftedReason;

    @Column(name = "created_by")
    private Long createdBy;

    protected SupplierSanction() {
    }

    public SupplierSanction(Supplier supplier, Long createdBy) {
        this.supplier = supplier;
        this.createdBy = createdBy;
    }

    public boolean inForce(LocalDate on) {
        return "active".equals(status) && !startDate.isAfter(on) && (endDate == null || !endDate.isBefore(on));
    }

    public Long getId() { return id; }
    public String getSanctionType() { return sanctionType; }
    public void setSanctionType(String sanctionType) { this.sanctionType = sanctionType; }
    public String getGrounds() { return grounds; }
    public void setGrounds(String grounds) { this.grounds = grounds; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getLiftedAt() { return liftedAt; }
    public void setLiftedAt(LocalDate liftedAt) { this.liftedAt = liftedAt; }
    public String getLiftedReason() { return liftedReason; }
    public void setLiftedReason(String liftedReason) { this.liftedReason = liftedReason; }
    public Long getCreatedBy() { return createdBy; }
}

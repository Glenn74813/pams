package com.avtech.pams.gatepass;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Permission to take an asset off the premises, and its return. */
@Entity
@Table(name = "gate_passes")
public class GatePass extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gate_pass_number", nullable = false)
    private String gatePassNumber;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "released_by", nullable = false)
    private Long releasedBy;

    /** Who carries the asset out. */
    @Column(name = "received_by", nullable = false)
    private String receivedBy;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "date_released", nullable = false)
    private LocalDate dateReleased;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "actual_return_date")
    private LocalDate actualReturnDate;

    @Column(name = "condition_on_release", nullable = false)
    private String conditionOnRelease;

    @Column(name = "condition_on_return")
    private String conditionOnReturn;

    /** released / returned (stored); overdue is worked out when read. */
    @Column(nullable = false)
    private String status = "released";

    private String remarks;

    @Column(name = "returned_by")
    private String returnedBy;

    @Column(name = "received_back_by")
    private Long receivedBackBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected GatePass() {
    }

    public GatePass(String number, Long propertyId, Long releasedBy) {
        this.gatePassNumber = number;
        this.propertyId = propertyId;
        this.releasedBy = releasedBy;
    }

    public boolean isOut() {
        return !"returned".equals(status);
    }

    public Long getId() { return id; }
    public String getGatePassNumber() { return gatePassNumber; }
    public Long getPropertyId() { return propertyId; }
    public Long getReleasedBy() { return releasedBy; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public LocalDate getDateReleased() { return dateReleased; }
    public void setDateReleased(LocalDate dateReleased) { this.dateReleased = dateReleased; }
    public LocalDate getExpectedReturnDate() { return expectedReturnDate; }
    public void setExpectedReturnDate(LocalDate d) { this.expectedReturnDate = d; }
    public LocalDate getActualReturnDate() { return actualReturnDate; }
    public void setActualReturnDate(LocalDate d) { this.actualReturnDate = d; }
    public String getConditionOnRelease() { return conditionOnRelease; }
    public void setConditionOnRelease(String c) { this.conditionOnRelease = c; }
    public String getConditionOnReturn() { return conditionOnReturn; }
    public void setConditionOnReturn(String c) { this.conditionOnReturn = c; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getReturnedBy() { return returnedBy; }
    public void setReturnedBy(String returnedBy) { this.returnedBy = returnedBy; }
    public Long getReceivedBackBy() { return receivedBackBy; }
    public void setReceivedBackBy(Long id) { this.receivedBackBy = id; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}

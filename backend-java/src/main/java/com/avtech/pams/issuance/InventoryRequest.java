package com.avtech.pams.issuance;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A requisition for supplies (the RIS), later issued in whole or in part. Issued semi-expendable
 * items also get an ICS number and PPE a PAR number.
 */
@Entity
@Table(name = "requests")
public class InventoryRequest extends Timestamped {

    public static final String PENDING = "pending";
    public static final String REJECTED = "rejected";
    public static final String PARTIALLY_FULFILLED = "partially_fulfilled";
    public static final String FULFILLED = "fulfilled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false)
    private String requestNumber;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "request_date")
    private LocalDate requestDate;

    @Column(nullable = false)
    private String status = PENDING;

    @Column(nullable = false)
    private String priority = "medium";

    private String purpose;

    @Column(name = "ris_number")
    private String risNumber;

    @Column(name = "ris_date")
    private LocalDate risDate;

    @Column(name = "ics_number")
    private String icsNumber;

    @Column(name = "ics_date")
    private LocalDate icsDate;

    @Column(name = "par_number")
    private String parNumber;

    @Column(name = "par_date")
    private LocalDate parDate;

    @Column(name = "custodian_name")
    private String custodianName;

    private String remarks;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<RequestItem> items = new ArrayList<>();

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public void addItem(RequestItem line) {
        line.setRequest(this);
        items.add(line);
    }

    public Long getId() {
        return id;
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getRisNumber() {
        return risNumber;
    }

    public void setRisNumber(String risNumber) {
        this.risNumber = risNumber;
    }

    public LocalDate getRisDate() {
        return risDate;
    }

    public void setRisDate(LocalDate risDate) {
        this.risDate = risDate;
    }

    public String getIcsNumber() {
        return icsNumber;
    }

    public void setIcsNumber(String icsNumber) {
        this.icsNumber = icsNumber;
    }

    public LocalDate getIcsDate() {
        return icsDate;
    }

    public void setIcsDate(LocalDate icsDate) {
        this.icsDate = icsDate;
    }

    public String getParNumber() {
        return parNumber;
    }

    public void setParNumber(String parNumber) {
        this.parNumber = parNumber;
    }

    public LocalDate getParDate() {
        return parDate;
    }

    public void setParDate(LocalDate parDate) {
        this.parDate = parDate;
    }

    public String getCustodianName() {
        return custodianName;
    }

    public void setCustodianName(String custodianName) {
        this.custodianName = custodianName;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public List<RequestItem> getItems() {
        return items;
    }
}

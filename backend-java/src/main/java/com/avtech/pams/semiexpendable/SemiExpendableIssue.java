package com.avtech.pams.semiexpendable;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Semi-expendable property handed to someone (COA GAM): expensed when issued, but it stays under
 * that person's accountability (ICS) until returned.
 */
@Entity
@Table(name = "semi_expendable_issues")
public class SemiExpendableIssue extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "issued_to")
    private Long issuedTo;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "custodian_name")
    private String custodianName;

    @Column(name = "ics_number")
    private String icsNumber;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "quantity_returned", nullable = false)
    private int quantityReturned;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(nullable = false)
    private String status = "issued";

    private String remarks;

    protected SemiExpendableIssue() {
    }

    public SemiExpendableIssue(Long itemId, Long requestId, Long issuedTo, Long departmentId, String custodianName,
                               String icsNumber, int quantity, LocalDate issuedDate) {
        this.itemId = itemId;
        this.requestId = requestId;
        this.issuedTo = issuedTo;
        this.departmentId = departmentId;
        this.custodianName = custodianName;
        this.icsNumber = icsNumber;
        this.quantity = quantity;
        this.issuedDate = issuedDate;
    }

    public int outstanding() {
        return Math.max(0, quantity - quantityReturned);
    }

    public void recordReturn(int qty, String note) {
        quantityReturned += qty;
        status = quantityReturned >= quantity ? "returned" : "partially_returned";
        if (note != null && !note.isBlank()) {
            remarks = remarks == null || remarks.isBlank() ? note.trim() : remarks + " | " + note.trim();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getIssuedTo() {
        return issuedTo;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public String getCustodianName() {
        return custodianName;
    }

    public String getIcsNumber() {
        return icsNumber;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getQuantityReturned() {
        return quantityReturned;
    }

    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    public String getStatus() {
        return status;
    }

    public String getRemarks() {
        return remarks;
    }
}

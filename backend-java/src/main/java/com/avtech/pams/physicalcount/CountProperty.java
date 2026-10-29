package com.avtech.pams.physicalcount;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One property tag on an asset count: expected from the masterlist, or found on the floor. */
@Entity
@Table(name = "physical_count_properties")
public class CountProperty extends Timestamped {

    public static final String PENDING = "pending";
    public static final String FOUND = "found";
    public static final String MISSING = "missing";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "physical_inventory_count_id", nullable = false)
    private Long countId;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "tag_number", nullable = false)
    private String tagNumber;

    private String description;

    @Column(name = "holder_id")
    private Long holderId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "book_value")
    private BigDecimal bookValue;

    @Column(name = "condition_on_record")
    private String conditionOnRecord;

    @Column(name = "in_masterlist", nullable = false)
    private boolean inMasterlist = true;

    @Column(name = "in_scope", nullable = false)
    private boolean inScope = true;

    @Column(nullable = false)
    private String result = PENDING;

    @Column(name = "observed_condition")
    private String observedCondition;

    private String location;
    private String remarks;

    @Column(name = "checked_by")
    private Long checkedBy;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    protected CountProperty() {
    }

    public CountProperty(Long countId, String tagNumber) {
        this.countId = countId;
        this.tagNumber = tagNumber;
    }

    public Long getId() { return id; }
    public Long getCountId() { return countId; }
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public String getTagNumber() { return tagNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getHolderId() { return holderId; }
    public void setHolderId(Long holderId) { this.holderId = holderId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public BigDecimal getBookValue() { return bookValue; }
    public void setBookValue(BigDecimal bookValue) { this.bookValue = bookValue; }
    public String getConditionOnRecord() { return conditionOnRecord; }
    public void setConditionOnRecord(String conditionOnRecord) { this.conditionOnRecord = conditionOnRecord; }
    public boolean isInMasterlist() { return inMasterlist; }
    public void setInMasterlist(boolean inMasterlist) { this.inMasterlist = inMasterlist; }
    public boolean isInScope() { return inScope; }
    public void setInScope(boolean inScope) { this.inScope = inScope; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getObservedCondition() { return observedCondition; }
    public String getLocation() { return location; }
    public String getRemarks() { return remarks; }
    public Long getCheckedBy() { return checkedBy; }
    public LocalDateTime getCheckedAt() { return checkedAt; }

    public void check(String result, String condition, String location, String remarks, Long by, LocalDateTime at) {
        this.result = result;
        this.observedCondition = condition;
        this.location = location;
        this.remarks = remarks;
        this.checkedBy = PENDING.equals(result) ? null : by;
        this.checkedAt = PENDING.equals(result) ? null : at;
    }
}

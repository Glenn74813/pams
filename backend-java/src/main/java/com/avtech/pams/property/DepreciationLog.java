package com.avtech.pams.property;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One month's depreciation posted for one asset. period is YYYY-MM and unique per asset. */
@Entity
@Table(name = "property_depreciation_logs")
public class DepreciationLog extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "depreciation_amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "accumulated_depreciation", nullable = false)
    private BigDecimal accumulated;

    @Column(name = "book_value", nullable = false)
    private BigDecimal bookValue;

    @Column(name = "depreciation_date", nullable = false)
    private LocalDate depreciationDate;

    @Column(name = "depreciation_period", nullable = false)
    private String depreciationPeriod = "monthly";

    @Column(name = "calculation_method", nullable = false)
    private String calculationMethod = "straight-line";

    private String period;

    private String notes;

    protected DepreciationLog() {
    }

    public DepreciationLog(Long propertyId, String period, LocalDate date, BigDecimal amount, BigDecimal accumulated,
                           BigDecimal bookValue, String notes) {
        this.propertyId = propertyId;
        this.period = period;
        this.depreciationDate = date;
        this.amount = amount;
        this.accumulated = accumulated;
        this.bookValue = bookValue;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getAccumulated() { return accumulated; }
    public BigDecimal getBookValue() { return bookValue; }
    public LocalDate getDepreciationDate() { return depreciationDate; }
    public String getPeriod() { return period; }
    public String getNotes() { return notes; }
}

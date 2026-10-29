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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A rating of a supplier's performance on one engagement (1–5 per criterion). */
@Entity
@Table(name = "supplier_evaluations")
public class SupplierEvaluation extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "evaluation_date", nullable = false)
    private LocalDate evaluationDate;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "delivery_rating", nullable = false)
    private Integer deliveryRating;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "quality_rating", nullable = false)
    private Integer qualityRating;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "timeliness_rating")
    private Integer timelinessRating;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "compliance_rating")
    private Integer complianceRating;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "after_sales_rating")
    private Integer afterSalesRating;

    @Column(name = "overall_rating", nullable = false)
    private BigDecimal overallRating = BigDecimal.ZERO;

    private String reference;
    private String remarks;

    @Column(name = "evaluated_by")
    private Long evaluatedBy;

    protected SupplierEvaluation() {
    }

    public SupplierEvaluation(Supplier supplier, Long evaluatedBy) {
        this.supplier = supplier;
        this.evaluatedBy = evaluatedBy;
    }

    public Long getId() { return id; }
    public LocalDate getEvaluationDate() { return evaluationDate; }
    public void setEvaluationDate(LocalDate evaluationDate) { this.evaluationDate = evaluationDate; }
    public Integer getDeliveryRating() { return deliveryRating; }
    public void setDeliveryRating(Integer deliveryRating) { this.deliveryRating = deliveryRating; }
    public Integer getQualityRating() { return qualityRating; }
    public void setQualityRating(Integer qualityRating) { this.qualityRating = qualityRating; }
    public Integer getTimelinessRating() { return timelinessRating; }
    public void setTimelinessRating(Integer timelinessRating) { this.timelinessRating = timelinessRating; }
    public Integer getComplianceRating() { return complianceRating; }
    public void setComplianceRating(Integer complianceRating) { this.complianceRating = complianceRating; }
    public Integer getAfterSalesRating() { return afterSalesRating; }
    public void setAfterSalesRating(Integer afterSalesRating) { this.afterSalesRating = afterSalesRating; }
    public BigDecimal getOverallRating() { return overallRating; }
    public void setOverallRating(BigDecimal overallRating) { this.overallRating = overallRating; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Long getEvaluatedBy() { return evaluatedBy; }
}

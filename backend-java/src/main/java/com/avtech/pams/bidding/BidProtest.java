package com.avtech.pams.bidding;

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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A request for reconsideration or a protest against a BAC decision. */
@Entity
@Table(name = "bid_protests")
public class BidProtest extends Timestamped {

    public static final List<String> TYPES = List.of("Request for Reconsideration", "Protest");
    public static final List<String> STATUSES = List.of("filed", "under_review", "upheld", "denied", "withdrawn");
    public static final List<String> OPEN = List.of("filed", "under_review");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidding_id")
    private Bidding bidding;

    @Column(name = "protest_type", nullable = false)
    private String type;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "filed_by")
    private String filedBy;

    @Column(nullable = false)
    private String grounds;

    @Column(name = "filed_date", nullable = false)
    private LocalDate filedDate;

    @Column(name = "protest_fee")
    private BigDecimal protestFee;

    @Column(nullable = false)
    private String status = "filed";

    private String resolution;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "created_by")
    private Long createdBy;

    protected BidProtest() {
    }

    public BidProtest(Bidding bidding, Long createdBy) {
        this.bidding = bidding;
        this.createdBy = createdBy;
    }

    public boolean isOpen() {
        return OPEN.contains(status);
    }

    public Long getId() { return id; }
    public Bidding getBidding() { return bidding; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getFiledBy() { return filedBy; }
    public void setFiledBy(String filedBy) { this.filedBy = filedBy; }
    public String getGrounds() { return grounds; }
    public void setGrounds(String grounds) { this.grounds = grounds; }
    public LocalDate getFiledDate() { return filedDate; }
    public void setFiledDate(LocalDate filedDate) { this.filedDate = filedDate; }
    public BigDecimal getProtestFee() { return protestFee; }
    public void setProtestFee(BigDecimal protestFee) { this.protestFee = protestFee; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public LocalDate getResolvedDate() { return resolvedDate; }
    public void setResolvedDate(LocalDate resolvedDate) { this.resolvedDate = resolvedDate; }
}

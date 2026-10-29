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

/** A rated criterion for MEARB (most economically advantageous responsive bid) evaluation. */
@Entity
@Table(name = "bidding_criteria")
public class BiddingCriterion extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidding_id")
    private Bidding bidding;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(name = "is_environmental", nullable = false)
    private boolean environmental;

    @Column(nullable = false)
    private int sequence;

    protected BiddingCriterion() {
    }

    public BiddingCriterion(Bidding bidding, String name, BigDecimal weight, boolean environmental, int sequence) {
        this.bidding = bidding;
        this.name = name;
        this.weight = weight;
        this.environmental = environmental;
        this.sequence = sequence;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getWeight() { return weight; }
    public boolean isEnvironmental() { return environmental; }
    public int getSequence() { return sequence; }
}

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

@Entity
@Table(name = "bidding_bidder_scores")
public class BiddingBidderScore extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidding_bidder_id")
    private BiddingBidder bidder;

    @Column(name = "bidding_criterion_id", nullable = false)
    private Long criterionId;

    @Column(nullable = false)
    private BigDecimal score;

    protected BiddingBidderScore() {
    }

    public BiddingBidderScore(BiddingBidder bidder, Long criterionId, BigDecimal score) {
        this.bidder = bidder;
        this.criterionId = criterionId;
        this.score = score;
    }

    public Long getCriterionId() { return criterionId; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
}

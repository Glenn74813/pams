package com.avtech.pams.bidding;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.CascadeType;
import com.avtech.pams.common.JsonText;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** A bidder in a bidding: the bid read at opening, its security, and the four evaluation stages. */
@Entity
@Table(name = "bidding_bidders")
public class BiddingBidder extends Timestamped {

    public static final List<String> RESULTS = List.of("pending", "passed", "failed");
    /** Evaluation stages, in the order RA 12009 applies them. */
    public static final List<String> STAGES = List.of("eligibility", "technical", "financial", "post_qualification");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidding_id")
    private Bidding bidding;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "bid_amount")
    private BigDecimal bidAmount;

    @Column(name = "eligibility_result", nullable = false)
    private String eligibilityResult = "pending";

    @Column(name = "technical_result", nullable = false)
    private String technicalResult = "pending";

    @Column(name = "financial_result", nullable = false)
    private String financialResult = "pending";

    @Column(name = "post_qual_result", nullable = false)
    private String postQualResult = "pending";

    @Column(name = "is_winner", nullable = false)
    private boolean winner;

    private Integer rank;
    private String remarks;

    @Column(name = "technical_score")
    private BigDecimal technicalScore;

    @Column(name = "bid_security_amount")
    private BigDecimal bidSecurityAmount;

    @Column(name = "bid_security_form")
    private String bidSecurityForm;

    @Column(name = "bid_security_posted", nullable = false)
    private boolean bidSecurityPosted;

    @Convert(converter = JsonText.ObjectMap.class)
    @Column(name = "eligibility_checklist")
    private Map<String, Object> eligibilityChecklist;

    @OneToMany(mappedBy = "bidder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BiddingBidderScore> scores = new ArrayList<>();

    protected BiddingBidder() {
    }

    public BiddingBidder(Bidding bidding, Long supplierId) {
        this.bidding = bidding;
        this.supplierId = supplierId;
    }

    public String result(String stage) {
        return switch (stage) {
            case "eligibility" -> eligibilityResult;
            case "technical" -> technicalResult;
            case "financial" -> financialResult;
            case "post_qualification" -> postQualResult;
            default -> throw new IllegalArgumentException(stage);
        };
    }

    public void setResult(String stage, String result) {
        switch (stage) {
            case "eligibility" -> eligibilityResult = result;
            case "technical" -> technicalResult = result;
            case "financial" -> financialResult = result;
            case "post_qualification" -> postQualResult = result;
            default -> throw new IllegalArgumentException(stage);
        }
    }

    public boolean failedAny() {
        return STAGES.stream().anyMatch(s -> "failed".equals(result(s)));
    }

    /** Eligible, technically and financially responsive: ranked for post-qualification. */
    public boolean responsive() {
        return "passed".equals(eligibilityResult) && "passed".equals(technicalResult) && "passed".equals(financialResult);
    }

    public Long getId() { return id; }
    public Bidding getBidding() { return bidding; }
    public Long getSupplierId() { return supplierId; }
    public BigDecimal getBidAmount() { return bidAmount; }
    public void setBidAmount(BigDecimal bidAmount) { this.bidAmount = bidAmount; }
    public String getPostQualResult() { return postQualResult; }
    public boolean isWinner() { return winner; }
    public void setWinner(boolean winner) { this.winner = winner; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public BigDecimal getTechnicalScore() { return technicalScore; }
    public void setTechnicalScore(BigDecimal technicalScore) { this.technicalScore = technicalScore; }
    public BigDecimal getBidSecurityAmount() { return bidSecurityAmount; }
    public void setBidSecurityAmount(BigDecimal bidSecurityAmount) { this.bidSecurityAmount = bidSecurityAmount; }
    public String getBidSecurityForm() { return bidSecurityForm; }
    public void setBidSecurityForm(String bidSecurityForm) { this.bidSecurityForm = bidSecurityForm; }
    public boolean isBidSecurityPosted() { return bidSecurityPosted; }
    public void setBidSecurityPosted(boolean bidSecurityPosted) { this.bidSecurityPosted = bidSecurityPosted; }
    public Map<String, Object> getEligibilityChecklist() { return eligibilityChecklist; }
    public void setEligibilityChecklist(Map<String, Object> eligibilityChecklist) { this.eligibilityChecklist = eligibilityChecklist; }
    public List<BiddingBidderScore> getScores() { return scores; }
}

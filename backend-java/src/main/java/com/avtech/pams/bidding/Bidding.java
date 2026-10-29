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

/**
 * A public bidding (competitive bidding, RA 12009): draft → pre-procurement → invitation →
 * bid opening → evaluation → awarded, or failed / cancelled.
 */
@Entity
@Table(name = "biddings")
public class Bidding extends Timestamped {

    public static final String DRAFT = "draft";
    public static final String PRE_PROCUREMENT = "pre_procurement";
    public static final String INVITATION = "invitation";
    public static final String BID_OPENING = "bid_opening";
    public static final String EVALUATION = "evaluation";
    public static final String AWARDED = "awarded";
    public static final String FAILED = "failed";
    public static final String CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bidding_number", nullable = false)
    private String biddingNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    @Column(nullable = false)
    private BigDecimal abc = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(name = "pre_proc_date")
    private LocalDateTime preProcDate;

    @Column(name = "pre_proc_notes")
    private String preProcNotes;

    @Column(name = "itb_reference")
    private String itbReference;

    @Column(name = "itb_posted_at")
    private LocalDate itbPostedAt;

    @Column(name = "itb_publication")
    private String itbPublication;

    @Column(name = "submission_deadline")
    private LocalDateTime submissionDeadline;

    @Column(name = "bid_opening_date")
    private LocalDateTime bidOpeningDate;

    @Column(name = "awarded_supplier_id")
    private Long awardedSupplierId;

    @Column(name = "awarded_amount")
    private BigDecimal awardedAmount;

    @Column(name = "awarded_at")
    private LocalDateTime awardedAt;

    @Column(name = "noa_number")
    private String noaNumber;

    @Column(name = "noa_date")
    private LocalDate noaDate;

    @Column(name = "ntp_number")
    private String ntpNumber;

    @Column(name = "ntp_date")
    private LocalDate ntpDate;

    @Column(name = "contract_recommendation")
    private String contractRecommendation;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "bac_resolution_id")
    private Long bacResolutionId;

    @Column(name = "prepared_by")
    private Long preparedBy;

    private String remarks;

    @Column(name = "evaluation_method", nullable = false)
    private String evaluationMethod = "price";

    @Column(name = "price_weight", nullable = false)
    private BigDecimal priceWeight = BigDecimal.valueOf(100);

    @Column(name = "technical_weight", nullable = false)
    private BigDecimal technicalWeight = BigDecimal.ZERO;

    @Column(name = "delivery_term")
    private String deliveryTerm;

    @Column(name = "delivery_lead_days")
    private Integer deliveryLeadDays;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "bidding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BiddingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "bidding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence, id")
    private List<BiddingCriterion> criteria = new ArrayList<>();

    @OneToMany(mappedBy = "bidding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BiddingBidder> bidders = new ArrayList<>();

    @OneToMany(mappedBy = "bidding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BidProtest> protests = new ArrayList<>();

    @OneToMany(mappedBy = "bidding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BidObserver> observers = new ArrayList<>();

    protected Bidding() {
    }

    public Bidding(String number, Long preparedBy) {
        this.biddingNumber = number;
        this.preparedBy = preparedBy;
    }

    public boolean isMearb() {
        return "mearb".equals(evaluationMethod);
    }

    /** Before the Invitation to Bid: the terms can still change. */
    public boolean isPlanning() {
        return DRAFT.equals(status) || PRE_PROCUREMENT.equals(status);
    }

    public boolean isOver() {
        return AWARDED.equals(status) || FAILED.equals(status) || CANCELLED.equals(status);
    }

    public void recalc() {
        abc = items.stream().map(BiddingItem::getAbcTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getBiddingNumber() { return biddingNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public void setPurchaseRequestId(Long purchaseRequestId) { this.purchaseRequestId = purchaseRequestId; }
    public BigDecimal getAbc() { return abc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPreProcDate() { return preProcDate; }
    public void setPreProcDate(LocalDateTime preProcDate) { this.preProcDate = preProcDate; }
    public String getPreProcNotes() { return preProcNotes; }
    public void setPreProcNotes(String preProcNotes) { this.preProcNotes = preProcNotes; }
    public String getItbReference() { return itbReference; }
    public void setItbReference(String itbReference) { this.itbReference = itbReference; }
    public LocalDate getItbPostedAt() { return itbPostedAt; }
    public void setItbPostedAt(LocalDate itbPostedAt) { this.itbPostedAt = itbPostedAt; }
    public String getItbPublication() { return itbPublication; }
    public void setItbPublication(String itbPublication) { this.itbPublication = itbPublication; }
    public LocalDateTime getSubmissionDeadline() { return submissionDeadline; }
    public void setSubmissionDeadline(LocalDateTime submissionDeadline) { this.submissionDeadline = submissionDeadline; }
    public LocalDateTime getBidOpeningDate() { return bidOpeningDate; }
    public void setBidOpeningDate(LocalDateTime bidOpeningDate) { this.bidOpeningDate = bidOpeningDate; }
    public Long getAwardedSupplierId() { return awardedSupplierId; }
    public void setAwardedSupplierId(Long awardedSupplierId) { this.awardedSupplierId = awardedSupplierId; }
    public BigDecimal getAwardedAmount() { return awardedAmount; }
    public void setAwardedAmount(BigDecimal awardedAmount) { this.awardedAmount = awardedAmount; }
    public LocalDateTime getAwardedAt() { return awardedAt; }
    public void setAwardedAt(LocalDateTime awardedAt) { this.awardedAt = awardedAt; }
    public String getNoaNumber() { return noaNumber; }
    public void setNoaNumber(String noaNumber) { this.noaNumber = noaNumber; }
    public LocalDate getNoaDate() { return noaDate; }
    public void setNoaDate(LocalDate noaDate) { this.noaDate = noaDate; }
    public String getNtpNumber() { return ntpNumber; }
    public void setNtpNumber(String ntpNumber) { this.ntpNumber = ntpNumber; }
    public LocalDate getNtpDate() { return ntpDate; }
    public void setNtpDate(LocalDate ntpDate) { this.ntpDate = ntpDate; }
    public String getContractRecommendation() { return contractRecommendation; }
    public void setContractRecommendation(String contractRecommendation) { this.contractRecommendation = contractRecommendation; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public Long getBacResolutionId() { return bacResolutionId; }
    public void setBacResolutionId(Long bacResolutionId) { this.bacResolutionId = bacResolutionId; }
    public Long getPreparedBy() { return preparedBy; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getEvaluationMethod() { return evaluationMethod; }
    public void setEvaluationMethod(String evaluationMethod) { this.evaluationMethod = evaluationMethod; }
    public BigDecimal getPriceWeight() { return priceWeight; }
    public void setPriceWeight(BigDecimal priceWeight) { this.priceWeight = priceWeight; }
    public BigDecimal getTechnicalWeight() { return technicalWeight; }
    public void setTechnicalWeight(BigDecimal technicalWeight) { this.technicalWeight = technicalWeight; }
    public String getDeliveryTerm() { return deliveryTerm; }
    public void setDeliveryTerm(String deliveryTerm) { this.deliveryTerm = deliveryTerm; }
    public Integer getDeliveryLeadDays() { return deliveryLeadDays; }
    public void setDeliveryLeadDays(Integer deliveryLeadDays) { this.deliveryLeadDays = deliveryLeadDays; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<BiddingItem> getItems() { return items; }
    public List<BiddingCriterion> getCriteria() { return criteria; }
    public List<BiddingBidder> getBidders() { return bidders; }
    public List<BidProtest> getProtests() { return protests; }
    public List<BidObserver> getObservers() { return observers; }
}

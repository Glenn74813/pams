package com.avtech.pams.contract;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A procurement contract on an award: draft → active → completed, or terminated. */
@Entity
@Table(name = "contracts")
public class Contract extends Timestamped {

    public static final List<String> TYPES = List.of("Goods", "Services", "Infrastructure", "Consulting", "Framework Agreement");
    public static final String DRAFT = "draft";
    public static final String ACTIVE = "active";
    public static final String COMPLETED = "completed";
    public static final String TERMINATED = "terminated";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_number", nullable = false)
    private String contractNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "contract_type", nullable = false)
    private String contractType = "Goods";

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    @Column(name = "bidding_id")
    private Long biddingId;

    @Column(name = "rfq_id")
    private Long rfqId;

    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "signed_date")
    private LocalDate signedDate;

    private String description;
    private String terms;

    @Column(name = "prepared_by")
    private Long preparedBy;

    private String remarks;

    @Column(name = "ld_basis_amount")
    private BigDecimal ldBasisAmount;

    @Column(name = "ld_days_delayed", nullable = false)
    private int ldDaysDelayed;

    @Column(name = "ld_rate", nullable = false)
    private BigDecimal ldRate = new BigDecimal("0.001");

    @Column(name = "ld_amount", nullable = false)
    private BigDecimal ldAmount = BigDecimal.ZERO;

    @Column(name = "ld_remarks")
    private String ldRemarks;

    @Column(name = "ld_as_of_date")
    private LocalDate ldAsOfDate;

    @Column(name = "delivery_lead_days")
    private Integer deliveryLeadDays;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("amendmentNumber")
    private List<ContractAmendment> amendments = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ContractSecurity> securities = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DeliveryExtension> extensions = new ArrayList<>();

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<FrameworkCalloff> calloffs = new ArrayList<>();

    protected Contract() {
    }

    public Contract(String number, Long preparedBy) {
        this.contractNumber = number;
        this.preparedBy = preparedBy;
    }

    public boolean isOpen() {
        return DRAFT.equals(status) || ACTIVE.equals(status);
    }

    public int approvedExtensionDays() {
        return extensions.stream().filter(e -> "approved".equals(e.getStatus()) && e.getApprovedDays() != null).mapToInt(DeliveryExtension::getApprovedDays).sum();
    }

    /** Net of the approved amendments: the price the contract was signed at. */
    public BigDecimal originalAmount() {
        return amount.subtract(amendments.stream().filter(a -> "approved".equals(a.getStatus())).map(ContractAmendment::getAmountChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public Long getId() { return id; }
    public String getContractNumber() { return contractNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }
    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public void setPurchaseRequestId(Long purchaseRequestId) { this.purchaseRequestId = purchaseRequestId; }
    public Long getBiddingId() { return biddingId; }
    public void setBiddingId(Long biddingId) { this.biddingId = biddingId; }
    public Long getRfqId() { return rfqId; }
    public void setRfqId(Long rfqId) { this.rfqId = rfqId; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public LocalDate getSignedDate() { return signedDate; }
    public void setSignedDate(LocalDate signedDate) { this.signedDate = signedDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
    public Long getPreparedBy() { return preparedBy; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public BigDecimal getLdBasisAmount() { return ldBasisAmount; }
    public void setLdBasisAmount(BigDecimal ldBasisAmount) { this.ldBasisAmount = ldBasisAmount; }
    public int getLdDaysDelayed() { return ldDaysDelayed; }
    public void setLdDaysDelayed(int ldDaysDelayed) { this.ldDaysDelayed = ldDaysDelayed; }
    public BigDecimal getLdRate() { return ldRate; }
    public BigDecimal getLdAmount() { return ldAmount; }
    public void setLdAmount(BigDecimal ldAmount) { this.ldAmount = ldAmount; }
    public String getLdRemarks() { return ldRemarks; }
    public void setLdRemarks(String ldRemarks) { this.ldRemarks = ldRemarks; }
    public LocalDate getLdAsOfDate() { return ldAsOfDate; }
    public void setLdAsOfDate(LocalDate ldAsOfDate) { this.ldAsOfDate = ldAsOfDate; }
    public Integer getDeliveryLeadDays() { return deliveryLeadDays; }
    public void setDeliveryLeadDays(Integer deliveryLeadDays) { this.deliveryLeadDays = deliveryLeadDays; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<ContractAmendment> getAmendments() { return amendments; }
    public List<ContractSecurity> getSecurities() { return securities; }
    public List<DeliveryExtension> getExtensions() { return extensions; }
    public List<FrameworkCalloff> getCalloffs() { return calloffs; }
}

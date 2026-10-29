package com.avtech.pams.rfq;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A request for quotation under an alternative mode of procurement (RA 12009):
 * draft → issued → closed → awarded, or cancelled.
 */
@Entity
@Table(name = "rfqs")
public class Rfq extends Timestamped {

    public static final List<String> METHODS = List.of("Shopping", "Small Value Procurement", "Negotiated Procurement", "Direct Contracting",
            "Direct Acquisition", "Repeat Order", "Limited Source Bidding", "Competitive Dialogue", "Unsolicited Offer", "Direct Sales");
    /** Single or exclusive-source methods: one supplier, and a documented justification. */
    public static final List<String> SINGLE_SOURCE = List.of("Direct Contracting", "Direct Acquisition", "Repeat Order", "Direct Sales", "Unsolicited Offer");
    public static final String DRAFT = "draft";
    public static final String ISSUED = "issued";
    public static final String CLOSED = "closed";
    public static final String AWARDED = "awarded";
    public static final String CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rfq_number", nullable = false)
    private String rfqNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    @Column(name = "procurement_method", nullable = false)
    private String procurementMethod;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(nullable = false)
    private BigDecimal abc = BigDecimal.ZERO;

    private LocalDateTime deadline;
    private String description;

    @Column(name = "prepared_by")
    private Long preparedBy;

    @Column(name = "awarded_supplier_id")
    private Long awardedSupplierId;

    @Column(name = "awarded_at")
    private LocalDateTime awardedAt;

    @Column(name = "bac_resolution_id")
    private Long bacResolutionId;

    private String remarks;
    private String justification;

    @Column(name = "noa_number")
    private String noaNumber;

    @Column(name = "noa_date")
    private LocalDate noaDate;

    @Column(name = "ntp_number")
    private String ntpNumber;

    @Column(name = "ntp_date")
    private LocalDate ntpDate;

    @Column(name = "delivery_term")
    private String deliveryTerm;

    @Column(name = "delivery_lead_days")
    private Integer deliveryLeadDays;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<RfqItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("totalAmount, id")
    private List<RfqQuotation> quotations = new ArrayList<>();

    protected Rfq() {
    }

    public Rfq(String number, Long preparedBy) {
        this.rfqNumber = number;
        this.preparedBy = preparedBy;
    }

    public boolean isSingleSource() {
        return SINGLE_SOURCE.contains(procurementMethod);
    }

    /** Taking quotations: issued, or closed but not yet awarded. */
    public boolean isOpenForQuotes() {
        return ISSUED.equals(status) || CLOSED.equals(status);
    }

    public void recalc() {
        abc = items.stream().map(RfqItem::getAbcTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getRfqNumber() { return rfqNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public void setPurchaseRequestId(Long purchaseRequestId) { this.purchaseRequestId = purchaseRequestId; }
    public String getProcurementMethod() { return procurementMethod; }
    public void setProcurementMethod(String procurementMethod) { this.procurementMethod = procurementMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAbc() { return abc; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getPreparedBy() { return preparedBy; }
    public Long getAwardedSupplierId() { return awardedSupplierId; }
    public void setAwardedSupplierId(Long awardedSupplierId) { this.awardedSupplierId = awardedSupplierId; }
    public LocalDateTime getAwardedAt() { return awardedAt; }
    public void setAwardedAt(LocalDateTime awardedAt) { this.awardedAt = awardedAt; }
    public Long getBacResolutionId() { return bacResolutionId; }
    public void setBacResolutionId(Long bacResolutionId) { this.bacResolutionId = bacResolutionId; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
    public String getNoaNumber() { return noaNumber; }
    public void setNoaNumber(String noaNumber) { this.noaNumber = noaNumber; }
    public LocalDate getNoaDate() { return noaDate; }
    public void setNoaDate(LocalDate noaDate) { this.noaDate = noaDate; }
    public String getNtpNumber() { return ntpNumber; }
    public void setNtpNumber(String ntpNumber) { this.ntpNumber = ntpNumber; }
    public LocalDate getNtpDate() { return ntpDate; }
    public void setNtpDate(LocalDate ntpDate) { this.ntpDate = ntpDate; }
    public String getDeliveryTerm() { return deliveryTerm; }
    public void setDeliveryTerm(String deliveryTerm) { this.deliveryTerm = deliveryTerm; }
    public Integer getDeliveryLeadDays() { return deliveryLeadDays; }
    public void setDeliveryLeadDays(Integer deliveryLeadDays) { this.deliveryLeadDays = deliveryLeadDays; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<RfqItem> getItems() { return items; }
    public List<RfqQuotation> getQuotations() { return quotations; }
}

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
import com.avtech.pams.procurement.ProcurementRules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A request for quotation under an alternative mode of procurement (RA 12009):
 * draft → issued → closed → awarded, or cancelled.
 */
@Entity
@Table(name = "rfqs")
public class Rfq extends Timestamped {

    public static final List<String> METHODS = List.of(ProcurementRules.SVP, ProcurementRules.NEGOTIATED, "Direct Contracting",
            "Direct Acquisition", "Repeat Order", "Limited Source Bidding", "Competitive Dialogue", "Direct Sales", ProcurementRules.STI);
    /** Modes that can fail as a bidding does and count towards two failed biddings (IRR 64.1). */
    public static final List<String> CAN_FAIL = List.of("Limited Source Bidding", "Competitive Dialogue");
    /** The instances of Negotiated Procurement (IRR Section 35; GPPB Resolution 01-2026; GPPB Circular 06-2026), by the code stored on the RFQ. */
    public static final Map<String, String> NEGOTIATED_GROUNDS = new LinkedHashMap<>();
    public static final String TWO_FAILED = "two_failed_biddings";

    static {
        NEGOTIATED_GROUNDS.put(TWO_FAILED, "Two failed biddings (35.1)");
        NEGOTIATED_GROUNDS.put("emergency", "Emergency cases (35.2)");
        NEGOTIATED_GROUNDS.put("take_over", "Take-over of contracts (35.3)");
        NEGOTIATED_GROUNDS.put("adjacent", "Adjacent or contiguous (35.4)");
        NEGOTIATED_GROUNDS.put("agency_to_agency", "Agency-to-agency (35.5)");
        NEGOTIATED_GROUNDS.put("scientific_artistic", "Scientific, scholarly or artistic work, exclusive technology and media (35.6)");
        NEGOTIATED_GROUNDS.put("highly_technical", "Highly technical consultants (35.7)");
        NEGOTIATED_GROUNDS.put("defense", "Defense cooperation agreements and inventory-based items (35.8)");
        NEGOTIATED_GROUNDS.put("lease", "Lease of real property and venue (35.9)");
        NEGOTIATED_GROUNDS.put("ngo", "NGO participation (35.10)");
        NEGOTIATED_GROUNDS.put("community", "Community participation (35.11)");
        NEGOTIATED_GROUNDS.put("un_agencies", "UN agencies and international organizations (35.12)");
        NEGOTIATED_GROUNDS.put("direct_retail", "Direct retail purchase of fuel, charging devices and online subscriptions (35.13)");
        NEGOTIATED_GROUNDS.put("international_events", "International events hosted by the Philippine Government (GPPB Resolution 01-2026)");
        NEGOTIATED_GROUNDS.put("sagip_saka", "Sagip Saka (GPPB Circular 06-2026)");
    }

    /** Single or exclusive-source methods: one supplier, and a documented justification. */
    public static final List<String> SINGLE_SOURCE = List.of("Direct Contracting", "Direct Acquisition", "Repeat Order", "Direct Sales", ProcurementRules.STI);
    public static final String DRAFT = "draft";
    public static final String ISSUED = "issued";
    public static final String CLOSED = "closed";
    public static final String AWARDED = "awarded";
    public static final String CANCELLED = "cancelled";
    public static final String FAILED = "failed";

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

    @jakarta.persistence.Column(name = "negotiated_ground")
    private String negotiatedGround;

    @Column(name = "eligibility_revised", nullable = false)
    private boolean eligibilityRevised;

    @Column(name = "documents_stage")
    private String documentsStage;

    @Column(name = "failure_reason")
    private String failureReason;

    /** One invited supplier per line (a Negotiated Procurement after two failed biddings invites at least three). */
    @jakarta.persistence.Column(name = "invited_suppliers")
    private String invitedSuppliers;

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
    public boolean isEligibilityRevised() { return eligibilityRevised; }
    public void setEligibilityRevised(boolean eligibilityRevised) { this.eligibilityRevised = eligibilityRevised; }
    public String getDocumentsStage() { return documentsStage; }
    public void setDocumentsStage(String documentsStage) { this.documentsStage = documentsStage; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getNegotiatedGround() { return negotiatedGround; }
    public void setNegotiatedGround(String negotiatedGround) { this.negotiatedGround = negotiatedGround; }
    public String getInvitedSuppliers() { return invitedSuppliers; }
    public void setInvitedSuppliers(String invitedSuppliers) { this.invitedSuppliers = invitedSuppliers; }
    public boolean isNegotiated() { return ProcurementRules.NEGOTIATED.equals(procurementMethod); }
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

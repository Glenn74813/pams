package com.avtech.pams.purchasing;

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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspection and Acceptance Report (COA Appendix 62). The inspection committee records what was
 * delivered, accepted and rejected; the supply or property officer then accepts it into stock, or
 * rejects the delivery.
 */
@Entity
@Table(name = "inspection_acceptance_reports")
public class InspectionReport extends Timestamped {

    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final String PARTIALLY_ACCEPTED = "partially_accepted";
    public static final String REJECTED = "rejected";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iar_number", nullable = false)
    private String iarNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "inspected_by", nullable = false)
    private Long inspectedBy;

    /** Inspection outcome: passed (every line complete), partial, or failed (nothing acceptable). */
    @Column(nullable = false)
    private String status = "passed";

    @Column(name = "total_items_ordered", nullable = false)
    private int totalOrdered;

    @Column(name = "total_items_received", nullable = false)
    private int totalReceived;

    @Column(name = "total_items_accepted", nullable = false)
    private int totalAccepted;

    @Column(name = "total_items_rejected", nullable = false)
    private int totalRejected;

    private String observations;
    private String remarks;

    @Column(name = "acceptance_status", nullable = false)
    private String acceptanceStatus = PENDING;

    @Column(name = "accepted_by")
    private Long acceptedBy;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<IarItem> items = new ArrayList<>();

    protected InspectionReport() {
    }

    public InspectionReport(String number, Long purchaseOrderId, Long inspectedBy) {
        this.iarNumber = number;
        this.purchaseOrderId = purchaseOrderId;
        this.inspectedBy = inspectedBy;
    }

    public boolean isPending() {
        return PENDING.equals(acceptanceStatus);
    }

    /** Totals and the inspection outcome, from the lines. */
    public void recalc() {
        totalOrdered = items.stream().mapToInt(IarItem::getQuantityOrdered).sum();
        totalReceived = items.stream().mapToInt(IarItem::getQuantityReceived).sum();
        totalAccepted = items.stream().mapToInt(IarItem::getQuantityAccepted).sum();
        totalRejected = items.stream().mapToInt(IarItem::getQuantityRejected).sum();
        boolean full = items.stream().allMatch(i -> "accepted".equals(i.getStatus()));
        status = totalAccepted == 0 ? "failed" : full ? "passed" : "partial";
    }

    public Long getId() { return id; }
    public String getIarNumber() { return iarNumber; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public LocalDate getInspectionDate() { return inspectionDate; }
    public void setInspectionDate(LocalDate inspectionDate) { this.inspectionDate = inspectionDate; }
    public Long getInspectedBy() { return inspectedBy; }
    public String getStatus() { return status; }
    public int getTotalOrdered() { return totalOrdered; }
    public int getTotalReceived() { return totalReceived; }
    public int getTotalAccepted() { return totalAccepted; }
    public int getTotalRejected() { return totalRejected; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getAcceptanceStatus() { return acceptanceStatus; }
    public void setAcceptanceStatus(String acceptanceStatus) { this.acceptanceStatus = acceptanceStatus; }
    public Long getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(Long acceptedBy) { this.acceptedBy = acceptedBy; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public List<IarItem> getItems() { return items; }
}

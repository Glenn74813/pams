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

/** A purchase order issued on an award: draft → sent → confirmed → partially received → received, or cancelled. */
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends Timestamped {

    public static final String DRAFT = "draft";
    public static final String SENT = "sent";
    public static final String CONFIRMED = "confirmed";
    public static final String PARTIALLY_RECEIVED = "partially_received";
    public static final String RECEIVED = "received";
    public static final String CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", nullable = false)
    private String poNumber;

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    private String remarks;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "rfq_id")
    private Long rfqId;

    @Column(name = "bidding_id")
    private Long biddingId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String number, Long createdBy, LocalDate orderDate) {
        this.poNumber = number;
        this.createdBy = createdBy;
        this.orderDate = orderDate;
    }

    /** Open for deliveries. */
    public boolean isReceivable() {
        return CONFIRMED.equals(status) || PARTIALLY_RECEIVED.equals(status);
    }

    public void recalc() {
        totalAmount = items.stream().map(PurchaseOrderItem::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public String getPoNumber() { return poNumber; }
    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public void setPurchaseRequestId(Long purchaseRequestId) { this.purchaseRequestId = purchaseRequestId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public LocalDate getOrderDate() { return orderDate; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public Long getCreatedBy() { return createdBy; }
    public Long getRfqId() { return rfqId; }
    public void setRfqId(Long rfqId) { this.rfqId = rfqId; }
    public Long getBiddingId() { return biddingId; }
    public void setBiddingId(Long biddingId) { this.biddingId = biddingId; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<PurchaseOrderItem> getItems() { return items; }
}

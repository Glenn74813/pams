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

/** One delivered PO line on an IAR, and the inventory item its accepted quantity went into. */
@Entity
@Table(name = "iar_items")
public class IarItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_acceptance_report_id")
    private InspectionReport report;

    @Column(name = "purchase_order_item_id")
    private Long purchaseOrderItemId;

    @Column(nullable = false)
    private String description;

    private String specifications;
    private String unit;

    @Column(name = "quantity_ordered", nullable = false)
    private int quantityOrdered;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "quantity_accepted", nullable = false)
    private int quantityAccepted;

    @Column(name = "quantity_rejected", nullable = false)
    private int quantityRejected;

    @Column(nullable = false)
    private String status = "accepted";

    private String remarks;

    @Column(name = "item_id")
    private Long itemId;

    protected IarItem() {
    }

    /** outstanding is what was still to be delivered on the PO line; the line is complete when all of it is accepted. */
    public IarItem(InspectionReport report, PurchaseOrderItem line, int outstanding, int received, int accepted, int rejected, String remarks) {
        this.report = report;
        this.purchaseOrderItemId = line.getId();
        this.description = line.getDescription();
        this.specifications = line.getSpecifications();
        this.unit = line.getUnit();
        this.quantityOrdered = line.getQuantity();
        this.quantityReceived = received;
        this.quantityAccepted = accepted;
        this.quantityRejected = rejected;
        this.remarks = remarks;
        this.status = accepted <= 0 ? "rejected" : accepted < outstanding || rejected > 0 ? "partial" : "accepted";
    }

    public Long getId() { return id; }
    public Long getPurchaseOrderItemId() { return purchaseOrderItemId; }
    public String getDescription() { return description; }
    public String getSpecifications() { return specifications; }
    public String getUnit() { return unit; }
    public int getQuantityOrdered() { return quantityOrdered; }
    public int getQuantityReceived() { return quantityReceived; }
    public int getQuantityAccepted() { return quantityAccepted; }
    public int getQuantityRejected() { return quantityRejected; }
    public String getStatus() { return status; }
    public String getRemarks() { return remarks; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
}

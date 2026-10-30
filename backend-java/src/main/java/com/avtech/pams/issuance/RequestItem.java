package com.avtech.pams.issuance;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line of a request: an item, how many were asked for and how many were issued. */
@Entity
@Table(name = "request_items")
public class RequestItem extends Timestamped {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String FULFILLED = "fulfilled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private InventoryRequest request;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "approved_quantity", nullable = false)
    private int approvedQuantity;

    @Column(nullable = false)
    private String status = PENDING;

    /** Unit cost when issued; null until then. */
    @Column(name = "unit_cost")
    private java.math.BigDecimal unitCost;

    public java.math.BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(java.math.BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    protected RequestItem() {
    }

    public RequestItem(Long itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }

    /** Records how many were issued: all of them is fulfilled, some is approved (partial). */
    public void issue(int qty) {
        approvedQuantity = qty;
        status = qty >= quantity ? FULFILLED : APPROVED;
    }

    public void reject() {
        approvedQuantity = 0;
        status = REJECTED;
    }

    public Long getId() {
        return id;
    }

    void setRequest(InventoryRequest request) {
        this.request = request;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getApprovedQuantity() {
        return approvedQuantity;
    }

    public String getStatus() {
        return status;
    }
}

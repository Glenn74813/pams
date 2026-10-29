package com.avtech.pams.inventory;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One received lot of an item: a brand/cost/expiry batch at a location. */
@Entity
@Table(name = "stocks")
public class Stock extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "brand_name")
    private String brandName;

    private String specifications;

    private String warranty;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "supplier_id")
    private Long supplierId;

    private String location;

    @Column(name = "iar_id")
    private Long iarId;

    public LocalDate warrantyExpiry() {
        return warrantyMonths == null || warrantyMonths <= 0 || receivedDate == null
                ? null : receivedDate.plusMonths(warrantyMonths);
    }

    /** A copy of this lot (same brand, cost, lot no., expiry, supplier) at another location. */
    public Stock splitTo(String newLocation, int qty) {
        Stock s = new Stock();
        s.itemId = itemId;
        s.brandName = brandName;
        s.specifications = specifications;
        s.warranty = warranty;
        s.warrantyMonths = warrantyMonths;
        s.quantity = qty;
        s.unitCost = unitCost;
        s.lotNumber = lotNumber;
        s.expirationDate = expirationDate;
        s.receivedDate = receivedDate;
        s.supplierId = supplierId;
        s.location = newLocation;
        s.iarId = iarId;
        return s;
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getIarId() {
        return iarId;
    }

    public void setIarId(Long iarId) {
        this.iarId = iarId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getSpecifications() {
        return specifications;
    }

    public void setSpecifications(String specifications) {
        this.specifications = specifications;
    }

    public String getWarranty() {
        return warranty;
    }

    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    public Integer getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(Integer warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}

package com.avtech.pams.inventory;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** A move of part of a lot from one location to another. On-hand totals don't change. */
@Entity
@Table(name = "stock_transfers")
public class StockTransfer extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_number", nullable = false)
    private String transferNumber;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "from_location")
    private String fromLocation;

    @Column(name = "to_location", nullable = false)
    private String toLocation;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "transferred_by")
    private Long transferredBy;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    private String remarks;

    protected StockTransfer() {
    }

    public StockTransfer(String transferNumber, Long itemId, Long stockId, String fromLocation, String toLocation,
                         int quantity, Long transferredBy, LocalDate transferDate, String remarks) {
        this.transferNumber = transferNumber;
        this.itemId = itemId;
        this.stockId = stockId;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.quantity = quantity;
        this.transferredBy = transferredBy;
        this.transferDate = transferDate;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public String getTransferNumber() {
        return transferNumber;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public int getQuantity() {
        return quantity;
    }

    public Long getTransferredBy() {
        return transferredBy;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public String getRemarks() {
        return remarks;
    }
}

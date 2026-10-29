package com.avtech.pams.rfq;

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

import java.math.BigDecimal;

@Entity
@Table(name = "rfq_quotation_items")
public class RfqQuotationItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_quotation_id")
    private RfqQuotation quotation;

    @Column(name = "rfq_item_id", nullable = false)
    private Long rfqItemId;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    private String remarks;

    protected RfqQuotationItem() {
    }

    public RfqQuotationItem(RfqQuotation quotation, Long rfqItemId, BigDecimal unitPrice, BigDecimal totalPrice, String remarks) {
        this.quotation = quotation;
        this.rfqItemId = rfqItemId;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.remarks = remarks;
    }

    public Long getId() { return id; }
    public Long getRfqItemId() { return rfqItemId; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public String getRemarks() { return remarks; }
}

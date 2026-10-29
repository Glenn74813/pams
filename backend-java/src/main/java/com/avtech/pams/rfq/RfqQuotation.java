package com.avtech.pams.rfq;

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
import java.util.ArrayList;
import java.util.List;

/** A supplier's quotation against an RFQ; prices every RFQ line. */
@Entity
@Table(name = "rfq_quotations")
public class RfqQuotation extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id")
    private Rfq rfq;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "quotation_number")
    private String quotationNumber;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "submitted_at")
    private LocalDate submittedAt;

    @Column(name = "is_winner", nullable = false)
    private boolean winner;

    @Column(name = "is_responsive", nullable = false)
    private boolean responsive = true;

    private String remarks;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<RfqQuotationItem> items = new ArrayList<>();

    protected RfqQuotation() {
    }

    public RfqQuotation(Rfq rfq, Long supplierId, String quotationNumber, LocalDate submittedAt, String remarks) {
        this.rfq = rfq;
        this.supplierId = supplierId;
        this.quotationNumber = quotationNumber;
        this.submittedAt = submittedAt;
        this.remarks = remarks;
    }

    public void recalc() {
        totalAmount = items.stream().map(RfqQuotationItem::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public Rfq getRfq() { return rfq; }
    public Long getSupplierId() { return supplierId; }
    public String getQuotationNumber() { return quotationNumber; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDate getSubmittedAt() { return submittedAt; }
    public boolean isWinner() { return winner; }
    public void setWinner(boolean winner) { this.winner = winner; }
    public boolean isResponsive() { return responsive; }
    public void setResponsive(boolean responsive) { this.responsive = responsive; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public List<RfqQuotationItem> getItems() { return items; }
}

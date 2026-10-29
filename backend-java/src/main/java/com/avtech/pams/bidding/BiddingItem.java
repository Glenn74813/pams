package com.avtech.pams.bidding;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One line of a bidding, drawn from a purchase request line. */
@Entity
@Table(name = "bidding_items")
public class BiddingItem extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidding_id")
    private Bidding bidding;

    @Column(name = "purchase_request_item_id")
    private Long purchaseRequestItemId;

    @Column(name = "general_description", nullable = false)
    private String generalDescription;

    private String specifications;
    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "abc_unit_cost", nullable = false)
    private BigDecimal abcUnitCost;

    private String remarks;

    protected BiddingItem() {
    }

    public BiddingItem(Bidding bidding, Long prItemId, String description, String specifications, String unit, int quantity, BigDecimal abcUnitCost) {
        this.bidding = bidding;
        this.purchaseRequestItemId = prItemId;
        this.generalDescription = description;
        this.specifications = specifications;
        this.unit = unit;
        this.quantity = quantity;
        this.abcUnitCost = abcUnitCost;
    }

    public BigDecimal getAbcTotal() {
        return abcUnitCost.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Long getPurchaseRequestItemId() { return purchaseRequestItemId; }
    public String getGeneralDescription() { return generalDescription; }
    public String getSpecifications() { return specifications; }
    public String getUnit() { return unit; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAbcUnitCost() { return abcUnitCost; }
}

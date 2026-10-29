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

/** An observer invited to the bidding (COA, a civil society organisation, a professional body…). */
@Entity
@Table(name = "bid_observers")
public class BidObserver extends Timestamped {

    public static final List<String> TYPES = List.of("COA", "CSO", "Professional Organization", "Other");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidding_id")
    private Bidding bidding;

    @Column(nullable = false)
    private String name;

    private String organization;

    @Column(name = "observer_type", nullable = false)
    private String type;

    @Column(nullable = false)
    private boolean invited = true;

    @Column(name = "invited_date")
    private LocalDate invitedDate;

    @Column(nullable = false)
    private boolean attended;

    private String remarks;

    protected BidObserver() {
    }

    public BidObserver(Bidding bidding) {
        this.bidding = bidding;
    }

    public Long getId() { return id; }
    public Bidding getBidding() { return bidding; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isInvited() { return invited; }
    public void setInvited(boolean invited) { this.invited = invited; }
    public LocalDate getInvitedDate() { return invitedDate; }
    public void setInvitedDate(LocalDate invitedDate) { this.invitedDate = invitedDate; }
    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

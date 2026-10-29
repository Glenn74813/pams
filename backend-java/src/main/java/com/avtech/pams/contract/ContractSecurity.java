package com.avtech.pams.contract;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A security posted for the contract (performance, warranty, retention…): posted, then returned or forfeited. */
@Entity
@Table(name = "contract_securities")
public class ContractSecurity extends Timestamped {

    public static final List<String> TYPES = List.of("Bid Security", "Performance Security", "Warranty Security", "Retention Money");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @Column(name = "security_type", nullable = false)
    private String securityType;

    private String form;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    private BigDecimal percentage;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(name = "validity_date")
    private LocalDate validityDate;

    @Column(nullable = false)
    private String status = "posted";

    private String remarks;

    protected ContractSecurity() {
    }

    public ContractSecurity(Contract contract) {
        this.contract = contract;
    }

    public Long getId() { return id; }
    public String getSecurityType() { return securityType; }
    public void setSecurityType(String securityType) { this.securityType = securityType; }
    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    public LocalDate getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDate postedDate) { this.postedDate = postedDate; }
    public LocalDate getValidityDate() { return validityDate; }
    public void setValidityDate(LocalDate validityDate) { this.validityDate = validityDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

package com.avtech.pams.supplier;

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
import com.avtech.pams.procurement.EligibilityRules;

import java.util.List;

/** A permit, registration or clearance on file for a supplier. */
@Entity
@Table(name = "supplier_compliance_documents")
public class ComplianceDocument extends Timestamped {

    public static final List<String> TYPES = List.of(EligibilityRules.PLATINUM, EligibilityRules.ORG_ID, EligibilityRules.REGISTRATION, "SEC Registration", "DTI Registration",
            EligibilityRules.MAYOR, EligibilityRules.BUSINESS_PERMIT, EligibilityRules.RENEWAL, EligibilityRules.BIR, EligibilityRules.LICENSE, EligibilityRules.CV,
            EligibilityRules.NFCC, EligibilityRules.LOC, EligibilityRules.PCAB, EligibilityRules.TAX_RETURN, EligibilityRules.OSS, "Tax Clearance", "Other");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    private String remarks;

    @Column(name = "document_id")
    private Long documentId;

    protected ComplianceDocument() {
    }

    public ComplianceDocument(Supplier supplier) {
        this.supplier = supplier;
    }

    public boolean validOn(LocalDate day) {
        return expiryDate == null || !expiryDate.isBefore(day);
    }

    public Long getId() { return id; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

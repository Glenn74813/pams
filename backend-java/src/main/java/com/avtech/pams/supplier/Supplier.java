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
import java.util.List;

/** A vendor in the supplier register. */
@Entity
@Table(name = "suppliers")
public class Supplier extends Timestamped {

    /**
     * What a supplier needs on file to be verified for competitive selection (GPPB Circular 06-2026, 5.2). The other
     * items of that list are checked bid by bid, and other modes have their own lists in EligibilityRules.
     */
    public static final List<List<String>> REQUIRED_DOCUMENTS = List.of(List.of(com.avtech.pams.procurement.EligibilityRules.PLATINUM));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

    private String email;
    private String phone;
    private String address;
    private String website;
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "vendor_code")
    private String vendorCode;

    @Column(name = "mobile_number")
    private String mobileNumber;

    @Column(name = "credit_terms")
    private String creditTerms;

    private String tin;

    @Column(name = "philgeps_reg_number")
    private String philgepsRegNumber;

    @Column(name = "is_eligible", nullable = false)
    private boolean eligible;

    @Column(name = "is_msme", nullable = false)
    private boolean msme;

    @Column(name = "supplier_kind", nullable = false)
    private String kind = com.avtech.pams.procurement.EligibilityRules.BUSINESS;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("documentType, id")
    private List<ComplianceDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startDate desc, id desc")
    private List<SupplierSanction> sanctions = new ArrayList<>();

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("evaluationDate desc, id desc")
    private List<SupplierEvaluation> evaluations = new ArrayList<>();

    public SupplierSanction activeSanction(LocalDate on) {
        return sanctions.stream().filter(s -> s.inForce(on)).findFirst().orElse(null);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getCreditTerms() { return creditTerms; }
    public void setCreditTerms(String creditTerms) { this.creditTerms = creditTerms; }
    public String getTin() { return tin; }
    public void setTin(String tin) { this.tin = tin; }
    public String getPhilgepsRegNumber() { return philgepsRegNumber; }
    public void setPhilgepsRegNumber(String philgepsRegNumber) { this.philgepsRegNumber = philgepsRegNumber; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }
    public boolean isMsme() { return msme; }
    public void setMsme(boolean msme) { this.msme = msme; }
    public List<ComplianceDocument> getDocuments() { return documents; }
    public List<SupplierSanction> getSanctions() { return sanctions; }
    public List<SupplierEvaluation> getEvaluations() { return evaluations; }
}

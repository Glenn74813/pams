package com.avtech.pams.property;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A change of accountability: a transfer between people (PTR), a return to the property office
 * (PRS), or re-issuing a returned asset to someone (a new PAR/ICS).
 */
@Entity
@Table(name = "property_transfers")
public class PropertyTransfer extends Timestamped {

    public static final String TRANSFER = "transfer";
    public static final String RETURN = "return";
    public static final String REISSUE = "reissue";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "from_user")
    private Long fromUser;

    @Column(name = "to_user")
    private Long toUser;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "transfer_reason", nullable = false)
    private String transferReason;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "transfer_type", nullable = false)
    private String transferType;

    protected PropertyTransfer() {
    }

    public PropertyTransfer(Long propertyId, String type, Long fromUser, Long toUser, LocalDate date, String reason,
                            Long by, String referenceNumber) {
        this.propertyId = propertyId;
        this.transferType = type;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.transferDate = date;
        this.transferReason = reason;
        this.approvedBy = by;
        this.referenceNumber = referenceNumber;
    }

    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public Long getFromUser() { return fromUser; }
    public Long getToUser() { return toUser; }
    public LocalDate getTransferDate() { return transferDate; }
    public String getTransferReason() { return transferReason; }
    public Long getApprovedBy() { return approvedBy; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getTransferType() { return transferType; }
}

package com.avtech.pams.bac;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

/** A BAC resolution: draft → for approval → approved (→ archived). */
@Entity
@Table(name = "bac_resolutions")
public class BacResolution extends Timestamped {

    public static final List<String> TYPES = List.of("recommendation", "mode_of_procurement", "declaration_of_failure",
            "resolution_to_award", "award", "other");
    /** Types that can carry an award. */
    public static final List<String> AWARD_TYPES = List.of("resolution_to_award", "award", "recommendation");
    public static final String DRAFT = "draft";
    public static final String FOR_APPROVAL = "for_approval";
    public static final String APPROVED = "approved";
    public static final String ARCHIVED = "archived";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resolution_number", nullable = false)
    private String resolutionNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "bac_meeting_id")
    private Long meetingId;

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    @Column(name = "resolution_type", nullable = false)
    private String type = "other";

    private String content;

    @Column(nullable = false)
    private String status = DRAFT;

    @Column(name = "prepared_by")
    private Long preparedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private String remarks;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected BacResolution() {
    }

    public BacResolution(String number, Long preparedBy) {
        this.resolutionNumber = number;
        this.preparedBy = preparedBy;
    }

    public Long getId() { return id; }
    public String getResolutionNumber() { return resolutionNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getMeetingId() { return meetingId; }
    public void setMeetingId(Long meetingId) { this.meetingId = meetingId; }
    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public void setPurchaseRequestId(Long purchaseRequestId) { this.purchaseRequestId = purchaseRequestId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPreparedBy() { return preparedBy; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}

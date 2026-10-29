package com.avtech.pams.bac;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A BAC meeting, with who attended. */
@Entity
@Table(name = "bac_meetings")
public class BacMeeting extends Timestamped {

    public static final List<String> STATUSES = List.of("scheduled", "ongoing", "completed", "cancelled");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_number", nullable = false)
    private String meetingNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "meeting_date", nullable = false)
    private LocalDateTime meetingDate;

    private String venue;
    private String agenda;

    @Column(nullable = false)
    private String status = "scheduled";

    private String minutes;

    @Column(name = "called_by")
    private Long calledBy;

    private String remarks;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BacMeetingAttendee> attendees = new ArrayList<>();

    protected BacMeeting() {
    }

    public BacMeeting(String number, Long calledBy) {
        this.meetingNumber = number;
        this.calledBy = calledBy;
    }

    public Long getId() { return id; }
    public String getMeetingNumber() { return meetingNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getMeetingDate() { return meetingDate; }
    public void setMeetingDate(LocalDateTime meetingDate) { this.meetingDate = meetingDate; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMinutes() { return minutes; }
    public void setMinutes(String minutes) { this.minutes = minutes; }
    public Long getCalledBy() { return calledBy; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public List<BacMeetingAttendee> getAttendees() { return attendees; }
}

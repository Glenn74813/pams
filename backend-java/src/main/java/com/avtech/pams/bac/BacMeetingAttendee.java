package com.avtech.pams.bac;

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

@Entity
@Table(name = "bac_meeting_attendees")
public class BacMeetingAttendee extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bac_meeting_id")
    private BacMeeting meeting;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    /** The attendee's BAC designation (chairperson, member, …) or another role such as observer. */
    @Column(name = "committee_role")
    private String committeeRole;

    @Column(nullable = false)
    private boolean present = true;

    private String remarks;

    protected BacMeetingAttendee() {
    }

    public BacMeetingAttendee(BacMeeting meeting, Long userId, String name, String committeeRole, boolean present, String remarks) {
        this.meeting = meeting;
        this.userId = userId;
        this.name = name;
        this.committeeRole = committeeRole;
        this.present = present;
        this.remarks = remarks;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getCommitteeRole() { return committeeRole; }
    public boolean isPresent() { return present; }
    public String getRemarks() { return remarks; }
}

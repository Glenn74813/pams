package com.avtech.pams.bac;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.List;

/** One seat on the standing Bids and Awards Committee. */
@Entity
@Table(name = "bac_members")
public class BacMember extends Timestamped {

    public static final List<String> DESIGNATIONS = List.of("chairperson", "vice_chairperson", "member", "provisional_member", "twg", "secretariat");
    /** Designations that vote and count towards quorum. */
    public static final List<String> VOTING = List.of("chairperson", "vice_chairperson", "member", "provisional_member");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String position;
    private String office;

    @Column(nullable = false)
    private String designation = "member";

    @Column(name = "appointment_order_no")
    private String appointmentOrderNo;

    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "term_start")
    private LocalDate termStart;

    @Column(name = "term_end")
    private LocalDate termEnd;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sequence;

    private String remarks;

    public boolean isVoting() {
        return VOTING.contains(designation);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getOffice() { return office; }
    public void setOffice(String office) { this.office = office; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getAppointmentOrderNo() { return appointmentOrderNo; }
    public void setAppointmentOrderNo(String appointmentOrderNo) { this.appointmentOrderNo = appointmentOrderNo; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public LocalDate getTermStart() { return termStart; }
    public void setTermStart(LocalDate termStart) { this.termStart = termStart; }
    public LocalDate getTermEnd() { return termEnd; }
    public void setTermEnd(LocalDate termEnd) { this.termEnd = termEnd; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

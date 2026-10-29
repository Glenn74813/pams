package com.avtech.pams.bac;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Bids and Awards Committee: its standing composition (RA 12009: chair, vice-chair, at least
 * five voting members, a secretariat), its meetings with attendance and quorum, and its
 * resolutions. A resolution is approved by someone other than its drafter, and only once the
 * meeting that adopted it has been held with a quorum.
 */
@RestController
@RequestMapping("/api/bac")
public class BacController {

    private static final String MEETING_TYPE = "App\\Models\\BacMeeting";
    private static final String RESOLUTION_TYPE = "App\\Models\\BacResolution";
    private static final String MEMBER_TYPE = "App\\Models\\BacMember";
    private static final String READ = "@perm.any('view_bac', 'manage_bac_meetings', 'manage_bac_resolutions', 'conduct_bid_evaluation', 'process_award', 'create_rfq', 'view_rfq')";

    private final BacMemberRepository members;
    private final BacMeetingRepository meetings;
    private final BacResolutionRepository resolutions;
    private final UserRepository users;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;

    public BacController(BacMemberRepository members, BacMeetingRepository meetings, BacResolutionRepository resolutions, UserRepository users,
                         DocumentNumbers numbers, NotificationService notifications, AuditService audit, CurrentUser current, JdbcTemplate jdbc) {
        this.members = members;
        this.meetings = meetings;
        this.resolutions = resolutions;
        this.users = users;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
    }

    public record Ref(Long id, String name) {
    }

    // ───────────────────────── composition ─────────────────────────

    public record MemberView(Long id, Ref user, String fullName, String position, String office, String designation,
                             String appointmentOrderNo, LocalDate appointmentDate, LocalDate termStart, LocalDate termEnd,
                             boolean isActive, boolean termExpired, int sequence, String remarks) {
    }

    public record Compliance(boolean hasChairperson, boolean hasViceChairperson, int votingMembers, boolean hasSecretariat,
                             boolean hasTwg, boolean isProperlyConstituted, List<String> issues) {
    }

    public record MembersView(List<MemberView> data, Compliance compliance) {
    }

    @GetMapping("/members")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public MembersView indexMembers() {
        List<BacMember> all = members.findAllByOrderBySequenceAscIdAsc();
        Map<Long, String> names = names(all.stream().map(BacMember::getUserId));
        LocalDate today = LocalDate.now(Times.ZONE);
        List<MemberView> out = all.stream().map(m -> new MemberView(m.getId(), ref(m.getUserId(), names), m.getFullName(), m.getPosition(),
                m.getOffice(), m.getDesignation(), m.getAppointmentOrderNo(), m.getAppointmentDate(), m.getTermStart(), m.getTermEnd(),
                m.isActive(), m.getTermEnd() != null && m.getTermEnd().isBefore(today), m.getSequence(), m.getRemarks())).toList();
        return new MembersView(out, compliance(all));
    }

    /** A BAC is properly constituted with a chairperson, a vice-chairperson, five or more voting members and a secretariat. */
    private Compliance compliance(List<BacMember> all) {
        LocalDate today = LocalDate.now(Times.ZONE);
        List<BacMember> seated = all.stream().filter(m -> m.isActive() && (m.getTermEnd() == null || !m.getTermEnd().isBefore(today))).toList();
        boolean chair = seated.stream().anyMatch(m -> "chairperson".equals(m.getDesignation()));
        boolean vice = seated.stream().anyMatch(m -> "vice_chairperson".equals(m.getDesignation()));
        int voting = (int) seated.stream().filter(BacMember::isVoting).count();
        boolean secretariat = seated.stream().anyMatch(m -> "secretariat".equals(m.getDesignation()));
        boolean twg = seated.stream().anyMatch(m -> "twg".equals(m.getDesignation()));
        List<String> issues = new ArrayList<>();
        if (!chair) issues.add("No chairperson.");
        if (!vice) issues.add("No vice-chairperson.");
        if (voting < 5) issues.add("Only " + voting + " voting member(s); at least 5 are required.");
        if (!secretariat) issues.add("No secretariat.");
        long chairs = seated.stream().filter(m -> "chairperson".equals(m.getDesignation())).count();
        if (chairs > 1) issues.add("More than one active chairperson.");
        return new Compliance(chair, vice, voting, secretariat, twg, issues.isEmpty(), issues);
    }

    public record MemberInput(Long userId, String fullName, String position, String office, String designation, String appointmentOrderNo,
                              LocalDate appointmentDate, LocalDate termStart, LocalDate termEnd, Boolean isActive, Integer sequence, String remarks) {
    }

    @PostMapping("/members")
    @PreAuthorize("@perm.any('manage_bac_meetings')")
    @Transactional
    public ResponseEntity<MemberView> storeMember(@RequestBody MemberInput in) {
        BacMember m = new BacMember();
        applyMember(m, in);
        members.save(m);
        audit.record("bac_member_added", MEMBER_TYPE, m.getId(), "BAC member added: " + m.getFullName() + " (" + m.getDesignation() + ")", null, memberSnapshot(m));
        return ResponseEntity.status(HttpStatus.CREATED).body(memberView(m));
    }

    @PutMapping("/members/{id}")
    @PreAuthorize("@perm.any('manage_bac_meetings')")
    @Transactional
    public MemberView updateMember(@PathVariable Long id, @RequestBody MemberInput in) {
        BacMember m = members.findById(id).orElseThrow(() -> ApiException.notFound("BAC member"));
        Map<String, Object> before = memberSnapshot(m);
        applyMember(m, in);
        members.flush();
        audit.record("bac_member_updated", MEMBER_TYPE, m.getId(), "BAC member updated: " + m.getFullName(), before, memberSnapshot(m));
        return memberView(m);
    }

    /** Members are relieved (made inactive), not deleted, so past attendance keeps its meaning; a mistaken entry with no history can go. */
    @DeleteMapping("/members/{id}")
    @PreAuthorize("@perm.any('manage_bac_meetings')")
    @Transactional
    public Map<String, Object> destroyMember(@PathVariable Long id) {
        BacMember m = members.findById(id).orElseThrow(() -> ApiException.notFound("BAC member"));
        Integer attended = m.getUserId() == null ? 0 : jdbc.queryForObject("select count(*) from bac_meeting_attendees where user_id = ?", Integer.class, m.getUserId());
        if (attended != null && attended > 0) {
            m.setActive(false);
            audit.record("bac_member_updated", MEMBER_TYPE, m.getId(), "BAC member relieved: " + m.getFullName(), Map.of("is_active", true), Map.of("is_active", false));
            return Map.of("message", m.getFullName() + " has attended meetings, so they were marked inactive instead of removed.");
        }
        members.delete(m);
        audit.record("bac_member_removed", MEMBER_TYPE, m.getId(), "BAC member removed: " + m.getFullName(), memberSnapshot(m), null);
        return Map.of("message", m.getFullName() + " removed.");
    }

    private void applyMember(BacMember m, MemberInput in) {
        String name = text(in.fullName());
        if (name == null && in.userId() != null) name = users.findById(in.userId()).map(User::getName).orElse(null);
        if (name == null) throw ApiException.validation("full_name", "Enter the member's name.");
        if (name.length() > 255) throw ApiException.validation("full_name", "The name is too long.");
        if (in.designation() == null || !BacMember.DESIGNATIONS.contains(in.designation())) throw ApiException.validation("designation", "Choose the designation.");
        if (in.userId() != null && !users.existsById(in.userId())) throw ApiException.validation("user_id", "That user does not exist.");
        if (in.termStart() != null && in.termEnd() != null && in.termEnd().isBefore(in.termStart())) {
            throw ApiException.validation("term_end", "The term cannot end before it starts.");
        }
        m.setUserId(in.userId());
        m.setFullName(name);
        m.setPosition(text(in.position()));
        m.setOffice(text(in.office()));
        m.setDesignation(in.designation());
        m.setAppointmentOrderNo(text(in.appointmentOrderNo()));
        m.setAppointmentDate(in.appointmentDate());
        m.setTermStart(in.termStart());
        m.setTermEnd(in.termEnd());
        if (in.isActive() != null) m.setActive(in.isActive());
        if (in.sequence() != null) m.setSequence(Math.max(0, in.sequence()));
        m.setRemarks(text(in.remarks()));
    }

    private MemberView memberView(BacMember m) {
        Map<Long, String> names = names(Stream.of(m.getUserId()));
        return new MemberView(m.getId(), ref(m.getUserId(), names), m.getFullName(), m.getPosition(), m.getOffice(), m.getDesignation(),
                m.getAppointmentOrderNo(), m.getAppointmentDate(), m.getTermStart(), m.getTermEnd(), m.isActive(),
                m.getTermEnd() != null && m.getTermEnd().isBefore(LocalDate.now(Times.ZONE)), m.getSequence(), m.getRemarks());
    }

    private static Map<String, Object> memberSnapshot(BacMember m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("full_name", m.getFullName());
        s.put("designation", m.getDesignation());
        s.put("is_active", m.isActive());
        s.put("term_end", m.getTermEnd() == null ? null : m.getTermEnd().toString());
        return s;
    }

    // ───────────────────────── meetings ─────────────────────────

    public record AttendeeView(Long id, Long userId, String name, String committeeRole, boolean present, String remarks) {
    }

    public record ResolutionRef(Long id, String resolutionNumber, String title, String status) {
    }

    public record Quorum(int votingSeated, int votingPresent, int needed, boolean met) {
    }

    public record MeetingView(Long id, String meetingNumber, String title, OffsetDateTime meetingDate, String venue, String agenda, String status,
                              String minutes, Ref caller, String remarks, int attendeesCount, Quorum quorum, List<AttendeeView> attendees,
                              List<ResolutionRef> resolutions, boolean canEdit, OffsetDateTime createdAt) {
    }

    @GetMapping("/meetings")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<MeetingView> indexMeetings(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String search) {
        Page<BacMeeting> result = meetings.search(blank(status), blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<BacMeeting, MeetingView> v = meetingViews(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/meetings/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public MeetingView showMeeting(@PathVariable Long id) {
        BacMeeting m = meetings.findLive(id).orElseThrow(() -> ApiException.notFound("Meeting"));
        return meetingViews(List.of(m), true).get(m);
    }

    public record MeetingInput(String title, LocalDateTime meetingDate, String venue, String agenda, String minutes, String status, String remarks) {
    }

    @PostMapping("/meetings")
    @PreAuthorize("@perm.any('manage_bac_meetings')")
    @Transactional
    public ResponseEntity<MeetingView> storeMeeting(@RequestBody MeetingInput in) {
        BacMeeting m = new BacMeeting(numbers.series("BAC-MTG-" + LocalDate.now(Times.ZONE).getYear() + "-", 3, "bac_meetings.meeting_number"), current.user().getId());
        if (in.meetingDate() == null) throw ApiException.validation("meeting_date", "Set the date and time of the meeting.");
        applyMeeting(m, in);
        meetings.save(m);
        audit.record("created", MEETING_TYPE, m.getId(), "Scheduled BAC meeting " + m.getMeetingNumber(), null, Map.of("title", m.getTitle(), "meeting_date", m.getMeetingDate().toString()));
        notifications.notifyPermission(List.of("view_bac", "manage_bac_meetings", "manage_bac_resolutions"), current.user().getId(), "bac_meeting",
                "BAC meeting scheduled", m.getMeetingNumber() + ": " + m.getTitle() + " on " + m.getMeetingDate().toLocalDate(), MEETING_TYPE, m.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingViews(List.of(m), true).get(m));
    }

    /** scheduled → ongoing → completed, or cancelled. Details are fixed once a meeting is over; minutes can still be written up. */
    @PutMapping("/meetings/{id}")
    @PreAuthorize("@perm.any('manage_bac_meetings')")
    @Transactional
    public MeetingView updateMeeting(@PathVariable Long id, @RequestBody MeetingInput in) {
        BacMeeting m = meetings.lockById(id).orElseThrow(() -> ApiException.notFound("Meeting"));
        String was = m.getStatus();
        if ("cancelled".equals(was)) throw ApiException.of(HttpStatus.CONFLICT, "This meeting was cancelled.");
        boolean over = "completed".equals(was);
        if (over && (in.title() != null || in.meetingDate() != null || in.venue() != null || in.agenda() != null)) {
            throw ApiException.of(HttpStatus.CONFLICT, "The meeting is over; only the minutes and remarks can change.");
        }
        if (in.status() != null && !in.status().equals(was)) {
            if (!BacMeeting.STATUSES.contains(in.status())) throw ApiException.validation("status", "Unknown status.");
            boolean ok = switch (was) {
                case "scheduled" -> List.of("ongoing", "completed", "cancelled").contains(in.status());
                case "ongoing" -> List.of("completed", "cancelled").contains(in.status());
                default -> false;
            };
            if (!ok) throw ApiException.validation("status", "A " + was + " meeting cannot become " + in.status() + ".");
            if ("completed".equals(in.status()) && m.getAttendees().isEmpty()) {
                throw ApiException.validation("status", "Record the attendance before marking the meeting completed.");
            }
            if ("cancelled".equals(in.status()) && !resolutions.forMeeting(m.getId()).isEmpty()) {
                throw ApiException.validation("status", "Resolutions are tied to this meeting; it cannot be cancelled.");
            }
        }
        applyMeeting(m, in);
        meetings.flush();
        audit.record("updated", MEETING_TYPE, m.getId(), "Updated BAC meeting " + m.getMeetingNumber(), Map.of("status", was), Map.of("status", m.getStatus()));
        return meetingViews(List.of(m), true).get(m);
    }

    public record AttendeeInput(Long userId, String name, String committeeRole, Boolean present, String remarks) {
    }

    public record AttendanceInput(List<AttendeeInput> attendees) {
    }

    @PostMapping("/meetings/{id}/attendance")
    @PreAuthorize("@perm.any('manage_bac_meetings')")
    @Transactional
    public MeetingView recordAttendance(@PathVariable Long id, @RequestBody AttendanceInput in) {
        BacMeeting m = meetings.lockById(id).orElseThrow(() -> ApiException.notFound("Meeting"));
        if ("cancelled".equals(m.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This meeting was cancelled.");
        if (in == null || in.attendees() == null || in.attendees().isEmpty()) throw ApiException.validation("attendees", "List who attended.");
        if (approvedResolutions(m) > 0) throw ApiException.of(HttpStatus.CONFLICT, "Resolutions from this meeting are approved; its attendance is final.");
        m.getAttendees().clear();
        meetings.flush();
        for (int i = 0; i < in.attendees().size(); i++) {
            AttendeeInput a = in.attendees().get(i);
            String name = a == null ? null : text(a.name());
            if (name == null) throw ApiException.validation("attendees." + i + ".name", "Attendee " + (i + 1) + " needs a name.");
            m.getAttendees().add(new BacMeetingAttendee(m, a.userId(), name, text(a.committeeRole()), !Boolean.FALSE.equals(a.present()), text(a.remarks())));
        }
        meetings.flush();
        Quorum q = quorum(m);
        audit.record("attendance_recorded", MEETING_TYPE, m.getId(), "Attendance recorded for " + m.getMeetingNumber() + " (" + q.votingPresent() + " voting present, quorum "
                + (q.met() ? "met" : "not met") + ")", null, Map.of("present", q.votingPresent()));
        return meetingViews(List.of(m), true).get(m);
    }

    @DeleteMapping("/meetings/{id}")
    @PreAuthorize("@perm.any('manage_bac_meetings')")
    @Transactional
    public Map<String, Object> destroyMeeting(@PathVariable Long id) {
        BacMeeting m = meetings.lockById(id).orElseThrow(() -> ApiException.notFound("Meeting"));
        if ("completed".equals(m.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "A held meeting is part of the record; it cannot be deleted.");
        if (!resolutions.forMeeting(m.getId()).isEmpty()) throw ApiException.of(HttpStatus.CONFLICT, "Resolutions are tied to this meeting.");
        m.setDeletedAt(Times.now());
        audit.record("deleted", MEETING_TYPE, m.getId(), "Deleted BAC meeting " + m.getMeetingNumber(), Map.of("title", m.getTitle()), null);
        return Map.of("message", m.getMeetingNumber() + " deleted.");
    }

    private void applyMeeting(BacMeeting m, MeetingInput in) {
        if (in.title() != null || m.getTitle() == null) {
            String t = text(in.title());
            if (t == null || t.length() > 255) throw ApiException.validation("title", "Give the meeting a title of up to 255 characters.");
            m.setTitle(t);
        }
        if (in.meetingDate() != null) m.setMeetingDate(in.meetingDate());
        if (in.venue() != null) m.setVenue(text(in.venue()));
        if (in.agenda() != null) m.setAgenda(text(in.agenda()));
        if (in.minutes() != null) m.setMinutes(text(in.minutes()));
        if (in.remarks() != null) m.setRemarks(text(in.remarks()));
        if (in.status() != null) m.setStatus(in.status());
    }

    /** Quorum: a majority of the seated voting members, counted from attendees present in a voting designation. */
    private Quorum quorum(BacMeeting m) {
        int seated = (int) members.findByActiveTrue().stream().filter(BacMember::isVoting).count();
        int present = (int) m.getAttendees().stream().filter(a -> a.isPresent() && a.getCommitteeRole() != null && BacMember.VOTING.contains(a.getCommitteeRole())).count();
        int needed = seated / 2 + 1;
        return new Quorum(seated, present, needed, seated > 0 && present >= needed);
    }

    private int approvedResolutions(BacMeeting m) {
        return (int) resolutions.forMeeting(m.getId()).stream().filter(r -> BacResolution.APPROVED.equals(r.getStatus()) || BacResolution.ARCHIVED.equals(r.getStatus())).count();
    }

    private Map<BacMeeting, MeetingView> meetingViews(Collection<BacMeeting> list, boolean full) {
        Map<Long, String> names = names(list.stream().map(BacMeeting::getCalledBy));
        boolean manager = current.any("manage_bac_meetings");
        Map<BacMeeting, MeetingView> out = new LinkedHashMap<>();
        for (BacMeeting m : list) {
            List<AttendeeView> att = !full ? null : m.getAttendees().stream()
                    .map(a -> new AttendeeView(a.getId(), a.getUserId(), a.getName(), a.getCommitteeRole(), a.isPresent(), a.getRemarks())).toList();
            List<ResolutionRef> res = !full ? null : resolutions.forMeeting(m.getId()).stream()
                    .map(r -> new ResolutionRef(r.getId(), r.getResolutionNumber(), r.getTitle(), r.getStatus())).toList();
            out.put(m, new MeetingView(m.getId(), m.getMeetingNumber(), m.getTitle(), Times.toOffset(m.getMeetingDate()), m.getVenue(), m.getAgenda(),
                    m.getStatus(), m.getMinutes(), ref(m.getCalledBy(), names), m.getRemarks(), m.getAttendees().size(), quorum(m), att, res,
                    manager && !"cancelled".equals(m.getStatus()), Times.toOffset(m.getCreatedAt())));
        }
        return out;
    }

    // ───────────────────────── resolutions ─────────────────────────

    public record ResolutionView(Long id, String resolutionNumber, String title, String resolutionType, String content, String status,
                                 Ref meeting, Ref purchaseRequest, Ref preparer, Ref approver, OffsetDateTime approvedAt, String remarks,
                                 List<String> usedBy, boolean canEdit, boolean canSubmit, boolean canApprove, boolean canArchive, boolean canDelete,
                                 OffsetDateTime createdAt) {
    }

    @GetMapping("/resolutions")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<ResolutionView> indexResolutions(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                                         @RequestParam(name = "resolution_type", required = false) String type,
                                                         @RequestParam(name = "purchase_request_id", required = false) Long pr,
                                                         @RequestParam(required = false) String search) {
        Page<BacResolution> result = resolutions.search(blank(status), blank(type), pr, blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<BacResolution, ResolutionView> v = resolutionViews(result.getContent());
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/resolutions/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public ResolutionView showResolution(@PathVariable Long id) {
        BacResolution r = resolutions.findLive(id).orElseThrow(() -> ApiException.notFound("Resolution"));
        return resolutionViews(List.of(r)).get(r);
    }

    public record ResolutionInput(String title, String resolutionType, Long bacMeetingId, Long purchaseRequestId, String content, String remarks) {
    }

    @PostMapping("/resolutions")
    @PreAuthorize("@perm.any('manage_bac_resolutions')")
    @Transactional
    public ResponseEntity<ResolutionView> storeResolution(@RequestBody ResolutionInput in) {
        BacResolution r = new BacResolution(numbers.series("BAC-RES-" + LocalDate.now(Times.ZONE).getYear() + "-", 3, "bac_resolutions.resolution_number"), current.user().getId());
        applyResolution(r, in);
        resolutions.save(r);
        audit.record("created", RESOLUTION_TYPE, r.getId(), "Drafted resolution " + r.getResolutionNumber(), null, Map.of("title", r.getTitle(), "type", r.getType()));
        return ResponseEntity.status(HttpStatus.CREATED).body(resolutionViews(List.of(r)).get(r));
    }

    @PutMapping("/resolutions/{id}")
    @PreAuthorize("@perm.any('manage_bac_resolutions')")
    @Transactional
    public ResolutionView updateResolution(@PathVariable Long id, @RequestBody ResolutionInput in) {
        BacResolution r = lockResolution(id);
        if (!BacResolution.DRAFT.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only draft resolutions can be edited.");
        applyResolution(r, in);
        resolutions.flush();
        audit.record("updated", RESOLUTION_TYPE, r.getId(), "Edited resolution " + r.getResolutionNumber(), null, Map.of("title", r.getTitle()));
        return resolutionViews(List.of(r)).get(r);
    }

    @PostMapping("/resolutions/{id}/submit")
    @PreAuthorize("@perm.any('manage_bac_resolutions')")
    @Transactional
    public ResolutionView submitResolution(@PathVariable Long id) {
        BacResolution r = lockResolution(id);
        if (!BacResolution.DRAFT.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only draft resolutions can be submitted for approval.");
        if (r.getContent() == null) throw ApiException.validation("content", "Write the resolution before submitting it.");
        r.setStatus(BacResolution.FOR_APPROVAL);
        resolutions.flush();
        audit.record("submitted", RESOLUTION_TYPE, r.getId(), "Resolution " + r.getResolutionNumber() + " submitted for approval", Map.of("status", "draft"), Map.of("status", r.getStatus()));
        notifications.notifyPermission(List.of("manage_bac_resolutions"), current.user().getId(), "bac_resolution", "Resolution awaiting approval",
                r.getResolutionNumber() + " (" + r.getTitle() + ") is awaiting approval.", RESOLUTION_TYPE, r.getId());
        return resolutionViews(List.of(r)).get(r);
    }

    public record ReturnInput(String remarks) {
    }

    /** Sends a resolution back to draft for changes. */
    @PostMapping("/resolutions/{id}/return")
    @PreAuthorize("@perm.any('manage_bac_resolutions')")
    @Transactional
    public ResolutionView returnResolution(@PathVariable Long id, @RequestBody ReturnInput in) {
        String why = in == null ? null : text(in.remarks());
        if (why == null) throw ApiException.validation("remarks", "Say what needs changing.");
        BacResolution r = lockResolution(id);
        if (!BacResolution.FOR_APPROVAL.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only resolutions awaiting approval can be returned.");
        notOwn(r);
        r.setStatus(BacResolution.DRAFT);
        r.setRemarks(why);
        resolutions.flush();
        audit.record("returned", RESOLUTION_TYPE, r.getId(), "Resolution " + r.getResolutionNumber() + " returned: " + why, Map.of("status", "for_approval"), Map.of("status", "draft"));
        notifications.notifyUser(r.getPreparedBy(), "bac_resolution", "Resolution returned", r.getResolutionNumber() + " was returned: " + why, RESOLUTION_TYPE, r.getId());
        return resolutionViews(List.of(r)).get(r);
    }

    @PostMapping("/resolutions/{id}/approve")
    @PreAuthorize("@perm.any('manage_bac_resolutions')")
    @Transactional
    public ResolutionView approveResolution(@PathVariable Long id) {
        BacResolution r = lockResolution(id);
        if (!BacResolution.FOR_APPROVAL.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Submit the resolution for approval first.");
        notOwn(r);
        if (r.getMeetingId() != null) {
            BacMeeting m = meetings.findLive(r.getMeetingId()).orElseThrow(() -> ApiException.of(HttpStatus.CONFLICT, "The meeting this resolution cites no longer exists."));
            if (!"completed".equals(m.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Meeting " + m.getMeetingNumber() + " has not been held (marked completed) yet.");
            Quorum q = quorum(m);
            if (!q.met()) {
                throw ApiException.of(HttpStatus.CONFLICT, "Meeting " + m.getMeetingNumber() + " had no quorum (" + q.votingPresent() + " of " + q.needed()
                        + " voting members needed); the resolution cannot be approved.");
            }
        }
        r.setStatus(BacResolution.APPROVED);
        r.setApprovedBy(current.user().getId());
        r.setApprovedAt(Times.now());
        resolutions.flush();
        audit.record("approved", RESOLUTION_TYPE, r.getId(), "Resolution " + r.getResolutionNumber() + " approved", Map.of("status", "for_approval"), Map.of("status", r.getStatus()));
        notifications.notifyUser(r.getPreparedBy(), "bac_resolution", "Resolution approved", r.getResolutionNumber() + " was approved.", RESOLUTION_TYPE, r.getId());
        return resolutionViews(List.of(r)).get(r);
    }

    @PostMapping("/resolutions/{id}/archive")
    @PreAuthorize("@perm.any('manage_bac_resolutions')")
    @Transactional
    public ResolutionView archiveResolution(@PathVariable Long id) {
        BacResolution r = lockResolution(id);
        if (!BacResolution.APPROVED.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only approved resolutions are archived.");
        r.setStatus(BacResolution.ARCHIVED);
        resolutions.flush();
        audit.record("archived", RESOLUTION_TYPE, r.getId(), "Resolution " + r.getResolutionNumber() + " archived", Map.of("status", "approved"), Map.of("status", r.getStatus()));
        return resolutionViews(List.of(r)).get(r);
    }

    @DeleteMapping("/resolutions/{id}")
    @PreAuthorize("@perm.any('manage_bac_resolutions')")
    @Transactional
    public Map<String, Object> destroyResolution(@PathVariable Long id) {
        BacResolution r = lockResolution(id);
        if (!BacResolution.DRAFT.equals(r.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only draft resolutions can be deleted.");
        if (!usedBy(r.getId()).isEmpty()) throw ApiException.of(HttpStatus.CONFLICT, "An award cites this resolution.");
        r.setDeletedAt(Times.now());
        audit.record("deleted", RESOLUTION_TYPE, r.getId(), "Deleted resolution " + r.getResolutionNumber(), Map.of("title", r.getTitle()), null);
        return Map.of("message", r.getResolutionNumber() + " deleted.");
    }

    private void applyResolution(BacResolution r, ResolutionInput in) {
        String title = text(in.title());
        if (title == null || title.length() > 255) throw ApiException.validation("title", "Give the resolution a title of up to 255 characters.");
        String type = in.resolutionType() == null ? "other" : in.resolutionType();
        if (!BacResolution.TYPES.contains(type)) throw ApiException.validation("resolution_type", "Unknown resolution type.");
        if (in.bacMeetingId() != null) {
            BacMeeting m = meetings.findLive(in.bacMeetingId()).orElseThrow(() -> ApiException.validation("bac_meeting_id", "That meeting does not exist."));
            if ("cancelled".equals(m.getStatus())) throw ApiException.validation("bac_meeting_id", "That meeting was cancelled.");
        }
        if (in.purchaseRequestId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from purchase_requests where id = ? and deleted_at is null", Integer.class, in.purchaseRequestId());
            if (n == null || n == 0) throw ApiException.validation("purchase_request_id", "That purchase request does not exist.");
        }
        if (List.of("resolution_to_award", "award").contains(type) && in.purchaseRequestId() == null) {
            throw ApiException.validation("purchase_request_id", "An award resolution must name the purchase request it awards.");
        }
        r.setTitle(title);
        r.setType(type);
        r.setMeetingId(in.bacMeetingId());
        r.setPurchaseRequestId(in.purchaseRequestId());
        r.setContent(text(in.content()));
        r.setRemarks(text(in.remarks()));
    }

    private BacResolution lockResolution(Long id) {
        return resolutions.lockById(id).orElseThrow(() -> ApiException.notFound("Resolution"));
    }

    private void notOwn(BacResolution r) {
        User me = current.user();
        if (me.getId().equals(r.getPreparedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You drafted this resolution, so someone else has to approve or return it.");
    }

    /** Award documents that cite a resolution (RFQs and biddings). */
    private List<String> usedBy(Long resolutionId) {
        return jdbc.queryForList("""
                select rfq_number from rfqs where bac_resolution_id = ? and deleted_at is null
                union all select bidding_number from biddings where bac_resolution_id = ? and deleted_at is null""", String.class, resolutionId, resolutionId);
    }

    private Map<BacResolution, ResolutionView> resolutionViews(Collection<BacResolution> list) {
        Map<Long, String> names = names(list.stream().flatMap(r -> Stream.of(r.getPreparedBy(), r.getApprovedBy())));
        Map<Long, String> meetingNames = new HashMap<>();
        meetings.findAllById(list.stream().map(BacResolution::getMeetingId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(m -> meetingNames.put(m.getId(), m.getMeetingNumber() + " — " + m.getTitle()));
        Map<Long, String> prNumbers = new HashMap<>();
        List<Long> prIds = list.stream().map(BacResolution::getPurchaseRequestId).filter(Objects::nonNull).distinct().toList();
        if (!prIds.isEmpty()) {
            jdbc.query("select id, pr_number from purchase_requests where id in (" + prIds.stream().map(x -> "?").collect(Collectors.joining(",")) + ")",
                    rs -> {
                        prNumbers.put(rs.getLong(1), rs.getString(2));
                    }, prIds.toArray());
        }
        User me = current.user();
        boolean manager = current.any("manage_bac_resolutions");
        Map<BacResolution, ResolutionView> out = new LinkedHashMap<>();
        for (BacResolution r : list) {
            boolean own = me.getId().equals(r.getPreparedBy()) && !me.isSuperAdmin();
            boolean draft = BacResolution.DRAFT.equals(r.getStatus());
            List<String> used = usedBy(r.getId());
            out.put(r, new ResolutionView(r.getId(), r.getResolutionNumber(), r.getTitle(), r.getType(), r.getContent(), r.getStatus(),
                    r.getMeetingId() == null ? null : new Ref(r.getMeetingId(), meetingNames.get(r.getMeetingId())),
                    r.getPurchaseRequestId() == null ? null : new Ref(r.getPurchaseRequestId(), prNumbers.get(r.getPurchaseRequestId())),
                    ref(r.getPreparedBy(), names), ref(r.getApprovedBy(), names), Times.toOffset(r.getApprovedAt()), r.getRemarks(), used,
                    manager && draft, manager && draft, manager && !own && BacResolution.FOR_APPROVAL.equals(r.getStatus()),
                    manager && BacResolution.APPROVED.equals(r.getStatus()), manager && draft && used.isEmpty(), Times.toOffset(r.getCreatedAt())));
        }
        return out;
    }

    // ───────────────────────── helpers ─────────────────────────

    private Map<Long, String> names(Stream<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(ids.filter(Objects::nonNull).collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        return names;
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id, names.get(id));
    }

    static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

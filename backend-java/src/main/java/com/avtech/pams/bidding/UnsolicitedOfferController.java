package com.avtech.pams.bidding;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.Inserts;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.procurement.AwardSupport;
import com.avtech.pams.procurement.ProcurementRules;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unsolicited Offer with Bid Matching (IRR Section 30): the offer is assessed within 20 days, its documents come in
 * within 30, evaluation takes up to 60 (plus up to 30), negotiation up to 15; the offeror then becomes the original
 * offeror, posts bid security within 10 days, and an Invitation to Bid goes out within 30. The lowest responsive
 * comparative bid can be matched within 15 days, and the award follows on the bidding.
 */
@RestController
@RequestMapping("/api/unsolicited-offers")
public class UnsolicitedOfferController {

    private static final String TYPE = "App\\Models\\UnsolicitedOffer";
    private static final String READ = "@perm.any('view_bidding', 'manage_bidding', 'process_award', 'conduct_bid_evaluation')";
    private static final String MANAGE = "@perm.any('manage_bidding')";
    private static final int ASSESS_DAYS = 20;
    private static final int DOCUMENT_DAYS = 30;
    private static final int WITHDRAW_DAYS = 5;
    private static final int EVALUATION_DAYS = 60;
    private static final int EXTENSION_DAYS = 30;
    private static final int NEGOTIATION_START_DAYS = 5;
    private static final int NEGOTIATION_DAYS = 15;
    private static final int SECURITY_DAYS = 10;
    private static final int INVITATION_DAYS = 30;
    private static final int MATCH_DAYS = 15;

    /** The instances of a new concept or technology in IRR 30.1 (a). */
    static final Map<String, String> BASES = new LinkedHashMap<>();
    /** Before the certification the offer can still be dropped; after the award nothing moves. */
    private static final Set<String> OPEN = Set.of("received", "awaiting_documents", "evaluation", "negotiation", "original_offeror", "comparative_bidding", "bid_matching");

    static {
        BASES.put("substantial_improvement", "Substantial improvement over existing solutions");
        BASES.put("emerging_sustainable", "Promotes emerging technology and sustainable solutions");
        BASES.put("state_of_the_art", "State-of-the-art technology, certified by an independent agency");
        BASES.put("significant_benefit", "Significant benefits in efficiency, cost or outcome");
    }

    private final JdbcTemplate jdbc;
    private final Db db;
    private final DocumentNumbers numbers;
    private final AwardSupport awards;
    private final BiddingController biddings;
    private final CurrentUser current;
    private final AuditService audit;

    public UnsolicitedOfferController(JdbcTemplate jdbc, Db db, DocumentNumbers numbers, AwardSupport awards, BiddingController biddings, CurrentUser current, AuditService audit) {
        this.jdbc = jdbc;
        this.db = db;
        this.numbers = numbers;
        this.awards = awards;
        this.biddings = biddings;
        this.current = current;
        this.audit = audit;
    }

    public record Ref(Long id, String name) {
    }

    public record OfferView(Long id, String offerNumber, Ref supplier, String title, String concept, String category, BigDecimal proposedAmount, LocalDate receivedDate, String status,
                            LocalDate noticeDate, LocalDate documentsDate, String newConceptBasis, Integer evaluationExtensionDays, String evaluationExtensionReason, LocalDate evaluationDate,
                            LocalDate negotiationStart, LocalDate negotiationEnd, BigDecimal negotiatedAmount, String negotiatedTerms, String certificationNumber, LocalDate certificationDate,
                            String bidSecurityForm, BigDecimal bidSecurityAmount, LocalDate bidSecurityDate, BigDecimal bidSecurityRequired, Ref bidding, String biddingStatus, LocalDate lccrbDate,
                            Ref lccrbSupplier, BigDecimal lccrbAmount, String matchResult, BigDecimal matchAmount, LocalDate matchDate, String closingReason, LocalDate closedDate,
                            boolean lateWithdrawal, LocalDate dueDate, String dueFor, boolean overdue, boolean canManage) {
    }

    @GetMapping("/options")
    @PreAuthorize(READ)
    public Map<String, Object> options() {
        return Map.of("bases", BASES, "security_forms", BiddingController.SECURITY_RATES.keySet(), "categories", List.of("Goods", "Consulting Services"));
    }

    @GetMapping
    @PreAuthorize(READ)
    public PageResponse<OfferView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status, @RequestParam(required = false) String search) {
        StringBuilder where = new StringBuilder(" from unsolicited_offers o join suppliers s on s.id = o.supplier_id where o.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" and o.status = ?");
            args.add(status);
        }
        if (search != null && !search.isBlank()) {
            where.append(" and (lower(o.offer_number) like ? or lower(o.title) like ? or lower(s.name) like ?)");
            String like = "%" + search.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Integer total = jdbc.queryForObject("select count(*)" + where, Integer.class, args.toArray());
        int number = Math.max(1, page);
        List<Object> paged = new ArrayList<>(args);
        paged.add((number - 1) * 20);
        List<OfferView> rows = jdbc.queryForList("select o.*" + where + " order by o.id desc offset ? rows fetch next 20 rows only", paged.toArray()).stream().map(this::view).toList();
        return new PageResponse<>(rows, new PageResponse.Pagination(total == null ? 0 : total, 20, number, Math.max(1, (int) Math.ceil((total == null ? 0 : total) / 20.0))));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public OfferView show(@PathVariable Long id) {
        return view(row(id));
    }

    public record OfferInput(Long supplierId, String supplierName, String title, String concept, String category, BigDecimal proposedAmount, LocalDate receivedDate) {
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<OfferView> store(@RequestBody OfferInput in) {
        String title = text(in.title());
        if (title == null || title.length() > 255) throw ApiException.validation("title", "Give the offer a title of up to 255 characters.");
        String concept = text(in.concept());
        if (concept == null) throw ApiException.validation("concept", "Describe the new concept or technology offered (IRR 30.2 a).");
        if (!List.of("Goods", "Consulting Services").contains(String.valueOf(in.category()))) {
            throw ApiException.validation("category", "This mode covers Goods and Consulting Services only (IRR 30.1).");
        }
        if (in.proposedAmount() == null || in.proposedAmount().signum() <= 0) throw ApiException.validation("proposed_amount", "Enter the proposed contract cost, which serves as the indicative ABC.");
        LocalDate received = past("received_date", in.receivedDate());
        Long supplierId = awards.supplier(in.supplierId(), in.supplierName());
        String number = numbers.yearly("UO", "unsolicited_offers.offer_number");
        Timestamp now = Timestamp.valueOf(Times.now());
        Long id = Inserts.insert(jdbc, """
                insert into unsolicited_offers (offer_number, supplier_id, title, concept, category, proposed_amount, received_date, status, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'received', ?, ?, ?)""", number, supplierId, title, concept, in.category(), in.proposedAmount(), Date.valueOf(received), current.user().getId(), now, now);
        audit.record("created", TYPE, id, "Received unsolicited offer " + number + " from " + awards.supplierName(supplierId), null, Map.of("title", title));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(row(id)));
    }

    public record AssessInput(Boolean needed, LocalDate noticeDate, String reason) {
    }

    /** Whether the project is needed, within 20 days of receipt; if not acted on in time the offer is deemed rejected (IRR 30.3). */
    @PostMapping("/{id}/assess")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView assess(@PathVariable Long id, @RequestBody AssessInput in) {
        Map<String, Object> o = lock(id);
        status(o, "received");
        LocalDate date = past("notice_date", in.noticeDate());
        if (date.isBefore(date(o, "received_date"))) throw ApiException.validation("notice_date", "The notice cannot come before the offer was received.");
        if (date.isAfter(date(o, "received_date").plusDays(ASSESS_DAYS))) return close(id, "rejected", "Deemed rejected: not acted on within " + ASSESS_DAYS + " calendar days (IRR 30.3).", date);
        if (!Boolean.TRUE.equals(in.needed())) {
            String why = text(in.reason());
            if (why == null) throw ApiException.validation("reason", "Say why the project is not needed.");
            return close(id, "rejected", why, date);
        }
        jdbc.update("update unsolicited_offers set status = 'awaiting_documents', notice_date = ?, updated_at = ? where id = ?", Date.valueOf(date), stamp(), id);
        record(id, o, "Need determined; offeror notified to complete its documents within " + DOCUMENT_DAYS + " days");
        return view(row(id));
    }

    public record DocumentsInput(Boolean conceptDetails, Boolean proofOfConcept, Boolean eligibilityDocuments, Boolean termsAndConsent, LocalDate date) {
    }

    /** All four sets of IRR 30.2, within 30 days of the notice; a resubmission for lacking requirements is a new offer. */
    @PostMapping("/{id}/documents")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView documents(@PathVariable Long id, @RequestBody DocumentsInput in) {
        Map<String, Object> o = lock(id);
        status(o, "awaiting_documents");
        LocalDate date = past("date", in.date());
        if (date.isAfter(date(o, "notice_date").plusDays(DOCUMENT_DAYS))) return close(id, "rejected", "Deemed rejected: documents not complete within " + DOCUMENT_DAYS + " calendar days of the notice (IRR 30.3).", date);
        if (!Boolean.TRUE.equals(in.conceptDetails()) || !Boolean.TRUE.equals(in.proofOfConcept()) || !Boolean.TRUE.equals(in.eligibilityDocuments()) || !Boolean.TRUE.equals(in.termsAndConsent())) {
            throw ApiException.validation("documents", "All four are needed: concept details and cost, proof of concept, eligibility documents, and terms with consent to bid matching (IRR 30.2).");
        }
        jdbc.update("update unsolicited_offers set status = 'evaluation', documents_date = ?, updated_at = ? where id = ?", Date.valueOf(date), stamp(), id);
        record(id, o, "Documents complete; evaluation started");
        return view(row(id));
    }

    public record EvaluationInput(Boolean favorable, String newConceptBasis, Boolean fundsAvailable, Boolean eligibilityVerified, LocalDate date, LocalDate negotiationStart,
                                  Integer extensionDays, String extensionReason, String reason) {
    }

    /** The result of the evaluation (IRR 30.5): 60 days, or up to 30 more with the HoPE's justified extension. */
    @PostMapping("/{id}/evaluate")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView evaluate(@PathVariable Long id, @RequestBody EvaluationInput in) {
        Map<String, Object> o = lock(id);
        status(o, "evaluation");
        LocalDate date = past("date", in.date());
        int extension = in.extensionDays() == null ? 0 : in.extensionDays();
        if (extension < 0 || extension > EXTENSION_DAYS) throw ApiException.validation("extension_days", "The HoPE may extend by up to " + EXTENSION_DAYS + " calendar days.");
        String extensionReason = text(in.extensionReason());
        if (extension > 0 && extensionReason == null) throw ApiException.validation("extension_reason", "Record the HoPE's reason for the extension.");
        LocalDate limit = date(o, "documents_date").plusDays(EVALUATION_DAYS + extension);
        if (date.isAfter(limit)) throw ApiException.validation("date", "The evaluation had to finish by " + limit + "; record the HoPE's extension, or reject the offer.");
        if (!Boolean.TRUE.equals(in.favorable())) {
            String why = text(in.reason());
            if (why == null) throw ApiException.validation("reason", "Say why the evaluation was not favorable.");
            return close(id, "rejected", why, date);
        }
        String basis = text(in.newConceptBasis());
        if (basis == null || !BASES.containsKey(basis)) throw ApiException.validation("new_concept_basis", "Choose how the HoPE found this a new concept or technology (IRR 30.1 a).");
        if (!Boolean.TRUE.equals(in.fundsAvailable())) throw ApiException.validation("funds_available", "Confirm that funds are available to meet the offer (IRR 30.5).");
        if (!Boolean.TRUE.equals(in.eligibilityVerified())) throw ApiException.validation("eligibility_verified", "Confirm the offeror's legal, technical and financial eligibility (IRR 30.5).");
        LocalDate start = in.negotiationStart() == null ? date : in.negotiationStart();
        if (start.isBefore(date) || start.isAfter(date.plusDays(NEGOTIATION_START_DAYS))) throw ApiException.validation("negotiation_start", "Negotiation starts within " + NEGOTIATION_START_DAYS + " calendar days of the notice (IRR 30.5).");
        jdbc.update("""
                update unsolicited_offers set status = 'negotiation', new_concept_basis = ?, evaluation_extension_days = ?, evaluation_extension_reason = ?, evaluation_date = ?,
                       negotiation_start = ?, updated_at = ? where id = ?""", basis, extension == 0 ? null : extension, extensionReason, Date.valueOf(date), Date.valueOf(start), stamp(), id);
        record(id, o, "Evaluation favorable; negotiation starts " + start);
        return view(row(id));
    }

    public record NegotiationInput(Boolean successful, LocalDate date, BigDecimal negotiatedAmount, String terms, String certificationNumber, String reason) {
    }

    /** Negotiation ends within 15 days: a failure ends the procurement, success confers original offeror status (IRR 30.6). */
    @PostMapping("/{id}/negotiation")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView negotiation(@PathVariable Long id, @RequestBody NegotiationInput in) {
        Map<String, Object> o = lock(id);
        status(o, "negotiation");
        LocalDate date = past("date", in.date());
        LocalDate start = date(o, "negotiation_start");
        if (date.isBefore(start)) throw ApiException.validation("date", "This is before the negotiation started on " + start + ".");
        if (!Boolean.TRUE.equals(in.successful())) {
            String why = text(in.reason());
            if (why == null) throw ApiException.validation("reason", "State why the negotiation failed.");
            return close(id, "failed_negotiation", why, date);
        }
        if (date.isAfter(start.plusDays(NEGOTIATION_DAYS))) throw ApiException.validation("date", "Negotiation is completed within " + NEGOTIATION_DAYS + " calendar days, by " + start.plusDays(NEGOTIATION_DAYS) + "; declare a failure of negotiation instead.");
        if (in.negotiatedAmount() == null || in.negotiatedAmount().signum() <= 0) throw ApiException.validation("negotiated_amount", "Enter the negotiated price.");
        String certification = text(in.certificationNumber());
        if (certification == null || certification.length() > 60) throw ApiException.validation("certification_number", "Enter the number of the certification conferring original offeror status.");
        jdbc.update("""
                update unsolicited_offers set status = 'original_offeror', negotiation_end = ?, negotiated_amount = ?, negotiated_terms = ?, certification_number = ?, certification_date = ?, updated_at = ?
                where id = ?""", Date.valueOf(date), in.negotiatedAmount(), text(in.terms()), certification, Date.valueOf(date), stamp(), id);
        record(id, o, "Negotiation successful at ₱" + ProcurementRules.peso(in.negotiatedAmount()) + "; original offeror status conferred (" + certification + ")");
        return view(row(id));
    }

    public record SecurityInput(String form, BigDecimal amount, LocalDate date) {
    }

    /** Bid security within 10 days of the certification (IRR 30.6). */
    @PostMapping("/{id}/bid-security")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView bidSecurity(@PathVariable Long id, @RequestBody SecurityInput in) {
        Map<String, Object> o = lock(id);
        status(o, "original_offeror");
        LocalDate date = past("date", in.date());
        LocalDate due = date(o, "certification_date").plusDays(SECURITY_DAYS);
        if (date.isAfter(due)) throw ApiException.validation("date", "The bid security was due by " + due + " (IRR 30.6).");
        if (in.form() == null || !BiddingController.SECURITY_RATES.containsKey(in.form())) throw ApiException.validation("form", "Choose the form of bid security.");
        BigDecimal need = required(in.form(), (BigDecimal) o.get("negotiated_amount"));
        if (need.signum() > 0 && (in.amount() == null || in.amount().compareTo(need) < 0)) throw ApiException.validation("amount", "This form needs at least ₱" + ProcurementRules.peso(need) + ".");
        jdbc.update("update unsolicited_offers set bid_security_form = ?, bid_security_amount = ?, bid_security_date = ?, updated_at = ? where id = ?", in.form(), in.amount(), Date.valueOf(date), stamp(), id);
        record(id, o, "Bid security recorded: " + in.form());
        return view(row(id));
    }

    public record LinkInput(Long biddingId) {
    }

    /** The bidding that invites comparative bids; a failed or cancelled one can be replaced. */
    @PostMapping("/{id}/link-bidding")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView linkBidding(@PathVariable Long id, @RequestBody LinkInput in) {
        Map<String, Object> o = lock(id);
        String status = (String) o.get("status");
        if (!"original_offeror".equals(status) && !"comparative_bidding".equals(status)) throw ApiException.of(HttpStatus.CONFLICT, "A bidding is linked once the offeror holds original offeror status.");
        if (o.get("bid_security_date") == null) throw ApiException.of(HttpStatus.CONFLICT, "Record the original offeror's bid security first.");
        if (o.get("bidding_id") != null) {
            String was = jdbc.queryForObject("select status from biddings where id = ?", String.class, o.get("bidding_id"));
            if (!"failed".equals(was) && !"cancelled".equals(was)) throw ApiException.of(HttpStatus.CONFLICT, "This offer already has a bidding under way.");
        }
        if (in.biddingId() == null) throw ApiException.validation("bidding_id", "Choose the bidding.");
        List<Map<String, Object>> rows = jdbc.queryForList("select bidding_number, status, evaluation_method from biddings where id = ? and deleted_at is null", in.biddingId());
        if (rows.isEmpty()) throw ApiException.validation("bidding_id", "That bidding does not exist.");
        Map<String, Object> b = rows.get(0);
        if (!"draft".equals(b.get("status")) && !"pre_procurement".equals(b.get("status"))) throw ApiException.validation("bidding_id", "Link the bidding before its Invitation to Bid is posted.");
        if (!"price".equals(b.get("evaluation_method"))) throw ApiException.validation("bidding_id", "Comparative bids go to the lowest price (IRR 30.7); set the bidding to lowest price.");
        Integer used = jdbc.queryForObject("select count(*) from unsolicited_offers where bidding_id = ? and deleted_at is null and id <> ?", Integer.class, in.biddingId(), id);
        if (used != null && used > 0) throw ApiException.validation("bidding_id", "Another offer is using that bidding.");
        jdbc.update("update unsolicited_offers set status = 'comparative_bidding', bidding_id = ?, updated_at = ? where id = ?", in.biddingId(), stamp(), id);
        record(id, o, "Comparative bids invited through " + b.get("bidding_number"));
        return view(row(id));
    }

    /** Takes the result of the bidding: the lowest responsive bid to match, or no comparative bid at all (IRR 30.7, 30.8). */
    @PostMapping("/{id}/start-matching")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView startMatching(@PathVariable Long id) {
        Map<String, Object> o = lock(id);
        status(o, "comparative_bidding");
        BiddingController.Comparative c = biddings.comparative(((Number) o.get("bidding_id")).longValue());
        if (c.none()) {
            jdbc.update("update unsolicited_offers set status = 'bid_matching', match_result = 'no_comparative', updated_at = ? where id = ?", stamp(), id);
            record(id, o, "No comparative bid received or none passed the opening; the award goes to the original offeror (IRR 30.8)");
            return view(row(id));
        }
        if (c.supplierId() == null) throw ApiException.of(HttpStatus.CONFLICT, "No bid has passed post-qualification yet, so there is no lowest bid to match.");
        LocalDate today = LocalDate.now(Times.ZONE);
        jdbc.update("update unsolicited_offers set status = 'bid_matching', lccrb_date = ?, lccrb_supplier_id = ?, lccrb_amount = ?, updated_at = ? where id = ?",
                Date.valueOf(today), c.supplierId(), c.amount(), stamp(), id);
        record(id, o, "Lowest responsive comparative bid: " + awards.supplierName(c.supplierId()) + " at ₱" + ProcurementRules.peso(c.amount()) + "; " + MATCH_DAYS + " days to match");
        return view(row(id));
    }

    public record MatchInput(Boolean matched, BigDecimal amount, LocalDate date) {
    }

    /** The original offeror's answer, within 15 days; matching or beating the lowest bid wins the award (IRR 30.8). */
    @PostMapping("/{id}/match")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView match(@PathVariable Long id, @RequestBody MatchInput in) {
        Map<String, Object> o = lock(id);
        status(o, "bid_matching");
        if (o.get("match_result") != null) throw ApiException.of(HttpStatus.CONFLICT, "The result of bid matching is already recorded.");
        LocalDate date = past("date", in.date());
        LocalDate due = date(o, "lccrb_date").plusDays(MATCH_DAYS);
        BigDecimal lowest = (BigDecimal) o.get("lccrb_amount");
        if (!Boolean.TRUE.equals(in.matched())) {
            jdbc.update("update unsolicited_offers set match_result = 'not_matched', match_date = ?, updated_at = ? where id = ?", Date.valueOf(date), stamp(), id);
            record(id, o, "Original offeror did not match; the award goes to the lowest responsive bid");
            return view(row(id));
        }
        if (date.isAfter(due)) throw ApiException.validation("date", "The 15 days to match ended on " + due + " (IRR 30.8); record that it did not match.");
        if (in.amount() == null || in.amount().signum() <= 0) throw ApiException.validation("amount", "Enter the matching price.");
        if (in.amount().compareTo(lowest) > 0) throw ApiException.validation("amount", "To win the award the price must equal or beat ₱" + ProcurementRules.peso(lowest) + ".");
        jdbc.update("update unsolicited_offers set match_result = 'matched', match_amount = ?, match_date = ?, updated_at = ? where id = ?", in.amount(), Date.valueOf(date), stamp(), id);
        record(id, o, "Original offeror matched at ₱" + ProcurementRules.peso(in.amount()));
        return view(row(id));
    }

    public record CloseInput(String reason, LocalDate date) {
    }

    /** Rejects the offer: for instance a later offer of the same concept, which the first in time prevails over (IRR 30.4). */
    @PostMapping("/{id}/reject")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView reject(@PathVariable Long id, @RequestBody CloseInput in) {
        Map<String, Object> o = lock(id);
        if (!Set.of("received", "awaiting_documents", "evaluation", "negotiation").contains(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Only an offer not yet certified can be rejected.");
        String why = text(in.reason());
        if (why == null) throw ApiException.validation("reason", "State why the offer is rejected.");
        return close(id, "rejected", why, past("date", in.date()));
    }

    /**
     * The offeror withdraws. Within 5 days of the notice it costs nothing; later it bars the offeror from the agency's
     * procurement for 1 year, then 2 years, then for good (IRR 30.3).
     */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize(MANAGE)
    @Transactional
    public OfferView withdraw(@PathVariable Long id, @RequestBody CloseInput in) {
        Map<String, Object> o = lock(id);
        if (!OPEN.contains(o.get("status")) || o.get("match_result") != null) throw ApiException.of(HttpStatus.CONFLICT, "This offer can no longer be withdrawn.");
        LocalDate date = past("date", in.date());
        LocalDate notice = o.get("notice_date") == null ? null : date(o, "notice_date");
        boolean late = notice != null && date.isAfter(notice.plusDays(WITHDRAW_DAYS));
        String why = text(in.reason());
        if (!late) return close(id, "withdrawn", why == null ? "Withdrawn by the offeror." : why, date);
        Long supplierId = ((Number) o.get("supplier_id")).longValue();
        Integer earlier = jdbc.queryForObject("select count(*) from unsolicited_offers where supplier_id = ? and late_withdrawal = " + db.t() + " and id <> ?", Integer.class, supplierId, id);
        int offense = (earlier == null ? 0 : earlier) + 1;
        LocalDate until = offense == 1 ? date.plusYears(1) : offense == 2 ? date.plusYears(2) : null;
        String grounds = "Withdrew unsolicited offer " + o.get("offer_number") + " after the 5-day period (IRR 30.3), offense " + offense;
        Timestamp now = stamp();
        jdbc.update("""
                insert into supplier_blacklists (supplier_id, sanction_type, grounds, reference_number, start_date, end_date, status, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'active', ?, ?, ?)""", supplierId, until == null ? "Blacklist" : "Suspension", grounds, o.get("offer_number"), Date.valueOf(date),
                until == null ? null : Date.valueOf(until), current.user().getId(), now, now);
        jdbc.update("update suppliers set is_eligible = " + db.f() + ", updated_at = ? where id = ?", now, supplierId);
        jdbc.update("update unsolicited_offers set late_withdrawal = " + db.t() + " where id = ?", id);
        return close(id, "withdrawn", (why == null ? "" : why + " ") + "Late withdrawal: barred " + (until == null ? "permanently" : "until " + until) + ".", date);
    }

    private OfferView close(Long id, String status, String reason, LocalDate date) {
        Map<String, Object> o = row(id);
        jdbc.update("update unsolicited_offers set status = ?, closing_reason = ?, closed_date = ?, updated_at = ? where id = ?", status, reason, Date.valueOf(date), stamp(), id);
        audit.record("updated", TYPE, id, o.get("offer_number") + " " + status.replace('_', ' ') + ": " + reason, Map.of("status", o.get("status")), Map.of("status", status));
        return view(row(id));
    }

    private void record(Long id, Map<String, Object> before, String what) {
        audit.record("updated", TYPE, id, before.get("offer_number") + ": " + what, Map.of("status", before.get("status")), null);
    }

    private static void status(Map<String, Object> o, String wanted) {
        if (!wanted.equals(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "This step does not apply to an offer that is " + String.valueOf(o.get("status")).replace('_', ' ') + ".");
    }

    private static BigDecimal required(String form, BigDecimal amount) {
        return amount.multiply(BiddingController.SECURITY_RATES.get(form)).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> row(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from unsolicited_offers where id = ? and deleted_at is null", id);
        if (rows.isEmpty()) throw ApiException.notFound("Unsolicited offer");
        return rows.get(0);
    }

    private Map<String, Object> lock(Long id) {
        List<Map<String, Object>> rows = db.lockRows("unsolicited_offers", "id = ? and deleted_at is null", id);
        if (rows.isEmpty()) throw ApiException.notFound("Unsolicited offer");
        return rows.get(0);
    }

    private OfferView view(Map<String, Object> o) {
        Long supplierId = ((Number) o.get("supplier_id")).longValue();
        Long biddingId = o.get("bidding_id") == null ? null : ((Number) o.get("bidding_id")).longValue();
        Map<String, Object> bidding = biddingId == null ? null : jdbc.queryForMap("select bidding_number, status, itb_posted_at from biddings where id = ?", biddingId);
        Long lccrb = o.get("lccrb_supplier_id") == null ? null : ((Number) o.get("lccrb_supplier_id")).longValue();
        String form = (String) o.get("bid_security_form");
        BigDecimal negotiated = (BigDecimal) o.get("negotiated_amount");
        Object[] due = due(o, bidding);
        LocalDate dueDate = (LocalDate) due[0];
        return new OfferView(((Number) o.get("id")).longValue(), (String) o.get("offer_number"), new Ref(supplierId, awards.supplierName(supplierId)), (String) o.get("title"), (String) o.get("concept"),
                (String) o.get("category"), (BigDecimal) o.get("proposed_amount"), date(o, "received_date"), (String) o.get("status"), date(o, "notice_date"), date(o, "documents_date"),
                (String) o.get("new_concept_basis"), o.get("evaluation_extension_days") == null ? null : ((Number) o.get("evaluation_extension_days")).intValue(), (String) o.get("evaluation_extension_reason"),
                date(o, "evaluation_date"), date(o, "negotiation_start"), date(o, "negotiation_end"), negotiated, (String) o.get("negotiated_terms"), (String) o.get("certification_number"),
                date(o, "certification_date"), form, (BigDecimal) o.get("bid_security_amount"), date(o, "bid_security_date"), form == null || negotiated == null ? null : required(form, negotiated),
                biddingId == null ? null : new Ref(biddingId, (String) bidding.get("bidding_number")), bidding == null ? null : (String) bidding.get("status"), date(o, "lccrb_date"),
                lccrb == null ? null : new Ref(lccrb, awards.supplierName(lccrb)), (BigDecimal) o.get("lccrb_amount"), (String) o.get("match_result"), (BigDecimal) o.get("match_amount"), date(o, "match_date"),
                (String) o.get("closing_reason"), date(o, "closed_date"), Boolean.TRUE.equals(o.get("late_withdrawal")), dueDate, (String) due[1],
                dueDate != null && dueDate.isBefore(LocalDate.now(Times.ZONE)), current.any("manage_bidding"));
    }

    /** The next deadline of the offer, and what it is for. */
    private Object[] due(Map<String, Object> o, Map<String, Object> bidding) {
        return switch ((String) o.get("status")) {
            case "received" -> new Object[]{date(o, "received_date").plusDays(ASSESS_DAYS), "Assess the need and notify the offeror"};
            case "awaiting_documents" -> new Object[]{date(o, "notice_date").plusDays(DOCUMENT_DAYS), "Complete documents from the offeror"};
            case "evaluation" -> new Object[]{date(o, "documents_date").plusDays(EVALUATION_DAYS), "Finish the evaluation"};
            case "negotiation" -> new Object[]{date(o, "negotiation_start").plusDays(NEGOTIATION_DAYS), "Finish the negotiation"};
            case "original_offeror" -> o.get("bid_security_date") == null
                    ? new Object[]{date(o, "certification_date").plusDays(SECURITY_DAYS), "Bid security from the original offeror"}
                    : new Object[]{date(o, "certification_date").plusDays(INVITATION_DAYS), "Publish the Invitation to Bid"};
            case "comparative_bidding" -> bidding != null && bidding.get("itb_posted_at") == null
                    ? new Object[]{date(o, "certification_date").plusDays(INVITATION_DAYS), "Publish the Invitation to Bid"}
                    : new Object[]{null, null};
            case "bid_matching" -> o.get("match_result") == null ? new Object[]{date(o, "lccrb_date").plusDays(MATCH_DAYS), "The original offeror's answer to the lowest bid"} : new Object[]{null, "Award on the bidding"};
            default -> new Object[]{null, null};
        };
    }

    private static LocalDate date(Map<String, Object> o, String key) {
        Object v = o.get(key);
        return v == null ? null : v instanceof Date d ? d.toLocalDate() : v instanceof LocalDate l ? l : ((Timestamp) v).toLocalDateTime().toLocalDate();
    }

    private static LocalDate past(String field, LocalDate given) {
        LocalDate today = LocalDate.now(Times.ZONE);
        LocalDate date = given == null ? today : given;
        if (date.isAfter(today)) throw ApiException.validation(field, "The date cannot be in the future.");
        return date;
    }

    private static Timestamp stamp() {
        return Timestamp.valueOf(Times.now());
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

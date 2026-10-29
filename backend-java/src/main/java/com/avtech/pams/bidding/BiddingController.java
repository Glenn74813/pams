package com.avtech.pams.bidding;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.bac.BacResolution;
import com.avtech.pams.bac.BacResolutionRepository;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.procurement.AwardSupport;
import com.avtech.pams.procurement.PrLineUsage;
import com.avtech.pams.procurement.ProcurementRules;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Competitive (public) bidding under RA 12009: pre-procurement conference, Invitation to Bid, bid
 * opening after the submission deadline, then evaluation stage by stage — eligibility, technical,
 * financial — ranking of the responsive bids (lowest price, or MEARB rated score), and
 * post-qualification of the top-ranked bidder, moving down the ranking only when it fails. The
 * award goes to the bidder that passes post-qualification, on an approved BAC resolution and with
 * no protest pending.
 */
@RestController
@RequestMapping("/api/bidding")
public class BiddingController {

    private static final String TYPE = "App\\Models\\Bidding";
    private static final String READ = "@perm.any('view_bidding', 'manage_bidding', 'process_award', 'conduct_bid_evaluation')";
    private static final String EVALUATE = "@perm.any('manage_bidding', 'conduct_bid_evaluation')";
    /** ABC above which a pre-procurement conference must be held. */
    private static final BigDecimal PRE_PROC_THRESHOLD = new BigDecimal("2000000");
    /** Bid security as a share of the ABC, by form (a Bid Securing Declaration needs no amount). */
    static final Map<String, BigDecimal> SECURITY_RATES = new LinkedHashMap<>();

    static {
        SECURITY_RATES.put("cash", new BigDecimal("0.02"));
        SECURITY_RATES.put("cashiers_check", new BigDecimal("0.02"));
        SECURITY_RATES.put("managers_check", new BigDecimal("0.02"));
        SECURITY_RATES.put("bank_draft", new BigDecimal("0.02"));
        SECURITY_RATES.put("irrevocable_letter_of_credit", new BigDecimal("0.02"));
        SECURITY_RATES.put("surety_bond", new BigDecimal("0.05"));
        SECURITY_RATES.put("bid_securing_declaration", BigDecimal.ZERO);
    }

    private final BiddingRepository biddings;
    private final BacResolutionRepository resolutions;
    private final UserRepository users;
    private final PrLineUsage usage;
    private final AwardSupport awards;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;
    private final Db db;

    public BiddingController(BiddingRepository biddings, BacResolutionRepository resolutions, UserRepository users,
                             PrLineUsage usage, AwardSupport awards, DocumentNumbers numbers, NotificationService notifications, AuditService audit,
                             CurrentUser current, JdbcTemplate jdbc, Db db) {
        this.biddings = biddings;
        this.resolutions = resolutions;
        this.users = users;
        this.usage = usage;
        this.awards = awards;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
        this.db = db;
    }

    // ───────────────────────── views ─────────────────────────

    public record Ref(Long id, String name) {
    }

    public record LineView(Long id, Long purchaseRequestItemId, String generalDescription, String specifications, String unit, int quantity,
                           BigDecimal abcUnitCost, BigDecimal abcTotal) {
    }

    public record CriterionView(Long id, String name, BigDecimal weight, boolean isEnvironmental) {
    }

    public record BidderView(Long id, Ref supplier, boolean supplierEligible, BigDecimal bidAmount, boolean overAbc, String bidSecurityForm,
                             BigDecimal bidSecurityAmount, boolean bidSecurityPosted, BigDecimal bidSecurityRequired, boolean bidSecurityOk,
                             Map<String, String> results, Map<Long, BigDecimal> scores, BigDecimal ratedScore, BigDecimal mearbScore,
                             Integer computedRank, boolean postQualTurn, boolean isWinner, String remarks) {
    }

    public record ProtestView(Long id, String protestType, Ref supplier, String filedBy, String grounds, LocalDate filedDate, BigDecimal protestFee,
                              String status, String resolution, LocalDate resolvedDate) {
    }

    public record ObserverView(Long id, String name, String organization, String observerType, boolean invited, LocalDate invitedDate,
                               boolean attended, String remarks) {
    }

    public record Interval(String key, String label, LocalDate from, LocalDate to, int maxDays, Integer elapsedDays, boolean breached, boolean pending) {
    }

    public record BiddingView(Long id, String biddingNumber, String title, String status, BigDecimal abc, Ref purchaseRequest, Ref department,
                              Ref preparer, String deliveryTerm, Integer deliveryLeadDays, String evaluationMethod, BigDecimal priceWeight,
                              BigDecimal technicalWeight, OffsetDateTime preProcDate, String preProcNotes, boolean preProcRequired,
                              String itbReference, LocalDate itbPostedAt, String itbPublication, OffsetDateTime submissionDeadline,
                              boolean deadlinePassed, OffsetDateTime bidOpeningDate, Ref awardedSupplier, BigDecimal awardedAmount,
                              OffsetDateTime awardedAt, Ref bacResolution, String noaNumber, LocalDate noaDate, String ntpNumber, LocalDate ntpDate,
                              String contractRecommendation, String failureReason, int failedCount, String remarks, int biddersCount,
                              List<LineView> items, List<CriterionView> criteria, List<BidderView> bidders, List<ProtestView> protests,
                              List<ObserverView> observers, Long recommendedBidderId, Long nextPostQualBidderId, int openProtests,
                              int observersInvited, List<Interval> timeline, boolean canEdit, boolean canSchedule, boolean canOpen,
                              boolean canRecordBids, boolean canEvaluate, boolean canAward, boolean canNoa, boolean canNtp, boolean canFail,
                              boolean canCancel, boolean canDelete, OffsetDateTime createdAt) {
    }

    // ───────────────────────── reads ─────────────────────────

    @GetMapping
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<BiddingView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                           @RequestParam(name = "purchase_request_id", required = false) Long pr,
                                           @RequestParam(required = false) String search) {
        Page<Bidding> result = biddings.search(blank(status), pr, blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<Bidding, BiddingView> v = views(result.getContent(), false);
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public BiddingView show(@PathVariable Long id) {
        Bidding b = biddings.findLive(id).orElseThrow(() -> ApiException.notFound("Bidding"));
        return views(List.of(b), true).get(b);
    }

    public record PrLineOption(Long id, String generalDescription, String specifications, String unit, int quantity, int remainingQuantity,
                               BigDecimal estimatedUnitCost, String procurementMode) {
    }

    public record PrOption(Long id, String prNumber, String purpose, String department, List<PrLineOption> lines, int failedBiddings) {
    }

    /** Approved purchase requests with lines planned for competitive bidding that are not yet out for procurement. */
    @GetMapping("/purchase-requests")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional(readOnly = true)
    public Map<String, Object> prOptions() {
        Map<Long, Integer> taken = usage.taken(null, null, null);
        Map<Long, Integer> failed = new HashMap<>();
        jdbc.query("select purchase_request_id, count(*) from biddings where status = 'failed' and deleted_at is null and purchase_request_id is not null group by purchase_request_id",
                rs -> {
                    failed.put(rs.getLong(1), rs.getInt(2));
                });
        Map<Long, PrOption> byPr = new LinkedHashMap<>();
        jdbc.query("""
                select pr.id, pr.pr_number, pr.purpose, d.name,
                       pri.id, pri.general_description, pri.specifications, pri.unit, pri.quantity, pri.estimated_unit_cost, pri.procurement_mode
                from purchase_requests pr join departments d on d.id = pr.department_id
                join purchase_request_items pri on pri.purchase_request_id = pr.id
                where pr.status = 'approved' and pr.deleted_at is null and (pri.procurement_mode is null or pri.procurement_mode = 'Competitive Bidding')
                order by pr.id desc, pri.id""", rs -> {
            int left = rs.getInt(9) - taken.getOrDefault(rs.getLong(5), 0);
            if (left <= 0) return;
            long prId = rs.getLong(1);
            PrOption o = byPr.get(prId);
            if (o == null) {
                o = new PrOption(prId, rs.getString(2), rs.getString(3), rs.getString(4), new ArrayList<>(), failed.getOrDefault(prId, 0));
                byPr.put(prId, o);
            }
            o.lines().add(new PrLineOption(rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getInt(9), left, rs.getBigDecimal(10), rs.getString(11)));
        });
        return Map.of("data", byPr.values(), "security_forms", SECURITY_RATES.keySet(), "protest_types", BidProtest.TYPES,
                "protest_statuses", BidProtest.STATUSES, "observer_types", BidObserver.TYPES);
    }

    // ───────────────────────── planning ─────────────────────────

    public record ItemInput(Long purchaseRequestItemId, Integer quantity, BigDecimal abcUnitCost, String specifications) {
    }

    public record BiddingInput(String title, Long purchaseRequestId, String deliveryTerm, Integer deliveryLeadDays, String evaluationMethod,
                               BigDecimal priceWeight, BigDecimal technicalWeight, String remarks, List<ItemInput> items) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public ResponseEntity<BiddingView> store(@RequestBody BiddingInput in) {
        Bidding b = new Bidding(numbers.yearly("PB", "biddings.bidding_number"), current.user().getId());
        apply(b, in, true);
        biddings.saveAndFlush(b);
        audit.record("created", TYPE, b.getId(), "Drafted bidding " + b.getBiddingNumber(), null, snapshot(b));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(b));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView update(@PathVariable Long id, @RequestBody BiddingInput in) {
        Bidding b = lock(id);
        if (!b.isPlanning()) throw ApiException.of(HttpStatus.CONFLICT, "The terms are fixed once the Invitation to Bid is posted.");
        if (in.purchaseRequestId() != null && !in.purchaseRequestId().equals(b.getPurchaseRequestId())) {
            throw ApiException.validation("purchase_request_id", "A bidding stays with its purchase request; start a new one instead.");
        }
        Map<String, Object> before = snapshot(b);
        apply(b, new BiddingInput(in.title(), b.getPurchaseRequestId(), in.deliveryTerm(), in.deliveryLeadDays(), in.evaluationMethod(),
                in.priceWeight(), in.technicalWeight(), in.remarks(), in.items()), true);
        biddings.flush();
        audit.record("updated", TYPE, b.getId(), "Edited bidding " + b.getBiddingNumber(), before, snapshot(b));
        return view(b);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        Bidding b = lock(id);
        if (!Bidding.DRAFT.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft bidding can be deleted; cancel it instead.");
        b.setDeletedAt(Times.now());
        audit.record("deleted", TYPE, b.getId(), "Deleted bidding " + b.getBiddingNumber(), snapshot(b), null);
        return Map.of("message", b.getBiddingNumber() + " deleted.");
    }

    public record CriterionInput(String name, BigDecimal weight, Boolean isEnvironmental) {
    }

    public record CriteriaInput(List<CriterionInput> criteria) {
    }

    /** Rated criteria for MEARB; their weights add up to 100. Fixed once the Invitation to Bid is out. */
    @PutMapping("/{id}/criteria")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView setCriteria(@PathVariable Long id, @RequestBody CriteriaInput in) {
        Bidding b = lock(id);
        if (!b.isPlanning()) throw ApiException.of(HttpStatus.CONFLICT, "Criteria are fixed once the Invitation to Bid is posted.");
        if (!b.isMearb()) throw ApiException.validation("criteria", "Rated criteria apply to MEARB evaluation only.");
        List<CriterionInput> list = in == null || in.criteria() == null ? List.of() : in.criteria();
        BigDecimal sum = BigDecimal.ZERO;
        Set<String> names = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CriterionInput c = list.get(i);
            String name = c == null ? null : text(c.name());
            if (name == null || name.length() > 255) throw ApiException.validation("criteria." + i + ".name", "Criterion " + (i + 1) + " needs a name.");
            if (!names.add(name.toLowerCase())) throw ApiException.validation("criteria." + i + ".name", "\"" + name + "\" is listed twice.");
            if (c.weight() == null || c.weight().signum() <= 0 || c.weight().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw ApiException.validation("criteria." + i + ".weight", "\"" + name + "\" needs a weight between 0 and 100.");
            }
            sum = sum.add(c.weight());
        }
        if (!list.isEmpty() && sum.compareTo(BigDecimal.valueOf(100)) != 0) throw ApiException.validation("criteria", "The weights add up to " + sum.stripTrailingZeros().toPlainString() + "; they must total 100.");
        b.getCriteria().clear();
        biddings.flush();
        for (int i = 0; i < list.size(); i++) {
            CriterionInput c = list.get(i);
            b.getCriteria().add(new BiddingCriterion(b, text(c.name()), c.weight(), Boolean.TRUE.equals(c.isEnvironmental()), i));
        }
        biddings.flush();
        audit.record("updated", TYPE, b.getId(), "Set " + list.size() + " MEARB criteria for " + b.getBiddingNumber(), null, Map.of("criteria", list.size()));
        return view(b);
    }

    public record PreProcInput(LocalDateTime preProcDate, String preProcNotes) {
    }

    @PostMapping("/{id}/pre-proc")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView setPreProc(@PathVariable Long id, @RequestBody PreProcInput in) {
        Bidding b = lock(id);
        if (!b.isPlanning()) throw ApiException.of(HttpStatus.CONFLICT, "The pre-procurement conference comes before the Invitation to Bid.");
        if (in == null || in.preProcDate() == null) throw ApiException.validation("pre_proc_date", "Set the date of the pre-procurement conference.");
        b.setPreProcDate(in.preProcDate());
        b.setPreProcNotes(text(in.preProcNotes()));
        b.setStatus(Bidding.PRE_PROCUREMENT);
        biddings.flush();
        audit.record("pre_proc_scheduled", TYPE, b.getId(), "Pre-procurement conference set for " + b.getBiddingNumber() + " on " + in.preProcDate().toLocalDate(),
                null, Map.of("pre_proc_date", in.preProcDate().toString()));
        return view(b);
    }

    public record InvitationInput(String itbReference, LocalDate itbPostedAt, String itbPublication, LocalDateTime submissionDeadline, LocalDateTime bidOpeningDate) {
    }

    @PostMapping("/{id}/invitation")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView setInvitation(@PathVariable Long id, @RequestBody InvitationInput in) {
        Bidding b = lock(id);
        if (!b.isPlanning()) throw ApiException.of(HttpStatus.CONFLICT, "The Invitation to Bid has already been posted.");
        if (b.getAbc().compareTo(PRE_PROC_THRESHOLD) > 0 && b.getPreProcDate() == null) {
            throw ApiException.of(HttpStatus.CONFLICT, "The ABC is over ₱" + ProcurementRules.peso(PRE_PROC_THRESHOLD) + "; hold the pre-procurement conference first.");
        }
        if (b.isMearb()) {
            BigDecimal sum = b.getCriteria().stream().map(BiddingCriterion::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (b.getCriteria().isEmpty() || sum.compareTo(BigDecimal.valueOf(100)) != 0) {
                throw ApiException.of(HttpStatus.CONFLICT, "Set the MEARB rated criteria (weights totalling 100) before the Invitation to Bid.");
            }
        }
        LocalDate today = LocalDate.now(Times.ZONE);
        LocalDate posted = in.itbPostedAt() == null ? today : in.itbPostedAt();
        if (posted.isAfter(today)) throw ApiException.validation("itb_posted_at", "Record the posting once it has happened.");
        if (in.submissionDeadline() == null) throw ApiException.validation("submission_deadline", "Set the deadline for bids.");
        if (!in.submissionDeadline().toLocalDate().isAfter(posted)) throw ApiException.validation("submission_deadline", "The deadline has to come after the Invitation is posted.");
        if (!in.submissionDeadline().isAfter(Times.now())) throw ApiException.validation("submission_deadline", "The deadline for bids has to be in the future.");
        LocalDateTime opening = in.bidOpeningDate() == null ? in.submissionDeadline() : in.bidOpeningDate();
        if (opening.isBefore(in.submissionDeadline())) throw ApiException.validation("bid_opening_date", "Bids are opened at or after the submission deadline.");
        b.setItbReference(text(in.itbReference()));
        b.setItbPostedAt(posted);
        b.setItbPublication(text(in.itbPublication()));
        b.setSubmissionDeadline(in.submissionDeadline());
        b.setBidOpeningDate(opening);
        b.setStatus(Bidding.INVITATION);
        biddings.flush();
        audit.record("invitation_posted", TYPE, b.getId(), "Invitation to Bid posted for " + b.getBiddingNumber() + "; bids due " + in.submissionDeadline(),
                null, Map.of("submission_deadline", in.submissionDeadline().toString()));
        notifications.notifyPermission(List.of("view_bac", "manage_bidding", "conduct_bid_evaluation"), current.user().getId(), "bidding_invitation",
                "Invitation to Bid posted", b.getBiddingNumber() + " (" + b.getTitle() + "): bids due " + in.submissionDeadline().toLocalDate() + ".", TYPE, b.getId());
        return view(b);
    }

    /** Opens the bids once the submission deadline has passed. */
    @PostMapping("/{id}/open-bids")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView openBids(@PathVariable Long id) {
        Bidding b = lock(id);
        if (!Bidding.INVITATION.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Bids are opened after the Invitation to Bid.");
        if (b.getSubmissionDeadline().isAfter(Times.now())) {
            throw ApiException.of(HttpStatus.CONFLICT, "Bids are still being received until " + b.getSubmissionDeadline().toLocalDate() + " " + b.getSubmissionDeadline().toLocalTime() + ".");
        }
        b.setStatus(Bidding.BID_OPENING);
        biddings.flush();
        audit.record("bid_opening", TYPE, b.getId(), "Bids opened for " + b.getBiddingNumber(), Map.of("status", "invitation"), Map.of("status", "bid_opening"));
        return view(b);
    }

    // ───────────────────────── bids and evaluation ─────────────────────────

    public record BidderInput(Long supplierId, String supplierName, BigDecimal bidAmount, BigDecimal bidSecurityAmount, String bidSecurityForm,
                              Boolean bidSecurityPosted, String remarks) {
    }

    @PostMapping("/{id}/bidders")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public ResponseEntity<BiddingView> addBidder(@PathVariable Long id, @RequestBody BidderInput in) {
        Bidding b = lock(id);
        if (!Bidding.BID_OPENING.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Bids are recorded at the bid opening.");
        Long supplierId = awards.supplier(in.supplierId(), in.supplierName());
        if (b.getBidders().stream().anyMatch(x -> x.getSupplierId().equals(supplierId))) throw ApiException.validation("supplier_id", "This supplier has already bid.");
        BiddingBidder x = new BiddingBidder(b, supplierId);
        applyBid(b, x, in);
        b.getBidders().add(x);
        biddings.flush();
        audit.record("updated", TYPE, b.getId(), "Bid of " + awards.supplierName(supplierId) + " recorded for " + b.getBiddingNumber()
                + (x.getBidAmount() == null ? "" : " (₱" + ProcurementRules.peso(x.getBidAmount()) + ")"), null, Map.of("supplier_id", supplierId));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(b));
    }

    public record EvaluationInput(BigDecimal bidAmount, BigDecimal bidSecurityAmount, String bidSecurityForm, Boolean bidSecurityPosted,
                                  String stage, String result, String remarks, Map<String, Object> eligibilityChecklist) {
    }

    /**
     * Corrects a bid as read at opening, or records one evaluation stage. Stages run in order; a
     * bidder failed at one stage goes no further; post-qualification is for the top-ranked bidder
     * only, and passes down the ranking when it fails.
     */
    @PutMapping("/bidders/{bidderId}")
    @PreAuthorize(EVALUATE)
    @Transactional
    public BiddingView updateBidder(@PathVariable Long bidderId, @RequestBody EvaluationInput in) {
        Bidding b = lock(biddings.biddingOfBidder(bidderId).orElseThrow(() -> ApiException.notFound("Bidder")));
        BiddingBidder x = b.getBidders().stream().filter(y -> y.getId().equals(bidderId)).findFirst().orElseThrow();
        if (in.bidAmount() != null || in.bidSecurityForm() != null || in.bidSecurityAmount() != null || in.bidSecurityPosted() != null) {
            if (!Bidding.BID_OPENING.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Bids and bid securities are recorded at the opening, before evaluation starts.");
            applyBid(b, x, new BidderInput(null, null, in.bidAmount() != null ? in.bidAmount() : x.getBidAmount(),
                    in.bidSecurityAmount() != null ? in.bidSecurityAmount() : x.getBidSecurityAmount(),
                    in.bidSecurityForm() != null ? in.bidSecurityForm() : x.getBidSecurityForm(),
                    in.bidSecurityPosted() != null ? in.bidSecurityPosted() : x.isBidSecurityPosted(), in.remarks()));
        }
        if (in.eligibilityChecklist() != null) x.setEligibilityChecklist(in.eligibilityChecklist());
        if (in.stage() != null) evaluate(b, x, in.stage(), in.result(), text(in.remarks()));
        biddings.flush();
        return view(b);
    }

    @DeleteMapping("/bidders/{bidderId}")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView removeBidder(@PathVariable Long bidderId) {
        Bidding b = lock(biddings.biddingOfBidder(bidderId).orElseThrow(() -> ApiException.notFound("Bidder")));
        if (!Bidding.BID_OPENING.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Bids can only be struck out during the opening, before evaluation.");
        BiddingBidder x = b.getBidders().stream().filter(y -> y.getId().equals(bidderId)).findFirst().orElseThrow();
        b.getBidders().remove(x);
        biddings.flush();
        audit.record("updated", TYPE, b.getId(), "Removed the bid of " + awards.supplierName(x.getSupplierId()) + " from " + b.getBiddingNumber(), Map.of("supplier_id", x.getSupplierId()), null);
        return view(b);
    }

    public record ScoreInput(Long biddingCriterionId, BigDecimal score) {
    }

    public record ScoresInput(List<ScoreInput> scores) {
    }

    @PutMapping("/bidders/{bidderId}/scores")
    @PreAuthorize(EVALUATE)
    @Transactional
    public BiddingView scoreBidder(@PathVariable Long bidderId, @RequestBody ScoresInput in) {
        Bidding b = lock(biddings.biddingOfBidder(bidderId).orElseThrow(() -> ApiException.notFound("Bidder")));
        if (!b.isMearb()) throw ApiException.validation("scores", "Rated scores apply to MEARB evaluation only.");
        if (!Bidding.BID_OPENING.equals(b.getStatus()) && !Bidding.EVALUATION.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "Scores are given during evaluation.");
        if (postQualStarted(b)) throw ApiException.of(HttpStatus.CONFLICT, "Post-qualification has started; the rated scores are final.");
        BiddingBidder x = b.getBidders().stream().filter(y -> y.getId().equals(bidderId)).findFirst().orElseThrow();
        Set<Long> criteria = b.getCriteria().stream().map(BiddingCriterion::getId).collect(Collectors.toSet());
        for (ScoreInput s : in == null || in.scores() == null ? List.<ScoreInput>of() : in.scores()) {
            if (s == null || !criteria.contains(s.biddingCriterionId())) throw ApiException.validation("scores", "A score is for a criterion not on this bidding.");
            if (s.score() == null || s.score().signum() < 0 || s.score().compareTo(BigDecimal.valueOf(100)) > 0) throw ApiException.validation("scores", "Scores run from 0 to 100.");
            BiddingBidderScore existing = x.getScores().stream().filter(y -> y.getCriterionId().equals(s.biddingCriterionId())).findFirst().orElse(null);
            if (existing != null) existing.setScore(s.score());
            else x.getScores().add(new BiddingBidderScore(x, s.biddingCriterionId(), s.score()));
        }
        if (Bidding.BID_OPENING.equals(b.getStatus())) b.setStatus(Bidding.EVALUATION);
        biddings.flush();
        return view(b);
    }

    // ───────────────────────── outcome ─────────────────────────

    public record ReasonInput(String reason) {
    }

    @PostMapping("/{id}/declare-failed")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public Map<String, Object> declareFailed(@PathVariable Long id, @RequestBody ReasonInput in) {
        String why = in == null ? null : text(in.reason());
        if (why == null) throw ApiException.validation("failure_reason", "State the ground for declaring a failure of bidding.");
        Bidding b = lock(id);
        if (b.isOver() || b.isPlanning()) throw ApiException.of(HttpStatus.CONFLICT, "Only a bidding that has been advertised can be declared failed.");
        Integer before = jdbc.queryForObject("select count(*) from biddings where purchase_request_id = ? and status = 'failed' and deleted_at is null and id <> ?",
                Integer.class, b.getPurchaseRequestId(), b.getId());
        int count = (before == null ? 0 : before) + 1;
        String was = b.getStatus();
        b.setStatus(Bidding.FAILED);
        b.setFailureReason(why);
        b.setFailedCount(count);
        biddings.flush();
        audit.record("declared_failed", TYPE, b.getId(), b.getBiddingNumber() + " declared a failed bidding (#" + count + " for this PR): " + why, Map.of("status", was), Map.of("status", "failed"));
        String message = count >= 2
                ? "Failed bidding #" + count + " for this purchase request. After two failed biddings, Negotiated Procurement may be used (RFQ / alternative modes)."
                : "Failed bidding #" + count + ". The purchase request lines are free for a re-bid.";
        return Map.of("bidding", view(b), "message", message, "may_negotiate", count >= 2);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView cancel(@PathVariable Long id, @RequestBody(required = false) ReasonInput in) {
        Bidding b = lock(id);
        if (b.isOver()) throw ApiException.of(HttpStatus.CONFLICT, "This bidding can no longer be cancelled.");
        String why = in == null ? null : text(in.reason());
        if (why == null && !Bidding.DRAFT.equals(b.getStatus())) throw ApiException.validation("reason", "Say why the bidding is cancelled.");
        String was = b.getStatus();
        b.setStatus(Bidding.CANCELLED);
        if (why != null) b.setRemarks(why);
        biddings.flush();
        audit.record("cancelled", TYPE, b.getId(), b.getBiddingNumber() + " cancelled" + (why == null ? "" : ": " + why), Map.of("status", was), Map.of("status", "cancelled"));
        return view(b);
    }

    public record AwardInput(Long bidderId, Long bacResolutionId, String contractRecommendation) {
    }

    @PostMapping("/{id}/award")
    @PreAuthorize("@perm.any('process_award')")
    @Transactional
    public BiddingView award(@PathVariable Long id, @RequestBody AwardInput in) {
        Bidding b = lock(id);
        if (!Bidding.EVALUATION.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "A bidding is awarded after evaluation.");
        User me = current.user();
        if (me.getId().equals(b.getPreparedBy()) && !me.isSuperAdmin()) throw ApiException.forbidden("You prepared this bidding, so someone else has to award it.");
        long open = b.getProtests().stream().filter(BidProtest::isOpen).count();
        if (open > 0) throw ApiException.of(HttpStatus.CONFLICT, open + " protest(s) or request(s) for reconsideration are pending; resolve them before the award.");
        Evaluation ev = evaluation(b);
        BiddingBidder x = b.getBidders().stream().filter(y -> y.getId().equals(in.bidderId())).findFirst()
                .orElseThrow(() -> ApiException.validation("bidder_id", "Choose a bidder on this bidding."));
        if (ev.recommended() == null) throw ApiException.of(HttpStatus.CONFLICT, "No bidder has passed post-qualification yet.");
        if (!x.getId().equals(ev.recommended())) {
            throw ApiException.validation("bidder_id", "The award goes to the bidder that passed post-qualification as the "
                    + (b.isMearb() ? "highest-rated" : "lowest calculated") + " responsive bid.");
        }
        if (!awards.eligible(x.getSupplierId())) throw ApiException.validation("bidder_id", awards.supplierName(x.getSupplierId()) + " is inactive or not eligible.");
        BacResolution res = awards.resolution(in.bacResolutionId(), b.getPurchaseRequestId(), null, b.getId());
        b.getBidders().forEach(y -> y.setWinner(false));
        x.setWinner(true);
        ev.ranks().forEach((bidderId, rank) -> b.getBidders().stream().filter(y -> y.getId().equals(bidderId)).findFirst().ifPresent(y -> y.setRank(rank)));
        b.setStatus(Bidding.AWARDED);
        b.setAwardedSupplierId(x.getSupplierId());
        b.setAwardedAmount(x.getBidAmount());
        b.setAwardedAt(Times.now());
        b.setBacResolutionId(res.getId());
        String rec = text(in.contractRecommendation());
        if (rec != null) b.setContractRecommendation(rec);
        biddings.flush();
        audit.record("awarded", TYPE, b.getId(), b.getBiddingNumber() + " awarded to " + awards.supplierName(x.getSupplierId()) + " (₱" + ProcurementRules.peso(x.getBidAmount())
                + ") on " + res.getResolutionNumber(), Map.of("status", "evaluation"), Map.of("status", "awarded", "supplier_id", x.getSupplierId()));
        notifications.notifyPermission(List.of("view_bidding", "manage_bidding", "process_award"), me.getId(), "bidding_award", "Bidding awarded",
                b.getBiddingNumber() + " (" + b.getTitle() + ") was awarded to " + awards.supplierName(x.getSupplierId()) + ". Issue the NOA and NTP.", TYPE, b.getId());
        return view(b);
    }

    public record NoticeInput(LocalDate date) {
    }

    @PostMapping("/{id}/noa")
    @PreAuthorize("@perm.any('process_award')")
    @Transactional
    public BiddingView issueNoa(@PathVariable Long id, @RequestBody(required = false) NoticeInput in) {
        Bidding b = lock(id);
        if (!Bidding.AWARDED.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "The Notice of Award follows the award.");
        if (b.getNoaNumber() != null) throw ApiException.of(HttpStatus.CONFLICT, "The Notice of Award " + b.getNoaNumber() + " was already issued.");
        LocalDate date = in == null || in.date() == null ? LocalDate.now(Times.ZONE) : in.date();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("date", "The date cannot be in the future.");
        if (date.isBefore(b.getAwardedAt().toLocalDate())) throw ApiException.validation("date", "The Notice of Award cannot predate the award.");
        b.setNoaNumber(numbers.yearly("NOA", "rfqs.noa_number", "biddings.noa_number"));
        b.setNoaDate(date);
        biddings.flush();
        audit.record("noa_issued", TYPE, b.getId(), "Notice of Award " + b.getNoaNumber() + " issued for " + b.getBiddingNumber(), null, Map.of("noa_number", b.getNoaNumber()));
        return view(b);
    }

    @PostMapping("/{id}/ntp")
    @PreAuthorize("@perm.any('process_award')")
    @Transactional
    public BiddingView issueNtp(@PathVariable Long id, @RequestBody(required = false) NoticeInput in) {
        Bidding b = lock(id);
        if (b.getNoaNumber() == null) throw ApiException.of(HttpStatus.CONFLICT, "Issue the Notice of Award before the Notice to Proceed.");
        if (b.getNtpNumber() != null) throw ApiException.of(HttpStatus.CONFLICT, "The Notice to Proceed " + b.getNtpNumber() + " was already issued.");
        LocalDate date = in == null || in.date() == null ? LocalDate.now(Times.ZONE) : in.date();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("date", "The date cannot be in the future.");
        if (date.isBefore(b.getNoaDate())) throw ApiException.validation("date", "The Notice to Proceed cannot predate the Notice of Award.");
        b.setNtpNumber(numbers.yearly("NTP", "rfqs.ntp_number", "biddings.ntp_number"));
        b.setNtpDate(date);
        biddings.flush();
        audit.record("ntp_issued", TYPE, b.getId(), "Notice to Proceed " + b.getNtpNumber() + " issued for " + b.getBiddingNumber(), null, Map.of("ntp_number", b.getNtpNumber()));
        return view(b);
    }

    // ───────────────────────── protests and observers ─────────────────────────

    public record ProtestInput(String protestType, Long supplierId, String filedBy, String grounds, LocalDate filedDate, BigDecimal protestFee,
                               String status, String resolution, LocalDate resolvedDate) {
    }

    @PostMapping("/{id}/protests")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public ResponseEntity<BiddingView> storeProtest(@PathVariable Long id, @RequestBody ProtestInput in) {
        Bidding b = lock(id);
        if (b.isPlanning()) throw ApiException.of(HttpStatus.CONFLICT, "Nothing to protest before the bidding is advertised.");
        if (Bidding.CANCELLED.equals(b.getStatus())) throw ApiException.of(HttpStatus.CONFLICT, "This bidding was cancelled.");
        BidProtest p = new BidProtest(b, current.user().getId());
        if (in.protestType() == null || !BidProtest.TYPES.contains(in.protestType())) throw ApiException.validation("protest_type", "Choose a request for reconsideration or a protest.");
        String grounds = text(in.grounds());
        if (grounds == null) throw ApiException.validation("grounds", "State the grounds.");
        LocalDate filed = in.filedDate() == null ? LocalDate.now(Times.ZONE) : in.filedDate();
        if (filed.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("filed_date", "The filing date cannot be in the future.");
        if (in.supplierId() != null && b.getBidders().stream().noneMatch(x -> x.getSupplierId().equals(in.supplierId()))) {
            throw ApiException.validation("supplier_id", "Only a bidder in this bidding can file.");
        }
        if (in.protestFee() != null && in.protestFee().signum() < 0) throw ApiException.validation("protest_fee", "The fee cannot be negative.");
        p.setType(in.protestType());
        p.setSupplierId(in.supplierId());
        p.setFiledBy(text(in.filedBy()));
        p.setGrounds(grounds);
        p.setFiledDate(filed);
        p.setProtestFee(in.protestFee());
        b.getProtests().add(p);
        biddings.flush();
        audit.record("protest_filed", TYPE, b.getId(), in.protestType() + " filed on " + b.getBiddingNumber(), null, Map.of("protest_type", in.protestType()));
        notifications.notifyPermission(List.of("manage_bidding", "process_award"), current.user().getId(), "bidding_protest", in.protestType() + " filed",
                in.protestType() + " filed on " + b.getBiddingNumber() + "; the award waits until it is resolved.", TYPE, b.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(b));
    }

    /** Moves a protest along: under review, then upheld / denied (with the resolution) or withdrawn. */
    @PutMapping("/protests/{protestId}")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView updateProtest(@PathVariable Long protestId, @RequestBody ProtestInput in) {
        Bidding b = lock(biddings.biddingOfProtest(protestId).orElseThrow(() -> ApiException.notFound("Protest")));
        BidProtest p = b.getProtests().stream().filter(y -> y.getId().equals(protestId)).findFirst().orElseThrow();
        if (!p.isOpen()) throw ApiException.of(HttpStatus.CONFLICT, "This " + p.getType().toLowerCase() + " has been " + p.getStatus() + ".");
        String status = in.status() == null ? p.getStatus() : in.status();
        if (!BidProtest.STATUSES.contains(status)) throw ApiException.validation("status", "Unknown status.");
        String resolution = text(in.resolution());
        if (List.of("upheld", "denied").contains(status)) {
            if (resolution == null) throw ApiException.validation("resolution", "Record the BAC's resolution of the " + p.getType().toLowerCase() + ".");
            LocalDate when = in.resolvedDate() == null ? LocalDate.now(Times.ZONE) : in.resolvedDate();
            if (when.isBefore(p.getFiledDate())) throw ApiException.validation("resolved_date", "It cannot be resolved before it was filed.");
            p.setResolvedDate(when);
        }
        if (in.grounds() != null && text(in.grounds()) != null) p.setGrounds(text(in.grounds()));
        if (resolution != null) p.setResolution(resolution);
        String was = p.getStatus();
        p.setStatus(status);
        biddings.flush();
        audit.record("updated", TYPE, b.getId(), p.getType() + " on " + b.getBiddingNumber() + ": " + was + " → " + status, Map.of("status", was), Map.of("status", status));
        return view(b);
    }

    public record ObserverInput(String name, String organization, String observerType, Boolean invited, LocalDate invitedDate, Boolean attended, String remarks) {
    }

    @PostMapping("/{id}/observers")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public ResponseEntity<BiddingView> storeObserver(@PathVariable Long id, @RequestBody ObserverInput in) {
        Bidding b = lock(id);
        if (b.isOver()) throw ApiException.of(HttpStatus.CONFLICT, "This bidding is closed.");
        BidObserver o = new BidObserver(b);
        applyObserver(o, in);
        b.getObservers().add(o);
        biddings.flush();
        return ResponseEntity.status(HttpStatus.CREATED).body(view(b));
    }

    @PutMapping("/observers/{observerId}")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView updateObserver(@PathVariable Long observerId, @RequestBody ObserverInput in) {
        Bidding b = lock(biddings.biddingOfObserver(observerId).orElseThrow(() -> ApiException.notFound("Observer")));
        BidObserver o = b.getObservers().stream().filter(y -> y.getId().equals(observerId)).findFirst().orElseThrow();
        applyObserver(o, in);
        biddings.flush();
        return view(b);
    }

    @DeleteMapping("/observers/{observerId}")
    @PreAuthorize("@perm.any('manage_bidding')")
    @Transactional
    public BiddingView destroyObserver(@PathVariable Long observerId) {
        Bidding b = lock(biddings.biddingOfObserver(observerId).orElseThrow(() -> ApiException.notFound("Observer")));
        if (b.isOver()) throw ApiException.of(HttpStatus.CONFLICT, "This bidding is closed; its observers are part of the record.");
        b.getObservers().removeIf(y -> y.getId().equals(observerId));
        biddings.flush();
        return view(b);
    }

    // ───────────────────────── rules ─────────────────────────

    private record PrLine(Long id, String description, String specifications, String unit, int quantity, BigDecimal unitCost, String mode) {
    }

    private void apply(Bidding b, BiddingInput in, boolean replace) {
        String title = text(in.title());
        if (title == null || title.length() > 255) throw ApiException.validation("title", "Give the bidding a title of up to 255 characters.");
        if (in.purchaseRequestId() == null) throw ApiException.validation("purchase_request_id", "Choose the approved purchase request this bidding is for.");
        List<Map<String, Object>> pr = db.lockRows("purchase_requests", "id = ? and deleted_at is null", in.purchaseRequestId());
        if (pr.isEmpty()) throw ApiException.validation("purchase_request_id", "That purchase request does not exist.");
        if (!"approved".equals(pr.get(0).get("status"))) throw ApiException.validation("purchase_request_id", "Only fully approved purchase requests go to bidding.");
        Map<Long, PrLine> prLines = new HashMap<>();
        jdbc.query("select id, general_description, specifications, unit, quantity, estimated_unit_cost, procurement_mode from purchase_request_items where purchase_request_id = ?",
                rs -> {
                    prLines.put(rs.getLong(1), new PrLine(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBigDecimal(6), rs.getString(7)));
                }, in.purchaseRequestId());
        if (in.items() == null || in.items().isEmpty()) throw ApiException.validation("items", "Add at least one line from the purchase request.");
        Map<Long, Integer> taken = usage.taken(in.purchaseRequestId(), null, b.getId());
        Set<Long> seen = new HashSet<>();
        List<BiddingItem> lines = new ArrayList<>();
        for (int i = 0; i < in.items().size(); i++) {
            ItemInput it = in.items().get(i);
            String f = "items." + i + ".";
            PrLine p = it == null || it.purchaseRequestItemId() == null ? null : prLines.get(it.purchaseRequestItemId());
            if (p == null) throw ApiException.validation(f + "purchase_request_item_id", "Line " + (i + 1) + " is not on the purchase request.");
            if (!seen.add(p.id())) throw ApiException.validation(f + "purchase_request_item_id", "\"" + p.description() + "\" is listed twice.");
            if (p.mode() != null && !"Competitive Bidding".equals(p.mode())) {
                throw ApiException.validation(f + "purchase_request_item_id", "\"" + p.description() + "\" was planned under " + p.mode() + ", not competitive bidding.");
            }
            int left = p.quantity() - taken.getOrDefault(p.id(), 0);
            int qty = it.quantity() == null ? left : it.quantity();
            if (qty < 1) throw ApiException.validation(f + "quantity", "Nothing is left of \"" + p.description() + "\"; it is already on an RFQ or another bidding.");
            if (qty > left) throw ApiException.validation(f + "quantity", "\"" + p.description() + "\": " + qty + " asked, but only " + left + " of " + p.quantity()
                    + " are not already on an RFQ or another bidding.");
            BigDecimal cost = it.abcUnitCost() == null ? p.unitCost() : it.abcUnitCost();
            if (cost.signum() < 0 || cost.scale() > 2) throw ApiException.validation(f + "abc_unit_cost", "Line " + (i + 1) + ": enter the ABC per unit in pesos.");
            if (cost.compareTo(p.unitCost()) > 0) {
                throw ApiException.validation(f + "abc_unit_cost", "\"" + p.description() + "\": the ABC of ₱" + ProcurementRules.peso(cost)
                        + " per unit is above the ₱" + ProcurementRules.peso(p.unitCost()) + " the purchase request was approved for.");
            }
            String spec = text(it.specifications());
            lines.add(new BiddingItem(b, p.id(), p.description(), spec != null ? spec : p.specifications(), p.unit(), qty, cost.setScale(2, RoundingMode.HALF_UP)));
        }
        String method = in.evaluationMethod() == null ? "price" : in.evaluationMethod();
        if (!List.of("price", "mearb").contains(method)) throw ApiException.validation("evaluation_method", "Evaluation is by lowest price or MEARB.");
        BigDecimal pw = BigDecimal.valueOf(100), tw = BigDecimal.ZERO;
        if ("mearb".equals(method)) {
            pw = in.priceWeight() == null ? BigDecimal.valueOf(70) : in.priceWeight();
            tw = in.technicalWeight() == null ? BigDecimal.valueOf(100).subtract(pw) : in.technicalWeight();
            if (pw.signum() < 0 || tw.signum() < 0 || pw.add(tw).compareTo(BigDecimal.valueOf(100)) != 0) {
                throw ApiException.validation("price_weight", "Price and rated weights must add up to 100.");
            }
        }
        if (in.deliveryLeadDays() != null && in.deliveryLeadDays() < 0) throw ApiException.validation("delivery_lead_days", "Delivery days cannot be negative.");
        if (!replace) return;
        if (!method.equals(b.getEvaluationMethod()) && !b.getCriteria().isEmpty() && "price".equals(method)) b.getCriteria().clear();
        b.setTitle(title);
        b.setPurchaseRequestId(in.purchaseRequestId());
        b.setDeliveryTerm(text(in.deliveryTerm()));
        b.setDeliveryLeadDays(in.deliveryLeadDays());
        b.setEvaluationMethod(method);
        b.setPriceWeight(pw);
        b.setTechnicalWeight(tw);
        b.setRemarks(text(in.remarks()));
        b.getItems().clear();
        biddings.flush();
        b.getItems().addAll(lines);
        b.recalc();
    }

    private void applyBid(Bidding b, BiddingBidder x, BidderInput in) {
        if (in.bidAmount() != null && (in.bidAmount().signum() <= 0 || in.bidAmount().scale() > 2)) throw ApiException.validation("bid_amount", "Enter the bid as read, in pesos.");
        String form = text(in.bidSecurityForm());
        if (form != null && !SECURITY_RATES.containsKey(form)) throw ApiException.validation("bid_security_form", "Unknown form of bid security.");
        if (in.bidSecurityAmount() != null && in.bidSecurityAmount().signum() < 0) throw ApiException.validation("bid_security_amount", "The amount cannot be negative.");
        x.setBidAmount(in.bidAmount() == null ? null : in.bidAmount().setScale(2, RoundingMode.HALF_UP));
        x.setBidSecurityForm(form);
        x.setBidSecurityAmount(in.bidSecurityAmount());
        x.setBidSecurityPosted(Boolean.TRUE.equals(in.bidSecurityPosted()));
        if (text(in.remarks()) != null) x.setRemarks(text(in.remarks()));
    }

    private static BigDecimal securityRequired(Bidding b, BiddingBidder x) {
        BigDecimal rate = x.getBidSecurityForm() == null ? null : SECURITY_RATES.get(x.getBidSecurityForm());
        return rate == null ? null : b.getAbc().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean securityOk(Bidding b, BiddingBidder x) {
        BigDecimal need = securityRequired(b, x);
        if (need == null || !x.isBidSecurityPosted()) return false;
        return need.signum() == 0 || (x.getBidSecurityAmount() != null && x.getBidSecurityAmount().compareTo(need) >= 0);
    }

    private static boolean overAbc(Bidding b, BiddingBidder x) {
        return x.getBidAmount() != null && b.getAbc().signum() > 0 && x.getBidAmount().compareTo(b.getAbc()) > 0;
    }

    private boolean postQualStarted(Bidding b) {
        return b.getBidders().stream().anyMatch(x -> !"pending".equals(x.getPostQualResult()));
    }

    private void evaluate(Bidding b, BiddingBidder x, String stage, String result, String remarks) {
        if (!Bidding.BID_OPENING.equals(b.getStatus()) && !Bidding.EVALUATION.equals(b.getStatus())) {
            throw ApiException.of(HttpStatus.CONFLICT, "Bids are evaluated after the opening and before the award.");
        }
        if (!BiddingBidder.STAGES.contains(stage)) throw ApiException.validation("stage", "Unknown evaluation stage.");
        if (result == null || !BiddingBidder.RESULTS.contains(result)) throw ApiException.validation("result", "The result is pending, passed or failed.");
        if ("failed".equals(result) && remarks == null) throw ApiException.validation("remarks", "Record why the bid failed " + stage.replace('_', '-') + ".");
        int at = BiddingBidder.STAGES.indexOf(stage);
        for (int i = 0; i < at; i++) {
            if (!"passed".equals(x.result(BiddingBidder.STAGES.get(i)))) {
                throw ApiException.validation("stage", "The bid has to pass " + BiddingBidder.STAGES.get(i).replace('_', '-') + " first.");
            }
        }
        String name = awards.supplierName(x.getSupplierId());
        if ("post_qualification".equals(stage)) {
            if (b.getBidders().stream().anyMatch(y -> !y.failedAny() && !y.responsive())) {
                throw ApiException.of(HttpStatus.CONFLICT, "Finish the eligibility, technical and financial evaluation of every bid before post-qualification.");
            }
            Evaluation ev = evaluation(b);
            boolean current = x.getId().equals(ev.postQualTurn()) || x.getId().equals(ev.recommended());
            if ("passed".equals(result) && !awards.eligible(x.getSupplierId())) {
                throw ApiException.validation("result", name + " is not eligible: verify its PhilGEPS, permits and tax documents in the supplier register (and check sanctions) first.");
            }
            if (!current && !"pending".equals(result)) {
                throw ApiException.validation("stage", "Post-qualification is for the " + (b.isMearb() ? "highest-rated" : "lowest calculated")
                        + " responsive bidder; it moves to the next only when that one fails.");
            }
        } else {
            if (postQualStarted(b)) throw ApiException.of(HttpStatus.CONFLICT, "Post-qualification has started; the earlier evaluation results are final.");
            if ("eligibility".equals(stage) && "passed".equals(result) && !securityOk(b, x)) {
                throw ApiException.validation("result", name + " has not posted a sufficient bid security (" + (x.getBidSecurityForm() == null
                        ? "none recorded" : x.getBidSecurityForm().replace('_', ' ') + ", ₱" + ProcurementRules.peso(securityRequired(b, x)) + " required") + ").");
            }
            if ("financial".equals(stage) && "passed".equals(result)) {
                if (x.getBidAmount() == null) throw ApiException.validation("result", "Record " + name + "'s bid amount first.");
                if (overAbc(b, x)) throw ApiException.validation("result", "₱" + ProcurementRules.peso(x.getBidAmount()) + " exceeds the ABC of ₱"
                        + ProcurementRules.peso(b.getAbc()) + "; a bid above the ABC is rejected (RA 12009).");
            }
            // A changed result undoes the stages after it.
            if (!result.equals(x.result(stage))) for (int i = at + 1; i < BiddingBidder.STAGES.size(); i++) x.setResult(BiddingBidder.STAGES.get(i), "pending");
        }
        String was = x.result(stage);
        x.setResult(stage, result);
        if (remarks != null) x.setRemarks(remarks);
        if (Bidding.BID_OPENING.equals(b.getStatus())) b.setStatus(Bidding.EVALUATION);
        audit.record("evaluated", TYPE, b.getId(), b.getBiddingNumber() + ": " + name + " " + result + " " + stage.replace('_', '-') + (remarks == null ? "" : " (" + remarks + ")"),
                Map.of(stage, was), Map.of(stage, result));
    }

    /** Ranking of the responsive bids, and who is up for post-qualification or recommended for award. */
    private record Evaluation(Map<Long, Integer> ranks, Map<Long, BigDecimal> rated, Map<Long, BigDecimal> mearb, Long postQualTurn, Long recommended) {
    }

    private Evaluation evaluation(Bidding b) {
        List<BiddingBidder> pool = b.getBidders().stream().filter(x -> x.responsive() && !overAbc(b, x) && x.getBidAmount() != null).toList();
        Map<Long, BigDecimal> rated = new HashMap<>();
        Map<Long, BigDecimal> total = new HashMap<>();
        List<BiddingBidder> ranked;
        if (b.isMearb()) {
            BigDecimal lowest = pool.stream().map(BiddingBidder::getBidAmount).min(BigDecimal::compareTo).orElse(null);
            BigDecimal weights = b.getCriteria().stream().map(BiddingCriterion::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (BiddingBidder x : pool) {
                BigDecimal r = x.getTechnicalScore() == null ? BigDecimal.ZERO : x.getTechnicalScore();
                if (weights.signum() > 0) {
                    Map<Long, BigDecimal> byCrit = x.getScores().stream().collect(Collectors.toMap(BiddingBidderScore::getCriterionId, BiddingBidderScore::getScore));
                    r = b.getCriteria().stream().map(c -> byCrit.getOrDefault(c.getId(), BigDecimal.ZERO).multiply(c.getWeight()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add).divide(weights, 2, RoundingMode.HALF_UP);
                }
                BigDecimal price = lowest == null ? BigDecimal.ZERO : lowest.multiply(BigDecimal.valueOf(100)).divide(x.getBidAmount(), 4, RoundingMode.HALF_UP);
                rated.put(x.getId(), r);
                total.put(x.getId(), price.multiply(b.getPriceWeight()).add(r.multiply(b.getTechnicalWeight())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
            ranked = pool.stream().sorted(Comparator.comparing((BiddingBidder x) -> total.get(x.getId())).reversed().thenComparing(BiddingBidder::getBidAmount)).toList();
        } else {
            ranked = pool.stream().sorted(Comparator.comparing(BiddingBidder::getBidAmount).thenComparing(BiddingBidder::getId)).toList();
        }
        Map<Long, Integer> ranks = new LinkedHashMap<>();
        for (int i = 0; i < ranked.size(); i++) ranks.put(ranked.get(i).getId(), i + 1);
        Long turn = null, recommended = null;
        for (BiddingBidder x : ranked) {
            if ("failed".equals(x.getPostQualResult())) continue;
            if ("passed".equals(x.getPostQualResult())) recommended = x.getId();
            else turn = x.getId();
            break;
        }
        return new Evaluation(ranks, rated, total, turn, recommended);
    }

    private void applyObserver(BidObserver o, ObserverInput in) {
        String name = text(in.name());
        if (name == null || name.length() > 255) throw ApiException.validation("name", "Enter the observer's name.");
        String type = in.observerType() == null ? "COA" : in.observerType();
        if (!BidObserver.TYPES.contains(type)) throw ApiException.validation("observer_type", "Unknown observer type.");
        o.setName(name);
        o.setOrganization(text(in.organization()));
        o.setType(type);
        if (in.invited() != null) o.setInvited(in.invited());
        o.setInvitedDate(in.invitedDate());
        if (in.attended() != null) o.setAttended(in.attended());
        o.setRemarks(text(in.remarks()));
    }

    /** Maximum procurement timelines for goods (RA 12009 IRR), measured in calendar days. */
    public static List<Interval> timeline(Bidding b) {
        LocalDate opening = b.getBidOpeningDate() == null ? null : b.getBidOpeningDate().toLocalDate();
        Object[][] spec = {
                {"itb_to_opening", "Posting of ITB → bid opening", b.getItbPostedAt(), opening, 50},
                {"opening_to_noa", "Bid opening → Notice of Award", opening, b.getNoaDate(), 40},
                {"itb_to_noa", "Posting of ITB → Notice of Award", b.getItbPostedAt(), b.getNoaDate(), 90},
                {"noa_to_ntp", "Notice of Award → Notice to Proceed", b.getNoaDate(), b.getNtpDate(), 17},
        };
        LocalDate today = LocalDate.now(Times.ZONE);
        List<Interval> out = new ArrayList<>();
        for (Object[] s : spec) {
            LocalDate from = (LocalDate) s[2], to = (LocalDate) s[3];
            int max = (int) s[4];
            Integer elapsed = from == null ? null : (int) ChronoUnit.DAYS.between(from, to != null ? to : today);
            boolean pending = from != null && to == null;
            // A stage not reached yet only counts once its start date has passed.
            if (pending && (b.isOver() && !Bidding.AWARDED.equals(b.getStatus()) || from.isAfter(today))) elapsed = null;
            out.add(new Interval((String) s[0], (String) s[1], from, to, max, elapsed, elapsed != null && elapsed > max, pending));
        }
        return out;
    }

    // ───────────────────────── views ─────────────────────────

    private Bidding lock(Long id) {
        return biddings.lockById(id).orElseThrow(() -> ApiException.notFound("Bidding"));
    }

    private BiddingView view(Bidding b) {
        return views(List.of(b), true).get(b);
    }

    private Map<Bidding, BiddingView> views(Collection<Bidding> list, boolean full) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().map(Bidding::getPreparedBy).filter(Objects::nonNull).collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        Set<Long> supplierIds = list.stream().flatMap(b -> Stream.concat(Stream.of(b.getAwardedSupplierId()),
                full ? Stream.concat(b.getBidders().stream().map(BiddingBidder::getSupplierId), b.getProtests().stream().map(BidProtest::getSupplierId)) : Stream.empty()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> suppliers = new HashMap<>();
        Map<Long, Boolean> okSupplier = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            jdbc.query("select s.id, s.name from suppliers s where s.id in (" + supplierIds.stream().map(x -> "?").collect(Collectors.joining(",")) + ")", rs -> {
                suppliers.put(rs.getLong(1), rs.getString(2));
            }, supplierIds.toArray());
            suppliers.keySet().forEach(sid -> okSupplier.put(sid, awards.eligible(sid)));
        }
        Map<Long, String[]> prs = new HashMap<>();
        List<Long> prIds = list.stream().map(Bidding::getPurchaseRequestId).filter(Objects::nonNull).distinct().toList();
        if (!prIds.isEmpty()) {
            jdbc.query("select pr.id, pr.pr_number, d.id, d.name from purchase_requests pr join departments d on d.id = pr.department_id where pr.id in ("
                    + prIds.stream().map(x -> "?").collect(Collectors.joining(",")) + ")", rs -> {
                prs.put(rs.getLong(1), new String[]{rs.getString(2), rs.getString(3), rs.getString(4)});
            }, prIds.toArray());
        }
        Map<Long, String> resNumbers = new HashMap<>();
        resolutions.findAllById(list.stream().map(Bidding::getBacResolutionId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(res -> resNumbers.put(res.getId(), res.getResolutionNumber()));
        User me = current.user();
        boolean manager = current.any("manage_bidding");
        boolean evaluator = current.any("manage_bidding", "conduct_bid_evaluation");
        boolean awarder = current.any("process_award");
        LocalDateTime now = Times.now();
        Map<Bidding, BiddingView> out = new LinkedHashMap<>();
        for (Bidding b : list) {
            Evaluation ev = full ? evaluation(b) : null;
            List<BidderView> bidders = !full ? null : b.getBidders().stream().map(x -> {
                Map<String, String> results = new LinkedHashMap<>();
                BiddingBidder.STAGES.forEach(s -> results.put(s, x.result(s)));
                Map<Long, BigDecimal> scores = x.getScores().stream().collect(Collectors.toMap(BiddingBidderScore::getCriterionId, BiddingBidderScore::getScore));
                return new BidderView(x.getId(), new Ref(x.getSupplierId(), suppliers.get(x.getSupplierId())), Boolean.TRUE.equals(okSupplier.get(x.getSupplierId())),
                        x.getBidAmount(), overAbc(b, x), x.getBidSecurityForm(), x.getBidSecurityAmount(), x.isBidSecurityPosted(), securityRequired(b, x),
                        securityOk(b, x), results, scores, ev.rated().get(x.getId()), ev.mearb().get(x.getId()), ev.ranks().get(x.getId()),
                        x.getId().equals(ev.postQualTurn()), x.isWinner(), x.getRemarks());
            }).sorted(Comparator.comparing((BidderView v) -> v.computedRank() == null ? Integer.MAX_VALUE : v.computedRank()).thenComparing(BidderView::id)).toList();
            List<ProtestView> protests = !full ? null : b.getProtests().stream().map(p -> new ProtestView(p.getId(), p.getType(),
                    p.getSupplierId() == null ? null : new Ref(p.getSupplierId(), suppliers.get(p.getSupplierId())), p.getFiledBy(), p.getGrounds(), p.getFiledDate(),
                    p.getProtestFee(), p.getStatus(), p.getResolution(), p.getResolvedDate())).toList();
            List<ObserverView> observers = !full ? null : b.getObservers().stream().map(o -> new ObserverView(o.getId(), o.getName(), o.getOrganization(), o.getType(),
                    o.isInvited(), o.getInvitedDate(), o.isAttended(), o.getRemarks())).toList();
            String[] pr = b.getPurchaseRequestId() == null ? null : prs.get(b.getPurchaseRequestId());
            boolean own = me.getId().equals(b.getPreparedBy()) && !me.isSuperAdmin();
            int open = (int) b.getProtests().stream().filter(BidProtest::isOpen).count();
            boolean deadlinePassed = b.getSubmissionDeadline() != null && !b.getSubmissionDeadline().isAfter(now);
            boolean inEval = Bidding.BID_OPENING.equals(b.getStatus()) || Bidding.EVALUATION.equals(b.getStatus());
            out.put(b, new BiddingView(b.getId(), b.getBiddingNumber(), b.getTitle(), b.getStatus(), b.getAbc(),
                    pr == null ? null : new Ref(b.getPurchaseRequestId(), pr[0]), pr == null ? null : new Ref(Long.valueOf(pr[1]), pr[2]),
                    b.getPreparedBy() == null ? null : new Ref(b.getPreparedBy(), names.get(b.getPreparedBy())), b.getDeliveryTerm(), b.getDeliveryLeadDays(),
                    b.getEvaluationMethod(), b.getPriceWeight(), b.getTechnicalWeight(), Times.toOffset(b.getPreProcDate()), b.getPreProcNotes(),
                    b.getAbc().compareTo(PRE_PROC_THRESHOLD) > 0, b.getItbReference(), b.getItbPostedAt(), b.getItbPublication(),
                    Times.toOffset(b.getSubmissionDeadline()), deadlinePassed, Times.toOffset(b.getBidOpeningDate()),
                    b.getAwardedSupplierId() == null ? null : new Ref(b.getAwardedSupplierId(), suppliers.get(b.getAwardedSupplierId())), b.getAwardedAmount(),
                    Times.toOffset(b.getAwardedAt()), b.getBacResolutionId() == null ? null : new Ref(b.getBacResolutionId(), resNumbers.get(b.getBacResolutionId())),
                    b.getNoaNumber(), b.getNoaDate(), b.getNtpNumber(), b.getNtpDate(), b.getContractRecommendation(), b.getFailureReason(), b.getFailedCount(),
                    b.getRemarks(), b.getBidders().size(),
                    !full ? null : b.getItems().stream().map(i -> new LineView(i.getId(), i.getPurchaseRequestItemId(), i.getGeneralDescription(), i.getSpecifications(),
                            i.getUnit(), i.getQuantity(), i.getAbcUnitCost(), i.getAbcTotal())).toList(),
                    !full ? null : b.getCriteria().stream().map(c -> new CriterionView(c.getId(), c.getName(), c.getWeight(), c.isEnvironmental())).toList(),
                    bidders, protests, observers, ev == null ? null : ev.recommended(), ev == null ? null : ev.postQualTurn(), open,
                    (int) b.getObservers().stream().filter(BidObserver::isInvited).count(), full ? timeline(b) : null,
                    manager && b.isPlanning(), manager && b.isPlanning(), manager && Bidding.INVITATION.equals(b.getStatus()) && deadlinePassed,
                    manager && Bidding.BID_OPENING.equals(b.getStatus()), evaluator && inEval,
                    awarder && !own && Bidding.EVALUATION.equals(b.getStatus()) && ev != null && ev.recommended() != null && open == 0,
                    awarder && Bidding.AWARDED.equals(b.getStatus()) && b.getNoaNumber() == null, awarder && b.getNoaNumber() != null && b.getNtpNumber() == null,
                    manager && !b.isOver() && !b.isPlanning(), manager && !b.isOver(), manager && Bidding.DRAFT.equals(b.getStatus()),
                    Times.toOffset(b.getCreatedAt())));
        }
        return out;
    }

    private static Map<String, Object> snapshot(Bidding b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", b.getTitle());
        m.put("status", b.getStatus());
        m.put("abc", b.getAbc());
        m.put("evaluation_method", b.getEvaluationMethod());
        m.put("lines", b.getItems().size());
        return m;
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blank(String s) {
        return text(s);
    }
}

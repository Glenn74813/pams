package com.avtech.pams.report;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APCPI — the GPPB Agency Procurement Compliance and Performance Indicators self-assessment. One
 * assessment per fiscal year; each indicator is rated 0–3 and rolled up into pillar averages and a
 * maturity band. Where the system holds the evidence, a suggested rating is computed from it.
 */
@RestController
@RequestMapping("/api/apcpi")
public class ApcpiController {

    private static final String VIEW = "@perm.any('view_procurement', 'approve_procurement', 'manage_bac_meetings', 'view_reports')";
    private static final String MANAGE = "@perm.any('manage_bac_meetings', 'approve_procurement')";
    private static final String TYPE = "App\\Models\\ApcpiAssessment";
    static final int MAX_SCORE = 3;

    public record Pillar(String key, String title, List<String[]> indicators) {
    }

    /** Four-pillar indicator catalogue (condensed from the GPPB APCPI System), same keys as the Laravel app. */
    static final List<Pillar> CATALOGUE = List.of(
            new Pillar("pillar_1", "Pillar I — Legislative and Regulatory Framework", List.of(
                    new String[]{"p1_i1", "Procurement governed by RA 12009 (NGPA) and its IRR, with no conflicting issuances"},
                    new String[]{"p1_i2", "Use of GPPB-prescribed Philippine Bidding Documents / standard forms"},
                    new String[]{"p1_i3", "Generic / non-restrictive technical specifications in bidding documents"},
                    new String[]{"p1_i4", "Justified and properly approved use of Alternative Methods of Procurement"})),
            new Pillar("pillar_2", "Pillar II — Institutional Framework and Management Capacity", List.of(
                    new String[]{"p2_i1", "Properly constituted BAC, TWG, and BAC Secretariat"},
                    new String[]{"p2_i2", "Procurement Unit / Secretariat with adequate staff and resources"},
                    new String[]{"p2_i3", "Continuing procurement capacity-building / training of personnel"},
                    new String[]{"p2_i4", "APP prepared, approved, and aligned with the approved budget"})),
            new Pillar("pillar_3", "Pillar III — Procurement Operations and Market Practices", List.of(
                    new String[]{"p3_i1", "Procurement completed within the prescribed maximum periods (MaxPETD)"},
                    new String[]{"p3_i2", "Adequate competition — sufficient bidders / quotations obtained"},
                    new String[]{"p3_i3", "Timely payment and contract implementation / delivery monitoring"},
                    new String[]{"p3_i4", "Contracts awarded to the LCRB/SCRB or highest-rated responsive bid"})),
            new Pillar("pillar_4", "Pillar IV — Integrity and Transparency", List.of(
                    new String[]{"p4_i1", "Posting of procurement opportunities, awards, and contracts (transparency)"},
                    new String[]{"p4_i2", "Presence and participation of observers in procurement activities"},
                    new String[]{"p4_i3", "Functional complaints / protest mechanism for bidders"},
                    new String[]{"p4_i4", "Internal audit and submission of the Procurement Monitoring Report (PMR)"})));

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;
    private final Db db;

    public ApcpiController(JdbcTemplate jdbc, CurrentUser current, AuditService audit, Db db) {
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
        this.db = db;
    }

    public record Score(int score, String remarks, String meansOfVerification) {
    }

    public record PillarSummary(String title, BigDecimal average, String maturity) {
    }

    public record Summary(Map<String, PillarSummary> pillars, BigDecimal overall, String maturity) {
    }

    /** A rating the system can back with its own records, and the figures behind it. */
    public record Evidence(Integer suggested, String basis) {
    }

    public record Assessment(Long id, int fiscalYear, String status, String remarks, String conductedBy, OffsetDateTime finalizedAt,
                             List<Pillar> catalogue, Map<String, Score> scores, Summary summary, Map<String, Evidence> evidence) {
    }

    public record ListRow(Long id, int fiscalYear, String status, String conductedBy, BigDecimal overall, String maturity, OffsetDateTime finalizedAt) {
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public Map<String, Object> index() {
        List<ListRow> rows = jdbc.query("""
                select a.id, a.fiscal_year, a.status, u.name, a.finalized_at from apcpi_assessments a left join users u on u.id = a.conducted_by
                order by a.fiscal_year desc""", (rs, i) -> {
            Summary s = summary(scores(rs.getLong(1)));
            Timestamp f = rs.getTimestamp(5);
            return new ListRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), s.overall(), s.maturity(),
                    f == null ? null : Times.toOffset(f.toLocalDateTime()));
        });
        return Map.of("data", rows, "catalogue", CATALOGUE);
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Assessment show(@PathVariable Long id) {
        return present(id);
    }

    public record CreateInput(Integer fiscalYear, String remarks) {
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<Assessment> store(@RequestBody CreateInput in) {
        if (in.fiscalYear() == null || in.fiscalYear() < 2000 || in.fiscalYear() > 2100) throw ApiException.validation("fiscal_year", "Enter the fiscal year.");
        Long exists = jdbc.queryForObject("select count(*) from apcpi_assessments where fiscal_year = ?", Long.class, in.fiscalYear());
        if (exists != null && exists > 0) throw ApiException.validation("fiscal_year", "An APCPI assessment already exists for FY " + in.fiscalYear() + ".");
        Timestamp now = Timestamp.valueOf(Times.now());
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    insert into apcpi_assessments (fiscal_year, status, conducted_by, remarks, created_at, updated_at)
                    values (?, 'draft', ?, ?, ?, ?)""", new String[]{"id"});
            ps.setInt(1, in.fiscalYear());
            ps.setObject(2, current.user().getId());
            ps.setString(3, blank(in.remarks()));
            ps.setTimestamp(4, now);
            ps.setTimestamp(5, now);
            return ps;
        }, kh);
        Long id = kh.getKey().longValue();
        for (Pillar p : CATALOGUE) {
            for (String[] ind : p.indicators()) {
                jdbc.update("insert into apcpi_scores (apcpi_assessment_id, indicator_key, score, created_at, updated_at) values (?, ?, 0, ?, ?)",
                        id, ind[0], now, now);
            }
        }
        audit.record("apcpi_created", TYPE, id, "APCPI self-assessment created for FY " + in.fiscalYear(), null, Map.of("fiscal_year", in.fiscalYear()));
        return ResponseEntity.status(HttpStatus.CREATED).body(present(id));
    }

    public record ScoreInput(String indicatorKey, Integer score, String remarks, String meansOfVerification) {
    }

    public record ScoresInput(List<ScoreInput> scores, String remarks) {
    }

    @PutMapping("/{id}/scores")
    @PreAuthorize(MANAGE)
    @Transactional
    public Assessment updateScores(@PathVariable Long id, @RequestBody ScoresInput in) {
        Map<String, Object> a = lock(id);
        if ("finalized".equals(a.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "The assessment is finalized; reopen it to make changes.");
        if (in.scores() == null) throw ApiException.validation("scores", "Send the indicator scores.");
        List<String> keys = CATALOGUE.stream().flatMap(p -> p.indicators().stream()).map(i -> i[0]).toList();
        Map<String, Score> before = scores(id);
        Map<String, Integer> changed = new LinkedHashMap<>();
        Timestamp now = Timestamp.valueOf(Times.now());
        for (ScoreInput s : in.scores()) {
            if (s.indicatorKey() == null || !keys.contains(s.indicatorKey())) continue;
            if (s.score() == null || s.score() < 0 || s.score() > MAX_SCORE) throw ApiException.validation("scores", "Scores run from 0 to " + MAX_SCORE + ".");
            int updated = jdbc.update("""
                    update apcpi_scores set score = ?, remarks = ?, means_of_verification = ?, updated_at = ?
                    where apcpi_assessment_id = ? and indicator_key = ?""",
                    s.score(), blank(s.remarks()), blank(s.meansOfVerification()), now, id, s.indicatorKey());
            if (updated == 0) {
                jdbc.update("""
                        insert into apcpi_scores (apcpi_assessment_id, indicator_key, score, remarks, means_of_verification, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?)""",
                        id, s.indicatorKey(), s.score(), blank(s.remarks()), blank(s.meansOfVerification()), now, now);
            }
            Score old = before.get(s.indicatorKey());
            if (old == null || old.score() != s.score()) changed.put(s.indicatorKey(), s.score());
        }
        if (in.remarks() != null) jdbc.update("update apcpi_assessments set remarks = ?, updated_at = ? where id = ?", blank(in.remarks()), now, id);
        if (!changed.isEmpty()) {
            Map<String, Integer> old = new LinkedHashMap<>();
            changed.keySet().forEach(k -> old.put(k, before.containsKey(k) ? before.get(k).score() : 0));
            audit.record("apcpi_scored", TYPE, id, "APCPI FY " + a.get("fiscal_year") + ": " + changed.size() + " indicator(s) re-rated", old, changed);
        }
        return present(id);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize(MANAGE)
    @Transactional
    public Assessment finalize(@PathVariable Long id) {
        Map<String, Object> a = lock(id);
        if ("finalized".equals(a.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Already finalized.");
        Long unrated = jdbc.queryForObject("select count(*) from apcpi_scores where apcpi_assessment_id = ? and score = 0 and remarks is null", Long.class, id);
        if (unrated != null && unrated > 0) {
            throw ApiException.of(HttpStatus.CONFLICT, unrated + " indicator(s) are still 0 with no remarks. Rate them, or explain the 0 in the remarks, before finalizing.");
        }
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("update apcpi_assessments set status = 'finalized', finalized_at = ?, updated_at = ? where id = ?", now, now, id);
        Summary s = summary(scores(id));
        audit.record("apcpi_finalized", TYPE, id, "APCPI FY " + a.get("fiscal_year") + " finalized: " + s.overall() + " (" + s.maturity() + ")", null,
                Map.of("overall", s.overall(), "maturity", s.maturity()));
        return present(id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(MANAGE)
    @Transactional
    public Assessment reopen(@PathVariable Long id) {
        Map<String, Object> a = lock(id);
        jdbc.update("update apcpi_assessments set status = 'draft', finalized_at = null, updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), id);
        audit.record("apcpi_reopened", TYPE, id, "APCPI FY " + a.get("fiscal_year") + " reopened for editing", Map.of("status", a.get("status")), Map.of("status", "draft"));
        return present(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public Map<String, String> destroy(@PathVariable Long id) {
        Map<String, Object> a = lock(id);
        if ("finalized".equals(a.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Reopen a finalized assessment before deleting it.");
        jdbc.update("delete from apcpi_assessments where id = ?", id);
        audit.record("apcpi_deleted", TYPE, id, "APCPI FY " + a.get("fiscal_year") + " deleted", Map.of("fiscal_year", a.get("fiscal_year")), null);
        return Map.of("message", "Assessment deleted");
    }

    // ───────────────────────── helpers ─────────────────────────

    private Assessment present(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select a.*, u.name conductor from apcpi_assessments a left join users u on u.id = a.conducted_by where a.id = ?", id);
        if (rows.isEmpty()) throw ApiException.notFound("Assessment");
        Map<String, Object> a = rows.get(0);
        Map<String, Score> scores = scores(id);
        int year = ((Number) a.get("fiscal_year")).intValue();
        Timestamp f = (Timestamp) a.get("finalized_at");
        return new Assessment(id, year, (String) a.get("status"), (String) a.get("remarks"), (String) a.get("conductor"),
                f == null ? null : Times.toOffset(f.toLocalDateTime()), CATALOGUE, scores, summary(scores), evidence(year));
    }

    private Map<String, Object> lock(Long id) {
        List<Map<String, Object>> rows = db.lockRows("apcpi_assessments", "id = ?", id);
        if (rows.isEmpty()) throw ApiException.notFound("Assessment");
        return rows.get(0);
    }

    private Map<String, Score> scores(Long id) {
        Map<String, Score> out = new LinkedHashMap<>();
        jdbc.query("select indicator_key, score, remarks, means_of_verification from apcpi_scores where apcpi_assessment_id = ?",
                rs -> { out.put(rs.getString(1), new Score(rs.getInt(2), rs.getString(3), rs.getString(4))); }, id);
        return out;
    }

    static Summary summary(Map<String, Score> scores) {
        Map<String, PillarSummary> pillars = new LinkedHashMap<>();
        List<Integer> all = new ArrayList<>();
        for (Pillar p : CATALOGUE) {
            List<Integer> vals = p.indicators().stream().map(i -> scores.containsKey(i[0]) ? scores.get(i[0]).score() : 0).toList();
            all.addAll(vals);
            BigDecimal avg = average(vals);
            pillars.put(p.key(), new PillarSummary(p.title(), avg, maturity(avg)));
        }
        BigDecimal overall = average(all);
        return new Summary(pillars, overall, maturity(overall));
    }

    private static BigDecimal average(List<Integer> vals) {
        if (vals.isEmpty()) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(vals.stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(vals.size()), 2, RoundingMode.HALF_UP);
    }

    /** Maturity band for an average score (0–3), per the GPPB interpretation. */
    static String maturity(BigDecimal avg) {
        double v = avg.doubleValue();
        if (v >= 2.51) return "Advanced";
        if (v >= 1.51) return "Maturing";
        if (v >= 0.51) return "Beginning";
        return "Negligible";
    }

    /** Suggested ratings from the fiscal year's own records, for the indicators the system can measure. */
    private Map<String, Evidence> evidence(int year) {
        Map<String, Evidence> e = new LinkedHashMap<>();
        // p2_i1 — BAC composition: a chair, vice chair and at least three members in office.
        long members = n("select count(*) from bac_members where is_active = " + db.t());
        e.put("p2_i1", new Evidence(members >= 5 ? 3 : members >= 3 ? 2 : members > 0 ? 1 : 0, members + " active BAC member(s) on record"));
        // p2_i4 — APP for the year approved.
        String app = jdbc.query("select status from apps where deleted_at is null and fiscal_year = ? order by id offset 0 rows fetch next 1 rows only", rs -> rs.next() ? rs.getString(1) : null, year);
        e.put("p2_i4", new Evidence(app == null ? 0 : "approved".equals(app) ? 3 : 1, app == null ? "No APP for FY " + year : "FY " + year + " APP is " + app));
        // p3_i1 — awarded biddings whose ITB → NOA period stayed within 90 days.
        long awarded = n("select count(*) from biddings where deleted_at is null and status = 'awarded' and noa_date is not null and itb_posted_at is not null and " + db.year("noa_date") + " = ?", year);
        long onTime = n("select count(*) from biddings where deleted_at is null and status = 'awarded' and noa_date is not null and itb_posted_at is not null and " + db.year("noa_date") + " = ? and " + db.daysBetween("itb_posted_at", "noa_date") + " <= 90", year);
        e.put("p3_i1", awarded == 0 ? new Evidence(null, "No biddings awarded in FY " + year)
                : new Evidence(rate(onTime, awarded), onTime + " of " + awarded + " bidding(s) awarded within 90 days of the ITB"));
        // p3_i2 — competition: average bids per bidding and quotations per RFQ.
        BigDecimal bids = avg("select avg(cast(c as decimal(18,6))) from (select count(*) c from bidding_bidders x join biddings b on b.id = x.bidding_id where b.deleted_at is null and " + db.year("b.created_at") + " = ? group by b.id) t", year);
        BigDecimal quotes = avg("select avg(cast(c as decimal(18,6))) from (select count(*) c from rfq_quotations q join rfqs r on r.id = q.rfq_id where r.deleted_at is null and " + db.year("r.created_at") + " = ? group by r.id) t", year);
        double comp = Math.max(bids == null ? 0 : bids.doubleValue(), quotes == null ? 0 : quotes.doubleValue());
        e.put("p3_i2", bids == null && quotes == null ? new Evidence(null, "No biddings or RFQs in FY " + year)
                : new Evidence(comp >= 3 ? 3 : comp >= 2 ? 2 : comp >= 1 ? 1 : 0, "Average " + (bids == null ? "—" : bids) + " bid(s) per bidding, "
                + (quotes == null ? "—" : quotes) + " quotation(s) per RFQ"));
        // p3_i3 — contracts completed or running without liquidated damages.
        long contracts = n("select count(*) from contracts where deleted_at is null and status in ('active', 'completed') and " + db.year("coalesce(signed_date, start_date, cast(created_at as date))") + " = ?", year);
        long late = n("select count(*) from contracts where deleted_at is null and status in ('active', 'completed') and coalesce(ld_amount, 0) > 0 and " + db.year("coalesce(signed_date, start_date, cast(created_at as date))") + " = ?", year);
        e.put("p3_i3", contracts == 0 ? new Evidence(null, "No contracts in FY " + year)
                : new Evidence(rate(contracts - late, contracts), (contracts - late) + " of " + contracts + " contract(s) without liquidated damages"));
        // p4_i2 — observers invited to awarded biddings actually attended.
        long withObs = n("select count(distinct b.id) from biddings b join bid_observers o on o.bidding_id = b.id and o.attended = " + db.t() + " where b.deleted_at is null and " + db.year("b.created_at") + " = ?", year);
        long allBid = n("select count(*) from biddings where deleted_at is null and status not in ('draft', 'cancelled') and " + db.year("created_at") + " = ?", year);
        e.put("p4_i2", allBid == 0 ? new Evidence(null, "No biddings in FY " + year)
                : new Evidence(rate(withObs, allBid), withObs + " of " + allBid + " bidding(s) had an observer present"));
        // p4_i3 — protests recorded and resolved.
        long protests = n("select count(*) from bid_protests p join biddings b on b.id = p.bidding_id where " + db.year("p.created_at") + " = ?", year);
        long resolved = n("select count(*) from bid_protests p join biddings b on b.id = p.bidding_id where " + db.year("p.created_at") + " = ? and p.status not in ('filed', 'under_review')", year);
        e.put("p4_i3", protests == 0 ? new Evidence(null, "No requests for reconsideration or protests filed in FY " + year)
                : new Evidence(rate(resolved, protests), resolved + " of " + protests + " protest(s) resolved"));
        return e;
    }

    /** 0–3 from a share: 90% and up is 3, 70% is 2, 40% is 1. */
    private static int rate(long part, long whole) {
        double r = whole == 0 ? 0 : (double) part / whole;
        return r >= 0.9 ? 3 : r >= 0.7 ? 2 : r >= 0.4 ? 1 : 0;
    }

    private long n(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private BigDecimal avg(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

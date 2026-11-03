package com.avtech.pams.procurement;

import java.util.Map;
import java.util.ArrayList;
import java.math.BigDecimal;
import com.avtech.pams.bac.BacResolution;
import com.avtech.pams.bac.BacResolutionRepository;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Rules shared by RFQ and bidding awards: who may be awarded, and the BAC resolution an award rests on. */
@Component
public class AwardSupport {

    private final BacResolutionRepository resolutions;
    private final DocumentNumbers numbers;
    private final JdbcTemplate jdbc;
    private final Db db;

    public AwardSupport(BacResolutionRepository resolutions, DocumentNumbers numbers, JdbcTemplate jdbc, Db db) {
        this.resolutions = resolutions;
        this.numbers = numbers;
        this.jdbc = jdbc;
        this.db = db;
    }

    /**
     * An existing supplier by id, or one named here and added to the supplier list. It must be active;
     * eligibility is checked at the award, so a quotation or bid can be recorded while documents are verified.
     */
    public Long supplier(Long supplierId, String supplierName) {
        if (supplierId != null) {
            Boolean active = jdbc.query("select is_active from suppliers where id = ?", rs -> rs.next() ? rs.getBoolean(1) : null, supplierId);
            if (!Boolean.TRUE.equals(active)) throw ApiException.validation("supplier_id", "That supplier is inactive.");
            return supplierId;
        }
        String name = supplierName == null || supplierName.isBlank() ? null : supplierName.trim();
        if (name == null) throw ApiException.validation("supplier_id", "Choose or name the supplier.");
        if (name.length() > 255) throw ApiException.validation("supplier_name", "The supplier name is too long.");
        List<Long> found = jdbc.queryForList("select id from suppliers where lower(name) = lower(?) order by id offset 0 rows fetch next 1 rows only", Long.class, name);
        if (!found.isEmpty()) {
            Boolean active = jdbc.query("select is_active from suppliers where id = ?", rs -> rs.next() ? rs.getBoolean(1) : null, found.get(0));
            if (!Boolean.TRUE.equals(active)) throw ApiException.validation("supplier_name", name + " is inactive.");
            return found.get(0);
        }
        // Named here for the first time: registered, but not eligible for an award until its compliance documents are verified.
        Timestamp now = Timestamp.valueOf(Times.now());
        String vendorCode = numbers.series("VND-", 4, "suppliers.vendor_code");
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("insert into suppliers (name, vendor_code, is_active, is_eligible, is_msme, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, name);
            ps.setString(2, vendorCode);
            ps.setBoolean(3, true);
            ps.setBoolean(4, false);
            ps.setBoolean(5, false);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    /** "Supplier s may be awarded": active, marked eligible, and under no blacklist or suspension today (Manila date). */
    public boolean eligible(Long supplierId) {
        Date today = Date.valueOf(LocalDate.now(Times.ZONE));
        Integer ok = jdbc.query("select case when s.is_active = " + db.t() + " and s.is_eligible = " + db.t() + " and not exists ("
                        + " select 1 from supplier_blacklists b where b.supplier_id = s.id and b.status = 'active'"
                        + " and b.start_date <= ? and (b.end_date is null or b.end_date >= ?)) then 1 else 0 end"
                        + " from suppliers s where s.id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, today, today, supplierId);
        return ok != null && ok == 1;
    }

    /** Active and under no blacklist or suspension: all that a mode outside GPPB Circular 06-2026 asks of a supplier here. */
    public boolean allowed(Long supplierId) {
        Date today = Date.valueOf(LocalDate.now(Times.ZONE));
        Integer ok = jdbc.query("select case when s.is_active = " + db.t() + " and not exists ("
                        + " select 1 from supplier_blacklists b where b.supplier_id = s.id and b.status = 'active'"
                        + " and b.start_date <= ? and (b.end_date is null or b.end_date >= ?)) then 1 else 0 end"
                        + " from suppliers s where s.id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, today, today, supplierId);
        return ok != null && ok == 1;
    }

    /**
     * What stops this supplier being awarded under the mode; empty when nothing does. Competitive selection also needs
     * the supplier verified in the register; the other modes go by the documents on file, which the circular says need
     * not be submitted again (4.4).
     */
    public List<String> awardProblems(Long supplierId, String mode, String ground, boolean revised, int categoryRank, BigDecimal abc) {
        List<String> problems = new ArrayList<>();
        if (!allowed(supplierId)) {
            problems.add("inactive, blacklisted or suspended");
            return problems;
        }
        if (EligibilityRules.competitive(mode) && !eligible(supplierId)) problems.add("not verified as eligible in the supplier register");
        String kind = jdbc.queryForObject("select supplier_kind from suppliers where id = ?", String.class, supplierId);
        problems.addAll(missingDocuments(supplierId, EligibilityRules.required(mode, ground, revised, categoryRank, abc, kind)));
        return problems;
    }

    /** Each requirement with no valid document on file, as "missing X" or "expired X". */
    public List<String> missingDocuments(Long supplierId, List<EligibilityRules.Requirement> required) {
        LocalDate today = LocalDate.now(Times.ZONE);
        List<String> out = new ArrayList<>();
        if (required.isEmpty()) return out;
        List<Map<String, Object>> docs = jdbc.queryForList("select document_type, expiry_date from supplier_compliance_documents where supplier_id = ?", supplierId);
        for (EligibilityRules.Requirement r : required) {
            boolean present = false, valid = false;
            for (Map<String, Object> d : docs) {
                if (!r.accepted().contains(d.get("document_type"))) continue;
                present = true;
                Date expiry = (Date) d.get("expiry_date");
                // The renewal receipt stands in for a permit that has already expired (4.3.2 a).
                if (expiry == null || !expiry.toLocalDate().isBefore(today) || EligibilityRules.RENEWAL.equals(d.get("document_type"))) valid = true;
            }
            if (!valid) out.add((present ? "expired " : "missing ") + r.label());
        }
        return out;
    }

    /**
     * True when the supplier was let through on the receipt of a permit renewal and has not yet shown the valid
     * permit, which GPPB Circular 06-2026 (4.3.2 a) requires after the award and before payment.
     */
    public boolean permitPending(Long supplierId) {
        if (supplierId == null) return false;
        Date today = Date.valueOf(LocalDate.now(Times.ZONE));
        Integer n = jdbc.queryForObject("""
                select case when exists (select 1 from supplier_compliance_documents where supplier_id = ? and document_type = ?)
                             and not exists (select 1 from supplier_compliance_documents where supplier_id = ? and document_type in (?, ?, ?)
                                             and (expiry_date is null or expiry_date >= ?))
                            then 1 else 0 end""", Integer.class, supplierId, EligibilityRules.RENEWAL, supplierId,
                EligibilityRules.MAYOR, EligibilityRules.BUSINESS_PERMIT, EligibilityRules.PLATINUM, today);
        return n != null && n == 1;
    }

    /** 0 goods, 1 infrastructure, 2 consulting: the strictest category on the lines of an RFQ. */
    public int rfqCategoryRank(Long rfqId) {
        Integer rank = jdbc.queryForObject("""
                select max(case when pri.procurement_category = 'Consulting Services' then 2 when pri.procurement_category = 'Infrastructure Projects' then 1 else 0 end)
                from rfq_items ri join purchase_request_items pri on pri.id = ri.purchase_request_item_id where ri.rfq_id = ?""", Integer.class, rfqId);
        return rank == null ? 0 : rank;
    }

    public String supplierName(Long id) {
        List<String> n = jdbc.queryForList("select name from suppliers where id = ?", String.class, id);
        return n.isEmpty() ? "supplier #" + id : n.get(0);
    }

    /**
     * The approved BAC resolution recommending this award: an award-type resolution for the same
     * purchase request, not already backing another award (other than this RFQ or bidding).
     */
    public BacResolution resolution(Long resolutionId, Long purchaseRequestId, Long exceptRfq, Long exceptBidding) {
        BacResolution res = resolutions.findLive(resolutionId == null ? -1L : resolutionId)
                .orElseThrow(() -> ApiException.validation("bac_resolution_id", "Cite the approved BAC resolution recommending this award."));
        if (!BacResolution.APPROVED.equals(res.getStatus())) throw ApiException.validation("bac_resolution_id", res.getResolutionNumber() + " is not approved.");
        if (!BacResolution.AWARD_TYPES.contains(res.getType())) throw ApiException.validation("bac_resolution_id", res.getResolutionNumber() + " is not a resolution recommending an award.");
        if (!Objects.equals(res.getPurchaseRequestId(), purchaseRequestId)) {
            throw ApiException.validation("bac_resolution_id", res.getResolutionNumber() + " is for a different purchase request.");
        }
        Integer reused = jdbc.queryForObject("""
                select (select count(*) from rfqs where bac_resolution_id = ? and id <> ? and deleted_at is null and status <> 'cancelled')
                     + (select count(*) from biddings where bac_resolution_id = ? and id <> ? and deleted_at is null and status not in ('cancelled', 'failed'))""",
                Integer.class, res.getId(), exceptRfq == null ? -1L : exceptRfq, res.getId(), exceptBidding == null ? -1L : exceptBidding);
        if (reused != null && reused > 0) throw ApiException.validation("bac_resolution_id", res.getResolutionNumber() + " already backs another award.");
        return res;
    }
}

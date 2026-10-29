package com.avtech.pams.procurement;

import com.avtech.pams.bac.BacResolution;
import com.avtech.pams.bac.BacResolutionRepository;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/** Rules shared by RFQ and bidding awards: who may be awarded, and the BAC resolution an award rests on. */
@Component
public class AwardSupport {

    private final BacResolutionRepository resolutions;
    private final DocumentNumbers numbers;
    private final JdbcTemplate jdbc;

    public AwardSupport(BacResolutionRepository resolutions, DocumentNumbers numbers, JdbcTemplate jdbc) {
        this.resolutions = resolutions;
        this.numbers = numbers;
        this.jdbc = jdbc;
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
        List<Long> found = jdbc.queryForList("select id from suppliers where lower(name) = lower(?) order by id limit 1", Long.class, name);
        if (!found.isEmpty()) {
            Boolean active = jdbc.query("select is_active from suppliers where id = ?", rs -> rs.next() ? rs.getBoolean(1) : null, found.get(0));
            if (!Boolean.TRUE.equals(active)) throw ApiException.validation("supplier_name", name + " is inactive.");
            return found.get(0);
        }
        // Named here for the first time: registered, but not eligible for an award until its compliance documents are verified.
        Timestamp now = Timestamp.valueOf(Times.now());
        return jdbc.queryForObject("insert into suppliers (name, vendor_code, is_active, is_eligible, is_msme, created_at, updated_at) values (?, ?, true, false, false, ?, ?) returning id",
                Long.class, name, numbers.series("VND-", 4, "suppliers.vendor_code"), now, now);
    }

    /** SQL for "supplier s may be awarded": active, marked eligible, and under no blacklist or suspension today. */
    public static final String ELIGIBLE_SQL = """
            (s.is_active and s.is_eligible and not exists (
              select 1 from supplier_blacklists b where b.supplier_id = s.id and b.status = 'active'
                and b.start_date <= current_date and (b.end_date is null or b.end_date >= current_date)))""";

    public boolean eligible(Long supplierId) {
        Boolean ok = jdbc.query("select " + ELIGIBLE_SQL + " from suppliers s where s.id = ?", rs -> rs.next() ? rs.getBoolean(1) : null, supplierId);
        return Boolean.TRUE.equals(ok);
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

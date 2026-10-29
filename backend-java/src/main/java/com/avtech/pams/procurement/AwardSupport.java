package com.avtech.pams.procurement;

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

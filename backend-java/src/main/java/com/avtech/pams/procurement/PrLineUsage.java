package com.avtech.pams.procurement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * How much of each purchase request line is already out for procurement, on live RFQs (not
 * cancelled) or live biddings (not cancelled or failed). A line is procured once, by one route.
 */
@Component
public class PrLineUsage {

    private final JdbcTemplate jdbc;

    public PrLineUsage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Quantity taken per PR line, optionally for one PR only, leaving out one RFQ or one bidding (the one being edited). */
    public Map<Long, Integer> taken(Long prId, Long exceptRfq, Long exceptBidding) {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query("""
                select line, sum(qty) from (
                  select ri.purchase_request_item_id as line, ri.quantity as qty from rfq_items ri join rfqs r on r.id = ri.rfq_id
                  where r.deleted_at is null and r.status <> 'cancelled' and r.id <> ? and (cast(? as bigint) is null or r.purchase_request_id = ?)
                  union all
                  select bi.purchase_request_item_id, bi.quantity from bidding_items bi join biddings b on b.id = bi.bidding_id
                  where b.deleted_at is null and b.status not in ('cancelled', 'failed') and b.id <> ? and (cast(? as bigint) is null or b.purchase_request_id = ?)
                ) t where line is not null group by line""", rs -> {
            out.put(rs.getLong(1), rs.getInt(2));
        }, exceptRfq == null ? -1L : exceptRfq, prId, prId, exceptBidding == null ? -1L : exceptBidding, prId, prId);
        return out;
    }
}

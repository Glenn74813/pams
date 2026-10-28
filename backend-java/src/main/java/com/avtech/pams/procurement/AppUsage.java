package com.avtech.pams.procurement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How much of each APP line purchase requests have taken. Every live request that is not
 * cancelled counts against the line's quantity (drafts included, so two drafts cannot plan the same
 * units); only fully approved requests count as committed budget.
 */
@Component
public class AppUsage {

    public record Use(int quantity, BigDecimal requested, BigDecimal committed) {
        static final Use NONE = new Use(0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private final JdbcTemplate jdbc;

    public AppUsage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Use of(Map<Long, Use> uses, Long appItemId) {
        return uses.getOrDefault(appItemId, Use.NONE);
    }

    /** Usage per APP line, leaving out one purchase request (the one being edited) when exceptPr is set. */
    public Map<Long, Use> forItems(Collection<Long> appItemIds, Long exceptPr) {
        if (appItemIds.isEmpty()) return Collections.emptyMap();
        String in = appItemIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] args = appItemIds.toArray();
        Map<Long, Use> out = new HashMap<>();
        jdbc.query("""
                select pri.app_item_id, sum(pri.quantity) as qty, sum(pri.estimated_total) as requested,
                       sum(case when pr.status = 'approved' then pri.estimated_total else 0 end) as committed
                from purchase_request_items pri join purchase_requests pr on pr.id = pri.purchase_request_id
                where pr.deleted_at is null and pr.status not in ('cancelled', 'rejected')
                  and pr.id <> ? and pri.app_item_id in (""" + in + ") group by pri.app_item_id",
                rs -> {
                    out.put(rs.getLong(1), new Use(rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
                }, prepend(exceptPr == null ? -1L : exceptPr, args));
        return out;
    }

    /** Usage for every line of the given APPs. */
    public Map<Long, Use> forApps(Collection<Long> appIds) {
        if (appIds.isEmpty()) return Collections.emptyMap();
        String in = appIds.stream().map(id -> "?").collect(Collectors.joining(","));
        return forItems(jdbc.queryForList("select id from app_items where app_id in (" + in + ")", Long.class, appIds.toArray()), null);
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }
}

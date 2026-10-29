package com.avtech.pams.purchasing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What has been delivered against each PO line: the accepted quantities on IARs that have not been
 * rejected (still pending, or accepted). Keeps the PO's status in step with them.
 */
@Component
public class Deliveries {

    private final JdbcTemplate jdbc;

    public Deliveries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Delivered quantity per PO line, leaving out one IAR (the one being edited). */
    public Map<Long, Integer> delivered(Long poId, Long exceptIar) {
        return sum(poId, exceptIar, List.of("pending", "accepted", "partially_accepted"));
    }

    /** Quantity per PO line that has been accepted into stock. */
    public Map<Long, Integer> accepted(Long poId) {
        return sum(poId, null, List.of("accepted", "partially_accepted"));
    }

    private Map<Long, Integer> sum(Long poId, Long exceptIar, List<String> statuses) {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query("""
                select ii.purchase_order_item_id, sum(ii.quantity_accepted) from iar_items ii
                join inspection_acceptance_reports r on r.id = ii.inspection_acceptance_report_id
                where r.purchase_order_id = ? and r.id <> ? and ii.purchase_order_item_id is not null
                  and r.acceptance_status in (""" + String.join(",", statuses.stream().map(x -> "?").toList()) + ") group by 1",
                rs -> {
                    out.put(rs.getLong(1), rs.getInt(2));
                }, prepend(poId, exceptIar == null ? -1L : exceptIar, statuses));
        return out;
    }

    /** confirmed → partially received → received, from what has been delivered. */
    public void sync(PurchaseOrder po) {
        if (!po.isReceivable() && !PurchaseOrder.RECEIVED.equals(po.getStatus())) return;
        Map<Long, Integer> done = delivered(po.getId(), null);
        boolean complete = po.getItems().stream().allMatch(i -> done.getOrDefault(i.getId(), 0) >= i.getQuantity());
        boolean any = done.values().stream().anyMatch(v -> v > 0);
        po.setStatus(complete ? PurchaseOrder.RECEIVED : any ? PurchaseOrder.PARTIALLY_RECEIVED : PurchaseOrder.CONFIRMED);
    }

    private static Object[] prepend(Object a, Object b, List<String> rest) {
        Object[] all = new Object[rest.size() + 2];
        all[0] = a;
        all[1] = b;
        for (int i = 0; i < rest.size(); i++) all[i + 2] = rest.get(i);
        return all;
    }
}

package com.avtech.pams.report.coa;

import com.avtech.pams.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One item's stock movements with their unit costs, for the ledger cards (Supplies Ledger Card,
 * Semi-expendable Property Card and Ledger Card): the same inventory logs and balance logic as the
 * Stock Card, with each line costed from what it references — the stock lot or IAR lots received,
 * the request line issued, the adjustment, or the asset registered — and the balance carried at a
 * moving average cost.
 */
@Component
public class ItemMovements {

    static final String STOCK = "App\\Models\\Stock";
    static final String IAR = "App\\Models\\InspectionAcceptanceReport";
    static final String REQUEST = "App\\Models\\Request";
    static final String ADJUSTMENT = "App\\Models\\StockAdjustment";
    static final String PROPERTY = "App\\Models\\Property";

    /** qty is signed: positive received, negative issued. balanceCost is the moving average after the line. */
    public record Line(LocalDate date, String reference, int qty, BigDecimal unitCost, String office, String officer, String itemNo,
                       int balance, BigDecimal balanceCost, BigDecimal balanceAmount, String remarks) {

        public BigDecimal amount() {
            return unitCost.multiply(BigDecimal.valueOf(Math.abs(qty))).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record Card(Map<String, Object> item, int openingQty, BigDecimal openingCost, List<Line> lines) {
    }

    private final JdbcTemplate jdbc;

    public ItemMovements(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Card of(long itemId) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                select i.id, i.code, i.name, i.description, i.unit_of_measure, i.min_stock, i.current_stock, i.weighted_average_cost,
                       i.is_semi_expandable, i.is_expandable, c.name as category_name
                from items i left join categories c on c.id = i.category_id where i.id = ?""", itemId);
        if (found.isEmpty()) throw ApiException.notFound("Item");
        Map<String, Object> item = found.get(0);
        BigDecimal wac = money(item.get("weighted_average_cost"));

        List<Map<String, Object>> logs = jdbc.queryForList("""
                select l.id, l.action, l.quantity, l.previous_quantity, l.new_quantity, l.notes, l.created_at, l.reference_type,
                       u.id as user_id, u.name as user_name, d.name as department_name,
                       st.unit_cost as stock_cost,
                       (select avg(s2.unit_cost) from stocks s2 where l.reference_type = ? and s2.iar_id = l.reference_id and s2.item_id = l.item_id) as iar_cost,
                       iar.iar_number,
                       ri.unit_cost as request_cost, rq.ris_number, rq.ics_number, rq.request_number, rd.name as request_department,
                       ru.name as requester_name, se.custodian_name, se.ics_number as issue_ics,
                       sa.unit_cost as adjustment_cost, sa.adjustment_number,
                       pp.acquisition_cost as property_cost, pp.tag_number
                from inventory_logs l
                left join users u on u.id = l.user_id
                left join departments d on d.id = u.department_id
                left join stocks st on l.reference_type = ? and st.id = l.reference_id
                left join inspection_acceptance_reports iar on l.reference_type = ? and iar.id = l.reference_id
                left join requests rq on l.reference_type = ? and rq.id = l.reference_id
                left join request_items ri on ri.id = (select min(x.id) from request_items x where x.request_id = rq.id and x.item_id = l.item_id)
                left join semi_expendable_issues se on se.id = (select min(y.id) from semi_expendable_issues y where y.request_id = rq.id and y.item_id = l.item_id)
                left join departments rd on rd.id = rq.department_id
                left join users ru on ru.id = rq.requested_by
                left join stock_adjustments sa on l.reference_type = ? and sa.id = l.reference_id
                left join properties pp on l.reference_type = ? and pp.id = l.reference_id
                where l.item_id = ?
                  and l.action in ('stock_in', 'stock_out', 'delivery_accepted', 'item_issued', 'adjustment', 'adjustment_in', 'adjustment_out', 'stock_edited')
                order by l.created_at asc, l.id asc
                """, IAR, STOCK, IAR, REQUEST, ADJUSTMENT, PROPERTY, itemId);

        int net = 0;
        for (Map<String, Object> log : logs) net += StockCardForm.signedDelta(log);
        int opening = intOf(item.get("current_stock")) - net;

        // The opening balance is carried at the first known receipt cost, else the item's average.
        BigDecimal openingCost = wac;
        for (Map<String, Object> log : logs) {
            BigDecimal c = receiptCost(log);
            if (c != null) {
                openingCost = c;
                break;
            }
        }

        List<Line> lines = new ArrayList<>();
        int bal = opening;
        BigDecimal value = openingCost.multiply(BigDecimal.valueOf(Math.max(0, opening)));
        for (Map<String, Object> log : logs) {
            int signed = StockCardForm.signedDelta(log);
            if (signed == 0) continue;
            BigDecimal avg = bal > 0 ? value.divide(BigDecimal.valueOf(bal), 2, RoundingMode.HALF_UP) : wac;
            BigDecimal unit;
            if (signed > 0) {
                unit = first(receiptCost(log), avg);
                value = value.add(unit.multiply(BigDecimal.valueOf(signed)));
            } else {
                unit = first(money0(log.get("request_cost")), money0(log.get("adjustment_cost")), money0(log.get("property_cost")), avg);
                value = value.subtract(unit.multiply(BigDecimal.valueOf(-signed)));
            }
            bal += signed;
            if (bal <= 0) value = BigDecimal.ZERO;
            BigDecimal balCost = bal > 0 ? value.divide(BigDecimal.valueOf(bal), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            String office = signed < 0 ? first(str(log.get("request_department")), str(log.get("department_name"))) : "";
            String officer = signed < 0 ? first(str(log.get("custodian_name")), str(log.get("requester_name"))) : "";
            String itemNo = first(str(log.get("issue_ics")), str(log.get("ics_number")), str(log.get("tag_number")));
            lines.add(new Line(date(log.get("created_at")), reference(log, signed), signed, unit.setScale(2, RoundingMode.HALF_UP), office, officer, itemNo,
                    bal, balCost, value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP), remarks(log)));
        }
        return new Card(item, opening, openingCost.setScale(2, RoundingMode.HALF_UP), lines);
    }

    private static BigDecimal receiptCost(Map<String, Object> log) {
        if (StockCardForm.signedDelta(log) <= 0) return null;
        return first(money0(log.get("stock_cost")), money0(log.get("iar_cost")), money0(log.get("adjustment_cost")));
    }

    /** The document the line comes from: IAR, RIS/ICS, adjustment or PAR number, else the log's own wording. */
    private static String reference(Map<String, Object> log, int signed) {
        String r = first(str(log.get("iar_number")), str(log.get("ris_number")), str(log.get("ics_number")), str(log.get("request_number")),
                str(log.get("adjustment_number")), str(log.get("tag_number")));
        if (!r.isEmpty()) return r;
        String action = (String) log.get("action");
        if (action != null && action.startsWith("adjustment")) return "Adjustment";
        return signed > 0 ? "Stock In" : "Issued";
    }

    private static String remarks(Map<String, Object> log) {
        String n = str(log.get("notes"));
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    @SafeVarargs
    private static <T> T first(T... options) {
        for (T o : options) {
            if (o instanceof String s ? !s.isEmpty() : o != null) return o;
        }
        return options[options.length - 1];
    }

    static BigDecimal money(Object v) {
        return v instanceof BigDecimal b ? b : v instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
    }

    /** A cost, or null when missing or zero (so the next source is tried). */
    private static BigDecimal money0(Object v) {
        BigDecimal b = v == null ? null : money(v);
        return b == null || b.signum() == 0 ? null : b;
    }

    static LocalDate date(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.time.LocalDateTime t) return t.toLocalDate();
        return (LocalDate) v;
    }

    static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static int intOf(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }
}

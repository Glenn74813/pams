package com.avtech.pams.report;

import com.avtech.pams.common.Db;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.report.TorListReports.Period;
import com.avtech.pams.report.coa.Signatories;
import com.avtech.pams.report.coa.TabularReport;
import com.avtech.pams.report.coa.TabularReport.Kind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The reports TOR 1.6.4 wants monthly, annually or for any dates. The COA forms themselves are per
 * record or as at a date, so each of these lists the documents and transactions of the period, built
 * as a {@link TabularReport} for Excel, CSV or PDF.
 */
@Component
public class TorPeriodReports {

    private final JdbcTemplate jdbc;
    private final Db db;
    private final Signatories signatories;

    public TorPeriodReports(JdbcTemplate jdbc, Db db, Signatories signatories) {
        this.jdbc = jdbc;
        this.db = db;
        this.signatories = signatories;
    }

    /** One line of an accountability or transaction register, sorted by date before it is written. */
    private record Dated(LocalDate date, Object[] cells) {
    }

    private void write(TabularReport t, List<Dated> lines) {
        lines.sort(Comparator.comparing(Dated::date, Comparator.nullsLast(Comparator.naturalOrder())));
        for (Dated d : lines) t.row(d.cells());
    }

    private static boolean within(LocalDate d, Period p) {
        return d != null && (p.from() == null || !d.isBefore(p.from())) && (p.to() == null || !d.isAfter(p.to()));
    }

    // ───────────────────────── PAR and ICS ─────────────────────────

    /** Property Acknowledgment Receipts: assets under a PAR, and PPE issued on a request's PAR before its assets are registered. */
    public TabularReport par(Period period, String entity) {
        return accountability(period, entity, "PAR Register", "PROPERTY ACKNOWLEDGMENT RECEIPTS (PAR) ISSUED", "PAR", "par_number", "par_date", "i.is_expandable = " + db.t());
    }

    /** Inventory Custodian Slips: assets under an ICS, and semi-expendable items issued on a request's ICS. */
    public TabularReport ics(Period period, String entity) {
        return accountability(period, entity, "ICS Register", "INVENTORY CUSTODIAN SLIPS (ICS) ISSUED", "ICS", "ics_number", "ics_date", "i.is_semi_expandable = " + db.t());
    }

    private TabularReport accountability(Period period, String entity, String sheet, String title, String prefix, String numberColumn, String dateColumn, String itemClass) {
        TabularReport t = new TabularReport(sheet, title).entity(entity).subtitle(period.label())
                .col(prefix + " No.", Kind.TEXT, 18).col("Date", Kind.DATE, 11).col("Property / Item No.", Kind.TEXT, 18).col("Description", Kind.TEXT, 34)
                .col("Accountable Officer", Kind.TEXT, 22).col("Office", Kind.TEXT, 22).col("Qty", Kind.INT, 7).col("Unit Cost", Kind.MONEY, 13).col("Amount", Kind.MONEY, 13);
        List<Dated> lines = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String when = period.on("p.assigned_date", args);
        args.add(0, prefix + "-%");
        for (Map<String, Object> r : jdbc.queryForList("""
                select p.accountability_number, p.assigned_date, p.tag_number, i.name, p.acquisition_cost, u.name as holder, d.name as office
                from properties p join items i on i.id = p.item_id left join users u on u.id = p.assigned_to left join departments d on d.id = u.department_id
                where p.accountability_number like ?""" + when, args.toArray())) {
            BigDecimal cost = money(r.get("acquisition_cost"));
            lines.add(new Dated(date(r.get("assigned_date")), new Object[]{r.get("accountability_number"), date(r.get("assigned_date")), r.get("tag_number"), r.get("name"),
                    r.get("holder"), r.get("office"), 1, cost, cost}));
        }
        // Issued on a request: lines of that class, unless the document already has its assets above.
        List<Object> reqArgs = new ArrayList<>();
        String reqWhen = period.on("r." + dateColumn, reqArgs);
        for (Map<String, Object> r : jdbc.queryForList("select r." + numberColumn + " as number, r." + dateColumn + " as issued, i.code, i.name, "
                + "case when ri.approved_quantity > 0 then ri.approved_quantity else ri.quantity end as qty, coalesce(ri.unit_cost, i.weighted_average_cost) as unit_cost, u.name as holder, d.name as office "
                + "from requests r join request_items ri on ri.request_id = r.id join items i on i.id = ri.item_id "
                + "left join users u on u.id = r.requested_by left join departments d on d.id = r.department_id "
                + "where r." + numberColumn + " is not null and " + itemClass
                + " and not exists (select 1 from properties x where x.accountability_number = r." + numberColumn + ")" + reqWhen, reqArgs.toArray())) {
            BigDecimal cost = money(r.get("unit_cost"));
            int qty = ((Number) r.get("qty")).intValue();
            lines.add(new Dated(date(r.get("issued")), new Object[]{r.get("number"), date(r.get("issued")), r.get("code"), r.get("name"), r.get("holder"), r.get("office"),
                    qty, cost, cost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP)}));
        }
        write(t, lines);
        t.total("TOTAL (" + lines.size() + " line" + (lines.size() == 1 ? "" : "s") + ")");
        t.sign("Prepared by:", signatories.resolve("property_officer"), null);
        return t;
    }

    // ───────────────────────── property card and stock card ─────────────────────────

    /** Everything the Property Cards would show for the period, across all assets: acquisitions, movements and disposals. */
    public TabularReport propertyTransactions(Period period, String entity) {
        TabularReport t = new TabularReport("Property Transactions", "PROPERTY CARD TRANSACTIONS").entity(entity).subtitle(period.label())
                .col("Date", Kind.DATE, 11).col("Property No.", Kind.TEXT, 18).col("Description", Kind.TEXT, 32).col("Transaction", Kind.TEXT, 14).col("Reference", Kind.TEXT, 18)
                .col("From", Kind.TEXT, 20).col("To", Kind.TEXT, 20).col("Acquisition Cost", Kind.MONEY, 13);
        List<Dated> lines = new ArrayList<>();
        List<Object> a = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select coalesce(p.acquisition_date, p.assigned_date) as d, p.tag_number, i.name, p.accountability_number, p.acquisition_cost, u.name as holder
                from properties p join items i on i.id = p.item_id left join users u on u.id = p.assigned_to where 1 = 1""" + period.on("coalesce(p.acquisition_date, p.assigned_date)", a), a.toArray())) {
            lines.add(new Dated(date(r.get("d")), new Object[]{date(r.get("d")), r.get("tag_number"), r.get("name"), "Acquisition", r.get("accountability_number"), null, r.get("holder"), money(r.get("acquisition_cost"))}));
        }
        List<Object> b = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select t.transfer_date, t.transfer_type, t.reference_number, p.tag_number, i.name, p.acquisition_cost, f.name as from_name, u.name as to_name
                from property_transfers t join properties p on p.id = t.property_id join items i on i.id = p.item_id
                left join users f on f.id = t.from_user left join users u on u.id = t.to_user where 1 = 1""" + period.on("t.transfer_date", b), b.toArray())) {
            String type = (String) r.get("transfer_type");
            lines.add(new Dated(date(r.get("transfer_date")), new Object[]{date(r.get("transfer_date")), r.get("tag_number"), r.get("name"),
                    "return".equals(type) ? "Return" : "reissue".equals(type) ? "Issue" : "Transfer", r.get("reference_number"), office(r.get("from_name")), office(r.get("to_name")), money(r.get("acquisition_cost"))}));
        }
        List<Object> c = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select x.disposal_date, x.disposal_number, x.disposal_type, p.tag_number, i.name, p.acquisition_cost
                from disposal_requests x join properties p on p.id = x.property_id join items i on i.id = p.item_id
                where x.deleted_at is null and x.status = 'completed'""" + period.on("x.disposal_date", c), c.toArray())) {
            lines.add(new Dated(date(r.get("disposal_date")), new Object[]{date(r.get("disposal_date")), r.get("tag_number"), r.get("name"), "Disposal", r.get("disposal_number"), null, label(r.get("disposal_type")), money(r.get("acquisition_cost"))}));
        }
        write(t, lines);
        return t;
    }

    /** Stock Card movements of the period, of one item or all, with what was received, issued and left on hand. */
    public TabularReport stockMovements(Period period, Long itemId, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.onTimestamp("l.created_at", args);
        String one = "";
        if (itemId != null) {
            one = " and l.item_id = ?";
            args.add(itemId);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select l.created_at, l.action, l.quantity, l.previous_quantity, l.new_quantity, l.notes, i.code, i.name, i.unit_of_measure, u.name as by_name
                from inventory_logs l join items i on i.id = l.item_id left join users u on u.id = l.user_id
                where l.quantity is not null""" + when + one + " order by i.name, i.id, l.created_at, l.id", args.toArray());
        TabularReport t = new TabularReport("Stock Movements", "STOCK CARD MOVEMENTS").entity(entity).subtitle(period.label())
                .col("Date", Kind.DATE, 11).col("Stock No.", Kind.TEXT, 14).col("Item", Kind.TEXT, 30).col("Unit", Kind.TEXT, 8).col("Movement", Kind.TEXT, 14).col("Receipt Qty", Kind.INT, 9)
                .col("Issue Qty", Kind.INT, 9).col("Balance", Kind.INT, 9).col("Reference / Remarks", Kind.TEXT, 30).col("By", Kind.TEXT, 18);
        for (Map<String, Object> r : rows) {
            int qty = ((Number) r.get("quantity")).intValue();
            Number before = (Number) r.get("previous_quantity");
            Number after = (Number) r.get("new_quantity");
            int delta = before != null && after != null ? after.intValue() - before.intValue() : qty;
            t.row(date(r.get("created_at")), r.get("code"), r.get("name"), r.get("unit_of_measure"), label(r.get("action")), delta > 0 ? delta : null, delta < 0 ? -delta : null,
                    after, r.get("notes"), r.get("by_name"));
        }
        return t;
    }

    // ───────────────────────── physical counts ─────────────────────────

    /** Physical counts held in the period; the RPCI, RPCSP and RPCPPE themselves are exported as at a date. */
    public TabularReport physicalCounts(Period period, String type, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.on("c.count_date", args);
        String only = "";
        if (type != null) {
            only = " and c.count_type = ?";
            args.add(type);
        }
        String title = "ppe".equals(type) ? "PHYSICAL COUNTS OF PROPERTY, PLANT AND EQUIPMENT (RPCPPE)" : "supplies".equals(type) ? "PHYSICAL COUNTS OF INVENTORIES (RPCI)"
                : "semi_expendable".equals(type) ? "PHYSICAL COUNTS OF SEMI-EXPENDABLE PROPERTY (RPCSP)" : "PHYSICAL COUNTS";
        TabularReport t = new TabularReport("Physical Counts", title).entity(entity).subtitle(period.label())
                .col("Count No.", Kind.TEXT, 18).col("Date", Kind.DATE, 11).col("Type", Kind.TEXT, 16).col("Office", Kind.TEXT, 24).col("Status", Kind.TEXT, 12)
                .col("Items Counted", Kind.INT, 10).col("Found", Kind.INT, 10).col("Discrepancies", Kind.INT, 11).col("Purpose / Remarks", Kind.TEXT, 34);
        for (Map<String, Object> r : jdbc.queryForList("""
                select c.count_number, c.count_date, c.count_type, c.status, c.total_items_counted, c.total_items_found, c.total_discrepancies, c.purpose, c.remarks, d.name as office
                from physical_inventory_counts c left join departments d on d.id = c.department_id
                where 1 = 1""" + when + only + " order by c.count_date, c.count_number", args.toArray())) {
            Object note = r.get("purpose") != null ? r.get("purpose") : r.get("remarks");
            t.row(r.get("count_number"), date(r.get("count_date")), label(r.get("count_type")), r.get("office"), label(r.get("status")), r.get("total_items_counted"),
                    r.get("total_items_found"), r.get("total_discrepancies"), note);
        }
        return t;
    }

    // ───────────────────────── disposal ─────────────────────────

    /** Completed disposals of the period: everything for the WMR list, assets only for the IIRUP list. */
    public TabularReport disposals(Period period, boolean propertyOnly, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.on("x.disposal_date", args);
        TabularReport t = new TabularReport(propertyOnly ? "IIRUP Register" : "WMR Register",
                propertyOnly ? "UNSERVICEABLE PROPERTY INSPECTED AND DISPOSED (IIRUP)" : "WASTE MATERIALS DISPOSED (WMR)").entity(entity).subtitle(period.label())
                .col("Disposal No.", Kind.TEXT, 18).col("Date", Kind.DATE, 11).col("Property / Stock No.", Kind.TEXT, 18).col("Description", Kind.TEXT, 32).col("Mode", Kind.TEXT, 16)
                .col("Qty", Kind.INT, 7).col("Unit Cost", Kind.MONEY, 13).col("Amount", Kind.MONEY, 13).col("Reason", Kind.TEXT, 28);
        int n = 0;
        for (Map<String, Object> r : jdbc.queryForList("""
                select x.disposal_number, x.disposal_date, x.disposal_type, x.disposal_category, x.quantity, x.unit_cost, x.reason,
                       p.tag_number, p.acquisition_cost, coalesce(pi.name, i.name) as item_name, i.code
                from disposal_requests x left join properties p on p.id = x.property_id left join items pi on pi.id = p.item_id left join items i on i.id = x.item_id
                where x.deleted_at is null and x.status = 'completed'""" + (propertyOnly ? " and x.disposal_category = 'property'" : "") + when + " order by x.disposal_date, x.disposal_number", args.toArray())) {
            boolean asset = r.get("tag_number") != null;
            int qty = asset || r.get("quantity") == null ? 1 : ((Number) r.get("quantity")).intValue();
            BigDecimal cost = money(asset ? r.get("acquisition_cost") : r.get("unit_cost"));
            t.row(r.get("disposal_number"), date(r.get("disposal_date")), asset ? r.get("tag_number") : r.get("code"), r.get("item_name"), label(r.get("disposal_type")), qty, cost,
                    cost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP), r.get("reason"));
            n++;
        }
        t.total("TOTAL (" + n + ")");
        t.sign("Certified by:", signatories.resolve("property_officer"), null);
        return t;
    }

    /**
     * Assets reported lost or damaged in the period: on a gate pass return (with its date), or marked lost on the
     * asset (dated by its last update, as the status keeps no date of its own).
     */
    public TabularReport lostDamaged(Period period, String entity) {
        TabularReport t = new TabularReport("RLSDDP Register", "LOST, STOLEN, DAMAGED OR DESTROYED PROPERTY (RLSDDP)").entity(entity).subtitle(period.label())
                .col("Date", Kind.DATE, 11).col("Property No.", Kind.TEXT, 18).col("Description", Kind.TEXT, 32).col("Accountable Officer", Kind.TEXT, 22).col("Office", Kind.TEXT, 22)
                .col("Reported as", Kind.TEXT, 12).col("Source", Kind.TEXT, 20).col("Acquisition Cost", Kind.MONEY, 13);
        List<Dated> lines = new ArrayList<>();
        Map<Object, Boolean> onPass = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                select g.property_id, g.gate_pass_number, g.actual_return_date, g.condition_on_return, p.tag_number, i.name, p.acquisition_cost, u.name as holder, d.name as office
                from gate_passes g join properties p on p.id = g.property_id join items i on i.id = p.item_id
                left join users u on u.id = p.assigned_to left join departments d on d.id = u.department_id
                where g.deleted_at is null and g.condition_on_return in ('lost', 'damaged')""")) {
            onPass.put(((Number) r.get("property_id")).longValue(), true);
            LocalDate when = date(r.get("actual_return_date"));
            if (within(when, period)) {
                lines.add(new Dated(when, new Object[]{when, r.get("tag_number"), r.get("name"), r.get("holder"), r.get("office"), label(r.get("condition_on_return")),
                        "Gate pass " + r.get("gate_pass_number"), money(r.get("acquisition_cost"))}));
            }
        }
        for (Map<String, Object> r : jdbc.queryForList("""
                select p.id, p.updated_at, p.tag_number, i.name, p.acquisition_cost, u.name as holder, d.name as office
                from properties p join items i on i.id = p.item_id left join users u on u.id = p.assigned_to left join departments d on d.id = u.department_id
                where p.status = 'lost'""")) {
            if (onPass.containsKey(((Number) r.get("id")).longValue())) continue;
            LocalDate when = date(r.get("updated_at"));
            if (within(when, period)) {
                lines.add(new Dated(when, new Object[]{when, r.get("tag_number"), r.get("name"), r.get("holder"), r.get("office"), "Lost", "Asset record", money(r.get("acquisition_cost"))}));
            }
        }
        write(t, lines);
        t.total("TOTAL (" + lines.size() + ")");
        t.note("The RLSDDP form itself (Appendix 75) is prepared per asset under Reports → Lost / Damaged Property.");
        return t;
    }

    // ───────────────────────── gate passes and transfers ─────────────────────────

    public TabularReport gatePasses(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.on("g.date_released", args);
        TabularReport t = new TabularReport("Gate Passes", "GATE PASSES ISSUED").entity(entity).subtitle(period.label())
                .col("Gate Pass No.", Kind.TEXT, 18).col("Released", Kind.DATE, 11).col("Property No.", Kind.TEXT, 18).col("Description", Kind.TEXT, 30).col("Carried out by", Kind.TEXT, 20)
                .col("Purpose", Kind.TEXT, 28).col("Expected Back", Kind.DATE, 11).col("Returned", Kind.DATE, 11).col("Status", Kind.TEXT, 10);
        for (Map<String, Object> r : jdbc.queryForList("""
                select g.gate_pass_number, g.date_released, g.received_by, g.purpose, g.expected_return_date, g.actual_return_date, g.status, p.tag_number, i.name
                from gate_passes g join properties p on p.id = g.property_id join items i on i.id = p.item_id
                where g.deleted_at is null""" + when + " order by g.date_released, g.gate_pass_number", args.toArray())) {
            t.row(r.get("gate_pass_number"), date(r.get("date_released")), r.get("tag_number"), r.get("name"), r.get("received_by"), r.get("purpose"),
                    date(r.get("expected_return_date")), date(r.get("actual_return_date")), label(r.get("status")));
        }
        return t;
    }

    /** Request for Transfer of Accountability (TOR 1.6.4.13): each change of accountable officer in the period, with its reason. */
    public TabularReport transfers(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.on("t.transfer_date", args);
        TabularReport t = new TabularReport("Transfer of Accountability", "TRANSFERS OF ACCOUNTABILITY (RTA)").entity(entity).subtitle(period.label())
                .col("Reference", Kind.TEXT, 18).col("Date", Kind.DATE, 11).col("Property No.", Kind.TEXT, 18).col("Description", Kind.TEXT, 30).col("From", Kind.TEXT, 22).col("To", Kind.TEXT, 22)
                .col("Reason", Kind.TEXT, 30).col("Approved by", Kind.TEXT, 20).col("Acquisition Cost", Kind.MONEY, 13);
        int n = 0;
        for (Map<String, Object> r : jdbc.queryForList("""
                select t.reference_number, t.transfer_date, t.transfer_reason, p.tag_number, i.name, p.acquisition_cost, f.name as from_name, u.name as to_name, a.name as approver
                from property_transfers t join properties p on p.id = t.property_id join items i on i.id = p.item_id
                left join users f on f.id = t.from_user left join users u on u.id = t.to_user left join users a on a.id = t.approved_by
                where t.transfer_type = 'transfer'""" + when + " order by t.transfer_date, t.id", args.toArray())) {
            t.row(r.get("reference_number"), date(r.get("transfer_date")), r.get("tag_number"), r.get("name"), r.get("from_name"), r.get("to_name"), r.get("transfer_reason"),
                    r.get("approver"), money(r.get("acquisition_cost")));
            n++;
        }
        t.total("TOTAL (" + n + ")");
        t.sign("Prepared by:", signatories.resolve("property_officer"), null);
        return t;
    }

    // ───────────────────────── reorder point ─────────────────────────

    /** Items at or below their reorder point now, with how much was issued in the period and what to order. */
    public TabularReport reorderPoint(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.onTimestamp("l.created_at", args);
        TabularReport t = new TabularReport("Reordering Point", "REORDERING POINT").entity(entity).subtitle("Stock as of today · issues: " + period.label().toLowerCase())
                .col("Stock No.", Kind.TEXT, 14).col("Item", Kind.TEXT, 34).col("Category", Kind.TEXT, 20).col("Unit", Kind.TEXT, 8).col("On Hand", Kind.INT, 9).col("Reorder Point", Kind.INT, 9)
                .col("Maximum", Kind.INT, 9).col("Issued in Period", Kind.INT, 9).col("Suggested Order", Kind.INT, 10).col("Unit Cost", Kind.MONEY, 13).col("Order Value", Kind.MONEY, 13);
        for (Map<String, Object> r : jdbc.queryForList("select i.code, i.name, c.name as category, i.unit_of_measure, i.current_stock, i.min_stock, i.max_stock, i.is_expandable, i.weighted_average_cost, "
                + "(select coalesce(sum(abs(l.quantity)), 0) from inventory_logs l where l.item_id = i.id and l.action = 'item_issued'" + when + ") as issued "
                + "from items i left join categories c on c.id = i.category_id where i.min_stock > 0 and i.current_stock <= i.min_stock order by i.name, i.id", args.toArray())) {
            int current = ((Number) r.get("current_stock")).intValue();
            int min = ((Number) r.get("min_stock")).intValue();
            int max = r.get("max_stock") == null ? 0 : ((Number) r.get("max_stock")).intValue();
            int order = Item.suggestedOrder(current, min, max, truthy(r.get("is_expandable")));
            BigDecimal cost = money(r.get("weighted_average_cost"));
            t.row(r.get("code"), r.get("name"), r.get("category"), r.get("unit_of_measure"), current, min, r.get("max_stock"), r.get("issued"), order, cost,
                    cost.multiply(BigDecimal.valueOf(order)).setScale(2, RoundingMode.HALF_UP));
        }
        t.total("TOTAL");
        t.sign("Prepared by:", signatories.resolve("supply_officer"), null);
        return t;
    }

    // ───────────────────────── helpers ─────────────────────────

    /** An empty side of a move is the property office (returns and first issues). */
    private static Object office(Object name) {
        return name == null ? "Property office" : name;
    }

    private static boolean truthy(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0);
    }

    private static LocalDate date(Object v) {
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return TorListReports.date(v);
    }

    private static BigDecimal money(Object v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String label(Object v) {
        if (v == null) return null;
        String s = v.toString().replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

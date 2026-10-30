package com.avtech.pams.report;

import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import com.avtech.pams.property.DepreciationService;
import com.avtech.pams.property.Property;
import com.avtech.pams.property.PropertyRepository;
import com.avtech.pams.report.coa.PpeLedgerCardForm;
import com.avtech.pams.report.coa.Signatories;
import com.avtech.pams.report.coa.TabularReport;
import com.avtech.pams.report.coa.TabularReport.Kind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The list-type reports the Terms of Reference ask for — the depreciation lapsing schedule, the
 * Property Inventory Form, the IAR / RIS / purchase order registers and the master lists — each
 * built once as a {@link TabularReport} and written as Excel, CSV or PDF by the caller.
 * Date ranges are inclusive and optional on the master lists; amounts are computed in Java.
 */
@Component
public class TorListReports {

    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String REQUEST = "App\\Models\\Request";

    private final JdbcTemplate jdbc;
    private final Db db;
    private final PropertyRepository properties;
    private final DepreciationService depreciation;
    private final Signatories signatories;

    public TorListReports(JdbcTemplate jdbc, Db db, PropertyRepository properties, DepreciationService depreciation, Signatories signatories) {
        this.jdbc = jdbc;
        this.db = db;
        this.properties = properties;
        this.depreciation = depreciation;
        this.signatories = signatories;
    }

    /** An inclusive date range; either end may be open. */
    public record Period(LocalDate from, LocalDate to) {

        public boolean any() {
            return from != null || to != null;
        }

        public String label() {
            if (from != null && to != null) return "For the period " + from.format(MDY) + " to " + to.format(MDY);
            if (from != null) return "From " + from.format(MDY);
            if (to != null) return "Up to " + to.format(MDY);
            return "All records";
        }

        /** {@code and <expr> >= ? and <expr> <= ?} for a date expression, adding the bound values to args. */
        String on(String dateExpr, List<Object> args) {
            StringBuilder s = new StringBuilder();
            if (from != null) {
                s.append(" and ").append(dateExpr).append(" >= ?");
                args.add(from);
            }
            if (to != null) {
                s.append(" and ").append(dateExpr).append(" <= ?");
                args.add(to);
            }
            return s.toString();
        }

        /** The same for a timestamp column: from midnight of {@code from} to before midnight after {@code to}. */
        String onTimestamp(String column, List<Object> args) {
            StringBuilder s = new StringBuilder();
            if (from != null) {
                s.append(" and ").append(column).append(" >= ?");
                args.add(Timestamp.valueOf(from.atStartOfDay()));
            }
            if (to != null) {
                s.append(" and ").append(column).append(" < ?");
                args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            }
            return s.toString();
        }
    }

    // ───────────────────────── depreciation ─────────────────────────

    /** Depreciation lapsing schedule of all depreciable PPE in service at {@code asAt}, by asset category. */
    @Transactional(readOnly = true)
    public TabularReport lapsingSchedule(LocalDate asAt, String entity) {
        LocalDate at = asAt != null ? asAt : LocalDate.now(Times.ZONE);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.id, p.tag_number, p.serial_number, i.name as item_name, coalesce(c.name, 'Uncategorized') as category_name,
                       coalesce(p.acquisition_date, p.assigned_date) as acquired
                from properties p join items i on i.id = p.item_id left join categories c on c.id = i.category_id
                where p.status <> 'disposed' and p.acquisition_cost > 0
                order by coalesce(c.name, 'Uncategorized'), p.tag_number, p.id""");
        Map<Long, Property> byId = new HashMap<>();
        properties.findAllById(rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList()).forEach(p -> byId.put(p.getId(), p));

        TabularReport t = new TabularReport("Lapsing Schedule", "SCHEDULE OF DEPRECIATION (LAPSING SCHEDULE)")
                .entity(entity)
                .subtitle("Property, Plant and Equipment as at " + at.format(MDY))
                .col("Property No.", Kind.TEXT, 16).col("Description", Kind.TEXT, 30).col("Date Acquired", Kind.DATE, 11)
                .col("Acquisition Cost", Kind.MONEY, 13).col("Residual Value", Kind.MONEY, 11).col("Useful Life (yrs)", Kind.NUMBER, 8)
                .col("Monthly Depreciation", Kind.MONEY, 12).col("Annual Depreciation", Kind.MONEY, 12)
                .col("Depreciation This Year", Kind.MONEY, 12).col("Accumulated Depreciation", Kind.MONEY, 13).col("Book Value", Kind.MONEY, 13);
        String group = null;
        for (Map<String, Object> r : rows) {
            Property p = byId.get(((Number) r.get("id")).longValue());
            LocalDate acquired = date(r.get("acquired"));
            if (p == null || acquired == null || acquired.isAfter(at)) continue;
            String cat = (String) r.get("category_name");
            if (!cat.equals(group)) {
                if (group != null) t.subtotal("Subtotal — " + group);
                t.group(cat);
                group = cat;
            }
            BigDecimal cost = p.getAcquisitionCost();
            boolean dep = p.isDepreciable();
            BigDecimal monthly = dep ? depreciation.monthly(p) : BigDecimal.ZERO;
            BigDecimal acc = PpeLedgerCardForm.accumulatedAt(depreciation, p, at);
            BigDecimal prior = PpeLedgerCardForm.accumulatedAt(depreciation, p, LocalDate.of(at.getYear() - 1, 12, 31));
            String serial = p.getSerialNumber();
            t.row(p.getTagNumber(), r.get("item_name") + (serial != null && !serial.isBlank() ? " (S/N " + serial + ")" : ""), acquired,
                    cost, dep ? depreciation.residual(p) : BigDecimal.ZERO, p.getUsefulLifeYears(), monthly,
                    monthly.multiply(BigDecimal.valueOf(12)), acc.subtract(prior), acc, cost.subtract(acc).setScale(2, RoundingMode.HALF_UP));
        }
        if (group != null) t.subtotal("Subtotal — " + group);
        t.total("GRAND TOTAL");
        t.note("Straight-line method; residual value is 5% of cost unless set on the asset; depreciation starts the month after acquisition "
                + "and is recognised per full calendar month through the as-at date (COA GAM).");
        t.sign("Prepared by:", signatories.resolve("accountant"), "Accountant / Bookkeeper");
        t.sign("Certified Correct:", signatories.resolve("property_officer"), "Property/Supply Officer");
        t.sign("Approved:", signatories.resolve("head_of_agency"), null);
        return t;
    }

    // ───────────────────────── Property Inventory Form ─────────────────────────

    /** Property Inventory Form: the assets held by one accountable officer (or every asset not disposed). */
    public TabularReport propertyInventory(Long userId, String entity) {
        List<Object> args = new ArrayList<>();
        String who = "";
        String officer = null;
        if (userId != null) {
            who = " and p.assigned_to = ?";
            args.add(userId);
            List<String> n = jdbc.queryForList("select name from users where id = ?", String.class, userId);
            officer = n.isEmpty() ? null : n.get(0);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.tag_number, p.serial_number, p.brand, p.condition, p.status, p.acquisition_cost, p.accountability_number,
                       coalesce(s.received_date, p.acquisition_date) as acquired, s.location, i.name as item_name,
                       u.name as holder, d.name as department_name
                from properties p left join items i on i.id = p.item_id left join stocks s on s.id = p.stock_id
                left join users u on u.id = p.assigned_to left join departments d on d.id = u.department_id
                where p.status <> 'disposed'""" + who + " order by u.name, p.tag_number, p.id", args.toArray());

        TabularReport t = new TabularReport("PIF", "PROPERTY INVENTORY FORM")
                .entity(entity)
                .subtitle(officer != null ? "Accountable Officer: " + officer : "All accountable officers")
                .subtitle("As at " + LocalDate.now(Times.ZONE).format(MDY))
                .col("Property No.", Kind.TEXT, 16).col("Description", Kind.TEXT, 30).col("PAR/ICS No.", Kind.TEXT, 15)
                .col("Date Acquired", Kind.DATE, 11).col("Acquisition Cost", Kind.MONEY, 13);
        if (officer == null) t.col("Accountable Officer", Kind.TEXT, 18);
        t.col("Location", Kind.TEXT, 14).col("Condition", Kind.TEXT, 10).col("Remarks", Kind.TEXT, 16);
        for (Map<String, Object> r : rows) {
            String serial = str(r.get("serial_number"));
            String brand = str(r.get("brand"));
            String desc = str(r.get("item_name")) + (brand.isEmpty() ? "" : ", " + brand) + (serial.isEmpty() ? "" : " (S/N " + serial + ")");
            String location = r.get("location") != null ? str(r.get("location")) : str(r.get("department_name"));
            String remark = "assigned".equals(r.get("status")) ? "" : ucfirst(str(r.get("status")).replace('_', ' '));
            if (officer == null) {
                t.row(r.get("tag_number"), desc, r.get("accountability_number"), date(r.get("acquired")), r.get("acquisition_cost"),
                        r.get("holder"), location, ucfirst(str(r.get("condition"))), remark);
            } else {
                t.row(r.get("tag_number"), desc, r.get("accountability_number"), date(r.get("acquired")), r.get("acquisition_cost"),
                        location, ucfirst(str(r.get("condition"))), remark);
            }
        }
        t.total("TOTAL (" + rows.size() + " item" + (rows.size() == 1 ? "" : "s") + ")");
        t.note("CERTIFICATION: I hereby certify that the property listed above is in my custody and was physically counted and inspected on the "
                + "date indicated, and that its condition is as stated. I shall be accountable for it in accordance with COA rules and regulations.");
        t.sign("Accountable Officer:", new Signatories.Signatory(officer, "Accountable Officer", null, null), "Signature over Printed Name");
        t.sign("Inventoried by:", signatories.resolve("property_officer"), "Property/Supply Officer");
        t.sign("Noted by:", signatories.resolve("inventory_committee_chair"), null);
        return t;
    }

    // ───────────────────────── registers ─────────────────────────

    /** IAR monitoring: inspection and acceptance reports by inspection (else delivery) date, with the accepted amount. */
    public TabularReport iarRegister(Period period, Long supplierId, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.on("coalesce(r.inspection_date, r.delivery_date, " + db.date("r.created_at") + ")", args);
        if (supplierId != null) {
            when += " and po.supplier_id = ?";
            args.add(supplierId);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select r.iar_number, r.delivery_date, r.inspection_date, r.status, r.acceptance_status, r.accepted_at,
                       r.total_items_received, r.total_items_accepted, r.total_items_rejected,
                       po.po_number, s.name as supplier_name, iu.name as inspector,
                       (select sum(ii.quantity_accepted * poi.unit_cost) from iar_items ii
                          left join purchase_order_items poi on poi.id = ii.purchase_order_item_id
                         where ii.inspection_acceptance_report_id = r.id) as accepted_amount
                from inspection_acceptance_reports r
                left join purchase_orders po on po.id = r.purchase_order_id
                left join suppliers s on s.id = po.supplier_id
                left join users iu on iu.id = r.inspected_by
                where 1 = 1""" + when + " order by coalesce(r.inspection_date, r.delivery_date), r.iar_number", args.toArray());
        TabularReport t = new TabularReport("IAR Monitoring", "INSPECTION AND ACCEPTANCE REPORT (IAR) MONITORING")
                .entity(entity).subtitle(period.label())
                .col("IAR No.", Kind.TEXT, 15).col("PO No.", Kind.TEXT, 14).col("Supplier", Kind.TEXT, 24)
                .col("Delivery Date", Kind.DATE, 11).col("Inspection Date", Kind.DATE, 11).col("Inspected by", Kind.TEXT, 16)
                .col("Inspection", Kind.TEXT, 10).col("Acceptance", Kind.TEXT, 10).col("Date Accepted", Kind.DATE, 11)
                .col("Qty Received", Kind.INT, 9).col("Qty Accepted", Kind.INT, 9).col("Qty Rejected", Kind.INT, 9)
                .col("Amount Accepted", Kind.MONEY, 13);
        for (Map<String, Object> r : rows) {
            t.row(r.get("iar_number"), r.get("po_number"), r.get("supplier_name"), date(r.get("delivery_date")), date(r.get("inspection_date")),
                    r.get("inspector"), label(r.get("status")), label(r.get("acceptance_status")), date(r.get("accepted_at")),
                    r.get("total_items_received"), r.get("total_items_accepted"), r.get("total_items_rejected"), money(r.get("accepted_amount")));
        }
        t.total("TOTAL (" + rows.size() + " IAR" + (rows.size() == 1 ? "" : "s") + ")");
        t.sign("Prepared by:", signatories.resolve("supply_officer"), null);
        t.sign("Noted by:", signatories.resolve("inspector"), null);
        return t;
    }

    /** RIS monitoring: requests given an RIS number, by RIS (else request) date, with when they were processed and issued. */
    public TabularReport risRegister(Period period, Long departmentId, String entity) {
        List<Object> args = new ArrayList<>(List.of(REQUEST, REQUEST));
        String when = period.on("coalesce(r.ris_date, r.request_date)", args);
        if (departmentId != null) {
            when += " and r.department_id = ?";
            args.add(departmentId);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select r.id, r.ris_number, r.request_number, r.request_date, r.ris_date, r.status, r.purpose,
                       d.name as department_name, u.name as requester_name,
                       (select min(l.created_at) from inventory_logs l where l.reference_type = ? and l.reference_id = r.id and l.action = 'request_items_processed') as approved_at,
                       (select min(l.created_at) from inventory_logs l where l.reference_type = ? and l.reference_id = r.id and l.action = 'item_issued') as issued_at,
                       (select count(*) from request_items ri where ri.request_id = r.id) as line_count,
                       (select sum(coalesce(ri.approved_quantity, ri.quantity) * coalesce(ri.unit_cost, i.weighted_average_cost))
                          from request_items ri join items i on i.id = ri.item_id where ri.request_id = r.id) as amount
                from requests r
                left join departments d on d.id = r.department_id
                left join users u on u.id = r.requested_by
                where r.ris_number is not null""" + when + " order by coalesce(r.ris_date, r.request_date), r.ris_number", args.toArray());
        TabularReport t = new TabularReport("RIS Monitoring", "REQUISITION AND ISSUE SLIP (RIS) MONITORING")
                .entity(entity).subtitle(period.label())
                .col("RIS No.", Kind.TEXT, 16).col("Request No.", Kind.TEXT, 16).col("Office", Kind.TEXT, 20).col("Requested by", Kind.TEXT, 16)
                .col("Date Requested", Kind.DATE, 11).col("Date Approved", Kind.DATE, 11).col("Date Issued", Kind.DATE, 11)
                .col("Status", Kind.TEXT, 10).col("Line Items", Kind.INT, 7).col("Amount", Kind.MONEY, 13).col("Purpose", Kind.TEXT, 24);
        for (Map<String, Object> r : rows) {
            t.row(r.get("ris_number"), r.get("request_number"), r.get("department_name"), r.get("requester_name"), date(r.get("request_date")),
                    date(r.get("approved_at")), date(r.get("issued_at")), label(r.get("status")), r.get("line_count"), money(r.get("amount")), r.get("purpose"));
        }
        t.total("TOTAL (" + rows.size() + " RIS)");
        t.sign("Prepared by:", signatories.resolve("supply_officer"), null);
        return t;
    }

    /** Summary of purchase orders by order date: supplier, procurement mode, amount, status and what has been delivered. */
    public TabularReport purchaseOrders(Period period, Long supplierId, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.on("coalesce(po.order_date, " + db.date("po.created_at") + ")", args);
        if (supplierId != null) {
            when += " and po.supplier_id = ?";
            args.add(supplierId);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select po.po_number, po.order_date, po.delivery_date, po.status, po.total_amount, po.rfq_id, po.bidding_id, po.ps_order_id,
                       s.name as supplier_name, pr.pr_number, rq.procurement_method,
                       (select sum(poi.quantity) from purchase_order_items poi where poi.purchase_order_id = po.id) as qty_ordered,
                       (select sum(ii.quantity_accepted) from iar_items ii join inspection_acceptance_reports r on r.id = ii.inspection_acceptance_report_id
                         where r.purchase_order_id = po.id) as qty_accepted,
                       (select max(r.delivery_date) from inspection_acceptance_reports r where r.purchase_order_id = po.id) as delivered_on
                from purchase_orders po
                left join suppliers s on s.id = po.supplier_id
                left join purchase_requests pr on pr.id = po.purchase_request_id
                left join rfqs rq on rq.id = po.rfq_id
                where po.deleted_at is null""" + when + " order by coalesce(po.order_date, " + db.date("po.created_at") + "), po.po_number", args.toArray());
        TabularReport t = new TabularReport("PO Summary", "SUMMARY OF PURCHASE ORDERS")
                .entity(entity).subtitle(period.label())
                .col("PO No.", Kind.TEXT, 14).col("PO Date", Kind.DATE, 11).col("PR No.", Kind.TEXT, 14).col("Supplier", Kind.TEXT, 24)
                .col("Mode of Procurement", Kind.TEXT, 18).col("Amount", Kind.MONEY, 14).col("Status", Kind.TEXT, 12)
                .col("Qty Ordered", Kind.INT, 8).col("Qty Delivered", Kind.INT, 8).col("Delivered", Kind.TEXT, 9).col("Last Delivery", Kind.DATE, 11);
        for (Map<String, Object> r : rows) {
            long ordered = r.get("qty_ordered") == null ? 0 : ((Number) r.get("qty_ordered")).longValue();
            long accepted = r.get("qty_accepted") == null ? 0 : ((Number) r.get("qty_accepted")).longValue();
            String delivered = accepted <= 0 ? "No" : accepted >= ordered ? "Yes" : "Partial";
            t.row(r.get("po_number"), date(r.get("order_date")), r.get("pr_number"), r.get("supplier_name"), mode(r), money(r.get("total_amount")),
                    label(r.get("status")), ordered, accepted, delivered, date(r.get("delivered_on")));
        }
        t.total("TOTAL (" + rows.size() + " PO" + (rows.size() == 1 ? "" : "s") + ")");
        t.sign("Prepared by:", signatories.resolve("supply_officer"), null);
        t.sign("Noted by:", signatories.resolve("bac_chairperson"), null);
        return t;
    }

    private static String mode(Map<String, Object> po) {
        if (po.get("ps_order_id") != null) return "Procurement from PS-DBM";
        if (po.get("bidding_id") != null) return "Competitive Bidding";
        if (po.get("rfq_id") != null) return po.get("procurement_method") != null ? label(po.get("procurement_method")) : "Alternative (RFQ)";
        return "Direct";
    }

    // ───────────────────────── master lists ─────────────────────────

    /** Every asset (PPE and semi-expendable property), optionally those acquired in the period. */
    public TabularReport assets(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.on("coalesce(p.acquisition_date, " + db.date("p.created_at") + ")", args);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.tag_number, p.serial_number, p.brand, p.status, p.condition, p.acquisition_cost, p.current_book_value, p.useful_life_years,
                       p.accountability_number, coalesce(p.acquisition_date, p.assigned_date) as acquired, s.location,
                       i.name as item_name, i.is_semi_expandable, c.name as category_name, u.name as holder, d.name as department_name
                from properties p left join items i on i.id = p.item_id left join categories c on c.id = i.category_id
                left join stocks s on s.id = p.stock_id left join users u on u.id = p.assigned_to left join departments d on d.id = u.department_id
                where 1 = 1""" + when + " order by p.tag_number, p.id", args.toArray());
        TabularReport t = new TabularReport("Asset Master List", "ASSET MASTER LIST").entity(entity).subtitle(period.any() ? "Acquired: " + period.label() : null)
                .col("Property No.", Kind.TEXT, 16).col("Description", Kind.TEXT, 26).col("Category", Kind.TEXT, 14).col("Class", Kind.TEXT, 10)
                .col("Serial No.", Kind.TEXT, 14).col("Brand", Kind.TEXT, 10).col("Date Acquired", Kind.DATE, 11).col("Acquisition Cost", Kind.MONEY, 13)
                .col("Book Value", Kind.MONEY, 13).col("Useful Life", Kind.NUMBER, 7).col("Accountable Officer", Kind.TEXT, 16).col("Office", Kind.TEXT, 16)
                .col("Location", Kind.TEXT, 12).col("PAR/ICS No.", Kind.TEXT, 14).col("Status", Kind.TEXT, 10).col("Condition", Kind.TEXT, 9);
        for (Map<String, Object> r : rows) {
            Object book = r.get("current_book_value") != null ? r.get("current_book_value") : r.get("acquisition_cost");
            t.row(r.get("tag_number"), r.get("item_name"), r.get("category_name"), flag(r.get("is_semi_expandable")) ? "Semi-expendable" : "PPE",
                    r.get("serial_number"), r.get("brand"), date(r.get("acquired")), money(r.get("acquisition_cost")), money(book), r.get("useful_life_years"),
                    r.get("holder"), r.get("department_name"), r.get("location"), r.get("accountability_number"), label(r.get("status")), label(r.get("condition")));
        }
        t.total("TOTAL (" + rows.size() + " asset" + (rows.size() == 1 ? "" : "s") + ")");
        return t;
    }

    /** The item catalogue with re-order point, stock on hand, unit cost and value; optionally items created in the period. */
    public TabularReport items(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.onTimestamp("i.created_at", args);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select i.code, i.name, i.description, i.unit_of_measure, i.min_stock, i.max_stock, i.current_stock, i.weighted_average_cost,
                       i.is_expandable, i.is_semi_expandable, c.name as category_name, i.created_at
                from items i left join categories c on c.id = i.category_id
                where 1 = 1""" + when + " order by i.code, i.id", args.toArray());
        TabularReport t = new TabularReport("Item Master List", "SUPPLIES AND MATERIALS (ITEM) MASTER LIST").entity(entity)
                .subtitle(period.any() ? "Created: " + period.label() : null)
                .col("Item Code", Kind.TEXT, 12).col("Item", Kind.TEXT, 28).col("Category", Kind.TEXT, 16).col("Classification", Kind.TEXT, 13)
                .col("Unit", Kind.TEXT, 8).col("Re-order Point", Kind.NUMBER, 8).col("Maximum", Kind.NUMBER, 8).col("On Hand", Kind.INT, 8)
                .col("Unit Cost (Avg.)", Kind.MONEY, 12).col("Total Value", Kind.MONEY, 13).col("Stock Status", Kind.TEXT, 11).col("Date Created", Kind.DATE, 11);
        for (Map<String, Object> r : rows) {
            int onHand = r.get("current_stock") == null ? 0 : ((Number) r.get("current_stock")).intValue();
            int min = r.get("min_stock") == null ? 0 : ((Number) r.get("min_stock")).intValue();
            BigDecimal cost = money(r.get("weighted_average_cost"));
            t.row(r.get("code"), r.get("name"), r.get("category_name"), classification(r), r.get("unit_of_measure"), r.get("min_stock"), r.get("max_stock"),
                    onHand, cost, cost.multiply(BigDecimal.valueOf(onHand)), onHand <= 0 ? "Out of stock" : onHand <= min ? "Re-order" : "In stock",
                    date(r.get("created_at")));
        }
        t.total("TOTAL (" + rows.size() + " item" + (rows.size() == 1 ? "" : "s") + ")");
        return t;
    }

    /** Consumable supplies with stock on hand, most-issued first: what end users can requisition now. */
    public TabularReport availableSupplies(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.onTimestamp("l.created_at", args);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select i.id, i.code, i.name, i.unit_of_measure, i.current_stock, i.min_stock, i.weighted_average_cost, c.name as category_name,
                       (select coalesce(sum(l.quantity), 0) from inventory_logs l where l.item_id = i.id and l.action = 'item_issued'""" + when + """
                       ) as issued,
                       (select count(*) from inventory_logs l where l.item_id = i.id and l.action = 'item_issued'""" + when + """
                       ) as times_issued
                from items i left join categories c on c.id = i.category_id
                where i.current_stock > 0 and i.is_expandable = ? and i.is_semi_expandable = ?
                order by i.name""", concat(args, args, false, false));
        rows = new ArrayList<>(rows);
        rows.sort((a, b) -> Long.compare(((Number) b.get("issued")).longValue(), ((Number) a.get("issued")).longValue()));
        TabularReport t = new TabularReport("Available Supplies", "LIST OF AVAILABLE AND COMMONLY-USED SUPPLIES").entity(entity)
                .subtitle("Stock on hand as at " + LocalDate.now(Times.ZONE).format(MDY))
                .subtitle("Usage: " + (period.any() ? period.label() : "all issues on record"))
                .col("Item Code", Kind.TEXT, 12).col("Item", Kind.TEXT, 32).col("Category", Kind.TEXT, 16).col("Unit", Kind.TEXT, 8)
                .col("On Hand", Kind.INT, 9).col("Re-order Point", Kind.NUMBER, 9).col("Unit Cost (Avg.)", Kind.MONEY, 12)
                .col("Qty Issued", Kind.INT, 9).col("Times Issued", Kind.INT, 8).col("Commonly Used", Kind.TEXT, 9);
        for (Map<String, Object> r : rows) {
            long issued = ((Number) r.get("issued")).longValue();
            long times = ((Number) r.get("times_issued")).longValue();
            t.row(r.get("code"), r.get("name"), r.get("category_name"), r.get("unit_of_measure"), r.get("current_stock"), r.get("min_stock"),
                    money(r.get("weighted_average_cost")), issued, times, times >= 2 ? "Yes" : "");
        }
        t.note("Consumable items with stock on hand, most issued first. \"Commonly used\" marks items issued on two or more requisitions in the period.");
        return t;
    }

    /** Suppliers on file, optionally those registered in the period. */
    public TabularReport suppliers(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.onTimestamp("s.created_at", args);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select s.vendor_code, s.name, s.contact_person, s.email, s.phone, s.mobile_number, s.address, s.tin, s.philgeps_reg_number,
                       s.credit_terms, s.is_eligible, s.is_msme, s.is_active, s.created_at
                from suppliers s where 1 = 1""" + when + " order by s.name, s.id", args.toArray());
        TabularReport t = new TabularReport("Supplier Master List", "SUPPLIER MASTER LIST").entity(entity)
                .subtitle(period.any() ? "Registered: " + period.label() : null)
                .col("Vendor Code", Kind.TEXT, 11).col("Supplier", Kind.TEXT, 26).col("Contact Person", Kind.TEXT, 16).col("Email", Kind.TEXT, 20)
                .col("Phone / Mobile", Kind.TEXT, 15).col("Address", Kind.TEXT, 26).col("TIN", Kind.TEXT, 13).col("PhilGEPS Reg. No.", Kind.TEXT, 13)
                .col("Credit Terms", Kind.TEXT, 10).col("Eligible", Kind.TEXT, 7).col("MSME", Kind.TEXT, 6).col("Active", Kind.TEXT, 6).col("Date Registered", Kind.DATE, 11);
        for (Map<String, Object> r : rows) {
            String phone = String.join(" / ", java.util.stream.Stream.of(str(r.get("phone")), str(r.get("mobile_number"))).filter(s -> !s.isEmpty()).toList());
            t.row(r.get("vendor_code"), r.get("name"), r.get("contact_person"), r.get("email"), phone, r.get("address"), r.get("tin"),
                    r.get("philgeps_reg_number"), r.get("credit_terms"), yes(r.get("is_eligible")), yes(r.get("is_msme")), yes(r.get("is_active")), date(r.get("created_at")));
        }
        return t;
    }

    /** User accounts (no credentials): role, office, status; optionally accounts created in the period. */
    public TabularReport users(Period period, String entity) {
        List<Object> args = new ArrayList<>();
        String when = period.onTimestamp("u.created_at", args);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select u.name, u.email, u.is_active, u.two_factor_enabled, u.last_seen_at, u.created_at, r.name as role_name, d.name as department_name
                from users u left join roles r on r.id = u.role_id left join departments d on d.id = u.department_id
                where 1 = 1""" + when + " order by u.name, u.id", args.toArray());
        TabularReport t = new TabularReport("User Master List", "USER MASTER LIST").entity(entity)
                .subtitle(period.any() ? "Created: " + period.label() : null)
                .col("Name", Kind.TEXT, 22).col("Email", Kind.TEXT, 28).col("Role", Kind.TEXT, 18).col("Office", Kind.TEXT, 22)
                .col("Active", Kind.TEXT, 7).col("Two-factor", Kind.TEXT, 8).col("Last Active", Kind.DATE, 11).col("Date Created", Kind.DATE, 11);
        for (Map<String, Object> r : rows) {
            t.row(r.get("name"), r.get("email"), r.get("role_name"), r.get("department_name"), yes(r.get("is_active")), yes(r.get("two_factor_enabled")),
                    date(r.get("last_seen_at")), date(r.get("created_at")));
        }
        return t;
    }

    // ───────────────────────── helpers ─────────────────────────

    private static Object[] concat(List<Object> a, List<Object> b, Object... tail) {
        List<Object> all = new ArrayList<>(a);
        all.addAll(b);
        all.addAll(List.of(tail));
        return all.toArray();
    }

    private static String classification(Map<String, Object> item) {
        if (flag(item.get("is_expandable"))) return "PPE";
        if (flag(item.get("is_semi_expandable"))) return "Semi-expendable";
        return "Consumable";
    }

    static boolean flag(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0);
    }

    private static String yes(Object v) {
        return flag(v) ? "Yes" : "No";
    }

    static LocalDate date(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.time.LocalDateTime t) return t.toLocalDate();
        if (v instanceof java.time.OffsetDateTime t) return t.toLocalDate();
        return v instanceof LocalDate d ? d : null;
    }

    private static BigDecimal money(Object v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String ucfirst(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "partially_received" → "Partially received". */
    private static String label(Object v) {
        return v == null ? "" : ucfirst(v.toString().replace('_', ' '));
    }
}

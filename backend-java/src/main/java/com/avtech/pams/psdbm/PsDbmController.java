package com.avtech.pams.psdbm;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.procurement.PrLineUsage;
import com.avtech.pams.procurement.ProcurementRules;
import com.avtech.pams.purchasing.PurchaseOrder;
import com.avtech.pams.purchasing.PurchaseOrderItem;
import com.avtech.pams.purchasing.PurchaseOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Common-use supplies and equipment (CSE) bought from the Procurement Service of the DBM, without
 * bidding (LOI 755, EO 359 s. 1989, AO 17 s. 2011). The PS price catalogue; and the Agency
 * Procurement Request (APR) raised from the PS-DBM lines of an approved PR: placed through the
 * PhilGEPS Virtual Store, paid ahead (cash, LDDAP-ADA, check, or funds already with PS), then
 * delivered or picked up against a PS Delivery Receipt. Recording the DR opens a confirmed order to
 * PS-DBM, so the delivery is inspected and taken into stock through Receiving like any other.
 * A line PS cannot supply takes PS's Certificate of Non-Availability of Stock (CNAS); for 15 days
 * that line may then be bought by Shopping or SVP.
 */
@RestController
@RequestMapping("/api/ps-dbm")
public class PsDbmController {

    private static final String TYPE = "App\\Models\\PsOrder";
    private static final String CATALOGUE_TYPE = "App\\Models\\PsCatalogueItem";
    private static final String READ = "@perm.any('view_procurement', 'edit_procurement', 'approve_procurement', 'view_reports')";
    private static final String MANAGE = "@perm.any('edit_procurement')";
    public static final String SUPPLIER_NAME = "Procurement Service - DBM";
    static final List<String> PAYMENT_MODES = List.of("cash", "lddap_ada", "check", "unutilized_funds");
    static final int CNAS_DAYS = 15;

    private final JdbcTemplate jdbc;
    private final Db db;
    private final DocumentNumbers numbers;
    private final PrLineUsage usage;
    private final PurchaseOrderRepository orders;
    private final CurrentUser current;
    private final AuditService audit;

    public PsDbmController(JdbcTemplate jdbc, Db db, DocumentNumbers numbers, PrLineUsage usage, PurchaseOrderRepository orders,
                           CurrentUser current, AuditService audit) {
        this.jdbc = jdbc;
        this.db = db;
        this.numbers = numbers;
        this.usage = usage;
        this.orders = orders;
        this.current = current;
        this.audit = audit;
    }

    // ───────────────────────── catalogue ─────────────────────────

    public record CatalogueItem(Long id, String code, String description, String unit, BigDecimal unitPrice, Long itemId, String itemName,
                                boolean isActive, LocalDate priceAsOf) {
    }

    private static final String CATALOGUE = """
            select c.id, c.code, c.description, c.unit, c.unit_price, c.item_id, i.name, c.is_active, c.price_as_of
            from ps_catalogue_items c left join items i on i.id = c.item_id""";

    private static CatalogueItem catalogueItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date asOf = rs.getDate(9);
        return new CatalogueItem(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getObject(6, Long.class),
                rs.getString(7), rs.getBoolean(8), asOf == null ? null : asOf.toLocalDate());
    }

    @GetMapping("/catalogue")
    @PreAuthorize(READ)
    public Map<String, Object> catalogue(@RequestParam(required = false) String search, @RequestParam(name = "active_only", defaultValue = "false") boolean activeOnly) {
        List<Object> args = new ArrayList<>();
        String where = " where 1 = 1";
        if (activeOnly) where += " and c.is_active = " + db.t();
        if (search != null && !search.isBlank()) {
            where += " and (lower(c.code) like ? or lower(c.description) like ?)";
            String q = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(q);
            args.add(q);
        }
        return Map.of("data", jdbc.query(CATALOGUE + where + " order by c.description", (rs, n) -> catalogueItem(rs), args.toArray()));
    }

    public record CatalogueInput(String code, String description, String unit, BigDecimal unitPrice, Long itemId, Boolean isActive, LocalDate priceAsOf) {
    }

    @PostMapping("/catalogue")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<CatalogueItem> addCatalogueItem(@RequestBody CatalogueInput in) {
        Long id = saveCatalogue(null, in);
        audit.record("created", CATALOGUE_TYPE, id, "Added PS-DBM catalogue item " + in.code().trim(), null, Map.of("unit_price", in.unitPrice()));
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogueItem(id));
    }

    @PutMapping("/catalogue/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public CatalogueItem updateCatalogueItem(@PathVariable Long id, @RequestBody CatalogueInput in) {
        CatalogueItem before = catalogueItem(id);
        saveCatalogue(id, in);
        CatalogueItem after = catalogueItem(id);
        audit.record("updated", CATALOGUE_TYPE, id, "Edited PS-DBM catalogue item " + after.code(), Map.of("unit_price", before.unitPrice(), "is_active", before.isActive()),
                Map.of("unit_price", after.unitPrice(), "is_active", after.isActive()));
        return after;
    }

    /** A catalogue item already on a request is kept (deactivate it instead). */
    @DeleteMapping("/catalogue/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public Map<String, String> deleteCatalogueItem(@PathVariable Long id) {
        CatalogueItem c = catalogueItem(id);
        Integer used = jdbc.queryForObject("select count(*) from ps_order_items where ps_catalogue_item_id = ?", Integer.class, id);
        if (used != null && used > 0) throw ApiException.of(HttpStatus.CONFLICT, c.code() + " is on " + used + " request line(s); deactivate it instead.");
        jdbc.update("delete from ps_catalogue_items where id = ?", id);
        audit.record("deleted", CATALOGUE_TYPE, id, "Deleted PS-DBM catalogue item " + c.code(), Map.of("code", c.code()), null);
        return Map.of("message", "Catalogue item deleted.");
    }

    private Long saveCatalogue(Long id, CatalogueInput in) {
        String code = text(in.code());
        String desc = text(in.description());
        String unit = text(in.unit());
        if (code == null || code.length() > 50) throw ApiException.validation("code", "Enter the PS item code (up to 50 characters).");
        if (desc == null || desc.length() > 500) throw ApiException.validation("description", "Describe the item (up to 500 characters).");
        if (unit == null || unit.length() > 50) throw ApiException.validation("unit", "Enter the unit of measure.");
        if (in.unitPrice() == null || in.unitPrice().signum() < 0 || in.unitPrice().scale() > 2) throw ApiException.validation("unit_price", "Enter the PS price in pesos.");
        if (in.itemId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from items where id = ?", Integer.class, in.itemId());
            if (n == null || n == 0) throw ApiException.validation("item_id", "That inventory item does not exist.");
        }
        Integer clash = jdbc.queryForObject("select count(*) from ps_catalogue_items where upper(code) = ? and id <> ?", Integer.class,
                code.toUpperCase(Locale.ROOT), id == null ? -1L : id);
        if (clash != null && clash > 0) throw ApiException.validation("code", "Another catalogue item already has code " + code + ".");
        Timestamp now = Timestamp.valueOf(Times.now());
        boolean active = in.isActive() == null || in.isActive();
        Date asOf = in.priceAsOf() == null ? Date.valueOf(LocalDate.now(Times.ZONE)) : Date.valueOf(in.priceAsOf());
        if (id == null) {
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update(c -> {
                PreparedStatement ps = c.prepareStatement("""
                        insert into ps_catalogue_items (code, description, unit, unit_price, item_id, is_active, price_as_of, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, code);
                ps.setString(2, desc);
                ps.setString(3, unit);
                ps.setBigDecimal(4, in.unitPrice());
                ps.setObject(5, in.itemId());
                ps.setBoolean(6, active);
                ps.setDate(7, asOf);
                ps.setTimestamp(8, now);
                ps.setTimestamp(9, now);
                return ps;
            }, key);
            return generated(key);
        }
        catalogueItem(id);
        jdbc.update("update ps_catalogue_items set code = ?, description = ?, unit = ?, unit_price = ?, item_id = ?, is_active = ?, price_as_of = ?, updated_at = ? where id = ?",
                code, desc, unit, in.unitPrice(), in.itemId(), active, asOf, now, id);
        return id;
    }

    private CatalogueItem catalogueItem(Long id) {
        List<CatalogueItem> rows = jdbc.query(CATALOGUE + " where c.id = ?", (rs, n) -> catalogueItem(rs), id);
        if (rows.isEmpty()) throw ApiException.notFound("Catalogue item");
        return rows.get(0);
    }

    // ───────────────────────── agency procurement requests ─────────────────────────

    public record Ref(Long id, String name) {
    }

    public record Line(Long id, Long purchaseRequestItemId, Long psCatalogueItemId, String catalogueCode, String description, String unit, int quantity,
                       BigDecimal unitPrice, BigDecimal amount, String availability, String cnasNumber, LocalDate cnasDate, LocalDate cnasValidUntil) {
    }

    public record OrderView(Long id, String aprNumber, String status, Ref purchaseRequest, String department, String vsReference, LocalDate submittedDate,
                            String paymentMode, String paymentReference, LocalDate paymentDate, BigDecimal amountPaid, String drNumber, LocalDate drDate,
                            String deliveryMode, Ref purchaseOrder, String purchaseOrderStatus, BigDecimal totalAmount, String remarks, Ref preparedBy,
                            List<Line> items, boolean canEdit, boolean canSubmit, boolean canPay, boolean canDeliver, boolean canCancel,
                            boolean canMarkUnavailable) {
    }

    @GetMapping("/orders")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public PageResponse<OrderView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String search) {
        List<Object> args = new ArrayList<>();
        String where = " where o.deleted_at is null";
        if (status != null && !status.isBlank()) {
            where += " and o.status = ?";
            args.add(status.trim());
        }
        if (search != null && !search.isBlank()) {
            where += " and (lower(o.apr_number) like ? or lower(pr.pr_number) like ? or lower(coalesce(o.vs_reference, '')) like ? or lower(coalesce(o.dr_number, '')) like ?)";
            String q = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            for (int i = 0; i < 4; i++) args.add(q);
        }
        String from = " from ps_orders o join purchase_requests pr on pr.id = o.purchase_request_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
        int size = 20, p = Math.max(1, page);
        args.add((long) (p - 1) * size);
        args.add(size);
        List<Long> ids = jdbc.queryForList("select o.id" + from + where + " order by o.id desc offset ? rows fetch next ? rows only", Long.class, args.toArray());
        List<OrderView> rows = new ArrayList<>();
        for (Long id : ids) rows.add(view(id, false));
        long t = total == null ? 0 : total;
        return new PageResponse<>(rows, new PageResponse.Pagination(t, size, p, (int) Math.max(1, (t + size - 1) / size)));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize(READ)
    @Transactional(readOnly = true)
    public OrderView show(@PathVariable Long id) {
        return view(id, true);
    }

    public record PrLineOption(Long id, String generalDescription, String specifications, String unit, int quantity, int remaining, BigDecimal estimatedUnitCost) {
    }

    public record PrOption(Long id, String prNumber, String purpose, String department, List<PrLineOption> lines) {
    }

    /** Approved purchase requests with PS-DBM lines not yet on a live request. */
    @GetMapping("/purchase-requests")
    @PreAuthorize(MANAGE)
    @Transactional(readOnly = true)
    public Map<String, Object> prOptions() {
        Map<Long, Integer> taken = usage.taken(null, null, null);
        Map<Long, PrOption> byPr = new LinkedHashMap<>();
        jdbc.query("""
                select pr.id, pr.pr_number, pr.purpose, d.name, pri.id, pri.general_description, pri.specifications, pri.unit, pri.quantity, pri.estimated_unit_cost
                from purchase_requests pr join purchase_request_items pri on pri.purchase_request_id = pr.id left join departments d on d.id = pr.department_id
                where pr.status = 'approved' and pr.deleted_at is null and pri.procurement_mode = ?
                order by pr.id desc, pri.id""", rs -> {
            int left = rs.getInt(9) - taken.getOrDefault(rs.getLong(5), 0);
            if (left <= 0) return;
            long prId = rs.getLong(1);
            PrOption o = byPr.get(prId);
            if (o == null) {
                o = new PrOption(prId, rs.getString(2), rs.getString(3), rs.getString(4), new ArrayList<>());
                byPr.put(prId, o);
            }
            o.lines().add(new PrLineOption(rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getInt(9), left, rs.getBigDecimal(10)));
        }, ProcurementRules.PS_DBM);
        return Map.of("data", byPr.values());
    }

    public record LineInput(Long purchaseRequestItemId, Long psCatalogueItemId, Integer quantity, BigDecimal unitPrice) {
    }

    public record OrderInput(Long purchaseRequestId, String remarks, List<LineInput> items) {
    }

    @PostMapping("/orders")
    @PreAuthorize(MANAGE)
    @Transactional
    public ResponseEntity<OrderView> create(@RequestBody OrderInput in) {
        if (in.purchaseRequestId() == null) throw ApiException.validation("purchase_request_id", "Choose the approved purchase request.");
        lockPr(in.purchaseRequestId());
        List<PreparedLine> lines = prepare(in, null);
        String number = numbers.yearly("APR", "ps_orders.apr_number");
        Timestamp now = Timestamp.valueOf(Times.now());
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("""
                    insert into ps_orders (apr_number, purchase_request_id, status, remarks, prepared_by, total_amount, created_at, updated_at)
                    values (?, ?, 'draft', ?, ?, 0, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, number);
            ps.setLong(2, in.purchaseRequestId());
            ps.setString(3, text(in.remarks()));
            ps.setLong(4, current.user().getId());
            ps.setTimestamp(5, now);
            ps.setTimestamp(6, now);
            return ps;
        }, key);
        Long id = generated(key);
        writeLines(id, lines);
        audit.record("created", TYPE, id, "Agency Procurement Request " + number + " drafted for PS-DBM (₱" + ProcurementRules.peso(total(id)) + ")", null,
                Map.of("purchase_request_id", in.purchaseRequestId(), "lines", lines.size()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(id, true));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public OrderView update(@PathVariable Long id, @RequestBody OrderInput in) {
        Map<String, Object> o = lock(id);
        if (!"draft".equals(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft request can be changed.");
        Long prId = ((Number) o.get("purchase_request_id")).longValue();
        if (in.purchaseRequestId() != null && !in.purchaseRequestId().equals(prId)) throw ApiException.validation("purchase_request_id", "The purchase request of a request cannot change.");
        lockPr(prId);
        List<PreparedLine> lines = prepare(new OrderInput(prId, in.remarks(), in.items()), id);
        jdbc.update("delete from ps_order_items where ps_order_id = ?", id);
        writeLines(id, lines);
        jdbc.update("update ps_orders set remarks = ?, updated_at = ? where id = ?", text(in.remarks()), Timestamp.valueOf(Times.now()), id);
        audit.record("updated", TYPE, id, "Edited Agency Procurement Request " + o.get("apr_number"), null, Map.of("lines", lines.size()));
        return view(id, true);
    }

    @DeleteMapping("/orders/{id}")
    @PreAuthorize(MANAGE)
    @Transactional
    public Map<String, String> destroy(@PathVariable Long id) {
        Map<String, Object> o = lock(id);
        if (!"draft".equals(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft request can be deleted; cancel it instead.");
        jdbc.update("update ps_orders set deleted_at = ?, updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), Timestamp.valueOf(Times.now()), id);
        audit.record("deleted", TYPE, id, "Deleted Agency Procurement Request " + o.get("apr_number"), Map.of("apr_number", o.get("apr_number")), null);
        return Map.of("message", "Request deleted.");
    }

    public record SubmitInput(String vsReference, LocalDate submittedDate) {
    }

    /** Placed in the PhilGEPS Virtual Store; its order reference is recorded here. */
    @PostMapping("/orders/{id}/submit")
    @PreAuthorize(MANAGE)
    @Transactional
    public OrderView submit(@PathVariable Long id, @RequestBody SubmitInput in) {
        Map<String, Object> o = lock(id);
        if (!"draft".equals(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Only a draft request is submitted.");
        String ref = text(in.vsReference());
        if (ref == null || ref.length() > 100) throw ApiException.validation("vs_reference", "Enter the Virtual Store order reference.");
        LocalDate date = in.submittedDate() == null ? LocalDate.now(Times.ZONE) : in.submittedDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("submitted_date", "The order date cannot be in the future.");
        jdbc.update("update ps_orders set status = 'submitted', vs_reference = ?, submitted_date = ?, updated_at = ? where id = ?",
                ref, Date.valueOf(date), Timestamp.valueOf(Times.now()), id);
        audit.record("submitted", TYPE, id, o.get("apr_number") + " placed in the Virtual Store (" + ref + ")", Map.of("status", "draft"), Map.of("status", "submitted"));
        return view(id, true);
    }

    public record CnasInput(String cnasNumber, LocalDate cnasDate) {
    }

    /**
     * PS has no stock of this line: its Certificate of Non-Availability of Stock frees the quantity, which
     * may then be bought by Shopping or SVP while the certificate is valid (15 days).
     */
    @PostMapping("/orders/{id}/items/{lineId}/not-available")
    @PreAuthorize(MANAGE)
    @Transactional
    public OrderView notAvailable(@PathVariable Long id, @PathVariable Long lineId, @RequestBody CnasInput in) {
        Map<String, Object> o = lock(id);
        if (!"submitted".equals(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Stock availability is settled after the request is placed and before payment.");
        String no = text(in.cnasNumber());
        if (no == null || no.length() > 100) throw ApiException.validation("cnas_number", "Enter the CNAS reference.");
        LocalDate date = in.cnasDate() == null ? LocalDate.now(Times.ZONE) : in.cnasDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("cnas_date", "The CNAS date cannot be in the future.");
        int n = jdbc.update("update ps_order_items set availability = 'not_available', cnas_number = ?, cnas_date = ?, updated_at = ? where id = ? and ps_order_id = ? and availability = 'available'",
                no, Date.valueOf(date), Timestamp.valueOf(Times.now()), lineId, id);
        if (n == 0) throw ApiException.validation("line", "That line is not on this request, or is already marked not available.");
        recalc(id);
        Integer left = jdbc.queryForObject("select count(*) from ps_order_items where ps_order_id = ? and availability = 'available'", Integer.class, id);
        if (left != null && left == 0) {
            jdbc.update("update ps_orders set status = 'cancelled', updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), id);
        }
        audit.record("updated", TYPE, id, o.get("apr_number") + ": line not available at PS (CNAS " + no + ")", null, Map.of("line", lineId, "cnas_number", no));
        return view(id, true);
    }

    public record PaymentInput(String paymentMode, String paymentReference, LocalDate paymentDate, BigDecimal amountPaid) {
    }

    /** PS releases stock only against payment of the whole request. */
    @PostMapping("/orders/{id}/pay")
    @PreAuthorize(MANAGE)
    @Transactional
    public OrderView pay(@PathVariable Long id, @RequestBody PaymentInput in) {
        Map<String, Object> o = lock(id);
        if (!"submitted".equals(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Payment is recorded on a request placed in the Virtual Store.");
        if (in.paymentMode() == null || !PAYMENT_MODES.contains(in.paymentMode())) throw ApiException.validation("payment_mode", "Choose cash, LDDAP-ADA, check or funds already with PS.");
        String ref = text(in.paymentReference());
        if (ref == null || ref.length() > 100) throw ApiException.validation("payment_reference", "Enter the OR, LDDAP-ADA or check number.");
        LocalDate date = in.paymentDate() == null ? LocalDate.now(Times.ZONE) : in.paymentDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("payment_date", "The payment date cannot be in the future.");
        BigDecimal total = total(id);
        if (in.amountPaid() == null || in.amountPaid().setScale(2, RoundingMode.HALF_UP).compareTo(total) != 0) {
            throw ApiException.validation("amount_paid", "The payment must equal the request total of ₱" + ProcurementRules.peso(total) + " (lines PS can supply).");
        }
        jdbc.update("update ps_orders set status = 'paid', payment_mode = ?, payment_reference = ?, payment_date = ?, amount_paid = ?, updated_at = ? where id = ?",
                in.paymentMode(), ref, Date.valueOf(date), total, Timestamp.valueOf(Times.now()), id);
        audit.record("paid", TYPE, id, o.get("apr_number") + " paid to PS-DBM: ₱" + ProcurementRules.peso(total) + " (" + in.paymentMode() + " " + ref + ")",
                Map.of("status", "submitted"), Map.of("status", "paid", "amount_paid", total));
        return view(id, true);
    }

    public record DeliveryInput(String drNumber, LocalDate drDate, String deliveryMode, String deliveryAddress) {
    }

    /**
     * PS issued its Delivery Receipt: the goods are picked up or delivered in one lot. A confirmed order
     * to PS-DBM opens, numbered as the APR, for Receiving to inspect and take into stock.
     */
    @PostMapping("/orders/{id}/deliver")
    @PreAuthorize(MANAGE)
    @Transactional
    public OrderView deliver(@PathVariable Long id, @RequestBody DeliveryInput in) {
        Map<String, Object> o = lock(id);
        if (!"paid".equals(o.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "PS issues the Delivery Receipt after payment.");
        String dr = text(in.drNumber());
        if (dr == null || dr.length() > 100) throw ApiException.validation("dr_number", "Enter the PS Delivery Receipt number.");
        LocalDate date = in.drDate() == null ? LocalDate.now(Times.ZONE) : in.drDate();
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("dr_date", "The DR date cannot be in the future.");
        String mode = in.deliveryMode() == null ? "pickup" : in.deliveryMode();
        if (!List.of("pickup", "delivery").contains(mode)) throw ApiException.validation("delivery_mode", "Pick-up or delivery.");

        PurchaseOrder po = new PurchaseOrder((String) o.get("apr_number"), current.user().getId(), date);
        po.setPurchaseRequestId(((Number) o.get("purchase_request_id")).longValue());
        po.setSupplierId(psSupplier());
        po.setDeliveryDate(date);
        po.setPsOrderId(id);
        po.setApprovedBy(current.user().getId());
        po.setDeliveryAddress(text(in.deliveryAddress()));
        po.setRemarks("PS-DBM Delivery Receipt " + dr + " (" + mode + "), Virtual Store " + o.get("vs_reference"));
        jdbc.query("""
                select pi.description, pri.specifications, pi.unit, pi.quantity, pi.unit_price from ps_order_items pi
                join purchase_request_items pri on pri.id = pi.purchase_request_item_id
                where pi.ps_order_id = ? and pi.availability = 'available' order by pi.id""", rs -> {
            po.getItems().add(new PurchaseOrderItem(po, rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5), null, null));
        }, id);
        po.recalc();
        po.setStatus(PurchaseOrder.CONFIRMED);
        orders.saveAndFlush(po);
        jdbc.update("update ps_orders set status = 'delivered', dr_number = ?, dr_date = ?, delivery_mode = ?, purchase_order_id = ?, updated_at = ? where id = ?",
                dr, Date.valueOf(date), mode, po.getId(), Timestamp.valueOf(Times.now()), id);
        audit.record("delivered", TYPE, id, o.get("apr_number") + ": PS Delivery Receipt " + dr + " (" + mode + "); ready for inspection in Receiving",
                Map.of("status", "paid"), Map.of("status", "delivered", "dr_number", dr));
        return view(id, true);
    }

    public record ReasonInput(String reason) {
    }

    /** Before payment only; once paid, unused amounts stay with PS as the agency's unutilized funds or come back by refund. */
    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize(MANAGE)
    @Transactional
    public OrderView cancel(@PathVariable Long id, @RequestBody(required = false) ReasonInput in) {
        Map<String, Object> o = lock(id);
        if (!List.of("draft", "submitted").contains((String) o.get("status"))) {
            throw ApiException.of(HttpStatus.CONFLICT, "A paid request cannot be cancelled here; ask PS for a refund or keep the amount as unutilized funds.");
        }
        String why = in == null ? null : text(in.reason());
        if (why == null) throw ApiException.validation("reason", "Say why the request is cancelled.");
        jdbc.update("update ps_orders set status = 'cancelled', remarks = ?, updated_at = ? where id = ?",
                o.get("remarks") == null ? "Cancelled: " + why : o.get("remarks") + "\nCancelled: " + why, Timestamp.valueOf(Times.now()), id);
        audit.record("cancelled", TYPE, id, o.get("apr_number") + " cancelled: " + why, Map.of("status", o.get("status")), Map.of("status", "cancelled"));
        return view(id, true);
    }

    // ───────────────────────── helpers ─────────────────────────

    private record PreparedLine(Long prItemId, Long catalogueId, String description, String unit, int quantity, BigDecimal unitPrice) {
    }

    private List<PreparedLine> prepare(OrderInput in, Long exceptOrder) {
        List<Map<String, Object>> pr = jdbc.queryForList("select status from purchase_requests where id = ? and deleted_at is null", in.purchaseRequestId());
        if (pr.isEmpty()) throw ApiException.validation("purchase_request_id", "That purchase request does not exist.");
        if (!"approved".equals(pr.get(0).get("status"))) throw ApiException.validation("purchase_request_id", "Only fully approved purchase requests go to PS-DBM.");
        if (in.items() == null || in.items().isEmpty()) throw ApiException.validation("items", "Add at least one line from the purchase request.");
        Map<Long, Integer> taken = usage.taken(in.purchaseRequestId(), null, null, exceptOrder);
        Set<Long> seen = new HashSet<>();
        List<PreparedLine> out = new ArrayList<>();
        for (int i = 0; i < in.items().size(); i++) {
            LineInput l = in.items().get(i);
            String f = "items." + i + ".";
            List<Map<String, Object>> rows = l == null || l.purchaseRequestItemId() == null ? List.of() : jdbc.queryForList(
                    "select general_description, unit, quantity, estimated_unit_cost, procurement_mode from purchase_request_items where id = ? and purchase_request_id = ?",
                    l.purchaseRequestItemId(), in.purchaseRequestId());
            if (rows.isEmpty()) throw ApiException.validation(f + "purchase_request_item_id", "Line " + (i + 1) + " is not on the purchase request.");
            Map<String, Object> p = rows.get(0);
            String desc = (String) p.get("general_description");
            if (!seen.add(l.purchaseRequestItemId())) throw ApiException.validation(f + "purchase_request_item_id", "\"" + desc + "\" is listed twice.");
            if (!ProcurementRules.PS_DBM.equals(p.get("procurement_mode"))) {
                throw ApiException.validation(f + "purchase_request_item_id", "\"" + desc + "\" is planned under " + p.get("procurement_mode") + ", not " + ProcurementRules.PS_DBM + ".");
            }
            int planned = ((Number) p.get("quantity")).intValue();
            int left = planned - taken.getOrDefault(l.purchaseRequestItemId(), 0);
            int qty = l.quantity() == null ? left : l.quantity();
            if (qty < 1 || left <= 0) throw ApiException.validation(f + "quantity", "Nothing is left of \"" + desc + "\"; it is already on another request.");
            if (qty > left) throw ApiException.validation(f + "quantity", "\"" + desc + "\": " + qty + " asked, but only " + left + " of " + planned + " are not on another request.");
            String unit = (String) p.get("unit");
            BigDecimal price = l.unitPrice();
            if (l.psCatalogueItemId() != null) {
                CatalogueItem c = catalogueItem(l.psCatalogueItemId());
                if (!c.isActive()) throw ApiException.validation(f + "ps_catalogue_item_id", c.code() + " is no longer in the PS catalogue.");
                price = c.unitPrice();
                desc = c.description();
                unit = c.unit();
            }
            if (price == null || price.signum() <= 0 || price.scale() > 2) throw ApiException.validation(f + "unit_price", "Line " + (i + 1) + ": choose the PS catalogue item or enter its PS price.");
            BigDecimal estimate = (BigDecimal) p.get("estimated_unit_cost");
            if (estimate != null && price.compareTo(estimate) > 0) {
                throw ApiException.validation(f + "unit_price", "\"" + desc + "\": the PS price of ₱" + ProcurementRules.peso(price) + " is above the ₱"
                        + ProcurementRules.peso(estimate) + " the purchase request was approved for; amend the PR first.");
            }
            out.add(new PreparedLine(l.purchaseRequestItemId(), l.psCatalogueItemId(), desc, unit, qty, price));
        }
        return out;
    }

    private void writeLines(Long orderId, List<PreparedLine> lines) {
        Timestamp now = Timestamp.valueOf(Times.now());
        for (PreparedLine l : lines) {
            jdbc.update("""
                    insert into ps_order_items (ps_order_id, purchase_request_item_id, ps_catalogue_item_id, description, unit, quantity, unit_price, amount,
                                                availability, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'available', ?, ?)""", orderId, l.prItemId(), l.catalogueId(), l.description(), l.unit(), l.quantity(),
                    l.unitPrice(), l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP), now, now);
        }
        recalc(orderId);
    }

    private void recalc(Long orderId) {
        jdbc.update("update ps_orders set total_amount = ?, updated_at = ? where id = ?", total(orderId), Timestamp.valueOf(Times.now()), orderId);
    }

    private BigDecimal total(Long orderId) {
        BigDecimal t = jdbc.queryForObject("select coalesce(sum(amount), 0) from ps_order_items where ps_order_id = ? and availability = 'available'", BigDecimal.class, orderId);
        return (t == null ? BigDecimal.ZERO : t).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> lock(Long id) {
        List<Map<String, Object>> rows = db.lockRows("ps_orders", "id = ? and deleted_at is null", id);
        if (rows.isEmpty()) throw ApiException.notFound("Agency Procurement Request");
        return rows.get(0);
    }

    private void lockPr(Long prId) {
        // Serialises requests drawing on the same PR lines, as RFQs and biddings do.
        db.lockRows("purchase_requests", "id = ? and deleted_at is null", prId);
    }

    /** The supplier record for PS-DBM, created on first use; a government agency, so no PhilGEPS eligibility documents apply. */
    private Long psSupplier() {
        List<Long> ids = jdbc.queryForList("select id from suppliers where name = ? order by id", Long.class, SUPPLIER_NAME);
        if (!ids.isEmpty()) return ids.get(0);
        Timestamp now = Timestamp.valueOf(Times.now());
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("""
                    insert into suppliers (name, vendor_code, address, notes, is_active, is_eligible, is_msme, created_at, updated_at)
                    values (?, 'PS-DBM', 'RR Road, Cristobal St., Paco, Manila', ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, SUPPLIER_NAME);
            ps.setString(2, "Procurement Service of the Department of Budget and Management: common-use supplies and equipment (LOI 755).");
            ps.setBoolean(3, true);
            ps.setBoolean(4, true);
            ps.setBoolean(5, false);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, key);
        return generated(key);
    }

    private static Long generated(GeneratedKeyHolder key) {
        Map<String, Object> keys = key.getKeys();
        if (keys != null && keys.get("id") instanceof Number n) return n.longValue();
        Number n = key.getKey();
        return n == null ? null : n.longValue();
    }

    private OrderView view(Long id, boolean full) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select o.*, pr.pr_number, d.name as department, u.name as preparer, po.po_number, po.status as po_status
                from ps_orders o join purchase_requests pr on pr.id = o.purchase_request_id left join departments d on d.id = pr.department_id
                left join users u on u.id = o.prepared_by left join purchase_orders po on po.id = o.purchase_order_id
                where o.id = ? and o.deleted_at is null""", id);
        if (rows.isEmpty()) throw ApiException.notFound("Agency Procurement Request");
        Map<String, Object> o = rows.get(0);
        List<Line> lines = jdbc.query("""
                select pi.id, pi.purchase_request_item_id, pi.ps_catalogue_item_id, c.code, pi.description, pi.unit, pi.quantity, pi.unit_price, pi.amount,
                       pi.availability, pi.cnas_number, pi.cnas_date
                from ps_order_items pi left join ps_catalogue_items c on c.id = pi.ps_catalogue_item_id where pi.ps_order_id = ? order by pi.id""", (rs, n) -> {
            Date cnas = rs.getDate(12);
            return new Line(rs.getLong(1), rs.getLong(2), rs.getObject(3, Long.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
                    rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getString(10), rs.getString(11), cnas == null ? null : cnas.toLocalDate(),
                    cnas == null ? null : cnas.toLocalDate().plusDays(CNAS_DAYS));
        }, id);
        String status = (String) o.get("status");
        boolean manage = current.any("edit_procurement");
        Object poId = o.get("purchase_order_id");
        return new OrderView(id, (String) o.get("apr_number"), status, new Ref(((Number) o.get("purchase_request_id")).longValue(), (String) o.get("pr_number")),
                (String) o.get("department"), (String) o.get("vs_reference"), date(o.get("submitted_date")), (String) o.get("payment_mode"),
                (String) o.get("payment_reference"), date(o.get("payment_date")), (BigDecimal) o.get("amount_paid"), (String) o.get("dr_number"),
                date(o.get("dr_date")), (String) o.get("delivery_mode"), poId == null ? null : new Ref(((Number) poId).longValue(), (String) o.get("po_number")),
                (String) o.get("po_status"), (BigDecimal) o.get("total_amount"), (String) o.get("remarks"),
                o.get("prepared_by") == null ? null : new Ref(((Number) o.get("prepared_by")).longValue(), (String) o.get("preparer")),
                full ? lines : null, manage && "draft".equals(status), manage && "draft".equals(status), manage && "submitted".equals(status),
                manage && "paid".equals(status), manage && List.of("draft", "submitted").contains(status), manage && "submitted".equals(status));
    }

    private static LocalDate date(Object v) {
        return v instanceof Date d ? d.toLocalDate() : v instanceof java.time.LocalDate l ? l : v instanceof Timestamp t ? t.toLocalDateTime().toLocalDate() : null;
    }

    private static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

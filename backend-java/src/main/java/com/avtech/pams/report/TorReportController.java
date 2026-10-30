package com.avtech.pams.report;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.CnasForm;
import com.avtech.pams.report.coa.CosUndertakingForm;
import com.avtech.pams.report.coa.IrpForm;
import com.avtech.pams.report.coa.ItrForm;
import com.avtech.pams.report.coa.PpeLedgerCardForm;
import com.avtech.pams.report.coa.RegspiForm;
import com.avtech.pams.report.coa.RspiForm;
import com.avtech.pams.report.coa.SemiExpendableCardForm;
import com.avtech.pams.report.coa.SuppliesLedgerCardForm;
import com.avtech.pams.report.coa.TabularReport;
import com.avtech.pams.report.coa.TabularReport.Format;
import com.avtech.pams.setting.SettingService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The forms and registers required by the Terms of Reference (sections 1.4.2, 1.5.2 and 1.6) that
 * the COA exports did not cover: ledger cards, the COA Circular 2022-004 semi-expendable forms,
 * IRP, CNAS, the COS letter of undertaking, and the list reports (lapsing schedule, PIF,
 * registers and master lists) in Excel, CSV or PDF. Entity and fund cluster default to Settings;
 * each download is audited.
 */
@RestController
@RequestMapping("/api/reports")
public class TorReportController {

    private static final String REPORTS = "@perm.any('view_reports', 'generate_reports')";
    private static final DateTimeFormatter ISO = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;
    private final Db db;
    private final SettingService settings;
    private final AuditService audit;
    private final TorListReports lists;
    private final TorPeriodReports periods;
    private final PpeLedgerCardForm ppeLedger;
    private final SuppliesLedgerCardForm suppliesLedger;
    private final SemiExpendableCardForm semiCard;
    private final RegspiForm regspi;
    private final RspiForm rspi;
    private final ItrForm itr;
    private final IrpForm irp;
    private final CnasForm cnas;
    private final CosUndertakingForm undertaking;

    public TorReportController(JdbcTemplate jdbc, Db db, SettingService settings, AuditService audit, TorListReports lists, TorPeriodReports periods, PpeLedgerCardForm ppeLedger,
                               SuppliesLedgerCardForm suppliesLedger, SemiExpendableCardForm semiCard, RegspiForm regspi, RspiForm rspi, ItrForm itr,
                               IrpForm irp, CnasForm cnas, CosUndertakingForm undertaking) {
        this.jdbc = jdbc;
        this.db = db;
        this.settings = settings;
        this.audit = audit;
        this.lists = lists;
        this.periods = periods;
        this.ppeLedger = ppeLedger;
        this.suppliesLedger = suppliesLedger;
        this.semiCard = semiCard;
        this.regspi = regspi;
        this.rspi = rspi;
        this.itr = itr;
        this.irp = irp;
        this.cnas = cnas;
        this.undertaking = undertaking;
    }

    // ───────────────────────── pickers ─────────────────────────

    /** Choices for the report forms: offices, people, semi-expendable items, issuances, semi-expendable transfers and CNAS lines. */
    @GetMapping("/tor/options")
    @PreAuthorize(REPORTS)
    public Map<String, Object> options() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("departments", jdbc.queryForList("select id, name from departments order by name"));
        m.put("users", jdbc.queryForList("select u.id, u.name, u.email, d.name as department from users u left join departments d on d.id = u.department_id order by u.name"));
        m.put("semi_expendable_items", jdbc.queryForList("select id, code, name from items where is_semi_expandable = " + db.t() + " order by name"));
        m.put("issuances", jdbc.queryForList("""
                select r.id, r.request_number, r.par_number, r.ics_number, r.request_date, u.name as requester
                from requests r left join users u on u.id = r.requested_by
                where r.par_number is not null or r.ics_number is not null order by r.request_date desc, r.id desc"""));
        m.put("accountability_numbers", jdbc.queryForList("""
                select p.accountability_number, min(u.name) as holder, count(*) as assets
                from properties p left join users u on u.id = p.assigned_to
                where p.accountability_number is not null group by p.accountability_number order by p.accountability_number"""));
        m.put("semi_expendable_transfers", jdbc.queryForList("""
                select t.id, t.reference_number, t.transfer_date, p.tag_number, i.name as item_name, fu.name as from_name, tu.name as to_name
                from property_transfers t join properties p on p.id = t.property_id join items i on i.id = p.item_id
                left join users fu on fu.id = t.from_user left join users tu on tu.id = t.to_user
                where i.is_semi_expandable = """ + db.t() + " order by t.transfer_date desc, t.id desc"));
        m.put("cnas_lines", jdbc.queryForList("""
                select oi.id, oi.cnas_number, oi.cnas_date, oi.description, o.apr_number
                from ps_order_items oi join ps_orders o on o.id = oi.ps_order_id
                where oi.availability = 'not_available' and o.deleted_at is null order by oi.cnas_date desc, oi.id desc"""));
        return m;
    }

    // ───────────────────────── ledger cards ─────────────────────────

    /** Appendix 70 — Property, Plant and Equipment Ledger Card. */
    @GetMapping("/ppe-ledger-card/{propertyId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_properties')")
    public ResponseEntity<byte[]> ppeLedgerCard(@PathVariable long propertyId, @RequestParam(name = "as_at_date", required = false) LocalDate asAt,
                                                @RequestParam(name = "entity_name", required = false) String entity,
                                                @RequestParam(name = "fund_cluster", required = false) String fund) {
        String tag = one("select coalesce(tag_number, cast(id as varchar(20))) from properties where id = ?", "Property", propertyId);
        return xlsx(ppeLedger.generate(propertyId, asAt, entity(entity), fund(fund)), "PPELedgerCard_" + tag + ".xlsx", "PPE Ledger Card of " + tag,
                "App\\Models\\Property", propertyId);
    }

    /** Appendix 57 — Supplies Ledger Card. */
    @GetMapping("/supplies-ledger-card/{itemId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_inventory', 'view_warehouse', 'manage_warehouse')")
    public ResponseEntity<byte[]> suppliesLedgerCard(@PathVariable long itemId, @RequestParam(name = "entity_name", required = false) String entity,
                                                     @RequestParam(name = "fund_cluster", required = false) String fund) {
        String code = one("select code from items where id = ?", "Item", itemId);
        return xlsx(suppliesLedger.generate(itemId, entity(entity), fund(fund)), "SuppliesLedgerCard_" + code + ".xlsx", "Supplies Ledger Card of " + code,
                "App\\Models\\Item", itemId);
    }

    /** COA Circular 2022-004 Annex A.1 — Semi-expendable Property Card. */
    @GetMapping("/semi-expendable-card/{itemId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_inventory', 'view_warehouse', 'manage_warehouse')")
    public ResponseEntity<byte[]> semiExpendableCard(@PathVariable long itemId, @RequestParam(name = "entity_name", required = false) String entity,
                                                     @RequestParam(name = "fund_cluster", required = false) String fund) {
        String code = one("select code from items where id = ?", "Item", itemId);
        return xlsx(semiCard.generate(itemId, false, entity(entity), fund(fund)), "SPC_" + code + ".xlsx", "Semi-expendable Property Card of " + code,
                "App\\Models\\Item", itemId);
    }

    /** COA Circular 2022-004 Annex A.2 — Semi-expendable Property Ledger Card. */
    @GetMapping("/semi-expendable-ledger-card/{itemId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_inventory', 'view_warehouse', 'manage_warehouse')")
    public ResponseEntity<byte[]> semiExpendableLedgerCard(@PathVariable long itemId, @RequestParam(name = "entity_name", required = false) String entity,
                                                           @RequestParam(name = "fund_cluster", required = false) String fund) {
        String code = one("select code from items where id = ?", "Item", itemId);
        return xlsx(semiCard.generate(itemId, true, entity(entity), fund(fund)), "SPLC_" + code + ".xlsx", "Semi-expendable Property Ledger Card of " + code,
                "App\\Models\\Item", itemId);
    }

    // ───────────────────────── semi-expendable property ─────────────────────────

    /** Annex A.4 — Registry of Semi-Expendable Property Issued, for a period and optionally one office. */
    @GetMapping("/regspi/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> regspi(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                         @RequestParam(name = "department_id", required = false) Long departmentId,
                                         @RequestParam(name = "entity_name", required = false) String entity,
                                         @RequestParam(name = "fund_cluster", required = false) String fund) {
        range(from, to);
        String office = null;
        if (departmentId != null) office = one("select name from departments where id = ?", "Office", departmentId);
        return xlsx(regspi.generate(from, to, departmentId, office, entity(entity), fund(fund)), "RegSPI_" + from.format(ISO) + "-" + to.format(ISO) + ".xlsx",
                "RegSPI " + from + " to " + to + (office == null ? "" : " (" + office + ")"), null, null);
    }

    /** Annex A.7 — Report of Semi-Expendable Property Issued, for a period. */
    @GetMapping("/rspi/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> rspi(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                       @RequestParam(name = "serial_no", required = false) String serial,
                                       @RequestParam(name = "entity_name", required = false) String entity,
                                       @RequestParam(name = "fund_cluster", required = false) String fund) {
        range(from, to);
        return xlsx(rspi.generate(from, to, blank(serial), entity(entity), fund(fund)), "RSPI_" + from.format(ISO) + "-" + to.format(ISO) + ".xlsx",
                "RSPI " + from + " to " + to, null, null);
    }

    /** Annex A.5 — Inventory Transfer Report, for a transfer of semi-expendable property. */
    @GetMapping("/itr/{transferId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_properties', 'transfer_properties')")
    public ResponseEntity<byte[]> itr(@PathVariable long transferId, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        String ref = one("select coalesce(reference_number, cast(id as varchar(20))) from property_transfers where id = ?", "Property transfer", transferId);
        return xlsx(itr.generate(transferId, entity(entity), fund(fund)), "ITR_" + ref + ".xlsx", "ITR " + ref, "App\\Models\\PropertyTransfer", transferId);
    }

    // ───────────────────────── issuance, PS-DBM, COS ─────────────────────────

    /** Invoice Receipt for Property, for a request (request_id) or the assets under one PAR/ICS number (par_number). */
    @GetMapping("/irp/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_requests', 'release_stock', 'view_properties')")
    public ResponseEntity<byte[]> irp(@RequestParam(name = "request_id", required = false) Long requestId,
                                      @RequestParam(name = "par_number", required = false) String parNumber,
                                      @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        String par = blank(parNumber);
        if ((requestId == null) == (par == null)) throw ApiException.validation("request_id", "Choose either a request or a PAR/ICS number.");
        String no = irp.number(requestId, par);
        return xlsx(irp.generate(requestId, par, entity(entity), fund(fund)), "IRP_" + no + ".xlsx", "IRP " + no,
                requestId != null ? "App\\Models\\Request" : null, requestId);
    }

    /** Certificate of Non-Availability of Stock for a PS-DBM order line marked not available. */
    @GetMapping("/cnas/{lineId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_procurement')")
    public ResponseEntity<byte[]> cnas(@PathVariable long lineId, @RequestParam(name = "entity_name", required = false) String entity) {
        String no = cnas.number(lineId);
        return xlsx(cnas.generate(lineId, entity(entity)), "CNAS_" + no + ".xlsx", "CNAS " + no, "App\\Models\\PsOrderItem", lineId);
    }

    /** Letter of Undertaking of a Contract of Service worker for the property issued to them. */
    @GetMapping("/cos-undertaking/{userId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_users')")
    public ResponseEntity<byte[]> cosUndertaking(@PathVariable long userId, @RequestParam(required = false) String position,
                                                 @RequestParam(name = "contract_end", required = false) LocalDate contractEnd,
                                                 @RequestParam(name = "entity_name", required = false) String entity) {
        String name = one("select name from users where id = ?", "User", userId);
        if (position != null && position.length() > 150) throw ApiException.validation("position", "Keep the position under 150 characters.");
        return xlsx(undertaking.generate(userId, entity(entity), blank(position), contractEnd), "COS_Undertaking_" + name.replace(' ', '_') + ".xlsx",
                "COS Letter of Undertaking of " + name, "App\\Models\\User", userId);
    }

    // ───────────────────────── list reports (xlsx | csv | pdf) ─────────────────────────

    /** Depreciation lapsing schedule of PPE as at a date, by category with subtotals. */
    @GetMapping("/lapsing-schedule/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> lapsingSchedule(@RequestParam(name = "as_at_date", required = false) LocalDate asAt,
                                                  @RequestParam(required = false) String format,
                                                  @RequestParam(name = "entity_name", required = false) String entity) {
        LocalDate at = asAt != null ? asAt : LocalDate.now(Times.ZONE);
        return list(lists.lapsingSchedule(at, entity(entity)), format, "LapsingSchedule_" + at.format(ISO), "Depreciation Lapsing Schedule as at " + at);
    }

    /** Property Inventory Form, for one accountable officer (user_id) or everyone. */
    @GetMapping("/pif/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_properties')")
    public ResponseEntity<byte[]> pif(@RequestParam(name = "user_id", required = false) Long userId, @RequestParam(required = false) String format,
                                      @RequestParam(name = "entity_name", required = false) String entity) {
        String who = userId == null ? "All" : one("select name from users where id = ?", "User", userId);
        return list(lists.propertyInventory(userId, entity(entity)), format, "PIF_" + who.replace(' ', '_') + "_" + CoaExportController.stamp(),
                "Property Inventory Form (" + who + ")");
    }

    /** IAR monitoring register. */
    @GetMapping("/registers/iar/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> iarRegister(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                              @RequestParam(required = false) String format, @RequestParam(name = "entity_name", required = false) String entity) {
        return period(from, to, format, "IAR_Monitoring", "IAR monitoring", p -> lists.iarRegister(p, entity(entity)));
    }

    /** RIS monitoring register (optionally one office). */
    @GetMapping("/registers/ris/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> risRegister(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                              @RequestParam(name = "department_id", required = false) Long departmentId,
                                              @RequestParam(required = false) String format, @RequestParam(name = "entity_name", required = false) String entity) {
        return period(from, to, format, "RIS_Monitoring", "RIS monitoring", p -> lists.risRegister(p, departmentId, entity(entity)));
    }

    /** Summary of purchase orders. */
    @GetMapping("/registers/purchase-orders/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> purchaseOrders(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(required = false) String format, @RequestParam(name = "entity_name", required = false) String entity) {
        return period(from, to, format, "PO_Summary", "Summary of Purchase Orders", p -> lists.purchaseOrders(p, entity(entity)));
    }

    /** Master lists: assets, items, suppliers, users, available-supplies; from/to filter on acquisition/creation date. */
    /** The reports of TOR 1.6.4 for any dates (the UI offers a month or a year too). */
    @GetMapping("/registers/{report}/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> periodRegister(@PathVariable String report, @RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format,
                                                 @RequestParam(name = "item_id", required = false) Long itemId,
                                                 @RequestParam(name = "count_type", required = false) String countType,
                                                 @RequestParam(name = "entity_name", required = false) String entity) {
        String e = entity(entity);
        String type = blank(countType);
        if (type != null && !List.of("supplies", "ppe", "semi_expendable").contains(type)) throw ApiException.validation("count_type", "Choose supplies, ppe or semi_expendable.");
        return switch (report) {
            case "par" -> period(from, to, format, "PAR_Register", "PAR register", p -> periods.par(p, e));
            case "ics" -> period(from, to, format, "ICS_Register", "ICS register", p -> periods.ics(p, e));
            case "property-transactions" -> period(from, to, format, "Property_Transactions", "Property card transactions", p -> periods.propertyTransactions(p, e));
            case "stock-movements" -> period(from, to, format, "Stock_Movements", "Stock card movements", p -> periods.stockMovements(p, itemId, e));
            case "physical-counts" -> period(from, to, format, "Physical_Counts", "Physical counts", p -> periods.physicalCounts(p, type, e));
            case "wmr" -> period(from, to, format, "WMR_Register", "Waste materials register", p -> periods.disposals(p, false, e));
            case "iirup" -> period(from, to, format, "IIRUP_Register", "Unserviceable property register", p -> periods.disposals(p, true, e));
            case "rlsddp" -> period(from, to, format, "RLSDDP_Register", "Lost or damaged property register", p -> periods.lostDamaged(p, e));
            case "gate-passes" -> period(from, to, format, "Gate_Passes", "Gate pass register", p -> periods.gatePasses(p, e));
            case "transfers" -> period(from, to, format, "RTA_Register", "Transfers of accountability", p -> periods.transfers(p, e));
            case "reorder-point" -> period(from, to, format, "Reordering_Point", "Reordering point", p -> periods.reorderPoint(p, e));
            default -> throw ApiException.notFound("Report");
        };
    }

    @GetMapping("/master-lists/{list}/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> masterList(@PathVariable String list, @RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String format,
                                             @RequestParam(name = "entity_name", required = false) String entity) {
        String e = entity(entity);
        return switch (list) {
            case "assets" -> period(from, to, format, "Asset_Master_List", "Asset master list", p -> lists.assets(p, e));
            case "items" -> period(from, to, format, "Item_Master_List", "Supplies/item master list", p -> lists.items(p, e));
            case "suppliers" -> period(from, to, format, "Supplier_Master_List", "Supplier master list", p -> lists.suppliers(p, e));
            case "users" -> period(from, to, format, "User_Master_List", "User master list", p -> lists.users(p, e));
            case "available-supplies" -> period(from, to, format, "Available_Supplies", "List of available and commonly-used supplies", p -> lists.availableSupplies(p, e));
            default -> throw ApiException.notFound("Master list");
        };
    }

    // ───────────────────────── helpers ─────────────────────────

    private ResponseEntity<byte[]> period(LocalDate from, LocalDate to, String format, String base, String what,
                                          Function<TorListReports.Period, TabularReport> build) {
        if (from != null && to != null) range(from, to);
        TorListReports.Period p = new TorListReports.Period(from, to);
        String suffix = from == null && to == null ? CoaExportController.stamp()
                : (from == null ? "start" : from.format(ISO)) + "-" + (to == null ? "now" : to.format(ISO));
        return list(build.apply(p), format, base + "_" + suffix, what + " (" + p.label() + ")");
    }

    private ResponseEntity<byte[]> list(TabularReport report, String format, String base, String what) {
        Format f = Format.of(format);
        if (f == null) throw ApiException.validation("format", "Choose xlsx, csv or pdf.");
        return file(report.render(f), base + "." + f.ext, f.contentType, what, null, null);
    }

    private static void range(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw ApiException.validation("to", "The end date is before the start date.");
    }

    String entity(String given) {
        return given != null && !given.isBlank() ? given.trim() : settings.get("agency_name", "");
    }

    String fund(String given) {
        return given != null && !given.isBlank() ? given.trim() : settings.get("fund_cluster", "");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String one(String sql, String what, Object id) {
        List<String> v = jdbc.queryForList(sql, String.class, id);
        if (v.isEmpty()) throw ApiException.notFound(what);
        return v.get(0) == null ? String.valueOf(id) : v.get(0);
    }

    private ResponseEntity<byte[]> xlsx(byte[] bytes, String filename, String what, String type, Long id) {
        return file(bytes, filename, CoaExportController.XLSX, what, type, id);
    }

    /** As CoaExportController.file, with the content type of the format. */
    private ResponseEntity<byte[]> file(byte[] bytes, String filename, String contentType, String what, String type, Long id) {
        audit.record("downloaded", type, id, "Downloaded " + what + " (" + PdfFormAdvice.fileName(filename) + ")", null, Map.of("file", PdfFormAdvice.fileName(filename)));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename.replaceAll("[\\\\/:*?\"<>|]", "-"), StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}

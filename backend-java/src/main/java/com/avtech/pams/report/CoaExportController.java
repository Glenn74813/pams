package com.avtech.pams.report;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.AbstractOfBidsForm;
import com.avtech.pams.report.coa.IarForm;
import com.avtech.pams.report.coa.IirupForm;
import com.avtech.pams.report.coa.PropertyCardForm;
import com.avtech.pams.report.coa.RlsddpForm;
import com.avtech.pams.report.coa.RpcppeForm;
import com.avtech.pams.report.coa.WmrForm;
import com.avtech.pams.report.coa.IcsForm;
import com.avtech.pams.report.coa.ParForm;
import com.avtech.pams.report.coa.PtrForm;
import com.avtech.pams.report.coa.RisForm;
import com.avtech.pams.report.coa.RpciForm;
import com.avtech.pams.report.coa.RpcspForm;
import com.avtech.pams.report.coa.RsmiForm;
import com.avtech.pams.report.coa.StockCardForm;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The COA forms as Excel files, at the same URLs and with the same file names as the Laravel app.
 * Entity name and fund cluster default to the agency profile in Settings. Each export is audited.
 */
@RestController
@RequestMapping("/api")
public class CoaExportController {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String REPORTS = "@perm.any('view_reports', 'generate_reports')";

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final AuditService audit;
    private final RsmiForm rsmi;
    private final StockCardForm stockCard;
    private final RpciForm rpci;
    private final RpcspForm rpcsp;
    private final RisForm ris;
    private final IcsForm ics;
    private final ParForm par;
    private final IarForm iar;
    private final PtrForm ptr;
    private final AbstractOfBidsForm abstractOfBids;
    private final PropertyCardForm propertyCard;
    private final RpcppeForm rpcppe;
    private final WmrForm wmr;
    private final IirupForm iirup;
    private final RlsddpForm rlsddp;

    public CoaExportController(JdbcTemplate jdbc, SettingService settings, AuditService audit, RsmiForm rsmi, StockCardForm stockCard, RpciForm rpci,
                               RpcspForm rpcsp, RisForm ris, IcsForm ics, ParForm par, IarForm iar, PtrForm ptr, AbstractOfBidsForm abstractOfBids,
                               PropertyCardForm propertyCard, RpcppeForm rpcppe, WmrForm wmr, IirupForm iirup, RlsddpForm rlsddp) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.audit = audit;
        this.rsmi = rsmi;
        this.stockCard = stockCard;
        this.rpci = rpci;
        this.rpcsp = rpcsp;
        this.ris = ris;
        this.ics = ics;
        this.par = par;
        this.iar = iar;
        this.ptr = ptr;
        this.abstractOfBids = abstractOfBids;
        this.propertyCard = propertyCard;
        this.rpcppe = rpcppe;
        this.wmr = wmr;
        this.iirup = iirup;
        this.rlsddp = rlsddp;
    }

    // ───────────────────────── reports page ─────────────────────────

    /** Appendix 64 — Report of Supplies and Materials Issued. */
    @GetMapping("/reports/rsmi/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> rsmi(@RequestParam int year, @RequestParam int month,
                                       @RequestParam(name = "entity_name", required = false) String entity,
                                       @RequestParam(name = "fund_cluster", required = false) String fund) {
        if (month < 1 || month > 12) throw ApiException.validation("month", "Choose a month.");
        String name = YearMonth.of(year, month).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return file(rsmi.generate(year, month, entity(entity), fund(fund)), "RSMI_" + name + "_" + year + ".xlsx", "RSMI for " + name + " " + year, null, null);
    }

    /** Appendix 58 — Stock Card. */
    @GetMapping("/reports/stock-card/{itemId}/export")
    @PreAuthorize("@perm.any('view_inventory', 'view_warehouse', 'manage_warehouse', 'add_stock', 'view_reports')")
    public ResponseEntity<byte[]> stockCard(@PathVariable int itemId, @RequestParam(name = "entity_name", required = false) String entity,
                                            @RequestParam(name = "fund_cluster", required = false) String fund) {
        String code = one("select code from items where id = ?", "Item", itemId);
        return file(stockCard.generate(itemId, entity(entity), fund(fund)), "StockCard_" + code + ".xlsx", "Stock Card of " + code, "App\\Models\\Item", (long) itemId);
    }

    /** Appendix 66 — Report on the Physical Count of Inventories. */
    @GetMapping("/reports/rpci/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> rpci(@RequestParam(name = "inventory_type", required = false) String type,
                                       @RequestParam(name = "as_at_date", required = false) LocalDate asAt,
                                       @RequestParam(name = "fund_cluster", required = false) String fund) {
        if (type != null && !type.isBlank() && !List.of("consumable", "semi_expandable").contains(type)) throw ApiException.validation("inventory_type", "Unknown inventory type.");
        return file(rpci.generate(type == null || type.isBlank() ? null : type, asAt, fund(fund), null), "RPCI_" + stamp() + ".xlsx", "RPCI", null, null);
    }

    /** Appendix 68 — Report on the Physical Count of Semi-Expendable Property. */
    @GetMapping("/reports/rpcsp/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> rpcsp(@RequestParam(name = "as_at_date", required = false) LocalDate asAt,
                                        @RequestParam(name = "fund_cluster", required = false) String fund) {
        return file(rpcsp.generate(asAt, fund(fund), null), "RPCSP_" + stamp() + ".xlsx", "RPCSP", null, null);
    }

    /** Appendix 73 — Report on the Physical Count of PPE. */
    @GetMapping("/reports/rpcppe/export")
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> rpcppe(@RequestParam(name = "ppe_type", required = false) String type,
                                         @RequestParam(name = "as_at_date", required = false) LocalDate asAt,
                                         @RequestParam(name = "fund_cluster", required = false) String fund) {
        if (type != null && !type.isBlank() && !List.of("ppe", "semi_expandable").contains(type)) throw ApiException.validation("ppe_type", "Unknown PPE type.");
        return file(rpcppe.generate(type == null || type.isBlank() ? null : type, asAt, fund(fund), null), "RPCPPE_" + stamp() + ".xlsx", "RPCPPE", null, null);
    }

    /** Appendix 69 — Property Card. */
    @GetMapping("/reports/property-card/{propertyId}/export")
    @PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_properties')")
    public ResponseEntity<byte[]> propertyCard(@PathVariable Long propertyId, @RequestParam(name = "entity_name", required = false) String entity,
                                               @RequestParam(name = "fund_cluster", required = false) String fund) {
        String tag = one("select coalesce(tag_number, cast(id as varchar(20))) from properties where id = ?", "Property", propertyId);
        return file(propertyCard.generate(propertyId, entity(entity), fund(fund)), "PropertyCard_" + tag + ".xlsx", "Property Card of " + tag,
                "App\\Models\\Property", propertyId);
    }

    /** Appendix 65 — Waste Materials Report (GET or POST; ids as a list or comma-separated). */
    @RequestMapping(value = "/reports/wmr/export", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> wmr(@RequestParam(name = "disposal_ids") List<Long> ids, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund,
                                      @RequestParam(name = "storage_place", required = false) String storage) {
        checkDisposals(ids);
        return file(wmr.generate(ids, entity(entity), fund(fund), storage == null || storage.isBlank() ? null : storage.trim()), "WMR_" + stamp() + ".xlsx", "WMR", null, null);
    }

    /** Appendix 74 — Inventory and Inspection Report of Unserviceable Property. */
    @RequestMapping(value = "/reports/iirup/export", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> iirup(@RequestParam(name = "disposal_ids") List<Long> ids, @RequestParam(name = "entity_name", required = false) String entity,
                                        @RequestParam(name = "fund_cluster", required = false) String fund,
                                        @RequestParam(name = "accountable_officer", required = false) String officer,
                                        @RequestParam(required = false) String designation, @RequestParam(required = false) String station) {
        checkDisposals(ids);
        return file(iirup.generate(ids, entity(entity), fund(fund), blank(officer), blank(designation), blank(station)), "IIRUP_" + stamp() + ".xlsx", "IIRUP", null, null);
    }

    /** Appendix 75 — Report of Lost, Stolen, Damaged or Destroyed Property. */
    @RequestMapping(value = "/reports/rlsddp/export", method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
    @PreAuthorize(REPORTS)
    public ResponseEntity<byte[]> rlsddp(@RequestParam(name = "property_id") Long propertyId, @RequestParam String status, @RequestParam String circumstances,
                                         @RequestParam(name = "entity_name", required = false) String entity,
                                         @RequestParam(name = "fund_cluster", required = false) String fund,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(name = "rlsddp_number", required = false) String number,
                                         @RequestParam(name = "rlsddp_date", required = false) LocalDate date,
                                         @RequestParam(name = "par_number", required = false) String parNumber,
                                         @RequestParam(name = "par_date", required = false) LocalDate parDate,
                                         @RequestParam(name = "police_notified", required = false) Boolean police,
                                         @RequestParam(name = "police_station", required = false) String policeStation,
                                         @RequestParam(name = "police_date", required = false) LocalDate policeDate) {
        if (!List.of("lost", "stolen", "damaged", "destroyed").contains(status)) throw ApiException.validation("status", "Choose lost, stolen, damaged or destroyed.");
        if (circumstances.isBlank() || circumstances.length() > 2000) throw ApiException.validation("circumstances", "Describe the circumstances (up to 2,000 characters).");
        one("select cast(id as varchar(20)) from properties where id = ?", "Property", propertyId);
        return file(rlsddp.generate(propertyId, status, circumstances.trim(), entity(entity), fund(fund), blank(department), blank(number),
                        date == null ? null : date.toString(), blank(parNumber), parDate == null ? null : parDate.toString(), police, blank(policeStation),
                        policeDate == null ? null : policeDate.toString()),
                "RLSDDP_" + stamp() + ".xlsx", "RLSDDP (" + status + ")", "App\\Models\\Property", propertyId);
    }

    private void checkDisposals(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw ApiException.validation("disposal_ids", "Choose at least one disposal.");
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        Long n = jdbc.queryForObject("select count(*) from disposal_requests where id in (" + in + ")", Long.class, ids.toArray());
        if (n == null || n != ids.stream().distinct().count()) throw ApiException.validation("disposal_ids", "One of the disposals does not exist.");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ───────────────────────── issuance slips ─────────────────────────

    private static final String SLIPS = "@perm.any('view_requests', 'view_own_requests', 'create_requests', 'approve_requests', 'release_stock', 'view_reports')";

    /** Appendix 63 — Requisition and Issue Slip. */
    @GetMapping("/requests/{id}/export-ris")
    @PreAuthorize(SLIPS)
    public ResponseEntity<byte[]> ris(@PathVariable Long id, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        String no = number(id, "ris_number");
        return file(ris.generate(id, entity(entity), fund(fund)), "RIS_" + no + ".xlsx", "RIS " + no, "App\\Models\\Request", id);
    }

    /** Appendix 71 — Property Acknowledgment Receipt. */
    @GetMapping("/requests/{id}/export-par")
    @PreAuthorize(SLIPS)
    public ResponseEntity<byte[]> par(@PathVariable Long id, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        String no = number(id, "par_number");
        return file(par.generate(id, entity(entity), fund(fund)), "PAR_" + no + ".xlsx", "PAR " + no, "App\\Models\\Request", id);
    }

    /** Appendix 59 — Inventory Custodian Slip. */
    @GetMapping("/requests/{id}/export-ics")
    @PreAuthorize(SLIPS)
    public ResponseEntity<byte[]> ics(@PathVariable Long id, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        String no = number(id, "ics_number");
        return file(ics.generate(id, entity(entity), fund(fund)), "ICS_" + no + ".xlsx", "ICS " + no, "App\\Models\\Request", id);
    }

    // ───────────────────────── receiving, transfers, bidding ─────────────────────────

    /** Appendix 62 — Inspection and Acceptance Report. */
    @GetMapping("/receiving/inspection-reports/{id}/export")
    @PreAuthorize("@perm.any('view_receiving', 'accept_delivery', 'view_reports')")
    public ResponseEntity<byte[]> iar(@PathVariable long id, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        String no = one("select iar_number from inspection_acceptance_reports where id = ?", "Inspection report", id);
        return file(iar.generate(id, entity(entity), fund(fund)), "IAR_" + no + ".xlsx", "IAR " + no, "App\\Models\\InspectionAcceptanceReport", id);
    }

    /** Appendix 76 — Property Transfer Report. */
    @GetMapping("/properties/transfers/{transferId}/export")
    @PreAuthorize("@perm.any('view_properties', 'transfer_properties', 'view_reports')")
    public ResponseEntity<byte[]> ptr(@PathVariable long transferId, @RequestParam(name = "entity_name", required = false) String entity,
                                      @RequestParam(name = "fund_cluster", required = false) String fund) {
        List<String> ref = jdbc.queryForList("select coalesce(reference_number, cast(id as varchar(20))) from property_transfers where id = ?", String.class, transferId);
        if (ref.isEmpty()) throw ApiException.notFound("Transfer");
        return file(ptr.generate(transferId, entity(entity), fund(fund)), "PTR_" + ref.get(0) + ".xlsx", "PTR " + ref.get(0), "App\\Models\\PropertyTransfer", transferId);
    }

    /** Abstract of Bids as calculated. */
    @GetMapping("/bidding/{id}/abstract/export")
    @PreAuthorize("@perm.any('view_bidding', 'manage_bidding', 'process_award', 'conduct_bid_evaluation')")
    public ResponseEntity<byte[]> abstractOfBids(@PathVariable Long id) {
        String no = one("select bidding_number from biddings where id = ? and deleted_at is null", "Bidding", id);
        String agency = settings.get("agency_name", "");
        return file(abstractOfBids.generate(id, agency == null || agency.isBlank() ? null : agency), "AbstractOfBids_" + no + ".xlsx",
                "Abstract of Bids " + no, "App\\Models\\Bidding", id);
    }

    // ───────────────────────── helpers ─────────────────────────

    String entity(String given) {
        return given != null && !given.isBlank() ? given.trim() : settings.get("agency_name", "");
    }

    String fund(String given) {
        return given != null && !given.isBlank() ? given.trim() : settings.get("fund_cluster", "");
    }

    private String number(Long requestId, String column) {
        List<String> no = jdbc.queryForList("select coalesce(" + column + ", request_number, cast(id as varchar(20))) from requests where id = ?", String.class, requestId);
        if (no.isEmpty()) throw ApiException.notFound("Request");
        return no.get(0);
    }

    private String one(String sql, String what, Object id) {
        List<String> v = jdbc.queryForList(sql, String.class, id);
        if (v.isEmpty()) throw ApiException.notFound(what);
        return v.get(0) == null ? String.valueOf(id) : v.get(0);
    }

    static String stamp() {
        return LocalDate.now(Times.ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    ResponseEntity<byte[]> file(byte[] bytes, String filename, String what, String type, Long id) {
        audit.record("downloaded", type, id, "Downloaded " + what + " (" + filename + ")", null, Map.of("file", filename));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename.replaceAll("[\\\\/:*?\"<>|]", "-"), StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}

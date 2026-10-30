package com.avtech.pams.report;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Times;
import com.avtech.pams.report.coa.AppCseForm;
import com.avtech.pams.report.coa.AppGppbForm;
import com.avtech.pams.report.coa.AprForm;
import com.avtech.pams.report.coa.CoaTransmittalForm;
import com.avtech.pams.report.coa.PpmpGppbForm;
import com.avtech.pams.setting.SettingService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Government procurement planning and reporting forms: PPMP and APP in the GPPB-prescribed formats,
 * APP-CSE, the GPPB Agency Procurement Report and the COA transmittal of POs and contracts — each a
 * JSON preview and an Excel download laid out as the form. Every download is audited.
 */
@RestController
@RequestMapping("/api/reports/planning")
@PreAuthorize("@perm.any('view_reports', 'generate_reports', 'view_procurement_planning', 'view_procurement')")
public class PlanningReportController {

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final AuditService audit;
    private final PpmpGppbForm ppmp;
    private final AppGppbForm app;
    private final AppCseForm appCse;
    private final AprForm apr;
    private final CoaTransmittalForm transmittal;

    public PlanningReportController(JdbcTemplate jdbc, SettingService settings, AuditService audit, PpmpGppbForm ppmp, AppGppbForm app,
                                    AppCseForm appCse, AprForm apr, CoaTransmittalForm transmittal) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.audit = audit;
        this.ppmp = ppmp;
        this.app = app;
        this.appCse = appCse;
        this.apr = apr;
        this.transmittal = transmittal;
    }

    private static int year(Integer fy) {
        int y = fy == null ? LocalDate.now(Times.ZONE).getYear() : fy;
        if (y < 2000 || y > 2100) throw ApiException.validation("fiscal_year", "Enter a fiscal year between 2000 and 2100.");
        return y;
    }

    /** Filter choices: end-user units and the PPMPs of the fiscal year (all years when none is given). */
    @GetMapping("/options")
    public Map<String, Object> options(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("departments", jdbc.queryForList("select id, name, code from departments order by name"));
        out.put("ppmps", fy == null
                ? jdbc.queryForList("""
                        select p.id, p.ppmp_number, p.title, p.status, p.fiscal_year, p.department_id, d.name as department
                        from ppmps p left join departments d on d.id = p.department_id where p.deleted_at is null order by p.fiscal_year desc, p.ppmp_number""")
                : jdbc.queryForList("""
                        select p.id, p.ppmp_number, p.title, p.status, p.fiscal_year, p.department_id, d.name as department
                        from ppmps p left join departments d on d.id = p.department_id where p.deleted_at is null and p.fiscal_year = ?
                        order by p.ppmp_number""", fy));
        out.put("document_types", CoaTransmittalForm.TYPES);
        return out;
    }

    // ───────────────────────── PPMP (GPPB) ─────────────────────────

    @GetMapping("/ppmp")
    public Map<String, Object> ppmp(@RequestParam(name = "ppmp_id", required = false) Long ppmpId,
                                    @RequestParam(name = "department_id", required = false) Long departmentId,
                                    @RequestParam(name = "fiscal_year", required = false) Integer fy,
                                    @RequestParam(required = false) String status) {
        return ppmp.data(ppmpId, departmentId, ppmpId == null ? year(fy) : null, status);
    }

    @GetMapping("/ppmp/export")
    public ResponseEntity<byte[]> ppmpExport(@RequestParam(name = "ppmp_id", required = false) Long ppmpId,
                                             @RequestParam(name = "department_id", required = false) Long departmentId,
                                             @RequestParam(name = "fiscal_year", required = false) Integer fy,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(name = "entity_name", required = false) String entity) {
        Map<String, Object> d = ppmp(ppmpId, departmentId, fy, status);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) d.get("ppmps");
        String name = ppmpId != null && !list.isEmpty() ? String.valueOf(list.get(0).get("ppmp_number"))
                : code(d.get("department_id")) + "_FY" + d.get("fiscal_year");
        return file(ppmp.generate(d, entity(entity)), "PPMP_" + name + ".xlsx", "PPMP (GPPB format) " + name,
                ppmpId != null ? "App\\Models\\Ppmp" : null, ppmpId);
    }

    // ───────────────────────── APP (GPPB) ─────────────────────────

    @GetMapping("/app")
    public Map<String, Object> app(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        return app.data(year(fy));
    }

    @GetMapping("/app/export")
    public ResponseEntity<byte[]> appExport(@RequestParam(name = "fiscal_year", required = false) Integer fy,
                                            @RequestParam(name = "entity_name", required = false) String entity) {
        Map<String, Object> d = app(fy);
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) d.get("app");
        return file(app.generate(d, entity(entity)), "APP_GPPB_FY" + d.get("fiscal_year") + ".xlsx", "APP (GPPB format) FY " + d.get("fiscal_year"),
                a == null ? null : "App\\Models\\App", a == null ? null : ((Number) a.get("id")).longValue());
    }

    // ───────────────────────── APP-CSE ─────────────────────────

    @GetMapping("/app-cse")
    public Map<String, Object> appCse(@RequestParam(name = "fiscal_year", required = false) Integer fy,
                                      @RequestParam(required = false) String source,
                                      @RequestParam(name = "department_id", required = false) Long departmentId) {
        return appCse.data(year(fy), source, departmentId);
    }

    @GetMapping("/app-cse/export")
    public ResponseEntity<byte[]> appCseExport(@RequestParam(name = "fiscal_year", required = false) Integer fy,
                                               @RequestParam(required = false) String source,
                                               @RequestParam(name = "department_id", required = false) Long departmentId,
                                               @RequestParam(name = "entity_name", required = false) String entity) {
        Map<String, Object> d = appCse(fy, source, departmentId);
        String suffix = departmentId == null ? "" : "_" + code(departmentId);
        return file(appCse.generate(d, entity(entity)), "APP-CSE_FY" + d.get("fiscal_year") + suffix + ".xlsx", "APP-CSE FY " + d.get("fiscal_year"), null, null);
    }

    // ───────────────────────── Agency Procurement Report ─────────────────────────

    @GetMapping("/apr")
    public Map<String, Object> apr(@RequestParam(name = "fiscal_year", required = false) Integer fy) {
        return apr.data(year(fy));
    }

    @GetMapping("/apr/export")
    public ResponseEntity<byte[]> aprExport(@RequestParam(name = "fiscal_year", required = false) Integer fy,
                                            @RequestParam(name = "entity_name", required = false) String entity) {
        Map<String, Object> d = apr(fy);
        return file(apr.generate(d, entity(entity)), "APR_FY" + d.get("fiscal_year") + ".xlsx", "Agency Procurement Report FY " + d.get("fiscal_year"), null, null);
    }

    // ───────────────────────── COA transmittal ─────────────────────────

    @GetMapping("/coa-transmittal")
    public Map<String, Object> coaTransmittal(@RequestParam(name = "date_from", required = false) LocalDate from,
                                              @RequestParam(name = "date_to", required = false) LocalDate to,
                                              @RequestParam(name = "document_type", required = false) String type,
                                              @RequestParam(name = "transmittal_number", required = false) String number,
                                              @RequestParam(required = false) String auditor) {
        return transmittal.data(from, to, type, number, auditor);
    }

    @GetMapping("/coa-transmittal/export")
    public ResponseEntity<byte[]> coaTransmittalExport(@RequestParam(name = "date_from", required = false) LocalDate from,
                                                       @RequestParam(name = "date_to", required = false) LocalDate to,
                                                       @RequestParam(name = "document_type", required = false) String type,
                                                       @RequestParam(name = "transmittal_number", required = false) String number,
                                                       @RequestParam(required = false) String auditor,
                                                       @RequestParam(name = "entity_name", required = false) String entity) {
        Map<String, Object> d = coaTransmittal(from, to, type, number, auditor);
        return file(transmittal.generate(d, entity(entity)), "COA_Transmittal_" + d.get("date_from") + "_to_" + d.get("date_to") + ".xlsx",
                "COA transmittal " + d.get("date_from") + " to " + d.get("date_to") + " (" + ((List<?>) d.get("rows")).size() + " documents)", null, null);
    }

    // ───────────────────────── helpers ─────────────────────────

    private String entity(String given) {
        return given != null && !given.isBlank() ? given.trim() : settings.get("agency_name", "");
    }

    private String code(Object departmentId) {
        if (departmentId == null) return "ALL";
        List<String> c = jdbc.queryForList("select coalesce(nullif(code, ''), cast(id as varchar(20))) from departments where id = ?", String.class,
                ((Number) departmentId).longValue());
        return c.isEmpty() ? String.valueOf(departmentId) : c.get(0);
    }

    private ResponseEntity<byte[]> file(byte[] bytes, String filename, String what, String type, Long id) {
        audit.record("downloaded", type, id, "Downloaded " + what + " (" + PdfFormAdvice.fileName(filename) + ")", null, Map.of("file", PdfFormAdvice.fileName(filename)));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(CoaExportController.XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename.replaceAll("[\\\\/:*?\"<>|\\s]", "-"), StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}

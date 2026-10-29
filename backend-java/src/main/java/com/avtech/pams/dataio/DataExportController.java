package com.avtech.pams.dataio;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * TOR Technical X: every table, every column, every row, as one UTF-8 CSV per table in a ZIP with a
 * manifest. Rows are streamed from a database cursor straight into the response. Secrets (password
 * hashes, two-factor secrets, remember and API tokens, revoked token ids) are left out.
 */
@RestController
@RequestMapping("/api/data")
public class DataExportController {

    static final Set<String> EXCLUDED_TABLES = Set.of("revoked_tokens");
    static final Map<String, Set<String>> EXCLUDED_COLUMNS = Map.of(
            "users", Set.of("password", "remember_token", "two_factor_secret", "two_factor_recovery_codes"),
            "personal_access_tokens", Set.of("token"),
            "import_batches", Set.of("payload"));
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final JdbcTemplate cursor;
    private final AuditService audit;
    private final ObjectProvider<BuildProperties> build;
    private final Db db;

    public DataExportController(JdbcTemplate jdbc, DataSource dataSource, AuditService audit, ObjectProvider<BuildProperties> build, Db db) {
        this.jdbc = jdbc;
        this.db = db;
        this.audit = audit;
        this.build = build;
        this.cursor = new JdbcTemplate(dataSource);
        this.cursor.setFetchSize(500);
    }

    @GetMapping("/export")
    @PreAuthorize("@perm.any('manage_system_settings')")
    @Transactional
    public void export(HttpServletResponse response) throws IOException {
        String schema = schema();
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                 where table_type = 'BASE TABLE' and table_schema = ?
                 order by table_name""", String.class, schema).stream()
                .filter(t -> !EXCLUDED_TABLES.contains(t)).toList();

        String stamp = Times.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pams_export_" + stamp + ".zip\"");

        List<Map<String, Object>> manifestTables = new ArrayList<>();
        long totalRows = 0;
        ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8);
        Writer w = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8), 1 << 16);
        for (String table : tables) {
            Set<String> skip = EXCLUDED_COLUMNS.getOrDefault(table, Set.of());
            List<String> columns = jdbc.queryForList("""
                    select column_name from information_schema.columns
                     where table_schema = ? and table_name = ?
                     order by ordinal_position""", String.class, schema, table).stream()
                    .filter(c -> !skip.contains(c)).toList();
            if (columns.isEmpty()) continue;
            zip.putNextEntry(new ZipEntry(table + ".csv"));
            w.write(Csv.BOM);
            Csv.row(w, columns.stream().map(Csv::raw).toList());
            String select = "select " + String.join(", ", columns.stream().map(DataExportController::quote).toList())
                    + " from " + quote(table) + orderBy(columns);
            long[] count = {0};
            cursor.query(select, (RowCallbackHandler) rs -> {
                try {
                    List<String> cells = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) cells.add(Csv.raw(text(rs.getObject(i))));
                    Csv.row(w, cells);
                    count[0]++;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            w.flush();
            zip.closeEntry();
            totalRows += count[0];
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("table", table);
            t.put("file", table + ".csv");
            t.put("row_count", count[0]);
            t.put("columns", columns);
            if (!skip.isEmpty()) t.put("excluded_columns", skip.stream().sorted().toList());
            manifestTables.add(t);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "pams-export");
        manifest.put("format_version", 1);
        manifest.put("app", "pams-backend");
        manifest.put("app_version", appVersion());
        manifest.put("schema_version", schemaVersion());
        manifest.put("exported_at", Times.toOffset(Times.now()).toString());
        manifest.put("timezone", Times.ZONE_ID);
        manifest.put("encoding", "UTF-8 with BOM, RFC 4180, CRLF line endings; empty cell = NULL");
        manifest.put("table_count", manifestTables.size());
        manifest.put("total_rows", totalRows);
        manifest.put("excluded_tables", EXCLUDED_TABLES.stream().sorted().toList());
        manifest.put("tables", manifestTables);
        zip.putNextEntry(new ZipEntry("manifest.json"));
        w.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        w.flush();
        zip.closeEntry();

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("tables", manifestTables.size());
        scope.put("rows", totalRows);
        audit.record("exported", null, null,
                "Exported the full database (" + manifestTables.size() + " tables, " + totalRows + " rows, ZIP of CSV)", null, scope);
        zip.finish();
        w.flush();
    }

    private String schema() {
        // The connection's current schema: "public" on PostgreSQL, "dbo" (the login's default) on SQL Server.
        String s = jdbc.execute((ConnectionCallback<String>) Connection::getSchema);
        return s == null || s.isBlank() ? (db.sqlServer() ? "dbo" : "public") : s;
    }

    private static String orderBy(List<String> columns) {
        return columns.contains("id") ? " order by " + quote("id") : "";
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    static String text(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? "true" : "false";
        if (v instanceof Timestamp t) return t.toLocalDateTime().format(TS);
        if (v instanceof BigDecimal d) return d.toPlainString();
        if (v instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        return v.toString();
    }

    private String appVersion() {
        BuildProperties b = build.getIfAvailable();
        if (b != null && b.getVersion() != null) return b.getVersion();
        String v = DataExportController.class.getPackage().getImplementationVersion();
        return v == null ? "development" : v;
    }

    private String schemaVersion() {
        try {
            List<String> v = jdbc.queryForList(
                    "select version from flyway_schema_history where success = ? and version is not null order by installed_rank desc",
                    String.class, true);
            return v.isEmpty() ? null : v.getFirst();
        } catch (RuntimeException e) {
            return null;
        }
    }
}

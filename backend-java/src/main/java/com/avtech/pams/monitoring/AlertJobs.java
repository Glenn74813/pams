package com.avtech.pams.monitoring;

import com.avtech.pams.common.Db;
import com.avtech.pams.common.Times;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.property.DepreciationService;
import com.avtech.pams.property.Property;
import com.avtech.pams.property.PropertyRepository;
import com.avtech.pams.setting.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The daily, weekly and monthly background jobs carried over from the Laravel scheduler: reorder
 * and expiry alerts, procurement and contract deadline alerts, the quarterly APP reminder, the
 * monthly depreciation run, notification clean-up and the database backup. Each one can also be
 * run on demand from Monitoring (POST /api/monitoring/alerts/run).
 */
@Component
public class AlertJobs {

    private static final Logger log = LoggerFactory.getLogger(AlertJobs.class);

    public static final List<String> JOBS = List.of("low_stock", "expiring_stock", "procurement_deadlines", "contracts", "supplier_documents",
            "procurement_schedule", "depreciation", "rsmi", "clean_notifications", "backup");

    public static final Map<String, String> SCHEDULE = Map.ofEntries(
            Map.entry("low_stock", "Daily 08:45"), Map.entry("expiring_stock", "Daily 08:50"), Map.entry("procurement_deadlines", "Daily 08:15"),
            Map.entry("contracts", "Daily 08:30"), Map.entry("supplier_documents", "Daily 08:40"),
            Map.entry("procurement_schedule", "08:20 on the first day of each quarter"), Map.entry("depreciation", "01:00 on the first day of each month"),
            Map.entry("rsmi", "23:59 on the last day of each month"), Map.entry("clean_notifications", "Sundays 03:00"), Map.entry("backup", "Daily 02:00"));

    private static final List<String> STOCK_PEOPLE = List.of("manage_warehouse", "create_adjustments", "approve_adjustments", "view_monitoring");
    // Not create_procurement: End Users hold it to raise purchase requests.
    private static final List<String> PROCUREMENT_PEOPLE = List.of("manage_bidding", "process_award", "create_rfq");
    private static final List<String> CONTRACT_PEOPLE = List.of("create_contracts", "edit_contracts");
    private static final String ITEM = "App\\Models\\Item";

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final ProcurementWatch watch;
    private final SettingService settings;
    private final PropertyRepository properties;
    private final DepreciationService depreciation;
    private final TransactionTemplate tx;
    private final Db db;
    private final String dbUrl, dbUser, dbPassword, storageDir, backupDir;
    private final ObjectProvider<com.avtech.pams.report.ReportController> reports;

    public AlertJobs(JdbcTemplate jdbc, NotificationService notifications, ProcurementWatch watch, SettingService settings,
                     PropertyRepository properties, DepreciationService depreciation, TransactionTemplate tx,
                     @Value("${spring.datasource.url}") String dbUrl, @Value("${spring.datasource.username}") String dbUser,
                     @Value("${spring.datasource.password}") String dbPassword, @Value("${pams.storage.public-dir}") String storageDir, Db db,
                     @Value("${pams.backup-dir:}") String backupDir, ObjectProvider<com.avtech.pams.report.ReportController> reports) {
        this.backupDir = backupDir;
        this.reports = reports;
        this.jdbc = jdbc;
        this.db = db;
        this.notifications = notifications;
        this.watch = watch;
        this.settings = settings;
        this.properties = properties;
        this.depreciation = depreciation;
        this.tx = tx;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.storageDir = storageDir;
    }

    /** Runs a job by name and returns what it did; a failing job is logged, never thrown. */
    public String run(String job) {
        try {
            return switch (job) {
                case "low_stock" -> lowStock();
                case "expiring_stock" -> expiringStock();
                case "procurement_deadlines" -> procurementDeadlines();
                case "contracts" -> contracts();
                case "supplier_documents" -> supplierDocuments();
                case "procurement_schedule" -> procurementSchedule();
                case "depreciation" -> depreciation();
                case "clean_notifications" -> cleanNotifications();
                case "backup" -> backup();
                case "rsmi" -> rsmi();
                default -> throw new IllegalArgumentException("Unknown job " + job);
            };
        } catch (RuntimeException e) {
            log.error("Alert job {} failed", job, e);
            return "Failed: " + e.getMessage();
        }
    }

    @Scheduled(cron = "0 15 8 * * *", zone = Times.ZONE_ID)
    void dailyProcurement() { log.info("procurement_deadlines: {}", run("procurement_deadlines")); }

    @Scheduled(cron = "0 30 8 * * *", zone = Times.ZONE_ID)
    void dailyContracts() { log.info("contracts: {}", run("contracts")); }

    @Scheduled(cron = "0 40 8 * * *", zone = Times.ZONE_ID)
    void dailySuppliers() { log.info("supplier_documents: {}", run("supplier_documents")); }

    @Scheduled(cron = "0 45 8 * * *", zone = Times.ZONE_ID)
    void dailyLowStock() { log.info("low_stock: {}", run("low_stock")); }

    @Scheduled(cron = "0 50 8 * * *", zone = Times.ZONE_ID)
    void dailyExpiring() { log.info("expiring_stock: {}", run("expiring_stock")); }

    @Scheduled(cron = "0 20 8 1 1,4,7,10 *", zone = Times.ZONE_ID)
    void quarterlySchedule() { log.info("procurement_schedule: {}", run("procurement_schedule")); }

    @Scheduled(cron = "0 0 1 1 * *", zone = Times.ZONE_ID)
    void monthlyDepreciation() { log.info("depreciation: {}", run("depreciation")); }

    @Scheduled(cron = "0 0 3 * * SUN", zone = Times.ZONE_ID)
    void weeklyClean() { log.info("clean_notifications: {}", run("clean_notifications")); }

    @Scheduled(cron = "0 59 23 L * *", zone = Times.ZONE_ID)
    void monthEndRsmi() { log.info("rsmi: {}", run("rsmi")); }

    @Scheduled(cron = "0 0 2 * * *", zone = Times.ZONE_ID)
    void dailyBackup() { log.info("backup: {}", run("backup")); }

    // ───────────────────────── jobs ─────────────────────────

    String lowStock() {
        int[] n = {0, 0};
        jdbc.query("select id, code, name, current_stock, min_stock from items where min_stock > 0 and current_stock <= min_stock order by name", rs -> {
            n[0]++;
            long id = rs.getLong(1);
            if (recentlyAlerted("low_stock", ITEM, id, 7)) return;
            int cur = rs.getInt(4), min = rs.getInt(5);
            notifications.notifyPermission(STOCK_PEOPLE, null, "low_stock", cur <= 0 ? "Reorder alert — out of stock" : "Reorder alert — low stock",
                    rs.getString(3) + " (" + rs.getString(2) + ") is at or below its reorder point: " + cur + " on hand, minimum " + min
                            + ". See Monitoring → Re-order report.", ITEM, id);
            n[1]++;
        });
        return n[0] + " item(s) at or below the reorder point, " + n[1] + " alerted (7-day cooldown).";
    }

    String expiringStock() {
        String days = settings.get("fefo_expiry_warning_days", "30").replaceAll("\\D", "");
        int window = days.isEmpty() ? 30 : Math.max(1, Math.min(Integer.parseInt(days), 365));
        LocalDate today = LocalDate.now(Times.ZONE);
        int[] n = {0, 0};
        jdbc.query("""
                select i.id, i.code, i.name,
                       coalesce(sum(case when s.expiration_date < ? then s.quantity end), 0),
                       coalesce(sum(case when s.expiration_date >= ? then s.quantity end), 0), min(s.expiration_date)
                from stocks s join items i on i.id = s.item_id
                where s.quantity > 0 and s.expiration_date is not null and s.expiration_date <= ?
                group by i.id, i.code, i.name order by min(s.expiration_date)""", rs -> {
            n[0]++;
            long id = rs.getLong(1);
            if (recentlyAlerted("expiring_stock", ITEM, id, 7)) return;
            long expired = rs.getLong(4), soon = rs.getLong(5);
            StringBuilder msg = new StringBuilder(rs.getString(3) + " (" + rs.getString(2) + "): ");
            if (expired > 0) msg.append(expired).append(" unit(s) expired — remove them from issue and dispose of them through the Waste Materials Report");
            if (expired > 0 && soon > 0) msg.append("; ");
            if (soon > 0) msg.append(soon).append(" unit(s) expire within ").append(window).append(" day(s) — issue these first");
            notifications.notifyPermission(STOCK_PEOPLE, null, "expiring_stock", expired > 0 ? "Expired stock needs disposal" : "Stock nearing expiry",
                    msg.append('.').toString(), ITEM, id);
            n[1]++;
        }, Date.valueOf(today), Date.valueOf(today), Date.valueOf(today.plusDays(window)));
        return n[0] + " item(s) with expired or expiring lots (" + window + "-day window), " + n[1] + " alerted.";
    }

    String procurementDeadlines() {
        return notifyWatch(Map.of("bidding_stage", PROCUREMENT_PEOPLE, "rfq_deadline", PROCUREMENT_PEOPLE,
                "po_delivery", List.of("view_receiving", "manage_warehouse", "view_procurement")), "procurement_deadline", "Procurement deadline");
    }

    String contracts() {
        return notifyWatch(Map.of("contract_delivery", CONTRACT_PEOPLE, "contract_expiry", CONTRACT_PEOPLE, "security_expiry", CONTRACT_PEOPLE),
                "contract_watch", "Contract");
    }

    String supplierDocuments() {
        return notifyWatch(Map.of("supplier_document", List.of("edit_suppliers", "create_suppliers")), "supplier_document", "Supplier document");
    }

    /** One notification per record per day, listing everything due on it. */
    private String notifyWatch(Map<String, List<String>> kinds, String type, String noun) {
        List<ProcurementWatch.Entry> rows = watch.scan().stream().filter(e -> kinds.containsKey(e.kind())).toList();
        Map<String, List<ProcurementWatch.Entry>> byRecord = rows.stream()
                .collect(Collectors.groupingBy(e -> e.relatedType() + "#" + e.relatedId() + "#" + e.kind(), LinkedHashMap::new, Collectors.toList()));
        int sent = 0;
        for (List<ProcurementWatch.Entry> group : byRecord.values()) {
            ProcurementWatch.Entry first = group.get(0);
            if (recentlyAlerted(type, first.relatedType(), first.relatedId(), 1, label(first.kind()))) continue;
            boolean overdue = group.stream().anyMatch(e -> ProcurementWatch.OVERDUE.equals(e.severity()));
            String body = first.reference() + (first.title() == null ? "" : " (" + first.title() + ")") + ": "
                    + group.stream().map(ProcurementWatch.Entry::detail).collect(Collectors.joining("; ")) + ".";
            notifications.notifyPermission(kinds.get(first.kind()), null, type, noun + (overdue ? " overdue" : " due soon") + " — " + label(first.kind()),
                    body, first.relatedType(), first.relatedId());
            sent++;
        }
        return rows.size() + " deadline(s) on " + byRecord.size() + " record(s), " + sent + " alerted.";
    }

    private static String label(String kind) {
        return switch (kind) {
            case "bidding_stage" -> "bidding stage";
            case "rfq_deadline" -> "quotation deadline";
            case "po_delivery" -> "PO delivery";
            case "contract_delivery" -> "delivery delay";
            case "contract_expiry" -> "contract expiry";
            case "security_expiry" -> "security validity";
            case "supplier_document" -> "eligibility document";
            default -> kind;
        };
    }

    String procurementSchedule() {
        List<ProcurementWatch.Entry> due = watch.scan().stream().filter(e -> "app_schedule".equals(e.kind())).toList();
        if (due.isEmpty()) return "Every scheduled APP line up to this quarter has a purchase request.";
        int quarter = (LocalDate.now(Times.ZONE).getMonthValue() - 1) / 3 + 1;
        notifications.notifyPermission(List.of("view_procurement", "manage_app", "create_rfq", "manage_bidding"), null, "procurement_schedule",
                "Items due for procurement — Q" + quarter,
                due.size() + " approved APP line(s) scheduled up to Q" + quarter + " still have no purchase request: "
                        + due.stream().limit(5).map(ProcurementWatch.Entry::title).collect(Collectors.joining(", "))
                        + (due.size() > 5 ? " and " + (due.size() - 5) + " more" : "") + ". See Monitoring → Procurement watch.",
                "App\\Models\\App", due.get(0).relatedId());
        return due.size() + " APP line(s) awaiting purchase requests; reminder sent.";
    }

    String depreciation() {
        YearMonth through = YearMonth.now(Times.ZONE).minusMonths(1);
        int[] totals = {0, 0};
        for (Property listed : properties.depreciableAssets()) {
            Integer n = tx.execute(s -> depreciation.postThrough(properties.lockById(listed.getId()).orElseThrow(), through));
            if (n != null && n > 0) {
                totals[0]++;
                totals[1] += n;
            }
        }
        return totals[1] == 0 ? "Nothing new to post through " + through + "."
                : "Posted " + totals[1] + " month(s) of depreciation for " + totals[0] + " asset(s) through " + through + ".";
    }

    String cleanNotifications() {
        int n = jdbc.update("delete from notifications where is_read = " + db.t() + " and created_at < ?", Timestamp.valueOf(Times.now().minusDays(30)));
        return "Deleted " + n + " read notification(s) older than 30 days.";
    }

    /** The month's RSMI summary, as Laravel's generate:rsmi did at month end (Reports → RSMI lists it). */
    String rsmi() {
        java.time.LocalDate today = java.time.LocalDate.now(Times.ZONE);
        var r = reports.getObject();
        Map<String, Object> saved = r.store(today.getYear(), today.getMonthValue(), r.rsmiData(today.getYear(), today.getMonthValue()), null);
        return "RSMI " + saved.get("report_number") + " generated for " + today.getMonth() + " " + today.getYear() + ".";
    }

    /** storage/backups beside the uploads unless pams.backup-dir (PAMS_BACKUP_DIR) says otherwise. */
    private Path backupDir() {
        return backupDir == null || backupDir.isBlank() ? Path.of(storageDir).toAbsolutePath().getParent().resolve("backups") : Path.of(backupDir).toAbsolutePath();
    }

    /** A full database backup, kept 7 days: pg_dump on PostgreSQL, BACKUP DATABASE on SQL Server. */
    String backup() {
        if (db.sqlServer()) return sqlServerBackup();
        // jdbc:postgresql://host:port/db
        String rest = dbUrl.replaceFirst("^jdbc:postgresql://", "");
        String hostPort = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        String db = rest.contains("/") ? rest.substring(rest.indexOf('/') + 1).replaceFirst("\\?.*$", "") : "inventory";
        String host = hostPort.contains(":") ? hostPort.substring(0, hostPort.indexOf(':')) : hostPort;
        String port = hostPort.contains(":") ? hostPort.substring(hostPort.indexOf(':') + 1) : "5432";
        Path dir = backupDir();
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("backup_" + Times.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".dump");
            ProcessBuilder pb = new ProcessBuilder("pg_dump", "-h", host, "-p", port, "-U", dbUser, "-Fc", "-f", file.toString(), db);
            pb.environment().put("PGPASSWORD", dbPassword == null ? "" : dbPassword);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(dir.resolve("backup.log").toFile()));
            Process p = pb.start();
            if (!p.waitFor(10, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                return "Failed: pg_dump took longer than 10 minutes.";
            }
            if (p.exitValue() != 0) return "Failed: pg_dump exited with " + p.exitValue() + " (see " + dir.resolve("backup.log") + ").";
            int removed = 0;
            Instant cutoff = Instant.now().minus(Duration.ofDays(7));
            try (Stream<Path> old = Files.list(dir)) {
                for (Path f : old.filter(f -> f.getFileName().toString().startsWith("backup_")).toList()) {
                    if (Files.getLastModifiedTime(f).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(f);
                        removed++;
                    }
                }
            }
            return "Backup written to " + file + " (" + Files.size(file) / 1024 + " KB)" + (removed > 0 ? "; removed " + removed + " older than 7 days." : ".");
        } catch (IOException e) {
            return "Failed: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Failed: interrupted.";
        }
    }

    /**
     * BACKUP DATABASE to the backup folder. The path is opened by the SQL Server service, so it must be a folder
     * that service can write (on the database server, or a share). Old files are pruned when this process can see them.
     */
    private String sqlServerBackup() {
        String name = jdbc.queryForObject("select db_name()", String.class);
        String folder = backupDir == null || backupDir.isBlank() ? backupDir().toString() : backupDir;
        String sep = folder.contains("\\") ? "\\" : "/";
        String file = folder.replaceAll("[\\\\/]+$", "") + sep + "backup_" + Times.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".bak";
        try {
            jdbc.execute((java.sql.Connection c) -> {
                boolean auto = c.getAutoCommit();
                c.setAutoCommit(true); // BACKUP cannot run inside a transaction
                try (java.sql.PreparedStatement ps = c.prepareStatement("BACKUP DATABASE [" + name.replace("]", "]]") + "] TO DISK = ? WITH INIT, CHECKSUM")) {
                    ps.setString(1, file);
                    ps.execute();
                    while (ps.getMoreResults() || ps.getUpdateCount() != -1) { /* drain informational results */ }
                } finally {
                    c.setAutoCommit(auto);
                }
                return null;
            });
        } catch (org.springframework.dao.DataAccessException e) {
            return "Failed: " + e.getMostSpecificCause().getMessage();
        }
        int removed = 0;
        Path dir = Path.of(folder);
        if (Files.isDirectory(dir)) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(7));
            try (Stream<Path> old = Files.list(dir)) {
                for (Path f : old.filter(f -> f.getFileName().toString().startsWith("backup_")).toList()) {
                    if (Files.getLastModifiedTime(f).toInstant().isBefore(cutoff) && Files.deleteIfExists(f)) removed++;
                }
            } catch (IOException ignored) {
                // Pruning is best effort.
            }
        }
        return "Backup written to " + file + (removed > 0 ? "; removed " + removed + " older than 7 days." : ".");
    }

    private boolean recentlyAlerted(String type, String relatedType, Long relatedId, int days) {
        return recentlyAlerted(type, relatedType, relatedId, days, null);
    }

    /** True when the same alert (same title ending, if given) went out in the last {@code days} days; one day = since midnight. */
    private boolean recentlyAlerted(String type, String relatedType, Long relatedId, int days, String titleEnd) {
        Timestamp since = Timestamp.valueOf(LocalDate.now(Times.ZONE).minusDays(days - 1L).atStartOfDay());
        List<Object> args = new java.util.ArrayList<>();
        StringBuilder where = new StringBuilder("type = ?");
        args.add(type);
        if (relatedType == null) where.append(" and related_type is null");
        else { where.append(" and related_type = ?"); args.add(relatedType); }
        if (relatedId == null) where.append(" and related_id is null");
        else { where.append(" and related_id = ?"); args.add(relatedId); }
        where.append(" and created_at >= ?");
        args.add(since);
        if (titleEnd != null) { where.append(" and title like concat('%— ', ?)"); args.add(titleEnd); }
        Integer hit = jdbc.queryForObject("select case when exists (select 1 from notifications where " + where + ") then 1 else 0 end",
                Integer.class, args.toArray());
        return hit != null && hit == 1;
    }
}

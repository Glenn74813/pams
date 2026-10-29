package com.avtech.pams.monitoring;

import com.avtech.pams.common.Times;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.property.DepreciationService;
import com.avtech.pams.property.Property;
import com.avtech.pams.property.PropertyRepository;
import com.avtech.pams.setting.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            "procurement_schedule", "depreciation", "clean_notifications", "backup");

    public static final Map<String, String> SCHEDULE = Map.of(
            "low_stock", "Daily 08:45", "expiring_stock", "Daily 08:50", "procurement_deadlines", "Daily 08:15", "contracts", "Daily 08:30",
            "supplier_documents", "Daily 08:40", "procurement_schedule", "08:20 on the first day of each quarter",
            "depreciation", "01:00 on the first day of each month", "clean_notifications", "Sundays 03:00", "backup", "Daily 02:00");

    private static final List<String> STOCK_PEOPLE = List.of("manage_warehouse", "create_adjustments", "approve_adjustments", "view_monitoring");
    private static final List<String> PROCUREMENT_PEOPLE = List.of("manage_bidding", "process_award", "create_rfq", "create_procurement");
    private static final List<String> CONTRACT_PEOPLE = List.of("create_contracts", "edit_contracts");
    private static final String ITEM = "App\\Models\\Item";

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final ProcurementWatch watch;
    private final SettingService settings;
    private final PropertyRepository properties;
    private final DepreciationService depreciation;
    private final TransactionTemplate tx;
    private final String dbUrl, dbUser, dbPassword, storageDir;

    public AlertJobs(JdbcTemplate jdbc, NotificationService notifications, ProcurementWatch watch, SettingService settings,
                     PropertyRepository properties, DepreciationService depreciation, TransactionTemplate tx,
                     @Value("${spring.datasource.url}") String dbUrl, @Value("${spring.datasource.username}") String dbUser,
                     @Value("${spring.datasource.password}") String dbPassword, @Value("${pams.storage.public-dir}") String storageDir) {
        this.jdbc = jdbc;
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
                       coalesce(sum(s.quantity) filter (where s.expiration_date < ?), 0),
                       coalesce(sum(s.quantity) filter (where s.expiration_date >= ?), 0), min(s.expiration_date)
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
                "po_delivery", List.of("view_receiving", "create_procurement", "manage_warehouse")), "procurement_deadline", "Procurement deadline");
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
        notifications.notifyPermission(List.of("create_procurement", "view_procurement", "create_rfq", "manage_bidding"), null, "procurement_schedule",
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
        int n = jdbc.update("delete from notifications where is_read = true and created_at < ?", Timestamp.valueOf(Times.now().minusDays(30)));
        return "Deleted " + n + " read notification(s) older than 30 days.";
    }

    /** pg_dump of the whole database into storage/backups, keeping the last 7 days. */
    String backup() {
        // jdbc:postgresql://host:port/db
        String rest = dbUrl.replaceFirst("^jdbc:postgresql://", "");
        String hostPort = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        String db = rest.contains("/") ? rest.substring(rest.indexOf('/') + 1).replaceFirst("\\?.*$", "") : "inventory";
        String host = hostPort.contains(":") ? hostPort.substring(0, hostPort.indexOf(':')) : hostPort;
        String port = hostPort.contains(":") ? hostPort.substring(hostPort.indexOf(':') + 1) : "5432";
        Path dir = Path.of(storageDir).toAbsolutePath().getParent().resolve("backups");
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

    private boolean recentlyAlerted(String type, String relatedType, Long relatedId, int days) {
        return recentlyAlerted(type, relatedType, relatedId, days, null);
    }

    /** True when the same alert (same title ending, if given) went out in the last {@code days} days; one day = since midnight. */
    private boolean recentlyAlerted(String type, String relatedType, Long relatedId, int days, String titleEnd) {
        Timestamp since = Timestamp.valueOf(LocalDate.now(Times.ZONE).minusDays(days - 1L).atStartOfDay());
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from notifications where type = ? and related_type is not distinct from ? and related_id is not distinct from ?
                               and created_at >= ? and (cast(? as text) is null or title like '%— ' || cast(? as text)))""",
                Boolean.class, type, relatedType, relatedId, since, titleEnd, titleEnd));
    }
}

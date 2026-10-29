package com.avtech.pams.document;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Electronic document repository with version history (TOR §15): procurement and inventory
 * records are filed by type and may be attached to the record they belong to (a PR, PO, contract…).
 * Every version keeps its file and a SHA-256 checksum; downloads are recorded in the audit trail.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    public static final List<String> TYPES = List.of("PPMP", "APP", "PR", "RFQ", "ABSTRACT", "BAC_RESOLUTION", "CONTRACT", "PO", "IAR", "NOA", "NTP",
            "DISPOSAL", "OTHER");
    /** Records a document may be attached to (Laravel model names, so both apps agree). */
    static final Set<String> OWNERS = Set.of("Ppmp", "App", "PurchaseRequest", "Rfq", "Bidding", "BacResolution", "Contract", "PurchaseOrder",
            "InspectionAcceptanceReport", "DisposalRequest", "Supplier", "Property");
    private static final Map<String, String> MIME = Map.of("pdf", "application/pdf", "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "xls", "application/vnd.ms-excel",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg");
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final String TYPE = "App\\Models\\Document";

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;
    private final Path root;
    private final Db db;

    public DocumentController(JdbcTemplate jdbc, CurrentUser current, AuditService audit, Db db, @Value("${pams.storage.public-dir}") String publicDir) {
        this.jdbc = jdbc;
        this.db = db;
        this.current = current;
        this.audit = audit;
        this.root = Path.of(publicDir).toAbsolutePath().normalize();
    }

    public record Ref(Long id, String name) {
    }

    public record VersionView(Long id, int versionNumber, String originalFilename, String mimeType, long fileSize, String fileSizeHuman,
                              String checksum, String remarks, Ref uploader, OffsetDateTime createdAt) {
    }

    public record DocumentView(Long id, String title, String documentNumber, String type, String category, String description,
                               String documentableType, Long documentableId, String status, int currentVersion, Ref uploader,
                               VersionView latestVersion, List<VersionView> versions, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    @GetMapping
    @PreAuthorize("@perm.any('view_documents', 'upload_documents')")
    public PageResponse<DocumentView> index(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(name = "per_page", defaultValue = "20") int perPage,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(name = "documentable_type", required = false) String ownerType,
                                            @RequestParam(name = "documentable_id", required = false) Long ownerId,
                                            @RequestParam(required = false) String search) {
        int size = Math.max(1, Math.min(perPage, 100));
        int p = Math.max(1, page);
        StringBuilder w = new StringBuilder(" where d.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (blank(type) != null) {
            w.append(" and d.type = ?");
            args.add(type.trim());
        }
        if (blank(status) != null) {
            w.append(" and d.status = ?");
            args.add(status.trim());
        }
        if (blank(ownerType) != null && ownerId != null) {
            String model = shortName(ownerType);
            w.append(" and d.documentable_id = ? and d.documentable_type in (?, ?)");
            args.add(ownerId);
            args.add(model);
            args.add("App\\Models\\" + model);
        }
        if (blank(search) != null) {
            w.append(" and (lower(d.title) like lower(?) escape '\\' or lower(d.document_number) like lower(?) escape '\\' or lower(d.category) like lower(?) escape '\\')");
            String like = "%" + search.trim().replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = jdbc.queryForObject("select count(*) from documents d" + w, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((p - 1) * size);
        pageArgs.add(size);
        List<DocumentView> rows = jdbc.query(SELECT + w + " order by d.created_at desc, d.id desc offset ? rows fetch next ? rows only", (rs, i) -> doc(rs, null), pageArgs.toArray());
        long n = total == null ? 0 : total;
        return new PageResponse<>(rows, new PageResponse.Pagination(n, size, p, Math.max(1, (int) ((n + size - 1) / size))));
    }

    @GetMapping("/types")
    public Map<String, Object> types() {
        return Map.of("types", TYPES, "owners", OWNERS.stream().sorted().toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.any('view_documents', 'upload_documents')")
    public DocumentView show(@PathVariable Long id) {
        return load(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.any('upload_documents')")
    @Transactional
    public ResponseEntity<DocumentView> store(@RequestParam String title, @RequestParam String type,
                                              @RequestParam(name = "document_number", required = false) String number,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) String description,
                                              @RequestParam(name = "documentable_type", required = false) String ownerType,
                                              @RequestParam(name = "documentable_id", required = false) Long ownerId,
                                              @RequestParam(required = false) String remarks,
                                              @RequestParam(required = false) MultipartFile file) {
        String t = blank(title);
        if (t == null || t.length() > 255) throw ApiException.validation("title", "Give the document a title (up to 255 characters).");
        if (!TYPES.contains(type)) throw ApiException.validation("type", "Choose a document type.");
        if (blank(number) != null && number.trim().length() > 100) throw ApiException.validation("document_number", "The document number is too long.");
        String owner = null;
        if (blank(ownerType) != null || ownerId != null) {
            owner = shortName(ownerType);
            if (!OWNERS.contains(owner) || ownerId == null) throw ApiException.validation("documentable_type", "Attach the document to a known record.");
        }
        checkFile(file);
        Timestamp now = Timestamp.valueOf(Times.now());
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String ownerFinal = owner;
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("""
                    insert into documents (title, document_number, type, category, description, documentable_type, documentable_id, status,
                                           current_version, uploaded_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, 'active', 0, ?, ?, ?)""", new String[]{"id"});
            ps.setString(1, t);
            ps.setString(2, blank(number));
            ps.setString(3, type);
            ps.setString(4, blank(category));
            ps.setString(5, blank(description));
            ps.setString(6, ownerFinal == null ? null : "App\\Models\\" + ownerFinal);
            ps.setObject(7, ownerFinal == null ? null : ownerId);
            ps.setLong(8, current.user().getId());
            ps.setTimestamp(9, now);
            ps.setTimestamp(10, now);
            return ps;
        }, key);
        long id = key.getKey().longValue();
        storeVersion(id, 0, null, file, remarks);
        audit.record("created", TYPE, id, "Filed " + type + " document “" + t + "”" + (owner == null ? "" : " on " + owner + " #" + ownerId), null,
                Map.of("title", t, "type", type, "file", file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        return ResponseEntity.status(HttpStatus.CREATED).body(load(id));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.any('upload_documents')")
    @Transactional
    public ResponseEntity<DocumentView> addVersion(@PathVariable Long id, @RequestParam(required = false) MultipartFile file,
                                                   @RequestParam(required = false) String remarks) {
        Map<String, Object> d = lock(id);
        if ("archived".equals(d.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Restore the document from the archive before adding a version.");
        checkFile(file);
        String latest = jdbc.query("select checksum from document_versions where document_id = ? order by version_number desc offset 0 rows fetch next 1 rows only",
                rs -> rs.next() ? rs.getString(1) : null, id);
        int next = ((Number) d.get("current_version")).intValue();
        int v = storeVersion(id, next, latest, file, remarks);
        audit.record("versioned", TYPE, id, "Version " + v + " of “" + d.get("title") + "” uploaded" + (blank(remarks) == null ? "" : ": " + remarks.trim()),
                Map.of("version", next), Map.of("version", v));
        return ResponseEntity.status(HttpStatus.CREATED).body(load(id));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("@perm.any('view_documents', 'upload_documents')")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Long version = jdbc.query("select id from document_versions where document_id = ? order by version_number desc offset 0 rows fetch next 1 rows only",
                rs -> rs.next() ? rs.getLong(1) : null, id);
        if (version == null) throw ApiException.of(HttpStatus.NOT_FOUND, "This document has no file.");
        return downloadVersion(id, version);
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    @PreAuthorize("@perm.any('view_documents', 'upload_documents')")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long id, @PathVariable Long versionId) {
        DocumentView d = load(id);
        List<Map<String, Object>> rows = jdbc.queryForList("select * from document_versions where id = ? and document_id = ?", versionId, id);
        if (rows.isEmpty()) throw ApiException.notFound("Version");
        Map<String, Object> v = rows.get(0);
        Path file = resolve((String) v.get("file_path"));
        if (file == null || !Files.isRegularFile(file)) throw ApiException.of(HttpStatus.NOT_FOUND, "The file for this version is missing from storage.");
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw ApiException.of(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the file.");
        }
        String expected = (String) v.get("checksum");
        boolean intact = expected == null || expected.equals(sha256(bytes));
        String name = (String) v.get("original_filename");
        audit.record("downloaded", TYPE, id, "Downloaded “" + d.title() + "” v" + v.get("version_number") + " (" + name + ")"
                + (intact ? "" : " — CHECKSUM MISMATCH"), null, null);
        String mime = v.get("mime_type") == null ? MIME.getOrDefault(extension(name), "application/octet-stream") : (String) v.get("mime_type");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Checksum-Verified", intact ? (expected == null ? "unknown" : "true") : "false")
                .body(bytes);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@perm.any('upload_documents')")
    @Transactional
    public DocumentView archive(@PathVariable Long id) {
        Map<String, Object> d = lock(id);
        String to = "archived".equals(d.get("status")) ? "active" : "archived";
        jdbc.update("update documents set status = ?, updated_at = ? where id = ?", to, Timestamp.valueOf(Times.now()), id);
        audit.record(to.equals("archived") ? "archived" : "restored", TYPE, id, (to.equals("archived") ? "Archived" : "Restored") + " “" + d.get("title") + "”",
                Map.of("status", d.get("status")), Map.of("status", to));
        return load(id);
    }

    /** Soft delete: the row and its files are kept for audit. */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('delete_documents')")
    @Transactional
    public Map<String, String> destroy(@PathVariable Long id) {
        Map<String, Object> d = lock(id);
        jdbc.update("update documents set deleted_at = ?, updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), Timestamp.valueOf(Times.now()), id);
        audit.record("deleted", TYPE, id, "Deleted “" + d.get("title") + "” (files kept for audit)", Map.of("title", d.get("title"), "type", d.get("type")), null);
        return Map.of("message", "Document deleted");
    }

    // ───────────────────────── helpers ─────────────────────────

    private static final String SELECT = """
            select d.*, u.name uploader_name,
                   v.id v_id, v.version_number v_no, v.original_filename v_name, v.mime_type v_mime, v.file_size v_size, v.checksum v_sum,
                   v.remarks v_remarks, v.uploaded_by v_by, vu.name v_by_name, v.created_at v_at
            from documents d
            left join users u on u.id = d.uploaded_by
            left join document_versions v on v.document_id = d.id
                  and v.version_number = (select max(x.version_number) from document_versions x where x.document_id = d.id)
            left join users vu on vu.id = v.uploaded_by""";

    private DocumentView load(Long id) {
        List<DocumentView> found = jdbc.query(SELECT + " where d.id = ? and d.deleted_at is null", (rs, i) -> doc(rs, versions(id)), id);
        if (found.isEmpty()) throw ApiException.notFound("Document");
        return found.get(0);
    }

    private Map<String, Object> lock(Long id) {
        List<Map<String, Object>> rows = db.lockRows("documents", "id = ? and deleted_at is null", id);
        if (rows.isEmpty()) throw ApiException.notFound("Document");
        return rows.get(0);
    }

    private List<VersionView> versions(Long id) {
        return jdbc.query("""
                select v.*, u.name uname from document_versions v left join users u on u.id = v.uploaded_by
                where v.document_id = ? order by v.version_number desc""", (rs, i) -> new VersionView(rs.getLong("id"), rs.getInt("version_number"),
                rs.getString("original_filename"), rs.getString("mime_type"), rs.getLong("file_size"), human(rs.getLong("file_size")),
                rs.getString("checksum"), rs.getString("remarks"), ref(rs, "uploaded_by", "uname"), at(rs, "created_at")), id);
    }

    private DocumentView doc(ResultSet rs, List<VersionView> versions) throws SQLException {
        VersionView latest = rs.getObject("v_id") == null ? null : new VersionView(rs.getLong("v_id"), rs.getInt("v_no"), rs.getString("v_name"),
                rs.getString("v_mime"), rs.getLong("v_size"), human(rs.getLong("v_size")), rs.getString("v_sum"), rs.getString("v_remarks"),
                ref(rs, "v_by", "v_by_name"), at(rs, "v_at"));
        String owner = rs.getString("documentable_type");
        return new DocumentView(rs.getLong("id"), rs.getString("title"), rs.getString("document_number"), rs.getString("type"), rs.getString("category"),
                rs.getString("description"), owner == null ? null : shortName(owner), (Long) rs.getObject("documentable_id"), rs.getString("status"),
                rs.getInt("current_version"), ref(rs, "uploaded_by", "uploader_name"), latest, versions, at(rs, "created_at"), at(rs, "updated_at"));
    }

    /** Saves the upload as the next version and returns its number. */
    private int storeVersion(long documentId, int currentVersion, String latestChecksum, MultipartFile file, String remarks) {
        String ext = extension(file.getOriginalFilename());
        String relative = "documents/" + documentId + "/" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);
        String sum;
        try {
            Files.createDirectories(target.getParent());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), md)) {
                Files.copy(in, target);
            }
            sum = HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw ApiException.of(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the file.");
        }
        if (sum.equals(latestChecksum)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Leaving an orphan file is harmless.
            }
            throw ApiException.validation("file", "This file is identical to the current version.");
        }
        int next = currentVersion + 1;
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("""
                insert into document_versions (document_id, version_number, file_path, original_filename, mime_type, file_size, checksum, remarks,
                                               uploaded_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", documentId, next, relative, safeName(file.getOriginalFilename()),
                MIME.get(ext), file.getSize(), sum, blank(remarks), current.user().getId(), now, now);
        jdbc.update("update documents set current_version = ?, updated_at = ? where id = ?", next, now, documentId);
        return next;
    }

    private static void checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.validation("file", "Choose a file to upload.");
        if (file.getSize() > MAX_BYTES) throw ApiException.validation("file", "The file must be 20 MB or smaller.");
        if (!MIME.containsKey(extension(file.getOriginalFilename()))) throw ApiException.validation("file", "Upload a PDF, Word, Excel, PNG or JPG file.");
    }

    private Path resolve(String relative) {
        if (relative == null || relative.isBlank()) return null;
        Path p = root.resolve(relative).normalize();
        return p.startsWith(root) ? p : null;
    }

    static String shortName(String type) {
        if (type == null) return null;
        String t = type.trim();
        return t.contains("\\") ? t.substring(t.lastIndexOf('\\') + 1) : t;
    }

    private static String safeName(String name) {
        String n = name == null || name.isBlank() ? "file" : Path.of(name).getFileName().toString();
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    private static String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String human(long bytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        double v = bytes;
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return (i == 0 ? String.valueOf(bytes) : String.format(Locale.ROOT, "%.2f", v)) + " " + units[i];
    }

    private static Ref ref(ResultSet rs, String idCol, String nameCol) throws SQLException {
        Object id = rs.getObject(idCol);
        return id == null ? null : new Ref(((Number) id).longValue(), rs.getString(nameCol));
    }

    private static OffsetDateTime at(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : Times.toOffset(t.toLocalDateTime());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}

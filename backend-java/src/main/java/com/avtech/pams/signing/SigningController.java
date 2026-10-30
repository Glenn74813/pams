package com.avtech.pams.signing;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.Inserts;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Digital signing of documents (TOR 1.4.11; other functional requirements 1–2). Anyone can send a
 * document (a COA form they exported, a PDF) to a colleague for signing. The signer signs it with a
 * certificate from a trusted CA — the PNPKI or the agency's private CA — either in their own signing
 * tool, pasting the signature here, or through an external signing platform that is told about the
 * request and posts the signature back over the platform API. Every signature is checked against the
 * document's exact bytes and the trusted CAs before it is kept, and any copy of a file can be
 * verified later.
 */
@RestController
@RequestMapping("/api/signing")
public class SigningController {

    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final String ADMIN = "@perm.any('edit_settings')";

    private final JdbcTemplate jdbc;
    private final CertificateVerifier verifier;
    private final DocumentNumbers numbers;
    private final CurrentUser current;
    private final AuditService audit;
    private final NotificationService notifications;
    private final SigningPlatform platform;
    private final Path root;

    public SigningController(JdbcTemplate jdbc, CertificateVerifier verifier, DocumentNumbers numbers, CurrentUser current, AuditService audit,
                             NotificationService notifications, SigningPlatform platform, @Value("${pams.storage.public-dir}") String publicDir) {
        this.jdbc = jdbc;
        this.verifier = verifier;
        this.numbers = numbers;
        this.current = current;
        this.audit = audit;
        this.notifications = notifications;
        this.platform = platform;
        // Kept beside, not under, the public dir: these files are only served through the checks below.
        this.root = Path.of(publicDir).toAbsolutePath().normalize().resolveSibling("signing").normalize();
    }

    public record SignatureView(Long id, String signer, String algorithm, String certificateSubject, String certificateIssuer,
                                String certificateSerial, OffsetDateTime certificateNotAfter, String trustedCa, String source,
                                OffsetDateTime signedAt) {
    }

    public record RequestView(Long id, String requestNumber, String title, String fileName, long fileSize, String fileSha256,
                              Long requestedById, String requestedBy, Long signerId, String signer, String status,
                              String platformReference, String remarks, OffsetDateTime signedAt, OffsetDateTime createdAt,
                              boolean canSign, boolean canCancel, List<SignatureView> signatures) {
    }

    public record SignInput(String certificatePem, String signature, String algorithm) {
    }

    public record RemarksInput(String remarks) {
    }

    public record TrustedView(Long id, String name, String subject, String fingerprint, OffsetDateTime notAfter, OffsetDateTime createdAt) {
    }

    public record TrustedInput(String name, String pem) {
    }

    // ───────────────────────── requests ─────────────────────────

    /** {@code box}: inbox (to sign), outbox (sent) or all (administrators). */
    @GetMapping("/requests")
    public PageResponse<RequestView> index(@RequestParam(defaultValue = "inbox") String box, @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        Long me = current.user().getId();
        switch (box) {
            case "inbox" -> { where.append(" and r.signer_id = ?"); args.add(me); }
            case "outbox" -> { where.append(" and r.requested_by = ?"); args.add(me); }
            case "all" -> { if (!current.any("edit_settings", "view_audit_trail")) throw ApiException.forbidden("Only administrators see every signing request."); }
            default -> throw ApiException.validation("box", "Use inbox, outbox or all.");
        }
        if (status != null && !status.isBlank()) {
            where.append(" and r.status = ?");
            args.add(status);
        }
        Long total = jdbc.queryForObject("select count(*) from signing_requests r" + where, Long.class, args.toArray());
        int size = 20;
        int pageNo = Math.max(1, page);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((pageNo - 1) * size);
        pageArgs.add(size);
        List<Long> ids = jdbc.queryForList("select r.id from signing_requests r" + where
                + " order by case when r.status = 'pending' then 0 else 1 end, r.created_at desc, r.id desc offset ? rows fetch next ? rows only", Long.class, pageArgs.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(ids.stream().map(id -> view(id, false)).toList(), new PageResponse.Pagination(t, size, pageNo, (int) Math.max(1, (t + size - 1) / size)));
    }

    @GetMapping("/requests/{id}")
    public RequestView show(@PathVariable Long id) {
        RequestView r = view(id, true);
        allowed(r);
        return r;
    }

    @PostMapping("/requests")
    @Transactional
    public ResponseEntity<RequestView> create(@RequestParam("file") MultipartFile file, @RequestParam String title, @RequestParam("signer_id") Long signerId,
                                              @RequestParam(required = false) String remarks) {
        if (file == null || file.isEmpty()) throw ApiException.validation("file", "Choose the document to sign.");
        if (file.getSize() > MAX_BYTES) throw ApiException.validation("file", "The document may not be larger than 20 MB.");
        if (title == null || title.isBlank() || title.trim().length() > 255) throw ApiException.validation("title", "Give the document a title (up to 255 characters).");
        List<Map<String, Object>> signer = jdbc.queryForList("select id, name, email, is_active from users where id = ?", signerId);
        if (signer.isEmpty() || !truthy(signer.get(0).get("is_active"))) throw ApiException.validation("signer_id", "Choose an active user to sign.");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("file", "The document could not be read.");
        }
        String name = safeName(file.getOriginalFilename());
        String number = numbers.yearly("SIG", "signing_requests.request_number");
        String relative = number + "/" + name;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw ApiException.validation("file", "Invalid file name.");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw ApiException.of(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the document.");
        }
        Timestamp now = Timestamp.valueOf(Times.now());
        String sha = CertificateVerifier.sha256(data);
        Long id = Inserts.insert(jdbc, """
                insert into signing_requests (request_number, title, file_name, file_path, content_type, file_sha256, file_size, requested_by, signer_id, status, remarks, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?)""", number, title.trim(), name, relative, file.getContentType(), sha, (long) data.length,
                current.user().getId(), signerId, blank(remarks), now, now);
        audit.record("created", "SigningRequest", id, "Sent " + title.trim() + " (" + number + ") to " + signer.get(0).get("name") + " for signing",
                null, Map.of("sha256", sha));
        notifications.notifyUser(signerId, "signing_request", "Document to sign", current.user().getName() + " sent \"" + title.trim() + "\" for your digital signature.",
                "SigningRequest", id);
        platform.announce(number, title.trim(), sha, (String) signer.get(0).get("email"));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(id, true));
    }

    @GetMapping("/requests/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        RequestView r = view(id, false);
        allowed(r);
        return download(id);
    }

    /** The signer signs the exact bytes of the file (download it, sign it, then paste the signature). */
    @PostMapping("/requests/{id}/sign")
    @Transactional
    public RequestView sign(@PathVariable Long id, @RequestBody SignInput in) {
        RequestView r = pending(id);
        if (!r.signerId().equals(current.user().getId())) throw ApiException.forbidden("Only " + r.signer() + " can sign this document.");
        record(r, in.certificatePem(), in.signature(), in.algorithm(), "in_app", null);
        return view(id, true);
    }

    @PostMapping("/requests/{id}/decline")
    @Transactional
    public RequestView decline(@PathVariable Long id, @RequestBody(required = false) RemarksInput in) {
        RequestView r = pending(id);
        if (!r.signerId().equals(current.user().getId())) throw ApiException.forbidden("Only " + r.signer() + " can decline this document.");
        String remarks = in == null ? null : blank(in.remarks());
        if (remarks == null) throw ApiException.validation("remarks", "Say why you are not signing.");
        jdbc.update("update signing_requests set status = 'declined', remarks = ?, updated_at = ? where id = ?", remarks, Timestamp.valueOf(Times.now()), id);
        audit.record("updated", "SigningRequest", id, "Declined to sign " + r.requestNumber(), null, Map.of("remarks", remarks));
        notifications.notifyUser(r.requestedById(), "signing_request", "Signing declined", r.signer() + " declined to sign \"" + r.title() + "\": " + remarks, "SigningRequest", id);
        return view(id, true);
    }

    @PostMapping("/requests/{id}/cancel")
    @Transactional
    public RequestView cancel(@PathVariable Long id) {
        RequestView r = pending(id);
        if (!r.requestedById().equals(current.user().getId()) && !current.any("edit_settings")) throw ApiException.forbidden("Only the sender can cancel this request.");
        jdbc.update("update signing_requests set status = 'cancelled', updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), id);
        audit.record("updated", "SigningRequest", id, "Cancelled signing request " + r.requestNumber(), null, null);
        return view(id, true);
    }

    /** Checks any copy of a file: every signature recorded for exactly these bytes. */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.validation("file", "Choose the file to check.");
        String sha;
        try {
            sha = CertificateVerifier.sha256(file.getBytes());
        } catch (IOException e) {
            throw ApiException.validation("file", "The file could not be read.");
        }
        List<Long> ids = jdbc.queryForList("select id from signing_requests where file_sha256 = ? order by id", Long.class, sha);
        List<RequestView> found = ids.stream().map(id -> view(id, true)).filter(r -> !r.signatures().isEmpty()).toList();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("sha256", sha);
        res.put("signed", !found.isEmpty());
        res.put("message", found.isEmpty() ? "No signature is recorded for this exact file. Any change to a signed file, even re-saving it, makes it a different file."
                : "This file is unchanged since it was signed.");
        res.put("requests", found);
        return res;
    }

    // ───────────────────────── trusted certificate authorities ─────────────────────────

    @GetMapping("/trusted-cas")
    public List<TrustedView> trusted() {
        return jdbc.query("select id, name, subject, fingerprint, not_after, created_at from trusted_certificates order by name", (rs, i) -> new TrustedView(
                rs.getLong("id"), rs.getString("name"), rs.getString("subject"), rs.getString("fingerprint"),
                rs.getTimestamp("not_after") == null ? null : Times.toOffset(rs.getTimestamp("not_after").toLocalDateTime()),
                rs.getTimestamp("created_at") == null ? null : Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())));
    }

    @PostMapping("/trusted-cas")
    @PreAuthorize(ADMIN)
    @Transactional
    public ResponseEntity<TrustedView> addTrusted(@RequestBody TrustedInput in) {
        String name = blank(in.name());
        if (name == null || name.length() > 150) throw ApiException.validation("name", "Name the certificate authority (up to 150 characters).");
        List<X509Certificate> certs = CertificateVerifier.parse(in.pem());
        if (certs.size() != 1) throw ApiException.validation("pem", "Add one CA certificate at a time.");
        X509Certificate c = certs.get(0);
        if (c.getBasicConstraints() < 0) throw ApiException.validation("pem", "This is not a CA certificate (it cannot issue other certificates).");
        String fp = CertificateVerifier.fingerprint(c);
        Long dup = jdbc.queryForObject("select count(*) from trusted_certificates where fingerprint = ?", Long.class, fp);
        if (dup != null && dup > 0) throw ApiException.validation("pem", "This certificate is already trusted.");
        Timestamp now = Timestamp.valueOf(Times.now());
        String subject = c.getSubjectX500Principal().getName();
        Long id = Inserts.insert(jdbc, "insert into trusted_certificates (name, pem, subject, fingerprint, not_after, created_by, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                name, in.pem().trim(), subject.length() > 500 ? subject.substring(0, 500) : subject, fp,
                Timestamp.from(c.getNotAfter().toInstant()), current.user().getId(), now);
        audit.record("created", "TrustedCertificate", id, "Trusted certificate authority " + name, null, Map.of("fingerprint", fp));
        return ResponseEntity.status(HttpStatus.CREATED).body(trusted().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow());
    }

    @DeleteMapping("/trusted-cas/{id}")
    @PreAuthorize(ADMIN)
    @Transactional
    public Map<String, Object> removeTrusted(@PathVariable Long id) {
        List<String> name = jdbc.queryForList("select name from trusted_certificates where id = ?", String.class, id);
        if (name.isEmpty()) throw ApiException.notFound("Certificate authority");
        // Signatures made under it keep their record; the link is cleared so the CA can go.
        jdbc.update("update document_signatures set trusted_certificate_id = null where trusted_certificate_id = ?", id);
        jdbc.update("delete from trusted_certificates where id = ?", id);
        audit.record("deleted", "TrustedCertificate", id, "Stopped trusting certificate authority " + name.get(0), null, null);
        return Map.of("message", "Certificate authority removed.");
    }

    // ───────────────────────── signing platform API ─────────────────────────

    public record PlatformSignInput(String requestNumber, String certificatePem, String signature, String algorithm, String platformReference) {
    }

    /** The signing platform fetches the document it was told about. Authenticated by the platform key, not a user token. */
    @GetMapping("/platform/requests/{number}/file")
    public ResponseEntity<byte[]> platformFile(@PathVariable String number, @RequestHeader(name = "X-Signing-Key", required = false) String key) {
        platform.check(key);
        return download(byNumber(number));
    }

    /** The signing platform posts the signature it collected from the signer. */
    @PostMapping("/platform/signatures")
    @Transactional
    public Map<String, Object> platformSign(@RequestBody PlatformSignInput in, @RequestHeader(name = "X-Signing-Key", required = false) String key) {
        platform.check(key);
        Long id = byNumber(in.requestNumber());
        RequestView r = pending(id);
        String ref = blank(in.platformReference());
        if (ref != null && ref.length() > 255) throw ApiException.validation("platform_reference", "Keep the reference within 255 characters.");
        record(r, in.certificatePem(), in.signature(), in.algorithm(), "platform", ref);
        return Map.of("message", "Signature recorded.", "request_number", r.requestNumber(), "status", "signed");
    }

    // ───────────────────────── helpers ─────────────────────────

    private void record(RequestView r, String pem, String signature, String algorithm, String source, String platformRef) {
        byte[] doc = bytes(r.id());
        if (!CertificateVerifier.sha256(doc).equals(r.fileSha256())) throw ApiException.of(HttpStatus.CONFLICT, "The stored document no longer matches what was sent; ask the sender again.");
        CertificateVerifier.Verified v = verifier.verify(doc, pem, signature, algorithm);
        String signerEmail = jdbc.queryForObject("select email from users where id = ?", String.class, r.signerId());
        if (v.email() != null && signerEmail != null && !v.email().equalsIgnoreCase(signerEmail)) {
            throw ApiException.validation("certificate_pem", "This certificate belongs to " + v.email() + ", not to " + r.signer() + ".");
        }
        X509Certificate c = v.signer();
        Timestamp now = Timestamp.valueOf(Times.now());
        Inserts.insert(jdbc, """
                insert into document_signatures (signing_request_id, signer_id, file_sha256, algorithm, signature, certificate_pem, certificate_subject,
                    certificate_issuer, certificate_serial, certificate_not_after, trusted_certificate_id, source, signed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", r.id(), r.signerId(), r.fileSha256(), algorithm.toUpperCase(),
                signature.replaceAll("\\s", ""), pem.trim(), cut(c.getSubjectX500Principal().getName(), 500), cut(c.getIssuerX500Principal().getName(), 500),
                c.getSerialNumber().toString(16), Timestamp.from(c.getNotAfter().toInstant()), v.trustedId(), source, now);
        jdbc.update("update signing_requests set status = 'signed', signed_at = ?, platform_reference = coalesce(?, platform_reference), updated_at = ? where id = ?",
                now, platformRef, now, r.id());
        audit.record("updated", "SigningRequest", r.id(), "Digitally signed " + r.requestNumber() + " (" + c.getSubjectX500Principal().getName() + ")",
                null, Map.of("certificate_serial", c.getSerialNumber().toString(16), "source", source));
        notifications.notifyUser(r.requestedById(), "signing_request", "Document signed", r.signer() + " signed \"" + r.title() + "\".", "SigningRequest", r.id());
    }

    private RequestView pending(Long id) {
        RequestView r = view(id, false);
        if (!"pending".equals(r.status())) throw ApiException.of(HttpStatus.CONFLICT, "This request is already " + r.status() + ".");
        return r;
    }

    private void allowed(RequestView r) {
        Long me = current.user().getId();
        if (!me.equals(r.signerId()) && !me.equals(r.requestedById()) && !current.any("edit_settings", "view_audit_trail")) {
            throw ApiException.forbidden("Only the sender and the signer can open this document.");
        }
    }

    private Long byNumber(String number) {
        List<Long> id = jdbc.queryForList("select id from signing_requests where request_number = ?", Long.class, number);
        if (id.isEmpty()) throw ApiException.notFound("Signing request");
        return id.get(0);
    }

    private byte[] bytes(Long id) {
        String path = jdbc.queryForObject("select file_path from signing_requests where id = ?", String.class, id);
        Path file = root.resolve(path).normalize();
        try {
            if (!file.startsWith(root)) throw new IOException("outside the store");
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw ApiException.of(HttpStatus.GONE, "The stored document is missing.");
        }
    }

    private ResponseEntity<byte[]> download(Long id) {
        Map<String, Object> r = jdbc.queryForMap("select file_name, content_type from signing_requests where id = ?", id);
        String type = (String) r.get("content_type");
        return ResponseEntity.ok().contentType(type == null || type.isBlank() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename((String) r.get("file_name"), StandardCharsets.UTF_8).build().toString())
                .body(bytes(id));
    }

    private RequestView view(Long id, boolean withSignatures) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select r.*, q.name as requested_name, s.name as signer_name from signing_requests r
                join users q on q.id = r.requested_by join users s on s.id = r.signer_id where r.id = ?""", id);
        if (rows.isEmpty()) throw ApiException.notFound("Signing request");
        Map<String, Object> r = rows.get(0);
        // The platform API calls in without a user; nothing is "yours" then.
        Long me = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof com.avtech.pams.auth.AuthPrincipal p
                ? p.user().getId() : null;
        String status = (String) r.get("status");
        Long signerId = ((Number) r.get("signer_id")).longValue();
        Long requester = ((Number) r.get("requested_by")).longValue();
        List<SignatureView> sigs = withSignatures ? jdbc.query("""
                select d.id, u.name as signer, d.algorithm, d.certificate_subject, d.certificate_issuer, d.certificate_serial, d.certificate_not_after,
                       t.name as trusted_name, d.source, d.signed_at
                from document_signatures d join users u on u.id = d.signer_id left join trusted_certificates t on t.id = d.trusted_certificate_id
                where d.signing_request_id = ? order by d.signed_at""", (rs, i) -> new SignatureView(rs.getLong("id"), rs.getString("signer"), rs.getString("algorithm"),
                rs.getString("certificate_subject"), rs.getString("certificate_issuer"), rs.getString("certificate_serial"),
                rs.getTimestamp("certificate_not_after") == null ? null : Times.toOffset(rs.getTimestamp("certificate_not_after").toLocalDateTime()),
                rs.getString("trusted_name"), rs.getString("source"), Times.toOffset(rs.getTimestamp("signed_at").toLocalDateTime())), id) : List.of();
        return new RequestView(id, (String) r.get("request_number"), (String) r.get("title"), (String) r.get("file_name"),
                ((Number) r.get("file_size")).longValue(), (String) r.get("file_sha256"), requester, (String) r.get("requested_name"), signerId,
                (String) r.get("signer_name"), status, (String) r.get("platform_reference"), (String) r.get("remarks"),
                stamp(r.get("signed_at")), stamp(r.get("created_at")), "pending".equals(status) && signerId.equals(me),
                "pending".equals(status) && requester.equals(me), sigs);
    }

    private static OffsetDateTime stamp(Object v) {
        return v instanceof Timestamp t ? Times.toOffset(t.toLocalDateTime()) : v instanceof java.time.LocalDateTime l ? Times.toOffset(l) : null;
    }

    private static boolean truthy(Object v) {
        return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0);
    }

    private static String safeName(String original) {
        String n = original == null ? "document" : Path.of(original.replace('\\', '/')).getFileName().toString();
        n = n.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (n.isEmpty() || n.startsWith(".")) n = "document" + n;
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Constant-time comparison for the platform key. */
    static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}

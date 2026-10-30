package com.avtech.pams.property;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Inserts;
import com.avtech.pams.common.PhotoStore;
import com.avtech.pams.common.Times;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A picture of each asset and item (TOR 1.4.4, 1.5.4), and where each asset is kept, with the
 * history of its moves (TOR 1.4.7).
 */
@RestController
@RequestMapping("/api")
public class AssetMediaController {

    private final JdbcTemplate jdbc;
    private final PhotoStore photos;
    private final CurrentUser current;
    private final AuditService audit;

    public AssetMediaController(JdbcTemplate jdbc, PhotoStore photos, CurrentUser current, AuditService audit) {
        this.jdbc = jdbc;
        this.photos = photos;
        this.current = current;
        this.audit = audit;
    }

    // ───────────────────────── photos ─────────────────────────

    @GetMapping("/properties/{id}/photo")
    public ResponseEntity<byte[]> propertyPhoto(@PathVariable Long id) {
        Map<String, Object> p = property(id);
        if (!current.any("view_properties", "view_reports", "generate_reports") && !current.user().getId().equals(asLong(p.get("assigned_to")))) {
            throw ApiException.forbidden("You can only view assets you are accountable for.");
        }
        return image((String) p.get("photo_path"));
    }

    @PostMapping("/properties/{id}/photo")
    @PreAuthorize("@perm.any('edit_properties', 'create_properties')")
    @Transactional
    public Map<String, Object> uploadPropertyPhoto(@PathVariable Long id, @RequestParam("photo") MultipartFile file) {
        Map<String, Object> p = property(id);
        if ("disposed".equals(p.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Disposed assets cannot be edited.");
        String path = photos.save("properties", id, file);
        jdbc.update("update properties set photo_path = ?, updated_at = ? where id = ?", path, Timestamp.valueOf(Times.now()), id);
        photos.delete((String) p.get("photo_path"));
        audit.record("updated", "App\\Models\\Property", id, "Uploaded a photo of asset " + p.get("tag_number"), null, Map.of("photo", path));
        return Map.of("message", "Photo saved.", "has_photo", true);
    }

    @DeleteMapping("/properties/{id}/photo")
    @PreAuthorize("@perm.any('edit_properties')")
    @Transactional
    public Map<String, Object> deletePropertyPhoto(@PathVariable Long id) {
        Map<String, Object> p = property(id);
        jdbc.update("update properties set photo_path = null, updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), id);
        photos.delete((String) p.get("photo_path"));
        audit.record("updated", "App\\Models\\Property", id, "Removed the photo of asset " + p.get("tag_number"), null, null);
        return Map.of("message", "Photo removed.", "has_photo", false);
    }

    /** Item pictures are shown wherever the item is (catalogue, requests), so anyone signed in can see them. */
    @GetMapping("/items/{id}/photo")
    public ResponseEntity<byte[]> itemPhoto(@PathVariable Long id) {
        return image((String) item(id).get("photo_path"));
    }

    @PostMapping("/items/{id}/photo")
    @PreAuthorize("@perm.any('edit_inventory', 'create_inventory')")
    @Transactional
    public Map<String, Object> uploadItemPhoto(@PathVariable Long id, @RequestParam("photo") MultipartFile file) {
        Map<String, Object> i = item(id);
        String path = photos.save("items", id, file);
        jdbc.update("update items set photo_path = ?, updated_at = ? where id = ?", path, Timestamp.valueOf(Times.now()), id);
        photos.delete((String) i.get("photo_path"));
        audit.record("updated", "App\\Models\\Item", id, "Uploaded a photo of item " + i.get("code"), null, Map.of("photo", path));
        return Map.of("message", "Photo saved.", "has_photo", true);
    }

    @DeleteMapping("/items/{id}/photo")
    @PreAuthorize("@perm.any('edit_inventory')")
    @Transactional
    public Map<String, Object> deleteItemPhoto(@PathVariable Long id) {
        Map<String, Object> i = item(id);
        jdbc.update("update items set photo_path = null, updated_at = ? where id = ?", Timestamp.valueOf(Times.now()), id);
        photos.delete((String) i.get("photo_path"));
        audit.record("updated", "App\\Models\\Item", id, "Removed the photo of item " + i.get("code"), null, null);
        return Map.of("message", "Photo removed.", "has_photo", false);
    }

    // ───────────────────────── location ─────────────────────────

    public record MoveInput(Long locationId, String remarks) {
    }

    public record MoveView(Long id, String from, String to, String movedBy, String remarks, OffsetDateTime movedAt) {
    }

    /** Moves the asset to another location (or clears it); the holder does not change. */
    @PostMapping("/properties/{id}/location")
    @PreAuthorize("@perm.any('edit_properties', 'transfer_properties', 'assign_properties')")
    @Transactional
    public Map<String, Object> move(@PathVariable Long id, @RequestBody MoveInput in) {
        Map<String, Object> p = property(id);
        if ("disposed".equals(p.get("status"))) throw ApiException.of(HttpStatus.CONFLICT, "Disposed assets cannot be moved.");
        Long from = asLong(p.get("location_id"));
        Long to = in.locationId();
        String toName = null;
        if (to != null) {
            List<Map<String, Object>> loc = jdbc.queryForList("select name, is_active from asset_locations where id = ?", to);
            if (loc.isEmpty()) throw ApiException.validation("location_id", "Choose a location from the list.");
            if (!Boolean.TRUE.equals(loc.get(0).get("is_active")) && !Integer.valueOf(1).equals(loc.get(0).get("is_active"))) {
                throw ApiException.validation("location_id", "That location is no longer in use.");
            }
            toName = (String) loc.get(0).get("name");
        }
        if (java.util.Objects.equals(from, to)) throw ApiException.validation("location_id", "The asset is already there.");
        String remarks = in.remarks() == null || in.remarks().isBlank() ? null : in.remarks().trim();
        if (remarks != null && remarks.length() > 500) throw ApiException.validation("remarks", "Keep the remarks within 500 characters.");
        Timestamp now = Timestamp.valueOf(Times.now());
        jdbc.update("update properties set location_id = ?, updated_at = ? where id = ?", to, now, id);
        Inserts.insert(jdbc, "insert into property_location_moves (property_id, from_location_id, to_location_id, moved_by, remarks, created_at) values (?, ?, ?, ?, ?, ?)",
                id, from, to, current.user().getId(), remarks, now);
        Map<String, Object> after = new HashMap<>();
        after.put("location", toName);
        audit.record("updated", "App\\Models\\Property", id, "Moved asset " + p.get("tag_number") + (toName == null ? " out of its location" : " to " + toName), null, after);
        return Map.of("message", toName == null ? "Location cleared." : "Moved to " + toName + ".");
    }

    @GetMapping("/properties/{id}/locations")
    public List<MoveView> moves(@PathVariable Long id) {
        Map<String, Object> p = property(id);
        if (!current.any("view_properties", "view_reports", "generate_reports") && !current.user().getId().equals(asLong(p.get("assigned_to")))) {
            throw ApiException.forbidden("You can only view assets you are accountable for.");
        }
        return jdbc.query("""
                select m.id, f.name as from_name, t.name as to_name, u.name as moved_by, m.remarks, m.created_at
                from property_location_moves m
                left join asset_locations f on f.id = m.from_location_id
                left join asset_locations t on t.id = m.to_location_id
                left join users u on u.id = m.moved_by
                where m.property_id = ? order by m.created_at desc, m.id desc""",
                (rs, i) -> new MoveView(rs.getLong("id"), rs.getString("from_name"), rs.getString("to_name"), rs.getString("moved_by"),
                        rs.getString("remarks"), rs.getTimestamp("created_at") == null ? null : Times.toOffset(rs.getTimestamp("created_at").toLocalDateTime())), id);
    }

    // ───────────────────────── helpers ─────────────────────────

    private Map<String, Object> property(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, tag_number, status, assigned_to, location_id, photo_path from properties where id = ?", id);
        if (rows.isEmpty()) throw ApiException.notFound("Asset");
        return rows.get(0);
    }

    private Map<String, Object> item(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, code, photo_path from items where id = ?", id);
        if (rows.isEmpty()) throw ApiException.notFound("Item");
        return rows.get(0);
    }

    private ResponseEntity<byte[]> image(String path) {
        PhotoStore.Photo photo = path == null ? null : photos.read(path);
        if (photo == null) throw ApiException.notFound("Photo");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate()).body(photo.data());
    }

    private static Long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}

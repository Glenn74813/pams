package com.avtech.pams.location;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.Db;
import com.avtech.pams.common.Inserts;
import com.avtech.pams.common.Times;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Set-up library (TOR 1.2.3): the buildings, floors and rooms where assets are kept. */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final JdbcTemplate jdbc;
    private final Db db;
    private final AuditService audit;

    public LocationController(JdbcTemplate jdbc, Db db, AuditService audit) {
        this.jdbc = jdbc;
        this.db = db;
        this.audit = audit;
    }

    public record LocationView(Long id, String name, String building, String floor, String room, String description,
                               boolean isActive, long assets) {
    }

    public record LocationInput(String name, String building, String floor, String room, String description, Boolean isActive) {
    }

    /** Everyone signed in can read the list (asset forms pick from it). */
    @GetMapping
    public List<LocationView> index(@RequestParam(name = "active_only", defaultValue = "false") boolean activeOnly) {
        return jdbc.query("""
                select l.id, l.name, l.building, l.floor, l.room, l.description, l.is_active,
                       (select count(*) from properties p where p.location_id = l.id and p.status <> 'disposed') as assets
                from asset_locations l""" + (activeOnly ? " where l.is_active = " + db.t() : "") + " order by l.name",
                (rs, i) -> new LocationView(rs.getLong("id"), rs.getString("name"), rs.getString("building"), rs.getString("floor"),
                        rs.getString("room"), rs.getString("description"), rs.getBoolean("is_active"), rs.getLong("assets")));
    }

    @PostMapping
    @PreAuthorize("@perm.any('edit_settings')")
    @Transactional
    public ResponseEntity<LocationView> store(@RequestBody LocationInput in) {
        String name = name(in, null);
        Timestamp now = Timestamp.valueOf(Times.now());
        Long id = Inserts.insert(jdbc, """
                insert into asset_locations (name, building, floor, room, description, is_active, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)""", name, text(in.building(), 150), text(in.floor(), 50), text(in.room(), 100),
                text(in.description(), 500), in.isActive() == null || in.isActive(), now, now);
        audit.record("created", "AssetLocation", id, "Added location " + name, null, Map.of("name", name));
        return ResponseEntity.status(HttpStatus.CREATED).body(show(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('edit_settings')")
    @Transactional
    public LocationView update(@PathVariable Long id, @RequestBody LocationInput in) {
        LocationView before = show(id);
        String name = name(in, id);
        jdbc.update("update asset_locations set name = ?, building = ?, floor = ?, room = ?, description = ?, is_active = ?, updated_at = ? where id = ?",
                name, text(in.building(), 150), text(in.floor(), 50), text(in.room(), 100), text(in.description(), 500),
                in.isActive() == null ? before.isActive() : in.isActive(), Timestamp.valueOf(Times.now()), id);
        audit.record("updated", "AssetLocation", id, "Updated location " + name, Map.of("name", before.name()), Map.of("name", name));
        return show(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('edit_settings')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        LocationView l = show(id);
        Long used = jdbc.queryForObject("""
                select (select count(*) from properties where location_id = ?)
                     + (select count(*) from property_location_moves where from_location_id = ? or to_location_id = ?)""", Long.class, id, id, id);
        if (used != null && used > 0) {
            throw ApiException.of(HttpStatus.CONFLICT, "Assets are or were kept at " + l.name() + "; deactivate it instead.");
        }
        jdbc.update("delete from asset_locations where id = ?", id);
        audit.record("deleted", "AssetLocation", id, "Deleted location " + l.name(), Map.of("name", l.name()), null);
        return Map.of("message", "Location deleted.");
    }

    LocationView show(Long id) {
        return index(false).stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow(() -> ApiException.notFound("Location"));
    }

    private String name(LocationInput in, Long except) {
        String name = text(in.name(), 150);
        if (name == null) throw ApiException.validation("name", "Give the location a name.");
        Long dup = jdbc.queryForObject("select count(*) from asset_locations where lower(name) = lower(?) and id <> ?", Long.class, name, except == null ? -1L : except);
        if (dup != null && dup > 0) throw ApiException.validation("name", "A location with this name already exists.");
        return name;
    }

    private static String text(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > max) throw ApiException.validation("name", "Keep each field within " + max + " characters.");
        return t;
    }
}

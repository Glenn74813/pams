package com.avtech.pams.department;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.common.ApiException;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offices (departments) that users belong to and that request supplies, plan procurement and hold assets. */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private static final String TYPE = "App\\Models\\Department";
    /** Tables that point at a department; one with any row keeps the department from being deleted. */
    private static final List<String> USED_BY = List.of("users", "requests", "request_logs", "purchase_requests", "ppmps", "app_items",
            "semi_expendable_issues", "physical_inventory_counts", "physical_count_properties");

    private final DepartmentRepository departments;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public DepartmentController(DepartmentRepository departments, JdbcTemplate jdbc, AuditService audit) {
        this.departments = departments;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record Row(Long id, String name, String code, long users) {
    }

    @GetMapping
    @PreAuthorize("@perm.any('view_users', 'create_users', 'edit_users')")
    public Map<String, Object> index() {
        return Map.of("data", jdbc.query("""
                select d.id, d.name, d.code, (select count(*) from users u where u.department_id = d.id) from departments d order by d.name""",
                (rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4))));
    }

    public record Input(String name, String code) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_users', 'edit_users')")
    @Transactional
    public ResponseEntity<DepartmentView> create(@RequestBody Input in) {
        Department d = new Department();
        apply(d, in, null);
        departments.saveAndFlush(d);
        audit.record("created", TYPE, d.getId(), "Added office " + d.getName() + " (" + d.getCode() + ")", null, Map.of("name", d.getName(), "code", d.getCode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DepartmentView.of(d));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('create_users', 'edit_users')")
    @Transactional
    public DepartmentView update(@PathVariable Long id, @RequestBody Input in) {
        Department d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Office"));
        Map<String, Object> before = Map.of("name", d.getName(), "code", d.getCode());
        apply(d, in, id);
        departments.flush();
        audit.record("updated", TYPE, id, "Edited office " + d.getName(), before, Map.of("name", d.getName(), "code", d.getCode()));
        return DepartmentView.of(d);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('delete_users')")
    @Transactional
    public Map<String, String> destroy(@PathVariable Long id) {
        Department d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Office"));
        for (String table : USED_BY) {
            Long n = jdbc.queryForObject("select count(*) from " + table + " where department_id = ?", Long.class, id);
            if (n != null && n > 0) {
                throw ApiException.of(HttpStatus.CONFLICT, d.getName() + " is still used by " + n + " " + table.replace('_', ' ')
                        + " record(s), so it cannot be deleted. Rename it instead.");
            }
        }
        departments.delete(d);
        departments.flush();
        audit.record("deleted", TYPE, id, "Deleted office " + d.getName(), Map.of("name", d.getName(), "code", d.getCode()), null);
        return Map.of("message", "Office deleted.");
    }

    private void apply(Department d, Input in, Long id) {
        String name = in.name() == null ? "" : in.name().trim();
        String code = in.code() == null ? "" : in.code().trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 255) throw ApiException.validation("name", "Enter the office name (up to 255 characters).");
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{0,19}")) throw ApiException.validation("code", "Enter a short code of letters and numbers (up to 20), e.g. ICT.");
        Long clash = jdbc.queryForObject("select count(*) from departments where (lower(name) = ? or upper(code) = ?) and id <> ?", Long.class,
                name.toLowerCase(Locale.ROOT), code, id == null ? -1L : id);
        if (clash != null && clash > 0) throw ApiException.validation("name", "Another office already has that name or code.");
        d.setName(name);
        d.setCode(code);
    }
}

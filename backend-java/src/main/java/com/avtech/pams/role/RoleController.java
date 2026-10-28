package com.avtech.pams.role;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Role management. Needs manage_roles (Laravel only let Super Admin in and ignored that permission).
 * Granting the wildcard, or touching a Super Admin role, stays Super Admin only.
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("@perm.any('manage_roles')")
public class RoleController {

    private final RoleRepository roles;
    private final CurrentUser current;
    private final AuditService audit;

    public RoleController(RoleRepository roles, CurrentUser current, AuditService audit) {
        this.roles = roles;
        this.current = current;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> index() {
        List<RoleView> list = roles.findAllWithUserCounts().stream()
                .map(row -> RoleView.of((Role) row[0], (Long) row[1]))
                .toList();
        return Map.of("data", list);
    }

    @GetMapping("/permissions-list")
    public Map<String, Object> permissionsList() {
        return Map.of("data", Permissions.CATALOGUE);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> show(@PathVariable Long id) {
        return Map.of("data", RoleView.of(find(id)));
    }

    public record RoleRequest(
            @NotBlank(message = "The role name is required.") @Size(max = 100, message = "The role name may not exceed 100 characters.") String name,
            @Size(max = 500, message = "The description may not exceed 500 characters.") String description,
            @NotNull(message = "Choose at least one permission.") List<String> permissions) {
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> store(@Valid @RequestBody RoleRequest body) {
        String name = body.name().trim();
        if (roles.existsByNameIgnoreCase(name)) {
            throw ApiException.validation("name", "A role with this name already exists.");
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(body.description());
        role.setPermissions(checkedPermissions(body.permissions()));
        role.setSystem(false);
        roles.save(role);
        audit.record("created", AuditService.ROLE, role.getId(), "Created Role " + role.getName(), null, snapshot(role));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Role created successfully", "data", RoleView.of(role)));
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody RoleRequest body) {
        Role role = find(id);
        if (role.isSuperAdmin() && !current.superAdmin()) {
            throw ApiException.forbidden("Only Super Admin can change a Super Admin role.");
        }
        String name = body.name().trim();
        if (Role.SUPER_ADMIN.equals(role.getName()) && !Role.SUPER_ADMIN.equals(name)) {
            throw ApiException.forbidden("Cannot rename the Super Admin role.");
        }
        if (roles.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.validation("name", "A role with this name already exists.");
        }
        List<String> permissions = checkedPermissions(body.permissions());
        if (Role.SUPER_ADMIN.equals(role.getName()) && !permissions.contains(Role.WILDCARD)) {
            throw ApiException.forbidden("The Super Admin role must keep full access.");
        }
        Map<String, Object> before = snapshot(role);
        role.setName(name);
        role.setDescription(body.description());
        role.setPermissions(permissions);
        roles.flush();
        audit.record("updated", AuditService.ROLE, role.getId(), "Updated Role " + role.getName(), before, snapshot(role));
        return Map.of("message", "Role updated successfully", "data", RoleView.of(role));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        Role role = find(id);
        if (role.isSystem()) {
            throw ApiException.forbidden("Cannot delete system roles.");
        }
        if (role.isSuperAdmin() && !current.superAdmin()) {
            throw ApiException.forbidden("Only Super Admin can delete a Super Admin role.");
        }
        long holders = roles.findAllWithUserCounts().stream()
                .filter(row -> ((Role) row[0]).getId().equals(id))
                .mapToLong(row -> (Long) row[1]).sum();
        if (holders > 0) {
            throw ApiException.forbidden("Cannot delete role with " + holders + " user(s) assigned. Reassign users first.");
        }
        roles.delete(role);
        audit.record("deleted", AuditService.ROLE, id, "Deleted Role " + role.getName(), snapshot(role), null);
        return Map.of("message", "Role deleted successfully");
    }

    private static Map<String, Object> snapshot(Role r) {
        return Map.of("name", r.getName(), "description", r.getDescription() == null ? "" : r.getDescription(),
                "permissions", List.copyOf(r.getPermissions()));
    }

    private Role find(Long id) {
        return roles.findById(id).orElseThrow(() -> ApiException.notFound("Role"));
    }

    /** Only known permission keys; the wildcard only from a Super Admin. */
    private List<String> checkedPermissions(List<String> requested) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String p : requested) {
            if (Role.WILDCARD.equals(p)) {
                if (!current.superAdmin()) {
                    throw ApiException.forbidden("Only Super Admin can grant full access.");
                }
            } else if (!Permissions.ALL_KEYS.contains(p)) {
                throw ApiException.validation("permissions", "Unknown permission: " + p);
            }
            out.add(p);
        }
        return List.copyOf(out);
    }
}

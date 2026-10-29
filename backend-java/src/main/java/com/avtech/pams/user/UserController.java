package com.avtech.pams.user;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.auth.PasswordPolicy;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.department.Department;
import com.avtech.pams.department.DepartmentRepository;
import com.avtech.pams.department.DepartmentView;
import com.avtech.pams.role.Role;
import com.avtech.pams.role.RoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final int PER_PAGE = 15;

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser current;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    private final PasswordPolicy passwordPolicy;

    public UserController(UserRepository users, RoleRepository roles, DepartmentRepository departments,
                          PasswordEncoder passwordEncoder, CurrentUser current,
                          AuditService audit, JdbcTemplate jdbc, PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.current = current;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("@perm.any('view_users')")
    @Transactional(readOnly = true)
    public PageResponse<UserView> index(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(name = "role_id", required = false) Long roleId) {
        String q = search == null || search.isBlank() ? null : search.trim();
        var pageable = PageRequest.of(Math.max(0, page - 1), PER_PAGE, Sort.by("name"));
        return PageResponse.of(users.search(q, roleId, pageable), UserView::of);
    }

    /** Names for pickers (assignees, signatories). Any signed-in user; no roles or other details. */
    @GetMapping("/all")
    public Map<String, Object> all() {
        return Map.of("data", users.findAllOptions());
    }

    /** Roles a user may be given, for the user form. */
    @GetMapping("/roles")
    @PreAuthorize("@perm.any('view_users', 'create_users', 'edit_users')")
    @Transactional(readOnly = true)
    public Map<String, Object> roles() {
        boolean caller = current.superAdmin();
        var list = roles.findAll(Sort.by("id")).stream()
                .filter(r -> caller || !r.isSuperAdmin())
                .map(r -> Map.of("id", r.getId(), "name", r.getName(),
                        "description", r.getDescription() == null ? "" : r.getDescription()))
                .toList();
        return Map.of("data", list);
    }

    @GetMapping("/departments")
    @Transactional(readOnly = true)
    public Map<String, Object> departments() {
        return Map.of("data", departments.findAll(Sort.by("name")).stream().map(DepartmentView::of).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.any('view_users')")
    @Transactional(readOnly = true)
    public Map<String, Object> show(@PathVariable Long id) {
        return Map.of("data", UserView.of(find(id)));
    }

    public record CreateUserRequest(
            @NotBlank(message = "The name is required.") @Size(min = 2, max = 100, message = "The name must be 2 to 100 characters.") String name,
            @NotBlank(message = "The email is required.") @Email(message = "Enter a valid email address.") @Size(max = 100, message = "The email may not exceed 100 characters.") String email,
            String password,
            String passwordConfirmation,
            @NotNull(message = "Choose a role.") Long roleId,
            @NotNull(message = "Choose a department.") Long departmentId) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_users')")
    @Transactional
    public ResponseEntity<Map<String, Object>> store(@Valid @RequestBody CreateUserRequest body) {
        String email = body.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.validation("email", "The email has already been taken.");
        }
        passwordPolicy.check("password", body.password(), body.passwordConfirmation(), email);
        Role role = assignableRole(body.roleId());

        User u = new User();
        u.setName(body.name().trim());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(body.password()));
        u.setPasswordChangedAt(Times.now());
        u.setRole(role);
        u.setDepartment(department(body.departmentId()));
        users.save(u);
        audit.record("created", AuditService.USER, u.getId(), "Created User " + u.getName(), null, snapshot(u));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User created successfully", "user", UserView.of(u)));
    }

    public record UpdateUserRequest(
            @Size(min = 2, max = 100, message = "The name must be 2 to 100 characters.") String name,
            @Email(message = "Enter a valid email address.") @Size(max = 100, message = "The email may not exceed 100 characters.") String email,
            String password,
            String passwordConfirmation,
            Long roleId,
            Long departmentId) {
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('edit_users')")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body) {
        User u = find(id);
        User me = current.user();
        if (u.isSuperAdmin() && !me.isSuperAdmin()) {
            throw ApiException.forbidden("Only Super Admin can edit Super Admin users.");
        }

        Map<String, Object> before = snapshot(u);
        boolean passwordChanged = false;
        if (body.name() != null) {
            u.setName(body.name().trim());
        }
        if (body.email() != null) {
            String email = body.email().trim().toLowerCase();
            if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
                throw ApiException.validation("email", "The email has already been taken.");
            }
            u.setEmail(email);
        }
        if (body.password() != null && !body.password().isEmpty()) {
            passwordPolicy.check("password", body.password(), body.passwordConfirmation(), u.getEmail());
            u.setPasswordHash(passwordEncoder.encode(body.password()));
            u.setPasswordChangedAt(Times.now());
            passwordChanged = true;
        }
        if (body.roleId() != null && !body.roleId().equals(u.getRole().getId())) {
            if (u.getId().equals(me.getId())) {
                throw ApiException.forbidden("You cannot change your own role.");
            }
            u.setRole(assignableRole(body.roleId()));
        }
        if (body.departmentId() != null) {
            u.setDepartment(department(body.departmentId()));
        }
        users.flush();
        Map<String, Object> after = snapshot(u);
        if (passwordChanged) after.put("password", "(changed)");
        Map<String, Object> oldChanged = new java.util.LinkedHashMap<>();
        Map<String, Object> newChanged = new java.util.LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!java.util.Objects.equals(before.get(k), v)) {
                oldChanged.put(k, before.get(k));
                newChanged.put(k, v);
            }
        });
        if (!newChanged.isEmpty()) {
            audit.record("updated", AuditService.USER, u.getId(), "Updated User " + u.getName(), oldChanged, newChanged);
        }
        return Map.of("message", "User updated successfully", "user", UserView.of(u));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.any('delete_users')")
    @Transactional
    public Map<String, Object> destroy(@PathVariable Long id) {
        User me = current.user();
        if (me.getId().equals(id)) {
            throw ApiException.forbidden("Cannot delete your own account.");
        }
        User u = find(id);
        if (u.isSuperAdmin() && !me.isSuperAdmin()) {
            throw ApiException.forbidden("Only Super Admin can delete Super Admin users.");
        }
        // inventory_logs cascade-delete with their user, which would silently rewrite stock cards.
        Integer movements = jdbc.queryForObject("select count(*) from inventory_logs where user_id = ?", Integer.class, id);
        if (movements != null && movements > 0) {
            throw ApiException.of(HttpStatus.CONFLICT, u.getName() + " has recorded " + movements
                    + " inventory transaction(s); deleting the account would erase them from the stock cards. "
                    + "Change their role or password instead.");
        }
        String name = u.getName();
        Map<String, Object> before = snapshot(u);
        users.delete(u);
        // Surface FK violations (the user still owns records) here, as a 409, not at commit time.
        users.flush();
        audit.record("deleted", AuditService.USER, id, "Deleted User " + name, before, null);
        return Map.of("message", "User '" + name + "' deleted successfully");
    }

    private static Map<String, Object> snapshot(User u) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole() == null ? null : u.getRole().getName());
        m.put("department", u.getDepartment() == null ? null : u.getDepartment().getName());
        return m;
    }

    private User find(Long id) {
        return users.findWithRoleById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    /** Only a Super Admin may hand out a role that carries Super Admin powers. */
    private Role assignableRole(Long roleId) {
        Role role = roles.findById(roleId).orElseThrow(() -> ApiException.validation("role_id", "The selected role is invalid."));
        if (role.isSuperAdmin() && !current.superAdmin()) {
            throw ApiException.forbidden("Only Super Admin can assign the Super Admin role.");
        }
        return role;
    }

    private Department department(Long departmentId) {
        return departments.findById(departmentId)
                .orElseThrow(() -> ApiException.validation("department_id", "The selected department is invalid."));
    }
}

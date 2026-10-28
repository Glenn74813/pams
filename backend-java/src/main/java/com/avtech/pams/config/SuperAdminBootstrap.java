package com.avtech.pams.config;

import com.avtech.pams.common.Times;
import com.avtech.pams.department.DepartmentRepository;
import com.avtech.pams.role.Role;
import com.avtech.pams.role.RoleRepository;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On a fresh database (no users at all), creates the first Super Admin from PAMS_ADMIN_EMAIL and
 * PAMS_ADMIN_PASSWORD. Does nothing once any user exists, so it never touches a live system.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public SuperAdminBootstrap(UserRepository users, RoleRepository roles, DepartmentRepository departments,
                               PasswordEncoder passwordEncoder,
                               @Value("${PAMS_ADMIN_EMAIL:}") String email,
                               @Value("${PAMS_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.roles = roles;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No users exist. Set PAMS_ADMIN_EMAIL and PAMS_ADMIN_PASSWORD to create the first Super Admin.");
            return;
        }
        Role superAdmin = roles.findByName(Role.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Super Admin role missing; V2 seed did not run"));
        User u = new User();
        u.setName("Super Administrator");
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setPasswordChangedAt(Times.now());
        u.setEmailVerifiedAt(Times.now());
        u.setRole(superAdmin);
        u.setDepartment(departments.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No department exists")));
        users.save(u);
        log.info("Created the first Super Admin account: {}", u.getEmail());
    }
}

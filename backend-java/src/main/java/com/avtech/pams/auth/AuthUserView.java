package com.avtech.pams.auth;

import com.avtech.pams.department.DepartmentView;
import com.avtech.pams.role.Permissions;
import com.avtech.pams.user.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** The signed-in user with everything the frontend needs to build menus and check permissions. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthUserView(
        Long id,
        String name,
        String email,
        Long roleId,
        Long departmentId,
        RoleRef role,
        DepartmentView department,
        List<String> permissions,
        List<String> accessibleModules,
        boolean isSuperAdmin,
        boolean twoFactorEnabled,
        boolean hasSignature,
        String signatureImage) {

    public record RoleRef(Long id, String name, String description) {
    }

    public static AuthUserView of(User u, String signatureImage) {
        var role = u.getRole();
        return new AuthUserView(u.getId(), u.getName(), u.getEmail(),
                role.getId(), u.getDepartment().getId(),
                new RoleRef(role.getId(), role.getName(), role.getDescription()),
                DepartmentView.of(u.getDepartment()),
                List.copyOf(role.getPermissions()),
                Permissions.accessibleModules(role),
                u.isSuperAdmin(), u.isTwoFactorEnabled(), u.getSignaturePath() != null,
                signatureImage);
    }
}

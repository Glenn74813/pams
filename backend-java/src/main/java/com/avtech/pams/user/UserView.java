package com.avtech.pams.user;

import com.avtech.pams.common.Times;
import com.avtech.pams.department.DepartmentView;

import java.time.OffsetDateTime;

/** A user as the user-management screens see it. Never includes the password hash or 2FA secret. */
public record UserView(
        Long id,
        String name,
        String email,
        Long roleId,
        Long departmentId,
        RoleSummary role,
        DepartmentView department,
        boolean twoFactorEnabled,
        boolean hasSignature,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime passwordChangedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean isActive,
        OffsetDateTime lockedUntil) {

    public record RoleSummary(Long id, String name, boolean isSuperAdmin) {
    }

    public static UserView of(User u) {
        return of(u, null);
    }

    public static UserView of(User u, OffsetDateTime lockedUntil) {
        var role = u.getRole();
        return new UserView(u.getId(), u.getName(), u.getEmail(),
                role == null ? null : role.getId(),
                u.getDepartment() == null ? null : u.getDepartment().getId(),
                role == null ? null : new RoleSummary(role.getId(), role.getName(), role.isSuperAdmin()),
                DepartmentView.of(u.getDepartment()),
                u.isTwoFactorEnabled(), u.getSignaturePath() != null,
                Times.toOffset(u.getEmailVerifiedAt()), Times.toOffset(u.getPasswordChangedAt()),
                Times.toOffset(u.getCreatedAt()), Times.toOffset(u.getUpdatedAt()), u.isActive(), lockedUntil);
    }
}

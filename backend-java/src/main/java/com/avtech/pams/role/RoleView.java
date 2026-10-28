package com.avtech.pams.role;

import com.avtech.pams.common.Times;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

public record RoleView(
        Long id,
        String name,
        String description,
        List<String> permissions,
        boolean isSystem,
        boolean isSuperAdmin,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long usersCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static RoleView of(Role r) {
        return of(r, null);
    }

    public static RoleView of(Role r, Long usersCount) {
        return new RoleView(r.getId(), r.getName(), r.getDescription(), List.copyOf(r.getPermissions()),
                r.isSystem(), r.isSuperAdmin(), usersCount,
                Times.toOffset(r.getCreatedAt()), Times.toOffset(r.getUpdatedAt()));
    }
}

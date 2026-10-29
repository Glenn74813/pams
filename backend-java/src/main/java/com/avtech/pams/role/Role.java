package com.avtech.pams.role;

import com.avtech.pams.common.Timestamped;
import com.avtech.pams.common.JsonText;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "roles")
public class Role extends Timestamped {

    public static final String SUPER_ADMIN = "Super Admin";
    public static final String WILDCARD = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    @Convert(converter = JsonText.StringList.class)
    @Column(nullable = false)
    private List<String> permissions = new ArrayList<>();

    @Column(name = "is_system", nullable = false)
    private boolean system;

    public boolean hasPermission(String permission) {
        return permissions.contains(WILDCARD) || permissions.contains(permission);
    }

    public boolean hasAnyPermission(Collection<String> wanted) {
        return wanted.stream().anyMatch(this::hasPermission);
    }

    /** A role with the wildcard can do everything, whatever it is called. */
    public boolean isSuperAdmin() {
        return SUPER_ADMIN.equals(name) || permissions.contains(WILDCARD);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? new ArrayList<>() : new ArrayList<>(permissions);
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }
}

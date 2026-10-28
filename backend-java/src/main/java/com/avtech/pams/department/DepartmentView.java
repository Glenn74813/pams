package com.avtech.pams.department;

public record DepartmentView(Long id, String name, String code) {

    public static DepartmentView of(Department d) {
        return d == null ? null : new DepartmentView(d.getId(), d.getName(), d.getCode());
    }
}

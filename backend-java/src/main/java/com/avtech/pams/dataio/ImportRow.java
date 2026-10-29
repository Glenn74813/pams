package com.avtech.pams.dataio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One data row of an upload: its values, what is wrong with it, and what it becomes when imported. */
final class ImportRow {

    static final String OK = "ok";
    static final String DUPLICATE = "duplicate";
    static final String INCOMPLETE = "incomplete";
    static final String INVALID = "invalid";

    final int row;
    final Map<String, String> data;
    final List<String> incomplete = new ArrayList<>();
    final List<String> invalid = new ArrayList<>();
    final List<String> duplicate = new ArrayList<>();
    String key;
    Object plan;

    ImportRow(int row, Map<String, String> data) {
        this.row = row;
        this.data = data;
    }

    String status() {
        if (!incomplete.isEmpty()) return INCOMPLETE;
        if (!invalid.isEmpty()) return INVALID;
        if (!duplicate.isEmpty()) return DUPLICATE;
        return OK;
    }

    boolean ok() {
        return incomplete.isEmpty() && invalid.isEmpty() && duplicate.isEmpty();
    }

    List<String> messages() {
        List<String> m = new ArrayList<>(incomplete);
        m.addAll(invalid);
        m.addAll(duplicate);
        return m;
    }

    Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("status", status());
        m.put("key", key);
        m.put("messages", messages());
        m.put("data", data);
        return m;
    }

    record ItemPlan(String code, String name, String description, Long categoryId, String newCategory, String unitOfMeasure,
                    String classification, Integer minStock, Integer maxStock, boolean assembly, Integer quantity,
                    BigDecimal unitCost, String location) {
    }

    record SupplierPlan(String vendorCode, String name, String tin, String philgepsRegNumber, String contactPerson, String email,
                        String phone, String mobileNumber, String address, String website, String creditTerms, String notes,
                        boolean active, boolean msme) {
    }

    record DepartmentPlan(String code, String name) {
    }

    record UserPlan(String name, String email, Long roleId, Long departmentId) {
    }
}

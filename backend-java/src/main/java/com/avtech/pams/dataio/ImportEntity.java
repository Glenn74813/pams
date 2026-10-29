package com.avtech.pams.dataio;

import com.avtech.pams.common.ApiException;

import java.util.List;
import java.util.Locale;

/**
 * The master data that can be bulk imported, with the CSV columns each accepts. Columns of the full
 * export (category_id, is_expandable, role_id, ...) are accepted as alternatives, so a table taken
 * from an export ZIP can be imported as it is.
 */
enum ImportEntity {

    ITEMS("items", "Items", List.of(
            new Column("code", false, "", "Left blank, the next ITEM-00000 number is given.", List.of()),
            new Column("name", true, "Bond paper, A4, 80 gsm", "Unique.", List.of()),
            new Column("description", false, "White, 500 sheets per ream", "", List.of()),
            new Column("category", true, "Office Supplies", "Category name, or Parent > Child.", List.of("category_id", "category_name")),
            new Column("unit_of_measure", true, "ream", "", List.of("unit")),
            new Column("classification", true, "consumable", "consumable, semi_expendable or ppe.", List.of()),
            new Column("min_stock", false, "10", "Whole number, default 0.", List.of()),
            new Column("max_stock", false, "100", "Whole number; blank or 0 = no maximum.", List.of()),
            new Column("is_assembly", false, "false", "true or false.", List.of()),
            new Column("initial_quantity", false, "50", "Opening stock; needs unit_cost.", List.of("quantity")),
            new Column("unit_cost", false, "245.00", "Checked against the capitalization threshold.", List.of()),
            new Column("location", false, "Supply Room", "Where the opening stock is kept.", List.of())),
            List.of("is_expandable", "is_semi_expandable")),

    SUPPLIERS("suppliers", "Suppliers", List.of(
            new Column("vendor_code", false, "", "Left blank, the next VND-0000 number is given.", List.of()),
            new Column("name", true, "ZZ Example Trading Corp.", "Registered name; unique.", List.of()),
            new Column("tin", false, "123-456-789-000", "9 digits plus branch code; unique.", List.of()),
            new Column("philgeps_reg_number", false, "PG-2026-0001", "", List.of()),
            new Column("contact_person", false, "Juan Dela Cruz", "", List.of()),
            new Column("email", false, "sales@example.com", "", List.of()),
            new Column("phone", false, "(02) 8123 4567", "", List.of()),
            new Column("mobile_number", false, "0917 123 4567", "", List.of()),
            new Column("address", false, "123 Rizal Ave., Manila", "", List.of()),
            new Column("website", false, "https://example.com", "", List.of()),
            new Column("credit_terms", false, "30 days", "", List.of()),
            new Column("notes", false, "", "", List.of()),
            new Column("is_active", false, "true", "true or false, default true.", List.of()),
            new Column("is_msme", false, "false", "true or false, default false.", List.of())),
            List.of()),

    DEPARTMENTS("departments", "Departments", List.of(
            new Column("code", true, "ACCTG", "Unique.", List.of()),
            new Column("name", true, "Accounting Office", "Unique.", List.of())),
            List.of()),

    USERS("users", "Users", List.of(
            new Column("name", true, "Maria Santos", "2 to 100 characters.", List.of()),
            new Column("email", true, "maria.santos@example.gov.ph", "Unique; the sign-in name.", List.of()),
            new Column("role", true, "End User", "Role name.", List.of("role_id", "role_name")),
            new Column("department", true, "ACCTG", "Department code or name.", List.of("department_id", "department_code", "department_name"))),
            List.of());

    record Column(String name, boolean required, String example, String hint, List<String> aliases) {
    }

    final String key;
    final String label;
    final List<Column> columns;
    /** Extra export columns that are read (and so not reported as ignored). */
    final List<String> extra;

    ImportEntity(String key, String label, List<Column> columns, List<String> extra) {
        this.key = key;
        this.label = label;
        this.columns = columns;
        this.extra = extra;
    }

    static ImportEntity of(String key) {
        if (key != null) {
            for (ImportEntity e : values()) {
                if (e.key.equals(key.trim().toLowerCase(Locale.ROOT))) return e;
            }
        }
        throw ApiException.validation("entity", "Choose what to import: items, suppliers, departments or users.");
    }
}

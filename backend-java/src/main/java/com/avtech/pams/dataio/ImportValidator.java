package com.avtech.pams.dataio;

import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.dataio.ImportEntity.Column;
import com.avtech.pams.dataio.ImportRow.DepartmentPlan;
import com.avtech.pams.dataio.ImportRow.ItemPlan;
import com.avtech.pams.dataio.ImportRow.SupplierPlan;
import com.avtech.pams.dataio.ImportRow.UserPlan;
import com.avtech.pams.inventory.Classification;
import com.avtech.pams.role.Role;
import com.avtech.pams.role.RoleRepository;
import com.avtech.pams.setting.SettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Classifies each uploaded row as ok, duplicate, incomplete or invalid, applying the same rules as
 * the create screens (InventoryService.create, SupplierController.store, UserController.store), and
 * prepares what each ok row becomes. Runs again at commit time against the data as it is then.
 */
@Component
class ImportValidator {

    static final int MAX_ROWS = 20_000;
    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final String TIN = "^\\d{3}-?\\d{3}-?\\d{3}(-?\\d{3,5})?$";
    private static final Set<String> TRUE = Set.of("true", "yes", "y", "1", "t");
    private static final Set<String> FALSE = Set.of("false", "no", "n", "0", "f");

    private final JdbcTemplate jdbc;
    private final RoleRepository roles;
    private final SettingService settings;
    private final CurrentUser current;

    ImportValidator(JdbcTemplate jdbc, RoleRepository roles, SettingService settings, CurrentUser current) {
        this.jdbc = jdbc;
        this.roles = roles;
        this.settings = settings;
        this.current = current;
    }

    /** Which file column feeds each field; a problem with the header row aborts the upload. */
    record Header(Map<String, Integer> index, Map<String, String> via, List<String> ignored) {
    }

    static final class UploadException extends RuntimeException {
        UploadException(String message) {
            super(message);
        }
    }

    record Parsed(Header header, List<ImportRow> rows) {
    }

    Parsed parse(ImportEntity entity, String text) {
        List<Csv.Record> records;
        try {
            records = Csv.parse(text);
        } catch (IllegalArgumentException e) {
            throw new UploadException("The file is not valid CSV: " + e.getMessage());
        }
        if (records.isEmpty()) throw new UploadException("The file is empty.");
        Header header = header(entity, records.getFirst().fields());
        if (records.size() == 1) throw new UploadException("The file has a header row but no data rows.");
        if (records.size() - 1 > MAX_ROWS) {
            throw new UploadException("The file has " + (records.size() - 1) + " rows; import at most " + MAX_ROWS + " at a time.");
        }
        List<ImportRow> rows = new ArrayList<>();
        for (Csv.Record r : records.subList(1, records.size())) {
            Map<String, String> data = new LinkedHashMap<>();
            header.index().forEach((name, i) -> {
                String v = i < r.fields().size() ? r.fields().get(i).trim() : "";
                data.put(name, v.isEmpty() ? null : v);
            });
            rows.add(new ImportRow(r.line(), data));
        }
        return new Parsed(header, rows);
    }

    private static Header header(ImportEntity entity, List<String> raw) {
        List<String> names = raw.stream().map(h -> h.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_")).toList();
        Map<String, Integer> index = new LinkedHashMap<>();
        Map<String, String> via = new HashMap<>();
        Set<Integer> used = new java.util.HashSet<>();
        for (Column c : entity.columns) {
            List<String> accepted = new ArrayList<>(List.of(c.name()));
            accepted.addAll(c.aliases());
            for (String a : accepted) {
                int i = names.indexOf(a);
                if (i >= 0) {
                    index.put(c.name(), i);
                    via.put(c.name(), a);
                    used.add(i);
                    break;
                }
            }
        }
        for (String extra : entity.extra) {
            int i = names.indexOf(extra);
            if (i >= 0) {
                index.put(extra, i);
                used.add(i);
            }
        }
        List<String> missing = new ArrayList<>();
        for (Column c : entity.columns) {
            if (!c.required() || index.containsKey(c.name())) continue;
            if (entity == ImportEntity.ITEMS && c.name().equals("classification")
                    && (index.containsKey("is_expandable") || index.containsKey("is_semi_expandable"))) continue;
            missing.add(c.name());
        }
        if (!missing.isEmpty()) {
            throw new UploadException("The header row is missing the column" + (missing.size() > 1 ? "s " : " ") + String.join(", ", missing)
                    + ". Download the " + entity.label.toLowerCase(Locale.ROOT) + " template for the expected headers.");
        }
        List<String> ignored = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (!used.contains(i) && !names.get(i).isEmpty()) ignored.add(names.get(i));
        }
        return new Header(index, via, ignored);
    }

    // ───────────────────────── classification ─────────────────────────

    void validate(ImportEntity entity, Parsed parsed, boolean createCategories) {
        switch (entity) {
            case ITEMS -> items(parsed, createCategories);
            case SUPPLIERS -> suppliers(parsed.rows());
            case DEPARTMENTS -> departments(parsed.rows());
            case USERS -> users(parsed);
        }
    }

    private record CategoryRef(long id, String name, Long parentId) {
    }

    private void items(Parsed parsed, boolean createCategories) {
        Map<String, Integer> seenCode = new HashMap<>();
        Map<String, Integer> seenName = new HashMap<>();
        Set<String> codes = lowerSet("select code from items");
        Set<String> names = lowerSet("select name from items");
        List<CategoryRef> cats = jdbc.query("select id, name, parent_id from categories",
                (rs, i) -> new CategoryRef(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3, Long.class)));
        Map<Long, CategoryRef> catById = new HashMap<>();
        cats.forEach(c -> catById.put(c.id(), c));
        BigDecimal threshold = settings.capitalizationThreshold();
        boolean byCategoryId = "category_id".equals(parsed.header().via().get("category"));

        for (ImportRow r : parsed.rows()) {
            Map<String, String> d = r.data;
            String code = d.get("code");
            String name = d.get("name");
            r.key = code != null ? code + " " + Objects.toString(name, "") : name;
            required(r, d, "name", "category", "unit_of_measure");
            length(r, "code", code, 255);
            length(r, "name", name, 255);
            length(r, "unit_of_measure", d.get("unit_of_measure"), 255);

            Long categoryId = null;
            String newCategory = null;
            String cat = d.get("category");
            if (cat != null) {
                if (byCategoryId) {
                    Long id = longValue(r, "category_id", cat);
                    if (id != null && !catById.containsKey(id)) r.invalid.add("category_id: there is no category #" + id + ".");
                    categoryId = id;
                } else if (cat.contains(">")) {
                    String[] p = cat.split(">", 2);
                    String parent = p[0].trim();
                    String child = p[1].trim();
                    List<CategoryRef> m = cats.stream().filter(c -> c.name().equalsIgnoreCase(child) && c.parentId() != null
                            && catById.containsKey(c.parentId()) && catById.get(c.parentId()).name().equalsIgnoreCase(parent)).toList();
                    if (m.isEmpty()) r.invalid.add("category: there is no category \"" + child + "\" under \"" + parent + "\".");
                    else categoryId = m.getFirst().id();
                } else {
                    List<CategoryRef> m = cats.stream().filter(c -> c.name().equalsIgnoreCase(cat)).toList();
                    if (m.size() > 1) {
                        r.invalid.add("category: more than one category is named \"" + cat + "\"; write it as Parent > " + cat + ".");
                    } else if (m.size() == 1) {
                        categoryId = m.getFirst().id();
                    } else if (createCategories) {
                        if (cat.length() > 255) r.invalid.add("category: the name may not exceed 255 characters.");
                        newCategory = cat;
                    } else {
                        r.invalid.add("category: \"" + cat + "\" does not exist. Add it first, or choose to create missing categories.");
                    }
                }
            }

            String cls = classification(r, d);
            boolean assembly = bool(r, "is_assembly", d.get("is_assembly"), false);
            Integer min = intValue(r, "min_stock", d.get("min_stock"));
            Integer max = intValue(r, "max_stock", d.get("max_stock"));
            if (min != null && min < 0) r.invalid.add("min_stock: cannot be negative.");
            if (max != null && max < 0) r.invalid.add("max_stock: cannot be negative.");
            if (max != null && max > 0 && max < (min == null ? 0 : min)) r.invalid.add("max_stock: must be at least the minimum stock.");
            Integer qty = intValue(r, "initial_quantity", d.get("initial_quantity"));
            BigDecimal cost = decimal(r, "unit_cost", d.get("unit_cost"));
            if (qty != null && qty < 0) r.invalid.add("initial_quantity: cannot be negative.");
            if (cost != null && cost.signum() < 0) r.invalid.add("unit_cost: cannot be negative.");
            boolean hasQty = qty != null && qty > 0;
            boolean hasCost = cost != null && cost.signum() > 0;
            if (hasQty != hasCost && (qty == null || qty >= 0) && (cost == null || cost.signum() >= 0)) {
                r.invalid.add(hasQty ? "unit_cost: enter the unit cost of the opening stock." : "initial_quantity: enter the opening quantity for this unit cost.");
            }
            if (hasCost && !assembly && cls != null) {
                if (cls.equals("semi_expendable") && cost.compareTo(threshold) >= 0) {
                    r.invalid.add("unit_cost: " + peso(cost) + " meets or exceeds the " + peso(threshold)
                            + " capitalization threshold. COA requires this item to be classified as PPE, not Semi-Expendable.");
                }
                if (cls.equals("ppe") && cost.compareTo(threshold) < 0) {
                    r.invalid.add("unit_cost: " + peso(cost) + " is below the " + peso(threshold)
                            + " capitalization threshold. COA classifies this as Semi-Expendable Property, not PPE.");
                }
            }

            if (code != null) {
                String k = code.toLowerCase(Locale.ROOT);
                if (codes.contains(k)) r.duplicate.add("An item with code " + code + " already exists.");
                else if (seenCode.containsKey(k)) r.duplicate.add("Same item code as row " + seenCode.get(k) + ".");
            }
            if (name != null) {
                String k = name.toLowerCase(Locale.ROOT);
                if (names.contains(k)) r.duplicate.add("An item named \"" + name + "\" already exists.");
                else if (seenName.containsKey(k)) r.duplicate.add("Same item name as row " + seenName.get(k) + ".");
            }
            if (r.ok()) {
                if (code != null) seenCode.put(code.toLowerCase(Locale.ROOT), r.row);
                seenName.put(name.toLowerCase(Locale.ROOT), r.row);
                r.plan = new ItemPlan(code, name, d.get("description"), categoryId, newCategory, d.get("unit_of_measure"), cls,
                        min, max, assembly, hasQty ? qty : null, hasQty ? cost : null, d.get("location"));
            }
        }
    }

    private static String classification(ImportRow r, Map<String, String> d) {
        String v = d.get("classification");
        if (v != null) {
            String k = v.toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_").replace("expandable", "expendable");
            if (k.equals("semi_expendable_property") || k.equals("semiexpendable")) k = "semi_expendable";
            if (k.equals("property,_plant_and_equipment")) k = "ppe";
            try {
                return Classification.fromKey(k).key();
            } catch (IllegalArgumentException e) {
                r.invalid.add("classification: \"" + v + "\" is not one of consumable, semi_expendable or ppe.");
                return null;
            }
        }
        if (d.containsKey("is_expandable") || d.containsKey("is_semi_expandable")) {
            boolean ppe = bool(r, "is_expandable", d.get("is_expandable"), false);
            boolean semi = bool(r, "is_semi_expandable", d.get("is_semi_expandable"), false);
            if (ppe && semi) {
                r.invalid.add("is_expandable / is_semi_expandable: an item cannot be both PPE and semi-expendable.");
                return null;
            }
            return ppe ? "ppe" : semi ? "semi_expendable" : "consumable";
        }
        r.incomplete.add("classification is required.");
        return null;
    }

    private void suppliers(List<ImportRow> rows) {
        Set<String> names = lowerSet("select name from suppliers");
        Set<String> codes = lowerSet("select vendor_code from suppliers where vendor_code is not null");
        Set<String> emails = lowerSet("select email from suppliers where email is not null");
        Set<String> tins = new java.util.HashSet<>();
        jdbc.queryForList("select tin from suppliers where tin is not null", String.class).forEach(t -> tins.add(digits(t)));
        Map<String, Integer> seenName = new HashMap<>();
        Map<String, Integer> seenTin = new HashMap<>();
        Map<String, Integer> seenCode = new HashMap<>();
        Map<String, Integer> seenEmail = new HashMap<>();

        for (ImportRow r : rows) {
            Map<String, String> d = r.data;
            String name = d.get("name");
            r.key = name;
            required(r, d, "name");
            length(r, "name", name, 255);
            for (String f : List.of("vendor_code", "philgeps_reg_number", "contact_person", "email", "phone", "mobile_number", "website", "credit_terms")) {
                length(r, f, d.get(f), 255);
            }
            String tin = d.get("tin");
            if (tin != null && !tin.matches(TIN)) r.invalid.add("tin: 9 digits plus the branch code, e.g. 123-456-789-000.");
            String email = d.get("email");
            if (email != null && !email.matches(EMAIL)) r.invalid.add("email: \"" + email + "\" is not a valid email address.");
            String website = d.get("website");
            if (website != null && !website.matches("^https?://\\S+$")) r.invalid.add("website: enter it as https://…");
            boolean active = bool(r, "is_active", d.get("is_active"), true);
            boolean msme = bool(r, "is_msme", d.get("is_msme"), false);
            if (email != null) {
                String k = email.toLowerCase(Locale.ROOT);
                if (emails.contains(k)) r.invalid.add("email: another supplier uses " + email + ".");
                else if (seenEmail.containsKey(k)) r.invalid.add("email: same as row " + seenEmail.get(k) + ".");
            }

            if (name != null) {
                String k = name.toLowerCase(Locale.ROOT);
                if (names.contains(k)) r.duplicate.add("A supplier named " + name + " is already registered.");
                else if (seenName.containsKey(k)) r.duplicate.add("Same supplier name as row " + seenName.get(k) + ".");
            }
            if (tin != null && tin.matches(TIN)) {
                String k = digits(tin);
                if (tins.contains(k)) r.duplicate.add("A supplier with TIN " + tin + " is already registered.");
                else if (seenTin.containsKey(k)) r.duplicate.add("Same TIN as row " + seenTin.get(k) + ".");
            }
            String code = d.get("vendor_code");
            if (code != null) {
                String k = code.toLowerCase(Locale.ROOT);
                if (codes.contains(k)) r.duplicate.add("Vendor code " + code + " is taken.");
                else if (seenCode.containsKey(k)) r.duplicate.add("Same vendor code as row " + seenCode.get(k) + ".");
            }
            if (r.ok()) {
                seenName.put(name.toLowerCase(Locale.ROOT), r.row);
                if (tin != null) seenTin.put(digits(tin), r.row);
                if (code != null) seenCode.put(code.toLowerCase(Locale.ROOT), r.row);
                if (email != null) seenEmail.put(email.toLowerCase(Locale.ROOT), r.row);
                r.plan = new SupplierPlan(code, name, tin, d.get("philgeps_reg_number"), d.get("contact_person"), email, d.get("phone"),
                        d.get("mobile_number"), d.get("address"), website, d.get("credit_terms"), d.get("notes"), active, msme);
            }
        }
    }

    private void departments(List<ImportRow> rows) {
        Set<String> codes = lowerSet("select code from departments");
        Set<String> names = lowerSet("select name from departments");
        Map<String, Integer> seenCode = new HashMap<>();
        Map<String, Integer> seenName = new HashMap<>();
        for (ImportRow r : rows) {
            Map<String, String> d = r.data;
            String code = d.get("code");
            String name = d.get("name");
            r.key = code != null ? code + " " + Objects.toString(name, "") : name;
            required(r, d, "code", "name");
            length(r, "code", code, 255);
            length(r, "name", name, 255);
            if (code != null) {
                String k = code.toLowerCase(Locale.ROOT);
                if (codes.contains(k)) r.duplicate.add("Department code " + code + " already exists.");
                else if (seenCode.containsKey(k)) r.duplicate.add("Same department code as row " + seenCode.get(k) + ".");
            }
            if (name != null) {
                String k = name.toLowerCase(Locale.ROOT);
                if (names.contains(k)) r.duplicate.add("A department named \"" + name + "\" already exists.");
                else if (seenName.containsKey(k)) r.duplicate.add("Same department name as row " + seenName.get(k) + ".");
            }
            if (r.ok()) {
                seenCode.put(code.toLowerCase(Locale.ROOT), r.row);
                seenName.put(name.toLowerCase(Locale.ROOT), r.row);
                r.plan = new DepartmentPlan(code, name);
            }
        }
    }

    private record DeptRef(long id, String code, String name) {
    }

    private void users(Parsed parsed) {
        Set<String> emails = lowerSet("select email from users");
        List<Role> allRoles = roles.findAll();
        List<DeptRef> depts = jdbc.query("select id, code, name from departments", (rs, i) -> new DeptRef(rs.getLong(1), rs.getString(2), rs.getString(3)));
        boolean superAdmin = current.superAdmin();
        boolean roleById = "role_id".equals(parsed.header().via().get("role"));
        boolean deptById = "department_id".equals(parsed.header().via().get("department"));
        Map<String, Integer> seenEmail = new HashMap<>();

        for (ImportRow r : parsed.rows()) {
            Map<String, String> d = r.data;
            String name = d.get("name");
            String email = d.get("email") == null ? null : d.get("email").toLowerCase(Locale.ROOT);
            r.key = email;
            required(r, d, "name", "email", "role", "department");
            if (name != null && (name.length() < 2 || name.length() > 100)) r.invalid.add("name: must be 2 to 100 characters.");
            if (email != null && (!email.matches(EMAIL) || email.length() > 100)) r.invalid.add("email: \"" + d.get("email") + "\" is not a valid email address.");

            Long roleId = null;
            String role = d.get("role");
            if (role != null) {
                Role match = null;
                if (roleById) {
                    Long id = longValue(r, "role_id", role);
                    if (id != null) match = allRoles.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
                } else {
                    match = allRoles.stream().filter(x -> x.getName().equalsIgnoreCase(role)).findFirst().orElse(null);
                }
                if (match == null) r.invalid.add("role: \"" + role + "\" is not a role.");
                else if (match.isSuperAdmin() && !superAdmin) r.invalid.add("role: only Super Admin can assign the Super Admin role.");
                else roleId = match.getId();
            }
            Long deptId = null;
            String dept = d.get("department");
            if (dept != null) {
                DeptRef match;
                if (deptById) {
                    Long id = longValue(r, "department_id", dept);
                    match = id == null ? null : depts.stream().filter(x -> x.id() == id).findFirst().orElse(null);
                } else {
                    match = depts.stream().filter(x -> x.code().equalsIgnoreCase(dept)).findFirst()
                            .orElse(depts.stream().filter(x -> x.name().equalsIgnoreCase(dept)).findFirst().orElse(null));
                }
                if (match == null) r.invalid.add("department: \"" + dept + "\" is not a department code or name.");
                else deptId = match.id();
            }
            if (email != null) {
                if (emails.contains(email)) r.duplicate.add("A user with email " + email + " already exists.");
                else if (seenEmail.containsKey(email)) r.duplicate.add("Same email as row " + seenEmail.get(email) + ".");
            }
            if (r.ok()) {
                seenEmail.put(email, r.row);
                r.plan = new UserPlan(name, email, roleId, deptId);
            }
        }
    }

    // ───────────────────────── helpers ─────────────────────────

    private Set<String> lowerSet(String sql) {
        Set<String> out = new java.util.HashSet<>();
        for (String s : jdbc.queryForList(sql, String.class)) if (s != null) out.add(s.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    private static void required(ImportRow r, Map<String, String> d, String... fields) {
        for (String f : fields) if (d.get(f) == null) r.incomplete.add(f + " is required.");
    }

    private static void length(ImportRow r, String field, String v, int max) {
        if (v != null && v.length() > max) r.invalid.add(field + ": may not exceed " + max + " characters.");
    }

    private static boolean bool(ImportRow r, String field, String v, boolean dflt) {
        if (v == null) return dflt;
        String k = v.toLowerCase(Locale.ROOT);
        if (TRUE.contains(k)) return true;
        if (FALSE.contains(k)) return false;
        r.invalid.add(field + ": \"" + v + "\" is not true or false.");
        return dflt;
    }

    private static Integer intValue(ImportRow r, String field, String v) {
        if (v == null) return null;
        try {
            return Integer.valueOf(v.replace(",", ""));
        } catch (NumberFormatException e) {
            r.invalid.add(field + ": \"" + v + "\" is not a whole number.");
            return null;
        }
    }

    private static Long longValue(ImportRow r, String field, String v) {
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            r.invalid.add(field + ": \"" + v + "\" is not an id.");
            return null;
        }
    }

    private static BigDecimal decimal(ImportRow r, String field, String v) {
        if (v == null) return null;
        try {
            BigDecimal b = new BigDecimal(v.replace(",", "").replace("₱", "").trim());
            if (b.scale() > 2) r.invalid.add(field + ": at most 2 decimal places.");
            return b;
        } catch (NumberFormatException e) {
            r.invalid.add(field + ": \"" + v + "\" is not an amount.");
            return null;
        }
    }

    static String digits(String s) {
        return s.replaceAll("\\D", "");
    }

    private static String peso(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "₱" + f.format(v);
    }
}

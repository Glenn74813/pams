package com.avtech.pams.role;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The permission catalogue (grouped as the role editor shows it) and the sidebar module map.
 * Ported from App\Models\Role::getPermissionsList and App\Traits\HasPermissions::getAccessibleModules.
 */
public final class Permissions {

    private Permissions() {
    }

    public static final Map<String, Map<String, String>> CATALOGUE = catalogue();

    public static final Set<String> ALL_KEYS = CATALOGUE.values().stream()
            .flatMap(group -> group.keySet().stream())
            .collect(Collectors.toUnmodifiableSet());

    /** Sidebar module -> permissions that unlock it (any one is enough). An empty list means everyone. */
    public static final Map<String, List<String>> MODULES = modules();

    private static Map<String, Map<String, String>> catalogue() {
        Map<String, Map<String, String>> c = new LinkedHashMap<>();
        c.put("Dashboard", group("view_dashboard", "View Dashboard"));
        c.put("Inventory / Items", group(
                "view_inventory", "View Items",
                "create_inventory", "Create Items",
                "edit_inventory", "Edit Items",
                "delete_inventory", "Delete Items"));
        c.put("Properties / Assets", group(
                "view_properties", "View All Properties",
                "view_own_properties", "View Own Assigned Properties",
                "create_properties", "Create Properties",
                "edit_properties", "Edit Properties",
                "delete_properties", "Delete Properties",
                "assign_properties", "Assign Properties",
                "transfer_properties", "Transfer Properties"));
        c.put("Procurement", group(
                "view_procurement", "View Procurement",
                "create_procurement", "Create Purchase Requests/Orders",
                "edit_procurement", "Edit Purchase Requests/Orders",
                "approve_procurement", "Approve Purchase Requests (alternate signatory for any PR step)"));
        c.put("Purchase Request Workflow", group(
                "pr_approve_department", "PR Approval — Department Head",
                "pr_approve_budget", "PR Approval — Budget Officer",
                "pr_approve_accounting", "PR Approval — Accounting",
                "pr_approve_bac", "PR Approval — BAC Secretariat"));
        c.put("Receiving", group(
                "view_receiving", "View Receiving",
                "create_receiving", "Create Inspection Reports",
                "accept_delivery", "Accept Deliveries"));
        c.put("Warehouse", group(
                "view_warehouse", "View Warehouse",
                "manage_warehouse", "Manage Warehouse",
                "add_stock", "Add Stock",
                "release_stock", "Release Stock"));
        c.put("Requests / Issuance", group(
                "view_requests", "View All Requests",
                "view_own_requests", "View Own Requests",
                "create_requests", "Create Requests",
                "edit_requests", "Edit Any Request",
                "edit_own_requests", "Edit Own Requests (if not approved)",
                "delete_own_requests", "Delete Own Requests (if not approved)",
                "approve_requests", "Approve Requests",
                "reject_requests", "Reject Requests"));
        c.put("Monitoring", group("view_monitoring", "View Monitoring"));
        c.put("Physical Inventory", group(
                "view_physical_inventory", "View Physical Inventory",
                "create_physical_inventory", "Create Physical Count",
                "conduct_physical_inventory", "Conduct Physical Count"));
        c.put("Stock Adjustments", group(
                "create_adjustments", "Create Stock Adjustments",
                "approve_adjustments", "Approve Stock Adjustments"));
        c.put("Gate Pass", group(
                "view_gate_pass", "View Gate Pass",
                "create_gate_pass", "Create Gate Pass",
                "edit_gate_pass", "Edit Gate Pass",
                "delete_gate_pass", "Delete Gate Pass"));
        c.put("Disposal", group(
                "view_disposal", "View Disposal",
                "create_disposal", "Create Disposal Requests",
                "approve_disposal", "Approve Disposal"));
        c.put("Reports", group(
                "view_reports", "View Reports",
                "generate_reports", "Generate Reports"));
        c.put("Analytics", group("view_analytics", "View Analytics"));
        c.put("Log Trails", group("view_log_trails", "View Log Trails"));
        c.put("Suppliers", group(
                "view_suppliers", "View Suppliers",
                "create_suppliers", "Create Suppliers",
                "edit_suppliers", "Edit Suppliers",
                "delete_suppliers", "Delete Suppliers"));
        c.put("Procurement Planning", group(
                "view_procurement_planning", "View PPMP / APP",
                "create_ppmp", "Create PPMP",
                "view_own_ppmp", "View Own PPMP",
                "edit_own_ppmp", "Edit Own PPMP",
                "delete_own_ppmp", "Delete Own PPMP",
                "submit_own_ppmp", "Submit Own PPMP",
                "approve_ppmp", "Approve PPMP",
                "manage_app", "Generate & Consolidate APP",
                "monitor_budget", "Monitor Budget / APP Utilization"));
        c.put("BAC Management", group(
                "view_bac", "View BAC Activities",
                "manage_bac_meetings", "Manage BAC Meetings",
                "manage_bac_resolutions", "Manage BAC Resolutions",
                "conduct_bid_evaluation", "Conduct Bid Evaluation"));
        c.put("RFQ & Alternative Procurement", group(
                "view_rfq", "View RFQs",
                "create_rfq", "Create RFQ / Manage Quotations",
                "generate_abstract", "Generate Abstract of Quotations"));
        c.put("Competitive Bidding", group(
                "view_bidding", "View Biddings",
                "manage_bidding", "Manage Bidding Process",
                "process_award", "Process Award (NOA / NTP)"));
        c.put("Contract Management", group(
                "view_contracts", "View Contracts",
                "create_contracts", "Create Contracts",
                "edit_contracts", "Edit / Amend Contracts"));
        c.put("Payments", group(
                "view_payments", "View Payment Schedules & Petty Cash",
                "manage_payments", "Schedule & Release Payments",
                "approve_payments", "Approve Payments & Petty Cash Vouchers",
                "manage_petty_cash", "Keep a Petty Cash Fund (custodian)"));
        c.put("Document Management", group(
                "view_documents", "View Documents",
                "upload_documents", "Upload / Version Documents",
                "delete_documents", "Delete Documents"));
        c.put("Audit & Compliance", group(
                "view_audit_trail", "View Audit Trail",
                "export_audit_trail", "Export Audit / Compliance Reports"));
        c.put("User Management", group(
                "view_users", "View Users",
                "create_users", "Create Users",
                "edit_users", "Edit Users",
                "delete_users", "Delete Users"));
        c.put("Role Management", group("manage_roles", "Manage Roles & Permissions"));
        c.put("Settings", group(
                "view_settings", "View Settings",
                "edit_settings", "Edit Settings (all areas)",
                "manage_agency_profile", "Manage Agency Profile & Document Logos",
                "manage_system_settings", "Manage System Settings (thresholds, parameters)",
                "manage_signatories", "Manage Authorized Signatories"));
        return c;
    }

    private static Map<String, List<String>> modules() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("dashboard", List.of("view_dashboard"));
        m.put("items", List.of("view_inventory"));
        m.put("properties", List.of("view_properties", "view_own_properties"));
        m.put("my-assets", List.of());
        m.put("procurement-planning", List.of("view_procurement_planning", "create_ppmp", "view_own_ppmp",
                "edit_own_ppmp", "delete_own_ppmp", "submit_own_ppmp", "approve_ppmp", "manage_app", "monitor_budget"));
        m.put("purchase-requests", List.of("view_procurement", "create_procurement", "approve_procurement",
                "pr_approve_department", "pr_approve_budget", "pr_approve_accounting", "pr_approve_bac"));
        m.put("bac", List.of("view_bac", "manage_bac_meetings", "manage_bac_resolutions", "conduct_bid_evaluation"));
        m.put("rfq", List.of("view_rfq", "create_rfq", "generate_abstract"));
        m.put("ps-dbm", List.of("view_procurement", "edit_procurement", "approve_procurement"));
        m.put("bidding", List.of("view_bidding", "manage_bidding", "process_award"));
        m.put("contracts", List.of("view_contracts", "create_contracts", "edit_contracts"));
        m.put("suppliers", List.of("view_suppliers", "create_suppliers", "edit_suppliers"));
        m.put("procurement", List.of("view_procurement"));
        m.put("receiving", List.of("view_receiving"));
        m.put("warehouse", List.of("view_warehouse"));
        m.put("requests", List.of("view_requests", "view_own_requests", "create_requests"));
        m.put("monitoring", List.of("view_monitoring"));
        m.put("physical", List.of("view_physical_inventory", "create_physical_inventory", "conduct_physical_inventory"));
        m.put("stock-adjustments", List.of("create_adjustments", "approve_adjustments"));
        m.put("semi-expendable", List.of("view_inventory", "manage_warehouse", "add_stock"));
        m.put("gate-pass", List.of("view_gate_pass"));
        m.put("maintenance", List.of("view_properties", "edit_properties"));
        m.put("clearance", List.of("view_properties", "assign_properties", "transfer_properties"));
        // Everyone can send documents for signing and sign what is sent to them.
        m.put("signing", List.of());
        m.put("disposal", List.of("view_disposal", "create_disposal", "approve_disposal"));
        m.put("reports", List.of("view_reports"));
        m.put("apcpi", List.of("view_procurement", "approve_procurement", "manage_bac_meetings", "view_reports"));
        m.put("documents", List.of("view_documents"));
        m.put("payments", List.of("view_payments", "manage_payments", "approve_payments", "manage_petty_cash"));
        m.put("audit", List.of("view_audit_trail"));
        m.put("log-trails", List.of("view_log_trails"));
        m.put("users", List.of("view_users"));
        // Everyone manages their own password, 2FA and signature here; the admin tabs check their own permissions.
        m.put("settings", List.of());
        return m;
    }

    /** Sidebar modules this role may open. Super Admin gets all of them; everyone gets my-assets. */
    public static List<String> accessibleModules(Role role) {
        if (role == null) {
            return List.of("my-assets");
        }
        return MODULES.entrySet().stream()
                .filter(e -> role.isSuperAdmin() || e.getValue().isEmpty() || role.hasAnyPermission(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<String, String> group(String... keyLabelPairs) {
        Map<String, String> g = new LinkedHashMap<>();
        for (int i = 0; i < keyLabelPairs.length; i += 2) {
            g.put(keyLabelPairs[i], keyLabelPairs[i + 1]);
        }
        return g;
    }
}

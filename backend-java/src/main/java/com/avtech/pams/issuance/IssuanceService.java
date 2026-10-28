package com.avtech.pams.issuance;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.Times;
import com.avtech.pams.department.DepartmentRepository;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.notification.NotificationService;
import com.avtech.pams.semiexpendable.SemiExpendableIssue;
import com.avtech.pams.semiexpendable.SemiExpendableIssueRepository;
import com.avtech.pams.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Requisition and issue of supplies (RIS). Issuing takes stock first-expired-first-out, records it
 * on the stock card, puts semi-expendable items under the recipient's ICS accountability, and
 * numbers the RIS (plus ICS / PAR when semi-expendable or PPE items go out).
 */
@Service
public class IssuanceService {

    static final String REQUEST_TYPE = "App\\Models\\Request";
    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high");

    private final InventoryRequestRepository requests;
    private final ItemRepository items;
    private final DepartmentRepository departments;
    private final SemiExpendableIssueRepository semiIssues;
    private final StockLedger ledger;
    private final DocumentNumbers numbers;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;

    public IssuanceService(InventoryRequestRepository requests, ItemRepository items, DepartmentRepository departments,
                           SemiExpendableIssueRepository semiIssues, StockLedger ledger, DocumentNumbers numbers,
                           NotificationService notifications, AuditService audit, CurrentUser current, JdbcTemplate jdbc) {
        this.requests = requests;
        this.items = items;
        this.departments = departments;
        this.semiIssues = semiIssues;
        this.ledger = ledger;
        this.numbers = numbers;
        this.notifications = notifications;
        this.audit = audit;
        this.current = current;
        this.jdbc = jdbc;
    }

    public record LineInput(Long itemId, Integer quantity) {
    }

    public record RequestInput(Long departmentId, String purpose, String priority, String custodianName, List<LineInput> items) {
    }

    /** One approver decision. approvedQuantity defaults to the quantity requested. */
    public record Decision(Long id, String status, Integer approvedQuantity) {
    }

    public record Outcome(InventoryRequest request, List<String> warnings) {
    }

    // ───────────────────────── create / edit / delete ─────────────────────────

    @Transactional
    public InventoryRequest create(RequestInput in) {
        User me = current.user();
        InventoryRequest r = new InventoryRequest();
        r.setRequestNumber(numbers.monthly("REQ", "requests.request_number"));
        r.setRequestedBy(me.getId());
        r.setRequestDate(LocalDate.now(Times.ZONE));
        apply(r, in, me);
        requests.saveAndFlush(r);

        Timestamp now = Timestamp.valueOf(Times.now());
        for (RequestItem line : r.getItems()) {
            // Department demand history (used by analytics).
            jdbc.update("insert into request_logs (department_id, item_id, quantity, requested_by, requested_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                    r.getDepartmentId(), line.getItemId(), line.getQuantity(), me.getId(), now, now, now);
        }
        ledger.event(null, me.getId(), "request_created", REQUEST_TYPE, r.getId(),
                "Request " + r.getRequestNumber() + " created: " + describe(r));
        audit.record("created", REQUEST_TYPE, r.getId(), "Created Request " + r.getRequestNumber(), null, snapshot(r));
        return r;
    }

    @Transactional
    public InventoryRequest update(Long id, RequestInput in) {
        InventoryRequest r = requests.lockById(id).orElseThrow(() -> ApiException.notFound("Request"));
        User me = current.user();
        if (!r.isPending()) throw ApiException.of(HttpStatus.CONFLICT, "Only pending requests can be edited.");
        boolean own = me.getId().equals(r.getRequestedBy());
        if (!(current.any("edit_requests") || (own && current.any("edit_own_requests")))) {
            throw ApiException.forbidden("You can only edit your own pending requests.");
        }
        Map<String, Object> before = snapshot(r);
        r.getItems().clear();
        requests.flush();
        apply(r, in, me);
        requests.flush();
        ledger.event(null, me.getId(), "request_updated", REQUEST_TYPE, r.getId(), "Request " + r.getRequestNumber() + " updated by " + me.getName());
        audit.record("updated", REQUEST_TYPE, r.getId(), "Updated Request " + r.getRequestNumber(), before, snapshot(r));
        return r;
    }

    @Transactional
    public void delete(Long id) {
        InventoryRequest r = requests.lockById(id).orElseThrow(() -> ApiException.notFound("Request"));
        User me = current.user();
        if (!r.isPending()) throw ApiException.of(HttpStatus.CONFLICT, "Only pending requests can be deleted.");
        boolean own = me.getId().equals(r.getRequestedBy());
        if (!(me.isSuperAdmin() || (own && current.any("delete_own_requests")))) {
            throw ApiException.forbidden("You can only delete your own pending requests.");
        }
        Map<String, Object> before = snapshot(r);
        requests.delete(r);
        requests.flush();
        ledger.event(null, me.getId(), "request_deleted", null, null, "Request " + r.getRequestNumber() + " deleted by " + me.getName());
        audit.record("deleted", REQUEST_TYPE, id, "Deleted Request " + r.getRequestNumber(), before, null);
    }

    // ───────────────────────── approve / issue / reject ─────────────────────────

    /** Issues every line, each at the quantity requested or whatever usable stock there is. */
    @Transactional
    public Outcome approveAll(Long id) {
        return process(id, null);
    }

    /** Lines left out of the decisions are rejected. */
    @Transactional
    public Outcome process(Long id, List<Decision> decisions) {
        User me = current.user();
        InventoryRequest r = requests.lockById(id).orElseThrow(() -> ApiException.notFound("Request"));
        if (!r.isPending()) throw ApiException.of(HttpStatus.CONFLICT, "Only pending requests can be processed.");
        if (me.getId().equals(r.getRequestedBy()) && !me.isSuperAdmin()) {
            throw ApiException.forbidden("You cannot approve your own request; another approver has to.");
        }

        // Lock every item up front, in id order, so concurrent issues can't deadlock.
        Map<Long, Item> locked = new TreeMap<>();
        r.getItems().stream().map(RequestItem::getItemId).sorted().distinct()
                .forEach(itemId -> locked.put(itemId, ledger.lock(itemId)));

        Map<Long, Decision> byLine = new LinkedHashMap<>();
        if (decisions != null) {
            for (Decision d : decisions) {
                if (d.id() == null || d.status() == null || !Set.of("approved", "rejected").contains(d.status())) {
                    throw ApiException.validation("items", "Each decision needs a line id and a status of approved or rejected.");
                }
                byLine.put(d.id(), d);
            }
        }

        // Work out what each line will issue before touching stock or numbers.
        Map<RequestItem, Integer> toIssue = new LinkedHashMap<>();
        for (RequestItem line : r.getItems()) {
            Item item = locked.get(line.getItemId());
            int qty;
            if (decisions == null) {
                qty = Math.min(line.getQuantity(), ledger.usable(item.getId()));
            } else {
                Decision d = byLine.get(line.getId());
                if (d == null || "rejected".equals(d.status())) {
                    qty = 0;
                } else {
                    qty = d.approvedQuantity() == null ? line.getQuantity() : d.approvedQuantity();
                    if (qty < 0) throw ApiException.validation("items", "Approved quantities cannot be negative.");
                    if (qty > line.getQuantity()) {
                        throw ApiException.validation("items", "Cannot issue more of \"" + item.getName() + "\" than was requested ("
                                + line.getQuantity() + ").");
                    }
                    if (qty > item.getCurrentStock()) {
                        throw ApiException.validation("items", "Cannot issue " + qty + " of \"" + item.getName() + "\": only "
                                + item.getCurrentStock() + " in stock.");
                    }
                }
            }
            toIssue.put(line, qty);
        }

        boolean anyIssued = toIssue.values().stream().anyMatch(q -> q > 0);
        LocalDate today = LocalDate.now(Times.ZONE);
        if (anyIssued) {
            r.setRisNumber(numbers.monthly("RIS", "requests.ris_number"));
            r.setRisDate(today);
        }
        if (issues(toIssue, locked, Item::isSemiExpendable)) {
            r.setIcsNumber(numbers.monthly("ICS", "requests.ics_number", "properties.accountability_number", "property_transfers.reference_number"));
            r.setIcsDate(today);
        }
        if (issues(toIssue, locked, Item::isPpe)) {
            r.setParNumber(numbers.monthly("PAR", "requests.par_number", "properties.accountability_number", "property_transfers.reference_number"));
            r.setParDate(today);
        }

        String office = r.getDepartmentId() == null ? null : departments.findById(r.getDepartmentId()).map(d -> d.getName()).orElse(null);
        String holder = r.getCustodianName() != null && !r.getCustodianName().isBlank() ? r.getCustodianName() : requesterName(r);
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<RequestItem, Integer> e : toIssue.entrySet()) {
            RequestItem line = e.getKey();
            int qty = e.getValue();
            if (qty == 0) {
                line.reject();
                continue;
            }
            Item item = locked.get(line.getItemId());
            int before = item.getCurrentStock();
            warnings.addAll(ledger.issue(item, qty).warnings());
            line.issue(qty);
            ledger.log(item, me.getId(), "item_issued", qty, before, REQUEST_TYPE, r.getId(),
                    "Issued " + qty + " via " + r.getRisNumber() + (office == null ? "" : " (" + office + ")"));
            if (item.isSemiExpendable()) {
                semiIssues.save(new SemiExpendableIssue(item.getId(), r.getId(), r.getRequestedBy(), r.getDepartmentId(),
                        holder, r.getIcsNumber(), qty, today));
            }
        }

        String before = r.getStatus();
        long rejected = r.getItems().stream().filter(l -> RequestItem.REJECTED.equals(l.getStatus())).count();
        long fulfilled = r.getItems().stream().filter(l -> RequestItem.FULFILLED.equals(l.getStatus())).count();
        int total = r.getItems().size();
        r.setStatus(rejected == total ? InventoryRequest.REJECTED
                : fulfilled == total ? InventoryRequest.FULFILLED : InventoryRequest.PARTIALLY_FULFILLED);
        requests.flush();

        ledger.event(null, me.getId(), "request_items_processed", REQUEST_TYPE, r.getId(), "Request " + r.getRequestNumber()
                + " processed: " + fulfilled + " fulfilled, " + rejected + " rejected (overall: " + r.getStatus() + ")");
        audit.record("updated", REQUEST_TYPE, r.getId(), "Processed Request " + r.getRequestNumber(),
                Map.of("status", before), Map.of("status", r.getStatus(), "ris_number", String.valueOf(r.getRisNumber())));
        String label = r.getStatus().replace('_', ' ');
        notifications.notifyUser(r.getRequestedBy(), "request_status", "Request " + label,
                "Your request " + r.getRequestNumber() + " has been " + label + ".", REQUEST_TYPE, r.getId());
        locked.values().stream().filter(i -> i.getMinStock() > 0 && i.getCurrentStock() <= i.getMinStock())
                .forEach(i -> notifications.notifyPermission(List.of("create_adjustments", "approve_adjustments", "manage_warehouse"),
                        null, "low_stock", "Low stock alert", i.getName() + " (" + i.getCode() + ") is at or below its reorder point: "
                                + i.getCurrentStock() + " on hand, reorder point " + i.getMinStock() + ".", "App\\Models\\Item", i.getId()));
        return new Outcome(r, warnings.stream().distinct().toList());
    }

    @Transactional
    public InventoryRequest reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.validation("reason", "Give a reason for rejecting the request.");
        User me = current.user();
        InventoryRequest r = requests.lockById(id).orElseThrow(() -> ApiException.notFound("Request"));
        if (!r.isPending()) {
            throw ApiException.of(HttpStatus.CONFLICT, "Only pending requests can be rejected; processed requests have already moved stock.");
        }
        r.getItems().forEach(RequestItem::reject);
        r.setStatus(InventoryRequest.REJECTED);
        r.setRemarks("Rejected: " + reason.trim());
        requests.flush();
        ledger.event(null, me.getId(), "request_rejected", REQUEST_TYPE, r.getId(), "Request " + r.getRequestNumber() + " rejected. Reason: " + reason.trim());
        audit.record("updated", REQUEST_TYPE, r.getId(), "Rejected Request " + r.getRequestNumber(),
                Map.of("status", InventoryRequest.PENDING), Map.of("status", InventoryRequest.REJECTED, "reason", reason.trim()));
        notifications.notifyUser(r.getRequestedBy(), "request_status", "Request rejected",
                "Your request " + r.getRequestNumber() + " was rejected. Reason: " + reason.trim(), REQUEST_TYPE, r.getId());
        return r;
    }

    // ───────────────────────── helpers ─────────────────────────

    private void apply(InventoryRequest r, RequestInput in, User me) {
        if (in.purpose() == null || in.purpose().isBlank()) throw ApiException.validation("purpose", "State the purpose of the request.");
        String priority = in.priority() == null || in.priority().isBlank() ? "medium" : in.priority();
        if (!PRIORITIES.contains(priority)) throw ApiException.validation("priority", "Priority must be low, medium or high.");
        Long dept = in.departmentId() == null ? me.getDepartment().getId() : in.departmentId();
        if (!departments.existsById(dept)) throw ApiException.validation("department_id", "Choose a valid department.");
        if (in.items() == null || in.items().isEmpty()) throw ApiException.validation("items", "Add at least one item.");

        Set<Long> seen = new HashSet<>();
        for (LineInput l : in.items()) {
            if (l.itemId() == null) throw ApiException.validation("items", "Every line needs an item.");
            if (l.quantity() == null || l.quantity() < 1) throw ApiException.validation("items", "Quantities must be at least 1.");
            if (!seen.add(l.itemId())) throw ApiException.validation("items", "Each item can only appear once; combine the quantities.");
            Item item = items.findById(l.itemId()).orElseThrow(() -> ApiException.validation("items", "One of the items no longer exists."));
            if (!item.isAvailable()) {
                throw ApiException.validation("items", "\"" + item.getName() + "\" is out of stock and cannot be requested.");
            }
            r.addItem(new RequestItem(l.itemId(), l.quantity()));
        }
        r.setDepartmentId(dept);
        r.setPurpose(in.purpose().trim());
        r.setPriority(priority);
        r.setCustodianName(in.custodianName() == null || in.custodianName().isBlank() ? null : in.custodianName().trim());
    }

    private static boolean issues(Map<RequestItem, Integer> toIssue, Map<Long, Item> locked, java.util.function.Predicate<Item> kind) {
        return toIssue.entrySet().stream().anyMatch(e -> e.getValue() > 0 && kind.test(locked.get(e.getKey().getItemId())));
    }

    private String requesterName(InventoryRequest r) {
        if (r.getRequestedBy() == null) return null;
        List<String> n = jdbc.queryForList("select name from users where id = ?", String.class, r.getRequestedBy());
        return n.isEmpty() ? null : n.get(0);
    }

    private String describe(InventoryRequest r) {
        Map<Long, String> names = new LinkedHashMap<>();
        items.findAllById(r.getItems().stream().map(RequestItem::getItemId).toList()).forEach(i -> names.put(i.getId(), i.getName()));
        return String.join(", ", r.getItems().stream().map(l -> names.getOrDefault(l.getItemId(), "item") + " x" + l.getQuantity()).toList());
    }

    private static Map<String, Object> snapshot(InventoryRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("department_id", r.getDepartmentId());
        m.put("purpose", r.getPurpose());
        m.put("priority", r.getPriority());
        m.put("status", r.getStatus());
        m.put("items", r.getItems().stream().map(l -> Map.of("item_id", l.getItemId(), "quantity", l.getQuantity())).toList());
        return m;
    }
}

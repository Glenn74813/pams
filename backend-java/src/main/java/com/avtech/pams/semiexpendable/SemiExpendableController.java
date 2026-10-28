package com.avtech.pams.semiexpendable;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.department.DepartmentRepository;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.inventory.Stock;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Semi-expendable property under someone's accountability (ICS), and recording its return. */
@RestController
@RequestMapping("/api/semi-expendable")
public class SemiExpendableController {

    private static final String TYPE = "App\\Models\\SemiExpendableIssue";
    private static final Set<String> STATUSES = Set.of("issued", "partially_returned", "returned");

    private final SemiExpendableIssueRepository issues;
    private final ItemRepository items;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final StockLedger ledger;
    private final AuditService audit;
    private final CurrentUser current;

    public SemiExpendableController(SemiExpendableIssueRepository issues, ItemRepository items, UserRepository users,
                                    DepartmentRepository departments, StockLedger ledger, AuditService audit, CurrentUser current) {
        this.issues = issues;
        this.items = items;
        this.users = users;
        this.departments = departments;
        this.ledger = ledger;
        this.audit = audit;
        this.current = current;
    }

    public record Ref(Long id, String name) {
    }

    public record ItemRef(Long id, String code, String name, String unitOfMeasure) {
    }

    public record IssueView(Long id, ItemRef item, Long requestId, Ref holder, Ref department, String custodianName,
                            String icsNumber, int quantity, int quantityReturned, int outstanding, LocalDate issuedDate,
                            String status, String remarks) {
    }

    /** Staff with inventory rights see the whole ledger; anyone else sees only what they hold. */
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<IssueView> index(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false, defaultValue = "false") boolean outstanding,
                                         @RequestParam(name = "item_id", required = false) Long itemId,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false, defaultValue = "false") boolean mine) {
        boolean staff = current.any("view_inventory", "manage_warehouse", "add_stock");
        Long holder = mine || !staff ? current.user().getId() : null;
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw ApiException.validation("status", "Unknown status.");
        Page<SemiExpendableIssue> result = issues.search(blank(status), outstanding, itemId, holder, blank(search),
                PageRequest.of(Math.max(0, page - 1), 20));
        Map<SemiExpendableIssue, IssueView> v = views(result.getContent());
        return PageResponse.of(result, v::get);
    }

    public record ReturnInput(Integer quantity, Boolean restock, String remarks) {
    }

    /**
     * Records units handed back. Serviceable returns go back into stock as a lot at the item's
     * weighted average cost; unserviceable ones are only recorded (for disposal).
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("@perm.any('add_stock', 'manage_warehouse')")
    @Transactional
    public IssueView recordReturn(@PathVariable Long id, @RequestBody ReturnInput in) {
        SemiExpendableIssue issue = issues.lockById(id).orElseThrow(() -> ApiException.notFound("Accountability record"));
        if (in.quantity() == null || in.quantity() < 1) throw ApiException.validation("quantity", "Enter a quantity of at least 1.");
        if (in.quantity() > issue.outstanding()) {
            throw ApiException.validation("quantity", "Return quantity (" + in.quantity() + ") exceeds what is still outstanding (" + issue.outstanding() + ").");
        }
        boolean restock = Boolean.TRUE.equals(in.restock());
        Item item = ledger.lock(issue.getItemId());
        int qty = in.quantity();
        String ics = issue.getIcsNumber() == null ? "—" : issue.getIcsNumber();
        issue.recordReturn(qty, in.remarks());
        Long me = current.user().getId();
        if (restock) {
            int before = item.getCurrentStock();
            Stock lot = new Stock();
            lot.setItemId(item.getId());
            lot.setQuantity(qty);
            lot.setUnitCost(item.getWeightedAverageCost().setScale(2, RoundingMode.HALF_UP));
            lot.setReceivedDate(LocalDate.now(Times.ZONE));
            lot.setLocation("Returned (semi-expendable)");
            ledger.receive(item, lot);
            ledger.log(item, me, "stock_in", qty, before, TYPE, issue.getId(), "Semi-expendable returned to stock (" + qty + ") — ICS " + ics);
        } else {
            ledger.event(item.getId(), me, "semi_expendable_returned", TYPE, issue.getId(),
                    "Semi-expendable returned unserviceable (" + qty + ") — " + item.getName() + " (for disposal), ICS " + ics);
        }
        issues.flush();
        audit.record("updated", TYPE, issue.getId(), "Recorded return of " + qty + " " + item.getName() + " (ICS " + ics + ")",
                null, Map.of("quantity_returned", issue.getQuantityReturned(), "restocked", restock, "status", issue.getStatus()));
        return views(List.of(issue)).get(issue);
    }

    private Map<SemiExpendableIssue, IssueView> views(Collection<SemiExpendableIssue> list) {
        Map<Long, Item> byItem = new HashMap<>();
        items.findAllById(list.stream().map(SemiExpendableIssue::getItemId).collect(Collectors.toSet())).forEach(i -> byItem.put(i.getId(), i));
        Map<Long, String> userNames = new HashMap<>();
        users.findAllById(list.stream().map(SemiExpendableIssue::getIssuedTo).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> userNames.put(u.getId(), u.getName()));
        Map<Long, String> deptNames = new HashMap<>();
        departments.findAllById(list.stream().map(SemiExpendableIssue::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(d -> deptNames.put(d.getId(), d.getName()));
        Map<SemiExpendableIssue, IssueView> out = new java.util.LinkedHashMap<>();
        for (SemiExpendableIssue s : list) {
            Item i = byItem.get(s.getItemId());
            out.put(s, new IssueView(s.getId(), i == null ? null : new ItemRef(i.getId(), i.getCode(), i.getName(), i.getUnitOfMeasure()),
                    s.getRequestId(), s.getIssuedTo() == null ? null : new Ref(s.getIssuedTo(), userNames.get(s.getIssuedTo())),
                    s.getDepartmentId() == null ? null : new Ref(s.getDepartmentId(), deptNames.get(s.getDepartmentId())),
                    s.getCustodianName(), s.getIcsNumber(), s.getQuantity(), s.getQuantityReturned(), s.outstanding(),
                    s.getIssuedDate(), s.getStatus(), s.getRemarks()));
        }
        return out;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}

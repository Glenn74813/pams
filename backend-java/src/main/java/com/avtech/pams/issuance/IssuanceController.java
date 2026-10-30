package com.avtech.pams.issuance;

import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.department.DepartmentRepository;
import com.avtech.pams.inventory.Classification;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.issuance.IssuanceService.Decision;
import com.avtech.pams.issuance.IssuanceService.RequestInput;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
public class IssuanceController {

    private static final Set<String> STATUSES = Set.of("pending", "approved", "rejected", "partially_fulfilled", "fulfilled");

    private final IssuanceService service;
    private final InventoryRequestRepository requests;
    private final ItemRepository items;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final CurrentUser current;
    private final com.avtech.pams.inventory.StockLedger ledger;

    public IssuanceController(IssuanceService service, InventoryRequestRepository requests, ItemRepository items,
                              DepartmentRepository departments, UserRepository users, CurrentUser current,
                              com.avtech.pams.inventory.StockLedger ledger) {
        this.ledger = ledger;
        this.service = service;
        this.requests = requests;
        this.items = items;
        this.departments = departments;
        this.users = users;
        this.current = current;
    }

    // ───────────────────────── views ─────────────────────────

    public record Ref(Long id, String name) {
    }

    public record LineView(Long id, Long itemId, String code, String name, String unitOfMeasure, Classification classification,
                           int currentStock, int usableStock, BigDecimal unitCost, int quantity, int approvedQuantity, String status) {
    }

    public record RequestView(Long id, String requestNumber, String status, String priority, String purpose, LocalDate requestDate,
                              Ref department, Ref requester, String custodianName, String risNumber, LocalDate risDate,
                              String icsNumber, LocalDate icsDate, String parNumber, LocalDate parDate, String remarks,
                              List<LineView> items, int totalRequested, int totalIssued,
                              boolean canEdit, boolean canDelete, boolean canProcess, boolean canReject,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    // ───────────────────────── endpoints ─────────────────────────

    /** Everyone with view_requests sees all requests; requesters see only their own. */
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<RequestView> index(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(name = "start_date", required = false) LocalDate from,
                                           @RequestParam(name = "end_date", required = false) LocalDate to,
                                           @RequestParam(required = false, defaultValue = "false") boolean mine) {
        Long only = scope(mine);
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw ApiException.validation("status", "Unknown status.");
        Page<InventoryRequest> result = requests.search(only, blank(status), from, to, blank(search),
                PageRequest.of(Math.max(0, page - 1), 20, Sort.by(Sort.Order.desc("requestDate"), Sort.Order.desc("id"))));
        return PageResponse.of(result, views(result.getContent())::get);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public RequestView show(@PathVariable Long id) {
        InventoryRequest r = requests.findWithItemsById(id).orElseThrow(() -> ApiException.notFound("Request"));
        if (!current.any("view_requests") && !current.user().getId().equals(r.getRequestedBy())) {
            throw ApiException.forbidden("You can only view your own requests.");
        }
        return views(List.of(r)).get(r);
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_requests')")
    public ResponseEntity<RequestView> store(@RequestBody RequestInput body) {
        InventoryRequest r = service.create(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(show(r.getId()));
    }

    @PutMapping("/{id}")
    public RequestView update(@PathVariable Long id, @RequestBody RequestInput body) {
        service.update(id, body);
        return show(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> destroy(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "Request deleted.");
    }

    public record ProcessInput(List<Decision> items) {
    }

    @PutMapping("/{id}/approve-items")
    @PreAuthorize("@perm.any('approve_requests')")
    public Map<String, Object> approveItems(@PathVariable Long id, @RequestBody ProcessInput body) {
        if (body.items() == null || body.items().isEmpty()) throw ApiException.validation("items", "Give a decision for at least one line.");
        var outcome = service.process(id, body.items());
        return Map.of("message", "Request processed.", "data", show(outcome.request().getId()), "warnings", outcome.warnings());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("@perm.any('approve_requests')")
    public Map<String, Object> approve(@PathVariable Long id) {
        var outcome = service.approveAll(id);
        return Map.of("message", "Request processed.", "data", show(outcome.request().getId()), "warnings", outcome.warnings());
    }

    public record RejectInput(String reason) {
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("@perm.any('reject_requests', 'approve_requests')")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody RejectInput body) {
        service.reject(id, body.reason());
        return Map.of("message", "Request rejected.", "data", show(id));
    }

    // ───────────────────────── helpers ─────────────────────────

    /** null = all requests; otherwise only this requester's. */
    private Long scope(boolean mine) {
        User me = current.user();
        if (!mine && current.any("view_requests", "view_reports", "generate_reports")) return null;
        if (mine || current.any("view_own_requests", "create_requests")) return me.getId();
        throw ApiException.forbidden("You do not have access to requests.");
    }

    private Map<InventoryRequest, RequestView> views(Collection<InventoryRequest> list) {
        Set<Long> itemIds = list.stream().flatMap(r -> r.getItems().stream()).map(RequestItem::getItemId).collect(Collectors.toSet());
        Map<Long, Item> itemById = new HashMap<>();
        items.findAllById(itemIds).forEach(i -> itemById.put(i.getId(), i));
        Map<Long, String> deptNames = new HashMap<>();
        departments.findAllById(list.stream().map(InventoryRequest::getDepartmentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .forEach(d -> deptNames.put(d.getId(), d.getName()));
        Map<Long, String> userNames = new HashMap<>();
        users.findAllById(list.stream().map(InventoryRequest::getRequestedBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> userNames.put(u.getId(), u.getName()));

        User me = current.user();
        boolean editAny = current.any("edit_requests");
        boolean editOwn = current.any("edit_own_requests");
        boolean deleteOwn = current.any("delete_own_requests");
        boolean approver = current.any("approve_requests");
        boolean rejecter = current.any("approve_requests", "reject_requests");

        Map<InventoryRequest, RequestView> out = new java.util.LinkedHashMap<>();
        for (InventoryRequest r : list) {
            boolean own = me.getId().equals(r.getRequestedBy());
            boolean pending = r.isPending();
            List<LineView> lines = r.getItems().stream().map(l -> {
                Item i = itemById.get(l.getItemId());
                return new LineView(l.getId(), l.getItemId(), i == null ? null : i.getCode(), i == null ? "(deleted item)" : i.getName(),
                        i == null ? null : i.getUnitOfMeasure(), i == null ? null : i.classification(),
                        i == null ? 0 : i.getCurrentStock(), i == null || !pending ? 0 : ledger.usable(i.getId()),
                        l.getUnitCost() != null ? l.getUnitCost() : i == null ? null : i.getWeightedAverageCost(),
                        l.getQuantity(), l.getApprovedQuantity(), l.getStatus());
            }).toList();
            out.put(r, new RequestView(r.getId(), r.getRequestNumber(), r.getStatus(), r.getPriority(), r.getPurpose(), r.getRequestDate(),
                    r.getDepartmentId() == null ? null : new Ref(r.getDepartmentId(), deptNames.get(r.getDepartmentId())),
                    r.getRequestedBy() == null ? null : new Ref(r.getRequestedBy(), userNames.get(r.getRequestedBy())),
                    r.getCustodianName(), r.getRisNumber(), r.getRisDate(), r.getIcsNumber(), r.getIcsDate(),
                    r.getParNumber(), r.getParDate(), r.getRemarks(), lines,
                    lines.stream().mapToInt(LineView::quantity).sum(), lines.stream().mapToInt(LineView::approvedQuantity).sum(),
                    pending && (editAny || (own && editOwn)),
                    pending && (me.isSuperAdmin() || (own && deleteOwn)),
                    pending && approver && (!own || me.isSuperAdmin()),
                    pending && rejecter && (!own || me.isSuperAdmin()),
                    Times.toOffset(r.getCreatedAt()), Times.toOffset(r.getUpdatedAt())));
        }
        return out;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

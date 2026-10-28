package com.avtech.pams.gatepass;

import com.avtech.pams.audit.AuditService;
import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.DocumentNumbers;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.inventory.StockLedger;
import com.avtech.pams.property.Property;
import com.avtech.pams.property.PropertyRepository;
import com.avtech.pams.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gate passes for taking an asset off the premises. One open pass per asset, never for disposed
 * or retired assets, and passes are kept (not deleted) as part of the asset's history.
 */
@RestController
@RequestMapping("/api/gate-pass")
public class GatePassController {

    private static final String TYPE = "App\\Models\\GatePass";
    private static final Set<String> RELEASE = Set.of("new", "excellent", "good", "fair", "poor");
    private static final Set<String> RETURN = Set.of("good", "fair", "poor", "damaged", "lost");

    private final GatePassRepository passes;
    private final PropertyRepository properties;
    private final ItemRepository items;
    private final UserRepository users;
    private final StockLedger ledger;
    private final DocumentNumbers numbers;
    private final AuditService audit;
    private final CurrentUser current;

    public GatePassController(GatePassRepository passes, PropertyRepository properties, ItemRepository items, UserRepository users,
                              StockLedger ledger, DocumentNumbers numbers, AuditService audit, CurrentUser current) {
        this.passes = passes;
        this.properties = properties;
        this.items = items;
        this.users = users;
        this.ledger = ledger;
        this.numbers = numbers;
        this.audit = audit;
        this.current = current;
    }

    public record GatePassView(Long id, String gatePassNumber, Long propertyId, String tagNumber, String serialNumber, String itemName,
                               String holder, String receivedBy, String purpose, LocalDate dateReleased, LocalDate expectedReturnDate,
                               LocalDate actualReturnDate, String conditionOnRelease, String conditionOnReturn, String status,
                               boolean overdue, String remarks, String releasedBy, String returnedBy, String receivedBackBy,
                               boolean canEdit, boolean canReturn) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<GatePassView> index(@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String search) {
        Long only = null;
        if (!current.any("view_gate_pass")) {
            if (!current.any("create_gate_pass")) throw ApiException.forbidden("You do not have access to gate passes.");
            only = current.user().getId();
        }
        Page<GatePass> result = passes.search(only, blank(status), LocalDate.now(Times.ZONE), blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<GatePass, GatePassView> v = views(result.getContent());
        return PageResponse.of(result, v::get);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public GatePassView show(@PathVariable Long id) {
        GatePass g = passes.findById(id).filter(x -> x.getDeletedAt() == null).orElseThrow(() -> ApiException.notFound("Gate pass"));
        if (!current.any("view_gate_pass") && !current.user().getId().equals(g.getReleasedBy())) throw ApiException.forbidden("You can only view gate passes you issued.");
        return views(List.of(g)).get(g);
    }

    @GetMapping("/property/{propertyId}")
    @Transactional(readOnly = true)
    public List<GatePassView> forProperty(@PathVariable Long propertyId) {
        if (!current.any("view_gate_pass", "view_properties")) throw ApiException.forbidden("You do not have access to gate passes.");
        List<GatePass> list = passes.findByPropertyIdOrderByDateReleasedDescIdDesc(propertyId).stream().filter(g -> g.getDeletedAt() == null).toList();
        Map<GatePass, GatePassView> v = views(list);
        return list.stream().map(v::get).toList();
    }

    public record GatePassInput(Long propertyId, String receivedBy, String purpose, LocalDate dateReleased, LocalDate expectedReturnDate,
                                String conditionOnRelease, String remarks) {
    }

    @PostMapping
    @PreAuthorize("@perm.any('create_gate_pass')")
    @Transactional
    public ResponseEntity<GatePassView> create(@RequestBody GatePassInput in) {
        if (in.propertyId() == null) throw ApiException.validation("property_id", "Choose the asset.");
        Property p = properties.lockById(in.propertyId()).orElseThrow(() -> ApiException.validation("property_id", "That asset no longer exists."));
        if (Set.of(Property.DISPOSED, Property.RETIRED).contains(p.getStatus())) {
            throw ApiException.validation("property_id", "Asset " + p.getTagNumber() + " is " + p.getStatus() + " and cannot leave on a gate pass.");
        }
        if (passes.outFor(p.getId())) throw ApiException.validation("property_id", "Asset " + p.getTagNumber() + " is already out on another gate pass.");
        GatePass g = new GatePass(numbers.monthly("GP", "gate_passes.gate_pass_number"), p.getId(), current.user().getId());
        apply(g, in);
        passes.save(g);
        ledger.event(p.getItemId(), current.user().getId(), "gate_pass_created", TYPE, g.getId(),
                "Gate pass " + g.getGatePassNumber() + " for asset " + p.getTagNumber() + " — carried out by " + g.getReceivedBy());
        audit.record("created", TYPE, g.getId(), "Issued gate pass " + g.getGatePassNumber() + " for " + p.getTagNumber(), null,
                Map.of("property_id", p.getId(), "received_by", g.getReceivedBy(), "purpose", g.getPurpose()));
        return ResponseEntity.status(HttpStatus.CREATED).body(views(List.of(g)).get(g));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.any('edit_gate_pass', 'create_gate_pass')")
    @Transactional
    public GatePassView update(@PathVariable Long id, @RequestBody GatePassInput in) {
        GatePass g = passes.lockById(id).orElseThrow(() -> ApiException.notFound("Gate pass"));
        if (!g.isOut()) throw ApiException.of(HttpStatus.CONFLICT, "A returned gate pass cannot be changed.");
        apply(g, in);
        passes.flush();
        audit.record("updated", TYPE, g.getId(), "Edited gate pass " + g.getGatePassNumber(), null,
                Map.of("received_by", g.getReceivedBy(), "purpose", g.getPurpose(), "expected_return_date", String.valueOf(g.getExpectedReturnDate())));
        return views(List.of(g)).get(g);
    }

    public record ReturnInput(LocalDate actualReturnDate, String conditionOnReturn, String returnedBy, String remarks) {
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("@perm.any('edit_gate_pass', 'create_gate_pass')")
    @Transactional
    public GatePassView markReturned(@PathVariable Long id, @RequestBody ReturnInput in) {
        GatePass g = passes.lockById(id).orElseThrow(() -> ApiException.notFound("Gate pass"));
        if (!g.isOut()) throw ApiException.of(HttpStatus.CONFLICT, "This gate pass has already been returned.");
        if (in.conditionOnReturn() == null || !RETURN.contains(in.conditionOnReturn())) {
            throw ApiException.validation("condition_on_return", "Choose the condition on return.");
        }
        LocalDate date = in.actualReturnDate() == null ? LocalDate.now(Times.ZONE) : in.actualReturnDate();
        if (date.isBefore(g.getDateReleased())) throw ApiException.validation("actual_return_date", "The return date is before the release date.");
        if (date.isAfter(LocalDate.now(Times.ZONE))) throw ApiException.validation("actual_return_date", "The return date cannot be in the future.");
        g.setActualReturnDate(date);
        g.setConditionOnReturn(in.conditionOnReturn());
        g.setReturnedBy(blank(in.returnedBy()));
        if (in.remarks() != null && !in.remarks().isBlank()) g.setRemarks(in.remarks().trim());
        g.setStatus("returned");
        g.setReceivedBackBy(current.user().getId());
        // Bring the asset's recorded condition in line with what came back.
        Property p = properties.lockById(g.getPropertyId()).orElseThrow();
        if (Set.of("good", "fair", "poor").contains(in.conditionOnReturn())) p.setCondition(in.conditionOnReturn());
        else if ("damaged".equals(in.conditionOnReturn())) p.setCondition("poor");
        passes.flush();
        ledger.event(p.getItemId(), current.user().getId(), "gate_pass_returned", TYPE, g.getId(),
                "Gate pass " + g.getGatePassNumber() + " returned for asset " + p.getTagNumber() + " — condition " + in.conditionOnReturn());
        audit.record("updated", TYPE, g.getId(), "Returned gate pass " + g.getGatePassNumber(), Map.of("status", "released"),
                Map.of("status", "returned", "condition_on_return", in.conditionOnReturn()));
        return views(List.of(g)).get(g);
    }

    private void apply(GatePass g, GatePassInput in) {
        if (in.receivedBy() == null || in.receivedBy().isBlank()) throw ApiException.validation("received_by", "Name the person carrying the asset out.");
        if (in.purpose() == null || in.purpose().isBlank()) throw ApiException.validation("purpose", "State the purpose.");
        if (in.purpose().length() > 500) throw ApiException.validation("purpose", "Keep the purpose under 500 characters.");
        String cond = in.conditionOnRelease() == null || in.conditionOnRelease().isBlank() ? "good" : in.conditionOnRelease();
        if (!RELEASE.contains(cond)) throw ApiException.validation("condition_on_release", "Choose the condition on release.");
        LocalDate released = in.dateReleased() == null ? LocalDate.now(Times.ZONE) : in.dateReleased();
        if (in.expectedReturnDate() != null && in.expectedReturnDate().isBefore(released)) {
            throw ApiException.validation("expected_return_date", "The expected return date is before the release date.");
        }
        g.setReceivedBy(in.receivedBy().trim());
        g.setPurpose(in.purpose().trim());
        g.setDateReleased(released);
        g.setExpectedReturnDate(in.expectedReturnDate());
        g.setConditionOnRelease(cond);
        g.setRemarks(in.remarks() == null || in.remarks().isBlank() ? null : in.remarks().trim());
    }

    private Map<GatePass, GatePassView> views(Collection<GatePass> list) {
        Map<Long, Property> props = new HashMap<>();
        properties.findAllById(list.stream().map(GatePass::getPropertyId).collect(Collectors.toSet())).forEach(p -> props.put(p.getId(), p));
        Map<Long, String> itemNames = new HashMap<>();
        items.findAllById(props.values().stream().map(Property::getItemId).collect(Collectors.toSet())).forEach((Item i) -> itemNames.put(i.getId(), i.getName()));
        Map<Long, String> names = new HashMap<>();
        users.findAllById(Stream.concat(list.stream().flatMap(g -> Stream.of(g.getReleasedBy(), g.getReceivedBackBy())),
                        props.values().stream().map(Property::getAssignedTo)).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getId(), u.getName()));
        LocalDate today = LocalDate.now(Times.ZONE);
        boolean can = current.any("edit_gate_pass", "create_gate_pass");
        Map<GatePass, GatePassView> out = new LinkedHashMap<>();
        for (GatePass g : list) {
            Property p = props.get(g.getPropertyId());
            boolean overdue = g.isOut() && g.getExpectedReturnDate() != null && g.getExpectedReturnDate().isBefore(today);
            out.put(g, new GatePassView(g.getId(), g.getGatePassNumber(), g.getPropertyId(), p == null ? null : p.getTagNumber(),
                    p == null ? null : p.getSerialNumber(), p == null ? null : itemNames.get(p.getItemId()),
                    p == null || p.getAssignedTo() == null ? null : names.get(p.getAssignedTo()),
                    g.getReceivedBy(), g.getPurpose(), g.getDateReleased(), g.getExpectedReturnDate(), g.getActualReturnDate(),
                    g.getConditionOnRelease(), g.getConditionOnReturn(), overdue ? "overdue" : g.isOut() ? "released" : "returned",
                    overdue, g.getRemarks(), names.get(g.getReleasedBy()), g.getReturnedBy(), names.get(g.getReceivedBackBy()),
                    can && g.isOut(), can && g.isOut()));
        }
        return out;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

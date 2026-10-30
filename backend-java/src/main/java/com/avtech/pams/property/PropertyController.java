package com.avtech.pams.property;

import com.avtech.pams.auth.CurrentUser;
import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.common.Times;
import com.avtech.pams.inventory.Classification;
import com.avtech.pams.inventory.Item;
import com.avtech.pams.inventory.ItemRepository;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
public class PropertyController {

    private static final Set<String> STATUSES = Set.of("assigned", "available", "under_repair", "retired", "disposed", "lost");

    private final PropertyService service;
    private final PropertyRepository properties;
    private final PropertyTransferRepository transfers;
    private final DepreciationService depreciation;
    private final DepreciationLogRepository logs;
    private final ItemRepository items;
    private final UserRepository users;
    private final CurrentUser current;
    private final JdbcTemplate jdbc;

    public PropertyController(PropertyService service, PropertyRepository properties, PropertyTransferRepository transfers,
                              DepreciationService depreciation, DepreciationLogRepository logs, ItemRepository items,
                              UserRepository users, CurrentUser current, JdbcTemplate jdbc) {
        this.service = service;
        this.properties = properties;
        this.transfers = transfers;
        this.depreciation = depreciation;
        this.logs = logs;
        this.items = items;
        this.users = users;
        this.current = current;
        this.jdbc = jdbc;
    }

    // ───────────────────────── views ─────────────────────────

    public record Ref(Long id, String name) {
    }

    public record ItemRef(Long id, String code, String name, String unitOfMeasure, Classification classification) {
    }

    public record PropertyView(Long id, String tagNumber, String serialNumber, ItemRef item, String brand, String specifications,
                               String warranty, Integer warrantyMonths, LocalDate warrantyExpiry, Ref holder, String holderOffice,
                               LocalDate assignedDate, String accountabilityNumber, String status, String condition,
                               BigDecimal acquisitionCost, LocalDate acquisitionDate, int usefulLifeYears, BigDecimal salvageValue,
                               boolean isDepreciable, BigDecimal accumulatedDepreciation, BigDecimal bookValue, BigDecimal monthlyDepreciation,
                               boolean canEdit, boolean canTransfer, boolean canDelete) {
    }

    public record TransferView(Long id, String transferType, String referenceNumber, LocalDate transferDate, Ref from, Ref to,
                               String reason, String recordedBy, Long propertyId, String tagNumber, String itemName) {
    }

    // ───────────────────────── register & list ─────────────────────────

    @GetMapping("/properties")
    @Transactional(readOnly = true)
    public PageResponse<PropertyView> index(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String condition,
                                            @RequestParam(required = false, defaultValue = "false") boolean mine) {
        Long holder = scope(mine);
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw ApiException.validation("status", "Unknown status.");
        Page<Property> result = properties.search(holder, blank(status), blank(condition), blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        Map<Property, PropertyView> v = views(result.getContent());
        return PageResponse.of(result, v::get);
    }

    /** Assets the signed-in person is accountable for. Everyone can see their own. */
    @GetMapping("/properties/my-assets")
    @Transactional(readOnly = true)
    public Map<String, Object> myAssets() {
        List<Property> mine = properties.findByAssignedToOrderByAssignedDateDesc(current.user().getId());
        Map<Property, PropertyView> v = views(mine);
        BigDecimal total = mine.stream().map(p -> p.getAcquisitionCost() == null ? BigDecimal.ZERO : p.getAcquisitionCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("data", mine.stream().map(v::get).toList(), "summary", Map.of("total", mine.size(), "total_value", total));
    }

    @GetMapping("/properties/{id}")
    @Transactional(readOnly = true)
    public PropertyView show(@PathVariable Long id) {
        Property p = visible(id);
        return views(List.of(p)).get(p);
    }

    @PostMapping("/properties")
    @PreAuthorize("@perm.any('create_properties', 'assign_properties')")
    public ResponseEntity<Map<String, Object>> register(@RequestBody PropertyService.RegisterInput in) {
        var r = service.register(in);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", r.properties().size() + " unit(s) registered under " + r.accountabilityNumber() + ".",
                "accountability_number", r.accountabilityNumber(),
                "properties", r.properties().stream().map(p -> show(p.getId())).toList()));
    }

    @PutMapping("/properties/{id}")
    @PreAuthorize("@perm.any('edit_properties')")
    public PropertyView update(@PathVariable Long id, @RequestBody PropertyService.DetailsInput in) {
        service.update(id, in);
        return show(id);
    }

    @DeleteMapping("/properties/{id}")
    @PreAuthorize("@perm.any('delete_properties')")
    public Map<String, Object> destroy(@PathVariable Long id) {
        service.delete(id);
        return Map.of("message", "Registration undone; the unit is back in stock.");
    }

    // ───────────────────────── accountability ─────────────────────────

    @PostMapping("/properties/{id}/transfer")
    @PreAuthorize("@perm.any('transfer_properties')")
    public Map<String, Object> transfer(@PathVariable Long id, @RequestBody PropertyService.MoveInput in) {
        PropertyTransfer t = service.transfer(id, in);
        return Map.of("message", "Transferred under " + t.getReferenceNumber() + ".", "transfer", transferViews(List.of(t)).get(0), "property", show(id));
    }

    @PostMapping("/properties/{id}/return")
    @PreAuthorize("@perm.any('transfer_properties', 'assign_properties')")
    public Map<String, Object> turnIn(@PathVariable Long id, @RequestBody PropertyService.MoveInput in) {
        PropertyTransfer t = service.turnIn(id, in);
        return Map.of("message", "Return recorded under " + t.getReferenceNumber() + ".", "transfer", transferViews(List.of(t)).get(0), "property", show(id));
    }

    @PostMapping("/properties/{id}/issue")
    @PreAuthorize("@perm.any('assign_properties', 'transfer_properties')")
    public Map<String, Object> reissue(@PathVariable Long id, @RequestBody PropertyService.MoveInput in) {
        PropertyTransfer t = service.reissue(id, in);
        return Map.of("message", "Issued under " + t.getReferenceNumber() + ".", "transfer", transferViews(List.of(t)).get(0), "property", show(id));
    }

    @PostMapping("/properties/{id}/status")
    @PreAuthorize("@perm.any('edit_properties')")
    public PropertyView status(@PathVariable Long id, @RequestBody PropertyService.StatusInput in) {
        service.changeStatus(id, in);
        return show(id);
    }

    @GetMapping("/properties/{id}/transfers")
    @Transactional(readOnly = true)
    public List<TransferView> history(@PathVariable Long id) {
        visible(id);
        return transferViews(transfers.findByPropertyIdOrderByTransferDateDescIdDesc(id));
    }

    @GetMapping("/properties/transfers/all")
    @PreAuthorize("@perm.any('view_properties', 'transfer_properties', 'view_reports', 'generate_reports')")
    @Transactional(readOnly = true)
    public PageResponse<TransferView> allTransfers(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String search) {
        Page<PropertyTransfer> result = transfers.search(blank(type), blank(search), PageRequest.of(Math.max(0, page - 1), 20));
        List<TransferView> v = transferViews(result.getContent());
        Map<Long, TransferView> byId = new HashMap<>();
        v.forEach(t -> byId.put(t.id(), t));
        return PageResponse.of(result, t -> byId.get(t.getId()));
    }

    // ───────────────────────── depreciation ─────────────────────────

    @GetMapping("/properties/{id}/depreciation")
    @Transactional(readOnly = true)
    public Map<String, Object> depreciation(@PathVariable Long id) {
        Property p = visible(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("is_depreciable", p.isDepreciable());
        m.put("acquisition_cost", p.getAcquisitionCost());
        m.put("residual_value", depreciation.residual(p));
        m.put("useful_life_years", p.getUsefulLifeYears());
        m.put("monthly_depreciation", depreciation.monthly(p));
        m.put("starts", depreciation.start(p).toString());
        m.put("accumulated_depreciation", p.getAccumulatedDepreciation());
        m.put("book_value", depreciation.bookValue(p));
        m.put("schedule", p.isDepreciable() ? depreciation.schedule(p) : List.of());
        m.put("postings", logs.findByPropertyIdOrderByDepreciationDateAscIdAsc(id).stream()
                .map(l -> Map.of("period", String.valueOf(l.getPeriod()), "amount", l.getAmount(), "accumulated", l.getAccumulated(), "book_value", l.getBookValue()))
                .toList());
        return m;
    }

    public record RunInput(String period) {
    }

    /** Posts monthly depreciation for every depreciable asset through the given month (default: last month). */
    @PostMapping("/depreciation/run")
    @PreAuthorize("@perm.any('edit_properties')")
    @Transactional
    public Map<String, Object> run(@RequestBody(required = false) RunInput in) {
        YearMonth through;
        try {
            through = in == null || in.period() == null || in.period().isBlank()
                    ? YearMonth.now(Times.ZONE).minusMonths(1) : YearMonth.parse(in.period());
        } catch (DateTimeParseException e) {
            throw ApiException.validation("period", "Give the period as YYYY-MM.");
        }
        if (through.isAfter(YearMonth.now(Times.ZONE))) throw ApiException.validation("period", "You cannot post depreciation for a future month.");
        int assets = 0;
        int months = 0;
        BigDecimal before = BigDecimal.ZERO;
        BigDecimal after = BigDecimal.ZERO;
        for (Property listed : properties.depreciableAssets()) {
            Property p = properties.lockById(listed.getId()).orElseThrow();
            before = before.add(p.getAccumulatedDepreciation());
            int n = depreciation.postThrough(p, through);
            after = after.add(p.getAccumulatedDepreciation());
            if (n > 0) {
                assets++;
                months += n;
            }
        }
        return Map.of("message", months == 0 ? "Nothing new to post through " + through + "."
                        : "Posted " + months + " month(s) of depreciation for " + assets + " asset(s) through " + through + ".",
                "period", through.toString(), "assets", assets, "months_posted", months,
                "amount_posted", after.subtract(before).setScale(2, RoundingMode.HALF_UP));
    }

    // ───────────────────────── helpers ─────────────────────────

    /** null = all; otherwise only this person's assets. */
    private Long scope(boolean mine) {
        // Report viewers pick assets for the COA forms (Property Card, RLSDDP) without managing them.
        if (!mine && current.any("view_properties", "view_reports", "generate_reports")) return null;
        return current.user().getId();
    }

    private Property visible(Long id) {
        Property p = properties.findById(id).orElseThrow(() -> ApiException.notFound("Asset"));
        if (!current.any("view_properties") && !current.user().getId().equals(p.getAssignedTo())) {
            throw ApiException.forbidden("You can only view assets you are accountable for.");
        }
        return p;
    }

    private Map<Property, PropertyView> views(Collection<Property> list) {
        Map<Long, Item> byItem = new HashMap<>();
        items.findAllById(list.stream().map(Property::getItemId).collect(Collectors.toSet())).forEach(i -> byItem.put(i.getId(), i));
        Map<Long, User> byUser = new HashMap<>();
        users.findAllById(list.stream().map(Property::getAssignedTo).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> byUser.put(u.getId(), u));
        Map<Long, String> offices = new HashMap<>();
        if (!byUser.isEmpty()) {
            String in = byUser.keySet().stream().map(x -> "?").collect(Collectors.joining(","));
            jdbc.query("select u.id, d.name from users u join departments d on d.id = u.department_id where u.id in (" + in + ")",
                    rs -> { offices.put(rs.getLong(1), rs.getString(2)); }, byUser.keySet().toArray());
        }
        boolean edit = current.any("edit_properties");
        boolean move = current.any("transfer_properties", "assign_properties");
        boolean del = current.any("delete_properties");
        Map<Property, PropertyView> out = new LinkedHashMap<>();
        for (Property p : list) {
            Item i = byItem.get(p.getItemId());
            User h = p.getAssignedTo() == null ? null : byUser.get(p.getAssignedTo());
            Integer wm = p.getWarrantyMonths();
            LocalDate base = p.getAcquisitionDate() != null ? p.getAcquisitionDate() : p.getAssignedDate();
            out.put(p, new PropertyView(p.getId(), p.getTagNumber(), p.getSerialNumber(),
                    i == null ? null : new ItemRef(i.getId(), i.getCode(), i.getName(), i.getUnitOfMeasure(), i.classification()),
                    p.getBrand(), p.getSpecifications(), p.getWarranty(), wm,
                    wm == null || wm <= 0 || base == null ? null : base.plusMonths(wm),
                    h == null ? null : new Ref(h.getId(), h.getName()), h == null ? null : offices.get(h.getId()),
                    p.getAssignedDate(), p.getAccountabilityNumber(), p.getStatus(), p.getCondition(),
                    p.getAcquisitionCost(), p.getAcquisitionDate(), p.getUsefulLifeYears(), p.getSalvageValue(), p.isDepreciable(),
                    p.getAccumulatedDepreciation(), depreciation.bookValue(p), p.isDepreciable() ? depreciation.monthly(p) : BigDecimal.ZERO,
                    edit && !p.isGone(), move && !p.isGone(), del));
        }
        return out;
    }

    private List<TransferView> transferViews(List<PropertyTransfer> list) {
        Map<Long, String> names = new HashMap<>();
        users.findAllById(list.stream().flatMap(t -> Stream.of(t.getFromUser(), t.getToUser(), t.getApprovedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet())).forEach(u -> names.put(u.getId(), u.getName()));
        Map<Long, Property> props = new HashMap<>();
        properties.findAllById(list.stream().map(PropertyTransfer::getPropertyId).collect(Collectors.toSet())).forEach(p -> props.put(p.getId(), p));
        Map<Long, String> itemNames = new HashMap<>();
        items.findAllById(props.values().stream().map(Property::getItemId).collect(Collectors.toSet())).forEach(i -> itemNames.put(i.getId(), i.getName()));
        return list.stream().map(t -> {
            Property p = props.get(t.getPropertyId());
            return new TransferView(t.getId(), t.getTransferType(), t.getReferenceNumber(), t.getTransferDate(),
                    t.getFromUser() == null ? null : new Ref(t.getFromUser(), names.get(t.getFromUser())),
                    t.getToUser() == null ? null : new Ref(t.getToUser(), names.get(t.getToUser())),
                    t.getTransferReason(), names.get(t.getApprovedBy()), t.getPropertyId(),
                    p == null ? null : p.getTagNumber(), p == null ? null : itemNames.get(p.getItemId()));
        }).toList();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

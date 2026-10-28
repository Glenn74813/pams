package com.avtech.pams.procurement;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.setting.SettingService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reference lists (RA 12009 modes, categories, fund sources) and line validation shared by PPMPs, APPs and PRs. */
@Component
public class ProcurementRules {

    public static final List<String> MODES = List.of("Competitive Bidding", "Competitive Dialogue", "Limited Source Bidding",
            "Direct Contracting", "Direct Acquisition", "Repeat Order", "Small Value Procurement", "Negotiated Procurement",
            "Shopping", "Direct Sales", "Unsolicited Offer");
    public static final List<String> CATEGORIES = List.of("Goods", "Infrastructure Projects", "Consulting Services");
    public static final List<String> FUND_SOURCES = List.of("General Appropriations Act (GAA)", "Continuing Appropriations",
            "Special Purpose Funds (SPFs)", "Automatic Appropriations", "Revolving Funds", "Trust Receipts / Trust Funds",
            "Internally Generated Income (IGI)", "Foreign-Assisted Funds", "Grants and Donations", "Corporate Funds",
            "Local Government Funds");
    public static final List<String> QUARTERS = List.of("Q1", "Q2", "Q3", "Q4");
    /** RA 12009 IRR ceilings, overridable per method through the procurement_method_ceilings setting. */
    private static final Map<String, BigDecimal> CEILINGS = Map.of(
            "Shopping", new BigDecimal("2000000"), "Small Value Procurement", new BigDecimal("2000000"));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SettingService settings;

    public ProcurementRules(SettingService settings) {
        this.settings = settings;
    }

    /** A line as the client sends it, for a PPMP or a purchase request. */
    public record LineInput(String generalDescription, String specifications, String code, String unit, Integer quantity,
                            BigDecimal estimatedUnitCost, String procurementMode, String procurementCategory, Boolean isSustainable,
                            String schedule, String fundSource, String remarks, Long appItemId) {
    }

    /** A checked, trimmed line. */
    public record Line(String description, String specifications, String code, String unit, int quantity, BigDecimal unitCost,
                       BigDecimal total, String mode, String category, boolean sustainable, String schedule, String fundSource,
                       String remarks, Long appItemId) {
    }

    /**
     * Validates the lines. A PPMP line must name its mode; a PR line may leave mode, category,
     * fund source and specifications to be filled from the APP line it draws on.
     */
    public List<Line> lines(List<LineInput> in, boolean modeRequired) {
        if (in == null || in.isEmpty()) throw ApiException.validation("items", "Add at least one line.");
        if (in.size() > 200) throw ApiException.validation("items", "A document can have at most 200 lines.");
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            LineInput l = in.get(i);
            String f = "items." + i + ".";
            if (l == null) throw ApiException.validation("items", "Line " + (i + 1) + " is empty.");
            String desc = text(l.generalDescription());
            if (desc == null) throw ApiException.validation(f + "general_description", "Line " + (i + 1) + " needs a description.");
            if (desc.length() > 255) throw ApiException.validation(f + "general_description", "Line " + (i + 1) + ": keep the description under 255 characters.");
            if (l.quantity() == null || l.quantity() < 1) throw ApiException.validation(f + "quantity", "Line " + (i + 1) + ": quantity must be at least 1.");
            if (l.quantity() > 10_000_000) throw ApiException.validation(f + "quantity", "Line " + (i + 1) + ": quantity is too large.");
            BigDecimal cost = l.estimatedUnitCost();
            if (cost == null || cost.signum() < 0) throw ApiException.validation(f + "estimated_unit_cost", "Line " + (i + 1) + ": enter the estimated unit cost.");
            if (cost.scale() > 2 || cost.compareTo(new BigDecimal("9999999999")) > 0) {
                throw ApiException.validation(f + "estimated_unit_cost", "Line " + (i + 1) + ": unit cost must be a peso amount with at most 2 decimals.");
            }
            String mode = text(l.procurementMode());
            if (mode == null && modeRequired) throw ApiException.validation(f + "procurement_mode", "Line " + (i + 1) + ": choose the mode of procurement.");
            if (mode != null && !MODES.contains(mode)) throw ApiException.validation(f + "procurement_mode", "Line " + (i + 1) + ": unknown mode of procurement.");
            String category = text(l.procurementCategory());
            if (category != null && !CATEGORIES.contains(category)) throw ApiException.validation(f + "procurement_category", "Line " + (i + 1) + ": unknown procurement category.");
            String fund = text(l.fundSource());
            if (fund != null && !FUND_SOURCES.contains(fund)) throw ApiException.validation(f + "fund_source", "Line " + (i + 1) + ": unknown source of funds.");
            String schedule = text(l.schedule());
            if (schedule != null && !QUARTERS.contains(schedule)) throw ApiException.validation(f + "schedule", "Line " + (i + 1) + ": schedule is a quarter, Q1 to Q4.");
            String unit = text(l.unit());
            if (unit != null && unit.length() > 50) throw ApiException.validation(f + "unit", "Line " + (i + 1) + ": unit is too long.");
            String code = text(l.code());
            if (code != null && code.length() > 100) throw ApiException.validation(f + "code", "Line " + (i + 1) + ": accounting code is too long.");
            BigDecimal total = cost.multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP);
            out.add(new Line(desc, text(l.specifications()), code, unit, l.quantity(), cost.setScale(2, RoundingMode.HALF_UP), total,
                    mode, category, Boolean.TRUE.equals(l.isSustainable()), schedule, fund, text(l.remarks()), l.appItemId()));
        }
        return out;
    }

    /** The ceiling for a method, or null when it has none. */
    public BigDecimal ceiling(String mode) {
        String raw = settings.get("procurement_method_ceilings");
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode n = JSON.readTree(raw);
                if (n.isObject() && n.has(mode)) {
                    JsonNode v = n.get(mode);
                    return v.isNull() || v.asString().isBlank() ? null : new BigDecimal(v.asString());
                }
            } catch (RuntimeException ignored) {
                // A malformed setting falls back to the statutory defaults.
            }
        }
        return CEILINGS.get(mode);
    }

    /** Rejects a line whose amount is over its method's ceiling. */
    public void checkCeiling(String field, String description, String mode, BigDecimal amount) {
        if (mode == null) return;
        BigDecimal cap = ceiling(mode);
        if (cap != null && amount.compareTo(cap) > 0) {
            throw ApiException.validation(field, "\"" + description + "\" is planned as " + mode + " (₱" + peso(amount) + "), which exceeds the ₱"
                    + peso(cap) + " ceiling for that method (RA 12009 IRR). Use Competitive Bidding or a method appropriate to the amount.");
        }
    }

    public static String peso(BigDecimal v) {
        return new DecimalFormat("#,##0.00").format(v);
    }

    static String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.avtech.pams.procurement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which supplier documents each mode of procurement asks for (GPPB Circular 06-2026, Annex "A" of Resolution
 * 10-2026). The list is exclusive (3.5): nothing else may be required. Modes outside the circular (3.3, 3.4) have
 * their own arrangements, so PAMS asks for no documents on them.
 */
public final class EligibilityRules {

    public static final String PLATINUM = "PhilGEPS Certificate of Registration (Platinum)";
    public static final String ORG_ID = "PhilGEPS Organization ID";
    /** Recorded before the circular; it shows registration but not Platinum membership. */
    public static final String REGISTRATION = "PhilGEPS Registration";
    public static final String MAYOR = "Mayor's Permit";
    public static final String BUSINESS_PERMIT = "Business Registration Permit";
    public static final String RENEWAL = "Mayor's Permit renewal receipt";
    public static final String BIR = "BIR Registration";
    public static final String LICENSE = "Professional License";
    public static final String CV = "Curriculum Vitae";
    public static final String NFCC = "NFCC Computation";
    public static final String LOC = "Committed Line of Credit";
    public static final String PCAB = "PCAB License";
    public static final String TAX_RETURN = "Income or Business Tax Return";
    public static final String OSS = "Omnibus Sworn Statement";

    public static final String BUSINESS = "business";
    public static final String GOVERNMENT = "government";
    public static final String INDIVIDUAL = "individual";
    public static final String ARTIST = "artist";
    public static final Map<String, String> KINDS = new LinkedHashMap<>();

    /** The stage at which a covered RFQ takes the documents (4.2). */
    public static final Map<String, String> STAGES = new LinkedHashMap<>();

    /** The modes and grounds of the circular's matrix, for showing a supplier's readiness: label → {mode, ground}. */
    public static final Map<String, String[]> MATRIX = new LinkedHashMap<>();

    static {
        KINDS.put(BUSINESS, "Business");
        KINDS.put(GOVERNMENT, "Government agency");
        KINDS.put(INDIVIDUAL, "Individual");
        KINDS.put(ARTIST, "Artist not engaged in business");
        STAGES.put("offer", "With the offer");
        STAGES.put("evaluation", "During evaluation");
        STAGES.put("before_noa", "Before the Notice of Award");
        MATRIX.put("Competitive selection", new String[]{"Competitive Bidding", null});
        MATRIX.put("Direct Contracting", new String[]{"Direct Contracting", null});
        MATRIX.put("Small Value Procurement", new String[]{ProcurementRules.SVP, null});
        MATRIX.put("Negotiated: two failed biddings", new String[]{ProcurementRules.NEGOTIATED, "two_failed_biddings"});
        MATRIX.put("Negotiated: emergency", new String[]{ProcurementRules.NEGOTIATED, "emergency"});
        MATRIX.put("Negotiated: take-over of contracts", new String[]{ProcurementRules.NEGOTIATED, "take_over"});
        MATRIX.put("Negotiated: adjacent or contiguous", new String[]{ProcurementRules.NEGOTIATED, "adjacent"});
        MATRIX.put("Negotiated: scientific, artistic, exclusive technology, media", new String[]{ProcurementRules.NEGOTIATED, "scientific_artistic"});
        MATRIX.put("Negotiated: highly technical consultants", new String[]{ProcurementRules.NEGOTIATED, "highly_technical"});
        MATRIX.put("Negotiated: lease of real property or venue", new String[]{ProcurementRules.NEGOTIATED, "lease"});
    }

    private static final Set<String> COMPETITIVE = Set.of("Competitive Bidding", "Limited Source Bidding", "Competitive Dialogue", ProcurementRules.UNSOLICITED);
    private static final Set<String> COVERED_GROUNDS = Set.of("two_failed_biddings", "emergency", "take_over", "adjacent", "scientific_artistic", "highly_technical", "lease");
    private static final BigDecimal P50K = new BigDecimal("50000");
    private static final BigDecimal P500K = new BigDecimal("500000");
    private static final int INFRASTRUCTURE = 1;
    private static final int CONSULTING = 2;

    private EligibilityRules() {
    }

    /** One requirement: its name, and the documents on file that satisfy it. */
    public record Requirement(String label, List<String> accepted) {
    }

    public static boolean competitive(String mode) {
        return COMPETITIVE.contains(mode);
    }

    /** True when the circular lists documents for this mode, so the RFQ must say when they are submitted. */
    public static boolean covered(String mode, String ground) {
        return "Direct Contracting".equals(mode) || ProcurementRules.SVP.equals(mode)
                || (ProcurementRules.NEGOTIATED.equals(mode) && COVERED_GROUNDS.contains(ground));
    }

    /**
     * The documents for an award. categoryRank is 0 goods, 1 infrastructure, 2 consulting. revised is the BAC's finding
     * after the mandatory review of two failed biddings. The bid-by-bid items of Section 5.2 (ongoing contracts, SLCC,
     * bid security and so on) are checked on the bid, so competitive selection lists only what is kept on file.
     */
    public static List<Requirement> required(String mode, String ground, boolean revised, int categoryRank, BigDecimal abc, String kind) {
        List<Requirement> out = new ArrayList<>();
        if (competitive(mode)) {
            out.add(platinum());
            return out;
        }
        if (!covered(mode, ground)) return out;
        boolean infra = categoryRank == INFRASTRUCTURE;
        boolean consulting = categoryRank == CONSULTING;
        BigDecimal amount = abc == null ? BigDecimal.ZERO : abc;
        boolean over50k = amount.compareTo(P50K) > 0;
        boolean over500k = amount.compareTo(P500K) > 0;
        boolean individual = INDIVIDUAL.equals(kind);
        boolean government = GOVERNMENT.equals(kind);
        String key = ProcurementRules.NEGOTIATED.equals(mode) ? ground : mode;
        switch (key) {
            case "Direct Contracting" -> {
                out.add(philgeps());
                out.add(mayor(false));
                if (over500k) out.add(taxReturn());
            }
            case ProcurementRules.SVP -> {
                out.add(philgeps());
                out.add(mayor(false));
                if (consulting) out.add(license());
                if (infra) out.add(pcab());
                if (over500k) out.add(taxReturn());
                if (over50k) out.add(oss());
            }
            case "two_failed_biddings" -> {
                // When the BAC revises the requirements, it sets them for this procurement (IRR 35.1.1 a).
                if (!revised) out.add(platinum());
            }
            case "emergency" -> {
                out.add(mayor(false));
                if (over500k && infra) {
                    out.add(nfcc());
                    out.add(pcab());
                }
                if (over500k) out.add(taxReturn());
                if (over50k) out.add(oss());
            }
            case "take_over" -> {
                out.add(philgeps());
                out.add(mayor(false));
                if (consulting) out.add(license());
                if (infra) {
                    out.add(nfcc());
                    out.add(pcab());
                }
            }
            case "adjacent" -> {
                if (infra) {
                    out.add(nfcc());
                    out.add(pcab());
                }
            }
            case "scientific_artistic" -> {
                if (ARTIST.equals(kind)) break;
                out.add(philgeps());
                out.add(mayor(individual));
                if (consulting) out.add(license());
                if (over500k) out.add(taxReturn());
            }
            case "highly_technical" -> {
                out.add(philgeps());
                out.add(mayor(individual));
                out.add(license());
            }
            case "lease" -> {
                if (government) break;
                out.add(philgeps());
                out.add(mayor(false));
                out.add(taxReturn());
            }
            default -> {
            }
        }
        return out;
    }

    private static Requirement platinum() {
        return new Requirement("PhilGEPS Certificate of Registration (Platinum)", List.of(PLATINUM));
    }

    /** 4.3.2 (b): a Platinum certificate may stand in for the Organization ID or the permit. */
    private static Requirement philgeps() {
        return new Requirement("PhilGEPS Organization ID", List.of(ORG_ID, REGISTRATION, PLATINUM));
    }

    /** 4.3.2 (a) accepts an expired permit with the receipt of its renewal; 4.5 lets an individual show BIR registration instead. */
    private static Requirement mayor(boolean birInstead) {
        List<String> accepted = new ArrayList<>(List.of(MAYOR, BUSINESS_PERMIT, RENEWAL, PLATINUM));
        if (birInstead) accepted.add(BIR);
        return new Requirement("Mayor's or Business Permit", accepted);
    }

    private static Requirement license() {
        return new Requirement("Professional License or CV", List.of(LICENSE, CV));
    }

    private static Requirement nfcc() {
        return new Requirement("NFCC or committed Line of Credit", List.of(NFCC, LOC));
    }

    private static Requirement pcab() {
        return new Requirement(PCAB, List.of(PCAB));
    }

    private static Requirement taxReturn() {
        return new Requirement(TAX_RETURN, List.of(TAX_RETURN));
    }

    private static Requirement oss() {
        return new Requirement(OSS, List.of(OSS));
    }
}

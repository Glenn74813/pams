package com.avtech.pams.report.outgoing;

import com.avtech.pams.report.coa.Signatories;
import com.avtech.pams.report.coa.Xlsx;
import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import com.avtech.pams.setting.SettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the documents PAMS sends out (RFQ, PO, notices, contracts, BAC papers) share: the agency letterhead with its
 * document logos, who signs, and the signature blocks. A block carries only the signatories the form requires; the
 * signature image goes on only once that person has acted on the record (approved, issued, signed), so an unsigned
 * draft never looks signed. The supplier's conforme is always left for a wet signature.
 */
@Component
public class OutgoingDocs {

    static final String FONT = "Arial";
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    /** Who signs a block. {@code name} null: nobody is assigned yet, and a blank line is printed. */
    public record Signer(String name, String designation, byte[] image, String imageExt) {
        Signer as(String designation) {
            return new Signer(name, designation, image, imageExt);
        }
    }

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final Signatories signatories;
    private final Path root;

    public OutgoingDocs(JdbcTemplate jdbc, SettingService settings, Signatories signatories, @Value("${pams.storage.public-dir}") String publicDir) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.signatories = signatories;
        this.root = Path.of(publicDir).toAbsolutePath().normalize();
    }

    // ───────────────────────── signatories ─────────────────────────

    /** A COA/BAC signatory role from Settings → Signatories. */
    public Signer role(String key, String designation) {
        Signatories.Signatory s = signatories.resolve(key);
        return new Signer(s.name(), designation, s.image(), s.imageExt());
    }

    /** The Head of the Procuring Entity: the Settings signatory, else the agency head named in the agency profile. */
    public Signer hope() {
        Signer s = role("head_of_agency", "Head of the Procuring Entity");
        if (s.name() != null) return s;
        String name = settings.get("agency_head_name", "");
        String title = settings.get("agency_head_title", "");
        return new Signer(blank(name) ? null : name, blank(title) ? "Head of the Procuring Entity" : title, null, null);
    }

    /** The BAC Chairperson: the Settings signatory, else the active BAC member designated chairperson. */
    public Signer bacChair() {
        Signer s = role("bac_chairperson", "BAC Chairperson");
        if (s.name() != null) return s;
        return bac().stream().filter(m -> "BAC Chairperson".equals(m.designation())).findFirst().orElse(s);
    }

    /** A user with their uploaded signature, e.g. the official who approved a PO. */
    public Signer user(Long id, String designation) {
        if (id == null) return new Signer(null, designation, null, null);
        List<Map<String, Object>> rows = jdbc.queryForList("select name, signature_path from users where id = ?", id);
        if (rows.isEmpty()) return new Signer(null, designation, null, null);
        byte[][] img = image((String) rows.get(0).get("signature_path"));
        return new Signer((String) rows.get(0).get("name"), designation, img[0], img[1] == null ? null : new String(img[1]));
    }

    /** The BAC members who sign BAC papers: chair, vice chair, members and provisional members (not the TWG or Secretariat). */
    public List<Signer> bac() {
        List<Signer> out = new ArrayList<>();
        jdbc.query("""
                select m.full_name, m.designation, u.signature_path from bac_members m left join users u on u.id = m.user_id
                where m.is_active = ? and m.designation in ('chairperson', 'vice_chairperson', 'member', 'provisional_member')
                order by case m.designation when 'chairperson' then 1 when 'vice_chairperson' then 2 when 'member' then 3 else 4 end, m.sequence, m.id""",
                rs -> {
                    byte[][] img = image(rs.getString(3));
                    String d = switch (rs.getString(2)) {
                        case "chairperson" -> "BAC Chairperson";
                        case "vice_chairperson" -> "BAC Vice Chairperson";
                        case "provisional_member" -> "BAC Provisional Member";
                        default -> "BAC Member";
                    };
                    out.add(new Signer(rs.getString(1), d, img[0], img[1] == null ? null : new String(img[1])));
                }, true);
        return out;
    }

    /** The image bytes and extension of a file under the public storage dir, or nulls. */
    private byte[][] image(String relative) {
        if (blank(relative)) return new byte[2][];
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return new byte[2][];
        try {
            return new byte[][]{Files.readAllBytes(file), (relative.toLowerCase().endsWith(".png") ? "png" : "jpeg").getBytes()};
        } catch (IOException e) {
            return new byte[2][];
        }
    }

    // ───────────────────────── layout ─────────────────────────

    /** A Letter, portrait sheet with the given column widths (A, B, …). */
    public Xlsx sheet(String title, double... widths) {
        Xlsx x = new Xlsx(title, FONT, 10);
        x.page().paper(Xlsx.LETTER).portrait().margins(0.6, 0.6, 0.6, 0.6).fitToWidth(1).fitToHeight(0).horizontalCentered();
        for (int i = 0; i < widths.length; i++) x.width(Xlsx.col(i + 1), widths[i]);
        return x;
    }

    /**
     * The letterhead across A..{@code last}: the document logos on top (left, centre, right), "Republic of the
     * Philippines", the agency name and address. Returns the next free row.
     */
    public int letterhead(Xlsx x, String last) {
        String mid = Xlsx.col((Xlsx.colIndex(last) + 1) / 2);
        jdbc.query("select path, position from document_logos where position like 'top-%' order by position, sequence, id", rs -> {
            byte[][] img = image(rs.getString(1));
            if (img[0] == null) return;
            String cell = switch (rs.getString(2)) {
                case "top-left" -> "A1";
                case "top-right" -> last + "1";
                default -> mid + "1";
            };
            x.image(cell, img[0], new String(img[1]), 64, 2, 2);
        });
        for (int r = 1; r <= 3; r++) x.merge("A" + r + ":" + last + r);
        x.set("A1", "Republic of the Philippines");
        x.style("A1").size(9).center();
        x.set("A2", or(settings.get("agency_name", ""), "[Name of the Procuring Entity]"));
        x.style("A2").bold().size(13).center();
        x.set("A3", or(settings.get("agency_address", ""), ""));
        x.style("A3").size(9).center();
        x.height(1, 18).height(2, 22).height(3, 16);
        x.style("A4:" + last + "4").border(Edge.TOP, Line.MEDIUM);
        return 5;
    }

    /** The document title, centred and bold, with an optional form reference (e.g. "Appendix 61") above it. */
    public int title(Xlsx x, int row, String last, String reference, String title) {
        if (reference != null) {
            x.merge("A" + row + ":" + last + row);
            x.set("A" + row, reference);
            x.style("A" + row).italic().size(8).right();
            row++;
        }
        x.merge("A" + row + ":" + last + row);
        x.set("A" + row, title);
        x.style("A" + row).bold().size(14).center();
        x.height(row, 22);
        return row + 2;
    }

    /** A paragraph merged across {@code from..to}, wrapped, with a row height for its length. Returns the next row. */
    public int para(Xlsx x, int row, String from, String to, String text, int charsPerLine) {
        x.merge(from + row + ":" + to + row);
        x.set(from + row, text);
        x.style(from + row).wrap().valign(Xlsx.V.TOP);
        x.height(row, Math.max(1, lines(text, charsPerLine)) * 13.0 + 3);
        return row + 1;
    }

    private static int lines(String text, int perLine) {
        int n = 0;
        for (String p : text.split("\n", -1)) n += Math.max(1, (int) Math.ceil(p.length() / (double) perLine));
        return n;
    }

    /** "Label: value" on one row, the value underlined. */
    public void field(Xlsx x, String labelCell, String label, String valueRange, String value) {
        x.set(labelCell, label);
        x.style(labelCell).size(9);
        String first = valueRange.contains(":") ? valueRange.substring(0, valueRange.indexOf(':')) : valueRange;
        if (valueRange.contains(":")) x.merge(valueRange);
        x.set(first, value == null ? "" : value);
        x.style(valueRange).bold().border(Edge.BOTTOM, Line.THIN);
    }

    /**
     * A signature block across {@code from..to}: the caption ("Very truly yours:"), the signature image when
     * {@code signed}, the printed name over a line, and the designation. Returns the next free row.
     */
    public int sign(Xlsx x, int row, String from, String to, String caption, Signer s, boolean signed) {
        keep(x, from, row, row + (caption != null ? 3 : 2));
        if (caption != null) {
            x.merge(from + row + ":" + to + row);
            x.set(from + row, caption);
            x.style(from + row).size(9);
            row++;
        }
        x.height(row, 34);
        if (signed && s.image() != null) {
            int a = Xlsx.colIndex(from), b = Xlsx.colIndex(to);
            x.image(Xlsx.col((a + b) / 2) + row, s.image(), s.imageExt(), 40, 0, 2);
        }
        row++;
        x.merge(from + row + ":" + to + row);
        x.set(from + row, s.name() == null ? "" : s.name().toUpperCase(Locale.ROOT));
        x.style(from + row + ":" + to + row).bold().center().border(Edge.BOTTOM, Line.THIN);
        row++;
        x.merge(from + row + ":" + to + row);
        x.set(from + row, s.designation());
        x.style(from + row).size(9).center().wrap();
        if (s.designation() != null && s.designation().length() > 45) x.height(row, 24);
        return row + 1;
    }

    /** Keeps rows {@code from..to} on one PDF page: a vertical merge in a column right of every form's print area. */
    public static void keep(Xlsx x, String column, int from, int to) {
        // One marker column per block column, so blocks side by side never overlap.
        String marker = Xlsx.col(60 + Xlsx.colIndex(column));
        if (to > from) x.merge(marker + from + ":" + marker + to);
    }

    /** The supplier's conforme: a blank line for the signature over the printed name, and the date. */
    public int conforme(Xlsx x, int row, String from, String to, String supplier) {
        return supplier(x, row, from, to, "Conforme:", supplier);
    }

    /** The supplier's signature under a caption ("Conforme:", "Received by:"), left for a wet signature. */
    public int supplier(Xlsx x, int row, String from, String to, String caption, String supplier) {
        keep(x, from, row, row + 4);
        x.merge(from + row + ":" + to + row);
        x.set(from + row, caption);
        x.style(from + row).size(9);
        row++;
        x.height(row, 34);
        row++;
        x.merge(from + row + ":" + to + row);
        x.set(from + row, blank(supplier) ? "" : supplier.toUpperCase(Locale.ROOT));
        x.style(from + row + ":" + to + row).bold().center().border(Edge.BOTTOM, Line.THIN);
        row++;
        x.merge(from + row + ":" + to + row);
        x.set(from + row, "Signature over Printed Name of Supplier's Authorized Representative");
        x.style(from + row).size(8).center().wrap();
        x.height(row, 24);
        row++;
        x.merge(from + row + ":" + to + row);
        x.set(from + row, "Date: ____________________");
        x.style(from + row).size(9).center();
        return row + 1;
    }

    // ───────────────────────── values ─────────────────────────

    public String setting(String key) {
        return settings.get(key, "");
    }

    public static String peso(Object v) {
        BigDecimal n = num(v);
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return "₱" + f.format(n);
    }

    public static BigDecimal num(Object v) {
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return BigDecimal.ZERO;
    }

    /** "November 20, 2026", or a blank line. */
    public static String date(Object v) {
        LocalDate d = v instanceof Date s ? s.toLocalDate() : v instanceof Timestamp t ? t.toLocalDateTime().toLocalDate()
                : v instanceof LocalDateTime l ? l.toLocalDate() : v instanceof LocalDate l ? l : null;
        return d == null ? "____________________" : d.format(LONG);
    }

    public static Long id(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    public static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    public static String or(String v, String fallback) {
        return blank(v) ? fallback : v;
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
            "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /** "Two Million Three Hundred Seventy-Five Thousand Pesos and 50/100", as amounts are written on government forms. */
    public static String words(Object amount) {
        BigDecimal n = num(amount).setScale(2, RoundingMode.HALF_UP);
        long whole = n.longValue();
        int cents = n.subtract(BigDecimal.valueOf(whole)).movePointRight(2).intValue();
        String w = whole == 0 ? "Zero" : group(whole);
        return w + (whole == 1 ? " Peso" : " Pesos") + (cents > 0 ? " and " + String.format("%02d", cents) + "/100" : " Only");
    }

    private static String group(long n) {
        String[][] scales = {{"1000000000", "Billion"}, {"1000000", "Million"}, {"1000", "Thousand"}};
        StringBuilder out = new StringBuilder();
        for (String[] s : scales) {
            long unit = Long.parseLong(s[0]);
            if (n >= unit) {
                out.append(hundreds((int) (n / unit))).append(' ').append(s[1]).append(' ');
                n %= unit;
            }
        }
        if (n > 0) out.append(hundreds((int) n));
        return out.toString().trim();
    }

    private static String hundreds(int n) {
        StringBuilder out = new StringBuilder();
        if (n >= 100) {
            out.append(ONES[n / 100]).append(" Hundred");
            n %= 100;
            if (n > 0) out.append(' ');
        }
        if (n >= 20) {
            out.append(TENS[n / 10]);
            if (n % 10 > 0) out.append('-').append(ONES[n % 10]);
        } else if (n > 0) {
            out.append(ONES[n]);
        }
        return out.toString();
    }
}

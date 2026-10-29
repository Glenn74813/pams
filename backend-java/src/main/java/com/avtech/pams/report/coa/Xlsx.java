package com.avtech.pams.report.coa;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A one-sheet workbook written the way the Laravel PhpSpreadsheet services wrote the COA forms, so
 * they port line for line: A1 references, styles applied to ranges and accumulated per cell, and
 * range borders that follow PhpSpreadsheet ('top'/'bottom'/'left'/'right' are the edges of the range,
 * 'allBorders' every cell, 'outline' the box, 'inside'/'vertical'/'horizontal' the inner lines).
 *
 * <pre>
 * Xlsx x = new Xlsx("RSMI", "Times New Roman", 12);
 * x.width("A", 12);
 * x.merge("A3:H3");
 * x.set("A3", "REPORT OF SUPPLIES AND MATERIALS ISSUED");
 * x.style("A3").font(FONT).bold().size(14).center();
 * x.style("A9:F9").border(Edge.OUTLINE, Line.MEDIUM);
 * x.page().landscape().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0);
 * byte[] file = x.bytes();
 * </pre>
 */
public final class Xlsx {

    public enum Edge { TOP, BOTTOM, LEFT, RIGHT, ALL, OUTLINE, INSIDE, VERTICAL, HORIZONTAL }

    public enum Line { NONE, THIN, MEDIUM, THICK, DOUBLE, DASHED }

    public enum H { LEFT, CENTER, RIGHT, GENERAL, JUSTIFY }

    public enum V { TOP, CENTER, BOTTOM }

    /** PhpSpreadsheet paper size codes (same numbers as Excel). */
    public static final short LETTER = 1, LEGAL = 5, A4 = 9, FOLIO = 14;

    /** Everything a cell's style can carry; null means "not set" (inherit the default). */
    private static final class Fmt {
        String font;
        Double size;
        Boolean bold, italic, underline;
        H h;
        V v;
        Boolean wrap;
        String numFmt;
        Line top, bottom, left, right;

        Key key() {
            return new Key(font, size, bold, italic, underline, h, v, wrap, numFmt, top, bottom, left, right);
        }
    }

    private record Key(String font, Double size, Boolean bold, Boolean italic, Boolean underline, H h, V v, Boolean wrap, String numFmt,
                       Line top, Line bottom, Line left, Line right) {
    }

    private final String title;
    private String defaultFont;
    private double defaultSize;
    private final Map<Long, Object> values = new HashMap<>();
    private final Map<Long, Fmt> formats = new HashMap<>();
    private final List<String> merges = new ArrayList<>();
    private final Map<Integer, Double> widths = new LinkedHashMap<>();
    private final List<Integer> autoSize = new ArrayList<>();
    private final List<Integer> autoSizeUnmerged = new ArrayList<>();
    private final Map<Integer, Double> heights = new LinkedHashMap<>();
    private final Page page = new Page();
    private final List<Object[]> images = new ArrayList<>();

    public Xlsx(String sheetTitle) {
        this(sheetTitle, "Calibri", 11);
    }

    public Xlsx(String sheetTitle, String defaultFont, double defaultSize) {
        this.title = sheetTitle;
        this.defaultFont = defaultFont;
        this.defaultSize = defaultSize;
    }

    /** {@code getDefaultStyle()->getFont()->setName()->setSize()}. */
    public Xlsx defaultFont(String name, double size) {
        this.defaultFont = name;
        this.defaultSize = size;
        return this;
    }

    // ───────────────────────── content ─────────────────────────

    /** {@code setCellValue}: strings, numbers (Integer/Long/Double/BigDecimal) or null. */
    public Xlsx set(String cell, Object value) {
        int[] rc = rc(cell);
        if (value instanceof java.math.BigDecimal b) value = b.doubleValue();
        values.put(key(rc[0], rc[1]), value);
        return this;
    }

    public Xlsx set(String col, int row, Object value) {
        return set(col + row, value);
    }

    public Xlsx merge(String range) {
        merges.add(range);
        return this;
    }

    public Xlsx width(String col, double width) {
        widths.put(CellReference.convertColStringToIndex(col), width);
        return this;
    }

    public Xlsx autoSize(String col) {
        autoSize.add(CellReference.convertColStringToIndex(col));
        return this;
    }

    /**
     * {@code getColumnDimension($col)->setAutoSize(true)} as PhpSpreadsheet measures it: cells inside
     * a merged range spanning several columns are left out of the width.
     */
    public Xlsx autoSizeSkippingMerges(String col) {
        autoSizeUnmerged.add(CellReference.convertColStringToIndex(col));
        return this;
    }

    /**
     * What PhpSpreadsheet's DefaultValueBinder stores for a {@code setCellValue} string: a numeric
     * string such as {@code "12.50"} becomes a number (but not one with a leading zero like
     * {@code "007"}); anything else is returned unchanged.
     */
    public static Object bind(Object value) {
        if (!(value instanceof String s) || !s.matches("[+-]?(\\d+\\.?\\d*|\\d*\\.?\\d+)([Ee][-+]?[0-2]?\\d{1,3})?")) return value;
        String t = s.replaceFirst("^[+-]+", "");
        if (t.length() > 1 && t.charAt(0) == '0' && t.charAt(1) != '.') return value;
        try {
            double d = Double.parseDouble(s);
            if (s.indexOf('.') < 0 && Math.abs(d) <= Long.MAX_VALUE && !s.matches(".*[Ee].*")) return Long.parseLong(s.replaceFirst("^\\+", ""));
            return d;
        } catch (NumberFormatException e) {
            return value;
        }
    }

    public Xlsx height(int row, double points) {
        heights.put(row, points);
        return this;
    }

    public Page page() {
        return page;
    }

    /** A picture anchored at a cell (PhpSpreadsheet Drawing): height in pixels, width kept in proportion. */
    public Xlsx image(String cell, byte[] data, String ext, int heightPx, int offsetX, int offsetY) {
        images.add(new Object[]{cell, data, ext, heightPx, offsetX, offsetY});
        return this;
    }

    /** {@code getStyle(range)}: every setter applies to the whole range (borders by edge, as PhpSpreadsheet does). */
    public Style style(String range) {
        return new Style(range);
    }

    public Style style(String col, int row) {
        return new Style(col + row);
    }

    // ───────────────────────── styles ─────────────────────────

    public final class Style {
        private final int r1, c1, r2, c2;

        private Style(String range) {
            String[] p = range.split(":");
            int[] a = rc(p[0]);
            int[] b = p.length > 1 ? rc(p[1]) : a;
            r1 = Math.min(a[0], b[0]);
            r2 = Math.max(a[0], b[0]);
            c1 = Math.min(a[1], b[1]);
            c2 = Math.max(a[1], b[1]);
        }

        private Style each(java.util.function.Consumer<Fmt> f) {
            for (int r = r1; r <= r2; r++) for (int c = c1; c <= c2; c++) f.accept(fmt(r, c));
            return this;
        }

        public Style font(String name) { return each(f -> f.font = name); }
        public Style size(double pt) { return each(f -> f.size = pt); }
        public Style bold() { return bold(true); }
        public Style bold(boolean on) { return each(f -> f.bold = on); }
        public Style italic() { return italic(true); }
        public Style italic(boolean on) { return each(f -> f.italic = on); }
        public Style underline() { return each(f -> f.underline = true); }
        public Style align(H h) { return each(f -> f.h = h); }
        public Style valign(V v) { return each(f -> f.v = v); }
        public Style center() { return align(H.CENTER); }
        public Style left() { return align(H.LEFT); }
        public Style right() { return align(H.RIGHT); }
        public Style vcenter() { return valign(V.CENTER); }
        public Style wrap() { return each(f -> f.wrap = true); }
        public Style numberFormat(String code) { return each(f -> f.numFmt = code); }

        /** One border edge (or set) of the range, PhpSpreadsheet-style. */
        public Style border(Edge edge, Line line) {
            for (int r = r1; r <= r2; r++) {
                for (int c = c1; c <= c2; c++) {
                    Fmt f = fmt(r, c);
                    switch (edge) {
                        case TOP -> { if (r == r1) f.top = line; }
                        case BOTTOM -> { if (r == r2) f.bottom = line; }
                        case LEFT -> { if (c == c1) f.left = line; }
                        case RIGHT -> { if (c == c2) f.right = line; }
                        case ALL -> { f.top = line; f.bottom = line; f.left = line; f.right = line; }
                        case OUTLINE -> {
                            if (r == r1) f.top = line;
                            if (r == r2) f.bottom = line;
                            if (c == c1) f.left = line;
                            if (c == c2) f.right = line;
                        }
                        case INSIDE -> {
                            if (r > r1) f.top = line;
                            if (r < r2) f.bottom = line;
                            if (c > c1) f.left = line;
                            if (c < c2) f.right = line;
                        }
                        case VERTICAL -> {
                            if (c > c1) f.left = line;
                            if (c < c2) f.right = line;
                        }
                        case HORIZONTAL -> {
                            if (r > r1) f.top = line;
                            if (r < r2) f.bottom = line;
                        }
                    }
                }
            }
            return this;
        }

        /** Shorthand for several edges with one line style. */
        public Style borders(Line line, Edge... edges) {
            for (Edge e : edges) border(e, line);
            return this;
        }
    }

    // ───────────────────────── page setup ─────────────────────────

    public static final class Page {
        private boolean landscape;
        private short paper = A4;
        private Short fitWidth, fitHeight;
        private Double top, right, bottom, left, header, footer;
        private String printArea;
        private Short scale;
        private boolean centerH;

        public Page landscape() { landscape = true; return this; }
        public Page portrait() { landscape = false; return this; }
        public Page paper(short size) { paper = size; return this; }
        public Page fitToWidth(int n) { fitWidth = (short) n; return this; }
        public Page fitToHeight(int n) { fitHeight = (short) n; return this; }
        public Page scale(int percent) { scale = (short) percent; return this; }
        public Page horizontalCentered() { centerH = true; return this; }
        /** Inches, as PhpSpreadsheet's getPageMargins(). */
        public Page margins(double top, double right, double bottom, double left) {
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.left = left;
            return this;
        }
        public Page top(double in) { top = in; return this; }
        public Page right(double in) { right = in; return this; }
        public Page bottom(double in) { bottom = in; return this; }
        public Page left(double in) { left = in; return this; }
        public Page header(double in) { header = in; return this; }
        public Page footer(double in) { footer = in; return this; }
        public Page printArea(String range) { printArea = range; return this; }
    }

    // ───────────────────────── output ─────────────────────────

    public byte[] bytes() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFFont base = wb.getFontAt(0);
            base.setFontName(defaultFont);
            base.setFontHeightInPoints((short) Math.round(defaultSize));
            Sheet sheet = wb.createSheet(title.length() > 31 ? title.substring(0, 31) : title);
            Map<Key, CellStyle> cache = new HashMap<>();
            Map<String, XSSFFont> fonts = new HashMap<>();
            java.util.Set<Long> keys = new java.util.HashSet<>(values.keySet());
            keys.addAll(formats.keySet());
            for (long k : keys) {
                int r = (int) (k >> 20), c = (int) (k & 0xFFFFF);
                Row row = sheet.getRow(r) == null ? sheet.createRow(r) : sheet.getRow(r);
                Cell cell = row.getCell(c) == null ? row.createCell(c) : row.getCell(c);
                Object v = values.get(k);
                if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                else if (v instanceof Boolean b) cell.setCellValue(b);
                else if (v != null) {
                    String s = v.toString();
                    if (s.startsWith("=") && s.length() > 1) cell.setCellFormula(s.substring(1));
                    else cell.setCellValue(s);
                }
                Fmt f = formats.get(k);
                if (f != null) cell.setCellStyle(cache.computeIfAbsent(f.key(), key -> style(wb, key, fonts)));
            }
            for (String m : merges) {
                CellRangeAddress a = CellRangeAddress.valueOf(m);
                // PhpSpreadsheet records a one-cell "merge" (e.g. K1:K1) harmlessly; POI refuses it.
                if (a.getNumberOfCells() > 1) sheet.addMergedRegionUnsafe(a);
            }
            if (!images.isEmpty()) {
                org.apache.poi.xssf.usermodel.XSSFDrawing drawing = ((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).createDrawingPatriarch();
                for (Object[] im : images) {
                    byte[] data = (byte[]) im[1];
                    int type = "png".equals(im[2]) ? org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_PNG : org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_JPEG;
                    int idx = wb.addPicture(data, type);
                    int[] at = rc((String) im[0]);
                    int h = (Integer) im[3];
                    int w = h;
                    try {
                        java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
                        if (bi != null && bi.getHeight() > 0) w = (int) Math.round((double) bi.getWidth() * h / bi.getHeight());
                    } catch (IOException ignored) {
                        // Square fallback.
                    }
                    int emu = org.apache.poi.util.Units.EMU_PER_PIXEL;
                    org.apache.poi.xssf.usermodel.XSSFClientAnchor anchor = new org.apache.poi.xssf.usermodel.XSSFClientAnchor(
                            (Integer) im[4] * emu, (Integer) im[5] * emu, 0, 0, at[1], at[0], at[1], at[0]);
                    anchor.setAnchorType(org.apache.poi.ss.usermodel.ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                    org.apache.poi.xssf.usermodel.XSSFPicture pic = drawing.createPicture(anchor, idx);
                    pic.getCTPicture().getSpPr().getXfrm().getExt().setCx((long) w * emu);
                    pic.getCTPicture().getSpPr().getXfrm().getExt().setCy((long) h * emu);
                }
            }
            widths.forEach((c, w) -> sheet.setColumnWidth(c, (int) Math.round(w * 256)));
            for (int c : autoSize) sheet.autoSizeColumn(c, true);
            for (int c : autoSizeUnmerged) sheet.autoSizeColumn(c, false);
            heights.forEach((r, h) -> {
                Row row = sheet.getRow(r - 1) == null ? sheet.createRow(r - 1) : sheet.getRow(r - 1);
                row.setHeightInPoints(h.floatValue());
            });
            PrintSetup ps = sheet.getPrintSetup();
            ps.setLandscape(page.landscape);
            ps.setPaperSize(page.paper);
            if (page.fitWidth != null || page.fitHeight != null) {
                sheet.setFitToPage(true);
                ps.setFitWidth(page.fitWidth == null ? 1 : page.fitWidth);
                ps.setFitHeight(page.fitHeight == null ? 0 : page.fitHeight);
            }
            if (page.scale != null) ps.setScale(page.scale);
            if (page.top != null) sheet.setMargin(Sheet.TopMargin, page.top);
            if (page.right != null) sheet.setMargin(Sheet.RightMargin, page.right);
            if (page.bottom != null) sheet.setMargin(Sheet.BottomMargin, page.bottom);
            if (page.left != null) sheet.setMargin(Sheet.LeftMargin, page.left);
            if (page.header != null) sheet.setMargin(Sheet.HeaderMargin, page.header);
            if (page.footer != null) sheet.setMargin(Sheet.FooterMargin, page.footer);
            if (page.centerH) sheet.setHorizontallyCenter(true);
            if (page.printArea != null) {
                CellRangeAddress a = CellRangeAddress.valueOf(page.printArea);
                wb.setPrintArea(0, a.getFirstColumn(), a.getLastColumn(), a.getFirstRow(), a.getLastRow());
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CellStyle style(XSSFWorkbook wb, Key k, Map<String, XSSFFont> fonts) {
        CellStyle s = wb.createCellStyle();
        String fontName = k.font() == null ? defaultFont : k.font();
        double size = k.size() == null ? defaultSize : k.size();
        boolean bold = Boolean.TRUE.equals(k.bold()), italic = Boolean.TRUE.equals(k.italic()), under = Boolean.TRUE.equals(k.underline());
        String fk = fontName + "|" + size + "|" + bold + italic + under;
        XSSFFont font = fonts.computeIfAbsent(fk, x -> {
            XSSFFont f = wb.createFont();
            f.setFontName(fontName);
            f.setFontHeight(size);
            f.setBold(bold);
            f.setItalic(italic);
            if (under) f.setUnderline(org.apache.poi.ss.usermodel.Font.U_SINGLE);
            return f;
        });
        s.setFont(font);
        if (k.h() != null) s.setAlignment(switch (k.h()) {
            case LEFT -> HorizontalAlignment.LEFT;
            case CENTER -> HorizontalAlignment.CENTER;
            case RIGHT -> HorizontalAlignment.RIGHT;
            case JUSTIFY -> HorizontalAlignment.JUSTIFY;
            case GENERAL -> HorizontalAlignment.GENERAL;
        });
        // PhpSpreadsheet's default vertical alignment is bottom, as Excel's.
        s.setVerticalAlignment(k.v() == null ? VerticalAlignment.BOTTOM : switch (k.v()) {
            case TOP -> VerticalAlignment.TOP;
            case CENTER -> VerticalAlignment.CENTER;
            case BOTTOM -> VerticalAlignment.BOTTOM;
        });
        if (Boolean.TRUE.equals(k.wrap())) s.setWrapText(true);
        if (k.numFmt() != null) s.setDataFormat(wb.createDataFormat().getFormat(k.numFmt()));
        if (k.top() != null) s.setBorderTop(line(k.top()));
        if (k.bottom() != null) s.setBorderBottom(line(k.bottom()));
        if (k.left() != null) s.setBorderLeft(line(k.left()));
        if (k.right() != null) s.setBorderRight(line(k.right()));
        return s;
    }

    private static BorderStyle line(Line l) {
        return switch (l) {
            case NONE -> BorderStyle.NONE;
            case THIN -> BorderStyle.THIN;
            case MEDIUM -> BorderStyle.MEDIUM;
            case THICK -> BorderStyle.THICK;
            case DOUBLE -> BorderStyle.DOUBLE;
            case DASHED -> BorderStyle.DASHED;
        };
    }

    private Fmt fmt(int r, int c) {
        return formats.computeIfAbsent(key(r, c), k -> new Fmt());
    }

    private static long key(int r, int c) {
        return ((long) r << 20) | c;
    }

    /** "H12" → {row index 11, column index 7}. */
    private static int[] rc(String cell) {
        CellReference ref = new CellReference(cell.replace("$", ""));
        return new int[]{ref.getRow(), ref.getCol()};
    }

    /** Column letter for a 1-based index, as Coordinate::stringFromColumnIndex. */
    public static String col(int oneBased) {
        return CellReference.convertNumToColString(oneBased - 1);
    }

    /** 1-based index for a column letter, as Coordinate::columnIndexFromString. */
    public static int colIndex(String col) {
        return CellReference.convertColStringToIndex(col) + 1;
    }
}

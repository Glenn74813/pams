package com.avtech.pams.report.coa;

import com.avtech.pams.report.coa.Xlsx.Edge;
import com.avtech.pams.report.coa.Xlsx.Line;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One list-type report (registers, master lists, schedules) written as Excel, CSV or PDF from the
 * same title, period line, columns and rows. Money and count columns can be subtotalled per group
 * and totalled at the end; an optional certification text and signature blocks close the report.
 *
 * <pre>
 * TabularReport t = new TabularReport("IAR Monitoring", "INSPECTION AND ACCEPTANCE REPORT MONITORING")
 *         .entity(entity).period("For the period 01/01/2026 to 06/30/2026")
 *         .col("IAR No.", Kind.TEXT, 16).col("Amount", Kind.MONEY, 14);
 * t.row("IAR-2026-0001", new BigDecimal("1200.00"));
 * t.total("TOTAL");
 * byte[] file = t.render("pdf");
 * </pre>
 */
public final class TabularReport {

    /** INT and MONEY columns are summed in subtotals and totals; NUMBER (useful life, re-order point) is not. */
    public enum Kind { TEXT, DATE, INT, NUMBER, MONEY }

    /** Format of each output, by the {@code format} request parameter. */
    public enum Format {
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        CSV("csv", "text/csv; charset=UTF-8"),
        PDF("pdf", "application/pdf");

        public final String ext;
        public final String contentType;

        Format(String ext, String contentType) {
            this.ext = ext;
            this.contentType = contentType;
        }

        /** null or blank means Excel; anything other than xlsx/csv/pdf is null (the caller rejects it). */
        public static Format of(String s) {
            if (s == null || s.isBlank()) return XLSX;
            for (Format f : values()) if (f.ext.equalsIgnoreCase(s.trim())) return f;
            return null;
        }
    }

    private record Col(String label, Kind kind, double width) {
    }

    private enum RowType { DATA, GROUP, SUBTOTAL, TOTAL }

    private record Row(RowType type, Object[] cells) {
    }

    /** A signature block: caption ("Prepared by:"), signatory and the line under the name. */
    public record Sign(String caption, Signatories.Signatory who, String designation) {
    }

    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final String FONT = "Times New Roman";

    private final String sheet;
    private final String title;
    private String appendix;
    private String entity;
    private final List<String> subtitles = new ArrayList<>();
    private final List<Col> cols = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<Sign> signs = new ArrayList<>();
    private int groupStart = -1;

    public TabularReport(String sheetTitle, String title) {
        this.sheet = sheetTitle;
        this.title = title;
    }

    // ───────────────────────── building ─────────────────────────

    /** Label printed top right, e.g. "Appendix 70" (forms with an official layout). */
    public TabularReport appendix(String label) {
        this.appendix = label;
        return this;
    }

    public TabularReport entity(String name) {
        this.entity = name;
        return this;
    }

    /** A centred line under the title: the period, an "as at" date, the accountable officer. */
    public TabularReport subtitle(String line) {
        if (line != null && !line.isBlank()) subtitles.add(line);
        return this;
    }

    public TabularReport col(String label, Kind kind, double width) {
        cols.add(new Col(label, kind, width));
        return this;
    }

    /** One data row; cells in column order (String, Number, LocalDate or null). */
    public TabularReport row(Object... cells) {
        rows.add(new Row(RowType.DATA, cells));
        return this;
    }

    /** Starts a group (a bold heading row); {@link #subtotal} sums the rows since. */
    public TabularReport group(String heading) {
        rows.add(new Row(RowType.GROUP, new Object[]{heading}));
        groupStart = rows.size();
        return this;
    }

    public TabularReport subtotal(String label) {
        rows.add(new Row(RowType.SUBTOTAL, sums(label, groupStart < 0 ? 0 : groupStart)));
        return this;
    }

    /** Sums every data row of the report. */
    public TabularReport total(String label) {
        rows.add(new Row(RowType.TOTAL, sums(label, 0)));
        return this;
    }

    /** A paragraph after the table (a certification, a note on the basis of the figures). */
    public TabularReport note(String text) {
        notes.add(text);
        return this;
    }

    public TabularReport sign(String caption, Signatories.Signatory who, String designation) {
        signs.add(new Sign(caption, who, designation));
        return this;
    }

    public int dataRows() {
        return (int) rows.stream().filter(r -> r.type() == RowType.DATA).count();
    }

    private Object[] sums(String label, int from) {
        Object[] out = new Object[cols.size()];
        out[0] = label;
        for (int c = 1; c < cols.size(); c++) {
            Kind k = cols.get(c).kind();
            if (k != Kind.MONEY && k != Kind.INT) continue;
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = from; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (r.type() == RowType.DATA && c < r.cells().length && r.cells()[c] instanceof Number n) sum = sum.add(new BigDecimal(n.toString()));
            }
            out[c] = k == Kind.INT ? (Object) sum.longValue() : sum;
        }
        return out;
    }

    // ───────────────────────── output ─────────────────────────

    public byte[] render(Format f) {
        return switch (f) {
            case XLSX -> xlsx();
            case CSV -> csv();
            case PDF -> pdf();
        };
    }

    public byte[] xlsx() {
        Xlsx x = new Xlsx(sheet, FONT, 10);
        int n = cols.size();
        String last = Xlsx.col(n);
        for (int c = 0; c < n; c++) x.width(Xlsx.col(c + 1), cols.get(c).width());
        x.page().paper(Xlsx.A4).fitToWidth(1).fitToHeight(0);
        if (totalWidth() > 110) x.page().landscape();
        x.page().top(0.5).bottom(0.5).left(0.5).right(0.5);

        int row = 1;
        if (appendix != null) {
            x.set(last + row, appendix);
            x.style(last + row).size(9).italic().right();
        }
        row += 2;
        x.merge("A" + row + ":" + last + row);
        x.set("A" + row, title);
        x.style("A" + row).size(14).bold().center();
        row++;
        for (String s : subtitles) {
            x.merge("A" + row + ":" + last + row);
            x.set("A" + row, s);
            x.style("A" + row).center();
            row++;
        }
        if (entity != null) {
            row++;
            x.set("A" + row, "Entity Name: " + RpciForm.or(entity, "___________________________________"));
            x.style("A" + row).bold();
            row++;
        }
        row++;
        int head = row;
        for (int c = 0; c < n; c++) x.set(Xlsx.col(c + 1) + row, cols.get(c).label());
        x.style("A" + row + ":" + last + row).bold().size(9).center().vcenter().wrap().border(Edge.ALL, Line.THIN);
        row++;
        if (dataRows() == 0) {
            x.merge("A" + row + ":" + last + row);
            x.set("A" + row, "Nothing to report for the selected filters.");
            x.style("A" + row).italic().center();
            x.style("A" + row + ":" + last + row).border(Edge.ALL, Line.THIN);
            row++;
        }
        for (Row r : rows) {
            String span = "A" + row + ":" + last + row;
            if (r.type() == RowType.GROUP) {
                x.merge(span);
                x.set("A" + row, r.cells()[0]);
                x.style(span).bold().border(Edge.ALL, Line.THIN);
                row++;
                continue;
            }
            int lead = labelSpan(r);
            if (lead > 1) {
                x.merge("A" + row + ":" + Xlsx.col(lead) + row);
                x.style("A" + row).right();
            }
            for (int c = 0; c < n; c++) {
                Object v = c < r.cells().length ? r.cells()[c] : null;
                String cell = Xlsx.col(c + 1) + row;
                Kind k = cols.get(c).kind();
                if (v instanceof LocalDate d) x.set(cell, d.format(MDY));
                else if (v instanceof java.sql.Date d) x.set(cell, d.toLocalDate().format(MDY));
                else x.set(cell, v);
                if (k == Kind.MONEY) x.style(cell).numberFormat("#,##0.00").right();
                else if (k == Kind.INT || k == Kind.NUMBER) x.style(cell).numberFormat("#,##0").right();
                else if (k == Kind.DATE) x.style(cell).center();
                else x.style(cell).wrap();
            }
            x.style(span).border(Edge.ALL, Line.THIN).vcenter();
            if (r.type() != RowType.DATA) x.style(span).bold();
            if (r.type() == RowType.TOTAL) x.style(span).border(Edge.TOP, Line.DOUBLE);
            row++;
        }
        x.style("A" + head + ":" + last + (row - 1)).size(9);

        row++;
        for (String note : notes) {
            x.merge("A" + row + ":" + last + row);
            x.set("A" + row, note);
            x.style("A" + row).wrap().valign(Xlsx.V.TOP);
            x.height(row, Math.max(15, 15 * Math.ceil(note.length() / Math.max(40.0, totalWidth() * 1.1))));
            row += 2;
        }
        if (!signs.isEmpty()) {
            // Up to three blocks across the page; the columns are split evenly between them.
            int per = Math.max(1, n / signs.size());
            int top = row;
            for (int s = 0; s < signs.size(); s++) {
                Sign sg = signs.get(s);
                String c = Xlsx.col(Math.min(n, 1 + s * per));
                x.set(c + top, sg.caption());
                Signatories.stamp(x, c + (top + 1), c + (top + 3), sg.who());
                x.set(c + (top + 4), sg.designation() != null ? sg.designation() : sg.who().designation());
                x.style(c + (top + 4)).size(8).italic();
                x.set(c + (top + 5), "Date: ________________");
                x.style(c + (top + 5)).size(9);
            }
        }
        return x.bytes();
    }

    /** RFC 4180: comma-separated, CRLF line ends, fields quoted when needed; UTF-8 with a BOM for Excel. */
    public byte[] csv() {
        StringBuilder b = new StringBuilder("﻿");
        line(b, List.of(title));
        for (String s : subtitles) line(b, List.of(s));
        if (entity != null && !entity.isBlank()) line(b, List.of("Entity Name: " + entity));
        b.append("\r\n");
        line(b, cols.stream().map(Col::label).toList());
        for (Row r : rows) {
            List<String> out = new ArrayList<>();
            for (int c = 0; c < cols.size(); c++) {
                Object v = r.type() == RowType.GROUP ? (c == 0 ? r.cells()[0] : null) : c < r.cells().length ? r.cells()[c] : null;
                out.add(plain(v, cols.get(c).kind(), true));
            }
            line(b, out);
        }
        for (String note : notes) {
            b.append("\r\n");
            line(b, List.of(note));
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder b, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) b.append(',');
            String f = fields.get(i) == null ? "" : fields.get(i);
            if (f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
                b.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                b.append(f);
            }
        }
        b.append("\r\n");
    }

    /** A cell as text: ISO dates and unformatted numbers for CSV, MM/dd/yyyy and grouped amounts for PDF. */
    private static String plain(Object v, Kind k, boolean csv) {
        if (v == null) return "";
        if (v instanceof java.sql.Date d) v = d.toLocalDate();
        if (v instanceof LocalDate d) return csv ? d.toString() : d.format(MDY);
        if (v instanceof Number n) {
            BigDecimal d = new BigDecimal(n.toString());
            if (k == Kind.MONEY) return csv ? d.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : String.format(Locale.US, "%,.2f", d);
            return csv ? d.stripTrailingZeros().toPlainString() : String.format(Locale.US, "%,d", d.longValue());
        }
        return v.toString();
    }

    public byte[] pdf() {
        boolean wide = totalWidth() > 110;
        Rectangle size = wide ? PageSize.A4.rotate() : PageSize.A4;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(size, 28, 28, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font small = new Font(Font.TIMES_ROMAN, 7.5f);
        Font smallBold = new Font(Font.TIMES_ROMAN, 7.5f, Font.BOLD);
        Font body = new Font(Font.TIMES_ROMAN, 10);
        if (appendix != null) {
            Paragraph a = new Paragraph(appendix, new Font(Font.TIMES_ROMAN, 9, Font.ITALIC));
            a.setAlignment(Element.ALIGN_RIGHT);
            doc.add(a);
        }
        Paragraph t = new Paragraph(title, new Font(Font.TIMES_ROMAN, 14, Font.BOLD));
        t.setAlignment(Element.ALIGN_CENTER);
        doc.add(t);
        for (String s : subtitles) {
            Paragraph p = new Paragraph(s, body);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);
        }
        if (entity != null) doc.add(new Paragraph("Entity Name: " + RpciForm.or(entity, "___________________________________"), new Font(Font.TIMES_ROMAN, 10, Font.BOLD)));
        doc.add(new Paragraph(" ", body));

        PdfPTable table = new PdfPTable(cols.size());
        table.setWidthPercentage(100);
        float[] w = new float[cols.size()];
        for (int c = 0; c < cols.size(); c++) w[c] = (float) cols.get(c).width();
        table.setWidths(w);
        table.setHeaderRows(1);
        for (Col c : cols) {
            PdfPCell cell = new PdfPCell(new Phrase(c.label(), smallBold));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setGrayFill(0.9f);
            table.addCell(cell);
        }
        if (dataRows() == 0) {
            PdfPCell cell = new PdfPCell(new Phrase("Nothing to report for the selected filters.", small));
            cell.setColspan(cols.size());
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        for (Row r : rows) {
            if (r.type() == RowType.GROUP) {
                PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(r.cells()[0]), smallBold));
                cell.setColspan(cols.size());
                cell.setGrayFill(0.96f);
                table.addCell(cell);
                continue;
            }
            Font f = r.type() == RowType.DATA ? small : smallBold;
            int span = labelSpan(r);
            if (span > 1) {
                PdfPCell label = new PdfPCell(new Phrase(String.valueOf(r.cells()[0]), f));
                label.setColspan(span);
                label.setHorizontalAlignment(Element.ALIGN_RIGHT);
                if (r.type() == RowType.TOTAL) label.setBorderWidthTop(1.2f);
                table.addCell(label);
            }
            for (int c = span > 1 ? span : 0; c < cols.size(); c++) {
                Kind k = cols.get(c).kind();
                Object v = c < r.cells().length ? r.cells()[c] : null;
                PdfPCell cell = new PdfPCell(new Phrase(plain(v, k, false), f));
                cell.setHorizontalAlignment(k == Kind.MONEY || k == Kind.INT || k == Kind.NUMBER ? Element.ALIGN_RIGHT : k == Kind.DATE ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
                if (r.type() == RowType.TOTAL) cell.setBorderWidthTop(1.2f);
                table.addCell(cell);
            }
        }
        doc.add(table);
        for (String note : notes) {
            Paragraph p = new Paragraph(note, body);
            p.setSpacingBefore(10);
            doc.add(p);
        }
        if (!signs.isEmpty()) {
            PdfPTable s = new PdfPTable(signs.size());
            s.setWidthPercentage(100);
            s.setSpacingBefore(18);
            for (Sign sg : signs) {
                PdfPCell cell = new PdfPCell();
                cell.setBorder(Rectangle.NO_BORDER);
                cell.addElement(new Paragraph(sg.caption(), body));
                cell.addElement(new Paragraph(" ", body));
                cell.addElement(new Paragraph(" ", body));
                cell.addElement(new Paragraph(new Chunk(sg.who().name() == null ? "________________________" : sg.who().name(), new Font(Font.TIMES_ROMAN, 10, Font.BOLD))));
                cell.addElement(new Paragraph(sg.designation() != null ? sg.designation() : sg.who().designation(), new Font(Font.TIMES_ROMAN, 8, Font.ITALIC)));
                cell.addElement(new Paragraph("Date: ________________", new Font(Font.TIMES_ROMAN, 9)));
                s.addCell(cell);
            }
            doc.add(s);
        }
        doc.close();
        return out.toByteArray();
    }

    /** A subtotal/total label spans the leading columns that have no sum; 1 for data rows. */
    private int labelSpan(Row r) {
        if (r.type() != RowType.SUBTOTAL && r.type() != RowType.TOTAL) return 1;
        int c = 1;
        while (c < cols.size() && (c >= r.cells().length || r.cells()[c] == null)) c++;
        return c;
    }

    private double totalWidth() {
        return cols.stream().mapToDouble(Col::width).sum();
    }
}

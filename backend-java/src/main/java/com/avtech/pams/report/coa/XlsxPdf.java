package com.avtech.pams.report.coa;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a generated COA form workbook on PDF pages, so each form has one layout to maintain: the
 * Excel builder's own columns, row heights, merges, borders, fonts, signature images and page setup.
 * The sheet is scaled to the page width (and to one page when the form asks for that) and otherwise
 * breaks between rows, never through a merged block.
 */
public final class XlsxPdf {

    private XlsxPdf() {
    }

    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final float CELL_PAD = 2f;

    public static byte[] convert(byte[] xlsx) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = null;
            PdfWriter writer = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                XSSFSheet sheet = wb.getSheetAt(i);
                Rectangle size = pageSize(sheet);
                if (doc == null) {
                    doc = new Document(size);
                    writer = PdfWriter.getInstance(doc, out);
                    doc.open();
                } else {
                    doc.setPageSize(size);
                    doc.newPage();
                }
                new SheetPainter(wb, sheet, size, writer).paint(doc);
            }
            if (doc != null) doc.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not write the PDF", e);
        }
    }

    private static Rectangle pageSize(Sheet sheet) {
        Rectangle base = switch (sheet.getPrintSetup().getPaperSize()) {
            case 1 -> PageSize.LETTER;
            case 5 -> PageSize.LEGAL;
            case 14 -> new Rectangle(612, 936);
            default -> PageSize.A4;
        };
        return sheet.getPrintSetup().getLandscape() ? new Rectangle(base.getHeight(), base.getWidth()) : new Rectangle(base.getWidth(), base.getHeight());
    }

    /** One sheet's measurements and drawing; lengths are in unscaled sheet points unless a name says otherwise. */
    private static final class SheetPainter {
        private final XSSFWorkbook wb;
        private final XSSFSheet sheet;
        private final Rectangle page;
        private final PdfWriter writer;
        private final FormulaEvaluator eval;
        private final DataFormatter formatter = new DataFormatter();
        private final Map<String, BaseFont> fonts = new HashMap<>();
        private final Map<Long, CellRangeAddress> merges = new HashMap<>();
        private int firstRow, lastRow, firstCol, lastCol;
        private float[] colX;
        private float[] rowY;
        private final float top, left, usableW, usableH;

        SheetPainter(XSSFWorkbook wb, XSSFSheet sheet, Rectangle page, PdfWriter writer) {
            this.wb = wb;
            this.sheet = sheet;
            this.page = page;
            this.writer = writer;
            this.eval = wb.getCreationHelper().createFormulaEvaluator();
            top = (float) sheet.getMargin(Sheet.TopMargin) * 72;
            left = (float) sheet.getMargin(Sheet.LeftMargin) * 72;
            usableW = page.getWidth() - left - (float) sheet.getMargin(Sheet.RightMargin) * 72;
            usableH = page.getHeight() - top - (float) sheet.getMargin(Sheet.BottomMargin) * 72;
        }

        void paint(Document doc) throws IOException, DocumentException {
            measure();
            float width = colX[colX.length - 1];
            float height = rowY[rowY.length - 1];
            float scale = Math.min(1f, usableW / width);
            // Fit-to-one-page forms shrink to the height too; others run on to more pages.
            if (sheet.getFitToPage() && sheet.getPrintSetup().getFitHeight() == 1) scale = Math.min(scale, usableH / height);
            List<int[]> pages = paginate(usableH / scale);
            float offsetX = sheet.getHorizontallyCenter() ? (usableW - width * scale) / 2 : 0;
            boolean firstPage = true;
            for (int[] range : pages) {
                if (!firstPage) doc.newPage();
                firstPage = false;
                PdfContentByte cb = writer.getDirectContent();
                drawCells(cb, range[0], range[1], scale, offsetX);
                drawPictures(cb, range[0], range[1], scale, offsetX);
            }
        }

        private void measure() {
            firstRow = sheet.getFirstRowNum();
            lastRow = sheet.getLastRowNum();
            firstCol = 0;
            lastCol = 0;
            for (Row r : sheet) if (r.getLastCellNum() > lastCol + 1) lastCol = r.getLastCellNum() - 1;
            for (CellRangeAddress m : sheet.getMergedRegions()) {
                for (int r = m.getFirstRow(); r <= m.getLastRow(); r++) for (int c = m.getFirstColumn(); c <= m.getLastColumn(); c++) merges.put(key(r, c), m);
                lastCol = Math.max(lastCol, m.getLastColumn());
                lastRow = Math.max(lastRow, m.getLastRow());
            }
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            if (drawing != null) {
                for (XSSFShape s : drawing.getShapes()) {
                    if (s instanceof XSSFPicture p) lastRow = Math.max(lastRow, p.getClientAnchor().getRow1() + 3);
                }
            }
            if (firstRow < 0) firstRow = 0;
            String area = wb.getPrintArea(wb.getSheetIndex(sheet));
            if (area != null) {
                CellRangeAddress a = CellRangeAddress.valueOf(area.substring(area.indexOf('!') + 1).replace("$", ""));
                firstRow = a.getFirstRow();
                lastRow = a.getLastRow();
                firstCol = a.getFirstColumn();
                lastCol = a.getLastColumn();
            }
            colX = new float[lastCol - firstCol + 2];
            for (int c = firstCol; c <= lastCol; c++) colX[c - firstCol + 1] = colX[c - firstCol] + sheet.getColumnWidthInPixels(c) * 0.75f;
            rowY = new float[lastRow - firstRow + 2];
            for (int r = firstRow; r <= lastRow; r++) rowY[r - firstRow + 1] = rowY[r - firstRow] + rowHeight(r);
        }

        /** The stored height, or as Excel does for a row without one: tall enough for its wrapped text. */
        private float rowHeight(int r) {
            Row row = sheet.getRow(r);
            float base = sheet.getDefaultRowHeightInPoints();
            if (row == null) return base;
            if (((org.apache.poi.xssf.usermodel.XSSFRow) row).getCTRow().isSetHt()) return row.getHeightInPoints();
            float h = base;
            for (Cell cell : row) {
                if (cell.getColumnIndex() < firstCol || cell.getColumnIndex() > lastCol) continue;
                XSSFCellStyle st = (XSSFCellStyle) cell.getCellStyle();
                XSSFFont f = st.getFont();
                float size = f.getFontHeightInPoints();
                h = Math.max(h, size * 1.25f);
                if (!st.getWrapText()) continue;
                CellRangeAddress m = merges.get(key(r, cell.getColumnIndex()));
                if (m != null && m.getLastRow() > m.getFirstRow()) continue;
                int c1 = m == null ? cell.getColumnIndex() : m.getFirstColumn();
                int c2 = m == null ? cell.getColumnIndex() : m.getLastColumn();
                float w = 0;
                for (int c = c1; c <= c2; c++) w += sheet.getColumnWidthInPixels(c) * 0.75f;
                int lines = wrap(text(cell), font(f, text(cell)), size, w - CELL_PAD * 2).size();
                h = Math.max(h, lines * size * 1.2f + 2);
            }
            return h;
        }

        /** Page breaks between rows, keeping a block of vertically merged rows together. */
        private List<int[]> paginate(float pageHeight) {
            List<int[]> out = new ArrayList<>();
            int start = firstRow;
            float used = 0;
            for (int r = firstRow; r <= lastRow; r++) {
                int blockEnd = r;
                for (int c = firstCol; c <= lastCol; c++) {
                    CellRangeAddress m = merges.get(key(r, c));
                    if (m != null) blockEnd = Math.max(blockEnd, m.getLastRow());
                }
                float block = rowY[Math.min(blockEnd, lastRow) - firstRow + 1] - rowY[r - firstRow];
                if (used + block > pageHeight && r > start) {
                    out.add(new int[]{start, r - 1});
                    start = r;
                    used = 0;
                }
                float one = rowY[r - firstRow + 1] - rowY[r - firstRow];
                used += one;
            }
            out.add(new int[]{start, lastRow});
            return out;
        }

        private void drawCells(PdfContentByte cb, int from, int to, float scale, float offsetX) throws IOException, DocumentException {
            float yBase = page.getHeight() - top;
            float rowOrigin = rowY[from - firstRow];
            for (int r = from; r <= to; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (Cell cell : row) {
                    int c = cell.getColumnIndex();
                    if (c < firstCol || c > lastCol) continue;
                    CellRangeAddress m = merges.get(key(r, c));
                    XSSFCellStyle st = (XSSFCellStyle) cell.getCellStyle();
                    float x1 = px(c, scale, offsetX), x2 = px(c + 1, scale, offsetX);
                    float y1 = yBase - (rowY[r - firstRow] - rowOrigin) * scale, y2 = yBase - (rowY[r - firstRow + 1] - rowOrigin) * scale;
                    fill(cb, st, x1, y1, x2, y2);
                    borders(cb, st, r, c, m, x1, y1, x2, y2, scale);
                    if (m != null && (m.getFirstRow() != r || m.getFirstColumn() != c)) continue;
                    String value = text(cell);
                    if (value.isEmpty()) continue;
                    int r2 = m == null ? r : Math.min(m.getLastRow(), lastRow);
                    int c2 = m == null ? c : Math.min(m.getLastColumn(), lastCol);
                    float bx2 = px(c2 + 1, scale, offsetX);
                    float by2 = yBase - (rowY[r2 - firstRow + 1] - rowOrigin) * scale;
                    drawText(cb, cell, st, value, x1, y1, bx2, by2, r, c2, scale, offsetX);
                }
            }
        }

        private float px(int col, float scale, float offsetX) {
            return left + offsetX + colX[col - firstCol] * scale;
        }

        private void fill(PdfContentByte cb, XSSFCellStyle st, float x1, float y1, float x2, float y2) {
            XSSFColor fg = st.getFillForegroundXSSFColor();
            if (fg == null || st.getFillPattern() == org.apache.poi.ss.usermodel.FillPatternType.NO_FILL || fg.getRGB() == null) return;
            byte[] c = fg.getRGB();
            cb.saveState();
            cb.setRGBColorFill(c[0] & 0xFF, c[1] & 0xFF, c[2] & 0xFF);
            cb.rectangle(x1, y2, x2 - x1, y1 - y2);
            cb.fill();
            cb.restoreState();
        }

        private void borders(PdfContentByte cb, XSSFCellStyle st, int r, int c, CellRangeAddress m, float x1, float y1, float x2, float y2, float scale) {
            // Lines inside a merged block are not shown, as in Excel.
            if (m == null || r == m.getFirstRow()) line(cb, st.getBorderTop(), x1, y1, x2, y1, scale);
            if (m == null || r == m.getLastRow()) line(cb, st.getBorderBottom(), x1, y2, x2, y2, scale);
            if (m == null || c == m.getFirstColumn()) line(cb, st.getBorderLeft(), x1, y1, x1, y2, scale);
            if (m == null || c == m.getLastColumn()) line(cb, st.getBorderRight(), x2, y1, x2, y2, scale);
        }

        private void line(PdfContentByte cb, BorderStyle b, float x1, float y1, float x2, float y2, float scale) {
            if (b == null || b == BorderStyle.NONE) return;
            float w = switch (b) {
                case MEDIUM, MEDIUM_DASHED, MEDIUM_DASH_DOT -> 1f;
                case THICK -> 1.5f;
                case DOUBLE -> 1.2f;
                case HAIR -> 0.25f;
                default -> 0.5f;
            };
            cb.saveState();
            cb.setLineWidth(Math.max(0.25f, w * Math.max(scale, 0.6f)));
            if (b == BorderStyle.DASHED || b == BorderStyle.MEDIUM_DASHED) cb.setLineDash(3f, 2f, 0f);
            cb.moveTo(x1, y1);
            cb.lineTo(x2, y2);
            cb.stroke();
            cb.restoreState();
        }

        private void drawText(PdfContentByte cb, Cell cell, XSSFCellStyle st, String value, float x1, float y1, float x2, float y2, int r, int lastColOfCell, float scale, float offsetX)
                throws IOException, DocumentException {
            XSSFFont f = st.getFont();
            float size = f.getFontHeightInPoints() * scale;
            if (value.trim().length() == 1 && "☐☑☒✓✔".contains(value.trim())) {
                drawMark(cb, value.trim().charAt(0), st.getAlignment(), x1, y1, x2, y2, size);
                return;
            }
            BaseFont bf = font(f, value);
            float pad = CELL_PAD * scale;
            HorizontalAlignment h = st.getAlignment();
            boolean number = isNumber(cell);
            int align = switch (h) {
                case CENTER, CENTER_SELECTION -> PdfContentByte.ALIGN_CENTER;
                case RIGHT -> PdfContentByte.ALIGN_RIGHT;
                case GENERAL -> number ? PdfContentByte.ALIGN_RIGHT : PdfContentByte.ALIGN_LEFT;
                default -> PdfContentByte.ALIGN_LEFT;
            };
            float width = x2 - x1 - pad * 2;
            List<String> lines = st.getWrapText() ? wrap(value, bf, size, width) : List.of(value.replace('\n', ' '));
            float leading = size * 1.2f;
            float block = lines.size() * leading;
            VerticalAlignment v = st.getVerticalAlignment();
            float baseline = switch (v) {
                case TOP -> y1 - pad - size;
                case CENTER -> (y1 + y2) / 2 + block / 2 - size;
                default -> y2 + pad + block - size - (leading - size) / 2;
            };
            // Unwrapped text runs over empty neighbouring cells, as in Excel, and stops at a filled one.
            cb.saveState();
            if (!st.getWrapText() && !number) {
                Row row = sheet.getRow(r);
                float clipLeft = x1, clipRight = x2;
                if (align != PdfContentByte.ALIGN_RIGHT) {
                    int c = lastColOfCell + 1;
                    while (c <= lastCol && !filled(row, r, c)) c++;
                    clipRight = px(c, scale, offsetX);
                }
                if (align != PdfContentByte.ALIGN_LEFT) {
                    int c = cell.getColumnIndex() - 1;
                    while (c >= firstCol && !filled(row, r, c)) c--;
                    clipLeft = px(c + 1, scale, offsetX);
                }
                cb.rectangle(clipLeft, y2 - 1, clipRight - clipLeft, y1 - y2 + 2);
                cb.clip();
                cb.newPath();
            }
            for (String ln : lines) {
                float x = align == PdfContentByte.ALIGN_CENTER ? (x1 + x2) / 2 : align == PdfContentByte.ALIGN_RIGHT ? x2 - pad : x1 + pad;
                cb.beginText();
                cb.setFontAndSize(bf, size);
                cb.showTextAligned(align, ln, x, baseline, 0);
                cb.endText();
                if (f.getUnderline() != org.apache.poi.ss.usermodel.Font.U_NONE) {
                    float w = bf.getWidthPoint(ln, size);
                    float ux = align == PdfContentByte.ALIGN_CENTER ? x - w / 2 : align == PdfContentByte.ALIGN_RIGHT ? x - w : x;
                    cb.setLineWidth(0.4f);
                    cb.moveTo(ux, baseline - size * 0.12f);
                    cb.lineTo(ux + w, baseline - size * 0.12f);
                    cb.stroke();
                }
                baseline -= leading;
            }
            cb.restoreState();
        }

        /** Check boxes and ticks are drawn: no font the server can rely on has them. */
        private void drawMark(PdfContentByte cb, char mark, HorizontalAlignment h, float x1, float y1, float x2, float y2, float size) {
            float box = size * 0.8f;
            float x = h == HorizontalAlignment.RIGHT ? x2 - box - 2 : h == HorizontalAlignment.LEFT ? x1 + 2 : (x1 + x2 - box) / 2;
            float y = (y1 + y2 - box) / 2;
            cb.saveState();
            cb.setLineWidth(Math.max(0.4f, size * 0.06f));
            if (mark == '☐' || mark == '☑' || mark == '☒') {
                cb.rectangle(x, y, box, box);
                cb.stroke();
            }
            if (mark == '☒') {
                cb.moveTo(x, y);
                cb.lineTo(x + box, y + box);
                cb.moveTo(x, y + box);
                cb.lineTo(x + box, y);
                cb.stroke();
            } else if (mark != '☐') {
                cb.moveTo(x + box * 0.18f, y + box * 0.5f);
                cb.lineTo(x + box * 0.42f, y + box * 0.22f);
                cb.lineTo(x + box * 0.85f, y + box * 0.82f);
                cb.stroke();
            }
            cb.restoreState();
        }

        private boolean filled(Row row, int r, int c) {
            return merges.containsKey(key(r, c)) || (row.getCell(c) != null && !text(row.getCell(c)).isEmpty());
        }

        private void drawPictures(PdfContentByte cb, int from, int to, float scale, float offsetX) throws IOException, DocumentException {
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            if (drawing == null) return;
            float yBase = page.getHeight() - top;
            float rowOrigin = rowY[from - firstRow];
            for (XSSFShape s : drawing.getShapes()) {
                if (!(s instanceof XSSFPicture p)) continue;
                org.apache.poi.ss.usermodel.ClientAnchor a = p.getClientAnchor();
                int r = a.getRow1(), c = a.getCol1();
                if (r < from || r > to || c < firstCol || c > lastCol) continue;
                long cx = p.getCTPicture().getSpPr().getXfrm().getExt().getCx();
                long cy = p.getCTPicture().getSpPr().getXfrm().getExt().getCy();
                float w = cx / 12700f * scale, h = cy / 12700f * scale;
                float x = px(c, scale, offsetX) + a.getDx1() / 12700f * scale;
                float y = yBase - (rowY[r - firstRow] - rowOrigin + a.getDy1() / 12700f) * scale - h;
                Image img = Image.getInstance(p.getPictureData().getData());
                img.scaleAbsolute(w, h);
                img.setAbsolutePosition(x, y);
                cb.addImage(img);
            }
        }

        private String text(Cell cell) {
            if (cell == null) return "";
            CellType t = cell.getCellType();
            if (t == CellType.BLANK) return "";
            try {
                return formatter.formatCellValue(cell, eval);
            } catch (RuntimeException e) {
                // A formula the evaluator cannot work out still shows its cached value.
                return t == CellType.FORMULA ? formatter.formatRawCellContents(cell.getNumericCellValue(), cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString()) : "";
            }
        }

        private boolean isNumber(Cell cell) {
            CellType t = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
            return t == CellType.NUMERIC;
        }

        /** Times or Helvetica as the workbook asks; text the standard fonts cannot show (₱) uses the embedded Unicode font. */
        private BaseFont font(XSSFFont f, String value) {
            boolean unicode = !CP1252.newEncoder().canEncode(value);
            String name = f.getFontName() == null ? "" : f.getFontName().toLowerCase();
            boolean serif = name.contains("times") || name.contains("cambria") || name.contains("georgia");
            String key = unicode ? "unicode" : (serif ? "t" : "h") + (f.getBold() ? "b" : "") + (f.getItalic() ? "i" : "");
            return fonts.computeIfAbsent(key, k -> {
                try {
                    if (unicode) return BaseFont.createFont("font-fallback/LiberationSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    String std = serif
                            ? (f.getBold() && f.getItalic() ? BaseFont.TIMES_BOLDITALIC : f.getBold() ? BaseFont.TIMES_BOLD : f.getItalic() ? BaseFont.TIMES_ITALIC : BaseFont.TIMES_ROMAN)
                            : (f.getBold() && f.getItalic() ? BaseFont.HELVETICA_BOLDOBLIQUE : f.getBold() ? BaseFont.HELVETICA_BOLD : f.getItalic() ? BaseFont.HELVETICA_OBLIQUE : BaseFont.HELVETICA);
                    return BaseFont.createFont(std, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                } catch (DocumentException | IOException e) {
                    throw new IllegalStateException("Could not load a PDF font", e);
                }
            });
        }

        private static List<String> wrap(String value, BaseFont bf, float size, float width) {
            List<String> out = new ArrayList<>();
            for (String para : value.split("\n", -1)) {
                StringBuilder ln = new StringBuilder();
                for (String word : para.split(" ")) {
                    String next = ln.isEmpty() ? word : ln + " " + word;
                    if (bf.getWidthPoint(next, size) <= width) {
                        ln.setLength(0);
                        ln.append(next);
                        continue;
                    }
                    if (!ln.isEmpty()) out.add(ln.toString());
                    ln.setLength(0);
                    // A word wider than the cell breaks between letters, as in Excel.
                    for (char ch : word.toCharArray()) {
                        if (!ln.isEmpty() && bf.getWidthPoint(ln.toString() + ch, size) > width) {
                            out.add(ln.toString());
                            ln.setLength(0);
                        }
                        ln.append(ch);
                    }
                }
                out.add(ln.toString());
            }
            return out;
        }

        private static long key(int r, int c) {
            return ((long) r << 20) | c;
        }
    }
}

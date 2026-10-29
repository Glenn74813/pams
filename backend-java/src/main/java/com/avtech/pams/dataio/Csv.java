package com.avtech.pams.dataio;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** RFC 4180 CSV reading and writing (quoted fields, embedded commas, quotes and line breaks). */
final class Csv {

    static final String BOM = "﻿";

    private Csv() {
    }

    /** UTF-8 (with or without BOM); falls back to Windows-1252, which is what Excel's plain "CSV" saves. */
    static String decode(byte[] bytes) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return s.startsWith(BOM) ? s.substring(1) : s;
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /** All records; a line with only empty fields is skipped. Each record carries its starting line number. */
    static List<Record> parse(String text) {
        List<Record> out = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int line = 1;
        int recordLine = 1;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') line++;
                    cur.append(c);
                }
                continue;
            }
            if (c == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
                fieldStarted = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                fields.add(cur.toString());
                add(out, fields, recordLine);
                fields = new ArrayList<>();
                cur.setLength(0);
                fieldStarted = false;
                line++;
                recordLine = line;
            } else {
                cur.append(c);
                fieldStarted = true;
            }
        }
        if (quoted) throw new IllegalArgumentException("A quoted field starting on line " + recordLine + " is never closed.");
        if (fieldStarted || cur.length() > 0 || !fields.isEmpty()) {
            fields.add(cur.toString());
            add(out, fields, recordLine);
        }
        return out;
    }

    private static void add(List<Record> out, List<String> fields, int line) {
        if (fields.stream().allMatch(f -> f.trim().isEmpty())) return;
        out.add(new Record(line, fields));
    }

    record Record(int line, List<String> fields) {
    }

    /** A field as written in the full export: quoted only when needed, value untouched. */
    static String raw(String v) {
        if (v == null || v.isEmpty()) return "";
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf('\r') < 0
                && !Character.isWhitespace(v.charAt(0)) && !Character.isWhitespace(v.charAt(v.length() - 1))) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** A field for a report opened in a spreadsheet: always quoted, and formulas neutralised. */
    static String safe(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    static void row(Writer w, List<String> cells) throws IOException {
        w.write(String.join(",", cells));
        w.write("\r\n");
    }
}

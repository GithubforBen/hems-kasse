package de.hems.kasse.export;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Tiny CSV writer for Excel-friendly German exports.
 *
 * <ul>
 *   <li>Field separator: {@code ;} (German Excel default — comma would clash with decimals).</li>
 *   <li>Decimals: {@code ,} via German locale.</li>
 *   <li>Encoding: UTF-8 with a leading BOM so Excel renders umlauts correctly.</li>
 *   <li>Line endings: {@code CRLF}.</li>
 *   <li>Strings containing the separator, a quote, or a newline are wrapped in
 *       double quotes; embedded quotes are doubled.</li>
 * </ul>
 */
public class CsvWriter {

    public static final char SEP = ';';
    public static final String CRLF = "\r\n";
    public static final char BOM = '﻿';

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat EURO;
    static {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.GERMANY);
        EURO = new DecimalFormat("0.00", s);
        EURO.setGroupingUsed(false);
    }

    private final StringWriter out = new StringWriter(4096);
    private boolean firstRowWritten = false;

    public CsvWriter() {
        out.write(BOM);
    }

    public CsvWriter row(Object... cells) {
        if (firstRowWritten) out.write(CRLF);
        firstRowWritten = true;
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) out.write(SEP);
            out.write(escape(cells[i]));
        }
        return this;
    }

    public CsvWriter rows(List<List<Object>> rows) {
        for (var r : rows) row(r.toArray());
        return this;
    }

    /** Blank line — useful when stacking summary + detail blocks in one file. */
    public CsvWriter blank() {
        if (firstRowWritten) out.write(CRLF);
        firstRowWritten = true;
        return this;
    }

    public String toCsv() {
        return out.toString() + CRLF; // trailing newline for tidiness
    }

    // ---------- formatters ----------

    /** Format integer cents as German money string ({@code 12,50}), no currency symbol. */
    public static String euro(Integer cents) {
        if (cents == null) return "";
        return EURO.format(BigDecimal.valueOf(cents).movePointLeft(2));
    }

    /** Same as {@link #euro} but prefixes a {@code +} for positive values — handy for "Diff". */
    public static String signedEuro(Integer cents) {
        if (cents == null) return "";
        String s = euro(cents);
        return cents > 0 ? "+" + s : s;
    }

    public static String date(Instant ts) {
        return ts == null ? "" : DATE.format(LocalDateTime.ofInstant(ts, BERLIN));
    }

    public static String time(Instant ts) {
        return ts == null ? "" : TIME.format(LocalDateTime.ofInstant(ts, BERLIN));
    }

    public static String dateTime(Instant ts) {
        return ts == null ? "" : DATETIME.format(LocalDateTime.ofInstant(ts, BERLIN));
    }

    // ---------- internals ----------

    private static final java.util.regex.Pattern NUMERIC =
            java.util.regex.Pattern.compile("[+-]?\\d+([.,]\\d+)?");

    static boolean looksLikeFormula(String s) {
        if (s.isEmpty()) return false;
        char first = s.charAt(0);
        if (first != '=' && first != '+' && first != '-' && first != '@'
                && first != '\t' && first != '\r') {
            return false;
        }
        // Exempt plain signed numbers (e.g. "+3,00", "-1,25") — useful for the
        // Diff column and Excel happily parses them as numeric.
        return !NUMERIC.matcher(s).matches();
    }

    private static String escape(Object cell) {
        if (cell == null) return "";
        String s = cell.toString();
        // Prevent CSV formula injection (CWE-1236): a leading =, +, -, @, tab or CR
        // would be parsed as a formula by Excel / LibreOffice / Sheets and could
        // execute arbitrary commands (e.g. =cmd|'/c calc'!A1). Signed numbers
        // produced by signedEuro (e.g. "+3,00", "-1,25") are exempt so the Diff
        // column still parses as a number.
        if (looksLikeFormula(s)) {
            s = "'" + s;
        }
        boolean needsQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == SEP || c == '"' || c == '\n' || c == '\r') { needsQuotes = true; break; }
        }
        if (!needsQuotes) return s;
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append('"');
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }
}

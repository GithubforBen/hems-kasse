package de.hems.kasse.export;

import java.util.Locale;

/**
 * The shape of a CSV export. Keep the slug stable — it's used as the {@code ?type=} query param
 * and in the suggested filename.
 */
public enum ExportType {
    /** One row per closed shift: dates, totals, soll/ist, diff. */
    SHIFTS("schichten"),
    /** One row per individual sale receipt. */
    SALES("verkaeufe"),
    /** One row per cart line (product × qty in a single sale) — most granular. */
    ITEMS("artikel"),
    /** Aggregated per-product totals (count + revenue). */
    PRODUCTS("produkte");

    private final String slug;

    ExportType(String slug) { this.slug = slug; }

    public String slug() { return slug; }

    public static ExportType from(String raw) {
        if (raw == null) return SHIFTS;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "shifts", "schichten" -> SHIFTS;
            case "sales", "verkaeufe", "verkäufe" -> SALES;
            case "items", "artikel" -> ITEMS;
            case "products", "produkte" -> PRODUCTS;
            default -> throw new IllegalArgumentException("Unbekannter Export-Typ: " + raw);
        };
    }
}

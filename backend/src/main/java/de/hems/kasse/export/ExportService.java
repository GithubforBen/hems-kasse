package de.hems.kasse.export;

import de.hems.kasse.sales.Sale;
import de.hems.kasse.sales.SaleItem;
import de.hems.kasse.sales.SaleRepository;
import de.hems.kasse.shift.Shift;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.hems.kasse.export.CsvWriter.*;

/**
 * Renders the four CSV report types defined by {@link ExportType}.
 *
 * <p>All currency columns are integer cents in the database but printed as German
 * decimal strings ({@code 12,50}). The "Diff" column gets a {@code +} prefix when positive.</p>
 */
@Service
public class ExportService {

    private final SaleRepository sales;

    public ExportService(SaleRepository sales) {
        this.sales = sales;
    }

    /** Per-shift summary table — {@code shifts.csv}. */
    public String shiftsCsv(List<Shift> shifts) {
        var w = new CsvWriter().row(
                "Schicht-ID", "Verkäufer:in", "Gruppe", "Kassette", "Rolle",
                "Gestartet", "Abgeschlossen",
                "Anfangsbestand (€)",
                "Umsatz Bar (€)", "Umsatz Karte (€)", "Umsatz PayPal (€)", "Umsatz gesamt (€)",
                "Soll Bar (€)", "Ist Bar (€)", "Diff (€)",
                "Bons", "Artikel verkauft",
                "Anmerkungen");
        for (Shift s : shifts) {
            w.row(
                    s.getId(),
                    s.getUserName(),
                    nullToEmpty(s.getGruppe()),
                    nullToEmpty(s.getRegisterName()),
                    s.getRole(),
                    dateTime(s.getStartedAt()),
                    dateTime(s.getClosedAt()),
                    euro(s.getOpeningCashCents()),
                    euro(s.getCashSalesCents()),
                    euro(s.getCardSalesCents()),
                    euro(s.getPaypalSalesCents()),
                    euro(s.getTotalSalesCents()),
                    euro(s.getExpectedCashCents()),
                    euro(s.getCountedCashCents()),
                    signedEuro(s.getDiffCents()),
                    or0(s.getSalesCount()),
                    or0(s.getItemsSold()),
                    nullToEmpty(s.getNotes()));
        }
        return w.toCsv();
    }

    /** One row per sale receipt — {@code verkaeufe.csv}. */
    public String salesCsv(List<Shift> shifts) {
        var w = new CsvWriter().row(
                "Schicht-ID", "Verkäufer:in", "Gruppe",
                "Datum", "Uhrzeit",
                "Bon-Nr.", "Transaktions-ID", "Zahlungsart",
                "Summe (€)", "Gegeben (€)", "Rückgeld (€)",
                "Artikel-Anzahl", "Artikel");
        for (Shift s : shifts) {
            List<Sale> shiftSales = sales.findAllByShiftIdOrderByTsDesc(s.getId());
            // Newest first looks nice in lists, but for export users usually want chronological:
            for (int i = shiftSales.size() - 1; i >= 0; i--) {
                Sale x = shiftSales.get(i);
                int bonNr = shiftSales.size() - i;
                int itemCount = x.getItems().stream().mapToInt(SaleItem::getQty).sum();
                String summary = x.getItems().stream()
                        .map(it -> it.getQty() + "× " + it.getName())
                        .reduce((a, b) -> a + ", " + b).orElse("");
                w.row(
                        s.getId(),
                        s.getUserName(),
                        nullToEmpty(s.getGruppe()),
                        date(x.getTs()),
                        time(x.getTs()),
                        bonNr,
                        x.getTransactionRef(),
                        x.getMethod().name(),
                        euro(x.getTotalCents()),
                        euro(x.getGivenCents()),
                        euro(x.getChangeCents()),
                        itemCount,
                        summary);
            }
        }
        return w.toCsv();
    }

    /** One row per line item — {@code artikel.csv}, most granular. */
    public String itemsCsv(List<Shift> shifts) {
        var w = new CsvWriter().row(
                "Schicht-ID", "Verkäufer:in", "Gruppe",
                "Datum", "Uhrzeit",
                "Bon-Nr.", "Transaktions-ID", "Zahlungsart",
                "Produkt", "Menge", "Einzelpreis (€)", "Zeilensumme (€)");
        for (Shift s : shifts) {
            List<Sale> shiftSales = sales.findAllByShiftIdOrderByTsDesc(s.getId());
            for (int i = shiftSales.size() - 1; i >= 0; i--) {
                Sale x = shiftSales.get(i);
                int bonNr = shiftSales.size() - i;
                for (SaleItem it : x.getItems()) {
                    w.row(
                            s.getId(),
                            s.getUserName(),
                            nullToEmpty(s.getGruppe()),
                            date(x.getTs()),
                            time(x.getTs()),
                            bonNr,
                            x.getTransactionRef(),
                            x.getMethod().name(),
                            it.getName(),
                            it.getQty(),
                            euro(it.getPriceCents()),
                            euro(it.getPriceCents() * it.getQty()));
                }
            }
        }
        return w.toCsv();
    }

    /** Aggregated per-product totals — {@code produkte.csv}. */
    public String productsCsv(List<Shift> shifts) {
        // Keyed by product name (id may be null for items whose product has since been deleted).
        Map<String, Agg> by = new LinkedHashMap<>();
        int totalQty = 0;
        int totalRev = 0;
        for (Shift s : shifts) {
            for (Sale x : sales.findAllByShiftIdOrderByTsDesc(s.getId())) {
                for (SaleItem it : x.getItems()) {
                    Agg a = by.computeIfAbsent(it.getName(), k -> new Agg());
                    a.qty += it.getQty();
                    a.rev += it.getPriceCents() * it.getQty();
                    a.bons++;
                    totalQty += it.getQty();
                    totalRev += it.getPriceCents() * it.getQty();
                }
            }
        }
        // Sort by revenue desc, then by qty desc, then name.
        var entries = by.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue().rev, a.getValue().rev);
                    if (c != 0) return c;
                    c = Integer.compare(b.getValue().qty, a.getValue().qty);
                    return c != 0 ? c : a.getKey().compareToIgnoreCase(b.getKey());
                })
                .toList();

        var w = new CsvWriter().row(
                "Rang", "Produkt", "Menge",
                "Anteil Menge (%)", "Umsatz (€)", "Anteil Umsatz (%)",
                "Ø Preis (€)", "Erscheint auf # Bons");
        int rank = 1;
        for (var e : entries) {
            Agg a = e.getValue();
            int avg = a.qty == 0 ? 0 : (int) Math.round(a.rev / (double) a.qty);
            String pctQty = totalQty == 0 ? "0,0" : String.format(java.util.Locale.GERMANY, "%.1f", 100.0 * a.qty / totalQty);
            String pctRev = totalRev == 0 ? "0,0" : String.format(java.util.Locale.GERMANY, "%.1f", 100.0 * a.rev / totalRev);
            w.row(rank++, e.getKey(), a.qty, pctQty, euro(a.rev), pctRev, euro(avg), a.bons);
        }
        // Summary line.
        w.blank().row("", "Gesamt", totalQty, "100,0", euro(totalRev), "100,0", "", "");
        return w.toCsv();
    }

    public String render(ExportType type, List<Shift> shifts) {
        return switch (type) {
            case SHIFTS -> shiftsCsv(shifts);
            case SALES -> salesCsv(shifts);
            case ITEMS -> itemsCsv(shifts);
            case PRODUCTS -> productsCsv(shifts);
        };
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static int or0(Integer i) { return i == null ? 0 : i; }

    private static final class Agg {
        int qty;
        int rev;
        int bons;
    }
}

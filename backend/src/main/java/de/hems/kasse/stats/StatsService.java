package de.hems.kasse.stats;

import de.hems.kasse.inventory.InventoryCount;
import de.hems.kasse.inventory.InventoryCountLine;
import de.hems.kasse.inventory.InventoryCountRepository;
import de.hems.kasse.sales.Sale;
import de.hems.kasse.sales.SaleItem;
import de.hems.kasse.sales.SaleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates sales and Lager data into chart-ready statistics: top sellers, time-of-day /
 * weekday / daily trends, per-product sale-time histograms ("Verkaufszeiten") and Fehlbestand
 * frequency derived from the Inventur history. Pure read-side aggregation — no records written.
 */
@Service
public class StatsService {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final int TOP_N = 12;

    private final SaleRepository sales;
    private final InventoryCountRepository counts;

    public StatsService(SaleRepository sales, InventoryCountRepository counts) {
        this.sales = sales;
        this.counts = counts;
    }

    public record ProductStat(String name, int qty, int revenueCents) {}
    public record TimeBucket(int bucket, int qty, int revenueCents) {}
    public record DailyPoint(String date, int qty, int revenueCents, int sales) {}
    public record ProductHourPoint(String product, int hour, int qty) {}
    public record ShortageStat(String name, int countsWithShortage, int totalShortage, int worstShortage) {}

    public record Stats(
            Instant from, Instant to,
            int totalRevenueCents, int totalQty, int totalSales,
            List<ProductStat> topByQty,
            List<ProductStat> topByRevenue,
            List<TimeBucket> byHour,
            List<TimeBucket> byWeekday,
            List<DailyPoint> daily,
            List<ProductHourPoint> productHours,
            List<ShortageStat> shortages
    ) {}

    public Stats compute(Instant from, Instant to) {
        List<Sale> list = sales.findAllByTsBetweenOrderByTsAsc(from, to);

        Map<String, Agg> byProduct = new LinkedHashMap<>();
        int[] hourQty = new int[24];
        int[] hourRev = new int[24];
        int[] weekdayQty = new int[7];
        int[] weekdayRev = new int[7];
        Map<String, DayAgg> byDay = new LinkedHashMap<>();
        Map<String, int[]> productHourQty = new LinkedHashMap<>();

        int totalRev = 0;
        int totalQty = 0;

        for (Sale s : list) {
            LocalDateTime ldt = LocalDateTime.ofInstant(s.getTs(), BERLIN);
            int hour = ldt.getHour();
            int weekday = ldt.getDayOfWeek().getValue() - 1; // 0 = Montag … 6 = Sonntag
            String day = ldt.toLocalDate().toString(); // yyyy-MM-dd

            byDay.computeIfAbsent(day, k -> new DayAgg()).sales++;

            for (SaleItem it : s.getItems()) {
                int rev = it.getPriceCents() * it.getQty();

                Agg a = byProduct.computeIfAbsent(it.getName(), k -> new Agg());
                a.qty += it.getQty();
                a.rev += rev;

                hourQty[hour] += it.getQty();
                hourRev[hour] += rev;
                weekdayQty[weekday] += it.getQty();
                weekdayRev[weekday] += rev;

                DayAgg d = byDay.get(day);
                d.qty += it.getQty();
                d.rev += rev;

                productHourQty.computeIfAbsent(it.getName(), k -> new int[24])[hour] += it.getQty();

                totalRev += rev;
                totalQty += it.getQty();
            }
        }

        var topByQty = byProduct.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().qty, a.getValue().qty))
                .limit(TOP_N)
                .map(e -> new ProductStat(e.getKey(), e.getValue().qty, e.getValue().rev))
                .toList();
        var topByRevenue = byProduct.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().rev, a.getValue().rev))
                .limit(TOP_N)
                .map(e -> new ProductStat(e.getKey(), e.getValue().qty, e.getValue().rev))
                .toList();

        List<TimeBucket> byHour = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) byHour.add(new TimeBucket(h, hourQty[h], hourRev[h]));

        List<TimeBucket> byWeekday = new ArrayList<>(7);
        for (int d = 0; d < 7; d++) byWeekday.add(new TimeBucket(d, weekdayQty[d], weekdayRev[d]));

        var daily = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyPoint(e.getKey(), e.getValue().qty, e.getValue().rev, e.getValue().sales))
                .toList();

        List<ProductHourPoint> productHours = new ArrayList<>();
        for (var e : productHourQty.entrySet()) {
            for (int h = 0; h < 24; h++) {
                int qty = e.getValue()[h];
                if (qty > 0) productHours.add(new ProductHourPoint(e.getKey(), h, qty));
            }
        }

        return new Stats(from, to, totalRev, totalQty, list.size(),
                topByQty, topByRevenue, byHour, byWeekday, daily, productHours, shortages());
    }

    /** Fehlbestand-Häufigkeit: per product, how often and how badly Inventuren found a shortage. */
    private List<ShortageStat> shortages() {
        Map<String, ShortageAgg> by = new LinkedHashMap<>();
        for (InventoryCount c : counts.findAllByOrderByTsDesc()) {
            for (InventoryCountLine l : c.getLines()) {
                if (l.getDiffQty() >= 0) continue;
                int shortage = -l.getDiffQty();
                ShortageAgg a = by.computeIfAbsent(l.getProductName(), k -> new ShortageAgg());
                a.counts++;
                a.total += shortage;
                a.worst = Math.max(a.worst, shortage);
            }
        }
        return by.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().total, a.getValue().total))
                .map(e -> new ShortageStat(e.getKey(), e.getValue().counts, e.getValue().total, e.getValue().worst))
                .toList();
    }

    private static final class Agg { int qty; int rev; }
    private static final class DayAgg { int qty; int rev; int sales; }
    private static final class ShortageAgg { int counts; int total; int worst; }
}

package de.hems.kasse.inventory;

import de.hems.kasse.catalog.Product;
import de.hems.kasse.catalog.ProductRepository;
import de.hems.kasse.sales.SaleItemRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

/**
 * Ledger/replay-style stock tracking: admins record Inventuren (counts) and Wareneingänge
 * (intakes); Fehlbestand (shortage) is derived by comparing counted stock against expected
 * stock — baseline (last count) + intakes − consumption since that baseline. Mirrors the
 * codebase's preference for immutable historical records with computed fields frozen at
 * write time (see {@link de.hems.kasse.shift.Shift#getExpectedCashCents()}).
 *
 * <p>MVP baseline strategy: a single global baseline (the most recent Inventur, assumed to
 * cover every tracked product) rather than a per-product "last counted" timestamp.
 */
@Service
public class InventoryService {

    private final InventoryCountRepository counts;
    private final StockIntakeRepository intakes;
    private final SaleItemRepository saleItems;
    private final ProductRepository products;

    public InventoryService(InventoryCountRepository counts, StockIntakeRepository intakes,
                            SaleItemRepository saleItems, ProductRepository products) {
        this.counts = counts;
        this.intakes = intakes;
        this.saleItems = saleItems;
        this.products = products;
    }

    public record CountLineInput(UUID productId, int countedQty) {}
    public record IntakeLineInput(UUID productId, int qty) {}
    public record ExpectedStockLine(UUID productId, String name, int expectedQty, Instant baselineTs) {}

    @Transactional
    public InventoryCount recordCount(String byName, List<CountLineInput> lineInputs, String notes) {
        Instant now = Instant.now();
        InventoryCount previous = counts.findFirstByOrderByTsDesc().orElse(null);
        Instant from = previous != null ? previous.getTs() : Instant.EPOCH;

        Map<UUID, Integer> baseline = baselineMap(previous);
        Map<UUID, Long> intakeQty = sumByProduct(intakes.intakeQuantities(from, now));
        Map<UUID, Long> consumedQty = consumptionSince(from, now);

        List<InventoryCountLine> lines = new ArrayList<>();
        for (CountLineInput in : lineInputs) {
            Product p = products.findById(in.productId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unbekanntes Produkt: " + in.productId()));
            if (p.isVariable()) {
                throw new ResponseStatusException(BAD_REQUEST, "„" + p.getName() + "“ hat einen freien Preis und keinen Lagerbestand");
            }
            int expected = (int) (baseline.getOrDefault(p.getId(), 0)
                    + intakeQty.getOrDefault(p.getId(), 0L)
                    - consumedQty.getOrDefault(p.getId(), 0L));
            lines.add(InventoryCountLine.builder()
                    .id(UUID.randomUUID())
                    .productId(p.getId())
                    .productName(p.getName())
                    .countedQty(in.countedQty())
                    .expectedQty(expected)
                    .diffQty(in.countedQty() - expected)
                    .build());
        }

        InventoryCount count = InventoryCount.builder()
                .id(UUID.randomUUID())
                .ts(now)
                .byName(byName)
                .notes(notes)
                .lines(lines)
                .build();
        return counts.save(count);
    }

    @Transactional
    public StockIntake recordIntake(String byName, List<IntakeLineInput> lineInputs, String notes) {
        List<StockIntakeLine> lines = new ArrayList<>();
        for (IntakeLineInput in : lineInputs) {
            Product p = products.findById(in.productId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unbekanntes Produkt: " + in.productId()));
            if (p.isVariable()) {
                throw new ResponseStatusException(BAD_REQUEST, "„" + p.getName() + "“ hat einen freien Preis und keinen Lagerbestand");
            }
            lines.add(StockIntakeLine.builder()
                    .id(UUID.randomUUID())
                    .productId(p.getId())
                    .productName(p.getName())
                    .qty(in.qty())
                    .build());
        }

        StockIntake intake = StockIntake.builder()
                .id(UUID.randomUUID())
                .ts(Instant.now())
                .byName(byName)
                .notes(notes)
                .lines(lines)
                .build();
        return intakes.save(intake);
    }

    public List<InventoryCount> countHistory() {
        return counts.findAllByOrderByTsDesc();
    }

    public InventoryCount countById(UUID id) {
        return counts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    public List<StockIntake> intakeHistory() {
        return intakes.findAllByOrderByTsDesc();
    }

    public StockIntake intakeById(UUID id) {
        return intakes.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    /** Live projection (no record created): expected stock right now, derived from the latest Inventur. */
    public List<ExpectedStockLine> currentExpectedStock() {
        Instant now = Instant.now();
        InventoryCount latest = counts.findFirstByOrderByTsDesc().orElse(null);
        Instant from = latest != null ? latest.getTs() : Instant.EPOCH;

        Map<UUID, Integer> baseline = baselineMap(latest);
        Map<UUID, Long> intakeQty = sumByProduct(intakes.intakeQuantities(from, now));
        Map<UUID, Long> consumedQty = consumptionSince(from, now);
        Instant baselineTs = latest != null ? latest.getTs() : null;

        return products.findAll().stream()
                .filter(p -> !p.isVariable() && !p.isComposed())
                .sorted(Comparator.comparing(Product::getName))
                .map(p -> new ExpectedStockLine(
                        p.getId(),
                        p.getName(),
                        (int) (baseline.getOrDefault(p.getId(), 0)
                                + intakeQty.getOrDefault(p.getId(), 0L)
                                - consumedQty.getOrDefault(p.getId(), 0L)),
                        baselineTs))
                .toList();
    }

    /** Sum of direct simple-product sales plus resolved Verkaufstasten-component consumption in [from, to). */
    private Map<UUID, Long> consumptionSince(Instant from, Instant to) {
        Map<UUID, Long> result = new java.util.HashMap<>(sumByProduct(saleItems.directConsumption(from, to)));
        for (var row : saleItems.componentConsumption(from, to)) {
            result.merge(row.getProductId(), row.getQty(), Long::sum);
        }
        return result;
    }

    private static Map<UUID, Integer> baselineMap(InventoryCount previous) {
        if (previous == null) return Map.of();
        return previous.getLines().stream()
                .filter(l -> l.getProductId() != null)
                .collect(Collectors.toMap(InventoryCountLine::getProductId, InventoryCountLine::getCountedQty, (a, b) -> b));
    }

    private static Map<UUID, Long> sumByProduct(List<ProductQty> rows) {
        return rows.stream().collect(Collectors.toMap(ProductQty::getProductId, ProductQty::getQty, Long::sum));
    }
}

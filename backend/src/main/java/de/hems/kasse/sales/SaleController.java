package de.hems.kasse.sales;

import de.hems.kasse.auth.KassePrincipal;
import de.hems.kasse.catalog.Product;
import de.hems.kasse.catalog.ProductRepository;
import de.hems.kasse.shift.Shift;
import de.hems.kasse.shift.ShiftService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final ShiftService shifts;

    public SaleController(SaleRepository sales, ProductRepository products, ShiftService shifts) {
        this.sales = sales;
        this.products = products;
        this.shifts = shifts;
    }

    /** Matches PaymentController.AMOUNT_MAX_CENTS — nothing at a school cake sale costs more. */
    static final int MAX_TOTAL_CENTS = 99_999_999;

    public record NewSaleItem(@NotNull UUID productId, @Min(1) @Max(9_999) int qty,
                              @Min(0) @Max(MAX_TOTAL_CENTS) Integer priceCentsOverride,
                              @Min(0) @Max(100) Integer discountPercent) {}
    public record NewSale(@NotNull String method,
                          @Min(0) @Max(MAX_TOTAL_CENTS) int givenCents,
                          @NotEmpty List<@Valid NewSaleItem> items,
                          @Size(max = 64) String transactionRef) {}

    public record SaleItemComponentDto(UUID productId, String name, int qty) {
        static SaleItemComponentDto of(SaleItemComponent c) {
            return new SaleItemComponentDto(c.getProductId(), c.getName(), c.getQty());
        }
    }
    public record SaleItemDto(UUID productId, String name, int priceCents, int listPriceCents, int discountPercent,
                              int qty, String color, List<SaleItemComponentDto> components) {}
    public record SaleDto(UUID id, Instant ts, String method,
                          int totalCents, int givenCents, int changeCents,
                          String byName, List<SaleItemDto> items, String transactionRef) {
        static SaleDto of(Sale s) {
            var items = s.getItems().stream()
                    .map(it -> new SaleItemDto(it.getProductId(), it.getName(), it.getPriceCents(),
                            it.getListPriceCents(), it.getDiscountPercent(), it.getQty(), it.getColor(),
                            it.getComponents().stream().map(SaleItemComponentDto::of).toList()))
                    .toList();
            return new SaleDto(s.getId(), s.getTs(), s.getMethod().name(),
                    s.getTotalCents(), s.getGivenCents(), s.getChangeCents(),
                    s.getByName(), items, s.getTransactionRef());
        }
    }

    @GetMapping
    public List<SaleDto> listForCurrentShift(@AuthenticationPrincipal KassePrincipal p,
                                             @RequestHeader(name = "X-Kasse-Register-Id", required = false) UUID registerId) {
        Shift s = shifts.currentOrOpen(p, registerId);
        return sales.findAllByShiftIdOrderByTsDesc(s.getId()).stream().map(SaleDto::of).toList();
    }

    @PostMapping
    @Transactional
    public SaleDto record(@AuthenticationPrincipal KassePrincipal p,
                          @RequestHeader(name = "X-Kasse-Register-Id", required = false) UUID registerId,
                          @RequestBody @Valid NewSale body) {
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(body.method().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown payment method");
        }

        Shift shift = shifts.currentOrOpen(p, registerId);
        List<SaleItem> items = new ArrayList<>(body.items().size());
        long totalLong = 0; // long so oversized carts can't overflow into a negative total
        for (NewSaleItem ni : body.items()) {
            Product prod = products.findById(ni.productId())
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                            "Unbekanntes Produkt: " + ni.productId()));
            int listPrice = (prod.isVariable() && ni.priceCentsOverride() != null)
                    ? ni.priceCentsOverride() : prod.getPriceCents();
            int discountPercent = ni.discountPercent() == null ? 0 : ni.discountPercent();
            if (discountPercent > 0 && !prod.isDiscountable()) {
                throw new ResponseStatusException(BAD_REQUEST, "„" + prod.getName() + "“ ist nicht rabattierbar");
            }
            int priceCents = applyDiscount(listPrice, discountPercent, prod.getMinPriceCents());
            totalLong += (long) priceCents * ni.qty();

            List<SaleItemComponent> comps = prod.getComponents().stream()
                    .map(pc -> SaleItemComponent.builder()
                            .id(UUID.randomUUID())
                            .productId(pc.getComponentProduct().getId())
                            .name(pc.getComponentProduct().getName())
                            .qty(pc.getQty() * ni.qty())
                            .build())
                    .toList();

            items.add(SaleItem.builder()
                    .id(UUID.randomUUID())
                    .productId(prod.getId())
                    .name(prod.getName())
                    .priceCents(priceCents)
                    .listPriceCents(listPrice)
                    .discountPercent(discountPercent)
                    .qty(ni.qty())
                    .color(prod.getColor())
                    .components(new ArrayList<>(comps))
                    .build());
        }

        if (totalLong > MAX_TOTAL_CENTS) {
            throw new ResponseStatusException(BAD_REQUEST, "Summe zu groß");
        }
        int total = (int) totalLong;

        int given = body.givenCents();
        int change = 0;
        if (method == PaymentMethod.BAR) {
            if (given < total) throw new ResponseStatusException(BAD_REQUEST, "Gegeben < Summe");
            change = given - total;
        } else { // KARTE / PAYPAL
            given = total;
        }

        UUID saleId = UUID.randomUUID();
        String txRef = resolveTransactionRef(body.transactionRef(), saleId);

        Sale sale = Sale.builder()
                .id(saleId)
                .shiftId(shift.getId())
                .ts(Instant.now())
                .method(method)
                .totalCents(total)
                .givenCents(given)
                .changeCents(change)
                .byName(p.name())
                .transactionRef(txRef)
                .items(items)
                .build();
        return SaleDto.of(sales.save(sale));
    }

    /**
     * Applies a percentage discount to a unit price, never dropping below the article's price floor.
     * Authoritative server-side computation — the client preview must match but is not trusted.
     */
    static int applyDiscount(int listPriceCents, int discountPercent, Integer minPriceCents) {
        if (discountPercent <= 0) return listPriceCents;
        int discounted = (int) Math.round(listPriceCents * (100 - discountPercent) / 100.0);
        if (minPriceCents != null && discounted < minPriceCents) {
            return Math.min(listPriceCents, minPriceCents);
        }
        return Math.max(0, discounted);
    }

    /** Uses the client-supplied ref (max 12 alphanumeric chars) or derives one from the sale UUID. */
    private static String resolveTransactionRef(String provided, UUID saleId) {
        if (provided != null && !provided.isBlank()) {
            String clean = provided.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (!clean.isEmpty()) return clean.substring(0, Math.min(clean.length(), 12));
        }
        return saleId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}

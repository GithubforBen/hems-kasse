package de.hems.kasse.shift;

import de.hems.kasse.auth.KassePrincipal;
import de.hems.kasse.sales.Sale;
import de.hems.kasse.sales.SaleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService service;
    private final ShiftRepository shifts;
    private final SaleRepository sales;

    public ShiftController(ShiftService service, ShiftRepository shifts, SaleRepository sales) {
        this.service = service;
        this.shifts = shifts;
        this.sales = sales;
    }

    public record ShiftDto(
            UUID id, String userName, String klasse, String role,
            Instant startedAt, Instant closedAt,
            int openingCashCents,
            Integer countedCashCents, Integer expectedCashCents, Integer diffCents,
            Integer cashSalesCents, Integer cardSalesCents, Integer totalSalesCents,
            Integer salesCount, Integer itemsSold,
            String notes
    ) {
        public static ShiftDto of(Shift s) {
            return new ShiftDto(s.getId(), s.getUserName(), s.getKlasse(), s.getRole(),
                    s.getStartedAt(), s.getClosedAt(),
                    s.getOpeningCashCents(),
                    s.getCountedCashCents(), s.getExpectedCashCents(), s.getDiffCents(),
                    s.getCashSalesCents(), s.getCardSalesCents(), s.getTotalSalesCents(),
                    s.getSalesCount(), s.getItemsSold(),
                    s.getNotes());
        }
    }

    public record SaleLine(UUID id, Instant ts, String method,
                           int totalCents, int givenCents, int changeCents,
                           String byName, List<SaleItemDto> items) {}
    public record SaleItemDto(String name, int priceCents, int qty, String color) {}

    public record ShiftDetailDto(ShiftDto shift, List<SaleLine> sales) {}

    public record PatchShift(@Min(0) Integer openingCashCents, String notes) {}
    public record CloseShift(@Min(0) int countedCashCents, String notes) {}

    @GetMapping("/current")
    public ShiftDto current(@AuthenticationPrincipal KassePrincipal p) {
        return ShiftDto.of(service.currentOrOpen(p));
    }

    @PatchMapping("/current")
    public ShiftDto patchCurrent(@AuthenticationPrincipal KassePrincipal p,
                                 @RequestBody @Valid PatchShift body) {
        return ShiftDto.of(service.setOpeningCash(p,
                body.openingCashCents() == null ? -1 : body.openingCashCents(),
                body.notes()));
    }

    @PostMapping("/current/close")
    public ShiftDto close(@AuthenticationPrincipal KassePrincipal p,
                          @RequestBody @Valid CloseShift body) {
        return ShiftDto.of(service.close(p, body.countedCashCents(), body.notes()));
    }

    @GetMapping("/mine")
    public List<ShiftDto> mine(@AuthenticationPrincipal KassePrincipal p) {
        return shifts.findAllBySubjectKeyAndClosedAtIsNotNullOrderByClosedAtDesc(p.subjectKey())
                .stream().map(ShiftDto::of).toList();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ShiftDto> all(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String klasse,
            @RequestParam(required = false) String q) {
        return shifts.searchClosed(from, to,
                klasse == null || klasse.isBlank() ? null : klasse,
                q == null || q.isBlank() ? null : q)
                .stream().map(ShiftDto::of).toList();
    }

    @GetMapping("/{id}")
    public ShiftDetailDto detail(@AuthenticationPrincipal KassePrincipal p, @PathVariable UUID id) {
        Shift s = service.forCaller(p, id);
        List<SaleLine> lines = sales.findAllByShiftIdOrderByTsDesc(s.getId()).stream()
                .map(this::toLine).toList();
        return new ShiftDetailDto(ShiftDto.of(s), lines);
    }

    private SaleLine toLine(Sale x) {
        var items = x.getItems().stream()
                .sorted(Comparator.comparing(it -> it.getName()))
                .map(it -> new SaleItemDto(it.getName(), it.getPriceCents(), it.getQty(), it.getColor()))
                .toList();
        return new SaleLine(x.getId(), x.getTs(), x.getMethod().name(),
                x.getTotalCents(), x.getGivenCents(), x.getChangeCents(),
                x.getByName(), items);
    }
}

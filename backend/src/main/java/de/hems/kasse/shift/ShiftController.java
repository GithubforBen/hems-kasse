package de.hems.kasse.shift;

import de.hems.kasse.auth.KassePrincipal;
import de.hems.kasse.export.ExportService;
import de.hems.kasse.export.ExportType;
import de.hems.kasse.sales.Sale;
import de.hems.kasse.sales.SaleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final ShiftService service;
    private final ShiftRepository shifts;
    private final SaleRepository sales;
    private final ExportService exports;

    public ShiftController(ShiftService service, ShiftRepository shifts, SaleRepository sales, ExportService exports) {
        this.service = service;
        this.shifts = shifts;
        this.sales = sales;
        this.exports = exports;
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

    // ----------------------------------------------------------------------
    // CSV exports
    // ----------------------------------------------------------------------

    /** Single shift, any of the four report types. Owner or admin only. */
    @GetMapping("/{id}/export.csv")
    public ResponseEntity<byte[]> exportOne(@AuthenticationPrincipal KassePrincipal p,
                                            @PathVariable UUID id,
                                            @RequestParam(defaultValue = "items") String type) {
        Shift s = service.forCaller(p, id);
        ExportType t = parseType(type);
        String body = exports.render(t, List.of(s));
        return csv(body, "schicht-" + shortId(s.getId()) + "-" + t.slug() + ".csv");
    }

    /** Caller's own closed shifts, aggregated according to {@code type}. */
    @GetMapping("/mine/export.csv")
    public ResponseEntity<byte[]> exportMine(@AuthenticationPrincipal KassePrincipal p,
                                             @RequestParam(defaultValue = "shifts") String type) {
        ExportType t = parseType(type);
        var list = shifts.findAllBySubjectKeyAndClosedAtIsNotNullOrderByClosedAtDesc(p.subjectKey());
        String body = exports.render(t, list);
        return csv(body, "meine-schichten-" + t.slug() + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv");
    }

    /** All closed shifts (admin), filterable by date range / klasse / name. */
    @GetMapping("/export.csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportAll(
            @RequestParam(defaultValue = "shifts") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String klasse,
            @RequestParam(required = false) String q) {
        ExportType t = parseType(type);
        var list = shifts.searchClosed(from, to,
                klasse == null || klasse.isBlank() ? null : klasse,
                q == null || q.isBlank() ? null : q);
        String body = exports.render(t, list);
        return csv(body, "schichten-" + t.slug() + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv");
    }

    // ----------------------------------------------------------------------
    private static ExportType parseType(String raw) {
        try { return ExportType.from(raw); }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static ResponseEntity<byte[]> csv(String body, String filename) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}

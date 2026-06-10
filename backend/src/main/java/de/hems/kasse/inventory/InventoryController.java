package de.hems.kasse.inventory;

import de.hems.kasse.auth.KassePrincipal;
import de.hems.kasse.export.CsvWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

import static de.hems.kasse.export.CsvWriter.dateTime;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Lager: Inventuren (Zählungen), Wareneingänge und der daraus abgeleitete Fehlbestand.
 * Reads are visible to any authenticated role ("soll man dann auch alles sehen können");
 * recording counts/intakes is admin-only.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    // ---------- DTOs ----------
    public record CountLineDto(UUID productId, String productName, int countedQty, int expectedQty, int diffQty) {
        static CountLineDto of(InventoryCountLine l) {
            return new CountLineDto(l.getProductId(), l.getProductName(), l.getCountedQty(), l.getExpectedQty(), l.getDiffQty());
        }
    }
    public record CountDto(UUID id, Instant ts, String byName, String notes, List<CountLineDto> lines) {
        static CountDto of(InventoryCount c) {
            var lines = c.getLines().stream()
                    .sorted(Comparator.comparing(InventoryCountLine::getProductName))
                    .map(CountLineDto::of).toList();
            return new CountDto(c.getId(), c.getTs(), c.getByName(), c.getNotes(), lines);
        }
    }

    public record IntakeLineDto(UUID productId, String productName, int qty) {
        static IntakeLineDto of(StockIntakeLine l) {
            return new IntakeLineDto(l.getProductId(), l.getProductName(), l.getQty());
        }
    }
    public record IntakeDto(UUID id, Instant ts, String byName, String notes, List<IntakeLineDto> lines) {
        static IntakeDto of(StockIntake i) {
            var lines = i.getLines().stream()
                    .sorted(Comparator.comparing(StockIntakeLine::getProductName))
                    .map(IntakeLineDto::of).toList();
            return new IntakeDto(i.getId(), i.getTs(), i.getByName(), i.getNotes(), lines);
        }
    }

    public record ExpectedStockDto(UUID productId, String name, int expectedQty, Instant baselineTs) {
        static ExpectedStockDto of(InventoryService.ExpectedStockLine l) {
            return new ExpectedStockDto(l.productId(), l.name(), l.expectedQty(), l.baselineTs());
        }
    }

    public record NewCountLine(@NotNull UUID productId, @Min(0) int countedQty) {}
    public record NewCount(@NotEmpty List<@Valid NewCountLine> lines, @Size(max = 2000) String notes) {}

    public record NewIntakeLine(@NotNull UUID productId, @Min(1) int qty) {}
    public record NewIntake(@NotEmpty List<@Valid NewIntakeLine> lines, @Size(max = 2000) String notes) {}

    // ---------- Reads ----------
    @GetMapping("/counts")
    public List<CountDto> counts() {
        return service.countHistory().stream().map(CountDto::of).toList();
    }

    @GetMapping("/counts/{id}")
    public CountDto count(@PathVariable UUID id) {
        return CountDto.of(service.countById(id));
    }

    @GetMapping("/intakes")
    public List<IntakeDto> intakes() {
        return service.intakeHistory().stream().map(IntakeDto::of).toList();
    }

    @GetMapping("/intakes/{id}")
    public IntakeDto intake(@PathVariable UUID id) {
        return IntakeDto.of(service.intakeById(id));
    }

    @GetMapping("/expected")
    public List<ExpectedStockDto> expected() {
        return service.currentExpectedStock().stream().map(ExpectedStockDto::of).toList();
    }

    // ---------- Writes ----------
    @PostMapping("/counts")
    @PreAuthorize("hasRole('ADMIN')")
    public CountDto recordCount(@AuthenticationPrincipal KassePrincipal p, @RequestBody @Valid NewCount body) {
        var lines = body.lines().stream()
                .map(l -> new InventoryService.CountLineInput(l.productId(), l.countedQty()))
                .toList();
        return CountDto.of(service.recordCount(p.name(), lines, body.notes()));
    }

    @PostMapping("/intakes")
    @PreAuthorize("hasRole('ADMIN')")
    public IntakeDto recordIntake(@AuthenticationPrincipal KassePrincipal p, @RequestBody @Valid NewIntake body) {
        var lines = body.lines().stream()
                .map(l -> new InventoryService.IntakeLineInput(l.productId(), l.qty()))
                .toList();
        return IntakeDto.of(service.recordIntake(p.name(), lines, body.notes()));
    }

    // ---------- CSV export ----------
    @GetMapping("/export.csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "counts") String type) {
        String stamp = LocalDate.now(ZoneOffset.UTC).toString();
        return switch (type) {
            case "counts" -> csv(countsCsv(), "inventuren-" + stamp + ".csv");
            case "intakes" -> csv(intakesCsv(), "wareneingaenge-" + stamp + ".csv");
            case "expected" -> csv(expectedCsv(), "lagerbestand-" + stamp + ".csv");
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unbekannter Export-Typ: " + type);
        };
    }

    private String countsCsv() {
        var w = new CsvWriter().row("Datum", "Erfasst von", "Produkt", "Erwartet", "Gezählt", "Fehlbestand", "Notiz");
        for (InventoryCount c : service.countHistory()) {
            for (InventoryCountLine l : c.getLines()) {
                w.row(dateTime(c.getTs()), c.getByName(), l.getProductName(),
                        l.getExpectedQty(), l.getCountedQty(), signedInt(l.getDiffQty()), nullToEmpty(c.getNotes()));
            }
        }
        return w.toCsv();
    }

    private String intakesCsv() {
        var w = new CsvWriter().row("Datum", "Erfasst von", "Produkt", "Menge", "Notiz");
        for (StockIntake i : service.intakeHistory()) {
            for (StockIntakeLine l : i.getLines()) {
                w.row(dateTime(i.getTs()), i.getByName(), l.getProductName(), l.getQty(), nullToEmpty(i.getNotes()));
            }
        }
        return w.toCsv();
    }

    private String expectedCsv() {
        var w = new CsvWriter().row("Produkt", "Erwartet", "Basis (letzte Inventur)");
        for (var l : service.currentExpectedStock()) {
            w.row(l.name(), l.expectedQty(), l.baselineTs() != null ? dateTime(l.baselineTs()) : "noch keine Inventur");
        }
        return w.toCsv();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String signedInt(int n) { return n > 0 ? "+" + n : String.valueOf(n); }

    private static ResponseEntity<byte[]> csv(String body, String filename) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}

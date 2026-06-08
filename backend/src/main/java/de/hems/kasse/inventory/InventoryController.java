package de.hems.kasse.inventory;

import de.hems.kasse.auth.KassePrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
}

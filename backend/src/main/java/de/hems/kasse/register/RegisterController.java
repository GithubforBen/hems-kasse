package de.hems.kasse.register;

import de.hems.kasse.export.CsvWriter;
import de.hems.kasse.shift.ShiftRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/registers")
public class RegisterController {

    private final RegisterRepository registers;
    private final ShiftRepository shifts;

    public RegisterController(RegisterRepository registers, ShiftRepository shifts) {
        this.registers = registers;
        this.shifts = shifts;
    }

    public record RegisterDto(UUID id, String name, int sortOrder, boolean active) {
        static RegisterDto of(Register r) {
            return new RegisterDto(r.getId(), r.getName(), r.getSortOrder(), r.isActive());
        }
    }

    public record NewRegister(@NotBlank @Size(max = 80) String name) {}
    public record PatchRegister(@Size(max = 80) String name, Integer sortOrder, Boolean active) {}

    /** Any authenticated caller — VERKAUF needs this list to pick a Kassette after login. */
    @GetMapping
    public List<RegisterDto> list() {
        return registers.findAllByOrderBySortOrderAsc().stream().map(RegisterDto::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RegisterDto create(@RequestBody @Valid NewRegister body) {
        int nextOrder = registers.findAll().stream().mapToInt(Register::getSortOrder).max().orElse(0) + 1;
        Register r = Register.builder()
                .id(UUID.randomUUID())
                .name(body.name().trim())
                .sortOrder(nextOrder)
                .active(true)
                .build();
        return RegisterDto.of(registers.save(r));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public RegisterDto patch(@PathVariable UUID id, @RequestBody @Valid PatchRegister body) {
        Register r = registers.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (body.name() != null && !body.name().isBlank()) r.setName(body.name().trim());
        if (body.sortOrder() != null) r.setSortOrder(body.sortOrder());
        if (body.active() != null) r.setActive(body.active());
        return RegisterDto.of(registers.save(r));
    }

    /** Soft-delete: refuse if an open shift still references this register. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(@PathVariable UUID id) {
        Register r = registers.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (shifts.existsByRegisterIdAndClosedAtIsNull(id)) {
            throw new ResponseStatusException(CONFLICT, "Kassette hat eine offene Schicht");
        }
        r.setActive(false);
        registers.save(r);
    }

    @GetMapping("/export.csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportCsv() {
        var w = new CsvWriter().row("Name", "Reihenfolge", "Aktiv");
        for (Register r : registers.findAllByOrderBySortOrderAsc()) {
            w.row(r.getName(), r.getSortOrder(), r.isActive() ? "Ja" : "Nein");
        }
        return csv(w.toCsv(), "kassetten-" + LocalDate.now(ZoneOffset.UTC) + ".csv");
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

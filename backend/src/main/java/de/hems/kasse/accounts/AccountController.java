package de.hems.kasse.accounts;

import de.hems.kasse.auth.KassePrincipal;
import de.hems.kasse.auth.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Admin-only management of Gruppen and admin logins.
 *
 * <p>Plaintext passwords are never part of the ordinary listing — they are only returned by
 * {@link #slips} and by the two endpoints that just set a password, and every such response is
 * marked {@code no-store} so no proxy or browser cache keeps a copy.
 */
@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    public record AccountDto(UUID id, String role, String name, boolean active,
                             Instant createdAt, Instant updatedAt) {
        static AccountDto of(Account a) {
            return new AccountDto(a.getId(), a.getRole().name(), a.getName(), a.isActive(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    /** An account plus its readable password — everything the Passwort-Zettel needs. */
    public record SlipDto(UUID id, String role, String name, boolean active, String password) {}

    public record NewAccount(
            @NotBlank @Size(max = 20) String role,
            @NotBlank @Size(max = AccountService.MAX_NAME_LENGTH) String name,
            @Size(max = AccountService.MAX_PASSWORD_LENGTH) String password) {}

    public record PatchAccount(@Size(max = AccountService.MAX_NAME_LENGTH) String name, Boolean active) {}

    public record NewPassword(@Size(max = AccountService.MAX_PASSWORD_LENGTH) String password) {}

    @GetMapping
    public List<AccountDto> list() {
        return accounts.all().stream().map(AccountDto::of).toList();
    }

    @PostMapping
    public ResponseEntity<SlipDto> create(@RequestBody @Valid NewAccount body) {
        Account created = accounts.create(parseRole(body.role()), body.name(), body.password());
        return noStore(toSlip(created));
    }

    @PatchMapping("/{id}")
    public AccountDto patch(@PathVariable UUID id, @RequestBody @Valid PatchAccount body) {
        Account a = accounts.byId(id);
        if (body.name() != null && !body.name().isBlank()) a = accounts.rename(id, body.name());
        if (body.active() != null) a = accounts.setActive(id, body.active());
        return AccountDto.of(a);
    }

    /** Sets the given password, or generates one when the body is empty. Returns the plaintext. */
    @PostMapping("/{id}/password")
    public ResponseEntity<SlipDto> setPassword(@PathVariable UUID id, @RequestBody(required = false) NewPassword body) {
        Account updated = accounts.setPassword(id, body == null ? null : body.password());
        return noStore(toSlip(updated));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal KassePrincipal caller) {
        Account a = accounts.byId(id);
        if (a.getRole() == Role.ADMIN && a.getName().equalsIgnoreCase(caller.name())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Das eigene Konto kann nicht gelöscht werden.");
        }
        accounts.delete(id);
    }

    /**
     * Slip data for printing. Without {@code ids} every account is returned, which is the
     * "alle Zettel drucken" case; otherwise only the selected ones.
     */
    @GetMapping("/slips")
    public ResponseEntity<List<SlipDto>> slips(@RequestParam(required = false) List<UUID> ids) {
        List<Account> selected = ids == null || ids.isEmpty()
                ? accounts.all()
                : ids.stream().map(accounts::byId).toList();
        return noStore(selected.stream().map(this::toSlip).toList());
    }

    private SlipDto toSlip(Account a) {
        return new SlipDto(a.getId(), a.getRole().name(), a.getName(), a.isActive(), accounts.plaintextOf(a));
    }

    /** Keeps password-bearing responses out of every cache between here and the browser. */
    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }

    private static Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unbekannte Rolle: " + raw);
        }
    }
}

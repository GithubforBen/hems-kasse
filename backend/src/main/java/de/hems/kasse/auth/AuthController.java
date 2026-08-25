package de.hems.kasse.auth;

import de.hems.kasse.accounts.Account;
import de.hems.kasse.accounts.AccountService;
import de.hems.kasse.shift.ShiftService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwt;
    private final LoginAttemptService attempts;
    private final ShiftService shifts;
    private final AccountService accounts;

    public AuthController(JwtService jwt, LoginAttemptService attempts, ShiftService shifts,
                          AccountService accounts) {
        this.jwt = jwt;
        this.attempts = attempts;
        this.shifts = shifts;
        this.accounts = accounts;
    }

    public record LoginRequest(
            @NotBlank @Size(max = 40) String role,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String gruppe,
            Integer abrechnungNr,
            @NotBlank @Size(max = 200) String password) {}

    public record UserDto(String name, String gruppe, Integer abrechnungNr, String role) {}

    public record LoginResponse(String token, UserDto user) {}

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest req, HttpServletRequest http) {
        Role role;
        try {
            role = Role.valueOf(req.role().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown role");
        }

        String name = req.name().trim();
        String targetKey = switch (role) {
            case VERKAUF -> KassePrincipal.subjectKey(role, name, req.gruppe());
            case ADMIN -> KassePrincipal.subjectKey(role, name, null);
        };
        String ipKey = "ip:" + clientIp(http);
        if (attempts.isBlocked(targetKey) || attempts.isBlocked(ipKey)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "Zu viele Fehlversuche – bitte in ein paar Minuten erneut versuchen");
        }

        return switch (role) {
            case VERKAUF -> {
                if (req.gruppe() == null || req.gruppe().isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "Gruppe fehlt");
                }
                String gruppe = req.gruppe().trim();
                Account account = accounts.authenticate(Role.VERKAUF, gruppe, req.password())
                        .orElseThrow(() -> {
                            attempts.recordFailure(targetKey);
                            attempts.recordFailure(ipKey);
                            return new ResponseStatusException(UNAUTHORIZED, "Falsches Gruppenpasswort");
                        });
                attempts.reset(targetKey);
                // Use the stored spelling so receipts and exports stay consistent regardless of
                // how the name was typed at the login form.
                gruppe = account.getName();
                // Refuse a spent envelope here, while the cashier is still looking at the form —
                // failing later, once a session exists, is far harder to recover from.
                shifts.assertUsable(req.abrechnungNr());
                KassePrincipal p = KassePrincipal.verkauf(name, gruppe, req.abrechnungNr());
                yield new LoginResponse(jwt.issue(p), new UserDto(name, gruppe, req.abrechnungNr(), "VERKAUF"));
            }
            case ADMIN -> {
                Account account = accounts.authenticate(Role.ADMIN, name, req.password())
                        .orElseThrow(() -> {
                            attempts.recordFailure(targetKey);
                            attempts.recordFailure(ipKey);
                            return new ResponseStatusException(UNAUTHORIZED, "Falscher Admin-Login");
                        });
                attempts.reset(targetKey);
                KassePrincipal p = KassePrincipal.admin(account.getName());
                yield new LoginResponse(jwt.issue(p), new UserDto(account.getName(), null, null, "ADMIN"));
            }
        };
    }

    /**
     * Best-effort client IP for the throttle. Behind the Cloudflare tunnel every request
     * arrives from the tunnel container, so prefer the proxy-supplied headers; these are
     * spoofable when no proxy is involved, which is acceptable because the per-account
     * key blocks regardless of the claimed source address.
     */
    private static String clientIp(HttpServletRequest req) {
        String cf = req.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal KassePrincipal p) {
        if (p == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(new UserDto(p.name(), p.gruppe(), p.abrechnungNr(), p.role().name()));
    }
}

package de.hems.kasse.auth;

import de.hems.kasse.config.KasseProperties;
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
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwt;
    private final LoginAttemptService attempts;
    private final ShiftService shifts;
    private final Map<String, String> groupPasswords;
    private final Map<String, String> adminUsers;

    public AuthController(JwtService jwt, LoginAttemptService attempts, ShiftService shifts,
                          KasseProperties props) {
        this.jwt = jwt;
        this.attempts = attempts;
        this.shifts = shifts;
        this.groupPasswords = props.getPasswordsByGroup();
        this.adminUsers = props.getPasswordsByAdmin();
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
                String expected = groupPasswords.get(gruppe.toLowerCase(Locale.ROOT));
                if (expected == null || !constantTimeEquals(expected, req.password())) {
                    attempts.recordFailure(targetKey);
                    attempts.recordFailure(ipKey);
                    throw new ResponseStatusException(UNAUTHORIZED, "Falsches Gruppenpasswort");
                }
                attempts.reset(targetKey);
                // Refuse a spent envelope here, while the cashier is still looking at the form —
                // failing later, once a session exists, is far harder to recover from.
                shifts.assertUsable(req.abrechnungNr());
                KassePrincipal p = KassePrincipal.verkauf(name, gruppe, req.abrechnungNr());
                yield new LoginResponse(jwt.issue(p), new UserDto(name, gruppe, req.abrechnungNr(), "VERKAUF"));
            }
            case ADMIN -> {
                String expected = adminUsers.get(name.toLowerCase(Locale.ROOT));
                if (expected == null || !constantTimeEquals(expected, req.password())) {
                    attempts.recordFailure(targetKey);
                    attempts.recordFailure(ipKey);
                    throw new ResponseStatusException(UNAUTHORIZED, "Falscher Admin-Login");
                }
                attempts.reset(targetKey);
                KassePrincipal p = KassePrincipal.admin(name);
                yield new LoginResponse(jwt.issue(p), new UserDto(name, null, null, "ADMIN"));
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

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ab.length; i++) r |= ab[i] ^ bb[i];
        return r == 0;
    }
}

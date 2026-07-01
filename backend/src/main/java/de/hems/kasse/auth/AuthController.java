package de.hems.kasse.auth;

import de.hems.kasse.config.KasseProperties;
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
    private final Map<String, String> classPasswords;
    private final Map<String, String> adminUsers;

    public AuthController(JwtService jwt, LoginAttemptService attempts, KasseProperties props) {
        this.jwt = jwt;
        this.attempts = attempts;
        this.classPasswords = props.getClassPasswords();
        this.adminUsers = props.getAdminUsers();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 40) String role,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String klasse,
            @NotBlank @Size(max = 200) String password) {}

    public record UserDto(String name, String klasse, String role) {}

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
            case VERKAUF -> KassePrincipal.subjectKey(role, name, req.klasse());
            case ADMIN -> KassePrincipal.subjectKey(role, name, null);
        };
        String ipKey = "ip:" + clientIp(http);
        if (attempts.isBlocked(targetKey) || attempts.isBlocked(ipKey)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "Zu viele Fehlversuche – bitte in ein paar Minuten erneut versuchen");
        }

        return switch (role) {
            case VERKAUF -> {
                if (req.klasse() == null || req.klasse().isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "Klasse fehlt");
                }
                String klasse = req.klasse().trim();
                String expected = classPasswords.get(klasse.toLowerCase(Locale.ROOT));
                if (expected == null || !constantTimeEquals(expected, req.password())) {
                    attempts.recordFailure(targetKey);
                    attempts.recordFailure(ipKey);
                    throw new ResponseStatusException(UNAUTHORIZED, "Falsches Klassenpasswort");
                }
                attempts.reset(targetKey);
                KassePrincipal p = KassePrincipal.verkauf(name, klasse);
                yield new LoginResponse(jwt.issue(p), new UserDto(name, klasse, "VERKAUF"));
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
                yield new LoginResponse(jwt.issue(p), new UserDto(name, null, "ADMIN"));
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
        return ResponseEntity.ok(new UserDto(p.name(), p.klasse(), p.role().name()));
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

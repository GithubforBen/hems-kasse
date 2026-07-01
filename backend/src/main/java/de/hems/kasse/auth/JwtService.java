package de.hems.kasse.auth;

import de.hems.kasse.config.KasseProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    /** The placeholder shipped in .env.example — fine for local dev, fatal in production. */
    private static final String EXAMPLE_SECRET =
            "ZGV2LW9ubHktc2VjcmV0LXBsZWFzZS1jaGFuZ2UtbWUtMzItYnl0ZXMtbWluaW11bQ==";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(KasseProperties props) {
        String raw = props.getJwt().getSecret();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("KASSE_JWT_SECRET is not set");
        }
        if (EXAMPLE_SECRET.equals(raw)) {
            log.warn("KASSE_JWT_SECRET is still the .env.example placeholder — anyone who has seen "
                    + "the repository can forge admin tokens. Generate a real secret before going live: "
                    + "openssl rand -base64 48");
        }
        // Accept either base64 or plain text — keep at least 32 bytes for HS256.
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
            if (bytes.length < 32) bytes = raw.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            bytes = raw.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("KASSE_JWT_SECRET must decode to at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofHours(Math.max(1, props.getJwt().getTtlHours()));
    }

    public String issue(KassePrincipal p) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(p.name())
                .claim("role", p.role().name())
                .claim("klasse", p.klasse())
                .claim("sk", p.subjectKey())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public KassePrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Role role = Role.valueOf(String.valueOf(claims.get("role")));
            String name = claims.getSubject();
            String klasse = claims.get("klasse", String.class);
            String sk = claims.get("sk", String.class);
            if (sk == null) sk = KassePrincipal.subjectKey(role, name, klasse);
            return new KassePrincipal(role, name, klasse, sk);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}

package de.hems.kasse.config;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * All Schulkasse configuration. Lives under the {@code kasse.*} prefix in {@code application.yml},
 * which is populated from environment variables (loaded from {@code .env} via spring-dotenv).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kasse")
public class KasseProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();

    /** Raw comma-separated "GRUPPE:passwort,GRUPPE:passwort" from KASSE_GROUP_PASSWORDS. */
    private String groupPasswords = "";
    /** Raw comma-separated "user:passwort,user:passwort" from KASSE_ADMIN_USERS. */
    private String adminUsers = "";

    private Epc epc = new Epc();

    /**
     * Parsed views of the two raw lists above. They are deliberately not settable: a settable
     * {@code Map} field here would share its canonical name with the environment variable that
     * feeds the raw string (KASSE_GROUP_PASSWORDS → {@code kasse.group-passwords}), and Spring
     * would then try to bind that string straight onto the map and refuse to start.
     */
    @Setter(AccessLevel.NONE)
    private Map<String, String> passwordsByGroup = new LinkedHashMap<>();
    @Setter(AccessLevel.NONE)
    private Map<String, String> passwordsByAdmin = new LinkedHashMap<>();

    @PostConstruct
    void parseColonLists() {
        passwordsByGroup = parse(groupPasswords);
        passwordsByAdmin = parse(adminUsers);
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            int idx = trimmed.indexOf(':');
            if (idx <= 0 || idx == trimmed.length() - 1) {
                throw new IllegalStateException("Invalid entry (expected key:value): '" + trimmed + "'");
            }
            String key = trimmed.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(idx + 1).trim();
            out.put(key, value);
        }
        return out;
    }

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private int ttlHours = 12;
    }

    @Getter @Setter
    public static class Cors {
        private String origins = "http://localhost:3000";
    }

    @Getter @Setter
    public static class Epc {
        private String name = "";
        private String iban = "";
        private String bic = "";
        private String purpose = "";
        private String remittance = "Kuchenverkauf";
    }
}

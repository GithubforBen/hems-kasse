package de.hems.kasse.accounts;

import de.hems.kasse.auth.Role;
import de.hems.kasse.config.KasseProperties;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    /**
     * Deliberately excludes characters that get misread off a printed slip: 0/O, 1/l/I and the
     * letters that look alike in most fonts. Everything is lowercase so nobody has to guess at
     * capitalisation while a queue is waiting.
     */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int GENERATED_LENGTH = 10;
    public static final int MIN_PASSWORD_LENGTH = 4;
    public static final int MAX_PASSWORD_LENGTH = 200;
    public static final int MAX_NAME_LENGTH = 120;

    private final AccountRepository accounts;
    private final SecretBox secrets;
    private final KasseProperties props;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accounts, SecretBox secrets, KasseProperties props) {
        this.accounts = accounts;
        this.secrets = secrets;
        this.props = props;
    }

    // ------------------------------------------------------------------ bootstrap

    /**
     * Carries the .env logins over into the database once, so upgrading an existing
     * installation does not lock anybody out. Only names that do not exist yet are inserted,
     * which makes this safe to run on every boot: a password changed in the admin UI is never
     * overwritten by the stale value still sitting in the .env.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedFromEnvironment() {
        int created = seedRole(Role.VERKAUF, props.getPasswordsByGroup())
                + seedRole(Role.ADMIN, props.getPasswordsByAdmin());
        if (created > 0) {
            log.info("{} Konto/Konten aus der .env übernommen. Sie lassen sich jetzt im Admin-Bereich verwalten.", created);
        }
        if (accounts.countByRole(Role.ADMIN) == 0) {
            log.warn("Es existiert kein Admin-Konto. Bitte KASSE_ADMIN_USERS setzen und neu starten, "
                    + "sonst kommt niemand in den Admin-Bereich.");
        }
    }

    private int seedRole(Role role, Map<String, String> fromEnv) {
        int created = 0;
        for (var e : fromEnv.entrySet()) {
            if (accounts.findByRoleAndName(role, e.getKey()).isPresent()) continue;
            try {
                accounts.save(newAccount(role, e.getKey(), e.getValue()));
                created++;
            } catch (DataIntegrityViolationException race) {
                // Another instance seeded the same name concurrently — nothing left to do.
                log.debug("Konto {}/{} wurde parallel angelegt", role, e.getKey());
            }
        }
        return created;
    }

    // ------------------------------------------------------------------ login

    /**
     * Returns the matching active account, or empty when the name is unknown, deactivated or
     * the password does not match. The caller must not distinguish these cases to the user.
     */
    public Optional<Account> authenticate(Role role, String name, String password) {
        return accounts.findByRoleAndName(role, name)
                .filter(Account::isActive)
                .filter(a -> constantTimeEquals(readPassword(a), password));
    }

    /** Plaintext password, or null when it cannot be decrypted with the current key. */
    private String readPassword(Account a) {
        try {
            return secrets.decrypt(a.getPasswordEnc());
        } catch (SecretBox.SecretUnreadableException e) {
            log.error("Passwort von {}/{} ist nicht lesbar – KASSE_SECRET_KEY geändert? "
                    + "Das Konto braucht ein neues Passwort.", a.getRole(), a.getName());
            return null;
        }
    }

    // ------------------------------------------------------------------ management

    public List<Account> all() {
        return accounts.findAllByOrderByRoleAscNameAsc();
    }

    public Account byId(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Konto nicht gefunden"));
    }

    /** The plaintext password of an account, for the Passwort-Zettel. */
    public String plaintextOf(Account a) {
        String pw = readPassword(a);
        if (pw == null) {
            throw new ResponseStatusException(CONFLICT,
                    "Das Passwort von „" + a.getName() + "\" lässt sich nicht mehr entschlüsseln. "
                            + "Bitte ein neues Passwort erzeugen.");
        }
        return pw;
    }

    @Transactional
    public Account create(Role role, String rawName, String rawPassword) {
        String name = requireName(rawName);
        String password = rawPassword == null || rawPassword.isBlank() ? generatePassword() : requirePassword(rawPassword);
        if (accounts.findByRoleAndName(role, name).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "„" + name + "\" gibt es bereits.");
        }
        try {
            return accounts.save(newAccount(role, name, password));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(CONFLICT, "„" + name + "\" gibt es bereits.");
        }
    }

    @Transactional
    public Account rename(UUID id, String rawName) {
        Account a = byId(id);
        String name = requireName(rawName);
        if (name.equalsIgnoreCase(a.getName())) {
            a.setName(name); // pure capitalisation change
        } else {
            if (accounts.findByRoleAndName(a.getRole(), name).isPresent()) {
                throw new ResponseStatusException(CONFLICT, "„" + name + "\" gibt es bereits.");
            }
            a.setName(name);
        }
        a.setUpdatedAt(Instant.now());
        return accounts.save(a);
    }

    @Transactional
    public Account setActive(UUID id, boolean active) {
        Account a = byId(id);
        if (!active) assertNotLastAdmin(a, "Das letzte Admin-Konto kann nicht deaktiviert werden.");
        a.setActive(active);
        a.setUpdatedAt(Instant.now());
        return accounts.save(a);
    }

    /** Sets a given password, or generates one when {@code rawPassword} is blank. */
    @Transactional
    public Account setPassword(UUID id, String rawPassword) {
        Account a = byId(id);
        String password = rawPassword == null || rawPassword.isBlank() ? generatePassword() : requirePassword(rawPassword);
        a.setPasswordEnc(secrets.encrypt(password));
        a.setUpdatedAt(Instant.now());
        return accounts.save(a);
    }

    @Transactional
    public void delete(UUID id) {
        Account a = byId(id);
        assertNotLastAdmin(a, "Das letzte Admin-Konto kann nicht gelöscht werden.");
        accounts.delete(a);
    }

    /**
     * Guards against locking every administrator out of the app. Shifts keep the group name as
     * plain text, so removing a Gruppe never rewrites history and needs no such guard.
     */
    private void assertNotLastAdmin(Account a, String message) {
        if (a.getRole() != Role.ADMIN || !a.isActive()) return;
        boolean anotherActiveAdmin = accounts.findAllByRoleAndActiveIsTrueOrderByNameAsc(Role.ADMIN)
                .stream().anyMatch(other -> !other.getId().equals(a.getId()));
        if (!anotherActiveAdmin) throw new ResponseStatusException(CONFLICT, message);
    }

    public String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private Account newAccount(Role role, String name, String password) {
        Instant now = Instant.now();
        return Account.builder()
                .id(UUID.randomUUID())
                .role(role)
                .name(name.trim())
                .passwordEnc(secrets.encrypt(password))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Name fehlt");
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "Name ist zu lang (max. " + MAX_NAME_LENGTH + " Zeichen)");
        }
        return name;
    }

    private static String requirePassword(String raw) {
        String pw = raw.trim();
        if (pw.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Passwort ist zu kurz (mindestens " + MIN_PASSWORD_LENGTH + " Zeichen)");
        }
        if (pw.length() > MAX_PASSWORD_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Passwort ist zu lang (max. " + MAX_PASSWORD_LENGTH + " Zeichen)");
        }
        return pw;
    }

    /** Length-independent comparison so a wrong password reveals nothing through timing. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ab.length; i++) r |= ab[i] ^ bb[i];
        return r == 0;
    }

    /** Normalised key used by the login throttle and by shift subject keys. */
    public static String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}

package de.hems.kasse.auth;

import java.util.Locale;

/**
 * Authenticated caller derived from a valid JWT. {@code subjectKey} is the canonical
 * identity for "my history" lookups; never trust request bodies for this.
 */
public record KassePrincipal(Role role, String name, String gruppe, String subjectKey) {

    public static KassePrincipal verkauf(String name, String gruppe) {
        return new KassePrincipal(Role.VERKAUF, name, gruppe, subjectKey(Role.VERKAUF, name, gruppe));
    }

    public static KassePrincipal admin(String username) {
        return new KassePrincipal(Role.ADMIN, username, null, subjectKey(Role.ADMIN, username, null));
    }

    public static String subjectKey(Role role, String name, String gruppe) {
        return switch (role) {
            case VERKAUF -> "verkauf:" + normalise(gruppe);
            case ADMIN -> "admin:" + normalise(name);
        };
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}

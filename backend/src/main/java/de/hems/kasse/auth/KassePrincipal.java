package de.hems.kasse.auth;

import java.util.Locale;

/**
 * Authenticated caller derived from a valid JWT. {@code subjectKey} is the canonical
 * identity for "my history" lookups; never trust request bodies for this.
 */
public record KassePrincipal(Role role, String name, String gruppe, Integer abrechnungNr, String subjectKey) {

    public static KassePrincipal verkauf(String name, String gruppe, Integer abrechnungNr) {
        return new KassePrincipal(Role.VERKAUF, name, gruppe, abrechnungNr,
                subjectKey(Role.VERKAUF, name, gruppe));
    }

    public static KassePrincipal admin(String username) {
        return new KassePrincipal(Role.ADMIN, username, null, null, subjectKey(Role.ADMIN, username, null));
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

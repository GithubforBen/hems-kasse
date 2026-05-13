package de.hems.kasse.auth;

public enum Role {
    VERKAUF,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
